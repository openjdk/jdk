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

#ifndef SHARE_GC_Z_ZREMEMBEREDSET_HPP
#define SHARE_GC_Z_ZREMEMBEREDSET_HPP

#include "gc/z/zAddress.hpp"
#include "utilities/bitMap.hpp"

class OopClosure;
class ZPage;

class ZRememberedSetIterator {
private:
  BitMap* const _bitmap;
  BitMap::idx_t _pos;
  BitMap::idx_t _end;

public:
  ZRememberedSetIterator(BitMap* bitmap);
  ZRememberedSetIterator(BitMap* bitmap, BitMap::idx_t start, BitMap::idx_t end);

  bool next(size_t* index);
};

class ZRememberedSetReverseIterator {
private:
  BitMap* const _bitmap;
  BitMap::idx_t _start;
  BitMap::idx_t _pos;

public:
  ZRememberedSetReverseIterator(BitMap* bitmap);
  ZRememberedSetReverseIterator(BitMap* bitmap, BitMap::idx_t start, BitMap::idx_t end);

  void reset(BitMap::idx_t start, BitMap::idx_t end);
  void reset(BitMap::idx_t end);

  bool next(size_t* index);
};

struct ZRememberedSetContaining {
  zaddress_unsafe _field_addr;
  zaddress_unsafe _addr;
};

class ZRememberedSetContainingIterator {
private:
  ZPage* const                  _page;
  ZRememberedSetReverseIterator _remset_iter;

  zaddress_unsafe               _obj;
  ZRememberedSetReverseIterator _obj_remset_iter;

  size_t to_index(zaddress_unsafe addr);
  zaddress_unsafe to_addr(size_t index);

public:
  ZRememberedSetContainingIterator(ZPage* page);

  bool next(ZRememberedSetContaining* containing);
};

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
  friend class ZRememberedSetIterator;
  friend class ZRememberedSetContainingIterator;

private:
  static int _current;

  CHeapBitMap _bitmap[2];

  CHeapBitMap* current();
  const CHeapBitMap* current() const;

  CHeapBitMap* previous();

  template <typename Function>
  void iterate_bitmap(Function function, CHeapBitMap* bitmap);

  static uintptr_t to_offset(BitMap::idx_t index);
  static BitMap::idx_t to_index(uintptr_t offset);
  static BitMap::idx_t to_bit_size(size_t size);

public:
  static void flip();

  ZRememberedSet(size_t page_size);

  void resize(size_t page_size);
  void reset();

  bool get(uintptr_t offset) const;
  bool set(uintptr_t offset);
  void unset_non_par(uintptr_t offset);
  void unset_range_non_par(uintptr_t offset, size_t size);

  // Visit all set offsets.
  template <typename Function /* void(uintptr_t offset) */>
  void iterate(Function function);

  template <typename Function /* void(uintptr_t offset) */>
  void iterate_current(Function function);

  void clear_current();
  void clear_current(uintptr_t end_offset);
  void clear_previous();

  ZRememberedSetReverseIterator iterator_reverse();
  ZRememberedSetIterator iterator_current_limited(uintptr_t offset, size_t size);
};

#endif // SHARE_GC_Z_ZREMEMBEREDSET_HPP
