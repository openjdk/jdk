/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
#include "precompiled.hpp"
#include "logging/log.hpp"
#include "logging/logConfiguration.hpp"
#include "logging/logFileOutput.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/defaultStream.hpp"

const char* LogFileOutput::FileOpenMode = "a";
const char* LogFileOutput::PidFilenamePlaceholder = "%p";
const char* LogFileOutput::TimestampFilenamePlaceholder = "%t";
const char* LogFileOutput::TimestampFormat = "%Y-%m-%d_%H-%M-%S";
const char* LogFileOutput::FileSizeOptionKey = "filesize";
const char* LogFileOutput::FileCountOptionKey = "filecount";
char        LogFileOutput::_pid_str[PidBufferSize];
char        LogFileOutput::_vm_start_time_str[StartTimeBufferSize];

LogFileOutput::LogFileOutput(const char* name)
    : LogFileStreamOutput(NULL), _name(os::strdup_check_oom(name, mtLogging)),
      _file_name(NULL), _archive_name(NULL), _archive_name_len(0), _current_size(0),
      _rotate_size(0), _current_file(1), _file_count(0),
      _rotation_lock(Mutex::leaf, "LogFileOutput rotation lock", true, Mutex::_safepoint_check_sometimes) {
  _file_name = make_file_name(name, _pid_str, _vm_start_time_str);
}

void LogFileOutput::set_file_name_parameters(jlong vm_start_time) {
  int res = jio_snprintf(_pid_str, sizeof(_pid_str), "%d", os::current_process_id());
  assert(res > 0, "PID buffer too small");

  struct tm local_time;
  time_t utc_time = vm_start_time / 1000;
  os::localtime_pd(&utc_time, &local_time);
  res = (int)strftime(_vm_start_time_str, sizeof(_vm_start_time_str), TimestampFormat, &local_time);
  assert(res > 0, "VM start time buffer too small.");
}

LogFileOutput::~LogFileOutput() {
  if (_stream != NULL) {
    if (_archive_name != NULL) {
      archive();
    }
    if (fclose(_stream) != 0) {
      jio_fprintf(defaultStream::error_stream(), "Could not close log file '%s' (%s).\n",
                  _file_name, strerror(errno));
    }
  }
  os::free(_archive_name);
  os::free(_file_name);
  os::free(const_cast<char*>(_name));
}

size_t LogFileOutput::parse_value(const char* value_str) {
  char* end;
  unsigned long long value = strtoull(value_str, &end, 10);
  if (!isdigit(*value_str) || end != value_str + strlen(value_str) || value >= SIZE_MAX) {
    return SIZE_MAX;
  }
  return value;
}

bool LogFileOutput::configure_rotation(const char* options) {
  if (options == NULL || strlen(options) == 0) {
    return true;
  }
  bool success = true;
  char* opts = os::strdup_check_oom(options, mtLogging);

  char* comma_pos;
  char* pos = opts;
  do {
    comma_pos = strchr(pos, ',');
    if (comma_pos != NULL) {
      *comma_pos = '\0';
    }

    char* equals_pos = strchr(pos, '=');
    if (equals_pos == NULL) {
      success = false;
      break;
    }
    char* key = pos;
    char* value_str = equals_pos + 1;
    *equals_pos = '\0';

    if (strcmp(FileCountOptionKey, key) == 0) {
      size_t value = parse_value(value_str);
      if (value == SIZE_MAX || value >= UINT_MAX) {
        success = false;
        break;
      }
      _file_count = static_cast<uint>(value);
      _file_count_max_digits = static_cast<uint>(log10(static_cast<double>(_file_count)) + 1);
      _archive_name_len = 2 + strlen(_file_name) + _file_count_max_digits;
      _archive_name = NEW_C_HEAP_ARRAY(char, _archive_name_len, mtLogging);
    } else if (strcmp(FileSizeOptionKey, key) == 0) {
      size_t value = parse_value(value_str);
      if (value == SIZE_MAX || value > SIZE_MAX / K) {
        success = false;
        break;
      }
      _rotate_size = value * K;
    } else {
      success = false;
      break;
    }
    pos = comma_pos + 1;
  } while (comma_pos != NULL);

  os::free(opts);
  return success;
}

bool LogFileOutput::initialize(const char* options) {
  if (!configure_rotation(options)) {
    return false;
  }
  _stream = fopen(_file_name, FileOpenMode);
  if (_stream == NULL) {
    log_error(logging)("Could not open log file '%s' (%s).\n", _file_name, strerror(errno));
    return false;
  }
  return true;
}

