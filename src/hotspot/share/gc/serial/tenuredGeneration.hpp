/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SERIAL_TENUREDGENERATION_HPP
#define SHARE_GC_SERIAL_TENUREDGENERATION_HPP

#include "gc/serial/cSpaceCounters.hpp"
#include "gc/serial/generation.hpp"
#include "gc/serial/serialBlockOffsetTable.hpp"
#include "gc/shared/generationCounters.hpp"
#include "gc/shared/space.hpp"
#include "utilities/macros.hpp"

class CardTableRS;
class ContiguousSpace;

// TenuredGeneration models the heap containing old (promoted/tenured) objects
// contained in a single contiguous space. This generation is covered by a card
// table, and uses a card-size block-offset array to implement block_start.
// Garbage collection is performed using mark-compact.

class TenuredGeneration: public Generation {
  friend class VMStructs;
  // Abstractly, this is a subtype that gets access to protected fields.
  friend class VM_PopulateDumpSharedSpace;

  MemRegion _prev_used_region;

  // This is shared with other generations.
  CardTableRS* _rs;
  // This is local to this generation.
  SerialBlockOffsetTable* _bts;

  // Current shrinking effect: this damps shrinking when the heap gets empty.
  size_t _shrink_factor;

  size_t _min_heap_delta_bytes;   // Minimum amount to expand.

  // Some statistics from before gc started.
  // These are gathered in the gc_prologue (and should_collect)
  // to control growing/shrinking policy in spite of promotions.
  size_t _capacity_at_prologue;
  size_t _used_at_prologue;

  void assert_correct_size_change_locking();

  ContiguousSpace*    _the_space;       // Actual space holding objects

  GenerationCounters* _gen_counters;
  CSpaceCounters*     _space_counters;

  // Avg amount promoted; used for avoiding promotion undo
  // This class does not update deviations if the sample is zero.
  AdaptivePaddedNoZeroDevAverage*   _avg_promoted;

  // Attempt to expand the generation by "bytes".  Expand by at a
  // minimum "expand_bytes".  Return true if some amount (not
  // necessarily the full "bytes") was done.
  bool expand(size_t bytes, size_t expand_bytes);

  // Shrink generation with specified size
  void shrink(size_t bytes);

  void compute_new_size_inner();

public:
  void compute_new_size();

  ContiguousSpace* space() const { return _the_space; }

  // Grow generation with specified size (returns false if unable to grow)
  bool grow_by(size_t bytes);
  // Grow generation to reserved size.
  bool grow_to_reserved();

  size_t capacity() const;
  size_t used() const;
  size_t free() const;

  MemRegion used_region() const { return space()->used_region(); }
  MemRegion prev_used_region() const { return _prev_used_region; }
  void save_used_region()   { _prev_used_region = used_region(); }

  // Returns true if this generation cannot be expanded further
  // without a GC.
  bool is_maximal_no_gc() const {
    return _virtual_space.uncommitted_size() == 0;
  }

  HeapWord* block_start(const void* addr) const;

  void scan_old_to_young_refs(HeapWord* saved_top_in_old_gen);

  bool is_in(const void* p) const;

  TenuredGeneration(ReservedSpace rs,
                    size_t initial_byte_size,
                    size_t min_byte_size,
                    size_t max_byte_size,
                    CardTableRS* remset);

  // Printing
  const char* name() const { return "Tenured"; }

  // Iteration
  void object_iterate(ObjectClosure* blk);

  void complete_loaded_archive_space(MemRegion archive_space);
  inline void update_for_block(HeapWord* start, HeapWord* end);

  // Allocate and returns a block of the requested size, or returns "null".
  // Assumes the caller has done any necessary locking.
  inline HeapWord* allocate(size_t word_size);

  // Expand the old-gen then invoke allocate above.
  HeapWord* expand_and_allocate(size_t size);

  void gc_prologue();
  void gc_epilogue();

  bool should_allocate(size_t word_size, bool is_tlab) {
    bool result = false;
    size_t overflow_limit = (size_t)1 << (BitsPerSize_t - LogHeapWordSize);
    if (!is_tlab) {
      result = (word_size > 0) && (word_size < overflow_limit);
    }
    return result;
  }

  // Performance Counter support
  void update_counters();

  // Statistics

  void update_promote_stats();

  // Returns true if promotions of the specified amount are
  // likely to succeed without a promotion failure.
  // Promotion of the full amount is not guaranteed but
  // might be attempted in the worst case.
  bool promotion_attempt_is_safe(size_t max_promoted_in_bytes) const;

  // "obj" is the address of an object in young-gen.  Allocate space for "obj"
  // in the old-gen, returning the result (or null if the allocation failed).
  //
  // The "obj_size" argument is just obj->size(), passed along so the caller can
  // avoid repeating the virtual call to retrieve it.
  oop allocate_for_promotion(oop obj, size_t obj_size);

  virtual void verify();
  virtual void print_on(outputStream* st) const;
};

#endif // SHARE_GC_SERIAL_TENUREDGENERATION_HPP
