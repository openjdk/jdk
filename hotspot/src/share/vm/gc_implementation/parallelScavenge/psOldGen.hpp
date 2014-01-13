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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSOLDGEN_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSOLDGEN_HPP

#include "gc_implementation/parallelScavenge/objectStartArray.hpp"
#include "gc_implementation/parallelScavenge/psGenerationCounters.hpp"
#include "gc_implementation/parallelScavenge/psVirtualspace.hpp"
#include "gc_implementation/shared/mutableSpace.hpp"
#include "gc_implementation/shared/spaceCounters.hpp"
#include "runtime/safepoint.hpp"

class PSMarkSweepDecorator;

class PSOldGen : public CHeapObj<mtGC> {
  friend class VMStructs;
  friend class PSPromotionManager; // Uses the cas_allocate methods
  friend class ParallelScavengeHeap;
  friend class AdjoiningGenerations;

 protected:
  MemRegion                _reserved;          // Used for simple containment tests
  PSVirtualSpace*          _virtual_space;     // Controls mapping and unmapping of virtual mem
  ObjectStartArray         _start_array;       // Keeps track of where objects start in a 512b block
  MutableSpace*            _object_space;      // Where all the objects live
  PSMarkSweepDecorator*    _object_mark_sweep; // The mark sweep view of _object_space
  const char* const        _name;              // Name of this generation.

  // Performance Counters
  PSGenerationCounters*    _gen_counters;
  SpaceCounters*           _space_counters;

  // Sizing information, in bytes, set in constructor
  const size_t _init_gen_size;
  const size_t _min_gen_size;
  const size_t _max_gen_size;

  // Used when initializing the _name field.
  static inline const char* select_name();

  HeapWord* allocate_noexpand(size_t word_size) {
    // We assume the heap lock is held here.
    assert_locked_or_safepoint(Heap_lock);
    HeapWord* res = object_space()->allocate(word_size);
    if (res != NULL) {
      _start_array.allocate_block(res);
    }
    return res;
  }

  // Support for MT garbage collection. CAS allocation is lower overhead than grabbing
  // and releasing the heap lock, which is held during gc's anyway. This method is not
  // safe for use at the same time as allocate_noexpand()!
  HeapWord* cas_allocate_noexpand(size_t word_size) {
    assert(SafepointSynchronize::is_at_safepoint(), "Must only be called at safepoint");
    HeapWord* res = object_space()->cas_allocate(word_size);
    if (res != NULL) {
      _start_array.allocate_block(res);
    }
    return res;
  }

  // Support for MT garbage collection. See above comment.
  HeapWord* cas_allocate(size_t word_size) {
    HeapWord* res = cas_allocate_noexpand(word_size);
    return (res == NULL) ? expand_and_cas_allocate(word_size) : res;
  }

  HeapWord* expand_and_allocate(size_t word_size);
  HeapWord* expand_and_cas_allocate(size_t word_size);
  void expand(size_t bytes);
  bool expand_by(size_t bytes);
  bool expand_to_reserved();

  void shrink(size_t bytes);

  void post_resize();

 public:
  // Initialize the generation.
  PSOldGen(ReservedSpace rs, size_t alignment,
           size_t initial_size, size_t min_size, size_t max_size,
           const char* perf_data_name, int level);

  PSOldGen(size_t initial_size, size_t min_size, size_t max_size,
           const char* perf_data_name, int level);

  virtual void initialize(ReservedSpace rs, size_t alignment,
                  const char* perf_data_name, int level);
  void initialize_virtual_space(ReservedSpace rs, size_t alignment);
  virtual void initialize_work(const char* perf_data_name, int level);
  virtual void initialize_performance_counters(const char* perf_data_name, int level);

  MemRegion reserved() const                { return _reserved; }
  virtual size_t max_gen_size()             { return _max_gen_size; }
  size_t min_gen_size()                     { return _min_gen_size; }

  // Returns limit on the maximum size of the generation.  This
  // is the same as _max_gen_size for PSOldGen but need not be
  // for a derived class.
  virtual size_t gen_size_limit();

  bool is_in(const void* p) const           {
    return _virtual_space->contains((void *)p);
  }

  bool is_in_reserved(const void* p) const {
    return reserved().contains(p);
  }

  MutableSpace*         object_space() const      { return _object_space; }
  PSMarkSweepDecorator* object_mark_sweep() const { return _object_mark_sweep; }
  ObjectStartArray*     start_array()             { return &_start_array; }
  PSVirtualSpace*       virtual_space() const     { return _virtual_space;}

  // Has the generation been successfully allocated?
  bool is_allocated();

  // MarkSweep methods
  virtual void precompact();
  void adjust_pointers();
  void compact();

  // Size info
  size_t capacity_in_bytes() const        { return object_space()->capacity_in_bytes(); }
  size_t used_in_bytes() const            { return object_space()->used_in_bytes(); }
  size_t free_in_bytes() const            { return object_space()->free_in_bytes(); }

  size_t capacity_in_words() const        { return object_space()->capacity_in_words(); }
  size_t used_in_words() const            { return object_space()->used_in_words(); }
  size_t free_in_words() const            { return object_space()->free_in_words(); }

  // Includes uncommitted memory
  size_t contiguous_available() const;

  bool is_maximal_no_gc() const {
    return virtual_space()->uncommitted_size() == 0;
  }

  // Calculating new sizes
  void resize(size_t desired_free_space);

  // Allocation. We report all successful allocations to the size policy
  // Note that the perm gen does not use this method, and should not!
  HeapWord* allocate(size_t word_size);

  // Iteration.
  void oop_iterate_no_header(OopClosure* cl) { object_space()->oop_iterate_no_header(cl); }
  void object_iterate(ObjectClosure* cl) { object_space()->object_iterate(cl); }

  // Debugging - do not use for time critical operations
  virtual void print() const;
  virtual void print_on(outputStream* st) const;
  void print_used_change(size_t prev_used) const;

  void verify();
  void verify_object_start_array();

  // These should not used
  virtual void reset_after_change();

  // These should not used
  virtual size_t available_for_expansion();
  virtual size_t available_for_contraction();

  void space_invariants() PRODUCT_RETURN;

  // Performace Counter support
  void update_counters();

  // Printing support
  virtual const char* name() const { return _name; }

  // Debugging support
  // Save the tops of all spaces for later use during mangling.
  void record_spaces_top() PRODUCT_RETURN;
};

#endif // SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSOLDGEN_HPP
