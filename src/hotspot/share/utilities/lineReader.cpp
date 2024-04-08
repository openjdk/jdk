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

#include "precompiled.hpp"
#include "runtime/os.hpp"
#include "utilities/lineReader.hpp"

LineReader::LineReader() {
  _file = nullptr;
  _is_oom = false;
  _buffer = nullptr;
  _buffer_len = 0;
}

LineReader::~LineReader() {
  if (_buffer != nullptr) {
    os::free(_buffer);
  }
}

void LineReader::init(FILE* file) {
  _file = file;
  _buffer_len = 16; // start at small size to test expansion logic
  _buffer = (char*)os::malloc(_buffer_len, mtClass);
  if (_buffer == nullptr) {
    _is_oom = true;
  }
}

char* LineReader::read_line() {
  if (_is_oom) {
    return nullptr;
  }

  int line_len = 0; // the number of characters we have read so far (excluding the trailing \0)
  while (true) {
    assert(line_len < _buffer_len, "sanity");
    int free_space = _buffer_len - line_len;
    char* p = _buffer + line_len;
    int new_len = 0;
    if (fgets(p, free_space, _file) == nullptr) {
      // _file is at EOF
      if (line_len == 0) {
        return nullptr; // EOF
      } else {
        // We have read something in previous loop iteration(s). Return that.
        // The next call to read_line() will return nullptr to indicate EOF.
        return _buffer;
      }
    }

    // fgets() reads at most free_space characters, including the trailing \0, so
    // strlen(p) must be smaller than INT_MAX, and can be safely cast to int.
    assert(strlen(p) < INT_MAX, "sanity");
    new_len = (int)strlen(p);

    // _buffer_len will stop at INT_MAX, so we will never be able to read more than
    // INT_MAX chars for a single input line.
    assert(line_len >= 0 && new_len >= 0 && (line_len + new_len) >= 0, "no int overflow");

    line_len += new_len; // We have read line_len chars so far.

    assert(line_len < _buffer_len, "sanity");
    assert(_buffer[line_len] == '\0', "sanity");

    if (_buffer[line_len - 1] == '\n' || feof(_file)) {
      // We have read an entire line, or reached EOF
      return _buffer;
    }

    if (line_len == _buffer_len - 1) {
      // The buffer is not big enough to hold the entire input line. Expand it.
      if (_buffer_len == INT_MAX) {
        _is_oom = true; // cannot expand anymore.
        return nullptr;
      }
      int new_len = _buffer_len * 2;
      if (new_len < _buffer_len) { // overflows int
        new_len = INT_MAX;
      }
      assert(new_len > _buffer_len, "must be");

      char* new_buffer = (char*)os::realloc(_buffer, new_len, mtClass);
      if (new_buffer == nullptr) {
        _is_oom = true; // oom
        return nullptr;
      } else {
        _buffer = new_buffer;
        _buffer_len = new_len;
      }
    }
  }
}
