/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psScavenge.hpp"
#include "gc/parallel/psYoungGen.hpp"
#include "gc/shared/gcUtil.hpp"
#include "gc/shared/genArguments.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "logging/log.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "utilities/align.hpp"

PSYoungGen::PSYoungGen(ReservedSpace rs, size_t initial_size, size_t min_size, size_t max_size) :
  _reserved(),
  _virtual_space(nullptr),
  _eden_space(nullptr),
  _from_space(nullptr),
  _to_space(nullptr),
  _min_gen_size(min_size),
  _max_gen_size(max_size),
  _gen_counters(nullptr),
  _eden_counters(nullptr),
  _from_counters(nullptr),
  _to_counters(nullptr)
{
  initialize(rs, initial_size, SpaceAlignment);
}

void PSYoungGen::initialize_virtual_space(ReservedSpace rs,
                                          size_t initial_size,
                                          size_t alignment) {
  assert(initial_size != 0, "Should have a finite size");
  _virtual_space = new PSVirtualSpace(rs, alignment);
  if (!virtual_space()->expand_by(initial_size)) {
    vm_exit_during_initialization("Could not reserve enough space for object heap");
  }
}

void PSYoungGen::initialize(ReservedSpace rs, size_t initial_size, size_t alignment) {
  initialize_virtual_space(rs, initial_size, alignment);
  initialize_work();
}

void PSYoungGen::initialize_work() {

  _reserved = MemRegion((HeapWord*)virtual_space()->low_boundary(),
                        (HeapWord*)virtual_space()->high_boundary());
  assert(_reserved.byte_size() == max_gen_size(), "invariant");

  MemRegion cmr((HeapWord*)virtual_space()->low(),
                (HeapWord*)virtual_space()->high());
  ParallelScavengeHeap::heap()->card_table()->resize_covered_region(cmr);

  if (ZapUnusedHeapArea) {
    // Mangle newly committed space immediately because it
    // can be done here more simply that after the new
    // spaces have been computed.
    SpaceMangler::mangle_region(cmr);
  }

  if (UseNUMA) {
    _eden_space = new MutableNUMASpace(virtual_space()->alignment());
  } else {
    _eden_space = new MutableSpace(virtual_space()->alignment());
  }
  _from_space = new MutableSpace(virtual_space()->alignment());
  _to_space   = new MutableSpace(virtual_space()->alignment());

  // Generation Counters - generation 0, 3 subspaces
  _gen_counters = new GenerationCounters("new", 0, 3, min_gen_size(),
                                         max_gen_size(), virtual_space()->committed_size());

  // Compute maximum space sizes for performance counters
  size_t alignment = SpaceAlignment;
  size_t size = virtual_space()->reserved_size();

  size_t max_survivor_size;
  size_t max_eden_size;

  if (UseAdaptiveSizePolicy) {
    max_survivor_size = size / MinSurvivorRatio;

    // round the survivor space size down to the nearest alignment
    // and make sure its size is greater than 0.
    max_survivor_size = align_down(max_survivor_size, alignment);
    max_survivor_size = MAX2(max_survivor_size, alignment);

    // set the maximum size of eden to be the size of the young gen
    // less two times the minimum survivor size. The minimum survivor
    // size for UseAdaptiveSizePolicy is one alignment.
    max_eden_size = size - 2 * alignment;
  } else {
    max_survivor_size = size / InitialSurvivorRatio;

    // round the survivor space size down to the nearest alignment
    // and make sure its size is greater than 0.
    max_survivor_size = align_down(max_survivor_size, alignment);
    max_survivor_size = MAX2(max_survivor_size, alignment);

    // set the maximum size of eden to be the size of the young gen
    // less two times the survivor size when the generation is 100%
    // committed. The minimum survivor size for -UseAdaptiveSizePolicy
    // is dependent on the committed portion (current capacity) of the
    // generation - the less space committed, the smaller the survivor
    // space, possibly as small as an alignment. However, we are interested
    // in the case where the young generation is 100% committed, as this
    // is the point where eden reaches its maximum size. At this point,
    // the size of a survivor space is max_survivor_size.
    max_eden_size = size - 2 * max_survivor_size;
  }

  _eden_counters = new SpaceCounters("eden", 0, max_eden_size, _eden_space,
                                     _gen_counters);
  _from_counters = new SpaceCounters("s0", 1, max_survivor_size, _from_space,
                                     _gen_counters);
  _to_counters = new SpaceCounters("s1", 2, max_survivor_size, _to_space,
                                   _gen_counters);

  compute_initial_space_boundaries();
}

