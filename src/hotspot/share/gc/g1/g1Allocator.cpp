/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1Allocator.inline.hpp"
#include "gc/g1/g1AllocRegion.inline.hpp"
#include "gc/g1/g1EvacStats.inline.hpp"
#include "gc/g1/g1EvacuationInfo.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionSet.inline.hpp"
#include "gc/g1/heapRegionType.hpp"
#include "utilities/align.hpp"

G1Allocator::G1Allocator(G1CollectedHeap* heap) :
  _g1h(heap),
  _survivor_is_full(false),
  _old_is_full(false),
  _mutator_alloc_region(),
  _survivor_gc_alloc_region(heap->alloc_buffer_stats(InCSetState::Young)),
  _old_gc_alloc_region(heap->alloc_buffer_stats(InCSetState::Old)),
  _retained_old_gc_alloc_region(NULL) {
}

void G1Allocator::init_mutator_alloc_region() {
  assert(_mutator_alloc_region.get() == NULL, "pre-condition");
  _mutator_alloc_region.init();
}

void G1Allocator::release_mutator_alloc_region() {
  _mutator_alloc_region.release();
  assert(_mutator_alloc_region.get() == NULL, "post-condition");
}

bool G1Allocator::is_retained_old_region(HeapRegion* hr) {
  return _retained_old_gc_alloc_region == hr;
}

void G1Allocator::reuse_retained_old_region(G1EvacuationInfo& evacuation_info,
                                            OldGCAllocRegion* old,
                                            HeapRegion** retained_old) {
  HeapRegion* retained_region = *retained_old;
  *retained_old = NULL;
  assert(retained_region == NULL || !retained_region->is_archive(),
         "Archive region should not be alloc region (index %u)", retained_region->hrm_index());

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
      !retained_region->is_humongous()) {
    // The retained region was added to the old region set when it was
    // retired. We have to remove it now, since we don't allow regions
    // we allocate to in the region sets. We'll re-add it later, when
    // it's retired again.
    _g1h->old_set_remove(retained_region);
    old->set(retained_region);
    _g1h->hr_printer()->reuse(retained_region);
    evacuation_info.set_alloc_regions_used_before(retained_region->used());
  }
}

void G1Allocator::init_gc_alloc_regions(G1EvacuationInfo& evacuation_info) {
  assert_at_safepoint_on_vm_thread();

  _survivor_is_full = false;
  _old_is_full = false;

  _survivor_gc_alloc_region.init();
  _old_gc_alloc_region.init();
  reuse_retained_old_region(evacuation_info,
                            &_old_gc_alloc_region,
                            &_retained_old_gc_alloc_region);
}

void G1Allocator::release_gc_alloc_regions(G1EvacuationInfo& evacuation_info) {
  evacuation_info.set_allocation_regions(survivor_gc_alloc_region()->count() +
                                         old_gc_alloc_region()->count());
  survivor_gc_alloc_region()->release();
  // If we have an old GC alloc region to release, we'll save it in
  // _retained_old_gc_alloc_region. If we don't
  // _retained_old_gc_alloc_region will become NULL. This is what we
  // want either way so no reason to check explicitly for either
  // condition.
  _retained_old_gc_alloc_region = old_gc_alloc_region()->release();
}

void G1Allocator::abandon_gc_alloc_regions() {
  assert(survivor_gc_alloc_region()->get() == NULL, "pre-condition");
  assert(old_gc_alloc_region()->get() == NULL, "pre-condition");
  _retained_old_gc_alloc_region = NULL;
}

bool G1Allocator::survivor_is_full() const {
  return _survivor_is_full;
}

bool G1Allocator::old_is_full() const {
  return _old_is_full;
}

void G1Allocator::set_survivor_full() {
  _survivor_is_full = true;
}

void G1Allocator::set_old_full() {
  _old_is_full = true;
}

size_t G1Allocator::unsafe_max_tlab_alloc() {
  // Return the remaining space in the cur alloc region, but not less than
  // the min TLAB size.

  // Also, this value can be at most the humongous object threshold,
  // since we can't allow tlabs to grow big enough to accommodate
  // humongous objects.

  HeapRegion* hr = mutator_alloc_region()->get();
  size_t max_tlab = _g1h->max_tlab_size() * wordSize;
  if (hr == NULL) {
    return max_tlab;
  } else {
    return MIN2(MAX2(hr->free(), (size_t) MinTLABSize), max_tlab);
  }
}

size_t G1Allocator::used_in_alloc_regions() {
  assert(Heap_lock->owner() != NULL, "Should be owned on this thread's behalf.");
  return mutator_alloc_region()->used_in_alloc_regions();
}


