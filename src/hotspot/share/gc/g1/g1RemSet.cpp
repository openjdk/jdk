/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1BatchedTask.hpp"
#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1CardSet.inline.hpp"
#include "gc/g1/g1CardTable.inline.hpp"
#include "gc/g1/g1CardTableEntryClosure.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1DirtyCardQueue.hpp"
#include "gc/g1/g1FromCardCache.hpp"
#include "gc/g1/g1GCParPhaseTimesTracker.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1RootClosures.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1_globals.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionManager.inline.hpp"
#include "gc/g1/heapRegionRemSet.inline.hpp"
#include "gc/shared/bufferNodeList.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/ptrQueue.hpp"
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
// scanned (log buffers, remembered sets) into a single data structure to remove
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

  size_t _max_reserved_regions;

  // Has this region that is part of the regions in the collection set been processed yet.
  typedef bool G1RemsetIterState;

  G1RemsetIterState volatile* _collection_set_iter_state;

  // Card table iteration claim for each heap region, from 0 (completely unscanned)
  // to (>=) HeapRegion::CardsPerRegion (completely scanned).
  uint volatile* _card_table_scan_state;

  uint _scan_chunks_per_region;         // Number of chunks per region.
  uint8_t _log_scan_chunks_per_region;  // Log of number of chunks per region.
  bool* _region_scan_chunks;
  size_t _num_total_scan_chunks;        // Total number of elements in _region_scan_chunks.
  uint8_t _scan_chunks_shift;           // For conversion between card index and chunk index.
public:
  uint scan_chunk_size_in_cards() const { return (uint)1 << _scan_chunks_shift; }

  // Returns whether the chunk corresponding to the given region/card in region contain a
  // dirty card, i.e. actually needs scanning.
  bool chunk_needs_scan(uint const region_idx, uint const card_in_region) const {
    size_t const idx = ((size_t)region_idx << _log_scan_chunks_per_region) + (card_in_region >> _scan_chunks_shift);
    assert(idx < _num_total_scan_chunks, "Index " SIZE_FORMAT " out of bounds " SIZE_FORMAT,
           idx, _num_total_scan_chunks);
    return _region_scan_chunks[idx];
  }

