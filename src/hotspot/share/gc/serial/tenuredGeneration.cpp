/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/genMarkSweep.hpp"
#include "gc/serial/serialBlockOffsetTable.inline.hpp"
#include "gc/serial/serialHeap.hpp"
#include "gc/serial/tenuredGeneration.inline.hpp"
#include "gc/shared/collectorCounters.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/generationSpec.hpp"
#include "gc/shared/space.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "utilities/macros.hpp"

bool TenuredGeneration::grow_by(size_t bytes) {
  assert_correct_size_change_locking();
  bool result = _virtual_space.expand_by(bytes);
  if (result) {
    size_t new_word_size =
       heap_word_size(_virtual_space.committed_size());
    MemRegion mr(space()->bottom(), new_word_size);
    // Expand card table
    SerialHeap::heap()->rem_set()->resize_covered_region(mr);
    // Expand shared block offset array
    _bts->resize(new_word_size);

    // Fix for bug #4668531
    if (ZapUnusedHeapArea) {
      MemRegion mangle_region(space()->end(),
      (HeapWord*)_virtual_space.high());
      SpaceMangler::mangle_region(mangle_region);
    }

    // Expand space -- also expands space's BOT
    // (which uses (part of) shared array above)
    space()->set_end((HeapWord*)_virtual_space.high());

    // update the space and generation capacity counters
    update_counters();

    size_t new_mem_size = _virtual_space.committed_size();
    size_t old_mem_size = new_mem_size - bytes;
    log_trace(gc, heap)("Expanding %s from " SIZE_FORMAT "K by " SIZE_FORMAT "K to " SIZE_FORMAT "K",
                    name(), old_mem_size/K, bytes/K, new_mem_size/K);
  }
  return result;
}

bool TenuredGeneration::expand(size_t bytes, size_t expand_bytes) {
  assert_locked_or_safepoint(Heap_lock);
  if (bytes == 0) {
    return true;  // That's what grow_by(0) would return
  }
  size_t aligned_bytes  = ReservedSpace::page_align_size_up(bytes);
  if (aligned_bytes == 0){
    // The alignment caused the number of bytes to wrap.  An expand_by(0) will
    // return true with the implication that an expansion was done when it
    // was not.  A call to expand implies a best effort to expand by "bytes"
    // but not a guarantee.  Align down to give a best effort.  This is likely
    // the most that the generation can expand since it has some capacity to
    // start with.
    aligned_bytes = ReservedSpace::page_align_size_down(bytes);
  }
  size_t aligned_expand_bytes = ReservedSpace::page_align_size_up(expand_bytes);
  bool success = false;
  if (aligned_expand_bytes > aligned_bytes) {
    success = grow_by(aligned_expand_bytes);
  }
  if (!success) {
    success = grow_by(aligned_bytes);
  }
  if (!success) {
    success = grow_to_reserved();
  }
  if (success && GCLocker::is_active_and_needs_gc()) {
    log_trace(gc, heap)("Garbage collection disabled, expanded heap instead");
  }

  return success;
}

bool TenuredGeneration::grow_to_reserved() {
  assert_correct_size_change_locking();
  bool success = true;
  const size_t remaining_bytes = _virtual_space.uncommitted_size();
  if (remaining_bytes > 0) {
    success = grow_by(remaining_bytes);
    DEBUG_ONLY(if (!success) log_warning(gc)("grow to reserved failed");)
  }
  return success;
}

void TenuredGeneration::shrink(size_t bytes) {
  assert_correct_size_change_locking();

  size_t size = ReservedSpace::page_align_size_down(bytes);
  if (size == 0) {
    return;
  }

  // Shrink committed space
  _virtual_space.shrink_by(size);
  // Shrink space; this also shrinks the space's BOT
  space()->set_end((HeapWord*) _virtual_space.high());
  size_t new_word_size = heap_word_size(space()->capacity());
  // Shrink the shared block offset array
  _bts->resize(new_word_size);
  MemRegion mr(space()->bottom(), new_word_size);
  // Shrink the card table
  SerialHeap::heap()->rem_set()->resize_covered_region(mr);

  size_t new_mem_size = _virtual_space.committed_size();
  size_t old_mem_size = new_mem_size + size;
  log_trace(gc, heap)("Shrinking %s from " SIZE_FORMAT "K to " SIZE_FORMAT "K",
                      name(), old_mem_size/K, new_mem_size/K);
}

