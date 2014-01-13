/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/shared/collectorCounters.hpp"
#include "gc_implementation/shared/parGCAllocBuffer.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/blockOffsetTable.inline.hpp"
#include "memory/generation.inline.hpp"
#include "memory/generationSpec.hpp"
#include "memory/space.hpp"
#include "memory/tenuredGeneration.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "utilities/macros.hpp"

TenuredGeneration::TenuredGeneration(ReservedSpace rs,
                                     size_t initial_byte_size, int level,
                                     GenRemSet* remset) :
  OneContigSpaceCardGeneration(rs, initial_byte_size,
                               level, remset, NULL)
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
#if INCLUDE_ALL_GCS
  if (UseParNewGC) {
    typedef ParGCAllocBufferWithBOT* ParGCAllocBufferWithBOTPtr;
    _alloc_buffers = NEW_C_HEAP_ARRAY(ParGCAllocBufferWithBOTPtr,
                                      ParallelGCThreads, mtGC);
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
#endif // INCLUDE_ALL_GCS
}


const char* TenuredGeneration::name() const {
  return "tenured generation";
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

void TenuredGeneration::compute_new_size() {
  assert_locked_or_safepoint(Heap_lock);

  // Compute some numbers about the state of the heap.
  const size_t used_after_gc = used();
  const size_t capacity_after_gc = capacity();

  CardGeneration::compute_new_size();

  assert(used() == used_after_gc && used_after_gc <= capacity(),
         err_msg("used: " SIZE_FORMAT " used_after_gc: " SIZE_FORMAT
         " capacity: " SIZE_FORMAT, used(), used_after_gc, capacity()));
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


#if INCLUDE_ALL_GCS
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

#else  // INCLUDE_ALL_GCS
void TenuredGeneration::retire_alloc_buffers_before_full_gc() {}
void TenuredGeneration::verify_alloc_buffers_clean() {}
#endif // INCLUDE_ALL_GCS

bool TenuredGeneration::promotion_attempt_is_safe(size_t max_promotion_in_bytes) const {
  size_t available = max_contiguous_available();
  size_t av_promo  = (size_t)gc_stats()->avg_promoted()->padded_average();
  bool   res = (available >= av_promo) || (available >= max_promotion_in_bytes);
  if (PrintGC && Verbose) {
    gclog_or_tty->print_cr(
      "Tenured: promo attempt is%s safe: available("SIZE_FORMAT") %s av_promo("SIZE_FORMAT"),"
      "max_promo("SIZE_FORMAT")",
      res? "":" not", available, res? ">=":"<",
      av_promo, max_promotion_in_bytes);
  }
  return res;
}
