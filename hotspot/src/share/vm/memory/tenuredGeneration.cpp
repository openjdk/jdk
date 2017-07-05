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

# include "incls/_precompiled.incl"
# include "incls/_tenuredGeneration.cpp.incl"

TenuredGeneration::TenuredGeneration(ReservedSpace rs,
                                     size_t initial_byte_size, int level,
                                     GenRemSet* remset) :
  OneContigSpaceCardGeneration(rs, initial_byte_size,
                               MinHeapDeltaBytes, level, remset, NULL)
{
  HeapWord* bottom = (HeapWord*) _virtual_space.low();
  HeapWord* end    = (HeapWord*) _virtual_space.high();
  _the_space  = new TenuredSpace(_bts, MemRegion(bottom, end));
  _the_space->reset_saved_mark();
  _shrink_factor = 0;
  _capacity_at_prologue = 0;

  _gc_stats = new GCStats();

  // initialize performance counters

  const char* gen_name = "old";

  // Generation Counters -- generation 1, 1 subspace
  _gen_counters = new GenerationCounters(gen_name, 1, 1, &_virtual_space);

  _gc_counters = new CollectorCounters("MSC", 1);

  _space_counters = new CSpaceCounters(gen_name, 0,
                                       _virtual_space.reserved_size(),
                                       _the_space, _gen_counters);
#ifndef SERIALGC
  if (UseParNewGC && ParallelGCThreads > 0) {
    typedef ParGCAllocBufferWithBOT* ParGCAllocBufferWithBOTPtr;
    _alloc_buffers = NEW_C_HEAP_ARRAY(ParGCAllocBufferWithBOTPtr,
                                      ParallelGCThreads);
    if (_alloc_buffers == NULL)
      vm_exit_during_initialization("Could not allocate alloc_buffers");
    for (uint i = 0; i < ParallelGCThreads; i++) {
      _alloc_buffers[i] =
        new ParGCAllocBufferWithBOT(OldPLABSize, _bts);
      if (_alloc_buffers[i] == NULL)
        vm_exit_during_initialization("Could not allocate alloc_buffers");
    }
  } else {
    _alloc_buffers = NULL;
  }
#endif // SERIALGC
}


const char* TenuredGeneration::name() const {
  return "tenured generation";
}

