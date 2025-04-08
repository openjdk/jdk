/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_KLASSINFOLUTENTRY_INLINE_HPP
#define SHARE_OOPS_KLASSINFOLUTENTRY_INLINE_HPP

#include "oops/instanceKlass.hpp"
#include "oops/klass.hpp" // for KlassKind
#include "oops/klassInfoLUTEntry.hpp"
#include "oops/oop.hpp"
#include "utilities/debug.hpp"

// Returns true if entry carries IK-specific info (oop map block info + size).
// If false, caller needs to look up these infor Klass*.
inline bool KlassLUTEntry::ik_carries_infos() const {
  assert(is_instance(), "sanity");
  return _v.common.kind_specific_bits > 0;
}

// Returns size, in words, of oops of this class
inline int KlassLUTEntry::ik_wordsize() const {
  assert(is_instance() && ik_carries_infos(), "only for valid ik entries");
  return _v.ike.wordsize;
}

// Returns count of first OopMapBlock. Returns 0 if there is no OopMapBlock.
inline unsigned KlassLUTEntry::ik_omb_count_1() const {
  assert(is_instance() && ik_carries_infos(), "only for valid ik entries");
  return _v.ike.omb_count_1;
}

// Returns offset of first OopMapBlock. Only call if count is > 0
inline unsigned KlassLUTEntry::ik_omb_offset_1() const {
  assert(is_instance() && ik_carries_infos(), "only for valid ik entries");
  return _v.ike.omb_offset_1;
}

// Returns count of second OopMapBlock. Returns 0 if there is no second OopMapBlock.
inline unsigned KlassLUTEntry::ik_omb_count_2() const {
  assert(is_instance() && ik_carries_infos(), "only for valid ik entries");
  return _v.ike.omb_count_2;
}

// Returns offset of second OopMapBlock. Only call if count is > 0
inline unsigned KlassLUTEntry::ik_omb_offset_2() const {
  assert(is_instance() && ik_carries_infos(), "only for valid ik entries");
  return _v.ike.omb_offset_2;
}

// calculates word size given header size, element size, and array length
inline unsigned KlassLUTEntry::ak_calculate_wordsize_given_oop(oopDesc* obj) const {
  assert(is_array(), "only for ak entries");
  assert(UseCompactObjectHeaders, "+COH only");
  assert(UseKLUT, "+KLUT only");

  // See oopDesc::size_given_klass
  const unsigned l2esz = ak_layouthelper_esz();
  const unsigned hsz = ak_layouthelper_hsz();

  // In +COH, array length is always at offset 8
  STATIC_ASSERT(sizeof(markWord) == 8);
  const int* const array_len_addr = (int*)(obj->field_addr<int>(8));
  const size_t array_length = (size_t) (*array_len_addr);
  const size_t size_in_bytes = (array_length << l2esz) + hsz;

  // Note: for UseKLUT, we require a standard object alignment (see argument.cpp)
  constexpr int HardCodedObjectAlignmentInBytes = BytesPerWord;
  assert(MinObjAlignmentInBytes == HardCodedObjectAlignmentInBytes, "Sanity");

  return align_up(size_in_bytes, HardCodedObjectAlignmentInBytes) / HeapWordSize;
}

inline unsigned KlassLUTEntry::calculate_wordsize_given_oop(oopDesc* obj) const {
  size_t rc = 0;
  return is_array() ? ak_calculate_wordsize_given_oop(obj) : ik_wordsize();
}

#endif // SHARE_OOPS_KLASSINFOLUTENTRY_INLINE_HPP
