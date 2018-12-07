/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/dirtyCardQueue.hpp"
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1CardTable.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1FromCardCache.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1HotCardCache.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1RootClosures.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionManager.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/intHisto.hpp"
#include "utilities/stack.inline.hpp"
#include "utilities/ticks.hpp"

// Collects information about the overall remembered set scan progress during an evacuation.
class G1RemSetScanState : public CHeapObj<mtGC> {
private:
  class G1ClearCardTableTask : public AbstractGangTask {
    G1CollectedHeap* _g1h;
    uint* _dirty_region_list;
    size_t _num_dirty_regions;
    size_t _chunk_length;

    size_t volatile _cur_dirty_regions;
  public:
    G1ClearCardTableTask(G1CollectedHeap* g1h,
                         uint* dirty_region_list,
                         size_t num_dirty_regions,
                         size_t chunk_length) :
      AbstractGangTask("G1 Clear Card Table Task"),
      _g1h(g1h),
      _dirty_region_list(dirty_region_list),
      _num_dirty_regions(num_dirty_regions),
      _chunk_length(chunk_length),
      _cur_dirty_regions(0) {

      assert(chunk_length > 0, "must be");
    }

    static size_t chunk_size() { return M; }

    void work(uint worker_id) {
      while (_cur_dirty_regions < _num_dirty_regions) {
        size_t next = Atomic::add(_chunk_length, &_cur_dirty_regions) - _chunk_length;
        size_t max = MIN2(next + _chunk_length, _num_dirty_regions);

        for (size_t i = next; i < max; i++) {
          HeapRegion* r = _g1h->region_at(_dirty_region_list[i]);
          if (!r->is_survivor()) {
            r->clear_cardtable();
          }
        }
      }
    }
  };

  size_t _max_regions;

  // Scan progress for the remembered set of a single region. Transitions from
  // Unclaimed -> Claimed -> Complete.
  // At each of the transitions the thread that does the transition needs to perform
  // some special action once. This is the reason for the extra "Claimed" state.
  typedef jint G1RemsetIterState;

  static const G1RemsetIterState Unclaimed = 0; // The remembered set has not been scanned yet.
  static const G1RemsetIterState Claimed = 1;   // The remembered set is currently being scanned.
  static const G1RemsetIterState Complete = 2;  // The remembered set has been completely scanned.

  G1RemsetIterState volatile* _iter_states;
  // The current location where the next thread should continue scanning in a region's
  // remembered set.
  size_t volatile* _iter_claims;

  // Temporary buffer holding the regions we used to store remembered set scan duplicate
  // information. These are also called "dirty". Valid entries are from [0.._cur_dirty_region)
  uint* _dirty_region_buffer;

  typedef jbyte IsDirtyRegionState;
  static const IsDirtyRegionState Clean = 0;
  static const IsDirtyRegionState Dirty = 1;
  // Holds a flag for every region whether it is in the _dirty_region_buffer already
  // to avoid duplicates. Uses jbyte since there are no atomic instructions for bools.
  IsDirtyRegionState* _in_dirty_region_buffer;
  size_t _cur_dirty_region;

  // Creates a snapshot of the current _top values at the start of collection to
  // filter out card marks that we do not want to scan.
  class G1ResetScanTopClosure : public HeapRegionClosure {
  private:
    HeapWord** _scan_top;
  public:
    G1ResetScanTopClosure(HeapWord** scan_top) : _scan_top(scan_top) { }

    virtual bool do_heap_region(HeapRegion* r) {
      uint hrm_index = r->hrm_index();
      if (!r->in_collection_set() && r->is_old_or_humongous_or_archive() && !r->is_empty()) {
        _scan_top[hrm_index] = r->top();
      } else {
        _scan_top[hrm_index] = NULL;
      }
      return false;
    }
  };

  // For each region, contains the maximum top() value to be used during this garbage
  // collection. Subsumes common checks like filtering out everything but old and
  // humongous regions outside the collection set.
  // This is valid because we are not interested in scanning stray remembered set
  // entries from free or archive regions.
  HeapWord** _scan_top;
public:
  G1RemSetScanState() :
    _max_regions(0),
    _iter_states(NULL),
    _iter_claims(NULL),
    _dirty_region_buffer(NULL),
    _in_dirty_region_buffer(NULL),
    _cur_dirty_region(0),
    _scan_top(NULL) {
  }

  ~G1RemSetScanState() {
    if (_iter_states != NULL) {
      FREE_C_HEAP_ARRAY(G1RemsetIterState, _iter_states);
    }
    if (_iter_claims != NULL) {
      FREE_C_HEAP_ARRAY(size_t, _iter_claims);
    }
    if (_dirty_region_buffer != NULL) {
      FREE_C_HEAP_ARRAY(uint, _dirty_region_buffer);
    }
    if (_in_dirty_region_buffer != NULL) {
      FREE_C_HEAP_ARRAY(IsDirtyRegionState, _in_dirty_region_buffer);
    }
    if (_scan_top != NULL) {
      FREE_C_HEAP_ARRAY(HeapWord*, _scan_top);
    }
  }