int LogFileOutput::write(const LogDecorations& decorations, const char* msg) {
  if (_stream == NULL) {
    // An error has occurred with this output, avoid writing to it.
    return 0;
  }
  int written = LogFileStreamOutput::write(decorations, msg);
  _current_size += written;

  if (should_rotate()) {
    MutexLockerEx ml(&_rotation_lock, true /* no safepoint check */);
    if (should_rotate()) {
      rotate();
    }
  }

  return written;
}

void LogFileOutput::archive() {
  assert(_archive_name != NULL && _archive_name_len > 0, "Rotation must be configured before using this function.");
  int ret = jio_snprintf(_archive_name, _archive_name_len, "%s.%0*u",
                         _file_name, _file_count_max_digits, _current_file);
  assert(ret >= 0, "Buffer should always be large enough");

  // Attempt to remove possibly existing archived log file before we rename.
  // Don't care if it fails, we really only care about the rename that follows.
  remove(_archive_name);

  // Rename the file from ex hotspot.log to hotspot.log.2
  if (rename(_file_name, _archive_name) == -1) {
    jio_fprintf(defaultStream::error_stream(), "Could not rename log file '%s' to '%s' (%s).\n",
                _file_name, _archive_name, strerror(errno));
  }
}

void LogFileOutput::rotate() {
  // Archive the current log file
  archive();

  // Open the active log file using the same stream as before
  _stream = freopen(_file_name, FileOpenMode, _stream);
  if (_stream == NULL) {
    jio_fprintf(defaultStream::error_stream(), "Could not reopen file '%s' during log rotation (%s).\n",
                _file_name, strerror(errno));
    return;
  }

  // Reset accumulated size, increase current file counter, and check for file count wrap-around.
  _current_size = 0;
  _current_file = (_current_file >= _file_count ? 1 : _current_file + 1);
}

char* LogFileOutput::make_file_name(const char* file_name,
                                    const char* pid_string,
                                    const char* timestamp_string) {
  char* result = NULL;

  // Lets start finding out if we have any %d and/or %t in the name.
  // We will only replace the first occurrence of any placeholder
  const char* pid = strstr(file_name, PidFilenamePlaceholder);
  const char* timestamp = strstr(file_name, TimestampFilenamePlaceholder);

  if (pid == NULL && timestamp == NULL) {
    // We found no place-holders, return the simple filename
    return os::strdup_check_oom(file_name, mtLogging);
  }

  // At least one of the place-holders were found in the file_name
  const char* first = "";
  size_t first_pos = SIZE_MAX;
  size_t first_replace_len = 0;

  const char* second = "";
  size_t second_pos = SIZE_MAX;
  size_t second_replace_len = 0;

  // If we found a %p, then setup our variables accordingly
  if (pid != NULL) {
    if (timestamp == NULL || pid < timestamp) {
      first = pid_string;
      first_pos = pid - file_name;
      first_replace_len = strlen(PidFilenamePlaceholder);
    } else {
      second = pid_string;
      second_pos = pid - file_name;
      second_replace_len = strlen(PidFilenamePlaceholder);
    }
  }

  if (timestamp != NULL) {
    if (pid == NULL || timestamp < pid) {
      first = timestamp_string;
      first_pos = timestamp - file_name;
      first_replace_len = strlen(TimestampFilenamePlaceholder);
    } else {
      second = timestamp_string;
      second_pos = timestamp - file_name;
      second_replace_len = strlen(TimestampFilenamePlaceholder);
    }
  }

  size_t first_len = strlen(first);
  size_t second_len = strlen(second);

  // Allocate the new buffer, size it to hold all we want to put in there +1.
  size_t result_len =  strlen(file_name) + first_len - first_replace_len + second_len - second_replace_len;
  result = NEW_C_HEAP_ARRAY(char, result_len + 1, mtLogging);

  // Assemble the strings
  size_t file_name_pos = 0;
  size_t i = 0;
  while (i < result_len) {
    if (file_name_pos == first_pos) {
      // We are in the range of the first placeholder
      strcpy(result + i, first);
      // Bump output buffer position with length of replacing string
      i += first_len;
      // Bump source buffer position to skip placeholder
      file_name_pos += first_replace_len;
    } else if (file_name_pos == second_pos) {
      // We are in the range of the second placeholder
      strcpy(result + i, second);
      i += second_len;
      file_name_pos += second_replace_len;
    } else {
      // Else, copy char by char of the original file
      result[i] = file_name[file_name_pos++];
      i++;
    }
  }
  // Add terminating char
  result[result_len] = '\0';
  return result;
}
