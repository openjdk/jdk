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
 *
 */

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHSCANREMEMBEREDINLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHSCANREMEMBEREDINLINE_HPP

#include "memory/iterator.hpp"
#include "oops/oop.hpp"
#include "oops/objArrayOop.hpp"
#include "gc/shared/collectorCounters.hpp"
#include "gc/shenandoah/shenandoahCardStats.hpp"
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"

inline size_t
ShenandoahDirectCardMarkRememberedSet::last_valid_index() const {
  return _card_table->last_valid_index();
}

inline size_t
ShenandoahDirectCardMarkRememberedSet::total_cards() const {
  return _total_card_count;
}

inline size_t
ShenandoahDirectCardMarkRememberedSet::card_index_for_addr(HeapWord *p) const {
  return _card_table->index_for(p);
}

inline HeapWord*
ShenandoahDirectCardMarkRememberedSet::addr_for_card_index(size_t card_index) const {
  return _whole_heap_base + CardTable::card_size_in_words() * card_index;
}

inline const CardValue*
ShenandoahDirectCardMarkRememberedSet::get_card_table_byte_map(bool use_write_table) const {
  return use_write_table ?
           _card_table->write_byte_map()
           : _card_table->read_byte_map();
}

inline bool
ShenandoahDirectCardMarkRememberedSet::is_write_card_dirty(size_t card_index) const {
  CardValue* bp = &(_card_table->write_byte_map())[card_index];
  return (bp[0] == CardTable::dirty_card_val());
}

