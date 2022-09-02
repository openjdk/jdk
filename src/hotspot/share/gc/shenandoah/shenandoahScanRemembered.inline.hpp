/*
 * Copyright (c) 2021, Amazon.com, Inc. or its affiliates.  All rights reserved.
 *
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
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.hpp"

inline size_t
ShenandoahDirectCardMarkRememberedSet::last_valid_index() {
  return _card_table->last_valid_index();
}

inline size_t
ShenandoahDirectCardMarkRememberedSet::total_cards() {
  return _total_card_count;
}

inline size_t
ShenandoahDirectCardMarkRememberedSet::card_index_for_addr(HeapWord *p) {
  return _card_table->index_for(p);
}

inline HeapWord *
ShenandoahDirectCardMarkRememberedSet::addr_for_card_index(size_t card_index) {
  return _whole_heap_base + CardTable::card_size_in_words() * card_index;
}

inline bool
ShenandoahDirectCardMarkRememberedSet::is_write_card_dirty(size_t card_index) {
  uint8_t *bp = &(_card_table->write_byte_map())[card_index];
  return (bp[0] == CardTable::dirty_card_val());
}

inline bool
ShenandoahDirectCardMarkRememberedSet::is_card_dirty(size_t card_index) {
  uint8_t *bp = &(_card_table->read_byte_map())[card_index];
  return (bp[0] == CardTable::dirty_card_val());
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_card_as_dirty(size_t card_index) {
  uint8_t *bp = &(_card_table->write_byte_map())[card_index];
  bp[0] = CardTable::dirty_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_range_as_dirty(size_t card_index, size_t num_cards) {
  uint8_t *bp = &(_card_table->write_byte_map())[card_index];
  while (num_cards-- > 0) {
    *bp++ = CardTable::dirty_card_val();
  }
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_card_as_clean(size_t card_index) {
  uint8_t *bp = &(_card_table->write_byte_map())[card_index];
  bp[0] = CardTable::clean_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_range_as_clean(size_t card_index, size_t num_cards) {
  uint8_t *bp = &(_card_table->write_byte_map())[card_index];
  while (num_cards-- > 0) {
    *bp++ = CardTable::clean_card_val();
  }
}

inline bool
ShenandoahDirectCardMarkRememberedSet::is_card_dirty(HeapWord *p) {
  size_t index = card_index_for_addr(p);
  uint8_t *bp = &(_card_table->read_byte_map())[index];
  return (bp[0] == CardTable::dirty_card_val());
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_card_as_dirty(HeapWord *p) {
  size_t index = card_index_for_addr(p);
  uint8_t *bp = &(_card_table->write_byte_map())[index];
  bp[0] = CardTable::dirty_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_range_as_dirty(HeapWord *p, size_t num_heap_words) {
  uint8_t *bp = &(_card_table->write_byte_map_base())[uintptr_t(p) >> _card_shift];
  uint8_t *end_bp = &(_card_table->write_byte_map_base())[uintptr_t(p + num_heap_words) >> _card_shift];
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
  uint8_t *bp = &(_card_table->write_byte_map())[index];
  bp[0] = CardTable::clean_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_read_card_as_clean(size_t index) {
  uint8_t *bp = &(_card_table->read_byte_map())[index];
  bp[0] = CardTable::clean_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_range_as_clean(HeapWord *p, size_t num_heap_words) {
  uint8_t *bp = &(_card_table->write_byte_map_base())[uintptr_t(p) >> _card_shift];
  uint8_t *end_bp = &(_card_table->write_byte_map_base())[uintptr_t(p + num_heap_words) >> _card_shift];
  // If (p + num_heap_words) is not aligned on card boundary, we also need to clean last card.
  if (((unsigned long long) (p + num_heap_words)) & (CardTable::card_size() - 1)) {
    end_bp++;
  }
  while (bp < end_bp) {
    *bp++ = CardTable::clean_card_val();
  }
}

inline size_t
ShenandoahDirectCardMarkRememberedSet::cluster_count() {
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

  register_object_wo_lock(address);
}

template<typename RememberedSet>
inline void
ShenandoahCardCluster<RememberedSet>::register_object_wo_lock(HeapWord* address) {
  size_t card_at_start = _rs->card_index_for_addr(address);
  HeapWord *card_start_address = _rs->addr_for_card_index(card_at_start);
  uint8_t offset_in_card = address - card_start_address;

  if (!has_object(card_at_start)) {
    set_has_object_bit(card_at_start);
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
      clear_has_object_bit(i);
    }

    uint8_t follow_offset = static_cast<uint8_t>((address + length_in_words) - _rs->addr_for_card_index(card_at_end));
    if (has_object(card_at_end) && (get_first_start(card_at_end) < follow_offset)) {
      // It may be that after coalescing within this last card's memory range, the last card
      // no longer holds an object.
      if (get_last_start(card_at_end) >= follow_offset) {
        set_first_start(card_at_end, follow_offset);
      } else {
        // last_start is being coalesced so this card no longer has any objects.
        clear_has_object_bit(card_at_end);
      }
    }
    // else
    //  card_at_end did not have an object, so it still does not have an object, or
    //  card_at_end had an object that starts after the coalesced object, so no changes required for card_at_end

  }
}


template<typename RememberedSet>
inline size_t
ShenandoahCardCluster<RememberedSet>::get_first_start(size_t card_index) {
  assert(has_object(card_index), "Can't get first start because no object starts here");
  return object_starts[card_index].offsets.first & FirstStartBits;
}

template<typename RememberedSet>
inline size_t
ShenandoahCardCluster<RememberedSet>::get_last_start(size_t card_index) {
  assert(has_object(card_index), "Can't get last start because no object starts here");
  return object_starts[card_index].offsets.last;
}

template<typename RememberedSet>
inline size_t
ShenandoahScanRemembered<RememberedSet>::last_valid_index() { return _rs->last_valid_index(); }

template<typename RememberedSet>
inline size_t
ShenandoahScanRemembered<RememberedSet>::total_cards() { return _rs->total_cards(); }

template<typename RememberedSet>
inline size_t
ShenandoahScanRemembered<RememberedSet>::card_index_for_addr(HeapWord *p) { return _rs->card_index_for_addr(p); };

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
ShenandoahScanRemembered<RememberedSet>::register_object_wo_lock(HeapWord *addr) {
  _scc->register_object_wo_lock(addr);
}

template <typename RememberedSet>
inline bool
ShenandoahScanRemembered<RememberedSet>::verify_registration(HeapWord* address, ShenandoahMarkingContext* ctx) {

  size_t index = card_index_for_addr(address);
  if (!_scc->has_object(index)) {
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
      if (_scc->has_object(end_card_index) &&
          ((addr_for_card_index(end_card_index) + _scc->get_first_start(end_card_index)) != (base_addr + offset))) {
        return false;
      }
    }

    // Assure that no other objects are registered "inside" of this one.
    for (index++; index < end_card_index; index++) {
      if (_scc->has_object(index)) {
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

template<typename RememberedSet>
template <typename ClosureType>
inline void
ShenandoahScanRemembered<RememberedSet>::process_clusters(size_t first_cluster, size_t count, HeapWord *end_of_range,
                                                          ClosureType *cl, bool is_concurrent) {
  process_clusters(first_cluster, count, end_of_range, cl, false, is_concurrent);
}

// Process all objects starting within count clusters beginning with first_cluster for which the start address is
// less than end_of_range.  For any such object, process the complete object, even if its end reaches beyond end_of_range.

// Do not CANCEL within process_clusters.  It is assumed that if a worker thread accepts responsbility for processing
// a chunk of work, it will finish the work it starts.  Otherwise, the chunk of work will be lost in the transition to
// degenerated execution.
template<typename RememberedSet>
template <typename ClosureType>
inline void
ShenandoahScanRemembered<RememberedSet>::process_clusters(size_t first_cluster, size_t count, HeapWord *end_of_range,
                                                          ClosureType *cl, bool write_table, bool is_concurrent) {

  // Unlike traditional Shenandoah marking, the old-gen resident objects that are examined as part of the remembered set are not
  // themselves marked.  Each such object will be scanned only once.  Any young-gen objects referenced from the remembered set will
  // be marked and then subsequently scanned.

  // If old-gen evacuation is active, then MarkingContext for old-gen heap regions is valid.  We use the MarkingContext
  // bits to determine which objects within a DIRTY card need to be scanned.  This is necessary because old-gen heap
  // regions which are in the candidate collection set have not been coalesced and filled.  Thus, these heap regions
  // may contain zombie objects.  Zombie objects are known to be dead, but have not yet been "collected".  Scanning
  // zombie objects is unsafe because the Klass pointer is not reliable, objects referenced from a zombie may have been
  // collected and their memory repurposed, and because zombie objects might refer to objects that are themselves dead.

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahMarkingContext* ctx;

  if (heap->is_old_bitmap_stable()) {
    ctx = heap->marking_context();
  } else {
    ctx = nullptr;
  }

  size_t card_index = first_cluster * ShenandoahCardCluster<RememberedSet>::CardsPerCluster;
  HeapWord *start_of_range = _rs->addr_for_card_index(card_index);
  ShenandoahHeapRegion* r = heap->heap_region_containing(start_of_range);
  assert(end_of_range <= r->top(), "process_clusters() examines one region at a time");

  while (count-- > 0) {
    // TODO: do we want to check cancellation in inner loop, on every card processed?  That would be more responsive,
    // but require more overhead for checking.
    card_index = first_cluster * ShenandoahCardCluster<RememberedSet>::CardsPerCluster;
    size_t end_card_index = card_index + ShenandoahCardCluster<RememberedSet>::CardsPerCluster;
    first_cluster++;
    size_t next_card_index = 0;
    while (card_index < end_card_index) {
      if (_rs->addr_for_card_index(card_index) > end_of_range) {
        count = 0;
        card_index = end_card_index;
        break;
      }
      bool is_dirty = (write_table)? is_write_card_dirty(card_index): is_card_dirty(card_index);
      bool has_object = _scc->has_object(card_index);
      if (is_dirty) {
        size_t prev_card_index = card_index;
        if (has_object) {
          // Scan all objects that start within this card region.
          size_t start_offset = _scc->get_first_start(card_index);
          HeapWord *p = _rs->addr_for_card_index(card_index);
          HeapWord *card_start = p;
          HeapWord *endp = p + CardTable::card_size_in_words();
          assert(!r->is_humongous(), "Process humongous regions elsewhere");

          if (endp > end_of_range) {
            endp = end_of_range;
            next_card_index = end_card_index;
          } else {
            // endp either points to start of next card region, or to the next object that needs to be scanned, which may
            // reside in some successor card region.

            // Can't use _scc->card_index_for_addr(endp) here because it crashes with assertion
            // failure if endp points to end of heap.
            next_card_index = card_index + (endp - card_start) / CardTable::card_size_in_words();
          }

          p += start_offset;
          while (p < endp) {
            oop obj = cast_to_oop(p);

            // ctx->is_marked() returns true if mark bit set or if obj above TAMS.
            if (!ctx || ctx->is_marked(obj)) {
              // Future TODO:
              // For improved efficiency, we might want to give special handling of obj->is_objArray().  In
              // particular, in that case, we might want to divide the effort for scanning of a very long object array
              // between multiple threads.  Also, skip parts of the array that are not marked as dirty.
              if (obj->is_objArray()) {
                objArrayOop array = objArrayOop(obj);
                int len = array->length();
                array->oop_iterate_range(cl, 0, len);
              } else if (obj->is_instance()) {
                obj->oop_iterate(cl);
              } else {
                // Case 3: Primitive array. Do nothing, no oops there. We use the same
                // performance tweak TypeArrayKlass::oop_oop_iterate_impl is using:
                // We skip iterating over the klass pointer since we know that
                // Universe::TypeArrayKlass never moves.
                assert (obj->is_typeArray(), "should be type array");
              }
              p += obj->size();
            } else {
              // This object is not marked so we don't scan it.  Containing region r is initialized above.
              HeapWord* tams = ctx->top_at_mark_start(r);
              if (p >= tams) {
                p += obj->size();
              } else {
                p = ctx->get_next_marked_addr(p, tams);
              }
            }
          }
          if (p > endp) {
            card_index = card_index + (p - card_start) / CardTable::card_size_in_words();
          } else {                  // p == endp
            card_index = next_card_index;
          }
        } else {
          // Card is dirty but has no object.  Card will have been scanned during scan of a previous cluster.
          card_index++;
        }
      } else if (has_object) {
        // Card is clean but has object.

        // Scan the last object that starts within this card memory if it spans at least one dirty card within this cluster
        // or if it reaches into the next cluster.
        size_t start_offset = _scc->get_last_start(card_index);
        HeapWord *card_start = _rs->addr_for_card_index(card_index);
        HeapWord *p = card_start + start_offset;
        oop obj = cast_to_oop(p);

        size_t last_card;
        if (!ctx || ctx->is_marked(obj)) {
          HeapWord *nextp = p + obj->size();

          // Can't use _scc->card_index_for_addr(endp) here because it crashes with assertion
          // failure if nextp points to end of heap.
          last_card = card_index + (nextp - card_start) / CardTable::card_size_in_words();

          bool reaches_next_cluster = (last_card > end_card_index);
          bool spans_dirty_within_this_cluster = false;

          if (!reaches_next_cluster) {
            size_t span_card;
            for (span_card = card_index+1; span_card <= last_card; span_card++)
              if ((write_table)? _rs->is_write_card_dirty(span_card): _rs->is_card_dirty(span_card)) {
                spans_dirty_within_this_cluster = true;
                break;
              }
          }

          // TODO: only iterate over this object if it spans dirty within this cluster or within following clusters.
          // Code as written is known not to examine a zombie object because either the object is marked, or we are
          // not using the mark-context to differentiate objects, so the object is known to have been coalesced and
          // filled if it is not "live".

          if (reaches_next_cluster || spans_dirty_within_this_cluster) {
            if (obj->is_objArray()) {
              objArrayOop array = objArrayOop(obj);
              int len = array->length();
              array->oop_iterate_range(cl, 0, len);
            } else if (obj->is_instance()) {
              obj->oop_iterate(cl);
            } else {
              // Case 3: Primitive array. Do nothing, no oops there. We use the same
              // performance tweak TypeArrayKlass::oop_oop_iterate_impl is using:
              // We skip iterating over the klass pointer since we know that
              // Universe::TypeArrayKlass never moves.
              assert (obj->is_typeArray(), "should be type array");
            }
          }
        } else {
          // The object that spans end of this clean card is not marked, so no need to scan it or its
          // unmarked neighbors.  Containing region r is initialized above.
          HeapWord* tams = ctx->top_at_mark_start(r);
          HeapWord* nextp;
          if (p >= tams) {
            nextp = p + obj->size();
          } else {
            nextp = ctx->get_next_marked_addr(p, tams);
          }
          last_card = card_index + (nextp - card_start) / CardTable::card_size_in_words();
        }
        // Increment card_index to account for the spanning object, even if we didn't scan it.
        card_index = (last_card > card_index)? last_card: card_index + 1;
      } else {
        // Card is clean and has no object.  No need to clean this card.
        card_index++;
      }
    }
  }
}

// Given that this range of clusters is known to span a humongous object spanned by region r, scan the
// portion of the humongous object that corresponds to the specified range.
template<typename RememberedSet>
template <typename ClosureType>
inline void
ShenandoahScanRemembered<RememberedSet>::process_humongous_clusters(ShenandoahHeapRegion* r, size_t first_cluster, size_t count,
                                                                    HeapWord *end_of_range, ClosureType *cl, bool write_table,
                                                                    bool is_concurrent) {
  ShenandoahHeapRegion* start_region = r->humongous_start_region();
  HeapWord* p = start_region->bottom();
  oop obj = cast_to_oop(p);
  assert(r->is_humongous(), "Only process humongous regions here");
  assert(start_region->is_humongous_start(), "Should be start of humongous region");
  assert(p + obj->size() >= end_of_range, "Humongous object ends before range ends");

  size_t first_card_index = first_cluster * ShenandoahCardCluster<RememberedSet>::CardsPerCluster;
  HeapWord* first_cluster_addr = _rs->addr_for_card_index(first_card_index);
  size_t spanned_words = count * ShenandoahCardCluster<RememberedSet>::CardsPerCluster * CardTable::card_size_in_words();

  start_region->oop_iterate_humongous_slice(cl, true, first_cluster_addr, spanned_words, write_table, is_concurrent);
}

template<typename RememberedSet>
template <typename ClosureType>
inline void
ShenandoahScanRemembered<RememberedSet>::process_region(ShenandoahHeapRegion *region, ClosureType *cl, bool is_concurrent) {
  process_region(region, cl, false, is_concurrent);
}

template<typename RememberedSet>
template <typename ClosureType>
inline void
ShenandoahScanRemembered<RememberedSet>::process_region(ShenandoahHeapRegion *region, ClosureType *cl,
                                                        bool use_write_table, bool is_concurrent) {
  size_t cluster_size =
    CardTable::card_size_in_words() * ShenandoahCardCluster<ShenandoahDirectCardMarkRememberedSet>::CardsPerCluster;
  size_t clusters = ShenandoahHeapRegion::region_size_words() / cluster_size;
  process_region_slice(region, 0, clusters, region->end(), cl, use_write_table, is_concurrent);
}

template<typename RememberedSet>
template <typename ClosureType>
inline void
ShenandoahScanRemembered<RememberedSet>::process_region_slice(ShenandoahHeapRegion *region, size_t start_offset, size_t clusters,
                                                              HeapWord *end_of_range, ClosureType *cl, bool use_write_table,
                                                              bool is_concurrent) {
  HeapWord *start_of_range = region->bottom() + start_offset;
  size_t cluster_size =
    CardTable::card_size_in_words() * ShenandoahCardCluster<ShenandoahDirectCardMarkRememberedSet>::CardsPerCluster;
  size_t words = clusters * cluster_size;
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

  // Note that end_of_range may point to the middle of a cluster because region->top() or region->get_update_watermark() may
  // be less than start_of_range + words.

  // We want to assure that our process_clusters() request spans all relevant clusters.  Note that each cluster
  // processed will avoid processing beyond end_of_range.

  // Note that any object that starts between start_of_range and end_of_range, including humongous objects, will
  // be fully processed by process_clusters, even though the object may reach beyond end_of_range.

  // If I am assigned to process a range that starts beyond end_of_range (top or update-watermark), we have no work to do.

  if (start_of_range < end_of_range) {
    if (region->is_humongous()) {
      ShenandoahHeapRegion* start_region = region->humongous_start_region();
      process_humongous_clusters(start_region, start_cluster_no, clusters, end_of_range, cl, use_write_table, is_concurrent);
    } else {
      process_clusters(start_cluster_no, clusters, end_of_range, cl, use_write_table, is_concurrent);
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
inline void ShenandoahScanRemembered<RememberedSet>::roots_do(OopIterateClosure* cl) {
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
                                   false /* is_write_table */, false /* is_concurrent */);
      } else {
        process_clusters(start_cluster_no, num_clusters, end_of_range, cl, false /* is_concurrent */);
      }
    }
  }
}

