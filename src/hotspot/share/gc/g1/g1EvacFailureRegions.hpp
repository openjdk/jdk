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

#include "utilities/bitMap.hpp"

class G1AbstractSubTask;
class HeapRegionClosure;
class HeapRegionClaimer;

// This class records for every region on the heap whether it had experienced an
// evacuation failure.
// An evacuation failure may occur due to pinning or due to allocation failure
// (not enough to-space). For every such occurrence the class records region
// information to speed up iteration of these regions in various gc phases.
//
// Pinned regions may experience an allocation failure at the same time as G1
// tries to evacuate anything but objects that are possible to be pinned. So
//
//   _num_regions_pinned + _num_regions_alloc_failed >= _num_regions_evac_failed
//
class G1EvacFailureRegions {
  // Records for every region on the heap whether the region has experienced an
  // evacuation failure.
  CHeapBitMap _regions_evac_failed;
  // Records for every region on the heap whether the evacuation failure cause
  // has been allocation failure or region pinning.
  CHeapBitMap _regions_pinned;
  CHeapBitMap _regions_alloc_failed;
  // Evacuation failed regions (indexes) in the current collection.
  uint* _evac_failed_regions;
  // Number of regions evacuation failed in the current collection.
  volatile uint _num_regions_evac_failed;

public:
  G1EvacFailureRegions();
  ~G1EvacFailureRegions();

  uint get_region_idx(uint idx) const {
    assert(idx < _num_regions_evac_failed, "precondition");
    return _evac_failed_regions[idx];
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

  inline uint num_regions_evac_failed() const;

  inline bool has_regions_evac_failed() const;
  inline bool has_regions_evac_pinned() const;
  inline bool has_regions_alloc_failed() const;

  // Record that the garbage collection encountered an evacuation failure in the
  // given region. Returns whether this has been the first occurrence of an evacuation
  // failure in that region.
  inline bool record(uint worker_id, uint region_idx, bool cause_pinned);
};

#endif //SHARE_GC_G1_G1EVACFAILUREREGIONS_HPP