void TenuredGeneration::compute_new_size() {
  assert(_shrink_factor <= 100, "invalid shrink factor");
  size_t current_shrink_factor = _shrink_factor;
  _shrink_factor = 0;

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
  minimum_desired_capacity = MAX2(minimum_desired_capacity,
                                  spec()->init_size());
  assert(used_after_gc <= minimum_desired_capacity, "sanity check");

  if (PrintGC && Verbose) {
    const size_t free_after_gc = free();
    const double free_percentage = ((double)free_after_gc) / capacity_after_gc;
    gclog_or_tty->print_cr("TenuredGeneration::compute_new_size: ");
    gclog_or_tty->print_cr("  "
                  "  minimum_free_percentage: %6.2f"
                  "  maximum_used_percentage: %6.2f",
                  minimum_free_percentage,
                  maximum_used_percentage);
    gclog_or_tty->print_cr("  "
                  "   free_after_gc   : %6.1fK"
                  "   used_after_gc   : %6.1fK"
                  "   capacity_after_gc   : %6.1fK",
                  free_after_gc / (double) K,
                  used_after_gc / (double) K,
                  capacity_after_gc / (double) K);
    gclog_or_tty->print_cr("  "
                  "   free_percentage: %6.2f",
                  free_percentage);
  }

  if (capacity_after_gc < minimum_desired_capacity) {
    // If we have less free space than we want then expand
    size_t expand_bytes = minimum_desired_capacity - capacity_after_gc;
    // Don't expand unless it's significant
    if (expand_bytes >= _min_heap_delta_bytes) {
      expand(expand_bytes, 0); // safe if expansion fails
    }
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("    expanding:"
                    "  minimum_desired_capacity: %6.1fK"
                    "  expand_bytes: %6.1fK"
                    "  _min_heap_delta_bytes: %6.1fK",
                    minimum_desired_capacity / (double) K,
                    expand_bytes / (double) K,
                    _min_heap_delta_bytes / (double) K);
    }
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
    maximum_desired_capacity = MAX2(maximum_desired_capacity,
                                    spec()->init_size());
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("  "
                             "  maximum_free_percentage: %6.2f"
                             "  minimum_used_percentage: %6.2f",
                             maximum_free_percentage,
                             minimum_used_percentage);
      gclog_or_tty->print_cr("  "
                             "  _capacity_at_prologue: %6.1fK"
                             "  minimum_desired_capacity: %6.1fK"
                             "  maximum_desired_capacity: %6.1fK",
                             _capacity_at_prologue / (double) K,
                             minimum_desired_capacity / (double) K,
                             maximum_desired_capacity / (double) K);
    }
    assert(minimum_desired_capacity <= maximum_desired_capacity,
           "sanity check");

    if (capacity_after_gc > maximum_desired_capacity) {
      // Capacity too large, compute shrinking size
      shrink_bytes = capacity_after_gc - maximum_desired_capacity;
      // We don't want shrink all the way back to initSize if people call
      // System.gc(), because some programs do that between "phases" and then
      // we'd just have to grow the heap up again for the next phase.  So we
      // damp the shrinking: 0% on the first call, 10% on the second call, 40%
      // on the third call, and 100% by the fourth call.  But if we recompute
      // size without shrinking, it goes back to 0%.
      shrink_bytes = shrink_bytes / 100 * current_shrink_factor;
      assert(shrink_bytes <= max_shrink_bytes, "invalid shrink size");
      if (current_shrink_factor == 0) {
        _shrink_factor = 10;
      } else {
        _shrink_factor = MIN2(current_shrink_factor * 4, (size_t) 100);
      }
      if (PrintGC && Verbose) {
        gclog_or_tty->print_cr("  "
                      "  shrinking:"
                      "  initSize: %.1fK"
                      "  maximum_desired_capacity: %.1fK",
                      spec()->init_size() / (double) K,
                      maximum_desired_capacity / (double) K);
        gclog_or_tty->print_cr("  "
                      "  shrink_bytes: %.1fK"
                      "  current_shrink_factor: %d"
                      "  new shrink factor: %d"
                      "  _min_heap_delta_bytes: %.1fK",
                      shrink_bytes / (double) K,
                      current_shrink_factor,
                      _shrink_factor,
                      _min_heap_delta_bytes / (double) K);
      }
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
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("  "
                             "  aggressive shrinking:"
                             "  _capacity_at_prologue: %.1fK"
                             "  capacity_after_gc: %.1fK"
                             "  expansion_for_promotion: %.1fK"
                             "  shrink_bytes: %.1fK",
                             capacity_after_gc / (double) K,
                             _capacity_at_prologue / (double) K,
                             expansion_for_promotion / (double) K,
                             shrink_bytes / (double) K);
    }
  }
  // Don't shrink unless it's significant
  if (shrink_bytes >= _min_heap_delta_bytes) {
    shrink(shrink_bytes);
  }
  assert(used() == used_after_gc && used_after_gc <= capacity(),
         "sanity check");
}

void TenuredGeneration::gc_prologue(bool full) {
  _capacity_at_prologue = capacity();
  _used_at_prologue = used();
  if (VerifyBeforeGC) {
    verify_alloc_buffers_clean();
  }
}

void TenuredGeneration::gc_epilogue(bool full) {
  if (VerifyAfterGC) {
    verify_alloc_buffers_clean();
  }
  OneContigSpaceCardGeneration::gc_epilogue(full);
}


bool TenuredGeneration::should_collect(bool  full,
                                       size_t size,
                                       bool   is_tlab) {
  // This should be one big conditional or (||), but I want to be able to tell
  // why it returns what it returns (without re-evaluating the conditionals
  // in case they aren't idempotent), so I'm doing it this way.
  // DeMorgan says it's okay.
  bool result = false;
  if (!result && full) {
    result = true;
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("TenuredGeneration::should_collect: because"
                    " full");
    }
  }
  if (!result && should_allocate(size, is_tlab)) {
    result = true;
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("TenuredGeneration::should_collect: because"
                    " should_allocate(" SIZE_FORMAT ")",
                    size);
    }
  }
  // If we don't have very much free space.
  // XXX: 10000 should be a percentage of the capacity!!!
  if (!result && free() < 10000) {
    result = true;
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("TenuredGeneration::should_collect: because"
                    " free(): " SIZE_FORMAT,
                    free());
    }
  }
  // If we had to expand to accomodate promotions from younger generations
  if (!result && _capacity_at_prologue < capacity()) {
    result = true;
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("TenuredGeneration::should_collect: because"
                    "_capacity_at_prologue: " SIZE_FORMAT " < capacity(): " SIZE_FORMAT,
                    _capacity_at_prologue, capacity());
    }
  }
  return result;
}

void TenuredGeneration::collect(bool   full,
                                bool   clear_all_soft_refs,
                                size_t size,
                                bool   is_tlab) {
  retire_alloc_buffers_before_full_gc();
  OneContigSpaceCardGeneration::collect(full, clear_all_soft_refs,
                                        size, is_tlab);
}

