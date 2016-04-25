/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_CLASSFILESTREAM_HPP
#define SHARE_VM_CLASSFILE_CLASSFILESTREAM_HPP

#include "memory/allocation.hpp"
#include "utilities/bytes.hpp"
#include "utilities/exceptions.hpp"

// Input stream for reading .class file
//
// The entire input stream is present in a buffer allocated by the caller.
// The caller is responsible for deallocating the buffer and for using
// ResourceMarks appropriately when constructing streams.

class ClassPathEntry;

class ClassFileStream: public ResourceObj {
 private:
  const u1* const _buffer_start; // Buffer bottom
  const u1* const _buffer_end;   // Buffer top (one past last element)
  mutable const u1* _current;    // Current buffer position
  const char* const _source;     // Source of stream (directory name, ZIP/JAR archive name)
  bool _need_verify;             // True if verification is on for the class file

  void truncated_file_error(TRAPS) const ;

 protected:
  const u1* clone_buffer() const;
  const char* const clone_source() const;

 public:
  static const bool no_verification;
  static const bool verify;

  ClassFileStream(const u1* buffer,
                  int length,
                  const char* source,
                  bool verify_stream = verify); // to be verified by default

  virtual const ClassFileStream* clone() const;

  // Buffer access
  const u1* buffer() const { return _buffer_start; }
  int length() const { return _buffer_end - _buffer_start; }
  const u1* current() const { return _current; }
  void set_current(const u1* pos) const {
    assert(pos >= _buffer_start && pos <= _buffer_end, "invariant");
    _current = pos;
  }

  // for relative positioning
  juint current_offset() const {
    return (juint)(_current - _buffer_start);
  }
  const char* source() const { return _source; }
  bool need_verify() const { return _need_verify; }
  void set_verify(bool flag) { _need_verify = flag; }

  void check_truncated_file(bool b, TRAPS) const {
    if (b) {
      truncated_file_error(THREAD);
    }
  }

  void guarantee_more(int size, TRAPS) const {
    size_t remaining = (size_t)(_buffer_end - _current);
    unsigned int usize = (unsigned int)size;
    check_truncated_file(usize > remaining, CHECK);
  }

  // Read u1 from stream
  u1 get_u1(TRAPS) const;
  u1 get_u1_fast() const {
    return *_current++;
  }

  // Read u2 from stream
  u2 get_u2(TRAPS) const;
  u2 get_u2_fast() const {
    u2 res = Bytes::get_Java_u2((address)_current);
    _current += 2;
    return res;
  }

  // Read u4 from stream
  u4 get_u4(TRAPS) const;
  u4 get_u4_fast() const {
    u4 res = Bytes::get_Java_u4((address)_current);
    _current += 4;
    return res;
  }

  // Read u8 from stream
  u8 get_u8(TRAPS) const;
  u8 get_u8_fast() const {
    u8 res = Bytes::get_Java_u8((address)_current);
    _current += 8;
    return res;
  }

  // Get direct pointer into stream at current position.
  // Returns NULL if length elements are not remaining. The caller is
  // responsible for calling skip below if buffer contents is used.
  const u1* get_u1_buffer() const {
    return _current;
  }

  const u2* get_u2_buffer() const {
    return (const u2*) _current;
  }

  // Skip length u1 or u2 elements from stream
  void skip_u1(int length, TRAPS) const;
  void skip_u1_fast(int length) const {
    _current += length;
  }

  void skip_u2(int length, TRAPS) const;
  void skip_u2_fast(int length) const {
    _current += 2 * length;
  }

  void skip_u4(int length, TRAPS) const;
  void skip_u4_fast(int length) const {
    _current += 4 * length;
  }

  // Tells whether eos is reached
  bool at_eos() const { return _current == _buffer_end; }
};

#endif // SHARE_VM_CLASSFILE_CLASSFILESTREAM_HPP
