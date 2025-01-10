/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/mutableNUMASpace.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gc_globals.hpp"
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

MutableNUMASpace::MutableNUMASpace(size_t alignment) : MutableSpace(alignment), _must_use_large_pages(false) {
  _lgrp_spaces = new (mtGC) GrowableArray<LGRPSpace*>(0, mtGC);
  _page_size = os::vm_page_size();
  _adaptation_cycles = 0;
  _samples_count = 0;

#ifdef LINUX
  // Changing the page size can lead to freeing of memory. When using large pages
  // and the memory has been both reserved and committed, Linux does not support
  // freeing parts of it.
    if (UseLargePages && !os::can_commit_large_page_memory()) {
      _must_use_large_pages = true;
    }
#endif // LINUX

  size_t lgrp_limit = os::numa_get_groups_num();
  uint *lgrp_ids = NEW_C_HEAP_ARRAY(uint, lgrp_limit, mtGC);
  size_t lgrp_num = os::numa_get_leaf_groups(lgrp_ids, lgrp_limit);
  assert(lgrp_num > 0, "There should be at least one locality group");

  lgrp_spaces()->reserve(checked_cast<int>(lgrp_num));
  // Add new spaces for the new nodes
  for (size_t i = 0; i < lgrp_num; i++) {
    lgrp_spaces()->append(new LGRPSpace(lgrp_ids[i], alignment));
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
      if (s->free_in_words() > 0) {
        HeapWord* cur_top = s->top();
        size_t words_left_to_fill = pointer_delta(s->end(), s->top());;
        while (words_left_to_fill > 0) {
          size_t words_to_fill = MIN2(words_left_to_fill, CollectedHeap::filler_array_max_size());
          assert(words_to_fill >= CollectedHeap::min_fill_size(),
                 "Remaining size (" SIZE_FORMAT ") is too small to fill (based on " SIZE_FORMAT " and " SIZE_FORMAT ")",
                 words_to_fill, words_left_to_fill, CollectedHeap::filler_array_max_size());
          CollectedHeap::fill_with_object(cur_top, words_to_fill);
          cur_top += words_to_fill;
          words_left_to_fill -= words_to_fill;
        }
      }
    } else {
      return;
    }
  }
}

size_t MutableNUMASpace::used_in_words() const {
  size_t s = 0;
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    s += lgrp_spaces()->at(i)->space()->used_in_words();
  }
  return s;
}

size_t MutableNUMASpace::free_in_words() const {
  size_t s = 0;
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    s += lgrp_spaces()->at(i)->space()->free_in_words();
  }
  return s;
}

int MutableNUMASpace::lgrp_space_index(int lgrp_id) const {
  return lgrp_spaces()->find_if([&](LGRPSpace* space) {
    return space->lgrp_id() == checked_cast<uint>(lgrp_id);
  });
}

size_t MutableNUMASpace::tlab_capacity(Thread *thr) const {
  guarantee(thr != nullptr, "No thread");
  int lgrp_id = thr->lgrp_id();
  if (lgrp_id == -1) {
    // This case can occur after the topology of the system has
    // changed. Thread can change their location, the new home
    // group will be determined during the first allocation
    // attempt. For now we can safely assume that all spaces
    // have equal size because the whole space will be reinitialized.
    if (lgrp_spaces()->length() > 0) {
      return capacity_in_bytes() / lgrp_spaces()->length();
    } else {
      assert(false, "There should be at least one locality group");
      return 0;
    }
  }
  // That's the normal case, where we know the locality group of the thread.
  int i = lgrp_space_index(lgrp_id);
  if (i == -1) {
    return 0;
  }
  return lgrp_spaces()->at(i)->space()->capacity_in_bytes();
}

size_t MutableNUMASpace::tlab_used(Thread *thr) const {
  // Please see the comments for tlab_capacity().
  guarantee(thr != nullptr, "No thread");
  int lgrp_id = thr->lgrp_id();
  if (lgrp_id == -1) {
    if (lgrp_spaces()->length() > 0) {
      return (used_in_bytes()) / lgrp_spaces()->length();
    } else {
      assert(false, "There should be at least one locality group");
      return 0;
    }
  }
  int i = lgrp_space_index(lgrp_id);
  if (i == -1) {
    return 0;
  }
  return lgrp_spaces()->at(i)->space()->used_in_bytes();
}


