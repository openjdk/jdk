/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"
#include "logging/log.hpp"
#include "runtime/threads.hpp"

size_t ShenandoahDirectCardMarkRememberedSet::last_valid_index() const {
  return _card_table->last_valid_index();
}

size_t ShenandoahDirectCardMarkRememberedSet::total_cards() const {
  return _total_card_count;
}

size_t ShenandoahDirectCardMarkRememberedSet::card_index_for_addr(HeapWord *p) const {
  return _card_table->index_for(p);
}

HeapWord* ShenandoahDirectCardMarkRememberedSet::addr_for_card_index(size_t card_index) const {
  return _whole_heap_base + CardTable::card_size_in_words() * card_index;
}

bool ShenandoahDirectCardMarkRememberedSet::is_write_card_dirty(size_t card_index) const {
  CardValue* bp = &(_card_table->write_byte_map())[card_index];
  return (bp[0] == CardTable::dirty_card_val());
}

bool ShenandoahDirectCardMarkRememberedSet::is_card_dirty(size_t card_index) const {
  CardValue* bp = &(_card_table->read_byte_map())[card_index];
  return (bp[0] == CardTable::dirty_card_val());
}

void ShenandoahDirectCardMarkRememberedSet::mark_card_as_dirty(size_t card_index) {
  CardValue* bp = &(_card_table->write_byte_map())[card_index];
  bp[0] = CardTable::dirty_card_val();
}

void ShenandoahDirectCardMarkRememberedSet::mark_range_as_dirty(size_t card_index, size_t num_cards) {
  CardValue* bp = &(_card_table->write_byte_map())[card_index];
  while (num_cards-- > 0) {
    *bp++ = CardTable::dirty_card_val();
  }
}

bool ShenandoahDirectCardMarkRememberedSet::is_card_dirty(HeapWord* p) const {
  size_t index = card_index_for_addr(p);
  CardValue* bp = &(_card_table->read_byte_map())[index];
  return (bp[0] == CardTable::dirty_card_val());
}

bool ShenandoahDirectCardMarkRememberedSet::is_write_card_dirty(HeapWord* p) const {
  size_t index = card_index_for_addr(p);
  CardValue* bp = &(_card_table->write_byte_map())[index];
  return (bp[0] == CardTable::dirty_card_val());
}

void ShenandoahDirectCardMarkRememberedSet::mark_card_as_dirty(HeapWord* p) {
  size_t index = card_index_for_addr(p);
  CardValue* bp = &(_card_table->write_byte_map())[index];
  bp[0] = CardTable::dirty_card_val();
}

void ShenandoahDirectCardMarkRememberedSet::mark_range_as_dirty(HeapWord* p, size_t num_heap_words) {
  CardValue* bp = &(_card_table->write_byte_map_base())[uintptr_t(p) >> _card_shift];
  CardValue* end_bp = &(_card_table->write_byte_map_base())[uintptr_t(p + num_heap_words) >> _card_shift];
  // If (p + num_heap_words) is not aligned on card boundary, we also need to dirty last card.
  if (((unsigned long long) (p + num_heap_words)) & (CardTable::card_size() - 1)) {
    end_bp++;
  }
  while (bp < end_bp) {
    *bp++ = CardTable::dirty_card_val();
  }
}

void ShenandoahDirectCardMarkRememberedSet::mark_range_as_clean(HeapWord* p, size_t num_heap_words) {
  CardValue* bp = &(_card_table->write_byte_map_base())[uintptr_t(p) >> _card_shift];
  CardValue* end_bp = &(_card_table->write_byte_map_base())[uintptr_t(p + num_heap_words) >> _card_shift];
  // If (p + num_heap_words) is not aligned on card boundary, we also need to clean last card.
  if (((unsigned long long) (p + num_heap_words)) & (CardTable::card_size() - 1)) {
    end_bp++;
  }
  while (bp < end_bp) {
    *bp++ = CardTable::clean_card_val();
  }
}

void ShenandoahDirectCardMarkRememberedSet::mark_read_table_as_clean() {
  CardValue* read_table = _card_table->read_byte_map();
  CardValue* bp = &(read_table)[0];
  CardValue* end_bp = &(read_table)[_card_table->last_valid_index()];

  while (bp <= end_bp) {
    *bp++ = CardTable::clean_card_val();
  }

  log_develop_debug(gc, barrier)("Cleaned read_table from " PTR_FORMAT " to " PTR_FORMAT, p2i(&(read_table[0])), p2i(end_bp));
}

// No lock required because arguments align with card boundaries.
void ShenandoahCardCluster::reset_object_range(HeapWord* from, HeapWord* to) {
  assert(((((unsigned long long) from) & (CardTable::card_size() - 1)) == 0) &&
         ((((unsigned long long) to) & (CardTable::card_size() - 1)) == 0),
         "reset_object_range bounds must align with card boundaries");
  size_t card_at_start = _rs->card_index_for_addr(from);
  size_t num_cards = (to - from) / CardTable::card_size_in_words();

  for (size_t i = 0; i < num_cards; i++) {
    _object_starts[card_at_start + i].short_word = 0;
  }
}

// Assume only one thread at a time registers objects pertaining to
// each card-table entry's range of memory.
void ShenandoahCardCluster::register_object(HeapWord* address) {
  shenandoah_assert_heaplocked();

  register_object_without_lock(address);
}

