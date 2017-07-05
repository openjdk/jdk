/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTMARK_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTMARK_INLINE_HPP

#include "gc_implementation/g1/concurrentMark.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"

// Utility routine to set an exclusive range of cards on the given
// card liveness bitmap
inline void ConcurrentMark::set_card_bitmap_range(BitMap* card_bm,
                                                  BitMap::idx_t start_idx,
                                                  BitMap::idx_t end_idx,
                                                  bool is_par) {

  // Set the exclusive bit range [start_idx, end_idx).
  assert((end_idx - start_idx) > 0, "at least one card");
  assert(end_idx <= card_bm->size(), "sanity");

  // Silently clip the end index
  end_idx = MIN2(end_idx, card_bm->size());

  // For small ranges use a simple loop; otherwise use set_range or
  // use par_at_put_range (if parallel). The range is made up of the
  // cards that are spanned by an object/mem region so 8 cards will
  // allow up to object sizes up to 4K to be handled using the loop.
  if ((end_idx - start_idx) <= 8) {
    for (BitMap::idx_t i = start_idx; i < end_idx; i += 1) {
      if (is_par) {
        card_bm->par_set_bit(i);
      } else {
        card_bm->set_bit(i);
      }
    }
  } else {
    // Note BitMap::par_at_put_range() and BitMap::set_range() are exclusive.
    if (is_par) {
      card_bm->par_at_put_range(start_idx, end_idx, true);
    } else {
      card_bm->set_range(start_idx, end_idx);
    }
  }
}

// Returns the index in the liveness accounting card bitmap
// for the given address
inline BitMap::idx_t ConcurrentMark::card_bitmap_index_for(HeapWord* addr) {
  // Below, the term "card num" means the result of shifting an address
  // by the card shift -- address 0 corresponds to card number 0.  One
  // must subtract the card num of the bottom of the heap to obtain a
  // card table index.
  intptr_t card_num = intptr_t(uintptr_t(addr) >> CardTableModRefBS::card_shift);
  return card_num - heap_bottom_card_num();
}

// Counts the given memory region in the given task/worker
// counting data structures.
inline void ConcurrentMark::count_region(MemRegion mr, HeapRegion* hr,
                                         size_t* marked_bytes_array,
                                         BitMap* task_card_bm) {
  G1CollectedHeap* g1h = _g1h;
  CardTableModRefBS* ct_bs = g1h->g1_barrier_set();

  HeapWord* start = mr.start();
  HeapWord* end = mr.end();
  size_t region_size_bytes = mr.byte_size();
  uint index = hr->hrs_index();

  assert(!hr->continuesHumongous(), "should not be HC region");
  assert(hr == g1h->heap_region_containing(start), "sanity");
  assert(hr == g1h->heap_region_containing(mr.last()), "sanity");
  assert(marked_bytes_array != NULL, "pre-condition");
  assert(task_card_bm != NULL, "pre-condition");

  // Add to the task local marked bytes for this region.
  marked_bytes_array[index] += region_size_bytes;

  BitMap::idx_t start_idx = card_bitmap_index_for(start);
  BitMap::idx_t end_idx = card_bitmap_index_for(end);

  // Note: if we're looking at the last region in heap - end
  // could be actually just beyond the end of the heap; end_idx
  // will then correspond to a (non-existent) card that is also
  // just beyond the heap.
  if (g1h->is_in_g1_reserved(end) && !ct_bs->is_card_aligned(end)) {
    // end of region is not card aligned - increment to cover
    // all the cards spanned by the region.
    end_idx += 1;
  }
  // The card bitmap is task/worker specific => no need to use
  // the 'par' BitMap routines.
  // Set bits in the exclusive bit range [start_idx, end_idx).
  set_card_bitmap_range(task_card_bm, start_idx, end_idx, false /* is_par */);
}

