/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1BatchedTask.hpp"
#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1CardSet.inline.hpp"
#include "gc/g1/g1CardTable.inline.hpp"
#include "gc/g1/g1CardTableClaimTable.inline.hpp"
#include "gc/g1/g1CardTableEntryClosure.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSet.inline.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1ConcurrentRefineSweepTask.hpp"
#include "gc/g1/g1FromCardCache.hpp"
#include "gc/g1/g1GCParPhaseTimesTracker.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionManager.inline.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1RootClosures.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/stack.inline.hpp"
#include "utilities/ticks.hpp"
#include CPU_HEADER(gc/g1/g1Globals)

// Collects information about the overall heap root scan progress during an evacuation.
//
// Scanning the remembered sets works by first merging all sources of cards to be
// scanned (refinement table, remembered sets) into a single data structure to remove
// duplicates and simplify work distribution.
//
// During the following card scanning we not only scan this combined set of cards, but
// also remember that these were completely scanned. The following evacuation passes
// do not scan these cards again, and so need to be preserved across increments.
//
// The representation for all the cards to scan is the card table: cards can have
// one of three states during GC:
// - clean: these cards will not be scanned in this pass
// - dirty: these cards will be scanned in this pass
// - scanned: these cards have already been scanned in a previous pass
//
// After all evacuation is done, we reset the card table to clean.
//
// Work distribution occurs on "chunk" basis, i.e. contiguous ranges of cards. As an
// additional optimization, during card merging we remember which regions and which
// chunks actually contain cards to be scanned. Threads iterate only across these
// regions, and only compete for chunks containing any cards.
//
// Within these chunks, a worker scans the card table on "blocks" of cards, i.e.
// contiguous ranges of dirty cards to be scanned. These blocks are converted to actual
// memory ranges and then passed on to actual scanning.
class G1RemSetScanState : public CHeapObj<mtGC> {
  class G1DirtyRegions;

  G1CardTableClaimTable _card_claim_table;
  // The complete set of regions which card table needs to be cleared at the end
  // of GC because we scribbled over these card table entries.
  //
  // Regions may be added for two reasons:
  // - they were part of the collection set: they may contain regular card marks
  // that we never scan so we must always clear their card table.
  // - or in case g1 does an optional evacuation pass, g1 marks the cards in there
  // as g1_scanned_card_val. If G1 only did an initial evacuation pass, the
  // scanning already cleared these cards. In that case they are not in this set
  // at the end of the collection.
  G1DirtyRegions* _all_dirty_regions;
  // The set of regions which card table needs to be scanned for new dirty cards
  // in the current evacuation pass.
  G1DirtyRegions* _next_dirty_regions;

// Set of (unique) regions that can be added to concurrently.
  class G1DirtyRegions : public CHeapObj<mtGC> {
    uint* _buffer;
    Atomic<uint> _cur_idx;
    size_t _max_reserved_regions;

    Atomic<bool>* _contains;

  public:
    G1DirtyRegions(size_t max_reserved_regions) :
      _buffer(NEW_C_HEAP_ARRAY(uint, max_reserved_regions, mtGC)),
      _cur_idx(0),
      _max_reserved_regions(max_reserved_regions),
      _contains(NEW_C_HEAP_ARRAY(Atomic<bool>, max_reserved_regions, mtGC)) {

      reset();
    }

    ~G1DirtyRegions() {
      FREE_C_HEAP_ARRAY(uint, _buffer);
      FREE_C_HEAP_ARRAY(Atomic<bool>, _contains);
    }

    void reset() {
      _cur_idx.store_relaxed(0);
      for (uint i = 0; i < _max_reserved_regions; i++) {
        _contains[i].store_relaxed(false);
      }
    }

    uint size() const { return _cur_idx.load_relaxed(); }

    uint at(uint idx) const {
      assert(idx < size(), "Index %u beyond valid regions", idx);
      return _buffer[idx];
    }

    void add_dirty_region(uint region) {
      if (_contains[region].load_relaxed()) {
        return;
      }

      bool marked_as_dirty = _contains[region].compare_set(false, true);
      if (marked_as_dirty) {
        uint allocated = _cur_idx.fetch_then_add(1u);
        _buffer[allocated] = region;
      }
    }

    // Creates the union of this and the other G1DirtyRegions.
    void merge(const G1DirtyRegions* other) {
      for (uint i = 0; i < other->size(); i++) {
        uint region = other->at(i);
        if (!_contains[region].load_relaxed()) {
          uint cur = _cur_idx.load_relaxed();
          _buffer[cur] = region;
          _cur_idx.store_relaxed(cur + 1);
          _contains[region].store_relaxed(true);
        }
      }
    }
  };

  // For each region, contains the maximum top() value to be used during this garbage
  // collection. Subsumes common checks like filtering out everything but old and
  // humongous regions outside the collection set.
  // This is valid because we are not interested in scanning stray remembered set
  // entries from free regions.
  HeapWord** _scan_top;

class G1ClearCardTableTask : public G1AbstractSubTask {
    G1CollectedHeap* _g1h;
    G1DirtyRegions* _regions;
    Atomic<uint> _cur_dirty_regions;

    G1RemSetScanState* _scan_state;

    static constexpr uint num_cards_per_worker = M;
  public:
    G1ClearCardTableTask(G1CollectedHeap* g1h,
                         G1DirtyRegions* regions,
                         G1RemSetScanState* scan_state) :
      G1AbstractSubTask(G1GCPhaseTimes::ClearCardTable),
      _g1h(g1h),
      _regions(regions),
      _cur_dirty_regions(0),
      _scan_state(scan_state) {}

    double worker_cost() const override {
      uint num_regions = _regions->size();

      if (num_regions == 0) {
        // There is no card table clean work, only some cleanup of memory.
        return AlmostNoWork;
      }

      double num_cards = num_regions << G1HeapRegion::LogCardsPerRegion;
      return ceil(num_cards / num_cards_per_worker);
    }

    virtual ~G1ClearCardTableTask() {
      _scan_state->cleanup();
      if (VerifyDuringGC) {
        G1CollectedHeap::heap()->verifier()->verify_card_table_cleanup();
      }
    }