void ShenandoahCardCluster::register_object_without_lock(HeapWord* address) {
  size_t card_at_start = _rs->card_index_for_addr(address);
  HeapWord* card_start_address = _rs->addr_for_card_index(card_at_start);
  uint8_t offset_in_card = checked_cast<uint8_t>(pointer_delta(address, card_start_address));

  if (!starts_object(card_at_start)) {
    set_starts_object_bit(card_at_start);
    set_first_start(card_at_start, offset_in_card);
    set_last_start(card_at_start, offset_in_card);
  } else {
    if (offset_in_card < get_first_start(card_at_start))
      set_first_start(card_at_start, offset_in_card);
    if (offset_in_card > get_last_start(card_at_start))
      set_last_start(card_at_start, offset_in_card);
  }
}

void ShenandoahCardCluster::coalesce_objects(HeapWord* address, size_t length_in_words) {

  size_t card_at_start = _rs->card_index_for_addr(address);
  HeapWord* card_start_address = _rs->addr_for_card_index(card_at_start);
  size_t card_at_end = card_at_start + ((address + length_in_words) - card_start_address) / CardTable::card_size_in_words();

  if (card_at_start == card_at_end) {
    // There are no changes to the get_first_start array.  Either get_first_start(card_at_start) returns this coalesced object,
    // or it returns an object that precedes the coalesced object.
    if (card_start_address + get_last_start(card_at_start) < address + length_in_words) {
      uint8_t coalesced_offset = checked_cast<uint8_t>(pointer_delta(address, card_start_address));
      // The object that used to be the last object starting within this card is being subsumed within the coalesced
      // object.  Since we always coalesce entire objects, this condition only occurs if the last object ends before or at
      // the end of the card's memory range and there is no object following this object.  In this case, adjust last_start
      // to represent the start of the coalesced range.
      set_last_start(card_at_start, coalesced_offset);
    }
    // Else, no changes to last_starts information.  Either get_last_start(card_at_start) returns the object that immediately
    // follows the coalesced object, or it returns an object that follows the object immediately following the coalesced object.
  } else {
    uint8_t coalesced_offset = checked_cast<uint8_t>(pointer_delta(address, card_start_address));
    if (get_last_start(card_at_start) > coalesced_offset) {
      // Existing last start is being coalesced, create new last start
      set_last_start(card_at_start, coalesced_offset);
    }
    // otherwise, get_last_start(card_at_start) must equal coalesced_offset

    // All the cards between first and last get cleared.
    for (size_t i = card_at_start + 1; i < card_at_end; i++) {
      clear_starts_object_bit(i);
    }

    uint8_t follow_offset = checked_cast<uint8_t>((address + length_in_words) - _rs->addr_for_card_index(card_at_end));
    if (starts_object(card_at_end) && (get_first_start(card_at_end) < follow_offset)) {
      // It may be that after coalescing within this last card's memory range, the last card
      // no longer holds an object.
      if (get_last_start(card_at_end) >= follow_offset) {
        set_first_start(card_at_end, follow_offset);
      } else {
        // last_start is being coalesced so this card no longer has any objects.
        clear_starts_object_bit(card_at_end);
      }
    }
    // else
    //  card_at_end did not have an object, so it still does not have an object, or
    //  card_at_end had an object that starts after the coalesced object, so no changes required for card_at_end

  }
}


size_t ShenandoahCardCluster::get_first_start(size_t card_index) const {
  assert(starts_object(card_index), "Can't get first start because no object starts here");
  return _object_starts[card_index].offsets.first & FirstStartBits;
}

size_t ShenandoahCardCluster::get_last_start(size_t card_index) const {
  assert(starts_object(card_index), "Can't get last start because no object starts here");
  return _object_starts[card_index].offsets.last;
}

// Given a card_index, return the starting address of the first block in the heap
// that straddles into this card. If this card is co-initial with an object, then
// this would return the first address of the range that this card covers, which is
// where the card's first object also begins.
HeapWord* ShenandoahCardCluster::block_start(const size_t card_index) const {

  HeapWord* left = _rs->addr_for_card_index(card_index);

#ifdef ASSERT
  assert(ShenandoahHeap::heap()->mode()->is_generational(), "Do not use in non-generational mode");
  ShenandoahHeapRegion* region = ShenandoahHeap::heap()->heap_region_containing(left);
  assert(region->is_old(), "Do not use for young regions");
  // For HumongousRegion:s it's more efficient to jump directly to the
  // start region.
  assert(!region->is_humongous(), "Use region->humongous_start_region() instead");
#endif
  if (starts_object(card_index) && get_first_start(card_index) == 0) {
    // This card contains a co-initial object; a fortiori, it covers
    // also the case of a card being the first in a region.
    assert(oopDesc::is_oop(cast_to_oop(left)), "Should be an object");
    return left;
  }

  HeapWord* p = nullptr;
  oop obj = cast_to_oop(p);
  ssize_t cur_index = (ssize_t)card_index;
  assert(cur_index >= 0, "Overflow");
  assert(cur_index > 0, "Should have returned above");
  // Walk backwards over the cards...
  while (--cur_index > 0 && !starts_object(cur_index)) {
   // ... to the one that starts the object
  }
  // cur_index should start an object: we should not have walked
  // past the left end of the region.
  assert(cur_index >= 0 && (cur_index <= (ssize_t)card_index), "Error");
  assert(region->bottom() <= _rs->addr_for_card_index(cur_index),
         "Fell off the bottom of containing region");
  assert(starts_object(cur_index), "Error");
  size_t offset = get_last_start(cur_index);
  // can avoid call via card size arithmetic below instead
  p = _rs->addr_for_card_index(cur_index) + offset;
  // Recall that we already dealt with the co-initial object case above
  assert(p < left, "obj should start before left");
  // While it is safe to ask an object its size in the loop that
  // follows, the (ifdef'd out) loop should never be needed.
  // 1. we ask this question only for regions in the old generation
  // 2. there is no direct allocation ever by mutators in old generation
  //    regions. Only GC will ever allocate in old regions, and then
  //    too only during promotion/evacuation phases. Thus there is no danger
  //    of races between reading from and writing to the object start array,
  //    or of asking partially initialized objects their size (in the loop below).
  // 3. only GC asks this question during phases when it is not concurrently
  //    evacuating/promoting, viz. during concurrent root scanning (before
  //    the evacuation phase) and during concurrent update refs (after the
  //    evacuation phase) of young collections. This is never called
  //    during old or global collections.
  // 4. Every allocation under TAMS updates the object start array.
  NOT_PRODUCT(obj = cast_to_oop(p);)
  assert(oopDesc::is_oop(obj), "Should be an object");
#define WALK_FORWARD_IN_BLOCK_START false
  while (WALK_FORWARD_IN_BLOCK_START && p + obj->size() < left) {
    p += obj->size();
  }
#undef WALK_FORWARD_IN_BLOCK_START // false
  assert(p + obj->size() > left, "obj should end after left");
  return p;
}

