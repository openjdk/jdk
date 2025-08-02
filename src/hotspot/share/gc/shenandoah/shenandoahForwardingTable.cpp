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

void ShenandoahForwardingTable::initialize(size_t num_entries) {
  size_t table_size = num_entries + num_entries / 2; // Load-factor of 1.5 to make hash-table efficient.
  // Make sure we have enough room, considering object headers that we need to skip.
  HeapWord* table_end = _region->end();
  HeapWord* table_start = table_end - table_size * (sizeof(Entry) / sizeof(HeapWord*));
  log_develop_debug(gc)("table start: " PTR_FORMAT ", table_end: " PTR_FORMAT ", num entries: %lu", p2i(table_start), p2i(table_end), num_entries);
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  size_t num_live_objects = ctx->count_live_objects(table_start, table_end);
  while (table_size - num_live_objects < num_entries) {
    table_size += num_entries / 2;
    HeapWord* new_table_start = table_end - table_size * (sizeof(Entry) / sizeof(HeapWord*));
    log_develop_debug(gc)("new table start: " PTR_FORMAT ", table_start: " PTR_FORMAT ", live objects: %lu", p2i(new_table_start), p2i(table_start), num_live_objects);
    num_live_objects += ctx->count_live_objects(new_table_start, table_start);
    table_start = new_table_start;
  }
  _table = reinterpret_cast<Entry*>(table_start);
  _num_entries = table_size;
  log_develop_debug(gc)("Initialized forwarding table: table: " PTR_FORMAT ", num_entries: %lu, requested entries: %lu", p2i(_table), _num_entries, num_entries);
}

void ShenandoahForwardingTable::clear() {
  // Clear all entries, but be careful to skip existing object headers.
  // We still need them.
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  HeapWord* start = reinterpret_cast<HeapWord*>(_table);
  HeapWord* end = _region->top();
  assert(_region->bottom() <= start, "start must be in the region");
  assert(start < _region->end(), "start must be before end");
  while (start < end) {
    HeapWord* next_marked = ctx->get_next_marked_addr(start, end);
    assert(next_marked <= end, "next marked must be in the region");
    log_develop_trace(gc)("Clearing [" PTR_FORMAT ", " PTR_FORMAT ")", p2i(start), p2i(next_marked));
    Copy::fill_to_aligned_words(start, next_marked - start);
    start = next_marked + 1;
  }
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
    assert(index != hash % _num_entries, "must find a usable slot");
  }
  assert(table[index].original() == nullptr, "must have found empty slot");
  assert(table[index].forwardee() == nullptr, "must have found empty slot");
  assert(!table[index].is_marked(ctx), "must have found unmarked slot");
  new (&table[index]) Entry(original, forwardee);
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

void ShenandoahForwardingTable::build(size_t num_entries) {
  initialize(num_entries);
  clear();
  fill_forwardings();
  verify_forwardings();
}

HeapWord* ShenandoahForwardingTable::forwardee(HeapWord* original) const {
  Entry* table = _table;
  uint64_t hash = FastHash::get_hash64(reinterpret_cast<uint64_t>(original), reinterpret_cast<uint64_t>(table));
  uint64_t index = hash % _num_entries;
  log_develop_trace(gc)("Finding slot, start at index: " UINT64_FORMAT ", for original: " PTR_FORMAT, index, p2i(original));
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  while (table[index].is_marked(ctx) || table[index].original() != original) {
    log_develop_trace(gc)("Collision on " UINT64_FORMAT ": [" PTR_FORMAT ", " PTR_FORMAT "): is_marked: %s, original: " PTR_FORMAT ", forwardee: " PTR_FORMAT, index, p2i(&table[index]._original), p2i(&table[index]._forwardee), BOOL_TO_STR(table[index].is_marked(ctx)), p2i(table[index].original()), p2i(table[index].forwardee()));
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
  Copy::fill_to_aligned_words(_region->bottom(), reinterpret_cast<HeapWord*>(_table) - _region->bottom());
}
