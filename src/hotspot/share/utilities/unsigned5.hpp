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

#ifndef SHARE_UTILITIES_UNSIGNED5_HPP
#define SHARE_UTILITIES_UNSIGNED5_HPP

#include "memory/allStatic.hpp"
#include "utilities/copy.hpp"
#include "utilities/count_trailing_zeros.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "utilities/population_count.hpp"
#include "utilities/powerOfTwo.hpp"

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
  static const int flg_L = 7;        // log2i(L) = ctz(hob(L))

 public:
  static const int MAX_LENGTH = 5;   // lengths are in [1..5]
  static const uint32_t MAX_VALUE = (uint32_t)-1;  // 2^^32-1
  static const int END_BYTE = X-1;   // null byte not used by encodings
  static const int MIN_ENCODING_BYTE = X;   // encoding skips end-byte

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
    assert(limit == 0 || fits_in_limit(value, pos, limit), "");
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
  static constexpr uint32_t max_encoded_in_length(int len) {
    assert(len >= 1 && len <= MAX_LENGTH, "invalid length");
    if (len >= MAX_LENGTH)  return MAX_VALUE;  // largest non-overflow value
    // Be careful:  the constexpr magic evaporates if undefined behavior
    // results from any of these expressions.  Beware of signed overflow!
    uint32_t all_combinations = 0;
    uint32_t combinations_i = L;  // L * H^i
    for (uint32_t i = 0; i < (uint32_t)len; i++) {
      // count combinations of <H*L> that end at byte i
      all_combinations += combinations_i;
      combinations_i <<= lg_H;
    }
    return all_combinations - 1;
  }
  // reports the smallest value that encodes in *exactly* len bytes
  // len must be in the range [1..5]
  static constexpr uint32_t min_encoded_in_length(int len) {
    return (len == 1) ? 0 : max_encoded_in_length(len-1)+1;
  }

  // Returns the floor of the base-two logarithm of the largest
  // (unsigned) value that requires len bytes to represent.
  // Thus there are 5 possible results:  7, 13, 19, 25, 31.
  // For five-byte encodings, which support negative values,
  // this is the number 31.
  static constexpr int log2i_max_encoded_in_length(int len) {
    // This is a simple linear formula over integers.
    // This works because H is an exact power of 2.
    int log2i_max = flg_L + (len - 1) * lg_H;
    assert(log2i_max == log2i(max_encoded_in_length(len)), "");
    return log2i_max;
  }

  // tells if a value, when encoded, would fit between the offset and limit
  template<typename OFF>
  static constexpr bool fits_in_limit(uint32_t value, OFF offset, OFF limit) {
    return (offset + MAX_LENGTH <= limit ||
            offset + encoded_length(value) <= limit);
  }

  // parses one encoded value for correctness and returns the size,
  // or else returns zero if there is a problem (bad limit or excluded byte)
  // the limit is either zero meaning no limit check, or an exclusive offset
  template<typename ARR, typename OFF, typename GET = ArrayGetSet<ARR,OFF>>
  static int check_length(ARR array, OFF offset, OFF limit = 0,
                          GET get = GET()) {
    if ((limit != 0 && offset >= limit) || array == nullptr)
      return 0;                 // limit failure or unset array
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
    const OFF pos = offset;
    if (!fits_in_limit(value, pos, limit)) {
      grow(encoded_length(value));  // caller must ensure it somehow fixes array/limit span
      assert(fits_in_limit(value, pos, limit), "should have grown");
    }
    write_uint(value, array, offset, limit, set);
  }

  /// Encoding of pairs
  //
  // When two ints X,Y have small enough entropy (or "bandwidth") to
  // fit in one byte, then it is worth thinking about using a packed
  // representation like (Y<<S)+X.  For wider applicability and better
  // safety, we provide an escape hatch for when Y is too large
  // (1<<32-S or larger) or X is too large (1<<S or larger).
  //
  // Generally speaking, X and Y are independently packed into
  // bitfields of size S and 32-S, and numbers (X or Y) which are "too
  // big" for their bitfields are saturated to the maximum (all 1s).
  // Specifically, X can saturate individually, which leads to an
  // extra int emitted to carry the full X value.  And, if Y
  // saturates, then X is forced to saturate as well, and both X and Y
  // are passed as two extra ints.  Finally, if X saturates, the Y
  // bitfield (saturated or not) is incremented.  This last touch
  // minimizes the overhead of the worst case (3 ints).
  //
  // Here are the specific rules for encoding in 1, 2, or 3 tokens:
  //  - If Y<(1<<32-S) AND X<(1<<S)-1, then use < (Y<<S)+X >.
  //  - If X is "big" and Y<(1<<32-S)-1, then use < ((Y+1)<<S)+M, X >
  //  - Otherwise, Y is "big"; use < M, Y, X >.
  // Here, M is (1<<S)-1, the mask for the X bitfield.
  //
  // Examples of Y:X codes in these three cases, for S==3 (1<<S is 8):
  //  - 0:0 => <0>, 0:1 => <1>, 1:0 => <8>, x:y => <8y+x> (small x, y)
  //  - 0:X => <15,X>, 1:X => <8+15,X>, y:X => <8y+15,X> (small y, big X)
  //  - Y:0 => <7,0,Y>, Y:x => <7,x,Y>  (big Y, any X)
  // In the worst case, the overhead is an extra byte (the leading M=7),
  // compared to sending the two values separately (i.e., unpaired).

  // decode a pair of unsigned 32-bit ints from an array-like base address
  // returns both decoded values by reference, updates offset_rw
  // that is, offset_rw is both read and written
  // Returns the number of words written (1, 2, or 3).
  //
  // The expected bit-width of the first value X is specified (S in (Y<<S)+X).
  // If S=0, two individual 32-bit values are read, unconditionally,
  // just as if two separate calls of read_uint are used to pick up X and Y.
  //
  // Treatment of limit is the same as with read_uint.
  template<typename READ_UINT>
  inline static int read_uint_pair(int first_width,
                                   uint32_t& first_result,
                                   uint32_t& second_result,
                                   READ_UINT read_uint);

  // write a pair of values, preferably in one uint, if they can fit in it
  template<typename WRITE_UINT>
  inline static int write_uint_pair(int first_width, uint32_t first, uint32_t second,
                                    WRITE_UINT write_uint);

  // Returns the leading "YX" word used to encode a pair of ints, as
  // read by read_uint_pair.  See definition for more details.
  // This is public for the sake of ZSReader.
  static int encoded_pair_lead(int first_width, uint32_t first, uint32_t second);

  // returns the minimum number of words (1, 2, or 3) required to
  // encode a pair of ints, as read by read_uint_pair.
  static inline int encoded_pair_count(int first_width, uint32_t first, uint32_t second) {
    return encoded_pair_count(first_width, encoded_pair_lead(first_width, first, second));
  }

  // returns the number of encoding a pair of ints, as being read by
  // read_uint_pair, given the first word (the input XY word)
  static inline int encoded_pair_count(int first_width, uint32_t pair_lead_yx);

  // returns the encoded byte length of a pair of ints as read by read_uint_pair
  static inline int encoded_pair_length(int first_width, uint32_t first, uint32_t second);

  // implementation of -XX:+PrintCompressionStatistics and option control
  class Statistics {
   public:
    #define FOR_EACH_Statistics_Kind(FN) \
      FN(UK,Unknown) \
      FN(FI,FieldInfo) \
      FN(LT,LineNumberTable) \
      FN(DI,DebugInfo) \
      FN(OM,OopMap) \
      FN(DP,Dependencies) \
    /*end*/
    enum Kind {
      #define DECLARE_Statistics_Kind(AB,ignore) AB,
      FOR_EACH_Statistics_Kind(DECLARE_Statistics_Kind)
      #undef DECLARE_Statistics_Kind
      LIMIT
    };
    /*END*/

    // command line switch control routes through here, per kind
    // these settings are useful for evaluating the effects of compression
    static int compression_mode_setting(Kind kind) {
      if ((DisableMetadataCompression & (1 << (int)kind)) != 0) {
        return 0;  // zero means "no extra compression"
      }
      switch (kind) {
      case FI: return FICompressionOptions;
      case LT: return LTCompressionOptions;
      case DI: return DICompressionOptions;
      default: return 0;  // zero means "no extra compression"
      }
    }
    static bool compression_enabled(Kind kind) {
      return compression_mode_setting(kind) != 0;
    }
    static int int_pair_setting(Kind kind) {
      return compression_mode_setting(kind) & 31;
    }
    static bool zero_suppress_setting(Kind kind) {
      return (compression_mode_setting(kind) & 32) != 0;
    }
    static bool extra_setting(Kind kind) {
      return compression_mode_setting(kind) >> 6;
    }

   private:
    Kind   _kind;
    size_t _stream_count;       // number of streams observed (zero-order moment)
    // various summed totals:
    size_t _compressed_size;    // sum of sizes of all observed streams
    size_t _null_count;         // number of null end-bytes
    size_t _uint_count;         // number of encoded uints (after transforms)
    size_t _suppressed_zeroes;  // if zero suppression, # of zeroes elided
    size_t _pair_counts[3];     // if pair transforms, the 1/2/3-item counts
    size_t _bit_width_counts[BitsPerByte*MAX_LENGTH + 1];
    size_t _original_size;
    size_t _original_size_count;

    static Statistics _table[LIMIT];

   public:
    static Statistics& for_kind(Kind kind) {
      assert((int)kind >= 0 && kind < LIMIT, "");
      return _table[kind];
    }

    template<typename ARR, typename OFF, typename GET>
    void record_one_stream(ARR array, OFF limit, GET get,
                           size_t original_size = 0,
                           size_t* pair_counts = nullptr,
                           size_t suppressed_zeroes = 0);
    static void print_statistics();
    void print_on(outputStream* st);
  };

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
    ARR _array;     // base address of byte array
    OFF _limit;     // limit position in array, or zero if unknown
    OFF _position;  // current position in array, index of next byte to read
    int next_length() {
      return UNSIGNED5::check_length(_array, _position, _limit, GET());
    }
  public:
    Reader(ARR array = nullptr, OFF limit = 0) { setup(array, limit); }
    void setup(ARR array, OFF limit = 0) {
      _array = array;
      _limit = limit;
      reset();
    }
    void reset() {
      _position = 0;
    }

    uint32_t next_uint() {
      return UNSIGNED5::read_uint(_array, _position, _limit, GET());
    }
    bool has_next() {
      return next_length() != 0;
    }
    void next_uint_pair(int first_width, uint32_t& first, uint32_t& second) {
      auto reader = [&]{ return read_uint(_array, _position, _limit, GET()); };
      UNSIGNED5::read_uint_pair(first_width, first, second, reader);
    }
    // tries to skip count logical entries; returns actual number skipped
    int try_skip(int count) {
      int actual = 0;
      while (actual < count && has_next()) {
        ++actual;
        int len = next_length();  // 0 or length in [1..5]
        assert(len > 0, "");
        _position += len;
      }
      return actual;
    }
    // tries to skip a single null (out of band) byte
    bool try_skip_end_byte() {
      if ((_limit == 0 || _position < _limit)
          && GET()(_array, _position) == END_BYTE) {
        ++_position;
        return 1;
      } else {
        return 0;
      }
    }
    ARR array() { return _array; }
    OFF limit() { return _limit; }
    OFF position() { return _position; }  // number of bytes scanned
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
    void print(int count) { print_on(tty, count); }
    void print()          { print(-1); }

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
    ARR _array;
    OFF _limit;
    OFF _position;
    size_t _pair_counts[3];  // used only by -XX:+PrintCompressionStatistics
    void add_tidy_null() {
#ifdef ASSERT
      // For debugger displays, add '\0' after every token, if there's room.
      if (_limit != 0 && _position < _limit) {
        SET()(_array, _position, (u_char)0);
      }
#endif //ASSERT
    }
  public:
    Writer(ARR array = nullptr, OFF limit = 0) { setup(array, limit); }
    void setup(ARR array, OFF limit) {
      _array = array;
      _limit = limit;
      reset();
    }
    void reset() {
      _position = 0;
      _pair_counts[0] = _pair_counts[1] = _pair_counts[2] = 0;
    }
    // this is called by a grow-function, after it finds a bigger array:
    void grow_array(ARR array, OFF limit) {
      assert(_limit == 0 || (_position <= _limit && _limit <= limit), "");
      assert(_position <= limit, "");
      _limit = limit;
      if (_array != array) {
        memcpy(array, _array, _position);
        _array = array;
      }
    }
    void accept_uint(uint32_t value) {
      UNSIGNED5::write_uint(value, _array, _position, _limit, SET());
      add_tidy_null();
    }
    template<typename GFN>
    void accept_uint_grow(uint32_t value, GFN grow) {
      UNSIGNED5::write_uint_grow(value, _array, _position, _limit,
                                 grow, SET());
      add_tidy_null();
    }
    int accept_uint_pair(int first_width, uint32_t first, uint32_t second) {
      auto writer = [&](uint32_t value) { accept_uint(value); };
      int nw = UNSIGNED5::write_uint_pair(first_width, first, second, writer);
      collect_pair_count_stat(nw);
      add_tidy_null();
      return nw;
    }
    template<typename GFN>
    int accept_uint_pair_grow(int first_width, uint32_t first, uint32_t second,
                              GFN grow) {
      const int MAX_PAIR_LENGTH = 2*MAX_LENGTH + encoded_length(right_n_bits(first_width & 31));
      ensure_remaining_grow(MAX_PAIR_LENGTH, grow);
      return accept_uint_pair(first_width, first, second);
    }

    // Ensure that remaining() >= r, grow if needed.  Suggested
    // expression for r is (n*MAX_LENGTH)+1, where n is the number of
    // values you are about to write.
    template<typename GFN>
    void ensure_remaining_grow(int request_remaining, GFN grow) {
      const OFF have = remaining();
      if (have < (OFF)request_remaining) {
        grow(have - request_remaining);  // caller must fix array/limit span
        assert(remaining() >= (OFF)request_remaining, "should have grown");
      }
    }
    // use to add a terminating null or other data
    void accept_end_byte(uint8_t extra_byte = END_BYTE) {
      // do not increment _count here
      SET()(_array, _position++, extra_byte);
    }
    ARR array() { return _array; }
    OFF position() { return _position; }  // number of bytes emitted
    void set_position(OFF position) { _position = position; }
    OFF limit() { return _limit; }
    OFF remaining() {
      assert(_position <= _limit, "");
      return _limit - _position;
    }
    void collect_stats(UNSIGNED5::Statistics::Kind kind,
                       size_t original_size = 0,
                       size_t suppressed_zeroes = 0,
                       SET get = SET()) {
      UNSIGNED5::Statistics::for_kind(kind)
        .record_one_stream(array(), position(), get,
                           original_size, _pair_counts, suppressed_zeroes);
    }
    void collect_pair_count_stat(int nw) {
      if (PrintCompressionStatistics) {
        assert(nw >= 1 && nw <= 3, "");
        _pair_counts[nw-1]++;
      }
    }
    // ugly hole in abstraction for ZSWriter checkpoints:
    size_t* pair_count_stats() {
      return &_pair_counts[0];
    }

    void print(int count) { Reader<ARR,OFF,SET>(array(),position()).print(count); }
    void print()          { print(-1); }
  };

  // Sizer example use
  //  UNSIGNED5::Sizer s;
  //  for (auto i = ...)  s.accept_uint(i);
  //  printf("%d items occupying %d bytes", (int)s.count(), (int)s.position());
  //  auto buf = new char[s.position() + 1];
  //  UNSIGNED5::Writer<char*, int> w(buf);
  //  for (auto i = ...)  w.accept_uint(i);
  //  w.add_byte();
  //  assert(w.position() == s.position(), "s and w agree");
  template<typename OFF = size_t>
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
    void accept_uint_pair(int first_width, uint32_t first, uint32_t second) {
      _position += encoded_pair_length(first_width, first, second);
      _count    += encoded_pair_count(first_width, first, second);
    }
    OFF position() { return _position; }
    int count() { return _count; }
  };

  // 32-bit one-to-one sign encoding taken from Pack200, which
  // converts leading sign bits into leading zeroes with trailing sign bit.
  // Use this to better compress 32-bit values that might be negative.
  // It works best when positives and negatives are equally likely.
  // There is never data loss, because it is a 1-1 function.
  static uint32_t encode_sign(int32_t value) { return ((uint32_t)value << 1) ^ (value >> 31); }
  static int32_t decode_sign(uint32_t value) { return (value >> 1) ^ -(int32_t)(value & 1); }

  // An option to encode signs asymmetrically.  Use joint sign bits
  // when we expect positive numbers more often than negatives.  For
  // example, use 3 bits when the ratio is 7-to-1.  This makes sense
  // for delta encodings, when differences are mostly small positive
  // numbers.
  //
  // Sign bit count is in the range [0..15].  For any sign bit count,
  // the transcoding is 1-1 (a bijection) across the whole 32-bit
  // range, so you never lose data.  If the count is 0 or 1, there is
  // no transcoding or the normal single-sign encoding, respectively.
  // All transcodings leave zero unchanged.
  //
  // The encodings run like this:
  // S=0 0 1 2 3 .. -3 -2 -1 => 0 1 2 3 .. FF**FD FF**FE FF**FF (uint32_t(x))
  // S=1 0 1 2 3 .. -3 -2 -1 => 0 2 4 6 .. -5 -3 -1  (encode_sign(x))
  // S=2 0 ..  9 .. -3 -2 -1 => 0 1 2   4 5 6  8 9 10 12 .. 11  7 3
  // S=3 0 .. 10 .. -3 -2 -1 => 0 1 2 3 4 5 6  8 9 10 11 .. 23 15 7
  // S=10 0..1022 => 0..1022; 1023..2045 => 1024..2046; -2 -1 => 2047 1023
  static inline int32_t decode_multi_sign(int sign_bits, uint32_t value);
  static inline uint32_t encode_multi_sign(int sign_bits, int32_t value);

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

