/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZREMEMBEREDSET_INLINE_HPP
#define SHARE_GC_Z_ZREMEMBEREDSET_INLINE_HPP

#include "gc/z/zRememberedSet.hpp"

inline CHeapBitMap* ZRememberedSet::current() {
  return &_bitmap[_current];
}

inline const CHeapBitMap* ZRememberedSet::current() const {
  return &_bitmap[_current];
}

inline CHeapBitMap* ZRememberedSet::previous() {
  return &_bitmap[_current ^ 1];
}

inline bool ZRememberedSet::get(uintptr_t local_offset) const {
  const size_t index = local_offset / oopSize;
  return current()->at(index);
}

inline bool ZRememberedSet::set(uintptr_t local_offset) {
  const size_t index = local_offset / oopSize;
  return current()->par_set_bit(index);
}

inline void ZRememberedSet::unset_non_par(uintptr_t local_offset) {
  const size_t index = local_offset / oopSize;
  current()->clear_bit(index);
}

inline void ZRememberedSet::unset_range_non_par(uintptr_t local_offset, size_t size) {
  const size_t index = local_offset / oopSize;
  const size_t size_in_bits = size / oopSize;
  current()->clear_range(index, index + size_in_bits);
}

template <typename Function>
void ZRememberedSet::oops_do_function(Function function, zoffset page_start) {
  previous()->iterate_f([&](BitMap::idx_t index) {
    const size_t local_offset = index * oopSize;
    const zoffset offset = page_start + local_offset;
    const zaddress addr = ZOffset::address(offset);

    function((volatile zpointer*)addr);

    return true;
  });
}

template <typename Function>
void ZRememberedSet::oops_do_current_function(Function function, zoffset page_start) {
  current()->iterate_f([&](BitMap::idx_t index) {
    const size_t local_offset = index * oopSize;
    const zoffset offset = page_start + local_offset;
    const zaddress addr = ZOffset::address(offset);

    function((volatile zpointer*)addr);

    return true;
  });
}

#endif // SHARE_GC_Z_ZREMEMBEREDSET_INLINE_HPP
