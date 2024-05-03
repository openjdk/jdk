/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "logging/log.hpp"
#include "logging/logAsyncWriter.hpp"
#include "logging/logConfiguration.hpp"
#include "logging/logFileOutput.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/os.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/globalDefinitions.hpp"

const char* const LogFileOutput::Prefix = "file=";
const char* const LogFileOutput::FileOpenMode = "a";
const char* const LogFileOutput::PidFilenamePlaceholder = "%p";
const char* const LogFileOutput::TimestampFilenamePlaceholder = "%t";
const char* const LogFileOutput::TimestampFormat = "%Y-%m-%d_%H-%M-%S";
const char* const LogFileOutput::HostnameFilenamePlaceholder = "%hn";
const char* const LogFileOutput::FileSizeOptionKey = "filesize";
const char* const LogFileOutput::FileCountOptionKey = "filecount";
char        LogFileOutput::_pid_str[PidBufferSize];
char        LogFileOutput::_vm_start_time_str[StartTimeBufferSize];

LogFileOutput::LogFileOutput(const char* name)
    : LogFileStreamOutput(nullptr), _name(os::strdup_check_oom(name, mtLogging)),
      _file_name(nullptr), _archive_name(nullptr), _current_file(0),
      _file_count(DefaultFileCount), _is_default_file_count(true), _archive_name_len(0),
      _rotate_size(DefaultFileSize), _current_size(0), _rotation_semaphore(1) {
  assert(strstr(name, Prefix) == name, "invalid output name '%s': missing prefix: %s", name, Prefix);
  _file_name = make_file_name(name + strlen(Prefix), _pid_str, _vm_start_time_str);
}

const char* LogFileOutput::cur_log_file_name() {
  if (strlen(_archive_name) == 0) {
    return _file_name;
  } else {
    return _archive_name;
  }
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
  if (_stream != nullptr) {
    if (fclose(_stream) != 0) {
      jio_fprintf(defaultStream::error_stream(), "Could not close log file '%s' (%s).\n",
                  _file_name, os::strerror(errno));
    }
  }
  os::free(_archive_name);
  os::free(_file_name);
  os::free(const_cast<char*>(_name));
}

static size_t parse_value(const char* value_str) {
  char* end;
  unsigned long long value = strtoull(value_str, &end, 10);
  if (!isdigit(*value_str) || end != value_str + strlen(value_str) || value >= SIZE_MAX) {
    return SIZE_MAX;
  }
  return value;
}

static uint number_of_digits(uint number) {
  return number < 10 ? 1 : (number < 100 ? 2 : 3);
}

static bool is_regular_file(const char* filename) {
  struct stat st;
  int ret = os::stat(filename, &st);
  if (ret != 0) {
    return false;
  }
  return (st.st_mode & S_IFMT) == S_IFREG;
}

static bool is_fifo_file(const char* filename) {
  struct stat st;
  int ret = os::stat(filename, &st);
  if (ret != 0) {
    return false;
  }
  return S_ISFIFO(st.st_mode);
}

// Try to find the next number that should be used for file rotation.
// Return UINT_MAX on error.
static uint next_file_number(const char* filename,
                             uint number_of_digits,
                             uint filecount,
                             outputStream* errstream) {
  bool found = false;
  uint next_num = 0;

  // len is filename + dot + digits + null char
  size_t len = strlen(filename) + number_of_digits + 2;
  char* archive_name = NEW_C_HEAP_ARRAY(char, len, mtLogging);
  char* oldest_name = NEW_C_HEAP_ARRAY(char, len, mtLogging);

  for (uint i = 0; i < filecount; i++) {
    int ret = jio_snprintf(archive_name, len, "%s.%0*u",
                           filename, number_of_digits, i);
    assert(ret > 0 && static_cast<size_t>(ret) == len - 1,
           "incorrect buffer length calculation");

    if (os::file_exists(archive_name) && !is_regular_file(archive_name)) {
      // We've encountered something that's not a regular file among the
      // possible file rotation targets. Fail immediately to prevent
      // problems later.
      errstream->print_cr("Possible rotation target file '%s' already exists "
                          "but is not a regular file.", archive_name);
      next_num = UINT_MAX;
      break;
    }

    // Stop looking if we find an unused file name
    if (!os::file_exists(archive_name)) {
      next_num = i;
      found = true;
      break;
    }

    // Keep track of oldest existing log file
    if (!found
        || os::compare_file_modified_times(oldest_name, archive_name) > 0) {
      strcpy(oldest_name, archive_name);
      next_num = i;
      found = true;
    }
  }

  FREE_C_HEAP_ARRAY(char, oldest_name);
  FREE_C_HEAP_ARRAY(char, archive_name);
  return next_num;
}

