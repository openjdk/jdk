/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_PSOLDGEN_HPP
#define SHARE_GC_PARALLEL_PSOLDGEN_HPP

#include "gc/parallel/mutableSpace.hpp"
#include "gc/parallel/objectStartArray.hpp"
#include "gc/parallel/psGenerationCounters.hpp"
#include "gc/parallel/psVirtualspace.hpp"
#include "gc/parallel/spaceCounters.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"

class PSOldGen : public CHeapObj<mtGC> {
  friend class VMStructs;
 private:
  PSVirtualSpace*          _virtual_space;     // Controls mapping and unmapping of virtual mem
  ObjectStartArray         _start_array;       // Keeps track of where objects start in a 512b block
  MutableSpace*            _object_space;      // Where all the objects live

  // Performance Counters
  PSGenerationCounters*    _gen_counters;
  SpaceCounters*           _space_counters;

  // Sizing information, in bytes, set in constructor
  const size_t _min_gen_size;
  const size_t _max_gen_size;

  // Block size for parallel iteration
  static const size_t IterateBlockSize = 1024 * 1024;

  HeapWord* cas_allocate_noexpand(size_t word_size) {
    assert_locked_or_safepoint(Heap_lock);
    HeapWord* res = object_space()->cas_allocate(word_size);
    if (res != nullptr) {
      _start_array.update_for_block(res, res + word_size);
    }
    return res;
  }

  bool expand_for_allocate(size_t word_size);
  bool expand(size_t bytes);
  bool expand_by(size_t bytes);
  bool expand_to_reserved();

  void shrink(size_t bytes);

  void post_resize();

  void initialize(ReservedSpace rs, size_t initial_size, size_t alignment,
                  const char* perf_data_name, int level);
  void initialize_virtual_space(ReservedSpace rs, size_t initial_size, size_t alignment);
  void initialize_work(const char* perf_data_name, int level);
  void initialize_performance_counters(const char* perf_data_name, int level);

 public:
  // Initialize the generation.
  PSOldGen(ReservedSpace rs, size_t initial_size, size_t min_size,
           size_t max_size, const char* perf_data_name, int level);

  MemRegion reserved() const {
    return MemRegion((HeapWord*)(_virtual_space->low_boundary()),
                     (HeapWord*)(_virtual_space->high_boundary()));
  }

  MemRegion committed() const {
    return MemRegion((HeapWord*)(_virtual_space->low()),
                     (HeapWord*)(_virtual_space->high()));
  }

  size_t max_gen_size() const { return _max_gen_size; }
  size_t min_gen_size() const { return _min_gen_size; }

  bool is_in(const void* p) const           {
    return _virtual_space->is_in_committed((void *)p);
  }

  bool is_in_reserved(const void* p) const {
    return _virtual_space->is_in_reserved(p);
  }

  MutableSpace*         object_space() const      { return _object_space; }
  ObjectStartArray*     start_array()             { return &_start_array; }
  PSVirtualSpace*       virtual_space() const     { return _virtual_space;}

  // Size info
  size_t capacity_in_bytes() const        { return object_space()->capacity_in_bytes(); }
  size_t used_in_bytes() const            { return object_space()->used_in_bytes(); }
  size_t free_in_bytes() const            { return object_space()->free_in_bytes(); }

  size_t capacity_in_words() const        { return object_space()->capacity_in_words(); }
  size_t used_in_words() const            { return object_space()->used_in_words(); }
  size_t free_in_words() const            { return object_space()->free_in_words(); }

  bool is_maximal_no_gc() const {
    return virtual_space()->uncommitted_size() == 0;
  }

  void complete_loaded_archive_space(MemRegion archive_space);

  // Calculating new sizes
  void resize(size_t desired_free_space);

  HeapWord* allocate(size_t word_size) {
    HeapWord* res;
    do {
      res = cas_allocate_noexpand(word_size);
      // Retry failed allocation if expand succeeds.
    } while ((res == nullptr) && expand_for_allocate(word_size));
    return res;
  }

  // Iteration.
  void oop_iterate(OopIterateClosure* cl) { object_space()->oop_iterate(cl); }
  void object_iterate(ObjectClosure* cl) { object_space()->object_iterate(cl); }

  // Number of blocks to be iterated over in the used part of old gen.
  size_t num_iterable_blocks() const;
  // Iterate the objects starting in block block_index within [bottom, top) of the
  // old gen. The object just reaching into this block is not iterated over.
  // A block is an evenly sized non-overlapping part of the old gen of
  // IterateBlockSize bytes.
  void object_iterate_block(ObjectClosure* cl, size_t block_index);

  // Debugging - do not use for time critical operations
  void print() const;
  virtual void print_on(outputStream* st) const;

  void verify();

  // Performance Counter support
  void update_counters();

  // Printing support
  const char* name() const { return "ParOldGen"; }

  // Debugging support
  // Save the tops of all spaces for later use during mangling.
  void record_spaces_top() PRODUCT_RETURN;
};

#endif // SHARE_GC_PARALLEL_PSOLDGEN_HPP