class ZeroSuppressingU5 : AllStatic {
 public:
  // Some streams have a disproportionate number of zero values.
  // These widgets read and write such streams in such a way that less
  // storage is used, if the proportion of zero values is more than
  // about 15%.  As the proportion of zeroes increases beyond 15%, the
  // stored compressed data decreases in size gradually, down to a
  // lower limit of about 19% of the original (about 5.25x compression
  // for nearly all zero values).  As a rule of thumb, expect zeroes
  // to decrease 5x in size and non-zeroes to stay the same size.
  //
  // The compression technique is very simple and specific to zero
  // values.  If you are passing patterned data or data with many
  // values of (say) 1, this will not help.  But it might help to
  // encode your data slightly differently, so that discrete values or
  // patterns are encoded in such a way that zeroes become more
  // likely.  For example, correlated values (even if they are mixed
  // in with non-correlated values) can be delta-encoded relative to
  // each other.  This could have two benefits: First, the deltas
  // (even if signed) might be of shorter average byte-lengths (using
  // UNSIGNED5) than the absolute numbers themselves, and second,
  // repetitions will delta-encode as zeroes, which will be picked up
  // by the zero-supressing widget.
  //
  // Since there is no free lunch, if you apply one of these widgets
  // to a stream with 10% or fewer zero values, you will use slightly
  // more memory.  The good news is that the memory overhead is at
  // most one extra byte per sequence of values, of any length.  The
  // alert reader will deduce that there is a one-byte compression
  // command that says, "pass the rest of this data uncompressed".
  //
  // The compressed encoding uses UNSIGNED5 itself, rather than
  // something more general-purpose or complex.  The compressed stream
  // consists of a series of 32-bit commands, possibly followed by
  // 32-bit payload values (usually but not always non-zero).  All
  // these values (commands and payloads) are uniformly encoded as
  // UNSIGNED5.  This means that null bytes can be used to terminate
  // compressed streams just as with regular UNSIGNED5 streams.
  //
  // There are only two commands, a zero mask and a block copy.  The
  // zero mask encodes a 32-bit bitmask, where one-bits denote zeroes,
  // and zero-bits denote payload values (following the command
  // immediately in order).  The MSB of the mask denotes the final
  // zero emitted by the command, and payload values correspond only
  // to lower-order bits in the bitmask.  The block copy command
  // encodes a length, which counts the number of payloads that
  // followed immediately.  If the length decodes as zero, the payload
  // count is treated as infinite, which is the command to stop
  // compressing.
  //
  // The encoding details are a little fiddly, in order to get
  // (significantly) better compression.  Since the commands are
  // themselves 32-bit UNSIGNED5 values, some attention has been paid
  // to ensuring that common commands use fewer bytes than rare
  // commands.  Specifically, some 8-bit bitmasks are useful when zero
  // values appear more than 80% frequency.  These "special" bitmasks
  // have been reassigned UNSIGNED5 values that fit in one byte, when
  // that byte is not already useful as a mask.
  //
  template<typename ARR, typename OFF,
           typename GET = UNSIGNED5::ArrayGetSet<ARR,OFF>>
  class ZSReader {
    UNSIGNED5::Reader<ARR,OFF,GET> _r;  // stored compression commands and payloads
    // Besides the backing reader, the decompressor state is just 64 bits:
    uint32_t _zero_mask;   // if non-zero, the current bitmask for zeroes
    uint32_t _block_count; // if non-zero, remaining items in current block
    bool _sticky_passthrough;
    void set_clean_or_passthrough() {
      _zero_mask = 0;
      _block_count = (_sticky_passthrough ? PASSTHROUGH_BLOCK_COUNT : 0);
    }
    bool is_clean_or_passthrough() {
      return is_clean() || is_passthrough();
    }
    uint32_t next_uint_uncompressing();

