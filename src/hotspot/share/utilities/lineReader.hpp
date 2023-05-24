/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

// A simple class to read lines of arbitrary length from _filename.
// The _buffer is resource-allocated, so LineReader must be used within
// a ResourceMark.
class LineReader : public StackObj {
  char* _filename;
  FILE* _stream;
  int _errno;
  size_t _buffer_length;
  char* _buffer;
public:
  LineReader(const char* filename, size_t initial_length = 160);
  ~LineReader();

  bool is_open() const {
    return _stream != nullptr;
  }
  const char* filename() const { return _filename; }
  char* get_line();
  void close();

  // errno, if any, for the last file I/O operation performed by this LineReader
  int last_errno() {
    return _errno;
  }
};

#endif // SHARE_UTILITIES_LINEREADER_HPP
