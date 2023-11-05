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

#ifndef SHARE_GC_SERIAL_TENUREDGENERATION_HPP
#define SHARE_GC_SERIAL_TENUREDGENERATION_HPP

#include "gc/serial/cSpaceCounters.hpp"
#include "gc/serial/generation.hpp"
#include "gc/shared/gcStats.hpp"
#include "gc/shared/generationCounters.hpp"
#include "utilities/macros.hpp"

class SerialBlockOffsetSharedArray;
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

 protected:

  // This is shared with other generations.
  CardTableRS* _rs;
  // This is local to this generation.
  SerialBlockOffsetSharedArray* _bts;

  // Current shrinking effect: this damps shrinking when the heap gets empty.
  size_t _shrink_factor;

  size_t _min_heap_delta_bytes;   // Minimum amount to expand.

  // Some statistics from before gc started.
  // These are gathered in the gc_prologue (and should_collect)
  // to control growing/shrinking policy in spite of promotions.
  size_t _capacity_at_prologue;
  size_t _used_at_prologue;

  void assert_correct_size_change_locking();

  TenuredSpace*       _the_space;       // Actual space holding objects

  GenerationCounters* _gen_counters;
  CSpaceCounters*     _space_counters;

  // Accessing spaces
  TenuredSpace* space() const { return _the_space; }

  // Attempt to expand the generation by "bytes".  Expand by at a
  // minimum "expand_bytes".  Return true if some amount (not
  // necessarily the full "bytes") was done.
  bool expand(size_t bytes, size_t expand_bytes);

  // Shrink generation with specified size
  void shrink(size_t bytes);

  void compute_new_size_inner();
 public:
  virtual void compute_new_size();

  // Grow generation with specified size (returns false if unable to grow)
  bool grow_by(size_t bytes);
  // Grow generation to reserved size.
  bool grow_to_reserved();

  size_t capacity() const;
  size_t used() const;
  size_t free() const;
  MemRegion used_region() const;

  void space_iterate(SpaceClosure* blk, bool usedOnly = false);

  void younger_refs_iterate(OopIterateClosure* blk);

  bool is_in(const void* p) const;

  ContiguousSpace* first_compaction_space() const;

  TenuredGeneration(ReservedSpace rs,
                    size_t initial_byte_size,
                    size_t min_byte_size,
                    size_t max_byte_size,
                    CardTableRS* remset);

  Generation::Name kind() { return Generation::MarkSweepCompact; }

  // Printing
  const char* name() const { return "tenured generation"; }
  const char* short_name() const { return "Tenured"; }

  size_t unsafe_max_alloc_nogc() const;
  size_t contiguous_available() const;

  // Iteration
  void object_iterate(ObjectClosure* blk);

  void complete_loaded_archive_space(MemRegion archive_space);

  virtual inline HeapWord* allocate(size_t word_size, bool is_tlab);
  virtual inline HeapWord* par_allocate(size_t word_size, bool is_tlab);

  template <typename OopClosureType>
  void oop_since_save_marks_iterate(OopClosureType* cl);

  void save_marks();

  bool no_allocs_since_save_marks();

  inline bool block_is_obj(const HeapWord* addr) const;

  virtual void collect(bool full,
                       bool clear_all_soft_refs,
                       size_t size,
                       bool is_tlab);

  HeapWord* expand_and_allocate(size_t size, bool is_tlab);

  virtual void gc_prologue(bool full);
  virtual void gc_epilogue(bool full);

  bool should_collect(bool   full,
                      size_t word_size,
                      bool   is_tlab);

  // Performance Counter support
  void update_counters();

  virtual void record_spaces_top();

  // Statistics

  virtual void update_gc_stats(Generation* current_generation, bool full);

  virtual bool promotion_attempt_is_safe(size_t max_promoted_in_bytes) const;

  virtual void verify();
  virtual void print_on(outputStream* st) const;
};

#endif // SHARE_GC_SERIAL_TENUREDGENERATION_HPP