size_t ShenandoahScanRemembered::card_index_for_addr(HeapWord* p) {
  return _rs->card_index_for_addr(p);
}

HeapWord* ShenandoahScanRemembered::addr_for_card_index(size_t card_index) {
  return _rs->addr_for_card_index(card_index);
}

bool ShenandoahScanRemembered::is_card_dirty(size_t card_index) {
  return _rs->is_card_dirty(card_index);
}

bool ShenandoahScanRemembered::is_write_card_dirty(size_t card_index) {
  return _rs->is_write_card_dirty(card_index);
}

bool ShenandoahScanRemembered::is_card_dirty(HeapWord* p) {
  return _rs->is_card_dirty(p);
}

void ShenandoahScanRemembered::mark_card_as_dirty(HeapWord* p) {
  _rs->mark_card_as_dirty(p);
}

bool ShenandoahScanRemembered::is_write_card_dirty(HeapWord* p) {
  return _rs->is_write_card_dirty(p);
}

void ShenandoahScanRemembered::mark_range_as_dirty(HeapWord* p, size_t num_heap_words) {
  _rs->mark_range_as_dirty(p, num_heap_words);
}

void ShenandoahScanRemembered::mark_range_as_clean(HeapWord* p, size_t num_heap_words) {
  _rs->mark_range_as_clean(p, num_heap_words);
}

void ShenandoahScanRemembered::mark_read_table_as_clean() {
  _rs->mark_read_table_as_clean();
}

void ShenandoahScanRemembered::reset_object_range(HeapWord* from, HeapWord* to) {
  _scc->reset_object_range(from, to);
}

void ShenandoahScanRemembered::register_object(HeapWord* addr) {
  _scc->register_object(addr);
}

void ShenandoahScanRemembered::register_object_without_lock(HeapWord* addr) {
  _scc->register_object_without_lock(addr);
}

bool ShenandoahScanRemembered::verify_registration(HeapWord* address, ShenandoahMarkingContext* ctx) {

  size_t index = card_index_for_addr(address);
  if (!_scc->starts_object(index)) {
    return false;
  }
  HeapWord* base_addr = addr_for_card_index(index);
  size_t offset = _scc->get_first_start(index);
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  // Verify that I can find this object within its enclosing card by scanning forward from first_start.
  while (base_addr + offset < address) {
    oop obj = cast_to_oop(base_addr + offset);
    if (!ctx || ctx->is_marked(obj)) {
      offset += obj->size();
    } else {
      // If this object is not live, don't trust its size(); all objects above tams are live.
      ShenandoahHeapRegion* r = heap->heap_region_containing(obj);
      HeapWord* tams = ctx->top_at_mark_start(r);
      offset = ctx->get_next_marked_addr(base_addr + offset, tams) - base_addr;
    }
  }
  if (base_addr + offset != address){
    return false;
  }

  // At this point, offset represents object whose registration we are verifying.  We know that at least this object resides
  // within this card's memory.

  // Make sure that last_offset is properly set for the enclosing card, but we can't verify this for
  // candidate collection-set regions during mixed evacuations, so disable this check in general
  // during mixed evacuations.

  ShenandoahHeapRegion* r = heap->heap_region_containing(base_addr + offset);
  size_t max_offset = r->top() - base_addr;
  if (max_offset > CardTable::card_size_in_words()) {
    max_offset = CardTable::card_size_in_words();
  }
  size_t prev_offset;
  if (!ctx) {
    do {
      oop obj = cast_to_oop(base_addr + offset);
      prev_offset = offset;
      offset += obj->size();
    } while (offset < max_offset);
    if (_scc->get_last_start(index) != prev_offset) {
      return false;
    }

    // base + offset represents address of first object that starts on following card, if there is one.

    // Notes: base_addr is addr_for_card_index(index)
    //        base_addr + offset is end of the object we are verifying
    //        cannot use card_index_for_addr(base_addr + offset) because it asserts arg < end of whole heap
    size_t end_card_index = index + offset / CardTable::card_size_in_words();

    if (end_card_index > index && end_card_index <= _rs->last_valid_index()) {
      // If there is a following object registered on the next card, it should begin where this object ends.
      if (_scc->starts_object(end_card_index) &&
          ((addr_for_card_index(end_card_index) + _scc->get_first_start(end_card_index)) != (base_addr + offset))) {
        return false;
      }
    }

    // Assure that no other objects are registered "inside" of this one.
    for (index++; index < end_card_index; index++) {
      if (_scc->starts_object(index)) {
        return false;
      }
    }
  } else {
    // This is a mixed evacuation or a global collect: rely on mark bits to identify which objects need to be properly registered
    assert(!ShenandoahHeap::heap()->is_concurrent_old_mark_in_progress(), "Cannot rely on mark context here.");
    // If the object reaching or spanning the end of this card's memory is marked, then last_offset for this card
    // should represent this object.  Otherwise, last_offset is a don't care.
    ShenandoahHeapRegion* region = heap->heap_region_containing(base_addr + offset);
    HeapWord* tams = ctx->top_at_mark_start(region);
    oop last_obj = nullptr;
    do {
      oop obj = cast_to_oop(base_addr + offset);
      if (ctx->is_marked(obj)) {
        prev_offset = offset;
        offset += obj->size();
        last_obj = obj;
      } else {
        offset = ctx->get_next_marked_addr(base_addr + offset, tams) - base_addr;
        // If there are no marked objects remaining in this region, offset equals tams - base_addr.  If this offset is
        // greater than max_offset, we will immediately exit this loop.  Otherwise, the next iteration of the loop will
        // treat the object at offset as marked and live (because address >= tams) and we will continue iterating object
        // by consulting the size() fields of each.
      }
    } while (offset < max_offset);
    if (last_obj != nullptr && prev_offset + last_obj->size() >= max_offset) {
      // last marked object extends beyond end of card
      if (_scc->get_last_start(index) != prev_offset) {
        return false;
      }
      // otherwise, the value of _scc->get_last_start(index) is a don't care because it represents a dead object and we
      // cannot verify its context
    }
  }
  return true;
}

