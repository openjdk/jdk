/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahOldHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahYoungHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"

#include "utilities/quickSort.hpp"

ShenandoahYoungHeuristics::ShenandoahYoungHeuristics(ShenandoahYoungGeneration* generation)
    : ShenandoahGenerationalHeuristics(generation),
      _young_live_words_not_in_most_recent_cset(0),
      _old_live_words_not_in_most_recent_cset(0),
      _remset_words_in_most_recent_mark_scan(0),
      _young_live_words_after_most_recent_mark(0),
      _young_words_most_recently_evacuated(0),
      _old_words_most_recently_evacuated(0),
      _words_most_recently_promoted(0),
      _regions_most_recently_promoted_in_place(0),
      _live_words_most_recently_promoted_in_place(0),
      _anticipated_pip_words(0) {
}


void ShenandoahYoungHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                      RegionData* data, size_t size,
                                                                      size_t actual_free) {
  // See comments in ShenandoahAdaptiveHeuristics::choose_collection_set_from_regiondata():
  // we do the same here, but with the following adjustments for generational mode:
  //
  // In generational mode, the sort order within the data array is not strictly descending amounts
  // of garbage. In particular, regions that have reached tenure age will be sorted into this
  // array before younger regions that typically contain more garbage. This is one reason why,
  // for example, we continue examining regions even after rejecting a region that has
  // more live data than we can evacuate.

  // Better select garbage-first regions
  QuickSort::sort<RegionData>(data, (int) size, compare_by_garbage);

  size_t cur_young_garbage = add_preselected_regions_to_collection_set(cset, data, size);

  choose_young_collection_set(cset, data, size, actual_free, cur_young_garbage);
  
  size_t young_words_evacuated = cset->get_young_bytes_reserved_for_evacuation() / HeapWordSize;
  size_t old_words_evacuated = cset->get_old_bytes_reserved_for_evacuation() / HeapWordSize;
  set_young_words_most_recently_evacuated(young_words_evacuated);
  set_old_words_most_recently_evacuated(old_words_evacuated);
  
  // This memory will be updated in young
  size_t young_live_at_mark = get_young_live_words_after_most_recent_mark();
  size_t young_live_not_in_cset = young_live_at_mark - young_words_evacuated;
  set_young_live_words_not_in_most_recent_cset(young_live_not_in_cset);

  ShenandoahOldGeneration* old_gen = ShenandoahGenerationalHeap::heap()->old_generation();
  if (cset->has_old_regions()) {
    // This is a mixed collection.  We will need to update all of the old live that is not in the cset.
    // Treat all old-gen memory that was not placed into the mixed-candidates as live. Some of this will eventually
    // be coalesced and filled, but it is all going to be "updated". Consider any promotions following most recent
    // old mark to be "live" (now known to be dead, so must be updated). Note that there have not been any promotions
    // yet during this cycle, as we are just beginning to evacuate.
    size_t old_gen_used = old_gen->used() / HeapWordSize;
    size_t mixed_candidates_known_garbage = old_gen->unprocessed_collection_candidates_garbage() / HeapWordSize;
    size_t old_live_in_cset = cset->get_old_bytes_reserved_for_evacuation();
    size_t old_garbage_in_cset = cset->get_old_garbage();
    size_t old_live_not_in_cset = old_gen_used - (old_garbage_in_cset + old_live_in_cset + mixed_candidates_known_garbage);
    set_old_live_words_not_in_most_recent_cset(old_live_not_in_cset);
  }

  if (old_gen->has_in_place_promotions()) {
    size_t pip_words = old_gen->get_expected_in_place_promotable_live_words();
    set_live_words_most_recently_promoted_in_place(pip_words);
  } else {
    set_live_words_most_recently_promoted_in_place(0);
  }

  log_cset_composition(cset);
}

