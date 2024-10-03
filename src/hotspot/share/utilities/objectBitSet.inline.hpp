/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_OBJECTBITSET_INLINE_HPP
#define SHARE_UTILITIES_OBJECTBITSET_INLINE_HPP

#include "utilities/objectBitSet.hpp"

#include "memory/memRegion.hpp"
#include "utilities/bitMap.inline.hpp"

template<MemTag MT>
ObjectBitSet<MT>::BitMapFragment::BitMapFragment(uintptr_t granule, BitMapFragment* next) :
        _bits(_bitmap_granularity_size >> LogMinObjAlignmentInBytes, MT, true /* clear */),
        _next(next) {
}

template<MemTag MT>
ObjectBitSet<MT>::ObjectBitSet() :
        _bitmap_fragments(32, 8*K),
        _fragment_list(nullptr),
        _last_fragment_bits(nullptr),
        _last_fragment_granule(UINTPTR_MAX) {
}

template<MemTag MT>
ObjectBitSet<MT>::~ObjectBitSet() {
  BitMapFragment* current = _fragment_list;
  while (current != nullptr) {
    BitMapFragment* next = current->next();
    delete current;
    current = next;
  }
  // destructors for ResourceHashtable base deletes nodes, and
  // ResizeableResourceHashtableStorage deletes the table.
}

template<MemTag MT>
inline BitMap::idx_t ObjectBitSet<MT>::addr_to_bit(uintptr_t addr) const {
  return (addr & _bitmap_granularity_mask) >> LogMinObjAlignmentInBytes;
}

template<MemTag MT>
inline CHeapBitMap* ObjectBitSet<MT>::get_fragment_bits(uintptr_t addr) {
  uintptr_t granule = addr >> _bitmap_granularity_shift;
  if (granule == _last_fragment_granule) {
    return _last_fragment_bits;
  }
  CHeapBitMap* bits = nullptr;

  CHeapBitMap** found = _bitmap_fragments.get(granule);
  if (found != nullptr) {
    bits = *found;
  } else {
    BitMapFragment* fragment = new BitMapFragment(granule, _fragment_list);
    bits = fragment->bits();
    _fragment_list = fragment;
    _bitmap_fragments.put(granule, bits);
    _bitmap_fragments.maybe_grow();
  }

  _last_fragment_bits = bits;
  _last_fragment_granule = granule;

  return bits;
}

template<MemTag MT>
inline void ObjectBitSet<MT>::mark_obj(uintptr_t addr) {
  CHeapBitMap* bits = get_fragment_bits(addr);
  const BitMap::idx_t bit = addr_to_bit(addr);
  bits->set_bit(bit);
}

template<MemTag MT>
inline bool ObjectBitSet<MT>::is_marked(uintptr_t addr) {
  CHeapBitMap* bits = get_fragment_bits(addr);
  const BitMap::idx_t bit = addr_to_bit(addr);
  return bits->at(bit);
}

#endif // SHARE_UTILITIES_OBJECTBITSET_INLINE_HPP