void PSYoungGen::compute_initial_space_boundaries() {
  // Compute sizes
  size_t size = virtual_space()->committed_size();
  assert(size >= 3 * SpaceAlignment, "Young space is not large enough for eden + 2 survivors");

  size_t survivor_size = size / InitialSurvivorRatio;
  survivor_size = align_down(survivor_size, SpaceAlignment);
  // ... but never less than an alignment
  survivor_size = MAX2(survivor_size, SpaceAlignment);

  // Young generation is eden + 2 survivor spaces
  size_t eden_size = size - (2 * survivor_size);

  // Now go ahead and set 'em.
  set_space_boundaries(eden_size, survivor_size);
  space_invariants();

  if (UsePerfData) {
    _eden_counters->update_capacity();
    _from_counters->update_capacity();
    _to_counters->update_capacity();
  }
}

void PSYoungGen::set_space_boundaries(size_t eden_size, size_t survivor_size) {
  assert(eden_size < virtual_space()->committed_size(), "just checking");
  assert(eden_size > 0  && survivor_size > 0, "just checking");

  // Layout: to, from, eden
  char *to_start   = virtual_space()->low();
  char *to_end     = to_start + survivor_size;
  char *from_start = to_end;
  char *from_end   = from_start + survivor_size;
  char *eden_start = from_end;
  char *eden_end   = eden_start + eden_size;

  assert(eden_end == virtual_space()->high(), "just checking");

  assert(is_object_aligned(eden_start), "checking alignment");
  assert(is_object_aligned(to_start),   "checking alignment");
  assert(is_object_aligned(from_start), "checking alignment");

  MemRegion eden_mr((HeapWord*)eden_start, (HeapWord*)eden_end);
  MemRegion to_mr  ((HeapWord*)to_start, (HeapWord*)to_end);
  MemRegion from_mr((HeapWord*)from_start, (HeapWord*)from_end);

  WorkerThreads& pretouch_workers = ParallelScavengeHeap::heap()->workers();
  eden_space()->initialize(eden_mr, true, ZapUnusedHeapArea, MutableSpace::SetupPages, &pretouch_workers);
    to_space()->initialize(to_mr  , true, ZapUnusedHeapArea, MutableSpace::SetupPages, &pretouch_workers);
  from_space()->initialize(from_mr, true, ZapUnusedHeapArea, MutableSpace::SetupPages, &pretouch_workers);
}