void ShenandoahScanRemembered::coalesce_objects(HeapWord* addr, size_t length_in_words) {
  _scc->coalesce_objects(addr, length_in_words);
}

void ShenandoahScanRemembered::mark_range_as_empty(HeapWord* addr, size_t length_in_words) {
  _rs->mark_range_as_clean(addr, length_in_words);
  _scc->clear_objects_in_range(addr, length_in_words);
}

size_t ShenandoahScanRemembered::cluster_for_addr(HeapWordImpl **addr) {
  size_t card_index = _rs->card_index_for_addr(addr);
  size_t result = card_index / ShenandoahCardCluster::CardsPerCluster;
  return result;
}

HeapWord* ShenandoahScanRemembered::addr_for_cluster(size_t cluster_no) {
  size_t card_index = cluster_no * ShenandoahCardCluster::CardsPerCluster;
  return addr_for_card_index(card_index);
}

// This is used only for debug verification so don't worry about making the scan parallel.
void ShenandoahScanRemembered::roots_do(OopIterateClosure* cl) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  bool old_bitmap_stable = heap->old_generation()->is_mark_complete();
  log_debug(gc, remset)("Scan remembered set using bitmap: %s", BOOL_TO_STR(old_bitmap_stable));
  for (size_t i = 0, n = heap->num_regions(); i < n; ++i) {
    ShenandoahHeapRegion* region = heap->get_region(i);
    if (region->is_old() && region->is_active() && !region->is_cset()) {
      HeapWord* start_of_range = region->bottom();
      HeapWord* end_of_range = region->top();
      size_t start_cluster_no = cluster_for_addr(start_of_range);
      size_t num_heapwords = end_of_range - start_of_range;
      unsigned int cluster_size = CardTable::card_size_in_words() * ShenandoahCardCluster::CardsPerCluster;
      size_t num_clusters = (size_t) ((num_heapwords - 1 + cluster_size) / cluster_size);

      // Remembered set scanner
      if (region->is_humongous()) {
        process_humongous_clusters(region->humongous_start_region(), start_cluster_no, num_clusters, end_of_range, cl,
                                   false /* use_write_table */);
      } else {
        process_clusters(start_cluster_no, num_clusters, end_of_range, cl,
                         false /* use_write_table */, 0 /* fake worker id */);
      }
    }
  }
}

#ifndef PRODUCT
// Log given card stats
void ShenandoahScanRemembered::log_card_stats(HdrSeq* stats) {
  for (int i = 0; i < MAX_CARD_STAT_TYPE; i++) {
    log_info(gc, remset)("%18s: [ %8.2f %8.2f %8.2f %8.2f %8.2f ]",
      _card_stats_name[i],
      stats[i].percentile(0), stats[i].percentile(25),
      stats[i].percentile(50), stats[i].percentile(75),
      stats[i].maximum());
  }
}

// Log card stats for all nworkers for a specific phase t
void ShenandoahScanRemembered::log_card_stats(uint nworkers, CardStatLogType t) {
  assert(ShenandoahEnableCardStats, "Do not call");
  HdrSeq* sum_stats = card_stats_for_phase(t);
  log_info(gc, remset)("%s", _card_stat_log_type[t]);
  for (uint i = 0; i < nworkers; i++) {
    log_worker_card_stats(i, sum_stats);
  }

  // Every so often, log the cumulative global stats
  if (++_card_stats_log_counter[t] >= ShenandoahCardStatsLogInterval) {
    _card_stats_log_counter[t] = 0;
    log_info(gc, remset)("Cumulative stats");
    log_card_stats(sum_stats);
  }
}