  public:
    ZSReader(ARR array = nullptr, OFF limit = 0) { setup(array, limit); }
    void setup(ARR array, OFF limit = 0);
    void reset();

    bool at_start() {
      return _r.position() == 0;
    }

    bool is_clean() {   // true if there we have no un-executed commands
      return (_zero_mask | _block_count) == 0;
    }

    bool is_passthrough() {
      return (_block_count == PASSTHROUGH_BLOCK_COUNT);
    }

    // set this stream to pass-through mode (stop expanding)
    // must be done immediately after reset
    // this condition, requested by the user, is sticky; it cannot be reversed
    void set_passthrough() {
      assert(is_clean_or_passthrough(), "");
      _sticky_passthrough = true;
      _block_count = PASSTHROUGH_BLOCK_COUNT;
    }

    bool has_next() {
      return !is_clean_or_passthrough() || _r.has_next();
    }

    uint32_t next_uint() {
      return is_passthrough() ? _r.next_uint() : next_uint_uncompressing();
    }

    void next_uint_pair(int first_width, uint32_t& first, uint32_t& second) {
      auto reader = [&]{ return next_uint(); };
      UNSIGNED5::read_uint_pair(first_width, first, second, reader);
    }

    // tries to skip count logical entries; returns actual number skipped
    int try_skip(int count) {
      int actual = 0;
      while (actual < count && has_next()) {
        ++actual;
        next_uint();
        // We could make this faster but it's not worth it.
      }
      return actual;
    }
    // tries to skip a single null (out of band) byte
    bool try_skip_end_byte() {
      if (!has_next() && _r.try_skip_end_byte()) {
        set_clean_or_passthrough();
        return true;
      } else {
        return false;
      }
    }

