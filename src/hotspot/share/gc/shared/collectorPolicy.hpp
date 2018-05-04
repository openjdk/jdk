/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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
class ConcurrentMarkSweepPolicy;
class G1CollectorPolicy;
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

  CollectorPolicy();

 public:
  void initialize_all() {
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

  void initialize_flags();
  void initialize_size_info();

  DEBUG_ONLY(void assert_flags();)
  DEBUG_ONLY(void assert_size_info();)

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

  size_t young_gen_size_lower_bound();

  size_t old_gen_size_lower_bound();
};

class MarkSweepPolicy : public GenCollectorPolicy {
 protected:
  void initialize_alignments();

 public:
  MarkSweepPolicy() {}
};

#endif // SHARE_VM_GC_SHARED_COLLECTORPOLICY_HPP
