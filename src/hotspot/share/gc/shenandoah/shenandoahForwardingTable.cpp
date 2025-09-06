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

#include "code/vtableStubs.hpp"
#include "gc/shenandoah/shenandoahForwardingTable.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.hpp"
#include "utilities/copy.hpp"
#include "utilities/fastHash.hpp"

HeapWord* CompactFwdTableEntry::_heap_base = nullptr;
bool ShenandoahForwardingTable::_compact = false;

void ShenandoahForwardingTable::initialize_globals() {
  MemRegion heap = ShenandoahHeap::heap()->reserved_region();
  size_t heap_size_words = heap.word_size();
  if (ShenandoahHeapRegion::region_size_words() > CompactFwdTableEntry::max_region_size_words() ||
      heap.word_size() > CompactFwdTableEntry::max_heap_size_words()) {
    _compact = false;
  } else {
    _compact = true;
    CompactFwdTableEntry::set_heap_base(heap.start());
  }
}

static bool different_entries(HeapWord* a, HeapWord* b, size_t entry_size_in_words) {
  uintptr_t aint = reinterpret_cast<uintptr_t>(a) / HeapWordSize;
  uintptr_t bint = reinterpret_cast<uintptr_t>(b) / HeapWordSize;
  return aint / entry_size_in_words != bint / entry_size_in_words;
}

template<class Entry>
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

  assert((void*)(reinterpret_cast<Entry*>(_table) + _num_entries) == (void*)_region->end(), "table must be anchored at region end");
  log_develop_debug(gc)("Initialized forwarding table: table: " PTR_FORMAT ", num_entries: %lu, requested entries: %lu", p2i(_table), _num_entries, num_entries);
  return true;
}

template<class Entry>
void ShenandoahForwardingTable::clear() {
  assert((void*)(reinterpret_cast<Entry*>(_table) + _num_entries) == (void*)_region->end(), "table must be anchored at region end");

  // Clear all entries, but be careful to skip existing object headers.
  // We still need them.
  class ClearFwdTableClosure {
    HeapWord* _last;
  public:
    ClearFwdTableClosure(HeapWord* start) : _last(start) {}
    HeapWord* last() const { return _last; }
    void do_object(oop obj) {
      HeapWord* current = cast_from_oop<HeapWord*>(obj);
      if (_last != current) {
        Copy::fill_to_aligned_words(_last, current - _last);
      }
      _last = current + 1;
    }
  } cl(_region->bottom());
  ShenandoahHeap::heap()->marked_object_iterate(_region, &cl);

  // Clear unused tail.
  HeapWord* end = cl.last();
  HeapWord* region_end = _region->end();
  if (end != region_end) {
    Copy::fill_to_aligned_words(end, region_end - end);
  }
}

template<class Entry>
void ShenandoahForwardingTable::enter_forwarding(HeapWord* original, HeapWord* forwardee) {
  Entry* table = reinterpret_cast<Entry*>(_table);
  uint64_t hash = FastHash::get_hash64(reinterpret_cast<uint64_t>(original), reinterpret_cast<uint64_t>(table));
  uint64_t index = hash % _num_entries;
  log_develop_trace(gc)("Finding slot, start at index: " UINT64_FORMAT ", for original: " PTR_FORMAT ", forwardee: " PTR_FORMAT, index, p2i(original), p2i(forwardee));
  HeapWord* region_base = _region->bottom();
  //ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  while (table[index].is_used() /*|| table[index].is_marked(ctx)*/) {
#ifndef PRODUCT
    if (table[index].is_marked(ShenandoahHeap::heap()->marking_context())) {
      assert(!table[index].is_original(region_base, original), "marked location must not look like the original entry");
    }
#endif
    log_develop_trace(gc)("Collision on" UINT64_FORMAT ": is_marked: %s, original: " PTR_FORMAT ", forwardee: " PTR_FORMAT, index, BOOL_TO_STR(table[index].is_marked(ctx)), p2i(table[index].original(region_base)), p2i(table[index].forwardee()));
    index = (index + 1) % _num_entries;
    assert(index != hash % _num_entries, "must find a usable slot, _num_entries: %lu, actual forwardings: %lu, live_words: %lu", _num_entries, _num_actual_forwardings, _num_live_words);
  }
  assert(!table[index].is_used(), "must have found empty slot");
  assert(!table[index].is_marked(ShenandoahHeap::heap()->marking_context()), "must have found unmarked slot");
  new (&table[index]) Entry(region_base, original, forwardee);
  _num_actual_forwardings++;
  assert(_num_actual_forwardings <= _num_expected_forwardings, "must not exceed number of forwardings");
}

