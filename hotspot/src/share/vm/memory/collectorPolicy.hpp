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

#ifndef SHARE_VM_MEMORY_COLLECTORPOLICY_HPP
#define SHARE_VM_MEMORY_COLLECTORPOLICY_HPP

#include "memory/allocation.hpp"
#include "memory/barrierSet.hpp"
#include "memory/generationSpec.hpp"
#include "memory/genRemSet.hpp"
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
class TwoGenerationCollectorPolicy;
class AdaptiveSizePolicy;
#if INCLUDE_ALL_GCS
class ConcurrentMarkSweepPolicy;
class G1CollectorPolicy;
#endif // INCLUDE_ALL_GCS

class GCPolicyCounters;
class MarkSweepPolicy;

class CollectorPolicy : public CHeapObj<mtGC> {
 protected:
  GCPolicyCounters* _gc_policy_counters;

  // Requires that the concrete subclass sets the alignment constraints
  // before calling.
  virtual void initialize_flags();
  virtual void initialize_size_info();

  size_t _initial_heap_byte_size;
  size_t _max_heap_byte_size;
  size_t _min_heap_byte_size;

  size_t _min_alignment;
  size_t _max_alignment;

  // The sizing of the heap are controlled by a sizing policy.
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

  CollectorPolicy() :
    _min_alignment(1),
    _max_alignment(1),
    _initial_heap_byte_size(0),
    _max_heap_byte_size(0),
    _min_heap_byte_size(0),
    _size_policy(NULL),
    _should_clear_all_soft_refs(false),
    _all_soft_refs_clear(false)
  {}

 public:
  // Return maximum heap alignment that may be imposed by the policy
  static size_t compute_max_alignment();

  size_t min_alignment()                       { return _min_alignment; }
  size_t max_alignment()                       { return _max_alignment; }

  size_t initial_heap_byte_size() { return _initial_heap_byte_size; }
  size_t max_heap_byte_size()     { return _max_heap_byte_size; }
  size_t min_heap_byte_size()     { return _min_heap_byte_size; }

  enum Name {
    CollectorPolicyKind,
    TwoGenerationCollectorPolicyKind,
    ConcurrentMarkSweepPolicyKind,
    ASConcurrentMarkSweepPolicyKind,
    G1CollectorPolicyKind
  };

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
  virtual TwoGenerationCollectorPolicy* as_two_generation_policy()        { return NULL; }
  virtual MarkSweepPolicy*              as_mark_sweep_policy()            { return NULL; }
#if INCLUDE_ALL_GCS
  virtual ConcurrentMarkSweepPolicy*    as_concurrent_mark_sweep_policy() { return NULL; }
  virtual G1CollectorPolicy*            as_g1_policy()                    { return NULL; }
#endif // INCLUDE_ALL_GCS
  // Note that these are not virtual.
  bool is_generation_policy()            { return as_generation_policy() != NULL; }
  bool is_two_generation_policy()        { return as_two_generation_policy() != NULL; }
  bool is_mark_sweep_policy()            { return as_mark_sweep_policy() != NULL; }
#if INCLUDE_ALL_GCS
  bool is_concurrent_mark_sweep_policy() { return as_concurrent_mark_sweep_policy() != NULL; }
  bool is_g1_policy()                    { return as_g1_policy() != NULL; }
#else  // INCLUDE_ALL_GCS
  bool is_concurrent_mark_sweep_policy() { return false; }
  bool is_g1_policy()                    { return false; }
#endif // INCLUDE_ALL_GCS


  virtual BarrierSet::Name barrier_set_name() = 0;
  virtual GenRemSet::Name  rem_set_name() = 0;

  // Create the remembered set (to cover the given reserved region,
  // allowing breaking up into at most "max_covered_regions").
  virtual GenRemSet* create_rem_set(MemRegion reserved,
                                    int max_covered_regions);

  // This method controls how a collector satisfies a request
  // for a block of memory.  "gc_time_limit_was_exceeded" will
  // be set to true if the adaptive size policy determine that
  // an excessive amount of time is being spent doing collections
  // and caused a NULL to be returned.  If a NULL is not returned,
  // "gc_time_limit_was_exceeded" has an undefined meaning.
  virtual HeapWord* mem_allocate_work(size_t size,
                                      bool is_tlab,
                                      bool* gc_overhead_limit_was_exceeded) = 0;

