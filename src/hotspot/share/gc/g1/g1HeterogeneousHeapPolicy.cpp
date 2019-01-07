/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1HeterogeneousHeapPolicy.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/heterogeneousHeapRegionManager.hpp"

G1HeterogeneousHeapPolicy::G1HeterogeneousHeapPolicy(G1CollectorPolicy* policy, STWGCTimer* gc_timer) :
  G1Policy(policy, gc_timer), _manager(NULL) {}

// We call the super class init(), after which we provision young_list_target_length() regions in dram.
void G1HeterogeneousHeapPolicy::init(G1CollectedHeap* g1h, G1CollectionSet* collection_set) {
  G1Policy::init(g1h, collection_set);
  _manager = HeterogeneousHeapRegionManager::manager();
  _manager->adjust_dram_regions((uint)young_list_target_length(), G1CollectedHeap::heap()->workers());
}

// After a collection pause, young list target length is updated. So we need to make sure we have enough regions in dram for young gen.
void G1HeterogeneousHeapPolicy::record_collection_pause_end(double pause_time_ms, size_t cards_scanned, size_t heap_used_bytes_before_gc) {
  G1Policy::record_collection_pause_end(pause_time_ms, cards_scanned, heap_used_bytes_before_gc);
  _manager->adjust_dram_regions((uint)young_list_target_length(), G1CollectedHeap::heap()->workers());
}

// After a full collection, young list target length is updated. So we need to make sure we have enough regions in dram for young gen.
void G1HeterogeneousHeapPolicy::record_full_collection_end() {
  G1Policy::record_full_collection_end();
  _manager->adjust_dram_regions((uint)young_list_target_length(), G1CollectedHeap::heap()->workers());
}

bool G1HeterogeneousHeapPolicy::force_upgrade_to_full() {
  if (_manager->has_borrowed_regions()) {
    return true;
  }
  return false;
}
