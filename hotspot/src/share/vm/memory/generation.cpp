/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/shared/gcTimer.hpp"
#include "gc_implementation/shared/gcTrace.hpp"
#include "gc_implementation/shared/spaceDecorator.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/blockOffsetTable.inline.hpp"
#include "memory/cardTableRS.hpp"
#include "memory/gcLocker.inline.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/genMarkSweep.hpp"
#include "memory/genOopClosures.hpp"
#include "memory/genOopClosures.inline.hpp"
#include "memory/generation.hpp"
#include "memory/generation.inline.hpp"
#include "memory/space.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"

Generation::Generation(ReservedSpace rs, size_t initial_size, int level) :
  _level(level),
  _ref_processor(NULL) {
  if (!_virtual_space.initialize(rs, initial_size)) {
    vm_exit_during_initialization("Could not reserve enough space for "
                    "object heap");
  }
  // Mangle all of the the initial generation.
  if (ZapUnusedHeapArea) {
    MemRegion mangle_region((HeapWord*)_virtual_space.low(),
      (HeapWord*)_virtual_space.high());
    SpaceMangler::mangle_region(mangle_region);
  }
  _reserved = MemRegion((HeapWord*)_virtual_space.low_boundary(),
          (HeapWord*)_virtual_space.high_boundary());
}

GenerationSpec* Generation::spec() {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  assert(0 <= level() && level() < gch->_n_gens, "Bad gen level");
  return gch->_gen_specs[level()];
}

size_t Generation::max_capacity() const {
  return reserved().byte_size();
}

void Generation::print_heap_change(size_t prev_used) const {
  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print(" "  SIZE_FORMAT
                        "->" SIZE_FORMAT
                        "("  SIZE_FORMAT ")",
                        prev_used, used(), capacity());
  } else {
    gclog_or_tty->print(" "  SIZE_FORMAT "K"
                        "->" SIZE_FORMAT "K"
                        "("  SIZE_FORMAT "K)",
                        prev_used / K, used() / K, capacity() / K);
  }
}

// By default we get a single threaded default reference processor;
// generations needing multi-threaded refs processing or discovery override this method.
void Generation::ref_processor_init() {
  assert(_ref_processor == NULL, "a reference processor already exists");
  assert(!_reserved.is_empty(), "empty generation?");
  _ref_processor = new ReferenceProcessor(_reserved);    // a vanilla reference processor
  if (_ref_processor == NULL) {
    vm_exit_during_initialization("Could not allocate ReferenceProcessor object");
  }
}

void Generation::print() const { print_on(tty); }

void Generation::print_on(outputStream* st)  const {
  st->print(" %-20s", name());
  st->print(" total " SIZE_FORMAT "K, used " SIZE_FORMAT "K",
             capacity()/K, used()/K);
  st->print_cr(" [" INTPTR_FORMAT ", " INTPTR_FORMAT ", " INTPTR_FORMAT ")",
              _virtual_space.low_boundary(),
              _virtual_space.high(),
              _virtual_space.high_boundary());
}

void Generation::print_summary_info() { print_summary_info_on(tty); }

void Generation::print_summary_info_on(outputStream* st) {
  StatRecord* sr = stat_record();
  double time = sr->accumulated_time.seconds();
  st->print_cr("[Accumulated GC generation %d time %3.7f secs, "
               "%d GC's, avg GC time %3.7f]",
               level(), time, sr->invocations,
               sr->invocations > 0 ? time / sr->invocations : 0.0);
}

// Utility iterator classes

class GenerationIsInReservedClosure : public SpaceClosure {
 public:
  const void* _p;
  Space* sp;
  virtual void do_space(Space* s) {
    if (sp == NULL) {
      if (s->is_in_reserved(_p)) sp = s;
    }
  }
  GenerationIsInReservedClosure(const void* p) : _p(p), sp(NULL) {}
};