  // This method controls how a collector handles one or more
  // of its generations being fully allocated.
  virtual HeapWord *satisfy_failed_allocation(size_t size, bool is_tlab) = 0;
  // This method controls how a collector handles a metadata allocation
  // failure.
  virtual MetaWord* satisfy_failed_metadata_allocation(ClassLoaderData* loader_data,
                                                       size_t size,
                                                       Metaspace::MetadataType mdtype);

  // Performace Counter support
  GCPolicyCounters* counters()     { return _gc_policy_counters; }

  // Create the jstat counters for the GC policy.  By default, policy's
  // don't have associated counters, and we complain if this is invoked.
  virtual void initialize_gc_policy_counters() {
    ShouldNotReachHere();
  }

  virtual CollectorPolicy::Name kind() {
    return CollectorPolicy::CollectorPolicyKind;
  }

  // Returns true if a collector has eden space with soft end.
  virtual bool has_soft_ended_eden() {
    return false;
  }

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
 protected:
  size_t _min_gen0_size;
  size_t _initial_gen0_size;
  size_t _max_gen0_size;

  GenerationSpec **_generations;

  // Return true if an allocation should be attempted in the older
  // generation if it fails in the younger generation.  Return
  // false, otherwise.
  virtual bool should_try_older_generation_allocation(size_t word_size) const;

  void initialize_flags();
  void initialize_size_info();

  // Try to allocate space by expanding the heap.
  virtual HeapWord* expand_heap_and_allocate(size_t size, bool is_tlab);

 // Scale the base_size by NewRation according to
 //     result = base_size / (NewRatio + 1)
 // and align by min_alignment()
 size_t scale_by_NewRatio_aligned(size_t base_size);

 // Bound the value by the given maximum minus the
 // min_alignment.
 size_t bound_minus_alignment(size_t desired_size, size_t maximum_size);

 public:
  // Accessors
  size_t min_gen0_size()     { return _min_gen0_size; }
  size_t initial_gen0_size() { return _initial_gen0_size; }
  size_t max_gen0_size()     { return _max_gen0_size; }

  virtual int number_of_generations() = 0;

  virtual GenerationSpec **generations()       {
    assert(_generations != NULL, "Sanity check");
    return _generations;
  }

  virtual GenCollectorPolicy* as_generation_policy() { return this; }

  virtual void initialize_generations() = 0;

  virtual void initialize_all() {
    initialize_flags();
    initialize_size_info();
    initialize_generations();
  }

  HeapWord* mem_allocate_work(size_t size,
                              bool is_tlab,
                              bool* gc_overhead_limit_was_exceeded);

  HeapWord *satisfy_failed_allocation(size_t size, bool is_tlab);

  // Adaptive size policy
  virtual void initialize_size_policy(size_t init_eden_size,
                                      size_t init_promo_size,
                                      size_t init_survivor_size);
};

// All of hotspot's current collectors are subtypes of this
// class. Currently, these collectors all use the same gen[0],
// but have different gen[1] types. If we add another subtype
// of CollectorPolicy, this class should be broken out into
// its own file.

class TwoGenerationCollectorPolicy : public GenCollectorPolicy {
 protected:
  size_t _min_gen1_size;
  size_t _initial_gen1_size;
  size_t _max_gen1_size;

  void initialize_flags();
  void initialize_size_info();
  void initialize_generations()                { ShouldNotReachHere(); }

 public:
  // Accessors
  size_t min_gen1_size()     { return _min_gen1_size; }
  size_t initial_gen1_size() { return _initial_gen1_size; }
  size_t max_gen1_size()     { return _max_gen1_size; }

  // Inherited methods
  TwoGenerationCollectorPolicy* as_two_generation_policy() { return this; }

  int number_of_generations()                  { return 2; }
  BarrierSet::Name barrier_set_name()          { return BarrierSet::CardTableModRef; }
  GenRemSet::Name rem_set_name()               { return GenRemSet::CardTable; }

  virtual CollectorPolicy::Name kind() {
    return CollectorPolicy::TwoGenerationCollectorPolicyKind;
  }

  // Returns true is gen0 sizes were adjusted
  bool adjust_gen0_sizes(size_t* gen0_size_ptr, size_t* gen1_size_ptr,
                         const size_t heap_size, const size_t min_gen1_size);
};

class MarkSweepPolicy : public TwoGenerationCollectorPolicy {
 protected:
  void initialize_generations();

 public:
  MarkSweepPolicy();

  MarkSweepPolicy* as_mark_sweep_policy() { return this; }

  void initialize_gc_policy_counters();
};

#endif // SHARE_VM_MEMORY_COLLECTORPOLICY_HPP
