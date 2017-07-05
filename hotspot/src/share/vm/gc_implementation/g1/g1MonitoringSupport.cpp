/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/g1MonitoringSupport.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"

G1MonitoringSupport::G1MonitoringSupport(G1CollectedHeap* g1h,
                                         VirtualSpace* g1_storage_addr) :
  _g1h(g1h),
  _incremental_collection_counters(NULL),
  _full_collection_counters(NULL),
  _non_young_collection_counters(NULL),
  _old_space_counters(NULL),
  _young_collection_counters(NULL),
  _eden_counters(NULL),
  _from_counters(NULL),
  _to_counters(NULL),
  _g1_storage_addr(g1_storage_addr)
{
  // Counters for GC collections
  //
  //  name "collector.0".  In a generational collector this would be the
  // young generation collection.
  _incremental_collection_counters =
    new CollectorCounters("G1 incremental collections", 0);
  //   name "collector.1".  In a generational collector this would be the
  // old generation collection.
  _full_collection_counters =
    new CollectorCounters("G1 stop-the-world full collections", 1);

  // timer sampling for all counters supporting sampling only update the
  // used value.  See the take_sample() method.  G1 requires both used and
  // capacity updated so sampling is not currently used.  It might
  // be sufficient to update all counters in take_sample() even though
  // take_sample() only returns "used".  When sampling was used, there
  // were some anomolous values emitted which may have been the consequence
  // of not updating all values simultaneously (i.e., see the calculation done
  // in eden_space_used(), is it possbile that the values used to
  // calculate either eden_used or survivor_used are being updated by
  // the collector when the sample is being done?).
  const bool sampled = false;

  // "Generation" and "Space" counters.
  //
  //  name "generation.1" This is logically the old generation in
  // generational GC terms.  The "1, 1" parameters are for
  // the n-th generation (=1) with 1 space.
  // Counters are created from minCapacity, maxCapacity, and capacity
  _non_young_collection_counters =
    new GenerationCounters("whole heap", 1, 1, _g1_storage_addr);

  //  name  "generation.1.space.0"
  // Counters are created from maxCapacity, capacity, initCapacity,
  // and used.
  _old_space_counters = new HSpaceCounters("space", 0,
    _g1h->max_capacity(), _g1h->capacity(), _non_young_collection_counters);

  //   Young collection set
  //  name "generation.0".  This is logically the young generation.
  //  The "0, 3" are paremeters for the n-th genertaion (=0) with 3 spaces.
  // See  _non_young_collection_counters for additional counters
  _young_collection_counters = new GenerationCounters("young", 0, 3, NULL);

  // Replace "max_heap_byte_size() with maximum young gen size for
  // g1Collectedheap
  //  name "generation.0.space.0"
  // See _old_space_counters for additional counters
  _eden_counters = new HSpaceCounters("eden", 0,
    _g1h->max_capacity(), eden_space_committed(),
    _young_collection_counters);

  //  name "generation.0.space.1"
  // See _old_space_counters for additional counters
  // Set the arguments to indicate that this survivor space is not used.
  _from_counters = new HSpaceCounters("s0", 1, (long) 0, (long) 0,
    _young_collection_counters);

  //  name "generation.0.space.2"
  // See _old_space_counters for additional counters
  _to_counters = new HSpaceCounters("s1", 2,
    _g1h->max_capacity(),
    survivor_space_committed(),
    _young_collection_counters);
}

size_t G1MonitoringSupport::overall_committed() {
  return g1h()->capacity();
}

size_t G1MonitoringSupport::overall_used() {
  return g1h()->used_unlocked();
}

size_t G1MonitoringSupport::eden_space_committed() {
  return MAX2(eden_space_used(), (size_t) HeapRegion::GrainBytes);
}

size_t G1MonitoringSupport::eden_space_used() {
  size_t young_list_length = g1h()->young_list()->length();
  size_t eden_used = young_list_length * HeapRegion::GrainBytes;
  size_t survivor_used = survivor_space_used();
  eden_used = subtract_up_to_zero(eden_used, survivor_used);
  return eden_used;
}

size_t G1MonitoringSupport::survivor_space_committed() {
  return MAX2(survivor_space_used(),
              (size_t) HeapRegion::GrainBytes);
}

size_t G1MonitoringSupport::survivor_space_used() {
  size_t survivor_num = g1h()->g1_policy()->recorded_survivor_regions();
  size_t survivor_used = survivor_num * HeapRegion::GrainBytes;
  return survivor_used;
}

size_t G1MonitoringSupport::old_space_committed() {
  size_t committed = overall_committed();
  size_t eden_committed = eden_space_committed();
  size_t survivor_committed = survivor_space_committed();
  committed = subtract_up_to_zero(committed, eden_committed);
  committed = subtract_up_to_zero(committed, survivor_committed);
  committed = MAX2(committed, (size_t) HeapRegion::GrainBytes);
  return committed;
}

// See the comment near the top of g1MonitoringSupport.hpp for
// an explanation of these calculations for "used" and "capacity".
size_t G1MonitoringSupport::old_space_used() {
  size_t used = overall_used();
  size_t eden_used = eden_space_used();
  size_t survivor_used = survivor_space_used();
  used = subtract_up_to_zero(used, eden_used);
  used = subtract_up_to_zero(used, survivor_used);
  return used;
}

void G1MonitoringSupport::update_counters() {
  if (UsePerfData) {
    eden_counters()->update_capacity(eden_space_committed());
    eden_counters()->update_used(eden_space_used());
    to_counters()->update_capacity(survivor_space_committed());
    to_counters()->update_used(survivor_space_used());
    old_space_counters()->update_capacity(old_space_committed());
    old_space_counters()->update_used(old_space_used());
    non_young_collection_counters()->update_all();
  }
}

void G1MonitoringSupport::update_eden_counters() {
  if (UsePerfData) {
    eden_counters()->update_capacity(eden_space_committed());
    eden_counters()->update_used(eden_space_used());
  }
}