bool LogFileOutput::set_option(const char* key, const char* value, outputStream* errstream) {
  bool success = LogFileStreamOutput::set_option(key, value, errstream);
  if (!success) {
    if (strcmp(FileCountOptionKey, key) == 0) {
      size_t sizeval = parse_value(value);
      if (sizeval > MaxRotationFileCount) {
        errstream->print_cr("Invalid option: %s must be in range [0, %u]",
                            FileCountOptionKey,
                            MaxRotationFileCount);
      } else {
        _file_count = static_cast<uint>(sizeval);
        _is_default_file_count = false;
        success = true;
      }
    } else if (strcmp(FileSizeOptionKey, key) == 0) {
      julong longval;
      success = Arguments::atojulong(value, &longval);
      if (!success || (longval > SIZE_MAX)) {
        errstream->print_cr("Invalid option: %s must be in range [0, "
                            SIZE_FORMAT "]", FileSizeOptionKey, (size_t)SIZE_MAX);
        success = false;
      } else {
        _rotate_size = static_cast<size_t>(longval);
        success = true;
      }
    }
  }
  return success;
}

bool LogFileOutput::initialize(const char* options, outputStream* errstream) {
  if (!parse_options(options, errstream)) {
    return false;
  }

  bool file_exist = os::file_exists(_file_name);
  if (file_exist && _is_default_file_count && is_fifo_file(_file_name)) {
    _file_count = 0; // Prevent file rotation for fifo's such as named pipes.
  }

  if (_file_count > 0) {
    // compute digits with filecount - 1 since numbers will start from 0
    _file_count_max_digits = number_of_digits(_file_count - 1);
    _archive_name_len = 2 + strlen(_file_name) + _file_count_max_digits;
    _archive_name = NEW_C_HEAP_ARRAY(char, _archive_name_len, mtLogging);
    _archive_name[0] = 0;
  }

  log_trace(logging)("Initializing logging to file '%s' (filecount: %u"
                     ", filesize: " SIZE_FORMAT " KiB).",
                     _file_name, _file_count, _rotate_size / K);

  if (_file_count > 0 && file_exist) {
    if (!is_regular_file(_file_name)) {
      errstream->print_cr("Unable to log to file %s with log file rotation: "
                          "%s is not a regular file",
                          _file_name, _file_name);
      return false;
    }
    _current_file = next_file_number(_file_name,
                                     _file_count_max_digits,
                                     _file_count,
                                     errstream);
    if (_current_file == UINT_MAX) {
      return false;
    }
    log_trace(logging)("Existing log file found, saving it as '%s.%0*u'",
                       _file_name, _file_count_max_digits, _current_file);
    archive();
    increment_file_count();
  }

  _stream = os::fopen(_file_name, FileOpenMode);
  if (_stream == nullptr) {
    errstream->print_cr("Error opening log file '%s': %s",
                        _file_name, os::strerror(errno));
    return false;
  }

  if (_file_count == 0 && is_regular_file(_file_name)) {
    log_trace(logging)("Truncating log file");
    os::ftruncate(os::get_fileno(_stream), 0);
  }

  return true;
}

class RotationLocker : public StackObj {
  Semaphore& _sem;

 public:
  RotationLocker(Semaphore& sem) : _sem(sem) {
    sem.wait();
  }

  ~RotationLocker() {
    _sem.signal();
  }
};

int LogFileOutput::write_blocking(const LogDecorations& decorations, const char* msg) {
  RotationLocker lock(_rotation_semaphore);
  if (_stream == nullptr) {
    // An error has occurred with this output, avoid writing to it.
    return 0;
  }

  int written = write_internal(decorations, msg);
  // Need to flush to the filesystem before should_rotate()
  written = flush() ? written : -1;
  if (written > 0) {
    _current_size += written;

    if (should_rotate()) {
      rotate();
    }
  }

  return written;
}

int LogFileOutput::write(const LogDecorations& decorations, const char* msg) {
  if (_stream == nullptr) {
    // An error has occurred with this output, avoid writing to it.
    return 0;
  }

  AsyncLogWriter* aio_writer = AsyncLogWriter::instance();
  if (aio_writer != nullptr) {
    aio_writer->enqueue(*this, decorations, msg);
    return 0;
  }

  return write_blocking(decorations, msg);
}

int LogFileOutput::write(LogMessageBuffer::Iterator msg_iterator) {
  if (_stream == nullptr) {
    // An error has occurred with this output, avoid writing to it.
    return 0;
  }

  AsyncLogWriter* aio_writer = AsyncLogWriter::instance();
  if (aio_writer != nullptr) {
    aio_writer->enqueue(*this, msg_iterator);
    return 0;
  }

  RotationLocker lock(_rotation_semaphore);
  int written = LogFileStreamOutput::write(msg_iterator);
  if (written > 0) {
    _current_size += written;

    if (should_rotate()) {
      rotate();
    }
  }

  return written;
}

