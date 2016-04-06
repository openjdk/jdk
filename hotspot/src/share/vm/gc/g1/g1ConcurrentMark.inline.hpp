/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1CONCURRENTMARK_INLINE_HPP
#define SHARE_VM_GC_G1_G1CONCURRENTMARK_INLINE_HPP

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentMark.hpp"
#include "gc/shared/taskqueue.inline.hpp"

inline bool G1ConcurrentMark::par_mark(oop obj) {
  return _nextMarkBitMap->parMark((HeapWord*)obj);
}

inline bool G1CMBitMapRO::iterate(BitMapClosure* cl, MemRegion mr) {
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

// The argument addr should be the start address of a valid object
HeapWord* G1CMBitMapRO::nextObject(HeapWord* addr) {
  oop obj = (oop) addr;
  HeapWord* res =  addr + obj->size();
  assert(offsetToHeapWord(heapWordToOffset(res)) == res, "sanity");
  return res;
}

#define check_mark(addr)                                                       \
  assert(_bmStartWord <= (addr) && (addr) < (_bmStartWord + _bmWordSize),      \
         "outside underlying space?");                                         \
  assert(G1CollectedHeap::heap()->is_in_exact(addr),                           \
         "Trying to access not available bitmap " PTR_FORMAT                   \
         " corresponding to " PTR_FORMAT " (%u)",                              \
         p2i(this), p2i(addr), G1CollectedHeap::heap()->addr_to_region(addr));

inline void G1CMBitMap::mark(HeapWord* addr) {
  check_mark(addr);
  _bm.set_bit(heapWordToOffset(addr));
}

inline void G1CMBitMap::clear(HeapWord* addr) {
  check_mark(addr);
  _bm.clear_bit(heapWordToOffset(addr));
}

inline bool G1CMBitMap::parMark(HeapWord* addr) {
  check_mark(addr);
  return _bm.par_set_bit(heapWordToOffset(addr));
}

#undef check_mark

template<typename Fn>
inline void G1CMMarkStack::iterate(Fn fn) {
  assert(_saved_index == _index, "saved index: %d index: %d", _saved_index, _index);
  for (int i = 0; i < _index; ++i) {
    fn(_base[i]);
  }
}

// It scans an object and visits its children.
inline void G1CMTask::scan_object(oop obj) { process_grey_object<true>(obj); }

inline void G1CMTask::push(oop obj) {
  HeapWord* objAddr = (HeapWord*) obj;
  assert(_g1h->is_in_g1_reserved(objAddr), "invariant");
  assert(!_g1h->is_on_master_free_list(
              _g1h->heap_region_containing((HeapWord*) objAddr)), "invariant");
  assert(!_g1h->is_obj_ill(obj), "invariant");
  assert(_nextMarkBitMap->isMarked(objAddr), "invariant");

  if (!_task_queue->push(obj)) {
    // The local task queue looks full. We need to push some entries
    // to the global stack.
    move_entries_to_global_stack();

    // this should succeed since, even if we overflow the global
    // stack, we should have definitely removed some entries from the
    // local queue. So, there must be space on it.
    bool success = _task_queue->push(obj);
    assert(success, "invariant");
  }
}

inline bool G1CMTask::is_below_finger(oop obj, HeapWord* global_finger) const {
  // If obj is above the global finger, then the mark bitmap scan
  // will find it later, and no push is needed.  Similarly, if we have
  // a current region and obj is between the local finger and the
  // end of the current region, then no push is needed.  The tradeoff
  // of checking both vs only checking the global finger is that the
  // local check will be more accurate and so result in fewer pushes,
  // but may also be a little slower.
  HeapWord* objAddr = (HeapWord*)obj;
  if (_finger != NULL) {
    // We have a current region.

    // Finger and region values are all NULL or all non-NULL.  We
    // use _finger to check since we immediately use its value.
    assert(_curr_region != NULL, "invariant");
    assert(_region_limit != NULL, "invariant");
    assert(_region_limit <= global_finger, "invariant");

    // True if obj is less than the local finger, or is between
    // the region limit and the global finger.
    if (objAddr < _finger) {
      return true;
    } else if (objAddr < _region_limit) {
      return false;
    } // Else check global finger.
  }
  // Check global finger.
  return objAddr < global_finger;
}

template<bool scan>
inline void G1CMTask::process_grey_object(oop obj) {
  assert(scan || obj->is_typeArray(), "Skipping scan of grey non-typeArray");
  assert(_nextMarkBitMap->isMarked((HeapWord*) obj), "invariant");

  size_t obj_size = obj->size();
  _words_scanned += obj_size;

  if (scan) {
    obj->oop_iterate(_cm_oop_closure);
  }
  check_limits();
}

inline void G1CMTask::make_reference_grey(oop obj) {
  if (_cm->par_mark(obj)) {
    // No OrderAccess:store_load() is needed. It is implicit in the
    // CAS done in G1CMBitMap::parMark() call in the routine above.
    HeapWord* global_finger = _cm->finger();

    // We only need to push a newly grey object on the mark
    // stack if it is in a section of memory the mark bitmap
    // scan has already examined.  Mark bitmap scanning
    // maintains progress "fingers" for determining that.
    //
    // Notice that the global finger might be moving forward
    // concurrently. This is not a problem. In the worst case, we
    // mark the object while it is above the global finger and, by
    // the time we read the global finger, it has moved forward
    // past this object. In this case, the object will probably
    // be visited when a task is scanning the region and will also
    // be pushed on the stack. So, some duplicate work, but no
    // correctness problems.
    if (is_below_finger(obj, global_finger)) {
      if (obj->is_typeArray()) {
        // Immediately process arrays of primitive types, rather
        // than pushing on the mark stack.  This keeps us from
        // adding humongous objects to the mark stack that might
        // be reclaimed before the entry is processed - see
        // selection of candidates for eager reclaim of humongous
        // objects.  The cost of the additional type test is
        // mitigated by avoiding a trip through the mark stack,
        // by only doing a bookkeeping update and avoiding the
        // actual scan of the object - a typeArray contains no
        // references, and the metadata is built-in.
        process_grey_object<false>(obj);
      } else {
        push(obj);
      }
    }
  }
}

inline void G1CMTask::deal_with_reference(oop obj) {
  increment_refs_reached();

  HeapWord* objAddr = (HeapWord*) obj;
  assert(obj->is_oop_or_null(true /* ignore mark word */), "Expected an oop or NULL at " PTR_FORMAT, p2i(obj));
  if (_g1h->is_in_g1_reserved(objAddr)) {
    assert(obj != NULL, "null check is implicit");
    if (!_nextMarkBitMap->isMarked(objAddr)) {
      // Only get the containing region if the object is not marked on the
      // bitmap (otherwise, it's a waste of time since we won't do
      // anything with it).
      HeapRegion* hr = _g1h->heap_region_containing(obj);
      if (!hr->obj_allocated_since_next_marking(obj)) {
        make_reference_grey(obj);
      }
    }
  }
}

inline void G1ConcurrentMark::markPrev(oop p) {
  assert(!_prevMarkBitMap->isMarked((HeapWord*) p), "sanity");
  // Note we are overriding the read-only view of the prev map here, via
  // the cast.
  ((G1CMBitMap*)_prevMarkBitMap)->mark((HeapWord*) p);
}

bool G1ConcurrentMark::isPrevMarked(oop p) const {
  assert(p != NULL && p->is_oop(), "expected an oop");
  HeapWord* addr = (HeapWord*)p;
  assert(addr >= _prevMarkBitMap->startWord() ||
         addr < _prevMarkBitMap->endWord(), "in a region");

  return _prevMarkBitMap->isMarked(addr);
}

inline void G1ConcurrentMark::grayRoot(oop obj, HeapRegion* hr) {
  assert(obj != NULL, "pre-condition");
  HeapWord* addr = (HeapWord*) obj;
  if (hr == NULL) {
    hr = _g1h->heap_region_containing(addr);
  } else {
    assert(hr->is_in(addr), "pre-condition");
  }
  assert(hr != NULL, "sanity");
  // Given that we're looking for a region that contains an object
  // header it's impossible to get back a HC region.
  assert(!hr->is_continues_humongous(), "sanity");

  if (addr < hr->next_top_at_mark_start()) {
    if (!_nextMarkBitMap->isMarked(addr)) {
      par_mark(obj);
    }
  }
}

#endif // SHARE_VM_GC_G1_G1CONCURRENTMARK_INLINE_HPP
