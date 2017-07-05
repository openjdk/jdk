/*
 * Copyright (c) 1997, 2005, Oracle and/or its affiliates. All rights reserved.
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

// Input stream for reading .class file
//
// The entire input stream is present in a buffer allocated by the caller.
// The caller is responsible for deallocating the buffer and for using
// ResourceMarks appropriately when constructing streams.

class ClassFileStream: public ResourceObj {
 private:
  u1*   _buffer_start; // Buffer bottom
  u1*   _buffer_end;   // Buffer top (one past last element)
  u1*   _current;      // Current buffer position
  char* _source;       // Source of stream (directory name, ZIP/JAR archive name)
  bool  _need_verify;  // True if verification is on for the class file

  void truncated_file_error(TRAPS);
 public:
  // Constructor
  ClassFileStream(u1* buffer, int length, char* source);

  // Buffer access
  u1* buffer() const           { return _buffer_start; }
  int length() const           { return _buffer_end - _buffer_start; }
  u1* current() const          { return _current; }
  void set_current(u1* pos)    { _current = pos; }
  char* source() const         { return _source; }
  void set_verify(bool flag)   { _need_verify = flag; }

  void check_truncated_file(bool b, TRAPS) {
    if (b) {
      truncated_file_error(THREAD);
    }
  }

  void guarantee_more(int size, TRAPS) {
    size_t remaining = (size_t)(_buffer_end - _current);
    unsigned int usize = (unsigned int)size;
    check_truncated_file(usize > remaining, CHECK);
  }

  // Read u1 from stream
  u1 get_u1(TRAPS);
  u1 get_u1_fast() {
    return *_current++;
  }

  // Read u2 from stream
  u2 get_u2(TRAPS);
  u2 get_u2_fast() {
    u2 res = Bytes::get_Java_u2(_current);
    _current += 2;
    return res;
  }

  // Read u4 from stream
  u4 get_u4(TRAPS);
  u4 get_u4_fast() {
    u4 res = Bytes::get_Java_u4(_current);
    _current += 4;
    return res;
  }

  // Read u8 from stream
  u8 get_u8(TRAPS);
  u8 get_u8_fast() {
    u8 res = Bytes::get_Java_u8(_current);
    _current += 8;
    return res;
  }

  // Get direct pointer into stream at current position.
  // Returns NULL if length elements are not remaining. The caller is
  // responsible for calling skip below if buffer contents is used.
  u1* get_u1_buffer() {
    return _current;
  }

  u2* get_u2_buffer() {
    return (u2*) _current;
  }

  // Skip length u1 or u2 elements from stream
  void skip_u1(int length, TRAPS);
  void skip_u1_fast(int length) {
    _current += length;
  }

  void skip_u2(int length, TRAPS);
  void skip_u2_fast(int length) {
    _current += 2 * length;
  }

  // Tells whether eos is reached
  bool at_eos() const          { return _current == _buffer_end; }
};
