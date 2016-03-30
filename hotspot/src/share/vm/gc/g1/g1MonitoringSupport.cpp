/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/g1/g1MonitoringSupport.hpp"

G1GenerationCounters::G1GenerationCounters(G1MonitoringSupport* g1mm,
                                           const char* name,
                                           int ordinal, int spaces,
                                           size_t min_capacity,
                                           size_t max_capacity,
                                           size_t curr_capacity)
  : GenerationCounters(name, ordinal, spaces, min_capacity,
                       max_capacity, curr_capacity), _g1mm(g1mm) { }

// We pad the capacity three times given that the young generation
// contains three spaces (eden and two survivors).
G1YoungGenerationCounters::G1YoungGenerationCounters(G1MonitoringSupport* g1mm,
                                                     const char* name)
  : G1GenerationCounters(g1mm, name, 0 /* ordinal */, 3 /* spaces */,
               G1MonitoringSupport::pad_capacity(0, 3) /* min_capacity */,
               G1MonitoringSupport::pad_capacity(g1mm->young_gen_max(), 3),
               G1MonitoringSupport::pad_capacity(0, 3) /* curr_capacity */) {
  if (UsePerfData) {
    update_all();
  }
}

G1OldGenerationCounters::G1OldGenerationCounters(G1MonitoringSupport* g1mm,
                                                 const char* name)
  : G1GenerationCounters(g1mm, name, 1 /* ordinal */, 1 /* spaces */,
               G1MonitoringSupport::pad_capacity(0) /* min_capacity */,
               G1MonitoringSupport::pad_capacity(g1mm->old_gen_max()),
               G1MonitoringSupport::pad_capacity(0) /* curr_capacity */) {
  if (UsePerfData) {
    update_all();
  }
}

void G1YoungGenerationCounters::update_all() {
  size_t committed =
            G1MonitoringSupport::pad_capacity(_g1mm->young_gen_committed(), 3);
  _current_size->set_value(committed);
}

void G1OldGenerationCounters::update_all() {
  size_t committed =
            G1MonitoringSupport::pad_capacity(_g1mm->old_gen_committed());
  _current_size->set_value(committed);
}

G1MonitoringSupport::G1MonitoringSupport(G1CollectedHeap* g1h) :
  _g1h(g1h),
  _incremental_collection_counters(NULL),
  _full_collection_counters(NULL),
  _conc_collection_counters(NULL),
  _old_collection_counters(NULL),
  _old_space_counters(NULL),
  _young_collection_counters(NULL),
  _eden_counters(NULL),
  _from_counters(NULL),
  _to_counters(NULL),

  _overall_reserved(0),
  _overall_committed(0),    _overall_used(0),
  _young_region_num(0),
  _young_gen_committed(0),
  _eden_committed(0),       _eden_used(0),
  _survivor_committed(0),   _survivor_used(0),
  _old_committed(0),        _old_used(0) {

  _overall_reserved = g1h->max_capacity();
  recalculate_sizes();

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
  // name "collector.2". STW phases as part of a concurrent collection.
  _conc_collection_counters =
    new CollectorCounters("G1 stop-the-world phases", 2);

  // timer sampling for all counters supporting sampling only update the
  // used value.  See the take_sample() method.  G1 requires both used and
  // capacity updated so sampling is not currently used.  It might
  // be sufficient to update all counters in take_sample() even though
  // take_sample() only returns "used".  When sampling was used, there
  // were some anomolous values emitted which may have been the consequence
  // of not updating all values simultaneously (i.e., see the calculation done
  // in eden_space_used(), is it possible that the values used to
  // calculate either eden_used or survivor_used are being updated by
  // the collector when the sample is being done?).
  const bool sampled = false;

  // "Generation" and "Space" counters.
  //
  //  name "generation.1" This is logically the old generation in
  // generational GC terms.  The "1, 1" parameters are for
  // the n-th generation (=1) with 1 space.
  // Counters are created from minCapacity, maxCapacity, and capacity
  _old_collection_counters = new G1OldGenerationCounters(this, "old");

  //  name  "generation.1.space.0"
  // Counters are created from maxCapacity, capacity, initCapacity,
  // and used.
  _old_space_counters = new HSpaceCounters("space", 0 /* ordinal */,
    pad_capacity(overall_reserved()) /* max_capacity */,
    pad_capacity(old_space_committed()) /* init_capacity */,
   _old_collection_counters);

  //   Young collection set
  //  name "generation.0".  This is logically the young generation.
  //  The "0, 3" are parameters for the n-th generation (=0) with 3 spaces.
  // See  _old_collection_counters for additional counters
  _young_collection_counters = new G1YoungGenerationCounters(this, "young");

  //  name "generation.0.space.0"
  // See _old_space_counters for additional counters
  _eden_counters = new HSpaceCounters("eden", 0 /* ordinal */,
    pad_capacity(overall_reserved()) /* max_capacity */,
    pad_capacity(eden_space_committed()) /* init_capacity */,
    _young_collection_counters);

  //  name "generation.0.space.1"
  // See _old_space_counters for additional counters
  // Set the arguments to indicate that this survivor space is not used.
  _from_counters = new HSpaceCounters("s0", 1 /* ordinal */,
    pad_capacity(0) /* max_capacity */,
    pad_capacity(0) /* init_capacity */,
    _young_collection_counters);

  //  name "generation.0.space.2"
  // See _old_space_counters for additional counters
  _to_counters = new HSpaceCounters("s1", 2 /* ordinal */,
    pad_capacity(overall_reserved()) /* max_capacity */,
    pad_capacity(survivor_space_committed()) /* init_capacity */,
    _young_collection_counters);

  if (UsePerfData) {
    // Given that this survivor space is not used, we update it here
    // once to reflect that its used space is 0 so that we don't have to
    // worry about updating it again later.
    _from_counters->update_used(0);
  }
}