private:
  // The complete set of regions which card table needs to be cleared at the end
  // of GC because we scribbled over these card tables.
  //
  // Regions may be added for two reasons:
  // - they were part of the collection set: they may contain g1_young_card_val
  // or regular card marks that we never scan so we must always clear their card
  // table
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
    uint _cur_idx;
    size_t _max_reserved_regions;

    bool* _contains;

  public:
    G1DirtyRegions(size_t max_reserved_regions) :
      _buffer(NEW_C_HEAP_ARRAY(uint, max_reserved_regions, mtGC)),
      _cur_idx(0),
      _max_reserved_regions(max_reserved_regions),
      _contains(NEW_C_HEAP_ARRAY(bool, max_reserved_regions, mtGC)) {

      reset();
    }

    static size_t chunk_size() { return M; }

    ~G1DirtyRegions() {
      FREE_C_HEAP_ARRAY(uint, _buffer);
      FREE_C_HEAP_ARRAY(bool, _contains);
    }

    void reset() {
      _cur_idx = 0;
      ::memset(_contains, false, _max_reserved_regions * sizeof(bool));
    }

    uint size() const { return _cur_idx; }

    uint at(uint idx) const {
      assert(idx < _cur_idx, "Index %u beyond valid regions", idx);
      return _buffer[idx];
    }

    void add_dirty_region(uint region) {
      if (_contains[region]) {
        return;
      }

      bool marked_as_dirty = Atomic::cmpxchg(&_contains[region], false, true) == false;
      if (marked_as_dirty) {
        uint allocated = Atomic::fetch_then_add(&_cur_idx, 1u);
        _buffer[allocated] = region;
      }
    }

    // Creates the union of this and the other G1DirtyRegions.
    void merge(const G1DirtyRegions* other) {
      for (uint i = 0; i < other->size(); i++) {
        uint region = other->at(i);
        if (!_contains[region]) {
          _buffer[_cur_idx++] = region;
          _contains[region] = true;
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
    uint volatile _cur_dirty_regions;

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

      double num_cards = num_regions << HeapRegion::LogCardsPerRegion;
      return ceil(num_cards / num_cards_per_worker);
    }

    virtual ~G1ClearCardTableTask() {
      _scan_state->cleanup();
#ifndef PRODUCT
      G1CollectedHeap::heap()->verifier()->verify_card_table_cleanup();
#endif
    }

    void do_work(uint worker_id) override {
      const uint num_regions_per_worker = num_cards_per_worker / (uint)HeapRegion::CardsPerRegion;

      while (_cur_dirty_regions < _regions->size()) {
        uint next = Atomic::fetch_then_add(&_cur_dirty_regions, num_regions_per_worker);
        uint max = MIN2(next + num_regions_per_worker, _regions->size());

        for (uint i = next; i < max; i++) {
          HeapRegion* r = _g1h->region_at(_regions->at(i));
          r->clear_cardtable();
        }
      }
    }
  };

public:
  G1RemSetScanState() :
    _max_reserved_regions(0),
    _card_table_scan_state(nullptr),
    _scan_chunks_per_region(G1CollectedHeap::get_chunks_per_region()),
    _log_scan_chunks_per_region(log2i(_scan_chunks_per_region)),
    _region_scan_chunks(nullptr),
    _num_total_scan_chunks(0),
    _scan_chunks_shift(0),
    _all_dirty_regions(nullptr),
    _next_dirty_regions(nullptr),
    _scan_top(nullptr) {
  }

  ~G1RemSetScanState() {
    FREE_C_HEAP_ARRAY(uint, _card_table_scan_state);
    FREE_C_HEAP_ARRAY(bool, _region_scan_chunks);
    FREE_C_HEAP_ARRAY(HeapWord*, _scan_top);
  }

  void initialize(size_t max_reserved_regions) {
    assert(_card_table_scan_state == nullptr, "Must not be initialized twice");
    _max_reserved_regions = max_reserved_regions;
    _card_table_scan_state = NEW_C_HEAP_ARRAY(uint, max_reserved_regions, mtGC);
    _num_total_scan_chunks = max_reserved_regions * _scan_chunks_per_region;
    _region_scan_chunks = NEW_C_HEAP_ARRAY(bool, _num_total_scan_chunks, mtGC);

    _scan_chunks_shift = (uint8_t)log2i(HeapRegion::CardsPerRegion / _scan_chunks_per_region);
    _scan_top = NEW_C_HEAP_ARRAY(HeapWord*, max_reserved_regions, mtGC);
  }

  void prepare() {
    // Reset the claim and clear scan top for all regions, including
    // regions currently not available or free. Since regions might
    // become used during the collection these values must be valid
    // for those regions as well.
    for (size_t i = 0; i < _max_reserved_regions; i++) {
      clear_scan_top((uint)i);
    }

    _all_dirty_regions = new G1DirtyRegions(_max_reserved_regions);
    _next_dirty_regions = new G1DirtyRegions(_max_reserved_regions);
  }

  void prepare_for_merge_heap_roots() {
    assert(_next_dirty_regions->size() == 0, "next dirty regions must be empty");

    for (size_t i = 0; i < _max_reserved_regions; i++) {
      _card_table_scan_state[i] = 0;
    }

    ::memset(_region_scan_chunks, false, _num_total_scan_chunks * sizeof(*_region_scan_chunks));
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
    HeapRegion* hr = G1CollectedHeap::heap()->region_at_or_null(region_idx);
    return (hr != nullptr && !hr->in_collection_set() && hr->is_old_or_humongous());
  }

  size_t num_visited_cards() const {
    size_t result = 0;
    for (uint i = 0; i < _num_total_scan_chunks; i++) {
      if (_region_scan_chunks[i]) {
        result++;
      }
    }
    return result * (HeapRegion::CardsPerRegion / _scan_chunks_per_region);
  }

  size_t num_cards_in_dirty_regions() const {
    return _next_dirty_regions->size() * HeapRegion::CardsPerRegion;
  }

  void set_chunk_range_dirty(size_t const region_card_idx, size_t const card_length) {
    size_t chunk_idx = region_card_idx >> _scan_chunks_shift;
    // Make sure that all chunks that contain the range are marked. Calculate the
    // chunk of the last card that is actually marked.
    size_t const end_chunk = (region_card_idx + card_length - 1) >> _scan_chunks_shift;
    for (; chunk_idx <= end_chunk; chunk_idx++) {
      _region_scan_chunks[chunk_idx] = true;
    }
  }

  void set_chunk_dirty(size_t const card_idx) {
    assert((card_idx >> _scan_chunks_shift) < _num_total_scan_chunks,
           "Trying to access index " SIZE_FORMAT " out of bounds " SIZE_FORMAT,
           card_idx >> _scan_chunks_shift, _num_total_scan_chunks);
    size_t const chunk_idx = card_idx >> _scan_chunks_shift;
    _region_scan_chunks[chunk_idx] = true;
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

  void iterate_dirty_regions_from(HeapRegionClosure* cl, uint worker_id) {
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
    assert(region < _max_reserved_regions, "Tried to access invalid region %u", region);
    return _card_table_scan_state[region] < HeapRegion::CardsPerRegion;
  }

  uint claim_cards_to_scan(uint region, uint increment) {
    assert(region < _max_reserved_regions, "Tried to access invalid region %u", region);
    return Atomic::fetch_then_add(&_card_table_scan_state[region], increment, memory_order_relaxed);
  }

  void add_dirty_region(uint const region) {
#ifdef ASSERT
   HeapRegion* hr = G1CollectedHeap::heap()->region_at(region);
   assert(!hr->in_collection_set() && hr->is_old_or_humongous(),
          "Region %u is not suitable for scanning, is %sin collection set or %s",
          hr->hrm_index(), hr->in_collection_set() ? "" : "not ", hr->get_short_type_str());
#endif
    _next_dirty_regions->add_dirty_region(region);
  }

  void add_all_dirty_region(uint region) {
#ifdef ASSERT
    HeapRegion* hr = G1CollectedHeap::heap()->region_at(region);
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
};

G1RemSet::G1RemSet(G1CollectedHeap* g1h,
                   G1CardTable* ct) :
  _scan_state(new G1RemSetScanState()),
  _prev_period_summary(false),
  _g1h(g1h),
  _ct(ct),
  _g1p(_g1h->policy()) {
}

G1RemSet::~G1RemSet() {
  delete _scan_state;
}

void G1RemSet::initialize(uint max_reserved_regions) {
  _scan_state->initialize(max_reserved_regions);
}

// Helper class to claim dirty chunks within the card table.
class G1CardTableChunkClaimer {
  G1RemSetScanState* _scan_state;
  uint _region_idx;
  uint _cur_claim;

public:
  G1CardTableChunkClaimer(G1RemSetScanState* scan_state, uint region_idx) :
    _scan_state(scan_state),
    _region_idx(region_idx),
    _cur_claim(0) {
    guarantee(size() <= HeapRegion::CardsPerRegion, "Should not claim more space than possible.");
  }

  bool has_next() {
    while (true) {
      _cur_claim = _scan_state->claim_cards_to_scan(_region_idx, size());
      if (_cur_claim >= HeapRegion::CardsPerRegion) {
        return false;
      }
      if (_scan_state->chunk_needs_scan(_region_idx, _cur_claim)) {
        return true;
      }
    }
  }

  uint value() const { return _cur_claim; }
  uint size() const { return _scan_state->scan_chunk_size_in_cards(); }
};

// Scans a heap region for dirty cards.
class G1ScanHRForRegionClosure : public HeapRegionClosure {
  using CardValue = CardTable::CardValue;

  G1CollectedHeap* _g1h;
  G1CardTable* _ct;

  G1ParScanThreadState* _pss;

  G1RemSetScanState* _scan_state;

  G1GCPhaseTimes::GCParPhases _phase;

  uint   _worker_id;

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

  HeapWord* scan_memregion(uint region_idx_for_card, MemRegion mr) {
    HeapRegion* const card_region = _g1h->region_at(region_idx_for_card);
    G1ScanCardClosure card_cl(_g1h, _pss, _heap_roots_found);

    HeapWord* const scanned_to = card_region->oops_on_memregion_seq_iterate_careful<true>(mr, &card_cl);
    assert(scanned_to != nullptr, "Should be able to scan range");
    assert(scanned_to >= mr.end(), "Scanned to " PTR_FORMAT " less than range " PTR_FORMAT, p2i(scanned_to), p2i(mr.end()));

    _pss->trim_queue_partially();
    return scanned_to;
  }

  void do_claimed_block(uint const region_idx, CardValue* const dirty_l, CardValue* const dirty_r) {
    _ct->change_dirty_cards_to(dirty_l, dirty_r, _scanned_card_value);
    size_t num_cards = pointer_delta(dirty_r, dirty_l, sizeof(CardValue));
    _blocks_scanned++;

    HeapWord* const card_start = _ct->addr_for(dirty_l);
    HeapWord* const top = _scan_state->scan_top(region_idx);
    if (card_start >= top) {
      return;
    }

    HeapWord* scan_end = MIN2(card_start + (num_cards << BOTConstants::log_card_size_in_words()), top);
    if (_scanned_to >= scan_end) {
      return;
    }
    MemRegion mr(MAX2(card_start, _scanned_to), scan_end);
    _scanned_to = scan_memregion(region_idx, mr);

    _cards_scanned += num_cards;
  }

  // To locate consecutive dirty cards inside a chunk.
  class ChunkScanner {
    using Word = size_t;

    CardValue* const _start_card;
    CardValue* const _end_card;

    static const size_t ExpandedToScanMask = G1CardTable::WordAlreadyScanned;
    static const size_t ToScanMask = G1CardTable::g1_card_already_scanned;

    static bool is_card_dirty(const CardValue* const card) {
      return (*card & ToScanMask) == 0;
    }

    static bool is_word_aligned(const void* const addr) {
      return ((uintptr_t)addr) % sizeof(Word) == 0;
    }

    CardValue* find_first_dirty_card(CardValue* i_card) const {
      while (!is_word_aligned(i_card)) {
        if (is_card_dirty(i_card)) {
          return i_card;
        }
        i_card++;
      }

      for (/* empty */; i_card < _end_card; i_card += sizeof(Word)) {
        Word word_value = *reinterpret_cast<Word*>(i_card);
        bool has_dirty_cards_in_word = (~word_value & ExpandedToScanMask) != 0;

        if (has_dirty_cards_in_word) {
          for (uint i = 0; i < sizeof(Word); ++i) {
            if (is_card_dirty(i_card)) {
              return i_card;
            }
            i_card++;
          }
          assert(false, "should have early-returned");
        }
      }

      return _end_card;
    }

    CardValue* find_first_non_dirty_card(CardValue* i_card) const {
      while (!is_word_aligned(i_card)) {
        if (!is_card_dirty(i_card)) {
          return i_card;
        }
        i_card++;
      }

      for (/* empty */; i_card < _end_card; i_card += sizeof(Word)) {
        Word word_value = *reinterpret_cast<Word*>(i_card);
        bool all_cards_dirty = (word_value == G1CardTable::WordAllDirty);

        if (!all_cards_dirty) {
          for (uint i = 0; i < sizeof(Word); ++i) {
            if (!is_card_dirty(i_card)) {
              return i_card;
            }
            i_card++;
          }
          assert(false, "should have early-returned");
        }
      }

      return _end_card;
    }

  public:
    ChunkScanner(CardValue* const start_card, CardValue* const end_card) :
      _start_card(start_card),
      _end_card(end_card) {
        assert(is_word_aligned(start_card), "precondition");
        assert(is_word_aligned(end_card), "precondition");
      }

    template<typename Func>
    void on_dirty_cards(Func&& f) {
      for (CardValue* cur_card = _start_card; cur_card < _end_card; /* empty */) {
        CardValue* dirty_l = find_first_dirty_card(cur_card);
        CardValue* dirty_r = find_first_non_dirty_card(dirty_l);

        assert(dirty_l <= dirty_r, "inv");

        if (dirty_l == dirty_r) {
          assert(dirty_r == _end_card, "finished the entire chunk");
          return;
        }

        f(dirty_l, dirty_r);

        cur_card = dirty_r + 1;
      }
    }
  };

  void scan_heap_roots(HeapRegion* r) {
    uint const region_idx = r->hrm_index();

    ResourceMark rm;

    G1CardTableChunkClaimer claim(_scan_state, region_idx);

    // Set the current scan "finger" to null for every heap region to scan. Since
    // the claim value is monotonically increasing, the check to not scan below this
    // will filter out objects spanning chunks within the region too then, as opposed
    // to resetting this value for every claim.
    _scanned_to = nullptr;

    while (claim.has_next()) {
      _chunks_claimed++;

      size_t const region_card_base_idx = ((size_t)region_idx << HeapRegion::LogCardsPerRegion) + claim.value();

      CardValue* const start_card = _ct->byte_for_index(region_card_base_idx);
      CardValue* const end_card = start_card + claim.size();

      ChunkScanner chunk_scanner{start_card, end_card};
      chunk_scanner.on_dirty_cards([&] (CardValue* dirty_l, CardValue* dirty_r) {
                                     do_claimed_block(region_idx, dirty_l, dirty_r);
                                   });
    }
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

  bool do_heap_region(HeapRegion* r) {
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
  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.cards_scanned(), G1GCPhaseTimes::ScanHRScannedCards);
  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.blocks_scanned(), G1GCPhaseTimes::ScanHRScannedBlocks);
  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.chunks_claimed(), G1GCPhaseTimes::ScanHRClaimedChunks);
  p->record_or_add_thread_work_item(scan_phase, worker_id, cl.heap_roots_found(), G1GCPhaseTimes::ScanHRFoundRoots);
}

// Wrapper around a CodeBlobClosure to count the number of code blobs scanned.
class G1ScanAndCountCodeBlobClosure : public CodeBlobClosure {
  CodeBlobClosure* _cl;
  size_t _count;

public:
  G1ScanAndCountCodeBlobClosure(CodeBlobClosure* cl) : _cl(cl), _count(0) {
  }

  void do_code_blob(CodeBlob* cb) override {
    _cl->do_code_blob(cb);
    _count++;
  }

  size_t count() const {
    return _count;
  }
};

// Heap region closure to be applied to all regions in the current collection set
// increment to fix up non-card related roots.
class G1ScanCollectionSetRegionClosure : public HeapRegionClosure {
  G1ParScanThreadState* _pss;
  G1RemSetScanState* _scan_state;

  G1GCPhaseTimes::GCParPhases _scan_phase;
  G1GCPhaseTimes::GCParPhases _code_roots_phase;

  uint _worker_id;

  size_t _code_roots_scanned;

  size_t _opt_roots_scanned;
  size_t _opt_refs_scanned;
  size_t _opt_refs_memory_used;

  Tickspan _code_root_scan_time;
  Tickspan _code_trim_partially_time;

  Tickspan _rem_set_opt_root_scan_time;
  Tickspan _rem_set_opt_trim_partially_time;

  void scan_opt_rem_set_roots(HeapRegion* r) {
    G1OopStarChunkedList* opt_rem_set_list = _pss->oops_into_optional_region(r);

    G1ScanCardClosure scan_cl(G1CollectedHeap::heap(), _pss, _opt_roots_scanned);
    G1ScanRSForOptionalClosure cl(G1CollectedHeap::heap(), &scan_cl);
    _opt_refs_scanned += opt_rem_set_list->oops_do(&cl, _pss->closures()->strong_oops());
    _opt_refs_memory_used += opt_rem_set_list->used_memory();
  }

public:
  G1ScanCollectionSetRegionClosure(G1RemSetScanState* scan_state,
                                   G1ParScanThreadState* pss,
                                   uint worker_id,
                                   G1GCPhaseTimes::GCParPhases scan_phase,
                                   G1GCPhaseTimes::GCParPhases code_roots_phase) :
    _pss(pss),
    _scan_state(scan_state),
    _scan_phase(scan_phase),
    _code_roots_phase(code_roots_phase),
    _worker_id(worker_id),
    _code_roots_scanned(0),
    _opt_roots_scanned(0),
    _opt_refs_scanned(0),
    _opt_refs_memory_used(0),
    _code_root_scan_time(),
    _code_trim_partially_time(),
    _rem_set_opt_root_scan_time(),
    _rem_set_opt_trim_partially_time() { }

  bool do_heap_region(HeapRegion* r) {
    // The individual references for the optional remembered set are per-worker, so we
    // always need to scan them.
    if (r->has_index_in_opt_cset()) {
      EventGCPhaseParallel event;
      G1EvacPhaseWithTrimTimeTracker timer(_pss, _rem_set_opt_root_scan_time, _rem_set_opt_trim_partially_time);
      scan_opt_rem_set_roots(r);

      event.commit(GCId::current(), _worker_id, G1GCPhaseTimes::phase_name(_scan_phase));
    }

    // Scan code root remembered sets.
    {
      EventGCPhaseParallel event;
      G1EvacPhaseWithTrimTimeTracker timer(_pss, _code_root_scan_time, _code_trim_partially_time);
      G1ScanAndCountCodeBlobClosure cl(_pss->closures()->weak_codeblobs());

      // Scan the code root list attached to the current region
      r->code_roots_do(&cl);

      _code_roots_scanned += cl.count();

      event.commit(GCId::current(), _worker_id, G1GCPhaseTimes::phase_name(_code_roots_phase));
    }

    return false;
  }

  Tickspan code_root_scan_time() const { return _code_root_scan_time;  }
  Tickspan code_root_trim_partially_time() const { return _code_trim_partially_time; }

  size_t code_roots_scanned() const { return _code_roots_scanned; }

  Tickspan rem_set_opt_root_scan_time() const { return _rem_set_opt_root_scan_time; }
  Tickspan rem_set_opt_trim_partially_time() const { return _rem_set_opt_trim_partially_time; }

  size_t opt_roots_scanned() const { return _opt_roots_scanned; }
  size_t opt_refs_scanned() const { return _opt_refs_scanned; }
  size_t opt_refs_memory_used() const { return _opt_refs_memory_used; }
};

void G1RemSet::scan_collection_set_regions(G1ParScanThreadState* pss,
                                           uint worker_id,
                                           G1GCPhaseTimes::GCParPhases scan_phase,
                                           G1GCPhaseTimes::GCParPhases coderoots_phase,
                                           G1GCPhaseTimes::GCParPhases objcopy_phase) {
  G1ScanCollectionSetRegionClosure cl(_scan_state, pss, worker_id, scan_phase, coderoots_phase);
  _g1h->collection_set_iterate_increment_from(&cl, worker_id);

  G1GCPhaseTimes* p = _g1h->phase_times();

  p->record_or_add_time_secs(scan_phase, worker_id, cl.rem_set_opt_root_scan_time().seconds());
  p->record_or_add_time_secs(scan_phase, worker_id, cl.rem_set_opt_trim_partially_time().seconds());

  p->record_or_add_time_secs(coderoots_phase, worker_id, cl.code_root_scan_time().seconds());
  p->record_or_add_thread_work_item(coderoots_phase, worker_id, cl.code_roots_scanned(), G1GCPhaseTimes::CodeRootsScannedNMethods);

  p->add_time_secs(objcopy_phase, worker_id, cl.code_root_trim_partially_time().seconds());

  // At this time we record some metrics only for the evacuations after the initial one.
  if (scan_phase == G1GCPhaseTimes::OptScanHR) {
    p->record_or_add_thread_work_item(scan_phase, worker_id, cl.opt_roots_scanned(), G1GCPhaseTimes::ScanHRFoundRoots);
    p->record_or_add_thread_work_item(scan_phase, worker_id, cl.opt_refs_scanned(), G1GCPhaseTimes::ScanHRScannedOptRefs);
    p->record_or_add_thread_work_item(scan_phase, worker_id, cl.opt_refs_memory_used(), G1GCPhaseTimes::ScanHRUsedMemory);
  }
}

#ifdef ASSERT
void G1RemSet::assert_scan_top_is_null(uint hrm_index) {
  assert(_scan_state->scan_top(hrm_index) == nullptr,
         "scan_top of region %u is unexpectedly " PTR_FORMAT,
         hrm_index, p2i(_scan_state->scan_top(hrm_index)));
}
#endif

void G1RemSet::prepare_region_for_scan(HeapRegion* r) {
  uint hrm_index = r->hrm_index();

  r->prepare_remset_for_scan();

  // Only update non-collection set old regions, others must have already been set
  // to null (don't scan) in the initialization.
  if (r->in_collection_set()) {
    assert_scan_top_is_null(hrm_index);
  } else if (r->is_old_or_humongous()) {
    _scan_state->set_scan_top(hrm_index, r->top());
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

    void inc_remset_cards(size_t increment = 1) {
      _merged[G1GCPhaseTimes::MergeRSCards] += increment;
    }

    size_t merged(uint i) const { return _merged[i]; }
  };

  // Visitor for remembered sets. Several methods of it are called by a region's
  // card set iterator to drop card set remembered set entries onto the card.
  // table. This is in addition to being the HeapRegionClosure to iterate over
  // all region's remembered sets.
  //
  // We add a small prefetching cache in front of the actual work as dropping
  // onto the card table is basically random memory access. This improves
  // performance of this operation significantly.
  class G1MergeCardSetClosure : public HeapRegionClosure {
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
      if (_ct->mark_clean_as_dirty(value)) {
        _scan_state->set_chunk_dirty(_ct->index_for_cardvalue(value));
      }
      _stats.inc_remset_cards();
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
      assert(tag < G1GCPhaseTimes::MergeRSCards, "invalid tag %u", tag);
      if (remember_if_interesting(region_idx)) {
        _region_base_idx = (size_t)region_idx << HeapRegion::LogCardsPerRegion;
        _stats.inc_card_set_merged(tag);
        return true;
      }
      return false;
    }

    void do_card_range(uint const start_card_idx, uint const length) {
      _ct->mark_range_dirty(_region_base_idx + start_card_idx, length);
      _stats.inc_remset_cards(length);
      _scan_state->set_chunk_range_dirty(_region_base_idx + start_card_idx, length);
    }

    // Helper to merge the cards in the card set for the given region onto the card
    // table.
    //
    // Called directly for humongous starts regions because we should not add
    // humongous eager reclaim candidates to the "all" list of regions to
    // clear the card table by default as we do not know yet whether this region
    // will be reclaimed (and reused).
    // If the humongous region contains dirty cards, g1 will scan them
    // because dumping the remembered set entries onto the card table will add
    // the humongous region to the "dirty" region list to scan. Then scanning
    // either clears the card during scan (if there is only an initial evacuation
    // pass) or the "dirty" list will be merged with the "all" list later otherwise.
    // (And there is no problem either way if the region does not contain dirty
    // cards).
    void merge_card_set_for_region(HeapRegion* r) {
      assert(r->in_collection_set() || r->is_starts_humongous(), "must be");

      HeapRegionRemSet* rem_set = r->rem_set();
      if (!rem_set->is_empty()) {
        rem_set->iterate_for_merge(*this);
      }
    }

    virtual bool do_heap_region(HeapRegion* r) {
      assert(r->in_collection_set(), "must be");

      _scan_state->add_all_dirty_region(r->hrm_index());
      merge_card_set_for_region(r);

      return false;
    }

    G1MergeCardSetStats stats() {
      _merge_card_set_cache.flush();
      return _stats;
    }
  };

  // Closure to make sure that the marking bitmap is clear for any old region in
  // the collection set.
  // This is needed to be able to use the bitmap for evacuation failure handling.
  class G1ClearBitmapClosure : public HeapRegionClosure {
    G1CollectedHeap* _g1h;

    void assert_bitmap_clear(HeapRegion* hr, const G1CMBitMap* bitmap) {
      assert(bitmap->get_next_marked_addr(hr->bottom(), hr->end()) == hr->end(),
             "Bitmap should have no mark for region %u (%s)", hr->hrm_index(), hr->get_short_type_str());
    }

    bool should_clear_region(HeapRegion* hr) const {
      // The bitmap for young regions must obviously be clear as we never mark through them;
      // old regions that are currently being marked through are only in the collection set
      // after the concurrent cycle completed, so their bitmaps must also be clear except when
      // the pause occurs during the Concurrent Cleanup for Next Mark phase.
      // Only at that point the region's bitmap may contain marks while being in the collection
      // set at the same time.
      //
      // There is one exception: shutdown might have aborted the Concurrent Cleanup for Next
      // Mark phase midway, which might have also left stale marks in old generation regions.
      // There might actually have been scheduled multiple collections, but at that point we do
      // not care that much about performance and just do the work multiple times if needed.
      return (_g1h->collector_state()->clearing_bitmap() ||
              _g1h->concurrent_mark_is_terminating()) &&
              hr->is_old();
    }

  public:
    G1ClearBitmapClosure(G1CollectedHeap* g1h) : _g1h(g1h) { }

    bool do_heap_region(HeapRegion* hr) {
      assert(_g1h->is_in_cset(hr), "Should only be used iterating the collection set");

      // Evacuation failure uses the bitmap to record evacuation failed objects,
      // so the bitmap for the regions in the collection set must be cleared if not already.
      if (should_clear_region(hr)) {
        _g1h->clear_bitmap_for_region(hr);
        hr->reset_top_at_mark_start();
      } else {
        assert_bitmap_clear(hr, _g1h->concurrent_mark()->mark_bitmap());
      }
      _g1h->concurrent_mark()->clear_statistics(hr);
      return false;
    }
  };

  // Helper to allow two closure to be applied when
  // iterating through the collection set.
  class G1CombinedClosure : public HeapRegionClosure {
    HeapRegionClosure* _closure1;
    HeapRegionClosure* _closure2;
  public:
    G1CombinedClosure(HeapRegionClosure* cl1, HeapRegionClosure* cl2) :
      _closure1(cl1),
      _closure2(cl2) { }

    bool do_heap_region(HeapRegion* hr) {
      return _closure1->do_heap_region(hr) ||
             _closure2->do_heap_region(hr);
    }
  };

  // Visitor for the remembered sets of humongous candidate regions to merge their
  // remembered set into the card table.
  class G1FlushHumongousCandidateRemSets : public HeapRegionIndexClosure {
    G1MergeCardSetClosure _cl;

  public:
    G1FlushHumongousCandidateRemSets(G1RemSetScanState* scan_state) : _cl(scan_state) { }

    bool do_heap_region_index(uint region_index) override {
      G1CollectedHeap* g1h = G1CollectedHeap::heap();

      if (!g1h->region_attr(region_index).is_humongous_candidate()) {
        return false;
      }

      HeapRegion* r = g1h->region_at(region_index);
      if (r->rem_set()->is_empty()) {
        return false;
      }

      guarantee(r->rem_set()->occupancy_less_or_equal_than(G1EagerReclaimRemSetThreshold),
                "Found a not-small remembered set here. This is inconsistent with previous assumptions.");

      _cl.merge_card_set_for_region(r);

      // We should only clear the card based remembered set here as we will not
      // implicitly rebuild anything else during eager reclaim. Note that at the moment
      // (and probably never) we do not enter this path if there are other kind of
      // remembered sets for this region.
      // We want to continue collecting remembered set entries for humongous regions
      // that were not reclaimed.
      r->rem_set()->clear(true /* only_cardset */, true /* keep_tracked */);

      assert(r->rem_set()->is_empty() && r->rem_set()->is_complete(), "must be for eager reclaim candidates");

      return false;
    }

    G1MergeCardSetStats stats() {
      return _cl.stats();
    }
  };

  // Visitor for the log buffer entries to merge them into the card table.
  class G1MergeLogBufferCardsClosure : public G1CardTableEntryClosure {

    G1RemSetScanState* _scan_state;
    G1CardTable* _ct;

    size_t _cards_dirty;
    size_t _cards_skipped;

    void process_card(CardValue* card_ptr) {
      if (*card_ptr == G1CardTable::dirty_card_val()) {
        uint const region_idx = _ct->region_idx_for(card_ptr);
        _scan_state->add_dirty_region(region_idx);
        _scan_state->set_chunk_dirty(_ct->index_for_cardvalue(card_ptr));
        _cards_dirty++;
      }
    }

  public:
    G1MergeLogBufferCardsClosure(G1CollectedHeap* g1h, G1RemSetScanState* scan_state) :
      _scan_state(scan_state),
      _ct(g1h->card_table()),
      _cards_dirty(0),
      _cards_skipped(0)
    {}

    void do_card_ptr(CardValue* card_ptr, uint worker_id) {
      // The only time we care about recording cards that
      // contain references that point into the collection set
      // is during RSet updating within an evacuation pause.
      // In this case worker_id should be the id of a GC worker thread.
      assert(SafepointSynchronize::is_at_safepoint(), "not during an evacuation pause");

      uint const region_idx = _ct->region_idx_for(card_ptr);

      // The second clause must come after - the log buffers might contain cards to uncommitted
      // regions.
      // This code may count duplicate entries in the log buffers (even if rare) multiple
      // times.
      if (_scan_state->contains_cards_to_process(region_idx)) {
        process_card(card_ptr);
      } else {
        // We may have had dirty cards in the (initial) collection set (or the
        // young regions which are always in the initial collection set). We do
        // not fix their cards here: we already added these regions to the set of
        // regions to clear the card table at the end during the prepare() phase.
        _cards_skipped++;
      }
    }

    size_t cards_dirty() const { return _cards_dirty; }
    size_t cards_skipped() const { return _cards_skipped; }
  };

  HeapRegionClaimer _hr_claimer;
  G1RemSetScanState* _scan_state;
  BufferNode::Stack _dirty_card_buffers;
  bool _initial_evacuation;

  volatile bool _fast_reclaim_handled;

  void apply_closure_to_dirty_card_buffers(G1MergeLogBufferCardsClosure* cl, uint worker_id) {
    G1DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();
    while (BufferNode* node = _dirty_card_buffers.pop()) {
      cl->apply_to_buffer(node, worker_id);
      dcqs.deallocate_buffer(node);
    }
  }

public:
  G1MergeHeapRootsTask(G1RemSetScanState* scan_state, uint num_workers, bool initial_evacuation) :
    WorkerTask("G1 Merge Heap Roots"),
    _hr_claimer(num_workers),
    _scan_state(scan_state),
    _dirty_card_buffers(),
    _initial_evacuation(initial_evacuation),
    _fast_reclaim_handled(false)
  {
    if (initial_evacuation) {
      G1DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();
      BufferNodeList buffers = dcqs.take_all_completed_buffers();
      if (buffers._entry_count != 0) {
        _dirty_card_buffers.prepend(*buffers._head, *buffers._tail);
      }
    }
  }

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
            !_fast_reclaim_handled &&
            !Atomic::cmpxchg(&_fast_reclaim_handled, false, true)) {

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
        G1ClearBitmapClosure clear(g1h);
        G1CombinedClosure combined(&merge, &clear);

        g1h->collection_set_iterate_increment_from(&combined, nullptr, worker_id);
        G1MergeCardSetStats stats = merge.stats();

        for (uint i = 0; i < G1GCPhaseTimes::MergeRSContainersSentinel; i++) {
          p->record_or_add_thread_work_item(merge_remset_phase, worker_id, stats.merged(i), i);
        }
      }
    }

    // Now apply the closure to all remaining log entries.
    if (_initial_evacuation) {
      assert(merge_remset_phase == G1GCPhaseTimes::MergeRS, "Wrong merge phase");
      G1GCParPhaseTimesTracker x(p, G1GCPhaseTimes::MergeLB, worker_id);

      G1MergeLogBufferCardsClosure cl(g1h, _scan_state);
      apply_closure_to_dirty_card_buffers(&cl, worker_id);

      p->record_thread_work_item(G1GCPhaseTimes::MergeLB, worker_id, cl.cards_dirty(), G1GCPhaseTimes::MergeLBDirtyCards);
      p->record_thread_work_item(G1GCPhaseTimes::MergeLB, worker_id, cl.cards_skipped(), G1GCPhaseTimes::MergeLBSkippedCards);
    }
  }
};

