/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/atomicAccess.hpp"
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

inline ZForwarding* ZForwarding::alloc(ZForwardingAllocator* allocator, ZPage* page, ZPageAge to_age) {
  const size_t nentries = ZForwarding::nentries(page);
  void* const addr = AttachedArray::alloc(allocator, nentries);
  return ::new (addr) ZForwarding(page, to_age, nentries);
}

inline ZForwarding::ZForwarding(ZPage* page, ZPageAge to_age, size_t nentries)
  : _virtual(page->virtual_memory()),
    _object_alignment_shift(page->object_alignment_shift()),
    _entries(nentries),
    _page(page),
    _partition_id(page->single_partition_id()),
    _from_age(page->age()),
    _to_age(to_age),
    _claimed(false),
    _ref_lock(),
    _ref_count(1),
    _done(false),
    _relocated_remembered_fields_state(ZPublishState::none),
    _relocated_remembered_fields_array(),
    _relocated_remembered_fields_publish_young_seqnum(0),
    _in_place(false),
    _in_place_top_at_start(),
    _in_place_thread(nullptr) {}

inline ZPageType ZForwarding::type() const {
  return _page->type();
}

inline ZPageAge ZForwarding::from_age() const {
  return _from_age;
}

inline ZPageAge ZForwarding::to_age() const {
  return _to_age;
}

inline zoffset ZForwarding::start() const {
  return _virtual.start();
}

inline zoffset_end ZForwarding::end() const {
  return _virtual.end();
}

inline size_t ZForwarding::size() const {
  return _virtual.size();
}

inline size_t ZForwarding::object_alignment_shift() const {
  return _object_alignment_shift;
}

inline uint32_t ZForwarding::partition_id() const {
  return _partition_id;
}

inline bool ZForwarding::is_promotion() const {
  return _from_age != ZPageAge::old &&
         _to_age == ZPageAge::old;
}

template <typename Function>
inline void ZForwarding::object_iterate(Function function) {
  ZObjectClosure<Function> cl(function);
  _page->object_iterate(function);
}

template <typename Function>
inline void ZForwarding::address_unsafe_iterate_via_table(Function function) {
  for (ZForwardingCursor i = 0; i < _entries.length(); i++) {
    const ZForwardingEntry entry = at(&i);
    if (!entry.populated()) {
      // Skip empty entries
      continue;
    }

    // Find to-object

    const zoffset from_offset = start() + (entry.from_index() << object_alignment_shift());
    const zaddress_unsafe from_addr = ZOffset::address_unsafe(from_offset);

    // Apply function
    function(from_addr);
  }
}

