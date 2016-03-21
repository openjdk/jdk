/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1COLLECTIONSET_HPP
#define SHARE_VM_GC_G1_G1COLLECTIONSET_HPP

#include "gc/g1/collectionSetChooser.hpp"
#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class G1CollectedHeap;
class G1CollectorPolicy;
class G1CollectorState;
class G1GCPhaseTimes;
class HeapRegion;

class G1CollectionSet VALUE_OBJ_CLASS_SPEC {
  G1CollectedHeap* _g1;
  G1CollectorPolicy* _policy;

  CollectionSetChooser* _cset_chooser;

  uint _eden_region_length;
  uint _survivor_region_length;
  uint _old_region_length;

  // The head of the list (via "next_in_collection_set()") representing the
  // current collection set. Set from the incrementally built collection
  // set at the start of the pause.
  HeapRegion* _head;

  // The number of bytes in the collection set before the pause. Set from
  // the incrementally built collection set at the start of an evacuation
  // pause, and incremented in finalize_old_part() when adding old regions
  // (if any) to the collection set.
  size_t _bytes_used_before;

  // The sum of live bytes in the collection set, set as described above.
  size_t _bytes_live_before;

  size_t _recorded_rs_lengths;

  // The associated information that is maintained while the incremental
  // collection set is being built with young regions. Used to populate
  // the recorded info for the evacuation pause.

  enum CSetBuildType {
    Active,             // We are actively building the collection set
    Inactive            // We are not actively building the collection set
  };

  CSetBuildType _inc_build_state;

  // The head of the incrementally built collection set.
  HeapRegion* _inc_head;

  // The tail of the incrementally built collection set.
  HeapRegion* _inc_tail;

  // The number of bytes in the incrementally built collection set.
  // Used to set _collection_set_bytes_used_before at the start of
  // an evacuation pause.
  size_t _inc_bytes_used_before;

  // The number of live bytes in the incrementally built collection set.
  size_t _inc_bytes_live_before;

  // The RSet lengths recorded for regions in the CSet. It is updated
  // by the thread that adds a new region to the CSet. We assume that
  // only one thread can be allocating a new CSet region (currently,
  // it does so after taking the Heap_lock) hence no need to
  // synchronize updates to this field.
  size_t _inc_recorded_rs_lengths;

  // A concurrent refinement thread periodically samples the young
  // region RSets and needs to update _inc_recorded_rs_lengths as
  // the RSets grow. Instead of having to synchronize updates to that
  // field we accumulate them in this field and add it to
  // _inc_recorded_rs_lengths_diffs at the start of a GC.
  ssize_t _inc_recorded_rs_lengths_diffs;

  // The predicted elapsed time it will take to collect the regions in
  // the CSet. This is updated by the thread that adds a new region to
  // the CSet. See the comment for _inc_recorded_rs_lengths about
  // MT-safety assumptions.
  double _inc_predicted_elapsed_time_ms;

  // See the comment for _inc_recorded_rs_lengths_diffs.
  double _inc_predicted_elapsed_time_ms_diffs;

  G1CollectorState* collector_state();
  G1GCPhaseTimes* phase_times();

  double predict_region_elapsed_time_ms(HeapRegion* hr);

public:
  G1CollectionSet(G1CollectedHeap* g1h);
  ~G1CollectionSet();

  void set_policy(G1CollectorPolicy* g1p) {
    assert(_policy == NULL, "should only initialize once");
    _policy = g1p;
  }

  CollectionSetChooser* cset_chooser();

  void init_region_lengths(uint eden_cset_region_length,
                           uint survivor_cset_region_length);

  void set_recorded_rs_lengths(size_t rs_lengths);

  uint region_length() const       { return young_region_length() +
                                            old_region_length(); }
  uint young_region_length() const { return eden_region_length() +
                                            survivor_region_length(); }

  uint eden_region_length() const     { return _eden_region_length;     }
  uint survivor_region_length() const { return _survivor_region_length; }
  uint old_region_length() const      { return _old_region_length;      }

  // Incremental CSet Support

  // The head of the incrementally built collection set.
  HeapRegion* inc_head() { return _inc_head; }

  // The tail of the incrementally built collection set.
  HeapRegion* inc_tail() { return _inc_tail; }

  // Initialize incremental collection set info.
  void start_incremental_building();

  // Perform any final calculations on the incremental CSet fields
  // before we can use them.
  void finalize_incremental_building();

  void clear_incremental() {
    _inc_head = NULL;
    _inc_tail = NULL;
  }

  // Stop adding regions to the incremental collection set
  void stop_incremental_building() { _inc_build_state = Inactive; }

  // The head of the list (via "next_in_collection_set()") representing the
  // current collection set.
  HeapRegion* head() { return _head; }

  void clear_head() { _head = NULL; }

  size_t recorded_rs_lengths() { return _recorded_rs_lengths; }

  size_t bytes_used_before() const {
    return _bytes_used_before;
  }

  void reset_bytes_used_before() {
    _bytes_used_before = 0;
  }

  void reset_bytes_live_before() {
    _bytes_live_before = 0;
  }

  // Choose a new collection set.  Marks the chosen regions as being
  // "in_collection_set", and links them together.  The head and number of
  // the collection set are available via access methods.
  double finalize_young_part(double target_pause_time_ms);
  void finalize_old_part(double time_remaining_ms);

  // Add old region "hr" to the CSet.
  void add_old_region(HeapRegion* hr);

  // Update information about hr in the aggregated information for
  // the incrementally built collection set.
  void update_young_region_prediction(HeapRegion* hr, size_t new_rs_length);

  // Add hr to the LHS of the incremental collection set.
  void add_eden_region(HeapRegion* hr);

  // Add hr to the RHS of the incremental collection set.
  void add_survivor_regions(HeapRegion* hr);

#ifndef PRODUCT
  void print(HeapRegion* list_head, outputStream* st);
#endif // !PRODUCT

private:
  // Update the incremental cset information when adding a region
  // (should not be called directly).
  void add_young_region_common(HeapRegion* hr);

};

#endif // SHARE_VM_GC_G1_G1COLLECTIONSET_HPP

