/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZREMEMBERED_HPP
#define SHARE_GC_Z_ZREMEMBERED_HPP

#include "gc/z/zAddress.hpp"
#include "utilities/bitMap.hpp"

template <typename T> class GrowableArrayView;
class OopClosure;
class ZForwarding;
class ZForwardingTable;
class ZMark;
class ZPage;
class ZPageAllocator;
class ZPageTable;
class ZRemsetTableIterator;
struct ZRememberedSetContaining;
struct ZRemsetTableEntry;

class ZRemembered {
  friend class ZRememberedScanMarkFollowTask;
  friend class ZRemsetTableIterator;

private:
  ZPageTable* const             _page_table;
  const ZForwardingTable* const _old_forwarding_table;
  ZPageAllocator* const         _page_allocator;

  // Optimization aid for faster old pages iteration
  struct FoundOld {
    CHeapBitMap   _allocated_bitmap_0;
    CHeapBitMap   _allocated_bitmap_1;
    BitMap* const _bitmaps[2];
    int           _current;

    FoundOld();

    void flip();
    void clear_previous();

    void register_page(ZPage* page);

    BitMap* current_bitmap();
    BitMap* previous_bitmap();
  } _found_old;

  // Old pages iteration optimization aid
  void flip_found_old_sets();
  void clear_found_old_previous_set();

  template <typename Function>
  void oops_do_forwarded_via_containing(GrowableArrayView<ZRememberedSetContaining>* array, Function function) const;

  bool should_scan_page(ZPage* page) const;

  bool scan_page_and_clear_remset(ZPage* page) const;
  bool scan_forwarding(ZForwarding* forwarding, void* context) const;

public:
  ZRemembered(ZPageTable* page_table,
              const ZForwardingTable* old_forwarding_table,
              ZPageAllocator* page_allocator);

  // Add to remembered set
  void remember(volatile zpointer* p) const;

  // Scan all remembered sets and follow
  void scan_and_follow(ZMark* mark);

  // Save the current remembered sets,
  // and switch over to empty remembered sets.
  void flip();

  // Scan a remembered set entry
  bool scan_field(volatile zpointer* p) const;

  // Verification
  bool is_remembered(volatile zpointer* p) const;

  // Register pages with the remembered set
  void register_found_old(ZPage* page);

  // Remap the current remembered set
  void remap_current(ZRemsetTableIterator* iter);
};

// This iterator uses the "found old" optimization to skip having to iterate
// over the entire page table. Make sure to check where and how the FoundOld
// data is cycled before using this iterator.
class ZRemsetTableIterator {
private:
  ZRemembered* const            _remembered;
  BitMap* const                 _bm;
  ZPageTable* const             _page_table;
  const ZForwardingTable* const _old_forwarding_table;
  volatile BitMap::idx_t        _claimed;

public:
  ZRemsetTableIterator(ZRemembered* remembered, bool previous);

  bool next(ZRemsetTableEntry* entry_addr);
};

#endif // SHARE_GC_Z_ZREMEMBERED_HPP