HeapWord* G1Allocator::par_allocate_during_gc(InCSetState dest,
                                              size_t word_size) {
  size_t temp = 0;
  HeapWord* result = par_allocate_during_gc(dest, word_size, word_size, &temp);
  assert(result == NULL || temp == word_size,
         "Requested " SIZE_FORMAT " words, but got " SIZE_FORMAT " at " PTR_FORMAT,
         word_size, temp, p2i(result));
  return result;
}

HeapWord* G1Allocator::par_allocate_during_gc(InCSetState dest,
                                              size_t min_word_size,
                                              size_t desired_word_size,
                                              size_t* actual_word_size) {
  switch (dest.value()) {
    case InCSetState::Young:
      return survivor_attempt_allocation(min_word_size, desired_word_size, actual_word_size);
    case InCSetState::Old:
      return old_attempt_allocation(min_word_size, desired_word_size, actual_word_size);
    default:
      ShouldNotReachHere();
      return NULL; // Keep some compilers happy
  }
}

HeapWord* G1Allocator::survivor_attempt_allocation(size_t min_word_size,
                                                   size_t desired_word_size,
                                                   size_t* actual_word_size) {
  assert(!_g1h->is_humongous(desired_word_size),
         "we should not be seeing humongous-size allocations in this path");

  HeapWord* result = survivor_gc_alloc_region()->attempt_allocation(min_word_size,
                                                                    desired_word_size,
                                                                    actual_word_size);
  if (result == NULL && !survivor_is_full()) {
    MutexLockerEx x(FreeList_lock, Mutex::_no_safepoint_check_flag);
    result = survivor_gc_alloc_region()->attempt_allocation_locked(min_word_size,
                                                                   desired_word_size,
                                                                   actual_word_size);
    if (result == NULL) {
      set_survivor_full();
    }
  }
  if (result != NULL) {
    _g1h->dirty_young_block(result, *actual_word_size);
  }
  return result;
}

HeapWord* G1Allocator::old_attempt_allocation(size_t min_word_size,
                                              size_t desired_word_size,
                                              size_t* actual_word_size) {
  assert(!_g1h->is_humongous(desired_word_size),
         "we should not be seeing humongous-size allocations in this path");

  HeapWord* result = old_gc_alloc_region()->attempt_allocation(min_word_size,
                                                               desired_word_size,
                                                               actual_word_size);
  if (result == NULL && !old_is_full()) {
    MutexLockerEx x(FreeList_lock, Mutex::_no_safepoint_check_flag);
    result = old_gc_alloc_region()->attempt_allocation_locked(min_word_size,
                                                              desired_word_size,
                                                              actual_word_size);
    if (result == NULL) {
      set_old_full();
    }
  }
  return result;
}

uint G1PLABAllocator::calc_survivor_alignment_bytes() {
  assert(SurvivorAlignmentInBytes >= ObjectAlignmentInBytes, "sanity");
  if (SurvivorAlignmentInBytes == ObjectAlignmentInBytes) {
    // No need to align objects in the survivors differently, return 0
    // which means "survivor alignment is not used".
    return 0;
  } else {
    assert(SurvivorAlignmentInBytes > 0, "sanity");
    return SurvivorAlignmentInBytes;
  }
}

G1PLABAllocator::G1PLABAllocator(G1Allocator* allocator) :
  _g1h(G1CollectedHeap::heap()),
  _allocator(allocator),
  _surviving_alloc_buffer(_g1h->desired_plab_sz(InCSetState::Young)),
  _tenured_alloc_buffer(_g1h->desired_plab_sz(InCSetState::Old)),
  _survivor_alignment_bytes(calc_survivor_alignment_bytes()) {
  for (uint state = 0; state < InCSetState::Num; state++) {
    _direct_allocated[state] = 0;
    _alloc_buffers[state] = NULL;
  }
  _alloc_buffers[InCSetState::Young] = &_surviving_alloc_buffer;
  _alloc_buffers[InCSetState::Old]  = &_tenured_alloc_buffer;
}

bool G1PLABAllocator::may_throw_away_buffer(size_t const allocation_word_sz, size_t const buffer_size) const {
  return (allocation_word_sz * 100 < buffer_size * ParallelGCBufferWastePct);
}