void TenuredGeneration::compute_new_size_inner() {
  assert(_shrink_factor <= 100, "invalid shrink factor");
  size_t current_shrink_factor = _shrink_factor;
  if (ShrinkHeapInSteps) {
    // Always reset '_shrink_factor' if the heap is shrunk in steps.
    // If we shrink the heap in this iteration, '_shrink_factor' will
    // be recomputed based on the old value further down in this function.
    _shrink_factor = 0;
  }

  // We don't have floating point command-line arguments
  // Note:  argument processing ensures that MinHeapFreeRatio < 100.
  const double minimum_free_percentage = MinHeapFreeRatio / 100.0;
  const double maximum_used_percentage = 1.0 - minimum_free_percentage;

  // Compute some numbers about the state of the heap.
  const size_t used_after_gc = used();
  const size_t capacity_after_gc = capacity();

  const double min_tmp = used_after_gc / maximum_used_percentage;
  size_t minimum_desired_capacity = (size_t)MIN2(min_tmp, double(max_uintx));
  // Don't shrink less than the initial generation size
  minimum_desired_capacity = MAX2(minimum_desired_capacity, initial_size());
  assert(used_after_gc <= minimum_desired_capacity, "sanity check");

    const size_t free_after_gc = free();
    const double free_percentage = ((double)free_after_gc) / capacity_after_gc;
    log_trace(gc, heap)("TenuredGeneration::compute_new_size:");
    log_trace(gc, heap)("    minimum_free_percentage: %6.2f  maximum_used_percentage: %6.2f",
                  minimum_free_percentage,
                  maximum_used_percentage);
    log_trace(gc, heap)("     free_after_gc   : %6.1fK   used_after_gc   : %6.1fK   capacity_after_gc   : %6.1fK",
                  free_after_gc / (double) K,
                  used_after_gc / (double) K,
                  capacity_after_gc / (double) K);
    log_trace(gc, heap)("     free_percentage: %6.2f", free_percentage);

  if (capacity_after_gc < minimum_desired_capacity) {
    // If we have less free space than we want then expand
    size_t expand_bytes = minimum_desired_capacity - capacity_after_gc;
    // Don't expand unless it's significant
    if (expand_bytes >= _min_heap_delta_bytes) {
      expand(expand_bytes, 0); // safe if expansion fails
    }
    log_trace(gc, heap)("    expanding:  minimum_desired_capacity: %6.1fK  expand_bytes: %6.1fK  _min_heap_delta_bytes: %6.1fK",
                  minimum_desired_capacity / (double) K,
                  expand_bytes / (double) K,
                  _min_heap_delta_bytes / (double) K);
    return;
  }

  // No expansion, now see if we want to shrink
  size_t shrink_bytes = 0;
  // We would never want to shrink more than this
  size_t max_shrink_bytes = capacity_after_gc - minimum_desired_capacity;

  if (MaxHeapFreeRatio < 100) {
    const double maximum_free_percentage = MaxHeapFreeRatio / 100.0;
    const double minimum_used_percentage = 1.0 - maximum_free_percentage;
    const double max_tmp = used_after_gc / minimum_used_percentage;
    size_t maximum_desired_capacity = (size_t)MIN2(max_tmp, double(max_uintx));
    maximum_desired_capacity = MAX2(maximum_desired_capacity, initial_size());
    log_trace(gc, heap)("    maximum_free_percentage: %6.2f  minimum_used_percentage: %6.2f",
                             maximum_free_percentage, minimum_used_percentage);
    log_trace(gc, heap)("    _capacity_at_prologue: %6.1fK  minimum_desired_capacity: %6.1fK  maximum_desired_capacity: %6.1fK",
                             _capacity_at_prologue / (double) K,
                             minimum_desired_capacity / (double) K,
                             maximum_desired_capacity / (double) K);
    assert(minimum_desired_capacity <= maximum_desired_capacity,
           "sanity check");

    if (capacity_after_gc > maximum_desired_capacity) {
      // Capacity too large, compute shrinking size
      shrink_bytes = capacity_after_gc - maximum_desired_capacity;
      if (ShrinkHeapInSteps) {
        // If ShrinkHeapInSteps is true (the default),
        // we don't want to shrink all the way back to initSize if people call
        // System.gc(), because some programs do that between "phases" and then
        // we'd just have to grow the heap up again for the next phase.  So we
        // damp the shrinking: 0% on the first call, 10% on the second call, 40%
        // on the third call, and 100% by the fourth call.  But if we recompute
        // size without shrinking, it goes back to 0%.
        shrink_bytes = shrink_bytes / 100 * current_shrink_factor;
        if (current_shrink_factor == 0) {
          _shrink_factor = 10;
        } else {
          _shrink_factor = MIN2(current_shrink_factor * 4, (size_t) 100);
        }
      }
      assert(shrink_bytes <= max_shrink_bytes, "invalid shrink size");
      log_trace(gc, heap)("    shrinking:  initSize: %.1fK  maximum_desired_capacity: %.1fK",
                               initial_size() / (double) K, maximum_desired_capacity / (double) K);
      log_trace(gc, heap)("    shrink_bytes: %.1fK  current_shrink_factor: " SIZE_FORMAT "  new shrink factor: " SIZE_FORMAT "  _min_heap_delta_bytes: %.1fK",
                               shrink_bytes / (double) K,
                               current_shrink_factor,
                               _shrink_factor,
                               _min_heap_delta_bytes / (double) K);
    }
  }

  if (capacity_after_gc > _capacity_at_prologue) {
    // We might have expanded for promotions, in which case we might want to
    // take back that expansion if there's room after GC.  That keeps us from
    // stretching the heap with promotions when there's plenty of room.
    size_t expansion_for_promotion = capacity_after_gc - _capacity_at_prologue;
    expansion_for_promotion = MIN2(expansion_for_promotion, max_shrink_bytes);
    // We have two shrinking computations, take the largest
    shrink_bytes = MAX2(shrink_bytes, expansion_for_promotion);
    assert(shrink_bytes <= max_shrink_bytes, "invalid shrink size");
    log_trace(gc, heap)("    aggressive shrinking:  _capacity_at_prologue: %.1fK  capacity_after_gc: %.1fK  expansion_for_promotion: %.1fK  shrink_bytes: %.1fK",
                        capacity_after_gc / (double) K,
                        _capacity_at_prologue / (double) K,
                        expansion_for_promotion / (double) K,
                        shrink_bytes / (double) K);
  }
  // Don't shrink unless it's significant
  if (shrink_bytes >= _min_heap_delta_bytes) {
    shrink(shrink_bytes);
  }
}