// Log card stats for given worker_id, & clear them after merging into given cumulative stats
void ShenandoahScanRemembered::log_worker_card_stats(uint worker_id, HdrSeq* sum_stats) {
  assert(ShenandoahEnableCardStats, "Do not call");

  HdrSeq* worker_card_stats = card_stats(worker_id);
  log_info(gc, remset)("Worker %u Card Stats: ", worker_id);
  log_card_stats(worker_card_stats);
  // Merge worker stats into the cumulative stats & clear worker stats
  merge_worker_card_stats_cumulative(worker_card_stats, sum_stats);
}

void ShenandoahScanRemembered::merge_worker_card_stats_cumulative(
  HdrSeq* worker_stats, HdrSeq* sum_stats) {
  for (int i = 0; i < MAX_CARD_STAT_TYPE; i++) {
    sum_stats[i].add(worker_stats[i]);
    worker_stats[i].clear();
  }
}
#endif

// A closure that takes an oop in the old generation and, if it's pointing
// into the young generation, dirties the corresponding remembered set entry.
// This is only used to rebuild the remembered set after a full GC.
class ShenandoahDirtyRememberedSetClosure : public BasicOopIterateClosure {
protected:
  ShenandoahGenerationalHeap* const _heap;
  ShenandoahScanRemembered*   const _scanner;

public:
  ShenandoahDirtyRememberedSetClosure() :
          _heap(ShenandoahGenerationalHeap::heap()),
          _scanner(_heap->old_generation()->card_scan()) {}

  template<class T>
  inline void work(T* p) {
    assert(_heap->is_in_old(p), "Expecting to get an old gen address");
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      if (_heap->is_in_young(obj)) {
        // Dirty the card containing the cross-generational pointer.
        _scanner->mark_card_as_dirty((HeapWord*) p);
      }
    }
  }

  virtual void do_oop(narrowOop* p) { work(p); }
  virtual void do_oop(oop* p)       { work(p); }
};

ShenandoahDirectCardMarkRememberedSet::ShenandoahDirectCardMarkRememberedSet(ShenandoahCardTable* card_table, size_t total_card_count) :
  LogCardValsPerIntPtr(log2i_exact(sizeof(intptr_t)) - log2i_exact(sizeof(CardValue))),
  LogCardSizeInWords(log2i_exact(CardTable::card_size_in_words())) {

  // Paranoid assert for LogCardsPerIntPtr calculation above
  assert(sizeof(intptr_t) > sizeof(CardValue), "LogsCardValsPerIntPtr would underflow");

  _heap = ShenandoahHeap::heap();
  _card_table = card_table;
  _total_card_count = total_card_count;
  _card_shift = CardTable::card_shift();

  _byte_map = _card_table->byte_for_index(0);

  _whole_heap_base = _card_table->addr_for(_byte_map);
  _byte_map_base = _byte_map - (uintptr_t(_whole_heap_base) >> _card_shift);

  assert(total_card_count % ShenandoahCardCluster::CardsPerCluster == 0, "Invalid card count.");
  assert(total_card_count > 0, "Card count cannot be zero.");
}

// Merge any dirty values from write table into the read table, while leaving
// the write table unchanged.
void ShenandoahDirectCardMarkRememberedSet::merge_write_table(HeapWord* start, size_t word_count) {
  size_t start_index = card_index_for_addr(start);
#ifdef ASSERT
  // avoid querying card_index_for_addr() for an address past end of heap
  size_t end_index = card_index_for_addr(start + word_count - 1) + 1;
#endif
  assert(start_index % ((size_t)1 << LogCardValsPerIntPtr) == 0, "Expected a multiple of CardValsPerIntPtr");
  assert(end_index % ((size_t)1 << LogCardValsPerIntPtr) == 0, "Expected a multiple of CardValsPerIntPtr");

  // We'll access in groups of intptr_t worth of card entries
  intptr_t* const read_table  = (intptr_t*) &(_card_table->read_byte_map())[start_index];
  intptr_t* const write_table = (intptr_t*) &(_card_table->write_byte_map())[start_index];

  // Avoid division, use shift instead
  assert(word_count % ((size_t)1 << (LogCardSizeInWords + LogCardValsPerIntPtr)) == 0, "Expected a multiple of CardSizeInWords*CardValsPerIntPtr");
  size_t const num = word_count >> (LogCardSizeInWords + LogCardValsPerIntPtr);

  for (size_t i = 0; i < num; i++) {
    read_table[i] &= write_table[i];
  }

  log_develop_debug(gc, remset)("Finished merging write_table into read_table.");
}

void ShenandoahDirectCardMarkRememberedSet::swap_card_tables() {
  CardTable::CardValue* new_ptr = _card_table->swap_read_and_write_tables();

#ifdef ASSERT
  CardValue* start_bp = &(_card_table->write_byte_map())[0];
  CardValue* end_bp = &(start_bp[_card_table->last_valid_index()]);

  while (start_bp <= end_bp) {
    assert(*start_bp == CardTable::clean_card_val(), "Should be clean: " PTR_FORMAT, p2i(start_bp));
    start_bp++;
  }
#endif

  struct SwapTLSCardTable : public ThreadClosure {
    CardTable::CardValue* _new_ptr;
    SwapTLSCardTable(CardTable::CardValue* np) : _new_ptr(np) {}
    virtual void do_thread(Thread* t) {
      ShenandoahThreadLocalData::set_card_table(t, _new_ptr);
    }
  } swap_it(new_ptr);

  // Iterate on threads and adjust thread local data
  Threads::threads_do(&swap_it);

  log_develop_debug(gc, barrier)("Current write_card_table: " PTR_FORMAT, p2i(swap_it._new_ptr));
}

