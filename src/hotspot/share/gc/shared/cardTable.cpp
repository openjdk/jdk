/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/cardTable.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/space.hpp"
#include "logging/log.hpp"
#include "memory/memoryReserver.hpp"
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
  assert(UseG1GC || UseParallelGC || UseSerialGC || UseShenandoahGC,
         "Initialize card size should only be called by card based collectors.");

  _card_size = GCCardSizeInBytes;
  _card_shift = log2i_exact(_card_size);
  _card_size_in_words = _card_size / sizeof(HeapWord);

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
  _byte_map_base(nullptr)
{
  assert((uintptr_t(_whole_heap.start())  & (_card_size - 1))  == 0, "heap must start at card boundary");
  assert((uintptr_t(_whole_heap.end()) & (_card_size - 1))  == 0, "heap must end at card boundary");
}

void CardTable::initialize(void* region0_start, void* region1_start) {
  size_t num_cards = cards_required(_whole_heap.word_size());

  size_t num_bytes = num_cards * sizeof(CardValue);
  _byte_map_size = compute_byte_map_size(num_bytes);

  HeapWord* low_bound  = _whole_heap.start();
  HeapWord* high_bound = _whole_heap.end();

  const size_t rs_align = MAX2(_page_size, os::vm_allocation_granularity());
  ReservedSpace rs = MemoryReserver::reserve(_byte_map_size, rs_align, _page_size);

  if (!rs.is_reserved()) {
    vm_exit_during_initialization("Could not reserve enough space for the "
                                  "card marking array");
  }

  MemTracker::record_virtual_memory_tag(rs, mtGC);

  os::trace_page_sizes("Card Table", num_bytes, num_bytes,
                       rs.base(), rs.size(), _page_size);

  // The assembler store_check code will do an unsigned shift of the oop,
  // then add it to _byte_map_base, i.e.
  //
  //   _byte_map = _byte_map_base + (uintptr_t(low_bound) >> card_shift)
  _byte_map = (CardValue*) rs.base();
  _byte_map_base = _byte_map - (uintptr_t(low_bound) >> _card_shift);
  assert(byte_for(low_bound) == &_byte_map[0], "Checking start of map");
  assert(byte_for(high_bound-1) <= &_byte_map[last_valid_index()], "Checking end of map");

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
  assert(_covered[0].contains(mr) || _covered[1].contains(mr), "precondition");
  CardValue* cur  = byte_for(mr.start());
  CardValue* last = byte_after(mr.last());
  memset(cur, dirty_card, pointer_delta(last, cur, sizeof(CardValue)));
}