  void initialize(uint max_regions) {
    assert(_iter_states == NULL, "Must not be initialized twice");
    assert(_iter_claims == NULL, "Must not be initialized twice");
    _max_regions = max_regions;
    _iter_states = NEW_C_HEAP_ARRAY(G1RemsetIterState, max_regions, mtGC);
    _iter_claims = NEW_C_HEAP_ARRAY(size_t, max_regions, mtGC);
    _dirty_region_buffer = NEW_C_HEAP_ARRAY(uint, max_regions, mtGC);
    _in_dirty_region_buffer = NEW_C_HEAP_ARRAY(IsDirtyRegionState, max_regions, mtGC);
    _scan_top = NEW_C_HEAP_ARRAY(HeapWord*, max_regions, mtGC);
  }

  void reset() {
    for (uint i = 0; i < _max_regions; i++) {
      _iter_states[i] = Unclaimed;
      _scan_top[i] = NULL;
    }

    G1ResetScanTopClosure cl(_scan_top);
    G1CollectedHeap::heap()->heap_region_iterate(&cl);

    memset((void*)_iter_claims, 0, _max_regions * sizeof(size_t));
    memset(_in_dirty_region_buffer, Clean, _max_regions * sizeof(IsDirtyRegionState));
    _cur_dirty_region = 0;
  }

  // Attempt to claim the remembered set of the region for iteration. Returns true
  // if this call caused the transition from Unclaimed to Claimed.
  inline bool claim_iter(uint region) {
    assert(region < _max_regions, "Tried to access invalid region %u", region);
    if (_iter_states[region] != Unclaimed) {
      return false;
    }
    G1RemsetIterState res = Atomic::cmpxchg(Claimed, &_iter_states[region], Unclaimed);
    return (res == Unclaimed);
  }

  // Try to atomically sets the iteration state to "complete". Returns true for the
  // thread that caused the transition.
  inline bool set_iter_complete(uint region) {
    if (iter_is_complete(region)) {
      return false;
    }
    G1RemsetIterState res = Atomic::cmpxchg(Complete, &_iter_states[region], Claimed);
    return (res == Claimed);
  }

  // Returns true if the region's iteration is complete.
  inline bool iter_is_complete(uint region) const {
    assert(region < _max_regions, "Tried to access invalid region %u", region);
    return _iter_states[region] == Complete;
  }

  // The current position within the remembered set of the given region.
  inline size_t iter_claimed(uint region) const {
    assert(region < _max_regions, "Tried to access invalid region %u", region);
    return _iter_claims[region];
  }

  // Claim the next block of cards within the remembered set of the region with
  // step size.
  inline size_t iter_claimed_next(uint region, size_t step) {
    return Atomic::add(step, &_iter_claims[region]) - step;
  }

  void add_dirty_region(uint region) {
    if (_in_dirty_region_buffer[region] == Dirty) {
      return;
    }

    bool marked_as_dirty = Atomic::cmpxchg(Dirty, &_in_dirty_region_buffer[region], Clean) == Clean;
    if (marked_as_dirty) {
      size_t allocated = Atomic::add(1u, &_cur_dirty_region) - 1;
      _dirty_region_buffer[allocated] = region;
    }
  }

  HeapWord* scan_top(uint region_idx) const {
    return _scan_top[region_idx];
  }

  // Clear the card table of "dirty" regions.
  void clear_card_table(WorkGang* workers) {
    if (_cur_dirty_region == 0) {
      return;
    }

    size_t const num_chunks = align_up(_cur_dirty_region * HeapRegion::CardsPerRegion, G1ClearCardTableTask::chunk_size()) / G1ClearCardTableTask::chunk_size();
    uint const num_workers = (uint)MIN2(num_chunks, (size_t)workers->active_workers());
    size_t const chunk_length = G1ClearCardTableTask::chunk_size() / HeapRegion::CardsPerRegion;

    // Iterate over the dirty cards region list.
    G1ClearCardTableTask cl(G1CollectedHeap::heap(), _dirty_region_buffer, _cur_dirty_region, chunk_length);

    log_debug(gc, ergo)("Running %s using %u workers for " SIZE_FORMAT " "
                        "units of work for " SIZE_FORMAT " regions.",
                        cl.name(), num_workers, num_chunks, _cur_dirty_region);
    workers->run_task(&cl, num_workers);

#ifndef PRODUCT
    G1CollectedHeap::heap()->verifier()->verify_card_table_cleanup();
#endif
  }
};

G1RemSet::G1RemSet(G1CollectedHeap* g1h,
                   G1CardTable* ct,
                   G1HotCardCache* hot_card_cache) :
  _scan_state(new G1RemSetScanState()),
  _prev_period_summary(),
  _g1h(g1h),
  _num_conc_refined_cards(0),
  _ct(ct),
  _g1p(_g1h->g1_policy()),
  _hot_card_cache(hot_card_cache) {
}

G1RemSet::~G1RemSet() {
  if (_scan_state != NULL) {
    delete _scan_state;
  }
}

uint G1RemSet::num_par_rem_sets() {
  return DirtyCardQueueSet::num_par_ids() + G1ConcurrentRefine::max_num_threads() + MAX2(ConcGCThreads, ParallelGCThreads);
}

