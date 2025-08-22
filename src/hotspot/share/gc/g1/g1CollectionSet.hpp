/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1COLLECTIONSET_HPP
#define SHARE_GC_G1_G1COLLECTIONSET_HPP

#include "gc/g1/g1CollectionSetCandidates.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class G1CollectedHeap;
class G1CollectorState;
class G1GCPhaseTimes;
class G1ParScanThreadStateSet;
class G1Policy;
class G1SurvivorRegions;
class G1HeapRegion;
class G1HeapRegionClaimer;
class G1HeapRegionClosure;

// The collection set.
//
// The set of regions and candidate groups that were evacuated during an
// evacuation pause.
//
// At the end of a collection, before freeing it, this set contains all regions
// and collection set groups that were evacuated during this collection:
//
// - survivor regions from the last collection (if any)
// - eden regions allocated by the mutator
// - old gen regions evacuated during mixed gc
//
// This set is initially built at mutator time as regions are retired. If the
// collection is a mixed gc, it contains some additional (during the pause)
// incrementally added old regions from the collection set candidates.
//
// A more detailed overview of how the collection set changes over time follows:
//
// 0) at the end of GC the survivor regions are added to this collection set.
// 1) the mutator incrementally adds eden regions as they retire
//
// ----- gc starts
//
// 2) prepare (finalize) young regions of the collection set for collection
//    - relabel the survivors as eden
//    - finish up the incremental building that happened at mutator time
//
// iff this is a young-only collection:
//
// a3) evacuate the current collection set in one "initial evacuation" phase
//
// iff this is a mixed collection:
//
// b3) calculate the set of old gen regions we may be able to collect in this
//     collection from the list of collection set candidates.
//     - one part is added to the current collection set
//     - the remainder regions are labeled as optional, and NOT yet added to the
//     collection set.
// b4) evacuate the current collection set in the "initial evacuation" phase
// b5) evacuate the optional regions in the "optional evacuation" phase. This is
//     done in increments (or rounds).
//     b5-1) add a few of the optional regions to the current collection set
//     b5-2) evacuate only these newly added optional regions. For this mechanism we
//     reuse the incremental collection set building infrastructure (used also at
//     mutator time).
//     b5-3) repeat from b5-1 until the policy determines we are done
//
// all collections
//
// 6) free the collection set (contains all regions now; empties collection set
//    afterwards)
// 7) add survivors to this collection set
//
// ----- gc ends
//
// goto 1)
//
// Examples of how the collection set might look over time:
//
// Legend:
// S = survivor, E = eden, O = old.
// |xxxx| = increment (with increment markers), containing four regions
//
// |SSSS|                         ... after step 0), with four survivor regions
// |SSSSEE|                       ... at step 1), after retiring two eden regions
// |SSSSEEEE|                     ... after step 1), after retiring four eden regions
// |EEEEEEEE|                     ... after step 2)
//
// iff this is a young-only collection
//
// EEEEEEEE||                      ... after step a3), after initial evacuation phase
// ||                              ... after step 6)
// |SS|                            ... after step 7), with two survivor regions
//
// iff this is a mixed collection
//
// |EEEEEEEEOOOO|                  ... after step b3), added four regions to be
//                                     evacuated in the "initial evacuation" phase
// EEEEEEEEOOOO||                  ... after step b4), incremental part is empty
//                                     after evacuation
// EEEEEEEEOOOO|OO|                ... after step b5.1), added two regions to be
//                                     evacuated in the first round of the
//                                     "optional evacuation" phase
// EEEEEEEEOOOOOO|O|               ... after step b5.1), added one region to be
//                                     evacuated in the second round of the
//                                     "optional evacuation" phase
// EEEEEEEEOOOOOOO||               ... after step b5), the complete collection set.
// ||                              ... after step b6)
// |SSS|                           ... after step 7), with three survivor regions
//
// Candidate groups are kept in sync with the contents of the collection set regions.
class G1CollectionSet {
  G1CollectedHeap* _g1h;
  G1Policy* _policy;

  // All old gen collection set candidate regions.
  G1CollectionSetCandidates _candidates;

  // The actual collection set as a set of region indices.
  //
  // All regions in _regions below _regions_cur_length are assumed to be part of the
  // collection set.
  // We assume that at any time there is at most only one writer and (one or more)
  // concurrent readers. This means synchronization using storestore and loadload
  // barriers on the writer and reader respectively only are sufficient.
  //
  // This corresponds to the regions referenced by the candidate groups further below.
  uint* _regions;
  uint _regions_max_length;

  volatile uint _regions_cur_length;

  // Old gen groups selected for evacuation.
  G1CSetCandidateGroupList _groups;

  uint groups_cur_length() const;

  uint _eden_region_length;
  uint _survivor_region_length;
  uint _initial_old_region_length;

  // When doing mixed collections we can add old regions to the collection set, which
  // will be collected only if there is enough time. We call these optional (old)
  // groups. Regions are reachable via this list as well.
  G1CSetCandidateGroupList _optional_groups;

#ifdef ASSERT
  enum class CSetBuildType {
    Active,             // We are actively building the collection set
    Inactive            // We are not actively building the collection set
  };

  CSetBuildType _inc_build_state;
#endif
  // Index into the _regions indicating the start of the current collection set increment.
  size_t _regions_inc_part_start;
  // Index into the _groups indicating the start of the current collection set increment.
  uint _groups_inc_part_start;

