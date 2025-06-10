/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_UNSIGNED5_HPP
#define SHARE_UTILITIES_UNSIGNED5_HPP

#include "memory/allStatic.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"

// Low-level interface for [de-]coding compressed uint32_t (u4) values.

// A uint32_t value (32-bit unsigned int) can be encoded very quickly into
// one to five bytes, and decoded back again, again very quickly.
// This is useful for storing data, like offsets or access flags, that
// is usually simple (fits in fewer bytes usually) but sometimes has
// to be complicated (uses all five bytes when necessary).

// Notable features:
//  - represents all 32-bit uint32_t values
//  - never reads or writes beyond 5 bytes
//  - values up to 0xBE (0x307E/0xC207E/0x308207F) code in 1 byte (2/3/4 bytes)
//  - longer encodings are always of larger values (length grows monotonically)
//  - encodings are little-endian numerals in a modifed base-64 system
//  - "negatives" ((u4)-1) need 5 bytes (but see also UNSIGNED5::encode_sign)
//  - different encodings decode to different values (excepting overflow)
//  - zero bytes are *never* used, so it interoperates with null termination
//  - the algorithms are templates and cooperate well with your own types
//  - one writer algorithm can grow your resizable buffer on the fly

// The encoding, taken from J2SE Pack200, is called UNSIGNED5.
// It expects the uint32_t values you give it will have many leading zeroes.
//
// More details:
// Very small values, in the range [0..190], code in one byte.
// Any 32-bit value (including negatives) can be coded, in
// up to five bytes.  The grammar is:
//    low_byte  = [1..191]
//    high_byte = [192..255]
//    any_byte  = low_byte | high_byte
//    coding = low_byte
//           | high_byte low_byte
//           | high_byte high_byte low_byte
//           | high_byte high_byte high_byte low_byte
//           | high_byte high_byte high_byte high_byte any_byte
// Each high_byte contributes six bits of payload.
// The encoding is one-to-one (except for integer overflow)
// and easy to parse and unparse.  Longer sequences always
// decode to larger numbers.  Sequences of the same length
// compares as little-endian numerals decode to numbers which
// are ordered in the same sense as those numerals.

// Parsing (reading) consists of doing a limit test to see if the byte
// is a low-byte or a high-byte, and also unconditionally adding the
// digit value of the byte, multiplied by its 64-bit place value, to
// an accumulator.  The accumulator is returned after either 5 bytes
// are seen, or the first low-byte is seen.  Oddly enough, this is
// enough to create a dense var-int format, which is why it was
// adopted for Pack200.  By comparison, the more common LEB128 format
// is less dense (for many typical workloads) and does not guarantee a
// length limit.

class UNSIGNED5 : AllStatic {
 private:
  // Math constants for the modified UNSIGNED5 coding of Pack200
  static const int lg_H  = 6;        // log-base-2 of H (lg 64 == 6)
  static const int H     = 1<<lg_H;  // number of "high" bytes (64)
  static const int X     = 1  ;      // there is one excluded byte ('\0')
  static const int MAX_b = (1<<BitsPerByte)-1;  // largest byte value
  static const int L     = (MAX_b+1)-X-H;       // number of "low" bytes (191)

 public:
  static const int MAX_LENGTH = 5;   // lengths are in [1..5]
  static const uint32_t MAX_VALUE = (uint32_t)-1;  // 2^^32-1

  // The default method for reading and writing bytes is simply
  // b=a[i] and a[i]=b, as defined by this helpful functor.
  template<typename ARR, typename OFF>
  struct ArrayGetSet {
    uint8_t operator()(ARR a, OFF i) const { return a[i]; };
    void operator()(ARR a, OFF i, uint8_t b) const { a[i] = b; };
    // So, an expression ArrayGetSet() acts like these lambdas:
    //auto get = [&](ARR a, OFF i){ return a[i]; };
    //auto set = [&](ARR a, OFF i, uint8_t x){ a[i] = x; };
  };

