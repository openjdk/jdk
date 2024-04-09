/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_LINEREADER_HPP
#define SHARE_UTILITIES_LINEREADER_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

// This class is a wrapper around fgets() for reading *reasonably long*
// text lines (up to LineReader::MAX_LEN-1 chars) from a FILE*.
//
// MAX_LEN is currently 4M. This should be enough for any practical use
// of text-based input files for HotSpot. Don't use LineReader if it's
// possible for valid lines to be longer than this limit.
class LineReader : public StackObj {
  char* _buffer;       // The buffer that holds the value returned by read_line().
  int   _buffer_len;   // Max characters that can be stored in _line, including the trailing \0;
  FILE* _file;
  bool  _is_oom;
public:
  static const int MAX_LEN = 4 * 1024 * 1024;
  LineReader(FILE* file);
  LineReader();
  void init(FILE* file);
  ~LineReader();

  // Out of memory. See comments below.
  bool is_oom() {
    return _is_oom;
  }

  // Return one line from _file, as a 0-terminated string. The length and contents of this
  // string are the same as those returned by a call to fgets() with a buffer that's
  // MAX_LEN bytes long. (Note: if the file contains a line longer than MAX_LEN-1 chars,
  // we'd break it up in multiple chunks, just as fgets() would).
  //
  // When successful, a non-null value is returned. The caller is free to read or modify this
  // string (up to the terminating \0 character) until the next call to read_line(), or until the
  // LineReader is destructed.
  //
  // nullptr is returned if:
  //   1. os::malloc/os::realloc failed to allocate enough space to accommodate the input line.
  //      When this happens, all subsequent calls to is_oom() will return true, and all
  //      subsequent calls to read_line() will return nullptr;
  //   2. Upon the entry of this function, _file is already at the EOF position.
  char* read_line();
};

#endif // SHARE_UTILITIES_LINEREADER_HPP