    void do_work(uint worker_id) override {
      const uint num_regions_per_worker = num_cards_per_worker / (uint)G1HeapRegion::CardsPerRegion;

      uint cur = _cur_dirty_regions.load_relaxed();
      while (cur < _regions->size()) {
        uint next = _cur_dirty_regions.fetch_then_add(num_regions_per_worker);
        uint max = MIN2(next + num_regions_per_worker, _regions->size());

        for (uint i = next; i < max; i++) {
          G1HeapRegion* r = _g1h->region_at(_regions->at(i));
          // The card table contains "dirty" card marks. Clear unconditionally.
          //
          // Humongous reclaim candidates are not in the dirty set. This is fine because
          // we clean their card and refinement tables when we reclaim separately.
          r->clear_card_table();
          // There is no need to clear the refinement table here: at the start of the collection
          // we had to clear the refinement card table for collection set regions already, and any
          // old regions use it for old->collection set candidates, so they should not be cleared
          // either.
        }
        cur = max;
      }
    }
  };

public:
  G1RemSetScanState() :
    _card_claim_table(G1CollectedHeap::get_chunks_per_region_for_scan()),
    _all_dirty_regions(nullptr),
    _next_dirty_regions(nullptr),
    _scan_top(nullptr) { }

  ~G1RemSetScanState() {
    FREE_C_HEAP_ARRAY(HeapWord*, _scan_top);
  }

  void initialize(uint max_reserved_regions) {
    _card_claim_table.initialize(max_reserved_regions);
    _scan_top = NEW_C_HEAP_ARRAY(HeapWord*, max_reserved_regions, mtGC);
  }

  // Reset the claim and clear scan top for all regions, including
  // regions currently not available or free. Since regions might
  // become used during the collection these values must be valid
  // for those regions as well.
  void prepare() {
    size_t max_reserved_regions = _card_claim_table.max_reserved_regions();

    for (size_t i = 0; i < max_reserved_regions; i++) {
      clear_scan_top((uint)i);
    }

    _all_dirty_regions = new G1DirtyRegions(max_reserved_regions);
    _next_dirty_regions = new G1DirtyRegions(max_reserved_regions);
  }

  void prepare_for_merge_heap_roots() {
    // We populate the next dirty regions at the start of GC with all old/humongous
    // regions.
    //assert(_next_dirty_regions->size() == 0, "next dirty regions must be empty");

    _card_claim_table.reset_all_to_unclaimed();
  }

  void complete_evac_phase(bool merge_dirty_regions) {
    if (merge_dirty_regions) {
      _all_dirty_regions->merge(_next_dirty_regions);
    }
    _next_dirty_regions->reset();
  }

  // Returns whether the given region contains cards we need to scan. The remembered
  // set and other sources may contain cards that
  // - are in uncommitted regions
  // - are located in the collection set
  // - are located in free regions
  // as we do not clean up remembered sets before merging heap roots.
  bool contains_cards_to_process(uint const region_idx) const {
    G1HeapRegion* hr = G1CollectedHeap::heap()->region_at_or_null(region_idx);
    return (hr != nullptr && !hr->in_collection_set() && hr->is_old_or_humongous());
  }

  size_t num_cards_in_dirty_regions() const {
    return _next_dirty_regions->size() * G1HeapRegion::CardsPerRegion;
  }

  G1AbstractSubTask* create_cleanup_after_scan_heap_roots_task() {
    return new G1ClearCardTableTask(G1CollectedHeap::heap(), _all_dirty_regions, this);
  }

  void cleanup() {
    delete _all_dirty_regions;
    _all_dirty_regions = nullptr;

    delete _next_dirty_regions;
    _next_dirty_regions = nullptr;
  }

  void iterate_dirty_regions_from(G1HeapRegionClosure* cl, uint worker_id) {
    uint num_regions = _next_dirty_regions->size();

    if (num_regions == 0) {
      return;
    }

    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    WorkerThreads* workers = g1h->workers();
    uint const max_workers = workers->active_workers();

    uint const start_pos = num_regions * worker_id / max_workers;
    uint cur = start_pos;

    do {
      bool result = cl->do_heap_region(g1h->region_at(_next_dirty_regions->at(cur)));
      guarantee(!result, "Not allowed to ask for early termination.");
      cur++;
      if (cur == _next_dirty_regions->size()) {
        cur = 0;
      }
    } while (cur != start_pos);
  }

  bool has_cards_to_scan(uint region) {
    return _card_claim_table.has_unclaimed_cards(region);
  }

  void add_dirty_region(uint const region) {
  #ifdef ASSERT
   G1HeapRegion* hr = G1CollectedHeap::heap()->region_at(region);
   assert(!hr->in_collection_set() && hr->is_old_or_humongous(),
          "Region %u is not suitable for scanning, is %sin collection set or %s",
          hr->hrm_index(), hr->in_collection_set() ? "" : "not ", hr->get_short_type_str());
  #endif
    _next_dirty_regions->add_dirty_region(region);
  }

  void add_all_dirty_region(uint region) {
#ifdef ASSERT
    G1HeapRegion* hr = G1CollectedHeap::heap()->region_at(region);
    assert(hr->in_collection_set(),
           "Only add collection set regions to all dirty regions directly but %u is %s",
           hr->hrm_index(), hr->get_short_type_str());
#endif
    _all_dirty_regions->add_dirty_region(region);
  }

  void set_scan_top(uint region_idx, HeapWord* value) {
    _scan_top[region_idx] = value;
  }

  HeapWord* scan_top(uint region_idx) const {
    return _scan_top[region_idx];
  }

  void clear_scan_top(uint region_idx) {
    set_scan_top(region_idx, nullptr);
  }

  G1CardTableChunkClaimer claimer(uint region_idx) {
    return G1CardTableChunkClaimer(&_card_claim_table, region_idx);
  }
};

G1RemSet::G1RemSet(G1CollectedHeap* g1h) :
  _scan_state(new G1RemSetScanState()),
  _prev_period_summary(false),
  _g1h(g1h),
  _g1p(_g1h->policy()) {
}

G1RemSet::~G1RemSet() {
  delete _scan_state;
}

void G1RemSet::initialize(uint max_reserved_regions) {
  _scan_state->initialize(max_reserved_regions);
}

// Scans a heap region for dirty cards.
class G1ScanHRForRegionClosure : public G1HeapRegionClosure {
  using CardValue = CardTable::CardValue;

  G1CollectedHeap* _g1h;
  G1CardTable* _ct;

  G1ParScanThreadState* _pss;

