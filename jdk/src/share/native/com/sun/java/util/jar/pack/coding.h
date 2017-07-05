/*
 * Copyright (c) 2002, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

struct unpacker;

#define INT_MAX_VALUE ((int)0x7FFFFFFF)
#define INT_MIN_VALUE ((int)0x80000000)

#define CODING_SPEC(B, H, S, D) ((B)<<20|(H)<<8|(S)<<4|(D)<<0)
#define CODING_B(x) ((x)>>20 & 0xF)
#define CODING_H(x) ((x)>>8  & 0xFFF)
#define CODING_S(x) ((x)>>4  & 0xF)
#define CODING_D(x) ((x)>>0  & 0xF)

#define CODING_INIT(B, H, S, D) \
  { CODING_SPEC(B, H, S, D) , 0, 0, 0, 0, 0, 0, 0, 0}

// For debugging purposes, some compilers do not like this and will complain.
//    #define long do_not_use_C_long_types_use_jlong_or_int
// Use of the type "long" is problematic, do not use it.

struct coding {
  int  spec;  // B,H,S,D

  // Handy values derived from the spec:
  int B() { return CODING_B(spec); }
  int H() { return CODING_H(spec); }
  int S() { return CODING_S(spec); }
  int D() { return CODING_D(spec); }
  int L() { return 256-CODING_H(spec); }
  int  min, max;
  int  umin, umax;
  char isSigned, isSubrange, isFullRange, isMalloc;

  coding* init();  // returns self or null if error
  coding* initFrom(int spec_) {
    assert(this->spec == 0);
    this->spec = spec_;
    return init();
  }

  static coding* findBySpec(int spec);
  static coding* findBySpec(int B, int H, int S=0, int D=0);
  static coding* findByIndex(int irregularCodingIndex);

  static uint parse(byte* &rp, int B, int H);
  static uint parse_lgH(byte* &rp, int B, int H, int lgH);
  static void parseMultiple(byte* &rp, int N, byte* limit, int B, int H);

  uint parse(byte* &rp) {
    return parse(rp, CODING_B(spec), CODING_H(spec));
  }
  void parseMultiple(byte* &rp, int N, byte* limit) {
    parseMultiple(rp, N, limit, CODING_B(spec), CODING_H(spec));
  }

  bool canRepresent(int x)         { return (x >= min  && x <= max);  }
  bool canRepresentUnsigned(int x) { return (x >= umin && x <= umax); }

  int sumInUnsignedRange(int x, int y);

  int readFrom(byte* &rpVar, int* dbase);
  void readArrayFrom(byte* &rpVar, int* dbase, int length, int* values);
  void skipArrayFrom(byte* &rpVar, int length) {
    readArrayFrom(rpVar, (int*)NULL, length, (int*)NULL);
  }

#ifndef PRODUCT
  const char* string();
#endif

  void free();  // free self if isMalloc

  // error handling
  static void abort(const char* msg = null) { unpack_abort(msg); }
};

enum coding_method_kind {
  cmk_ERROR,
  cmk_BHS,
  cmk_BHS0,
  cmk_BHS1,
  cmk_BHSD1,
  cmk_BHS1D1full,  // isFullRange
  cmk_BHS1D1sub,   // isSubRange

  // special cases hand-optimized (~50% of all decoded values)
  cmk_BYTE1,         //(1,256)      6%
  cmk_CHAR3,         //(3,128)      7%
  cmk_UNSIGNED5,     //(5,64)      13%
  cmk_DELTA5,        //(5,64,1,1)   5%
  cmk_BCI5,          //(5,4)       18%
  cmk_BRANCH5,       //(5,4,2)      4%
//cmk_UNSIGNED5H16,  //(5,16)       5%
//cmk_UNSIGNED2H4,   //(2,4)        6%
//cmk_DELTA4H8,      //(4,8,1,1)   10%
//cmk_DELTA3H16,     //(3,16,1,1)   9%
  cmk_BHS_LIMIT,

  cmk_pop,
  cmk_pop_BHS0,
  cmk_pop_BYTE1,
  cmk_pop_LIMIT,

  cmk_LIMIT
};

enum {
  BYTE1_spec       = CODING_SPEC(1, 256, 0, 0),
  CHAR3_spec       = CODING_SPEC(3, 128, 0, 0),
  UNSIGNED4_spec   = CODING_SPEC(4, 256, 0, 0),
  UNSIGNED5_spec   = CODING_SPEC(5, 64, 0, 0),
  SIGNED5_spec     = CODING_SPEC(5, 64, 1, 0),
  DELTA5_spec      = CODING_SPEC(5, 64, 1, 1),
  UDELTA5_spec     = CODING_SPEC(5, 64, 0, 1),
  MDELTA5_spec     = CODING_SPEC(5, 64, 2, 1),
  BCI5_spec        = CODING_SPEC(5, 4, 0, 0),
  BRANCH5_spec     = CODING_SPEC(5, 4, 2, 0)
};

enum {
  B_MAX = 5,
  C_SLOP = B_MAX*10
};

struct coding_method;

// iterator under the control of a meta-coding
struct value_stream {
  // current coding of values or values
  coding c;               // B,H,S,D,etc.
  coding_method_kind cmk; // type of decoding needed
  byte* rp;               // read pointer
  byte* rplimit;          // final value of read pointer
  int sum;                // partial sum of all values so far (D=1 only)
  coding_method* cm;      // coding method that defines this stream

  void init(byte* band_rp, byte* band_limit, coding* defc);
  void init(byte* band_rp, byte* band_limit, int spec)
    { init(band_rp, band_limit, coding::findBySpec(spec)); }

  void setCoding(coding* c);
  void setCoding(int spec) { setCoding(coding::findBySpec(spec)); }

  // Parse and decode a single value.
  int getInt();

  // Parse and decode a single byte, with no error checks.
  int getByte() {
    assert(cmk == cmk_BYTE1);
    assert(rp < rplimit);
    return *rp++ & 0xFF;
  }

  // Used only for asserts.
  bool hasValue();

  void done() { assert(!hasValue()); }

  // Sometimes a value stream has an auxiliary (but there are never two).
  value_stream* helper() {
    assert(hasHelper());
    return this+1;
  }
  bool hasHelper();

  // error handling
  //  inline void abort(const char* msg);
  //  inline void aborting();
};

struct coding_method {
  value_stream vs0;       // initial state snapshot (vs.meta==this)

  coding_method* next;    // what to do when we run out of bytes

  // these fields are used for pop codes only:
  int* fValues;           // favored value array
  int  fVlength;          // maximum favored value token
  coding_method* uValues; // unfavored value stream

  // pointer to outer unpacker, for error checks etc.
  unpacker* u;

  // Initialize a value stream.
  void reset(value_stream* state);

  // Parse a band header, size a band, and initialize for further action.
  // band_rp advances (but not past band_limit), and meta_rp advances.
  // The mode gives context, such as "inside a pop".
  // The defc and N are the incoming parameters to a meta-coding.
  // The value sink is used to collect output values, when desired.
  void init(byte* &band_rp, byte* band_limit,
            byte* &meta_rp, int mode,
            coding* defc, int N,
            intlist* valueSink);

  // error handling
  void abort(const char* msg) { unpack_abort(msg, u); }
  bool aborting()             { return unpack_aborting(u); }
};

//inline void value_stream::abort(const char* msg) { cm->abort(msg); }
//inline void value_stream::aborting()             { cm->aborting(); }
