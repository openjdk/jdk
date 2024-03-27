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
#include "utilities/unsigned5.inline.hpp"

// Simple interface for filing out and filing in basic types
// Used for writing out and reading in debugging information.
// It uses the UNSIGNED5 type uniformly.

class CompressedStream : public ResourceObj {
  friend class VMStructs;
 protected:
#ifdef ASSERT
  size_t _reported_data_size;
#endif //ASSERT

  CompressedStream() {
#ifdef ASSERT
    _reported_data_size = (size_t)-1;
#endif //ASSERT
  }

  size_t report_data_size(size_t data_size) {
    assert(_reported_data_size == (size_t)-1 || _reported_data_size == data_size,
           "data size reported twice with intervening side effects");
    DEBUG_ONLY(_reported_data_size = data_size);
    return data_size;
  }

  static bool in_bounds(size_t position, size_t limit,
                        bool inclusive_end = false) {
    // This is only useful for assertions, since not all streams
    // have known limit pointers.
    return (limit == 0 || (position >= 0 &&
                           (inclusive_end
                            ? position <= limit
                            : position <  limit)));
  }

 public:
  /// Canned routines for handling float/long/double.

  // write_int(reverse_bits(jint_cast(v)))
  static juint encode_float(jfloat value);
  // write_int(reverse_bits(<low,high>))
  static void encode_double(jdouble value, juint& first, juint& second);
  // write_signed_int(<low,high>)
  static void encode_long(jlong value, juint& first, juint& second);

  // jfloat_cast(reverse_bits(read_int()))
  static jfloat decode_float(juint encoding);
  // jdouble_cast(2*reverse_bits(read_int))
  static jdouble decode_double(juint first, juint second);
  // jlong_from(2*read_signed_int())
  static jlong decode_long(juint first, juint second);
};

/// This intermediate read/write layer deals with only unsigned ints.

#define DO_CZ DO_CZ

class CompressedIntReadStream : public CompressedStream {
#ifdef DO_CZ
  using Reader = ZeroSuppressingU5::ZSReader<u_char*,size_t>;
#else
  using Reader = UNSIGNED5::Reader<u_char*,size_t>;
#endif
  Reader _r;

 protected:
  size_t position() {
    return _r.position();
  }
  void reset() {
    reset_at(0);
  }
  void reset_at(size_t pos) {
#ifdef DO_CZ
    _r.reset_at_position(pos);
#else
    _r.set_position(pos);
#endif
  }

 public:
  CompressedIntReadStream(u_char* buffer, size_t limit = 0) {
    setup(buffer, limit);
  }

  CompressedIntReadStream(u_char* buffer, size_t limit, bool suppress_zeroes) {
    setup(buffer, limit, suppress_zeroes);
  }

  void setup(address initial_buffer, size_t initial_size,
             bool suppress_zeroes = false);

  juint read_int() {
    return _r.next_uint();
  }

  juint read_int_pair(int first_width, juint* second) {
    juint first = 0;
    _r.next_uint_pair(first_width, first, *second);
    return first;
  }

  size_t data_size() {
    return report_data_size(_r.position());
  }

  // Use this predicate only if the stream is known to use end-bytes.
  // Simpler streams do not have end-bytes because their size is known
  // by consulting external information.
  bool has_next() {
    return _r.has_next();
  }

  bool at_start() {
#ifdef DO_CZ
    return _r.at_start();
#else
    return _r.position() == 0;
#endif
  }
};

class CompressedIntWriteStream : public CompressedStream {
#ifdef DO_CZ
  using Writer = ZeroSuppressingU5::ZSWriter<u_char*,size_t>;
  using Checkpoint = ZeroSuppressingU5::ZSWriterCheckpoint<size_t,Writer>;
#else
  using Writer = UNSIGNED5::Writer<u_char*,size_t>;
  using Checkpoint = size_t;
#endif
  Writer _w;
  Checkpoint _w_checkpoint;

  void grow();

  void reset() {
    _w.reset();
  }

 public:
  CompressedIntWriteStream(size_t initial_size) {
    setup(nullptr, initial_size);
  }
  CompressedIntWriteStream(address initial_buffer, size_t initial_size,
                           bool suppress_zeroes = false) {
    setup(initial_buffer, initial_size, suppress_zeroes);
  }
  // If you use this one, make a call to setup before storing any data:
  CompressedIntWriteStream() {
  }
  void setup(address initial_buffer, size_t initial_size,
             bool suppress_zeroes = false);