  G1CollectorState* collector_state() const;
  G1GCPhaseTimes* phase_times();

  void verify_young_cset_indices() const NOT_DEBUG_RETURN;

  void add_young_region_common(G1HeapRegion* hr);

  // Add the given old region to the current collection set.
  void add_old_region(G1HeapRegion* hr);

  void prepare_optional_group(G1CSetCandidateGroup* gr, uint cur_index);

  void add_group_to_collection_set(G1CSetCandidateGroup* gr);

  void add_region_to_collection_set(G1HeapRegion* r);

  double select_candidates_from_marking(double time_remaining_ms);

  void select_candidates_from_retained(double time_remaining_ms);

  // Select groups for evacuation from the optional candidates given the remaining time
  // and return the number of actually selected regions.
  uint select_optional_groups(double time_remaining_ms);
  double select_candidates_from_optional_groups(double time_remaining_ms, uint& num_groups_selected);

  // Finalize the young part of the initial collection set. Relabel survivor regions
  // as Eden and calculate a prediction on how long the evacuation of all young regions
  // will take. Returns the time remaining from the given target pause time.
  double finalize_young_part(double target_pause_time_ms, G1SurvivorRegions* survivors);

  // Select the regions comprising the initial and optional collection set from marking
  // and retained collection set candidates.
  void finalize_old_part(double time_remaining_ms);

  // Iterate the part of the collection set given by the offset and length applying the given
  // G1HeapRegionClosure. The worker_id will determine where in the part to start the iteration
  // to allow for more efficient parallel iteration.
  void iterate_part_from(G1HeapRegionClosure* cl,
                         G1HeapRegionClaimer* hr_claimer,
                         size_t offset,
                         size_t length,
                         uint worker_id) const;
public:
  G1CollectionSet(G1CollectedHeap* g1h, G1Policy* policy);
  ~G1CollectionSet();

  // Initializes the collection set giving the maximum possible length of the collection set.
  void initialize(uint max_region_length);

  // Drop all collection set candidates (only the candidates).
  void abandon_all_candidates();

  G1CollectionSetCandidates* candidates() { return &_candidates; }
  const G1CollectionSetCandidates* candidates() const { return &_candidates; }

  G1CSetCandidateGroupList* groups() { return &_groups; }
  const G1CSetCandidateGroupList* groups() const { return &_groups; }

  void prepare_for_scan();

  void init_region_lengths(uint eden_cset_region_length,
                           uint survivor_cset_region_length);

  // Total length of the initial collection set in regions.
  uint initial_region_length() const { return young_region_length() +
                                              initial_old_region_length(); }
  uint young_region_length() const { return eden_region_length() +
                                            survivor_region_length(); }

  uint eden_region_length() const { return _eden_region_length; }
  uint survivor_region_length() const { return _survivor_region_length; }
  uint initial_old_region_length() const { return _initial_old_region_length; }
  uint num_optional_regions() const { return _optional_groups.num_regions(); }

  bool only_contains_young_regions() const { return (initial_old_region_length() + num_optional_regions()) == 0; }

  template <class CardOrRangeVisitor>
  inline void merge_cardsets_for_collection_groups(CardOrRangeVisitor& cl, uint worker_id, uint num_workers);

  // Reset the contents of the collection set.
  void clear();

  // Incremental collection set support

  // Initialize incremental collection set info.
  void start_incremental_building();
  // Start a new collection set increment, continuing the incremental building.
  void continue_incremental_building();
  // Stop adding regions to the current collection set increment.
  void stop_incremental_building();

  // Iterate over the current collection set increment applying the given G1HeapRegionClosure
  // from a starting position determined by the given worker id.
  void iterate_incremental_part_from(G1HeapRegionClosure* cl, G1HeapRegionClaimer* hr_claimer, uint worker_id) const;

  // Returns the length of the current increment in number of regions.
  size_t regions_cur_length() const { return _regions_cur_length - _regions_inc_part_start; }
  // Returns the length of the whole current collection set in number of regions
  size_t cur_length() const { return _regions_cur_length; }

  uint groups_increment_length() const;

  // Iterate over the entire collection set (all increments calculated so far), applying
  // the given G1HeapRegionClosure on all of the regions.
  void iterate(G1HeapRegionClosure* cl) const;
  void par_iterate(G1HeapRegionClosure* cl,
                   G1HeapRegionClaimer* hr_claimer,
                   uint worker_id) const;

  void iterate_optional(G1HeapRegionClosure* cl) const;

  // Finalize the initial collection set consisting of all young regions and potentially a
  // few old gen regions.
  void finalize_initial_collection_set(double target_pause_time_ms, G1SurvivorRegions* survivor);
  // Finalize the next collection set from the set of available optional old gen regions.
  // Returns whether there still were some optional regions.
  bool finalize_optional_for_evacuation(double remaining_pause_time);
  // Abandon (clean up) optional collection set regions that were not evacuated in this
  // pause.
  void abandon_optional_collection_set(G1ParScanThreadStateSet* pss);

  // Add eden region to the collection set.
  void add_eden_region(G1HeapRegion* hr);

  // Add survivor region to the collection set.
  void add_survivor_regions(G1HeapRegion* hr);

#ifndef PRODUCT
  bool verify_young_ages();

  void print(outputStream* st);
#endif // !PRODUCT
};

#endif // SHARE_GC_G1_G1COLLECTIONSET_HPP
