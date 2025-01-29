/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jvm.h"
#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "utilities/filenameUtil.hpp"

const char* const FilenameUtil::PidFilenamePlaceholder = "%p";
const char* const FilenameUtil::TimestampFilenamePlaceholder = "%t";
const char* const FilenameUtil::TimestampFormat = "%Y-%m-%d_%H-%M-%S";
const char* const FilenameUtil::HostnameFilenamePlaceholder = "%hn";

char* FilenameUtil::make_file_name_impl(const char* file_name, jlong timestamp, bool c_heap, MemTag tag) {
  char* result = nullptr;

  // Lets start finding out if we have any %p, %t and/or %hn in the name.
  // We will only replace the first occurrence of any placeholder
  const char* pid_opt = strstr(file_name, PidFilenamePlaceholder);
  const char* timestamp_opt = strstr(file_name, TimestampFilenamePlaceholder);
  const char* hostname_opt = strstr(file_name, HostnameFilenamePlaceholder);

  int len = strlen(file_name);
  if (pid_opt == nullptr && timestamp_opt == nullptr && hostname_opt == nullptr) {
    // We found no place-holders, return the simple filename
    if (c_heap) {
      return os::strdup_check_oom(file_name, tag);
    } else {
      char* buf = NEW_RESOURCE_ARRAY(char, strlen(file_name) + 1);
      strcpy(buf, file_name);
      return buf;
    }
  }

  char pid_string[PidBufferSize];
  char timestamp_string[StartTimeBufferSize];
  char hostname_string[HostnameBufferSize];

  // At least one of the place-holders were found in the file_name
  size_t result_len =  len;
  if (pid_opt != nullptr) {
    get_pid_string(pid_string, sizeof(pid_string));
    result_len -= strlen(PidFilenamePlaceholder);
    result_len += strlen(pid_string);
  }
  if (timestamp_opt != nullptr) {
    if (timestamp == 0) {
        timestamp = os::javaTimeMillis();
    }
    get_timestamp_string(timestamp_string, sizeof(timestamp_string), timestamp);
    result_len -= strlen(TimestampFilenamePlaceholder);
    result_len += strlen(timestamp_string);
  }
  if (hostname_opt != nullptr) {
    get_hostname_string(hostname_string, sizeof(hostname_string));
    result_len -= strlen(HostnameFilenamePlaceholder);
    result_len += strlen(hostname_string);
  }
  // Allocate the new buffer, size it to hold all we want to put in there +1.
  if (c_heap) {
    result = NEW_C_HEAP_ARRAY(char, result_len + 1, tag);
  } else {
    result = NEW_RESOURCE_ARRAY(char, result_len + 1);
  }
  // Assemble the strings
  size_t file_name_pos = 0;
  size_t i = 0;
  while (i < result_len) {
    if (file_name[file_name_pos] == '%') {
      // Replace the first occurrence of any placeholder
      if (pid_opt != nullptr && strncmp(&file_name[file_name_pos],
                                        PidFilenamePlaceholder,
                                        strlen(PidFilenamePlaceholder)) == 0) {
        strcpy(result + i, pid_string);
        i += strlen(pid_string);
        file_name_pos += strlen(PidFilenamePlaceholder);
        pid_opt = nullptr;
        continue;
      }
      if (timestamp_opt != nullptr && strncmp(&file_name[file_name_pos],
                                              TimestampFilenamePlaceholder,
                                              strlen(TimestampFilenamePlaceholder)) == 0) {
        strcpy(result + i, timestamp_string);
        i += strlen(timestamp_string);
        file_name_pos += strlen(TimestampFilenamePlaceholder);
        timestamp_opt = nullptr;
        continue;
      }
      if (hostname_opt != nullptr && strncmp(&file_name[file_name_pos],
                                             HostnameFilenamePlaceholder,
                                             strlen(HostnameFilenamePlaceholder)) == 0) {
        strcpy(result + i, hostname_string);
        i += strlen(hostname_string);
        file_name_pos += strlen(HostnameFilenamePlaceholder);
        hostname_opt = nullptr;
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

void FilenameUtil::get_pid_string(char* buf, size_t buf_len) {
  int res = jio_snprintf(buf, sizeof(buf), "%d", os::current_process_id());
  assert(res > 0, "PID buffer too small");
}

void FilenameUtil::get_timestamp_string(char* buf, size_t buf_len, jlong timestamp) {
  struct tm local_time;
  time_t utc_time = timestamp / 1000;
  os::localtime_pd(&utc_time, &local_time);
  int res = (int)strftime(buf, sizeof(buf), TimestampFormat, &local_time);
  assert(res > 0, "VM start time buffer too small.");
}

void FilenameUtil::get_hostname_string(char* buf, size_t buf_len) {
  if (!os::get_host_name(buf, buf_len)) {
    int res = jio_snprintf(buf, buf_len, "unknown-host");
    assert(res > 0, "Hostname buffer too small");
  }
}