// Counts the given memory region in the task/worker counting
// data structures for the given worker id.
inline void ConcurrentMark::count_region(MemRegion mr,
                                         HeapRegion* hr,
                                         uint worker_id) {
  size_t* marked_bytes_array = count_marked_bytes_array_for(worker_id);
  BitMap* task_card_bm = count_card_bitmap_for(worker_id);
  count_region(mr, hr, marked_bytes_array, task_card_bm);
}

// Counts the given memory region, which may be a single object, in the
// task/worker counting data structures for the given worker id.
inline void ConcurrentMark::count_region(MemRegion mr, uint worker_id) {
  HeapWord* addr = mr.start();
  HeapRegion* hr = _g1h->heap_region_containing_raw(addr);
  count_region(mr, hr, worker_id);
}

// Counts the given object in the given task/worker counting data structures.
inline void ConcurrentMark::count_object(oop obj,
                                         HeapRegion* hr,
                                         size_t* marked_bytes_array,
                                         BitMap* task_card_bm) {
  MemRegion mr((HeapWord*)obj, obj->size());
  count_region(mr, hr, marked_bytes_array, task_card_bm);
}

// Counts the given object in the task/worker counting data
// structures for the given worker id.
inline void ConcurrentMark::count_object(oop obj,
                                         HeapRegion* hr,
                                         uint worker_id) {
  size_t* marked_bytes_array = count_marked_bytes_array_for(worker_id);
  BitMap* task_card_bm = count_card_bitmap_for(worker_id);
  HeapWord* addr = (HeapWord*) obj;
  count_object(obj, hr, marked_bytes_array, task_card_bm);
}

// Attempts to mark the given object and, if successful, counts
// the object in the given task/worker counting structures.
inline bool ConcurrentMark::par_mark_and_count(oop obj,
                                               HeapRegion* hr,
                                               size_t* marked_bytes_array,
                                               BitMap* task_card_bm) {
  HeapWord* addr = (HeapWord*)obj;
  if (_nextMarkBitMap->parMark(addr)) {
    // Update the task specific count data for the object.
    count_object(obj, hr, marked_bytes_array, task_card_bm);
    return true;
  }
  return false;
}

// Attempts to mark the given object and, if successful, counts
// the object in the task/worker counting structures for the
// given worker id.
inline bool ConcurrentMark::par_mark_and_count(oop obj,
                                               size_t word_size,
                                               HeapRegion* hr,
                                               uint worker_id) {
  HeapWord* addr = (HeapWord*)obj;
  if (_nextMarkBitMap->parMark(addr)) {
    MemRegion mr(addr, word_size);
    count_region(mr, hr, worker_id);
    return true;
  }
  return false;
}

// Attempts to mark the given object and, if successful, counts
// the object in the task/worker counting structures for the
// given worker id.
inline bool ConcurrentMark::par_mark_and_count(oop obj,
                                               HeapRegion* hr,
                                               uint worker_id) {
  HeapWord* addr = (HeapWord*)obj;
  if (_nextMarkBitMap->parMark(addr)) {
    // Update the task specific count data for the object.
    count_object(obj, hr, worker_id);
    return true;
  }
  return false;
}

// As above - but we don't know the heap region containing the
// object and so have to supply it.
inline bool ConcurrentMark::par_mark_and_count(oop obj, uint worker_id) {
  HeapWord* addr = (HeapWord*)obj;
  HeapRegion* hr = _g1h->heap_region_containing_raw(addr);
  return par_mark_and_count(obj, hr, worker_id);
}

// Similar to the above routine but we already know the size, in words, of
// the object that we wish to mark/count
inline bool ConcurrentMark::par_mark_and_count(oop obj,
                                               size_t word_size,
                                               uint worker_id) {
  HeapWord* addr = (HeapWord*)obj;
  if (_nextMarkBitMap->parMark(addr)) {
    // Update the task specific count data for the object.
    MemRegion mr(addr, word_size);
    count_region(mr, worker_id);
    return true;
  }
  return false;
}

