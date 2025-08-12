/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 */

#include "gc/shenandoah/shenandoahForwardingTable.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.hpp"
#include "utilities/copy.hpp"
#include "utilities/fastHash.hpp"

bool ShenandoahForwardingTable::Entry::is_marked(ShenandoahMarkingContext* ctx) const {
  return ctx->is_marked_ignore_tams(cast_from_oop<HeapWord*>(cast_to_oop(&_original))) ||
         ctx->is_marked_ignore_tams(cast_from_oop<HeapWord*>(cast_to_oop(&_forwardee)));
}

static bool different_entries(HeapWord* a, HeapWord* b, size_t entry_size_in_words) {
  uintptr_t aint = reinterpret_cast<uintptr_t>(a) / HeapWordSize;
  uintptr_t bint = reinterpret_cast<uintptr_t>(b) / HeapWordSize;
  return aint / entry_size_in_words != bint / entry_size_in_words;
}

bool ShenandoahForwardingTable::initialize(size_t num_entries) {
  // Try to find the minimum hashtable that satisfies a load-factor of 0.75.
  // We know that we have num_entries live words that we can not use and we
  // need num_entries * 1.5 usable entries.
  constexpr size_t entry_size_in_words = sizeof(Entry) / sizeof(HeapWord*);
  HeapWord* const bottom =  _region->bottom();
  HeapWord* const top =  _region->top();
  HeapWord* const end = _region->end();
  // We want 1.5x entries than expected forwardings, to maintain the 0.75 load-factor.
  size_t const num_required_entries = num_entries + num_entries / 2;
  // Optimistic last possible table start. We don't need to search beyond that.
  HeapWord* const last_table_start = end - num_required_entries * entry_size_in_words;
  assert(last_table_start >= bottom, "table start must be in region: asked for %lu forwardings", num_entries);
  // Count number of live words in the tail [last_table_start, top).
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  size_t unusable_entries = 0;
  HeapWord* limit = top;
  while (last_table_start < limit) {
    HeapWord* live = ctx->get_last_marked_addr(last_table_start, limit);
    if (live < limit && different_entries(live, limit, entry_size_in_words)) {
      unusable_entries++;
    } else {
      break;
    }
    limit = live;
  }
  // Now try to find a lower bound that satisfies the 0.75 load-factor.
  // Start at the last possible address.
  HeapWord* table_start = last_table_start;
  assert(table_start >= bottom, "table start must be in region");
  size_t num_table_entries = (end - table_start) / entry_size_in_words;
  while (table_start > bottom && num_table_entries - unusable_entries < num_required_entries) {
    HeapWord* prev_live = ctx->get_last_marked_addr(bottom, table_start);
    if (prev_live >= table_start) {
      // No more live objects found. Use bottom as table_start.
      table_start = bottom;
      assert(table_start >= bottom, "table start must be in region");
    } else {
      if (different_entries(prev_live, table_start, entry_size_in_words)) {
        unusable_entries++;
      }
      table_start = prev_live;
      assert(table_start >= bottom, "table start must be in region");
    }
    num_table_entries = (end - table_start) / entry_size_in_words;
  }

  assert(table_start >= bottom, "table start must be in region");

  // We may have overshot a little, adjust for optimum lower boundary.
  if (num_table_entries > (unusable_entries + num_required_entries)) {
    size_t adjust = num_table_entries - unusable_entries - num_required_entries;
    HeapWord* old_start = table_start;
    table_start += adjust * entry_size_in_words;
    num_table_entries -= adjust;
    assert(table_start >= bottom, "table start must be in region: adjust: %lu, old table start: " PTR_FORMAT ", new table start: " PTR_FORMAT ", bottom: " PTR_FORMAT, adjust, p2i(old_start), p2i(table_start), p2i(bottom));
  }

  if (num_table_entries - unusable_entries < num_required_entries) {
    return false;
  }
  table_start = align_down(table_start, entry_size_in_words * HeapWordSize);
  _table = reinterpret_cast<Entry*>(table_start);
  _num_entries = (end - table_start) / entry_size_in_words;
  _num_expected_forwardings = num_entries;
  _num_actual_forwardings = 0;
  _num_live_words = unusable_entries;

  assert((void*)(_table + _num_entries) == (void*)_region->end(), "table must be anchored at region end");
  log_develop_debug(gc)("Initialized forwarding table: table: " PTR_FORMAT ", num_entries: %lu, requested entries: %lu", p2i(_table), _num_entries, num_entries);
  return true;
}