HeapWord* G1PLABAllocator::allocate_direct_or_new_plab(InCSetState dest,
                                                       size_t word_sz,
                                                       bool* plab_refill_failed) {
  size_t plab_word_size = _g1h->desired_plab_sz(dest);
  size_t required_in_plab = PLAB::size_required_for_allocation(word_sz);

  // Only get a new PLAB if the allocation fits and it would not waste more than
  // ParallelGCBufferWastePct in the existing buffer.
  if ((required_in_plab <= plab_word_size) &&
    may_throw_away_buffer(required_in_plab, plab_word_size)) {

    PLAB* alloc_buf = alloc_buffer(dest);
    alloc_buf->retire();

    size_t actual_plab_size = 0;
    HeapWord* buf = _allocator->par_allocate_during_gc(dest,
                                                       required_in_plab,
                                                       plab_word_size,
                                                       &actual_plab_size);

    assert(buf == NULL || ((actual_plab_size >= required_in_plab) && (actual_plab_size <= plab_word_size)),
           "Requested at minimum " SIZE_FORMAT ", desired " SIZE_FORMAT " words, but got " SIZE_FORMAT " at " PTR_FORMAT,
           required_in_plab, plab_word_size, actual_plab_size, p2i(buf));

    if (buf != NULL) {
      alloc_buf->set_buf(buf, actual_plab_size);

      HeapWord* const obj = alloc_buf->allocate(word_sz);
      assert(obj != NULL, "PLAB should have been big enough, tried to allocate "
                          SIZE_FORMAT " requiring " SIZE_FORMAT " PLAB size " SIZE_FORMAT,
                          word_sz, required_in_plab, plab_word_size);
      return obj;
    }
    // Otherwise.
    *plab_refill_failed = true;
  }
  // Try direct allocation.
  HeapWord* result = _allocator->par_allocate_during_gc(dest, word_sz);
  if (result != NULL) {
    _direct_allocated[dest.value()] += word_sz;
  }
  return result;
}

void G1PLABAllocator::undo_allocation(InCSetState dest, HeapWord* obj, size_t word_sz) {
  alloc_buffer(dest)->undo_allocation(obj, word_sz);
}

void G1PLABAllocator::flush_and_retire_stats() {
  for (uint state = 0; state < InCSetState::Num; state++) {
    PLAB* const buf = _alloc_buffers[state];
    if (buf != NULL) {
      G1EvacStats* stats = _g1h->alloc_buffer_stats(state);
      buf->flush_and_retire_stats(stats);
      stats->add_direct_allocated(_direct_allocated[state]);
      _direct_allocated[state] = 0;
    }
  }
}

size_t G1PLABAllocator::waste() const {
  size_t result = 0;
  for (uint state = 0; state < InCSetState::Num; state++) {
    PLAB * const buf = _alloc_buffers[state];
    if (buf != NULL) {
      result += buf->waste();
    }
  }
  return result;
}

size_t G1PLABAllocator::undo_waste() const {
  size_t result = 0;
  for (uint state = 0; state < InCSetState::Num; state++) {
    PLAB * const buf = _alloc_buffers[state];
    if (buf != NULL) {
      result += buf->undo_waste();
    }
  }
  return result;
}

bool G1ArchiveAllocator::_archive_check_enabled = false;
G1ArchiveRegionMap G1ArchiveAllocator::_closed_archive_region_map;
G1ArchiveRegionMap G1ArchiveAllocator::_open_archive_region_map;

G1ArchiveAllocator* G1ArchiveAllocator::create_allocator(G1CollectedHeap* g1h, bool open) {
  // Create the archive allocator, and also enable archive object checking
  // in mark-sweep, since we will be creating archive regions.
  G1ArchiveAllocator* result =  new G1ArchiveAllocator(g1h, open);
  enable_archive_object_check();
  return result;
}

bool G1ArchiveAllocator::alloc_new_region() {
  // Allocate the highest free region in the reserved heap,
  // and add it to our list of allocated regions. It is marked
  // archive and added to the old set.
  HeapRegion* hr = _g1h->alloc_highest_free_region();
  if (hr == NULL) {
    return false;
  }
  assert(hr->is_empty(), "expected empty region (index %u)", hr->hrm_index());
  if (_open) {
    hr->set_open_archive();
  } else {
    hr->set_closed_archive();
  }
  _g1h->policy()->remset_tracker()->update_at_allocate(hr);
  _g1h->archive_set_add(hr);
  _g1h->hr_printer()->alloc(hr);
  _allocated_regions.append(hr);
  _allocation_region = hr;

  // Set up _bottom and _max to begin allocating in the lowest
  // min_region_size'd chunk of the allocated G1 region.
  _bottom = hr->bottom();
  _max = _bottom + HeapRegion::min_region_size_in_words();

  // Tell mark-sweep that objects in this region are not to be marked.
  set_range_archive(MemRegion(_bottom, HeapRegion::GrainWords), _open);

  // Since we've modified the old set, call update_sizes.
  _g1h->g1mm()->update_sizes();
  return true;
}