ShenandoahScanRememberedTask::ShenandoahScanRememberedTask(ShenandoahObjToScanQueueSet* queue_set,
                                                           ShenandoahObjToScanQueueSet* old_queue_set,
                                                           ShenandoahReferenceProcessor* rp,
                                                           ShenandoahRegionChunkIterator* work_list, bool is_concurrent) :
  WorkerTask("Scan Remembered Set"),
  _queue_set(queue_set), _old_queue_set(old_queue_set), _rp(rp), _work_list(work_list), _is_concurrent(is_concurrent) {
  bool old_bitmap_stable = ShenandoahHeap::heap()->old_generation()->is_mark_complete();
  log_debug(gc, remset)("Scan remembered set using bitmap: %s", BOOL_TO_STR(old_bitmap_stable));
}

void ShenandoahScanRememberedTask::work(uint worker_id) {
  if (_is_concurrent) {
    // This sets up a thread local reference to the worker_id which is needed by the weak reference processor.
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    ShenandoahSuspendibleThreadSetJoiner stsj;
    do_work(worker_id);
  } else {
    // This sets up a thread local reference to the worker_id which is needed by the weak reference processor.
    ShenandoahParallelWorkerSession worker_session(worker_id);
    do_work(worker_id);
  }
}

void ShenandoahScanRememberedTask::do_work(uint worker_id) {
  ShenandoahWorkerTimingsTracker x(ShenandoahPhaseTimings::init_scan_rset, ShenandoahPhaseTimings::ScanClusters, worker_id);

  ShenandoahObjToScanQueue* q = _queue_set->queue(worker_id);
  ShenandoahObjToScanQueue* old = _old_queue_set == nullptr ? nullptr : _old_queue_set->queue(worker_id);
  ShenandoahMarkRefsClosure<YOUNG> cl(q, _rp, old);
  ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
  ShenandoahScanRemembered* scanner = heap->old_generation()->card_scan();

  // set up thread local closure for shen ref processor
  _rp->set_mark_closure(worker_id, &cl);
  struct ShenandoahRegionChunk assignment;
  while (_work_list->next(&assignment)) {
    ShenandoahHeapRegion* region = assignment._r;
    log_debug(gc)("ShenandoahScanRememberedTask::do_work(%u), processing slice of region "
                  "%zu at offset %zu, size: %zu",
                  worker_id, region->index(), assignment._chunk_offset, assignment._chunk_size);
    if (region->is_old()) {
      size_t cluster_size =
        CardTable::card_size_in_words() * ShenandoahCardCluster::CardsPerCluster;
      size_t clusters = assignment._chunk_size / cluster_size;
      assert(clusters * cluster_size == assignment._chunk_size, "Chunk assignments must align on cluster boundaries");
      HeapWord* end_of_range = region->bottom() + assignment._chunk_offset + assignment._chunk_size;

      // During concurrent mark, region->top() equals TAMS with respect to the current young-gen pass.
      if (end_of_range > region->top()) {
        end_of_range = region->top();
      }
      scanner->process_region_slice(region, assignment._chunk_offset, clusters, end_of_range, &cl, false, worker_id);
    }
#ifdef ENABLE_REMEMBERED_SET_CANCELLATION
    // This check is currently disabled to avoid crashes that occur
    // when we try to cancel remembered set scanning; it should be re-enabled
    // after the issues are fixed, as it would allow more prompt cancellation and
    // transition to degenerated / full GCs. Note that work that has been assigned/
    // claimed above must be completed before we return here upon cancellation.
    if (heap->check_cancelled_gc_and_yield(_is_concurrent)) {
      return;
    }
#endif
  }
}

size_t ShenandoahRegionChunkIterator::calc_regular_group_size() {
  // The group size is calculated from the number of regions.  Suppose the heap has N regions.  The first group processes
  // N/2 regions.  The second group processes N/4 regions, the third group N/8 regions and so on.
  // Note that infinite series N/2 + N/4 + N/8 + N/16 + ...  sums to N.
  //
  // The normal group size is the number of regions / 2.
  //
  // In the case that the region_size_words is greater than _maximum_chunk_size_words, the first group_size is
  // larger than the normal group size because each chunk in the group will be smaller than the region size.
  //
  // The last group also has more than the normal entries because it finishes the total scanning effort.  The chunk sizes are
  // different for each group.  The intention is that the first group processes roughly half of the heap, the second processes
  // half of the remaining heap, the third processes half of what remains and so on.  The smallest chunk size
  // is represented by _smallest_chunk_size_words.  We do not divide work any smaller than this.
  //
  size_t group_size = _heap->num_regions() / 2;
  return group_size;
}

size_t ShenandoahRegionChunkIterator::calc_first_group_chunk_size_b4_rebalance() {
  size_t words_in_first_chunk = ShenandoahHeapRegion::region_size_words();
  return words_in_first_chunk;
}