  // decode a single unsigned 32-bit int from an array-like base address
  // returns the decoded value, updates offset_rw
  // that is, offset_rw is both read and written
  // warning:  caller must ensure there is at least one byte available
  // the limit is either zero meaning no limit check, or an exclusive offset
  // in PRODUCT builds, limit is ignored
  template<typename ARR, typename OFF, typename GET = ArrayGetSet<ARR,OFF>>
  static uint32_t read_uint(ARR array, OFF& offset_rw, OFF limit, GET get = GET()) {
    const OFF pos = offset_rw;
    STATIC_ASSERT(sizeof(get(array, pos)) == 1);  // must be a byte-getter
    const uint32_t b_0 = (uint8_t) get(array, pos);  //b_0 = a[0]
    assert(b_0 >= X, "avoid excluded bytes");
    uint32_t sum = b_0 - X;
    if (sum < L) {  // common case
      offset_rw = pos + 1;
      return sum;
    }
    // must collect more bytes:  b[1]...b[4]
    int lg_H_i = lg_H;  // lg(H)*i == lg(H^^i)
    for (int i = 1; ; i++) {  // for i in [1..4]
      assert(limit == 0 || pos + i < limit, "oob");
      const uint32_t b_i = (uint8_t) get(array, pos + i);  //b_i = a[i]
      assert(b_i >= X, "avoid excluded bytes");
      sum += (b_i - X) << lg_H_i;  // sum += (b[i]-X)*(64^^i)
      if (b_i < X+L || i == MAX_LENGTH-1) {
        offset_rw = pos + i + 1;
        return sum;
      }
      lg_H_i += lg_H;
    }
  }

  // encode a single unsigned 32-bit int into an array-like span
  // offset_rw is both read and written
  // the limit is either zero meaning no limit check, or an exclusive offset
  // warning:  caller must ensure there is available space
  template<typename ARR, typename OFF, typename SET = ArrayGetSet<ARR,OFF>>
  static void write_uint(uint32_t value, ARR array, OFF& offset_rw, OFF limit, SET set = SET()) {
    const OFF pos = offset_rw;
    if (value < L) {
      const uint32_t b_0 = X + value;
      assert(b_0 == (uint8_t)b_0, "valid byte");
      set(array, pos, (uint8_t)b_0);  //a[0] = b_0
      offset_rw = pos + 1;
      return;
    }
    uint32_t sum = value;
    for (int i = 0; ; i++) {  // for i in [0..4]
      if (sum < L || i == MAX_LENGTH-1) {
        // remainder is either a "low code" or the 5th byte
        uint32_t b_i = X + sum;
        assert(b_i == (uint8_t)b_i, "valid byte");
        set(array, pos + i, (uint8_t)b_i);  //a[i] = b_i
        offset_rw = pos + i + 1;
        return;
      }
      sum -= L;
      uint32_t b_i = X + L + (sum % H);  // this is a "high code"
      assert(b_i == (uint8_t)b_i, "valid byte");
      set(array, pos + i, (uint8_t)b_i);  //a[i] = b_i
      sum >>= lg_H;                 // extracted 6 bits
    }
  }

  // returns the encoded byte length of an unsigned 32-bit int
  static constexpr int encoded_length(uint32_t value) {
    // model the reading of [0..5] high-bytes, followed possibly by a low-byte
    // Be careful:  the constexpr magic evaporates if undefined behavior
    // results from any of these expressions.  Beware of signed overflow!
    uint32_t sum = 0;
    uint32_t lg_H_i = 0;
    for (uint32_t i = 0; ; i++) {  // for i in [1..4]
      if (value <= sum + ((L-1) << lg_H_i) || i == MAX_LENGTH-1) {
        return i + 1;  // stopping at byte i implies length is i+1
      }
      sum += (MAX_b - X) << lg_H_i;
      lg_H_i += lg_H;
    }
  }

  // reports the largest uint32_t value that can be encoded using len bytes
  // len must be in the range [1..5]
  static constexpr uint32_t max_encoded_in_length(uint32_t len) {
    assert(len >= 1 && len <= MAX_LENGTH, "invalid length");
    if (len >= MAX_LENGTH)  return MAX_VALUE;  // largest non-overflow value
    // Be careful:  the constexpr magic evaporates if undefined behavior
    // results from any of these expressions.  Beware of signed overflow!
    uint32_t all_combinations = 0;
    uint32_t combinations_i = L;  // L * H^i
    for (uint32_t i = 0; i < len; i++) {
      // count combinations of <H*L> that end at byte i
      all_combinations += combinations_i;
      combinations_i <<= lg_H;
    }
    return all_combinations - 1;
  }

  // tells if a value, when encoded, would fit between the offset and limit
  template<typename OFF>
  static constexpr bool fits_in_limit(uint32_t value, OFF offset, OFF limit) {
    assert(limit != 0, "");
    return (offset + MAX_LENGTH <= limit ||
            offset + encoded_length(value) <= limit);
  }