inline bool ShenandoahRegionChunkIterator::has_next() const {
  return _index < _total_chunks;
}

inline bool ShenandoahRegionChunkIterator::next(struct ShenandoahRegionChunk *assignment) {
  if (_index > _total_chunks) {
    return false;
  }
  size_t new_index = Atomic::add(&_index, (size_t) 1, memory_order_relaxed);
  if (new_index > _total_chunks) {
    return false;
  }
  // convert to zero-based indexing
  new_index--;

  size_t group_no = new_index / _group_size;
  if (group_no + 1 > _num_groups) {
    group_no = _num_groups - 1;
  }

  // All size computations measured in HeapWord
  size_t region_size_words = ShenandoahHeapRegion::region_size_words();
  size_t group_region_index = _region_index[group_no];
  size_t group_region_offset = _group_offset[group_no];

  size_t index_within_group = new_index - (group_no * _group_size);
  size_t group_chunk_size = _first_group_chunk_size >> group_no;
  size_t offset_of_this_chunk = group_region_offset + index_within_group * group_chunk_size;
  size_t regions_spanned_by_chunk_offset = offset_of_this_chunk / region_size_words;
  size_t region_index = group_region_index + regions_spanned_by_chunk_offset;
  size_t offset_within_region = offset_of_this_chunk % region_size_words;

  assignment->_r = _heap->get_region(region_index);
  assignment->_chunk_offset = offset_within_region;
  assignment->_chunk_size = group_chunk_size;

  return true;
}

#endif   // SHARE_GC_SHENANDOAH_SHENANDOAHSCANREMEMBEREDINLINE_HPP
