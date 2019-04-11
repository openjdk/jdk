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
#include "gc/z/zForwarding.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "memory/allocation.hpp"
#include "utilities/debug.hpp"

ZForwarding* ZForwarding::create(ZPage* page) {
  // Allocate table for linear probing. The size of the table must be
  // a power of two to allow for quick and inexpensive indexing/masking.
  // The table is sized to have a load factor of 50%, i.e. sized to have
  // double the number of entries actually inserted.
  assert(page->live_objects() > 0, "Invalid value");
  const uint32_t nentries = ZUtils::round_up_power_of_2(page->live_objects() * 2);
  return ::new (AttachedArray::alloc(nentries)) ZForwarding(page, nentries);
}

void ZForwarding::destroy(ZForwarding* forwarding) {
  AttachedArray::free(forwarding);
}

ZForwarding::ZForwarding(ZPage* page, uint32_t nentries) :
    _virtual(page->virtual_memory()),
    _object_alignment_shift(page->object_alignment_shift()),
    _entries(nentries),
    _page(page),
    _refcount(1),
    _pinned(false) {}

void ZForwarding::verify() const {
  guarantee(_refcount > 0, "Invalid refcount");
  guarantee(_page != NULL, "Invalid page");

  uint32_t live_objects = 0;

  for (ZForwardingCursor i = 0; i < _entries.length(); i++) {
    const ZForwardingEntry entry = at(&i);
    if (!entry.populated()) {
      // Skip empty entries
      continue;
    }

    // Check from index
    guarantee(entry.from_index() < _page->object_max_count(), "Invalid from index");

    // Check for duplicates
    for (ZForwardingCursor j = i + 1; j < _entries.length(); j++) {
      const ZForwardingEntry other = at(&j);
      if (!other.populated()) {
        // Skip empty entries
        continue;
      }

      guarantee(entry.from_index() != other.from_index(), "Duplicate from");
      guarantee(entry.to_offset() != other.to_offset(), "Duplicate to");
    }

    live_objects++;
  }

  // Check number of non-empty entries
  guarantee(live_objects == _page->live_objects(), "Invalid number of entries");
}
