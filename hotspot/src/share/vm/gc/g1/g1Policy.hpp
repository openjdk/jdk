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

#ifndef SHARE_VM_GC_G1_G1POLICY_HPP
#define SHARE_VM_GC_G1_G1POLICY_HPP

#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1InCSetState.hpp"
#include "gc/g1/g1InitialMarkToMixedTimeTracker.hpp"
#include "gc/g1/g1MMUTracker.hpp"
#include "gc/g1/g1Predictions.hpp"
#include "gc/g1/g1YoungGenSizer.hpp"
#include "gc/shared/gcCause.hpp"
#include "utilities/pair.hpp"

// A G1Policy makes policy decisions that determine the
// characteristics of the collector.  Examples include:
//   * choice of collection set.
//   * when to collect.

class HeapRegion;
class G1CollectionSet;
class CollectionSetChooser;
class G1IHOPControl;
class G1Analytics;
class G1YoungGenSizer;

class G1Policy: public CHeapObj<mtGC> {
public:
  virtual const G1Predictions& predictor() const = 0;
  virtual const G1Analytics* analytics()   const = 0;

  // Add the given number of bytes to the total number of allocated bytes in the old gen.
  virtual void add_bytes_allocated_in_old_since_last_gc(size_t bytes) = 0;

  // Accessors

  virtual void set_region_eden(HeapRegion* hr) = 0;
  virtual void set_region_survivor(HeapRegion* hr) = 0;

  virtual void record_max_rs_lengths(size_t rs_lengths) = 0;

  virtual double predict_base_elapsed_time_ms(size_t pending_cards) const = 0;
  virtual double predict_base_elapsed_time_ms(size_t pending_cards,
                                              size_t scanned_cards) const = 0;

  virtual double predict_region_elapsed_time_ms(HeapRegion* hr, bool for_young_gc) const = 0;

  virtual void cset_regions_freed() = 0;

  virtual G1MMUTracker* mmu_tracker() = 0;

  virtual const G1MMUTracker* mmu_tracker() const = 0;

  virtual double max_pause_time_ms() const = 0;

  virtual size_t pending_cards() const = 0;

  // Calculate the minimum number of old regions we'll add to the CSet
  // during a mixed GC.
  virtual uint calc_min_old_cset_length() const = 0;

  // Calculate the maximum number of old regions we'll add to the CSet
  // during a mixed GC.
  virtual uint calc_max_old_cset_length() const = 0;

  // Returns the given amount of uncollected reclaimable space
  // as a percentage of the current heap capacity.
  virtual double reclaimable_bytes_perc(size_t reclaimable_bytes) const = 0;

  virtual ~G1Policy() {}

  virtual G1CollectorState* collector_state() const = 0;

  virtual G1GCPhaseTimes* phase_times() const = 0;

  // Check the current value of the young list RSet lengths and
  // compare it against the last prediction. If the current value is
  // higher, recalculate the young list target length prediction.
  virtual void revise_young_list_target_length_if_necessary(size_t rs_lengths) = 0;

  // This should be called after the heap is resized.
  virtual void record_new_heap_size(uint new_number_of_regions) = 0;

  virtual void init(G1CollectedHeap* g1h, G1CollectionSet* collection_set) = 0;

  virtual void note_gc_start() = 0;

  virtual bool need_to_start_conc_mark(const char* source, size_t alloc_word_size = 0) = 0;

  // Record the start and end of an evacuation pause.
  virtual void record_collection_pause_start(double start_time_sec) = 0;
  virtual void record_collection_pause_end(double pause_time_ms, size_t cards_scanned, size_t heap_used_bytes_before_gc) = 0;

  // Record the start and end of a full collection.
  virtual void record_full_collection_start() = 0;
  virtual void record_full_collection_end() = 0;

  // Must currently be called while the world is stopped.
  virtual void record_concurrent_mark_init_end(double mark_init_elapsed_time_ms) = 0;

  // Record start and end of remark.
  virtual void record_concurrent_mark_remark_start() = 0;
  virtual void record_concurrent_mark_remark_end() = 0;

  // Record start, end, and completion of cleanup.
  virtual void record_concurrent_mark_cleanup_start() = 0;
  virtual void record_concurrent_mark_cleanup_end() = 0;
  virtual void record_concurrent_mark_cleanup_completed() = 0;

  virtual void print_phases() = 0;

  // Record how much space we copied during a GC. This is typically
  // called when a GC alloc region is being retired.
  virtual void record_bytes_copied_during_gc(size_t bytes) = 0;

  // The amount of space we copied during a GC.
  virtual size_t bytes_copied_during_gc() const = 0;

  virtual void finalize_collection_set(double target_pause_time_ms) = 0;

  // This sets the initiate_conc_mark_if_possible() flag to start a
  // new cycle, as long as we are not already in one. It's best if it
  // is called during a safepoint when the test whether a cycle is in
  // progress or not is stable.
  virtual bool force_initial_mark_if_outside_cycle(GCCause::Cause gc_cause) = 0;

  // This is called at the very beginning of an evacuation pause (it
  // has to be the first thing that the pause does). If
  // initiate_conc_mark_if_possible() is true, and the concurrent
  // marking thread has completed its work during the previous cycle,
  // it will set during_initial_mark_pause() to so that the pause does
  // the initial-mark work and start a marking cycle.
  virtual void decide_on_conc_mark_initiation() = 0;

  // Print stats on young survival ratio
  virtual void print_yg_surv_rate_info() const = 0;

  virtual void finished_recalculating_age_indexes(bool is_survivors) = 0;

  virtual size_t young_list_target_length() const = 0;

  virtual bool should_allocate_mutator_region() const = 0;

  virtual bool can_expand_young_list() const = 0;

  virtual uint young_list_max_length() const = 0;

  virtual bool adaptive_young_list_length() const = 0;

  virtual bool should_process_references() const = 0;

  virtual uint tenuring_threshold() const = 0;
  virtual uint max_survivor_regions() = 0;

  virtual void note_start_adding_survivor_regions() = 0;

  virtual void note_stop_adding_survivor_regions() = 0;

  virtual void record_age_table(AgeTable* age_table) = 0;
};

#endif // SHARE_VM_GC_G1_G1POLICY_HPP