size_t ShenandoahRegionChunkIterator::calc_num_groups() {
  size_t total_heap_size = _heap->num_regions() * ShenandoahHeapRegion::region_size_words();
  size_t num_groups = 0;
  size_t cumulative_group_span = 0;
  size_t current_group_span = _first_group_chunk_size_b4_rebalance * _regular_group_size;
  size_t smallest_group_span = smallest_chunk_size_words() * _regular_group_size;
  while ((num_groups < _maximum_groups) && (cumulative_group_span + current_group_span <= total_heap_size)) {
    num_groups++;
    cumulative_group_span += current_group_span;
    if (current_group_span <= smallest_group_span) {
      break;
    } else {
      current_group_span /= 2;    // Each group spans half of what the preceding group spanned.
    }
  }
  // Loop post condition:
  //   num_groups <= _maximum_groups
  //   cumulative_group_span is the memory spanned by num_groups
  //   current_group_span is the span of the last fully populated group (assuming loop iterates at least once)
  //   each of num_groups is fully populated with _regular_group_size chunks in each
  // Non post conditions:
  //   cumulative_group_span may be less than total_heap size for one or more of the folowing reasons
  //   a) The number of regions remaining to be spanned is smaller than a complete group, or
  //   b) We have filled up all groups through _maximum_groups and still have not spanned all regions

  if (cumulative_group_span < total_heap_size) {
    // We've got more regions to span
    if ((num_groups < _maximum_groups) && (current_group_span > smallest_group_span)) {
      num_groups++;             // Place all remaining regions into a new not-full group (chunk_size half that of previous group)
    }
    // Else we are unable to create a new group because we've exceed the number of allowed groups or have reached the
    // minimum chunk size.

    // Any remaining regions will be treated as if they are part of the most recently created group.  This group will
    // have more than _regular_group_size chunks within it.
  }
  assert (num_groups <= _maximum_groups, "Cannot have more than %zu groups", _maximum_groups);
  return num_groups;
}

size_t ShenandoahRegionChunkIterator::calc_total_chunks() {
  size_t region_size_words = ShenandoahHeapRegion::region_size_words();
  size_t unspanned_heap_size = _heap->num_regions() * region_size_words;
  size_t num_chunks = 0;
  size_t cumulative_group_span = 0;
  size_t current_group_span = _first_group_chunk_size_b4_rebalance * _regular_group_size;
  size_t smallest_group_span = smallest_chunk_size_words() * _regular_group_size;

  // The first group gets special handling because the first chunk size can be no larger than _maximum_chunk_size_words
  if (region_size_words > _maximum_chunk_size_words) {
    // In the case that we shrink the first group's chunk size, certain other groups will also be subsumed within the first group
    size_t effective_chunk_size = _first_group_chunk_size_b4_rebalance;
    uint coalesced_groups = 0;
    while (effective_chunk_size >= _maximum_chunk_size_words) {
      // Each iteration of this loop subsumes one original group into a new rebalanced initial group.
      num_chunks += current_group_span / _maximum_chunk_size_words;
      unspanned_heap_size -= current_group_span;
      effective_chunk_size /= 2;
      current_group_span /= 2;
      coalesced_groups++;
    }
    assert(effective_chunk_size * 2 == _maximum_chunk_size_words,
           "We assume _first_group_chunk_size_b4_rebalance is _maximum_chunk_size_words * a power of two");
    _largest_chunk_size_words = _maximum_chunk_size_words;
    _adjusted_num_groups = _num_groups - (coalesced_groups - 1);
  } else {
    num_chunks = _regular_group_size;
    unspanned_heap_size -= current_group_span;
    _largest_chunk_size_words = current_group_span / num_chunks;
    _adjusted_num_groups = _num_groups;
    current_group_span /= 2;
  }

  size_t spanned_groups = 1;
  while (unspanned_heap_size > 0) {
    if (current_group_span <= unspanned_heap_size) {
      unspanned_heap_size -= current_group_span;
      num_chunks += _regular_group_size;
      spanned_groups++;

      // _num_groups is the number of groups required to span the configured heap size.  We are not allowed
      // to change the number of groups.  The last group is responsible for spanning all chunks not spanned
      // by previously processed groups.
      if (spanned_groups >= _num_groups) {
        // The last group has more than _regular_group_size entries.
        size_t chunk_span = current_group_span / _regular_group_size;
        size_t extra_chunks = unspanned_heap_size / chunk_span;
        assert (extra_chunks * chunk_span == unspanned_heap_size, "Chunks must precisely span regions");
        num_chunks += extra_chunks;
        return num_chunks;
      } else if (current_group_span <= smallest_group_span) {
        // We cannot introduce new groups because we've reached the lower bound on group size.  So this last
        // group may hold extra chunks.
        size_t chunk_span = smallest_chunk_size_words();
        size_t extra_chunks = unspanned_heap_size / chunk_span;
        assert (extra_chunks * chunk_span == unspanned_heap_size, "Chunks must precisely span regions");
        num_chunks += extra_chunks;
        return num_chunks;
      } else {
        current_group_span /= 2;
      }
    } else {
      // This last group has fewer than _regular_group_size entries.
      size_t chunk_span = current_group_span / _regular_group_size;
      size_t last_group_size = unspanned_heap_size / chunk_span;
      assert (last_group_size * chunk_span == unspanned_heap_size, "Chunks must precisely span regions");
      num_chunks += last_group_size;
      return num_chunks;
    }
  }
  return num_chunks;
}

ShenandoahRegionChunkIterator::ShenandoahRegionChunkIterator(size_t worker_count) :
    ShenandoahRegionChunkIterator(ShenandoahHeap::heap(), worker_count)
{
}

