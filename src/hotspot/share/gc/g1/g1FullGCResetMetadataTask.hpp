/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FULLGCRESETMETADATATASK_HPP
#define SHARE_GC_G1_G1FULLGCRESETMETADATATASK_HPP
#include "gc/g1/g1FullGCTask.hpp"
#include "gc/g1/g1HeapRegion.hpp"

class G1FullGCResetMetadataTask : public G1FullGCTask {
  G1FullCollector* _collector;
  HeapRegionClaimer _claimer;

  class G1ResetMetadataClosure : public HeapRegionClosure {
    G1CollectedHeap* _g1h;
    G1FullCollector* _collector;

    void reset_region_metadata(HeapRegion* hr);
    // Scrub all runs of dead objects within the given region by putting filler
    // objects and updating the corresponding BOT. If update_bot_for_live is true,
    // also update the BOT for live objects.
    void scrub_skip_compacting_region(HeapRegion* hr, bool update_bot_for_live);

    void reset_skip_compacting(HeapRegion* r);

  public:
    G1ResetMetadataClosure(G1FullCollector* collector);

    bool do_heap_region(HeapRegion* hr);
  };

public:
  G1FullGCResetMetadataTask(G1FullCollector* collector) :
    G1FullGCTask("G1 Reset Metadata Task", collector),
    _collector(collector),
    _claimer(collector->workers()) { }

  void work(uint worker_id);
};

#endif // SHARE_GC_G1_G1FULLGCRESETMETADATATASK_HPP