  G1RemSetScanState* _scan_state;

  G1GCPhaseTimes::GCParPhases _phase;

  uint   _worker_id;

  size_t _cards_pending;
  size_t _cards_empty;
  size_t _cards_scanned;
  size_t _blocks_scanned;
  size_t _chunks_claimed;
  size_t _heap_roots_found;

  Tickspan _rem_set_root_scan_time;
  Tickspan _rem_set_trim_partially_time;

  // The address to which this thread already scanned (walked the heap) up to during
  // card scanning (exclusive).
  HeapWord* _scanned_to;
  CardValue _scanned_card_value;

  HeapWord* scan_memregion(uint region_idx_for_card, MemRegion mr, size_t &roots_found) {
    G1HeapRegion* const card_region = _g1h->region_at(region_idx_for_card);
    G1ScanCardClosure card_cl(_g1h, _pss, roots_found);

    HeapWord* const scanned_to = card_region->oops_on_memregion_seq_iterate_careful<true>(mr, &card_cl);
    assert(scanned_to != nullptr, "Should be able to scan range");
    assert(scanned_to >= mr.end(), "Scanned to " PTR_FORMAT " less than range " PTR_FORMAT, p2i(scanned_to), p2i(mr.end()));

    _pss->trim_queue_partially();
    return scanned_to;
  }

  void do_claimed_block(uint const region_idx, CardValue* const dirty_l, CardValue* const dirty_r, size_t& pending_cards) {
    pending_cards += _ct->change_dirty_cards_to(dirty_l, dirty_r, _scanned_card_value);
    size_t num_cards = pointer_delta(dirty_r, dirty_l, sizeof(CardValue));
    _blocks_scanned++;

    HeapWord* const card_start = _ct->addr_for(dirty_l);
    HeapWord* const top = _scan_state->scan_top(region_idx);
    if (card_start >= top) {
      return;
    }

    HeapWord* scan_end = MIN2(card_start + (num_cards << (CardTable::card_shift() - LogHeapWordSize)), top);
    if (_scanned_to >= scan_end) {
      return;
    }
    MemRegion mr(MAX2(card_start, _scanned_to), scan_end);
    size_t roots_found = 0;
    _scanned_to = scan_memregion(region_idx, mr, roots_found);

    if (roots_found == 0) {
      _cards_empty += num_cards;
    }
    _cards_scanned += num_cards;
    _heap_roots_found += roots_found;
  }

  void scan_heap_roots(G1HeapRegion* r) {
    uint const region_idx = r->hrm_index();

    ResourceMark rm;

    G1CardTableChunkClaimer claim = _scan_state->claimer(region_idx);

    // Set the current scan "finger" to null for every heap region to scan. Since
    // the claim value is monotonically increasing, the check to not scan below this
    // will filter out objects spanning chunks within the region too then, as opposed
    // to resetting this value for every claim.
    _scanned_to = nullptr;

    size_t pending_cards = 0;

    while (claim.has_next()) {
      _chunks_claimed++;

      size_t const region_card_base_idx = ((size_t)region_idx << G1HeapRegion::LogCardsPerRegion) + claim.value();

      CardValue* const start_card = _ct->byte_for_index(region_card_base_idx);
      CardValue* const end_card = start_card + claim.size();

      G1ChunkScanner chunk_scanner{start_card, end_card};
      chunk_scanner.on_dirty_cards([&] (CardValue* dirty_l, CardValue* dirty_r) {
                                     do_claimed_block(region_idx, dirty_l, dirty_r, pending_cards);
                                   });
    }
    _cards_pending += pending_cards;
  }

public:
  G1ScanHRForRegionClosure(G1RemSetScanState* scan_state,
                           G1ParScanThreadState* pss,
                           uint worker_id,
                           G1GCPhaseTimes::GCParPhases phase,
                           bool remember_already_scanned_cards) :
    _g1h(G1CollectedHeap::heap()),
    _ct(_g1h->card_table()),
    _pss(pss),
    _scan_state(scan_state),
    _phase(phase),
    _worker_id(worker_id),
    _cards_pending(0),
    _cards_empty(0),
    _cards_scanned(0),
    _blocks_scanned(0),
    _chunks_claimed(0),
    _heap_roots_found(0),
    _rem_set_root_scan_time(),
    _rem_set_trim_partially_time(),
    _scanned_to(nullptr),
    _scanned_card_value(remember_already_scanned_cards ? G1CardTable::g1_scanned_card_val()
                                                       : G1CardTable::clean_card_val()) {
  }

  bool do_heap_region(G1HeapRegion* r) {
    assert(!r->in_collection_set() && r->is_old_or_humongous(),
           "Should only be called on old gen non-collection set regions but region %u is not.",
           r->hrm_index());
    uint const region_idx = r->hrm_index();

    if (_scan_state->has_cards_to_scan(region_idx)) {
      G1EvacPhaseWithTrimTimeTracker timer(_pss, _rem_set_root_scan_time, _rem_set_trim_partially_time);
      scan_heap_roots(r);
    }
    return false;
  }

  Tickspan rem_set_root_scan_time() const { return _rem_set_root_scan_time; }
  Tickspan rem_set_trim_partially_time() const { return _rem_set_trim_partially_time; }

  size_t cards_pending() const { return _cards_pending; }
  size_t cards_scanned_empty() const { return _cards_empty; }
  size_t cards_scanned() const { return _cards_scanned; }
  size_t blocks_scanned() const { return _blocks_scanned; }
  size_t chunks_claimed() const { return _chunks_claimed; }
  size_t heap_roots_found() const { return _heap_roots_found; }
};

