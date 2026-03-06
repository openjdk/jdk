/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CardTableClaimTable.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentRefineStats.inline.hpp"
#include "gc/g1/g1ConcurrentRefineSweepTask.hpp"

class G1RefineRegionClosure : public G1HeapRegionClosure {
  using CardValue = G1CardTable::CardValue;

  G1RemSet* _rem_set;
  G1CardTableClaimTable* _scan_state;

  uint _worker_id;

  size_t _num_collections_at_start;

  bool has_work(G1HeapRegion* r) {
    return _scan_state->has_unclaimed_cards(r->hrm_index());
  }

  void verify_card_pair_refers_to_same_card(CardValue* source_card, CardValue* dest_card) {
#ifdef ASSERT
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    G1HeapRegion* refinement_r = g1h->heap_region_containing(g1h->refinement_table()->addr_for(source_card));
    G1HeapRegion* card_r = g1h->heap_region_containing(g1h->card_table()->addr_for(dest_card));
    size_t refinement_i = g1h->refinement_table()->index_for_cardvalue(source_card);
    size_t card_i = g1h->card_table()->index_for_cardvalue(dest_card);

    assert(refinement_r == card_r, "not same region source %u (%zu) dest %u (%zu) ", refinement_r->hrm_index(), refinement_i, card_r->hrm_index(), card_i);
    assert(refinement_i == card_i, "indexes are not same %zu %zu", refinement_i, card_i);
#endif
  }

  void do_dirty_card(CardValue* source_card, CardValue* dest_card) {
    verify_card_pair_refers_to_same_card(source_card, dest_card);

    G1RemSet::RefineResult res = _rem_set->refine_card_concurrently(source_card, _worker_id);
    // Gather statistics based on the result.
    switch (res) {
      case G1RemSet::HasRefToCSet: {
        *dest_card = G1CardTable::g1_to_cset_card;
        _refine_stats.inc_cards_refer_to_cset();
        break;
      }
      case G1RemSet::AlreadyToCSet: {
        *dest_card = G1CardTable::g1_to_cset_card;
        _refine_stats.inc_cards_already_refer_to_cset();
        break;
      }
      case G1RemSet::NoCrossRegion: {
        _refine_stats.inc_cards_no_cross_region();
        break;
      }
      case G1RemSet::CouldNotParse: {
        // Could not refine - redirty with the original value.
        *dest_card = *source_card;
        _refine_stats.inc_cards_not_parsable();
        break;
      }
      case G1RemSet::HasRefToOld : break; // Nothing special to do.
    }
    // Clean card on source card table.
    *source_card = G1CardTable::clean_card_val();
  }

  void do_claimed_block(CardValue* dirty_l, CardValue* dirty_r, CardValue* dest_card) {
    for (CardValue* source = dirty_l; source < dirty_r; ++source, ++dest_card) {
      do_dirty_card(source, dest_card);
    }
  }

public:
  bool _completed;
  G1ConcurrentRefineStats _refine_stats;

  G1RefineRegionClosure(uint worker_id, G1CardTableClaimTable* scan_state) :
    G1HeapRegionClosure(),
    _rem_set(G1CollectedHeap::heap()->rem_set()),
    _scan_state(scan_state),
    _worker_id(worker_id),
    _completed(true),
    _refine_stats() { }

  bool do_heap_region(G1HeapRegion* r) override {

    if (!has_work(r)) {
      return false;
    }

    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    if (r->is_young()) {
      if (_scan_state->claim_all_cards(r->hrm_index()) == 0) {
        // Clear the pre-dirtying information.
        r->clear_refinement_table();
      }
      return false;
    }

    G1CardTable* card_table = g1h->card_table();
    G1CardTable* refinement_table = g1h->refinement_table();

    G1CardTableChunkClaimer claim(_scan_state, r->hrm_index());

    size_t const region_card_base_idx = (size_t)r->hrm_index() << G1HeapRegion::LogCardsPerRegion;

    while (claim.has_next()) {
      size_t const start_idx = region_card_base_idx + claim.value();
      CardValue* const start_card = refinement_table->byte_for_index(start_idx);
      CardValue* const end_card = start_card + claim.size();

      CardValue* dest_card = card_table->byte_for_index(start_idx);

      G1ChunkScanner scanner{start_card, end_card};

      size_t num_dirty_cards = 0;
      scanner.on_dirty_cards([&] (CardValue* dirty_l, CardValue* dirty_r) {
                               jlong refine_start = os::elapsed_counter();

                               do_claimed_block(dirty_l, dirty_r, dest_card + pointer_delta(dirty_l, start_card, sizeof(CardValue)));
                               num_dirty_cards += pointer_delta(dirty_r, dirty_l, sizeof(CardValue));

                               _refine_stats.inc_refine_duration(os::elapsed_counter() - refine_start);
                             });

      if (VerifyDuringGC) {
        for (CardValue* i = start_card; i < end_card; ++i) {
          guarantee(*i == G1CardTable::clean_card_val(), "must be");
        }
      }

      _refine_stats.inc_cards_scanned(claim.size());
      _refine_stats.inc_cards_clean(claim.size() - num_dirty_cards);

      if (SuspendibleThreadSet::should_yield()) {
        _completed = false;
        break;
      }
    }

    return !_completed;
  }
};

G1ConcurrentRefineSweepTask::G1ConcurrentRefineSweepTask(G1CardTableClaimTable* scan_state,
                                                           G1ConcurrentRefineStats* stats,
                                                           uint max_workers) :
  WorkerTask("G1 Refine Task"),
  _scan_state(scan_state),
  _stats(stats),
  _max_workers(max_workers),
  _sweep_completed(true)
{ }

void G1ConcurrentRefineSweepTask::work(uint worker_id) {
  jlong start = os::elapsed_counter();

  G1RefineRegionClosure sweep_cl(worker_id, _scan_state);
  _scan_state->heap_region_iterate_from_worker_offset(&sweep_cl, worker_id, _max_workers);

  if (!sweep_cl._completed) {
    _sweep_completed = false;
  }

  sweep_cl._refine_stats.inc_sweep_time(os::elapsed_counter() - start);
  _stats->add_atomic(&sweep_cl._refine_stats);
}

bool G1ConcurrentRefineSweepTask::sweep_completed() const { return _sweep_completed; }