void TenuredGeneration::space_iterate(SpaceClosure* blk,
                                                 bool usedOnly) {
  blk->do_space(space());
}

void TenuredGeneration::younger_refs_iterate(OopIterateClosure* blk) {
  // Apply "cl->do_oop" to (the address of) (exactly) all the ref fields in
  // "sp" that point into the young generation.
  // The iteration is only over objects allocated at the start of the
  // iterations; objects allocated as a result of applying the closure are
  // not included.

  _rs->younger_refs_in_space_iterate(space(), blk);
}

TenuredGeneration::TenuredGeneration(ReservedSpace rs,
                                     size_t initial_byte_size,
                                     size_t min_byte_size,
                                     size_t max_byte_size,
                                     CardTableRS* remset) :
  Generation(rs, initial_byte_size), _rs(remset),
  _min_heap_delta_bytes(), _capacity_at_prologue(),
  _used_at_prologue()
{
  // If we don't shrink the heap in steps, '_shrink_factor' is always 100%.
  _shrink_factor = ShrinkHeapInSteps ? 0 : 100;
  HeapWord* start = (HeapWord*)rs.base();
  size_t reserved_byte_size = rs.size();
  assert((uintptr_t(start) & 3) == 0, "bad alignment");
  assert((reserved_byte_size & 3) == 0, "bad alignment");
  MemRegion reserved_mr(start, heap_word_size(reserved_byte_size));
  _bts = new BlockOffsetSharedArray(reserved_mr,
                                    heap_word_size(initial_byte_size));
  MemRegion committed_mr(start, heap_word_size(initial_byte_size));
  _rs->resize_covered_region(committed_mr);

  // Verify that the start and end of this generation is the start of a card.
  // If this wasn't true, a single card could span more than on generation,
  // which would cause problems when we commit/uncommit memory, and when we
  // clear and dirty cards.
  guarantee(_rs->is_aligned(reserved_mr.start()), "generation must be card aligned");
  if (reserved_mr.end() != SerialHeap::heap()->reserved_region().end()) {
    // Don't check at the very end of the heap as we'll assert that we're probing off
    // the end if we try.
    guarantee(_rs->is_aligned(reserved_mr.end()), "generation must be card aligned");
  }
  _min_heap_delta_bytes = MinHeapDeltaBytes;
  _capacity_at_prologue = initial_byte_size;
  _used_at_prologue = 0;
  HeapWord* bottom = (HeapWord*) _virtual_space.low();
  HeapWord* end    = (HeapWord*) _virtual_space.high();
  _the_space  = new TenuredSpace(_bts, MemRegion(bottom, end));
  // If we don't shrink the heap in steps, '_shrink_factor' is always 100%.
  _shrink_factor = ShrinkHeapInSteps ? 0 : 100;
  _capacity_at_prologue = 0;

  _gc_stats = new GCStats();

  // initialize performance counters

  const char* gen_name = "old";
  // Generation Counters -- generation 1, 1 subspace
  _gen_counters = new GenerationCounters(gen_name, 1, 1,
      min_byte_size, max_byte_size, &_virtual_space);

  _gc_counters = new CollectorCounters("Serial full collection pauses", 1);

  _space_counters = new CSpaceCounters(gen_name, 0,
                                       _virtual_space.reserved_size(),
                                       _the_space, _gen_counters);
}

