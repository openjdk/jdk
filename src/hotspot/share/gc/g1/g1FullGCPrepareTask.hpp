/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FULLGCPREPARETASK_HPP
#define SHARE_GC_G1_G1FULLGCPREPARETASK_HPP

#include "gc/g1/g1FullGCTask.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "memory/allocation.hpp"

class G1CollectedHeap;
class G1CMBitMap;
class G1FullCollector;
class G1FullGCCompactionPoint;
class G1HeapRegion;

// Determines the regions in the heap that should be part of the compaction and
// distributes them among the compaction queues in round-robin fashion.
class G1DetermineCompactionQueueClosure : public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  G1FullCollector* _collector;
  uint _cur_worker;

  inline void free_empty_humongous_region(G1HeapRegion* hr);

  inline bool should_compact(G1HeapRegion* hr) const;

  // Returns the current worker id to assign a compaction point to, and selects
  // the next one round-robin style.
  inline uint next_worker();

  inline G1FullGCCompactionPoint* next_compaction_point();

  inline void add_to_compaction_queue(G1HeapRegion* hr);

public:
  G1DetermineCompactionQueueClosure(G1FullCollector* collector);

  inline bool do_heap_region(G1HeapRegion* hr) override;
};

class G1FullGCPrepareTask : public G1FullGCTask {
  volatile bool     _has_free_compaction_targets;
  HeapRegionClaimer _hrclaimer;

  void set_has_free_compaction_targets();

public:
  G1FullGCPrepareTask(G1FullCollector* collector);
  void work(uint worker_id);
  // After the Prepare phase, are there any unused (empty) regions (compaction
  // targets) at the end of any compaction queues?
  bool has_free_compaction_targets();

private:
  class G1CalculatePointersClosure : public HeapRegionClosure {
    G1CollectedHeap* _g1h;
    G1FullCollector* _collector;
    G1CMBitMap* _bitmap;
    G1FullGCCompactionPoint* _cp;

    void prepare_for_compaction(G1HeapRegion* hr);

  public:
    G1CalculatePointersClosure(G1FullCollector* collector,
                               G1FullGCCompactionPoint* cp);

    bool do_heap_region(G1HeapRegion* hr);
  };

  class G1PrepareCompactLiveClosure : public StackObj {
    G1FullGCCompactionPoint* _cp;

  public:
    G1PrepareCompactLiveClosure(G1FullGCCompactionPoint* cp);
    size_t apply(oop object);
  };
};

// Closure to re-prepare objects in the serial compaction point queue regions for
// serial compaction.
class G1SerialRePrepareClosure : public StackObj {
  G1FullGCCompactionPoint* _cp;
  HeapWord* _dense_prefix_top;

public:
  G1SerialRePrepareClosure(G1FullGCCompactionPoint* hrcp, HeapWord* dense_prefix_top) :
    _cp(hrcp),
    _dense_prefix_top(dense_prefix_top) { }

  inline size_t apply(oop obj);
};

#endif // SHARE_GC_G1_G1FULLGCPREPARETASK_HPP