void ShenandoahYoungHeuristics::choose_young_collection_set(ShenandoahCollectionSet* cset,
                                                            const RegionData* data,
                                                            size_t size, size_t actual_free,
                                                            size_t cur_young_garbage) const {

  auto heap = ShenandoahGenerationalHeap::heap();

  size_t capacity = heap->young_generation()->max_capacity();
  size_t garbage_threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahGarbageThreshold / 100;
  size_t ignore_threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahIgnoreGarbageThreshold / 100;
  const uint tenuring_threshold = heap->age_census()->tenuring_threshold();

  // This is young-gen collection or a mixed evacuation.
  // If this is mixed evacuation, the old-gen candidate regions have already been added.
  size_t max_cset = (size_t) (heap->young_generation()->get_evacuation_reserve() / ShenandoahEvacWaste);
  size_t cur_cset = 0;
  size_t free_target = (capacity * ShenandoahMinFreeThreshold) / 100 + max_cset;
  size_t min_garbage = (free_target > actual_free) ? (free_target - actual_free) : 0;


  log_info(gc, ergo)(
          "Adaptive CSet Selection for YOUNG. Max Evacuation: %zu%s, Actual Free: %zu%s.",
          byte_size_in_proper_unit(max_cset), proper_unit_for_byte_size(max_cset),
          byte_size_in_proper_unit(actual_free), proper_unit_for_byte_size(actual_free));

  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx].get_region();
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
  auto heap = ShenandoahGenerationalHeap::heap();
  ShenandoahOldGeneration* old_generation = heap->old_generation();
  ShenandoahOldHeuristics* old_heuristics = old_generation->heuristics();

  // Checks that an old cycle has run for at least ShenandoahMinimumOldTimeMs before allowing a young cycle.
  if (ShenandoahMinimumOldTimeMs > 0) {
    if (old_generation->is_preparing_for_mark() || old_generation->is_concurrent_mark_in_progress()) {
      size_t old_time_elapsed = size_t(old_heuristics->elapsed_cycle_time() * 1000);
      if (old_time_elapsed < ShenandoahMinimumOldTimeMs) {
        // Do not decline_trigger() when waiting for minimum quantum of Old-gen marking.  It is not at our discretion
        // to trigger at this time.
        return false;
      }
    }
  }

  // inherited triggers have already decided to start a cycle, so no further evaluation is required
  if (ShenandoahAdaptiveHeuristics::should_start_gc()) {
    return true;
  }

  // Check if allocation headroom is still okay. This also factors in:
  //   1. Some space to absorb allocation spikes (ShenandoahAllocSpikeFactor)
  //   2. Accumulated penalties from Degenerated and Full GC
  size_t capacity = _space_info->soft_max_capacity();
  size_t available = _space_info->soft_available();
  size_t allocation_headroom = available;

  size_t spike_headroom = capacity / 100 * ShenandoahAllocSpikeFactor;
  size_t penalties      = capacity / 100 * _gc_time_penalties;

  allocation_headroom -= MIN2(allocation_headroom, spike_headroom);
  allocation_headroom -= MIN2(allocation_headroom, penalties);

  // The predicted gc time accounts for reality that mixed cycles and cycles that promote heavily typicaly require more
  // than the average GC cycle time.
  double calculated_gc_time = predict_gc_time();
  double avg_alloc_rate = _allocation_rate.upper_bound(_margin_of_error_sd);

  log_debug(gc)("calculated GC time: %.2f ms, allocation rate: %.0f %s/s",
		calculated_gc_time * 1000, byte_size_in_proper_unit(avg_alloc_rate), proper_unit_for_byte_size(avg_alloc_rate));
  if (calculated_gc_time * avg_alloc_rate > allocation_headroom) {
    log_trigger("Calculated GC time (%.2f ms) is above the time for average allocation rate (%.0f %sB/s)"
                " to deplete free headroom (%zu%s) (margin of error = %.2f)",
		calculated_gc_time * 1000,
                byte_size_in_proper_unit(avg_alloc_rate), proper_unit_for_byte_size(avg_alloc_rate),
                byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom),
                _margin_of_error_sd);
    log_info(gc, ergo)("Free headroom: %zu%s (free) - %zu%s (spike) - %zu%s (penalties) = %zu%s",
                       byte_size_in_proper_unit(available),           proper_unit_for_byte_size(available),
                       byte_size_in_proper_unit(spike_headroom),      proper_unit_for_byte_size(spike_headroom),
                       byte_size_in_proper_unit(penalties),           proper_unit_for_byte_size(penalties),
                       byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom));
    log_info(gc, ergo)("Anticipated mark words: %zu, evac words: %zu, update words: %zu",
		       get_anticipated_mark_words(), get_anticipated_evac_words(), get_anticipated_update_words());
    accept_trigger_with_type(RATE);
    return true;
  }