void G1RemSet::initialize(size_t capacity, uint max_regions) {
  G1FromCardCache::initialize(num_par_rem_sets(), max_regions);
  _scan_state->initialize(max_regions);
}

G1ScanRSForRegionClosure::G1ScanRSForRegionClosure(G1RemSetScanState* scan_state,
                                                   G1ScanObjsDuringScanRSClosure* scan_obj_on_card,
                                                   G1ParScanThreadState* pss,
                                                   G1GCPhaseTimes::GCParPhases phase,
                                                   uint worker_i) :
  _g1h(G1CollectedHeap::heap()),
  _ct(_g1h->card_table()),
  _pss(pss),
  _scan_objs_on_card_cl(scan_obj_on_card),
  _scan_state(scan_state),
  _phase(phase),
  _worker_i(worker_i),
  _cards_scanned(0),
  _cards_claimed(0),
  _cards_skipped(0),
  _rem_set_root_scan_time(),
  _rem_set_trim_partially_time(),
  _strong_code_root_scan_time(),
  _strong_code_trim_partially_time() {
}

void G1ScanRSForRegionClosure::claim_card(size_t card_index, const uint region_idx_for_card){
  _ct->set_card_claimed(card_index);
  _scan_state->add_dirty_region(region_idx_for_card);
}

void G1ScanRSForRegionClosure::scan_card(MemRegion mr, uint region_idx_for_card) {
  HeapRegion* const card_region = _g1h->region_at(region_idx_for_card);
  assert(!card_region->is_young(), "Should not scan card in young region %u", region_idx_for_card);
  card_region->oops_on_card_seq_iterate_careful<true>(mr, _scan_objs_on_card_cl);
  _scan_objs_on_card_cl->trim_queue_partially();
  _cards_scanned++;
}

void G1ScanRSForRegionClosure::scan_rem_set_roots(HeapRegion* r) {
  EventGCPhaseParallel event;
  uint const region_idx = r->hrm_index();

  if (_scan_state->claim_iter(region_idx)) {
    // If we ever free the collection set concurrently, we should also
    // clear the card table concurrently therefore we won't need to
    // add regions of the collection set to the dirty cards region.
    _scan_state->add_dirty_region(region_idx);
  }

  if (r->rem_set()->cardset_is_empty()) {
    return;
  }

  // We claim cards in blocks so as to reduce the contention.
  size_t const block_size = G1RSetScanBlockSize;

  HeapRegionRemSetIterator iter(r->rem_set());
  size_t card_index;

  size_t claimed_card_block = _scan_state->iter_claimed_next(region_idx, block_size);
  for (size_t current_card = 0; iter.has_next(card_index); current_card++) {
    if (current_card >= claimed_card_block + block_size) {
      claimed_card_block = _scan_state->iter_claimed_next(region_idx, block_size);
    }
    if (current_card < claimed_card_block) {
      _cards_skipped++;
      continue;
    }
    _cards_claimed++;

    HeapWord* const card_start = _g1h->bot()->address_for_index_raw(card_index);
    uint const region_idx_for_card = _g1h->addr_to_region(card_start);

#ifdef ASSERT
    HeapRegion* hr = _g1h->region_at_or_null(region_idx_for_card);
    assert(hr == NULL || hr->is_in_reserved(card_start),
           "Card start " PTR_FORMAT " to scan outside of region %u", p2i(card_start), _g1h->region_at(region_idx_for_card)->hrm_index());
#endif
    HeapWord* const top = _scan_state->scan_top(region_idx_for_card);
    if (card_start >= top) {
      continue;
    }

    // If the card is dirty, then G1 will scan it during Update RS.
    if (_ct->is_card_claimed(card_index) || _ct->is_card_dirty(card_index)) {
      continue;
    }

    // We claim lazily (so races are possible but they're benign), which reduces the
    // number of duplicate scans (the rsets of the regions in the cset can intersect).
    // Claim the card after checking bounds above: the remembered set may contain
    // random cards into current survivor, and we would then have an incorrectly
    // claimed card in survivor space. Card table clear does not reset the card table
    // of survivor space regions.
    claim_card(card_index, region_idx_for_card);

    MemRegion const mr(card_start, MIN2(card_start + BOTConstants::N_words, top));

    scan_card(mr, region_idx_for_card);
  }
  event.commit(GCId::current(), _worker_i, G1GCPhaseTimes::phase_name(_phase));
}

void G1ScanRSForRegionClosure::scan_strong_code_roots(HeapRegion* r) {
  EventGCPhaseParallel event;
  r->strong_code_roots_do(_pss->closures()->weak_codeblobs());
  event.commit(GCId::current(), _worker_i, G1GCPhaseTimes::phase_name(G1GCPhaseTimes::CodeRoots));
}

