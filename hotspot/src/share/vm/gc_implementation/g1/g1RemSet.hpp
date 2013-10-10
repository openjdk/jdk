/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1REMSET_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1REMSET_HPP

#include "gc_implementation/g1/g1RemSetSummary.hpp"

// A G1RemSet provides ways of iterating over pointers into a selected
// collection set.

class G1CollectedHeap;
class CardTableModRefBarrierSet;
class ConcurrentG1Refine;

// A G1RemSet in which each heap region has a rem set that records the
// external heap references into it.  Uses a mod ref bs to track updates,
// so that they can be used to update the individual region remsets.

class G1RemSet: public CHeapObj<mtGC> {
private:
  G1RemSetSummary _prev_period_summary;
protected:
  G1CollectedHeap* _g1;
  size_t _conc_refine_cards;
  uint n_workers();

protected:
  enum SomePrivateConstants {
    UpdateRStoMergeSync  = 0,
    MergeRStoDoDirtySync = 1,
    DoDirtySync          = 2,
    LastSync             = 3,

    SeqTask              = 0,
    NumSeqTasks          = 1
  };

  CardTableModRefBS*     _ct_bs;
  SubTasksDone*          _seq_task;
  G1CollectorPolicy*     _g1p;

  ConcurrentG1Refine*    _cg1r;

  size_t*                _cards_scanned;
  size_t                 _total_cards_scanned;

  // Used for caching the closure that is responsible for scanning
  // references into the collection set.
  OopsInHeapRegionClosure** _cset_rs_update_cl;

  // Print the given summary info
  virtual void print_summary_info(G1RemSetSummary * summary, const char * header = NULL);
public:
  // This is called to reset dual hash tables after the gc pause
  // is finished and the initial hash table is no longer being
  // scanned.
  void cleanupHRRS();

  G1RemSet(G1CollectedHeap* g1, CardTableModRefBS* ct_bs);
  ~G1RemSet();

  // Invoke "blk->do_oop" on all pointers into the collection set
  // from objects in regions outside the collection set (having
  // invoked "blk->set_region" to set the "from" region correctly
  // beforehand.)
  //
  // Invoke code_root_cl->do_code_blob on the unmarked nmethods
  // on the strong code roots list for each region in the
  // collection set.
  //
  // The "worker_i" param is for the parallel case where the id
  // of the worker thread calling this function can be helpful in
  // partitioning the work to be done. It should be the same as
  // the "i" passed to the calling thread's work(i) function.
  // In the sequential case this param will be ignored.
  void oops_into_collection_set_do(OopsInHeapRegionClosure* blk,
                                   CodeBlobToOopClosure* code_root_cl,
                                   int worker_i);

  // Prepare for and cleanup after an oops_into_collection_set_do
  // call.  Must call each of these once before and after (in sequential
  // code) any threads call oops_into_collection_set_do.  (This offers an
  // opportunity to sequential setup and teardown of structures needed by a
  // parallel iteration over the CS's RS.)
  void prepare_for_oops_into_collection_set_do();
  void cleanup_after_oops_into_collection_set_do();

  void scanRS(OopsInHeapRegionClosure* oc,
              CodeBlobToOopClosure* code_root_cl,
              int worker_i);

  void updateRS(DirtyCardQueue* into_cset_dcq, int worker_i);

  CardTableModRefBS* ct_bs() { return _ct_bs; }
  size_t cardsScanned() { return _total_cards_scanned; }

  // Record, if necessary, the fact that *p (where "p" is in region "from",
  // which is required to be non-NULL) has changed to a new non-NULL value.
  template <class T> void write_ref(HeapRegion* from, T* p);
  template <class T> void par_write_ref(HeapRegion* from, T* p, int tid);

  // Requires "region_bm" and "card_bm" to be bitmaps with 1 bit per region
  // or card, respectively, such that a region or card with a corresponding
  // 0 bit contains no part of any live object.  Eliminates any remembered
  // set entries that correspond to dead heap ranges.
  void scrub(BitMap* region_bm, BitMap* card_bm);

  // Like the above, but assumes is called in parallel: "worker_num" is the
  // parallel thread id of the current thread, and "claim_val" is the
  // value that should be used to claim heap regions.
  void scrub_par(BitMap* region_bm, BitMap* card_bm,
                 uint worker_num, int claim_val);

  // Refine the card corresponding to "card_ptr".
  // If check_for_refs_into_cset is true, a true result is returned
  // if the given card contains oops that have references into the
  // current collection set.
  virtual bool refine_card(jbyte* card_ptr,
                           int worker_i,
                           bool check_for_refs_into_cset);

  // Print accumulated summary info from the start of the VM.
  virtual void print_summary_info();

  // Print accumulated summary info from the last time called.
  virtual void print_periodic_summary_info(const char* header);

  // Prepare remembered set for verification.
  virtual void prepare_for_verify();

  size_t conc_refine_cards() const {
    return _conc_refine_cards;
  }
};

class CountNonCleanMemRegionClosure: public MemRegionClosure {
  G1CollectedHeap* _g1;
  int _n;
  HeapWord* _start_first;
public:
  CountNonCleanMemRegionClosure(G1CollectedHeap* g1) :
    _g1(g1), _n(0), _start_first(NULL)
  {}
  void do_MemRegion(MemRegion mr);
  int n() { return _n; };
  HeapWord* start_first() { return _start_first; }
};

class UpdateRSOopClosure: public ExtendedOopClosure {
  HeapRegion* _from;
  G1RemSet* _rs;
  int _worker_i;

  template <class T> void do_oop_work(T* p);

public:
  UpdateRSOopClosure(G1RemSet* rs, int worker_i = 0) :
    _from(NULL), _rs(rs), _worker_i(worker_i)
  {}

  void set_from(HeapRegion* from) {
    assert(from != NULL, "from region must be non-NULL");
    _from = from;
  }

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(oop* p)       { do_oop_work(p); }

  // Override: this closure is idempotent.
  //  bool idempotent() { return true; }
  bool apply_to_weak_ref_discovered_field() { return true; }
};

class UpdateRSetImmediate: public OopsInHeapRegionClosure {
private:
  G1RemSet* _g1_rem_set;

  template <class T> void do_oop_work(T* p);
public:
  UpdateRSetImmediate(G1RemSet* rs) :
    _g1_rem_set(rs) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }
};


#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1REMSET_HPP