inline bool
ShenandoahDirectCardMarkRememberedSet::is_card_dirty(size_t card_index) const {
  CardValue* bp = &(_card_table->read_byte_map())[card_index];
  return (bp[0] == CardTable::dirty_card_val());
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_card_as_dirty(size_t card_index) {
  CardValue* bp = &(_card_table->write_byte_map())[card_index];
  bp[0] = CardTable::dirty_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_range_as_dirty(size_t card_index, size_t num_cards) {
  CardValue* bp = &(_card_table->write_byte_map())[card_index];
  while (num_cards-- > 0) {
    *bp++ = CardTable::dirty_card_val();
  }
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_card_as_clean(size_t card_index) {
  CardValue* bp = &(_card_table->write_byte_map())[card_index];
  bp[0] = CardTable::clean_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_range_as_clean(size_t card_index, size_t num_cards) {
  CardValue* bp = &(_card_table->write_byte_map())[card_index];
  while (num_cards-- > 0) {
    *bp++ = CardTable::clean_card_val();
  }
}

inline bool
ShenandoahDirectCardMarkRememberedSet::is_card_dirty(HeapWord *p) const {
  size_t index = card_index_for_addr(p);
  CardValue* bp = &(_card_table->read_byte_map())[index];
  return (bp[0] == CardTable::dirty_card_val());
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_card_as_dirty(HeapWord *p) {
  size_t index = card_index_for_addr(p);
  CardValue* bp = &(_card_table->write_byte_map())[index];
  bp[0] = CardTable::dirty_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_range_as_dirty(HeapWord *p, size_t num_heap_words) {
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

inline void
ShenandoahDirectCardMarkRememberedSet::mark_card_as_clean(HeapWord *p) {
  size_t index = card_index_for_addr(p);
  CardValue* bp = &(_card_table->write_byte_map())[index];
  bp[0] = CardTable::clean_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_range_as_clean(HeapWord *p, size_t num_heap_words) {
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

inline size_t
ShenandoahDirectCardMarkRememberedSet::cluster_count() const {
  return _cluster_count;
}

// No lock required because arguments align with card boundaries.
template<typename RememberedSet>
inline void
ShenandoahCardCluster<RememberedSet>::reset_object_range(HeapWord* from, HeapWord* to) {
  assert(((((unsigned long long) from) & (CardTable::card_size() - 1)) == 0) &&
         ((((unsigned long long) to) & (CardTable::card_size() - 1)) == 0),
         "reset_object_range bounds must align with card boundaries");
  size_t card_at_start = _rs->card_index_for_addr(from);
  size_t num_cards = (to - from) / CardTable::card_size_in_words();

  for (size_t i = 0; i < num_cards; i++) {
    object_starts[card_at_start + i].short_word = 0;
  }
}

// Assume only one thread at a time registers objects pertaining to
// each card-table entry's range of memory.
template<typename RememberedSet>
inline void
ShenandoahCardCluster<RememberedSet>::register_object(HeapWord* address) {
  shenandoah_assert_heaplocked();

  register_object_without_lock(address);
}

template<typename RememberedSet>
inline void
ShenandoahCardCluster<RememberedSet>::register_object_without_lock(HeapWord* address) {
  size_t card_at_start = _rs->card_index_for_addr(address);
  HeapWord *card_start_address = _rs->addr_for_card_index(card_at_start);
  uint8_t offset_in_card = address - card_start_address;

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

template<typename RememberedSet>
inline void
ShenandoahCardCluster<RememberedSet>::coalesce_objects(HeapWord* address, size_t length_in_words) {

  size_t card_at_start = _rs->card_index_for_addr(address);
  HeapWord *card_start_address = _rs->addr_for_card_index(card_at_start);
  size_t card_at_end = card_at_start + ((address + length_in_words) - card_start_address) / CardTable::card_size_in_words();

  if (card_at_start == card_at_end) {
    // There are no changes to the get_first_start array.  Either get_first_start(card_at_start) returns this coalesced object,
    // or it returns an object that precedes the coalesced object.
    if (card_start_address + get_last_start(card_at_start) < address + length_in_words) {
      uint8_t coalesced_offset = static_cast<uint8_t>(address - card_start_address);
      // The object that used to be the last object starting within this card is being subsumed within the coalesced
      // object.  Since we always coalesce entire objects, this condition only occurs if the last object ends before or at
      // the end of the card's memory range and there is no object following this object.  In this case, adjust last_start
      // to represent the start of the coalesced range.
      set_last_start(card_at_start, coalesced_offset);
    }
    // Else, no changes to last_starts information.  Either get_last_start(card_at_start) returns the object that immediately
    // follows the coalesced object, or it returns an object that follows the object immediately following the coalesced object.
  } else {
    uint8_t coalesced_offset = static_cast<uint8_t>(address - card_start_address);
    if (get_last_start(card_at_start) > coalesced_offset) {
      // Existing last start is being coalesced, create new last start
      set_last_start(card_at_start, coalesced_offset);
    }
    // otherwise, get_last_start(card_at_start) must equal coalesced_offset

    // All the cards between first and last get cleared.
    for (size_t i = card_at_start + 1; i < card_at_end; i++) {
      clear_starts_object_bit(i);
    }

    uint8_t follow_offset = static_cast<uint8_t>((address + length_in_words) - _rs->addr_for_card_index(card_at_end));
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


template<typename RememberedSet>
inline size_t
ShenandoahCardCluster<RememberedSet>::get_first_start(size_t card_index) const {
  assert(starts_object(card_index), "Can't get first start because no object starts here");
  return object_starts[card_index].offsets.first & FirstStartBits;
}

template<typename RememberedSet>
inline size_t
ShenandoahCardCluster<RememberedSet>::get_last_start(size_t card_index) const {
  assert(starts_object(card_index), "Can't get last start because no object starts here");
  return object_starts[card_index].offsets.last;
}

// Given a card_index, return the starting address of the first block in the heap
// that straddles into this card. If this card is co-initial with an object, then
// this would return the first address of the range that this card covers, which is
// where the card's first object also begins.
// TODO: collect some stats for the size of walks backward over cards.
// For larger objects, a logarithmic BOT such as used by G1 might make the
// backwards walk potentially faster.
template<typename RememberedSet>
HeapWord*
ShenandoahCardCluster<RememberedSet>::block_start(const size_t card_index) const {

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

template<typename RememberedSet>
inline size_t
ShenandoahScanRemembered<RememberedSet>::last_valid_index() { return _rs->last_valid_index(); }

template<typename RememberedSet>
inline size_t
ShenandoahScanRemembered<RememberedSet>::total_cards() { return _rs->total_cards(); }

template<typename RememberedSet>
inline size_t
ShenandoahScanRemembered<RememberedSet>::card_index_for_addr(HeapWord *p) { return _rs->card_index_for_addr(p); }

template<typename RememberedSet>
inline HeapWord *
ShenandoahScanRemembered<RememberedSet>::addr_for_card_index(size_t card_index) { return _rs->addr_for_card_index(card_index); }

template<typename RememberedSet>
inline bool
ShenandoahScanRemembered<RememberedSet>::is_card_dirty(size_t card_index) { return _rs->is_card_dirty(card_index); }

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::mark_card_as_dirty(size_t card_index) { _rs->mark_card_as_dirty(card_index); }

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::mark_range_as_dirty(size_t card_index, size_t num_cards) { _rs->mark_range_as_dirty(card_index, num_cards); }

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::mark_card_as_clean(size_t card_index) { _rs->mark_card_as_clean(card_index); }

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::mark_range_as_clean(size_t card_index, size_t num_cards) { _rs->mark_range_as_clean(card_index, num_cards); }

template<typename RememberedSet>
inline bool
ShenandoahScanRemembered<RememberedSet>::is_card_dirty(HeapWord *p) { return _rs->is_card_dirty(p); }

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::mark_card_as_dirty(HeapWord *p) { _rs->mark_card_as_dirty(p); }

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::mark_range_as_dirty(HeapWord *p, size_t num_heap_words) { _rs->mark_range_as_dirty(p, num_heap_words); }

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::mark_card_as_clean(HeapWord *p) { _rs->mark_card_as_clean(p); }

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>:: mark_range_as_clean(HeapWord *p, size_t num_heap_words) { _rs->mark_range_as_clean(p, num_heap_words); }

template<typename RememberedSet>
inline size_t
ShenandoahScanRemembered<RememberedSet>::cluster_count() { return _rs->cluster_count(); }

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::reset_object_range(HeapWord *from, HeapWord *to) {
  _scc->reset_object_range(from, to);
}

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::register_object(HeapWord *addr) {
  _scc->register_object(addr);
}

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::register_object_without_lock(HeapWord *addr) {
  _scc->register_object_without_lock(addr);
}

template <typename RememberedSet>
inline bool
ShenandoahScanRemembered<RememberedSet>::verify_registration(HeapWord* address, ShenandoahMarkingContext* ctx) {

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

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::coalesce_objects(HeapWord *addr, size_t length_in_words) {
  _scc->coalesce_objects(addr, length_in_words);
}

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::mark_range_as_empty(HeapWord *addr, size_t length_in_words) {
  _rs->mark_range_as_clean(addr, length_in_words);
  _scc->clear_objects_in_range(addr, length_in_words);
}

// Process all objects starting within count clusters beginning with first_cluster and for which the start address is
// less than end_of_range.  For any non-array object whose header lies on a dirty card, scan the entire object,
// even if its end reaches beyond end_of_range. Object arrays, on the other hand, are precisely dirtied and
// only the portions of the array on dirty cards need to be scanned.
//
// Do not CANCEL within process_clusters.  It is assumed that if a worker thread accepts responsibility for processing
// a chunk of work, it will finish the work it starts.  Otherwise, the chunk of work will be lost in the transition to
// degenerated execution, leading to dangling references.
template<typename RememberedSet>
template <typename ClosureType>
void ShenandoahScanRemembered<RememberedSet>::process_clusters(size_t first_cluster, size_t count, HeapWord* end_of_range,
                                                               ClosureType* cl, bool use_write_table, uint worker_id) {

  // If old-gen evacuation is active, then MarkingContext for old-gen heap regions is valid.  We use the MarkingContext
  // bits to determine which objects within a DIRTY card need to be scanned.  This is necessary because old-gen heap
  // regions that are in the candidate collection set have not been coalesced and filled.  Thus, these heap regions
  // may contain zombie objects.  Zombie objects are known to be dead, but have not yet been "collected".  Scanning
  // zombie objects is unsafe because the Klass pointer is not reliable, objects referenced from a zombie may have been
  // collected (if dead), or relocated (if live), or if dead but not yet collected, we don't want to "revive" them
  // by marking them (when marking) or evacuating them (when updating references).

  // start and end addresses of range of objects to be scanned, clipped to end_of_range
  const size_t start_card_index = first_cluster * ShenandoahCardCluster<RememberedSet>::CardsPerCluster;
  const HeapWord* start_addr = _rs->addr_for_card_index(start_card_index);
  // clip at end_of_range (exclusive)
  HeapWord* end_addr = MIN2(end_of_range, (HeapWord*)start_addr + (count * ShenandoahCardCluster<RememberedSet>::CardsPerCluster
                                                                   * CardTable::card_size_in_words()));
  assert(start_addr < end_addr, "Empty region?");

  const size_t whole_cards = (end_addr - start_addr + CardTable::card_size_in_words() - 1)/CardTable::card_size_in_words();
  const size_t end_card_index = start_card_index + whole_cards - 1;
  log_debug(gc, remset)("Worker %u: cluster = " SIZE_FORMAT " count = " SIZE_FORMAT " eor = " INTPTR_FORMAT
                        " start_addr = " INTPTR_FORMAT " end_addr = " INTPTR_FORMAT " cards = " SIZE_FORMAT,
                        worker_id, first_cluster, count, p2i(end_of_range), p2i(start_addr), p2i(end_addr), whole_cards);

  // use_write_table states whether we are using the card table that is being
  // marked by the mutators. If false, we are using a snapshot of the card table
  // that is not subject to modifications. Even when this arg is true, and
  // the card table is being actively marked, SATB marking ensures that we need not
  // worry about cards marked after the processing here has passed them.
  const CardValue* const ctbm = _rs->get_card_table_byte_map(use_write_table);

  // If old gen evacuation is active, ctx will hold the completed marking of
  // old generation objects. We'll only scan objects that are marked live by
  // the old generation marking. These include objects allocated since the
  // start of old generation marking (being those above TAMS).
  const ShenandoahHeap* heap = ShenandoahHeap::heap();
  const ShenandoahMarkingContext* ctx = heap->is_old_bitmap_stable() ?
                                        heap->marking_context() : nullptr;

  // The region we will scan is the half-open interval [start_addr, end_addr),
  // and lies entirely within a single region.
  const ShenandoahHeapRegion* region = ShenandoahHeap::heap()->heap_region_containing(start_addr);
  assert(region->contains(end_addr - 1), "Slice shouldn't cross regions");

  // This code may have implicit assumptions of examining only old gen regions.
  assert(region->is_old(), "We only expect to be processing old regions");
  assert(!region->is_humongous(), "Humongous regions can be processed more efficiently;"
                                  "see process_humongous_clusters()");
  // tams and ctx below are for old generation marking. As such, young gen roots must
  // consider everything above tams, since it doesn't represent a TAMS for young gen's
  // SATB marking.
  const HeapWord* tams = (ctx == nullptr ? region->bottom() : ctx->top_at_mark_start(region));

  NOT_PRODUCT(ShenandoahCardStats stats(whole_cards, card_stats(worker_id));)

  // In the case of imprecise marking, we remember the lowest address
  // scanned in a range of dirty cards, as we work our way left from the
  // highest end_addr. This serves as another upper bound on the address we will
  // scan as we move left over each contiguous range of dirty cards.
  HeapWord* upper_bound = nullptr;

  // Starting at the right end of the address range, walk backwards accumulating
  // a maximal dirty range of cards, then process those cards.
  ssize_t cur_index = (ssize_t) end_card_index;
  assert(cur_index >= 0, "Overflow");
  assert(((ssize_t)start_card_index) >= 0, "Overflow");
  while (cur_index >= (ssize_t)start_card_index) {

    // We'll continue the search starting with the card for the upper bound
    // address identified by the last dirty range that we processed, if any,
    // skipping any cards at higher addresses.
    if (upper_bound != nullptr) {
      ssize_t right_index = _rs->card_index_for_addr(upper_bound);
      assert(right_index >= 0, "Overflow");
      cur_index = MIN2(cur_index, right_index);
      assert(upper_bound < end_addr, "Program logic");
      end_addr  = upper_bound;   // lower end_addr
      upper_bound = nullptr;     // and clear upper_bound
      if (end_addr <= start_addr) {
        assert(right_index <= (ssize_t)start_card_index, "Program logic");
        // We are done with our cluster
        return;
      }
    }

    if (ctbm[cur_index] == CardTable::dirty_card_val()) {
      // ==== BEGIN DIRTY card range processing ====

      const size_t dirty_r = cur_index;  // record right end of dirty range (inclusive)
      while (--cur_index >= (ssize_t)start_card_index && ctbm[cur_index] == CardTable::dirty_card_val()) {
        // walk back over contiguous dirty cards to find left end of dirty range (inclusive)
      }
      // [dirty_l, dirty_r] is a "maximal" closed interval range of dirty card indices:
      // it may not be maximal if we are using the write_table, because of concurrent
      // mutations dirtying the card-table. It may also not be maximal if an upper bound
      // was established by the scan of the previous chunk.
      const size_t dirty_l = cur_index + 1;   // record left end of dirty range (inclusive)
      // Check that we identified a boundary on our left
      assert(ctbm[dirty_l] == CardTable::dirty_card_val(), "First card in range should be dirty");
      assert(dirty_l == start_card_index || use_write_table
             || ctbm[dirty_l - 1] == CardTable::clean_card_val(),
             "Interval isn't maximal on the left");
      assert(dirty_r >= dirty_l, "Error");
      assert(ctbm[dirty_r] == CardTable::dirty_card_val(), "Last card in range should be dirty");
      // Record alternations, dirty run length, and dirty card count
      NOT_PRODUCT(stats.record_dirty_run(dirty_r - dirty_l + 1);)

      // Find first object that starts this range:
      // [left, right) is a maximal right-open interval of dirty cards
      HeapWord* left = _rs->addr_for_card_index(dirty_l);        // inclusive
      HeapWord* right = _rs->addr_for_card_index(dirty_r + 1);   // exclusive
      // Clip right to end_addr established above (still exclusive)
      right = MIN2(right, end_addr);
      assert(right <= region->top() && end_addr <= region->top(), "Busted bounds");
      const MemRegion mr(left, right);

      // NOTE: We'll not call block_start() repeatedly
      // on a very large object if its head card is dirty. If not,
      // (i.e. the head card is clean) we'll call it each time we
      // process a new dirty range on the object. This is always
      // the case for large object arrays, which are typically more
      // common.
      // TODO: It is worthwhile to memoize this, so as to avoid that
      // overhead, and it is easy to do, but deferred to a follow-up.
      HeapWord* p = _scc->block_start(dirty_l);
      oop obj = cast_to_oop(p);

      // PREFIX: The object that straddles into this range of dirty cards
      // from the left may be subject to special treatment unless
      // it is an object array.
      if (p < left && !obj->is_objArray()) {
        // The mutator (both compiler and interpreter, but not JNI?)
        // typically dirty imprecisely (i.e. only the head of an object),
        // but GC closures typically dirty the object precisely. (It would
        // be nice to have everything be precise for maximum efficiency.)
        //
        // To handle this, we check the head card of the object here and,
        // if dirty, (arrange to) scan the object in its entirety. If we
        // find the head card clean, we'll scan only the portion of the
        // object lying in the dirty card range below, assuming this was
        // the result of precise marking by GC closures.

        // index of the "head card" for p
        const size_t hc_index = _rs->card_index_for_addr(p);
        if (ctbm[hc_index] == CardTable::dirty_card_val()) {
          // Scan or skip the object, depending on location of its
          // head card, and remember that we'll have processed all
          // the objects back up to p, which is thus an upper bound
          // for the next iteration of a dirty card loop.
          upper_bound = p;   // remember upper bound for next chunk
          if (p < start_addr) {
            // if object starts in a previous slice, it'll be handled
            // in its entirety by the thread processing that slice; we can
            // skip over it and avoid an unnecessary extra scan.
            assert(obj == cast_to_oop(p), "Inconsistency detected");
            p += obj->size();
          } else {
            // the object starts in our slice, we scan it in its entirety
            assert(obj == cast_to_oop(p), "Inconsistency detected");
            if (ctx == nullptr || ctx->is_marked(obj)) {
              // Scan the object in its entirety
              p += obj->oop_iterate_size(cl);
            } else {
              assert(p < tams, "Error 1 in ctx/marking/tams logic");
              // Skip over any intermediate dead objects
              p = ctx->get_next_marked_addr(p, tams);
              assert(p <= tams, "Error 2 in ctx/marking/tams logic");
            }
          }
          assert(p > left, "Should have processed into interior of dirty range");
        }
      }

      size_t i = 0;
      HeapWord* last_p = nullptr;

      // BODY: Deal with (other) objects in this dirty card range
      while (p < right) {
        obj = cast_to_oop(p);
        // walk right scanning eligible objects
        if (ctx == nullptr || ctx->is_marked(obj)) {
          // we need to remember the last object ptr we scanned, in case we need to
          // complete a partial suffix scan after mr, see below
          last_p = p;
          // apply the closure to the oops in the portion of
          // the object within mr.
          p += obj->oop_iterate_size(cl, mr);
          NOT_PRODUCT(i++);
        } else {
          // forget the last object pointer we remembered
          last_p = nullptr;
          assert(p < tams, "Tams and above are implicitly marked in ctx");
          // object under tams isn't marked: skip to next live object
          p = ctx->get_next_marked_addr(p, tams);
          assert(p <= tams, "Error 3 in ctx/marking/tams logic");
        }
      }

      // TODO: if an objArray then only use mr, else just iterate over entire object;
      // that would avoid the special treatment of suffix below.

      // SUFFIX: Fix up a possible incomplete scan at right end of window
      // by scanning the portion of a non-objArray that wasn't done.
      if (p > right && last_p != nullptr) {
        assert(last_p < right, "Error");
        // check if last_p suffix needs scanning
        const oop last_obj = cast_to_oop(last_p);
        if (!last_obj->is_objArray()) {
          // scan the remaining suffix of the object
          const MemRegion last_mr(right, p);
          assert(p == last_p + last_obj->size(), "Would miss portion of last_obj");
          last_obj->oop_iterate(cl, last_mr);
          log_debug(gc, remset)("Fixed up non-objArray suffix scan in [" INTPTR_FORMAT ", " INTPTR_FORMAT ")",
                                p2i(last_mr.start()), p2i(last_mr.end()));
        } else {
          log_debug(gc, remset)("Skipped suffix scan of objArray in [" INTPTR_FORMAT ", " INTPTR_FORMAT ")",
                                p2i(right), p2i(p));
        }
      }
      NOT_PRODUCT(stats.record_scan_obj_cnt(i);)

      // ==== END   DIRTY card range processing ====
    } else {
      // ==== BEGIN CLEAN card range processing ====

      assert(ctbm[cur_index] == CardTable::clean_card_val(), "Error");
      // walk back over contiguous clean cards
      size_t i = 0;
      while (--cur_index >= (ssize_t)start_card_index && ctbm[cur_index] == CardTable::clean_card_val()) {
        NOT_PRODUCT(i++);
      }
      // Record alternations, clean run length, and clean card count
      NOT_PRODUCT(stats.record_clean_run(i);)

      // ==== END CLEAN card range processing ====
    }
  }
}

// Given that this range of clusters is known to span a humongous object spanned by region r, scan the
// portion of the humongous object that corresponds to the specified range.
template<typename RememberedSet>
template <typename ClosureType>
inline void
ShenandoahScanRemembered<RememberedSet>::process_humongous_clusters(ShenandoahHeapRegion* r, size_t first_cluster, size_t count,
                                                                    HeapWord *end_of_range, ClosureType *cl, bool use_write_table) {
  ShenandoahHeapRegion* start_region = r->humongous_start_region();
  HeapWord* p = start_region->bottom();
  oop obj = cast_to_oop(p);
  assert(r->is_humongous(), "Only process humongous regions here");
  assert(start_region->is_humongous_start(), "Should be start of humongous region");
  assert(p + obj->size() >= end_of_range, "Humongous object ends before range ends");

  size_t first_card_index = first_cluster * ShenandoahCardCluster<RememberedSet>::CardsPerCluster;
  HeapWord* first_cluster_addr = _rs->addr_for_card_index(first_card_index);
  size_t spanned_words = count * ShenandoahCardCluster<RememberedSet>::CardsPerCluster * CardTable::card_size_in_words();
  start_region->oop_iterate_humongous_slice(cl, true, first_cluster_addr, spanned_words, use_write_table);
}


// This method takes a region & determines the end of the region that the worker can scan.
template<typename RememberedSet>
template <typename ClosureType>
inline void
ShenandoahScanRemembered<RememberedSet>::process_region_slice(ShenandoahHeapRegion *region, size_t start_offset, size_t clusters,
                                                              HeapWord *end_of_range, ClosureType *cl, bool use_write_table,
                                                              uint worker_id) {

  // This is called only for young gen collection, when we scan old gen regions
  assert(region->is_old(), "Expecting an old region");
  HeapWord *start_of_range = region->bottom() + start_offset;
  size_t start_cluster_no = cluster_for_addr(start_of_range);
  assert(addr_for_cluster(start_cluster_no) == start_of_range, "process_region_slice range must align on cluster boundary");

  // region->end() represents the end of memory spanned by this region, but not all of this
  //   memory is eligible to be scanned because some of this memory has not yet been allocated.
  //
  // region->top() represents the end of allocated memory within this region.  Any addresses
  //   beyond region->top() should not be scanned as that memory does not hold valid objects.

  if (use_write_table) {
    // This is update-refs servicing.
    if (end_of_range > region->get_update_watermark()) {
      end_of_range = region->get_update_watermark();
    }
  } else {
    // This is concurrent mark servicing.  Note that TAMS for this region is TAMS at start of old-gen
    // collection.  Here, we need to scan up to TAMS for most recently initiated young-gen collection.
    // Since all LABs are retired at init mark, and since replacement LABs are allocated lazily, and since no
    // promotions occur until evacuation phase, TAMS for most recent young-gen is same as top().
    if (end_of_range > region->top()) {
      end_of_range = region->top();
    }
  }

  log_debug(gc)("Remembered set scan processing Region " SIZE_FORMAT ", from " PTR_FORMAT " to " PTR_FORMAT ", using %s table",
                region->index(), p2i(start_of_range), p2i(end_of_range),
                use_write_table? "read/write (updating)": "read (marking)");

  // Note that end_of_range may point to the middle of a cluster because we limit scanning to
  // region->top() or region->get_update_watermark(). We avoid processing past end_of_range.
  // Objects that start between start_of_range and end_of_range, including humongous objects, will
  // be fully processed by process_clusters. In no case should we need to scan past end_of_range.
  if (start_of_range < end_of_range) {
    if (region->is_humongous()) {
      ShenandoahHeapRegion* start_region = region->humongous_start_region();
      // TODO: ysr : This will be called multiple times with same start_region, but different start_cluster_no.
      // Check that it does the right thing here, and doesn't do redundant work. Also see if the call API/interface
      // can be simplified.
      process_humongous_clusters(start_region, start_cluster_no, clusters, end_of_range, cl, use_write_table);
    } else {
      // TODO: ysr The start_of_range calculated above is discarded and may be calculated again in process_clusters().
      // See if the redundant and wasted calculations can be avoided, and if the call parameters can be cleaned up.
      // It almost sounds like this set of methods needs a working class to stash away some useful info that can be
      // efficiently passed around amongst these methods, as well as related state. Note that we can't use
      // ShenandoahScanRemembered as there seems to be only one instance of that object for the heap which is shared
      // by all workers. Note that there are also task methods which call these which may have per worker storage.
      // We need to be careful however that if the number of workers changes dynamically that state isn't sequestered
      // and become obsolete.
      process_clusters(start_cluster_no, clusters, end_of_range, cl, use_write_table, worker_id);
    }
  }
}

template<typename RememberedSet>
inline size_t
ShenandoahScanRemembered<RememberedSet>::cluster_for_addr(HeapWordImpl **addr) {
  size_t card_index = _rs->card_index_for_addr(addr);
  size_t result = card_index / ShenandoahCardCluster<RememberedSet>::CardsPerCluster;
  return result;
}

template<typename RememberedSet>
inline HeapWord*
ShenandoahScanRemembered<RememberedSet>::addr_for_cluster(size_t cluster_no) {
  size_t card_index = cluster_no * ShenandoahCardCluster<RememberedSet>::CardsPerCluster;
  return addr_for_card_index(card_index);
}

// This is used only for debug verification so don't worry about making the scan parallel.
template<typename RememberedSet>
void ShenandoahScanRemembered<RememberedSet>::roots_do(OopIterateClosure* cl) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  for (size_t i = 0, n = heap->num_regions(); i < n; ++i) {
    ShenandoahHeapRegion* region = heap->get_region(i);
    if (region->is_old() && region->is_active() && !region->is_cset()) {
      HeapWord* start_of_range = region->bottom();
      HeapWord* end_of_range = region->top();
      size_t start_cluster_no = cluster_for_addr(start_of_range);
      size_t num_heapwords = end_of_range - start_of_range;
      unsigned int cluster_size = CardTable::card_size_in_words() *
                                  ShenandoahCardCluster<ShenandoahDirectCardMarkRememberedSet>::CardsPerCluster;
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
template<typename RememberedSet>
inline void ShenandoahScanRemembered<RememberedSet>::log_card_stats(HdrSeq* stats) {
  for (int i = 0; i < MAX_CARD_STAT_TYPE; i++) {
    log_info(gc, remset)("%18s: [ %8.2f %8.2f %8.2f %8.2f %8.2f ]",
      _card_stats_name[i],
      stats[i].percentile(0), stats[i].percentile(25),
      stats[i].percentile(50), stats[i].percentile(75),
      stats[i].maximum());
  }
}

// Log card stats for all nworkers for a specific phase t
template<typename RememberedSet>
void ShenandoahScanRemembered<RememberedSet>::log_card_stats(uint nworkers, CardStatLogType t) {
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
template<typename RememberedSet>
void ShenandoahScanRemembered<RememberedSet>::log_worker_card_stats(uint worker_id, HdrSeq* sum_stats) {
  assert(ShenandoahEnableCardStats, "Do not call");

  HdrSeq* worker_card_stats = card_stats(worker_id);
  log_info(gc, remset)("Worker %u Card Stats: ", worker_id);
  log_card_stats(worker_card_stats);
  // Merge worker stats into the cumulative stats & clear worker stats
  merge_worker_card_stats_cumulative(worker_card_stats, sum_stats);
}

template<typename RememberedSet>
void ShenandoahScanRemembered<RememberedSet>::merge_worker_card_stats_cumulative(
  HdrSeq* worker_stats, HdrSeq* sum_stats) {
  for (int i = 0; i < MAX_CARD_STAT_TYPE; i++) {
    sum_stats[i].add(worker_stats[i]);
    worker_stats[i].clear();
  }
}
#endif

inline bool ShenandoahRegionChunkIterator::has_next() const {
  return _index < _total_chunks;
}

inline bool ShenandoahRegionChunkIterator::next(struct ShenandoahRegionChunk *assignment) {
  if (_index >= _total_chunks) {
    return false;
  }
  size_t new_index = Atomic::add(&_index, (size_t) 1, memory_order_relaxed);
  if (new_index > _total_chunks) {
    // First worker that hits new_index == _total_chunks continues, other
    // contending workers return false.
    return false;
  }
  // convert to zero-based indexing
  new_index--;
  assert(new_index < _total_chunks, "Error");

  // Find the group number for the assigned chunk index
  size_t group_no;
  for (group_no = 0; new_index >= _group_entries[group_no]; group_no++)
    ;
  assert(group_no < _num_groups, "Cannot have group no greater or equal to _num_groups");

  // All size computations measured in HeapWord
  size_t region_size_words = ShenandoahHeapRegion::region_size_words();
  size_t group_region_index = _region_index[group_no];
  size_t group_region_offset = _group_offset[group_no];

  size_t index_within_group = (group_no == 0)? new_index: new_index - _group_entries[group_no - 1];
  size_t group_chunk_size = _group_chunk_size[group_no];
  size_t offset_of_this_chunk = group_region_offset + index_within_group * group_chunk_size;
  size_t regions_spanned_by_chunk_offset = offset_of_this_chunk / region_size_words;
  size_t offset_within_region = offset_of_this_chunk % region_size_words;

  size_t region_index = group_region_index + regions_spanned_by_chunk_offset;

  assignment->_r = _heap->get_region(region_index);
  assignment->_chunk_offset = offset_within_region;
  assignment->_chunk_size = group_chunk_size;
  return true;
}

#endif   // SHARE_GC_SHENANDOAH_SHENANDOAHSCANREMEMBEREDINLINE_HPP