class GenerationIsInClosure : public SpaceClosure {
 public:
  const void* _p;
  Space* sp;
  virtual void do_space(Space* s) {
    if (sp == NULL) {
      if (s->is_in(_p)) sp = s;
    }
  }
  GenerationIsInClosure(const void* p) : _p(p), sp(NULL) {}
};

bool Generation::is_in(const void* p) const {
  GenerationIsInClosure blk(p);
  ((Generation*)this)->space_iterate(&blk);
  return blk.sp != NULL;
}

DefNewGeneration* Generation::as_DefNewGeneration() {
  assert((kind() == Generation::DefNew) ||
         (kind() == Generation::ParNew) ||
         (kind() == Generation::ASParNew),
    "Wrong youngest generation type");
  return (DefNewGeneration*) this;
}

Generation* Generation::next_gen() const {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  int next = level() + 1;
  if (next < gch->_n_gens) {
    return gch->_gens[next];
  } else {
    return NULL;
  }
}

size_t Generation::max_contiguous_available() const {
  // The largest number of contiguous free words in this or any higher generation.
  size_t max = 0;
  for (const Generation* gen = this; gen != NULL; gen = gen->next_gen()) {
    size_t avail = gen->contiguous_available();
    if (avail > max) {
      max = avail;
    }
  }
  return max;
}

bool Generation::promotion_attempt_is_safe(size_t max_promotion_in_bytes) const {
  size_t available = max_contiguous_available();
  bool   res = (available >= max_promotion_in_bytes);
  if (PrintGC && Verbose) {
    gclog_or_tty->print_cr(
      "Generation: promo attempt is%s safe: available("SIZE_FORMAT") %s max_promo("SIZE_FORMAT")",
      res? "":" not", available, res? ">=":"<",
      max_promotion_in_bytes);
  }
  return res;
}

// Ignores "ref" and calls allocate().
oop Generation::promote(oop obj, size_t obj_size) {
  assert(obj_size == (size_t)obj->size(), "bad obj_size passed in");

#ifndef PRODUCT
  if (Universe::heap()->promotion_should_fail()) {
    return NULL;
  }
#endif  // #ifndef PRODUCT

  HeapWord* result = allocate(obj_size, false);
  if (result != NULL) {
    Copy::aligned_disjoint_words((HeapWord*)obj, result, obj_size);
    return oop(result);
  } else {
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    return gch->handle_failed_promotion(this, obj, obj_size);
  }
}

oop Generation::par_promote(int thread_num,
                            oop obj, markOop m, size_t word_sz) {
  // Could do a bad general impl here that gets a lock.  But no.
  ShouldNotCallThis();
  return NULL;
}

void Generation::par_promote_alloc_undo(int thread_num,
                                        HeapWord* obj, size_t word_sz) {
  // Could do a bad general impl here that gets a lock.  But no.
  guarantee(false, "No good general implementation.");
}

Space* Generation::space_containing(const void* p) const {
  GenerationIsInReservedClosure blk(p);
  // Cast away const
  ((Generation*)this)->space_iterate(&blk);
  return blk.sp;
}

// Some of these are mediocre general implementations.  Should be
// overridden to get better performance.

class GenerationBlockStartClosure : public SpaceClosure {
 public:
  const void* _p;
  HeapWord* _start;
  virtual void do_space(Space* s) {
    if (_start == NULL && s->is_in_reserved(_p)) {
      _start = s->block_start(_p);
    }
  }
  GenerationBlockStartClosure(const void* p) { _p = p; _start = NULL; }
};

HeapWord* Generation::block_start(const void* p) const {
  GenerationBlockStartClosure blk(p);
  // Cast away const
  ((Generation*)this)->space_iterate(&blk);
  return blk._start;
}

class GenerationBlockSizeClosure : public SpaceClosure {
 public:
  const HeapWord* _p;
  size_t size;
  virtual void do_space(Space* s) {
    if (size == 0 && s->is_in_reserved(_p)) {
      size = s->block_size(_p);
    }
  }
  GenerationBlockSizeClosure(const HeapWord* p) { _p = p; size = 0; }
};