#ifndef PRODUCT
void PSYoungGen::space_invariants() {
  guarantee(eden_space()->capacity_in_bytes() >= SpaceAlignment, "eden too small");
  guarantee(from_space()->capacity_in_bytes() >= SpaceAlignment, "from too small");
  assert(from_space()->capacity_in_bytes() == to_space()->capacity_in_bytes(), "inv");

  HeapWord* eden_bottom = eden_space()->bottom();
  HeapWord* eden_end    = eden_space()->end();
  HeapWord* eden_top    = eden_space()->top();

  HeapWord* from_bottom = from_space()->bottom();
  HeapWord* from_end    = from_space()->end();
  HeapWord* from_top    = from_space()->top();

  HeapWord* to_bottom   = to_space()->bottom();
  HeapWord* to_end      = to_space()->end();
  HeapWord* to_top      = to_space()->top();

  assert(eden_bottom <= eden_top && eden_top <= eden_end, "inv");
  assert(from_bottom <= from_top && from_top <= from_end, "inv");
  assert(to_bottom <= to_top && to_top <= to_end, "inv");

  // Relationship of spaces to each other; from/to, eden
  guarantee((char*)MIN2(from_bottom, to_bottom) == virtual_space()->low(), "inv");

  guarantee(is_aligned(eden_bottom, SpaceAlignment), "inv");
  guarantee(is_aligned(from_bottom, SpaceAlignment), "inv");
  guarantee(is_aligned(  to_bottom, SpaceAlignment), "inv");

  // Check whether from space is below to space
  if (from_bottom < to_bottom) {
    // from, to
    guarantee(from_end == to_bottom, "inv");
    guarantee(to_end == eden_bottom, "inv");
  } else {
    // to, from
    guarantee(to_end == from_bottom, "inv");
    guarantee(from_end == eden_bottom, "inv");
  }
  guarantee((char*)eden_end <= virtual_space()->high(), "inv");
  guarantee(is_aligned(eden_end, SpaceAlignment), "inv");

  // More checks that the virtual space is consistent with the spaces
  assert(virtual_space()->committed_size() >=
    (eden_space()->capacity_in_bytes() + 2 * from_space()->capacity_in_bytes()), "Committed size is inconsistent");
  assert(virtual_space()->committed_size() <= virtual_space()->reserved_size(),
    "Space invariant");

  virtual_space()->verify();
}
#endif

bool PSYoungGen::try_expand_to_hold(size_t word_size) {
  assert(eden_space()->free_in_words() < word_size, "precondition");

  // For logging purpose
  size_t original_committed_size = virtual_space()->committed_size();

  assert(is_aligned(virtual_space()->committed_high_addr(), SpaceAlignment), "inv");
  if (pointer_delta(virtual_space()->committed_high_addr(), eden_space()->top(), sizeof(HeapWord)) >= word_size) {
    // eden needs expansion but no OS committing
    assert(virtual_space()->committed_high_addr() > (char*)eden_space()->end(), "inv");
  } else {
    // eden needs OS committing and expansion
    assert(virtual_space()->reserved_high_addr() > virtual_space()->committed_high_addr(), "inv");

    const size_t existing_free_in_eden = eden_space()->free_in_words();
    assert(existing_free_in_eden < word_size, "inv");

    size_t delta_words = word_size - existing_free_in_eden;
    size_t delta_bytes = delta_words * HeapWordSize;
    delta_bytes = align_up(delta_bytes, virtual_space()->alignment());
    if (!virtual_space()->expand_by(delta_bytes)) {
      // Expansion fails at OS level.
      return false;
    }

    assert(is_aligned(virtual_space()->committed_high_addr(), SpaceAlignment), "inv");
  }

  HeapWord* new_eden_end = (HeapWord*) virtual_space()->committed_high_addr();
  assert(new_eden_end > eden_space()->end(), "inv");
  MemRegion edenMR = MemRegion(eden_space()->bottom(), new_eden_end);

  eden_space()->initialize(edenMR,
                           eden_space()->is_empty(),
                           SpaceDecorator::DontMangle,
                           MutableSpace::SetupPages,
                           &ParallelScavengeHeap::heap()->workers());

  if (ZapUnusedHeapArea) {
    eden_space()->mangle_unused_area();
  }
  post_resize();
  log_debug(gc, ergo)("PSYoung size changed (eden expansion): %zuK->%zuK",
                      original_committed_size / K, virtual_space()->committed_size() / K);
  return true;
}

HeapWord* PSYoungGen::expand_and_allocate(size_t word_size) {
  assert(SafepointSynchronize::is_at_safepoint(), "precondition");
  assert(Thread::current()->is_VM_thread(), "precondition");

  {
    size_t available_word_size = pointer_delta(virtual_space()->reserved_high_addr(),
                                               eden_space()->top(),
                                               sizeof(HeapWord));
    if (word_size > available_word_size) {
      return nullptr;
    }
  }

  if (eden_space()->free_in_words() < word_size) {
    if (!try_expand_to_hold(word_size)) {
      return nullptr;
    }
  }

  HeapWord* result = eden_space()->cas_allocate(word_size);
  assert(result, "inv");
  return result;
}