bool G1ScanRSForRegionClosure::do_heap_region(HeapRegion* r) {
  assert(r->in_collection_set(),
         "Should only be called on elements of the collection set but region %u is not.",
         r->hrm_index());
  uint const region_idx = r->hrm_index();

  // Do an early out if we know we are complete.
  if (_scan_state->iter_is_complete(region_idx)) {
    return false;
  }

  {
    G1EvacPhaseWithTrimTimeTracker timer(_pss, _rem_set_root_scan_time, _rem_set_trim_partially_time);
    scan_rem_set_roots(r);
  }

  if (_scan_state->set_iter_complete(region_idx)) {
    G1EvacPhaseWithTrimTimeTracker timer(_pss, _strong_code_root_scan_time, _strong_code_trim_partially_time);
    // Scan the strong code root list attached to the current region
    scan_strong_code_roots(r);
  }
  return false;
}

void G1RemSet::scan_rem_set(G1ParScanThreadState* pss, uint worker_i) {
  G1ScanObjsDuringScanRSClosure scan_cl(_g1h, pss);
  G1ScanRSForRegionClosure cl(_scan_state, &scan_cl, pss, G1GCPhaseTimes::ScanRS, worker_i);
  _g1h->collection_set_iterate_from(&cl, worker_i);

  G1GCPhaseTimes* p = _g1p->phase_times();

  p->record_time_secs(G1GCPhaseTimes::ScanRS, worker_i, cl.rem_set_root_scan_time().seconds());
  p->add_time_secs(G1GCPhaseTimes::ObjCopy, worker_i, cl.rem_set_trim_partially_time().seconds());

  p->record_thread_work_item(G1GCPhaseTimes::ScanRS, worker_i, cl.cards_scanned(), G1GCPhaseTimes::ScanRSScannedCards);
  p->record_thread_work_item(G1GCPhaseTimes::ScanRS, worker_i, cl.cards_claimed(), G1GCPhaseTimes::ScanRSClaimedCards);
  p->record_thread_work_item(G1GCPhaseTimes::ScanRS, worker_i, cl.cards_skipped(), G1GCPhaseTimes::ScanRSSkippedCards);

  p->record_time_secs(G1GCPhaseTimes::CodeRoots, worker_i, cl.strong_code_root_scan_time().seconds());
  p->add_time_secs(G1GCPhaseTimes::ObjCopy, worker_i, cl.strong_code_root_trim_partially_time().seconds());
}

// Closure used for updating rem sets. Only called during an evacuation pause.
class G1RefineCardClosure: public CardTableEntryClosure {
  G1RemSet* _g1rs;
  G1ScanObjsDuringUpdateRSClosure* _update_rs_cl;

  size_t _cards_scanned;
  size_t _cards_skipped;
public:
  G1RefineCardClosure(G1CollectedHeap* g1h, G1ScanObjsDuringUpdateRSClosure* update_rs_cl) :
    _g1rs(g1h->g1_rem_set()), _update_rs_cl(update_rs_cl), _cards_scanned(0), _cards_skipped(0)
  {}

  bool do_card_ptr(jbyte* card_ptr, uint worker_i) {
    // The only time we care about recording cards that
    // contain references that point into the collection set
    // is during RSet updating within an evacuation pause.
    // In this case worker_i should be the id of a GC worker thread.
    assert(SafepointSynchronize::is_at_safepoint(), "not during an evacuation pause");

    bool card_scanned = _g1rs->refine_card_during_gc(card_ptr, _update_rs_cl);

    if (card_scanned) {
      _update_rs_cl->trim_queue_partially();
      _cards_scanned++;
    } else {
      _cards_skipped++;
    }
    return true;
  }

  size_t cards_scanned() const { return _cards_scanned; }
  size_t cards_skipped() const { return _cards_skipped; }
};

void G1RemSet::update_rem_set(G1ParScanThreadState* pss, uint worker_i) {
  G1GCPhaseTimes* p = _g1p->phase_times();

  // Apply closure to log entries in the HCC.
  if (G1HotCardCache::default_use_cache()) {
    G1EvacPhaseTimesTracker x(p, pss, G1GCPhaseTimes::ScanHCC, worker_i);

    G1ScanObjsDuringUpdateRSClosure scan_hcc_cl(_g1h, pss);
    G1RefineCardClosure refine_card_cl(_g1h, &scan_hcc_cl);
    _g1h->iterate_hcc_closure(&refine_card_cl, worker_i);
  }

  // Now apply the closure to all remaining log entries.
  {
    G1EvacPhaseTimesTracker x(p, pss, G1GCPhaseTimes::UpdateRS, worker_i);

    G1ScanObjsDuringUpdateRSClosure update_rs_cl(_g1h, pss);
    G1RefineCardClosure refine_card_cl(_g1h, &update_rs_cl);
    _g1h->iterate_dirty_card_closure(&refine_card_cl, worker_i);

    p->record_thread_work_item(G1GCPhaseTimes::UpdateRS, worker_i, refine_card_cl.cards_scanned(), G1GCPhaseTimes::UpdateRSScannedCards);
    p->record_thread_work_item(G1GCPhaseTimes::UpdateRS, worker_i, refine_card_cl.cards_skipped(), G1GCPhaseTimes::UpdateRSSkippedCards);
  }
}

void G1RemSet::oops_into_collection_set_do(G1ParScanThreadState* pss, uint worker_i) {
  update_rem_set(pss, worker_i);
  scan_rem_set(pss, worker_i);;
}

