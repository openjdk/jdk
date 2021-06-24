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

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zRememberSet.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/globalDefinitions.hpp"

int ZRememberSet::_current = 0;

void ZRememberSet::flip() {
  _current ^= 1;
}

ZRememberSet::ZRememberSet(size_t page_size) :
    _bitmap() {
  resize(page_size);
}

void ZRememberSet::resize(size_t page_size) {
  const BitMap::idx_t size_in_bits = page_size / oopSize;
  _bitmap[0].reinitialize(size_in_bits, false /* clear */);
  _bitmap[1].reinitialize(size_in_bits, false /* clear */);
}

void ZRememberSet::reset() {
  clear_current();
  clear_previous();
}

void ZRememberSet::clear_current() {
  current()->clear_large();
}

void ZRememberSet::clear_current(uintptr_t local_offset) {
  current()->clear_range(0, local_offset / oopSize);
}

void ZRememberSet::clear_previous() {
  previous()->clear_large();
}

ZRememberSetReverseIterator ZRememberSet::iterator_reverse() {
  return ZRememberSetReverseIterator(previous());
}

ZRememberSetIterator ZRememberSet::iterator_current_limited(uintptr_t local_offset, size_t size) {
  const size_t index = local_offset / oopSize;
  const size_t bit_size = size / oopSize;

  return ZRememberSetIterator(current(), index, index + bit_size);
}

ZRememberSetIterator::ZRememberSetIterator(BitMap* bitmap) :
    ZRememberSetIterator(bitmap, 0, bitmap->size() - 1) {}

ZRememberSetIterator::ZRememberSetIterator(BitMap* bitmap, BitMap::idx_t start, BitMap::idx_t end) :
    _bitmap(bitmap),
    _pos(start),
    _end(end) {}

bool ZRememberSetIterator::next(size_t* index) {
  BitMap::idx_t res = _bitmap->get_next_one_offset(_pos, _end);
  if (res == _end) {
    return false;
  }

  _pos = res + 1;

  *index = res;
  return true;
}

ZRememberSetReverseIterator::ZRememberSetReverseIterator(BitMap* bitmap) :
    ZRememberSetReverseIterator(bitmap, 0, bitmap->size() - 1) {}

ZRememberSetReverseIterator::ZRememberSetReverseIterator(BitMap* bitmap, BitMap::idx_t start, BitMap::idx_t end) :
    _bitmap(bitmap),
    _start(start),
    _pos(end) {}

void ZRememberSetReverseIterator::reset(BitMap::idx_t start, BitMap::idx_t end) {
  _start = start;
  _pos = end;
}

void ZRememberSetReverseIterator::reset(BitMap::idx_t end) {
  _pos = end;
}

bool ZRememberSetReverseIterator::next(size_t* index) {
  BitMap::idx_t res = _bitmap->get_prev_one_offset(_start, _pos);
  if (res == size_t(-1)) {
    return false;
  }

  assert(_pos > _start, "Shouldn't find bits at the start of ranges");
  _pos = res - 1;

  *index = res;
  return true;
}

size_t ZRememberSetContainingIterator::to_index(zaddress_unsafe addr) {
  zoffset offset = ZAddress::offset(addr);
  return (offset - _page->start()) / oopSize;
}

zaddress_unsafe ZRememberSetContainingIterator::to_addr(size_t index) {
  return ZOffset::address_unsafe(_page->start() + index * oopSize);
}

ZRememberSetContainingIterator::ZRememberSetContainingIterator(ZPage* page) :
    _page(page),
    _remset_iter(page->remset_reverse_iterator()),
    _obj(zaddress_unsafe::null),
    _obj_remset_iter(page->remset_reverse_iterator()) {}

bool ZRememberSetContainingIterator::next(ZRememberSetContaining* containing) {
  // Note: to skip having to read the contents of the heap, when collecting the
  // containing information, this code doesn't read the size of the objects and
  // therefore doesn't filter out remset bits that belong to dead objects.
  // The (addr, addr_field) pair will contain the nearest live object, of a
  // given remset bit. Users of 'containing' needs to do the filtering.

  size_t index;

  if (!is_null(_obj)) {
    if (_obj_remset_iter.next(&index)) {
      containing->_field_addr = to_addr(index);
      containing->_addr = _obj;

      log_trace(gc, remset)("Remset Containing Obj  index: " PTR_FORMAT " base: " PTR_FORMAT " field: " PTR_FORMAT, index, untype(containing->_addr), untype(containing->_field_addr));

      _obj_remset_iter.reset(to_index(containing->_field_addr) - 1);
      return true;
    } else {
      // No more remset bits in the obj
      _obj = zaddress_unsafe::null;
    }
  }

  if (_remset_iter.next(&index)) {
    containing->_field_addr = to_addr(index);
    containing->_addr = _page->find_base((volatile zpointer*)untype(containing->_field_addr));

    if (is_null(containing->_addr)) {
      // Found no live object
      return false;
    }

    // Found live object. Not necessarily the one that originally owned the remset bit.

    log_trace(gc, remset)("Remset Containing Main index: " PTR_FORMAT " base: " PTR_FORMAT " field: " PTR_FORMAT, index, untype(containing->_addr), untype(containing->_field_addr));

    // Don't scan inside the object in the main iterator
    // Note: Can't use -1, since this might be the first object in the page.
    _remset_iter.reset(to_index(containing->_addr));

    // Scan inside the object iterator
    _obj = containing->_addr;
    _obj_remset_iter.reset(to_index(containing->_addr), to_index(containing->_field_addr) - 1);

    return true;
  }

  return false;
}

ZRememberSetContainingInLiveIterator::ZRememberSetContainingInLiveIterator(ZPage* page) :
    _iter(page),
    _addr(zaddress::null),
    _addr_size(0),
    _count(0),
    _count_skipped(0),
    _page(page) {}

bool ZRememberSetContainingInLiveIterator::next(ZRememberSetContaining* containing) {
  ZRememberSetContaining local;
  while (_iter.next(&local)) {
    zaddress local_addr = safe(local._addr);
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

void ZRememberSetContainingInLiveIterator::print_statistics() const {
  _page->log_msg(err_msg(" (remembered iter count: " SIZE_FORMAT " skipped: " SIZE_FORMAT ")", _count, _count_skipped));
}
