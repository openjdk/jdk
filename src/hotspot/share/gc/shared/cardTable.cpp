/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/cardTable.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/space.inline.hpp"
#include "logging/log.hpp"
#include "memory/virtualspace.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#if INCLUDE_PARALLELGC
#include "gc/parallel/objectStartArray.hpp"
#endif

uint CardTable::_card_shift = 0;
uint CardTable::_card_size = 0;
uint CardTable::_card_size_in_words = 0;

void CardTable::initialize_card_size() {
  assert(UseG1GC || UseParallelGC || UseSerialGC,
         "Initialize card size should only be called by card based collectors.");

  _card_size = GCCardSizeInBytes;
  _card_shift = log2i_exact(_card_size);
  _card_size_in_words = _card_size / sizeof(HeapWord);

  // Set blockOffsetTable size based on card table entry size
  BOTConstants::initialize_bot_size(_card_shift);

#if INCLUDE_PARALLELGC
  // Set ObjectStartArray block size based on card table entry size
  ObjectStartArray::initialize_block_size(_card_shift);
#endif

  log_info_p(gc, init)("CardTable entry size: " UINT32_FORMAT,  _card_size);
}

size_t CardTable::compute_byte_map_size(size_t num_bytes) {
  assert(_page_size != 0, "uninitialized, check declaration order");
  const size_t granularity = os::vm_allocation_granularity();
  return align_up(num_bytes, MAX2(_page_size, granularity));
}

CardTable::CardTable(MemRegion whole_heap) :
  _whole_heap(whole_heap),
  _page_size(os::vm_page_size()),
  _byte_map_size(0),
  _byte_map(nullptr),
  _byte_map_base(nullptr),
  _guard_region()
{
  assert((uintptr_t(_whole_heap.start())  & (_card_size - 1))  == 0, "heap must start at card boundary");
  assert((uintptr_t(_whole_heap.end()) & (_card_size - 1))  == 0, "heap must end at card boundary");
}

void CardTable::initialize(void* region0_start, void* region1_start) {
  size_t num_cards = cards_required(_whole_heap.word_size());

  // each card takes 1 byte; + 1 for the guard card
  size_t num_bytes = num_cards + 1;
  _byte_map_size = compute_byte_map_size(num_bytes);

  HeapWord* low_bound  = _whole_heap.start();
  HeapWord* high_bound = _whole_heap.end();

  const size_t rs_align = _page_size == os::vm_page_size() ? 0 :
    MAX2(_page_size, os::vm_allocation_granularity());
  ReservedSpace heap_rs(_byte_map_size, rs_align, _page_size);

  MemTracker::record_virtual_memory_type((address)heap_rs.base(), mtGC);

  os::trace_page_sizes("Card Table", num_bytes, num_bytes,
                       heap_rs.base(), heap_rs.size(), _page_size);
  if (!heap_rs.is_reserved()) {
    vm_exit_during_initialization("Could not reserve enough space for the "
                                  "card marking array");
  }

  // The assembler store_check code will do an unsigned shift of the oop,
  // then add it to _byte_map_base, i.e.
  //
  //   _byte_map = _byte_map_base + (uintptr_t(low_bound) >> card_shift)
  _byte_map = (CardValue*) heap_rs.base();
  _byte_map_base = _byte_map - (uintptr_t(low_bound) >> _card_shift);
  assert(byte_for(low_bound) == &_byte_map[0], "Checking start of map");
  assert(byte_for(high_bound-1) <= &_byte_map[last_valid_index()], "Checking end of map");

  CardValue* guard_card = &_byte_map[num_cards];
  assert(is_aligned(guard_card, _page_size), "must be on its own OS page");
  _guard_region = MemRegion((HeapWord*)guard_card, _page_size);

  initialize_covered_region(region0_start, region1_start);

  log_trace(gc, barrier)("CardTable::CardTable: ");
  log_trace(gc, barrier)("    &_byte_map[0]: " PTR_FORMAT "  &_byte_map[last_valid_index()]: " PTR_FORMAT,
                         p2i(&_byte_map[0]), p2i(&_byte_map[last_valid_index()]));
  log_trace(gc, barrier)("    _byte_map_base: " PTR_FORMAT, p2i(_byte_map_base));
}

