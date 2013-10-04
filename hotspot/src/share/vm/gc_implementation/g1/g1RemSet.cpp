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

#include "precompiled.hpp"
#include "gc_implementation/g1/bufferingOopClosure.hpp"
#include "gc_implementation/g1/concurrentG1Refine.hpp"
#include "gc_implementation/g1/concurrentG1RefineThread.hpp"
#include "gc_implementation/g1/g1BlockOffsetTable.inline.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/g1HotCardCache.hpp"
#include "gc_implementation/g1/g1GCPhaseTimes.hpp"
#include "gc_implementation/g1/g1OopClosures.inline.hpp"
#include "gc_implementation/g1/g1RemSet.inline.hpp"
#include "gc_implementation/g1/heapRegionSeq.inline.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "memory/iterator.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/intHisto.hpp"

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

G1RemSet::G1RemSet(G1CollectedHeap* g1, CardTableModRefBS* ct_bs)
  : _g1(g1), _conc_refine_cards(0),
    _ct_bs(ct_bs), _g1p(_g1->g1_policy()),
    _cg1r(g1->concurrent_g1_refine()),
    _cset_rs_update_cl(NULL),
    _cards_scanned(NULL), _total_cards_scanned(0),
    _prev_period_summary()
{
  _seq_task = new SubTasksDone(NumSeqTasks);
  guarantee(n_workers() > 0, "There should be some workers");
  _cset_rs_update_cl = NEW_C_HEAP_ARRAY(OopsInHeapRegionClosure*, n_workers(), mtGC);
  for (uint i = 0; i < n_workers(); i++) {
    _cset_rs_update_cl[i] = NULL;
  }
  if (G1SummarizeRSetStats) {
    _prev_period_summary.initialize(this);
  }
}