ShenandoahRegionChunkIterator::ShenandoahRegionChunkIterator(ShenandoahHeap* heap, size_t worker_count) :
    _heap(heap),
    _regular_group_size(calc_regular_group_size()),
    _first_group_chunk_size_b4_rebalance(calc_first_group_chunk_size_b4_rebalance()),
    _num_groups(calc_num_groups()),
    _total_chunks(calc_total_chunks()),
    _index(0)
{
#ifdef ASSERT
  size_t expected_chunk_size_words = _clusters_in_smallest_chunk * CardTable::card_size_in_words() * ShenandoahCardCluster::CardsPerCluster;
  assert(smallest_chunk_size_words() == expected_chunk_size_words, "_smallest_chunk_size (%zu) is not valid because it does not equal (%zu)",
         smallest_chunk_size_words(), expected_chunk_size_words);
  assert(_num_groups <= _maximum_groups,
         "The number of remembered set scanning groups must be less than or equal to maximum groups");
  assert(smallest_chunk_size_words() << (_adjusted_num_groups - 1) == _largest_chunk_size_words,
         "The number of groups (%zu) needs to span smallest chunk size (%zu) to largest chunk size (%zu)",
         _adjusted_num_groups, smallest_chunk_size_words(), _largest_chunk_size_words);
#endif

  size_t words_in_region = ShenandoahHeapRegion::region_size_words();
  _region_index[0] = 0;
  _group_offset[0] = 0;
  if (words_in_region > _maximum_chunk_size_words) {
    // In the case that we shrink the first group's chunk size, certain other groups will also be subsumed within the first group
    size_t num_chunks = 0;
    size_t effective_chunk_size = _first_group_chunk_size_b4_rebalance;
    size_t  current_group_span = effective_chunk_size * _regular_group_size;
    while (effective_chunk_size >= _maximum_chunk_size_words) {
      num_chunks += current_group_span / _maximum_chunk_size_words;
      effective_chunk_size /= 2;
      current_group_span /= 2;
    }
    _group_entries[0] = num_chunks;
    _group_chunk_size[0] = _maximum_chunk_size_words;
  } else {
    _group_entries[0] = _regular_group_size;
    _group_chunk_size[0] = _first_group_chunk_size_b4_rebalance;
  }

  size_t previous_group_span = _group_entries[0] * _group_chunk_size[0];
  for (size_t i = 1; i < _adjusted_num_groups; i++) {
    _group_chunk_size[i] = _group_chunk_size[i-1] / 2;
    size_t chunks_in_group = _regular_group_size;
    size_t this_group_span = _group_chunk_size[i] * chunks_in_group;
    size_t total_span_of_groups = previous_group_span + this_group_span;
    _region_index[i] = previous_group_span / words_in_region;
    _group_offset[i] = previous_group_span % words_in_region;
    _group_entries[i] = _group_entries[i-1] + _regular_group_size;
    previous_group_span = total_span_of_groups;
  }
  if (_group_entries[_adjusted_num_groups-1] < _total_chunks) {
    assert((_total_chunks - _group_entries[_adjusted_num_groups-1]) * _group_chunk_size[_adjusted_num_groups-1] + previous_group_span ==
           heap->num_regions() * words_in_region, "Total region chunks (%zu"
           ") do not span total heap regions (%zu)", _total_chunks, _heap->num_regions());
    previous_group_span += (_total_chunks - _group_entries[_adjusted_num_groups-1]) * _group_chunk_size[_adjusted_num_groups-1];
    _group_entries[_adjusted_num_groups-1] = _total_chunks;
  }
  assert(previous_group_span == heap->num_regions() * words_in_region, "Total region chunks (%zu"
         ") do not span total heap regions (%zu): %zu does not equal %zu",
         _total_chunks, _heap->num_regions(), previous_group_span, heap->num_regions() * words_in_region);

  // Not necessary, but keeps things tidy
  for (size_t i = _adjusted_num_groups; i < _maximum_groups; i++) {
    _region_index[i] = 0;
    _group_offset[i] = 0;
    _group_entries[i] = _group_entries[i-1];
    _group_chunk_size[i] = 0;
  }
}

void ShenandoahRegionChunkIterator::reset() {
  _index = 0;
}

ShenandoahReconstructRememberedSetTask::ShenandoahReconstructRememberedSetTask(ShenandoahRegionIterator* regions)
  : WorkerTask("Shenandoah Reset Bitmap")
  , _regions(regions) { }

void ShenandoahReconstructRememberedSetTask::work(uint worker_id) {
  ShenandoahParallelWorkerSession worker_session(worker_id);
  ShenandoahHeapRegion* r = _regions->next();
  ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
  ShenandoahScanRemembered* scanner = heap->old_generation()->card_scan();
  ShenandoahDirtyRememberedSetClosure dirty_cards_for_cross_generational_pointers;

  while (r != nullptr) {
    if (r->is_old() && r->is_active()) {
      HeapWord* obj_addr = r->bottom();
      if (r->is_humongous_start()) {
        // First, clear the remembered set
        oop obj = cast_to_oop(obj_addr);
        size_t size = obj->size();

        size_t num_regions = ShenandoahHeapRegion::required_regions(size * HeapWordSize);
        size_t region_index = r->index();
        ShenandoahHeapRegion* humongous_region = heap->get_region(region_index);
        while (num_regions-- != 0) {
          scanner->reset_object_range(humongous_region->bottom(), humongous_region->end());
          region_index++;
          humongous_region = heap->get_region(region_index);
        }

        // Then register the humongous object and DIRTY relevant remembered set cards
        scanner->register_object_without_lock(obj_addr);
        obj->oop_iterate(&dirty_cards_for_cross_generational_pointers);
      } else if (!r->is_humongous()) {
        scanner->reset_object_range(r->bottom(), r->end());

        // Then iterate over all objects, registering object and DIRTYing relevant remembered set cards
        HeapWord* t = r->top();
        while (obj_addr < t) {
          oop obj = cast_to_oop(obj_addr);
          scanner->register_object_without_lock(obj_addr);
          obj_addr += obj->oop_iterate_size(&dirty_cards_for_cross_generational_pointers);
        }
      } // else, ignore humongous continuation region
    }
    // else, this region is FREE or YOUNG or inactive and we can ignore it.
    r = _regions->next();
  }
}
