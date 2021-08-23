/*
 * Copyright (c) 2021, Huawei and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "g1EvacuationFailureRegions.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/heapRegion.hpp"
#include "runtime/atomic.hpp"
#include "utilities/concurrentHashTable.inline.hpp"


uintx G1EvacuationFailureRegions::HashTableConfig::get_hash(Value const& value, bool* is_dead) {
  *is_dead = false;
  return value;
}

void* G1EvacuationFailureRegions::HashTableConfig::allocate_node(void* context, size_t size, Value const& value) {
  return AllocateHeap(size, mtGC);
}

void G1EvacuationFailureRegions::HashTableConfig::free_node(void* context, void* memory, Value const& value) {
  FreeHeap(memory);
}

G1EvacuationFailureRegions::G1EvacuationFailureRegions() {
}

G1EvacuationFailureRegions::~G1EvacuationFailureRegions() {
  FREE_C_HEAP_ARRAY(uint, _evac_failure_regions);
  delete _table;
}

void G1EvacuationFailureRegions::initialize() {
  Atomic::store(&_evac_failure_regions_cur_length, (size_t)0);
  _table = new HashTable();
  _evac_failure_regions = NEW_C_HEAP_ARRAY(uint, G1CollectedHeap::heap()->max_reserved_regions(), mtGC);
}

void G1EvacuationFailureRegions::record(HeapRegion* region) {
  HashTableLookUp lookup(region->hrm_index());
  uint idx = region->hrm_index();
  bool success = _table->insert(Thread::current(), lookup, idx);
  if (success) {
    size_t offset = Atomic::fetch_and_add(&_evac_failure_regions_cur_length, 1u);
    _evac_failure_regions[offset] = idx;
  }
}

void G1EvacuationFailureRegions::par_iterate(HeapRegionClosure* closure, HeapRegionClaimer* _hrclaimer, uint worker_id) {
  assert_at_safepoint();
  size_t length = Atomic::load(&_evac_failure_regions_cur_length);
  if (length == 0) {
    return;
  }

  uint total_workers = G1CollectedHeap::heap()->workers()->active_workers();
  size_t start_pos = (worker_id * length) / total_workers;
  size_t cur_pos = start_pos;

  do {
    uint region_idx = _evac_failure_regions[cur_pos];
    if (_hrclaimer == NULL || _hrclaimer->claim_region(region_idx)) {
      HeapRegion* r = G1CollectedHeap::heap()->region_at(region_idx);
      bool result = closure->do_heap_region(r);
      guarantee(!result, "Must not cancel iteration");
    }

    cur_pos++;
    if (cur_pos == length) {
      cur_pos = 0;
    }
  } while (cur_pos != start_pos);
}

void G1EvacuationFailureRegions::reset() {
  Atomic::store(&_evac_failure_regions_cur_length, (size_t)0);
  delete _table;
  _table = new HashTable();
}