    ARR array() { return _r.array(); }
    OFF position() {
      assert(is_clean_or_passthrough(), "");  // no active decompressor state
      return _r.position();
    }
    void reset_at_position(OFF position) {
      set_clean_or_passthrough();
      _r.set_position(position);
    }

    // Dump all compression codes in this reader.
    void print() { print_on(tty); }
    void print_on(outputStream* st);
  };

 private:
  // templates for the checkpointer
  struct CopyIn {
    template<typename T> void operator()(T& ours, T& theirs) { ours = theirs; }
  };
  struct CopyOut {
    template<typename T> void operator()(T& ours, T& theirs) { theirs = ours; }
  };

 public:
  template<typename OFF, typename ZSWriter>
  class ZSWriterCheckpoint {
    bool _is_active;
    OFF _position;
    int _block_length;   // represents passthrough, only
    // save the statistics
    size_t _suppressed_zeroes;
    size_t _pair_counts[3];
    template<typename FN> void mapper(ZSWriter& zw, FN fn) {
      auto& uw = zw._w;
      fn(_block_length, zw._block_length);
      fn(_suppressed_zeroes, zw._suppressed_zeroes);
      fn(_pair_counts[0], uw.pair_count_stats()[0]);
      fn(_pair_counts[1], uw.pair_count_stats()[1]);
      fn(_pair_counts[2], uw.pair_count_stats()[2]);
    }
  public:
    bool is_active() { return _is_active; }
    OFF position() {
      assert(is_active(), "");
      return _position;
    }
    ZSWriterCheckpoint() {
      _is_active = false;
      // rest of fields are garbage
    }
    ZSWriterCheckpoint(ZSWriter& zw) {
      _is_active = true;
      assert(zw.is_clean_or_passthrough(), "");
      _position = zw._w.position();
      mapper(zw, CopyIn());
    }
    void restore(ZSWriter& zw) {
      assert(is_active(), "");  // it is a one-shot checkpoint
      assert(zw._w.position() >= _position, "");  // must restore backwards
      zw.set_clean_or_passthrough();
      zw._w.set_position(_position);
      mapper(zw, CopyOut());
      _is_active = false;
    }
  };

