/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/z/zAddress.hpp"
#include "gc/z/zGranuleMap.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageTable.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/debug.hpp"

// When scanning the remembered set during the young generation marking, we
// want to visit all old pages. And we want that to be done in parallel and
// fast.
//
// Walking over the entire page table and letting the workers claim indices
// have been shown to have scalability issues.
//
// So, we have the "found old" optimization, which allows us to perform much
// fewer claims (order of old pages, instead of order of slots in the page
// table), and it allows us to read fewer pages.
//
// The set of "found old pages" isn't precise, and can contain stale entries
// referring to slots of freed pages, or even slots where young pages have
// been installed. However, it will not lack any of the old pages.
//
// The data is maintained very similar to when and how we maintain the
// remembered set bits: We keep two separates sets, one for read-only access
// by the young marking, and a currently active set where we register new
// pages. When pages get relocated, or die, the page table slot for that page
// must be cleared. This clearing is done just like we do with the remset
// scanning: The old entries are not copied to the current active set, only
// slots that were found to actually contain old pages are registered in the
// active set.

ZPageTable::FoundOld::FoundOld() :
    // Array initialization requires copy constructors, which CHeapBitMap
    // doesn't provide. Instantiate two instances, and populate an array
    // with pointers to the two instances.
    _allocated_bitmap_0{ZAddressOffsetMax >> ZGranuleSizeShift, mtGC, true /* clear */},
    _allocated_bitmap_1{ZAddressOffsetMax >> ZGranuleSizeShift, mtGC, true /* clear */},
    _bitmaps{&_allocated_bitmap_0, &_allocated_bitmap_1},
    _current{0} {}

void ZPageTable::FoundOld::flip() {
  _current ^= 1;
}

void ZPageTable::FoundOld::clear_previous() {
  previous_bitmap()->clear_large();
}

void ZPageTable::FoundOld::register_page(ZPage* page) {
  assert(page->is_old(), "Only register old pages");
  current_bitmap()->par_set_bit(untype(page->start()) >> ZGranuleSizeShift, memory_order_relaxed);
}

BitMap* ZPageTable::FoundOld::current_bitmap() {
  return _bitmaps[_current];
}

BitMap* ZPageTable::FoundOld::previous_bitmap() {
  return _bitmaps[_current ^ 1];
}

ZOldPagesParallelIterator::ZOldPagesParallelIterator(ZPageTable* page_table) :
    _page_table(page_table),
    _claimed(0) {}

// This iterator uses the "found old" optimization.
bool ZOldPagesParallelIterator::next(ZPage** page_addr)  {
  BitMap* const bm = _page_table->_found_old.previous_bitmap();

  BitMap::idx_t prev = Atomic::load(&_claimed);

  for (;;) {
    if (prev == bm->size()) {
      return false;
    }

    const BitMap::idx_t page_index = bm->get_next_one_offset(_claimed);
    if (page_index == bm->size()) {
      Atomic::cmpxchg(&_claimed, prev, page_index, memory_order_relaxed);
      return false;
    }

    const BitMap::idx_t res = Atomic::cmpxchg(&_claimed, prev, page_index + 1, memory_order_relaxed);
    if (res != prev) {
      // Someone else claimed
      prev = res;
      continue;
    }

    // Found bit

    ZPage* const page = _page_table->at(page_index);
    if (page == nullptr) {
      continue;
    }

    // Found page

    if (!page->is_old()) {
      continue;
    }

    // Found old page

    *page_addr = page;
    return true;
  }
}

ZPageTable::ZPageTable() :
    _map(ZAddressOffsetMax),
    _found_old() {}

void ZPageTable::insert(ZPage* page) {
  const zoffset offset = page->start();
  const size_t size = page->size();

  // Make sure a newly created page is
  // visible before updating the page table.
  OrderAccess::storestore();

  assert(_map.get(offset) == nullptr, "Invalid entry");
  _map.put(offset, size, page);

  if (page->is_old()) {
    register_found_old(page);
  }
}

void ZPageTable::remove(ZPage* page) {
  const zoffset offset = page->start();
  const size_t size = page->size();

  assert(_map.get(offset) == page, "Invalid entry");
  _map.put(offset, size, nullptr);
}

void ZPageTable::replace(ZPage* old_page, ZPage* new_page) {
  const zoffset offset = old_page->start();
  const size_t size = old_page->size();

  assert(_map.get(offset) == old_page, "Invalid entry");
  _map.release_put(offset, size, new_page);

  if (new_page->is_old()) {
    register_found_old(new_page);
  }
}

void ZPageTable::flip_found_old_sets() {
  _found_old.flip();
}

void ZPageTable::clear_found_old_previous_set() {
  _found_old.clear_previous();
}

void ZPageTable::register_found_old(ZPage* page) {
  assert(page->is_old(), "Should only register old pages");
  _found_old.register_page(page);
}

ZOldPagesParallelIterator ZPageTable::old_pages_parallel_iterator() {
  return ZOldPagesParallelIterator(this);
}

ZGenerationPagesParallelIterator::ZGenerationPagesParallelIterator(const ZPageTable* page_table, ZGenerationId id, ZPageAllocator* page_allocator) :
    _iterator(page_table),
    _generation_id(id),
    _page_allocator(page_allocator) {
  _page_allocator->enable_safe_destroy();
  _page_allocator->enable_safe_recycle();
}

ZGenerationPagesParallelIterator::~ZGenerationPagesParallelIterator() {
  _page_allocator->disable_safe_recycle();
  _page_allocator->disable_safe_destroy();
}

ZGenerationPagesIterator::ZGenerationPagesIterator(const ZPageTable* page_table, ZGenerationId id, ZPageAllocator* page_allocator) :
    _iterator(page_table),
    _generation_id(id),
    _page_allocator(page_allocator) {
  _page_allocator->enable_safe_destroy();
  _page_allocator->enable_safe_recycle();
}

ZGenerationPagesIterator::~ZGenerationPagesIterator() {
  _page_allocator->disable_safe_recycle();
  _page_allocator->disable_safe_destroy();
}
