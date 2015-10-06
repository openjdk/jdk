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

#include "precompiled.hpp"
#include "gc/g1/concurrentG1Refine.hpp"
#include "gc/g1/concurrentG1RefineThread.hpp"
#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1CodeBlobClosure.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1HotCardCache.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1RemSet.inline.hpp"
#include "gc/g1/heapRegionManager.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "memory/iterator.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/intHisto.hpp"
#include "utilities/stack.inline.hpp"

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
    _prev_period_summary()
{
  _cset_rs_update_cl = NEW_C_HEAP_ARRAY(G1ParPushHeapRSClosure*, n_workers(), mtGC);
  for (uint i = 0; i < n_workers(); i++) {
    _cset_rs_update_cl[i] = NULL;
  }
  if (G1SummarizeRSetStats) {
    _prev_period_summary.initialize(this);
  }
}

G1RemSet::~G1RemSet() {
  for (uint i = 0; i < n_workers(); i++) {
    assert(_cset_rs_update_cl[i] == NULL, "it should be");
  }
  FREE_C_HEAP_ARRAY(G1ParPushHeapRSClosure*, _cset_rs_update_cl);
}

class ScanRSClosure : public HeapRegionClosure {
  size_t _cards_done, _cards;
  G1CollectedHeap* _g1h;

  G1ParPushHeapRSClosure* _oc;
  CodeBlobClosure* _code_root_cl;

  G1BlockOffsetSharedArray* _bot_shared;
  G1SATBCardTableModRefBS *_ct_bs;

