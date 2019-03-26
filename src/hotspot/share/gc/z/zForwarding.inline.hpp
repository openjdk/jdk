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

#ifndef SHARE_GC_Z_ZFORWARDING_INLINE_HPP
#define SHARE_GC_Z_ZFORWARDING_INLINE_HPP

#include "gc/z/zAttachedArray.inline.hpp"
#include "gc/z/zForwarding.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHash.inline.hpp"
#include "gc/z/zHeap.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

inline uintptr_t ZForwarding::start() const {
  return _virtual.start();
}

inline size_t ZForwarding::size() const {
  return _virtual.size();
}

inline size_t ZForwarding::object_alignment_shift() const {
  return _object_alignment_shift;
}

inline ZPage* ZForwarding::page() const {
  return _page;
}

inline bool ZForwarding::is_pinned() const {
  return Atomic::load(&_pinned);
}

inline void ZForwarding::set_pinned() {
  Atomic::store(true, &_pinned);
}

inline bool ZForwarding::inc_refcount() {
  uint32_t refcount = Atomic::load(&_refcount);

  while (refcount > 0) {
    const uint32_t old_refcount = refcount;
    const uint32_t new_refcount = old_refcount + 1;
    const uint32_t prev_refcount = Atomic::cmpxchg(new_refcount, &_refcount, old_refcount);
    if (prev_refcount == old_refcount) {
      return true;
    }

    refcount = prev_refcount;
  }

  return false;
}

inline bool ZForwarding::dec_refcount() {
  assert(_refcount > 0, "Invalid state");
  return Atomic::sub(1u, &_refcount) == 0u;
}

inline bool ZForwarding::retain_page() {
  return inc_refcount();
}

inline void ZForwarding::release_page() {
  if (dec_refcount()) {
    ZHeap::heap()->free_page(_page, true /* reclaimed */);
    _page = NULL;
  }
}

inline ZForwardingEntry* ZForwarding::entries() const {
  return _entries(this);
}

inline ZForwardingEntry ZForwarding::at(ZForwardingCursor* cursor) const {
  return Atomic::load(entries() + *cursor);
}

inline ZForwardingEntry ZForwarding::first(uintptr_t from_index, ZForwardingCursor* cursor) const {
  const uint32_t mask = _entries.length() - 1;
  const uint32_t hash = ZHash::uint32_to_uint32((uint32_t)from_index);
  *cursor = hash & mask;
  return at(cursor);
}

inline ZForwardingEntry ZForwarding::next(ZForwardingCursor* cursor) const {
  const uint32_t mask = _entries.length() - 1;
  *cursor = (*cursor + 1) & mask;
  return at(cursor);
}

inline ZForwardingEntry ZForwarding::find(uintptr_t from_index) const {
  ZForwardingCursor dummy;
  return find(from_index, &dummy);
}

inline ZForwardingEntry ZForwarding::find(uintptr_t from_index, ZForwardingCursor* cursor) const {
  // Reading entries in the table races with the atomic CAS done for
  // insertion into the table. This is safe because each entry is at
  // most updated once (from -1 to something else).
  ZForwardingEntry entry = first(from_index, cursor);
  while (!entry.is_empty()) {
    if (entry.from_index() == from_index) {
      // Match found, return matching entry
      return entry;
    }

    entry = next(cursor);
  }

  // Match not found, return empty entry
  return entry;
}

inline uintptr_t ZForwarding::insert(uintptr_t from_index, uintptr_t to_offset, ZForwardingCursor* cursor) {
  const ZForwardingEntry new_entry(from_index, to_offset);
  const ZForwardingEntry old_entry; // Empty

  for (;;) {
    const ZForwardingEntry prev_entry = Atomic::cmpxchg(new_entry, entries() + *cursor, old_entry);
    if (prev_entry.is_empty()) {
      // Success
      return to_offset;
    }

    // Find next empty or matching entry
    ZForwardingEntry entry = at(cursor);
    while (!entry.is_empty()) {
      if (entry.from_index() == from_index) {
        // Match found, return already inserted address
        return entry.to_offset();
      }

      entry = next(cursor);
    }
  }
}

#endif // SHARE_GC_Z_ZFORWARDING_INLINE_HPP
