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

#include "precompiled.hpp"
#include "gc/g1/g1Analytics.hpp"
#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/g1/g1YoungGenSizer.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/gcPolicyCounters.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"

G1CollectorPolicy::G1CollectorPolicy() {

  // Set up the region size and associated fields. Given that the
  // policy is created before the heap, we have to set this up here,
  // so it's done as soon as possible.

  // It would have been natural to pass initial_heap_byte_size() and
  // max_heap_byte_size() to setup_heap_region_size() but those have
  // not been set up at this point since they should be aligned with
  // the region size. So, there is a circular dependency here. We base
  // the region size on the heap size, but the heap size should be
  // aligned with the region size. To get around this we use the
  // unaligned values for the heap.
  HeapRegion::setup_heap_region_size(InitialHeapSize, MaxHeapSize);
  HeapRegionRemSet::setup_remset_size();

  // Below, we might need to calculate the pause time target based on
  // the pause interval. When we do so we are going to give G1 maximum
  // flexibility and allow it to do pauses when it needs to. So, we'll
  // arrange that the pause interval to be pause time target + 1 to
  // ensure that a) the pause time target is maximized with respect to
  // the pause interval and b) we maintain the invariant that pause
  // time target < pause interval. If the user does not want this
  // maximum flexibility, they will have to set the pause interval
  // explicitly.

  // First make sure that, if either parameter is set, its value is
  // reasonable.
  guarantee(MaxGCPauseMillis >= 1, "Range checking for MaxGCPauseMillis should guarantee that value is >= 1");

  // Then, if the pause time target parameter was not set, set it to
  // the default value.
  if (FLAG_IS_DEFAULT(MaxGCPauseMillis)) {
    if (FLAG_IS_DEFAULT(GCPauseIntervalMillis)) {
      // The default pause time target in G1 is 200ms
      FLAG_SET_DEFAULT(MaxGCPauseMillis, 200);
    } else {
      // We do not allow the pause interval to be set without the
      // pause time target
      vm_exit_during_initialization("GCPauseIntervalMillis cannot be set "
                                    "without setting MaxGCPauseMillis");
    }
  }

  // Then, if the interval parameter was not set, set it according to
  // the pause time target (this will also deal with the case when the
  // pause time target is the default value).
  if (FLAG_IS_DEFAULT(GCPauseIntervalMillis)) {
    FLAG_SET_DEFAULT(GCPauseIntervalMillis, MaxGCPauseMillis + 1);
  }
  guarantee(GCPauseIntervalMillis >= 1, "Constraint for GCPauseIntervalMillis should guarantee that value is >= 1");
  guarantee(GCPauseIntervalMillis > MaxGCPauseMillis, "Constraint for GCPauseIntervalMillis should guarantee that GCPauseIntervalMillis > MaxGCPauseMillis");
}

void G1CollectorPolicy::initialize_alignments() {
  _space_alignment = HeapRegion::GrainBytes;
  size_t card_table_alignment = CardTableRS::ct_max_alignment_constraint();
  size_t page_size = UseLargePages ? os::large_page_size() : os::vm_page_size();
  _heap_alignment = MAX3(card_table_alignment, _space_alignment, page_size);
}

void G1CollectorPolicy::initialize_flags() {
  if (G1HeapRegionSize != HeapRegion::GrainBytes) {
    FLAG_SET_ERGO(size_t, G1HeapRegionSize, HeapRegion::GrainBytes);
  }

  guarantee(SurvivorRatio >= 1, "Range checking for SurvivorRatio should guarantee that value is >= 1");

  CollectorPolicy::initialize_flags();
}

// Create the jstat counters for the policy.
void G1CollectorPolicy::initialize_gc_policy_counters() {
  _gc_policy_counters = new GCPolicyCounters("GarbageFirst", 1, 3);
}