  bool at_start() {
#ifdef DO_CZ
    return _w.at_start();
#else
    return _w.position() == 0;
#endif
  }

  void write_int(juint value) {
    _w.accept_uint_grow(value, [&](int){ grow(); });
  }

  void write_int_pair(int first_width, juint first, juint second) {
    _w.accept_uint_pair_grow(first_width, first, second, [&](int){ grow(); });
  }

  // Flush the compressor and get ready for another run of data.
  // Return the offset where a reader can start reading the data.
  size_t checkpoint();

  // After making a checkpoint and then writing some data, flush the
  // compressor and return the number of bytes output.
  size_t data_size_after_checkpoint(size_t checkpoint_pos);

  // Undo all writes since a previous checkpoint.
  void restore(size_t checkpoint_pos);

  // Use this method only if the stream reader will need to call
  // has_next to sense the end of a variable sequence of items.
  // Otherwise, just rely on dead reckoning.
  void write_end_byte() {
    _w.accept_end_byte();
  }

  // Add end bytes to round up the size to a given multiple,
  // such as sizeof(HeapWord).
  void round_up_size_to_multiple(size_t size_unit) {
    assert(is_power_of_2(size_unit) && size_unit <= 4*sizeof(HeapWord), "");
    while ((_w.position() & (size_unit-1)) != 0) {
      _w.accept_end_byte();
    }
  }

  u_char* data_address_at(size_t position, size_t length = 0);

  // At end of a successful life cycle, we can copy out the data.
  // But we must not ask for conflicting data sizes.
  // The data_size query must happen at the end, just before copying.
  size_t data_size() {
#ifdef DO_CZ
    _w.flush();
#endif
    return report_data_size(_w.position());
  }

  void copy_bytes_to(address data, size_t data_size, UNSIGNED5::Statistics::Kind kind) {
    assert(this->data_size() == data_size, "incorrect data size argument");
    memcpy(data, _w.array(), data_size);
    _w.collect_stats(kind);
  }
};

/// This layer supports all the types.

class CompressedReadStream : public CompressedIntReadStream {
 public:
  template<typename... Arg>
  CompressedReadStream(Arg... arg)
    : CompressedIntReadStream(arg...)
  { }

  jboolean read_bool()                 { return (jboolean) read_int();  }
  jbyte    read_byte()                 { return (jbyte   ) read_int();  }
  jchar    read_char()                 { return (jchar   ) read_int();  }
  jshort   read_short()                { return (jshort  ) read_signed_int(); }
  jint     read_signed_int()           { return UNSIGNED5::decode_sign(read_int()); }
  jint     read_signed_int(int sign_bits) {
    juint x = read_int();
    return UNSIGNED5::decode_multi_sign(sign_bits, x);
  }
  jfloat   read_float() {
    int x = read_int();
    return decode_float(x);
  }
  jdouble  read_double() {
    int x = read_int();
    int y = read_int();
    return decode_double(x, y);
  }
  jlong    read_long() {
    int x = read_int();
    int y = read_int();
    return decode_long(x, y);
  }
};

class CompressedWriteStream : public CompressedIntWriteStream {
 public:
  template<typename... Arg>
  CompressedWriteStream(Arg... arg)
    : CompressedIntWriteStream(arg...)
  { }

  void write_byte(jbyte value)         { write_int(value & 0xFF); }
  void write_bool(jboolean value)      { write_int(value ? 1 : 0); }
  void write_char(jchar value)         { write_int(value); }
  void write_short(jshort value)       { write_signed_int(value);  }
  void write_signed_int(jint value)    { write_int(UNSIGNED5::encode_sign(value)); }
  void write_signed_int(jint value, int sign_bits) {
    juint x = UNSIGNED5::encode_multi_sign(sign_bits, value);
    write_int(x);
  }
  void write_float(jfloat value) {
    juint x = encode_float(value);
    write_int(x);
  }
  void write_double(jdouble value) {
    juint x, y;
    encode_double(value, x, y);
    write_int(x);
    write_int(y);
  }
  void write_long(jlong value) {
    juint x, y;
    encode_long(value, x, y);
    write_int(x);
    write_int(y);
  }
};

#endif // SHARE_CODE_COMPRESSEDSTREAM_HPP
