/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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

// Compress stream implementation over the Unsigned5 compression algorithm
// mixed with Zero-Run-Length-Encoding (variation of RLE compression schema).
// unsigned5(int) takes 1..5 bytes, and zero byte is never produced (see
// unsigned5 description). Once we see a zero byte, it is a special mark
// followed by byte representing number of zero values in a stream.
class CompressedReadStream : public CompressedStream {
 private:
  u_char _remaining_zeroes;
  inline u_char read()                 { _remaining_zeroes = 0; return _buffer[_position++]; }

 public:
  CompressedReadStream(u_char* buffer, int position = 0)
  : CompressedStream(buffer, position), _remaining_zeroes(0) {}

  jboolean read_bool()                 { return (jboolean) read();      }
  jbyte    read_byte()                 { return (jbyte   ) read();      }
  jchar    read_char()                 { return (jchar   ) read_int();  }
  jshort   read_short()                { return (jshort  ) read_signed_int(); }
  jint     read_signed_int();
  jfloat   read_float();               // jfloat_cast(reverse_bits(read_int()))
  jdouble  read_double();              // jdouble_cast(2*reverse_bits(read_int))
  jlong    read_long();                // jlong_from(2*read_signed_int())

  jint     read_int() {
    if (_remaining_zeroes > 0) {
      _remaining_zeroes--;
      return 0;
    }
    if (_buffer[_position] == 0) {
      _position++;
      _remaining_zeroes = _buffer[_position++];
      assert(_remaining_zeroes > 0, "corrupted stream");
      _remaining_zeroes--;
      return 0;
    }
    return UNSIGNED5::read_uint(_buffer, _position, 0);
  }

  void set_position(int position) {
    _position = position;
    _remaining_zeroes = 0;
  }
};


class CompressedWriteStream : public CompressedStream {
 private:
  bool full() {
    return _position >= _size;
  }
  void store(u_char b) {
    _buffer[_position++] = b;
    // Writing unencoded data ends the RLE sequence of zero integers
    _zero_count = 0;
  }
  void write(u_char b) {
    if (full()) grow();
    store(b);
  }
  void grow();

  // Handle encoding of subsequent zeroes. Return true if
  // input value is completely handled and no unsigned5 encoding required
  bool handle_zero(juint value) {
    if (value == 0) {
      if (_zero_count == 0xFF) { // biggest zero chain length is 255
        _zero_count = 1;
        // for now, write it as an ordinary value (UNSINGED5 encodes zero int as a single byte)
        // the new zero sequence is started if there are more than two zero values in a raw
        return false;
      }
      if (++_zero_count > 2) {
        _buffer[_position - 2] = 0;
        _buffer[_position - 1] = _zero_count;
        return true;
      }
    } else { // value != 0
      _zero_count = 0;
    }
    return false;
  }
 protected:
  int _size;
  u_char _zero_count;

 public:
  CompressedWriteStream(int initial_size);
  CompressedWriteStream(u_char* buffer, int initial_size, int position = 0)
  : CompressedStream(buffer, position) { _size = initial_size; _zero_count = 0; }

  void write_bool(jboolean value)      { write(value);      }
  void write_byte(jbyte value)         { write(value);      }
  void write_char(jchar value)         { write_int(value); }
  void write_short(jshort value)       { write_signed_int(value);  }
  void write_signed_int(jint value)    { write_int(UNSIGNED5::encode_sign(value)); }
  void write_float(jfloat value);      // write_int(reverse_bits(jint_cast(v)))
  void write_double(jdouble value);    // write_int(reverse_bits(<low,high>))
  void write_long(jlong value);        // write_signed_int(<low,high>)

  void write_int(juint value) {
    if (handle_zero(value)) return;
    UNSIGNED5::write_uint_grow(value, _buffer, _position, _size,
                               [&](int){ grow(); });
  }

  int position() { _zero_count = 0; return _position; }
};

#endif // SHARE_CODE_COMPRESSEDSTREAM_HPP