void G1RemSet::scan_heap_roots(G1ParScanThreadState* pss,
                               uint worker_id,
                               G1GCPhaseTimes::GCParPhases scan_phase,
                               G1GCPhaseTimes::GCParPhases objcopy_phase,
                               bool remember_already_scanned_cards) {
  EventGCPhaseParallel event;
  G1ScanHRForRegionClosure cl(_scan_state, pss, worker_id, scan_phase, remember_already_scanned_cards);
  _scan_state->iterate_dirty_regions_from(&cl, worker_id);

  event.commit(GCId::current(), worker_id, G1GCPhaseTimes::phase_name(scan_phase));

  G1GCPhaseTimes* p = _g1p->phase_times();

  p->record_or_add_time_secs(objcopy_phase, worker_id, cl.rem_set_trim_partially_time().seconds());

  p->record_or_add_time_secs(scan_phase, worker_id, cl.rem_set_root_scan_time().seconds());

  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.cards_pending(), G1GCPhaseTimes::ScanHRPendingCards);
  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.cards_scanned_empty(), G1GCPhaseTimes::ScanHRScannedEmptyCards);
  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.cards_scanned(), G1GCPhaseTimes::ScanHRScannedCards);
  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.blocks_scanned(), G1GCPhaseTimes::ScanHRScannedBlocks);
  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.chunks_claimed(), G1GCPhaseTimes::ScanHRClaimedChunks);
  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.heap_roots_found(), G1GCPhaseTimes::ScanHRFoundRoots);
}

// Wrapper around a NMethodClosure to count the number of nmethods scanned.
class G1ScanAndCountNMethodClosure : public NMethodClosure {
  NMethodClosure* _cl;
  size_t _count;

public:
  G1ScanAndCountNMethodClosure(NMethodClosure* cl) : _cl(cl), _count(0) {
  }

  void do_nmethod(nmethod* nm) override {
    _cl->do_nmethod(nm);
    _count++;
  }

  size_t count() const {
    return _count;
  }
};

// Heap region closure to be applied to all regions in the current collection set
// increment to fix up non-card related roots.
class G1ScanCodeRootsClosure : public G1HeapRegionClosure {
  G1ParScanThreadState* _pss;
  G1RemSetScanState* _scan_state;

  uint _worker_id;

  size_t _code_roots_scanned;

public:
  G1ScanCodeRootsClosure(G1RemSetScanState* scan_state,
                         G1ParScanThreadState* pss,
                         uint worker_id) :
    _pss(pss),
    _scan_state(scan_state),
    _worker_id(worker_id),
    _code_roots_scanned(0) { }

  bool do_heap_region(G1HeapRegion* r) {
    // Scan the code root list attached to the current region
    G1ScanAndCountNMethodClosure cl(_pss->closures()->weak_nmethods());
    r->code_roots_do(&cl);
    _code_roots_scanned += cl.count();
    return false;
  }

  size_t code_roots_scanned() const { return _code_roots_scanned; }
};

void G1RemSet::scan_collection_set_code_roots(G1ParScanThreadState* pss,
                                              uint worker_id,
                                              G1GCPhaseTimes::GCParPhases coderoots_phase,
                                              G1GCPhaseTimes::GCParPhases objcopy_phase) {
  EventGCPhaseParallel event;
  Tickspan code_root_scan_time;
  Tickspan code_root_trim_partially_time;

  G1GCPhaseTimes* p = _g1h->phase_times();
  {
    G1EvacPhaseWithTrimTimeTracker timer(pss, code_root_scan_time, code_root_trim_partially_time);

    G1ScanCodeRootsClosure cl(_scan_state, pss, worker_id);
    // Code roots work distribution occurs inside the iteration method. So scan all collection
    // set regions for all threads.
    _g1h->collection_set_iterate_increment_from(&cl, worker_id);

    p->record_or_add_thread_work_item(coderoots_phase, worker_id, cl.code_roots_scanned(), G1GCPhaseTimes::CodeRootsScannedNMethods);
  }

  p->record_or_add_time_secs(coderoots_phase, worker_id, code_root_scan_time.seconds());
  p->add_time_secs(objcopy_phase, worker_id, code_root_trim_partially_time.seconds());

  event.commit(GCId::current(), worker_id, G1GCPhaseTimes::phase_name(coderoots_phase));
}

class G1ScanOptionalRemSetRootsClosure : public G1HeapRegionClosure {
  G1ParScanThreadState* _pss;

  uint _worker_id;

  G1GCPhaseTimes::GCParPhases _scan_phase;

  size_t _opt_roots_scanned;

  size_t _opt_refs_scanned;
  size_t _opt_refs_memory_used;

  void scan_opt_rem_set_roots(G1HeapRegion* r) {
    G1OopStarChunkedList* opt_rem_set_list = _pss->oops_into_optional_region(r);

    G1ScanCardClosure scan_cl(G1CollectedHeap::heap(), _pss, _opt_roots_scanned);
    G1ScanRSForOptionalClosure cl(G1CollectedHeap::heap(), &scan_cl);
    _opt_refs_scanned += opt_rem_set_list->oops_do(&cl, _pss->closures()->strong_oops());
    _opt_refs_memory_used += opt_rem_set_list->used_memory();
  }

public:
  G1ScanOptionalRemSetRootsClosure(G1ParScanThreadState* pss,
                                   uint worker_id,
                                   G1GCPhaseTimes::GCParPhases scan_phase) :
    _pss(pss),
    _worker_id(worker_id),
    _scan_phase(scan_phase),
    _opt_roots_scanned(0),
    _opt_refs_scanned(0),
    _opt_refs_memory_used(0) { }

  bool do_heap_region(G1HeapRegion* r) override {
    if (r->has_index_in_opt_cset()) {
      scan_opt_rem_set_roots(r);
    }
    return false;
  }

  size_t opt_roots_scanned() const { return _opt_roots_scanned; }
  size_t opt_refs_scanned() const { return _opt_refs_scanned; }
  size_t opt_refs_memory_used() const { return _opt_refs_memory_used; }
};

void G1RemSet::scan_collection_set_optional_roots(G1ParScanThreadState* pss,
                                                  uint worker_id,
                                                  G1GCPhaseTimes::GCParPhases scan_phase,
                                                  G1GCPhaseTimes::GCParPhases objcopy_phase) {
  assert(scan_phase == G1GCPhaseTimes::OptScanHR, "must be");

  EventGCPhaseParallel event;

  Tickspan rem_set_opt_root_scan_time;
  Tickspan rem_set_opt_trim_partially_time;
  G1EvacPhaseWithTrimTimeTracker timer(pss, rem_set_opt_root_scan_time, rem_set_opt_trim_partially_time);

  G1GCPhaseTimes* p = _g1h->phase_times();

  G1ScanOptionalRemSetRootsClosure cl(pss, worker_id, scan_phase);
  // The individual references for the optional remembered set are per-worker, so every worker
  // always need to scan all regions (no claimer).
  _g1h->collection_set_iterate_increment_from(&cl, worker_id);

  p->record_or_add_time_secs(scan_phase, worker_id, rem_set_opt_root_scan_time.seconds());
  p->record_or_add_time_secs(objcopy_phase, worker_id, rem_set_opt_trim_partially_time.seconds());

  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.opt_roots_scanned(), G1GCPhaseTimes::ScanHRFoundRoots);
  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.opt_refs_scanned(), G1GCPhaseTimes::ScanHRScannedOptRefs);
  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.opt_refs_memory_used(), G1GCPhaseTimes::ScanHRUsedMemory);

  event.commit(GCId::current(), worker_id, G1GCPhaseTimes::phase_name(scan_phase));
}