void ShenandoahForwardingTable::clear() {
  // Clear all entries, but be careful to skip existing object headers.
  // We still need them.
  assert((void*)(_table + _num_entries) == (void*)_region->end(), "table must be anchored at region end");
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  HeapWord* start = reinterpret_cast<HeapWord*>(_table);
  HeapWord* end = _region->top();
  assert(_region->bottom() <= start, "start must be in the region, bottom: " PTR_FORMAT ", table start: " PTR_FORMAT, p2i(_region->bottom()), p2i(start));
  assert(start < _region->end(), "start must be before end");
  while (start < end) {
    HeapWord* next_marked = ctx->get_next_marked_addr(start, end);
    assert(next_marked <= end, "next marked must be in the region");
    log_develop_debug(gc)("Clearing [" PTR_FORMAT ", " PTR_FORMAT ")", p2i(start), p2i(next_marked));
    Copy::fill_to_aligned_words(start, next_marked - start);
    start = next_marked + 1;
  }
  log_develop_debug(gc)("Clearing [" PTR_FORMAT ", " PTR_FORMAT ")", p2i(end), p2i(_region->end()));
  Copy::fill_to_aligned_words(end, _region->end() - end);
}

uint64_t ShenandoahForwardingTable::hash(HeapWord* original, Entry* table) {
  return FastHash::get_hash64(reinterpret_cast<uint64_t>(original), reinterpret_cast<uint64_t>(table));
}

void ShenandoahForwardingTable::enter_forwarding(HeapWord* original, HeapWord* forwardee) {
  Entry* table = _table;
  uint64_t hash = FastHash::get_hash64(reinterpret_cast<uint64_t>(original), reinterpret_cast<uint64_t>(table));
  uint64_t index = hash % _num_entries;
  log_develop_trace(gc)("Finding slot, start at index: " UINT64_FORMAT ", for original: " PTR_FORMAT ", forwardee: " PTR_FORMAT, index, p2i(original), p2i(forwardee));
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  while (table[index].original() != nullptr || table[index].is_marked(ctx)) {
    log_develop_trace(gc)("Collision on" UINT64_FORMAT ": [" PTR_FORMAT ", " PTR_FORMAT "): is_marked: %s, original: " PTR_FORMAT ", forwardee: " PTR_FORMAT, index, p2i(&table[index]._original), p2i(&table[index]._forwardee), BOOL_TO_STR(table[index].is_marked(ctx)), p2i(table[index].original()), p2i(table[index].forwardee()));
    index++;
    if (index == _num_entries) {
      index = 0;
    }
    assert(index != hash % _num_entries, "must find a usable slot, _num_entries: %lu, actual forwardings: %lu, live_words: %lu", _num_entries, _num_actual_forwardings, _num_live_words);
  }
  assert(table[index].original() == nullptr, "must have found empty slot");
  assert(table[index].forwardee() == nullptr, "must have found empty slot, found this instead: " PTR_FORMAT " at index: %lu and location: " PTR_FORMAT ", table: [" PTR_FORMAT ", " PTR_FORMAT "), marked slot 1: %s, marked slot 2: %s", p2i(table[index].forwardee()), index, p2i(&table[index]._forwardee), p2i(_table), p2i(_table + _num_entries), BOOL_TO_STR(ctx->is_marked_ignore_tams((HeapWord*)&table[index]._original)), BOOL_TO_STR(ctx->is_marked_ignore_tams((HeapWord*)&table[index]._forwardee)));
  assert(!table[index].is_marked(ctx), "must have found unmarked slot");
  new (&table[index]) Entry(original, forwardee);
  _num_actual_forwardings++;
  assert(_num_actual_forwardings <= _num_expected_forwardings, "must not exceed number of forwardings");
}