  // parses one encoded value for correctness and returns the size,
  // or else returns zero if there is a problem (bad limit or excluded byte)
  // the limit is either zero meaning no limit check, or an exclusive offset
  template<typename ARR, typename OFF, typename GET = ArrayGetSet<ARR,OFF>>
  static int check_length(ARR array, OFF offset, OFF limit = 0,
                          GET get = GET()) {
    const OFF pos = offset;
    STATIC_ASSERT(sizeof(get(array, pos)) == 1);  // must be a byte-getter
    const uint32_t b_0 = (uint8_t) get(array, pos);  //b_0 = a[0]
    if (b_0 < X+L) {
      return (b_0 < X) ? 0 : 1;
    }
    // parse more bytes:  b[1]...b[4]
    for (int i = 1; ; i++) {  // for i in [1..4]
      if (limit != 0 && pos + i >= limit)  return 0;  // limit failure
      const uint32_t b_i = (uint8_t) get(array, pos + i);  //b_i = a[i]
      if (b_i < X)  return 0;  // excluded byte found
      if (b_i < X+L || i == MAX_LENGTH-1) {
        return i + 1;
      }
    }
  }

  template<typename ARR, typename OFF, typename GFN,
           typename SET = ArrayGetSet<ARR,OFF>>
  static void write_uint_grow(uint32_t value,
                              ARR& array, OFF& offset, OFF& limit,
                              GFN grow, SET set = SET()) {
    assert(limit != 0, "limit required");
    const OFF pos = offset;
    if (!fits_in_limit(value, pos, limit)) {
      grow(MAX_LENGTH);  // caller must ensure it somehow fixes array/limit span
      assert(pos + MAX_LENGTH <= limit, "should have grown");
    }
    write_uint(value, array, offset, limit, set);
  }

  /// Handy state machines for that will help you with reading,
  /// sizing, and writing (with optional growth).

  // Reader example use:
  //  struct MyReaderHelper {
  //    char operator()(char* a, int i) const { return a[i]; }
  //  };
  //  using MyReader = UNSIGNED5::Reader<char*, int, MyReaderHelper>;
  //  MyReader r(array); while (r.has_next())  print(r.next_uint());
  template<typename ARR, typename OFF, typename GET = ArrayGetSet<ARR,OFF>>
  class Reader {
    ARR _array;
    OFF _limit;
    OFF _position;
    int next_length() const {
      return UNSIGNED5::check_length(_array, _position, _limit, GET());
    }
  public:
    Reader(ARR array, OFF limit = 0)
      : _array(array), _limit(limit) { _position = 0; }
    uint32_t next_uint() {
      return UNSIGNED5::read_uint(_array, _position, _limit, GET());
    }
    bool has_next() const {
      return next_length() != 0;
    }
    // tries to skip count logical entries; returns actual number skipped
    int try_skip(int count) {
      int actual = 0;
      while (actual < count && has_next()) {
        int len = next_length();  // 0 or length in [1..5]
        if (len == 0)  break;
        _position += len;
      }
      return actual;
    }
    ARR array() { return _array; }
    OFF limit() const { return _limit; }
    OFF position() const { return _position; }
    void set_limit(OFF limit) { _limit = limit; }
    void set_position(OFF position) { _position = position; }

    // For debugging, even in product builds (see debug.cpp).
    // Checks and decodes a series of u5 values from the reader.
    // Sets position just after the last decoded byte or null byte.
    // If this reader has a limit, stop before that limit.
    // If this reader has no limit, stop after the first null byte.
    // In any case, if count is non-negative, print no more than
    // count items (uint32_t values or "null").
    // A negative count means we stop only at the limit or null,
    // kind of like strlen.
    void print(int count = -1) { print_on(tty, count); }

    // The character strings are printed before and after the
    // series of values (which are separated only by spaces).
    // If they are null they default to something like "U5:[ "
    // and " ] (values=%d/length=%d)\n".
    // The %d formats are for the number of printed items and
    // their length in bytes, if you want to see that also.
    void print_on(outputStream* st, int count = -1,
                  const char* left = nullptr, const char* right = nullptr);
  };