void G1RemSet::prepare_for_oops_into_collection_set_do() {
  DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();
  dcqs.concatenate_logs();

  _scan_state->reset();
}

void G1RemSet::cleanup_after_oops_into_collection_set_do() {
  G1GCPhaseTimes* phase_times = _g1h->g1_policy()->phase_times();

  // Set all cards back to clean.
  double start = os::elapsedTime();
  _scan_state->clear_card_table(_g1h->workers());
  phase_times->record_clear_ct_time((os::elapsedTime() - start) * 1000.0);
}

inline void check_card_ptr(jbyte* card_ptr, G1CardTable* ct) {
#ifdef ASSERT
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  assert(g1h->is_in_exact(ct->addr_for(card_ptr)),
         "Card at " PTR_FORMAT " index " SIZE_FORMAT " representing heap at " PTR_FORMAT " (%u) must be in committed heap",
         p2i(card_ptr),
         ct->index_for(ct->addr_for(card_ptr)),
         p2i(ct->addr_for(card_ptr)),
         g1h->addr_to_region(ct->addr_for(card_ptr)));
#endif
}

void G1RemSet::refine_card_concurrently(jbyte* card_ptr,
                                        uint worker_i) {
  assert(!_g1h->is_gc_active(), "Only call concurrently");

  // Construct the region representing the card.
  HeapWord* start = _ct->addr_for(card_ptr);
  // And find the region containing it.
  HeapRegion* r = _g1h->heap_region_containing_or_null(start);

  // If this is a (stale) card into an uncommitted region, exit.
  if (r == NULL) {
    return;
  }

  check_card_ptr(card_ptr, _ct);

  // If the card is no longer dirty, nothing to do.
  if (*card_ptr != G1CardTable::dirty_card_val()) {
    return;
  }

  // This check is needed for some uncommon cases where we should
  // ignore the card.
  //
  // The region could be young.  Cards for young regions are
  // distinctly marked (set to g1_young_gen), so the post-barrier will
  // filter them out.  However, that marking is performed
  // concurrently.  A write to a young object could occur before the
  // card has been marked young, slipping past the filter.
  //
  // The card could be stale, because the region has been freed since
  // the card was recorded. In this case the region type could be
  // anything.  If (still) free or (reallocated) young, just ignore
  // it.  If (reallocated) old or humongous, the later card trimming
  // and additional checks in iteration may detect staleness.  At
  // worst, we end up processing a stale card unnecessarily.
  //
  // In the normal (non-stale) case, the synchronization between the
  // enqueueing of the card and processing it here will have ensured
  // we see the up-to-date region type here.
  if (!r->is_old_or_humongous_or_archive()) {
    return;
  }

  // The result from the hot card cache insert call is either:
  //   * pointer to the current card
  //     (implying that the current card is not 'hot'),
  //   * null
  //     (meaning we had inserted the card ptr into the "hot" card cache,
  //     which had some headroom),
  //   * a pointer to a "hot" card that was evicted from the "hot" cache.
  //

  if (_hot_card_cache->use_cache()) {
    assert(!SafepointSynchronize::is_at_safepoint(), "sanity");

    const jbyte* orig_card_ptr = card_ptr;
    card_ptr = _hot_card_cache->insert(card_ptr);
    if (card_ptr == NULL) {
      // There was no eviction. Nothing to do.
      return;
    } else if (card_ptr != orig_card_ptr) {
      // Original card was inserted and an old card was evicted.
      start = _ct->addr_for(card_ptr);
      r = _g1h->heap_region_containing(start);

      // Check whether the region formerly in the cache should be
      // ignored, as discussed earlier for the original card.  The
      // region could have been freed while in the cache.
      if (!r->is_old_or_humongous_or_archive()) {
        return;
      }
    } // Else we still have the original card.
  }

  // Trim the region designated by the card to what's been allocated
  // in the region.  The card could be stale, or the card could cover
  // (part of) an object at the end of the allocated space and extend
  // beyond the end of allocation.

  // Non-humongous objects are only allocated in the old-gen during
  // GC, so if region is old then top is stable.  Humongous object
  // allocation sets top last; if top has not yet been set, this is
  // a stale card and we'll end up with an empty intersection.  If
  // this is not a stale card, the synchronization between the
  // enqueuing of the card and processing it here will have ensured
  // we see the up-to-date top here.
  HeapWord* scan_limit = r->top();

  if (scan_limit <= start) {
    // If the trimmed region is empty, the card must be stale.
    return;
  }

  // Okay to clean and process the card now.  There are still some
  // stale card cases that may be detected by iteration and dealt with
  // as iteration failure.
  *const_cast<volatile jbyte*>(card_ptr) = G1CardTable::clean_card_val();

  // This fence serves two purposes.  First, the card must be cleaned
  // before processing the contents.  Second, we can't proceed with
  // processing until after the read of top, for synchronization with
  // possibly concurrent humongous object allocation.  It's okay that
  // reading top and reading type were racy wrto each other.  We need
  // both set, in any order, to proceed.
  OrderAccess::fence();

  // Don't use addr_for(card_ptr + 1) which can ask for
  // a card beyond the heap.
  HeapWord* end = start + G1CardTable::card_size_in_words;
  MemRegion dirty_region(start, MIN2(scan_limit, end));
  assert(!dirty_region.is_empty(), "sanity");

  G1ConcurrentRefineOopClosure conc_refine_cl(_g1h, worker_i);

  bool card_processed =
    r->oops_on_card_seq_iterate_careful<false>(dirty_region, &conc_refine_cl);

  // If unable to process the card then we encountered an unparsable
  // part of the heap (e.g. a partially allocated object) while
  // processing a stale card.  Despite the card being stale, redirty
  // and re-enqueue, because we've already cleaned the card.  Without
  // this we could incorrectly discard a non-stale card.
  if (!card_processed) {
    // The card might have gotten re-dirtied and re-enqueued while we
    // worked.  (In fact, it's pretty likely.)
    if (*card_ptr != G1CardTable::dirty_card_val()) {
      *card_ptr = G1CardTable::dirty_card_val();
      MutexLockerEx x(Shared_DirtyCardQ_lock,
                      Mutex::_no_safepoint_check_flag);
      DirtyCardQueue* sdcq =
        G1BarrierSet::dirty_card_queue_set().shared_dirty_card_queue();
      sdcq->enqueue(card_ptr);
    }
  } else {
    _num_conc_refined_cards++; // Unsynchronized update, only used for logging.
  }
}

