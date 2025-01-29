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

public:
  // The call returns file name allocated on c heap or resource area.
  // C_HEAP = true, caller is responsible for releasing returned string
  // C_HEAP = false, caller is responsible for setting up ResourceMark
  template<bool C_HEAP, MemTag MT = mtNone>
  static char* make_file_name(const char* file_name, jlong timestamp = 0) {
    return make_file_name_impl(file_name, timestamp, C_HEAP, MT);
  }
private:
  static char* make_file_name_impl(const char* file_name, jlong timestamp, bool c_heap, MemTag tag);
  static void get_pid_string(char* buf, size_t buf_len);
  static void get_timestamp_string(char* buf, size_t buf_len, jlong timestamp);
  static void get_hostname_string(char* buf, size_t buf_len);
};

#endif // SHARE_UTILITIES_FILENAMEUTIL_HPP