size_t Generation::block_size(const HeapWord* p) const {
  GenerationBlockSizeClosure blk(p);
  // Cast away const
  ((Generation*)this)->space_iterate(&blk);
  assert(blk.size > 0, "seems reasonable");
  return blk.size;
}

class GenerationBlockIsObjClosure : public SpaceClosure {
 public:
  const HeapWord* _p;
  bool is_obj;
  virtual void do_space(Space* s) {
    if (!is_obj && s->is_in_reserved(_p)) {
      is_obj |= s->block_is_obj(_p);
    }
  }
  GenerationBlockIsObjClosure(const HeapWord* p) { _p = p; is_obj = false; }
};

bool Generation::block_is_obj(const HeapWord* p) const {
  GenerationBlockIsObjClosure blk(p);
  // Cast away const
  ((Generation*)this)->space_iterate(&blk);
  return blk.is_obj;
}

class GenerationOopIterateClosure : public SpaceClosure {
 public:
  ExtendedOopClosure* cl;
  MemRegion mr;
  virtual void do_space(Space* s) {
    s->oop_iterate(mr, cl);
  }
  GenerationOopIterateClosure(ExtendedOopClosure* _cl, MemRegion _mr) :
    cl(_cl), mr(_mr) {}
};

void Generation::oop_iterate(ExtendedOopClosure* cl) {
  GenerationOopIterateClosure blk(cl, _reserved);
  space_iterate(&blk);
}

void Generation::oop_iterate(MemRegion mr, ExtendedOopClosure* cl) {
  GenerationOopIterateClosure blk(cl, mr);
  space_iterate(&blk);
}

void Generation::younger_refs_in_space_iterate(Space* sp,
                                               OopsInGenClosure* cl) {
  GenRemSet* rs = SharedHeap::heap()->rem_set();
  rs->younger_refs_in_space_iterate(sp, cl);
}

class GenerationObjIterateClosure : public SpaceClosure {
 private:
  ObjectClosure* _cl;
 public:
  virtual void do_space(Space* s) {
    s->object_iterate(_cl);
  }
  GenerationObjIterateClosure(ObjectClosure* cl) : _cl(cl) {}
};

void Generation::object_iterate(ObjectClosure* cl) {
  GenerationObjIterateClosure blk(cl);
  space_iterate(&blk);
}

class GenerationSafeObjIterateClosure : public SpaceClosure {
 private:
  ObjectClosure* _cl;
 public:
  virtual void do_space(Space* s) {
    s->safe_object_iterate(_cl);
  }
  GenerationSafeObjIterateClosure(ObjectClosure* cl) : _cl(cl) {}
};

void Generation::safe_object_iterate(ObjectClosure* cl) {
  GenerationSafeObjIterateClosure blk(cl);
  space_iterate(&blk);
}

void Generation::prepare_for_compaction(CompactPoint* cp) {
  // Generic implementation, can be specialized
  CompactibleSpace* space = first_compaction_space();
  while (space != NULL) {
    space->prepare_for_compaction(cp);
    space = space->next_compaction_space();
  }
}

class AdjustPointersClosure: public SpaceClosure {
 public:
  void do_space(Space* sp) {
    sp->adjust_pointers();
  }
};

void Generation::adjust_pointers() {
  // Note that this is done over all spaces, not just the compactible
  // ones.
  AdjustPointersClosure blk;
  space_iterate(&blk, true);
}

void Generation::compact() {
  CompactibleSpace* sp = first_compaction_space();
  while (sp != NULL) {
    sp->compact();
    sp = sp->next_compaction_space();
  }
}