#ifdef ASSERT
void G1RemSet::assert_scan_top_is_null(uint hrm_index) {
  assert(_scan_state->scan_top(hrm_index) == nullptr,
         "scan_top of region %u is unexpectedly " PTR_FORMAT,
         hrm_index, p2i(_scan_state->scan_top(hrm_index)));
}
#endif

void G1RemSet::prepare_region_for_scan(G1HeapRegion* r) {
  uint hrm_index = r->hrm_index();

  r->prepare_remset_for_scan();

  // Only update non-collection set old regions, others must have already been set
  // to null (don't scan) in the initialization.
  if (r->in_collection_set()) {
    assert_scan_top_is_null(hrm_index);
  } else if (r->is_old_or_humongous()) {
    _scan_state->set_scan_top(hrm_index, r->top());
    _scan_state->add_dirty_region(hrm_index);
  } else {
    assert_scan_top_is_null(hrm_index);
    assert(r->is_free(),
           "Region %u should be free region but is %s", hrm_index, r->get_type_str());
  }
}

void G1RemSet::prepare_for_scan_heap_roots() {
  _scan_state->prepare();
}

// Small ring buffer used for prefetching cards for write from the card
// table during GC.
template <class T>
class G1MergeHeapRootsPrefetchCache {
public:
  static const uint CacheSize = G1MergeHeapRootsPrefetchCacheSize;

  static_assert(is_power_of_2(CacheSize), "Cache size must be power of 2");

private:
  T* _cache[CacheSize];

  uint _cur_cache_idx;

  NONCOPYABLE(G1MergeHeapRootsPrefetchCache);

protected:
  // Initial content of all elements in the cache. It's value should be
  // "neutral", i.e. no work done on it when processing it.
  G1CardTable::CardValue _dummy_card;

  ~G1MergeHeapRootsPrefetchCache() = default;

public:

  G1MergeHeapRootsPrefetchCache(G1CardTable::CardValue dummy_card_value) :
    _cur_cache_idx(0),
    _dummy_card(dummy_card_value) {

    for (uint i = 0; i < CacheSize; i++) {
      push(&_dummy_card);
    }
  }

  T* push(T* elem) {
    Prefetch::write(elem, 0);
    T* result = _cache[_cur_cache_idx];
    _cache[_cur_cache_idx++] = elem;
    _cur_cache_idx &= (CacheSize - 1);

    return result;
  }
};

// Task to merge a non-dirty refinement table into the (primary) card table.
class MergeRefinementTableTask : public WorkerTask {

  G1CardTableClaimTable* _scan_state;
  uint _max_workers;

  class G1MergeRefinementTableRegionClosure : public G1HeapRegionClosure {
    G1CardTableClaimTable* _scan_state;

    bool do_heap_region(G1HeapRegion* r) override {
      if (!_scan_state->has_unclaimed_cards(r->hrm_index())) {
        return false;
      }

      // We can blindly clear all collection set region's refinement tables: these
      // regions will be evacuated and need their refinement table reset in case
      // of evacuation failure.
      // Young regions contain random marks, which are obvious to just clear. The
      // card marks of other collection set region's refinement tables are also
      // uninteresting.
      if (r->in_collection_set()) {
        uint claim = _scan_state->claim_all_cards(r->hrm_index());
        // Concurrent refinement may have started merging this region (we also
        // get here for non-young regions), the claim may be non-zero for those.
        // We could get away here with just clearing the area from the current
        // claim to the last card in the region, but for now just do it all.
        if (claim < G1HeapRegion::CardsPerRegion) {
          r->clear_refinement_table();
        }
        return false;
      }

      assert(r->is_old_or_humongous(), "must be");

      G1CollectedHeap* g1h = G1CollectedHeap::heap();
      G1CardTable* card_table = g1h->card_table();
      G1CardTable* refinement_table = g1h->refinement_table();

      size_t const region_card_base_idx = (size_t)r->hrm_index() << G1HeapRegion::LogCardsPerRegion;

      G1CardTableChunkClaimer claim(_scan_state, r->hrm_index());

      while (claim.has_next()) {
        size_t const start_idx = region_card_base_idx + claim.value();

        size_t* card_cur_word = (size_t*)card_table->byte_for_index(start_idx);

        size_t* refinement_cur_word = (size_t*)refinement_table->byte_for_index(start_idx);
        size_t* const refinement_end_word = refinement_cur_word + claim.size() / (sizeof(size_t) / sizeof(G1CardTable::CardValue));

        for (; refinement_cur_word < refinement_end_word; ++refinement_cur_word, ++card_cur_word) {
          size_t value = *refinement_cur_word;
          *refinement_cur_word = G1CardTable::WordAllClean;
          // Dirty is "0", so we need to logically-and here. This is also safe
          // for all other possible values in the card table; at this point this
          // can be either g1_dirty_card or g1_to_cset_card which will both be
          // scanned.
          size_t new_value = *card_cur_word & value;
          *card_cur_word = new_value;
        }
      }

      return false;
    }

  public:
    G1MergeRefinementTableRegionClosure(G1CardTableClaimTable* scan_state) : G1HeapRegionClosure(), _scan_state(scan_state) {
    }
  };

public:
  MergeRefinementTableTask(G1CardTableClaimTable* scan_state, uint max_workers) :
    WorkerTask("Merge Refinement Table"), _scan_state(scan_state), _max_workers(max_workers) {     guarantee(_scan_state != nullptr, "must be");  }

  void work(uint worker_id) override {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    G1GCParPhaseTimesTracker x(g1h->phase_times(), G1GCPhaseTimes::SweepRT, worker_id, false /* allow multiple invocation */);

    G1MergeRefinementTableRegionClosure cl(_scan_state);
    _scan_state->heap_region_iterate_from_worker_offset(&cl, worker_id, _max_workers);
  }
};

