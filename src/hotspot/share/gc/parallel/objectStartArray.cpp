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
#include "gc/parallel/objectStartArray.inline.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "nmt/memTracker.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "utilities/align.hpp"

static size_t num_bytes_required(MemRegion mr) {
  assert(CardTable::is_card_aligned(mr.start()), "precondition");
  assert(CardTable::is_card_aligned(mr.end()), "precondition");

  return mr.word_size() / BOTConstants::card_size_in_words();
}

void ObjectStartArray::initialize(MemRegion reserved_region) {
  // Calculate how much space must be reserved
  size_t bytes_to_reserve = num_bytes_required(reserved_region);
  assert(bytes_to_reserve > 0, "Sanity");

  bytes_to_reserve =
    align_up(bytes_to_reserve, os::vm_allocation_granularity());

  // Do not use large-pages for the backing store. The one large page region
  // will be used for the heap proper.
  ReservedSpace backing_store(bytes_to_reserve);
  if (!backing_store.is_reserved()) {
    vm_exit_during_initialization("Could not reserve space for ObjectStartArray");
  }
  MemTracker::record_virtual_memory_type(backing_store.base(), mtGC);

  // We do not commit any memory initially
  _virtual_space.initialize(backing_store);

  assert(_virtual_space.low_boundary() != nullptr, "set from the backing_store");

  _offset_base = (uint8_t*)(_virtual_space.low_boundary() - (uintptr_t(reserved_region.start()) >> BOTConstants::log_card_size()));
}

void ObjectStartArray::set_covered_region(MemRegion mr) {
  DEBUG_ONLY(_covered_region = mr;)

  size_t requested_size = num_bytes_required(mr);
  // Only commit memory in page sized chunks
  requested_size = align_up(requested_size, os::vm_page_size());

  size_t current_size = _virtual_space.committed_size();

  if (requested_size == current_size) {
    return;
  }

  if (requested_size > current_size) {
    // Expand
    size_t expand_by = requested_size - current_size;
    if (!_virtual_space.expand_by(expand_by)) {
      vm_exit_out_of_memory(expand_by, OOM_MMAP_ERROR, "object start array expansion");
    }
  } else {
    // Shrink
    size_t shrink_by = current_size - requested_size;
    _virtual_space.shrink_by(shrink_by);
  }
}

static void fill_range(uint8_t* start, uint8_t* end, uint8_t v) {
  // + 1 for inclusive
  memset(start, v, pointer_delta(end, start, sizeof(uint8_t)) + 1);
}

void ObjectStartArray::update_for_block_work(HeapWord* blk_start,
                                             HeapWord* blk_end) {
  HeapWord* const cur_card_boundary = align_up_by_card_size(blk_start);
  uint8_t* const offset_entry = entry_for_addr(cur_card_boundary);

  // The first card holds the actual offset.
  *offset_entry = checked_cast<uint8_t>(pointer_delta(cur_card_boundary, blk_start));

  // Check if this block spans over other cards.
  uint8_t* const end_entry = entry_for_addr(blk_end - 1);
  assert(offset_entry <= end_entry, "inv");

  if (offset_entry != end_entry) {
    // Handling remaining entries.
    uint8_t* start_entry_for_region = offset_entry + 1;
    for (uint i = 0; i < BOTConstants::N_powers; i++) {
      // -1 so that the reach ends in this region and not at the start
      // of the next.
      uint8_t* reach = offset_entry + BOTConstants::power_to_cards_back(i + 1) - 1;
      uint8_t value = checked_cast<uint8_t>(BOTConstants::card_size_in_words() + i);

      fill_range(start_entry_for_region, MIN2(reach, end_entry), value);
      start_entry_for_region = reach + 1;

      if (reach >= end_entry) {
        break;
      }
    }
    assert(start_entry_for_region > end_entry, "Sanity check");
  }

  debug_only(verify_for_block(blk_start, blk_end);)
}

void ObjectStartArray::verify_for_block(HeapWord* blk_start, HeapWord* blk_end) const {
  assert(is_crossing_card_boundary(blk_start, blk_end), "precondition");

  const uint8_t* const start_entry = entry_for_addr(align_up_by_card_size(blk_start));
  const uint8_t* const end_entry = entry_for_addr(blk_end - 1);
  // Check entries in [start_entry, end_entry]
  assert(*start_entry < BOTConstants::card_size_in_words(), "offset entry");

  for (const uint8_t* i = start_entry + 1; i <= end_entry; ++i) {
    const uint8_t prev  = *(i-1);
    const uint8_t value = *i;
    if (prev != value) {
      assert(value >= prev, "monotonic");
      size_t n_cards_back = BOTConstants::entry_to_cards_back(value);
      assert(start_entry == (i - n_cards_back), "inv");
    }
  }
}
