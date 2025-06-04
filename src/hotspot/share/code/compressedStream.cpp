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

#include "code/compressedStream.hpp"
#include "utilities/ostream.hpp"
#include "utilities/reverse_bits.hpp"

jint CompressedReadStream::read_signed_int() {
  return UNSIGNED5::decode_sign(read_int());
}

// Compressing floats is simple, because the only common pattern
// is trailing zeroes.  (Compare leading sign bits on ints.)
// Since floats are left-justified, as opposed to right-justified
// ints, we can bit-reverse them in order to take advantage of int
// compression.  Since bit reversal converts trailing zeroes to
// leading zeroes, effect is better compression of those common
// 32-bit float values, such as integers or integers divided by
// powers of two, that have many trailing zeroes.
jfloat CompressedReadStream::read_float() {
  int rf = read_int();
  int f  = reverse_bits(rf);
  return jfloat_cast(f);
}

// The treatment of doubles is similar.  We could bit-reverse each
// entire 64-bit word, but it is almost as effective to bit-reverse
// the individual halves.  Since we are going to encode them
// separately as 32-bit halves anyway, it seems slightly simpler
// to reverse after splitting, and when reading reverse each
// half before joining them together.
jdouble CompressedReadStream::read_double() {
  jint rh = read_int();
  jint rl = read_int();
  jint h  = reverse_bits(rh);
  jint l  = reverse_bits(rl);
  return jdouble_cast(jlong_from(h, l));
}

// A 64-bit long is encoded into distinct 32-bit halves.  This saves
// us from having to define a 64-bit encoding and is almost as
// effective.  A modified LEB128 could encode longs into 9 bytes, and
// this technique maxes out at 10 bytes, so, if we didn't mind the
// extra complexity of another coding system, we could process 64-bit
// values as single units.  But, the complexity does not seem
// worthwhile.
jlong CompressedReadStream::read_long() {
  jint low  = read_signed_int();
  jint high = read_signed_int();
  return jlong_from(high, low);
}

CompressedWriteStream::CompressedWriteStream(int initial_size) : CompressedStream(nullptr, 0) {
  _buffer   = NEW_RESOURCE_ARRAY(u_char, initial_size);
  _size     = initial_size;
  _position = 0;
}

void CompressedWriteStream::grow() {
  int nsize = _size * 2;
  const int min_expansion = UNSIGNED5::MAX_LENGTH;
  if (nsize < min_expansion*2) {
    nsize = min_expansion*2;
  }
  u_char* _new_buffer = NEW_RESOURCE_ARRAY(u_char, nsize);
  memcpy(_new_buffer, _buffer, _position);
  _buffer = _new_buffer;
  _size   = nsize;
}

void CompressedWriteStream::write_float(jfloat value) {
  juint f = jint_cast(value);
  juint rf = reverse_bits(f);
  assert(f == reverse_bits(rf), "can re-read same bits");
  write_int(rf);
}

void CompressedWriteStream::write_double(jdouble value) {
  juint h  = high(jlong_cast(value));
  juint l  = low( jlong_cast(value));
  juint rh = reverse_bits(h);
  juint rl = reverse_bits(l);
  assert(h == reverse_bits(rh), "can re-read same bits");
  assert(l == reverse_bits(rl), "can re-read same bits");
  write_int(rh);
  write_int(rl);
}

void CompressedWriteStream::write_long(jlong value) {
  write_signed_int(low(value));
  write_signed_int(high(value));
}
