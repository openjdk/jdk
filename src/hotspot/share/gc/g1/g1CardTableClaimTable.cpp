/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/shared/workerThread.hpp"
#include "memory/allocation.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/powerOfTwo.hpp"

G1CardTableClaimTable::G1CardTableClaimTable(uint chunks_per_region) :
  _max_reserved_regions(0),
  _card_claims(nullptr),
  _cards_per_chunk(checked_cast<uint>(G1HeapRegion::CardsPerRegion / chunks_per_region))
{
  guarantee(chunks_per_region > 0, "%u chunks per region", chunks_per_region);
}

G1CardTableClaimTable::~G1CardTableClaimTable() {
  FREE_C_HEAP_ARRAY(uint, _card_claims);
}

void G1CardTableClaimTable::initialize(uint max_reserved_regions) {
  assert(_card_claims == nullptr, "Must not be initialized twice");
  _card_claims = NEW_C_HEAP_ARRAY(Atomic<uint>, max_reserved_regions, mtGC);
  _max_reserved_regions = max_reserved_regions;
  reset_all_to_unclaimed();
}

void G1CardTableClaimTable::reset_all_to_unclaimed() {
  for (uint i = 0; i < _max_reserved_regions; i++) {
    _card_claims[i].store_relaxed(0);
  }
}

void G1CardTableClaimTable::reset_all_to_claimed() {
  for (uint i = 0; i < _max_reserved_regions; i++) {
    _card_claims[i].store_relaxed((uint)G1HeapRegion::CardsPerRegion);
  }
}

void G1CardTableClaimTable::heap_region_iterate_from_worker_offset(G1HeapRegionClosure* cl, uint worker_id, uint max_workers) {
  // Every worker will actually look at all regions, skipping over regions that
  // are completed.
  const size_t n_regions = _max_reserved_regions;
  const uint start_index = (uint)(worker_id * n_regions / max_workers);

  for (uint count = 0; count < n_regions; count++) {
    const uint index = (start_index + count) % n_regions;
    assert(index < n_regions, "sanity");
    // Skip over fully processed regions
    if (!has_unclaimed_cards(index)) {
      continue;
    }
    G1HeapRegion* r = G1CollectedHeap::heap()->region_at(index);
    bool res = cl->do_heap_region(r);
    if (res) {
      return;
    }
  }
}

G1CardTableChunkClaimer::G1CardTableChunkClaimer(G1CardTableClaimTable* scan_state, uint region_idx) :
  _claim_values(scan_state),
  _region_idx(region_idx),
  _cur_claim(0) {
  guarantee(size() <= G1HeapRegion::CardsPerRegion, "Should not claim more space than possible.");
}

G1ChunkScanner::G1ChunkScanner(CardValue* const start_card, CardValue* const end_card) :
  _start_card(start_card),
  _end_card(end_card) {
    assert(is_word_aligned(start_card), "precondition");
    assert(is_word_aligned(end_card), "precondition");
}