  // Writer example use
  //  struct MyWriterHelper {
  //    char operator()(char* a, int i, char b) const { a[i] = b; }
  //  };
  //  using MyWriter = UNSIGNED5::Writer<char*, int, MyWriterHelper>;
  //  MyWriter w(array);
  //  for (auto i = ...)  w.accept_uint(i);
  template<typename ARR, typename OFF, typename SET = ArrayGetSet<ARR,OFF>>
  class Writer {
    ARR& _array;
    OFF* const _limit_ptr;
    OFF _position;
  public:
    Writer(const ARR& array)
      : _array(const_cast<ARR&>(array)), _limit_ptr(nullptr), _position(0) {
      // Note: if _limit_ptr is null, the ARR& is never reassigned,
      // because has_limit is false.  So the const_cast here is safe.
      assert(!has_limit(), "this writer cannot be growable");
    }
    Writer(ARR& array, OFF& limit)
      : _array(array), _limit_ptr(&limit), _position(0) {
      // Writable array argument can be rewritten by accept_grow.
      // So we need a legitimate (non-zero) limit to work with.
      // As a result, a writer's initial buffer must not be empty.
      assert(this->limit() != 0, "limit required");
    }
    void accept_uint(uint32_t value) {
      const OFF lim = has_limit() ? limit() : 0;
      UNSIGNED5::write_uint(value, _array, _position, lim, SET());
    }
    template<typename GFN>
    void accept_grow(uint32_t value, GFN grow) {
      assert(has_limit(), "must track growing limit");
      UNSIGNED5::write_uint_grow(value, _array, _position, *_limit_ptr,
                                 grow, SET());
    }
    // Ensure that remaining() >= r, grow if needed.  Suggested
    // expression for r is (n*MAX_LENGTH)+1, where n is the number of
    // values you are about to write.
    template<typename GFN>
    void ensure_remaining_grow(int request_remaining, GFN grow) {
      const OFF have = remaining();
      if (have < request_remaining) {
        grow(have - request_remaining);  // caller must fix array/limit span
        assert(remaining() >= request_remaining, "should have grown");
      }
    }
    // use to add a terminating null or other data
    void end_byte(uint8_t extra_byte = 0) {
      SET()(_array, _position++, extra_byte);
    }
    ARR array() { return _array; }
    OFF position() { return _position; }
    void set_position(OFF position) { _position = position; }
    bool has_limit() { return _limit_ptr != nullptr; }
    OFF limit() { assert(has_limit(), "needs limit"); return *_limit_ptr; }
    OFF remaining() { return limit() - position(); }
  };

  // Sizer example use
  //  UNSIGNED5::Sizer s;
  //  for (auto i = ...)  s.accept_uint(i);
  //  printf("%d items occupying %d bytes", s.count(), s.position());
  //  auto buf = new char[s.position() + 1];
  //  UNSIGNED5::Writer<char*, int> w(buf);
  //  for (auto i = ...)  w.accept_uint(i);
  //  w.add_byte();
  //  assert(w.position() == s.position(), "s and w agree");
  template<typename OFF = int>
  class Sizer {
    OFF _position;
    int _count;
  public:
    Sizer() { _position = 0; _count = 0; }
    // The accept_uint() API is the same as for Writer, which allows
    // templated code to work equally well on sizers and writers.
    // This in turn makes it easier to write code which runs a
    // sizing preflight pass before actually storing the data.
    void accept_uint(uint32_t value) {
      _position += encoded_length(value);
      _count++;
    }
    OFF position() { return _position; }
    int count() { return _count; }
  };

  // 32-bit one-to-one sign encoding taken from Pack200
  // converts leading sign bits into leading zeroes with trailing sign bit
  // use this to better compress 32-bit values that might be negative
  static uint32_t encode_sign(int32_t value) { return ((uint32_t)value << 1) ^ (value >> 31); }
  static int32_t decode_sign(uint32_t value) { return (value >> 1) ^ -(int32_t)(value & 1); }

  template<typename ARR, typename OFF, typename GET = ArrayGetSet<ARR,OFF>>
  static OFF print(ARR array, OFF offset = 0, OFF limit = 0,
                   GET get = GET()) {
    print_count(-1, array, offset, limit, get);
  }
  template<typename ARR, typename OFF, typename GET = ArrayGetSet<ARR,OFF>>
  static OFF print_count(int count,
                         ARR array, OFF offset = 0, OFF limit = 0,
                         GET get = GET()) {
    Reader<ARR,OFF,GET> r(array, offset);

    r.print_on(tty, count);
    return r.position();
  }
};
#endif // SHARE_UTILITIES_UNSIGNED5_HPP
