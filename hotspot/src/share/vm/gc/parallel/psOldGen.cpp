/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psAdaptiveSizePolicy.hpp"
#include "gc/parallel/psMarkSweepDecorator.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/shared/cardTableModRefBS.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "logging/log.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"

inline const char* PSOldGen::select_name() {
  return UseParallelOldGC ? "ParOldGen" : "PSOldGen";
}

PSOldGen::PSOldGen(ReservedSpace rs, size_t alignment,
                   size_t initial_size, size_t min_size, size_t max_size,
                   const char* perf_data_name, int level):
  _name(select_name()), _init_gen_size(initial_size), _min_gen_size(min_size),
  _max_gen_size(max_size)
{
  initialize(rs, alignment, perf_data_name, level);
}

PSOldGen::PSOldGen(size_t initial_size,
                   size_t min_size, size_t max_size,
                   const char* perf_data_name, int level):
  _name(select_name()), _init_gen_size(initial_size), _min_gen_size(min_size),
  _max_gen_size(max_size)
{}

void PSOldGen::initialize(ReservedSpace rs, size_t alignment,
                          const char* perf_data_name, int level) {
  initialize_virtual_space(rs, alignment);
  initialize_work(perf_data_name, level);

  // The old gen can grow to gen_size_limit().  _reserve reflects only
  // the current maximum that can be committed.
  assert(_reserved.byte_size() <= gen_size_limit(), "Consistency check");

  initialize_performance_counters(perf_data_name, level);
}

void PSOldGen::initialize_virtual_space(ReservedSpace rs, size_t alignment) {

  _virtual_space = new PSVirtualSpace(rs, alignment);
  if (!_virtual_space->expand_by(_init_gen_size)) {
    vm_exit_during_initialization("Could not reserve enough space for "
                                  "object heap");
  }
}

void PSOldGen::initialize_work(const char* perf_data_name, int level) {
  //
  // Basic memory initialization
  //

  MemRegion limit_reserved((HeapWord*)virtual_space()->low_boundary(),
    heap_word_size(_max_gen_size));
  assert(limit_reserved.byte_size() == _max_gen_size,
    "word vs bytes confusion");
  //
  // Object start stuff
  //

  start_array()->initialize(limit_reserved);

  _reserved = MemRegion((HeapWord*)virtual_space()->low_boundary(),
                        (HeapWord*)virtual_space()->high_boundary());

  //
  // Card table stuff
  //

  MemRegion cmr((HeapWord*)virtual_space()->low(),
                (HeapWord*)virtual_space()->high());
  if (ZapUnusedHeapArea) {
    // Mangle newly committed space immediately rather than
    // waiting for the initialization of the space even though
    // mangling is related to spaces.  Doing it here eliminates
    // the need to carry along information that a complete mangling
    // (bottom to end) needs to be done.
    SpaceMangler::mangle_region(cmr);
  }

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  BarrierSet* bs = heap->barrier_set();

  bs->resize_covered_region(cmr);

  CardTableModRefBS* ct = barrier_set_cast<CardTableModRefBS>(bs);

  // Verify that the start and end of this generation is the start of a card.
  // If this wasn't true, a single card could span more than one generation,
  // which would cause problems when we commit/uncommit memory, and when we
  // clear and dirty cards.
  guarantee(ct->is_card_aligned(_reserved.start()), "generation must be card aligned");
  if (_reserved.end() != heap->reserved_region().end()) {
    // Don't check at the very end of the heap as we'll assert that we're probing off
    // the end if we try.
    guarantee(ct->is_card_aligned(_reserved.end()), "generation must be card aligned");
  }

  //
  // ObjectSpace stuff
  //

  _object_space = new MutableSpace(virtual_space()->alignment());

  if (_object_space == NULL)
    vm_exit_during_initialization("Could not allocate an old gen space");

  object_space()->initialize(cmr,
                             SpaceDecorator::Clear,
                             SpaceDecorator::Mangle);

  _object_mark_sweep = new PSMarkSweepDecorator(_object_space, start_array(), MarkSweepDeadRatio);

  if (_object_mark_sweep == NULL)
    vm_exit_during_initialization("Could not complete allocation of old generation");

  // Update the start_array
  start_array()->set_covered_region(cmr);
}