void G1RemSet::print_merge_heap_roots_stats() {
  LogTarget(Debug, gc, remset) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);

    size_t num_visited_cards = _scan_state->num_visited_cards();

    size_t total_dirty_region_cards = _scan_state->num_cards_in_dirty_regions();

    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    size_t total_old_region_cards =
      (g1h->num_regions() - (g1h->num_free_regions() - g1h->collection_set()->cur_length())) * HeapRegion::CardsPerRegion;

    ls.print_cr("Visited cards " SIZE_FORMAT " Total dirty " SIZE_FORMAT " (%.2lf%%) Total old " SIZE_FORMAT " (%.2lf%%)",
                num_visited_cards,
                total_dirty_region_cards,
                percent_of(num_visited_cards, total_dirty_region_cards),
                total_old_region_cards,
                percent_of(num_visited_cards, total_old_region_cards));
  }
}

void G1RemSet::merge_heap_roots(bool initial_evacuation) {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  {
    Ticks start = Ticks::now();

    _scan_state->prepare_for_merge_heap_roots();

    Tickspan total = Ticks::now() - start;
    if (initial_evacuation) {
      g1h->phase_times()->record_prepare_merge_heap_roots_time(total.seconds() * 1000.0);
    } else {
      g1h->phase_times()->record_or_add_optional_prepare_merge_heap_roots_time(total.seconds() * 1000.0);
    }
  }

  WorkerThreads* workers = g1h->workers();
  size_t const increment_length = g1h->collection_set()->increment_length();

  uint const num_workers = initial_evacuation ? workers->active_workers() :
                                                MIN2(workers->active_workers(), (uint)increment_length);

  {
    G1MergeHeapRootsTask cl(_scan_state, num_workers, initial_evacuation);
    log_debug(gc, ergo)("Running %s using %u workers for " SIZE_FORMAT " regions",
                        cl.name(), num_workers, increment_length);
    workers->run_task(&cl, num_workers);
  }

  print_merge_heap_roots_stats();
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
         "Card at " PTR_FORMAT " index " SIZE_FORMAT " representing heap at " PTR_FORMAT " (%u) must be in committed heap",
         p2i(card_ptr),
         ct->index_for(ct->addr_for(card_ptr)),
         p2i(ct->addr_for(card_ptr)),
         g1h->addr_to_region(ct->addr_for(card_ptr)));
