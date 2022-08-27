/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

// Low-level interface for [de-]coding compressed u4 values.

// A u4 value (32-bit unsigned int) can be encoded very quickly into
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

  // decode a single unsigned 32-bit int from an array-like base address
  // returns the decoded value, updates offset_rw
  // that is, offset_rw is both read and written
  // warning:  caller must ensure there is at least one byte available
  // the limit is either zero meaning no limit check, or an exclusive offset
  // in PRODUCT builds, limit is ignored
  template<typename ARR, typename OFF, typename GET>
  static uint32_t read_uint(ARR array, OFF& offset_rw, OFF limit, GET get) {
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
  template<typename ARR, typename OFF, typename SET>
  static void write_uint(uint32_t value, ARR array, OFF& offset_rw, OFF limit, SET set) {
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
  template<typename ARR, typename OFF, typename GET>
  static int check_length(ARR array, OFF offset, OFF limit, GET get) {
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

  template<typename ARR, typename OFF, typename SET, typename GFN>
  static void write_uint_grow(uint32_t value,
                              ARR& array, OFF& offset, OFF& limit,
                              SET set, GFN grow) {
    assert(limit != 0, "limit required");
    const OFF pos = offset;
    if (!fits_in_limit(value, pos, limit)) {
      grow();  // caller must ensure it somehow fixes array/limit span
      assert(pos + MAX_LENGTH <= limit, "should have grown");
    }
    write_uint(value, array, offset, limit, set);
  }

  // convenience overloadings for when array[offset] works
  template<typename ARR, typename OFF>
  static uint32_t read_uint(ARR array, OFF& offset_rw, OFF limit) {
    auto get = [&](ARR a, OFF i){ return a[i]; };
    return read_uint(array, offset_rw, limit, get);
  }
  template<typename ARR, typename OFF>
  static void write_uint(uint32_t value, ARR array, OFF& offset_rw, OFF limit) {
    auto set = [&](ARR a, OFF i, uint8_t x){ a[i] = x; };
    return write_uint(value, array, offset_rw, limit, set);
  }
  template<typename ARR, typename OFF>
  static int check_length(ARR array, OFF offset, OFF limit = 0) {
    auto get = [&](ARR a, OFF i){ return a[i]; };
    return check_length(array, offset, limit, get);
  }
  template<typename ARR, typename OFF, typename GFN>
  static void write_uint_grow(uint32_t value,
                              ARR& array, OFF& offset_rw, OFF& limit,
                              GFN grow) {
    auto set = [&](ARR a, OFF i, uint8_t x){ a[i] = x; };
    return write_uint_grow(value, array, offset_rw, limit, set, grow);
  }

  // handy state machine
  // example use:
  //  struct MyReaderHelper {
  //    char operator()(char* a, int i) const { return a[i]; }
  //  };
  //  using MyReader = UNSIGNED5::Reader<char*, int, MyReaderHelper>;
  template<typename ARR, typename OFF, typename GET>
  class Reader {
    const ARR _array;
    const OFF _limit;
    OFF _position;
  public:
    Reader(ARR array, OFF limit = 0)
      : _array(array), _limit(limit) { _position = 0; }
    u4 next() {
      return UNSIGNED5::read_uint(_array, _position, _limit, GET());
    }
    bool has_next() {
      return UNSIGNED5::check_length(_array, _position, _limit, GET());
    }
    ARR array() { return _array; }
    OFF limit() { return _limit; }
    OFF position() { return _position; }
    void set_position(OFF position) { _position = position; }
  };

  // 32-bit one-to-one sign encoding taken from Pack200
  // converts leading sign bits into leading zeroes with trailing sign bit
  // use this to better compress 32-bit values that might be negative
  static uint32_t encode_sign(int32_t value) { return ((uint32_t)value << 1) ^ (value >> 31); }
  static int32_t decode_sign(uint32_t value) { return (value >> 1) ^ -(int32_t)(value & 1); }

  // 32-bit self-inverse encoding of float bits
  // converts trailing zeroes (common in floats) to leading zeroes
  // use this to better compress 32-bit values with many *trailing* zeroes
  static uint32_t reverse_int(uint32_t i) {
    // %%% should return reverse_bits(i); caller should be edited to use that
    // Hacker's Delight, Figure 7-1
    i = (i & 0x55555555) << 1 | ((i >> 1) & 0x55555555);
    i = (i & 0x33333333) << 2 | ((i >> 2) & 0x33333333);
    i = (i & 0x0f0f0f0f) << 4 | ((i >> 4) & 0x0f0f0f0f);
    i = (i << 24) | ((i & 0xff00) << 8) | ((i >> 8) & 0xff00) | (i >> 24);
    return i;
  }
};
#endif // SHARE_UTILITIES_UNSIGNED5_HPP