CardGeneration::CardGeneration(ReservedSpace rs, size_t initial_byte_size,
                               int level,
                               GenRemSet* remset) :
  Generation(rs, initial_byte_size, level), _rs(remset),
  _shrink_factor(0), _min_heap_delta_bytes(), _capacity_at_prologue(),
  _used_at_prologue()
{
  HeapWord* start = (HeapWord*)rs.base();
  size_t reserved_byte_size = rs.size();
  assert((uintptr_t(start) & 3) == 0, "bad alignment");
  assert((reserved_byte_size & 3) == 0, "bad alignment");
  MemRegion reserved_mr(start, heap_word_size(reserved_byte_size));
  _bts = new BlockOffsetSharedArray(reserved_mr,
                                    heap_word_size(initial_byte_size));
  MemRegion committed_mr(start, heap_word_size(initial_byte_size));
  _rs->resize_covered_region(committed_mr);
  if (_bts == NULL)
    vm_exit_during_initialization("Could not allocate a BlockOffsetArray");

  // Verify that the start and end of this generation is the start of a card.
  // If this wasn't true, a single card could span more than on generation,
  // which would cause problems when we commit/uncommit memory, and when we
  // clear and dirty cards.
  guarantee(_rs->is_aligned(reserved_mr.start()), "generation must be card aligned");
  if (reserved_mr.end() != Universe::heap()->reserved_region().end()) {
    // Don't check at the very end of the heap as we'll assert that we're probing off
    // the end if we try.
    guarantee(_rs->is_aligned(reserved_mr.end()), "generation must be card aligned");
  }
  _min_heap_delta_bytes = MinHeapDeltaBytes;
  _capacity_at_prologue = initial_byte_size;
  _used_at_prologue = 0;
}

bool CardGeneration::expand(size_t bytes, size_t expand_bytes) {
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
  if (PrintGC && Verbose) {
    if (success && GC_locker::is_active_and_needs_gc()) {
      gclog_or_tty->print_cr("Garbage collection disabled, expanded heap instead");
    }
  }

  return success;
}


// No young generation references, clear this generation's cards.
void CardGeneration::clear_remembered_set() {
  _rs->clear(reserved());
}


// Objects in this generation may have moved, invalidate this
// generation's cards.
void CardGeneration::invalidate_remembered_set() {
  _rs->invalidate(used_region());
}


void CardGeneration::compute_new_size() {
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
}

// Currently nothing to do.
void CardGeneration::prepare_for_verify() {}


void OneContigSpaceCardGeneration::collect(bool   full,
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
  gc_timer->register_gc_start(os::elapsed_counter());

  SerialOldTracer* gc_tracer = GenMarkSweep::gc_tracer();
  gc_tracer->report_gc_start(gch->gc_cause(), gc_timer->gc_start());

  GenMarkSweep::invoke_at_safepoint(_level, ref_processor(), clear_all_soft_refs);

  gc_timer->register_gc_end(os::elapsed_counter());

  gc_tracer->report_gc_end(os::elapsed_counter(), gc_timer->time_partitions());

  SpecializationStats::print();
}