void CardTable::clear_MemRegion(MemRegion mr) {
  // The MemRegion mr can have a word size of 0. This occurs the first time
  // a SerialGC full collection is performed, for example. In that case, the
  // MemRegion corresponds to the previously used region in the tenured space.
  // Since that is an empty region, mr.last() will fall outside the bounds
  // of the heap if the tenured region is at the start of the whole heap.
  // We can avoid that assertion failure since there are no words to be cleared
  // for a region with a word size of 0.
  if (mr.word_size() == 0) {
    // no need to call memset on a card table region of size 0 bytes
    return;
  }

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

MemRegion CardTable::card_table_mem_for_shared_virtual_space_region(const MemRegion mr) const {
  HeapWord* addr_l = (HeapWord*)byte_for(mr.start());

  log_trace(gc, barrier)("CardTable::card_table_mem_for_shared_virtual_space_region: ");
  log_trace(gc, barrier)("    mr.start():                    " PTR_FORMAT "  mr.last(): " PTR_FORMAT,
                         p2i(mr.start()), p2i(mr.last()));
  log_trace(gc, barrier)("    byte_for(mr.start()):          " PTR_FORMAT, p2i(addr_l));

  if (mr.start() == _covered[0].start()) {
    addr_l = (HeapWord*)align_down(addr_l, _page_size);
    log_trace(gc, barrier)("    aligned byte_for(mr.start()):  " PTR_FORMAT, p2i(addr_l));
  }

  HeapWord* addr_r;
  if (mr.is_empty()) {
    addr_r = addr_l;
  } else {
    addr_r = (HeapWord*)byte_after(mr.last());
    log_trace(gc, barrier)("    byte_after(mr.last()):         " PTR_FORMAT, p2i(addr_r));

    if (mr.start() == _covered[1].start()) {
      addr_r = (HeapWord*)align_up(addr_r, _page_size);
      log_trace(gc, barrier)("    aligned byte_after(mr.last()): " PTR_FORMAT, p2i(addr_r));
    }
  }

  return MemRegion(addr_l, addr_r);
}

void CardTable::resize_covered_region_in_shared_virtual_space(MemRegion new_heap_region0, MemRegion new_heap_region1) {
#ifdef ASSERT
  log_trace(gc, barrier)("CardTable::resize_covered_region_shared_virtual_space: ");
  log_trace(gc, barrier)("   _whole_heap.start(): " PTR_FORMAT " _whole_heap.end(): " PTR_FORMAT,
                         p2i(_whole_heap.start()), p2i(_whole_heap.end()));
  log_trace(gc, barrier)("   new_heap_region0.start(): " PTR_FORMAT " new_heap_region0.end(): " PTR_FORMAT,
                         p2i(new_heap_region0.start()), p2i(new_heap_region0.end()));
  log_trace(gc, barrier)("   new_heap_region1.start(): " PTR_FORMAT " new_heap_region1.end(): " PTR_FORMAT,
                         p2i(new_heap_region1.start()), p2i(new_heap_region1.end()));
#endif

  assert(UseSerialGC, "only the serial collector uses this method");
  assert(SharedSerialGCVirtualSpace, "the SharedSerialGCVirtualSpace flag must be enabled");
  assert(_whole_heap.contains(new_heap_region0),
         "attempt to cover area not in reserved area (region 0)");
  assert(_whole_heap.contains(new_heap_region1),
         "attempt to cover area not in reserved area (region 1)");
  assert(_covered[0].start() != nullptr, "_covered[0].start() must not be null");
  assert(_covered[1].start() == _covered[0].end(), "_covered[1] must start at the end of _covered[0]");

  const int tenured_idx = 0, young_idx = 1;

  // We don't allow changes to the start of region0, only the end.
  assert(_covered[tenured_idx].start() == new_heap_region0.start(), "start of region0 must not change");

  assert(new_heap_region1.start() == new_heap_region0.end(), "region1 must start at the end of region0");

#ifdef ASSERT
  log_trace(gc, barrier)("CardTable resizing covered region in shared virtual space: ");
  for (int idx=0; idx < 2; idx++) {
    log_trace(gc, barrier)("   Before _covered[%d].start(): " PTR_FORMAT
                           " _covered[%d].end(): " PTR_FORMAT,
                           idx, p2i(_covered[idx].start()),
                           idx, p2i(_covered[idx].end()));

    log_trace(gc, barrier)("   After  _covered[%d].start(): " PTR_FORMAT
                           " _covered[%d].end(): " PTR_FORMAT,
                           idx, p2i(idx == 0 ? new_heap_region0.start() : new_heap_region1.start()),
                           idx, p2i(idx == 0 ? new_heap_region0.end() : new_heap_region1.end()));
  }
#endif

  MemRegion prev_committed_card_table_mem_for_tenured = card_table_mem_for_shared_virtual_space_region(_covered[tenured_idx]);
  MemRegion prev_committed_card_table_mem_for_young = card_table_mem_for_shared_virtual_space_region(_covered[young_idx]);
  MemRegion prev_committed_card_table_mem = prev_committed_card_table_mem_for_tenured._union(prev_committed_card_table_mem_for_young);

  _covered[tenured_idx] = new_heap_region0;
  _covered[young_idx] = new_heap_region1;

  MemRegion committed_card_table_mem_for_tenured = card_table_mem_for_shared_virtual_space_region(new_heap_region0);
  MemRegion committed_card_table_mem_for_young = card_table_mem_for_shared_virtual_space_region(new_heap_region1);

  MemRegion card_table_mem_to_commit = committed_card_table_mem_for_tenured._union(committed_card_table_mem_for_young);
  assert(card_table_mem_to_commit.start() == prev_committed_card_table_mem.start(), "start of committed card table memory must not change");

#ifdef ASSERT
  MemRegion prev_heap_region = _covered[0]._union(_covered[1]);
  MemRegion heap_region = new_heap_region0._union(new_heap_region1);
  log_trace(gc, barrier)("CardTable computed combined region: ");
  log_trace(gc, barrier)("    prev_heap_region.start(): " PTR_FORMAT "  prev_heap_region.end(): " PTR_FORMAT,
                         p2i(prev_heap_region.start()), p2i(prev_heap_region.end()));
  log_trace(gc, barrier)("    heap_region.start():      " PTR_FORMAT "  heap_region.end():      " PTR_FORMAT,
                         p2i(heap_region.start()), p2i(heap_region.end()));
#endif

  // Adjust the size of the committed space
  if (card_table_mem_to_commit.word_size() > prev_committed_card_table_mem.word_size()) {
    // Expand.

    MemRegion delta = MemRegion(prev_committed_card_table_mem.end(),
                                card_table_mem_to_commit.word_size() - prev_committed_card_table_mem.word_size());

    size_t delta_byte_size = delta.byte_size();

    log_trace(gc, barrier)("CardTable resizing covered region, expanding committed card table region by %zu bytes", delta_byte_size);
    log_trace(gc, barrier)("    card_table_mem_to_commit.start(): " PTR_FORMAT "  card_table_mem_to_commit.last(): " PTR_FORMAT,
                           p2i(card_table_mem_to_commit.start()), p2i(card_table_mem_to_commit.last()));
    log_trace(gc, barrier)("    addr_for(start):                  " PTR_FORMAT "  addr_for(last):                  " PTR_FORMAT,
                           p2i(addr_for((CardValue*) card_table_mem_to_commit.start())),  p2i(addr_for((CardValue*) card_table_mem_to_commit.last())));
    log_trace(gc, barrier)("    commit delta start:               " PTR_FORMAT "  commit delta last:               " PTR_FORMAT,
                           p2i(delta.start()), p2i(delta.last()));

    os::commit_memory_or_exit((char*)delta.start(),
                              delta_byte_size,
                              _page_size,
                              !ExecMem,
                              "card table expansion");

    memset(delta.start(), clean_card, delta.byte_size());
  } else if (card_table_mem_to_commit.word_size() < prev_committed_card_table_mem.word_size()) {
    // Shrink.
    MemRegion delta = MemRegion(card_table_mem_to_commit.end(),
                                prev_committed_card_table_mem.word_size() - card_table_mem_to_commit.word_size());

    log_trace(gc, barrier)("CardTable resizing covered region, shrinking committed card table region: ");
    log_trace(gc, barrier)("    card_table_mem_to_commit_start: " PTR_FORMAT "  card_table_mem_to_commit_last: " PTR_FORMAT,
                           p2i(card_table_mem_to_commit.start()), p2i(card_table_mem_to_commit.last()));
    log_trace(gc, barrier)("    addr_for(start):                " PTR_FORMAT "  addr_for(last):                " PTR_FORMAT,
                           p2i(addr_for((CardValue*) card_table_mem_to_commit.start())),  p2i(addr_for((CardValue*) card_table_mem_to_commit.last())));
    log_trace(gc, barrier)("    uncommit_start:                 " PTR_FORMAT "  uncommit_last:                 " PTR_FORMAT,
                           p2i(delta.start()), p2i(delta.last()));

    bool res = os::uncommit_memory((char*)delta.start(),
                                   delta.byte_size());
    assert(res, "uncommit should succeed");
  } else {
    log_trace(gc, barrier)("Committed card table region unchanged");
  }

#ifdef ASSERT
  log_trace(gc, barrier)("CardTable::resize_covered_region_shared_virtual_space: ");
  log_trace(gc, barrier)("    prev_committed_card_table_mem.start():                  " PTR_FORMAT "  prev_committed_card_table_mem.last():                  " PTR_FORMAT,
                         p2i(prev_committed_card_table_mem.start()), p2i(prev_committed_card_table_mem.last()));
  log_trace(gc, barrier)("    card_table_mem_to_commit_start:                         " PTR_FORMAT "  card_table_mem_to_commit_last:                         " PTR_FORMAT,
                         p2i(card_table_mem_to_commit.start()), p2i(card_table_mem_to_commit.last()));
  log_trace(gc, barrier)("    committed_card_table_mem_for_tenured.start():           " PTR_FORMAT "  committed_card_table_mem_for_tenured.last():           " PTR_FORMAT,
                         p2i(committed_card_table_mem_for_tenured.start()), p2i(committed_card_table_mem_for_tenured.last()));
  log_trace(gc, barrier)("    committed_card_table_mem_for_young.start():             " PTR_FORMAT "  committed_card_table_mem_for_young.last():             " PTR_FORMAT,
                         p2i(committed_card_table_mem_for_young.start()), p2i(committed_card_table_mem_for_young.last()));
  log_trace(gc, barrier)("    addr_for(committed_card_table_mem_for_tenured.start()): " PTR_FORMAT "  addr_for(committed_card_table_mem_for_tenured.last()): " PTR_FORMAT,
                         p2i(addr_for((CardValue*) committed_card_table_mem_for_tenured.start())),  p2i(addr_for((CardValue*) committed_card_table_mem_for_tenured.last())));
  log_trace(gc, barrier)("    addr_for(committed_card_table_mem_for_young.start()):   " PTR_FORMAT "  addr_for(committed_card_table_mem_for_young.last()):   " PTR_FORMAT,
                         p2i(addr_for((CardValue*) committed_card_table_mem_for_young.start())),  p2i(addr_for((CardValue*) committed_card_table_mem_for_young.last())));

  for (int idx=0; idx < 2; idx++) {
    log_trace(gc, barrier)("    _covered[%d].start():           " PTR_FORMAT "  _covered[%d].last(): " PTR_FORMAT,
                            idx, p2i(_covered[idx].start()),
                            idx, p2i(_covered[idx].last()));
    log_trace(gc, barrier)("    byte_for(_covered[%d].start()): " PTR_FORMAT "  byte_for(_covered[%d].last()): " PTR_FORMAT,
                           idx, p2i(byte_for(_covered[idx].start())),
                           idx, p2i(byte_for(_covered[idx].last())));
  }
#endif

  assert(committed_card_table_mem_for_tenured.last() < committed_card_table_mem_for_young.start(),
         "last word of tenured (" PTR_FORMAT ") must be less than first word of young gen (" PTR_FORMAT ")",
         p2i(committed_card_table_mem_for_tenured.last()),
         p2i(committed_card_table_mem_for_young.start()));

  assert(committed_card_table_mem_for_young.last() <= card_table_mem_to_commit.last(),
         "last word of young gen (" PTR_FORMAT ") must be in committed card table memory (" PTR_FORMAT ")",
         p2i(committed_card_table_mem_for_young.last()),
         p2i(card_table_mem_to_commit.last()));

  if (committed_card_table_mem_for_tenured.word_size() > prev_committed_card_table_mem_for_tenured.word_size()) {
    // Write the clean_card to the entire delta region

    log_trace(gc, barrier)("CardTable expanding covered region for tenured: ");
    log_trace(gc, barrier)("    _covered[%d].start():          " PTR_FORMAT "  _covered[%d].last():             " PTR_FORMAT,
                           tenured_idx, p2i(_covered[tenured_idx].start()),
                           tenured_idx, p2i(_covered[tenured_idx].last()));
    log_trace(gc, barrier)("    committed_card_table_mem_for_tenured_start:      " PTR_FORMAT "  committed_card_table_mem_for_tenured_last:         " PTR_FORMAT,
                           p2i(committed_card_table_mem_for_tenured.start()), p2i(committed_card_table_mem_for_tenured.last()));
    log_trace(gc, barrier)("    byte_for(_covered[%d].start):  " PTR_FORMAT "  byte_for(_covered[%d].last):     " PTR_FORMAT,
                           tenured_idx, p2i(byte_for(_covered[tenured_idx].start())),
                           tenured_idx, p2i(byte_for(_covered[tenured_idx].last())));
    log_trace(gc, barrier)("    addr_for(start):              " PTR_FORMAT "  addr_for(last):     " PTR_FORMAT,
                           p2i(addr_for((CardValue*) committed_card_table_mem_for_tenured.start())),  p2i(addr_for((CardValue*) committed_card_table_mem_for_tenured.last())));

    MemRegion tenured_delta = MemRegion(prev_committed_card_table_mem_for_tenured.end(),
                                        committed_card_table_mem_for_tenured.word_size() - prev_committed_card_table_mem_for_tenured.word_size());

    memset(tenured_delta.start(), clean_card, tenured_delta.byte_size());

    // If the end of the committed_card_table_mem_for_young region has shrunk, there is nothing else to do.
    // If it has expanded, then the expansion of the committed card table memory has already
    // written the clean_card to the expanded region. Nothing else needs to be done in
    // this case as well.
  } else if (committed_card_table_mem_for_tenured.word_size() < prev_committed_card_table_mem_for_tenured.word_size()) {
    // Shrink.

    MemRegion tenured_delta;
    if (prev_committed_card_table_mem_for_tenured.end() > card_table_mem_to_commit.end()) {
      // Ensure the delta is in the current heap!
      tenured_delta = MemRegion(committed_card_table_mem_for_tenured.end(), card_table_mem_to_commit.end());
    } else {
      tenured_delta = MemRegion(committed_card_table_mem_for_tenured.end(),
                                prev_committed_card_table_mem_for_tenured.word_size() - committed_card_table_mem_for_tenured.word_size());
    }

    log_trace(gc, barrier)("CardTable shrinking covered region for tenured, writing clean_card to region: ");
    log_trace(gc, barrier)("    tenured_delta.start():        " PTR_FORMAT "  tenured_delta.last():           " PTR_FORMAT,
                           p2i(tenured_delta.start()), p2i(tenured_delta.last()));

    memset(tenured_delta.start(), clean_card, tenured_delta.byte_size());
  }

#ifdef ASSERT
  // Touch the last card of the covered region to show that it
  // is committed (or SEGV).
  if (is_init_completed()) {
    (void) (*(volatile CardValue*)byte_for(_covered[young_idx].last()));
  }
#endif
}