// Unconditionally mark the given object, and unconditionally count
// the object in the counting structures for worker id 0.
// Should *not* be called from parallel code.
inline bool ConcurrentMark::mark_and_count(oop obj, HeapRegion* hr) {
  HeapWord* addr = (HeapWord*)obj;
  _nextMarkBitMap->mark(addr);
  // Update the task specific count data for the object.
  count_object(obj, hr, 0 /* worker_id */);
  return true;
}

// As above - but we don't have the heap region containing the
// object, so we have to supply it.
inline bool ConcurrentMark::mark_and_count(oop obj) {
  HeapWord* addr = (HeapWord*)obj;
  HeapRegion* hr = _g1h->heap_region_containing_raw(addr);
  return mark_and_count(obj, hr);
}

inline bool CMBitMapRO::iterate(BitMapClosure* cl, MemRegion mr) {
  HeapWord* start_addr = MAX2(startWord(), mr.start());
  HeapWord* end_addr = MIN2(endWord(), mr.end());

  if (end_addr > start_addr) {
    // Right-open interval [start-offset, end-offset).
    BitMap::idx_t start_offset = heapWordToOffset(start_addr);
    BitMap::idx_t end_offset = heapWordToOffset(end_addr);

    start_offset = _bm.get_next_one_offset(start_offset, end_offset);
    while (start_offset < end_offset) {
      if (!cl->do_bit(start_offset)) {
        return false;
      }
      HeapWord* next_addr = MIN2(nextObject(offsetToHeapWord(start_offset)), end_addr);
      BitMap::idx_t next_offset = heapWordToOffset(next_addr);
      start_offset = _bm.get_next_one_offset(next_offset, end_offset);
    }
  }
  return true;
}

inline bool CMBitMapRO::iterate(BitMapClosure* cl) {
  MemRegion mr(startWord(), sizeInWords());
  return iterate(cl, mr);
}

inline void CMTask::push(oop obj) {
  HeapWord* objAddr = (HeapWord*) obj;
  assert(_g1h->is_in_g1_reserved(objAddr), "invariant");
  assert(!_g1h->is_on_master_free_list(
              _g1h->heap_region_containing((HeapWord*) objAddr)), "invariant");
  assert(!_g1h->is_obj_ill(obj), "invariant");
  assert(_nextMarkBitMap->isMarked(objAddr), "invariant");

  if (_cm->verbose_high()) {
    gclog_or_tty->print_cr("[%u] pushing "PTR_FORMAT, _worker_id, (void*) obj);
  }

  if (!_task_queue->push(obj)) {
    // The local task queue looks full. We need to push some entries
    // to the global stack.

    if (_cm->verbose_medium()) {
      gclog_or_tty->print_cr("[%u] task queue overflow, "
                             "moving entries to the global stack",
                             _worker_id);
    }
    move_entries_to_global_stack();

    // this should succeed since, even if we overflow the global
    // stack, we should have definitely removed some entries from the
    // local queue. So, there must be space on it.
    bool success = _task_queue->push(obj);
    assert(success, "invariant");
  }

  statsOnly( int tmp_size = _task_queue->size();
             if (tmp_size > _local_max_size) {
               _local_max_size = tmp_size;
             }
             ++_local_pushes );
}

// This determines whether the method below will check both the local
// and global fingers when determining whether to push on the stack a
// gray object (value 1) or whether it will only check the global one
// (value 0). The tradeoffs are that the former will be a bit more
// accurate and possibly push less on the stack, but it might also be
// a little bit slower.

#define _CHECK_BOTH_FINGERS_      1