#endif
}

bool G1RemSet::clean_card_before_refine(CardValue** const card_ptr_addr) {
  assert(!SafepointSynchronize::is_at_safepoint(), "Only call concurrently");

  CardValue* card_ptr = *card_ptr_addr;
  // Find the start address represented by the card.
  HeapWord* start = _ct->addr_for(card_ptr);
  // And find the region containing it.
  HeapRegion* r = _g1h->heap_region_containing_or_null(start);

  // If this is a (stale) card into an uncommitted region, exit.
  if (r == nullptr) {
    return false;
  }

  check_card_ptr(card_ptr, _ct);

  // If the card is no longer dirty, nothing to do.
  // We cannot load the card value before the "r == nullptr" check above, because G1
  // could uncommit parts of the card table covering uncommitted regions.
  if (*card_ptr != G1CardTable::dirty_card_val()) {
    return false;
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
  if (!r->is_old_or_humongous()) {
    return false;
  }

  // Trim the region designated by the card to what's been allocated
  // in the region.  The card could be stale, or the card could cover
  // (part of) an object at the end of the allocated space and extend
  // beyond the end of allocation.

  // Non-humongous objects are either allocated in the old regions during GC.
  // So if region is old then top is stable.
  // Humongous object allocation sets top last; if top has not yet been set,
  // this is a stale card and we'll end up with an empty intersection.
  // If this is not a stale card, the synchronization between the
  // enqueuing of the card and processing it here will have ensured
  // we see the up-to-date top here.
  HeapWord* scan_limit = r->top();

  if (scan_limit <= start) {
    // If the trimmed region is empty, the card must be stale.
    return false;
  }

  // Okay to clean and process the card now.  There are still some
  // stale card cases that may be detected by iteration and dealt with
  // as iteration failure.
  *const_cast<volatile CardValue*>(card_ptr) = G1CardTable::clean_card_val();

  return true;
}

void G1RemSet::refine_card_concurrently(CardValue* const card_ptr,
                                        const uint worker_id) {
  assert(!_g1h->is_gc_active(), "Only call concurrently");
  check_card_ptr(card_ptr, _ct);

  // Construct the MemRegion representing the card.
  HeapWord* start = _ct->addr_for(card_ptr);
  // And find the region containing it.
  HeapRegion* r = _g1h->heap_region_containing(start);
  // This reload of the top is safe even though it happens after the full
  // fence, because top is stable for old and unfiltered humongous
  // regions, so it must return the same value as the previous load when
  // cleaning the card. Also cleaning the card and refinement of the card
  // cannot span across safepoint, so we don't need to worry about top being
  // changed during safepoint.
  HeapWord* scan_limit = r->top();
  assert(scan_limit > start, "sanity");

  // Don't use addr_for(card_ptr + 1) which can ask for
  // a card beyond the heap.
  HeapWord* end = start + G1CardTable::card_size_in_words();
  MemRegion dirty_region(start, MIN2(scan_limit, end));
  assert(!dirty_region.is_empty(), "sanity");

  G1ConcurrentRefineOopClosure conc_refine_cl(_g1h, worker_id);
  if (r->oops_on_memregion_seq_iterate_careful<false>(dirty_region, &conc_refine_cl) != nullptr) {
    return;
  }

  // If unable to process the card then we encountered an unparsable
  // part of the heap (e.g. a partially allocated object, so only
  // temporarily a problem) while processing a stale card.  Despite
  // the card being stale, we can't simply ignore it, because we've
  // already marked the card cleaned, so taken responsibility for
  // ensuring the card gets scanned.
  //
  // However, the card might have gotten re-dirtied and re-enqueued
  // while we worked.  (In fact, it's pretty likely.)
  if (*card_ptr == G1CardTable::dirty_card_val()) {
    return;
  }

  enqueue_for_reprocessing(card_ptr);
}

// Re-dirty and re-enqueue the card to retry refinement later.
// This is used to deal with a rare race condition in concurrent refinement.
void G1RemSet::enqueue_for_reprocessing(CardValue* card_ptr) {
  // We can't use the thread-local queue, because that might be the queue
  // that is being processed by us; we could be a Java thread conscripted to
  // perform refinement on our queue's current buffer.  This situation only
  // arises from rare race condition, so it's not worth any significant
  // development effort or clever lock-free queue implementation.  Instead
  // we use brute force, allocating and enqueuing an entire buffer for just
  // this card.  Since buffers are processed in FIFO order and we try to
  // keep some in the queue, it is likely that the racing state will have
  // resolved by the time this card comes up for reprocessing.
  *card_ptr = G1CardTable::dirty_card_val();
  G1DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();
  void** buffer = dcqs.allocate_buffer();
  size_t index = dcqs.buffer_capacity() - 1;
  buffer[index] = card_ptr;
  dcqs.enqueue_completed_buffer(BufferNode::make_node_from_buffer(buffer, index));
}

void G1RemSet::print_periodic_summary_info(const char* header, uint period_count, bool show_thread_times) {
  if ((G1SummarizeRSetStatsPeriod > 0) && log_is_enabled(Trace, gc, remset) &&
      (period_count % G1SummarizeRSetStatsPeriod == 0)) {

    G1RemSetSummary current;
    _prev_period_summary.subtract_from(&current);

    Log(gc, remset) log;
    log.trace("%s", header);
    ResourceMark rm;
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
    ResourceMark rm;
    LogStream ls(log.trace());
    current.print_on(&ls, true /* show_thread_times*/);
  }
}
