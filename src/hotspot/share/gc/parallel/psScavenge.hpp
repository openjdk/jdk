/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_PSSCAVENGE_HPP
#define SHARE_GC_PARALLEL_PSSCAVENGE_HPP

#include "gc/parallel/psCardTable.hpp"
#include "gc/parallel/psVirtualspace.hpp"
#include "gc/shared/collectorCounters.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "memory/allStatic.hpp"
#include "oops/oop.hpp"
#include "utilities/stack.hpp"

class ParallelScavengeHeap;
class PSIsAliveClosure;
class STWGCTimer;

class PSScavenge: AllStatic {
  friend class PSIsAliveClosure;
  friend class PSKeepAliveClosure;
  friend class PSPromotionManager;

 protected:
  // Flags/counters
  static SpanSubjectToDiscoveryClosure _span_based_discoverer;
  static ReferenceProcessor*           _ref_processor;        // Reference processor for scavenging.
  static PSIsAliveClosure              _is_alive_closure;     // Closure used for reference processing
  static PSCardTable*                  _card_table;           // We cache the card table for fast access.
  static bool                          _survivor_overflow;    // Overflow this collection
  static uint                          _tenuring_threshold;   // tenuring threshold for next scavenge
  static elapsedTimer                  _accumulated_time;     // total time spent on scavenge
  static STWGCTimer                    _gc_timer;             // GC time book keeper
  static ParallelScavengeTracer        _gc_tracer;          // GC tracing
  // The lowest address possible for the young_gen.
  // This is used to decide if an oop should be scavenged,
  // cards should be marked, etc.
  static HeapWord*            _young_generation_boundary;
  // Used to optimize compressed oops young gen boundary checking.
  static uintptr_t            _young_generation_boundary_compressed;
  static CollectorCounters*   _counters;             // collector performance counters

  static void clean_up_failed_promotion();

  static bool should_attempt_scavenge();

  // Private accessors
  static PSCardTable* card_table()                 { assert(_card_table != nullptr, "Sanity"); return _card_table; }
  static const ParallelScavengeTracer* gc_tracer() { return &_gc_tracer; }

 public:
  // Accessors
  static uint             tenuring_threshold()  { return _tenuring_threshold; }
  static elapsedTimer*    accumulated_time()    { return &_accumulated_time; }

  // Performance Counters
  static CollectorCounters* counters()           { return _counters; }

  static void set_subject_to_discovery_span(MemRegion mr) {
    _span_based_discoverer.set_span(mr);
  }
  // Used by scavenge_contents
  static ReferenceProcessor* reference_processor() {
    assert(_ref_processor != nullptr, "Sanity");
    return _ref_processor;
  }
  // The promotion managers tell us if they encountered overflow
  static void set_survivor_overflow(bool state) {
    _survivor_overflow = state;
  }
  // Adaptive size policy support.
  static void set_young_generation_boundary(HeapWord* v);

  // Called by parallelScavengeHeap to init the tenuring threshold
  static void initialize();

  // Scavenge entry point.
  // Return true iff a young-gc is completed without promotion-failure.
  static bool invoke(bool clear_soft_refs);

  template <class T> static inline bool should_scavenge(T* p);

  // These call should_scavenge() above and, if it returns true, also check that
  // the object was not newly copied into to_space.  The version with the bool
  // argument is a convenience wrapper that fetches the to_space pointer from
  // the heap and calls the other version (if the arg is true).
  template <class T> static inline bool should_scavenge(T* p, MutableSpace* to_space);
  template <class T> static inline bool should_scavenge(T* p, bool check_to_space);

  // Is an object in the young generation
  // This assumes that the 'o' is in the heap,
  // so it only checks one side of the complete predicate.

  inline static bool is_obj_in_young(oop o) {
    return cast_from_oop<HeapWord*>(o) >= _young_generation_boundary;
  }

  inline static bool is_obj_in_young(narrowOop o) {
    return (uintptr_t)o >= _young_generation_boundary_compressed;
  }

  inline static bool is_obj_in_young(HeapWord* o) {
    return o >= _young_generation_boundary;
  }

  static bool is_obj_in_to_space(oop o) {
    return ParallelScavengeHeap::young_gen()->to_space()->contains(o);
  }
};

#endif // SHARE_GC_PARALLEL_PSSCAVENGE_HPP
