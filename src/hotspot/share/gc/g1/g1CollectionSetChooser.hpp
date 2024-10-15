/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1COLLECTIONSETCHOOSER_HPP
#define SHARE_GC_G1_G1COLLECTIONSETCHOOSER_HPP

#include "gc/g1/g1HeapRegion.hpp"
#include "gc/shared/gc_globals.hpp"
#include "memory/allStatic.hpp"
#include "runtime/globals.hpp"

class G1CollectionSetCandidates;
class WorkerThreads;

// Helper class to calculate collection set candidates, and containing some related
// methods.
class G1CollectionSetChooser : public AllStatic {
  static uint calculate_work_chunk_size(uint num_workers, uint num_regions);

public:
  static size_t mixed_gc_live_threshold_bytes() {
    return G1HeapRegion::GrainBytes * (size_t)G1MixedGCLiveThresholdPercent / 100;
  }

  static bool region_occupancy_low_enough_for_evac(size_t live_bytes) {
    return live_bytes < mixed_gc_live_threshold_bytes();
  }

  // Build and return set of collection set candidates sorted by decreasing gc
  // efficiency.
  static void build(WorkerThreads* workers, uint max_num_regions, G1CollectionSetCandidates* candidates);
};

#endif // SHARE_GC_G1_G1COLLECTIONSETCHOOSER_HPP
