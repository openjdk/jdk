/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZFORWARDING_INLINE_HPP
#define SHARE_GC_Z_ZFORWARDING_INLINE_HPP

#include "gc/z/zForwarding.hpp"

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zAttachedArray.inline.hpp"
#include "gc/z/zForwardingAllocator.inline.hpp"
#include "gc/z/zHash.inline.hpp"
#include "gc/z/zHeap.hpp"
#include "gc/z/zIterator.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"
#include "utilities/powerOfTwo.hpp"

inline uint32_t ZForwarding::nentries(const ZPage* page) {
  // The number returned by the function is used to size the hash table of
  // forwarding entries for this page. This hash table uses linear probing.
  // The size of the table must be a power of two to allow for quick and
  // inexpensive indexing/masking. The table is also sized to have a load
  // factor of 50%, i.e. sized to have double the number of entries actually
  // inserted, to allow for good lookup/insert performance.
  return round_up_power_of_2(page->live_objects() * 2);
}

inline ZForwarding* ZForwarding::alloc(ZForwardingAllocator* allocator, ZPage* page, bool promote_all) {
  const size_t nentries = ZForwarding::nentries(page);
  void* const addr = AttachedArray::alloc(allocator, nentries);
  return ::new (addr) ZForwarding(page, nentries, promote_all);
}

inline ZPageAge ZForwarding::compute_age_to(ZPageAge age_from, bool promote_all) {
  if (promote_all) {
    return ZPageAge::old;
  } else if (age_from == ZPageAge::eden) {
    return ZPageAge::survivor;
  } else {
    return ZPageAge::old;
  }
}

inline ZForwarding::ZForwarding(ZPage* page, size_t nentries, bool promote_all) :
    _virtual(page->virtual_memory()),
    _object_alignment_shift(page->object_alignment_shift()),
    _entries(nentries),
    _page(page),
    _generation_id(page->generation_id()),
    _age_from(page->age()),
    _age_to(compute_age_to(_age_from, promote_all)),
    _claimed(false),
    _ref_lock(),
    _ref_count(1),
    _ref_abort(false),
    _remset_scanned(false),
    _in_place(false),
    _in_place_clear_remset_watermark(0),
    _in_place_top_at_start(),
    _in_place_thread(NULL) {}

inline uint8_t ZForwarding::type() const {
  return _page->type();
}

inline ZGenerationId ZForwarding::generation_id() const {
  return _generation_id;
}

inline ZPageAge ZForwarding::age_from() const {
  return _age_from;
}

inline ZPageAge ZForwarding::age_to() const {
  return _age_to;
}

inline zoffset ZForwarding::start() const {
  return _virtual.start();
}

inline size_t ZForwarding::size() const {
  return _virtual.size();
}

inline size_t ZForwarding::object_alignment_shift() const {
  return _object_alignment_shift;
}

template <typename Function>
inline void ZForwarding::object_iterate(Function function) {
  ZObjectClosure<Function> cl(function);
  _page->object_iterate(function);
}

template <typename Function>
inline void ZForwarding::object_iterate_forwarded_via_livemap(Function function) {
  assert(!in_place_relocation(), "Not allowed to use livemap iteration");

  object_iterate([&](oop obj) {
    // Find to-object
    zaddress_unsafe from_addr = to_zaddress_unsafe(obj);
    zaddress to_addr = this->find(from_addr);
    oop to_obj = to_oop(to_addr);

    // Apply function
    function(to_obj);
  });
}

template <typename Function>
inline void ZForwarding::object_iterate_forwarded_via_table(Function function) {
  for (ZForwardingCursor i = 0; i < _entries.length(); i++) {
    const ZForwardingEntry entry = at(&i);
    if (!entry.populated()) {
      // Skip empty entries
      continue;
    }

    // Find to-object
    zoffset to_offset = to_zoffset(entry.to_offset());
    zaddress to_addr = ZOffset::address(to_offset);
    oop to_obj = to_oop(to_addr);

    // Apply function
    function(to_obj);
  }
}