size_t MutableNUMASpace::unsafe_max_tlab_alloc(Thread *thr) const {
  // Please see the comments for tlab_capacity().
  guarantee(thr != nullptr, "No thread");
  int lgrp_id = thr->lgrp_id();
  if (lgrp_id == -1) {
    if (lgrp_spaces()->length() > 0) {
      return free_in_bytes() / lgrp_spaces()->length();
    } else {
      assert(false, "There should be at least one locality group");
      return 0;
    }
  }
  int i = lgrp_space_index(lgrp_id);
  if (i == -1) {
    return 0;
  }
  return lgrp_spaces()->at(i)->space()->free_in_bytes();
}

// Bias region towards the first-touching lgrp. Set the right page sizes.
void MutableNUMASpace::bias_region(MemRegion mr, uint lgrp_id) {
  HeapWord *start = align_up(mr.start(), page_size());
  HeapWord *end = align_down(mr.end(), page_size());
  if (end > start) {
    MemRegion aligned_region(start, end);
    assert((intptr_t)aligned_region.start()     % page_size() == 0 &&
           (intptr_t)aligned_region.byte_size() % page_size() == 0, "Bad alignment");
    assert(region().contains(aligned_region), "Sanity");
    // First we tell the OS which page size we want in the given range. The underlying
    // large page can be broken down if we require small pages.
    const size_t os_align = UseLargePages ? page_size() : os::vm_page_size();
    os::realign_memory((char*)aligned_region.start(), aligned_region.byte_size(), os_align);
    // Then we uncommit the pages in the range.
    // The alignment_hint argument must be less than or equal to the small page
    // size if not using large pages or else this function does nothing.
    os::disclaim_memory((char*)aligned_region.start(), aligned_region.byte_size());
    // And make them local/first-touch biased.
    os::numa_make_local((char*)aligned_region.start(), aligned_region.byte_size(), checked_cast<int>(lgrp_id));
  }
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
  return base_space_size() / lgrp_spaces()->length() * page_size();
}

