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

#ifndef SHARE_UTILITIES_FILENAMEUTIL_HPP
#define SHARE_UTILITIES_FILENAMEUTIL_HPP

class FilenameUtil : AllStatic {
private:
  static const char* const PidFilenamePlaceholder;
  static const char* const TimestampFilenamePlaceholder;
  static const char* const TimestampFormat;
  static const char* const HostnameFilenamePlaceholder;

  static const size_t StartTimeBufferSize = 20;
  static const size_t PidBufferSize = 21;
  static const size_t HostnameBufferSize = 512;

  static jlong _start_time;

public:
  static void set_start_time(jlong start_time) {
    _start_time = start_time;
  }

  // Caller provides ResourceMark
  static char* make_file_name(const char* file_name);

private:
  // Caller provides ResourceMark
  static char* get_pid_string();
  static char* get_timestamp_string();
  static char* get_hostname_string();
}

#endif // SHARE_UTILITIES_FILENAMEUTIL_HPP