MemRegion CardTable::committed_for(const MemRegion mr) const {
  HeapWord* addr_l = (HeapWord*)align_down(byte_for(mr.start()), _page_size);
  HeapWord* addr_r = mr.is_empty()
                   ? addr_l
                   : (HeapWord*)align_up(byte_after(mr.last()), _page_size);

  if (mr.start() == _covered[0].start()) {
    // In case the card for gen-boundary is not page-size aligned, the crossing page belongs to _covered[1].
    addr_r = MIN2(addr_r, (HeapWord*)align_down(byte_for(_covered[1].start()), _page_size));
  }

  return MemRegion(addr_l, addr_r);
}

void CardTable::initialize_covered_region(void* region0_start, void* region1_start) {
  assert(_whole_heap.start() == region0_start, "precondition");
  assert(region0_start < region1_start, "precondition");

  assert(_covered[0].start() == nullptr, "precondition");
  assert(_covered[1].start() == nullptr, "precondition");

  _covered[0] = MemRegion((HeapWord*)region0_start, (size_t)0);
  _covered[1] = MemRegion((HeapWord*)region1_start, (size_t)0);
}

void CardTable::resize_covered_region(MemRegion new_region) {
  assert(UseSerialGC || UseParallelGC, "only these two collectors");
  assert(_whole_heap.contains(new_region),
         "attempt to cover area not in reserved area");
  assert(_covered[0].start() != nullptr, "precondition");
  assert(_covered[1].start() != nullptr, "precondition");

  int idx = new_region.start() == _whole_heap.start() ? 0 : 1;

  // We don't allow changes to the start of a region, only the end.
  assert(_covered[idx].start() == new_region.start(), "inv");

  MemRegion old_committed = committed_for(_covered[idx]);

  _covered[idx] = new_region;

  MemRegion new_committed = committed_for(new_region);

  if (new_committed.word_size() == old_committed.word_size()) {
    return;
  }

  if (new_committed.word_size() > old_committed.word_size()) {
    // Expand.
    MemRegion delta = MemRegion(old_committed.end(),
                                new_committed.word_size() - old_committed.word_size());

    os::commit_memory_or_exit((char*)delta.start(),
                              delta.byte_size(),
                              _page_size,
                              !ExecMem,
                              "card table expansion");

    memset(delta.start(), clean_card, delta.byte_size());
  } else {
    // Shrink.
    MemRegion delta = MemRegion(new_committed.end(),
                                old_committed.word_size() - new_committed.word_size());
    bool res = os::uncommit_memory((char*)delta.start(),
                                   delta.byte_size());
    assert(res, "uncommit should succeed");
  }

  log_trace(gc, barrier)("CardTable::resize_covered_region: ");
  log_trace(gc, barrier)("    _covered[%d].start(): " PTR_FORMAT " _covered[%d].last(): " PTR_FORMAT,
                         idx, p2i(_covered[idx].start()), idx, p2i(_covered[idx].last()));
  log_trace(gc, barrier)("    committed_start: " PTR_FORMAT "  committed_last: " PTR_FORMAT,
                         p2i(new_committed.start()), p2i(new_committed.last()));
  log_trace(gc, barrier)("    byte_for(start): " PTR_FORMAT "  byte_for(last): " PTR_FORMAT,
                         p2i(byte_for(_covered[idx].start())),  p2i(byte_for(_covered[idx].last())));
  log_trace(gc, barrier)("    addr_for(start): " PTR_FORMAT "  addr_for(last): " PTR_FORMAT,
                         p2i(addr_for((CardValue*) new_committed.start())),  p2i(addr_for((CardValue*) new_committed.last())));

#ifdef ASSERT
  // Touch the last card of the covered region to show that it
  // is committed (or SEGV).
  if (is_init_completed()) {
    (void) (*(volatile CardValue*)byte_for(_covered[idx].last()));
  }
#endif
}