void PSOldGen::initialize_performance_counters(const char* perf_data_name, int level) {
  // Generation Counters, generation 'level', 1 subspace
  _gen_counters = new PSGenerationCounters(perf_data_name, level, 1, _min_gen_size,
                                           _max_gen_size, virtual_space());
  _space_counters = new SpaceCounters(perf_data_name, 0,
                                      virtual_space()->reserved_size(),
                                      _object_space, _gen_counters);
}

// Assume that the generation has been allocated if its
// reserved size is not 0.
bool  PSOldGen::is_allocated() {
  return virtual_space()->reserved_size() != 0;
}

void PSOldGen::precompact() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  // Reset start array first.
  start_array()->reset();

  object_mark_sweep()->precompact();

  // Now compact the young gen
  heap->young_gen()->precompact();
}

void PSOldGen::adjust_pointers() {
  object_mark_sweep()->adjust_pointers();
}

void PSOldGen::compact() {
  object_mark_sweep()->compact(ZapUnusedHeapArea);
}

size_t PSOldGen::contiguous_available() const {
  return object_space()->free_in_bytes() + virtual_space()->uncommitted_size();
}

// Allocation. We report all successful allocations to the size policy
// Note that the perm gen does not use this method, and should not!
HeapWord* PSOldGen::allocate(size_t word_size) {
  assert_locked_or_safepoint(Heap_lock);
  HeapWord* res = allocate_noexpand(word_size);

  if (res == NULL) {
    res = expand_and_allocate(word_size);
  }

  // Allocations in the old generation need to be reported
  if (res != NULL) {
    ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
    heap->size_policy()->tenured_allocation(word_size * HeapWordSize);
  }

  return res;
}

HeapWord* PSOldGen::expand_and_allocate(size_t word_size) {
  expand(word_size*HeapWordSize);
  if (GCExpandToAllocateDelayMillis > 0) {
    os::sleep(Thread::current(), GCExpandToAllocateDelayMillis, false);
  }
  return allocate_noexpand(word_size);
}

HeapWord* PSOldGen::expand_and_cas_allocate(size_t word_size) {
  expand(word_size*HeapWordSize);
  if (GCExpandToAllocateDelayMillis > 0) {
    os::sleep(Thread::current(), GCExpandToAllocateDelayMillis, false);
  }
  return cas_allocate_noexpand(word_size);
}

void PSOldGen::expand(size_t bytes) {
  if (bytes == 0) {
    return;
  }
  MutexLocker x(ExpandHeap_lock);
  const size_t alignment = virtual_space()->alignment();
  size_t aligned_bytes  = align_size_up(bytes, alignment);
  size_t aligned_expand_bytes = align_size_up(MinHeapDeltaBytes, alignment);

  if (UseNUMA) {
    // With NUMA we use round-robin page allocation for the old gen. Expand by at least
    // providing a page per lgroup. Alignment is larger or equal to the page size.
    aligned_expand_bytes = MAX2(aligned_expand_bytes, alignment * os::numa_get_groups_num());
  }
  if (aligned_bytes == 0){
    // The alignment caused the number of bytes to wrap.  An expand_by(0) will
    // return true with the implication that and expansion was done when it
    // was not.  A call to expand implies a best effort to expand by "bytes"
    // but not a guarantee.  Align down to give a best effort.  This is likely
    // the most that the generation can expand since it has some capacity to
    // start with.
    aligned_bytes = align_size_down(bytes, alignment);
  }

  bool success = false;
  if (aligned_expand_bytes > aligned_bytes) {
    success = expand_by(aligned_expand_bytes);
  }
  if (!success) {
    success = expand_by(aligned_bytes);
  }
  if (!success) {
    success = expand_to_reserved();
  }

  if (success && GCLocker::is_active_and_needs_gc()) {
    log_debug(gc)("Garbage collection disabled, expanded heap instead");
  }
}

