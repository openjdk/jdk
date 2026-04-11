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
#ifndef SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHYOUNGHEURISTICS_HPP
#define SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHYOUNGHEURISTICS_HPP

#include "gc/shenandoah/heuristics/shenandoahGenerationalHeuristics.hpp"

class ShenandoahYoungGeneration;

/*
 * This is a specialization of the generational heuristic which chooses
 * young regions for evacuation. This heuristic also has additional triggers
 * designed to expedite mixed collections and promotions.
 */
class ShenandoahYoungHeuristics : public ShenandoahGenerationalHeuristics {
protected:
  // For the most recently completed GC (global, young, old), how many live words from the young generation were not included
  // in the collection set at time collection set was built.  This represents the amount of young memory that will need to be
  // updated.
  size_t _young_live_words_not_in_most_recent_cset;

  // For the most recently completed GC (global, young, old), how many live words from the old generation were not included
  // in the collection set at time collection set was built.  This represents the amount of old memory that will need to be
  // updated if the cset includes old regions.
  size_t _old_live_words_not_in_most_recent_cset;

  // How many words were scanned during mark?  (aka how many words were associated with DIRTY cards?)
  size_t _remset_words_in_most_recent_mark_scan;

  // How many live words were found in young generation by most recent marking effort?
  size_t _young_live_words_after_most_recent_mark;

  // How many young words were evacuated in the most recent evacuation effort?
  size_t _young_words_most_recently_evacuated;

  // How many old words were evacuated in the most recent evacuation effort?
  size_t _old_words_most_recently_evacuated;

  // How many words did we intend to promote from young by evacuation in the most recent young evacuation?
  size_t _words_most_recently_promoted;

  // How many regions were promoted in place during most recent young GC?
  size_t _regions_most_recently_promoted_in_place;

  // How many live words were promoted in place during most recent GC?
  size_t _live_words_most_recently_promoted_in_place;

  // How many words do we expect to promote-in-place in the next GC?
  // (aka how many live words in tenure-aged regions at end of most recently completed GC?)
  size_t _anticipated_pip_words;

public:
  explicit ShenandoahYoungHeuristics(ShenandoahYoungGeneration* generation);


  void choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                             RegionData* data, size_t size,
                                             size_t actual_free) override;

  bool should_start_gc() override;

  size_t bytes_of_allocation_runway_before_gc_trigger(size_t young_regions_to_be_reclaimed);

  inline void set_young_words_most_recently_evacuated(size_t words) {
    _young_words_most_recently_evacuated = words;
  }

  inline size_t get_young_words_most_recently_evacuated() {
    return _young_words_most_recently_evacuated;
  }

  inline void set_old_words_most_recently_evacuated(size_t words) {
    _old_words_most_recently_evacuated = words;
  }

  inline size_t get_old_words_most_recently_evacuated() {
    return _old_words_most_recently_evacuated;
  }

  inline void set_young_live_words_not_in_most_recent_cset(size_t words) {
    _young_live_words_not_in_most_recent_cset = words;
  }

  inline size_t get_young_live_words_not_in_most_recent_cset() {
    return _young_live_words_not_in_most_recent_cset;
  }

  inline void set_old_live_words_not_in_most_recent_cset(size_t words) {
    _old_live_words_not_in_most_recent_cset = words;
  }

  inline size_t get_old_live_words_not_in_most_recent_cset() {
    return _old_live_words_not_in_most_recent_cset;
  }

  inline void set_young_live_words_after_most_recent_mark(size_t words) {
    _young_live_words_after_most_recent_mark = words;
  }

  inline size_t get_young_live_words_after_most_recent_mark() {
    return _young_live_words_after_most_recent_mark;
  }

  inline void set_remset_words_in_most_recent_mark_scan(size_t words) {
    _remset_words_in_most_recent_mark_scan = words;
  }

  inline size_t get_remset_words_in_most_recent_mark_scan() {
    return _remset_words_in_most_recent_mark_scan;
  }

  inline void set_regions_most_recently_promoted_in_place(size_t regions) {
    _regions_most_recently_promoted_in_place = regions;
  }

  inline size_t get_regions_most_recently_promoted_in_place() {
    return _regions_most_recently_promoted_in_place;
  }

  inline void set_live_words_most_recently_promoted_in_place(size_t words) {
    _live_words_most_recently_promoted_in_place = words;
  }

  inline size_t get_live_words_most_recently_promoted_in_place() {
    return _live_words_most_recently_promoted_in_place;
  }

  double predict_evac_time(size_t anticipated_evac_words, size_t anticipated_pip_words) override;
  double predict_final_roots_time(size_t anticipated_pip_words) override;

  double predict_gc_time(size_t anticipated_mark_words) override;

  // Setting this value to zero denotes current GC cycle to be "traditional young", so average GC cycle tine or linear
  // prediction are preferred over phase-account prediction.
  inline void set_anticipated_mark_words(size_t words) {
    _anticipated_mark_words = words;
  }

  inline void set_anticipated_pip_words(size_t words) {
    _anticipated_pip_words = words;
  }

  inline size_t get_anticipated_pip_words() {
    return _anticipated_pip_words;
  }

  // We may eventually replace this function with adjust_old_evac_ratio
  void update_anticipated_after_completed_gc(size_t old_cset_regions, size_t young_cset_regions,
                                             ShenandoahOldGeneration* old_gen, ShenandoahYoungGeneration* young_gen,
                                             size_t promo_potential_words, size_t pip_potential_words,
                                             size_t mixed_candidate_live_words, size_t mixed_candidate_garbage_words);

private:
  void choose_young_collection_set(ShenandoahCollectionSet* cset,
                                   const RegionData* data,
                                   size_t size, size_t actual_free) const;

};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHYOUNGHEURISTICS_HPP
