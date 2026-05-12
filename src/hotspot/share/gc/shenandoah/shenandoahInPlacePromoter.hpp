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

#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahSimpleBitMap.hpp"

class ShenandoahMarkingContext;
class ShenandoahGenerationalHeap;

// This class is responsible for identifying regions that can be
// promoted in place. It also prepares these regions by preventing
// them from being used for allocations. Finally, it notifies the
// freeset which regions are to be promoted in place.
class ShenandoahInPlacePromotionPlanner {
  using idx_t = ShenandoahSimpleBitMap::idx_t;

  // Used to inform free set of regions being promoted
  struct RegionPromotions {
    idx_t _low_idx;
    idx_t _high_idx;
    size_t _regions;
    size_t _bytes;
    ShenandoahFreeSet* _free_set;

    explicit RegionPromotions(ShenandoahFreeSet* free_set)
      : _low_idx(free_set->max_regions())
      , _high_idx(-1)
      , _regions(0)
      , _bytes(0)
      , _free_set(free_set)
    {
    }

    void increment(idx_t region_index, size_t remnant_bytes) {
      if (region_index < _low_idx) {
        _low_idx = region_index;
      }
      if (region_index > _high_idx) {
        _high_idx = region_index;
      }
      _regions++;
      _bytes += remnant_bytes;
    }

    void update_free_set(ShenandoahFreeSetPartitionId partition_id) const {
      if (_regions > 0) {
        _free_set->shrink_interval_if_range_modifies_either_boundary(partition_id, _low_idx, _high_idx, _regions);
      }
    }
  };

  // Used to track metrics about the regions being promoted in place
  struct RegionPromotionStats {
    size_t count;
    size_t usage;
    size_t free;
    size_t garbage;

    RegionPromotionStats() : count(0), usage(0), free(0), garbage(0) {}
    void update(ShenandoahHeapRegion* region) {
      count++;
      usage += region->used();
      free += region->free();
      garbage += region->garbage();
    }
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

  // Tracks stats for in place promotions
  RegionPromotionStats _pip_regular_stats;
  RegionPromotionStats _pip_humongous_stats;

public:
  explicit ShenandoahInPlacePromotionPlanner(const ShenandoahGenerationalHeap* heap);

  // Returns true if this region has garbage below and usage above the configurable thresholds
  bool is_eligible(const ShenandoahHeapRegion* region) const;

  // Prepares the region for promotion by moving top to the end to prevent allocations
  void prepare(ShenandoahHeapRegion* region);

  // Notifies the free set and old generation of in place promotions
  void complete_planning() const;

  const RegionPromotionStats& regular_region_stats() const   { return _pip_regular_stats; }
  const RegionPromotionStats& humongous_region_stats() const { return _pip_humongous_stats; }

  size_t old_garbage_threshold() const { return _old_garbage_threshold; }
};

// For regions that have been selected and prepared for promotion, this class
// will perform the actual promotion.
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

