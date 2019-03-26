/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1OopClosures.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1RemSetSummary.hpp"
#include "gc/g1/heapRegion.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "utilities/ticks.hpp"

// A G1RemSet provides ways of iterating over pointers into a selected
// collection set.

class BitMap;
class CardTableBarrierSet;
class G1BlockOffsetTable;
class CodeBlobClosure;
class G1CollectedHeap;
class G1CMBitMap;
class G1HotCardCache;
class G1RemSetScanState;
class G1ParScanThreadState;
class G1Policy;
class G1ScanObjsDuringScanRSClosure;
class G1ScanObjsDuringUpdateRSClosure;
class HeapRegionClaimer;

// A G1RemSet in which each heap region has a rem set that records the
// external heap references into it.  Uses a mod ref bs to track updates,
// so that they can be used to update the individual region remsets.
class G1RemSet: public CHeapObj<mtGC> {
private:
  G1RemSetScanState* _scan_state;

  G1RemSetSummary _prev_period_summary;

  // Scan all remembered sets of the collection set for references into the collection
  // set.
  void scan_rem_set(G1ParScanThreadState* pss, uint worker_i);

  // Flush remaining refinement buffers for cross-region references to either evacuate references
  // into the collection set or update the remembered set.
  void update_rem_set(G1ParScanThreadState* pss, uint worker_i);

  G1CollectedHeap* _g1h;
  size_t _num_conc_refined_cards; // Number of cards refined concurrently to the mutator.

  G1CardTable*           _ct;
  G1Policy*              _g1p;
  G1HotCardCache*        _hot_card_cache;

public:

  typedef CardTable::CardValue CardValue;
  // Gives an approximation on how many threads can be expected to add records to
  // a remembered set in parallel. This can be used for sizing data structures to
  // decrease performance losses due to data structure sharing.
  // Examples for quantities that influence this value are the maximum number of
  // mutator threads, maximum number of concurrent refinement or GC threads.
  static uint num_par_rem_sets();

  // Initialize data that depends on the heap size being known.
  void initialize(size_t capacity, uint max_regions);

  G1RemSet(G1CollectedHeap* g1h,
           G1CardTable* ct,
           G1HotCardCache* hot_card_cache);
  ~G1RemSet();

  // Process all oops in the collection set from the cards in the refinement buffers and
  // remembered sets using pss.
  //
  // Further applies heap_region_codeblobs on the oops of the unmarked nmethods on the strong code
  // roots list for each region in the collection set.
  void oops_into_collection_set_do(G1ParScanThreadState* pss, uint worker_i);

  // Prepare for and cleanup after an oops_into_collection_set_do
  // call.  Must call each of these once before and after (in sequential
  // code) any thread calls oops_into_collection_set_do.
  void prepare_for_oops_into_collection_set_do();
  void cleanup_after_oops_into_collection_set_do();

  G1RemSetScanState* scan_state() const { return _scan_state; }

  // Refine the card corresponding to "card_ptr". Safe to be called concurrently
  // to the mutator.
  void refine_card_concurrently(CardValue* card_ptr,
                                uint worker_i);

  // Refine the card corresponding to "card_ptr", applying the given closure to
  // all references found. Must only be called during gc.
  // Returns whether the card has been scanned.
  bool refine_card_during_gc(CardValue* card_ptr, G1ScanObjsDuringUpdateRSClosure* update_rs_cl);

  // Print accumulated summary info from the start of the VM.
  void print_summary_info();

  // Print accumulated summary info from the last time called.
  void print_periodic_summary_info(const char* header, uint period_count);

  size_t num_conc_refined_cards() const { return _num_conc_refined_cards; }

  // Rebuilds the remembered set by scanning from bottom to TARS for all regions
  // using the given work gang.
  void rebuild_rem_set(G1ConcurrentMark* cm, WorkGang* workers, uint worker_id_offset);
};

class G1ScanRSForRegionClosure : public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  G1CardTable *_ct;

  G1ParScanThreadState* _pss;
  G1ScanObjsDuringScanRSClosure* _scan_objs_on_card_cl;

  G1RemSetScanState* _scan_state;

  G1GCPhaseTimes::GCParPhases _phase;

  uint   _worker_i;

  size_t _cards_scanned;
  size_t _cards_claimed;
  size_t _cards_skipped;

  Tickspan _rem_set_root_scan_time;
  Tickspan _rem_set_trim_partially_time;

  Tickspan _strong_code_root_scan_time;
  Tickspan _strong_code_trim_partially_time;

  void claim_card(size_t card_index, const uint region_idx_for_card);
  void scan_card(MemRegion mr, uint region_idx_for_card);

  void scan_rem_set_roots(HeapRegion* r);
  void scan_strong_code_roots(HeapRegion* r);
public:
  G1ScanRSForRegionClosure(G1RemSetScanState* scan_state,
                           G1ScanObjsDuringScanRSClosure* scan_obj_on_card,
                           G1ParScanThreadState* pss,
                           G1GCPhaseTimes::GCParPhases phase,
                           uint worker_i);

  bool do_heap_region(HeapRegion* r);

  Tickspan rem_set_root_scan_time() const { return _rem_set_root_scan_time; }
  Tickspan rem_set_trim_partially_time() const { return _rem_set_trim_partially_time; }

  Tickspan strong_code_root_scan_time() const { return _strong_code_root_scan_time;  }
  Tickspan strong_code_root_trim_partially_time() const { return _strong_code_trim_partially_time; }

  size_t cards_scanned() const { return _cards_scanned; }
  size_t cards_claimed() const { return _cards_claimed; }
  size_t cards_skipped() const { return _cards_skipped; }
};

#endif // SHARE_GC_G1_G1REMSET_HPP
