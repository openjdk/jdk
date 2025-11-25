/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZREMEMBEREDSET_HPP
#define SHARE_GC_Z_ZREMEMBEREDSET_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zBitMap.hpp"

class OopClosure;
class ZPage;

struct ZRememberedSetContaining {
  zaddress_unsafe _field_addr;
  zaddress_unsafe _addr;
};

// Iterates over all (object, oop fields) pairs where the field address has
// been marked as remembered, and fill in that information in a
// ZRememberedSetContaining
//
// Note that it's not guaranteed that _field_addr belongs to the recorded
// _addr. The entry could denote a stale remembered set field and _addr could
// just be the nearest object. The users are responsible for filtering that
// out.
class ZRememberedSetContainingIterator {
private:
  ZPage* const             _page;
  ZBitMap::ReverseIterator _remset_iter;

  zaddress_unsafe          _obj;
  ZBitMap::ReverseIterator _obj_remset_iter;

  size_t to_index(zaddress_unsafe addr);
  zaddress_unsafe to_addr(BitMap::idx_t index);

public:
  ZRememberedSetContainingIterator(ZPage* page);

  bool next(ZRememberedSetContaining* containing);
};

// Like ZRememberedSetContainingIterator, but with stale remembered set fields
// filtered out.
class ZRememberedSetContainingInLiveIterator {
private:
  ZRememberedSetContainingIterator _iter;
  zaddress                         _addr;
  size_t                           _addr_size;
  size_t                           _count;
  size_t                           _count_skipped;
  ZPage* const                     _page;

public:
  ZRememberedSetContainingInLiveIterator(ZPage* page);

  bool next(ZRememberedSetContaining* containing);

  void print_statistics() const;
};

// The remembered set of a ZPage.
//
// There's one bit per potential object field address within the ZPage.
//
// New entries are added to the "current" active bitmap, while the
// "previous" bitmap is used by the GC to find pointers from old
// gen to young gen.
class ZRememberedSet {
  friend class ZRememberedSetContainingIterator;

public:
  static int _current;

  ZMovableBitMap _bitmap[2];

  CHeapBitMap* current();
  const CHeapBitMap* current() const;

  CHeapBitMap* previous();
  const CHeapBitMap* previous() const;

  template <typename Function>
  void iterate_bitmap(Function function, CHeapBitMap* bitmap);

  static uintptr_t to_offset(BitMap::idx_t index);
  static BitMap::idx_t to_index(uintptr_t offset);
  static BitMap::idx_t to_bit_size(size_t size);

public:
  static void flip();

  ZRememberedSet();

  bool is_initialized() const;
  void initialize(size_t page_size);

  bool at_current(uintptr_t offset) const;
  bool at_previous(uintptr_t offset) const;
  bool set_current(uintptr_t offset);
  void unset_non_par_current(uintptr_t offset);
  void unset_range_non_par_current(uintptr_t offset, size_t size);

  // Visit all set offsets.
  template <typename Function /* void(uintptr_t offset) */>
  void iterate_previous(Function function);

  template <typename Function /* void(uintptr_t offset) */>
  void iterate_current(Function function);

  bool is_cleared_current() const;
  bool is_cleared_previous() const;

  void clear_previous();
  void swap_remset_bitmaps();

  ZBitMap::ReverseIterator iterator_reverse_previous();
  BitMap::Iterator iterator_limited_current(uintptr_t offset, size_t size);
  BitMap::Iterator iterator_limited_previous(uintptr_t offset, size_t size);
};

#endif // SHARE_GC_Z_ZREMEMBEREDSET_HPP