template<class Entry>
void ShenandoahForwardingTable::log_stats() const {
#ifndef PRODUCT
  log_info(gc)("Forwarding table load factor: %f", (float)(_num_actual_forwardings + _num_live_words) / (float) (_num_entries));
  log_info(gc)("Forwarding table size: %lu (== %lu bytes)", _num_entries, sizeof(Entry) * _num_entries);
  log_info(gc)("Forwarding table expected: %lu, actual: %lu, live words: %lu", _num_expected_forwardings, _num_actual_forwardings, _num_live_words);
#endif
}

template<class Entry>
void ShenandoahForwardingTable::fill_forwardings() {
  class FillForwardingsClosure {
    ShenandoahForwardingTable& _table;
  public:
    FillForwardingsClosure(ShenandoahForwardingTable& t) : _table(t) {}
    void do_object(oop obj) {
      HeapWord* original = cast_from_oop<HeapWord*>(obj);
      HeapWord* forwardee = cast_from_oop<HeapWord*>(ShenandoahForwarding::get_forwardee_raw(obj));
      _table.enter_forwarding<Entry>(original, forwardee);
    }
  } cl(*this);

  ShenandoahHeap::heap()->marked_object_iterate(_region, &cl);
  assert(_num_actual_forwardings == _num_expected_forwardings, "must enter exact number of forwardings, actual: %lu, expected: %lu", _num_actual_forwardings, _num_expected_forwardings);
  log_stats<Entry>();
}

#ifndef PRODUCT

template<class Entry>
void ShenandoahForwardingTable::verify_forwardings() {
  ShenandoahMarkingContext* ctx = ShenandoahHeap::heap()->marking_context();
  HeapWord* start = _region->bottom();
  HeapWord* end = _region->top();
  while (start < end) {
    HeapWord* original = ctx->get_next_marked_addr(start, end);
    if (original < end) {
      HeapWord* expected_forwardee = cast_from_oop<HeapWord*>(ShenandoahForwarding::get_forwardee_raw(cast_to_oop(original)));
      HeapWord* actual_forwardee = forwardee<Entry>(original);
      guarantee(actual_forwardee == expected_forwardee, "Forwardees in mark-word and table must match: original: " PTR_FORMAT ", mark-forwardee: " PTR_FORMAT ", found forwardee: " PTR_FORMAT, p2i(original), p2i(expected_forwardee), p2i(actual_forwardee));
    }
    start = original + 1;
  }
}
#endif

template<class Entry>
bool ShenandoahForwardingTable::build(size_t num_entries) {
  bool initialized = initialize<Entry>(num_entries);
  if (initialized) {
    clear<Entry>();
    fill_forwardings<Entry>();
    verify_forwardings<Entry>();
  }
  return initialized;
}

bool ShenandoahForwardingTable::build(size_t num_entries) {
  if (_compact) {
    return build<CompactFwdTableEntry>(num_entries);
  } else {
    return build<FwdTableEntry>(num_entries);
  }
}

void ShenandoahForwardingTable::zap_region() {
#ifndef PRODUCT
  Copy::fill_to_aligned_words(_region->bottom(), reinterpret_cast<HeapWord*>(_table) - _region->bottom(), 0x12345678);
#endif
}
