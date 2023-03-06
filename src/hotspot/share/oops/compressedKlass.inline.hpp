/*
 * Copyright (c) 2021 SAP SE. All rights reserved.
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 *
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
#include "memory/allStatic.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"


static inline bool check_alignment(Klass* v) {
  return (intptr_t)v % KlassAlignmentInBytes == 0;
}

inline Klass* CompressedKlassPointers::decode_raw(narrowKlass v) {
  return decode_raw(v, base());
}

inline Klass* CompressedKlassPointers::decode_raw(narrowKlass v, address narrow_base) {
  return (Klass*)(void*)((uintptr_t)narrow_base +((uintptr_t)v << shift()));
}

inline Klass* CompressedKlassPointers::decode_not_null(narrowKlass v) {
  return decode_not_null(v, base());
}

inline Klass* CompressedKlassPointers::decode_not_null(narrowKlass v, address narrow_base) {
  assert(!is_null(v), "narrow klass value can never be zero");
  Klass* result = decode_raw(v, narrow_base);
  DEBUG_ONLY(verify_klass_pointer(result, narrow_base));
  return result;
}

inline Klass* CompressedKlassPointers::decode(narrowKlass v) {
  return is_null(v) ? nullptr : decode_not_null(v);
}

inline narrowKlass CompressedKlassPointers::encode_not_null(Klass* v) {
  return encode_not_null(v, base());
}

inline narrowKlass CompressedKlassPointers::encode_not_null(Klass* v, address narrow_base) {
  DEBUG_ONLY(verify_klass_pointer(v, narrow_base));
  uint64_t v2 = (uint64_t)(pointer_delta((void*)v, narrow_base, 1));
  v2 >>= shift();
  assert(v2 <= UINT_MAX, "narrow klass pointer overflow");
  narrowKlass result = (narrowKlass)v2;
  DEBUG_ONLY(verify_narrow_klass_pointer(result));
  assert(decode_not_null(result, narrow_base) == v, "reversibility");
  return result;
}

inline narrowKlass CompressedKlassPointers::encode(Klass* v) {
  return is_null(v) ? (narrowKlass)0 : encode_not_null(v);
}

#ifdef ASSERT
inline void CompressedKlassPointers::verify_klass_pointer(const Klass* v, address narrow_base) {
  assert(is_aligned(v, KlassAlignmentInBytes), "misaligned Klass* pointer (" PTR_FORMAT ")", p2i(v));
  address end = narrow_base + KlassEncodingMetaspaceMax;
  assert((address)v >= narrow_base && (address)v < end,
         "Klass (" PTR_FORMAT ") located outside encoding range [" PTR_FORMAT ", " PTR_FORMAT ")",
         p2i(v), p2i(narrow_base), p2i(end));
}

inline void CompressedKlassPointers::verify_klass_pointer(const Klass* v) {
  verify_klass_pointer(v, base());
}

inline void CompressedKlassPointers::verify_narrow_klass_pointer(narrowKlass v) {
  // Make sure we only use the lower n bits
  assert((((uint64_t)v) & ~NarrowKlassPointerBitMask) == 0, "%x: not a valid narrow klass pointer", v);
}
#endif

#endif // SHARE_OOPS_COMPRESSEDOOPS_HPP
