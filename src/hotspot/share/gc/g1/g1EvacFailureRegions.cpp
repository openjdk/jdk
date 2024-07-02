/*
 * Copyright (c) 2021, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

#include "gc/g1/g1BatchedTask.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1EvacFailureRegions.inline.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "utilities/bitMap.inline.hpp"

G1EvacFailureRegions::G1EvacFailureRegions() :
  _regions_evac_failed(mtGC),
  _regions_pinned(mtGC),
  _regions_alloc_failed(mtGC),
  _evac_failed_regions(nullptr),
  _num_regions_evac_failed(0) { }

G1EvacFailureRegions::~G1EvacFailureRegions() {
  assert(_evac_failed_regions == nullptr, "not cleaned up");
}

void G1EvacFailureRegions::pre_collection(uint max_regions) {
  Atomic::store(&_num_regions_evac_failed, 0u);
  _regions_evac_failed.resize(max_regions);
  _regions_pinned.resize(max_regions);
  _regions_alloc_failed.resize(max_regions);
  _evac_failed_regions = NEW_C_HEAP_ARRAY(uint, max_regions, mtGC);
}

void G1EvacFailureRegions::post_collection() {
  _regions_evac_failed.resize(0);
  _regions_pinned.resize(0);
  _regions_alloc_failed.resize(0);

  FREE_C_HEAP_ARRAY(uint, _evac_failed_regions);
  _evac_failed_regions = nullptr;
}

bool G1EvacFailureRegions::contains(uint region_idx) const {
  return _regions_evac_failed.par_at(region_idx, memory_order_relaxed);
}

void G1EvacFailureRegions::par_iterate(HeapRegionClosure* closure,
                                       HeapRegionClaimer* hrclaimer,
                                       uint worker_id) const {
  G1CollectedHeap::heap()->par_iterate_regions_array(closure,
                                                     hrclaimer,
                                                     _evac_failed_regions,
                                                     Atomic::load(&_num_regions_evac_failed),
                                                     worker_id);
}