class G1MergeHeapRootsTask : public WorkerTask {

  class G1MergeCardSetStats {
    size_t _merged[G1GCPhaseTimes::MergeRSContainersSentinel];

  public:
    G1MergeCardSetStats() {
      for (uint i = 0; i < ARRAY_SIZE(_merged); i++) {
        _merged[i] = 0;
      }
    }

    void inc_card_set_merged(uint tag) {
      assert(tag < ARRAY_SIZE(_merged), "tag out of bounds %u", tag);
      _merged[tag]++;
    }

    void inc_merged_cards(size_t increment = 1) {
      _merged[G1GCPhaseTimes::MergeRSFromRemSetCards] += increment;
    }

    void inc_total_cards(size_t increment = 1) {
      _merged[G1GCPhaseTimes::MergeRSTotalCards] += increment;
    }

    void dec_remset_cards(size_t decrement) {
      _merged[G1GCPhaseTimes::MergeRSTotalCards] -= decrement;
    }

    size_t merged(uint i) const { return _merged[i]; }
  };

  // Visitor for remembered sets. Several methods of it are called by a region's
  // card set iterator to drop card set remembered set entries onto the card.
  // table.
  //
  // We add a small prefetching cache in front of the actual work as dropping
  // onto the card table is basically random memory access. This improves
  // performance of this operation significantly.
  class G1MergeCardSetClosure {
    friend class G1MergeCardSetCache;

    G1RemSetScanState* _scan_state;
    G1CardTable* _ct;

    G1MergeCardSetStats _stats;

    // Cached card table index of the currently processed region to avoid constant
    // recalculation as our remembered set containers are per region.
    size_t _region_base_idx;

    class G1MergeCardSetCache : public G1MergeHeapRootsPrefetchCache<G1CardTable::CardValue> {
      G1MergeCardSetClosure* const _merge_card_cl;

    public:
      G1MergeCardSetCache(G1MergeCardSetClosure* const merge_card_cl) :
        // Initially set dummy card value to Dirty to avoid any actual mark work if we
        // try to process it.
        G1MergeHeapRootsPrefetchCache<G1CardTable::CardValue>(G1CardTable::dirty_card_val()),
        _merge_card_cl(merge_card_cl) { }

      void flush() {
        for (uint i = 0; i < CacheSize; i++) {
          _merge_card_cl->mark_card(push(&_dummy_card));
        }
      }
    } _merge_card_set_cache;

    // Returns whether the region contains cards we need to scan. If so, remember that
    // region in the current set of dirty regions.
    bool remember_if_interesting(uint const region_idx) {
      if (!_scan_state->contains_cards_to_process(region_idx)) {
        return false;
      }
      _scan_state->add_dirty_region(region_idx);
      return true;
    }

    void mark_card(G1CardTable::CardValue* value) {
      if (_ct->mark_clean_as_from_remset(value)) {
        _stats.inc_merged_cards();
      }
      _stats.inc_total_cards();
    }

  public:
    G1MergeCardSetClosure(G1RemSetScanState* scan_state) :
      _scan_state(scan_state),
      _ct(G1CollectedHeap::heap()->card_table()),
      _stats(),
      _region_base_idx(0),
      _merge_card_set_cache(this) { }

    void do_card(uint const card_idx) {
      G1CardTable::CardValue* to_prefetch = _ct->byte_for_index(_region_base_idx + card_idx);
      G1CardTable::CardValue* to_process = _merge_card_set_cache.push(to_prefetch);

      mark_card(to_process);
    }

    // Returns whether the given region actually needs iteration.
    bool start_iterate(uint const tag, uint const region_idx) {
      assert(tag < G1GCPhaseTimes::MergeRSFromRemSetCards, "invalid tag %u", tag);
      if (remember_if_interesting(region_idx)) {
        _region_base_idx = (size_t)region_idx << G1HeapRegion::LogCardsPerRegion;
        _stats.inc_card_set_merged(tag);
        return true;
      }
      return false;
    }

    void do_card_range(uint const start_card_idx, uint const length) {
      size_t cards_changed = _ct->mark_clean_range_as_from_remset(_region_base_idx + start_card_idx, length);
      _stats.inc_merged_cards(cards_changed);
      _stats.inc_total_cards(length);
    }

    G1MergeCardSetStats stats() {
      _merge_card_set_cache.flush();
      // Compensation for the dummy cards that were initially pushed into the
      // card cache.
      // We do not need to compensate for the other counters because the dummy
      // card mark will never update another counter because it is initally "dirty".
      _stats.dec_remset_cards(G1MergeCardSetCache::CacheSize);
      return _stats;
    }
  };

  // Closure to prepare the collection set regions for evacuation failure, i.e. make
  // sure that the mark bitmap is clear for any old region in the collection set.
  //
  // These mark bitmaps record the evacuation failed objects.
  class G1PrepareRegionsForEvacFailClosure : public G1HeapRegionClosure {
    G1CollectedHeap* _g1h;
    G1RemSetScanState* _scan_state;
    bool _initial_evacuation;

    void assert_bitmap_clear(G1HeapRegion* hr, const G1CMBitMap* bitmap) {
      assert(bitmap->get_next_marked_addr(hr->bottom(), hr->end()) == hr->end(),
             "Bitmap should have no mark for region %u (%s)", hr->hrm_index(), hr->get_short_type_str());
    }

    void assert_refinement_table_clear(G1HeapRegion* hr) {
#ifdef ASSERT
      _g1h->refinement_table()->verify_region(MemRegion(hr->bottom(), hr->end()), G1CardTable::clean_card_val(), true);
#endif
    }

    bool should_clear_region(G1HeapRegion* hr) const {
      // The bitmap for young regions must obviously be clear as we never mark through them;
      // old regions that are currently being marked through are only in the collection set
      // after the concurrent cycle completed, so their bitmaps must also be clear except when
      // the pause occurs during the Concurrent Cleanup for Next Mark phase.
      // Only at that point the region's bitmap may contain marks while being in the collection
      // set at the same time.
      return _g1h->collector_state()->clear_bitmap_in_progress() &&
             hr->is_old();
    }

  public:
    G1PrepareRegionsForEvacFailClosure(G1CollectedHeap* g1h, G1RemSetScanState* scan_state, bool initial_evacuation) :
      _g1h(g1h),
      _scan_state(scan_state),
      _initial_evacuation(initial_evacuation)
    { }