  template<typename ARR, typename OFF,
           typename SET = UNSIGNED5::ArrayGetSet<ARR,OFF>>
  class ZSWriter {
    friend class ZSWriterCheckpoint<OFF,ZSWriter<ARR,OFF,SET>>;

    UNSIGNED5::Writer<ARR,OFF,SET> _w;
    size_t _suppressed_zeroes;

    // The compressor requires a window of up to 34 items.  We store
    // the window in the backing writer _w, but also summarize the
    // positions of zeroes in a bitmask.
    //
    // There are three distinct areas in the writer:
    //  - a committed area:  compression commands that are already done
    //  - a block area:  items which are destined for a block command
    //  - a zero mask area:  items being considered for a zero mask command
    //
    // Any or all of these three areas can be empty.  They are all
    // disjoint and contiguous, in the order of committed, then block,
    // then zero mask.  If the block area is non-empty, then it begins
    // with a zero word, which stands for an indefinite block copy
    // command.  It may be left as-is (if no further commands are
    // committed) or it may be updated to a definite block copy
    // command.  If updated, it may be expanded in size requiring some
    // shifting of bytes in the array of the writer _w.
    //
    // Typical contents of _w:
    //
    // ... X Y Z | bh(0) | A B C D ... | P 0 Q 0 0 R S 0 T ... |
    //  (...done)       bs (block...) zs (mask area...)       w.pos
    //                   \___ blen ___/ \_ zm(010110010...) __/
    //
    // The bh(0) item is present only if blen>0, which means we are
    // certain we want a block-copy command, whether or not we will
    // follow it with a zero-mask command.  New items are added on the
    // right, to the zero-mask area.  If the zero-mask area grows to
    // 32 bits (or larger) then a decision is made to group older
    // items into a zero-mask command, if that is profitable, or else
    // spill the oldest items down to the block area.  The possibility
    // of repeated spilling into the same block area is why we don't
    // commit to blocks quickly; we wait until a zero-mask command is
    // decided upon, and then all the pending block items are combined
    // into a single block-copy command.

    int _zero_mask_length; // number of valid z.m. bits, in 0..34
    OFF _zero_mask_start;  // starting position for zero mask area (if zml>0)
    uint64_t _zero_mask;   // map of all zeroes in the zero mask area (if zml>0)

