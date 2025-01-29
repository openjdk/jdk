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

#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "utilities/filenameUtil.hpp"

const char* const FilenameUtil::PidFilenamePlaceholder = "%p";
const char* const FilenameUtil::TimestampFilenamePlaceholder = "%t";
const char* const FilenameUtil::TimestampFormat = "%Y-%m-%d_%H-%M-%S";
const char* const FilenameUtil::HostnameFilenamePlaceholder = "%hn";

jlong FilenameUtil::_start_time = 0;

char* FilenameUtil::make_file_name(const char* file_name) {
  char hostname_string[HostnameBufferSize];
  char* result = nullptr;

  // Lets start finding out if we have any %p, %t and/or %hn in the name.
  // We will only replace the first occurrence of any placeholder
  const char* pid = strstr(file_name, PidFilenamePlaceholder);
  const char* timestamp = strstr(file_name, TimestampFilenamePlaceholder);
  const char* hostname = strstr(file_name, HostnameFilenamePlaceholder);

  int len = strlen(file_name);
  if (pid == nullptr && timestamp == nullptr && hostname == nullptr) {
    // We found no place-holders, return the simple filename
    char* buf = NEW_RESOURCE_ARRAY(char, len + 1);
    strcpy(buf, file_name);
    return buf;
  }

  char* pid_string = nullptr;
  char* timestamp_string = nullptr;
  char* hostname_string = nullptr;

  // At least one of the place-holders were found in the file_name
  size_t result_len =  len;
  if (pid != nullptr) {
    pid_string = get_pid_string();
    result_len -= strlen(PidFilenamePlaceholder);
    result_len += strlen(pid_string);
  }
  if (timestamp != nullptr) {
    timestamp_string = get_hostname_string();
    result_len -= strlen(TimestampFilenamePlaceholder);
    result_len += strlen(timestamp_string);
  }
  if (hostname != nullptr) {
    hostname_string = get_hostname_string();
    result_len -= strlen(HostnameFilenamePlaceholder);
    result_len += strlen(hostname_string);
  }
  // Allocate the new buffer, size it to hold all we want to put in there +1.
  result = NEW_RESOURCE_ARRAY(char, result_len + 1);

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

char* FilenameUtil::get_pid_string() {
  char* buf = NEW_RESOURCE_ARRAY(char, PidBufferSize);
  int res = jio_snprintf(_pid_str, sizeof(buf), "%d", os::current_process_id());
  assert(res > 0, "PID buffer too small");
  return buf;
}

char* FilenameUtil::get_timestamp_string() {
  char* buf = NEW_RESOURCE_ARRAY(char, StartTimeBufferSize);
  struct tm local_time;
  time_t utc_time = _start_time / 1000;
  os::localtime_pd(&utc_time, &local_time);
  res = (int)strftime(_vm_start_time_str, sizeof(buf), TimestampFormat, &local_time);
  assert(res > 0, "VM start time buffer too small.");
  return buf;
}

char* FilenameUtil::get_hostname_string() {
  char* buf = NEW_RESOURCE_ARRAY(char, HostnameBufferSize);
  if (!os::get_host_name(buf, HostnameBufferSize)) {
    int res = jio_snprintf(buf, HostnameBufferSize, "unknown-host");
    assert(res > 0, "Hostname buffer too small");
  }
  return buf;
}
