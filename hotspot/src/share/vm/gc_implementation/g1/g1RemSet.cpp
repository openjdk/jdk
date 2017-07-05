/*
 * Copyright (c) 2001, 2009, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_g1RemSet.cpp.incl"

#define CARD_REPEAT_HISTO 0

#if CARD_REPEAT_HISTO
static size_t ct_freq_sz;
static jbyte* ct_freq = NULL;

void init_ct_freq_table(size_t heap_sz_bytes) {
  if (ct_freq == NULL) {
    ct_freq_sz = heap_sz_bytes/CardTableModRefBS::card_size;
    ct_freq = new jbyte[ct_freq_sz];
    for (size_t j = 0; j < ct_freq_sz; j++) ct_freq[j] = 0;
  }
}

void ct_freq_note_card(size_t index) {
  assert(0 <= index && index < ct_freq_sz, "Bounds error.");
  if (ct_freq[index] < 100) { ct_freq[index]++; }
}

static IntHistogram card_repeat_count(10, 10);

void ct_freq_update_histo_and_reset() {
  for (size_t j = 0; j < ct_freq_sz; j++) {
    card_repeat_count.add_entry(ct_freq[j]);
    ct_freq[j] = 0;
  }

}
#endif


class IntoCSOopClosure: public OopsInHeapRegionClosure {
  OopsInHeapRegionClosure* _blk;
  G1CollectedHeap* _g1;
public:
  IntoCSOopClosure(G1CollectedHeap* g1, OopsInHeapRegionClosure* blk) :
    _g1(g1), _blk(blk) {}
  void set_region(HeapRegion* from) {
    _blk->set_region(from);
  }
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }
  template <class T> void do_oop_work(T* p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    if (_g1->obj_in_cs(obj)) _blk->do_oop(p);
  }
  bool apply_to_weak_ref_discovered_field() { return true; }
  bool idempotent() { return true; }
};

class IntoCSRegionClosure: public HeapRegionClosure {
  IntoCSOopClosure _blk;
  G1CollectedHeap* _g1;
public:
  IntoCSRegionClosure(G1CollectedHeap* g1, OopsInHeapRegionClosure* blk) :
    _g1(g1), _blk(g1, blk) {}
  bool doHeapRegion(HeapRegion* r) {
    if (!r->in_collection_set()) {
      _blk.set_region(r);
      if (r->isHumongous()) {
        if (r->startsHumongous()) {
          oop obj = oop(r->bottom());
          obj->oop_iterate(&_blk);
        }
      } else {
        r->oop_before_save_marks_iterate(&_blk);
      }
    }
    return false;
  }
};

void
StupidG1RemSet::oops_into_collection_set_do(OopsInHeapRegionClosure* oc,
                                            int worker_i) {
  IntoCSRegionClosure rc(_g1, oc);
  _g1->heap_region_iterate(&rc);
}

class VerifyRSCleanCardOopClosure: public OopClosure {
  G1CollectedHeap* _g1;
public:
  VerifyRSCleanCardOopClosure(G1CollectedHeap* g1) : _g1(g1) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }
  template <class T> void do_oop_work(T* p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    HeapRegion* to = _g1->heap_region_containing(obj);
    guarantee(to == NULL || !to->in_collection_set(),
              "Missed a rem set member.");
  }
};

HRInto_G1RemSet::HRInto_G1RemSet(G1CollectedHeap* g1, CardTableModRefBS* ct_bs)
  : G1RemSet(g1), _ct_bs(ct_bs), _g1p(_g1->g1_policy()),
    _cg1r(g1->concurrent_g1_refine()),
    _par_traversal_in_progress(false), _new_refs(NULL),
    _cards_scanned(NULL), _total_cards_scanned(0)
{
  _seq_task = new SubTasksDone(NumSeqTasks);
  guarantee(n_workers() > 0, "There should be some workers");
  _new_refs = NEW_C_HEAP_ARRAY(GrowableArray<OopOrNarrowOopStar>*, n_workers());
  for (uint i = 0; i < n_workers(); i++) {
    _new_refs[i] = new (ResourceObj::C_HEAP) GrowableArray<OopOrNarrowOopStar>(8192,true);
  }
}

HRInto_G1RemSet::~HRInto_G1RemSet() {
  delete _seq_task;
  for (uint i = 0; i < n_workers(); i++) {
    delete _new_refs[i];
  }
  FREE_C_HEAP_ARRAY(GrowableArray<OopOrNarrowOopStar>*, _new_refs);
}

void CountNonCleanMemRegionClosure::do_MemRegion(MemRegion mr) {
  if (_g1->is_in_g1_reserved(mr.start())) {
    _n += (int) ((mr.byte_size() / CardTableModRefBS::card_size));
    if (_start_first == NULL) _start_first = mr.start();
  }
}

class ScanRSClosure : public HeapRegionClosure {
  size_t _cards_done, _cards;
  G1CollectedHeap* _g1h;
  OopsInHeapRegionClosure* _oc;
  G1BlockOffsetSharedArray* _bot_shared;
  CardTableModRefBS *_ct_bs;
  int _worker_i;
  int _block_size;
  bool _try_claimed;
public:
  ScanRSClosure(OopsInHeapRegionClosure* oc, int worker_i) :
    _oc(oc),
    _cards(0),
    _cards_done(0),
    _worker_i(worker_i),
    _try_claimed(false)
  {
    _g1h = G1CollectedHeap::heap();
    _bot_shared = _g1h->bot_shared();
    _ct_bs = (CardTableModRefBS*) (_g1h->barrier_set());
    _block_size = MAX2<int>(G1RSetScanBlockSize, 1);
  }

  void set_try_claimed() { _try_claimed = true; }

  void scanCard(size_t index, HeapRegion *r) {
    _cards_done++;
    DirtyCardToOopClosure* cl =
      r->new_dcto_closure(_oc,
                         CardTableModRefBS::Precise,
                         HeapRegionDCTOC::IntoCSFilterKind);

    // Set the "from" region in the closure.
    _oc->set_region(r);
    HeapWord* card_start = _bot_shared->address_for_index(index);
    HeapWord* card_end = card_start + G1BlockOffsetSharedArray::N_words;
    Space *sp = SharedHeap::heap()->space_containing(card_start);
    MemRegion sm_region;
    if (ParallelGCThreads > 0) {
      // first find the used area
      sm_region = sp->used_region_at_save_marks();
    } else {
      // The closure is not idempotent.  We shouldn't look at objects
      // allocated during the GC.
      sm_region = sp->used_region_at_save_marks();
    }
    MemRegion mr = sm_region.intersection(MemRegion(card_start,card_end));
    if (!mr.is_empty()) {
      cl->do_MemRegion(mr);
    }
  }

  void printCard(HeapRegion* card_region, size_t card_index,
                 HeapWord* card_start) {
    gclog_or_tty->print_cr("T %d Region [" PTR_FORMAT ", " PTR_FORMAT ") "
                           "RS names card %p: "
                           "[" PTR_FORMAT ", " PTR_FORMAT ")",
                           _worker_i,
                           card_region->bottom(), card_region->end(),
                           card_index,
                           card_start, card_start + G1BlockOffsetSharedArray::N_words);
  }

  bool doHeapRegion(HeapRegion* r) {
    assert(r->in_collection_set(), "should only be called on elements of CS.");
    HeapRegionRemSet* hrrs = r->rem_set();
    if (hrrs->iter_is_complete()) return false; // All done.
    if (!_try_claimed && !hrrs->claim_iter()) return false;
    _g1h->push_dirty_cards_region(r);
    // If we didn't return above, then
    //   _try_claimed || r->claim_iter()
    // is true: either we're supposed to work on claimed-but-not-complete
    // regions, or we successfully claimed the region.
    HeapRegionRemSetIterator* iter = _g1h->rem_set_iterator(_worker_i);
    hrrs->init_iterator(iter);
    size_t card_index;

    // We claim cards in block so as to recude the contention. The block size is determined by
    // the G1RSetScanBlockSize parameter.
    size_t jump_to_card = hrrs->iter_claimed_next(_block_size);
    for (size_t current_card = 0; iter->has_next(card_index); current_card++) {
      if (current_card >= jump_to_card + _block_size) {
        jump_to_card = hrrs->iter_claimed_next(_block_size);
      }
      if (current_card < jump_to_card) continue;
      HeapWord* card_start = _g1h->bot_shared()->address_for_index(card_index);
#if 0
      gclog_or_tty->print("Rem set iteration yielded card [" PTR_FORMAT ", " PTR_FORMAT ").\n",
                          card_start, card_start + CardTableModRefBS::card_size_in_words);
#endif

      HeapRegion* card_region = _g1h->heap_region_containing(card_start);
      assert(card_region != NULL, "Yielding cards not in the heap?");
      _cards++;

      if (!card_region->is_on_dirty_cards_region_list()) {
        _g1h->push_dirty_cards_region(card_region);
      }

       // If the card is dirty, then we will scan it during updateRS.
      if (!card_region->in_collection_set() && !_ct_bs->is_card_dirty(card_index)) {
        // We make the card as "claimed" lazily (so races are possible but they're benign),
        // which reduces the number of duplicate scans (the rsets of the regions in the cset
        // can intersect).
        if (!_ct_bs->is_card_claimed(card_index)) {
          _ct_bs->set_card_claimed(card_index);
          scanCard(card_index, card_region);
        }
      }
    }
    if (!_try_claimed) {
      hrrs->set_iter_complete();
    }
    return false;
  }
  // Set all cards back to clean.
  void cleanup() {_g1h->cleanUpCardTable();}
  size_t cards_done() { return _cards_done;}
  size_t cards_looked_up() { return _cards;}
};

// We want the parallel threads to start their scanning at
// different collection set regions to avoid contention.
// If we have:
//          n collection set regions
//          p threads
// Then thread t will start at region t * floor (n/p)

HeapRegion* HRInto_G1RemSet::calculateStartRegion(int worker_i) {
  HeapRegion* result = _g1p->collection_set();
  if (ParallelGCThreads > 0) {
    size_t cs_size = _g1p->collection_set_size();
    int n_workers = _g1->workers()->total_workers();
    size_t cs_spans = cs_size / n_workers;
    size_t ind      = cs_spans * worker_i;
    for (size_t i = 0; i < ind; i++)
      result = result->next_in_collection_set();
  }
  return result;
}

void HRInto_G1RemSet::scanRS(OopsInHeapRegionClosure* oc, int worker_i) {
  double rs_time_start = os::elapsedTime();
  HeapRegion *startRegion = calculateStartRegion(worker_i);

  ScanRSClosure scanRScl(oc, worker_i);
  _g1->collection_set_iterate_from(startRegion, &scanRScl);
  scanRScl.set_try_claimed();
  _g1->collection_set_iterate_from(startRegion, &scanRScl);

  double scan_rs_time_sec = os::elapsedTime() - rs_time_start;

  assert( _cards_scanned != NULL, "invariant" );
  _cards_scanned[worker_i] = scanRScl.cards_done();

  _g1p->record_scan_rs_time(worker_i, scan_rs_time_sec * 1000.0);
}

void HRInto_G1RemSet::updateRS(int worker_i) {
  ConcurrentG1Refine* cg1r = _g1->concurrent_g1_refine();

  double start = os::elapsedTime();
  // Apply the appropriate closure to all remaining log entries.
  _g1->iterate_dirty_card_closure(false, worker_i);
  // Now there should be no dirty cards.
  if (G1RSLogCheckCardTable) {
    CountNonCleanMemRegionClosure cl(_g1);
    _ct_bs->mod_card_iterate(&cl);
    // XXX This isn't true any more: keeping cards of young regions
    // marked dirty broke it.  Need some reasonable fix.
    guarantee(cl.n() == 0, "Card table should be clean.");
  }

  _g1p->record_update_rs_time(worker_i, (os::elapsedTime() - start) * 1000.0);
}

#ifndef PRODUCT
class PrintRSClosure : public HeapRegionClosure {
  int _count;
public:
  PrintRSClosure() : _count(0) {}
  bool doHeapRegion(HeapRegion* r) {
    HeapRegionRemSet* hrrs = r->rem_set();
    _count += (int) hrrs->occupied();
    if (hrrs->occupied() == 0) {
      gclog_or_tty->print("Heap Region [" PTR_FORMAT ", " PTR_FORMAT ") "
                          "has no remset entries\n",
                          r->bottom(), r->end());
    } else {
      gclog_or_tty->print("Printing rem set for heap region [" PTR_FORMAT ", " PTR_FORMAT ")\n",
                          r->bottom(), r->end());
      r->print();
      hrrs->print();
      gclog_or_tty->print("\nDone printing rem set\n");
    }
    return false;
  }
  int occupied() {return _count;}
};
#endif

class CountRSSizeClosure: public HeapRegionClosure {
  size_t _n;
  size_t _tot;
  size_t _max;
  HeapRegion* _max_r;
  enum {
    N = 20,
    MIN = 6
  };
  int _histo[N];
public:
  CountRSSizeClosure() : _n(0), _tot(0), _max(0), _max_r(NULL) {
    for (int i = 0; i < N; i++) _histo[i] = 0;
  }
  bool doHeapRegion(HeapRegion* r) {
    if (!r->continuesHumongous()) {
      size_t occ = r->rem_set()->occupied();
      _n++;
      _tot += occ;
      if (occ > _max) {
        _max = occ;
        _max_r = r;
      }
      // Fit it into a histo bin.
      int s = 1 << MIN;
      int i = 0;
      while (occ > (size_t) s && i < (N-1)) {
        s = s << 1;
        i++;
      }
      _histo[i]++;
    }
    return false;
  }
  size_t n() { return _n; }
  size_t tot() { return _tot; }
  size_t mx() { return _max; }
  HeapRegion* mxr() { return _max_r; }
  void print_histo() {
    int mx = N;
    while (mx >= 0) {
      if (_histo[mx-1] > 0) break;
      mx--;
    }
    gclog_or_tty->print_cr("Number of regions with given RS sizes:");
    gclog_or_tty->print_cr("           <= %8d   %8d", 1 << MIN, _histo[0]);
    for (int i = 1; i < mx-1; i++) {
      gclog_or_tty->print_cr("  %8d  - %8d   %8d",
                    (1 << (MIN + i - 1)) + 1,
                    1 << (MIN + i),
                    _histo[i]);
    }
    gclog_or_tty->print_cr("            > %8d   %8d", (1 << (MIN+mx-2))+1, _histo[mx-1]);
  }
};

template <class T> void
HRInto_G1RemSet::scanNewRefsRS_work(OopsInHeapRegionClosure* oc,
                                    int worker_i) {
  double scan_new_refs_start_sec = os::elapsedTime();
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  CardTableModRefBS* ct_bs = (CardTableModRefBS*) (g1h->barrier_set());
  for (int i = 0; i < _new_refs[worker_i]->length(); i++) {
    T* p = (T*) _new_refs[worker_i]->at(i);
    oop obj = oopDesc::load_decode_heap_oop(p);
    // *p was in the collection set when p was pushed on "_new_refs", but
    // another thread may have processed this location from an RS, so it
    // might not point into the CS any longer.  If so, it's obviously been
    // processed, and we don't need to do anything further.
    if (g1h->obj_in_cs(obj)) {
      HeapRegion* r = g1h->heap_region_containing(p);

      DEBUG_ONLY(HeapRegion* to = g1h->heap_region_containing(obj));
      oc->set_region(r);
      // If "p" has already been processed concurrently, this is
      // idempotent.
      oc->do_oop(p);
    }
  }
  double scan_new_refs_time_ms = (os::elapsedTime() - scan_new_refs_start_sec) * 1000.0;
  _g1p->record_scan_new_refs_time(worker_i, scan_new_refs_time_ms);
}

void HRInto_G1RemSet::cleanupHRRS() {
  HeapRegionRemSet::cleanup();
}

void
HRInto_G1RemSet::oops_into_collection_set_do(OopsInHeapRegionClosure* oc,
                                             int worker_i) {
#if CARD_REPEAT_HISTO
  ct_freq_update_histo_and_reset();
#endif
  if (worker_i == 0) {
    _cg1r->clear_and_record_card_counts();
  }

  // Make this into a command-line flag...
  if (G1RSCountHisto && (ParallelGCThreads == 0 || worker_i == 0)) {
    CountRSSizeClosure count_cl;
    _g1->heap_region_iterate(&count_cl);
    gclog_or_tty->print_cr("Avg of %d RS counts is %f, max is %d, "
                  "max region is " PTR_FORMAT,
                  count_cl.n(), (float)count_cl.tot()/(float)count_cl.n(),
                  count_cl.mx(), count_cl.mxr());
    count_cl.print_histo();
  }

  if (ParallelGCThreads > 0) {
    // The two flags below were introduced temporarily to serialize
    // the updating and scanning of remembered sets. There are some
    // race conditions when these two operations are done in parallel
    // and they are causing failures. When we resolve said race
    // conditions, we'll revert back to parallel remembered set
    // updating and scanning. See CRs 6677707 and 6677708.
    if (G1UseParallelRSetUpdating || (worker_i == 0)) {
      updateRS(worker_i);
      scanNewRefsRS(oc, worker_i);
    } else {
      _g1p->record_update_rs_processed_buffers(worker_i, 0.0);
      _g1p->record_update_rs_time(worker_i, 0.0);
      _g1p->record_scan_new_refs_time(worker_i, 0.0);
    }
    if (G1UseParallelRSetScanning || (worker_i == 0)) {
      scanRS(oc, worker_i);
    } else {
      _g1p->record_scan_rs_time(worker_i, 0.0);
    }
  } else {
    assert(worker_i == 0, "invariant");
    updateRS(0);
    scanNewRefsRS(oc, 0);
    scanRS(oc, 0);
  }
}

void HRInto_G1RemSet::
prepare_for_oops_into_collection_set_do() {
#if G1_REM_SET_LOGGING
  PrintRSClosure cl;
  _g1->collection_set_iterate(&cl);
#endif
  cleanupHRRS();
  ConcurrentG1Refine* cg1r = _g1->concurrent_g1_refine();
  _g1->set_refine_cte_cl_concurrency(false);
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  dcqs.concatenate_logs();

  assert(!_par_traversal_in_progress, "Invariant between iterations.");
  if (ParallelGCThreads > 0) {
    set_par_traversal(true);
    _seq_task->set_par_threads((int)n_workers());
  }
  guarantee( _cards_scanned == NULL, "invariant" );
  _cards_scanned = NEW_C_HEAP_ARRAY(size_t, n_workers());
  for (uint i = 0; i < n_workers(); ++i) {
    _cards_scanned[i] = 0;
  }
  _total_cards_scanned = 0;
}


class cleanUpIteratorsClosure : public HeapRegionClosure {
  bool doHeapRegion(HeapRegion *r) {
    HeapRegionRemSet* hrrs = r->rem_set();
    hrrs->init_for_par_iteration();
    return false;
  }
};

class UpdateRSetOopsIntoCSImmediate : public OopClosure {
  G1CollectedHeap* _g1;
public:
  UpdateRSetOopsIntoCSImmediate(G1CollectedHeap* g1) : _g1(g1) { }
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }
  template <class T> void do_oop_work(T* p) {
    HeapRegion* to = _g1->heap_region_containing(oopDesc::load_decode_heap_oop(p));
    if (to->in_collection_set()) {
      to->rem_set()->add_reference(p, 0);
    }
  }
};

class UpdateRSetOopsIntoCSDeferred : public OopClosure {
  G1CollectedHeap* _g1;
  CardTableModRefBS* _ct_bs;
  DirtyCardQueue* _dcq;
public:
  UpdateRSetOopsIntoCSDeferred(G1CollectedHeap* g1, DirtyCardQueue* dcq) :
    _g1(g1), _ct_bs((CardTableModRefBS*)_g1->barrier_set()), _dcq(dcq) { }
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }
  template <class T> void do_oop_work(T* p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    if (_g1->obj_in_cs(obj)) {
      size_t card_index = _ct_bs->index_for(p);
      if (_ct_bs->mark_card_deferred(card_index)) {
        _dcq->enqueue((jbyte*)_ct_bs->byte_for_index(card_index));
      }
    }
  }
};

template <class T> void HRInto_G1RemSet::new_refs_iterate_work(OopClosure* cl) {
  for (size_t i = 0; i < n_workers(); i++) {
    for (int j = 0; j < _new_refs[i]->length(); j++) {
      T* p = (T*) _new_refs[i]->at(j);
      cl->do_oop(p);
    }
  }
}

void HRInto_G1RemSet::cleanup_after_oops_into_collection_set_do() {
  guarantee( _cards_scanned != NULL, "invariant" );
  _total_cards_scanned = 0;
  for (uint i = 0; i < n_workers(); ++i)
    _total_cards_scanned += _cards_scanned[i];
  FREE_C_HEAP_ARRAY(size_t, _cards_scanned);
  _cards_scanned = NULL;
  // Cleanup after copy
#if G1_REM_SET_LOGGING
  PrintRSClosure cl;
  _g1->heap_region_iterate(&cl);
#endif
  _g1->set_refine_cte_cl_concurrency(true);
  cleanUpIteratorsClosure iterClosure;
  _g1->collection_set_iterate(&iterClosure);
  // Set all cards back to clean.
  _g1->cleanUpCardTable();

  if (ParallelGCThreads > 0) {
    set_par_traversal(false);
  }

  if (_g1->evacuation_failed()) {
    // Restore remembered sets for the regions pointing into
    // the collection set.
    if (G1DeferredRSUpdate) {
      DirtyCardQueue dcq(&_g1->dirty_card_queue_set());
      UpdateRSetOopsIntoCSDeferred deferred_update(_g1, &dcq);
      new_refs_iterate(&deferred_update);
    } else {
      UpdateRSetOopsIntoCSImmediate immediate_update(_g1);
      new_refs_iterate(&immediate_update);
    }
  }
  for (uint i = 0; i < n_workers(); i++) {
    _new_refs[i]->clear();
  }

  assert(!_par_traversal_in_progress, "Invariant between iterations.");
}

class UpdateRSObjectClosure: public ObjectClosure {
  UpdateRSOopClosure* _update_rs_oop_cl;
public:
  UpdateRSObjectClosure(UpdateRSOopClosure* update_rs_oop_cl) :
    _update_rs_oop_cl(update_rs_oop_cl) {}
  void do_object(oop obj) {
    obj->oop_iterate(_update_rs_oop_cl);
  }

};

class ScrubRSClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  BitMap* _region_bm;
  BitMap* _card_bm;
  CardTableModRefBS* _ctbs;
public:
  ScrubRSClosure(BitMap* region_bm, BitMap* card_bm) :
    _g1h(G1CollectedHeap::heap()),
    _region_bm(region_bm), _card_bm(card_bm),
    _ctbs(NULL)
  {
    ModRefBarrierSet* bs = _g1h->mr_bs();
    guarantee(bs->is_a(BarrierSet::CardTableModRef), "Precondition");
    _ctbs = (CardTableModRefBS*)bs;
  }

  bool doHeapRegion(HeapRegion* r) {
    if (!r->continuesHumongous()) {
      r->rem_set()->scrub(_ctbs, _region_bm, _card_bm);
    }
    return false;
  }
};

void HRInto_G1RemSet::scrub(BitMap* region_bm, BitMap* card_bm) {
  ScrubRSClosure scrub_cl(region_bm, card_bm);
  _g1->heap_region_iterate(&scrub_cl);
}

void HRInto_G1RemSet::scrub_par(BitMap* region_bm, BitMap* card_bm,
                                int worker_num, int claim_val) {
  ScrubRSClosure scrub_cl(region_bm, card_bm);
  _g1->heap_region_par_iterate_chunked(&scrub_cl, worker_num, claim_val);
}


static IntHistogram out_of_histo(50, 50);

void HRInto_G1RemSet::concurrentRefineOneCard_impl(jbyte* card_ptr, int worker_i) {
  // Construct the region representing the card.
  HeapWord* start = _ct_bs->addr_for(card_ptr);
  // And find the region containing it.
  HeapRegion* r = _g1->heap_region_containing(start);
  assert(r != NULL, "unexpected null");

  HeapWord* end   = _ct_bs->addr_for(card_ptr + 1);
  MemRegion dirtyRegion(start, end);

#if CARD_REPEAT_HISTO
  init_ct_freq_table(_g1->g1_reserved_obj_bytes());
  ct_freq_note_card(_ct_bs->index_for(start));
#endif

  UpdateRSOopClosure update_rs_oop_cl(this, worker_i);
  update_rs_oop_cl.set_from(r);
  FilterOutOfRegionClosure filter_then_update_rs_oop_cl(r, &update_rs_oop_cl);

  // Undirty the card.
  *card_ptr = CardTableModRefBS::clean_card_val();
  // We must complete this write before we do any of the reads below.
  OrderAccess::storeload();
  // And process it, being careful of unallocated portions of TLAB's.
  HeapWord* stop_point =
    r->oops_on_card_seq_iterate_careful(dirtyRegion,
                                        &filter_then_update_rs_oop_cl);
  // If stop_point is non-null, then we encountered an unallocated region
  // (perhaps the unfilled portion of a TLAB.)  For now, we'll dirty the
  // card and re-enqueue: if we put off the card until a GC pause, then the
  // unallocated portion will be filled in.  Alternatively, we might try
  // the full complexity of the technique used in "regular" precleaning.
  if (stop_point != NULL) {
    // The card might have gotten re-dirtied and re-enqueued while we
    // worked.  (In fact, it's pretty likely.)
    if (*card_ptr != CardTableModRefBS::dirty_card_val()) {
      *card_ptr = CardTableModRefBS::dirty_card_val();
      MutexLockerEx x(Shared_DirtyCardQ_lock,
                      Mutex::_no_safepoint_check_flag);
      DirtyCardQueue* sdcq =
        JavaThread::dirty_card_queue_set().shared_dirty_card_queue();
      sdcq->enqueue(card_ptr);
    }
  } else {
    out_of_histo.add_entry(filter_then_update_rs_oop_cl.out_of_region());
    _conc_refine_cards++;
  }
}

void HRInto_G1RemSet::concurrentRefineOneCard(jbyte* card_ptr, int worker_i) {
  // If the card is no longer dirty, nothing to do.
  if (*card_ptr != CardTableModRefBS::dirty_card_val()) return;

  // Construct the region representing the card.
  HeapWord* start = _ct_bs->addr_for(card_ptr);
  // And find the region containing it.
  HeapRegion* r = _g1->heap_region_containing(start);
  if (r == NULL) {
    guarantee(_g1->is_in_permanent(start), "Or else where?");
    return;  // Not in the G1 heap (might be in perm, for example.)
  }
  // Why do we have to check here whether a card is on a young region,
  // given that we dirty young regions and, as a result, the
  // post-barrier is supposed to filter them out and never to enqueue
  // them? When we allocate a new region as the "allocation region" we
  // actually dirty its cards after we release the lock, since card
  // dirtying while holding the lock was a performance bottleneck. So,
  // as a result, it is possible for other threads to actually
  // allocate objects in the region (after the acquire the lock)
  // before all the cards on the region are dirtied. This is unlikely,
  // and it doesn't happen often, but it can happen. So, the extra
  // check below filters out those cards.
  if (r->is_young()) {
    return;
  }
  // While we are processing RSet buffers during the collection, we
  // actually don't want to scan any cards on the collection set,
  // since we don't want to update remebered sets with entries that
  // point into the collection set, given that live objects from the
  // collection set are about to move and such entries will be stale
  // very soon. This change also deals with a reliability issue which
  // involves scanning a card in the collection set and coming across
  // an array that was being chunked and looking malformed. Note,
  // however, that if evacuation fails, we have to scan any objects
  // that were not moved and create any missing entries.
  if (r->in_collection_set()) {
    return;
  }

  // Should we defer processing the card?
  //
  // Previously the result from the insert_cache call would be
  // either card_ptr (implying that card_ptr was currently "cold"),
  // null (meaning we had inserted the card ptr into the "hot"
  // cache, which had some headroom), or a "hot" card ptr
  // extracted from the "hot" cache.
  //
  // Now that the _card_counts cache in the ConcurrentG1Refine
  // instance is an evicting hash table, the result we get back
  // could be from evicting the card ptr in an already occupied
  // bucket (in which case we have replaced the card ptr in the
  // bucket with card_ptr and "defer" is set to false). To avoid
  // having a data structure (updates to which would need a lock)
  // to hold these unprocessed dirty cards, we need to immediately
  // process card_ptr. The actions needed to be taken on return
  // from cache_insert are summarized in the following table:
  //
  // res      defer   action
  // --------------------------------------------------------------
  // null     false   card evicted from _card_counts & replaced with
  //                  card_ptr; evicted ptr added to hot cache.
  //                  No need to process res; immediately process card_ptr
  //
  // null     true    card not evicted from _card_counts; card_ptr added
  //                  to hot cache.
  //                  Nothing to do.
  //
  // non-null false   card evicted from _card_counts & replaced with
  //                  card_ptr; evicted ptr is currently "cold" or
  //                  caused an eviction from the hot cache.
  //                  Immediately process res; process card_ptr.
  //
  // non-null true    card not evicted from _card_counts; card_ptr is
  //                  currently cold, or caused an eviction from hot
  //                  cache.
  //                  Immediately process res; no need to process card_ptr.

  jbyte* res = card_ptr;
  bool defer = false;
  if (_cg1r->use_cache()) {
    jbyte* res = _cg1r->cache_insert(card_ptr, &defer);
    if (res != NULL && (res != card_ptr || defer)) {
      start = _ct_bs->addr_for(res);
      r = _g1->heap_region_containing(start);
      if (r == NULL) {
        assert(_g1->is_in_permanent(start), "Or else where?");
      } else {
        guarantee(!r->is_young(), "It was evicted in the current minor cycle.");
        // Process card pointer we get back from the hot card cache
        concurrentRefineOneCard_impl(res, worker_i);
      }
    }
  }

  if (!defer) {
    concurrentRefineOneCard_impl(card_ptr, worker_i);
  }
}

class HRRSStatsIter: public HeapRegionClosure {
  size_t _occupied;
  size_t _total_mem_sz;
  size_t _max_mem_sz;
  HeapRegion* _max_mem_sz_region;
public:
  HRRSStatsIter() :
    _occupied(0),
    _total_mem_sz(0),
    _max_mem_sz(0),
    _max_mem_sz_region(NULL)
  {}

  bool doHeapRegion(HeapRegion* r) {
    if (r->continuesHumongous()) return false;
    size_t mem_sz = r->rem_set()->mem_size();
    if (mem_sz > _max_mem_sz) {
      _max_mem_sz = mem_sz;
      _max_mem_sz_region = r;
    }
    _total_mem_sz += mem_sz;
    size_t occ = r->rem_set()->occupied();
    _occupied += occ;
    return false;
  }
  size_t total_mem_sz() { return _total_mem_sz; }
  size_t max_mem_sz() { return _max_mem_sz; }
  size_t occupied() { return _occupied; }
  HeapRegion* max_mem_sz_region() { return _max_mem_sz_region; }
};

class PrintRSThreadVTimeClosure : public ThreadClosure {
public:
  virtual void do_thread(Thread *t) {
    ConcurrentG1RefineThread* crt = (ConcurrentG1RefineThread*) t;
    gclog_or_tty->print("    %5.2f", crt->vtime_accum());
  }
};

void HRInto_G1RemSet::print_summary_info() {
  G1CollectedHeap* g1 = G1CollectedHeap::heap();

#if CARD_REPEAT_HISTO
  gclog_or_tty->print_cr("\nG1 card_repeat count histogram: ");
  gclog_or_tty->print_cr("  # of repeats --> # of cards with that number.");
  card_repeat_count.print_on(gclog_or_tty);
#endif

  if (FILTEROUTOFREGIONCLOSURE_DOHISTOGRAMCOUNT) {
    gclog_or_tty->print_cr("\nG1 rem-set out-of-region histogram: ");
    gclog_or_tty->print_cr("  # of CS ptrs --> # of cards with that number.");
    out_of_histo.print_on(gclog_or_tty);
  }
  gclog_or_tty->print_cr("\n Concurrent RS processed %d cards",
                         _conc_refine_cards);
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  jint tot_processed_buffers =
    dcqs.processed_buffers_mut() + dcqs.processed_buffers_rs_thread();
  gclog_or_tty->print_cr("  Of %d completed buffers:", tot_processed_buffers);
  gclog_or_tty->print_cr("     %8d (%5.1f%%) by conc RS threads.",
                dcqs.processed_buffers_rs_thread(),
                100.0*(float)dcqs.processed_buffers_rs_thread()/
                (float)tot_processed_buffers);
  gclog_or_tty->print_cr("     %8d (%5.1f%%) by mutator threads.",
                dcqs.processed_buffers_mut(),
                100.0*(float)dcqs.processed_buffers_mut()/
                (float)tot_processed_buffers);
  gclog_or_tty->print_cr("  Conc RS threads times(s)");
  PrintRSThreadVTimeClosure p;
  gclog_or_tty->print("     ");
  g1->concurrent_g1_refine()->threads_do(&p);
  gclog_or_tty->print_cr("");

  if (G1UseHRIntoRS) {
    HRRSStatsIter blk;
    g1->heap_region_iterate(&blk);
    gclog_or_tty->print_cr("  Total heap region rem set sizes = " SIZE_FORMAT "K."
                           "  Max = " SIZE_FORMAT "K.",
                           blk.total_mem_sz()/K, blk.max_mem_sz()/K);
    gclog_or_tty->print_cr("  Static structures = " SIZE_FORMAT "K,"
                           " free_lists = " SIZE_FORMAT "K.",
                           HeapRegionRemSet::static_mem_size()/K,
                           HeapRegionRemSet::fl_mem_size()/K);
    gclog_or_tty->print_cr("    %d occupied cards represented.",
                           blk.occupied());
    gclog_or_tty->print_cr("    Max sz region = [" PTR_FORMAT ", " PTR_FORMAT " )"
                           ", cap = " SIZE_FORMAT "K, occ = " SIZE_FORMAT "K.",
                           blk.max_mem_sz_region()->bottom(), blk.max_mem_sz_region()->end(),
                           (blk.max_mem_sz_region()->rem_set()->mem_size() + K - 1)/K,
                           (blk.max_mem_sz_region()->rem_set()->occupied() + K - 1)/K);
    gclog_or_tty->print_cr("    Did %d coarsenings.",
                  HeapRegionRemSet::n_coarsenings());

  }
}
void HRInto_G1RemSet::prepare_for_verify() {
  if (G1HRRSFlushLogBuffersOnVerify &&
      (VerifyBeforeGC || VerifyAfterGC)
      &&  !_g1->full_collection()) {
    cleanupHRRS();
    _g1->set_refine_cte_cl_concurrency(false);
    if (SafepointSynchronize::is_at_safepoint()) {
      DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
      dcqs.concatenate_logs();
    }
    bool cg1r_use_cache = _cg1r->use_cache();
    _cg1r->set_use_cache(false);
    updateRS(0);
    _cg1r->set_use_cache(cg1r_use_cache);

    assert(JavaThread::dirty_card_queue_set().completed_buffers_num() == 0, "All should be consumed");
  }
}
