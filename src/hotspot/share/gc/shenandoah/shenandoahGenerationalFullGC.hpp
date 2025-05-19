/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALFULLGC_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALFULLGC_HPP

#include "gc/shared/preservedMarks.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "memory/iterator.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/growableArray.hpp"

class ShenandoahHeap;
class ShenandoahHeapRegion;

class ShenandoahGenerationalFullGC {
public:
  // Prepares the generational mode heap for a full collection.
  static void prepare();

  // Full GC may have compacted objects in the old generation, so we need to rebuild the card tables.
  static void rebuild_remembered_set(ShenandoahHeap* heap);

  // Records end of cycle for young and old and establishes size of live bytes in old
  static void handle_completion(ShenandoahHeap* heap);

  // Full GC may have promoted regions and may have temporarily violated constraints on the usage and
  // capacity of the old generation. This method will balance the accounting of regions between the
  // young and old generations. This is somewhat vestigial, but the outcome of this method is used
  // when rebuilding the free sets.
  static void balance_generations_after_gc(ShenandoahHeap* heap);

  // This will compute the target size for the old generation. It will be expressed in terms of
  // a region surplus and deficit, which will be redistributed accordingly after rebuilding the
  // free set.
  static void compute_balances();

  // Rebuilding the free set may have resulted in regions being pulled in to the old generation
  // evacuation reserve. For this reason, we must update the usage and capacity of the generations
  // again. In the distant past, the free set did not know anything about generations, so we had
  // a layer built above it to represent how much young/old memory was available. This layer is
  // redundant and adds complexity. We would like to one day remove it. Until then, we must keep it
  // synchronized with the free set's view of things.
  static ShenandoahGenerationalHeap::TransferResult balance_generations_after_rebuilding_free_set();

  // Logs the number of live bytes marked in the old generation. This is _not_ the same
  // value used as the baseline for the old generation _after_ the full gc is complete.
  // The value reported in the logs does not include objects and regions that may be
  // promoted during the full gc.
  static void log_live_in_old(ShenandoahHeap* heap);

  // This is used to tally the number, usage and space wasted by humongous objects for each generation.
  static void account_for_region(ShenandoahHeapRegion* r, size_t &region_count, size_t &region_usage, size_t &humongous_waste);

  // Regions which are scheduled for in-place promotion during evacuation temporarily
  // have their top set to their end to prevent new objects from being allocated in them
  // before they are promoted. If the full GC encounters such a region, it means the
  // in-place promotion did not happen, and we must restore the original value of top.
  static void restore_top_before_promote(ShenandoahHeap* heap);

  // Pinned regions are not compacted, so they may still hold unmarked objects with
  // references to reclaimed memory. Remembered set scanning will crash if it attempts
  // to iterate the oops in these objects. This method fills in dead objects for pinned,
  // old regions.
  static void maybe_coalesce_and_fill_region(ShenandoahHeapRegion* r);
};

class ShenandoahPrepareForGenerationalCompactionObjectClosure : public ObjectClosure {
private:
  PreservedMarks*             const _preserved_marks;
  ShenandoahGenerationalHeap* const _heap;
  uint                              _tenuring_threshold;

  // _empty_regions is a thread-local list of heap regions that have been completely emptied by this worker thread's
  // compaction efforts.  The worker thread that drives these efforts adds compacted regions to this list if the
  // region has not been compacted onto itself.
  GrowableArray<ShenandoahHeapRegion*>& _empty_regions;
  int _empty_regions_pos;
  ShenandoahHeapRegion*          _old_to_region;
  ShenandoahHeapRegion*          _young_to_region;
  ShenandoahHeapRegion*          _from_region;
  ShenandoahAffiliation          _from_affiliation;
  HeapWord*                      _old_compact_point;
  HeapWord*                      _young_compact_point;
  uint                           _worker_id;

public:
  ShenandoahPrepareForGenerationalCompactionObjectClosure(PreservedMarks* preserved_marks,
                                                          GrowableArray<ShenandoahHeapRegion*>& empty_regions,
                                                          ShenandoahHeapRegion* from_region, uint worker_id);

  void set_from_region(ShenandoahHeapRegion* from_region);
  void finish();
  void finish_old_region();
  void finish_young_region();
  bool is_compact_same_region();
  int empty_regions_pos() const { return _empty_regions_pos; }

  void do_object(oop p) override;
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALFULLGC_HPP