HeapWord* G1ArchiveAllocator::archive_mem_allocate(size_t word_size) {
  assert(word_size != 0, "size must not be zero");
  if (_allocation_region == NULL) {
    if (!alloc_new_region()) {
      return NULL;
    }
  }
  HeapWord* old_top = _allocation_region->top();
  assert(_bottom >= _allocation_region->bottom(),
         "inconsistent allocation state: " PTR_FORMAT " < " PTR_FORMAT,
         p2i(_bottom), p2i(_allocation_region->bottom()));
  assert(_max <= _allocation_region->end(),
         "inconsistent allocation state: " PTR_FORMAT " > " PTR_FORMAT,
         p2i(_max), p2i(_allocation_region->end()));
  assert(_bottom <= old_top && old_top <= _max,
         "inconsistent allocation state: expected "
         PTR_FORMAT " <= " PTR_FORMAT " <= " PTR_FORMAT,
         p2i(_bottom), p2i(old_top), p2i(_max));

  // Allocate the next word_size words in the current allocation chunk.
  // If allocation would cross the _max boundary, insert a filler and begin
  // at the base of the next min_region_size'd chunk. Also advance to the next
  // chunk if we don't yet cross the boundary, but the remainder would be too
  // small to fill.
  HeapWord* new_top = old_top + word_size;
  size_t remainder = pointer_delta(_max, new_top);
  if ((new_top > _max) ||
      ((new_top < _max) && (remainder < CollectedHeap::min_fill_size()))) {
    if (old_top != _max) {
      size_t fill_size = pointer_delta(_max, old_top);
      CollectedHeap::fill_with_object(old_top, fill_size);
      _summary_bytes_used += fill_size * HeapWordSize;
    }
    _allocation_region->set_top(_max);
    old_top = _bottom = _max;

    // Check if we've just used up the last min_region_size'd chunk
    // in the current region, and if so, allocate a new one.
    if (_bottom != _allocation_region->end()) {
      _max = _bottom + HeapRegion::min_region_size_in_words();
    } else {
      if (!alloc_new_region()) {
        return NULL;
      }
      old_top = _allocation_region->bottom();
    }
  }
  _allocation_region->set_top(old_top + word_size);
  _summary_bytes_used += word_size * HeapWordSize;

  return old_top;
}

void G1ArchiveAllocator::complete_archive(GrowableArray<MemRegion>* ranges,
                                          size_t end_alignment_in_bytes) {
  assert((end_alignment_in_bytes >> LogHeapWordSize) < HeapRegion::min_region_size_in_words(),
         "alignment " SIZE_FORMAT " too large", end_alignment_in_bytes);
  assert(is_aligned(end_alignment_in_bytes, HeapWordSize),
         "alignment " SIZE_FORMAT " is not HeapWord (%u) aligned", end_alignment_in_bytes, HeapWordSize);

  // If we've allocated nothing, simply return.
  if (_allocation_region == NULL) {
    return;
  }

  // If an end alignment was requested, insert filler objects.
  if (end_alignment_in_bytes != 0) {
    HeapWord* currtop = _allocation_region->top();
    HeapWord* newtop = align_up(currtop, end_alignment_in_bytes);
    size_t fill_size = pointer_delta(newtop, currtop);
    if (fill_size != 0) {
      if (fill_size < CollectedHeap::min_fill_size()) {
        // If the required fill is smaller than we can represent,
        // bump up to the next aligned address. We know we won't exceed the current
        // region boundary because the max supported alignment is smaller than the min
        // region size, and because the allocation code never leaves space smaller than
        // the min_fill_size at the top of the current allocation region.
        newtop = align_up(currtop + CollectedHeap::min_fill_size(),
                          end_alignment_in_bytes);
        fill_size = pointer_delta(newtop, currtop);
      }
      HeapWord* fill = archive_mem_allocate(fill_size);
      CollectedHeap::fill_with_objects(fill, fill_size);
    }
  }

  // Loop through the allocated regions, and create MemRegions summarizing
  // the allocated address range, combining contiguous ranges. Add the
  // MemRegions to the GrowableArray provided by the caller.
  int index = _allocated_regions.length() - 1;
  assert(_allocated_regions.at(index) == _allocation_region,
         "expected region %u at end of array, found %u",
         _allocation_region->hrm_index(), _allocated_regions.at(index)->hrm_index());
  HeapWord* base_address = _allocation_region->bottom();
  HeapWord* top = base_address;

  while (index >= 0) {
    HeapRegion* next = _allocated_regions.at(index);
    HeapWord* new_base = next->bottom();
    HeapWord* new_top = next->top();
    if (new_base != top) {
      ranges->append(MemRegion(base_address, pointer_delta(top, base_address)));
      base_address = new_base;
    }
    top = new_top;
    index = index - 1;
  }

  assert(top != base_address, "zero-sized range, address " PTR_FORMAT, p2i(base_address));
  ranges->append(MemRegion(base_address, pointer_delta(top, base_address)));
  _allocated_regions.clear();
  _allocation_region = NULL;
};
