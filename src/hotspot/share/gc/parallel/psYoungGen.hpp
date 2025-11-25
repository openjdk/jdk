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

#ifndef SHARE_GC_PARALLEL_PSYOUNGGEN_HPP
#define SHARE_GC_PARALLEL_PSYOUNGGEN_HPP

#include "gc/parallel/mutableSpace.hpp"
#include "gc/parallel/psHeapVirtualSpace.hpp"
#include "gc/shared/generationCounters.hpp"
#include "gc/shared/hSpaceCounters.hpp"

class ReservedSpace;

class PSYoungGen : public CHeapObj<mtGC> {
  friend class VMStructs;
  friend class ParallelScavengeHeap;

 public:
  // Young generation sizing state from the latest sizing pass. It records how
  // the desired eden/survivor sizes relate to the young-gen size bounds.
  // Consumers such as the tenuring-threshold heuristic can use this as sizing
  // feedback.
  enum class SizingState : int {
    // Desired young-gen size means "eden + 2 * survivor".
    // Its relation to max_gen_size is:
    // exactly equal.
    balanced = 0,
    // greater than.
    constrained,
    // less than.
    surplus
  };

 private:
  PSHeapVirtualSpace* _heap_vs;

  // Spaces
  MutableSpace* _eden_space;
  MutableSpace* _from_space;
  MutableSpace* _to_space;

  // Sizing information, in bytes, set in constructor
  const size_t _min_gen_size;
  const size_t _max_gen_size;

  // Current young-gen sizing state, updated after young-gen resizing.
  SizingState _sizing_state;

  void set_sizing_state(SizingState sizing_state) { _sizing_state = sizing_state; }

  // Performance counters
  GenerationCounters*   _gen_counters;
  HSpaceCounters*       _eden_counters;
  HSpaceCounters*       _from_counters;
  HSpaceCounters*       _to_counters;

  // Initialize the space boundaries
  void compute_initial_space_boundaries();

  // Space boundary helper
  void set_space_boundaries(size_t eden_size, size_t survivor_size);

  bool resize_generation(size_t desired_young_gen_size);
  void resize_spaces(size_t requested_eden_size,
                     size_t requested_survivor_size);

  void reinit_to_from_layout_inner(size_t requested_eden_size,
                                    size_t requested_survivor_size);

  // Try to expand eden to hold at least word_size.
  // Return true iff the expansion is successful.
  bool try_expand_to_hold(size_t word_size);

  // Adjust the spaces to be consistent with the virtual space.
  void post_resize();

  void initialize_work();

  void resize_inner(size_t desired_eden_size,
                    size_t desired_survivor_size);

 public:
  // Initialize the generation.
  PSYoungGen(PSHeapVirtualSpace* vs,
             size_t initial_byte_size,
             size_t min_gen_size,
             size_t max_gen_size);

  MemRegion reserved() const {
    return MemRegion((HeapWord*) _heap_vs->young_gen_low_addr(),
                     (HeapWord*) _heap_vs->young_gen_high_addr());
  }

  MemRegion committed() const {
    return MemRegion((HeapWord*) _heap_vs->young_gen_low_addr(),
                     (HeapWord*) _heap_vs->young_gen_committed_high_addr());
  }

  size_t reserved_size() const { return reserved().byte_size(); }
  size_t committed_size() const { return committed().byte_size(); }
  size_t uncommitted_size() const { return reserved_size() - committed_size(); }

  bool is_in(const void* p) const {
    return committed().contains(p);
  }

  bool is_in_reserved(const void* p) const {
    return reserved().contains(p);
  }

  void reinit_after_gen_boundary_change();

  MutableSpace*   eden_space() const    { return _eden_space; }
  MutableSpace*   from_space() const    { return _from_space; }
  MutableSpace*   to_space() const      { return _to_space; }

  // Called during/after GC
  void swap_spaces();

  bool is_from_to_layout() const {
    return from_space()->bottom() < to_space()->bottom();
  }

  void resize(size_t desired_eden_size, size_t desired_survivor_size);
  // Returns true if resizing was completed within the current young-gen reservation.
  // Returns false if desired sizes were prepared for a caller-managed gen-boundary left shift.
  bool try_resize(bool is_survivor_overflowing,
                  size_t old_gen_total_free_bytes,
                  size_t* out_eden_size,
                  size_t* out_survivor_size);
  void reinit_to_from_layout(size_t desired_eden_size, size_t desired_survivor_size);

  // Size info
  size_t capacity_in_bytes() const;
  size_t used_in_bytes() const;
  size_t free_in_bytes() const;

  size_t capacity_in_words() const;
  size_t used_in_words() const;
  size_t free_in_words() const;

  size_t min_gen_size() const { return _min_gen_size; }
  size_t max_gen_size() const { return _max_gen_size; }

  SizingState sizing_state() const { return _sizing_state; }

  // Allocation
  HeapWord* cas_allocate(size_t word_size) {
    HeapWord* result = eden_space()->cas_allocate(word_size);
    return result;
  }

  HeapWord* expand_and_allocate(size_t word_size);

  // Iteration.
  void object_iterate(ObjectClosure* cl);

  // Performance Counter support
  void update_counters();

  // Debugging - do not use for time critical operations
  void print() const;
  virtual void print_on(outputStream* st) const;
  const char* name() const { return "PSYoungGen"; }

  void verify();

  // Space boundary invariant checker
  void space_invariants() PRODUCT_RETURN;
};

#endif // SHARE_GC_PARALLEL_PSYOUNGGEN_HPP