G1RemSet::~G1RemSet() {
  delete _seq_task;
  for (uint i = 0; i < n_workers(); i++) {
    assert(_cset_rs_update_cl[i] == NULL, "it should be");
  }
  FREE_C_HEAP_ARRAY(OopsInHeapRegionClosure*, _cset_rs_update_cl, mtGC);
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
  CodeBlobToOopClosure* _code_root_cl;

  G1BlockOffsetSharedArray* _bot_shared;
  G1SATBCardTableModRefBS *_ct_bs;

  double _strong_code_root_scan_time_sec;
  int    _worker_i;
  int    _block_size;
  bool   _try_claimed;

public:
  ScanRSClosure(OopsInHeapRegionClosure* oc,
                CodeBlobToOopClosure* code_root_cl,
                int worker_i) :
    _oc(oc),
    _code_root_cl(code_root_cl),
    _strong_code_root_scan_time_sec(0.0),
    _cards(0),
    _cards_done(0),
    _worker_i(worker_i),
    _try_claimed(false)
  {
    _g1h = G1CollectedHeap::heap();
    _bot_shared = _g1h->bot_shared();
    _ct_bs = _g1h->g1_barrier_set();
    _block_size = MAX2<int>(G1RSetScanBlockSize, 1);
  }

  void set_try_claimed() { _try_claimed = true; }

  void scanCard(size_t index, HeapRegion *r) {
    // Stack allocate the DirtyCardToOopClosure instance
    HeapRegionDCTOC cl(_g1h, r, _oc,
                       CardTableModRefBS::Precise,
                       HeapRegionDCTOC::IntoCSFilterKind);

    // Set the "from" region in the closure.
    _oc->set_region(r);
    HeapWord* card_start = _bot_shared->address_for_index(index);
    HeapWord* card_end = card_start + G1BlockOffsetSharedArray::N_words;
    Space *sp = SharedHeap::heap()->space_containing(card_start);
    MemRegion sm_region = sp->used_region_at_save_marks();
    MemRegion mr = sm_region.intersection(MemRegion(card_start,card_end));
    if (!mr.is_empty() && !_ct_bs->is_card_claimed(index)) {
      // We make the card as "claimed" lazily (so races are possible
      // but they're benign), which reduces the number of duplicate
      // scans (the rsets of the regions in the cset can intersect).
      _ct_bs->set_card_claimed(index);
      _cards_done++;
      cl.do_MemRegion(mr);
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

  void scan_strong_code_roots(HeapRegion* r) {
    double scan_start = os::elapsedTime();
    r->strong_code_roots_do(_code_root_cl);
    _strong_code_root_scan_time_sec += (os::elapsedTime() - scan_start);
  }

  bool doHeapRegion(HeapRegion* r) {
    assert(r->in_collection_set(), "should only be called on elements of CS.");
    HeapRegionRemSet* hrrs = r->rem_set();
    if (hrrs->iter_is_complete()) return false; // All done.
    if (!_try_claimed && !hrrs->claim_iter()) return false;
    // If we ever free the collection set concurrently, we should also
    // clear the card table concurrently therefore we won't need to
    // add regions of the collection set to the dirty cards region.
    _g1h->push_dirty_cards_region(r);
    // If we didn't return above, then
    //   _try_claimed || r->claim_iter()
    // is true: either we're supposed to work on claimed-but-not-complete
    // regions, or we successfully claimed the region.

    HeapRegionRemSetIterator iter(hrrs);
    size_t card_index;

    // We claim cards in block so as to recude the contention. The block size is determined by
    // the G1RSetScanBlockSize parameter.
    size_t jump_to_card = hrrs->iter_claimed_next(_block_size);
    for (size_t current_card = 0; iter.has_next(card_index); current_card++) {
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
      if (!card_region->in_collection_set() &&
          !_ct_bs->is_card_dirty(card_index)) {
        scanCard(card_index, card_region);
      }
    }
    if (!_try_claimed) {
      // Scan the strong code root list attached to the current region
      scan_strong_code_roots(r);

      hrrs->set_iter_complete();
    }
    return false;
  }

  double strong_code_root_scan_time_sec() {
    return _strong_code_root_scan_time_sec;
  }

  size_t cards_done() { return _cards_done;}
  size_t cards_looked_up() { return _cards;}
};

void G1RemSet::scanRS(OopsInHeapRegionClosure* oc,
                      CodeBlobToOopClosure* code_root_cl,
                      int worker_i) {
  double rs_time_start = os::elapsedTime();
  HeapRegion *startRegion = _g1->start_cset_region_for_worker(worker_i);

  ScanRSClosure scanRScl(oc, code_root_cl, worker_i);

  _g1->collection_set_iterate_from(startRegion, &scanRScl);
  scanRScl.set_try_claimed();
  _g1->collection_set_iterate_from(startRegion, &scanRScl);

  double scan_rs_time_sec = (os::elapsedTime() - rs_time_start)
                            - scanRScl.strong_code_root_scan_time_sec();

  assert(_cards_scanned != NULL, "invariant");
  _cards_scanned[worker_i] = scanRScl.cards_done();

  _g1p->phase_times()->record_scan_rs_time(worker_i, scan_rs_time_sec * 1000.0);
  _g1p->phase_times()->record_strong_code_root_scan_time(worker_i,
                                                         scanRScl.strong_code_root_scan_time_sec() * 1000.0);
}

// Closure used for updating RSets and recording references that
// point into the collection set. Only called during an
// evacuation pause.

class RefineRecordRefsIntoCSCardTableEntryClosure: public CardTableEntryClosure {
  G1RemSet* _g1rs;
  DirtyCardQueue* _into_cset_dcq;
public:
  RefineRecordRefsIntoCSCardTableEntryClosure(G1CollectedHeap* g1h,
                                              DirtyCardQueue* into_cset_dcq) :
    _g1rs(g1h->g1_rem_set()), _into_cset_dcq(into_cset_dcq)
  {}
  bool do_card_ptr(jbyte* card_ptr, int worker_i) {
    // The only time we care about recording cards that
    // contain references that point into the collection set
    // is during RSet updating within an evacuation pause.
    // In this case worker_i should be the id of a GC worker thread.
    assert(SafepointSynchronize::is_at_safepoint(), "not during an evacuation pause");
    assert(worker_i < (int) (ParallelGCThreads == 0 ? 1 : ParallelGCThreads), "should be a GC worker");

    if (_g1rs->refine_card(card_ptr, worker_i, true)) {
      // 'card_ptr' contains references that point into the collection
      // set. We need to record the card in the DCQS
      // (G1CollectedHeap::into_cset_dirty_card_queue_set())
      // that's used for that purpose.
      //
      // Enqueue the card
      _into_cset_dcq->enqueue(card_ptr);
    }
    return true;
  }
};

void G1RemSet::updateRS(DirtyCardQueue* into_cset_dcq, int worker_i) {
  double start = os::elapsedTime();
  // Apply the given closure to all remaining log entries.
  RefineRecordRefsIntoCSCardTableEntryClosure into_cset_update_rs_cl(_g1, into_cset_dcq);

  _g1->iterate_dirty_card_closure(&into_cset_update_rs_cl, into_cset_dcq, false, worker_i);

  // Now there should be no dirty cards.
  if (G1RSLogCheckCardTable) {
    CountNonCleanMemRegionClosure cl(_g1);
    _ct_bs->mod_card_iterate(&cl);
    // XXX This isn't true any more: keeping cards of young regions
    // marked dirty broke it.  Need some reasonable fix.
    guarantee(cl.n() == 0, "Card table should be clean.");
  }

  _g1p->phase_times()->record_update_rs_time(worker_i, (os::elapsedTime() - start) * 1000.0);
}

void G1RemSet::cleanupHRRS() {
  HeapRegionRemSet::cleanup();
}

void G1RemSet::oops_into_collection_set_do(OopsInHeapRegionClosure* oc,
                                           CodeBlobToOopClosure* code_root_cl,
                                           int worker_i) {
#if CARD_REPEAT_HISTO
  ct_freq_update_histo_and_reset();
#endif

  // We cache the value of 'oc' closure into the appropriate slot in the
  // _cset_rs_update_cl for this worker
  assert(worker_i < (int)n_workers(), "sanity");
  _cset_rs_update_cl[worker_i] = oc;

  // A DirtyCardQueue that is used to hold cards containing references
  // that point into the collection set. This DCQ is associated with a
  // special DirtyCardQueueSet (see g1CollectedHeap.hpp).  Under normal
  // circumstances (i.e. the pause successfully completes), these cards
  // are just discarded (there's no need to update the RSets of regions
  // that were in the collection set - after the pause these regions
  // are wholly 'free' of live objects. In the event of an evacuation
  // failure the cards/buffers in this queue set are:
  // * passed to the DirtyCardQueueSet that is used to manage deferred
  //   RSet updates, or
  // * scanned for references that point into the collection set
  //   and the RSet of the corresponding region in the collection set
  //   is updated immediately.
  DirtyCardQueue into_cset_dcq(&_g1->into_cset_dirty_card_queue_set());

  assert((ParallelGCThreads > 0) || worker_i == 0, "invariant");

  // The two flags below were introduced temporarily to serialize
  // the updating and scanning of remembered sets. There are some
  // race conditions when these two operations are done in parallel
  // and they are causing failures. When we resolve said race
  // conditions, we'll revert back to parallel remembered set
  // updating and scanning. See CRs 6677707 and 6677708.
  if (G1UseParallelRSetUpdating || (worker_i == 0)) {
    updateRS(&into_cset_dcq, worker_i);
  } else {
    _g1p->phase_times()->record_update_rs_processed_buffers(worker_i, 0);
    _g1p->phase_times()->record_update_rs_time(worker_i, 0.0);
  }
  if (G1UseParallelRSetScanning || (worker_i == 0)) {
    scanRS(oc, code_root_cl, worker_i);
  } else {
    _g1p->phase_times()->record_scan_rs_time(worker_i, 0.0);
  }

  // We now clear the cached values of _cset_rs_update_cl for this worker
  _cset_rs_update_cl[worker_i] = NULL;
}

void G1RemSet::prepare_for_oops_into_collection_set_do() {
  cleanupHRRS();
  ConcurrentG1Refine* cg1r = _g1->concurrent_g1_refine();
  _g1->set_refine_cte_cl_concurrency(false);
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  dcqs.concatenate_logs();

  if (G1CollectedHeap::use_parallel_gc_threads()) {
    // Don't set the number of workers here.  It will be set
    // when the task is run
    // _seq_task->set_n_termination((int)n_workers());
  }
  guarantee( _cards_scanned == NULL, "invariant" );
  _cards_scanned = NEW_C_HEAP_ARRAY(size_t, n_workers(), mtGC);
  for (uint i = 0; i < n_workers(); ++i) {
    _cards_scanned[i] = 0;
  }
  _total_cards_scanned = 0;
}


// This closure, applied to a DirtyCardQueueSet, is used to immediately
// update the RSets for the regions in the CSet. For each card it iterates
// through the oops which coincide with that card. It scans the reference
// fields in each oop; when it finds an oop that points into the collection
// set, the RSet for the region containing the referenced object is updated.
class UpdateRSetCardTableEntryIntoCSetClosure: public CardTableEntryClosure {
  G1CollectedHeap* _g1;
  CardTableModRefBS* _ct_bs;
public:
  UpdateRSetCardTableEntryIntoCSetClosure(G1CollectedHeap* g1,
                                          CardTableModRefBS* bs):
    _g1(g1), _ct_bs(bs)
  { }

  bool do_card_ptr(jbyte* card_ptr, int worker_i) {
    // Construct the region representing the card.
    HeapWord* start = _ct_bs->addr_for(card_ptr);
    // And find the region containing it.
    HeapRegion* r = _g1->heap_region_containing(start);
    assert(r != NULL, "unexpected null");

    // Scan oops in the card looking for references into the collection set
    // Don't use addr_for(card_ptr + 1) which can ask for
    // a card beyond the heap.  This is not safe without a perm
    // gen.
    HeapWord* end   = start + CardTableModRefBS::card_size_in_words;
    MemRegion scanRegion(start, end);

    UpdateRSetImmediate update_rs_cl(_g1->g1_rem_set());
    FilterIntoCSClosure update_rs_cset_oop_cl(NULL, _g1, &update_rs_cl);
    FilterOutOfRegionClosure filter_then_update_rs_cset_oop_cl(r, &update_rs_cset_oop_cl);

    // We can pass false as the "filter_young" parameter here as:
    // * we should be in a STW pause,
    // * the DCQS to which this closure is applied is used to hold
    //   references that point into the collection set from the prior
    //   RSet updating,
    // * the post-write barrier shouldn't be logging updates to young
    //   regions (but there is a situation where this can happen - see
    //   the comment in G1RemSet::refine_card() below -
    //   that should not be applicable here), and
    // * during actual RSet updating, the filtering of cards in young
    //   regions in HeapRegion::oops_on_card_seq_iterate_careful is
    //   employed.
    // As a result, when this closure is applied to "refs into cset"
    // DCQS, we shouldn't see any cards in young regions.
    update_rs_cl.set_region(r);
    HeapWord* stop_point =
      r->oops_on_card_seq_iterate_careful(scanRegion,
                                          &filter_then_update_rs_cset_oop_cl,
                                          false /* filter_young */,
                                          NULL  /* card_ptr */);

    // Since this is performed in the event of an evacuation failure, we
    // we shouldn't see a non-null stop point
    assert(stop_point == NULL, "saw an unallocated region");
    return true;
  }
};

void G1RemSet::cleanup_after_oops_into_collection_set_do() {
  guarantee( _cards_scanned != NULL, "invariant" );
  _total_cards_scanned = 0;
  for (uint i = 0; i < n_workers(); ++i) {
    _total_cards_scanned += _cards_scanned[i];
  }
  FREE_C_HEAP_ARRAY(size_t, _cards_scanned, mtGC);
  _cards_scanned = NULL;
  // Cleanup after copy
  _g1->set_refine_cte_cl_concurrency(true);
  // Set all cards back to clean.
  _g1->cleanUpCardTable();

  DirtyCardQueueSet& into_cset_dcqs = _g1->into_cset_dirty_card_queue_set();
  int into_cset_n_buffers = into_cset_dcqs.completed_buffers_num();

  if (_g1->evacuation_failed()) {
    // Restore remembered sets for the regions pointing into the collection set.

    if (G1DeferredRSUpdate) {
      // If deferred RS updates are enabled then we just need to transfer
      // the completed buffers from (a) the DirtyCardQueueSet used to hold
      // cards that contain references that point into the collection set
      // to (b) the DCQS used to hold the deferred RS updates
      _g1->dirty_card_queue_set().merge_bufferlists(&into_cset_dcqs);
    } else {

      CardTableModRefBS* bs = (CardTableModRefBS*)_g1->barrier_set();
      UpdateRSetCardTableEntryIntoCSetClosure update_rs_cset_immediate(_g1, bs);

      int n_completed_buffers = 0;
      while (into_cset_dcqs.apply_closure_to_completed_buffer(&update_rs_cset_immediate,
                                                    0, 0, true)) {
        n_completed_buffers++;
      }
      assert(n_completed_buffers == into_cset_n_buffers, "missed some buffers");
    }
  }

  // Free any completed buffers in the DirtyCardQueueSet used to hold cards
  // which contain references that point into the collection.
  _g1->into_cset_dirty_card_queue_set().clear();
  assert(_g1->into_cset_dirty_card_queue_set().completed_buffers_num() == 0,
         "all buffers should be freed");
  _g1->into_cset_dirty_card_queue_set().clear_n_completed_buffers();
}

class ScrubRSClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  BitMap* _region_bm;
  BitMap* _card_bm;
  CardTableModRefBS* _ctbs;
public:
  ScrubRSClosure(BitMap* region_bm, BitMap* card_bm) :
    _g1h(G1CollectedHeap::heap()),
    _region_bm(region_bm), _card_bm(card_bm),
    _ctbs(_g1h->g1_barrier_set()) {}

  bool doHeapRegion(HeapRegion* r) {
    if (!r->continuesHumongous()) {
      r->rem_set()->scrub(_ctbs, _region_bm, _card_bm);
    }
    return false;
  }
};

void G1RemSet::scrub(BitMap* region_bm, BitMap* card_bm) {
  ScrubRSClosure scrub_cl(region_bm, card_bm);
  _g1->heap_region_iterate(&scrub_cl);
}

void G1RemSet::scrub_par(BitMap* region_bm, BitMap* card_bm,
                                uint worker_num, int claim_val) {
  ScrubRSClosure scrub_cl(region_bm, card_bm);
  _g1->heap_region_par_iterate_chunked(&scrub_cl,
                                       worker_num,
                                       n_workers(),
                                       claim_val);
}

G1TriggerClosure::G1TriggerClosure() :
  _triggered(false) { }

G1InvokeIfNotTriggeredClosure::G1InvokeIfNotTriggeredClosure(G1TriggerClosure* t_cl,
                                                             OopClosure* oop_cl)  :
  _trigger_cl(t_cl), _oop_cl(oop_cl) { }

G1Mux2Closure::G1Mux2Closure(OopClosure *c1, OopClosure *c2) :
  _c1(c1), _c2(c2) { }

G1UpdateRSOrPushRefOopClosure::
G1UpdateRSOrPushRefOopClosure(G1CollectedHeap* g1h,
                              G1RemSet* rs,
                              OopsInHeapRegionClosure* push_ref_cl,
                              bool record_refs_into_cset,
                              int worker_i) :
  _g1(g1h), _g1_rem_set(rs), _from(NULL),
  _record_refs_into_cset(record_refs_into_cset),
  _push_ref_cl(push_ref_cl), _worker_i(worker_i) { }

// Returns true if the given card contains references that point
// into the collection set, if we're checking for such references;
// false otherwise.

bool G1RemSet::refine_card(jbyte* card_ptr, int worker_i,
                           bool check_for_refs_into_cset) {

  // If the card is no longer dirty, nothing to do.
  if (*card_ptr != CardTableModRefBS::dirty_card_val()) {
    // No need to return that this card contains refs that point
    // into the collection set.
    return false;
  }

  // Construct the region representing the card.
  HeapWord* start = _ct_bs->addr_for(card_ptr);
  // And find the region containing it.
  HeapRegion* r = _g1->heap_region_containing(start);
  if (r == NULL) {
    // Again no need to return that this card contains refs that
    // point into the collection set.
    return false;  // Not in the G1 heap (might be in perm, for example.)
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
    return false;
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
    return false;
  }

  // The result from the hot card cache insert call is either:
  //   * pointer to the current card
  //     (implying that the current card is not 'hot'),
  //   * null
  //     (meaning we had inserted the card ptr into the "hot" card cache,
  //     which had some headroom),
  //   * a pointer to a "hot" card that was evicted from the "hot" cache.
  //

  G1HotCardCache* hot_card_cache = _cg1r->hot_card_cache();
  if (hot_card_cache->use_cache()) {
    assert(!check_for_refs_into_cset, "sanity");
    assert(!SafepointSynchronize::is_at_safepoint(), "sanity");

    card_ptr = hot_card_cache->insert(card_ptr);
    if (card_ptr == NULL) {
      // There was no eviction. Nothing to do.
      return false;
    }

    start = _ct_bs->addr_for(card_ptr);
    r = _g1->heap_region_containing(start);
    if (r == NULL) {
      // Not in the G1 heap
      return false;
    }

    // Checking whether the region we got back from the cache
    // is young here is inappropriate. The region could have been
    // freed, reallocated and tagged as young while in the cache.
    // Hence we could see its young type change at any time.
  }

  // Don't use addr_for(card_ptr + 1) which can ask for
  // a card beyond the heap.  This is not safe without a perm
  // gen at the upper end of the heap.
  HeapWord* end   = start + CardTableModRefBS::card_size_in_words;
  MemRegion dirtyRegion(start, end);

#if CARD_REPEAT_HISTO
  init_ct_freq_table(_g1->max_capacity());
  ct_freq_note_card(_ct_bs->index_for(start));
#endif

  OopsInHeapRegionClosure* oops_in_heap_closure = NULL;
  if (check_for_refs_into_cset) {
    // ConcurrentG1RefineThreads have worker numbers larger than what
    // _cset_rs_update_cl[] is set up to handle. But those threads should
    // only be active outside of a collection which means that when they
    // reach here they should have check_for_refs_into_cset == false.
    assert((size_t)worker_i < n_workers(), "index of worker larger than _cset_rs_update_cl[].length");
    oops_in_heap_closure = _cset_rs_update_cl[worker_i];
  }
  G1UpdateRSOrPushRefOopClosure update_rs_oop_cl(_g1,
                                                 _g1->g1_rem_set(),
                                                 oops_in_heap_closure,
                                                 check_for_refs_into_cset,
                                                 worker_i);
  update_rs_oop_cl.set_from(r);

  G1TriggerClosure trigger_cl;
  FilterIntoCSClosure into_cs_cl(NULL, _g1, &trigger_cl);
  G1InvokeIfNotTriggeredClosure invoke_cl(&trigger_cl, &into_cs_cl);
  G1Mux2Closure mux(&invoke_cl, &update_rs_oop_cl);

  FilterOutOfRegionClosure filter_then_update_rs_oop_cl(r,
                        (check_for_refs_into_cset ?
                                (OopClosure*)&mux :
                                (OopClosure*)&update_rs_oop_cl));

  // The region for the current card may be a young region. The
  // current card may have been a card that was evicted from the
  // card cache. When the card was inserted into the cache, we had
  // determined that its region was non-young. While in the cache,
  // the region may have been freed during a cleanup pause, reallocated
  // and tagged as young.
  //
  // We wish to filter out cards for such a region but the current
  // thread, if we're running concurrently, may "see" the young type
  // change at any time (so an earlier "is_young" check may pass or
  // fail arbitrarily). We tell the iteration code to perform this
  // filtering when it has been determined that there has been an actual
  // allocation in this region and making it safe to check the young type.
  bool filter_young = true;

  HeapWord* stop_point =
    r->oops_on_card_seq_iterate_careful(dirtyRegion,
                                        &filter_then_update_rs_oop_cl,
                                        filter_young,
                                        card_ptr);

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
    _conc_refine_cards++;
  }

  // This gets set to true if the card being refined has
  // references that point into the collection set.
  bool has_refs_into_cset = trigger_cl.triggered();

  // We should only be detecting that the card contains references
  // that point into the collection set if the current thread is
  // a GC worker thread.
  assert(!has_refs_into_cset || SafepointSynchronize::is_at_safepoint(),
           "invalid result at non safepoint");

  return has_refs_into_cset;
}

