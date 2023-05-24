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

#include "precompiled.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/lineReader.hpp"

#include <errno.h>

LineReader::LineReader(const char* filename, size_t initial_length) :
  _filename(os::strdup(filename)), _stream(nullptr), _last_errno(0),
  _buffer_length(initial_length), _buffer(nullptr)
{
  // Use os::open() because neither fopen() nor os::fopen()
  // can handle long path name on Windows. HMM, is this still valid today???
  int fd = os::open(_filename, O_RDONLY, S_IREAD);
  if (fd == -1) {
    _last_errno = errno;
  } else {
    // Obtain a File* from the file descriptor so that getc()
    // can be used in get_line().
    _stream = os::fdopen(fd, "r");
    if (_stream == nullptr) {
      _last_errno = errno;
      ::close(fd);
    } else {
      // fd will be closed by fclose(_stream)
      _buffer = NEW_RESOURCE_ARRAY(char, _buffer_length);
    }
  }
}

LineReader::~LineReader() {
  close(); // Just in case
  FreeHeap(_filename);
}

void LineReader::close() {
  if (_stream != nullptr) {
    fclose(_stream);
    _stream = nullptr;
  }
}

// Returns nullptr if we have reached EOF.
// \n is treated as the line separator.
// All occurrences of \r are stripped.
char* LineReader::get_line() {
  if (_stream == nullptr) {
    return nullptr;
  }
  size_t buffer_pos = 0;
  int c;
  while ((c = getc(_stream)) != EOF) {
    if (buffer_pos + 1 >= _buffer_length) {
      size_t new_length = _buffer_length * 2;
      if (new_length < _buffer_length) {
        // This could happen on 32-bit. On 64-bit, the VM would have exited
        // due to OOM before we ever get to here.
        fatal("Cannot handle excessively long lines");
      }
      _buffer = REALLOC_RESOURCE_ARRAY(char, _buffer, _buffer_length, new_length);
      assert(_buffer != nullptr, "OOM would have exited JVM");
      _buffer_length = new_length;
    }
    if (c == '\n') {
      break;
    } else if (c == '\r') {
      // skip LF
    } else {
      _buffer[buffer_pos++] = c;
    }
  }

  // null terminate it, reset the pointer
  _buffer[buffer_pos] = '\0'; // NL or EOF

  if (buffer_pos == 0 && c == EOF) {
    int n = errno;
    _last_errno = n;
    close();
    return nullptr;
  } else {
    // If we have read an empty line: _buffer[0] == '\0'
    return _buffer;
  }
}