void TenuredGeneration::gc_prologue(bool full) {
  _capacity_at_prologue = capacity();
  _used_at_prologue = used();
}

bool TenuredGeneration::should_collect(bool  full,
                                       size_t size,
                                       bool   is_tlab) {
  // This should be one big conditional or (||), but I want to be able to tell
  // why it returns what it returns (without re-evaluating the conditionals
  // in case they aren't idempotent), so I'm doing it this way.
  // DeMorgan says it's okay.
  if (full) {
    log_trace(gc)("TenuredGeneration::should_collect: because full");
    return true;
  }
  if (should_allocate(size, is_tlab)) {
    log_trace(gc)("TenuredGeneration::should_collect: because should_allocate(" SIZE_FORMAT ")", size);
    return true;
  }
  // If we don't have very much free space.
  // XXX: 10000 should be a percentage of the capacity!!!
  if (free() < 10000) {
    log_trace(gc)("TenuredGeneration::should_collect: because free(): " SIZE_FORMAT, free());
    return true;
  }
  // If we had to expand to accommodate promotions from the young generation
  if (_capacity_at_prologue < capacity()) {
    log_trace(gc)("TenuredGeneration::should_collect: because_capacity_at_prologue: " SIZE_FORMAT " < capacity(): " SIZE_FORMAT,
        _capacity_at_prologue, capacity());
    return true;
  }

  return false;
}

void TenuredGeneration::compute_new_size() {
  assert_locked_or_safepoint(Heap_lock);

  // Compute some numbers about the state of the heap.
  const size_t used_after_gc = used();
  const size_t capacity_after_gc = capacity();

  compute_new_size_inner();

  assert(used() == used_after_gc && used_after_gc <= capacity(),
         "used: " SIZE_FORMAT " used_after_gc: " SIZE_FORMAT
         " capacity: " SIZE_FORMAT, used(), used_after_gc, capacity());
}

