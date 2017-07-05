/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_COLLECTORPOLICY_HPP
#define SHARE_VM_GC_SHARED_COLLECTORPOLICY_HPP

#include "gc/shared/barrierSet.hpp"
#include "gc/shared/cardTableRS.hpp"
#include "gc/shared/generationSpec.hpp"
#include "memory/allocation.hpp"
#include "utilities/macros.hpp"

// This class (or more correctly, subtypes of this class)
// are used to define global garbage collector attributes.
// This includes initialization of generations and any other
// shared resources they may need.
//
// In general, all flag adjustment and validation should be
// done in initialize_flags(), which is called prior to
// initialize_size_info().
//
// This class is not fully developed yet. As more collector(s)
// are added, it is expected that we will come across further
// behavior that requires global attention. The correct place
// to deal with those issues is this class.

// Forward declarations.
class GenCollectorPolicy;
class AdaptiveSizePolicy;
#if INCLUDE_ALL_GCS
class ConcurrentMarkSweepPolicy;
class G1CollectorPolicy;
#endif // INCLUDE_ALL_GCS

class GCPolicyCounters;
class MarkSweepPolicy;

class CollectorPolicy : public CHeapObj<mtGC> {
 protected:
  virtual void initialize_alignments() = 0;
  virtual void initialize_flags();
  virtual void initialize_size_info();

  DEBUG_ONLY(virtual void assert_flags();)
  DEBUG_ONLY(virtual void assert_size_info();)

  size_t _initial_heap_byte_size;
  size_t _max_heap_byte_size;
  size_t _min_heap_byte_size;

  size_t _space_alignment;
  size_t _heap_alignment;

  // The sizing of the heap is controlled by a sizing policy.
  AdaptiveSizePolicy* _size_policy;

  // Set to true when policy wants soft refs cleared.
  // Reset to false by gc after it clears all soft refs.
  bool _should_clear_all_soft_refs;

  // Set to true by the GC if the just-completed gc cleared all
  // softrefs.  This is set to true whenever a gc clears all softrefs, and
  // set to false each time gc returns to the mutator.  For example, in the
  // ParallelScavengeHeap case the latter would be done toward the end of
  // mem_allocate() where it returns op.result()
  bool _all_soft_refs_clear;

  CollectorPolicy();

 public:
  virtual void initialize_all() {
    initialize_alignments();
    initialize_flags();
    initialize_size_info();
  }

  // Return maximum heap alignment that may be imposed by the policy.
  static size_t compute_heap_alignment();

  size_t space_alignment()        { return _space_alignment; }
  size_t heap_alignment()         { return _heap_alignment; }

  size_t initial_heap_byte_size() { return _initial_heap_byte_size; }
  size_t max_heap_byte_size()     { return _max_heap_byte_size; }
  size_t min_heap_byte_size()     { return _min_heap_byte_size; }

  AdaptiveSizePolicy* size_policy() { return _size_policy; }
  bool should_clear_all_soft_refs() { return _should_clear_all_soft_refs; }
  void set_should_clear_all_soft_refs(bool v) { _should_clear_all_soft_refs = v; }
  // Returns the current value of _should_clear_all_soft_refs.
  // _should_clear_all_soft_refs is set to false as a side effect.
  bool use_should_clear_all_soft_refs(bool v);
  bool all_soft_refs_clear() { return _all_soft_refs_clear; }
  void set_all_soft_refs_clear(bool v) { _all_soft_refs_clear = v; }

  // Called by the GC after Soft Refs have been cleared to indicate
  // that the request in _should_clear_all_soft_refs has been fulfilled.
  void cleared_all_soft_refs();

  // Identification methods.
  virtual GenCollectorPolicy*           as_generation_policy()            { return NULL; }
  virtual MarkSweepPolicy*              as_mark_sweep_policy()            { return NULL; }
#if INCLUDE_ALL_GCS
  virtual ConcurrentMarkSweepPolicy*    as_concurrent_mark_sweep_policy() { return NULL; }
#endif // INCLUDE_ALL_GCS
  // Note that these are not virtual.
  bool is_generation_policy()            { return as_generation_policy() != NULL; }
  bool is_mark_sweep_policy()            { return as_mark_sweep_policy() != NULL; }
#if INCLUDE_ALL_GCS
  bool is_concurrent_mark_sweep_policy() { return as_concurrent_mark_sweep_policy() != NULL; }
#else  // INCLUDE_ALL_GCS
  bool is_concurrent_mark_sweep_policy() { return false; }
#endif // INCLUDE_ALL_GCS