void ShenandoahForwardingTable::log_stats() const {
  log_info(gc)("Forwarding table load factor: %f", (float)(_num_actual_forwardings + _num_live_words) / (float) (_num_entries));
  log_info(gc)("Forwarding table size: %lu (== %lu bytes)", _num_entries, sizeof(Entry) * _num_entries);
  log_info(gc)("Forwarding table expected: %lu, actual: %lu, live words: %lu", _num_expected_forwardings, _num_actual_forwardings, _num_live_words);
}

void ShenandoahForwardingTable::fill_forwardings() {
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  HeapWord* start = _region->bottom();
  HeapWord* end = _region->top();
  while (start < end) {
    HeapWord* original = ctx->get_next_marked_addr(start, end);
    if (original < end) {
      HeapWord* forwardee = cast_from_oop<HeapWord*>(ShenandoahForwarding::get_forwardee_raw(cast_to_oop(original)));
      enter_forwarding(original, forwardee);
    }
    start = original + 1;
  }
  assert(_num_actual_forwardings == _num_expected_forwardings, "must enter exact number of forwardings, actual: %lu, expected: %lu", _num_actual_forwardings, _num_expected_forwardings);
  log_stats();
}

#ifndef PRODUCT
void ShenandoahForwardingTable::verify_forwardings() {
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  HeapWord* start = _region->bottom();
  HeapWord* end = _region->top();
  while (start < end) {
    HeapWord* original = ctx->get_next_marked_addr(start, end);
    if (original < end) {
      HeapWord* expected_forwardee = cast_from_oop<HeapWord*>(ShenandoahForwarding::get_forwardee_raw(cast_to_oop(original)));
      HeapWord* actual_forwardee = forwardee(original);
      guarantee(actual_forwardee == expected_forwardee, "Forwardees in mark-word and table must match: original: " PTR_FORMAT ", mark-forwardee: " PTR_FORMAT ", found forwardee: " PTR_FORMAT, p2i(original), p2i(expected_forwardee), p2i(actual_forwardee));
    }
    start = original + 1;
  }
}
#endif

bool ShenandoahForwardingTable::build(size_t num_entries) {
  bool initialized = initialize(num_entries);
  if (initialized) {
    clear();
    fill_forwardings();
    verify_forwardings();
  }
  return initialized;
}

HeapWord* ShenandoahForwardingTable::forwardee(HeapWord* original) const {
  Entry* table = _table;
  uint64_t hash = FastHash::get_hash64(reinterpret_cast<uint64_t>(original), reinterpret_cast<uint64_t>(table));
  uint64_t index = hash % _num_entries;
  log_develop_trace(gc)("Finding slot, start at index: " UINT64_FORMAT ", for original: " PTR_FORMAT, index, p2i(original));
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  while (table[index].is_marked(ctx) || table[index].original() != original) {
    //log_info(gc)("Collision on " UINT64_FORMAT ": [" PTR_FORMAT ", " PTR_FORMAT "): is_marked: %s, original: " PTR_FORMAT ", forwardee: " PTR_FORMAT, index, p2i(&table[index]._original), p2i(&table[index]._forwardee), BOOL_TO_STR(table[index].is_marked(ctx)), p2i(table[index].original()), p2i(table[index].forwardee()));
    index++;
    if (index == _num_entries) {
      index = 0;
    }
    assert(index != hash % _num_entries, "must find a usable slot");
  }
  assert(table[index].original() == original, "must have found original object");
  assert(table[index].forwardee() != nullptr, "must have found a forwarding");
  assert(!table[index].is_marked(ctx), "must have found unmarked slot");
  return table[index].forwardee();
}

void ShenandoahForwardingTable::zap_region() {
#ifndef PRODUCT
  Copy::fill_to_aligned_words(_region->bottom(), reinterpret_cast<HeapWord*>(_table) - _region->bottom(), 0x12345678);
#endif
}
