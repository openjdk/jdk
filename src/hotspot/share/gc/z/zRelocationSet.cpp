/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zForwardingAllocator.inline.hpp"
#include "gc/z/zRelocationSet.hpp"
#include "gc/z/zStat.hpp"
#include "memory/allocation.hpp"
#include "utilities/debug.hpp"

ZRelocationSet::ZRelocationSet() :
    _allocator(),
    _forwardings(NULL),
    _nforwardings(0) {}

void ZRelocationSet::populate(ZPage* const* small, size_t nsmall,
                              ZPage* const* medium, size_t nmedium,
                              size_t forwarding_entries) {
  // Set relocation set length
  _nforwardings = nsmall + nmedium;

  // Initialize forwarding allocator to have room for the
  // relocation set, all forwardings, and all forwarding entries.
  const size_t relocation_set_size = _nforwardings * sizeof(ZForwarding*);
  const size_t forwardings_size = _nforwardings * sizeof(ZForwarding);
  const size_t forwarding_entries_size = forwarding_entries * sizeof(ZForwardingEntry);
  _allocator.reset(relocation_set_size + forwardings_size + forwarding_entries_size);

  // Allocate relocation set
  _forwardings = new (_allocator.alloc(relocation_set_size)) ZForwarding*[_nforwardings];

  // Populate relocation set array
  size_t j = 0;

  // Populate medium pages
  for (size_t i = 0; i < nmedium; i++) {
    _forwardings[j++] = ZForwarding::alloc(&_allocator, medium[i]);
  }

  // Populate small pages
  for (size_t i = 0; i < nsmall; i++) {
    _forwardings[j++] = ZForwarding::alloc(&_allocator, small[i]);
  }

  assert(_allocator.is_full(), "Should be full");

  // Update statistics
  ZStatRelocation::set_at_populate_relocation_set(_allocator.size());
}

void ZRelocationSet::reset() {
  _nforwardings = 0;
}
