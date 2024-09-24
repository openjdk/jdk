/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_COMPRESSEDKLASS_INLINE_HPP
#define SHARE_OOPS_COMPRESSEDKLASS_INLINE_HPP

#include "oops/compressedKlass.hpp"

#include "memory/universe.hpp"
#include "oops/oop.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"

static inline bool check_alignment(Klass* v) {
  return (intptr_t)v % KlassAlignmentInBytes == 0;
}

inline Klass* CompressedKlassPointers::decode_not_null_without_asserts(narrowKlass v, address narrow_base, int shift) {
  return (Klass*)((uintptr_t)narrow_base +((uintptr_t)v << shift));
}

inline Klass* CompressedKlassPointers::decode_not_null(narrowKlass v, address narrow_base, int shift) {
  assert(!is_null(v), "narrow klass value can never be zero");
  Klass* result = decode_not_null_without_asserts(v, narrow_base, shift);
  assert(check_alignment(result), "address not aligned: " PTR_FORMAT, p2i(result));
  return result;
}

inline narrowKlass CompressedKlassPointers::encode_not_null(Klass* v, address narrow_base, int shift) {
  assert(!is_null(v), "klass value can never be zero");
  assert(check_alignment(v), "Address not aligned");
  uint64_t pd = (uint64_t)(pointer_delta(v, narrow_base, 1));
  assert(KlassEncodingMetaspaceMax > pd, "change encoding max if new encoding");
  uint64_t result = pd >> shift;
  assert((result & CONST64(0xffffffff00000000)) == 0, "narrow klass pointer overflow");
  assert(decode_not_null((narrowKlass)result, narrow_base, shift) == v, "reversibility");
  return (narrowKlass)result;
}

inline Klass* CompressedKlassPointers::decode_not_null_without_asserts(narrowKlass v) {
  return decode_not_null_without_asserts(v, base(), shift());
}

inline Klass* CompressedKlassPointers::decode_without_asserts(narrowKlass v) {
  return is_null(v) ? nullptr : decode_not_null_without_asserts(v);
}

inline Klass* CompressedKlassPointers::decode_not_null(narrowKlass v) {
  return decode_not_null(v, base(), shift());
}

inline Klass* CompressedKlassPointers::decode(narrowKlass v) {
  return is_null(v) ? nullptr : decode_not_null(v);
}

inline narrowKlass CompressedKlassPointers::encode_not_null(Klass* v) {
  return encode_not_null(v, base(), shift());
}

inline narrowKlass CompressedKlassPointers::encode(Klass* v) {
  return is_null(v) ? (narrowKlass)0 : encode_not_null(v);
}

#endif // SHARE_OOPS_COMPRESSEDKLASS_INLINE_HPP
