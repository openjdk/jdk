/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CONCURRENTMARKREMARKTASKS_HPP
#define SHARE_GC_G1_G1CONCURRENTMARKREMARKTASKS_HPP

#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1HeapRegionManager.hpp"
#include "gc/g1/g1HeapRegionSet.hpp"
#include "gc/shared/workerThread.hpp"
#include "runtime/atomic.hpp"

class G1CollectedHeap;
class G1ConcurrentMark;

// Update per-region liveness info based on CM stats. Then, reclaim empty
// regions right away and select certain regions (e.g. sparse ones) for remset
// rebuild.
class G1UpdateRegionLivenessAndSelectForRebuildTask : public WorkerTask {
  G1CollectedHeap* _g1h;
  G1ConcurrentMark* _cm;
  G1HeapRegionClaimer _hrclaimer;

  Atomic<uint> _total_selected_for_rebuild;

  // Reclaimed empty regions
  G1FreeRegionList _cleanup_list;

  struct G1OnRegionClosure;

public:
  G1UpdateRegionLivenessAndSelectForRebuildTask(G1CollectedHeap* g1h,
                                                G1ConcurrentMark* cm,
                                                uint num_workers);

  ~G1UpdateRegionLivenessAndSelectForRebuildTask();

  void work(uint worker_id) override;

  uint total_selected_for_rebuild() const {
    return _total_selected_for_rebuild.load_relaxed();
  }

  static uint desired_num_workers(uint num_regions);
};

#endif /* SHARE_GC_G1_G1CONCURRENTMARKREMARKTASKS_HPP */