bool PSOldGen::expand_by(size_t bytes) {
  assert_lock_strong(ExpandHeap_lock);
  assert_locked_or_safepoint(Heap_lock);
  if (bytes == 0) {
    return true;  // That's what virtual_space()->expand_by(0) would return
  }
  bool result = virtual_space()->expand_by(bytes);
  if (result) {
    if (ZapUnusedHeapArea) {
      // We need to mangle the newly expanded area. The memregion spans
      // end -> new_end, we assume that top -> end is already mangled.
      // Do the mangling before post_resize() is called because
      // the space is available for allocation after post_resize();
      HeapWord* const virtual_space_high = (HeapWord*) virtual_space()->high();
      assert(object_space()->end() < virtual_space_high,
        "Should be true before post_resize()");
      MemRegion mangle_region(object_space()->end(), virtual_space_high);
      // Note that the object space has not yet been updated to
      // coincide with the new underlying virtual space.
      SpaceMangler::mangle_region(mangle_region);
    }
    post_resize();
    if (UsePerfData) {
      _space_counters->update_capacity();
      _gen_counters->update_all();
    }
  }

  if (result) {
    size_t new_mem_size = virtual_space()->committed_size();
    size_t old_mem_size = new_mem_size - bytes;
    log_debug(gc)("Expanding %s from " SIZE_FORMAT "K by " SIZE_FORMAT "K to " SIZE_FORMAT "K",
                  name(), old_mem_size/K, bytes/K, new_mem_size/K);
  }

  return result;
}

bool PSOldGen::expand_to_reserved() {
  assert_lock_strong(ExpandHeap_lock);
  assert_locked_or_safepoint(Heap_lock);

  bool result = true;
  const size_t remaining_bytes = virtual_space()->uncommitted_size();
  if (remaining_bytes > 0) {
    result = expand_by(remaining_bytes);
    DEBUG_ONLY(if (!result) warning("grow to reserve failed"));
  }
  return result;
}

void PSOldGen::shrink(size_t bytes) {
  assert_lock_strong(ExpandHeap_lock);
  assert_locked_or_safepoint(Heap_lock);

  size_t size = align_size_down(bytes, virtual_space()->alignment());
  if (size > 0) {
    assert_lock_strong(ExpandHeap_lock);
    virtual_space()->shrink_by(bytes);
    post_resize();

    size_t new_mem_size = virtual_space()->committed_size();
    size_t old_mem_size = new_mem_size + bytes;
    log_debug(gc)("Shrinking %s from " SIZE_FORMAT "K by " SIZE_FORMAT "K to " SIZE_FORMAT "K",
                  name(), old_mem_size/K, bytes/K, new_mem_size/K);
  }
}

void PSOldGen::resize(size_t desired_free_space) {
  const size_t alignment = virtual_space()->alignment();
  const size_t size_before = virtual_space()->committed_size();
  size_t new_size = used_in_bytes() + desired_free_space;
  if (new_size < used_in_bytes()) {
    // Overflowed the addition.
    new_size = gen_size_limit();
  }
  // Adjust according to our min and max
  new_size = MAX2(MIN2(new_size, gen_size_limit()), min_gen_size());

  assert(gen_size_limit() >= reserved().byte_size(), "max new size problem?");
  new_size = align_size_up(new_size, alignment);

  const size_t current_size = capacity_in_bytes();

  log_trace(gc, ergo)("AdaptiveSizePolicy::old generation size: "
    "desired free: " SIZE_FORMAT " used: " SIZE_FORMAT
    " new size: " SIZE_FORMAT " current size " SIZE_FORMAT
    " gen limits: " SIZE_FORMAT " / " SIZE_FORMAT,
    desired_free_space, used_in_bytes(), new_size, current_size,
    gen_size_limit(), min_gen_size());

  if (new_size == current_size) {
    // No change requested
    return;
  }
  if (new_size > current_size) {
    size_t change_bytes = new_size - current_size;
    expand(change_bytes);
  } else {
    size_t change_bytes = current_size - new_size;
    // shrink doesn't grab this lock, expand does. Is that right?
    MutexLocker x(ExpandHeap_lock);
    shrink(change_bytes);
  }

  log_trace(gc, ergo)("AdaptiveSizePolicy::old generation size: collection: %d (" SIZE_FORMAT ") -> (" SIZE_FORMAT ") ",
                      ParallelScavengeHeap::heap()->total_collections(),
                      size_before,
                      virtual_space()->committed_size());
}

