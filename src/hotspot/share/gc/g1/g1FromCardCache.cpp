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

#include "gc/g1/g1FromCardCache.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/gc_globals.hpp"
#include "memory/padded.inline.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"

uintptr_t** G1FromCardCache::_cache = nullptr;
uint        G1FromCardCache::_max_entries = 0;
size_t      G1FromCardCache::_static_mem_size = 0;
#ifdef ASSERT
uint   G1FromCardCache::_max_workers = 0;
#endif

void G1FromCardCache::initialize() {
  guarantee(_cache == nullptr, "Should not call this multiple times");

  // A narrowOop is the smallest possible reference slot, so this is an upper
  // bound on the number of distinct cardsets referenced by one source card.
  _max_entries = CardTable::card_size() / sizeof(narrowOop);
  guarantee(_max_entries > 0, "Card must be able to contain at least one reference");
#ifdef ASSERT
  _max_workers = num_par_rem_sets();
#endif
  _cache = Padded2DArray<uintptr_t, mtGC>::create_unfreeable(num_par_rem_sets(),
                                                             EntriesStartIndex + _max_entries,
                                                             &_static_mem_size);

  if (AlwaysPreTouch) {
    invalidate();
  }
}

void G1FromCardCache::invalidate() {
  for (uint worker_id = 0; worker_id < num_par_rem_sets(); worker_id++) {
    uintptr_t* const cache = row(worker_id);
    for (uint i = 0; i < EntriesStartIndex + _max_entries; i++) {
      cache[i] = 0;
    }
  }
}

#ifndef PRODUCT
void G1FromCardCache::print(outputStream* out) {
  for (uint worker_id = 0; worker_id < num_par_rem_sets(); worker_id++) {
    uintptr_t* const cache = row(worker_id);
    uint num_cardsets = static_cast<uint>(cache[NumEntriesIndex]);
    out->print_cr("_from_card_cache[%u]: source card %zu, %u cardsets",
                  worker_id, cache[SourceCardIndex], num_cardsets);
    for (uint i = 0; i < num_cardsets; i++) {
      out->print_cr("  cardset FCC id[%u] = %u", i,
                    static_cast<uint>(cache[EntriesStartIndex + i]));
    }
  }
}
#endif

uint G1FromCardCache::num_par_rem_sets() {
  return G1ConcRefinementThreads + ConcGCThreads;
}