    int _block_length;     // number of items in current block area
    OFF _block_start;      // position of start of current block area (if bl>0)

    bool _sticky_passthrough;

    void set_clean_or_passthrough() {
      _zero_mask_length = 0;
      _block_length = (_sticky_passthrough ? PASSTHROUGH_BLOCK_COUNT : 0);
    }
    bool is_clean_or_passthrough() {
      return is_clean() || is_passthrough();
    }
    bool have_zero_mask() {
      return _zero_mask_length != 0;
    }
    bool have_current_block() {
      return _block_length != 0;
    }

  public:
    ZSWriter(ARR array = nullptr, OFF limit = 0) { setup(array, limit); }
    void setup(ARR array, OFF limit);
    void reset();
    void grow_array(ARR array, OFF limit);

    bool at_start() {
      return is_clean_or_passthrough() && _w.position() == 0;
    }

    bool is_clean() {   // true if there we have no un-committed state
      return (_zero_mask_length | _block_length) == 0;
    }

    bool is_passthrough() {
      return (_block_length == (int)PASSTHROUGH_BLOCK_COUNT);
    }

    // set this stream to pass-through mode (stop compressing)
    // must be done immediately after reset
    // this condition, requested by the user, is sticky; it cannot be reversed
    void set_passthrough() {
      assert(is_clean_or_passthrough(), "");
      _sticky_passthrough = true;
      _block_length = PASSTHROUGH_BLOCK_COUNT;
    }

    // Flush pending compressor state, if any.  After this function,
    // additional inputs will be accepted, but they might not be
    // compressed, because the compressor may choose to end up
    // in pass-through mode, if that gets the best compression of
    // the input so far.
    //
    // If the argument is true, then compression will be attempted on
    // future inputs as well.  However, this comes at a cost: The
    // compression of the input so far is NOT going to be optimal.  In
    // particular, if the input has been incompressible so far, then a
    // definite block header will be inserted, which can raise the
    // compression overhead above its guaranteed maximum of one byte.
    // If you need to make pointers into the middle of the compression
    // stream, this is what you need to do.  Alternatively, you can
    // ensure that every directly addressible substream starts fresh
    // after an end-byte.
    void flush(bool continue_compressing = false) {
      if (!is_clean_or_passthrough()) {
        commit(continue_compressing, false);
      }
    }

    // similar to UNSIGNED5::Writer::accept_uint
    void accept_uint(uint32_t value) {
      const OFF start_pos = accept_uint_setup();
      _w.accept_uint(value);
      if (!is_passthrough()) {
        digest_uint(start_pos, value);
      }
    }

    // similar to UNSIGNED5::Writer::accept_uint_grow
    template<typename GFN>
    void accept_uint_grow(uint32_t value, GFN grow) {
      const OFF start_pos = accept_uint_setup();
        _w.accept_uint_grow(value, [&](int n){ grow(1+n); });
      if (!is_passthrough()) {
        digest_uint(start_pos, value);
      }
    }

    void accept_uint_pair(int first_width, uint32_t first, uint32_t second) {
      const OFF start_pos = accept_uint_setup();
      int nw = _w.accept_uint_pair(first_width, first, second);
      if (!is_passthrough()) {
        digest_multiple_uints(start_pos, nw);
      }
    }

    template<typename GFN>
    void accept_uint_pair_grow(int first_width, uint32_t first, uint32_t second,
                               GFN grow) {
      const OFF start_pos = accept_uint_setup();
      int nw = _w.accept_uint_pair_grow(first_width, first, second,
                                        [&](int n){ grow(1+n); });
      if (!is_passthrough()) {
        digest_multiple_uints(start_pos, nw);
      }
    }

    // similar to UNSIGNED5::Writer::ensuring_remaining_grow
    template<typename GFN>
    void ensure_remaining_grow(int request_remaining, GFN grow) {
      _w.ensure_remaining_grow(1 + request_remaining, grow);
    }

    // Finish compression and add a terminating null byte as a fence.
    // This is one of the few operations that ends up with a clean state.
    void accept_end_byte();

    ARR array() { return _w.array(); }
    OFF position() {
      assert(is_clean_or_passthrough(), "");  // no active decompressor state
      return _w.position();
    }
    OFF limit() { return _w.limit(); }
    OFF remaining() { return _w.remaining(); }

    ZSWriterCheckpoint<OFF,ZSWriter<ARR,OFF,SET>> checkpoint() {
      return ZSWriterCheckpoint<OFF,ZSWriter<ARR,OFF,SET>>(*this);
    }
    void restore(ZSWriterCheckpoint<OFF,ZSWriter<ARR,OFF,SET>>& ckpt) {
      ckpt.restore(*this);
    }

    // There is a lot of machinery inside this black box...
   private:
    // advance in the output buffer of _w (must already have been written!)
    OFF advance_position(OFF start, int count);

    bool sanity_checks();

    // Get ready to push a single uint to the Writer _w.
    OFF accept_uint_setup() {
      assert(sanity_checks(), "");
      return _w.position();
    }

    // After pushing a uint (or maybe a few) to the Writer _w, update
    // the bookkeeping for compression.
    void digest_uint_mask(uint32_t more_zm, int more_zm_len, OFF start_pos);
    void digest_multiple_uints(OFF start_pos, int nw);
    void digest_uint(OFF start_pos, uint32_t value) {
      digest_uint_mask(value == 0 ? 1 : 0, 1, start_pos);
    }

    // Remove items from the zero mask area until it is no more than
    // the target size.  If they can be placed profitably into a zero
    // mask command, do so, else transfer them into the block area.
    void drain_zero_mask(int target_zero_mask_length);