  virtual CardTableRS* create_rem_set(MemRegion reserved);

  MetaWord* satisfy_failed_metadata_allocation(ClassLoaderData* loader_data,
                                               size_t size,
                                               Metaspace::MetadataType mdtype);
};

class ClearedAllSoftRefs : public StackObj {
  bool _clear_all_soft_refs;
  CollectorPolicy* _collector_policy;
 public:
  ClearedAllSoftRefs(bool clear_all_soft_refs,
                     CollectorPolicy* collector_policy) :
    _clear_all_soft_refs(clear_all_soft_refs),
    _collector_policy(collector_policy) {}

  ~ClearedAllSoftRefs() {
    if (_clear_all_soft_refs) {
      _collector_policy->cleared_all_soft_refs();
    }
  }
};

class GenCollectorPolicy : public CollectorPolicy {
  friend class TestGenCollectorPolicy;
  friend class VMStructs;
 protected:
  size_t _min_young_size;
  size_t _initial_young_size;
  size_t _max_young_size;
  size_t _min_old_size;
  size_t _initial_old_size;
  size_t _max_old_size;

  // _gen_alignment and _space_alignment will have the same value most of the
  // time. When using large pages they can differ.
  size_t _gen_alignment;

  GenerationSpec* _young_gen_spec;
  GenerationSpec* _old_gen_spec;

  GCPolicyCounters* _gc_policy_counters;

  // Return true if an allocation should be attempted in the older generation
  // if it fails in the younger generation.  Return false, otherwise.
  virtual bool should_try_older_generation_allocation(size_t word_size) const;

  void initialize_flags();
  void initialize_size_info();

  DEBUG_ONLY(void assert_flags();)
  DEBUG_ONLY(void assert_size_info();)

  // Try to allocate space by expanding the heap.
  virtual HeapWord* expand_heap_and_allocate(size_t size, bool is_tlab);

  // Compute max heap alignment.
  size_t compute_max_alignment();

  // Scale the base_size by NewRatio according to
  //     result = base_size / (NewRatio + 1)
  // and align by min_alignment()
  size_t scale_by_NewRatio_aligned(size_t base_size);

  // Bound the value by the given maximum minus the min_alignment.
  size_t bound_minus_alignment(size_t desired_size, size_t maximum_size);

 public:
  GenCollectorPolicy();

  // Accessors
  size_t min_young_size()     { return _min_young_size; }
  size_t initial_young_size() { return _initial_young_size; }
  size_t max_young_size()     { return _max_young_size; }
  size_t gen_alignment()      { return _gen_alignment; }
  size_t min_old_size()       { return _min_old_size; }
  size_t initial_old_size()   { return _initial_old_size; }
  size_t max_old_size()       { return _max_old_size; }

  GenerationSpec* young_gen_spec() const {
    assert(_young_gen_spec != NULL, "_young_gen_spec should have been initialized");
    return _young_gen_spec;
  }

  GenerationSpec* old_gen_spec() const {
    assert(_old_gen_spec != NULL, "_old_gen_spec should have been initialized");
    return _old_gen_spec;
  }

  // Performance Counter support
  GCPolicyCounters* counters()     { return _gc_policy_counters; }

  // Create the jstat counters for the GC policy.
  virtual void initialize_gc_policy_counters() = 0;

  virtual GenCollectorPolicy* as_generation_policy() { return this; }

  virtual void initialize_generations() { };

  virtual void initialize_all() {
    CollectorPolicy::initialize_all();
    initialize_generations();
  }

  size_t young_gen_size_lower_bound();

  size_t old_gen_size_lower_bound();

  HeapWord* mem_allocate_work(size_t size,
                              bool is_tlab,
                              bool* gc_overhead_limit_was_exceeded);

  HeapWord *satisfy_failed_allocation(size_t size, bool is_tlab);

  // Adaptive size policy
  virtual void initialize_size_policy(size_t init_eden_size,
                                      size_t init_promo_size,
                                      size_t init_survivor_size);
};

class MarkSweepPolicy : public GenCollectorPolicy {
 protected:
  void initialize_alignments();
  void initialize_generations();

 public:
  MarkSweepPolicy() {}

  MarkSweepPolicy* as_mark_sweep_policy() { return this; }

  void initialize_gc_policy_counters();
};

#endif // SHARE_VM_GC_SHARED_COLLECTORPOLICY_HPP