void TenuredGeneration::update_gc_stats(Generation* current_generation,
                                        bool full) {
  // If the young generation has been collected, gather any statistics
  // that are of interest at this point.
  bool current_is_young = SerialHeap::heap()->is_young_gen(current_generation);
  if (!full && current_is_young) {
    // Calculate size of data promoted from the young generation
    // before doing the collection.
    size_t used_before_gc = used();

    // If the young gen collection was skipped, then the
    // number of promoted bytes will be 0 and adding it to the
    // average will incorrectly lessen the average.  It is, however,
    // also possible that no promotion was needed.
    if (used_before_gc >= _used_at_prologue) {
      size_t promoted_in_bytes = used_before_gc - _used_at_prologue;
      gc_stats()->avg_promoted()->sample(promoted_in_bytes);
    }
  }
}

void TenuredGeneration::update_counters() {
  if (UsePerfData) {
    _space_counters->update_all();
    _gen_counters->update_all();
  }
}

bool TenuredGeneration::promotion_attempt_is_safe(size_t max_promotion_in_bytes) const {
  size_t available = max_contiguous_available();
  size_t av_promo  = (size_t)gc_stats()->avg_promoted()->padded_average();
  bool   res = (available >= av_promo) || (available >= max_promotion_in_bytes);

  log_trace(gc)("Tenured: promo attempt is%s safe: available(" SIZE_FORMAT ") %s av_promo(" SIZE_FORMAT "), max_promo(" SIZE_FORMAT ")",
    res? "":" not", available, res? ">=":"<", av_promo, max_promotion_in_bytes);

  return res;
}

void TenuredGeneration::collect(bool   full,
                                bool   clear_all_soft_refs,
                                size_t size,
                                bool   is_tlab) {
  SerialHeap* gch = SerialHeap::heap();

  STWGCTimer* gc_timer = GenMarkSweep::gc_timer();
  gc_timer->register_gc_start();

  SerialOldTracer* gc_tracer = GenMarkSweep::gc_tracer();
  gc_tracer->report_gc_start(gch->gc_cause(), gc_timer->gc_start());

  gch->pre_full_gc_dump(gc_timer);

  GenMarkSweep::invoke_at_safepoint(clear_all_soft_refs);

  gch->post_full_gc_dump(gc_timer);

  gc_timer->register_gc_end();

  gc_tracer->report_gc_end(gc_timer->gc_end(), gc_timer->time_partitions());
}

HeapWord*
TenuredGeneration::expand_and_allocate(size_t word_size, bool is_tlab) {
  assert(!is_tlab, "TenuredGeneration does not support TLAB allocation");
  expand(word_size*HeapWordSize, _min_heap_delta_bytes);
  return _the_space->allocate(word_size);
}

size_t TenuredGeneration::unsafe_max_alloc_nogc() const {
  return _the_space->free();
}

size_t TenuredGeneration::contiguous_available() const {
  return _the_space->free() + _virtual_space.uncommitted_size();
}

void TenuredGeneration::assert_correct_size_change_locking() {
  assert_locked_or_safepoint(Heap_lock);
}

void TenuredGeneration::object_iterate(ObjectClosure* blk) {
  _the_space->object_iterate(blk);
}

void TenuredGeneration::complete_loaded_archive_space(MemRegion archive_space) {
  // Create the BOT for the archive space.
  TenuredSpace* space = _the_space;
  space->initialize_threshold();
  HeapWord* start = archive_space.start();
  while (start < archive_space.end()) {
    size_t word_size = _the_space->block_size(start);
    space->alloc_block(start, start + word_size);
    start += word_size;
  }
}

void TenuredGeneration::save_marks() {
  _the_space->set_saved_mark();
}

bool TenuredGeneration::no_allocs_since_save_marks() {
  return _the_space->saved_mark_at_top();
}

void TenuredGeneration::gc_epilogue(bool full) {
  // update the generation and space performance counters
  update_counters();
  if (ZapUnusedHeapArea) {
    _the_space->check_mangled_unused_area_complete();
  }
}

void TenuredGeneration::record_spaces_top() {
  assert(ZapUnusedHeapArea, "Not mangling unused space");
  _the_space->set_top_for_allocations();
}

void TenuredGeneration::verify() {
  _the_space->verify();
}

void TenuredGeneration::print_on(outputStream* st)  const {
  Generation::print_on(st);
  st->print("   the");
  _the_space->print_on(st);
}