void LogFileOutput::archive() {
  assert(_archive_name != nullptr && _archive_name_len > 0, "Rotation must be configured before using this function.");
  int ret = jio_snprintf(_archive_name, _archive_name_len, "%s.%0*u",
                         _file_name, _file_count_max_digits, _current_file);
  assert(ret >= 0, "Buffer should always be large enough");

  // Attempt to remove possibly existing archived log file before we rename.
  // Don't care if it fails, we really only care about the rename that follows.
  remove(_archive_name);

  // Rename the file from ex hotspot.log to hotspot.log.2
  if (rename(_file_name, _archive_name) == -1) {
    jio_fprintf(defaultStream::error_stream(), "Could not rename log file '%s' to '%s' (%s).\n",
                _file_name, _archive_name, os::strerror(errno));
  }
}

void LogFileOutput::force_rotate() {
  if (_file_count == 0) {
    // Rotation not possible
    return;
  }

  RotationLocker lock(_rotation_semaphore);
  rotate();
}

void LogFileOutput::rotate() {
  if (fclose(_stream)) {
    jio_fprintf(defaultStream::error_stream(), "Error closing file '%s' during log rotation (%s).\n",
                _file_name, os::strerror(errno));
  }

  // Archive the current log file
  archive();

  // Open the active log file using the same stream as before
  _stream = os::fopen(_file_name, FileOpenMode);
  if (_stream == nullptr) {
    jio_fprintf(defaultStream::error_stream(), "Could not reopen file '%s' during log rotation (%s).\n",
                _file_name, os::strerror(errno));
    return;
  }

  // Reset accumulated size, increase current file counter, and check for file count wrap-around.
  _current_size = 0;
  increment_file_count();
}

char* LogFileOutput::make_file_name(const char* file_name,
                                    const char* pid_string,
                                    const char* timestamp_string) {
  char hostname_string[HostnameBufferSize];
  char* result = nullptr;

  // Lets start finding out if we have any %p, %t and/or %hn in the name.
  // We will only replace the first occurrence of any placeholder
  const char* pid = strstr(file_name, PidFilenamePlaceholder);
  const char* timestamp = strstr(file_name, TimestampFilenamePlaceholder);
  const char* hostname = strstr(file_name, HostnameFilenamePlaceholder);

  if (pid == nullptr && timestamp == nullptr && hostname == nullptr) {
    // We found no place-holders, return the simple filename
    return os::strdup_check_oom(file_name, mtLogging);
  }

  // At least one of the place-holders were found in the file_name
  size_t result_len =  strlen(file_name);
  if (pid != nullptr) {
    result_len -= strlen(PidFilenamePlaceholder);
    result_len += strlen(pid_string);
  }
  if (timestamp != nullptr) {
    result_len -= strlen(TimestampFilenamePlaceholder);
    result_len += strlen(timestamp_string);
  }
  if (hostname != nullptr) {
    if (!os::get_host_name(hostname_string, sizeof(hostname_string))) {
      int res = jio_snprintf(hostname_string, sizeof(hostname_string), "unknown-host");
      assert(res > 0, "Hostname buffer too small");
    }
    result_len -= strlen(HostnameFilenamePlaceholder);
    result_len += strlen(hostname_string);
  }
  // Allocate the new buffer, size it to hold all we want to put in there +1.
  result = NEW_C_HEAP_ARRAY(char, result_len + 1, mtLogging);

  // Assemble the strings
  size_t file_name_pos = 0;
  size_t i = 0;
  while (i < result_len) {
    if (file_name[file_name_pos] == '%') {
      // Replace the first occurrence of any placeholder
      if (pid != nullptr && strncmp(&file_name[file_name_pos],
                                    PidFilenamePlaceholder,
                                    strlen(PidFilenamePlaceholder)) == 0) {
        strcpy(result + i, pid_string);
        i += strlen(pid_string);
        file_name_pos += strlen(PidFilenamePlaceholder);
        pid = nullptr;
        continue;
      }
      if (timestamp != nullptr && strncmp(&file_name[file_name_pos],
                                          TimestampFilenamePlaceholder,
                                          strlen(TimestampFilenamePlaceholder)) == 0) {
        strcpy(result + i, timestamp_string);
        i += strlen(timestamp_string);
        file_name_pos += strlen(TimestampFilenamePlaceholder);
        timestamp = nullptr;
        continue;
      }
      if (hostname != nullptr && strncmp(&file_name[file_name_pos],
                                         HostnameFilenamePlaceholder,
                                         strlen(HostnameFilenamePlaceholder)) == 0) {
        strcpy(result + i, hostname_string);
        i += strlen(hostname_string);
        file_name_pos += strlen(HostnameFilenamePlaceholder);
        hostname = nullptr;
        continue;
      }
    }
    // Else, copy char by char of the original file
    result[i++] = file_name[file_name_pos++];
  }
  assert(i == result_len, "should be");
  assert(file_name[file_name_pos] == '\0', "should be");

  // Add terminating char
  result[result_len] = '\0';
  return result;
}

void LogFileOutput::describe(outputStream *out) {
  LogFileStreamOutput::describe(out);
  out->print(",filecount=%u,filesize=" SIZE_FORMAT "%s,async=%s", _file_count,
             byte_size_in_proper_unit(_rotate_size),
             proper_unit_for_byte_size(_rotate_size),
             LogConfiguration::is_async_mode() ? "true" : "false");
}
