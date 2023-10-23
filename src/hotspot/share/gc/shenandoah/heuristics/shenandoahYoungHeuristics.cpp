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

#include "precompiled.hpp"

#include "gc/shenandoah/heuristics/shenandoahOldHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahYoungHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"

#include "utilities/quickSort.hpp"

ShenandoahYoungHeuristics::ShenandoahYoungHeuristics(ShenandoahYoungGeneration* generation)
        : ShenandoahGenerationalHeuristics(generation) {
}


void ShenandoahYoungHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                      RegionData* data, size_t size,
                                                                      size_t actual_free) {
  // The logic for cset selection in adaptive is as follows:
  //
  //   1. We cannot get cset larger than available free space. Otherwise we guarantee OOME
  //      during evacuation, and thus guarantee full GC. In practice, we also want to let
  //      application to allocate something. This is why we limit CSet to some fraction of
  //      available space. In non-overloaded heap, max_cset would contain all plausible candidates
  //      over garbage threshold.
  //
  //   2. We should not get cset too low so that free threshold would not be met right
  //      after the cycle. Otherwise we get back-to-back cycles for no reason if heap is
  //      too fragmented. In non-overloaded non-fragmented heap min_garbage would be around zero.
  //
  // Therefore, we start by sorting the regions by garbage. Then we unconditionally add the best candidates
  // before we meet min_garbage. Then we add all candidates that fit with a garbage threshold before
  // we hit max_cset. When max_cset is hit, we terminate the cset selection. Note that in this scheme,
  // ShenandoahGarbageThreshold is the soft threshold which would be ignored until min_garbage is hit.

  // In generational mode, the sort order within the data array is not strictly descending amounts of garbage.  In
  // particular, regions that have reached tenure age will be sorted into this array before younger regions that contain
  // more garbage.  This represents one of the reasons why we keep looking at regions even after we decide, for example,
  // to exclude one of the regions because it might require evacuation of too much live data.

  // Better select garbage-first regions
  QuickSort::sort<RegionData>(data, (int) size, compare_by_garbage, false);

  size_t cur_young_garbage = add_preselected_regions_to_collection_set(cset, data, size);

  choose_young_collection_set(cset, data, size, actual_free, cur_young_garbage);

  log_cset_composition(cset);
}

void ShenandoahYoungHeuristics::choose_young_collection_set(ShenandoahCollectionSet* cset,
                                                            const RegionData* data,
                                                            size_t size, size_t actual_free,
                                                            size_t cur_young_garbage) const {

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  size_t capacity = heap->young_generation()->max_capacity();
  size_t garbage_threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahGarbageThreshold / 100;
  size_t ignore_threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahIgnoreGarbageThreshold / 100;
  const uint tenuring_threshold = heap->age_census()->tenuring_threshold();

  // This is young-gen collection or a mixed evacuation.
  // If this is mixed evacuation, the old-gen candidate regions have already been added.
  size_t max_cset = (size_t) (heap->get_young_evac_reserve() / ShenandoahEvacWaste);
  size_t cur_cset = 0;
  size_t free_target = (capacity * ShenandoahMinFreeThreshold) / 100 + max_cset;
  size_t min_garbage = (free_target > actual_free) ? (free_target - actual_free) : 0;


  log_info(gc, ergo)(
          "Adaptive CSet Selection for YOUNG. Max Evacuation: " SIZE_FORMAT "%s, Actual Free: " SIZE_FORMAT "%s.",
          byte_size_in_proper_unit(max_cset), proper_unit_for_byte_size(max_cset),
          byte_size_in_proper_unit(actual_free), proper_unit_for_byte_size(actual_free));

  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx]._region;
    if (cset->is_preselected(r->index())) {
      continue;
    }
    if (r->age() < tenuring_threshold) {
      size_t new_cset = cur_cset + r->get_live_data_bytes();
      size_t region_garbage = r->garbage();
      size_t new_garbage = cur_young_garbage + region_garbage;
      bool add_regardless = (region_garbage > ignore_threshold) && (new_garbage < min_garbage);
      assert(r->is_young(), "Only young candidates expected in the data array");
      if ((new_cset <= max_cset) && (add_regardless || (region_garbage > garbage_threshold))) {
        cur_cset = new_cset;
        cur_young_garbage = new_garbage;
        cset->add_region(r);
      }
    }
    // Note that we do not add aged regions if they were not pre-selected.  The reason they were not preselected
    // is because there is not sufficient room in old-gen to hold their to-be-promoted live objects or because
    // they are to be promoted in place.
  }
}