    // Remove items from the zero mask and assign them to the current
    // block.  This is an alternative to do_compression; both
    // functions finalize decisions about zero suppression (in the
    // current zero mask region).  But this one just passes the
    // UNSIGNED5 encodings unchanged, even if they are zeroes.
    // Sometimes that's the right decision, even for zeroes.
    void expand_current_block(int trim);

    // This is where the payoff happens.
    void do_compression(uint32_t best_zm);

    // commit the current block to a real command
    // use an indefinite length (works like passthrough) or a more
    // expensive definite length, according to the selected option
    void emit_block_command(bool use_indefinite_length);

    // commit a zero mask command (caller must marshal the payloads)
    void emit_zero_mask_command(uint32_t best_zm);

    // Commit any pending commands to the backing store and get to a
    // clean state ready to compress further data, or else to a
    // passthrough state to which any amount of further data can be
    // appended to the backing store.
    void commit(bool require_clean,
                bool require_passthrough);

   public:
    void collect_stats(UNSIGNED5::Statistics::Kind kind,
                       size_t original_size = 0, SET get = SET()) {
      _w.collect_stats(kind, original_size, _suppressed_zeroes, get);
    }

    // ugly hole in abstraction for testing:
    UNSIGNED5::Writer<ARR,OFF,SET>& writer_for_testing() {
      return _w;
    }

    void print() { print_on(tty); }
    void print_on(outputStream* st);
  };

 private:
  // Implementation details.
  static const uint32_t BLOCK_TAG_WIDTH = 4;  // tunable
  static const uint32_t BLOCK_TAG_MASK = (1<<BLOCK_TAG_WIDTH)-1;
  static const uint32_t MAX_BLOCK_COUNT = (uint32_t)-1 >> BLOCK_TAG_WIDTH;
  static const uint32_t PASSTHROUGH_BLOCK_COUNT = (uint32_t)-1;  //sentinel
  static const uint32_t MAX_MASK_WIDTH = 8 * sizeof(uint32_t);
  static const uint32_t SPECIAL_MASK_KNOCKOUTS = 1 | ((-0x80 >> (BLOCK_TAG_WIDTH-2)) & 0x7F);
  static const uint32_t GIVE_UP_AFTER = 1 << 10;  // must be less than 2^28
  static const uint32_t ZERO_ENCODING = UNSIGNED5::MIN_ENCODING_BYTE;
  static const bool SPLIT_MASKS = true;
  static const bool SHORTER_MASKS = true;
 
  static bool is_block_count_code(uint32_t cmd) {
    return (cmd & BLOCK_TAG_MASK) == 0;
  }
  static uint32_t decode_block_count(uint32_t cmd) {
    assert(is_block_count_code(cmd), "");
    // Any block count must be less than 2^28.  If you haven't found
    // zeroes by then, it's time to stop compressing.
    return cmd >> BLOCK_TAG_WIDTH;
  }
  static uint32_t encode_block_count(uint32_t count) {
    assert(count >= 0 && count <= MAX_BLOCK_COUNT, "");
    int cmd = count << BLOCK_TAG_WIDTH;
    assert(decode_block_count(cmd) == count, "");
    return cmd;
  }
  static bool is_valid_zero_mask(uint32_t mask) {
    // It must not look like a block copy command, so it must have at
    // least one bit set in the lower part (4 bits).  On the other
    // hand, it must not be a singleton bitmask.  A single zero,
    // encoded in a bitmask, is never a profitable command to issue.
    return (mask & BLOCK_TAG_MASK) != 0 && !is_power_of_2(mask);
  }
  static uint32_t decode_zero_mask(uint32_t cmd) {
    assert(!is_block_count_code(cmd), "");
    // usually, the encoding IS the mask, but sometimes it is special
    return (is_valid_zero_mask(cmd)
            ? cmd
            : decode_special_mask(cmd));
  }
  static uint32_t encode_zero_mask(uint32_t mask) {
    assert(is_valid_zero_mask(mask), "");
    int cmd = (!is_special_mask(mask)
               ? mask
               : encode_special_mask(mask));
    assert(decode_zero_mask(cmd) == mask, "");
    return cmd;
  }