template <typename Function>
inline void ZForwarding::object_iterate_forwarded_via_livemap(Function function) {
  assert(!in_place_relocation(), "Not allowed to use livemap iteration");

  object_iterate([&](oop obj) {
    // Find to-object
    const zaddress_unsafe from_addr = to_zaddress_unsafe(obj);
    const zaddress to_addr = this->find(from_addr);
    const oop to_obj = to_oop(to_addr);

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
    const zoffset to_offset = to_zoffset(entry.to_offset());
    const zaddress to_addr = ZOffset::address(to_offset);
    const oop to_obj = to_oop(to_addr);

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
  assert(_ref_count.load_relaxed() != 0, "The page has been released/detached");
  return _in_place;
}

inline ZForwardingEntry* ZForwarding::entries() const {
  return _entries(this);
}

inline ZForwardingEntry ZForwarding::at(ZForwardingCursor* cursor) const {
  // Load acquire for correctness with regards to
  // accesses to the contents of the forwarded object.
  return AtomicAccess::load_acquire(entries() + *cursor);
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

inline uintptr_t ZForwarding::index(zoffset from_offset) {
  return (from_offset - start()) >> object_alignment_shift();
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

inline zaddress ZForwarding::find(zoffset from_offset, ZForwardingCursor* cursor) {
  const uintptr_t from_index = index(from_offset);
  const ZForwardingEntry entry = find(from_index, cursor);
  return entry.populated() ? ZOffset::address(to_zoffset(entry.to_offset())) : zaddress::null;
}

inline zaddress ZForwarding::find(zaddress from_addr, ZForwardingCursor* cursor) {
  return find(ZAddress::offset(from_addr), cursor);
}

inline zaddress ZForwarding::find(zaddress_unsafe from_addr, ZForwardingCursor* cursor) {
  return find(ZAddress::offset(from_addr), cursor);
}

inline zaddress ZForwarding::find(zaddress_unsafe from_addr) {
  ZForwardingCursor cursor;
  return find(from_addr, &cursor);
}

inline zoffset ZForwarding::insert(uintptr_t from_index, zoffset to_offset, ZForwardingCursor* cursor) {
  const ZForwardingEntry new_entry(from_index, untype(to_offset));
  const ZForwardingEntry old_entry; // Empty

  // Make sure that object copy is finished
  // before forwarding table installation
  OrderAccess::release();

  for (;;) {
    const ZForwardingEntry prev_entry = AtomicAccess::cmpxchg(entries() + *cursor, old_entry, new_entry, memory_order_relaxed);
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

inline zaddress ZForwarding::insert(zoffset from_offset, zaddress to_addr, ZForwardingCursor* cursor) {
  const uintptr_t from_index = index(from_offset);
  const zoffset to_offset = ZAddress::offset(to_addr);
  const zoffset to_offset_final = insert(from_index, to_offset, cursor);
  return ZOffset::address(to_offset_final);
}

inline zaddress ZForwarding::insert(zaddress from_addr, zaddress to_addr, ZForwardingCursor* cursor) {
  return insert(ZAddress::offset(from_addr), to_addr, cursor);
}

inline void ZForwarding::relocated_remembered_fields_register(volatile zpointer* p) {
  // Invariant: Page is being retained
  assert(ZGeneration::young()->is_phase_mark(), "Only called when");

  const ZPublishState res = _relocated_remembered_fields_state.load_relaxed();

  // none:      Gather remembered fields
  // published: Have already published fields - not possible since they haven't been
  //            collected yet
  // reject:    YC rejected fields collected by the OC
  // accept:    YC has marked that there's no more concurrent scanning of relocated
  //            fields - not possible since this code is still relocating objects

  if (res == ZPublishState::none) {
    _relocated_remembered_fields_array.push(p);
    return;
  }

  assert(res == ZPublishState::reject, "Unexpected value");
}

// Returns true iff the page is being (or about to be) relocated by the OC
// while the YC gathered the remembered fields of the "from" page.
inline bool ZForwarding::relocated_remembered_fields_is_concurrently_scanned() const {
  return _relocated_remembered_fields_state.load_relaxed() == ZPublishState::reject;
}

template <typename Function>
inline void ZForwarding::relocated_remembered_fields_apply_to_published(Function function) {
  // Invariant: Page is not being retained
  assert(ZGeneration::young()->is_phase_mark(), "Only called when");

  const ZPublishState res = _relocated_remembered_fields_state.load_acquire();

  // none:      Nothing published - page had already been relocated before YC started
  // published: OC relocated and published relocated remembered fields
  // reject:    A previous YC concurrently scanned relocated remembered fields of the "from" page
  // accept:    A previous YC marked that it didn't do (reject)

  if (res == ZPublishState::published) {
    log_debug(gc, remset)("Forwarding remset accept          : " PTR_FORMAT " " PTR_FORMAT " (" PTR_FORMAT ", %s)",
        untype(start()), untype(end()), p2i(this), Thread::current()->name());

    // OC published relocated remembered fields
    ZArrayIterator<volatile zpointer*> iter(&_relocated_remembered_fields_array);
    for (volatile zpointer* to_field_addr; iter.next(&to_field_addr);) {
      function(to_field_addr);
    }

    // YC responsible for the array - eagerly deallocate
    _relocated_remembered_fields_array.clear_and_deallocate();
  }

  assert(_relocated_remembered_fields_publish_young_seqnum != 0, "Must have been set");
  if (_relocated_remembered_fields_publish_young_seqnum == ZGeneration::young()->seqnum()) {
    log_debug(gc, remset)("scan_forwarding failed retain unsafe " PTR_FORMAT, untype(start()));
    // The page was relocated concurrently with the current young generation
    // collection. Mark that it is unsafe (and unnecessary) to call scan_page
    // on the page in the page table.
    assert(res != ZPublishState::accept, "Unexpected");
    _relocated_remembered_fields_state.store_relaxed(ZPublishState::reject);
  } else {
    log_debug(gc, remset)("scan_forwarding failed retain safe " PTR_FORMAT, untype(start()));
    // Guaranteed that the page was fully relocated and removed from page table.
    // Because of this we can signal to scan_page that any page found in page table
    // of the same slot as the current forwarding is a page that is safe to scan,
    // and in fact must be scanned.
    _relocated_remembered_fields_state.store_relaxed(ZPublishState::accept);
  }
}

#endif // SHARE_GC_Z_ZFORWARDING_INLINE_HPP
