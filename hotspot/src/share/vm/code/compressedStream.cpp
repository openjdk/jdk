/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "code/compressedStream.hpp"
#include "utilities/ostream.hpp"

// 32-bit one-to-one sign encoding taken from Pack200
// converts leading sign bits into leading zeroes with trailing sign bit
inline juint CompressedStream::encode_sign(jint  value) {
  return (value << 1) ^ (value >> 31);
}
inline jint  CompressedStream::decode_sign(juint value) {
  return (value >> 1) ^ -(jint)(value & 1);
}

// 32-bit self-inverse encoding of float bits
// converts trailing zeroes (common in floats) to leading zeroes
inline juint CompressedStream::reverse_int(juint i) {
  // Hacker's Delight, Figure 7-1
  i = (i & 0x55555555) << 1 | (i >> 1) & 0x55555555;
  i = (i & 0x33333333) << 2 | (i >> 2) & 0x33333333;
  i = (i & 0x0f0f0f0f) << 4 | (i >> 4) & 0x0f0f0f0f;
  i = (i << 24) | ((i & 0xff00) << 8) | ((i >> 8) & 0xff00) | (i >> 24);
  return i;
}


jint CompressedReadStream::read_signed_int() {
  return decode_sign(read_int());
}

// Compressing floats is simple, because the only common pattern
// is trailing zeroes.  (Compare leading sign bits on ints.)
// Since floats are left-justified, as opposed to right-justified
// ints, we can bit-reverse them in order to take advantage of int
// compression.

jfloat CompressedReadStream::read_float() {
  int rf = read_int();
  int f  = reverse_int(rf);
  return jfloat_cast(f);
}

jdouble CompressedReadStream::read_double() {
  jint rh = read_int();
  jint rl = read_int();
  jint h  = reverse_int(rh);
  jint l  = reverse_int(rl);
  return jdouble_cast(jlong_from(h, l));
}

jlong CompressedReadStream::read_long() {
  jint low  = read_signed_int();
  jint high = read_signed_int();
  return jlong_from(high, low);
}

CompressedWriteStream::CompressedWriteStream(int initial_size) : CompressedStream(NULL, 0) {
  _buffer   = NEW_RESOURCE_ARRAY(u_char, initial_size);
  _size     = initial_size;
  _position = 0;
}

void CompressedWriteStream::grow() {
  u_char* _new_buffer = NEW_RESOURCE_ARRAY(u_char, _size * 2);
  memcpy(_new_buffer, _buffer, _position);
  _buffer = _new_buffer;
  _size   = _size * 2;
}

void CompressedWriteStream::write_signed_int(jint value) {
  // this encoding, called SIGNED5, is taken from Pack200
  write_int(encode_sign(value));
}

void CompressedWriteStream::write_float(jfloat value) {
  juint f = jint_cast(value);
  juint rf = reverse_int(f);
  assert(f == reverse_int(rf), "can re-read same bits");
  write_int(rf);
}

void CompressedWriteStream::write_double(jdouble value) {
  juint h  = high(jlong_cast(value));
  juint l  = low( jlong_cast(value));
  juint rh = reverse_int(h);
  juint rl = reverse_int(l);
  assert(h == reverse_int(rh), "can re-read same bits");
  assert(l == reverse_int(rl), "can re-read same bits");
  write_int(rh);
  write_int(rl);
}

void CompressedWriteStream::write_long(jlong value) {
  write_signed_int(low(value));
  write_signed_int(high(value));
}


/// The remaining details

#ifndef PRODUCT
// set this to trigger unit test
void test_compressed_stream(int trace);
bool test_compressed_stream_enabled = false;
#endif

// This encoding, called UNSIGNED5, is taken from J2SE Pack200.
// It assumes that most values have lots of leading zeroes.
// Very small values, in the range [0..191], code in one byte.
// Any 32-bit value (including negatives) can be coded, in
// up to five bytes.  The grammar is:
//    low_byte  = [0..191]
//    high_byte = [192..255]
//    any_byte  = low_byte | high_byte
//    coding = low_byte
//           | high_byte low_byte
//           | high_byte high_byte low_byte
//           | high_byte high_byte high_byte low_byte
//           | high_byte high_byte high_byte high_byte any_byte
// Each high_byte contributes six bits of payload.
// The encoding is one-to-one (except for integer overflow)
// and easy to parse and unparse.

jint CompressedReadStream::read_int_mb(jint b0) {
  int     pos = position() - 1;
  u_char* buf = buffer() + pos;
  assert(buf[0] == b0 && b0 >= L, "correctly called");
  jint    sum = b0;
  // must collect more bytes:  b[1]...b[4]
  int lg_H_i = lg_H;
  for (int i = 0; ; ) {
    jint b_i = buf[++i]; // b_i = read(); ++i;
    sum += b_i << lg_H_i;  // sum += b[i]*(64**i)
    if (b_i < L || i == MAX_i) {
      set_position(pos+i+1);
      return sum;
    }
    lg_H_i += lg_H;
  }
}

