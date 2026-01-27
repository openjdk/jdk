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
public:
  explicit ShenandoahYoungHeuristics(ShenandoahYoungGeneration* generation);


  size_t choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                               RegionData* data, size_t size,
                                               size_t actual_free) override;

  bool should_start_gc() override;

  size_t bytes_of_allocation_runway_before_gc_trigger(size_t young_regions_to_be_reclaimed);

private:
  void choose_young_collection_set(ShenandoahCollectionSet* cset,
                                   const RegionData* data,
                                   size_t size, size_t actual_free,
                                   size_t cur_young_garbage) const;

};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHYOUNGHEURISTICS_HPP
