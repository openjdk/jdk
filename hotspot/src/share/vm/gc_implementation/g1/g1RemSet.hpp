/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// A G1RemSet provides ways of iterating over pointers into a selected
// collection set.

class G1CollectedHeap;
class CardTableModRefBarrierSet;
class HRInto_G1RemSet;
class ConcurrentG1Refine;

class G1RemSet: public CHeapObj {
protected:
  G1CollectedHeap* _g1;

  unsigned _conc_refine_traversals;
  unsigned _conc_refine_cards;

  size_t n_workers();

public:
  G1RemSet(G1CollectedHeap* g1) :
    _g1(g1), _conc_refine_traversals(0), _conc_refine_cards(0)
  {}

  // Invoke "blk->do_oop" on all pointers into the CS in object in regions
  // outside the CS (having invoked "blk->set_region" to set the "from"
  // region correctly beforehand.) The "worker_i" param is for the
  // parallel case where the number of the worker thread calling this
  // function can be helpful in partitioning the work to be done. It
  // should be the same as the "i" passed to the calling thread's
  // work(i) function. In the sequential case this param will be ingored.
  virtual void oops_into_collection_set_do(OopsInHeapRegionClosure* blk,
                                           int worker_i) = 0;

  // Prepare for and cleanup after an oops_into_collection_set_do
  // call.  Must call each of these once before and after (in sequential
  // code) any threads call oops into collection set do.  (This offers an
  // opportunity to sequential setup and teardown of structures needed by a
  // parallel iteration over the CS's RS.)
  virtual void prepare_for_oops_into_collection_set_do() = 0;
  virtual void cleanup_after_oops_into_collection_set_do() = 0;

  // If "this" is of the given subtype, return "this", else "NULL".
  virtual HRInto_G1RemSet* as_HRInto_G1RemSet() { return NULL; }

  // Record, if necessary, the fact that *p (where "p" is in region "from")
  // has changed to its new value.
  virtual void write_ref(HeapRegion* from, oop* p) = 0;
  virtual void par_write_ref(HeapRegion* from, oop* p, int tid) = 0;

  // Requires "region_bm" and "card_bm" to be bitmaps with 1 bit per region
  // or card, respectively, such that a region or card with a corresponding
  // 0 bit contains no part of any live object.  Eliminates any remembered
  // set entries that correspond to dead heap ranges.
  virtual void scrub(BitMap* region_bm, BitMap* card_bm) = 0;
  // Like the above, but assumes is called in parallel: "worker_num" is the
  // parallel thread id of the current thread, and "claim_val" is the
  // value that should be used to claim heap regions.
  virtual void scrub_par(BitMap* region_bm, BitMap* card_bm,
                         int worker_num, int claim_val) = 0;

  // Do any "refinement" activity that might be appropriate to the given
  // G1RemSet.  If "refinement" has iterateive "passes", do one pass.
  // If "t" is non-NULL, it is the thread performing the refinement.
  // Default implementation does nothing.
  virtual void concurrentRefinementPass(ConcurrentG1Refine* cg1r) {}

  // Refine the card corresponding to "card_ptr".  If "sts" is non-NULL,
  // join and leave around parts that must be atomic wrt GC.  (NULL means
  // being done at a safepoint.)
  virtual void concurrentRefineOneCard(jbyte* card_ptr, int worker_i) {}

  unsigned conc_refine_cards() { return _conc_refine_cards; }

  // Print any relevant summary info.
  virtual void print_summary_info() {}

  // Prepare remebered set for verification.
  virtual void prepare_for_verify() {};
};


// The simplest possible G1RemSet: iterates over all objects in non-CS
// regions, searching for pointers into the CS.
class StupidG1RemSet: public G1RemSet {
public:
  StupidG1RemSet(G1CollectedHeap* g1) : G1RemSet(g1) {}

  void oops_into_collection_set_do(OopsInHeapRegionClosure* blk,
                                   int worker_i);

  void prepare_for_oops_into_collection_set_do() {}
  void cleanup_after_oops_into_collection_set_do() {}

  // Nothing is necessary in the version below.
  void write_ref(HeapRegion* from, oop* p) {}
  void par_write_ref(HeapRegion* from, oop* p, int tid) {}

  void scrub(BitMap* region_bm, BitMap* card_bm) {}
  void scrub_par(BitMap* region_bm, BitMap* card_bm,
                 int worker_num, int claim_val) {}

};

// A G1RemSet in which each heap region has a rem set that records the
// external heap references into it.  Uses a mod ref bs to track updates,
// so that they can be used to update the individual region remsets.

class HRInto_G1RemSet: public G1RemSet {
protected:
  enum SomePrivateConstants {
    UpdateRStoMergeSync  = 0,
    MergeRStoDoDirtySync = 1,
    DoDirtySync          = 2,
    LastSync             = 3,

    SeqTask              = 0,
    NumSeqTasks          = 1
  };

  CardTableModRefBS*             _ct_bs;
  SubTasksDone*                  _seq_task;
  G1CollectorPolicy* _g1p;

  ConcurrentG1Refine* _cg1r;

  size_t*             _cards_scanned;
  size_t              _total_cards_scanned;

  // _par_traversal_in_progress is "true" iff a parallel traversal is in
  // progress.  If so, then cards added to remembered sets should also have
  // their references into the collection summarized in "_new_refs".
  bool _par_traversal_in_progress;
  void set_par_traversal(bool b);
  GrowableArray<oop*>** _new_refs;
  void new_refs_iterate(OopClosure* cl);

public:
  // This is called to reset dual hash tables after the gc pause
  // is finished and the initial hash table is no longer being
  // scanned.
  void cleanupHRRS();

  HRInto_G1RemSet(G1CollectedHeap* g1, CardTableModRefBS* ct_bs);
  ~HRInto_G1RemSet();

  void oops_into_collection_set_do(OopsInHeapRegionClosure* blk,
                                   int worker_i);

  void prepare_for_oops_into_collection_set_do();
  void cleanup_after_oops_into_collection_set_do();
  void scanRS(OopsInHeapRegionClosure* oc, int worker_i);
  void scanNewRefsRS(OopsInHeapRegionClosure* oc, int worker_i);
  void updateRS(int worker_i);
  HeapRegion* calculateStartRegion(int i);

  HRInto_G1RemSet* as_HRInto_G1RemSet() { return this; }

  CardTableModRefBS* ct_bs() { return _ct_bs; }
  size_t cardsScanned() { return _total_cards_scanned; }

  // Record, if necessary, the fact that *p (where "p" is in region "from",
  // which is required to be non-NULL) has changed to a new non-NULL value.
  inline void write_ref(HeapRegion* from, oop* p);
  // The "_nv" version is the same; it exists just so that it is not virtual.
  inline void write_ref_nv(HeapRegion* from, oop* p);

  inline bool self_forwarded(oop obj);
  inline void par_write_ref(HeapRegion* from, oop* p, int tid);

  void scrub(BitMap* region_bm, BitMap* card_bm);
  void scrub_par(BitMap* region_bm, BitMap* card_bm,
                 int worker_num, int claim_val);

  virtual void concurrentRefinementPass(ConcurrentG1Refine* t);
  virtual void concurrentRefineOneCard(jbyte* card_ptr, int worker_i);

  virtual void print_summary_info();
  virtual void prepare_for_verify();
};

#define G1_REM_SET_LOGGING 0

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

class UpdateRSOopClosure: public OopClosure {
  HeapRegion* _from;
  HRInto_G1RemSet* _rs;
  int _worker_i;
public:
  UpdateRSOopClosure(HRInto_G1RemSet* rs, int worker_i = 0) :
    _from(NULL), _rs(rs), _worker_i(worker_i) {
    guarantee(_rs != NULL, "Requires an HRIntoG1RemSet");
  }

  void set_from(HeapRegion* from) {
    assert(from != NULL, "from region must be non-NULL");
    _from = from;
  }

  virtual void do_oop(narrowOop* p);
  virtual void do_oop(oop* p);

  // Override: this closure is idempotent.
  //  bool idempotent() { return true; }
  bool apply_to_weak_ref_discovered_field() { return true; }
};

