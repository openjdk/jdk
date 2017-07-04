/*
 * Copyright (c) 2001, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1REMSET_HPP
#define SHARE_VM_GC_G1_G1REMSET_HPP

#include "gc/g1/dirtyCardQueue.hpp"
#include "gc/g1/g1CardLiveData.hpp"
#include "gc/g1/g1RemSetSummary.hpp"
#include "gc/g1/heapRegion.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"

// A G1RemSet provides ways of iterating over pointers into a selected
// collection set.

class BitMap;
class CardTableModRefBS;
class G1BlockOffsetTable;
class CodeBlobClosure;
class G1CollectedHeap;
class G1HotCardCache;
class G1RemSetScanState;
class G1ParScanThreadState;
class G1Policy;
class G1SATBCardTableModRefBS;
class G1ScanObjsDuringScanRSClosure;
class G1ScanObjsDuringUpdateRSClosure;
class HeapRegionClaimer;

// A G1RemSet in which each heap region has a rem set that records the
// external heap references into it.  Uses a mod ref bs to track updates,
// so that they can be used to update the individual region remsets.
class G1RemSet: public CHeapObj<mtGC> {
private:
  G1RemSetScanState* _scan_state;
  G1CardLiveData _card_live_data;

  G1RemSetSummary _prev_period_summary;

  // A DirtyCardQueueSet that is used to hold cards that contain
  // references into the current collection set. This is used to
  // update the remembered sets of the regions in the collection
  // set in the event of an evacuation failure.
  DirtyCardQueueSet _into_cset_dirty_card_queue_set;

  // Scan all remembered sets of the collection set for references into the collection
  // set.
  void scan_rem_set(G1ParScanThreadState* pss,
                    CodeBlobClosure* heap_region_codeblobs,
                    uint worker_i);

  // Flush remaining refinement buffers for cross-region references to either evacuate references
  // into the collection set or update the remembered set.
  void update_rem_set(DirtyCardQueue* into_cset_dcq, G1ParScanThreadState* pss, uint worker_i);

  G1CollectedHeap* _g1;
  size_t _num_conc_refined_cards; // Number of cards refined concurrently to the mutator.

  CardTableModRefBS*     _ct_bs;
  G1Policy*              _g1p;
  G1HotCardCache*        _hot_card_cache;

public:
  // Gives an approximation on how many threads can be expected to add records to
  // a remembered set in parallel. This can be used for sizing data structures to
  // decrease performance losses due to data structure sharing.
  // Examples for quantities that influence this value are the maximum number of
  // mutator threads, maximum number of concurrent refinement or GC threads.
  static uint num_par_rem_sets();

  // Initialize data that depends on the heap size being known.
  void initialize(size_t capacity, uint max_regions);

  // This is called to reset dual hash tables after the gc pause
  // is finished and the initial hash table is no longer being
  // scanned.
  void cleanupHRRS();

  G1RemSet(G1CollectedHeap* g1,
           CardTableModRefBS* ct_bs,
           G1HotCardCache* hot_card_cache);
  ~G1RemSet();

  // Process all oops in the collection set from the cards in the refinement buffers and
  // remembered sets using pss.
  //
  // Further applies heap_region_codeblobs on the oops of the unmarked nmethods on the strong code
  // roots list for each region in the collection set.
  void oops_into_collection_set_do(G1ParScanThreadState* pss,
                                   CodeBlobClosure* heap_region_codeblobs,
                                   uint worker_i);

  // Prepare for and cleanup after an oops_into_collection_set_do
  // call.  Must call each of these once before and after (in sequential
  // code) any thread calls oops_into_collection_set_do.
  void prepare_for_oops_into_collection_set_do();
  void cleanup_after_oops_into_collection_set_do();

  G1RemSetScanState* scan_state() const { return _scan_state; }

  // Record, if necessary, the fact that *p (where "p" is in region "from",
  // which is required to be non-NULL) has changed to a new non-NULL value.
  template <class T> void par_write_ref(HeapRegion* from, T* p, uint tid);

  // Eliminates any remembered set entries that correspond to dead heap ranges.
  void scrub(uint worker_num, HeapRegionClaimer* hrclaimer);

  // Refine the card corresponding to "card_ptr". Safe to be called concurrently
  // to the mutator.
  void refine_card_concurrently(jbyte* card_ptr,
                                uint worker_i);

  // Refine the card corresponding to "card_ptr", applying the given closure to
  // all references found. Returns "true" if the given card contains
  // oops that have references into the current collection set. Must only be
  // called during gc.
  bool refine_card_during_gc(jbyte* card_ptr,
                             G1ScanObjsDuringUpdateRSClosure* update_rs_cl);

  // Print accumulated summary info from the start of the VM.
  void print_summary_info();

  // Print accumulated summary info from the last time called.
  void print_periodic_summary_info(const char* header, uint period_count);

  size_t num_conc_refined_cards() const { return _num_conc_refined_cards; }

  void create_card_live_data(WorkGang* workers, G1CMBitMap* mark_bitmap);
  void finalize_card_live_data(WorkGang* workers, G1CMBitMap* mark_bitmap);

  // Verify that the liveness count data created concurrently matches one created
  // during this safepoint.
  void verify_card_live_data(WorkGang* workers, G1CMBitMap* actual_bitmap);

  void clear_card_live_data(WorkGang* workers);

#ifdef ASSERT
  void verify_card_live_data_is_clear();
#endif
};

class G1ScanRSForRegionClosure : public HeapRegionClosure {
  G1RemSetScanState* _scan_state;

  size_t _cards_scanned;
  size_t _cards_claimed;
  size_t _cards_skipped;

  G1CollectedHeap* _g1h;

  G1ScanObjsDuringScanRSClosure* _scan_objs_on_card_cl;
  CodeBlobClosure* _code_root_cl;

  G1BlockOffsetTable* _bot;
  G1SATBCardTableModRefBS *_ct_bs;

  double _strong_code_root_scan_time_sec;
  uint   _worker_i;

  void scan_card(size_t index, HeapWord* card_start, HeapRegion *r);
  void scan_strong_code_roots(HeapRegion* r);
public:
  G1ScanRSForRegionClosure(G1RemSetScanState* scan_state,
                           G1ScanObjsDuringScanRSClosure* scan_obj_on_card,
                           CodeBlobClosure* code_root_cl,
                           uint worker_i);

  bool doHeapRegion(HeapRegion* r);

  double strong_code_root_scan_time_sec() {
    return _strong_code_root_scan_time_sec;
  }

  size_t cards_scanned() const { return _cards_scanned; }
  size_t cards_claimed() const { return _cards_claimed; }
  size_t cards_skipped() const { return _cards_skipped; }
};

class RebuildRSOopClosure: public ExtendedOopClosure {
  HeapRegion* _from;
  G1RemSet* _rs;
  uint _worker_i;

  template <class T> void do_oop_work(T* p);

public:
  RebuildRSOopClosure(G1RemSet* rs, uint worker_i = 0) :
    _from(NULL), _rs(rs), _worker_i(worker_i)
  {}

  void set_from(HeapRegion* from) {
    assert(from != NULL, "from region must be non-NULL");
    _from = from;
  }

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }
};

#endif // SHARE_VM_GC_G1_G1REMSET_HPP