HeapWord*
OneContigSpaceCardGeneration::expand_and_allocate(size_t word_size,
                                                  bool is_tlab,
                                                  bool parallel) {
  assert(!is_tlab, "OneContigSpaceCardGeneration does not support TLAB allocation");
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

bool OneContigSpaceCardGeneration::expand(size_t bytes, size_t expand_bytes) {
  GCMutexLocker x(ExpandHeap_lock);
  return CardGeneration::expand(bytes, expand_bytes);
}


void OneContigSpaceCardGeneration::shrink(size_t bytes) {
  assert_locked_or_safepoint(ExpandHeap_lock);
  size_t size = ReservedSpace::page_align_size_down(bytes);
  if (size > 0) {
    shrink_by(size);
  }
}


size_t OneContigSpaceCardGeneration::capacity() const {
  return _the_space->capacity();
}


size_t OneContigSpaceCardGeneration::used() const {
  return _the_space->used();
}


size_t OneContigSpaceCardGeneration::free() const {
  return _the_space->free();
}

MemRegion OneContigSpaceCardGeneration::used_region() const {
  return the_space()->used_region();
}

size_t OneContigSpaceCardGeneration::unsafe_max_alloc_nogc() const {
  return _the_space->free();
}

size_t OneContigSpaceCardGeneration::contiguous_available() const {
  return _the_space->free() + _virtual_space.uncommitted_size();
}

bool OneContigSpaceCardGeneration::grow_by(size_t bytes) {
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


bool OneContigSpaceCardGeneration::grow_to_reserved() {
  assert_locked_or_safepoint(ExpandHeap_lock);
  bool success = true;
  const size_t remaining_bytes = _virtual_space.uncommitted_size();
  if (remaining_bytes > 0) {
    success = grow_by(remaining_bytes);
    DEBUG_ONLY(if (!success) warning("grow to reserved failed");)
  }
  return success;
}

void OneContigSpaceCardGeneration::shrink_by(size_t bytes) {
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
void OneContigSpaceCardGeneration::prepare_for_verify() {}


// Override for a card-table generation with one contiguous
// space. NOTE: For reasons that are lost in the fog of history,
// this code is used when you iterate over perm gen objects,
// even when one uses CDS, where the perm gen has a couple of
// other spaces; this is because CompactingPermGenGen derives
// from OneContigSpaceCardGeneration. This should be cleaned up,
// see CR 6897789..
void OneContigSpaceCardGeneration::object_iterate(ObjectClosure* blk) {
  _the_space->object_iterate(blk);
}

void OneContigSpaceCardGeneration::space_iterate(SpaceClosure* blk,
                                                 bool usedOnly) {
  blk->do_space(_the_space);
}

void OneContigSpaceCardGeneration::object_iterate_since_last_GC(ObjectClosure* blk) {
  // Deal with delayed initialization of _the_space,
  // and lack of initialization of _last_gc.
  if (_last_gc.space() == NULL) {
    assert(the_space() != NULL, "shouldn't be NULL");
    _last_gc = the_space()->bottom_mark();
  }
  the_space()->object_iterate_from(_last_gc, blk);
}

void OneContigSpaceCardGeneration::younger_refs_iterate(OopsInGenClosure* blk) {
  blk->set_generation(this);
  younger_refs_in_space_iterate(_the_space, blk);
  blk->reset_generation();
}

void OneContigSpaceCardGeneration::save_marks() {
  _the_space->set_saved_mark();
}


void OneContigSpaceCardGeneration::reset_saved_marks() {
  _the_space->reset_saved_mark();
}


bool OneContigSpaceCardGeneration::no_allocs_since_save_marks() {
  return _the_space->saved_mark_at_top();
}

#define OneContig_SINCE_SAVE_MARKS_ITERATE_DEFN(OopClosureType, nv_suffix)      \
                                                                                \
void OneContigSpaceCardGeneration::                                             \
oop_since_save_marks_iterate##nv_suffix(OopClosureType* blk) {                  \
  blk->set_generation(this);                                                    \
  _the_space->oop_since_save_marks_iterate##nv_suffix(blk);                     \
  blk->reset_generation();                                                      \
  save_marks();                                                                 \
}

ALL_SINCE_SAVE_MARKS_CLOSURES(OneContig_SINCE_SAVE_MARKS_ITERATE_DEFN)

#undef OneContig_SINCE_SAVE_MARKS_ITERATE_DEFN


void OneContigSpaceCardGeneration::gc_epilogue(bool full) {
  _last_gc = WaterMark(the_space(), the_space()->top());

  // update the generation and space performance counters
  update_counters();
  if (ZapUnusedHeapArea) {
    the_space()->check_mangled_unused_area_complete();
  }
}

void OneContigSpaceCardGeneration::record_spaces_top() {
  assert(ZapUnusedHeapArea, "Not mangling unused space");
  the_space()->set_top_for_allocations();
}

void OneContigSpaceCardGeneration::verify() {
  the_space()->verify();
}

void OneContigSpaceCardGeneration::print_on(outputStream* st)  const {
  Generation::print_on(st);
  st->print("   the");
  the_space()->print_on(st);
}
