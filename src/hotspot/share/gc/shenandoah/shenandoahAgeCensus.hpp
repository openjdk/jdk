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

#ifndef PRODUCT
// Enable noise instrumentation
#define SHENANDOAH_CENSUS_NOISE 1
#endif  // PRODUCT

#ifdef SHENANDOAH_CENSUS_NOISE

#define CENSUS_NOISE(x) x
#define NO_CENSUS_NOISE(x)

struct ShenandoahNoiseStats {
  size_t skipped;   // Volume of objects skipped
  size_t aged;      // Volume of objects from aged regions
  size_t clamped;   // Volume of objects whose ages were clamped
  size_t young;     // Volume of (rejuvenated) objects of retrograde age

  ShenandoahNoiseStats() {
    clear();
  }

  void clear() {
    skipped = 0;
    aged = 0;
    clamped = 0;
    young = 0;
  }

#ifndef PRODUCT
  bool is_clear() {
    return (skipped + aged + clamped + young) == 0;
  }
#endif // !PRODUCT

  void merge(ShenandoahNoiseStats& other) {
    skipped += other.skipped;
    aged    += other.aged;
    clamped += other.clamped;
    young   += other.young;
  }

  void print(size_t total);
};
#else  // SHENANDOAH_CENSUS_NOISE
#define CENSUS_NOISE(x)
#define NO_CENSUS_NOISE(x) x
#endif // SHENANDOAH_CENSUS_NOISE

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
// evacuation). These are cleared after each marking (resectively, evacuation) cycle,
// once the per-worker data is consolidated into the appropriate population vector
// per minor collection. The _local_age_table is thus C x N, for N GC workers.
class ShenandoahAgeCensus: public CHeapObj<mtGC> {
  AgeTable** _global_age_table;      // Global age table used for adapting tenuring threshold, one per snapshot
  AgeTable** _local_age_table;       // Local scratch age tables to track object ages, one per worker

#ifdef SHENANDOAH_CENSUS_NOISE
  ShenandoahNoiseStats* _global_noise; // Noise stats, one per snapshot
  ShenandoahNoiseStats* _local_noise;  // Local scratch table for noise stats, one per worker
#endif // SHENANDOAH_CENSUS_NOISE

  uint _epoch;                       // Current epoch (modulo max age)
  uint *_tenuring_threshold;         // An array of the last N tenuring threshold values we
                                     // computed.

  // Mortality rate of a cohort, given its population in
  // previous and current epochs
  double mortality_rate(size_t prev_pop, size_t cur_pop);

  // Update the tenuring threshold, calling
  // compute_tenuring_threshold to calculate the new
  // value
  void update_tenuring_threshold();

  // This uses the data in the ShenandoahAgeCensus object's _global_age_table and the
  // current _epoch to compute a new tenuring threshold, which will be remembered
  // until the next invocation of compute_tenuring_threshold.
  uint compute_tenuring_threshold();

 public:
  enum {
    MAX_COHORTS = AgeTable::table_size,    // = markWord::max_age + 1
    MAX_SNAPSHOTS = MAX_COHORTS            // May change in the future
  };

  ShenandoahAgeCensus();

  // Return the local age table (population vector) for worker_id.
  // Only used in the case of (ShenandoahGenerationalAdaptiveTenuring && !ShenandoahGenerationalCensusAtEvac)
  AgeTable* get_local_age_table(uint worker_id) {
    return (AgeTable*) _local_age_table[worker_id];
  }

  // Update the local age table for worker_id by size for
  // given obj_age, region_age, and region_youth
  CENSUS_NOISE(void add(uint obj_age, uint region_age, uint region_youth, size_t size, uint worker_id);)
  NO_CENSUS_NOISE(void add(uint obj_age, uint region_age, size_t size, uint worker_id);)

#ifdef SHENANDOAH_CENSUS_NOISE
  // Update the local skip table for worker_id by size
  void add_skipped(size_t size, uint worker_id);
  // Update the local aged region volume table for worker_id by size
  void add_aged(size_t size, uint worker_id);
  // Update the local clamped object volume table for worker_id by size
  void add_clamped(size_t size, uint worker_id);
  // Update the local (rejuvenated) object volume (retrograde age) for worker_id by size
  void add_young(size_t size, uint worker_id);
#endif // SHENANDOAH_CENSUS_NOISE

  // Update to a new epoch, creating a slot for new census.
  void prepare_for_census_update();

  // Update the census data, and compute the new tenuring threshold.
  // age0_pop is the population of Cohort 0 that may have been missed in
  // the regular census.
  void update_census(size_t age0_pop, AgeTable* pv1 = nullptr, AgeTable* pv2 = nullptr);

  // Return the most recently computed tenuring threshold
  uint tenuring_threshold() const { return _tenuring_threshold[_epoch]; }

  // Return the tenuring threshold computed for the previous epoch
  uint previous_tenuring_threshold() const {
    assert(_epoch < MAX_SNAPSHOTS, "Error");
    uint prev = _epoch - 1;
    if (prev >= MAX_SNAPSHOTS) {
      // _epoch is 0
      prev = MAX_SNAPSHOTS - 1;
    }
    return _tenuring_threshold[prev];
  }

  // Reset the epoch, clearing accumulated census history
  void reset_global();
  // Reset any partial census information
  void reset_local();

  // Check whether census information is clear
  bool is_clear_global();
  bool is_clear_local();

  // Print the age census information
  void print();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHAGECENSUS_HPP