// NOTE! We need to be careful about resizing. During a GC, multiple
// allocators may be active during heap expansion. If we allow the
// heap resizing to become visible before we have correctly resized
// all heap related data structures, we may cause program failures.
void PSOldGen::post_resize() {
  // First construct a memregion representing the new size
  MemRegion new_memregion((HeapWord*)virtual_space()->low(),
    (HeapWord*)virtual_space()->high());
  size_t new_word_size = new_memregion.word_size();

  start_array()->set_covered_region(new_memregion);
  ParallelScavengeHeap::heap()->barrier_set()->resize_covered_region(new_memregion);

  // ALWAYS do this last!!
  object_space()->initialize(new_memregion,
                             SpaceDecorator::DontClear,
                             SpaceDecorator::DontMangle);

  assert(new_word_size == heap_word_size(object_space()->capacity_in_bytes()),
    "Sanity");
}

size_t PSOldGen::gen_size_limit() {
  return _max_gen_size;
}

void PSOldGen::reset_after_change() {
  ShouldNotReachHere();
  return;
}

size_t PSOldGen::available_for_expansion() {
  ShouldNotReachHere();
  return 0;
}

size_t PSOldGen::available_for_contraction() {
  ShouldNotReachHere();
  return 0;
}

void PSOldGen::print() const { print_on(tty);}
void PSOldGen::print_on(outputStream* st) const {
  st->print(" %-15s", name());
  st->print(" total " SIZE_FORMAT "K, used " SIZE_FORMAT "K",
              capacity_in_bytes()/K, used_in_bytes()/K);
  st->print_cr(" [" INTPTR_FORMAT ", " INTPTR_FORMAT ", " INTPTR_FORMAT ")",
                p2i(virtual_space()->low_boundary()),
                p2i(virtual_space()->high()),
                p2i(virtual_space()->high_boundary()));

  st->print("  object"); object_space()->print_on(st);
}

void PSOldGen::print_used_change(size_t prev_used) const {
  log_info(gc, heap)("%s: "  SIZE_FORMAT "K->" SIZE_FORMAT "K("  SIZE_FORMAT "K)",
      name(), prev_used / K, used_in_bytes() / K, capacity_in_bytes() / K);
}

void PSOldGen::update_counters() {
  if (UsePerfData) {
    _space_counters->update_all();
    _gen_counters->update_all();
  }
}

#ifndef PRODUCT

void PSOldGen::space_invariants() {
  assert(object_space()->end() == (HeapWord*) virtual_space()->high(),
    "Space invariant");
  assert(object_space()->bottom() == (HeapWord*) virtual_space()->low(),
    "Space invariant");
  assert(virtual_space()->low_boundary() <= virtual_space()->low(),
    "Space invariant");
  assert(virtual_space()->high_boundary() >= virtual_space()->high(),
    "Space invariant");
  assert(virtual_space()->low_boundary() == (char*) _reserved.start(),
    "Space invariant");
  assert(virtual_space()->high_boundary() == (char*) _reserved.end(),
    "Space invariant");
  assert(virtual_space()->committed_size() <= virtual_space()->reserved_size(),
    "Space invariant");
}
#endif

void PSOldGen::verify() {
  object_space()->verify();
}
class VerifyObjectStartArrayClosure : public ObjectClosure {
  PSOldGen* _old_gen;
  ObjectStartArray* _start_array;

 public:
  VerifyObjectStartArrayClosure(PSOldGen* old_gen, ObjectStartArray* start_array) :
    _old_gen(old_gen), _start_array(start_array) { }

  virtual void do_object(oop obj) {
    HeapWord* test_addr = (HeapWord*)obj + 1;
    guarantee(_start_array->object_start(test_addr) == (HeapWord*)obj, "ObjectStartArray cannot find start of object");
    guarantee(_start_array->is_block_allocated((HeapWord*)obj), "ObjectStartArray missing block allocation");
  }
};

void PSOldGen::verify_object_start_array() {
  VerifyObjectStartArrayClosure check( this, &_start_array );
  object_iterate(&check);
}

#ifndef PRODUCT
void PSOldGen::record_spaces_top() {
  assert(ZapUnusedHeapArea, "Not mangling unused space");
  object_space()->set_top_for_allocations();
}
#endif