// Note that these versions are precise!  The scanning code has to handle the
// fact that the write barrier may be either precise or imprecise.
void CardTable::dirty_MemRegion(MemRegion mr) {
  assert(align_down(mr.start(), HeapWordSize) == mr.start(), "Unaligned start");
  assert(align_up  (mr.end(),   HeapWordSize) == mr.end(),   "Unaligned end"  );
  CardValue* cur  = byte_for(mr.start());
  CardValue* last = byte_after(mr.last());
  while (cur < last) {
    *cur = dirty_card;
    cur++;
  }
}

void CardTable::clear_MemRegion(MemRegion mr) {
  // Be conservative: only clean cards entirely contained within the
  // region.
  CardValue* cur;
  if (mr.start() == _whole_heap.start()) {
    cur = byte_for(mr.start());
  } else {
    assert(mr.start() > _whole_heap.start(), "mr is not covered.");
    cur = byte_after(mr.start() - 1);
  }
  CardValue* last = byte_after(mr.last());
  memset(cur, clean_card, pointer_delta(last, cur, sizeof(CardValue)));
}

uintx CardTable::ct_max_alignment_constraint() {
  // Calculate maximum alignment using GCCardSizeInBytes as card_size hasn't been set yet
  return GCCardSizeInBytes * os::vm_page_size();
}

void CardTable::invalidate(MemRegion mr) {
  assert(align_down(mr.start(), HeapWordSize) == mr.start(), "Unaligned start");
  assert(align_up  (mr.end(),   HeapWordSize) == mr.end(),   "Unaligned end"  );
  for (int i = 0; i < max_covered_regions; i++) {
    MemRegion mri = mr.intersection(_covered[i]);
    if (!mri.is_empty()) dirty_MemRegion(mri);
  }
}

#ifndef PRODUCT
void CardTable::verify_region(MemRegion mr, CardValue val, bool val_equals) {
  CardValue* start    = byte_for(mr.start());
  CardValue* end      = byte_for(mr.last());
  bool failures = false;
  for (CardValue* curr = start; curr <= end; ++curr) {
    CardValue curr_val = *curr;
    bool failed = (val_equals) ? (curr_val != val) : (curr_val == val);
    if (failed) {
      if (!failures) {
        log_error(gc, verify)("== CT verification failed: [" PTR_FORMAT "," PTR_FORMAT "]", p2i(start), p2i(end));
        log_error(gc, verify)("==   %sexpecting value: %d", (val_equals) ? "" : "not ", val);
        failures = true;
      }
      log_error(gc, verify)("==   card " PTR_FORMAT " [" PTR_FORMAT "," PTR_FORMAT "], val: %d",
                            p2i(curr), p2i(addr_for(curr)),
                            p2i((HeapWord*) (((size_t) addr_for(curr)) + _card_size)),
                            (int) curr_val);
    }
  }
  guarantee(!failures, "there should not have been any failures");
}

void CardTable::verify_not_dirty_region(MemRegion mr) {
  verify_region(mr, dirty_card, false /* val_equals */);
}

void CardTable::verify_dirty_region(MemRegion mr) {
  verify_region(mr, dirty_card, true /* val_equals */);
}
#endif

void CardTable::print_on(outputStream* st) const {
  st->print_cr("Card table byte_map: [" PTR_FORMAT "," PTR_FORMAT "] _byte_map_base: " PTR_FORMAT,
               p2i(_byte_map), p2i(_byte_map + _byte_map_size), p2i(_byte_map_base));
}
