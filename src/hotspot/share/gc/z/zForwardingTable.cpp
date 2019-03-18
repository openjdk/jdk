/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zForwarding.hpp"
#include "gc/z/zForwardingTable.inline.hpp"
#include "gc/z/zGranuleMap.inline.hpp"
#include "utilities/debug.hpp"

ZForwardingTable::ZForwardingTable() :
    _map() {}

void ZForwardingTable::insert(uintptr_t start,
                              size_t size,
                              size_t object_alignment_shift,
                              uint32_t live_objects) {
  // Allocate forwarding
  ZForwarding* const forwarding = ZForwarding::create(start,
                                                      object_alignment_shift,
                                                      live_objects);

  // Insert into forwarding table
  const uintptr_t addr = ZAddress::good(start);
  assert(get(addr) == NULL, "Invalid entry");
  _map.put(addr, size, forwarding);
}

void ZForwardingTable::clear() {
  ZForwarding* prev_forwarding = NULL;

  // Clear and destroy all non-NULL entries
  ZGranuleMapIterator<ZForwarding*> iter(&_map);
  for (ZForwarding** entry; iter.next(&entry);) {
    ZForwarding* const forwarding = *entry;
    if (forwarding == NULL) {
      // Skip entry
      continue;
    }

    // Clear entry
    *entry = NULL;

    // More than one entry can point to the same
    // forwarding. Make sure we only destroy it once.
    if (forwarding != prev_forwarding) {
      ZForwarding::destroy(forwarding);
      prev_forwarding = forwarding;
    }
  }
}
