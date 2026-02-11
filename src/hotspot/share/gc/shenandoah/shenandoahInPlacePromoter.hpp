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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHINPLACEPROMOTER_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHINPLACEPROMOTER_HPP

#include "gc/shenandoah/shenandoahSimpleBitMap.hpp"

class ShenandoahFreeSet;
class ShenandoahMarkingContext;
class ShenandoahGenerationalHeap;
class ShenandoahHeapRegion;

class ShenandoahInPlacePromotionPlanner {
  using idx_t = ShenandoahSimpleBitMap::idx_t;

  struct RegionPromotions {
    idx_t _low_idx;
    idx_t _high_idx;
    size_t _regions;
    size_t _bytes;
    ShenandoahFreeSet* _free_set;

    explicit RegionPromotions(ShenandoahFreeSet* free_set);
    void increment(idx_t region_index, size_t remnant_bytes);
    void update_free_set(ShenandoahFreeSetPartitionId partition_id) const;
  };

  const size_t _old_garbage_threshold;
  const size_t _pip_used_threshold;

  const ShenandoahGenerationalHeap* _heap;
  ShenandoahFreeSet* _free_set;
  const ShenandoahMarkingContext* _marking_context;

  // Any region that is to be promoted in place needs to be retired from its Collector or Mutator partition.
  RegionPromotions _mutator_regions;
  RegionPromotions _collector_regions;

  // Tracks the padding of space above top in regions eligible for promotion in place
  size_t _pip_padding_bytes;
public:
  explicit ShenandoahInPlacePromotionPlanner(const ShenandoahGenerationalHeap* heap);

  // Returns true if this region has garbage below and usage above the configurable thresholds
  bool is_eligible(const ShenandoahHeapRegion* region) const;

  // Prepares the region for promotion by moving top to the end to prevent allocations
  void prepare(ShenandoahHeapRegion* region);

  // Notifies the free set of in place promotions
  void update_free_set() const;

  size_t old_garbage_threshold() const { return _old_garbage_threshold; }
};

class ShenandoahInPlacePromoter {
  ShenandoahGenerationalHeap* _heap;
public:
  explicit ShenandoahInPlacePromoter(ShenandoahGenerationalHeap* heap) : _heap(heap) {}

  // If the region still meets the criteria for promotion in place, it will be promoted
  void maybe_promote_region(ShenandoahHeapRegion* region) const;

private:
  void promote(ShenandoahHeapRegion* region) const;
  void promote_humongous(ShenandoahHeapRegion* region) const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHINPLACEPROMOTER_HPP