void G1MonitoringSupport::recalculate_sizes() {
  G1CollectedHeap* g1 = g1h();

  // Recalculate all the sizes from scratch. We assume that this is
  // called at a point where no concurrent updates to the various
  // values we read here are possible (i.e., at a STW phase at the end
  // of a GC).

  uint young_list_length = g1->young_list()->length();
  uint survivor_list_length = g1->young_list()->survivor_length();
  assert(young_list_length >= survivor_list_length, "invariant");
  uint eden_list_length = young_list_length - survivor_list_length;
  // Max length includes any potential extensions to the young gen
  // we'll do when the GC locker is active.
  uint young_list_max_length = g1->g1_policy()->young_list_max_length();
  assert(young_list_max_length >= survivor_list_length, "invariant");
  uint eden_list_max_length = young_list_max_length - survivor_list_length;

  _overall_used = g1->used_unlocked();
  _eden_used = (size_t) eden_list_length * HeapRegion::GrainBytes;
  _survivor_used = (size_t) survivor_list_length * HeapRegion::GrainBytes;
  _young_region_num = young_list_length;
  _old_used = subtract_up_to_zero(_overall_used, _eden_used + _survivor_used);

  // First calculate the committed sizes that can be calculated independently.
  _survivor_committed = _survivor_used;
  _old_committed = HeapRegion::align_up_to_region_byte_size(_old_used);

  // Next, start with the overall committed size.
  _overall_committed = g1->capacity();
  size_t committed = _overall_committed;

  // Remove the committed size we have calculated so far (for the
  // survivor and old space).
  assert(committed >= (_survivor_committed + _old_committed), "sanity");
  committed -= _survivor_committed + _old_committed;

  // Next, calculate and remove the committed size for the eden.
  _eden_committed = (size_t) eden_list_max_length * HeapRegion::GrainBytes;
  // Somewhat defensive: be robust in case there are inaccuracies in
  // the calculations
  _eden_committed = MIN2(_eden_committed, committed);
  committed -= _eden_committed;

  // Finally, give the rest to the old space...
  _old_committed += committed;
  // ..and calculate the young gen committed.
  _young_gen_committed = _eden_committed + _survivor_committed;

  assert(_overall_committed ==
         (_eden_committed + _survivor_committed + _old_committed),
         "the committed sizes should add up");
  // Somewhat defensive: cap the eden used size to make sure it
  // never exceeds the committed size.
  _eden_used = MIN2(_eden_used, _eden_committed);
  // _survivor_committed and _old_committed are calculated in terms of
  // the corresponding _*_used value, so the next two conditions
  // should hold.
  assert(_survivor_used <= _survivor_committed, "post-condition");
  assert(_old_used <= _old_committed, "post-condition");
}

void G1MonitoringSupport::recalculate_eden_size() {
  G1CollectedHeap* g1 = g1h();

  // When a new eden region is allocated, only the eden_used size is
  // affected (since we have recalculated everything else at the last GC).

  uint young_region_num = g1h()->young_list()->length();
  if (young_region_num > _young_region_num) {
    uint diff = young_region_num - _young_region_num;
    _eden_used += (size_t) diff * HeapRegion::GrainBytes;
    // Somewhat defensive: cap the eden used size to make sure it
    // never exceeds the committed size.
    _eden_used = MIN2(_eden_used, _eden_committed);
    _young_region_num = young_region_num;
  }
}

void G1MonitoringSupport::update_sizes() {
  recalculate_sizes();
  if (UsePerfData) {
    eden_counters()->update_capacity(pad_capacity(eden_space_committed()));
    eden_counters()->update_used(eden_space_used());
    // only the to survivor space (s1) is active, so we don't need to
    // update the counters for the from survivor space (s0)
    to_counters()->update_capacity(pad_capacity(survivor_space_committed()));
    to_counters()->update_used(survivor_space_used());
    old_space_counters()->update_capacity(pad_capacity(old_space_committed()));
    old_space_counters()->update_used(old_space_used());
    old_collection_counters()->update_all();
    young_collection_counters()->update_all();
    MetaspaceCounters::update_performance_counters();
    CompressedClassSpaceCounters::update_performance_counters();
  }
}

void G1MonitoringSupport::update_eden_size() {
  recalculate_eden_size();
  if (UsePerfData) {
    eden_counters()->update_used(eden_space_used());
  }
}
