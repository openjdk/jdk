/*
 * Copyright (c) 2021, Amazon.com, Inc. All rights reserved.
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/logConfiguration.hpp"
#include "logging/logFileStreamOutput.hpp"
#include "runtime/arguments.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/perfData.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/formatBuffer.hpp"

#include "gc/shenandoah/shenandoahLogFileOutput.hpp"

const char* const ShenandoahLogFileOutput::Prefix = "file=";
const char* const ShenandoahLogFileOutput::FileOpenMode = "w+";
const char* const ShenandoahLogFileOutput::PidFilenamePlaceholder = "%p";
const char* const ShenandoahLogFileOutput::TimestampFilenamePlaceholder = "%t";
const char* const ShenandoahLogFileOutput::TimestampFormat = "%Y-%m-%d_%H-%M-%S";
char        ShenandoahLogFileOutput::_pid_str[PidBufferSize];
char        ShenandoahLogFileOutput::_vm_start_time_str[StartTimeBufferSize];

#define WRITE_LOG_WITH_RESULT_CHECK(op, total)                \
{                                                             \
  int result = op;                                            \
  if (result < 0) {                                           \
    if (!_write_error_is_shown) {                             \
      jio_fprintf(defaultStream::error_stream(),              \
                  "Could not write log: %s\n", name());       \
      jio_fprintf(_stream, "\nERROR: Could not write log\n"); \
      _write_error_is_shown = true;                           \
      return -1;                                              \
    }                                                         \
  }                                                           \
  total += result;                                            \
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
void ShenandoahLogFileOutput::set_option(uint file_count, size_t rotation_size) {
    if (file_count < MaxRotationFileCount) {
        _file_count = file_count;
    }
    _rotate_size = rotation_size;
}

ShenandoahLogFileOutput::ShenandoahLogFileOutput(const char* name, jlong vm_start_time)
  : _name(os::strdup_check_oom(name, mtLogging)), _file_name(NULL), _archive_name(NULL), _stream(NULL), _current_file(0), _file_count(DefaultFileCount), _is_default_file_count(true), _archive_name_len(0),
     _rotate_size(DefaultFileSize),  _current_size(0), _rotation_semaphore(1) {
  set_file_name_parameters(vm_start_time);
  _file_name = make_file_name(name, _pid_str, _vm_start_time_str);
}

ShenandoahLogFileOutput::~ShenandoahLogFileOutput() {
  if (_stream != NULL) {
    if (fclose(_stream) != 0) {
      jio_fprintf(defaultStream::error_stream(), "Could not close log file '%s' (%s).\n",
                  _file_name, os::strerror(errno));
    }
  }
  os::free(_archive_name);
  os::free(_file_name);
  os::free(const_cast<char*>(_name));
}

bool ShenandoahLogFileOutput::flush() {
  bool result = true;
  if (fflush(_stream) != 0) {
    if (!_write_error_is_shown) {
      jio_fprintf(defaultStream::error_stream(),
                  "Could not flush log: %s (%s (%d))\n", name(), os::strerror(errno), errno);
      jio_fprintf(_stream, "\nERROR: Could not flush log (%d)\n", errno);
      _write_error_is_shown = true;
    }
    result = false;
  }
  return result;
}

void ShenandoahLogFileOutput::initialize(outputStream* errstream) {

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

    if (_file_count > 0 && file_exist) {
        if (!is_regular_file(_file_name)) {
            vm_exit_during_initialization(err_msg("Unable to log to file %s with log file rotation: "
                                                   "%s is not a regular file", _file_name, _file_name));
        }
        _current_file = next_file_number(_file_name,
                                         _file_count_max_digits,
                                         _file_count,
                                         errstream);
        if (_current_file == UINT_MAX) {
            vm_exit_during_initialization("Current file reaches the maximum for integer. Unable to initialize the log output.");
        }
        archive();
        increment_file_count();
    }
    _stream = os::fopen(_file_name, ShenandoahLogFileOutput::FileOpenMode);
    if (_stream == NULL) {
        vm_exit_during_initialization(err_msg("Error opening log file '%s': %s",
                                              _file_name, os::strerror(errno)));
    }
    if (_file_count == 0 && is_regular_file(_file_name)) {
        os::ftruncate(os::get_fileno(_stream), 0);
    }
}

class ShenandoahRotationLocker : public StackObj {
    Semaphore& _sem;

public:
    ShenandoahRotationLocker(Semaphore& sem) : _sem(sem) {
        sem.wait();
    }

    ~ShenandoahRotationLocker() {
        _sem.signal();
    }
};

int ShenandoahLogFileOutput::write_snapshot(PerfLongVariable** regions,
                                            PerfLongVariable* ts,
                                            PerfLongVariable* status,
                                            size_t num_regions,
                                            size_t region_size, size_t protocol_version) {
  if (_stream == NULL) {
      // An error has occurred with this output, avoid writing to it.
      return 0;
  }
  int written = 0;

  FileLocker flocker(_stream);
  WRITE_LOG_WITH_RESULT_CHECK(jio_fprintf(_stream, "%lli %lli %u %u %u\n",
                                          ts->get_value(),
                                          status->get_value(),
                                          num_regions,
                                          region_size, protocol_version), written);
  _current_size += written;
  if (num_regions > 0) {
    WRITE_LOG_WITH_RESULT_CHECK(jio_fprintf(_stream, "%lli", regions[0]->get_value()), written);
    _current_size += written;
  }
  for (uint i = 1; i < num_regions; ++i) {
    WRITE_LOG_WITH_RESULT_CHECK(jio_fprintf(_stream, " %lli", regions[i]->get_value()), written);
    _current_size += written;
  }
  jio_fprintf(_stream, "\n", written);
  _current_size += written;
  written = flush() ? written : -1;
  if (written > 0) {
      _current_size += written;

      if (should_rotate()) {
          rotate();
      }
  }

  return written;
}

void ShenandoahLogFileOutput::archive() {
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
                    _file_name, _archive_name, os::strerror(errno));
    }
}

void ShenandoahLogFileOutput::force_rotate() {
    if (_file_count == 0) {
        // Rotation not possible
        return;
    }

    ShenandoahRotationLocker lock(_rotation_semaphore);
    rotate();
}

void ShenandoahLogFileOutput::rotate() {
    if (fclose(_stream)) {
        jio_fprintf(defaultStream::error_stream(), "Error closing file '%s' during log rotation (%s).\n",
                    _file_name, os::strerror(errno));
    }

    // Archive the current log file
    archive();

    // Open the active log file using the same stream as before
    _stream = os::fopen(_file_name, FileOpenMode);
    if (_stream == NULL) {
        jio_fprintf(defaultStream::error_stream(), "Could not reopen file '%s' during log rotation (%s).\n",
                    _file_name, os::strerror(errno));
        return;
    }

    // Reset accumulated size, increase current file counter, and check for file count wrap-around.
    _current_size = 0;
    increment_file_count();
}

void ShenandoahLogFileOutput::set_file_name_parameters(jlong vm_start_time) {
  int res = jio_snprintf(_pid_str, sizeof(_pid_str), "%d", os::current_process_id());
  assert(res > 0, "PID buffer too small");

  struct tm local_time;
  time_t utc_time = vm_start_time / 1000;
  os::localtime_pd(&utc_time, &local_time);
  res = (int)strftime(_vm_start_time_str, sizeof(_vm_start_time_str), TimestampFormat, &local_time);
  assert(res > 0, "VM start time buffer too small.");
}

char* ShenandoahLogFileOutput::make_file_name(const char* file_name,
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
