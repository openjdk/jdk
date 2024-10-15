/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1EvacInfo.hpp"
#include "gc/g1/g1EvacStats.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionPrinter.hpp"
#include "gc/g1/g1HeapRegionSet.inline.hpp"
#include "gc/g1/g1HeapRegionType.hpp"
#include "gc/g1/g1NUMA.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/align.hpp"

G1Allocator::G1Allocator(G1CollectedHeap* heap) :
  _g1h(heap),
  _numa(heap->numa()),
  _survivor_is_full(false),
  _old_is_full(false),
  _num_alloc_regions(_numa->num_active_nodes()),
  _mutator_alloc_regions(nullptr),
  _survivor_gc_alloc_regions(nullptr),
  _old_gc_alloc_region(heap->alloc_buffer_stats(G1HeapRegionAttr::Old)),
  _retained_old_gc_alloc_region(nullptr) {

  _mutator_alloc_regions = NEW_C_HEAP_ARRAY(MutatorAllocRegion, _num_alloc_regions, mtGC);
  _survivor_gc_alloc_regions = NEW_C_HEAP_ARRAY(SurvivorGCAllocRegion, _num_alloc_regions, mtGC);
  G1EvacStats* stat = heap->alloc_buffer_stats(G1HeapRegionAttr::Young);

  for (uint i = 0; i < _num_alloc_regions; i++) {
    ::new(_mutator_alloc_regions + i) MutatorAllocRegion(i);
    ::new(_survivor_gc_alloc_regions + i) SurvivorGCAllocRegion(stat, i);
  }
}

G1Allocator::~G1Allocator() {
  for (uint i = 0; i < _num_alloc_regions; i++) {
    _mutator_alloc_regions[i].~MutatorAllocRegion();
    _survivor_gc_alloc_regions[i].~SurvivorGCAllocRegion();
  }
  FREE_C_HEAP_ARRAY(MutatorAllocRegion, _mutator_alloc_regions);
  FREE_C_HEAP_ARRAY(SurvivorGCAllocRegion, _survivor_gc_alloc_regions);
}

#ifdef ASSERT
bool G1Allocator::has_mutator_alloc_region() {
  uint node_index = current_node_index();
  return mutator_alloc_region(node_index)->get() != nullptr;
}
#endif

void G1Allocator::init_mutator_alloc_regions() {
  for (uint i = 0; i < _num_alloc_regions; i++) {
    assert(mutator_alloc_region(i)->get() == nullptr, "pre-condition");
    mutator_alloc_region(i)->init();
  }
}

void G1Allocator::release_mutator_alloc_regions() {
  for (uint i = 0; i < _num_alloc_regions; i++) {
    mutator_alloc_region(i)->release();
    assert(mutator_alloc_region(i)->get() == nullptr, "post-condition");
  }
}

bool G1Allocator::is_retained_old_region(G1HeapRegion* hr) {
  return _retained_old_gc_alloc_region == hr;
}

void G1Allocator::reuse_retained_old_region(G1EvacInfo* evacuation_info,
                                            OldGCAllocRegion* old,
                                            G1HeapRegion** retained_old) {
  G1HeapRegion* retained_region = *retained_old;
  *retained_old = nullptr;

  // We will discard the current GC alloc region if:
  // a) it's in the collection set (it can happen!),
  // b) it's already full (no point in using it),
  // c) it's empty (this means that it was emptied during
  // a cleanup and it should be on the free list now), or
  // d) it's humongous (this means that it was emptied
  // during a cleanup and was added to the free list, but
  // has been subsequently used to allocate a humongous
  // object that may be less than the region size).
  if (retained_region != nullptr &&
      !retained_region->in_collection_set() &&
      !(retained_region->top() == retained_region->end()) &&
      !retained_region->is_empty() &&
      !retained_region->is_humongous()) {
    // The retained region was added to the old region set when it was
    // retired. We have to remove it now, since we don't allow regions
    // we allocate to in the region sets. We'll re-add it later, when
    // it's retired again.
    _g1h->old_set_remove(retained_region);
    old->reuse(retained_region);
    G1HeapRegionPrinter::reuse(retained_region);
    evacuation_info->set_alloc_regions_used_before(retained_region->used());
  }
}

void G1Allocator::init_gc_alloc_regions(G1EvacInfo* evacuation_info) {
  assert_at_safepoint_on_vm_thread();

  _survivor_is_full = false;
  _old_is_full = false;

  for (uint i = 0; i < _num_alloc_regions; i++) {
    survivor_gc_alloc_region(i)->init();
  }

  _old_gc_alloc_region.init();
  reuse_retained_old_region(evacuation_info,
                            &_old_gc_alloc_region,
                            &_retained_old_gc_alloc_region);
}

