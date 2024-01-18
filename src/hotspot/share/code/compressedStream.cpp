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

#include "precompiled.hpp"
#include "code/compressedStream.hpp"
#include "utilities/ostream.hpp"
#include "utilities/reverse_bits.hpp"

// Compressing floats is simple, because the only common pattern
// is trailing zeroes.  (Compare leading sign bits on ints.)
// Since floats are left-justified, as opposed to right-justified
// ints, we can bit-reverse them in order to take advantage of int
// compression.  Since bit reversal converts trailing zeroes to
// leading zeroes, effect is better compression of those common
// 32-bit float values, such as integers or integers divided by
// powers of two, that have many trailing zeroes.

jfloat CompressedStream::decode_float(juint rf) {
  int f = reverse_bits(rf);
  return jfloat_cast(f);
}

juint CompressedStream::encode_float(jfloat value) {
  juint f = jint_cast(value);
  juint rf = reverse_bits(f);
  assert(f == reverse_bits(rf), "can re-read same bits");
  return rf;
}

// The treatment of doubles is similar.  We could bit-reverse each
// entire 64-bit word, but it is just as effective to bit-reverse
// the individual halves.  Since we are going to encode them
// separately as 32-bit halves anyway, it seems slightly simpler
// to reverse after splitting, and when reading reverse each
// half before joining them together.
//
// Although exponents have a small amount of sign replication, we do
// not attempt to do sign conversion.  In fact, both (reversed) halves
// are treated identically, because we do not want to ask which half
// is which, in the 64-bit double representation.  In principle we
// could attempt to compress the two halves differently, and even to
// use uint_pair encodings, but the benefit would be small and there
// would surely be bugs.  Our workloads do not use many doubles.

jdouble CompressedStream::decode_double(juint rh, juint rl) {
  jint h = reverse_bits(rh);
  jint l = reverse_bits(rl);
  return jdouble_cast(jlong_from(h, l));
}

void CompressedStream::encode_double(jdouble value, juint& rh, juint& rl) {
  juint h  = high(jlong_cast(value));
  juint l  = low( jlong_cast(value));
  rh = reverse_bits(h);
  rl = reverse_bits(l);
  assert(h == reverse_bits(rh), "can re-read same bits");
  assert(l == reverse_bits(rl), "can re-read same bits");
}

// A 64-bit long is encoded into distinct 32-bit halves.  This saves
// us from having to define a 64-bit encoding and is almost as
// effective.  A modified LEB128 could encode longs into 9 bytes, and
// this technique maxes out at 10 bytes, so, if we didn't mind the
// extra complexity of another coding system, we could process 64-bit
// values as single units.  But, the complexity does not seem
// worthwhile.

jlong CompressedStream::decode_long(juint ulo, juint uhi) {
  jint low  = UNSIGNED5::decode_sign(ulo);
  jint high = UNSIGNED5::decode_sign(uhi);
  return jlong_from(high, low);
}

void CompressedStream::encode_long(jlong value, juint& ulo, juint& uhi) {
  ulo = UNSIGNED5::encode_sign(low(value));
  uhi = UNSIGNED5::encode_sign(high(value));
}

void CompressedIntReadStream::setup(u_char* buffer,
                                    size_t limit,
                                    bool suppress_zeroes) {
  _r.setup(buffer, limit);
  reset();
  if (!suppress_zeroes)  _r.set_passthrough();
}

void CompressedIntWriteStream::setup(address initial_buffer,
                                     size_t initial_size,
                                     bool suppress_zeroes) {
  const size_t MIN_SIZE = UNSIGNED5::MAX_LENGTH;  // avoid really small sizes
  if (initial_size < MIN_SIZE) {
    initial_size = MIN_SIZE; initial_buffer = nullptr;
  }
  if (initial_buffer == nullptr) {
    initial_buffer = NEW_RESOURCE_ARRAY(u_char, initial_size);
  }
  _w.grow_array(initial_buffer, initial_size);
  reset();
  if (!suppress_zeroes)  _w.set_passthrough();
}

u_char* CompressedIntWriteStream::data_address_at(size_t position, size_t length) {
  assert(_w.limit() != 0, "");
  assert(in_bounds(position, _w.limit(), length == 0), "oob");
  assert(in_bounds(position + length, _w.limit(), true), "oob");
  return &_w.array()[position];
}

void CompressedIntWriteStream::grow() {
  size_t nsize = _w.limit() * 2;
  const size_t min_expansion = UNSIGNED5::MAX_LENGTH * 7;
  if (nsize < min_expansion) {
    nsize = min_expansion;
  }
  u_char* nbuf = NEW_RESOURCE_ARRAY(u_char, nsize);
  _w.grow_array(nbuf, nsize);
}

size_t CompressedIntWriteStream::checkpoint() {
#ifdef DO_CZ
  assert(_w.is_clean() || _w.is_passthrough(), "");
  _w_checkpoint = _w.checkpoint();
  return _w_checkpoint.position();
#else
  return _w_checkpoint = _w.position();
#endif
}

size_t CompressedIntWriteStream::data_size_after_checkpoint(size_t checkpoint_pos) {
#ifdef DO_CZ
  assert(_w_checkpoint.position() == checkpoint_pos, "");
  write_end_byte();  // close off any previous compression state
  return _w.position() - checkpoint_pos;
#else
  assert(_w_checkpoint == checkpoint_pos, "");
  return _w.position() - checkpoint_pos;
#endif
}

void CompressedIntWriteStream::restore(size_t checkpoint_pos) {
#ifdef DO_CZ
  assert(_w_checkpoint.position() == checkpoint_pos, "");
  _w.restore(_w_checkpoint);
#else
  assert(_w_checkpoint == checkpoint_pos, "");
  _w.set_position(checkpoint_pos);
#endif
}