void PSYoungGen::compute_desired_sizes(bool is_survivor_overflowing,
                                       size_t& eden_size,
                                       size_t& survivor_size) {
  assert(eden_space()->is_empty() && to_space()->is_empty(), "precondition");
  assert(is_from_to_layout(), "precondition");

  // Current sizes for all three spaces
  const size_t current_eden_size = eden_space()->capacity_in_bytes();
  assert(from_space()->capacity_in_bytes() == to_space()->capacity_in_bytes(), "inv");
  const size_t current_survivor_size = from_space()->capacity_in_bytes();
  assert(current_eden_size + 2 * current_survivor_size <= max_gen_size(), "inv");

  PSAdaptiveSizePolicy* size_policy = ParallelScavengeHeap::heap()->size_policy();

  // eden-space
  eden_size = size_policy->compute_desired_eden_size(is_survivor_overflowing, current_eden_size);
  eden_size = align_up(eden_size, SpaceAlignment);
  assert(eden_size >= SpaceAlignment, "inv");

  survivor_size = size_policy->compute_desired_survivor_size(current_survivor_size, max_gen_size());
  survivor_size = MAX3(survivor_size,
                       from_space()->used_in_bytes(),
                       SpaceAlignment);
  survivor_size = align_up(survivor_size, SpaceAlignment);

  log_debug(gc, ergo)("Desired size eden: %zu K, survivor: %zu K", eden_size/K, survivor_size/K);

  const size_t new_gen_size = eden_size + 2 * survivor_size;
  if (new_gen_size < min_gen_size()) {
    // Keep survivor and adjust eden to meet min-gen-size
    eden_size = min_gen_size() - 2 * survivor_size;
  } else if (max_gen_size() < new_gen_size) {
    log_info(gc, ergo)("Requested sizes exceeds MaxNewSize (K): %zu vs %zu)", new_gen_size/K, max_gen_size()/K);
    // New capacity would exceed max; need to revise these desired sizes.
    // Favor survivor over eden in order to reduce promotion (overflow).
    if (2 * survivor_size >= max_gen_size()) {
      // If requested survivor size is too large
      survivor_size = align_down((max_gen_size() - SpaceAlignment) / 2, SpaceAlignment);
      eden_size = max_gen_size() - 2 * survivor_size;
    } else {
      // Respect survivor size and reduce eden
      eden_size = max_gen_size() - 2 * survivor_size;
    }
  }

  assert(eden_size >= SpaceAlignment, "inv");
  assert(survivor_size >= SpaceAlignment, "inv");

  assert(is_aligned(eden_size, SpaceAlignment), "inv");
  assert(is_aligned(survivor_size, SpaceAlignment), "inv");
}

void PSYoungGen::resize_inner(size_t desired_eden_size,
                              size_t desired_survivor_size) {
  assert(desired_eden_size != 0, "precondition");
  assert(desired_survivor_size != 0, "precondition");

  size_t desired_young_gen_size = desired_eden_size + 2 * desired_survivor_size;

  assert(desired_young_gen_size >= min_gen_size(), "precondition");
  assert(desired_young_gen_size <= max_gen_size(), "precondition");

  if (eden_space()->capacity_in_bytes() == desired_eden_size
      && from_space()->capacity_in_bytes() == desired_survivor_size) {
    // no change
    return;
  }

  bool resize_success = resize_generation(desired_young_gen_size);

  if (resize_success) {
    resize_spaces(desired_eden_size, desired_survivor_size);

    space_invariants();

    log_trace(gc, ergo)("Young generation size: "
                        "desired eden: %zu survivor: %zu"
                        " used: %zu capacity: %zu"
                        " gen limits: %zu / %zu",
                        desired_eden_size, desired_survivor_size, used_in_bytes(), capacity_in_bytes(),
                        max_gen_size(), min_gen_size());
  }
}