void G1Allocator::release_gc_alloc_regions(G1EvacInfo* evacuation_info) {
  uint survivor_region_count = 0;
  for (uint node_index = 0; node_index < _num_alloc_regions; node_index++) {
    survivor_region_count += survivor_gc_alloc_region(node_index)->count();
    survivor_gc_alloc_region(node_index)->release();
  }
  evacuation_info->set_allocation_regions(survivor_region_count +
                                          old_gc_alloc_region()->count());

  // If we have an old GC alloc region to release, we'll save it in
  // _retained_old_gc_alloc_region. If we don't
  // _retained_old_gc_alloc_region will become null. This is what we
  // want either way so no reason to check explicitly for either
  // condition.
  _retained_old_gc_alloc_region = old_gc_alloc_region()->release();
}

void G1Allocator::abandon_gc_alloc_regions() {
  for (uint i = 0; i < _num_alloc_regions; i++) {
    assert(survivor_gc_alloc_region(i)->get() == nullptr, "pre-condition");
  }
  assert(old_gc_alloc_region()->get() == nullptr, "pre-condition");
  _retained_old_gc_alloc_region = nullptr;
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

  uint node_index = current_node_index();
  G1HeapRegion* hr = mutator_alloc_region(node_index)->get();
  size_t max_tlab = _g1h->max_tlab_size() * wordSize;

  if (hr == nullptr || hr->free() < MinTLABSize) {
    // The next TLAB allocation will most probably happen in a new region,
    // therefore we can attempt to allocate the maximum allowed TLAB size.
    return max_tlab;
  }

  return MIN2(hr->free(), max_tlab);
}

size_t G1Allocator::used_in_alloc_regions() {
  assert(Heap_lock->owner() != nullptr, "Should be owned on this thread's behalf.");
  size_t used = 0;
  for (uint i = 0; i < _num_alloc_regions; i++) {
    used += mutator_alloc_region(i)->used_in_alloc_regions();
  }
  return used;
}


HeapWord* G1Allocator::par_allocate_during_gc(G1HeapRegionAttr dest,
                                              size_t word_size,
                                              uint node_index) {
  size_t temp = 0;
  HeapWord* result = par_allocate_during_gc(dest, word_size, word_size, &temp, node_index);
  assert(result == nullptr || temp == word_size,
         "Requested " SIZE_FORMAT " words, but got " SIZE_FORMAT " at " PTR_FORMAT,
         word_size, temp, p2i(result));
  return result;
}

HeapWord* G1Allocator::par_allocate_during_gc(G1HeapRegionAttr dest,
                                              size_t min_word_size,
                                              size_t desired_word_size,
                                              size_t* actual_word_size,
                                              uint node_index) {
  switch (dest.type()) {
    case G1HeapRegionAttr::Young:
      return survivor_attempt_allocation(min_word_size, desired_word_size, actual_word_size, node_index);
    case G1HeapRegionAttr::Old:
      return old_attempt_allocation(min_word_size, desired_word_size, actual_word_size);
    default:
      ShouldNotReachHere();
      return nullptr; // Keep some compilers happy
  }
}

