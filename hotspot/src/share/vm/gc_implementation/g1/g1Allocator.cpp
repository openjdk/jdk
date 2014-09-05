/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc_implementation/g1/g1Allocator.hpp"
#include "gc_implementation/g1/g1CollectedHeap.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/heapRegion.inline.hpp"
#include "gc_implementation/g1/heapRegionSet.inline.hpp"

void G1DefaultAllocator::init_mutator_alloc_region() {
  assert(_mutator_alloc_region.get() == NULL, "pre-condition");
  _mutator_alloc_region.init();
}

void G1DefaultAllocator::release_mutator_alloc_region() {
  _mutator_alloc_region.release();
  assert(_mutator_alloc_region.get() == NULL, "post-condition");
}

void G1Allocator::reuse_retained_old_region(EvacuationInfo& evacuation_info,
                                            OldGCAllocRegion* old,
                                            HeapRegion** retained_old) {
  HeapRegion* retained_region = *retained_old;
  *retained_old = NULL;

  // We will discard the current GC alloc region if:
  // a) it's in the collection set (it can happen!),
  // b) it's already full (no point in using it),
  // c) it's empty (this means that it was emptied during
  // a cleanup and it should be on the free list now), or
  // d) it's humongous (this means that it was emptied
  // during a cleanup and was added to the free list, but
  // has been subsequently used to allocate a humongous
  // object that may be less than the region size).
  if (retained_region != NULL &&
      !retained_region->in_collection_set() &&
      !(retained_region->top() == retained_region->end()) &&
      !retained_region->is_empty() &&
      !retained_region->isHumongous()) {
    retained_region->record_top_and_timestamp();
    // The retained region was added to the old region set when it was
    // retired. We have to remove it now, since we don't allow regions
    // we allocate to in the region sets. We'll re-add it later, when
    // it's retired again.
    _g1h->_old_set.remove(retained_region);
    bool during_im = _g1h->g1_policy()->during_initial_mark_pause();
    retained_region->note_start_of_copying(during_im);
    old->set(retained_region);
    _g1h->_hr_printer.reuse(retained_region);
    evacuation_info.set_alloc_regions_used_before(retained_region->used());
  }
}

void G1DefaultAllocator::init_gc_alloc_regions(EvacuationInfo& evacuation_info) {
  assert_at_safepoint(true /* should_be_vm_thread */);

  _survivor_gc_alloc_region.init();
  _old_gc_alloc_region.init();
  reuse_retained_old_region(evacuation_info,
                            &_old_gc_alloc_region,
                            &_retained_old_gc_alloc_region);
}

void G1DefaultAllocator::release_gc_alloc_regions(uint no_of_gc_workers, EvacuationInfo& evacuation_info) {
  AllocationContext_t context = AllocationContext::current();
  evacuation_info.set_allocation_regions(survivor_gc_alloc_region(context)->count() +
                                         old_gc_alloc_region(context)->count());
  survivor_gc_alloc_region(context)->release();
  // If we have an old GC alloc region to release, we'll save it in
  // _retained_old_gc_alloc_region. If we don't
  // _retained_old_gc_alloc_region will become NULL. This is what we
  // want either way so no reason to check explicitly for either
  // condition.
  _retained_old_gc_alloc_region = old_gc_alloc_region(context)->release();

  if (ResizePLAB) {
    _g1h->_survivor_plab_stats.adjust_desired_plab_sz(no_of_gc_workers);
    _g1h->_old_plab_stats.adjust_desired_plab_sz(no_of_gc_workers);
  }
}

void G1DefaultAllocator::abandon_gc_alloc_regions() {
  assert(survivor_gc_alloc_region(AllocationContext::current())->get() == NULL, "pre-condition");
  assert(old_gc_alloc_region(AllocationContext::current())->get() == NULL, "pre-condition");
  _retained_old_gc_alloc_region = NULL;
}

G1ParGCAllocBuffer::G1ParGCAllocBuffer(size_t gclab_word_size) :
  ParGCAllocBuffer(gclab_word_size), _retired(true) { }

HeapWord* G1ParGCAllocator::allocate_slow(GCAllocPurpose purpose, size_t word_sz, AllocationContext_t context) {
  HeapWord* obj = NULL;
  size_t gclab_word_size = _g1h->desired_plab_sz(purpose);
  if (word_sz * 100 < gclab_word_size * ParallelGCBufferWastePct) {
    G1ParGCAllocBuffer* alloc_buf = alloc_buffer(purpose, context);
    add_to_alloc_buffer_waste(alloc_buf->words_remaining());
    alloc_buf->retire(false /* end_of_gc */, false /* retain */);

    HeapWord* buf = _g1h->par_allocate_during_gc(purpose, gclab_word_size, context);
    if (buf == NULL) {
      return NULL; // Let caller handle allocation failure.
    }
    // Otherwise.
    alloc_buf->set_word_size(gclab_word_size);
    alloc_buf->set_buf(buf);

    obj = alloc_buf->allocate(word_sz);
    assert(obj != NULL, "buffer was definitely big enough...");
  } else {
    obj = _g1h->par_allocate_during_gc(purpose, word_sz, context);
  }
  return obj;
}

G1DefaultParGCAllocator::G1DefaultParGCAllocator(G1CollectedHeap* g1h) :
            G1ParGCAllocator(g1h),
            _surviving_alloc_buffer(g1h->desired_plab_sz(GCAllocForSurvived)),
            _tenured_alloc_buffer(g1h->desired_plab_sz(GCAllocForTenured)) {

  _alloc_buffers[GCAllocForSurvived] = &_surviving_alloc_buffer;
  _alloc_buffers[GCAllocForTenured]  = &_tenured_alloc_buffer;

}

void G1DefaultParGCAllocator::retire_alloc_buffers() {
  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    size_t waste = _alloc_buffers[ap]->words_remaining();
    add_to_alloc_buffer_waste(waste);
    _alloc_buffers[ap]->flush_stats_and_retire(_g1h->stats_for_purpose((GCAllocPurpose)ap),
                                               true /* end_of_gc */,
                                               false /* retain */);
  }
}
