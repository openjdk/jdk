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

  size_t choose_collection_set(ShenandoahCollectionSet* collection_set) override;
protected:
  ShenandoahGeneration* _generation;

  size_t add_preselected_regions_to_collection_set(ShenandoahCollectionSet* cset,
                                                   const RegionData* data,
                                                   size_t size) const;
};


#endif //SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHGENERATIONALHEURISTICS_HPP

