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

#ifndef SHARE_GC_G1_G1EVACFAILUREREGIONS_HPP
#define SHARE_GC_G1_G1EVACFAILUREREGIONS_HPP

#include "runtime/atomic.hpp"
#include "utilities/bitMap.hpp"

class G1AbstractSubTask;
class G1HeapRegionChunkClosure;
class HeapRegionClosure;
class HeapRegionClaimer;

// This class records for every region on the heap whether it has to be retained
// (i.e. pinned or evacuation failure or both) and records for every such region
// information to speed up iteration of these regions in various gc phases.
class G1EvacFailureRegions {
  // Records for every region on the heap whether the region has been retained.
  CHeapBitMap _regions_retained;
  // Records for every region on the heap whether the evacuation failure cause
  // has been region pinning.
  CHeapBitMap _regions_pinned;
  CHeapBitMap _regions_failed_evacuation;
  // Retained regions (indexes) in the current collection.
  uint* _evac_retained_regions;
  // Number of regions evacuation retained in the current collection.
  volatile uint _evac_retained_regions_cur_length;
  // Number of regions evacuation failed due to pinning in the current collection.
  volatile uint _evac_failure_regions_pinned;
  volatile uint _evac_failure_regions_failed_evacuation;

public:
  G1EvacFailureRegions();
  ~G1EvacFailureRegions();

  uint get_region_idx(uint idx) const {
    assert(idx < _evac_retained_regions_cur_length, "precondition");
    return _evac_retained_regions[idx];
  }

  // Sets up the bitmap and failed regions array for addition.
  void pre_collection(uint max_regions);
  // Drops memory for internal data structures, but keep counts.
  void post_collection();

  bool contains(uint region_idx) const;
  void par_iterate(HeapRegionClosure* closure,
                   HeapRegionClaimer* hrclaimer,
                   uint worker_id) const;

  // Return a G1AbstractSubTask which does necessary preparation for evacuation failed regions
  G1AbstractSubTask* create_prepare_regions_task();

  uint num_regions_retained() const {
    return Atomic::load(&_evac_retained_regions_cur_length);
  }

  uint num_regions_pinned() const {
    return Atomic::load(&_evac_failure_regions_pinned);
  }

  uint num_regions_evac_failed() const {
      return Atomic::load(&_evac_failure_regions_failed_evacuation);
  }

  bool has_regions_retained() const {
    return num_regions_retained() > 0;
  }

  bool has_regions_evac_pinned() const {
    return num_regions_pinned() > 0;
  }

  bool has_regions_evac_failed() const {
    return num_regions_evac_failed() > 0;
  }

  // Record that the garbage collection encountered an evacuation failure in the
  // given region. Returns whether this has been the first occurrence of an evacuation
  // failure in that region.
  inline bool record(uint region_idx, bool cause_pinned);
};

#endif //SHARE_GC_G1_G1EVACFAILUREREGIONS_HPP