  double _strong_code_root_scan_time_sec;
  uint   _worker_i;
  size_t _block_size;
  bool   _try_claimed;

public:
  ScanRSClosure(G1ParPushHeapRSClosure* oc,
                CodeBlobClosure* code_root_cl,
                uint worker_i) :
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
    _block_size = MAX2<size_t>(G1RSetScanBlockSize, 1);
  }

  void set_try_claimed() { _try_claimed = true; }

  void scanCard(size_t index, HeapRegion *r) {
    // Stack allocate the DirtyCardToOopClosure instance
    HeapRegionDCTOC cl(_g1h, r, _oc,
                       CardTableModRefBS::Precise);

    // Set the "from" region in the closure.
    _oc->set_region(r);
    MemRegion card_region(_bot_shared->address_for_index(index), G1BlockOffsetSharedArray::N_words);
    MemRegion pre_gc_allocated(r->bottom(), r->scan_top());
    MemRegion mr = pre_gc_allocated.intersection(card_region);
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
    gclog_or_tty->print_cr("T %u Region [" PTR_FORMAT ", " PTR_FORMAT ") "
                           "RS names card " SIZE_FORMAT_HEX ": "
                           "[" PTR_FORMAT ", " PTR_FORMAT ")",
                           _worker_i,
                           p2i(card_region->bottom()), p2i(card_region->end()),
                           card_index,
                           p2i(card_start), p2i(card_start + G1BlockOffsetSharedArray::N_words));
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

    // We claim cards in block so as to reduce the contention. The block size is determined by
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

size_t G1RemSet::scanRS(G1ParPushHeapRSClosure* oc,
                        OopClosure* non_heap_roots,
                        uint worker_i) {
  double rs_time_start = os::elapsedTime();

  G1CodeBlobClosure code_root_cl(non_heap_roots);

  HeapRegion *startRegion = _g1->start_cset_region_for_worker(worker_i);

  ScanRSClosure scanRScl(oc, &code_root_cl, worker_i);

  _g1->collection_set_iterate_from(startRegion, &scanRScl);
  scanRScl.set_try_claimed();
  _g1->collection_set_iterate_from(startRegion, &scanRScl);

  double scan_rs_time_sec = (os::elapsedTime() - rs_time_start)
                            - scanRScl.strong_code_root_scan_time_sec();

  _g1p->phase_times()->record_time_secs(G1GCPhaseTimes::ScanRS, worker_i, scan_rs_time_sec);
  _g1p->phase_times()->record_time_secs(G1GCPhaseTimes::CodeRoots, worker_i, scanRScl.strong_code_root_scan_time_sec());

  return scanRScl.cards_done();
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
  bool do_card_ptr(jbyte* card_ptr, uint worker_i) {
    // The only time we care about recording cards that
    // contain references that point into the collection set
    // is during RSet updating within an evacuation pause.
    // In this case worker_i should be the id of a GC worker thread.
    assert(SafepointSynchronize::is_at_safepoint(), "not during an evacuation pause");
    assert(worker_i < ParallelGCThreads, "should be a GC worker");

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

void G1RemSet::updateRS(DirtyCardQueue* into_cset_dcq, uint worker_i) {
  G1GCParPhaseTimesTracker x(_g1p->phase_times(), G1GCPhaseTimes::UpdateRS, worker_i);
  // Apply the given closure to all remaining log entries.
  RefineRecordRefsIntoCSCardTableEntryClosure into_cset_update_rs_cl(_g1, into_cset_dcq);

  _g1->iterate_dirty_card_closure(&into_cset_update_rs_cl, into_cset_dcq, false, worker_i);
}

void G1RemSet::cleanupHRRS() {
  HeapRegionRemSet::cleanup();
}

size_t G1RemSet::oops_into_collection_set_do(G1ParPushHeapRSClosure* oc,
                                             OopClosure* non_heap_roots,
                                             uint worker_i) {
#if CARD_REPEAT_HISTO
  ct_freq_update_histo_and_reset();
#endif

  // We cache the value of 'oc' closure into the appropriate slot in the
  // _cset_rs_update_cl for this worker
  assert(worker_i < n_workers(), "sanity");
  _cset_rs_update_cl[worker_i] = oc;

  // A DirtyCardQueue that is used to hold cards containing references
  // that point into the collection set. This DCQ is associated with a
  // special DirtyCardQueueSet (see g1CollectedHeap.hpp).  Under normal
  // circumstances (i.e. the pause successfully completes), these cards
  // are just discarded (there's no need to update the RSets of regions
  // that were in the collection set - after the pause these regions
  // are wholly 'free' of live objects. In the event of an evacuation
  // failure the cards/buffers in this queue set are passed to the
  // DirtyCardQueueSet that is used to manage RSet updates
  DirtyCardQueue into_cset_dcq(&_g1->into_cset_dirty_card_queue_set());

  updateRS(&into_cset_dcq, worker_i);
  size_t cards_scanned = scanRS(oc, non_heap_roots, worker_i);

  // We now clear the cached values of _cset_rs_update_cl for this worker
  _cset_rs_update_cl[worker_i] = NULL;
  return cards_scanned;
}

void G1RemSet::prepare_for_oops_into_collection_set_do() {
  cleanupHRRS();
  _g1->set_refine_cte_cl_concurrency(false);
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  dcqs.concatenate_logs();
}

void G1RemSet::cleanup_after_oops_into_collection_set_do() {
  // Cleanup after copy
  _g1->set_refine_cte_cl_concurrency(true);
  // Set all cards back to clean.
  _g1->cleanUpCardTable();

  DirtyCardQueueSet& into_cset_dcqs = _g1->into_cset_dirty_card_queue_set();
  int into_cset_n_buffers = into_cset_dcqs.completed_buffers_num();

  if (_g1->evacuation_failed()) {
    double restore_remembered_set_start = os::elapsedTime();

    // Restore remembered sets for the regions pointing into the collection set.
    // We just need to transfer the completed buffers from the DirtyCardQueueSet
    // used to hold cards that contain references that point into the collection set
    // to the DCQS used to hold the deferred RS updates.
    _g1->dirty_card_queue_set().merge_bufferlists(&into_cset_dcqs);
    _g1->g1_policy()->phase_times()->record_evac_fail_restore_remsets((os::elapsedTime() - restore_remembered_set_start) * 1000.0);
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
    if (!r->is_continues_humongous()) {
      r->rem_set()->scrub(_ctbs, _region_bm, _card_bm);
    }
    return false;
  }
};

void G1RemSet::scrub(BitMap* region_bm, BitMap* card_bm, uint worker_num, HeapRegionClaimer *hrclaimer) {
  ScrubRSClosure scrub_cl(region_bm, card_bm);
  _g1->heap_region_par_iterate(&scrub_cl, worker_num, hrclaimer);
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
                              G1ParPushHeapRSClosure* push_ref_cl,
                              bool record_refs_into_cset,
                              uint worker_i) :
  _g1(g1h), _g1_rem_set(rs), _from(NULL),
  _record_refs_into_cset(record_refs_into_cset),
  _push_ref_cl(push_ref_cl), _worker_i(worker_i) { }

// Returns true if the given card contains references that point
// into the collection set, if we're checking for such references;
// false otherwise.

bool G1RemSet::refine_card(jbyte* card_ptr, uint worker_i,
                           bool check_for_refs_into_cset) {
  assert(_g1->is_in_exact(_ct_bs->addr_for(card_ptr)),
         "Card at " PTR_FORMAT " index " SIZE_FORMAT " representing heap at " PTR_FORMAT " (%u) must be in committed heap",
         p2i(card_ptr),
         _ct_bs->index_for(_ct_bs->addr_for(card_ptr)),
         p2i(_ct_bs->addr_for(card_ptr)),
         _g1->addr_to_region(_ct_bs->addr_for(card_ptr)));

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
  // since we don't want to update remembered sets with entries that
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

  G1ParPushHeapRSClosure* oops_in_heap_closure = NULL;
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
      &&  (!_g1->collector_state()->full_collection() || G1VerifyRSetsDuringFullGC)) {
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