void PSYoungGen::resize_after_young_gc(bool is_survivor_overflowing) {
  assert(eden_space()->is_empty(), "precondition");
  assert(to_space()->is_empty(), "precondition");

  size_t desired_eden_size = 0;
  size_t desired_survivor_size = 0;

  compute_desired_sizes(is_survivor_overflowing,
                        desired_eden_size,
                        desired_survivor_size);

  resize_inner(desired_eden_size, desired_survivor_size);
}

bool PSYoungGen::resize_generation(size_t desired_young_gen_size) {
  const size_t alignment = virtual_space()->alignment();
  size_t orig_size = virtual_space()->committed_size();
  bool size_changed = false;

  assert(min_gen_size() <= orig_size && orig_size <= max_gen_size(), "just checking");

  size_t desired_size = clamp(align_up(desired_young_gen_size, alignment),
                              min_gen_size(),
                              max_gen_size());

  if (desired_size > orig_size) {
    // Grow the generation
    size_t change = desired_size - orig_size;
    assert(change % alignment == 0, "just checking");
    HeapWord* prev_high = (HeapWord*) virtual_space()->high();
    if (!virtual_space()->expand_by(change)) {
      return false; // Error if we fail to resize!
    }
    if (ZapUnusedHeapArea) {
      // Mangle newly committed space immediately because it
      // can be done here more simply that after the new
      // spaces have been computed.
      HeapWord* new_high = (HeapWord*) virtual_space()->high();
      MemRegion mangle_region(prev_high, new_high);
      SpaceMangler::mangle_region(mangle_region);
    }
    size_changed = true;
  } else if (desired_size < orig_size) {
    size_t desired_change = orig_size - desired_size;
    assert(desired_change % alignment == 0, "just checking");
    virtual_space()->shrink_by(desired_change);
    size_changed = true;
  } else {
    if (orig_size == max_gen_size()) {
      log_trace(gc)("PSYoung generation size at maximum: %zuK", orig_size/K);
    } else if (orig_size == min_gen_size()) {
      log_trace(gc)("PSYoung generation size at minimum: %zuK", orig_size/K);
    }
  }

  if (size_changed) {
    post_resize();
    log_trace(gc)("PSYoung generation size changed: %zuK->%zuK",
                  orig_size/K, virtual_space()->committed_size()/K);
  }

  guarantee(desired_young_gen_size <= virtual_space()->committed_size() ||
            virtual_space()->committed_size() == max_gen_size(), "Sanity");

  return true;
}