    bool do_heap_region(G1HeapRegion* hr) {
      assert(_g1h->is_in_cset(hr), "Should only be used iterating the collection set");

      // Collection set regions after the initial evacuation need their refinement
      // table cleared because
      // * we use the refinement table for recording references to other regions
      // during evacuation failure handling
      // * during previous passes we used the refinement table to contain marks for
      // cross-region references. Now that we evacuate the region, they need to be
      // cleared.
      //
      // We do not need to do this extra work for initial evacuation because we
      // make sure the refinement table is clean for all regions either in
      // concurrent refinement or in the merge refinement table phase earlier.
      if (!_initial_evacuation) {
        hr->clear_refinement_table();
      } else {
        assert_refinement_table_clear(hr);
      }
      // Evacuation failure uses the bitmap to record evacuation failed objects,
      // so the bitmap for the regions in the collection set must be cleared if not already.
      if (should_clear_region(hr)) {
        _g1h->clear_bitmap_for_region(hr);
        _g1h->concurrent_mark()->reset_top_at_mark_start(hr);
      } else {
        assert_bitmap_clear(hr, _g1h->concurrent_mark()->mark_bitmap());
      }
      _g1h->concurrent_mark()->clear_statistics(hr);
      _scan_state->add_all_dirty_region(hr->hrm_index());
      return false;
    }
  };

  // Visitor for the remembered sets of humongous candidate regions to merge their
  // remembered set into the card table.
  class G1FlushHumongousCandidateRemSets : public G1HeapRegionIndexClosure {
    G1MergeCardSetClosure _cl;

  public:
    G1FlushHumongousCandidateRemSets(G1RemSetScanState* scan_state) : _cl(scan_state) { }

    bool do_heap_region_index(uint region_index) override {
      G1CollectedHeap* g1h = G1CollectedHeap::heap();

      if (!g1h->region_attr(region_index).is_humongous_candidate()) {
        return false;
      }

      G1HeapRegion* r = g1h->region_at(region_index);

      assert(r->rem_set()->is_complete(), "humongous candidates must have complete remset");

      guarantee(r->rem_set()->occupancy_less_or_equal_than(G1EagerReclaimRemSetThreshold),
                "Found a not-small remembered set here. This is inconsistent with previous assumptions.");

      if (!r->rem_set()->is_empty()) {
        r->rem_set()->iterate_for_merge(_cl);
        // We should only clear the card based remembered set here as we will not
        // implicitly rebuild anything else during eager reclaim. Note that at the moment
        // (and probably never) we do not enter this path if there are other kind of
        // remembered sets for this region.
        // We want to continue collecting remembered set entries for humongous regions
        // that were not reclaimed.
        r->rem_set()->clear(true /* only_cardset */, true /* keep_tracked */);
      }

      // Postcondition
      assert(r->rem_set()->is_empty(), "must be empty after flushing");
      assert(r->rem_set()->is_complete(), "should still be after flushing");

      return false;
    }

    G1MergeCardSetStats stats() {
      return _cl.stats();
    }
  };

  uint _num_workers;
  G1HeapRegionClaimer _hr_claimer;
  G1RemSetScanState* _scan_state;

  bool _initial_evacuation;

  Atomic<bool> _fast_reclaim_handled;

public:
  G1MergeHeapRootsTask(G1RemSetScanState* scan_state, uint num_workers, bool initial_evacuation) :
    WorkerTask("G1 Merge Heap Roots"),
    _num_workers(num_workers),
    _hr_claimer(num_workers),
    _scan_state(scan_state),
    _initial_evacuation(initial_evacuation),
    _fast_reclaim_handled(false)
  { }

  virtual void work(uint worker_id) {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    G1GCPhaseTimes* p = g1h->phase_times();

    G1GCPhaseTimes::GCParPhases merge_remset_phase = _initial_evacuation ?
                                                     G1GCPhaseTimes::MergeRS :
                                                     G1GCPhaseTimes::OptMergeRS;

    {
      // Merge remset of ...
      G1GCParPhaseTimesTracker x(p, merge_remset_phase, worker_id, !_initial_evacuation /* allow_multiple_record */);

      {
        // 1. eager-reclaim candidates
        if (_initial_evacuation &&
            g1h->has_humongous_reclaim_candidates() &&
            !_fast_reclaim_handled.load_relaxed() &&
            _fast_reclaim_handled.compare_set(false, true)) {

          G1GCParPhaseTimesTracker subphase_x(p, G1GCPhaseTimes::MergeER, worker_id);

          G1FlushHumongousCandidateRemSets cl(_scan_state);
          g1h->heap_region_iterate(&cl);
          G1MergeCardSetStats stats = cl.stats();

          for (uint i = 0; i < G1GCPhaseTimes::MergeRSContainersSentinel; i++) {
            p->record_or_add_thread_work_item(merge_remset_phase, worker_id, stats.merged(i), i);
          }
        }
      }

      {
        // 2. collection set
        G1MergeCardSetClosure merge(_scan_state);

        g1h->collection_set()->merge_cardsets_for_collection_groups(merge, worker_id, _num_workers);

        G1MergeCardSetStats stats = merge.stats();

        for (uint i = 0; i < G1GCPhaseTimes::MergeRSContainersSentinel; i++) {
          p->record_or_add_thread_work_item(merge_remset_phase, worker_id, stats.merged(i), i);
        }
      }
    }

    // Preparation for evacuation failure handling.
    {
      G1PrepareRegionsForEvacFailClosure prepare_evac_failure(g1h, _scan_state, _initial_evacuation);
      g1h->collection_set_iterate_increment_from(&prepare_evac_failure, &_hr_claimer, worker_id);
    }
  }
};

static void merge_refinement_table() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  G1ConcurrentRefineSweepState& state = g1h->concurrent_refine()->sweep_state_for_merge();
  WorkerThreads* workers = g1h->workers();

  MergeRefinementTableTask cl(state.sweep_table(), workers->active_workers());
  log_debug(gc, ergo)("Running %s using %u workers", cl.name(), workers->active_workers());
  workers->run_task(&cl);
}