bool G1RemSet::refine_card_during_gc(jbyte* card_ptr,
                                     G1ScanObjsDuringUpdateRSClosure* update_rs_cl) {
  assert(_g1h->is_gc_active(), "Only call during GC");

  // Construct the region representing the card.
  HeapWord* card_start = _ct->addr_for(card_ptr);
  // And find the region containing it.
  uint const card_region_idx = _g1h->addr_to_region(card_start);

  HeapWord* scan_limit = _scan_state->scan_top(card_region_idx);
  if (scan_limit == NULL) {
    // This is a card into an uncommitted region. We need to bail out early as we
    // should not access the corresponding card table entry.
    return false;
  }

  check_card_ptr(card_ptr, _ct);

  // If the card is no longer dirty, nothing to do. This covers cards that were already
  // scanned as parts of the remembered sets.
  if (*card_ptr != G1CardTable::dirty_card_val()) {
    return false;
  }

  // We claim lazily (so races are possible but they're benign), which reduces the
  // number of potential duplicate scans (multiple threads may enqueue the same card twice).
  *card_ptr = G1CardTable::clean_card_val() | G1CardTable::claimed_card_val();

  _scan_state->add_dirty_region(card_region_idx);
  if (scan_limit <= card_start) {
    // If the card starts above the area in the region containing objects to scan, skip it.
    return false;
  }

  // Don't use addr_for(card_ptr + 1) which can ask for
  // a card beyond the heap.
  HeapWord* card_end = card_start + G1CardTable::card_size_in_words;
  MemRegion dirty_region(card_start, MIN2(scan_limit, card_end));
  assert(!dirty_region.is_empty(), "sanity");

  HeapRegion* const card_region = _g1h->region_at(card_region_idx);
  assert(!card_region->is_young(), "Should not scan card in young region %u", card_region_idx);
  bool card_processed = card_region->oops_on_card_seq_iterate_careful<true>(dirty_region, update_rs_cl);
  assert(card_processed, "must be");
  return true;
}

void G1RemSet::print_periodic_summary_info(const char* header, uint period_count) {
  if ((G1SummarizeRSetStatsPeriod > 0) && log_is_enabled(Trace, gc, remset) &&
      (period_count % G1SummarizeRSetStatsPeriod == 0)) {

    G1RemSetSummary current(this);
    _prev_period_summary.subtract_from(&current);

    Log(gc, remset) log;
    log.trace("%s", header);
    ResourceMark rm;
    LogStream ls(log.trace());
    _prev_period_summary.print_on(&ls);

    _prev_period_summary.set(&current);
  }
}

void G1RemSet::print_summary_info() {
  Log(gc, remset, exit) log;
  if (log.is_trace()) {
    log.trace(" Cumulative RS summary");
    G1RemSetSummary current(this);
    ResourceMark rm;
    LogStream ls(log.trace());
    current.print_on(&ls);
  }
}

class G1RebuildRemSetTask: public AbstractGangTask {
  // Aggregate the counting data that was constructed concurrently
  // with marking.
  class G1RebuildRemSetHeapRegionClosure : public HeapRegionClosure {
    G1ConcurrentMark* _cm;
    G1RebuildRemSetClosure _update_cl;

    // Applies _update_cl to the references of the given object, limiting objArrays
    // to the given MemRegion. Returns the amount of words actually scanned.
    size_t scan_for_references(oop const obj, MemRegion mr) {
      size_t const obj_size = obj->size();
      // All non-objArrays and objArrays completely within the mr
      // can be scanned without passing the mr.
      if (!obj->is_objArray() || mr.contains(MemRegion((HeapWord*)obj, obj_size))) {
        obj->oop_iterate(&_update_cl);
        return obj_size;
      }
      // This path is for objArrays crossing the given MemRegion. Only scan the
      // area within the MemRegion.
      obj->oop_iterate(&_update_cl, mr);
      return mr.intersection(MemRegion((HeapWord*)obj, obj_size)).word_size();
    }