void TenuredGeneration::update_gc_stats(int current_level,
                                        bool full) {
  // If the next lower level(s) has been collected, gather any statistics
  // that are of interest at this point.
  if (!full && (current_level + 1) == level()) {
    // Calculate size of data promoted from the younger generations
    // before doing the collection.
    size_t used_before_gc = used();

    // If the younger gen collections were skipped, then the
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


#ifndef SERIALGC
oop TenuredGeneration::par_promote(int thread_num,
                                   oop old, markOop m, size_t word_sz) {

  ParGCAllocBufferWithBOT* buf = _alloc_buffers[thread_num];
  HeapWord* obj_ptr = buf->allocate(word_sz);
  bool is_lab = true;
  if (obj_ptr == NULL) {
#ifndef PRODUCT
    if (Universe::heap()->promotion_should_fail()) {
      return NULL;
    }
#endif  // #ifndef PRODUCT

    // Slow path:
    if (word_sz * 100 < ParallelGCBufferWastePct * buf->word_sz()) {
      // Is small enough; abandon this buffer and start a new one.
      size_t buf_size = buf->word_sz();
      HeapWord* buf_space =
        TenuredGeneration::par_allocate(buf_size, false);
      if (buf_space == NULL) {
        buf_space = expand_and_allocate(buf_size, false, true /* parallel*/);
      }
      if (buf_space != NULL) {
        buf->retire(false, false);
        buf->set_buf(buf_space);
        obj_ptr = buf->allocate(word_sz);
        assert(obj_ptr != NULL, "Buffer was definitely big enough...");
      }
    };
    // Otherwise, buffer allocation failed; try allocating object
    // individually.
    if (obj_ptr == NULL) {
      obj_ptr = TenuredGeneration::par_allocate(word_sz, false);
      if (obj_ptr == NULL) {
        obj_ptr = expand_and_allocate(word_sz, false, true /* parallel */);
      }
    }
    if (obj_ptr == NULL) return NULL;
  }
  assert(obj_ptr != NULL, "program logic");
  Copy::aligned_disjoint_words((HeapWord*)old, obj_ptr, word_sz);
  oop obj = oop(obj_ptr);
  // Restore the mark word copied above.
  obj->set_mark(m);
  return obj;
}

void TenuredGeneration::par_promote_alloc_undo(int thread_num,
                                               HeapWord* obj,
                                               size_t word_sz) {
  ParGCAllocBufferWithBOT* buf = _alloc_buffers[thread_num];
  if (buf->contains(obj)) {
    guarantee(buf->contains(obj + word_sz - 1),
              "should contain whole object");
    buf->undo_allocation(obj, word_sz);
  } else {
    CollectedHeap::fill_with_object(obj, word_sz);
  }
}

void TenuredGeneration::par_promote_alloc_done(int thread_num) {
  ParGCAllocBufferWithBOT* buf = _alloc_buffers[thread_num];
  buf->retire(true, ParallelGCRetainPLAB);
}

void TenuredGeneration::retire_alloc_buffers_before_full_gc() {
  if (UseParNewGC) {
    for (uint i = 0; i < ParallelGCThreads; i++) {
      _alloc_buffers[i]->retire(true /*end_of_gc*/, false /*retain*/);
    }
  }
}

// Verify that any retained parallel allocation buffers do not
// intersect with dirty cards.
void TenuredGeneration::verify_alloc_buffers_clean() {
  if (UseParNewGC) {
    for (uint i = 0; i < ParallelGCThreads; i++) {
      _rs->verify_aligned_region_empty(_alloc_buffers[i]->range());
    }
  }
}

#else  // SERIALGC
void TenuredGeneration::retire_alloc_buffers_before_full_gc() {}
void TenuredGeneration::verify_alloc_buffers_clean() {}
#endif // SERIALGC

bool TenuredGeneration::promotion_attempt_is_safe(
    size_t max_promotion_in_bytes,
    bool younger_handles_promotion_failure) const {

  bool result = max_contiguous_available() >= max_promotion_in_bytes;

  if (younger_handles_promotion_failure && !result) {
    result = max_contiguous_available() >=
      (size_t) gc_stats()->avg_promoted()->padded_average();
    if (PrintGC && Verbose && result) {
      gclog_or_tty->print_cr("TenuredGeneration::promotion_attempt_is_safe"
                  " contiguous_available: " SIZE_FORMAT
                  " avg_promoted: " SIZE_FORMAT,
                  max_contiguous_available(),
                  gc_stats()->avg_promoted()->padded_average());
    }
  } else {
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("TenuredGeneration::promotion_attempt_is_safe"
                  " contiguous_available: " SIZE_FORMAT
                  " promotion_in_bytes: " SIZE_FORMAT,
                  max_contiguous_available(), max_promotion_in_bytes);
    }
  }
  return result;
}