template <typename Function>
inline void ZForwarding::object_iterate_forwarded(Function function) {
  if (in_place_relocation()) {
    // The original objects are not available anymore, can't use the livemap
    object_iterate_forwarded_via_table(function);
  } else {
    object_iterate_forwarded_via_livemap(function);
  }
}

template <typename Function>
void ZForwarding::oops_do_in_forwarded(Function function) {
  object_iterate_forwarded([&](oop to_obj) {
    ZIterator::basic_oop_iterate_safe(to_obj, function);
  });
}

template <typename Function>
void ZForwarding::oops_do_in_forwarded_via_table(Function function) {
  object_iterate_forwarded_via_table([&](oop to_obj) {
    ZIterator::basic_oop_iterate_safe(to_obj, function);
  });
}

inline bool ZForwarding::in_place_relocation() const {
  assert(Atomic::load(&_ref_count) != 0, "The page has been released/detached");
  return _in_place;
}

inline ZForwardingEntry* ZForwarding::entries() const {
  return _entries(this);
}

inline ZForwardingEntry ZForwarding::at(ZForwardingCursor* cursor) const {
  // Load acquire for correctness with regards to
  // accesses to the contents of the forwarded object.
  return Atomic::load_acquire(entries() + *cursor);
}

inline ZForwardingEntry ZForwarding::first(uintptr_t from_index, ZForwardingCursor* cursor) const {
  const size_t mask = _entries.length() - 1;
  const size_t hash = ZHash::uint32_to_uint32((uint32_t)from_index);
  *cursor = hash & mask;
  return at(cursor);
}

inline ZForwardingEntry ZForwarding::next(ZForwardingCursor* cursor) const {
  const size_t mask = _entries.length() - 1;
  *cursor = (*cursor + 1) & mask;
  return at(cursor);
}

inline zaddress ZForwarding::find(zaddress_unsafe addr) {
  const uintptr_t from_index = (ZAddress::offset(addr) - start()) >> object_alignment_shift();
  ZForwardingCursor cursor;
  const ZForwardingEntry entry = find(from_index, &cursor);
  return entry.populated() ? ZOffset::address(to_zoffset(entry.to_offset())) : zaddress::null;
}

inline ZForwardingEntry ZForwarding::find(uintptr_t from_index, ZForwardingCursor* cursor) const {
  // Reading entries in the table races with the atomic CAS done for
  // insertion into the table. This is safe because each entry is at
  // most updated once (from zero to something else).
  ZForwardingEntry entry = first(from_index, cursor);
  while (entry.populated()) {
    if (entry.from_index() == from_index) {
      // Match found, return matching entry
      return entry;
    }

    entry = next(cursor);
  }

  // Match not found, return empty entry
  return entry;
}

inline zoffset ZForwarding::insert(uintptr_t from_index, zoffset to_offset, ZForwardingCursor* cursor) {
  const ZForwardingEntry new_entry(from_index, untype(to_offset));
  const ZForwardingEntry old_entry; // Empty

  for (;;) {
    const ZForwardingEntry prev_entry = Atomic::cmpxchg(entries() + *cursor, old_entry, new_entry, memory_order_release);
    if (!prev_entry.populated()) {
      // Success
      return to_offset;
    }

    // Find next empty or matching entry
    ZForwardingEntry entry = at(cursor);
    while (entry.populated()) {
      if (entry.from_index() == from_index) {
        // Match found, return already inserted address
        return to_zoffset(entry.to_offset());
      }

      entry = next(cursor);
    }
  }
}

inline bool ZForwarding::get_and_set_remset_scanned() {
  if (_remset_scanned) {
    return true;
  }
  _remset_scanned = true;
  return false;
}

#endif // SHARE_GC_Z_ZFORWARDING_INLINE_HPP
