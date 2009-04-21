/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// Inline functions for G1CollectedHeap

inline HeapRegion*
G1CollectedHeap::heap_region_containing(const void* addr) const {
  HeapRegion* hr = _hrs->addr_to_region(addr);
  // hr can be null if addr in perm_gen
  if (hr != NULL && hr->continuesHumongous()) {
    hr = hr->humongous_start_region();
  }
  return hr;
}

inline HeapRegion*
G1CollectedHeap::heap_region_containing_raw(const void* addr) const {
  assert(_g1_reserved.contains(addr), "invariant");
  size_t index = ((intptr_t) addr - (intptr_t) _g1_reserved.start())
                                              >> HeapRegion::LogOfHRGrainBytes;
  HeapRegion* res = _hrs->at(index);
  assert(res == _hrs->addr_to_region(addr), "sanity");
  return res;
}

inline bool G1CollectedHeap::obj_in_cs(oop obj) {
  HeapRegion* r = _hrs->addr_to_region(obj);
  return r != NULL && r->in_collection_set();
}

inline HeapWord* G1CollectedHeap::attempt_allocation(size_t word_size,
                                              bool permit_collection_pause) {
  HeapWord* res = NULL;

  assert( SafepointSynchronize::is_at_safepoint() ||
          Heap_lock->owned_by_self(), "pre-condition of the call" );

  if (_cur_alloc_region != NULL) {

    // If this allocation causes a region to become non empty,
    // then we need to update our free_regions count.

    if (_cur_alloc_region->is_empty()) {
      res = _cur_alloc_region->allocate(word_size);
      if (res != NULL)
        _free_regions--;
    } else {
      res = _cur_alloc_region->allocate(word_size);
    }
  }
  if (res != NULL) {
    if (!SafepointSynchronize::is_at_safepoint()) {
      assert( Heap_lock->owned_by_self(), "invariant" );
      Heap_lock->unlock();
    }
    return res;
  }
  // attempt_allocation_slow will also unlock the heap lock when appropriate.
  return attempt_allocation_slow(word_size, permit_collection_pause);
}

inline RefToScanQueue* G1CollectedHeap::task_queue(int i) {
  return _task_queues->queue(i);
}


inline  bool G1CollectedHeap::isMarkedPrev(oop obj) const {
  return _cm->prevMarkBitMap()->isMarked((HeapWord *)obj);
}

inline bool G1CollectedHeap::isMarkedNext(oop obj) const {
  return _cm->nextMarkBitMap()->isMarked((HeapWord *)obj);
}