void CompressedWriteStream::write_int_mb(jint value) {
  debug_only(int pos1 = position());
  juint sum = value;
  for (int i = 0; ; ) {
    if (sum < L || i == MAX_i) {
      // remainder is either a "low code" or the 5th byte
      assert(sum == (u_char)sum, "valid byte");
      write((u_char)sum);
      break;
    }
    sum -= L;
    int b_i = L + (sum % H);  // this is a "high code"
    sum >>= lg_H;             // extracted 6 bits
    write(b_i); ++i;
  }

#ifndef PRODUCT
  if (test_compressed_stream_enabled) {  // hack to enable this stress test
    test_compressed_stream_enabled = false;
    test_compressed_stream(0);
  }
#endif
}


#ifndef PRODUCT
/// a unit test (can be run by hand from a debugger)

// Avoid a VS2005 compiler stack overflow w/ fastdebug build.
// The following pragma optimize turns off optimization ONLY
// for this block (a matching directive turns it back on later).
// These directives can be removed once the MS VS.NET 2005
// compiler stack overflow is fixed.
#if defined(_MSC_VER) && _MSC_VER >=1400 && !defined(_WIN64)
#pragma optimize("", off)
#pragma warning(disable: 4748)
#endif

// generator for an "interesting" set of critical values
enum { stretch_limit = (1<<16) * (64-16+1) };
static jlong stretch(jint x, int bits) {
  // put x[high 4] into place
  jlong h = (jlong)((x >> (16-4))) << (bits - 4);
  // put x[low 12] into place, sign extended
  jlong l = ((jlong)x << (64-12)) >> (64-12);
  // move l upwards, maybe
  l <<= (x >> 16);
  return h ^ l;
}

void test_compressed_stream(int trace) {
  CompressedWriteStream bytes(stretch_limit * 100);
  jint n;
  int step = 0, fails = 0;
#define CHECKXY(x, y, fmt) { \
    ++step; \
    int xlen = (pos = decode.position()) - lastpos; lastpos = pos; \
    if (trace > 0 && (step % trace) == 0) { \
      tty->print_cr("step %d, n=%08x: value=" fmt " (len=%d)", \
                    step, n, x, xlen); } \
    if (x != y) {                                                     \
      tty->print_cr("step %d, n=%d: " fmt " != " fmt, step, n, x, y); \
      fails++; \
    } }
  for (n = 0; n < (1<<8); n++) {
    jbyte x = (jbyte)n;
    bytes.write_byte(x); ++step;
  }
  for (n = 0; n < stretch_limit; n++) {
    jint x = (jint)stretch(n, 32);
    bytes.write_int(x); ++step;
    bytes.write_signed_int(x); ++step;
    bytes.write_float(jfloat_cast(x)); ++step;
  }
  for (n = 0; n < stretch_limit; n++) {
    jlong x = stretch(n, 64);
    bytes.write_long(x); ++step;
    bytes.write_double(jdouble_cast(x)); ++step;
  }
  int length = bytes.position();
  if (trace != 0)
    tty->print_cr("set up test of %d stream values, size %d", step, length);
  step = 0;
  // now decode it all
  CompressedReadStream decode(bytes.buffer());
  int pos, lastpos = decode.position();
  for (n = 0; n < (1<<8); n++) {
    jbyte x = (jbyte)n;
    jbyte y = decode.read_byte();
    CHECKXY(x, y, "%db");
  }
  for (n = 0; n < stretch_limit; n++) {
    jint x = (jint)stretch(n, 32);
    jint y1 = decode.read_int();
    CHECKXY(x, y1, "%du");
    jint y2 = decode.read_signed_int();
    CHECKXY(x, y2, "%di");
    jint y3 = jint_cast(decode.read_float());
    CHECKXY(x, y3, "%df");
  }
  for (n = 0; n < stretch_limit; n++) {
    jlong x = stretch(n, 64);
    jlong y1 = decode.read_long();
    CHECKXY(x, y1, INT64_FORMAT "l");
    jlong y2 = jlong_cast(decode.read_double());
    CHECKXY(x, y2, INT64_FORMAT "d");
  }
  int length2 = decode.position();
  if (trace != 0)
    tty->print_cr("finished test of %d stream values, size %d", step, length2);
  guarantee(length == length2, "bad length");
  guarantee(fails == 0, "test failures");
}

#if defined(_MSC_VER) &&_MSC_VER >=1400 && !defined(_WIN64)
#pragma warning(default: 4748)
#pragma optimize("", on)
#endif

#endif // PRODUCT