  // Very sparse workloads, having many consecutive zeroes, call for
  // zero masks with high bit weights.  In particular, a sequence of
  // the form {@code <0**N|1|0**M>} has a good encoding as runs of
  // {@code N=8} zeroes until a final run of {@code N<8}, using the
  // following patterns:
  //
  //   - N=8: 0xFF* (8 zeroes, then another of these cases)
  //   - N=0: 0x02 0x06 0x0E 0x1E 0x3E 0x7E 0xFE* (0<M<8)
  //   - N=1:      0x05 0x0D 0x1D 0x3D 0x7D 0xFD* (0<M<7)
  //   - N=2:           0x0B 0x1B 0x3B 0x7B 0xFB* (0<M<6)
  //   - N=3:                0x17 0x37 0x77 0xF7* (0<M<5)
  //   - N=4:                     0x2F 0x6F 0xEF* (0<M<4)
  //   - N=5:                          0x5F 0xDF* (0<M<3)
  //   - N=6:                               0xBF* (M=1)
  //   - N=7: 0x7F  (7 zeroes, then one of the N=0 cases)
  //
  // The starred masks demand two bytes in UNSIGNED5 notation, so they
  // must either be encoded in one byte by the special replacement
  // convention, or else expanded somehow.  The correct way to expand
  // in these sparse cases is not to use a two-byte UNSIGNED5
  // encoding, which burns an extra byte that normally should encode
  // up to 8 zeroes.  Instead, as single zero mask bit is transferred
  // from the MSB of the over-sized mask to the LSB of the next mask.
  // So, for masks other than the important 0xFF*, 0xFE*, 0xBF*:
  //
  //   - N=1:  0xFD* => 0x7D (M=5) + 0x01|mask (N>0)
  //   - N=2:  0xFB* => 0x7B (M=4) + (same)
  //   - N=3:  0xF7* => 0x77 (M=3) + (same)
  //   - N=4:  0xEF* => 0x6F (M=2) + (same)
  //   - N=5:  0xDF* => 0x5F (M=1) + (same)
  //
  // Here are encodings (singleton mask to high bit-weight):
  //   0x01 => 0xFF* (8 zeroes in a row)
  //   0x02 => 0xFE* (one non-zero plus 7 zeroes)
  //   0x04 => 0xDF* (6 zeros, one non-zero, 2 more zeroes)
  //   0x08 => 0xBF* (7 zeros, one non-zero, 1 more zero)
  //
  // Each of these is a full 8-bit mask (0xFF) with at most one bit
  // "knocked out".  Higher codes correspond to higher bits knocked
  // out.
  //
  // Thus, every special mask fits into an 8-bit byte, has its top bit
  // set, and has at most one bit clear.  As such is useful for
  // compressing streams with more than 80% zeroes.
  static bool is_special_mask(uint32_t mask) {
    assert(is_valid_zero_mask(mask), "");
    return ((mask | SPECIAL_MASK_KNOCKOUTS) == 0xFF
            && population_count(mask) >= 7);
  }
  static uint32_t decode_special_mask(uint32_t cmd) {
    assert(is_power_of_2(cmd), "");
    // Compute at most one bit to "knock out" of 0xFF:
    int ko = (cmd <= 2) ? cmd - 1 : cmd << (7-BLOCK_TAG_WIDTH);
    assert((ko == 0 || is_power_of_2(ko)) && (ko & 0x7F) == ko, "");
    return 0xFF & ~ko;
  }
  static uint32_t encode_special_mask(uint32_t mask) {
    assert(is_special_mask(mask), "");
    uint32_t ko = ~mask & SPECIAL_MASK_KNOCKOUTS;
    assert(mask + ko == 0xFF && population_count(ko) <= 1, "");
    uint32_t cmd = (ko <= 1) ? ko + 1 : ko >> (7 - BLOCK_TAG_WIDTH);
    assert(decode_special_mask(cmd) == mask, "");
    return cmd;
  }

  // This function is the heart of the compression policy.  It starts
  // with a 32-element window of zero/non-zero observations, and
  // decides what initial sequence of them to turn into a zero mask
  // command.  But if the profit from doing so is less than min_profit
  // (or if there is negative profit), return a zero bitmask, meaning
  // no zero mask command should be emitted at this point.
  static uint32_t best_zero_mask(uint32_t zm, int min_profit) {
    // If zm is a valid mask we can use it, as long as profit is big enough.
    if (!is_valid_zero_mask(zm))  return 0;
    const int zml = UNSIGNED5::encoded_length(encode_zero_mask(zm));
    int best_mask = 0, best_profit = 0;
    // Maybe see if there is a shorter mask that gives us a better profit.
    for (int zm1len = SPLIT_MASKS ? 1 : zml; zm1len <= zml; zm1len++) {
      // Split the mask in two, and see if the earlier one is nice enough.
      const uint32_t zm1 = (zm1len == zml) ? zm : split_zero_mask(zm, zm1len);
      assert(zm1len == zml || zm1 != zm, "");  // it should be a real split
      // zero masks must have at least 2 bits set:
      if (zm1 == 0 || is_power_of_2(zm1))  continue;
      assert(UNSIGNED5::encoded_length(encode_zero_mask(zm1)) == zm1len, "");
      const int zm1_profit = population_count(zm1) - zm1len;
      if (SHORTER_MASKS && zm1len < zml && zm1_profit >= min_profit &&
          is_valid_zero_mask(zm >> zero_mask_length(zm1))) {
        // Split as soon as we can see a second complete mask ready to emit.
        return zm1;
      }
      if (best_profit <= zm1_profit) {
        // '<=' instead of '<' favors longer tokens, for slightly better scores
        best_profit = zm1_profit;
        best_mask = zm1;
      }
    }
    return (best_profit >= min_profit) ? best_mask : 0;
  }

  static int zero_mask_length(uint32_t zm) {
    // The elements represented by a zero mask correspond to lower bit
    // positions in the mask, from the MSB, up to and including the
    // highest 1-bit (which denotes a zero).  So return 32-clz(zm).
    return (zm == 0) ? 0 : 32 - count_leading_zeros(zm);
  }

  static uint32_t split_zero_mask(uint32_t zm, int zm1len) {
    assert(zm1len < UNSIGNED5::encoded_length(encode_zero_mask(zm)), "");
    const uint32_t minv = UNSIGNED5::min_encoded_in_length(zm1len);
    const uint32_t maxv = UNSIGNED5::max_encoded_in_length(zm1len);
    const int flg = UNSIGNED5::log2i_max_encoded_in_length(zm1len);
    const uint32_t flg_mask = ((((uint32_t)2) << flg) - 1);
    const uint32_t zm1 = zm & flg_mask; // split off earlier part of zm
    if (zm1 < minv) {
      return 0;  // split part is under-sized, so return empty result
    } else if (zm1 > maxv && !is_special_mask(zm1)) {
      return zm1 & (flg_mask >> 1);  // shave off the top bit also
    } else {
      return zm1;
    }
  }

};
#endif // SHARE_UTILITIES_UNSIGNED5_HPP