void PSYoungGen::resize_spaces(size_t requested_eden_size,
                               size_t requested_survivor_size) {
  assert(requested_eden_size > 0 && requested_survivor_size > 0,
         "precondition");
  assert(is_aligned(requested_eden_size, SpaceAlignment), "precondition");
  assert(is_aligned(requested_survivor_size, SpaceAlignment), "precondition");
  assert(from_space()->bottom() < to_space()->bottom(), "precondition");

  // layout: from, to, eden
  char* from_start = virtual_space()->low();
  char* from_end = from_start + requested_survivor_size;
  char* to_start = from_end;
  char* to_end = to_start + requested_survivor_size;
  char* eden_start = to_end;
  char* eden_end = eden_start + requested_eden_size;

  assert(eden_end <= virtual_space()->high(), "inv");

  MemRegion edenMR((HeapWord*)eden_start, (HeapWord*)eden_end);
  MemRegion fromMR((HeapWord*)from_start, (HeapWord*)from_end);
  MemRegion toMR  ((HeapWord*)to_start,   (HeapWord*)to_end);

#ifdef ASSERT
  if (!from_space()->is_empty()) {
    assert(fromMR.start() == from_space()->bottom(), "inv");
    assert(fromMR.contains(from_space()->used_region()), "inv");
  }
#endif
  // For logging below
  size_t old_from_capacity = from_space()->capacity_in_bytes();
  size_t old_to_capacity   = to_space()->capacity_in_bytes();

  WorkerThreads* workers = &ParallelScavengeHeap::heap()->workers();

  eden_space()->initialize(edenMR,
                           SpaceDecorator::Clear,
                           SpaceDecorator::DontMangle,
                           MutableSpace::SetupPages,
                           workers);
    to_space()->initialize(toMR,
                           SpaceDecorator::Clear,
                           SpaceDecorator::DontMangle,
                           MutableSpace::SetupPages,
                           workers);
  from_space()->initialize(fromMR,
                           from_space()->is_empty(),
                           SpaceDecorator::DontMangle,
                           MutableSpace::SetupPages,
                           workers);

  if (ZapUnusedHeapArea) {
    if (!UseNUMA) {
      eden_space()->mangle_unused_area();
    }
    to_space()->mangle_unused_area();
    from_space()->mangle_unused_area();
  }

  log_trace(gc, ergo)("AdaptiveSizePolicy::survivor sizes: (%zu, %zu) -> (%zu, %zu) ",
                      old_from_capacity, old_to_capacity,
                      from_space()->capacity_in_bytes(),
                      to_space()->capacity_in_bytes());
}

void PSYoungGen::swap_spaces() {
  MutableSpace* s    = from_space();
  _from_space        = to_space();
  _to_space          = s;
}

size_t PSYoungGen::capacity_in_bytes() const {
  return eden_space()->capacity_in_bytes()
       + from_space()->capacity_in_bytes();  // to_space() is only used during scavenge
}


size_t PSYoungGen::used_in_bytes() const {
  return eden_space()->used_in_bytes()
       + from_space()->used_in_bytes();      // to_space() is only used during scavenge
}


size_t PSYoungGen::free_in_bytes() const {
  return eden_space()->free_in_bytes()
       + from_space()->free_in_bytes();      // to_space() is only used during scavenge
}

size_t PSYoungGen::capacity_in_words() const {
  return eden_space()->capacity_in_words()
       + from_space()->capacity_in_words();  // to_space() is only used during scavenge
}


size_t PSYoungGen::used_in_words() const {
  return eden_space()->used_in_words()
       + from_space()->used_in_words();      // to_space() is only used during scavenge
}


size_t PSYoungGen::free_in_words() const {
  return eden_space()->free_in_words()
       + from_space()->free_in_words();      // to_space() is only used during scavenge
}

void PSYoungGen::object_iterate(ObjectClosure* blk) {
  eden_space()->object_iterate(blk);
  from_space()->object_iterate(blk);
  to_space()->object_iterate(blk);
}

void PSYoungGen::print() const { print_on(tty); }
void PSYoungGen::print_on(outputStream* st) const {
  st->print("%-15s", name());
  st->print(" total %zuK, used %zuK ", capacity_in_bytes() / K, used_in_bytes() / K);
  virtual_space()->print_space_boundaries_on(st);

  StreamIndentor si(st, 1);
  eden_space()->print_on(st, "eden ");
  from_space()->print_on(st, "from ");
  to_space()->print_on(st, "to   ");
}

void PSYoungGen::post_resize() {
  assert_locked_or_safepoint(Heap_lock);

  MemRegion cmr((HeapWord*)virtual_space()->low(),
                (HeapWord*)virtual_space()->high());
  ParallelScavengeHeap::heap()->card_table()->resize_covered_region(cmr);
}

void PSYoungGen::update_counters() {
  if (UsePerfData) {
    _eden_counters->update_all();
    _from_counters->update_all();
    _to_counters->update_all();
    _gen_counters->update_capacity(_virtual_space->committed_size());
  }
}

void PSYoungGen::verify() {
  eden_space()->verify();
  from_space()->verify();
  to_space()->verify();
}
