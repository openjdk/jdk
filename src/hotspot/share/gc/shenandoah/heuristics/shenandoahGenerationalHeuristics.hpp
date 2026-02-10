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

#ifndef SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHGENERATIONALHEURISTICS_HPP
#define SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHGENERATIONALHEURISTICS_HPP


#include "gc/shenandoah/heuristics/shenandoahAdaptiveHeuristics.hpp"

class ShenandoahGeneration;
class ShenandoahHeap;
class ShenandoahCollectionSet;
class RegionData;

/*
 * This class serves as the base class for heuristics used to trigger and
 * choose the collection sets for young and global collections. It leans
 * heavily on the existing functionality of ShenandoahAdaptiveHeuristics.
 *
 * It differs from the base class primarily in that choosing the collection
 * set is responsible for mixed collections and in-place promotions of tenured
 * regions.
 */
class ShenandoahGenerationalHeuristics : public ShenandoahAdaptiveHeuristics {

public:
  explicit ShenandoahGenerationalHeuristics(ShenandoahGeneration* generation);

  void choose_collection_set(ShenandoahCollectionSet* collection_set) override;

private:
  // Compute evacuation budgets prior to choosing collection set.
  void compute_evacuation_budgets(ShenandoahHeap* const heap);

  // Preselect for possible inclusion into the collection set exactly the most
  // garbage-dense regions, including those that satisfy criteria 1 & 2 below,
  // and whose live bytes will fit within old_available budget:
  // Criterion 1. region age >= tenuring threshold
  // Criterion 2. region garbage percentage > old garbage threshold
  //
  // Identifies regions eligible for promotion in place,
  // being those of at least tenuring_threshold age that have lower garbage
  // density.
  //
  // Updates promotion_potential and pad_for_promote_in_place fields
  // of the heap. Returns bytes of live object memory in the preselected
  // regions, which are marked in the preselected_regions() indicator
  // array of the heap's collection set, which should be initialized
  // to false.
  size_t select_aged_regions(const size_t old_promotion_reserve);

  // Filter and sort remaining regions before adding to collection set.
  void filter_regions(ShenandoahCollectionSet* collection_set);

  // Adjust evacuation budgets after choosing collection set.  The argument regions_to_xfer
  // represents regions to be transferred to old based on decisions made in top_off_collection_set()
  void adjust_evacuation_budgets(ShenandoahHeap* const heap,
                                 ShenandoahCollectionSet* const collection_set);

protected:
  ShenandoahGeneration* _generation;

  size_t _add_regions_to_old;

  size_t add_preselected_regions_to_collection_set(ShenandoahCollectionSet* cset,
                                                   const RegionData* data,
                                                   size_t size) const;
};


#endif //SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHGENERATIONALHEURISTICS_HPP

