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

#ifndef SHARE_GC_G1_G1CARDTABLECLAIMTABLE_HPP
#define SHARE_GC_G1_G1CARDTABLECLAIMTABLE_HPP

#include "gc/g1/g1CardTable.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"

class G1HeapRegionClosure;

// Helper class representing claim values for the cards in the card table corresponding
// to a region.
// I.e. for every region this class stores an atomic counter that represents the
// number of cards from 0 to the number of cards per region already claimed for
// this region.
// If the claimed value is >= the number of cards of a region, the region can be
// considered fully claimed.
//
// Claiming works on full region (all cards in region) or a range of contiguous cards
// (chunk). Chunk size is given at construction time.
class G1CardTableClaimTable : public CHeapObj<mtGC> {
  uint _max_reserved_regions;

  // Card table iteration claim values for every heap region, from 0 (completely unclaimed)
  // to (>=) G1HeapRegion::CardsPerRegion (completely claimed).
  Atomic<uint>* _card_claims;

  uint _cards_per_chunk;           // For conversion between card index and chunk index.

  // Claim increment number of cards, returning the previous claim value.
  inline uint claim_cards(uint region, uint increment);

public:
  G1CardTableClaimTable(uint chunks_per_region);
  ~G1CardTableClaimTable();

  // Allocates the data structure and initializes the claims to unclaimed.
  void initialize(uint max_reserved_regions);

  void reset_all_to_unclaimed();
  void reset_all_to_claimed();

  inline bool has_unclaimed_cards(uint region);
  inline void reset_to_unclaimed(uint region);

  // Claims all cards in that region, returning the previous claim value.
  inline uint claim_all_cards(uint region);

  // Claim a single chunk in that region, returning the previous claim value.
  inline uint claim_chunk(uint region);
  inline uint cards_per_chunk() const;

  size_t max_reserved_regions() { return _max_reserved_regions; }

  void heap_region_iterate_from_worker_offset(G1HeapRegionClosure* cl, uint worker_id, uint max_workers);
};

// Helper class to claim dirty chunks within the card table for a given region.
class G1CardTableChunkClaimer {
  G1CardTableClaimTable* _claim_values;

  uint _region_idx;
  uint _cur_claim;

public:
  G1CardTableChunkClaimer(G1CardTableClaimTable* claim_table, uint region_idx);

  inline bool has_next();

  inline uint value() const;
  inline uint size() const;
};

// Helper class to locate consecutive dirty cards inside a range of cards.
class G1ChunkScanner {
  using Word = size_t;
  using CardValue = G1CardTable::CardValue;

  CardValue* const _start_card;
  CardValue* const _end_card;

  static const size_t ExpandedToScanMask = G1CardTable::WordAlreadyScanned;
  static const size_t ToScanMask = G1CardTable::g1_card_already_scanned;

  inline bool is_card_dirty(const CardValue* const card) const;

  inline bool is_word_aligned(const void* const addr) const;

  inline CardValue* find_first_dirty_card(CardValue* i_card) const;
  inline CardValue* find_first_non_dirty_card(CardValue* i_card) const;

public:
  G1ChunkScanner(CardValue* const start_card, CardValue* const end_card);

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

#endif // SHARE_GC_G1_G1CARDTABLECLAIMTABLE_HPP
