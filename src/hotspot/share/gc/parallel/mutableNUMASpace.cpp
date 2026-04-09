/*
 * Copyright (c) 2006, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/parallel/mutableNUMASpace.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/pretouchTask.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "gc/shared/workerThread.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.hpp"
#include "runtime/atomic.hpp"
#include "runtime/java.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/threadSMR.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"

MutableNUMASpace::MutableNUMASpace(size_t page_size) : MutableSpace(page_size) {
  _lgrp_spaces = new (mtGC) GrowableArray<LGRPSpace*>(0, mtGC);
  _adaptation_cycles = 0;
  _samples_count = 0;

  size_t lgrp_limit = os::numa_get_groups_num();
  uint *lgrp_ids = NEW_C_HEAP_ARRAY(uint, lgrp_limit, mtGC);
  size_t lgrp_num = os::numa_get_leaf_groups(lgrp_ids, lgrp_limit);
  assert(lgrp_num > 0, "There should be at least one locality group");

  lgrp_spaces()->reserve(checked_cast<int>(lgrp_num));
  // Add new spaces for the new nodes
  for (size_t i = 0; i < lgrp_num; i++) {
    lgrp_spaces()->append(new LGRPSpace(lgrp_ids[i], page_size));
  }

  FREE_C_HEAP_ARRAY(uint, lgrp_ids);
}

MutableNUMASpace::~MutableNUMASpace() {
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    delete lgrp_spaces()->at(i);
  }
  delete lgrp_spaces();
}

#ifndef PRODUCT
void MutableNUMASpace::mangle_unused_area() {
  // This method should do nothing.
  // It can be called on a numa space during a full compaction.
}

void MutableNUMASpace::mangle_region(MemRegion mr) {
  // This method should do nothing because numa spaces are not mangled.
}

#endif  // NOT_PRODUCT

// There may be unallocated holes in the middle chunks
// that should be filled with dead objects to ensure parsability.
void MutableNUMASpace::ensure_parsability() {
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    MutableSpace *s = ls->space();
    if (s->top() < top()) { // For all spaces preceding the one containing top()
      size_t free_words = s->free_in_words();
      if (free_words > 0) {
        CollectedHeap::fill_with_objects(s->top(), free_words);
      }
    } else {
      return;
    }
  }
}

size_t MutableNUMASpace::used_in_words() const {
  size_t s = 0;
  for (LGRPSpace* ls : *lgrp_spaces()) {
    s += ls->space()->used_in_words();
  }
  return s;
}

size_t MutableNUMASpace::free_in_words() const {
  size_t s = 0;
  for (LGRPSpace* ls : *lgrp_spaces()) {
    s += ls->space()->free_in_words();
  }
  return s;
}

size_t MutableNUMASpace::tlab_capacity() const {
  size_t s = 0;
  for (LGRPSpace* ls : *lgrp_spaces()) {
    s += ls->space()->capacity_in_bytes();
  }
  return s / (size_t)lgrp_spaces()->length();
}

size_t MutableNUMASpace::tlab_used() const {
  size_t s = 0;
  for (LGRPSpace* ls : *lgrp_spaces()) {
    s += ls->space()->used_in_bytes();
  }
  return s / (size_t)lgrp_spaces()->length();
}

size_t MutableNUMASpace::unsafe_max_tlab_alloc() const {
  size_t s = 0;
  for (LGRPSpace* ls : *lgrp_spaces()) {
    s += ls->space()->free_in_bytes();
  }

  size_t average_free_in_bytes = s / (size_t)lgrp_spaces()->length();

  // free_in_bytes() is aligned to MinObjAlignmentInBytes, but averaging across
  // all LGRPs can produce a non-aligned result. We align the value here because
  // it may be used directly for TLAB allocation, which requires the allocation
  // size to be properly aligned.
  size_t aligned_average = align_down(average_free_in_bytes, MinObjAlignmentInBytes);

  return aligned_average;
}

// Bias region towards the first-touching lgrp. Set the right page sizes.
void MutableNUMASpace::bias_region(MemRegion mr, uint lgrp_id) {
  assert(is_aligned(mr.start(), page_size()), "precondition");
  assert(is_aligned(mr.end(), page_size()), "precondition");

  if (mr.is_empty()) {
    return;
  }
  // First we tell the OS which page size we want in the given range. The underlying
  // large page can be broken down if we require small pages.
  os::realign_memory((char*) mr.start(), mr.byte_size(), page_size());
  // Then we uncommit the pages in the range.
  os::disclaim_memory((char*) mr.start(), mr.byte_size());
  // And make them local/first-touch biased.
  os::numa_make_local((char*)mr.start(), mr.byte_size(), checked_cast<int>(lgrp_id));
}

// Update space layout. Perform adaptation.
void MutableNUMASpace::update() {
  if (UseAdaptiveNUMAChunkSizing && adaptation_cycles() < samples_count()) {
    // A NUMA space is never mangled
    initialize(region(),
               SpaceDecorator::Clear,
               SpaceDecorator::DontMangle);
  }
}

// Accumulate statistics about the allocation rate of each lgrp.
void MutableNUMASpace::accumulate_statistics() {
  if (UseAdaptiveNUMAChunkSizing) {
    for (int i = 0; i < lgrp_spaces()->length(); i++) {
      lgrp_spaces()->at(i)->sample();
    }
    increment_samples_count();
  }
}

// Get the current size of a chunk.
// This function computes the size of the chunk based on the
// difference between chunk ends. This allows it to work correctly in
// case the whole space is resized and during the process of adaptive
// chunk resizing.
size_t MutableNUMASpace::current_chunk_size(int i) {
  HeapWord *cur_end, *prev_end;
  if (i == 0) {
    prev_end = bottom();
  } else {
    prev_end = lgrp_spaces()->at(i - 1)->space()->end();
  }
  if (i == lgrp_spaces()->length() - 1) {
    cur_end = end();
  } else {
    cur_end = lgrp_spaces()->at(i)->space()->end();
  }
  if (cur_end > prev_end) {
    return pointer_delta(cur_end, prev_end, sizeof(char));
  }
  return 0;
}

// Return the default chunk size by equally diving the space.
// page_size() aligned.
size_t MutableNUMASpace::default_chunk_size() {
  // The number of pages may not be evenly divided.
  return align_down(capacity_in_bytes() / lgrp_spaces()->length(), page_size());
}

// Produce a new chunk size. page_size() aligned.
// This function is expected to be called on sequence of i's from 0 to
// lgrp_spaces()->length().
size_t MutableNUMASpace::adaptive_chunk_size(int i, size_t limit) {
  size_t pages_available = capacity_in_bytes() / page_size();
  for (int j = 0; j < i; j++) {
    pages_available -= align_down(current_chunk_size(j), page_size()) / page_size();
  }
  pages_available -= lgrp_spaces()->length() - i - 1;
  assert(pages_available > 0, "No pages left");
  float alloc_rate = 0;
  for (int j = i; j < lgrp_spaces()->length(); j++) {
    alloc_rate += lgrp_spaces()->at(j)->alloc_rate()->average();
  }
  size_t chunk_size = 0;
  if (alloc_rate > 0) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    chunk_size = (size_t)(ls->alloc_rate()->average() / alloc_rate * pages_available) * page_size();
  }
  chunk_size = MAX2(chunk_size, page_size());

  if (limit > 0) {
    limit = align_down(limit, page_size());
    if (chunk_size > current_chunk_size(i)) {
      size_t upper_bound = pages_available * page_size();
      if (upper_bound > limit &&
          current_chunk_size(i) < upper_bound - limit) {
        // The resulting upper bound should not exceed the available
        // amount of memory (pages_available * page_size()).
        upper_bound = current_chunk_size(i) + limit;
      }
      chunk_size = MIN2(chunk_size, upper_bound);
    } else {
      size_t lower_bound = page_size();
      if (current_chunk_size(i) > limit) { // lower_bound shouldn't underflow.
        lower_bound = current_chunk_size(i) - limit;
      }
      chunk_size = MAX2(chunk_size, lower_bound);
    }
  }
  assert(chunk_size <= pages_available * page_size(), "Chunk size out of range");
  return chunk_size;
}


// Return the bottom_region and the top_region. Align them to page_size() boundary.
// |------------------new_region---------------------------------|
// |----bottom_region--|---intersection---|------top_region------|
void MutableNUMASpace::select_tails(MemRegion new_region, MemRegion intersection,
                                    MemRegion* bottom_region, MemRegion *top_region) {
  assert(is_aligned(new_region.start(), page_size()), "precondition");
  assert(is_aligned(new_region.end(), page_size()), "precondition");
  assert(is_aligned(intersection.start(), page_size()), "precondition");
  assert(is_aligned(intersection.end(), page_size()), "precondition");

  // Is there bottom?
  if (new_region.start() < intersection.start()) { // Yes
    *bottom_region = MemRegion(new_region.start(), intersection.start());
  } else {
    *bottom_region = MemRegion();
  }

  // Is there top?
  if (intersection.end() < new_region.end()) { // Yes
    *top_region = MemRegion(intersection.end(), new_region.end());
  } else {
    *top_region = MemRegion();
  }
}

void MutableNUMASpace::initialize(MemRegion mr,
                                  bool clear_space,
                                  bool mangle_space,
                                  bool setup_pages,
                                  WorkerThreads* pretouch_workers) {
  assert(clear_space, "Reallocation will destroy data!");
  assert(lgrp_spaces()->length() > 0, "There should be at least one space");
  assert(is_aligned(mr.start(), page_size()), "precondition");
  assert(is_aligned(mr.end(), page_size()), "precondition");

  MemRegion old_region = region(), new_region;
  set_bottom(mr.start());
  set_end(mr.end());
  // Must always clear the space
  clear(SpaceDecorator::DontMangle);

  size_t num_pages = mr.byte_size() / page_size();

  if (num_pages < (size_t)lgrp_spaces()->length()) {
    log_warning(gc)("Degraded NUMA config: #os-pages (%zu) < #CPU (%d); space-size: %zu, page-size: %zu",
      num_pages, lgrp_spaces()->length(), mr.byte_size(), page_size());

    // Keep only the first few CPUs.
    lgrp_spaces()->trunc_to((int)num_pages);
  }

  // Handle space resize
  MemRegion top_region, bottom_region;
  if (!old_region.equals(region())) {
    new_region = mr;
    MemRegion intersection = new_region.intersection(old_region);
    if (intersection.is_empty()) {
      intersection = MemRegion(new_region.start(), new_region.start());
    }
    select_tails(new_region, intersection, &bottom_region, &top_region);
    bias_region(bottom_region, lgrp_spaces()->at(0)->lgrp_id());
    bias_region(top_region, lgrp_spaces()->at(lgrp_spaces()->length() - 1)->lgrp_id());
  }

  // Check if the space layout has changed significantly?
  // This happens when the space has been resized so that either head or tail
  // chunk became less than a page.
  bool layout_valid = UseAdaptiveNUMAChunkSizing          &&
                      current_chunk_size(0) > page_size() &&
                      current_chunk_size(lgrp_spaces()->length() - 1) > page_size();


  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    MutableSpace *s = ls->space();
    old_region = s->region();

    size_t chunk_byte_size = 0;
    if (i < lgrp_spaces()->length() - 1) {
      if (!UseAdaptiveNUMAChunkSizing ||
           NUMAChunkResizeWeight == 0 ||
           samples_count() < AdaptiveSizePolicyReadyThreshold) {
        // No adaptation. Divide the space equally.
        chunk_byte_size = default_chunk_size();
      } else
        if (!layout_valid || NUMASpaceResizeRate == 0) {
          // Fast adaptation. If no space resize rate is set, resize
          // the chunks instantly.
          chunk_byte_size = adaptive_chunk_size(i, 0);
        } else {
          // Slow adaptation. Resize the chunks moving no more than
          // NUMASpaceResizeRate bytes per collection.
          size_t limit = NUMASpaceResizeRate /
                         (lgrp_spaces()->length() * (lgrp_spaces()->length() + 1) / 2);
          chunk_byte_size = adaptive_chunk_size(i, MAX2(limit * (i + 1), page_size()));
        }

      assert(chunk_byte_size >= page_size(), "Chunk size too small");
      assert(chunk_byte_size <= capacity_in_bytes(), "Sanity check");
    }

    if (i == 0) { // Bottom chunk
      if (i != lgrp_spaces()->length() - 1) {
        new_region = MemRegion(bottom(), chunk_byte_size >> LogHeapWordSize);
      } else {
        new_region = MemRegion(bottom(), end());
      }
    } else if (i < lgrp_spaces()->length() - 1) { // Middle chunks
      MutableSpace* ps = lgrp_spaces()->at(i - 1)->space();
      new_region = MemRegion(ps->end(),
                             chunk_byte_size >> LogHeapWordSize);
    } else { // Top chunk
      MutableSpace* ps = lgrp_spaces()->at(i - 1)->space();
      new_region = MemRegion(ps->end(), end());
    }
    guarantee(region().contains(new_region), "Region invariant");


    // The general case:
    // |---------------------|--invalid---|--------------------------|
    // |------------------new_region---------------------------------|
    // |----bottom_region--|---intersection---|------top_region------|
    //                     |----old_region----|
    // The intersection part has all pages in place we don't need to migrate them.
    // Pages for the top and bottom part should be freed and then reallocated.

    MemRegion intersection = old_region.intersection(new_region);

    if (intersection.start() == nullptr || intersection.end() == nullptr) {
      intersection = MemRegion(new_region.start(), new_region.start());
    }

    select_tails(new_region, intersection, &bottom_region, &top_region);

    // In a system with static binding we have to change the bias whenever
    // we reshape the heap.
    bias_region(bottom_region, ls->lgrp_id());
    bias_region(top_region, ls->lgrp_id());

    if (AlwaysPreTouch) {
      PretouchTask::pretouch("ParallelGC PreTouch bottom_region", (char*)bottom_region.start(), (char*)bottom_region.end(),
                             page_size(), pretouch_workers);

      PretouchTask::pretouch("ParallelGC PreTouch top_region", (char*)top_region.start(), (char*)top_region.end(),
                             page_size(), pretouch_workers);
    }

    // Clear space (set top = bottom) but never mangle.
    s->initialize(new_region, SpaceDecorator::Clear, SpaceDecorator::DontMangle, MutableSpace::DontSetupPages);
  }
  set_adaptation_cycles(samples_count());
}

// Set the top of the whole space.
// Mark the holes in chunks below the top() as invalid.
void MutableNUMASpace::set_top(HeapWord* value) {
  bool found_top = false;
  for (int i = 0; i < lgrp_spaces()->length();) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    MutableSpace *s = ls->space();

    if (s->contains(value)) {
      // Check if setting the chunk's top to a given value would create a hole less than
      // a minimal object; assuming that's not the last chunk in which case we don't care.
      if (i < lgrp_spaces()->length() - 1) {
        size_t remainder = pointer_delta(s->end(), value);
        const size_t min_fill_size = CollectedHeap::min_fill_size();
        if (remainder < min_fill_size && remainder > 0) {
          // Add a minimum size filler object; it will cross the chunk boundary.
          CollectedHeap::fill_with_object(value, min_fill_size);
          value += min_fill_size;
          assert(!s->contains(value), "Should be in the next chunk");
          // Restart the loop from the same chunk, since the value has moved
          // to the next one.
          continue;
        }
      }

      s->set_top(value);
      found_top = true;
    } else {
        if (found_top) {
          s->set_top(s->bottom());
        } else {
          s->set_top(s->end());
        }
    }
    i++;
  }
  MutableSpace::set_top(value);
}

void MutableNUMASpace::clear(bool mangle_space) {
  MutableSpace::set_top(bottom());
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    // Never mangle NUMA spaces because the mangling will
    // bind the memory to a possibly unwanted lgroup.
    lgrp_spaces()->at(i)->space()->clear(SpaceDecorator::DontMangle);
  }
}

MutableNUMASpace::LGRPSpace *MutableNUMASpace::lgrp_space_for_current_thread() const {
  const int lgrp_id = os::numa_get_group_id();
  int lgrp_spaces_index = lgrp_spaces()->find_if([&](LGRPSpace* space) {
    return space->lgrp_id() == (uint)lgrp_id;
  });

  if (lgrp_spaces_index == -1) {
    // Running on a CPU with no memory; pick another CPU based on %.
    lgrp_spaces_index = lgrp_id % lgrp_spaces()->length();
  }

  return lgrp_spaces()->at(lgrp_spaces_index);
}

HeapWord* MutableNUMASpace::cas_allocate(size_t size) {
  LGRPSpace *ls = lgrp_space_for_current_thread();
  MutableSpace *s = ls->space();
  HeapWord *p = s->cas_allocate(size);
  if (p != nullptr) {
    size_t remainder = pointer_delta(s->end(), p + size);
    if (remainder < CollectedHeap::min_fill_size() && remainder > 0) {
      if (s->cas_deallocate(p, size)) {
        // We were the last to allocate and created a fragment less than
        // a minimal object.
        p = nullptr;
      } else {
        guarantee(false, "Deallocation should always succeed");
      }
    }
  }
  if (p != nullptr) {
    HeapWord* cur_top, *cur_chunk_top = p + size;
    while ((cur_top = top()) < cur_chunk_top) { // Keep _top updated.
      if (top_addr()->compare_set(cur_top, cur_chunk_top)) {
        break;
      }
    }
  }

  if (p == nullptr) {
    ls->set_allocation_failed();
  }
  return p;
}

void MutableNUMASpace::print_short_on(outputStream* st) const {
  MutableSpace::print_short_on(st);
  st->print(" (");
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    st->print("lgrp %u: ", lgrp_spaces()->at(i)->lgrp_id());
    lgrp_spaces()->at(i)->space()->print_short_on(st);
    if (i < lgrp_spaces()->length() - 1) {
      st->print(", ");
    }
  }
  st->print(")");
}

void MutableNUMASpace::print_on(outputStream* st, const char* prefix) const {
  MutableSpace::print_on(st, prefix);

  StreamIndentor si(st, 1);
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    FormatBuffer<128> lgrp_message("lgrp %u ", ls->lgrp_id());
    ls->space()->print_on(st, lgrp_message);
    if (NUMAStats) {
      StreamIndentor si2(st, 1);
      for (int i = 0; i < lgrp_spaces()->length(); i++) {
        lgrp_spaces()->at(i)->accumulate_statistics(page_size());
      }
      st->print("local/remote/unbiased/uncommitted: %zuK/"
                "%zuK/%zuK/%zuK\n",
                ls->space_stats()->_local_space / K,
                ls->space_stats()->_remote_space / K,
                ls->space_stats()->_unbiased_space / K,
                ls->space_stats()->_uncommited_space / K);
    }
  }
}

void MutableNUMASpace::verify() {
  // This can be called after setting an arbitrary value to the space's top,
  // so an object can cross the chunk boundary. We ensure the parsability
  // of the space and just walk the objects in linear fashion.
  ensure_parsability();
  MutableSpace::verify();
}

// Scan pages and gather stats about page placement and size.
void MutableNUMASpace::LGRPSpace::accumulate_statistics(size_t page_size) {
  clear_space_stats();
  char *start = (char*)align_up(space()->bottom(), page_size);
  char* end = (char*)align_down(space()->end(), page_size);
  for (char *p = start; p < end; ) {
    static const size_t PagesPerIteration = 128;
    const void* pages[PagesPerIteration];
    int lgrp_ids[PagesPerIteration];

    size_t npages = 0;
    for (; npages < PagesPerIteration && p < end; p += os::vm_page_size()) {
      pages[npages++] = p;
    }

    if (os::numa_get_group_ids_for_range(pages, lgrp_ids, npages)) {
      for (size_t i = 0; i < npages; i++) {
        if (lgrp_ids[i] < 0) {
          space_stats()->_uncommited_space += os::vm_page_size();
        } else if (checked_cast<uint>(lgrp_ids[i]) == lgrp_id()) {
          space_stats()->_local_space += os::vm_page_size();
        } else {
          space_stats()->_remote_space += os::vm_page_size();
        }
      }
    }
  }
  space_stats()->_unbiased_space = pointer_delta(start, space()->bottom(), sizeof(char)) +
                                   pointer_delta(space()->end(), end, sizeof(char));

}