#ifdef KELVIN_ORIGINAL_EXPEDITE_PROMO
  // Get through promotions and mixed evacuations as quickly as possible.  These cycles sometimes require significantly
  // more time than traditional young-generation cycles so start them up as soon as possible.  This is a "mitigation"
  // for the reality that old-gen and young-gen activities are not truly "concurrent".  If there is old-gen work to
  // be done, we start up the young-gen GC threads so they can do some of this old-gen work.  As implemented, promotion
  // gets priority over old-gen marking.
  size_t promo_expedite_threshold = percent_of(heap->young_generation()->max_capacity(), ShenandoahExpeditePromotionsThreshold);
  size_t promo_potential = old_generation->get_promotion_potential();
  if (promo_potential > promo_expedite_threshold) {
    // Detect unsigned arithmetic underflow
    assert(promo_potential < heap->capacity(), "Sanity");
    log_trigger("Expedite promotion of " PROPERFMT, PROPERFMTARGS(promo_potential));
    accept_trigger();
    return true;
  }
#endif

#ifdef KELVIN_ORIGINAL_EXPEDITE_MIXED
  size_t mixed_candidates = old_heuristics->unprocessed_old_collection_candidates();
  if (mixed_candidates > ShenandoahExpediteMixedThreshold && !heap->is_concurrent_weak_root_in_progress()) {
    // We need to run young GC in order to open up some free heap regions so we can finish mixed evacuations.
    // If concurrent weak root processing is in progress, it means the old cycle has chosen mixed collection
    // candidates, but has not completed. There is no point in trying to start the young cycle before the old
    // cycle completes.
    log_trigger("Expedite mixed evacuation of %zu regions", mixed_candidates);
    accept_trigger();
    return true;
  }
#endif

  // Don't decline_trigger() here  That was done in ShenandoahAdaptiveHeuristics::should_start_gc()
  return false;
}

