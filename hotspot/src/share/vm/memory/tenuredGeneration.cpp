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
#include "gc_implementation/shared/gcTimer.hpp"
#include "gc_implementation/shared/parGCAllocBuffer.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/blockOffsetTable.inline.hpp"
#include "memory/generationSpec.hpp"
#include "memory/genMarkSweep.hpp"
#include "memory/genOopClosures.inline.hpp"
#include "memory/space.hpp"
#include "memory/tenuredGeneration.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "utilities/macros.hpp"

TenuredGeneration::TenuredGeneration(ReservedSpace rs,
                                     size_t initial_byte_size, int level,
                                     GenRemSet* remset) :
  CardGeneration(rs, initial_byte_size, level, remset)
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
  GenCollectorPolicy* gcp = (GenCollectorPolicy*) GenCollectedHeap::heap()->collector_policy();

  // Generation Counters -- generation 1, 1 subspace
  _gen_counters = new GenerationCounters(gen_name, 1, 1,
      gcp->min_old_size(), gcp->max_old_size(), &_virtual_space);

  _gc_counters = new CollectorCounters("MSC", 1);

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
  // If we had to expand to accommodate promotions from younger generations
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

void TenuredGeneration::collect(bool   full,
                                bool   clear_all_soft_refs,
                                size_t size,
                                bool   is_tlab) {
  GenCollectedHeap* gch = GenCollectedHeap::heap();

  SpecializationStats::clear();
  // Temporarily expand the span of our ref processor, so
  // refs discovery is over the entire heap, not just this generation
  ReferenceProcessorSpanMutator
    x(ref_processor(), gch->reserved_region());

  STWGCTimer* gc_timer = GenMarkSweep::gc_timer();
  gc_timer->register_gc_start();

  SerialOldTracer* gc_tracer = GenMarkSweep::gc_tracer();
  gc_tracer->report_gc_start(gch->gc_cause(), gc_timer->gc_start());

  GenMarkSweep::invoke_at_safepoint(_level, ref_processor(), clear_all_soft_refs);

  gc_timer->register_gc_end();

  gc_tracer->report_gc_end(gc_timer->gc_end(), gc_timer->time_partitions());

  SpecializationStats::print();
}

HeapWord*
TenuredGeneration::expand_and_allocate(size_t word_size,
                                       bool is_tlab,
                                       bool parallel) {
  assert(!is_tlab, "TenuredGeneration does not support TLAB allocation");
  if (parallel) {
    MutexLocker x(ParGCRareEvent_lock);
    HeapWord* result = NULL;
    size_t byte_size = word_size * HeapWordSize;
    while (true) {
      expand(byte_size, _min_heap_delta_bytes);
      if (GCExpandToAllocateDelayMillis > 0) {
        os::sleep(Thread::current(), GCExpandToAllocateDelayMillis, false);
      }
      result = _the_space->par_allocate(word_size);
      if ( result != NULL) {
        return result;
      } else {
        // If there's not enough expansion space available, give up.
        if (_virtual_space.uncommitted_size() < byte_size) {
          return NULL;
        }
        // else try again
      }
    }
  } else {
    expand(word_size*HeapWordSize, _min_heap_delta_bytes);
    return _the_space->allocate(word_size);
  }
}

bool TenuredGeneration::expand(size_t bytes, size_t expand_bytes) {
  GCMutexLocker x(ExpandHeap_lock);
  return CardGeneration::expand(bytes, expand_bytes);
}


void TenuredGeneration::shrink(size_t bytes) {
  assert_locked_or_safepoint(ExpandHeap_lock);
  size_t size = ReservedSpace::page_align_size_down(bytes);
  if (size > 0) {
    shrink_by(size);
  }
}


size_t TenuredGeneration::capacity() const {
  return _the_space->capacity();
}


size_t TenuredGeneration::used() const {
  return _the_space->used();
}


size_t TenuredGeneration::free() const {
  return _the_space->free();
}

MemRegion TenuredGeneration::used_region() const {
  return the_space()->used_region();
}

size_t TenuredGeneration::unsafe_max_alloc_nogc() const {
  return _the_space->free();
}

size_t TenuredGeneration::contiguous_available() const {
  return _the_space->free() + _virtual_space.uncommitted_size();
}

bool TenuredGeneration::grow_by(size_t bytes) {
  assert_locked_or_safepoint(ExpandHeap_lock);
  bool result = _virtual_space.expand_by(bytes);
  if (result) {
    size_t new_word_size =
       heap_word_size(_virtual_space.committed_size());
    MemRegion mr(_the_space->bottom(), new_word_size);
    // Expand card table
    Universe::heap()->barrier_set()->resize_covered_region(mr);
    // Expand shared block offset array
    _bts->resize(new_word_size);

    // Fix for bug #4668531
    if (ZapUnusedHeapArea) {
      MemRegion mangle_region(_the_space->end(),
      (HeapWord*)_virtual_space.high());
      SpaceMangler::mangle_region(mangle_region);
    }

    // Expand space -- also expands space's BOT
    // (which uses (part of) shared array above)
    _the_space->set_end((HeapWord*)_virtual_space.high());

    // update the space and generation capacity counters
    update_counters();

    if (Verbose && PrintGC) {
      size_t new_mem_size = _virtual_space.committed_size();
      size_t old_mem_size = new_mem_size - bytes;
      gclog_or_tty->print_cr("Expanding %s from " SIZE_FORMAT "K by "
                      SIZE_FORMAT "K to " SIZE_FORMAT "K",
                      name(), old_mem_size/K, bytes/K, new_mem_size/K);
    }
  }
  return result;
}


bool TenuredGeneration::grow_to_reserved() {
  assert_locked_or_safepoint(ExpandHeap_lock);
  bool success = true;
  const size_t remaining_bytes = _virtual_space.uncommitted_size();
  if (remaining_bytes > 0) {
    success = grow_by(remaining_bytes);
    DEBUG_ONLY(if (!success) warning("grow to reserved failed");)
  }
  return success;
}

void TenuredGeneration::shrink_by(size_t bytes) {
  assert_locked_or_safepoint(ExpandHeap_lock);
  // Shrink committed space
  _virtual_space.shrink_by(bytes);
  // Shrink space; this also shrinks the space's BOT
  _the_space->set_end((HeapWord*) _virtual_space.high());
  size_t new_word_size = heap_word_size(_the_space->capacity());
  // Shrink the shared block offset array
  _bts->resize(new_word_size);
  MemRegion mr(_the_space->bottom(), new_word_size);
  // Shrink the card table
  Universe::heap()->barrier_set()->resize_covered_region(mr);

  if (Verbose && PrintGC) {
    size_t new_mem_size = _virtual_space.committed_size();
    size_t old_mem_size = new_mem_size + bytes;
    gclog_or_tty->print_cr("Shrinking %s from " SIZE_FORMAT "K to " SIZE_FORMAT "K",
                  name(), old_mem_size/K, new_mem_size/K);
  }
}

// Currently nothing to do.
void TenuredGeneration::prepare_for_verify() {}

void TenuredGeneration::object_iterate(ObjectClosure* blk) {
  _the_space->object_iterate(blk);
}

void TenuredGeneration::space_iterate(SpaceClosure* blk,
                                                 bool usedOnly) {
  blk->do_space(_the_space);
}

void TenuredGeneration::younger_refs_iterate(OopsInGenClosure* blk) {
  blk->set_generation(this);
  younger_refs_in_space_iterate(_the_space, blk);
  blk->reset_generation();
}

void TenuredGeneration::save_marks() {
  _the_space->set_saved_mark();
}


void TenuredGeneration::reset_saved_marks() {
  _the_space->reset_saved_mark();
}


bool TenuredGeneration::no_allocs_since_save_marks() {
  return _the_space->saved_mark_at_top();
}

#define TenuredGen_SINCE_SAVE_MARKS_ITERATE_DEFN(OopClosureType, nv_suffix)     \
                                                                                \
void TenuredGeneration::                                                        \
oop_since_save_marks_iterate##nv_suffix(OopClosureType* blk) {                  \
  blk->set_generation(this);                                                    \
  _the_space->oop_since_save_marks_iterate##nv_suffix(blk);                     \
  blk->reset_generation();                                                      \
  save_marks();                                                                 \
}

ALL_SINCE_SAVE_MARKS_CLOSURES(TenuredGen_SINCE_SAVE_MARKS_ITERATE_DEFN)

#undef TenuredGen_SINCE_SAVE_MARKS_ITERATE_DEFN


void TenuredGeneration::gc_epilogue(bool full) {
  // update the generation and space performance counters
  update_counters();
  if (ZapUnusedHeapArea) {
    the_space()->check_mangled_unused_area_complete();
  }
}

void TenuredGeneration::record_spaces_top() {
  assert(ZapUnusedHeapArea, "Not mangling unused space");
  the_space()->set_top_for_allocations();
}

void TenuredGeneration::verify() {
  the_space()->verify();
}

void TenuredGeneration::print_on(outputStream* st)  const {
  Generation::print_on(st);
  st->print("   the");
  the_space()->print_on(st);
}
