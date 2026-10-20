/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHAGECENSUS_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHAGECENSUS_HPP

#include "gc/shared/ageTable.hpp"

// A class for tracking a sequence of cohort population vectors (or,
// interchangeably, age tables) for up to C=MAX_COHORTS age cohorts, where a cohort
// represents the set of objects allocated during a specific inter-GC epoch.
// Epochs are demarcated by GC cycles, with those surviving a cycle aging by
// an epoch. The census tracks the historical variation of cohort demographics
// across N=MAX_SNAPSHOTS recent epochs. Since there are at most C age cohorts in
// the population, we need only track at most N=C epochal snapshots to track a
// maximal longitudinal demographics of every object's longitudinal cohort in
// the young generation. The _global_age_table is thus, currently, a C x N (row-major)
// matrix, with C=16, and, for now N=C=16, currently.
// In theory, we might decide to track even longer (N=MAX_SNAPSHOTS) demographic
// histories, but that isn't the case today. In particular, the current tenuring
// threshold algorithm uses only 2 most recent snapshots, with the remaining
// MAX_SNAPSHOTS-2=14 reserved for research purposes.
//
// In addition, this class also maintains per worker population vectors into which
// census for the current minor GC is accumulated (during marking or, optionally, during
// evacuation). These are cleared after each marking (respectively, evacuation) cycle,
// once the per-worker data is consolidated into the appropriate population vector
// per minor collection. The _local_age_table is thus C x N, for N GC workers.
class ShenandoahAgeCensus: public CHeapObj<mtGC> {
  friend class ShenandoahTenuringOverride;

  AgeTable** _global_age_tables;      // Global age tables used for adapting tenuring threshold, one per snapshot
  AgeTable** _local_age_tables;       // Local scratch age tables to track object ages, one per worker

  uint  _epoch;                      // Current epoch (modulo max age)
  uint* _tenuring_threshold;         // An array of the last N tenuring threshold values we
                                     // computed.

  uint _max_workers;                 // Maximum number of workers for parallel tasks

  bool _always_tenure;               // When true, every age is tenurable.

  // Mortality rate of a cohort, given its population in
  // previous and current epochs
  double mortality_rate(size_t prev_pop, size_t cur_pop);

  // Update to a new epoch, creating a slot for new census.
  void prepare_for_census_update();

  // Update the tenuring threshold, calling
  // compute_tenuring_threshold() to calculate the new
  // value
  void update_tenuring_threshold();

  // Use _global_age_table and the current _epoch to compute a new tenuring
  // threshold, which will be remembered until the next invocation of
  // compute_tenuring_threshold.
  uint compute_tenuring_threshold();

  // Return the tenuring threshold computed for the previous epoch
  uint previous_tenuring_threshold() const {
    assert(_epoch < MAX_SNAPSHOTS, "Error");
    uint prev = _epoch - 1;
    if (prev >= MAX_SNAPSHOTS) {
      // _epoch is 0
      assert(_epoch == 0, "Error");
      prev = MAX_SNAPSHOTS - 1;
    }
    return _tenuring_threshold[prev];
  }

  // Set always tenure mode. Currently only used by ShenandoahTenuringOverride
  // to force is_tenurable() to be true for every age during WB.fullGC tests.
  void set_always_tenure(bool always_tenure) { _always_tenure = always_tenure; }

 public:
  enum {
    MAX_COHORTS = AgeTable::table_size,    // = markWord::max_age + 1
    MAX_SNAPSHOTS = MAX_COHORTS            // May change in the future
  };

  ShenandoahAgeCensus();
  ShenandoahAgeCensus(uint max_workers);
  ~ShenandoahAgeCensus();

  // Return the local age table (population vector) for worker_id.
  AgeTable* get_local_age_table(uint worker_id) const {
    return _local_age_tables[worker_id];
  }

  // Return the most recently computed tenuring threshold.
  // Visible for testing. Use is_tenurable for consistent tenuring comparisons.
  uint tenuring_threshold() const { return _tenuring_threshold[_epoch]; }

  // Returns zero when always_tenure is true (which is only true when the gc
  // is triggered by WB.fullGC()). Note that zero itself is the floor, so do
  // not subtract from it to get the younger cohort.
  uint effective_threshold() const {
    return is_always_tenure() ? 0 : tenuring_threshold();
  }

  // Return true if this age is at or above the tenuring threshold, or if always tenure is enabled.
  bool is_tenurable(uint age) const {
    return age >= tenuring_threshold() || _always_tenure;
  }

  bool is_always_tenure() const { return _always_tenure; }

  // Update the local age table for worker_id by size for
  // given obj_age and region_age
  inline void add(uint obj_age, uint region_age, size_t size, uint worker_id);

  // Update the census data, and compute the new tenuring threshold.
  // This method should be called at the end of each marking cycle to update
  // the tenuring threshold to be used in the next cycle.
  // age0_pop is the population of Cohort 0 that may have been missed in
  // the regular census during the marking cycle, corresponding to objects
  // allocated when the concurrent marking was in progress.
  void update_census(size_t age0_pop);

  // Return the total size of the population at or above the given threshold for the current epoch
  size_t get_tenurable_bytes(uint tenuring_threshold) const;

  // As above, but use the current tenuring threshold
  size_t get_tenurable_bytes() const { return get_tenurable_bytes(tenuring_threshold()); }

  // Reset the epoch, clearing accumulated census history
  // Note: this isn't currently used, but reserved for planned
  // future usage.
  void reset_global();

  // Reset any (potentially partial) census information in worker-local age tables
  void reset_local();

#ifndef PRODUCT
  // Check whether census information is clear
  bool is_clear_global();
  bool is_clear_local();
#endif // !PRODUCT

  // Print the age census information
  void print();
};

// RAII object that enables ShenandoahAgeCensus always tenure mode for the
// duration of a scope and disables it on destruction. Used to force promotion
// of all young objects during whitebox full GCs.
class ShenandoahTenuringOverride : public StackObj {
  ShenandoahAgeCensus* _census;
  bool _active;
public:
  ShenandoahTenuringOverride(bool active, ShenandoahAgeCensus* census) :
    _census(census), _active(active) {
    if (_active) {
      _census->set_always_tenure(true);
    }
  }
  ~ShenandoahTenuringOverride() {
    if (_active) {
      _census->set_always_tenure(false);
    }
  }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHAGECENSUS_HPP