// Return a conservative estimate of how much memory can be allocated before we need to start GC. The estimate is based
// on memory that is currently available within young generation plus all of the memory that will be added to the young
// generation at the end of the current cycle (as represented by young_regions_to_be_reclaimed) and on the anticipated
// amount of time required to perform a GC.
size_t ShenandoahYoungHeuristics::bytes_of_allocation_runway_before_gc_trigger(size_t young_regions_to_be_reclaimed) {
  size_t capacity = _space_info->max_capacity();
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

void ShenandoahYoungHeuristics::adjust_old_evac_ratio(size_t old_cset_regions, size_t young_cset_regions,
						      ShenandoahOldGeneration* old_gen, ShenandoahYoungGeneration* young_gen,
						      size_t promo_potential_words, size_t pip_potential_words,
						      size_t mixed_candidate_live_words, size_t mixed_candidate_garbage_words) {

#undef KELVIN_DEBUG
  if ((mixed_candidate_live_words == 0) && (promo_potential_words == 0)) {
    // No need for any reserve in old.  Return with simple solution.
    set_anticipated_mark_words(0);
    ShenandoahOldEvacRatioPercent = 0;
#ifdef KELVIN_DEBUG
    log_info(gc)("Not adjusting old evac ratio because no demand for old");
#endif
    return;
  }

  const size_t region_size_words = ShenandoahHeapRegion::region_size_words();
  const size_t old_available_words = old_gen->available() / HeapWordSize + old_cset_regions * region_size_words;
  const size_t young_available_words = young_gen->available() / HeapWordSize + young_cset_regions * region_size_words;

  size_t intended_young_reserve_words = (young_gen->max_capacity() * ShenandoahEvacReserve) / (100 * HeapWordSize);
  if (intended_young_reserve_words > young_available_words) {
    intended_young_reserve_words = young_available_words;
  }

  // Note that allocation_runway must be large enough to support allocations that happen concurrently with the next GC.
  size_t allocation_runway_words = young_available_words - intended_young_reserve_words;

  double avg_cycle_time = _gc_cycle_time_history->davg() + (_margin_of_error_sd * _gc_cycle_time_history->dsd());
  double avg_alloc_rate = _allocation_rate.upper_bound(_margin_of_error_sd) / HeapWordSize;
  size_t minimum_runway_words = (size_t) (avg_cycle_time * avg_alloc_rate);

  size_t proposed_young_evac_budget = get_young_words_most_recently_evacuated();
  size_t proposed_young_evac_reserve = (size_t) (proposed_young_evac_budget / ShenandoahEvacWaste);
  if (proposed_young_evac_reserve > intended_young_reserve_words) {
    proposed_young_evac_reserve = intended_young_reserve_words;
    proposed_young_evac_budget = (size_t) (proposed_young_evac_reserve * ShenandoahEvacWaste);
  }
  if (intended_young_reserve_words < proposed_young_evac_reserve) {
    proposed_young_evac_reserve = intended_young_reserve_words;
  }
  size_t excess_reserves = intended_young_reserve_words - proposed_young_evac_reserve;
  size_t proposed_promo_evac_reserve = (size_t) (promo_potential_words / ShenandoahPromoEvacWaste);

#ifdef KELVIN_DEBUG
  log_info(gc)("Searching for EvacRatio, young_evac_words: %zu, promo_evac_reserve: %zu, mixed_candidate_live_words: %zu",
	       proposed_young_evac_reserve, proposed_promo_evac_reserve, mixed_candidate_live_words);
  log_info(gc)(" allocation_runway: %zu, excess_reserves: %zu", allocation_runway_words, excess_reserves);
#endif

  if (mixed_candidate_live_words == 0) {
    // There are no mixed-evacuation candidates, but we may desire to set aside memory in old to receive promotions
    size_t anticipated_mark_words = get_young_live_words_after_most_recent_mark();
    size_t anticipated_evac_words = proposed_young_evac_budget;
    size_t old_2b_updated = get_remset_words_in_most_recent_mark_scan();
    size_t young_2b_updated = get_young_live_words_not_in_most_recent_cset();
    size_t anticipated_updated_words = old_2b_updated + young_2b_updated;

    size_t proposed_promo_reserve = (size_t) (promo_potential_words / ShenandoahPromoEvacWaste);
    size_t total_available_reserve = intended_young_reserve_words + allocation_runway_words;

    set_anticipated_mark_words(anticipated_mark_words);
    set_anticipated_evac_words(anticipated_evac_words);
    set_anticipated_pip_words(pip_potential_words);
    set_anticipated_update_words(anticipated_updated_words);
    double anticipated_gc_time = predict_gc_time();
    size_t consumed_words_during_gc = (size_t) (anticipated_gc_time * avg_alloc_rate);

    if (consumed_words_during_gc + proposed_promo_reserve + proposed_young_evac_budget <
	intended_young_reserve_words + allocation_runway_words) {
      size_t proposed_total_reserve = proposed_promo_reserve + proposed_young_evac_budget;
      ShenandoahOldEvacRatioPercent = (100 * proposed_promo_reserve) / proposed_total_reserve;
      if (ShenandoahOldEvacRatioPercent > 100) {
	// Observations confirm that much of the proposed promotion reserve (50% or more) is likely to become garbage before
	// the start of subsequent GC marking.  By limiting the old evac ratio, we allow more mutator allocations to occur
	// while GC is idle, ultimately improving throughput.
	ShenandoahOldEvacRatioPercent = 100;
      }
      log_info(gc)("Adjusting ShenandoahOldEvacRatioPercent to %zu to support promotion of up to %zu bytes",
		   ShenandoahOldEvacRatioPercent, promo_potential_words * HeapWordSize);
      return;
    } else {
      ShenandoahOldEvacRatioPercent = 0;
#ifdef KELVIN_DEBUG
      log_info(gc)("Abandoning promotion because consumed_words_during_gc: %zu, proposed_promo_reserve: %zu, "
		   "proposed_young_evac_budget: %zu, intended_young_reserve: %zu, allocation_runway_words: %zu",
		   consumed_words_during_gc, proposed_promo_reserve, proposed_young_evac_budget,
		   intended_young_reserve_words, allocation_runway_words);
#endif
      log_info(gc)("Adjusting ShenandoahOldEvacRatioPercent to 0, deferring promotion of %zu bytes",
		   promo_potential_words * HeapWordSize);
      return;
    }
  }
#ifdef KELVIN_DEBUG
  log_info(gc)("Did not take the short-circuit path because non-zero mixed_candidate_live_words is %zu",
	       mixed_candidate_live_words);
#endif

  for (uint planned_mixed_collection_count = 1; planned_mixed_collection_count <= 16; planned_mixed_collection_count *= 2) {
    assert(mixed_candidate_live_words > 0, "This loop is for mixed evacuations only");

    // Compute the mixed GC cycle time based on proposed configuration
    size_t proposed_old_evac_budget = mixed_candidate_live_words / planned_mixed_collection_count;
    size_t proposed_old_garbage = mixed_candidate_garbage_words / planned_mixed_collection_count;
    size_t proposed_old_evac_reserve = (size_t) (proposed_old_evac_budget / ShenandoahOldEvacWaste);

    // During mixed evacs, prioritize mixed evacuation over promotions.  Assume we budget mainly for mixed evacuation.
    // Promotion happens only if there is extra available memory within the old-gen regions.
    size_t proposed_total_reserve = proposed_young_evac_reserve + proposed_old_evac_reserve;
    if (proposed_total_reserve + minimum_runway_words <= intended_young_reserve_words + allocation_runway_words) {

      // TODO: Note that we are still "blind" to the possible increase of effort required for a bootstrap old GC cycle.
      //   A bootstrap cycle uses the remembered set to mark young.  So this is the same effort as a normal young cycle.
      //   There is a small amount of extra work that is not accounted for here.  During root scanning, and during
      //   mark-through-ref, a normal young cycle will ignore references to old.  However, a bootstrap cycle must
      //   mark each referenced old object.  During the bootstrap cycle, we do not scan marked objects that reside
      //   in old-gen memory.  That is done during subsequent concurrent mark cycles.  Current implementation assumes
      //   the difference between mark times for normal and bootstrap GC cycles is negligible.
      size_t anticipated_mark_words = get_young_live_words_after_most_recent_mark();
      size_t anticipated_evac_words;
      size_t anticipated_update_words;

      // We hope to perform a mixed evacuation in this cycle.
      anticipated_evac_words = proposed_old_evac_budget + proposed_young_evac_budget;
      size_t old_2b_updated =
	old_gen->used_including_humongous_waste() - (proposed_old_evac_budget + proposed_old_garbage);
      size_t young_2b_updated = get_young_live_words_not_in_most_recent_cset();
      anticipated_update_words = old_2b_updated + young_2b_updated;

      set_anticipated_mark_words(anticipated_mark_words);
      set_anticipated_evac_words(anticipated_evac_words);
      set_anticipated_pip_words(pip_potential_words);
      set_anticipated_update_words(anticipated_update_words);
      double anticipated_gc_time = predict_gc_time();
      size_t consumed_words_during_gc = (size_t) (anticipated_gc_time * avg_alloc_rate);

      if (consumed_words_during_gc + proposed_old_evac_reserve + proposed_young_evac_budget <
	  intended_young_reserve_words + allocation_runway_words) {
	size_t proposed_total_reserve = proposed_old_evac_reserve + proposed_young_evac_budget;
	ShenandoahOldEvacRatioPercent = (100 * proposed_old_evac_reserve) / proposed_total_reserve;
	double adjustment = 1.0;
	if (ShenandoahOldEvacRatioPercent > 100) {
	  adjustment = (ShenandoahOldEvacRatioPercent + 99) / 100.0;
	  // Observations confirm that much of the proposed promotion reserve (50% or more) is likely to become garbage before
	  // the start of subsequent GC marking.  By limiting the old evac ratio, we allow more mutator allocations to occur
	  // while GC is idle, ultimately improving throughput.
	  ShenandoahOldEvacRatioPercent = 100;
	}
	uint approximate_mix_count = (uint) (planned_mixed_collection_count * adjustment);
	log_info(gc)("Setting OldEvacRatioPercent to %zu, planning to perform approximately %u more mixed evacuation(s)",
		     ShenandoahOldEvacRatioPercent, approximate_mix_count);
	return;
      }
    }
    // Try again with a less aggressive planned_mixed_collection_count
  }

  // Not enough available memory to make meaningful progress on mixed evacuations.  Focus on young for this cycle

  // TODO: When this happens, maybe we should shrink our list of candidates by 12.5% or so, improving the likelihood that
  // our next attempt to schedule mixed evacs will be successful. Note that the first regions in the set of candidates
  // generally provide the largest amount of reclaimed garbage.  If we prune the set of old candidate regions, we'll need
  // to make sure the regions expelled from this candidate set are coalesced and filled before we start another old-mark
  // effort.  If we do this, we'll have to mark old again pretty soon, but maybe this will allow more garbage to accumulate
  // in regions before the next old-mark runs, so the next time we visit these same candidate regions, we will be able to
  // reclaim their garbage with less total effort.

  log_info(gc)("Adjusting ShenandoahOldEvacRatioPercent to 0 under duress, deferring mixed evacuations");
  set_anticipated_mark_words(0);
  ShenandoahOldEvacRatioPercent = 0;
}

double ShenandoahYoungHeuristics::predict_gc_time() {
  size_t mark_words = get_anticipated_mark_words();
  if (mark_words == 0) {
    // Use other heuristics to trigger.
    return 0.0;
  }
  double mark_time = predict_mark_time(mark_words);
  double evac_time = predict_evac_time(get_anticipated_evac_words(), get_anticipated_pip_words());
  double update_time = predict_update_time(get_anticipated_update_words());
  double result = mark_time + evac_time + update_time;
#undef KELVIN_DEBUG
#ifdef KELVIN_DEBUG
  log_info(gc)("predicting gc time: %.3f from mark(%zu): %.3f, evac(%zu, %zu): %.3f, update(%zu): %.3f",
	       result, get_anticipated_mark_words(), mark_time, get_anticipated_evac_words(), get_anticipated_pip_words(),
	       evac_time, get_anticipated_update_words(), update_time);
#endif
  return result;
}


double ShenandoahYoungHeuristics::predict_evac_time(size_t anticipated_evac_words, size_t anticipated_pip_words) {
  return _phase_stats[ShenandoahMajorGCPhase::_evac].predict_at((double) (5 * anticipated_evac_words + anticipated_pip_words));
}

double ShenandoahYoungHeuristics::predict_final_roots_time(size_t anticipated_pip_words) {
  return _phase_stats[ShenandoahMajorGCPhase::_final_roots].predict_at((double) anticipated_pip_words);
}

uint ShenandoahYoungHeuristics::should_surge_phase(ShenandoahMajorGCPhase phase, double now) {
  _phase_stats[phase].set_most_recent_start_time(now);

  // If we're already surging within this cycle, do not reduce the surge level
  uint surge = _surge_level;
  size_t allocatable = ShenandoahHeap::heap()->free_set()->available();
  double time_to_finish_gc = 0.0;

#ifdef KELVIN_SURGE
  log_info(gc)("should_surge(), inherited surge_level %u, allocatable: %zu", surge, allocatable);
#endif

  if (_previous_cycle_max_surge_level > Min_Surge_Level) {
    // If we required more than minimal surge in previous cycle, continue with a small surge now.  Assume we're catching up.
    if (surge < Min_Surge_Level) {
      surge = Min_Surge_Level;
    }
  }

  size_t bytes_allocated = _space_info->bytes_allocated_since_gc_start();
  _phase_stats[phase].set_most_recent_bytes_allocated(bytes_allocated);
  double avg_alloc_rate = _allocation_rate.average_rate(_margin_of_error_sd);
  double alloc_rate = avg_alloc_rate;

#ifdef KELVIN_SURGE
  log_info(gc)(" bytes_allocated: %zu, avg_alloc_rate: %.3f MB/s, _margin_of_error_sd: %.3f",
	       bytes_allocated, alloc_rate / (1024 * 1024), _margin_of_error_sd);
#endif

  double predicted_gc_time = predict_gc_time();
  switch (phase) {
    case ShenandoahMajorGCPhase::_num_phases:
      assert(false, "Should not happen");
      break;
    case ShenandoahMajorGCPhase::_final_roots:
    {
      // May happen after _mark in case this is an abbreviated cycle
      time_to_finish_gc += predict_final_roots_time((double) _anticipated_pip_words);

      // final_roots is preceded by mark, no evac or update
      size_t allocated_since_last_sample = bytes_allocated;
      double time_since_last_sample = now - _phase_stats[_mark].get_most_recent_start_time();

      double alloc_rate_since_gc_start = allocated_since_last_sample / time_since_last_sample;
      if (alloc_rate_since_gc_start > alloc_rate) {
        alloc_rate = alloc_rate_since_gc_start;
#ifdef KELVIN_SURGE
        log_info(gc)(" increasing alloc rate to %.3f MB/s in final_roots: %zu / %.3f",
                     alloc_rate / (1024 * 1024), bytes_allocated, now - _phase_stats[_mark].get_most_recent_start_time());
#endif
      }
    }
    break;

    case ShenandoahMajorGCPhase::_mark:
    {
      time_to_finish_gc += predict_mark_time((double) get_anticipated_mark_words());
      // TODO: Use the larger of predict_gc_time(now) and avg_cycle_time if we integrate "accelerated triggers"
      double avg_cycle_time = _gc_cycle_time_history->davg() + (_margin_of_error_sd * _gc_cycle_time_history->dsd());
#ifdef KELVIN_SURGE
      log_info(gc)(" avg_cycle_time: %.3f, predicted_cycle_time: %.3f, time_to_finish_mark: %.3f",
                   avg_cycle_time, predicted_gc_time, time_to_finish_gc);
#endif
      if (avg_cycle_time > predicted_gc_time) {
        predicted_gc_time = avg_cycle_time;
      }
    }
    case ShenandoahMajorGCPhase::_evac:
    {
      if (phase == _evac) {
        size_t allocated_since_last_sample = bytes_allocated;
        double time_since_last_sample = now - _phase_stats[_mark].get_most_recent_start_time();
        double alloc_rate_since_gc_start = allocated_since_last_sample / time_since_last_sample;
        if (alloc_rate_since_gc_start > alloc_rate) {
          alloc_rate = alloc_rate_since_gc_start;
#ifdef KELVIN_SURGE
          log_info(gc)(" increasing alloc rate to %.3f MB/s in evac: %zu / %.3f",
                       alloc_rate / (1024 * 1024), bytes_allocated, now - _phase_stats[_mark].get_most_recent_start_time());
#endif
      	}
      }
      time_to_finish_gc += predict_evac_time(get_anticipated_evac_words(), get_anticipated_pip_words());
#ifdef KELVIN_SURGE
      log_info(gc)(" with evac, time_to_finish_gc: %.3f", time_to_finish_gc);
#endif
    }
    case ShenandoahMajorGCPhase::_update:
    {
      if (phase == _update) {
        size_t allocated_since_last_sample = bytes_allocated - _phase_stats[_evac].get_most_recent_bytes_allocated();
        double time_since_last_sample = now - _phase_stats[_evac].get_most_recent_start_time();
        double alloc_rate_since_evac_start = allocated_since_last_sample / time_since_last_sample;
        if (alloc_rate_since_evac_start > alloc_rate) {
          alloc_rate = alloc_rate_since_evac_start;
#ifdef KELVIN_SURGE
          log_info(gc)(" increasing alloc rate to %.3f MB/s in update since evac: %zu / %.3f",
                       alloc_rate / (1024 * 1024), bytes_allocated - _phase_stats[_evac].get_most_recent_bytes_allocated(),
                       (now - _phase_stats[_evac].get_most_recent_start_time()));
#endif
        }
        allocated_since_last_sample = bytes_allocated;
        time_since_last_sample = now - _phase_stats[_mark].get_most_recent_start_time();
        double alloc_rate_since_gc_start = allocated_since_last_sample / time_since_last_sample;
        if (alloc_rate_since_gc_start > alloc_rate) {
          alloc_rate = alloc_rate_since_gc_start;
#ifdef KELVIN_SURGE
          log_info(gc)(" increasing alloc rate to %.3f MB/s in update since mark: %zu / %.3f",
                       alloc_rate / (1024 * 1024), bytes_allocated, now - _phase_stats[_mark].get_most_recent_start_time());
#endif
        }
      }
      time_to_finish_gc += predict_update_time(get_anticipated_update_words());
#ifdef KELVIN_SURGE
      log_info(gc)(" with update, time_to_finish_gc: %.3f", time_to_finish_gc);
#endif
    }
  }

  if (surge == Max_Surge_Level) {
    // Even if surge is already max, we need to do the above to update _phase_stats.  But no need to do acceleration
    // computations if we're already at max surge level.
    return surge;
  }

  if (time_to_finish_gc < predicted_gc_time) {
    time_to_finish_gc = predicted_gc_time;
  }

  double avg_odds;
  if (allocatable == 0) {
    // Avoid divide by zero, and force high surge if we are out of memory
    avg_odds = 1000.0;
  } else {
    avg_odds = (alloc_rate * time_to_finish_gc) / allocatable;
  }

#ifdef KELVIN_NEEDS_WORK
  // we don't have acceleration history in this branch
  if ((now - _previous_allocation_timestamp) >= MINIMUM_ALLOC_RATE_SAMPLE_INTERVAL) {
    size_t words_allocated_since_last_sample = _freeset->get_mutator_allocations_since_previous_sample();
    double time_since_last_sample = now - _previous_allocation_timestamp;
    double instantaneous_rate_words_per_second = words_allocated_since_last_sample / time_since_last_sample;
    _previous_allocation_timestamp = now;

#ifdef KELVIN_SURGE
    log_info(gc)("should_surge_gc()?, time_to_finish_gc: %0.3f, words_allocated_since_last_sample: %zu, "
                 "instantaneous_rate: %0.3f MB/s",
                 time_to_finish_gc, words_allocated_since_last_sample,
                 (instantaneous_rate_words_per_second * HeapWordSize) / (1024 * 1024));
#endif
    add_rate_to_acceleration_history(now, instantaneous_rate_words_per_second);
  }
#endif
#ifdef KELVIN_SURGE
  log_info(gc)(" avg_odds: %.3f", avg_odds);
#endif

#ifdef KELVIN_NEEDS_WORK
  // we don't have accelerated_consumption in this branch
  double race_odds;
  if (_spike_acceleration_num_samples > 0) {
    double acceleration;
    double current_rate;
 
   size_t consumption_accelerated = accelerated_consumption(acceleration, current_rate,
                                                             avg_alloc_rate / HeapWordSize, time_to_finish_gc);
    consumption_accelerated *= HeapWordSize;
    double accelerated_odds;
    if (allocatable == 0) {
      // Avoid divide by zero, and force high surge if we are out of memory
      accelerated_odds = 1000.0;
    } else {
      accelerated_odds = ((double) consumption_accelerated) / allocatable;
    }
#ifdef KELVIN_SURGE
    log_info(gc)("should_surge() current rate: %.3f MB/s, acceleration: %.3f MB/s/s, "
                 "consumption_accelerated: %zu, allocatable: %zu, avg_odds: %.3f, accelerated_odds: %.3f",
                 (HeapWordSize * current_rate) / (1024 * 1024), (HeapWordSize * acceleration) / (1024 * 1024),
                 consumption_accelerated, allocatable, avg_odds, accelerated_odds);
#endif
    race_odds = MAX2(avg_odds, accelerated_odds);
  } else {
    race_odds = avg_odds;
  }
#endif

  uint candidate_surge = (avg_odds > 1.0)? (uint) ((avg_odds - 0.75) / 0.25): 0;
  if (candidate_surge > Max_Surge_Level) {
    candidate_surge = Max_Surge_Level;
  }
  if (ConcGCThreads * (1 + candidate_surge * 0.25) > ParallelGCThreads) {
    candidate_surge = (uint) (((((double) ParallelGCThreads) / ConcGCThreads) - 1.0) / 0.25);
  }
  if (candidate_surge > surge) {
    surge = candidate_surge;
  }

#ifdef KELVIN_SURGE
  const char* phase_name = major_phase_name(phase);
  log_info(gc)("ShouldSurge(%s), allocatable: %zu, alloc_rate: %.3f MB/s, time_to_finish_gc: %.3fs, race_odds: %.3f returns %u",
               phase_name, allocatable, alloc_rate / (1024 * 1024), time_to_finish_gc, race_odds, surge);
#endif
  _surge_level = surge;
  if ((phase == ShenandoahMajorGCPhase::_update) || (phase == ShenandoahMajorGCPhase::_final_roots)) {
    _previous_cycle_max_surge_level = surge;
  }
  return surge;
}

