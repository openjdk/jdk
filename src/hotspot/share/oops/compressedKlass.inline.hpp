/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

inline Klass* CompressedKlassPointers::decode_not_null_without_asserts(narrowKlass v, address narrow_base_base, int shift) {
  return (Klass*)((uintptr_t)narrow_base_base +((uintptr_t)v << shift));
}

inline narrowKlass CompressedKlassPointers::encode_not_null_without_asserts(const Klass* k, address narrow_base, int shift) {
  return (narrowKlass)(pointer_delta(k, narrow_base, 1) >> shift);
}

inline Klass* CompressedKlassPointers::decode_not_null_without_asserts(narrowKlass v) {
  return decode_not_null_without_asserts(v, base(), shift());
}

inline Klass* CompressedKlassPointers::decode_without_asserts(narrowKlass v) {
  return is_null(v) ? nullptr : decode_not_null_without_asserts(v, base(), shift());
}

inline Klass* CompressedKlassPointers::decode_not_null(narrowKlass v) {
  assert(!is_null(v), "narrow klass value can never be zero");
  DEBUG_ONLY(check_valid_narrow_klass_id(v);)
  Klass* const k = decode_not_null_without_asserts(v, base(), shift());
  DEBUG_ONLY(check_encodable(k));
  return k;
}

inline Klass* CompressedKlassPointers::decode(narrowKlass v) {
  return is_null(v) ? nullptr : decode_not_null(v);
}

inline narrowKlass CompressedKlassPointers::encode_not_null(const Klass* v) {
  assert(!is_null(v), "klass value can never be zero");
  DEBUG_ONLY(check_encodable(v);)
  const narrowKlass nk = encode_not_null_without_asserts(v, base(), shift());
  assert(decode_not_null_without_asserts(nk, base(), shift()) == v, "reversibility");
  DEBUG_ONLY(check_valid_narrow_klass_id(nk);)
  return nk;
}

inline narrowKlass CompressedKlassPointers::encode(const Klass* v) {
  return is_null(v) ? (narrowKlass)0 : encode_not_null(v);
}

#ifdef ASSERT
inline void CompressedKlassPointers::check_encodable(const void* addr) {
  assert(UseCompressedClassPointers, "Only call for +UseCCP");
  assert(addr != nullptr, "Null Klass?");
  assert(is_encodable(addr),
         "Address " PTR_FORMAT " is not encodable (Klass range: " RANGEFMT ", klass alignment: %d)",
         p2i(addr), RANGE2FMTARGS(_klass_range_start, _klass_range_end), klass_alignment_in_bytes());
}

inline void CompressedKlassPointers::check_valid_narrow_klass_id(narrowKlass nk) {
  check_init(_base);
  assert(UseCompressedClassPointers, "Only call for +UseCCP");
  assert(nk > 0, "narrow Klass ID is 0");
  const uint64_t nk_mask = ~right_n_bits(narrow_klass_pointer_bits());
  assert(((uint64_t)nk & nk_mask) == 0, "narrow klass id bit spillover (%u)", nk);
  assert(nk >= _lowest_valid_narrow_klass_id &&
         nk <= _highest_valid_narrow_klass_id, "narrowKlass ID out of range (%u)", nk);
}
#endif // ASSERT

// Given a narrow Klass ID, returns true if it appears to be valid
inline bool CompressedKlassPointers::is_valid_narrow_klass_id(narrowKlass nk) {
  return nk >= _lowest_valid_narrow_klass_id && nk < _highest_valid_narrow_klass_id;
}

inline address CompressedKlassPointers::encoding_range_end() {
  const int max_bits = narrow_klass_pointer_bits() + _shift;
  return (address)((uintptr_t)_base + nth_bit(max_bits));
}

#endif // SHARE_OOPS_COMPRESSEDKLASS_INLINE_HPP