    // A humongous object is live (with respect to the scanning) either
    // a) it is marked on the bitmap as such
    // b) its TARS is larger than TAMS, i.e. has been allocated during marking.
    bool is_humongous_live(oop const humongous_obj, const G1CMBitMap* const bitmap, HeapWord* tams, HeapWord* tars) const {
      return bitmap->is_marked(humongous_obj) || (tars > tams);
    }

    // Iterator over the live objects within the given MemRegion.
    class LiveObjIterator : public StackObj {
      const G1CMBitMap* const _bitmap;
      const HeapWord* _tams;
      const MemRegion _mr;
      HeapWord* _current;

      bool is_below_tams() const {
        return _current < _tams;
      }

      bool is_live(HeapWord* obj) const {
        return !is_below_tams() || _bitmap->is_marked(obj);
      }

      HeapWord* bitmap_limit() const {
        return MIN2(const_cast<HeapWord*>(_tams), _mr.end());
      }

      void move_if_below_tams() {
        if (is_below_tams() && has_next()) {
          _current = _bitmap->get_next_marked_addr(_current, bitmap_limit());
        }
      }
    public:
      LiveObjIterator(const G1CMBitMap* const bitmap, const HeapWord* tams, const MemRegion mr, HeapWord* first_oop_into_mr) :
          _bitmap(bitmap),
          _tams(tams),
          _mr(mr),
          _current(first_oop_into_mr) {

        assert(_current <= _mr.start(),
               "First oop " PTR_FORMAT " should extend into mr [" PTR_FORMAT ", " PTR_FORMAT ")",
               p2i(first_oop_into_mr), p2i(mr.start()), p2i(mr.end()));

        // Step to the next live object within the MemRegion if needed.
        if (is_live(_current)) {
          // Non-objArrays were scanned by the previous part of that region.
          if (_current < mr.start() && !oop(_current)->is_objArray()) {
            _current += oop(_current)->size();
            // We might have positioned _current on a non-live object. Reposition to the next
            // live one if needed.
            move_if_below_tams();
          }
        } else {
          // The object at _current can only be dead if below TAMS, so we can use the bitmap.
          // immediately.
          _current = _bitmap->get_next_marked_addr(_current, bitmap_limit());
          assert(_current == _mr.end() || is_live(_current),
                 "Current " PTR_FORMAT " should be live (%s) or beyond the end of the MemRegion (" PTR_FORMAT ")",
                 p2i(_current), BOOL_TO_STR(is_live(_current)), p2i(_mr.end()));
        }
      }

      void move_to_next() {
        _current += next()->size();
        move_if_below_tams();
      }

      oop next() const {
        oop result = oop(_current);
        assert(is_live(_current),
               "Object " PTR_FORMAT " must be live TAMS " PTR_FORMAT " below %d mr " PTR_FORMAT " " PTR_FORMAT " outside %d",
               p2i(_current), p2i(_tams), _tams > _current, p2i(_mr.start()), p2i(_mr.end()), _mr.contains(result));
        return result;
      }

      bool has_next() const {
        return _current < _mr.end();
      }
    };

    // Rebuild remembered sets in the part of the region specified by mr and hr.
    // Objects between the bottom of the region and the TAMS are checked for liveness
    // using the given bitmap. Objects between TAMS and TARS are assumed to be live.
    // Returns the number of live words between bottom and TAMS.
    size_t rebuild_rem_set_in_region(const G1CMBitMap* const bitmap,
                                     HeapWord* const top_at_mark_start,
                                     HeapWord* const top_at_rebuild_start,
                                     HeapRegion* hr,
                                     MemRegion mr) {
      size_t marked_words = 0;

      if (hr->is_humongous()) {
        oop const humongous_obj = oop(hr->humongous_start_region()->bottom());
        if (is_humongous_live(humongous_obj, bitmap, top_at_mark_start, top_at_rebuild_start)) {
          // We need to scan both [bottom, TAMS) and [TAMS, top_at_rebuild_start);
          // however in case of humongous objects it is sufficient to scan the encompassing
          // area (top_at_rebuild_start is always larger or equal to TAMS) as one of the
          // two areas will be zero sized. I.e. TAMS is either
          // the same as bottom or top(_at_rebuild_start). There is no way TAMS has a different
          // value: this would mean that TAMS points somewhere into the object.
          assert(hr->top() == top_at_mark_start || hr->top() == top_at_rebuild_start,
                 "More than one object in the humongous region?");
          humongous_obj->oop_iterate(&_update_cl, mr);
          return top_at_mark_start != hr->bottom() ? mr.intersection(MemRegion((HeapWord*)humongous_obj, humongous_obj->size())).byte_size() : 0;
        } else {
          return 0;
        }
      }

      for (LiveObjIterator it(bitmap, top_at_mark_start, mr, hr->block_start(mr.start())); it.has_next(); it.move_to_next()) {
        oop obj = it.next();
        size_t scanned_size = scan_for_references(obj, mr);
        if ((HeapWord*)obj < top_at_mark_start) {
          marked_words += scanned_size;
        }
      }

      return marked_words * HeapWordSize;
    }
public:
  G1RebuildRemSetHeapRegionClosure(G1CollectedHeap* g1h,
                                   G1ConcurrentMark* cm,
                                   uint worker_id) :
    HeapRegionClosure(),
    _cm(cm),
    _update_cl(g1h, worker_id) { }

