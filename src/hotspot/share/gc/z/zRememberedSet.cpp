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

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBitMap.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zRememberedSet.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "utilities/globalDefinitions.hpp"

int ZRememberedSet::_current = 0;

void ZRememberedSet::flip() {
  _current ^= 1;
}

ZRememberedSet::ZRememberedSet()
  : _bitmap{ZMovableBitMap(), ZMovableBitMap()} {
  // Defer initialization of the bitmaps until the owning
  // page becomes old and its remembered set is initialized.
}

bool ZRememberedSet::is_initialized() const {
  return _bitmap[0].size() > 0;
}

void ZRememberedSet::initialize(size_t page_size) {
  assert(!is_initialized(), "precondition");
  const BitMap::idx_t size_in_bits = to_bit_size(page_size);
  _bitmap[0].initialize(size_in_bits, true /* clear */);
  _bitmap[1].initialize(size_in_bits, true /* clear */);
}

bool ZRememberedSet::is_cleared_current() const {
  return current()->is_empty();
}

bool ZRememberedSet::is_cleared_previous() const {
  return previous()->is_empty();
}

void ZRememberedSet::clear_previous() {
  previous()->clear_large();
}

void ZRememberedSet::swap_remset_bitmaps() {
  assert(previous()->is_empty(), "Previous remset bits should be empty when swapping");
  current()->iterate([&](BitMap::idx_t index) {
    previous()->set_bit(index);
    return true;
  });
  current()->clear_large();
}

ZBitMap::ReverseIterator ZRememberedSet::iterator_reverse_previous() {
  return ZBitMap::ReverseIterator(previous());
}

BitMap::Iterator ZRememberedSet::iterator_limited_current(uintptr_t offset, size_t size) {
  const size_t index = to_index(offset);;
  const size_t bit_size = to_bit_size(size);

  return BitMap::Iterator(*current(), index, index + bit_size);
}

ZBitMap::Iterator ZRememberedSet::iterator_limited_previous(uintptr_t offset, size_t size) {
  const size_t index = to_index(offset);;
  const size_t bit_size = to_bit_size(size);

  return BitMap::Iterator(*previous(), index, index + bit_size);
}

size_t ZRememberedSetContainingIterator::to_index(zaddress_unsafe addr) {
  const uintptr_t local_offset = _page->local_offset(addr);
  return ZRememberedSet::to_index(local_offset);
}

zaddress_unsafe ZRememberedSetContainingIterator::to_addr(BitMap::idx_t index) {
  const uintptr_t local_offset = ZRememberedSet::to_offset(index);
  return ZOffset::address_unsafe(_page->global_offset(local_offset));
}

ZRememberedSetContainingIterator::ZRememberedSetContainingIterator(ZPage* page)
  : _page(page),
    _remset_iter(page->remset_reverse_iterator_previous()),
    _obj(zaddress_unsafe::null),
    _obj_remset_iter(page->remset_reverse_iterator_previous()) {}

bool ZRememberedSetContainingIterator::next(ZRememberedSetContaining* containing) {
  // Note: to skip having to read the contents of the heap, when collecting the
  // containing information, this code doesn't read the size of the objects and
  // therefore doesn't filter out remset bits that belong to dead objects.
  // The (addr, addr_field) pair will contain the nearest live object, of a
  // given remset bit. Users of 'containing' need to do the filtering.

  BitMap::idx_t index;

  if (!is_null(_obj)) {
    // We've already found a remset bit and likely owning object in the main
    // iterator. Now use that information to skip having to search for the
    // same object multiple times.

    if (_obj_remset_iter.next(&index)) {
      containing->_field_addr = to_addr(index);
      containing->_addr = _obj;

      log_develop_trace(gc, remset)("Remset Containing Obj  index: " PTR_FORMAT " base: " PTR_FORMAT " field: " PTR_FORMAT, index, untype(containing->_addr), untype(containing->_field_addr));

      return true;
    } else {
      // No more remset bits in the scanned object
      _obj = zaddress_unsafe::null;
    }
  }

  // At this point, we don't know where the nearest earlier object starts.
  // Search for the next earlier remset bit, and then search for the likely
  // owning object.
  if (_remset_iter.next(&index)) {
    containing->_field_addr = to_addr(index);
    containing->_addr = _page->find_base((volatile zpointer*)untype(containing->_field_addr));

    if (is_null(containing->_addr)) {
      // Found no live object
      return false;
    }

    // Found live object. Not necessarily the one that originally owned the remset bit.
    const BitMap::idx_t obj_index = to_index(containing->_addr);

    log_develop_trace(gc, remset)("Remset Containing Main index: " PTR_FORMAT " base: " PTR_FORMAT " field: " PTR_FORMAT, index, untype(containing->_addr), untype(containing->_field_addr));

    // Don't scan inside the object in the main iterator
    _remset_iter.reset(obj_index);

    // Scan inside the object iterator
    _obj = containing->_addr;
    _obj_remset_iter.reset(obj_index, index);

    return true;
  }

  return false;
}

ZRememberedSetContainingInLiveIterator::ZRememberedSetContainingInLiveIterator(ZPage* page)
  : _iter(page),
    _addr(zaddress::null),
    _addr_size(0),
    _count(0),
    _count_skipped(0),
    _page(page) {}

bool ZRememberedSetContainingInLiveIterator::next(ZRememberedSetContaining* containing) {
  ZRememberedSetContaining local;
  while (_iter.next(&local)) {
    const zaddress local_addr = safe(local._addr);
    if (local_addr != _addr) {
      _addr = local_addr;
      _addr_size = ZUtils::object_size(_addr);
    }

    const size_t field_offset = safe(local._field_addr) - _addr;
    if (field_offset < _addr_size) {
      *containing = local;
      _count++;
      return true;
    }

    // Skip field outside object
    _count_skipped++;
  }

  // No more entries found
  return false;
}

void ZRememberedSetContainingInLiveIterator::print_statistics() const {
  _page->log_msg(" (remembered iter count: %zu skipped: %zu)", _count, _count_skipped);
}
