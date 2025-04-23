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
#include "oops/objLayout.inline.hpp"
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

template <HeaderMode mode>
static void check_header_mode() {
  assert(mode != HeaderMode::Uncompressed, "bad call");
  assert(UseCompressedClassPointers, "bad call");
  if (mode == HeaderMode::Compact) {
    assert(UseCompactObjectHeaders, "bad call");
  }
  assert(MinObjAlignmentInBytes == BytesPerWord, "Bad call");
}

// calculates word size given header size, element size, and array length
template <HeaderMode mode, class OopType>
inline size_t KlassLUTEntry::oak_calculate_wordsize_given_oop_fast(oopDesc* obj) const {
  check_header_mode<mode>();
  assert(sizeof(OopType) == (UseCompressedOops ? 4 : 8), "bad call");
  assert(is_obj_array(), "Bad call");
  assert(obj->is_objArray(), "Bad call");

  // Only call for +UCCP and for standard ObjectAlignmentInBytes
  constexpr int obj_alignment = BytesPerWord;
  constexpr int log2_oopsize = (sizeof(OopType) == 4 ? 2 : 3); // narrowOop or Oop
  constexpr unsigned length_field_offset = (unsigned)ObjLayoutHelpers::markword_plus_klass_in_bytes<mode>();
  constexpr unsigned first_element_offset = (unsigned)ObjLayoutHelpers::array_first_element_offset_in_bytes<mode, OopType>();
  assert(first_element_offset == ak_first_element_offset_in_bytes(), "sanity");

  // Load length from object
  const unsigned* const array_len_addr = (unsigned*)(obj->field_addr<unsigned>(length_field_offset));
  const unsigned array_length = (unsigned) (*array_len_addr);
  assert(array_length == (unsigned)((typeArrayOop)obj)->length(), "sanity");

  // Calculate size
  const unsigned size_in_bytes = (array_length << log2_oopsize) + first_element_offset;
  return align_up(size_in_bytes, obj_alignment) / HeapWordSize;
}

// calculates word size given header size, element size, and array length
template <HeaderMode mode>
inline size_t KlassLUTEntry::tak_calculate_wordsize_given_oop_fast(oopDesc* obj) const {
  check_header_mode<mode>();
  // The purpose of this function is to hard-code as much as we can via template parameters.
  assert(is_type_array(), "Bad call");
  assert(obj->is_typeArray(), "Bad call");

  // Only call for +UCCP and for standard ObjectAlignmentInBytes
  constexpr int obj_alignment = BytesPerWord;
  const int log2_elemsize = ak_log2_elem_size();
  constexpr unsigned length_field_offset = (unsigned)ObjLayoutHelpers::markword_plus_klass_in_bytes<mode>();
  const unsigned first_element_offset = ak_first_element_offset_in_bytes(); // from klute, cannot calculate at build time

  // Load length from object
  const unsigned* const array_len_addr = (unsigned*)(obj->field_addr<unsigned>(length_field_offset));
  const unsigned array_length = (unsigned) (*array_len_addr);
  assert(array_length == (unsigned)((typeArrayOop)obj)->length(), "sanity");

  // Calculate size
  const unsigned size_in_bytes = (array_length << log2_elemsize) + first_element_offset;
  return align_up((size_t)size_in_bytes, obj_alignment) / HeapWordSize;
}

#endif // SHARE_OOPS_KLASSINFOLUTENTRY_INLINE_HPP