    bool do_heap_region(HeapRegion* hr) {
      if (_cm->has_aborted()) {
        return true;
      }

      uint const region_idx = hr->hrm_index();
      DEBUG_ONLY(HeapWord* const top_at_rebuild_start_check = _cm->top_at_rebuild_start(region_idx);)
      assert(top_at_rebuild_start_check == NULL ||
             top_at_rebuild_start_check > hr->bottom(),
             "A TARS (" PTR_FORMAT ") == bottom() (" PTR_FORMAT ") indicates the old region %u is empty (%s)",
             p2i(top_at_rebuild_start_check), p2i(hr->bottom()),  region_idx, hr->get_type_str());

      size_t total_marked_bytes = 0;
      size_t const chunk_size_in_words = G1RebuildRemSetChunkSize / HeapWordSize;

      HeapWord* const top_at_mark_start = hr->prev_top_at_mark_start();

      HeapWord* cur = hr->bottom();
      while (cur < hr->end()) {
        // After every iteration (yield point) we need to check whether the region's
        // TARS changed due to e.g. eager reclaim.
        HeapWord* const top_at_rebuild_start = _cm->top_at_rebuild_start(region_idx);
        if (top_at_rebuild_start == NULL) {
          return false;
        }

        MemRegion next_chunk = MemRegion(hr->bottom(), top_at_rebuild_start).intersection(MemRegion(cur, chunk_size_in_words));
        if (next_chunk.is_empty()) {
          break;
        }

        const Ticks start = Ticks::now();
        size_t marked_bytes = rebuild_rem_set_in_region(_cm->prev_mark_bitmap(),
                                                        top_at_mark_start,
                                                        top_at_rebuild_start,
                                                        hr,
                                                        next_chunk);
        Tickspan time = Ticks::now() - start;

        log_trace(gc, remset, tracking)("Rebuilt region %u "
                                        "live " SIZE_FORMAT " "
                                        "time %.3fms "
                                        "marked bytes " SIZE_FORMAT " "
                                        "bot " PTR_FORMAT " "
                                        "TAMS " PTR_FORMAT " "
                                        "TARS " PTR_FORMAT,
                                        region_idx,
                                        _cm->liveness(region_idx) * HeapWordSize,
                                        time.seconds() * 1000.0,
                                        marked_bytes,
                                        p2i(hr->bottom()),
                                        p2i(top_at_mark_start),
                                        p2i(top_at_rebuild_start));

        if (marked_bytes > 0) {
          total_marked_bytes += marked_bytes;
        }
        cur += chunk_size_in_words;

        _cm->do_yield_check();
        if (_cm->has_aborted()) {
          return true;
        }
      }
      // In the final iteration of the loop the region might have been eagerly reclaimed.
      // Simply filter out those regions. We can not just use region type because there
      // might have already been new allocations into these regions.
      DEBUG_ONLY(HeapWord* const top_at_rebuild_start = _cm->top_at_rebuild_start(region_idx);)
      assert(top_at_rebuild_start == NULL ||
             total_marked_bytes == hr->marked_bytes(),
             "Marked bytes " SIZE_FORMAT " for region %u (%s) in [bottom, TAMS) do not match calculated marked bytes " SIZE_FORMAT " "
             "(" PTR_FORMAT " " PTR_FORMAT " " PTR_FORMAT ")",
             total_marked_bytes, hr->hrm_index(), hr->get_type_str(), hr->marked_bytes(),
             p2i(hr->bottom()), p2i(top_at_mark_start), p2i(top_at_rebuild_start));
       // Abort state may have changed after the yield check.
      return _cm->has_aborted();
    }
  };

  HeapRegionClaimer _hr_claimer;
  G1ConcurrentMark* _cm;

  uint _worker_id_offset;
public:
  G1RebuildRemSetTask(G1ConcurrentMark* cm,
                      uint n_workers,
                      uint worker_id_offset) :
      AbstractGangTask("G1 Rebuild Remembered Set"),
      _hr_claimer(n_workers),
      _cm(cm),
      _worker_id_offset(worker_id_offset) {
  }

  void work(uint worker_id) {
    SuspendibleThreadSetJoiner sts_join;

    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    G1RebuildRemSetHeapRegionClosure cl(g1h, _cm, _worker_id_offset + worker_id);
    g1h->heap_region_par_iterate_from_worker_offset(&cl, &_hr_claimer, worker_id);
  }
};

void G1RemSet::rebuild_rem_set(G1ConcurrentMark* cm,
                               WorkGang* workers,
                               uint worker_id_offset) {
  uint num_workers = workers->active_workers();

  G1RebuildRemSetTask cl(cm,
                         num_workers,
                         worker_id_offset);
  workers->run_task(&cl, num_workers);
}