HeapWord* G1Allocator::survivor_attempt_allocation(size_t min_word_size,
                                                   size_t desired_word_size,
                                                   size_t* actual_word_size,
                                                   uint node_index) {
  assert(!_g1h->is_humongous(desired_word_size),
         "we should not be seeing humongous-size allocations in this path");

  HeapWord* result = survivor_gc_alloc_region(node_index)->attempt_allocation(min_word_size,
                                                                              desired_word_size,
                                                                              actual_word_size);
  if (result == nullptr && !survivor_is_full()) {
    MutexLocker x(FreeList_lock, Mutex::_no_safepoint_check_flag);
    // Multiple threads may have queued at the FreeList_lock above after checking whether there
    // actually is still memory available. Redo the check under the lock to avoid unnecessary work;
    // the memory may have been used up as the threads waited to acquire the lock.
    if (!survivor_is_full()) {
      result = survivor_gc_alloc_region(node_index)->attempt_allocation_locked(min_word_size,
                                                                               desired_word_size,
                                                                               actual_word_size);
      if (result == nullptr) {
        set_survivor_full();
      }
    }
  }
  if (result != nullptr) {
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
  if (result == nullptr && !old_is_full()) {
    MutexLocker x(FreeList_lock, Mutex::_no_safepoint_check_flag);
    // Multiple threads may have queued at the FreeList_lock above after checking whether there
    // actually is still memory available. Redo the check under the lock to avoid unnecessary work;
    // the memory may have been used up as the threads waited to acquire the lock.
    if (!old_is_full()) {
      result = old_gc_alloc_region()->attempt_allocation_locked(min_word_size,
                                                                desired_word_size,
                                                                actual_word_size);
      if (result == nullptr) {
        set_old_full();
      }
    }
  }
  return result;
}

G1PLABAllocator::PLABData::PLABData() :
  _alloc_buffer(nullptr),
  _direct_allocated(0),
  _num_plab_fills(0),
  _num_direct_allocations(0),
  _plab_fill_counter(0),
  _cur_desired_plab_size(0),
  _num_alloc_buffers(0) { }

G1PLABAllocator::PLABData::~PLABData() {
  if (_alloc_buffer == nullptr) {
    return;
  }
  for (uint node_index = 0; node_index < _num_alloc_buffers; node_index++) {
    delete _alloc_buffer[node_index];
  }
  FREE_C_HEAP_ARRAY(PLAB*, _alloc_buffer);
}

void G1PLABAllocator::PLABData::initialize(uint num_alloc_buffers, size_t desired_plab_size, size_t tolerated_refills) {
  _num_alloc_buffers = num_alloc_buffers;
  _alloc_buffer = NEW_C_HEAP_ARRAY(PLAB*, _num_alloc_buffers, mtGC);

  for (uint node_index = 0; node_index < _num_alloc_buffers; node_index++) {
    _alloc_buffer[node_index] = new PLAB(desired_plab_size);
  }

  _plab_fill_counter = tolerated_refills;
  _cur_desired_plab_size = desired_plab_size;
}

void G1PLABAllocator::PLABData::notify_plab_refill(size_t tolerated_refills, size_t next_plab_size) {
  _num_plab_fills++;
  if (should_boost()) {
    _plab_fill_counter = tolerated_refills;
    _cur_desired_plab_size = next_plab_size;
  } else {
    _plab_fill_counter--;
  }
}

G1PLABAllocator::G1PLABAllocator(G1Allocator* allocator) :
  _g1h(G1CollectedHeap::heap()),
  _allocator(allocator) {

  if (ResizePLAB) {
    // See G1EvacStats::compute_desired_plab_sz for the reasoning why this is the
    // expected number of refills.
    double const ExpectedNumberOfRefills = (100 - G1LastPLABAverageOccupancy) / TargetPLABWastePct;
    // Add some padding to the threshold to not boost exactly when the targeted refills
    // were reached.
    // E.g. due to limitation of PLAB size to non-humongous objects and region boundaries
    // a thread may experience more refills than expected. Keeping the PLAB waste low
    // is the main goal, so being a bit conservative is better.
    double const PadFactor = 1.5;
    _tolerated_refills = MAX2(ExpectedNumberOfRefills, 1.0) * PadFactor;
  } else {
    // Make the tolerated refills a huge number.
    _tolerated_refills = SIZE_MAX;
  }
  // The initial PLAB refill should not count, hence the +1 for the first boost.
  size_t initial_tolerated_refills = ResizePLAB ? _tolerated_refills + 1 : _tolerated_refills;
  for (region_type_t state = 0; state < G1HeapRegionAttr::Num; state++) {
    _dest_data[state].initialize(alloc_buffers_length(state), _g1h->desired_plab_sz(state), initial_tolerated_refills);
  }
}

bool G1PLABAllocator::may_throw_away_buffer(size_t const words_remaining, size_t const buffer_size) const {
  return (words_remaining * 100 < buffer_size * ParallelGCBufferWastePct);
}

HeapWord* G1PLABAllocator::allocate_direct_or_new_plab(G1HeapRegionAttr dest,
                                                       size_t word_sz,
                                                       bool* plab_refill_failed,
                                                       uint node_index) {
  PLAB* alloc_buf = alloc_buffer(dest, node_index);
  size_t words_remaining = alloc_buf->words_remaining();
  assert(words_remaining < word_sz, "precondition");

  size_t plab_word_size = plab_size(dest.type());
  size_t next_plab_word_size = plab_word_size;

  PLABData* plab_data = &_dest_data[dest.type()];

  if (plab_data->should_boost()) {
    next_plab_word_size = _g1h->clamp_plab_size(next_plab_word_size * 2);
  }

  size_t required_in_plab = PLAB::size_required_for_allocation(word_sz);

  // Only get a new PLAB if the allocation fits into the to-be-allocated PLAB and
  // retiring the current PLAB would not waste more than ParallelGCBufferWastePct
  // in the current PLAB. Boosting the PLAB also increasingly allows more waste to occur.
  if ((required_in_plab <= next_plab_word_size) &&
    may_throw_away_buffer(words_remaining, plab_word_size)) {

    alloc_buf->retire();

    plab_data->notify_plab_refill(_tolerated_refills, next_plab_word_size);
    plab_word_size = next_plab_word_size;

    size_t actual_plab_size = 0;
    HeapWord* buf = _allocator->par_allocate_during_gc(dest,
                                                       required_in_plab,
                                                       plab_word_size,
                                                       &actual_plab_size,
                                                       node_index);

    assert(buf == nullptr || ((actual_plab_size >= required_in_plab) && (actual_plab_size <= plab_word_size)),
           "Requested at minimum %zu, desired %zu words, but got %zu at " PTR_FORMAT,
           required_in_plab, plab_word_size, actual_plab_size, p2i(buf));

    if (buf != nullptr) {
      alloc_buf->set_buf(buf, actual_plab_size);

      HeapWord* const obj = alloc_buf->allocate(word_sz);
      assert(obj != nullptr, "PLAB should have been big enough, tried to allocate "
                          "%zu requiring %zu PLAB size %zu",
                          word_sz, required_in_plab, plab_word_size);
      return obj;
    }
    // Otherwise.
    *plab_refill_failed = true;
  }
  // Try direct allocation.
  HeapWord* result = _allocator->par_allocate_during_gc(dest, word_sz, node_index);
  if (result != nullptr) {
    plab_data->_direct_allocated += word_sz;
    plab_data->_num_direct_allocations++;
  }
  return result;
}

void G1PLABAllocator::undo_allocation(G1HeapRegionAttr dest, HeapWord* obj, size_t word_sz, uint node_index) {
  alloc_buffer(dest, node_index)->undo_allocation(obj, word_sz);
}

void G1PLABAllocator::flush_and_retire_stats(uint num_workers) {
  for (region_type_t state = 0; state < G1HeapRegionAttr::Num; state++) {
    G1EvacStats* stats = _g1h->alloc_buffer_stats(state);
    for (uint node_index = 0; node_index < alloc_buffers_length(state); node_index++) {
      PLAB* const buf = alloc_buffer(state, node_index);
      if (buf != nullptr) {
        buf->flush_and_retire_stats(stats);
      }
    }
    PLABData* plab_data = &_dest_data[state];
    stats->add_num_plab_filled(plab_data->_num_plab_fills);
    stats->add_direct_allocated(plab_data->_direct_allocated);
    stats->add_num_direct_allocated(plab_data->_num_direct_allocations);
  }

  log_trace(gc, plab)("PLAB boost: Young %zu -> %zu refills %zu (tolerated %zu) Old %zu -> %zu refills %zu (tolerated %zu)",
                      _g1h->alloc_buffer_stats(G1HeapRegionAttr::Young)->desired_plab_size(num_workers),
                      plab_size(G1HeapRegionAttr::Young),
                      _dest_data[G1HeapRegionAttr::Young]._num_plab_fills,
                      _tolerated_refills,
                      _g1h->alloc_buffer_stats(G1HeapRegionAttr::Old)->desired_plab_size(num_workers),
                      plab_size(G1HeapRegionAttr::Old),
                      _dest_data[G1HeapRegionAttr::Old]._num_plab_fills,
                      _tolerated_refills);
}

size_t G1PLABAllocator::waste() const {
  size_t result = 0;
  for (region_type_t state = 0; state < G1HeapRegionAttr::Num; state++) {
    for (uint node_index = 0; node_index < alloc_buffers_length(state); node_index++) {
      PLAB* const buf = alloc_buffer(state, node_index);
      if (buf != nullptr) {
        result += buf->waste();
      }
    }
  }
  return result;
}

size_t G1PLABAllocator::plab_size(G1HeapRegionAttr which) const {
  return _dest_data[which.type()]._cur_desired_plab_size;
}

size_t G1PLABAllocator::undo_waste() const {
  size_t result = 0;
  for (region_type_t state = 0; state < G1HeapRegionAttr::Num; state++) {
    for (uint node_index = 0; node_index < alloc_buffers_length(state); node_index++) {
      PLAB* const buf = alloc_buffer(state, node_index);
      if (buf != nullptr) {
        result += buf->undo_waste();
      }
    }
  }
  return result;
}
