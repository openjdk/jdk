/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include <cstdio>

#include "precompiled.hpp"
#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1CardSetContainers.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1HeapRegionManager.inline.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "memory/allocation.hpp"
#include "memory/padded.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/powerOfTwo.hpp"

HeapWord* G1HeapRegionRemSet::_heap_base_address = nullptr;

const char* G1HeapRegionRemSet::_state_strings[] =  {"Untracked", "Updating", "Complete"};
const char* G1HeapRegionRemSet::_short_state_strings[] =  {"UNTRA", "UPDAT", "CMPLT"};

void G1HeapRegionRemSet::initialize(MemRegion reserved) {
  G1CardSet::initialize(reserved);
  _heap_base_address = reserved.start();
}

G1HeapRegionRemSet::G1HeapRegionRemSet(G1HeapRegion* hr,
                                   G1CardSetConfiguration* config) :
  _code_roots(),
  _card_set_mm(config, G1CollectedHeap::heap()->card_set_freelist_pool()),
  _card_set(config, &_card_set_mm),
  _hr(hr),
  _state(Untracked) { }

void G1HeapRegionRemSet::clear_fcc() {
  G1FromCardCache::clear(_hr->hrm_index());
}

void G1HeapRegionRemSet::clear(bool only_cardset, bool keep_tracked) {
  if (!only_cardset) {
    _code_roots.clear();
  }
  clear_fcc();
  _card_set.clear();
  if (!keep_tracked) {
    set_state_untracked();
  } else {
    assert(is_tracked(), "must be");
  }
  assert(occupied() == 0, "Should be clear.");
}

void G1HeapRegionRemSet::reset_table_scanner() {
  _code_roots.reset_table_scanner();
  _card_set.reset_table_scanner();
}

G1MonotonicArenaMemoryStats G1HeapRegionRemSet::card_set_memory_stats() const {
  return _card_set_mm.memory_stats();
}

void G1HeapRegionRemSet::print_static_mem_size(outputStream* out) {
  out->print_cr("  Static structures = " SIZE_FORMAT, G1HeapRegionRemSet::static_mem_size());
}

// Code roots support
//
// The code root set is protected by two separate locking schemes
// When at safepoint the per-hrrs lock must be held during modifications
// except when doing a full gc.
// When not at safepoint the CodeCache_lock must be held during modifications.

void G1HeapRegionRemSet::add_code_root(nmethod* nm) {
  assert(nm != nullptr, "sanity");
  _code_roots.add(nm);
}

void G1HeapRegionRemSet::remove_code_root(nmethod* nm) {
  assert(nm != nullptr, "sanity");

  _code_roots.remove(nm);

  // Check that there were no duplicates
  guarantee(!_code_roots.contains(nm), "duplicate entry found");
}

void G1HeapRegionRemSet::bulk_remove_code_roots() {
  _code_roots.bulk_remove();
}

void G1HeapRegionRemSet::code_roots_do(NMethodClosure* blk) const {
  _code_roots.nmethods_do(blk);
}

void G1HeapRegionRemSet::clean_code_roots(G1HeapRegion* hr) {
  _code_roots.clean(hr);
}

size_t G1HeapRegionRemSet::code_roots_mem_size() {
  return _code_roots.mem_size();
}