// Produce a new chunk size. page_size() aligned.
// This function is expected to be called on sequence of i's from 0 to
// lgrp_spaces()->length().
size_t MutableNUMASpace::adaptive_chunk_size(int i, size_t limit) {
  size_t pages_available = base_space_size();
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
  // Is there bottom?
  if (new_region.start() < intersection.start()) { // Yes
    // Try to coalesce small pages into a large one.
    if (UseLargePages && page_size() >= alignment()) {
      HeapWord* p = align_up(intersection.start(), alignment());
      if (new_region.contains(p)
          && pointer_delta(p, new_region.start(), sizeof(char)) >= alignment()) {
        if (intersection.contains(p)) {
          intersection = MemRegion(p, intersection.end());
        } else {
          intersection = MemRegion(p, p);
        }
      }
    }
    *bottom_region = MemRegion(new_region.start(), intersection.start());
  } else {
    *bottom_region = MemRegion();
  }

  // Is there top?
  if (intersection.end() < new_region.end()) { // Yes
    // Try to coalesce small pages into a large one.
    if (UseLargePages && page_size() >= alignment()) {
      HeapWord* p = align_down(intersection.end(), alignment());
      if (new_region.contains(p)
          && pointer_delta(new_region.end(), p, sizeof(char)) >= alignment()) {
        if (intersection.contains(p)) {
          intersection = MemRegion(intersection.start(), p);
        } else {
          intersection = MemRegion(p, p);
        }
      }
    }
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

  MemRegion old_region = region(), new_region;
  set_bottom(mr.start());
  set_end(mr.end());
  // Must always clear the space
  clear(SpaceDecorator::DontMangle);

  // Compute chunk sizes
  size_t prev_page_size = page_size();
  set_page_size(alignment());
  HeapWord* rounded_bottom = align_up(bottom(), page_size());
  HeapWord* rounded_end = align_down(end(), page_size());
  size_t base_space_size_pages = pointer_delta(rounded_end, rounded_bottom, sizeof(char)) / page_size();

  // Try small pages if the chunk size is too small
  if (base_space_size_pages / lgrp_spaces()->length() == 0
      && page_size() > os::vm_page_size()) {
    // Changing the page size below can lead to freeing of memory. So we fail initialization.
    if (_must_use_large_pages) {
      vm_exit_during_initialization("Failed initializing NUMA with large pages. Too small heap size");
    }
    set_page_size(os::vm_page_size());
    rounded_bottom = align_up(bottom(), page_size());
    rounded_end = align_down(end(), page_size());
    base_space_size_pages = pointer_delta(rounded_end, rounded_bottom, sizeof(char)) / page_size();
  }
  guarantee(base_space_size_pages / lgrp_spaces()->length() > 0, "Space too small");
  set_base_space_size(base_space_size_pages);

  // Handle space resize
  MemRegion top_region, bottom_region;
  if (!old_region.equals(region())) {
    new_region = MemRegion(rounded_bottom, rounded_end);
    MemRegion intersection = new_region.intersection(old_region);
    if (intersection.start() == nullptr ||
        intersection.end() == nullptr   ||
        prev_page_size > page_size()) { // If the page size got smaller we have to change
                                        // the page size preference for the whole space.
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

    size_t chunk_byte_size = 0, old_chunk_byte_size = 0;
    if (i < lgrp_spaces()->length() - 1) {
      if (!UseAdaptiveNUMAChunkSizing                                ||
          (UseAdaptiveNUMAChunkSizing && NUMAChunkResizeWeight == 0) ||
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
        new_region = MemRegion(bottom(), rounded_bottom + (chunk_byte_size >> LogHeapWordSize));
      } else {
        new_region = MemRegion(bottom(), end());
      }
    } else
      if (i < lgrp_spaces()->length() - 1) { // Middle chunks
        MutableSpace *ps = lgrp_spaces()->at(i - 1)->space();
        new_region = MemRegion(ps->end(),
                               ps->end() + (chunk_byte_size >> LogHeapWordSize));
      } else { // Top chunk
        MutableSpace *ps = lgrp_spaces()->at(i - 1)->space();
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

    // Clear space (set top = bottom) but never mangle.
    s->initialize(new_region, SpaceDecorator::Clear, SpaceDecorator::DontMangle, MutableSpace::DontSetupPages);

    set_adaptation_cycles(samples_count());
  }
}

// Set the top of the whole space.
// Mark the holes in chunks below the top() as invalid.
void MutableNUMASpace::set_top(HeapWord* value) {
  bool found_top = false;
  for (int i = 0; i < lgrp_spaces()->length();) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    MutableSpace *s = ls->space();
    HeapWord *top = MAX2(align_down(s->top(), page_size()), s->bottom());

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

/*
   Linux supports static memory binding, therefore the most part of the
   logic dealing with the possible invalid page allocation is effectively
   disabled. Besides there is no notion of the home node in Linux. A
   thread is allowed to migrate freely. Although the scheduler is rather
   reluctant to move threads between the nodes. We check for the current
   node every allocation. And with a high probability a thread stays on
   the same node for some time allowing local access to recently allocated
   objects.
 */

HeapWord* MutableNUMASpace::cas_allocate(size_t size) {
  Thread* thr = Thread::current();
  int lgrp_id = thr->lgrp_id();
  if (lgrp_id == -1 || !os::numa_has_group_homing()) {
    lgrp_id = os::numa_get_group_id();
    thr->set_lgrp_id(lgrp_id);
  }

  int i = lgrp_space_index(lgrp_id);
  // It is possible that a new CPU has been hotplugged and
  // we haven't reshaped the space accordingly.
  if (i == -1) {
    i = os::random() % lgrp_spaces()->length();
  }
  LGRPSpace *ls = lgrp_spaces()->at(i);
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
      if (Atomic::cmpxchg(top_addr(), cur_top, cur_chunk_top) == cur_top) {
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

void MutableNUMASpace::print_on(outputStream* st) const {
  MutableSpace::print_on(st);
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    st->print("    lgrp %u", ls->lgrp_id());
    ls->space()->print_on(st);
    if (NUMAStats) {
      for (int i = 0; i < lgrp_spaces()->length(); i++) {
        lgrp_spaces()->at(i)->accumulate_statistics(page_size());
      }
      st->print("    local/remote/unbiased/uncommitted: " SIZE_FORMAT "K/"
                SIZE_FORMAT "K/" SIZE_FORMAT "K/" SIZE_FORMAT "K\n",
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
