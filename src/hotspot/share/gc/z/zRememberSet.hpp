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

#ifndef SHARE_GC_Z_ZREMEMBERSET_HPP
#define SHARE_GC_Z_ZREMEMBERSET_HPP

#include "gc/z/zAddress.hpp"
#include "utilities/bitMap.hpp"

class OopClosure;
class ZPage;

class ZRememberSetIterator {
private:
  BitMap* const _bitmap;
  BitMap::idx_t _pos;
  BitMap::idx_t _end;

public:
  ZRememberSetIterator(BitMap* bitmap);
  ZRememberSetIterator(BitMap* bitmap, BitMap::idx_t start, BitMap::idx_t end);

  bool next(size_t* index);
};

class ZRememberSetReverseIterator {
private:
  BitMap* const _bitmap;
  BitMap::idx_t _start;
  BitMap::idx_t _pos;

public:
  ZRememberSetReverseIterator(BitMap* bitmap);
  ZRememberSetReverseIterator(BitMap* bitmap, BitMap::idx_t start, BitMap::idx_t end);

  void reset(BitMap::idx_t start, BitMap::idx_t end);
  void reset(BitMap::idx_t end);

  bool next(size_t* index);
};

struct ZRememberSetContaining {
  zaddress_unsafe _field_addr;
  zaddress_unsafe _addr;
};

class ZRememberSetContainingIterator {
private:
  ZPage* const                _page;
  ZRememberSetReverseIterator _remset_iter;

  zaddress_unsafe             _obj;
  ZRememberSetReverseIterator _obj_remset_iter;

  size_t to_index(zaddress_unsafe addr);
  zaddress_unsafe to_addr(size_t index);

public:
  ZRememberSetContainingIterator(ZPage* page);

  bool next(ZRememberSetContaining* containing);
};

class ZRememberSetContainingInLiveIterator {
private:
  ZRememberSetContainingIterator _iter;
  zaddress                       _addr;
  size_t                         _addr_size;
  size_t                         _count;
  size_t                         _count_skipped;
  ZPage* const                   _page;

public:
  ZRememberSetContainingInLiveIterator(ZPage* page);

  bool next(ZRememberSetContaining* containing);

  void print_statistics() const;
};

class ZRememberSet {
private:
  static int _current;

  CHeapBitMap _bitmap[2];

  CHeapBitMap* current();
  const CHeapBitMap* current() const;

public:
  static void flip();

  ZRememberSet(size_t page_size);

  CHeapBitMap* previous();

  void resize(size_t page_size);
  void reset();

  bool get(uintptr_t local_offset) const;
  bool set(uintptr_t local_offset);
  void unset_non_par(uintptr_t local_offset);
  void unset_range_non_par(uintptr_t local_offset, size_t size);

  template <typename Function>
  void oops_do_function(Function function, zoffset page_start);

  template <typename Function>
  void oops_do_current_function(Function function, zoffset page_start);

  void clear_current();
  void clear_current(uintptr_t local_offset);
  void clear_previous();

  ZRememberSetReverseIterator iterator_reverse();
  ZRememberSetIterator iterator_current_limited(uintptr_t local_offset, size_t size);
};

#endif // SHARE_GC_Z_ZREMEMBERSET_HPP
