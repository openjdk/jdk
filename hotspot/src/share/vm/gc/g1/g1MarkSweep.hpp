/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1MARKSWEEP_HPP
#define SHARE_VM_GC_G1_G1MARKSWEEP_HPP

#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/serial/genMarkSweep.hpp"
#include "gc/shared/generation.hpp"
#include "memory/universe.hpp"
#include "oops/markOop.hpp"
#include "oops/oop.hpp"
#include "runtime/timer.hpp"
#include "utilities/growableArray.hpp"

class ReferenceProcessor;

// G1MarkSweep takes care of global mark-compact garbage collection for a
// G1CollectedHeap using a four-phase pointer forwarding algorithm.  All
// generations are assumed to support marking; those that can also support
// compaction.
//
// Class unloading will only occur when a full gc is invoked.
class G1PrepareCompactClosure;
class G1ArchiveRegionMap;

class G1MarkSweep : AllStatic {
 public:

  static void invoke_at_safepoint(ReferenceProcessor* rp,
                                  bool clear_all_softrefs);

  static STWGCTimer* gc_timer() { return GenMarkSweep::_gc_timer; }
  static SerialOldTracer* gc_tracer() { return GenMarkSweep::_gc_tracer; }

  // Create the _archive_region_map which is used to identify archive objects.
  static void enable_archive_object_check();

  // Set the regions containing the specified address range as archive/non-archive.
  static void set_range_archive(MemRegion range, bool is_archive);

  // Check if an object is in an archive region using the _archive_region_map.
  static bool in_archive_range(oop object);

  // Check if archive object checking is enabled, to avoid calling in_archive_range
  // unnecessarily.
  static bool archive_check_enabled() { return G1MarkSweep::_archive_check_enabled; }

 private:
  static bool _archive_check_enabled;
  static G1ArchiveRegionMap  _archive_region_map;

  // Mark live objects
  static void mark_sweep_phase1(bool& marked_for_deopt,
                                bool clear_all_softrefs);
  // Calculate new addresses
  static void mark_sweep_phase2();
  // Update pointers
  static void mark_sweep_phase3();
  // Move objects to new positions
  static void mark_sweep_phase4();

  static void allocate_stacks();
  static void prepare_compaction();
  static void prepare_compaction_work(G1PrepareCompactClosure* blk);
};

class G1PrepareCompactClosure : public HeapRegionClosure {
 protected:
  G1CollectedHeap* _g1h;
  ModRefBarrierSet* _mrbs;
  CompactPoint _cp;
  uint _humongous_regions_removed;

  virtual void prepare_for_compaction(HeapRegion* hr, HeapWord* end);
  void prepare_for_compaction_work(CompactPoint* cp, HeapRegion* hr, HeapWord* end);
  void free_humongous_region(HeapRegion* hr);
  bool is_cp_initialized() const { return _cp.space != NULL; }

 public:
  G1PrepareCompactClosure() :
    _g1h(G1CollectedHeap::heap()),
    _mrbs(_g1h->g1_barrier_set()),
    _humongous_regions_removed(0) { }

  void update_sets();
  bool doHeapRegion(HeapRegion* hr);
};

// G1ArchiveRegionMap is a boolean array used to mark G1 regions as
// archive regions.  This allows a quick check for whether an object
// should not be marked because it is in an archive region.
class G1ArchiveRegionMap : public G1BiasedMappedArray<bool> {
protected:
  bool default_value() const { return false; }
};

#endif // SHARE_VM_GC_G1_G1MARKSWEEP_HPP