void G1RemSet::print_periodic_summary_info(const char* header) {
  G1RemSetSummary current;
  current.initialize(this);

  _prev_period_summary.subtract_from(&current);
  print_summary_info(&_prev_period_summary, header);

  _prev_period_summary.set(&current);
}

void G1RemSet::print_summary_info() {
  G1RemSetSummary current;
  current.initialize(this);

  print_summary_info(&current, " Cumulative RS summary");
}

void G1RemSet::print_summary_info(G1RemSetSummary * summary, const char * header) {
  assert(summary != NULL, "just checking");

  if (header != NULL) {
    gclog_or_tty->print_cr("%s", header);
  }

#if CARD_REPEAT_HISTO
  gclog_or_tty->print_cr("\nG1 card_repeat count histogram: ");
  gclog_or_tty->print_cr("  # of repeats --> # of cards with that number.");
  card_repeat_count.print_on(gclog_or_tty);
#endif

  summary->print_on(gclog_or_tty);
}

void G1RemSet::prepare_for_verify() {
  if (G1HRRSFlushLogBuffersOnVerify &&
      (VerifyBeforeGC || VerifyAfterGC)
      &&  (!_g1->full_collection() || G1VerifyRSetsDuringFullGC)) {
    cleanupHRRS();
    _g1->set_refine_cte_cl_concurrency(false);
    if (SafepointSynchronize::is_at_safepoint()) {
      DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
      dcqs.concatenate_logs();
    }

    G1HotCardCache* hot_card_cache = _cg1r->hot_card_cache();
    bool use_hot_card_cache = hot_card_cache->use_cache();
    hot_card_cache->set_use_cache(false);

    DirtyCardQueue into_cset_dcq(&_g1->into_cset_dirty_card_queue_set());
    updateRS(&into_cset_dcq, 0);
    _g1->into_cset_dirty_card_queue_set().clear();

    hot_card_cache->set_use_cache(use_hot_card_cache);
    assert(JavaThread::dirty_card_queue_set().completed_buffers_num() == 0, "All should be consumed");
  }
}
