/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CODE_COMPRESSEDSTREAM_HPP
#define SHARE_CODE_COMPRESSEDSTREAM_HPP

#include "memory/allocation.hpp"
#include "utilities/globalJavaValue.hpp"
#include "utilities/unsigned5.hpp"

// Simple interface for filing out and filing in basic types
// Used for writing out and reading in debugging information.

class CompressedStream : public ResourceObj {
  friend class VMStructs;
 protected:
  u_char* _buffer;
  int     _position;

 public:
  CompressedStream(u_char* buffer, int position = 0) {
    _buffer   = buffer;
    _position = position;
  }

  u_char* buffer() const               { return _buffer; }

  // Positioning
  int position() const                 { return _position; }
  void set_position(int position)      { _position = position; }
};


class CompressedReadStream : public CompressedStream {
 private:
  inline u_char read()                 { return _buffer[_position++]; }

 public:
  CompressedReadStream(u_char* buffer, int position = 0)
  : CompressedStream(buffer, position) {}

  jboolean read_bool()                 { return (jboolean) read();      }
  jbyte    read_byte()                 { return (jbyte   ) read();      }
  jchar    read_char()                 { return (jchar   ) read_int();  }
  jshort   read_short()                { return (jshort  ) read_signed_int(); }
  jint     read_signed_int();
  jfloat   read_float();               // jfloat_cast(reverse_bits(read_int()))
  jdouble  read_double();              // jdouble_cast(2*reverse_bits(read_int))
  jlong    read_long();                // jlong_from(2*read_signed_int())

  jint     read_int() {
    return UNSIGNED5::read_uint(_buffer, _position, 0);
  }
};


class CompressedWriteStream : public CompressedStream {
 private:
  bool full() {
    return _position >= _size;
  }
  void store(u_char b) {
    _buffer[_position++] = b;
  }
  void write(u_char b) {
    if (full()) grow();
    store(b);
  }
  void grow();

 protected:
  int _size;

 public:
  CompressedWriteStream(int initial_size);
  CompressedWriteStream(u_char* buffer, int initial_size, int position = 0)
  : CompressedStream(buffer, position) { _size = initial_size; }

  void write_bool(jboolean value)      { write(value);      }
  void write_byte(jbyte value)         { write(value);      }
  void write_char(jchar value)         { write_int(value); }
  void write_short(jshort value)       { write_signed_int(value);  }
  void write_signed_int(jint value)    { write_int(UNSIGNED5::encode_sign(value)); }
  void write_float(jfloat value);      // write_int(reverse_bits(jint_cast(v)))
  void write_double(jdouble value);    // write_int(reverse_bits(<low,high>))
  void write_long(jlong value);        // write_signed_int(<low,high>)

  void write_int(juint value) {
    UNSIGNED5::write_uint_grow(value, _buffer, _position, _size,
                               [&](int){ grow(); });
  }
};

#endif // SHARE_CODE_COMPRESSEDSTREAM_HPP
