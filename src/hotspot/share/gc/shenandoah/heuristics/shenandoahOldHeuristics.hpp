/*
 * Copyright (c) 2021, Amazon.com, Inc. or its affiliates. All rights reserved.
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

class ShenandoahCollectionSet;
class ShenandoahHeapRegion;
class ShenandoahOldGeneration;

class ShenandoahOldHeuristics : public ShenandoahHeuristics {

private:

  static uint NOT_FOUND;

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

  // This can be the 'static' or 'adaptive' heuristic.
  ShenandoahHeuristics* _trigger_heuristic;

  // Flag is set when promotion failure is detected (by gc thread), and cleared when
  // old generation collection begins (by control thread).
  volatile bool _promotion_failed;

  // Keep a pointer to our generation that we can use without down casting a protected member from the base class.
  ShenandoahOldGeneration* _old_generation;

 protected:
  virtual void choose_collection_set_from_regiondata(ShenandoahCollectionSet* set, RegionData* data, size_t data_size,
                                                     size_t free) override;

public:
  ShenandoahOldHeuristics(ShenandoahOldGeneration* generation, ShenandoahHeuristics* trigger_heuristic);

  virtual void choose_collection_set(ShenandoahCollectionSet* collection_set, ShenandoahOldHeuristics* old_heuristics) override;

  // Prepare for evacuation of old-gen regions by capturing the mark results of a recently completed concurrent mark pass.
  void prepare_for_old_collections();

  // Return true iff the collection set is primed with at least one old-gen region.
  bool prime_collection_set(ShenandoahCollectionSet* set);

  // How many old-collection candidates have not yet been processed?
  uint unprocessed_old_collection_candidates();

  // How many old or hidden collection candidates have not yet been processed?
  uint last_old_collection_candidate_index();

  // Return the next old-collection candidate in order of decreasing amounts of garbage.  (We process most-garbage regions
  // first.)  This does not consume the candidate.  If the candidate is selected for inclusion in a collection set, then
  // the candidate is consumed by invoking consume_old_collection_candidate().
  ShenandoahHeapRegion* next_old_collection_candidate();

  // Adjust internal state to reflect that one fewer old-collection candidate remains to be processed.
  void consume_old_collection_candidate();

  // How many old-collection regions were identified at the end of the most recent old-gen mark to require their
  // unmarked objects to be coalesced and filled?
  uint last_old_region_index() const;

  // Fill in buffer with all of the old-collection regions that were identified at the end of the most recent old-gen
  // mark to require their unmarked objects to be coalesced and filled.  The buffer array must have at least
  // last_old_region_index() entries, or memory may be corrupted when this function overwrites the
  // end of the array.
  unsigned int get_coalesce_and_fill_candidates(ShenandoahHeapRegion** buffer);

  // If a GLOBAL gc occurs, it will collect the entire heap which invalidates any collection candidates being
  // held by this heuristic for supplying mixed collections.
  void abandon_collection_candidates();

  // Notify the heuristic of promotion failures. The promotion attempt will be skipped and the object will
  // be evacuated into the young generation. The collection should complete normally, but we want to schedule
  // an old collection as soon as possible.
  void handle_promotion_failure();

  virtual void record_cycle_start() override;

  virtual void record_cycle_end() override;

  virtual bool should_start_gc() override;

  virtual bool should_degenerate_cycle() override;

  virtual void record_success_concurrent(bool abbreviated) override;

  virtual void record_success_degenerated() override;

  virtual void record_success_full() override;

  virtual void record_allocation_failure_gc() override;

  virtual void record_requested_gc() override;

  virtual void reset_gc_learning() override;

  virtual bool can_unload_classes() override;

  virtual bool can_unload_classes_normal() override;

  virtual bool should_unload_classes() override;

  virtual const char* name() override;

  virtual bool is_diagnostic() override;

  virtual bool is_experimental() override;

 private:
  void slide_pinned_regions_to_front();
};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHOLDHEURISTICS_HPP
