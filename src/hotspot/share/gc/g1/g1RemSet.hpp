/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1REMSET_HPP
#define SHARE_GC_G1_G1REMSET_HPP

#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1OopClosures.hpp"
#include "gc/g1/g1RemSetSummary.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "utilities/ticks.hpp"

// A G1RemSet provides ways of iterating over pointers into a selected
// collection set.

class BitMap;
class CardTableBarrierSet;
class G1AbstractSubTask;
class G1CollectedHeap;
class G1CMBitMap;
class G1HeapRegionClaimer;
class G1RemSetScanState;
class G1ParScanThreadState;
class G1ParScanThreadStateSet;
class G1Policy;
class G1RemSetSamplingTask;
class G1ScanCardClosure;
class G1ServiceThread;

// A G1RemSet in which each heap region has a rem set that records the
// external heap references into it.  Uses a mod ref bs to track updates,
// so that they can be used to update the individual region remsets.
class G1RemSet: public CHeapObj<mtGC> {
public:
  typedef CardTable::CardValue CardValue;

private:
  G1RemSetScanState* _scan_state;

  G1RemSetSummary _prev_period_summary;

  G1CollectedHeap* _g1h;

  G1CardTable*           _ct;
  G1Policy*              _g1p;

  void print_merge_heap_roots_stats();

  void assert_scan_top_is_null(uint hrm_index) NOT_DEBUG_RETURN;

  void enqueue_for_reprocessing(CardValue* card_ptr);

public:
  // Initialize data that depends on the heap size being known.
  void initialize(uint max_reserved_regions);

  G1RemSet(G1CollectedHeap* g1h, G1CardTable* ct);
  ~G1RemSet();

  // Scan all cards in the non-collection set regions that potentially contain
  // references into the current whole collection set.
  void scan_heap_roots(G1ParScanThreadState* pss,
                       uint worker_id,
                       G1GCPhaseTimes::GCParPhases scan_phase,
                       G1GCPhaseTimes::GCParPhases objcopy_phase,
                       bool remember_already_scanned_cards);

  // Merge cards from various sources (remembered sets, log buffers)
  // and calculate the cards that need to be scanned later (via scan_heap_roots()).
  // If initial_evacuation is set, this is called during the initial evacuation.
  void merge_heap_roots(bool initial_evacuation);

  void complete_evac_phase(bool has_more_than_one_evacuation_phase);
  // Prepare for and cleanup after scanning the heap roots. Must be called
  // once before and after in sequential code.
  void prepare_for_scan_heap_roots();

  // Print coarsening stats.
  void print_coarsen_stats();
  // Creates a task for cleaining up temporary data structures and the
  // card table, removing temporary duplicate detection information.
  G1AbstractSubTask* create_cleanup_after_scan_heap_roots_task();
  // Excludes the given region from heap root scanning.
  void exclude_region_from_scan(uint region_idx);
  // Creates a snapshot of the current _top values at the start of collection to
  // filter out card marks that we do not want to scan.
  void prepare_region_for_scan(G1HeapRegion* region);

  // Do work for regions in the current increment of the collection set, scanning
  // non-card based (heap) roots.
  void scan_collection_set_regions(G1ParScanThreadState* pss,
                                   uint worker_id,
                                   G1GCPhaseTimes::GCParPhases scan_phase,
                                   G1GCPhaseTimes::GCParPhases coderoots_phase,
                                   G1GCPhaseTimes::GCParPhases objcopy_phase);

  // Two methods for concurrent refinement support, executed concurrently to
  // the mutator:
  // Cleans the card at "*card_ptr_addr" before refinement, returns true iff the
  // card needs later refinement.
  bool clean_card_before_refine(CardValue** const card_ptr_addr);
  // Refine the region corresponding to "card_ptr". Must be called after
  // being filtered by clean_card_before_refine(), and after proper
  // fence/synchronization.
  void refine_card_concurrently(CardValue* const card_ptr,
                                const uint worker_id);

  // Print accumulated summary info from the start of the VM.
  void print_summary_info();

  // Print accumulated summary info from the last time called.
  void print_periodic_summary_info(const char* header, uint period_count, bool show_thread_times);
};

#endif // SHARE_GC_G1_G1REMSET_HPP