inline void CMTask::deal_with_reference(oop obj) {
  if (_cm->verbose_high()) {
    gclog_or_tty->print_cr("[%u] we're dealing with reference = "PTR_FORMAT,
                           _worker_id, (void*) obj);
  }

  ++_refs_reached;

  HeapWord* objAddr = (HeapWord*) obj;
  assert(obj->is_oop_or_null(true /* ignore mark word */), "Error");
  if (_g1h->is_in_g1_reserved(objAddr)) {
    assert(obj != NULL, "null check is implicit");
    if (!_nextMarkBitMap->isMarked(objAddr)) {
      // Only get the containing region if the object is not marked on the
      // bitmap (otherwise, it's a waste of time since we won't do
      // anything with it).
      HeapRegion* hr = _g1h->heap_region_containing_raw(obj);
      if (!hr->obj_allocated_since_next_marking(obj)) {
        if (_cm->verbose_high()) {
          gclog_or_tty->print_cr("[%u] "PTR_FORMAT" is not considered marked",
                                 _worker_id, (void*) obj);
        }

        // we need to mark it first
        if (_cm->par_mark_and_count(obj, hr, _marked_bytes_array, _card_bm)) {
          // No OrderAccess:store_load() is needed. It is implicit in the
          // CAS done in CMBitMap::parMark() call in the routine above.
          HeapWord* global_finger = _cm->finger();

#if _CHECK_BOTH_FINGERS_
          // we will check both the local and global fingers

          if (_finger != NULL && objAddr < _finger) {
            if (_cm->verbose_high()) {
              gclog_or_tty->print_cr("[%u] below the local finger ("PTR_FORMAT"), "
                                     "pushing it", _worker_id, _finger);
            }
            push(obj);
          } else if (_curr_region != NULL && objAddr < _region_limit) {
            // do nothing
          } else if (objAddr < global_finger) {
            // Notice that the global finger might be moving forward
            // concurrently. This is not a problem. In the worst case, we
            // mark the object while it is above the global finger and, by
            // the time we read the global finger, it has moved forward
            // passed this object. In this case, the object will probably
            // be visited when a task is scanning the region and will also
            // be pushed on the stack. So, some duplicate work, but no
            // correctness problems.

            if (_cm->verbose_high()) {
              gclog_or_tty->print_cr("[%u] below the global finger "
                                     "("PTR_FORMAT"), pushing it",
                                     _worker_id, global_finger);
            }
            push(obj);
          } else {
            // do nothing
          }
#else // _CHECK_BOTH_FINGERS_
          // we will only check the global finger

          if (objAddr < global_finger) {
            // see long comment above

            if (_cm->verbose_high()) {
              gclog_or_tty->print_cr("[%u] below the global finger "
                                     "("PTR_FORMAT"), pushing it",
                                     _worker_id, global_finger);
            }
            push(obj);
          }
#endif // _CHECK_BOTH_FINGERS_
        }
      }
    }
  }
}

inline void ConcurrentMark::markPrev(oop p) {
  assert(!_prevMarkBitMap->isMarked((HeapWord*) p), "sanity");
  // Note we are overriding the read-only view of the prev map here, via
  // the cast.
  ((CMBitMap*)_prevMarkBitMap)->mark((HeapWord*) p);
}

inline void ConcurrentMark::grayRoot(oop obj, size_t word_size,
                                     uint worker_id, HeapRegion* hr) {
  assert(obj != NULL, "pre-condition");
  HeapWord* addr = (HeapWord*) obj;
  if (hr == NULL) {
    hr = _g1h->heap_region_containing_raw(addr);
  } else {
    assert(hr->is_in(addr), "pre-condition");
  }
  assert(hr != NULL, "sanity");
  // Given that we're looking for a region that contains an object
  // header it's impossible to get back a HC region.
  assert(!hr->continuesHumongous(), "sanity");

  // We cannot assert that word_size == obj->size() given that obj
  // might not be in a consistent state (another thread might be in
  // the process of copying it). So the best thing we can do is to
  // assert that word_size is under an upper bound which is its
  // containing region's capacity.
  assert(word_size * HeapWordSize <= hr->capacity(),
         err_msg("size: "SIZE_FORMAT" capacity: "SIZE_FORMAT" "HR_FORMAT,
                 word_size * HeapWordSize, hr->capacity(),
                 HR_FORMAT_PARAMS(hr)));

  if (addr < hr->next_top_at_mark_start()) {
    if (!_nextMarkBitMap->isMarked(addr)) {
      par_mark_and_count(obj, word_size, hr, worker_id);
    }
  }
}

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTMARK_INLINE_HPP
