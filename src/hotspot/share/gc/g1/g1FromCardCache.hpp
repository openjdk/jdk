/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FROMCARDCACHE_HPP
#define SHARE_GC_G1_G1FROMCARDCACHE_HPP

#include "memory/allStatic.hpp"
#include "utilities/ostream.hpp"

// G1FromCardCache remembers which destination cardsets have been
// encountered while a worker scans the current source card.
class G1FromCardCache : public AllStatic {
private:
  // Each worker owns one row laid out as:
  //   [ source-card | number-of-entries | FCC id | FCC id | ... ]
  //          0                 1              2        3
  // The count identifies the populated prefix of FCC ids, which is searched
  // linearly like G1CardSetArray.
  static uintptr_t** _cache;
  static uint _max_entries;
  static size_t _static_mem_size;
#ifdef ASSERT
  static uint _max_workers;

  static void check_worker_bounds(uint worker_id) {
    assert(worker_id < _max_workers, "Worker_id %u is larger than maximum %u", worker_id, _max_workers);
  }
#endif

  static const uint SourceCardIndex = 0;
  static const uint NumEntriesIndex = 1;
  static const uint EntriesStartIndex = 2;

  // This card index indicates that the row is unused. This allows us to use
  // the OS lazy backing of memory with zero-filled pages to avoid initial
  // actual memory use. This means that the heap must not contain card zero.
  static const uintptr_t InvalidCard = 0;

  // Number of refinement and concurrent GC workers that may add records to
  // remembered sets in parallel.
  static uint num_par_rem_sets();

  static uintptr_t* row(uint worker_id) {
    DEBUG_ONLY(check_worker_bounds(worker_id);)
    return _cache[worker_id];
  }

  static void invalidate();
public:
  // Discard the state associated with worker_id. This must be called before a
  // worker begins a new refinement or rebuild scan and after a rebuild yield.
  static void reset(uint worker_id) {
    uintptr_t* const cache = row(worker_id);
    cache[SourceCardIndex] = InvalidCard;
    cache[NumEntriesIndex] = 0;
  }

  // Returns true if cardset_fcc_id has already been encountered while
  // worker_id was scanning source_card. Otherwise, records the id and returns
  // false.
  static bool contains_or_add(uint worker_id, uintptr_t source_card, uint cardset_fcc_id) {
    uintptr_t* const cache = row(worker_id);

    if (cache[SourceCardIndex] != source_card) {
      cache[SourceCardIndex] = source_card;
      cache[NumEntriesIndex] = 0;
    }

    const uint num_entries = static_cast<uint>(cache[NumEntriesIndex]);
    for (uint i = 0; i < num_entries; i++) {
      if (cache[EntriesStartIndex + i] == cardset_fcc_id) {
        return true;
      }
    }

    // There cannot be more distinct destination cardsets than reference slots
    // in the source card. Keep the product fallback conservative because this
    // is only a cache.
    assert(num_entries < _max_entries, "source card has too many destination cardsets");
    if (num_entries < _max_entries) {
      cache[EntriesStartIndex + num_entries] = cardset_fcc_id;
      cache[NumEntriesIndex] = num_entries + 1;
    }
    return false;
  }

  static void initialize();

  static void print(outputStream* out = tty) PRODUCT_RETURN;

  static size_t static_mem_size() {
    return _static_mem_size;
  }
};

#endif // SHARE_GC_G1_G1FROMCARDCACHE_HPP
