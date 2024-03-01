/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FULLGCCOMPACTTASK_HPP
#define SHARE_GC_G1_G1FULLGCCOMPACTTASK_HPP

#include "gc/g1/g1FullGCCompactionPoint.hpp"
#include "gc/g1/g1FullGCScope.hpp"
#include "gc/g1/g1FullGCTask.hpp"
#include "gc/g1/g1HeapRegionManager.hpp"
#include "gc/shared/referenceProcessor.hpp"

class G1CollectedHeap;
class G1CMBitMap;
class G1FullCollector;

class G1FullGCCompactTask : public G1FullGCTask {
  G1FullCollector* _collector;
  HeapRegionClaimer _claimer;
  G1CollectedHeap* _g1h;

  void compact_region(HeapRegion* hr);
  void compact_humongous_obj(HeapRegion* hr);
  void free_non_overlapping_regions(uint src_start_idx, uint dest_start_idx, uint num_regions);

  static void copy_object_to_new_location(oop obj);

public:
  G1FullGCCompactTask(G1FullCollector* collector) :
    G1FullGCTask("G1 Compact Task", collector),
    _collector(collector),
    _claimer(collector->workers()),
    _g1h(G1CollectedHeap::heap()) { }

  void work(uint worker_id);
  void serial_compaction();
  void humongous_compaction();

  class G1CompactRegionClosure : public StackObj {
    G1CMBitMap* _bitmap;
    void clear_in_bitmap(oop object);
  public:
    G1CompactRegionClosure(G1CMBitMap* bitmap) : _bitmap(bitmap) { }
    size_t apply(oop object);
  };
};

#endif // SHARE_GC_G1_G1FULLGCCOMPACTTASK_HPP