void G1RemSet::merge_heap_roots(bool initial_evacuation) {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1GCPhaseTimes* pt = g1h->phase_times();

  // 1. Prepare the merging process
  {
    Ticks start = Ticks::now();

    _scan_state->prepare_for_merge_heap_roots();

    Tickspan total = Ticks::now() - start;
    if (initial_evacuation) {
      pt->record_prepare_merge_heap_roots_time(total.seconds() * 1000.0);
    } else {
      pt->record_or_add_optional_prepare_merge_heap_roots_time(total.seconds() * 1000.0);
    }
  }

  // 2. (Optionally) Merge the refinement table into the card table (if needed).
  G1ConcurrentRefineSweepState& state = g1h->concurrent_refine()->sweep_state();
  if (initial_evacuation && state.is_in_progress()) {
    Ticks start = Ticks::now();

    merge_refinement_table();

    g1h->phase_times()->record_merge_refinement_table_time((Ticks::now() - start).seconds() * MILLIUNITS);
  }

  // 3. Merge other heap roots.
  Ticks start = Ticks::now();

  {
    WorkerThreads* workers = g1h->workers();

    size_t const increment_length = g1h->collection_set()->groups_increment_length();

    uint const num_workers = initial_evacuation ? workers->active_workers() :
                                                  MIN2(workers->active_workers(), (uint)increment_length);

    G1MergeHeapRootsTask cl(_scan_state, num_workers, initial_evacuation);
    log_debug(gc, ergo)("Running %s using %u workers for %zu regions",
                        cl.name(), num_workers, increment_length);
    workers->run_task(&cl, num_workers);
  }

  if (initial_evacuation) {
    pt->record_merge_heap_roots_time((Ticks::now() - start).seconds() * 1000.0);
  } else {
    pt->record_or_add_optional_merge_heap_roots_time((Ticks::now() - start).seconds() * 1000.0);
  }

  if (VerifyDuringGC && initial_evacuation) {
    g1h->verifier()->verify_card_tables_clean(false /* both_card_tables */);
  }
}

void G1RemSet::complete_evac_phase(bool has_more_than_one_evacuation_phase) {
  _scan_state->complete_evac_phase(has_more_than_one_evacuation_phase);
}

void G1RemSet::exclude_region_from_scan(uint region_idx) {
  _scan_state->clear_scan_top(region_idx);
}

G1AbstractSubTask* G1RemSet::create_cleanup_after_scan_heap_roots_task() {
  return _scan_state->create_cleanup_after_scan_heap_roots_task();
}

void G1RemSet::print_coarsen_stats() {
  LogTarget(Debug, gc, remset) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);

    G1CardSet::print_coarsen_stats(&ls);
  }
}

inline void check_card_ptr(CardTable::CardValue* card_ptr, G1CardTable* ct) {
#ifdef ASSERT
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  assert(g1h->is_in(ct->addr_for(card_ptr)),
         "Card at " PTR_FORMAT " index %zu representing heap at " PTR_FORMAT " (%u) must be in committed heap",
         p2i(card_ptr),
         ct->index_for(ct->addr_for(card_ptr)),
         p2i(ct->addr_for(card_ptr)),
         g1h->addr_to_region(ct->addr_for(card_ptr)));
#endif
}

G1RemSet::RefineResult G1RemSet::refine_card_concurrently(CardValue* const card_ptr,
                                                          const uint worker_id) {
  assert(!_g1h->is_stw_gc_active(), "Only call concurrently");
  G1CardTable* ct = _g1h->refinement_table();
  check_card_ptr(card_ptr, ct);

  // That card is already known to contain a reference to the collection set. Skip
  // further processing.
  if (*card_ptr == G1CardTable::g1_to_cset_card) {
    return AlreadyToCSet;
  }

  // Construct the MemRegion representing the card.
  HeapWord* start = ct->addr_for(card_ptr);
  // And find the region containing it.
  G1HeapRegion* r = _g1h->heap_region_containing(start);
  // This reload of the top is safe even though it happens after the full
  // fence, because top is stable for old and unfiltered humongous
  // regions, so it must return the same value as the previous load when
  // cleaning the card. Also cleaning the card and refinement of the card
  // cannot span across safepoint, so we don't need to worry about top being
  // changed during safepoint.
  HeapWord* scan_limit = r->top();
  assert(scan_limit > start, "sanity region %u (%s) scan_limit " PTR_FORMAT " start " PTR_FORMAT, r->hrm_index(), r->get_short_type_str(), p2i(scan_limit), p2i(start));

  // Don't use addr_for(card_ptr + 1) which can ask for
  // a card beyond the heap.
  HeapWord* end = start + G1CardTable::card_size_in_words();
  MemRegion dirty_region(start, MIN2(scan_limit, end));
  assert(!dirty_region.is_empty(), "sanity");

  G1ConcurrentRefineOopClosure conc_refine_cl(_g1h, worker_id);
  if (r->oops_on_memregion_seq_iterate_careful<false>(dirty_region, &conc_refine_cl) != nullptr) {
    if (conc_refine_cl.has_ref_to_cset()) {
      return HasRefToCSet;
    } else if (conc_refine_cl.has_ref_to_old()) {
      return HasRefToOld;
    } else {
      return NoCrossRegion;
    }
  }
  // If unable to process the card then we encountered an unparsable
  // part of the heap (e.g. a partially allocated object, so only
  // temporarily a problem) while processing a stale card.  Despite
  // the card being stale, we can't simply ignore it, because we've
  // already marked the card as cleaned, so taken responsibility for
  // ensuring the card gets scanned.
  return CouldNotParse;
}

void G1RemSet::print_periodic_summary_info(const char* header, uint period_count, bool show_thread_times) {
  if ((G1SummarizeRSetStatsPeriod > 0) && log_is_enabled(Trace, gc, remset) &&
      (period_count % G1SummarizeRSetStatsPeriod == 0)) {

    G1RemSetSummary current;
    _prev_period_summary.subtract_from(&current);

    Log(gc, remset) log;
    log.trace("%s", header);
    LogStream ls(log.trace());
    _prev_period_summary.print_on(&ls, show_thread_times);

    _prev_period_summary.set(&current);
  }
}

void G1RemSet::print_summary_info() {
  Log(gc, remset, exit) log;
  if (log.is_trace()) {
    log.trace(" Cumulative RS summary");
    G1RemSetSummary current;
    LogStream ls(log.trace());
    current.print_on(&ls, true /* show_thread_times*/);
  }
}
