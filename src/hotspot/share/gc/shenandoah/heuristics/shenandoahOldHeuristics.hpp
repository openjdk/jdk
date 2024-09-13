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

#ifndef SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHOLDHEURISTICS_HPP
#define SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHOLDHEURISTICS_HPP


#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"

class ShenandoahCollectionSet;
class ShenandoahHeapRegion;
class ShenandoahOldGeneration;

/*
 * This heuristic is responsible for choosing a set of candidates for inclusion
 * in mixed collections. These candidates are chosen when marking of the old
 * generation is complete. Note that this list of candidates may live through
 * several mixed collections.
 *
 * This heuristic is also responsible for triggering old collections. It has its
 * own collection of triggers to decide whether to start an old collection. It does
 * _not_ use any of the functionality from the adaptive heuristics for triggers.
 * It also does not use any of the functionality from the heuristics base classes
 * to choose the collection set. For these reasons, it does not extend from
 * ShenandoahGenerationalHeuristics.
 */
class ShenandoahOldHeuristics : public ShenandoahHeuristics {

private:

  static uint NOT_FOUND;

  ShenandoahGenerationalHeap* _heap;
  ShenandoahOldGeneration* _old_gen;

  // After final marking of the old generation, this heuristic will select
  // a set of candidate regions to be included in subsequent mixed collections.
  // The regions are sorted into a `_region_data` array (declared in base
  // class) in decreasing order of garbage. The heuristic will give priority
  // to regions containing more garbage.

  // The following members are used to keep track of which candidate regions
  // have yet to be added to a mixed collection. There is also some special
  // handling for pinned regions, described further below.

  // Pinned regions may not be included in the collection set. Any old regions
  // which were pinned at the time when old regions were added to the mixed
  // collection will have been skipped. These regions are still contain garbage,
  // so we want to include them at the start of the list of candidates for the
  // _next_ mixed collection cycle. This variable is the index of the _first_
  // old region which is pinned when the mixed collection set is formed.
  uint _first_pinned_candidate;

  // This is the index of the last region which is above the garbage threshold.
  // No regions after this will be considered for inclusion in a mixed collection
  // set.
  uint _last_old_collection_candidate;

  // This index points to the first candidate in line to be added to the mixed
  // collection set. It is updated as regions are added to the collection set.
  uint _next_old_collection_candidate;

  // This is the last index in the array of old regions which were active at
  // the end of old final mark.
  uint _last_old_region;

  // How much live data must be evacuated from within the unprocessed mixed evacuation candidates?
  size_t _live_bytes_in_unprocessed_candidates;

  // Keep a pointer to our generation that we can use without down casting a protected member from the base class.
  ShenandoahOldGeneration* _old_generation;

  // Flags are set when promotion failure is detected (by gc thread), and cleared when
  // old generation collection begins (by control thread).  Flags are set and cleared at safepoints.
  bool _cannot_expand_trigger;
  bool _fragmentation_trigger;
  bool _growth_trigger;

  // Motivation for a fragmentation_trigger
  double _fragmentation_density;
  size_t _fragmentation_first_old_region;
  size_t _fragmentation_last_old_region;

  // Compare by live is used to prioritize compaction of old-gen regions.  With old-gen compaction, the goal is
  // to tightly pack long-lived objects into available regions.  In most cases, there has not been an accumulation
  // of garbage within old-gen regions.  The more likely opportunity will be to combine multiple sparsely populated
  // old-gen regions which may have been promoted in place into a smaller number of densely packed old-gen regions.
  // This improves subsequent allocation efficiency and reduces the likelihood of allocation failure (including
  // humongous allocation failure) due to fragmentation of the available old-gen allocation pool
  static int compare_by_live(RegionData a, RegionData b);

  static int compare_by_index(RegionData a, RegionData b);

  // Set the fragmentation trigger if old-gen memory has become fragmented.
  void set_trigger_if_old_is_fragmented(size_t first_old_region, size_t last_old_region,
                                        size_t old_region_count, size_t num_regions);

  // Set the overgrowth trigger if old-gen memory has grown beyond a particular threshold.
  void set_trigger_if_old_is_overgrown();

 protected:
  void choose_collection_set_from_regiondata(ShenandoahCollectionSet* set, RegionData* data, size_t data_size, size_t free) override;

public:
  explicit ShenandoahOldHeuristics(ShenandoahOldGeneration* generation, ShenandoahGenerationalHeap* gen_heap);

  // Prepare for evacuation of old-gen regions by capturing the mark results of a recently completed concurrent mark pass.
  void prepare_for_old_collections();

  // Return true iff the collection set is primed with at least one old-gen region.
  bool prime_collection_set(ShenandoahCollectionSet* set);

  // How many old-collection candidates have not yet been processed?
  uint unprocessed_old_collection_candidates() const;

  // How much live memory must be evacuated from within old-collection candidates that have not yet been processed?
  size_t unprocessed_old_collection_candidates_live_memory() const;

  void set_unprocessed_old_collection_candidates_live_memory(size_t initial_live);

  void decrease_unprocessed_old_collection_candidates_live_memory(size_t evacuated_live);

  // How many old or hidden collection candidates have not yet been processed?
  uint last_old_collection_candidate_index() const;

  // Return the next old-collection candidate in order of decreasing amounts of garbage.  (We process most-garbage regions
  // first.)  This does not consume the candidate.  If the candidate is selected for inclusion in a collection set, then
  // the candidate is consumed by invoking consume_old_collection_candidate().
  ShenandoahHeapRegion* next_old_collection_candidate();

  // Adjust internal state to reflect that one fewer old-collection candidate remains to be processed.
  void consume_old_collection_candidate();

  // Fill in buffer with all the old-collection regions that were identified at the end of the most recent old-gen
  // mark to require their unmarked objects to be coalesced and filled.  The buffer array must have at least
  // last_old_region_index() entries, or memory may be corrupted when this function overwrites the
  // end of the array.
  unsigned int get_coalesce_and_fill_candidates(ShenandoahHeapRegion** buffer);

  // True if there are old regions that need to be filled.
  bool has_coalesce_and_fill_candidates() const { return coalesce_and_fill_candidates_count() > 0; }

  // Return the number of old regions that need to be filled.
  size_t coalesce_and_fill_candidates_count() const { return _last_old_region - _next_old_collection_candidate; }

  // If a GLOBAL gc occurs, it will collect the entire heap which invalidates any collection candidates being
  // held by this heuristic for supplying mixed collections.
  void abandon_collection_candidates();

  void trigger_cannot_expand() { _cannot_expand_trigger = true; };

  inline void get_fragmentation_trigger_reason_for_log_message(double &density, size_t &first_index, size_t &last_index) {
    density = _fragmentation_density;
    first_index = _fragmentation_first_old_region;
    last_index = _fragmentation_last_old_region;
  }

  void clear_triggers();

  // Check whether conditions merit the start of old GC.  Set appropriate trigger if so.
  void evaluate_triggers(size_t first_old_region, size_t last_old_region, size_t old_region_count, size_t num_regions);

  void record_cycle_end() override;

  bool should_start_gc() override;

  void record_success_concurrent() override;

  void record_success_degenerated() override;

  void record_success_full() override;

  const char* name() override;

  bool is_diagnostic() override;

  bool is_experimental() override;

private:
  void slide_pinned_regions_to_front();
  bool all_candidates_are_pinned();
};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHOLDHEURISTICS_HPP