bool ShenandoahYoungHeuristics::should_start_gc() {
  // inherited triggers have already decided to start a cycle, so no further evaluation is required
  if (ShenandoahAdaptiveHeuristics::should_start_gc()) {
    return true;
  }

  // Get through promotions and mixed evacuations as quickly as possible.  These cycles sometimes require significantly
  // more time than traditional young-generation cycles so start them up as soon as possible.  This is a "mitigation"
  // for the reality that old-gen and young-gen activities are not truly "concurrent".  If there is old-gen work to
  // be done, we start up the young-gen GC threads so they can do some of this old-gen work.  As implemented, promotion
  // gets priority over old-gen marking.
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  size_t promo_expedite_threshold = percent_of(heap->young_generation()->max_capacity(), ShenandoahExpeditePromotionsThreshold);
  size_t promo_potential = heap->get_promotion_potential();
  if (promo_potential > promo_expedite_threshold) {
    // Detect unsigned arithmetic underflow
    assert(promo_potential < heap->capacity(), "Sanity");
    log_info(gc)("Trigger (%s): expedite promotion of " SIZE_FORMAT "%s",
                 _space_info->name(),
                 byte_size_in_proper_unit(promo_potential),
                 proper_unit_for_byte_size(promo_potential));
    return true;
  }

  ShenandoahOldHeuristics* old_heuristics = heap->old_heuristics();
  size_t mixed_candidates = old_heuristics->unprocessed_old_collection_candidates();
  if (mixed_candidates > ShenandoahExpediteMixedThreshold && !heap->is_concurrent_weak_root_in_progress()) {
    // We need to run young GC in order to open up some free heap regions so we can finish mixed evacuations.
    // If concurrent weak root processing is in progress, it means the old cycle has chosen mixed collection
    // candidates, but has not completed. There is no point in trying to start the young cycle before the old
    // cycle completes.
    log_info(gc)("Trigger (%s): expedite mixed evacuation of " SIZE_FORMAT " regions",
                 _space_info->name(), mixed_candidates);
    return true;
  }

  return false;
}

// Return a conservative estimate of how much memory can be allocated before we need to start GC. The estimate is based
// on memory that is currently available within young generation plus all of the memory that will be added to the young
// generation at the end of the current cycle (as represented by young_regions_to_be_reclaimed) and on the anticipated
// amount of time required to perform a GC.
size_t ShenandoahYoungHeuristics::bytes_of_allocation_runway_before_gc_trigger(size_t young_regions_to_be_reclaimed) {
  size_t capacity = _space_info->soft_max_capacity();
  size_t usage = _space_info->used();
  size_t available = (capacity > usage)? capacity - usage: 0;
  size_t allocated = _space_info->bytes_allocated_since_gc_start();

  size_t available_young_collected = ShenandoahHeap::heap()->collection_set()->get_young_available_bytes_collected();
  size_t anticipated_available =
          available + young_regions_to_be_reclaimed * ShenandoahHeapRegion::region_size_bytes() - available_young_collected;
  size_t spike_headroom = capacity * ShenandoahAllocSpikeFactor / 100;
  size_t penalties      = capacity * _gc_time_penalties / 100;

  double rate = _allocation_rate.sample(allocated);

  // At what value of available, would avg and spike triggers occur?
  //  if allocation_headroom < avg_cycle_time * avg_alloc_rate, then we experience avg trigger
  //  if allocation_headroom < avg_cycle_time * rate, then we experience spike trigger if is_spiking
  //
  // allocation_headroom =
  //     0, if penalties > available or if penalties + spike_headroom > available
  //     available - penalties - spike_headroom, otherwise
  //
  // so we trigger if available - penalties - spike_headroom < avg_cycle_time * avg_alloc_rate, which is to say
  //                  available < avg_cycle_time * avg_alloc_rate + penalties + spike_headroom
  //            or if available < penalties + spike_headroom
  //
  // since avg_cycle_time * avg_alloc_rate > 0, the first test is sufficient to test both conditions
  //
  // thus, evac_slack_avg is MIN2(0,  available - avg_cycle_time * avg_alloc_rate + penalties + spike_headroom)
  //
  // similarly, evac_slack_spiking is MIN2(0, available - avg_cycle_time * rate + penalties + spike_headroom)
  // but evac_slack_spiking is only relevant if is_spiking, as defined below.

  double avg_cycle_time = _gc_cycle_time_history->davg() + (_margin_of_error_sd * _gc_cycle_time_history->dsd());

  // TODO: Consider making conservative adjustments to avg_cycle_time, such as: (avg_cycle_time *= 2) in cases where
  // we expect a longer-than-normal GC duration.  This includes mixed evacuations, evacuation that perform promotion
  // including promotion in place, and OLD GC bootstrap cycles.  It has been observed that these cycles sometimes
  // require twice or more the duration of "normal" GC cycles.  We have experimented with this approach.  While it
  // does appear to reduce the frequency of degenerated cycles due to late triggers, it also has the effect of reducing
  // evacuation slack so that there is less memory available to be transferred to OLD.  The result is that we
  // throttle promotion and it takes too long to move old objects out of the young generation.

  double avg_alloc_rate = _allocation_rate.upper_bound(_margin_of_error_sd);
  size_t evac_slack_avg;
  if (anticipated_available > avg_cycle_time * avg_alloc_rate + penalties + spike_headroom) {
    evac_slack_avg = anticipated_available - (avg_cycle_time * avg_alloc_rate + penalties + spike_headroom);
  } else {
    // we have no slack because it's already time to trigger
    evac_slack_avg = 0;
  }

  bool is_spiking = _allocation_rate.is_spiking(rate, _spike_threshold_sd);
  size_t evac_slack_spiking;
  if (is_spiking) {
    if (anticipated_available > avg_cycle_time * rate + penalties + spike_headroom) {
      evac_slack_spiking = anticipated_available - (avg_cycle_time * rate + penalties + spike_headroom);
    } else {
      // we have no slack because it's already time to trigger
      evac_slack_spiking = 0;
    }
  } else {
    evac_slack_spiking = evac_slack_avg;
  }

  size_t threshold = min_free_threshold();
  size_t evac_min_threshold = (anticipated_available > threshold)? anticipated_available - threshold: 0;
  return MIN3(evac_slack_spiking, evac_slack_avg, evac_min_threshold);
}

