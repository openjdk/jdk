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
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.hpp"

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
  return _whole_heap_base + CardTable::card_size_in_words * card_index;
}

inline bool
ShenandoahDirectCardMarkRememberedSet::is_card_dirty(size_t card_index) {
  uint8_t *bp = &_byte_map[card_index];
  return (bp[0] == CardTable::dirty_card_val());
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_card_as_dirty(size_t card_index) {
  uint8_t *bp = &_byte_map[card_index];
  bp[0] = CardTable::dirty_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_range_as_dirty(size_t card_index, size_t num_cards) {
  uint8_t *bp = &_byte_map[card_index];
  while (num_cards-- > 0)
    *bp++ = CardTable::dirty_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_card_as_clean(size_t card_index) {
  uint8_t *bp = &_byte_map[card_index];
  bp[0] = CardTable::clean_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_range_as_clean(size_t card_index, size_t num_cards) {
  uint8_t *bp = &_byte_map[card_index];
  while (num_cards-- > 0)
    *bp++ = CardTable::clean_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_overreach_card_as_dirty(size_t card_index) {
  uint8_t *bp = &_overreach_map[card_index];
  bp[0] = CardTable::dirty_card_val();
}

inline bool
ShenandoahDirectCardMarkRememberedSet::is_card_dirty(HeapWord *p) {
  uint8_t *bp = &_byte_map_base[uintptr_t(p) >> _card_shift];
  return (bp[0] == CardTable::dirty_card_val());
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_card_as_dirty(HeapWord *p) {
  uint8_t *bp = &_byte_map_base[uintptr_t(p) >> _card_shift];
  bp[0] = CardTable::dirty_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_range_as_dirty(HeapWord *p, size_t num_heap_words) {
  uint8_t *bp = &_byte_map_base[uintptr_t(p) >> _card_shift];
  uint8_t *end_bp = &_byte_map_base[uintptr_t(p + num_heap_words) >> _card_shift];
  while (bp < end_bp)
    *bp++ = CardTable::dirty_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_card_as_clean(HeapWord *p) {
  uint8_t *bp = &_byte_map_base[uintptr_t(p) >> _card_shift];
  bp[0] = CardTable::clean_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_range_as_clean(HeapWord *p, size_t num_heap_words) {
  uint8_t *bp = &_byte_map_base[uintptr_t(p) >> _card_shift];
  uint8_t *end_bp = &_byte_map_base[uintptr_t(p + num_heap_words) >> _card_shift];
  while (bp < end_bp)
    *bp++ = CardTable::clean_card_val();
}

inline void
ShenandoahDirectCardMarkRememberedSet::mark_overreach_card_as_dirty(void *p) {
  uint8_t *bp = &_overreach_map_base[uintptr_t(p) >> _card_shift];
  bp[0] = CardTable::dirty_card_val();
}

inline size_t
ShenandoahDirectCardMarkRememberedSet::cluster_count() {
  return _cluster_count;
}

template<typename RememberedSet>
inline void
ShenandoahCardCluster<RememberedSet>::register_object(HeapWord* address) {
  size_t card_at_start = _rs->card_index_for_addr(address);
  HeapWord *card_start_address = _rs->addr_for_card_index(card_at_start);
  uint8_t offset_in_card = address - card_start_address;

  if ((object_starts[card_at_start] & ObjectStartsInCardRegion) == 0) {
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
#ifdef FAST_REMEMBERED_SET_SCANNING
  size_t card_at_start = _rs->card_index_for_addr(address);
  HeapWord *card_start_address = _rs->addr_for_card_index(card_at_start);
  size_t card_at_end = card_at_start + ((address + length_in_words) - card_start_address) / CardTable::card_size_in_words;

  if (card_at_start == card_at_end) {
    // No changes to object_starts array.  Either:
    //  get_first_start(card_at_start) returns this coalesced object,
    //    or it returns an object that precedes the coalesced object.
    //  get_last_start(card_at_start) returns the object that immediately follows the coalesced object,
    //    or it returns an object that comes after the object immediately following the coalesced object.
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
#else  // FAST_REMEMBERED_SET_SCANNING
  // Do nothing for now as we have a brute-force implementation
  // of findSpanningObject().
#endif // FAST_REMEMBERED_SET_SCANNING
}


template<typename RememberedSet>
inline bool
ShenandoahCardCluster<RememberedSet>::has_object(size_t card_index) {
#ifdef FAST_REMEMBERED_SET_SCANNING
  if (object_starts[card_index] & ObjectStartsInCardRegion)
    return true;
  else
    return false;
#else // FAST_REMEMBERED_SET_SCANNING'
  ShenandoahHeap *heap = ShenandoahHeap::heap();
  HeapWord *addr = _rs->addr_for_card_index(card_index);
  ShenandoahHeapRegion *region = heap->heap_region_containing(addr);

  // region->block_start(addr) is not robust to inquiries beyond top() and it crashes.
  if (region->top() <= addr)
    return false;

  // region->block_start(addr) is also not robust to inquiries within a humongous continuation region.
  // if region is humongous continuation, no object starts within it.
  if (region->is_humongous_continuation())
    return false;

  HeapWord *obj = region->block_start(addr);

  // addr is the first address of the card region.
  // obj is the object that spans addr (or starts at addr).
  assert(obj != NULL, "Object cannot be null");
  if (obj >= addr)
    return true;
  else {
    HeapWord *end_addr = addr + CardTable::card_size_in_words;

    // end_addr needs to be adjusted downward if top address of the enclosing region is less than end_addr.  this is intended
    // to be slow and reliable alternative to the planned production quality replacement, so go ahead and spend some extra
    // cycles here in order to make this code reliable.
    if (region->top() < end_addr) {
      end_addr = region->top();
    }

    obj += oop(obj)->size();
    if (obj < end_addr)
      return true;
    else
      return false;
  }
#endif // FAST_REMEMBERED_SET_SCANNING'
}

template<typename RememberedSet>
inline size_t
ShenandoahCardCluster<RememberedSet>::get_first_start(size_t card_index) {
#ifdef FAST_REMEMBERED_SET_SCANNING
  assert(object_starts[card_index] & ObjectStartsInCardRegion, "Can't get first start because no object starts here");
  return (object_starts[card_index] & FirstStartBits) >> FirstStartShift;
#else  // FAST_REMEMBERED_SET_SCANNING
  HeapWord *addr = _rs->addr_for_card_index(card_index);
  ShenandoahHeap *heap = ShenandoahHeap::heap();
  ShenandoahHeapRegion *region = heap->heap_region_containing(addr);

  HeapWord *obj = region->block_start(addr);

  assert(obj != NULL, "Object cannot be null.");
  if (obj >= addr)
    return obj - addr;
  else {
    HeapWord *end_addr = addr + CardTable::card_size_in_words;
    obj += oop(obj)->size();

    // If obj > end_addr, offset will reach beyond end of this card
    // region.  But clients should not invoke this service unless
    // they first confirm that this card has an object.
    assert(obj < end_addr, "Object out of range");
    return obj - addr;
  }
#endif  // FAST_REMEMBERED_SET_SCANNING
}

template<typename RememberedSet>
inline size_t
ShenandoahCardCluster<RememberedSet>::get_last_start(size_t card_index) {
#ifdef FAST_REMEMBERED_SET_SCANNING
  assert(object_starts[card_index] & ObjectStartsInCardRegion, "Can't get last start because no objects starts here");
  return (object_starts[card_index] & LastStartBits) >> LastStartShift;
#else  // FAST_REMEMBERED_SET_SCANNING
  HeapWord *addr = _rs->addr_for_card_index(card_index);
  HeapWord *end_addr = addr + CardTable::card_size_in_words;
  ShenandoahHeap *heap = ShenandoahHeap::heap();
  ShenandoahHeapRegion *region = heap->heap_region_containing(addr);
  HeapWord *obj = region->block_start(addr);
  assert(obj != NULL, "Object cannot be null.");

  if (region->top() <= end_addr) {
    end_addr = region->top();
  }

  HeapWord *end_obj = obj + oop(obj)->size();
  while (end_obj < end_addr) {
    obj = end_obj;
    end_obj = obj + oop(obj)->size();
  }
  assert(obj >= addr, "Object out of range.");
  return obj - addr;
#endif  // FAST_REMEMBERED_SET_SCANNING
}

#ifdef CROSSING_OFFSETS_NO_LONGER_NEEDED
template<typename RememberedSet>
inline size_t
ShenandoahCardCluster<RememberedSet>::get_crossing_object_start(size_t card_index) {
  HeapWord *addr = _rs->addr_for_card_index(card_index);
  size_t cluster_no = card_index / ShenandoahCardCluster<RememberedSet>::CardsPerCluster;
  HeapWord *cluster_addr = _rs->addr_for_card_index(cluster_no * CardsPerCluster);

  ShenandoahHeap *heap = ShenandoahHeap::heap();
  ShenandoahHeapRegion *region = heap->heap_region_containing(addr);
  HeapWord *obj = region->block_start(addr);

  if (obj > cluster_addr)
    return obj - cluster_addr;
  else
    return 0x7fff;
}
#endif

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
inline void
ShenandoahScanRemembered<RememberedSet>:: mark_overreach_card_as_dirty(size_t card_index) { _rs->mark_overreach_card_as_dirty(card_index); }

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
inline void
ShenandoahScanRemembered<RememberedSet>::mark_overreach_card_as_dirty(void *p) { _rs->mark_overreach_card_as_dirty(p); }

template<typename RememberedSet>
inline size_t
ShenandoahScanRemembered<RememberedSet>::cluster_count() { return _rs->cluster_count(); }

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::initialize_overreach(size_t first_cluster, size_t count) { _rs->initialize_overreach(first_cluster, count); }

template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::merge_overreach(size_t first_cluster, size_t count) { _rs->merge_overreach(first_cluster, count); }


template<typename RememberedSet>
inline void
ShenandoahScanRemembered<RememberedSet>::register_object(HeapWord *addr) {
  _scc->register_object(addr);
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
                                                          ClosureType *cl) {

  // Unlike traditional Shenandoah marking, the old-gen resident objects that are examined as part of the remembered set are not
  // themselves marked.  Each such object will be scanned only once.  Any young-gen objects referenced from the remembered set will
  // be marked and then subsequently scanned.

  while (count-- > 0) {
    size_t card_index = first_cluster * ShenandoahCardCluster<RememberedSet>::CardsPerCluster;
    size_t end_card_index = card_index + ShenandoahCardCluster<RememberedSet>::CardsPerCluster;

    first_cluster++;
    size_t next_card_index = 0;
    while (card_index < end_card_index) {

      bool is_dirty = _rs->is_card_dirty(card_index);
      bool has_object = _scc->has_object(card_index);

      if (is_dirty) {
        if (has_object) {
          // Scan all objects that start within this card region.
          size_t start_offset = _scc->get_first_start(card_index);
          HeapWord *p = _rs->addr_for_card_index(card_index);
          HeapWord *card_start = p;
          HeapWord *endp = p + CardTable::card_size_in_words;
          if (endp > end_of_range) {
            endp = end_of_range;
            next_card_index = end_card_index;
          } else {
            // endp either points to start of next card region, or to the next object that needs to be scanned, which may
            // reside in some successor card region.

            // Can't use _scc->card_index_for_addr(endp) here because it crashes with assertion
            // failure if endp points to end of heap.
            next_card_index = card_index + (endp - card_start) / CardTable::card_size_in_words;
          }

          p += start_offset;
          while (p < endp) {
            oop obj = oop(p);

            // Future TODO:
            // For improved efficiency, we might want to give special handling of obj->is_objArray().  In
            // particular, in that case, we might want to divide the effort for scanning of a very long object array
            // between multiple threads.
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
          }
          if (p > endp)
            card_index = card_index + (p - card_start) / CardTable::card_size_in_words;
          else                  // p == endp
            card_index = next_card_index;
        } else {
          // otherwise, this card will have been scanned during scan of a previous cluster.
          card_index++;
        }
      } else if (has_object) {

        // Scan the last object that starts within this card memory if it spans at least one dirty card within this cluster
        // or if it reaches into the next cluster.
        size_t start_offset = _scc->get_last_start(card_index);
        HeapWord *card_start = _rs->addr_for_card_index(card_index);
        HeapWord *p = card_start + start_offset;
        oop obj = oop(p);
        HeapWord *nextp = p + obj->size();

        // Can't use _scc->card_index_for_addr(endp) here because it crashes with assertion
        // failure if nextp points to end of heap.
        size_t last_card = card_index + (nextp - card_start) / CardTable::card_size_in_words;

        bool reaches_next_cluster = (last_card > end_card_index);
        bool spans_dirty_within_this_cluster = false;

        if (!reaches_next_cluster) {
          size_t span_card;
          for (span_card = card_index+1; span_card <= last_card; span_card++)
            if (_rs->is_card_dirty(span_card)) {
              spans_dirty_within_this_cluster = true;
              break;
            }
        }

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
        // Increment card_index to account for the spanning object, even if we didn't scan it.
        card_index = (last_card > card_index)? last_card: card_index + 1;
      } else {
        card_index++;
      }
    }
  }
}

template<typename RememberedSet>
template <typename ClosureType>
inline void
ShenandoahScanRemembered<RememberedSet>::process_region(ShenandoahHeapRegion *region, ClosureType *cl) {
  HeapWord *start_of_range = region->bottom();
  size_t start_cluster_no = cluster_for_addr(start_of_range);

  // region->end() represents the end of memory spanned by this region, but not all of this
  //   memory is eligible to be scanned because some of this memory has not yet been allocated.
  //
  // region->top() represents the end of allocated memory within this region.  Any addresses
  //   beyond region->top() should not be scanned as that memory does not hold valid objects.
  HeapWord *end_of_range = region->top();

  // end_of_range may point to the middle of a cluster because region->top() may be different than region->end.
  // We want to assure that our process_clusters() request spans all relevant clusters.  Note that each cluster
  // processed will avoid processing beyond end_of_range.

  size_t num_heapwords = end_of_range - start_of_range;
  unsigned int cluster_size = CardTable::card_size_in_words *
    ShenandoahCardCluster<ShenandoahDirectCardMarkRememberedSet>::CardsPerCluster;
  size_t num_clusters = (size_t) ((num_heapwords - 1 + cluster_size) / cluster_size);

  // Remembered set scanner
  process_clusters(start_cluster_no, num_clusters, end_of_range, cl);
}

template<typename RememberedSet>
inline size_t
ShenandoahScanRemembered<RememberedSet>::cluster_for_addr(HeapWordImpl **addr) {
  size_t card_index = _rs->card_index_for_addr(addr);
  size_t result = card_index / ShenandoahCardCluster<RememberedSet>::CardsPerCluster;
  return result;
}

#endif   // SHARE_GC_SHENANDOAH_SHENANDOAHSCANREMEMBEREDINLINE_HPP
