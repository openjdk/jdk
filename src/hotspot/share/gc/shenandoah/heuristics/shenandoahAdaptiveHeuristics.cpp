/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahAdaptiveHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "utilities/quickSort.hpp"

// These constants are used to adjust the margin of error for the moving
// average of the allocation rate and cycle time. The units are standard
// deviations.
const double ShenandoahAdaptiveHeuristics::FULL_PENALTY_SD = 0.2;
const double ShenandoahAdaptiveHeuristics::DEGENERATE_PENALTY_SD = 0.1;

// These are used to decide if we want to make any adjustments at all
// at the end of a successful concurrent cycle.
const double ShenandoahAdaptiveHeuristics::LOWEST_EXPECTED_AVAILABLE_AT_END = -0.5;
const double ShenandoahAdaptiveHeuristics::HIGHEST_EXPECTED_AVAILABLE_AT_END = 0.5;

// These values are the confidence interval expressed as standard deviations.
// At the minimum confidence level, there is a 25% chance that the true value of
// the estimate (average cycle time or allocation rate) is not more than
// MINIMUM_CONFIDENCE standard deviations away from our estimate. Similarly, the
// MAXIMUM_CONFIDENCE interval here means there is a one in a thousand chance
// that the true value of our estimate is outside the interval. These are used
// as bounds on the adjustments applied at the outcome of a GC cycle.
const double ShenandoahAdaptiveHeuristics::MINIMUM_CONFIDENCE = 0.319; // 25%
const double ShenandoahAdaptiveHeuristics::MAXIMUM_CONFIDENCE = 3.291; // 99.9%

const uint ShenandoahAdaptiveHeuristics::MINIMUM_RESIZE_INTERVAL = 10;

ShenandoahAdaptiveHeuristics::ShenandoahAdaptiveHeuristics(ShenandoahGeneration* generation) :
  ShenandoahHeuristics(generation),
  _margin_of_error_sd(ShenandoahAdaptiveInitialConfidence),
  _spike_threshold_sd(ShenandoahAdaptiveInitialSpikeThreshold),
  _last_trigger(OTHER),
  _available(Moving_Average_Samples, ShenandoahAdaptiveDecayFactor) { }

ShenandoahAdaptiveHeuristics::~ShenandoahAdaptiveHeuristics() {}

void ShenandoahAdaptiveHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                         RegionData* data, size_t size,
                                                                         size_t actual_free) {
  size_t garbage_threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahGarbageThreshold / 100;
  size_t ignore_threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahIgnoreGarbageThreshold / 100;
  ShenandoahHeap* heap = ShenandoahHeap::heap();

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
  bool is_generational = heap->mode()->is_generational();
  bool is_global = (_generation->generation_mode() == GLOBAL);
  size_t capacity = heap->young_generation()->max_capacity();

  // cur_young_garbage represents the amount of memory to be reclaimed from young-gen.  In the case that live objects
  // are known to be promoted out of young-gen, we count this as cur_young_garbage because this memory is reclaimed
  // from young-gen and becomes available to serve future young-gen allocation requests.
  size_t cur_young_garbage = 0;

  // Better select garbage-first regions
  QuickSort::sort<RegionData>(data, (int)size, compare_by_garbage, false);

  if (is_generational) {
    if (is_global) {
      size_t max_young_cset    = (size_t) (heap->get_young_evac_reserve() / ShenandoahEvacWaste);
      size_t young_cur_cset = 0;
      size_t max_old_cset    = (size_t) (heap->get_old_evac_reserve() / ShenandoahEvacWaste);
      size_t old_cur_cset = 0;
      size_t free_target = (capacity * ShenandoahMinFreeThreshold) / 100 + max_young_cset;
      size_t min_garbage = (free_target > actual_free) ? (free_target - actual_free) : 0;

      log_info(gc, ergo)("Adaptive CSet Selection for GLOBAL. Max Young Evacuation: " SIZE_FORMAT
                         "%s, Max Old Evacuation: " SIZE_FORMAT "%s, Actual Free: " SIZE_FORMAT "%s.",
                         byte_size_in_proper_unit(max_young_cset),    proper_unit_for_byte_size(max_young_cset),
                         byte_size_in_proper_unit(max_old_cset),    proper_unit_for_byte_size(max_old_cset),
                         byte_size_in_proper_unit(actual_free), proper_unit_for_byte_size(actual_free));

      for (size_t idx = 0; idx < size; idx++) {
        ShenandoahHeapRegion* r = data[idx]._region;
        bool add_region = false;
        if (r->is_old()) {
          size_t new_cset = old_cur_cset + r->get_live_data_bytes();
          if ((new_cset <= max_old_cset) && (r->garbage() > garbage_threshold)) {
            add_region = true;
            old_cur_cset = new_cset;
          }
        } else if (cset->is_preselected(r->index())) {
          assert(r->age() >= InitialTenuringThreshold, "Preselected regions must have tenure age");
          // Entire region will be promoted, This region does not impact young-gen or old-gen evacuation reserve.
          // This region has been pre-selected and its impact on promotion reserve is already accounted for.
          add_region = true;
          // r->used() is r->garbage() + r->get_live_data_bytes()
          // Since all live data in this region is being evacuated from young-gen, it is as if this memory
          // is garbage insofar as young-gen is concerned.  Counting this as garbage reduces the need to
          // reclaim highly utilized young-gen regions just for the sake of finding min_garbage to reclaim
          // within youn-gen memory.
          cur_young_garbage += r->used();
        } else if (r->age() < InitialTenuringThreshold) {
          size_t new_cset = young_cur_cset + r->get_live_data_bytes();
          size_t region_garbage = r->garbage();
          size_t new_garbage = cur_young_garbage + region_garbage;
          bool add_regardless = (region_garbage > ignore_threshold) && (new_garbage < min_garbage);
          if ((new_cset <= max_young_cset) && (add_regardless || (region_garbage > garbage_threshold))) {
            add_region = true;
            young_cur_cset = new_cset;
            cur_young_garbage = new_garbage;
          }
        }
        // Note that we do not add aged regions if they were not pre-selected.  The reason they were not preselected
        // is because there is not sufficient room in old-gen to hold their to-be-promoted live objects.

        if (add_region) {
          cset->add_region(r);
        }
      }
    } else {
      // This is young-gen collection or a mixed evacuation.  If this is mixed evacuation, the old-gen candidate regions
      // have already been added.
      size_t max_cset    = (size_t) (heap->get_young_evac_reserve() / ShenandoahEvacWaste);
      size_t cur_cset = 0;
      size_t free_target = (capacity * ShenandoahMinFreeThreshold) / 100 + max_cset;
      size_t min_garbage = (free_target > actual_free) ? (free_target - actual_free) : 0;

      log_info(gc, ergo)("Adaptive CSet Selection for YOUNG. Max Evacuation: " SIZE_FORMAT "%s, Actual Free: " SIZE_FORMAT "%s.",
                         byte_size_in_proper_unit(max_cset),    proper_unit_for_byte_size(max_cset),
                         byte_size_in_proper_unit(actual_free), proper_unit_for_byte_size(actual_free));

      for (size_t idx = 0; idx < size; idx++) {
        ShenandoahHeapRegion* r = data[idx]._region;
        bool add_region = false;

        if (!r->is_old()) {
          if (cset->is_preselected(r->index())) {
            assert(r->age() >= InitialTenuringThreshold, "Preselected regions must have tenure age");
            // Entire region will be promoted, This region does not impact young-gen evacuation reserve.  Memory has already
            // been set aside to hold evacuation results as advance_promotion_reserve.
            add_region = true;
            // Since all live data in this region is being evacuated from young-gen, it is as if this memory
            // is garbage insofar as young-gen is concerned.  Counting this as garbage reduces the need to
            // reclaim highly utilized young-gen regions just for the sake of finding min_garbage to reclaim
            // within youn-gen memory
            cur_young_garbage += r->get_live_data_bytes();
          } else if  (r->age() < InitialTenuringThreshold) {
            size_t new_cset = cur_cset + r->get_live_data_bytes();
            size_t region_garbage = r->garbage();
            size_t new_garbage = cur_young_garbage + region_garbage;
            bool add_regardless = (region_garbage > ignore_threshold) && (new_garbage < min_garbage);
            if ((new_cset <= max_cset) && (add_regardless || (region_garbage > garbage_threshold))) {
              add_region = true;
              cur_cset = new_cset;
              cur_young_garbage = new_garbage;
            }
          }
          // Note that we do not add aged regions if they were not pre-selected.  The reason they were not preselected
          // is because there is not sufficient room in old-gen to hold their to-be-promoted live objects.

          if (add_region) {
            cset->add_region(r);
          }
        }
      }
    }
  } else {
    // Traditional Shenandoah (non-generational)
    size_t capacity    = ShenandoahHeap::heap()->soft_max_capacity();
    size_t max_cset    = (size_t)((1.0 * capacity / 100 * ShenandoahEvacReserve) / ShenandoahEvacWaste);
    size_t free_target = (capacity * ShenandoahMinFreeThreshold) / 100 + max_cset;
    size_t min_garbage = (free_target > actual_free) ? (free_target - actual_free) : 0;

    log_info(gc, ergo)("Adaptive CSet Selection. Target Free: " SIZE_FORMAT "%s, Actual Free: "
                     SIZE_FORMAT "%s, Max Evacuation: " SIZE_FORMAT "%s, Min Garbage: " SIZE_FORMAT "%s",
                     byte_size_in_proper_unit(free_target), proper_unit_for_byte_size(free_target),
                     byte_size_in_proper_unit(actual_free), proper_unit_for_byte_size(actual_free),
                     byte_size_in_proper_unit(max_cset),    proper_unit_for_byte_size(max_cset),
                     byte_size_in_proper_unit(min_garbage), proper_unit_for_byte_size(min_garbage));

    size_t cur_cset = 0;
    size_t cur_garbage = 0;

    for (size_t idx = 0; idx < size; idx++) {
      ShenandoahHeapRegion* r = data[idx]._region;

      size_t new_cset    = cur_cset + r->get_live_data_bytes();
      size_t new_garbage = cur_garbage + r->garbage();

      if (new_cset > max_cset) {
        break;
      }

      if ((new_garbage < min_garbage) || (r->garbage() > garbage_threshold)) {
        cset->add_region(r);
        cur_cset = new_cset;
        cur_garbage = new_garbage;
      }
    }
  }
}

void ShenandoahAdaptiveHeuristics::record_cycle_start() {
  ShenandoahHeuristics::record_cycle_start();
  _allocation_rate.allocation_counter_reset();
  ++_cycles_since_last_resize;
}

void ShenandoahAdaptiveHeuristics::record_success_concurrent(bool abbreviated) {
  ShenandoahHeuristics::record_success_concurrent(abbreviated);

  size_t available = MIN2(_generation->available(), ShenandoahHeap::heap()->free_set()->available());

  double z_score = 0.0;
  double available_sd = _available.sd();
  if (available_sd > 0) {
    double available_avg = _available.avg();
    z_score = (double(available) - available_avg) / available_sd;
    log_debug(gc, ergo)("%s Available: " SIZE_FORMAT " %sB, z-score=%.3f. Average available: %.1f %sB +/- %.1f %sB.",
                        _generation->name(),
                        byte_size_in_proper_unit(available), proper_unit_for_byte_size(available), z_score,
                        byte_size_in_proper_unit(available_avg), proper_unit_for_byte_size(available_avg),
                        byte_size_in_proper_unit(available_sd), proper_unit_for_byte_size(available_sd));
  }

  _available.add(double(available));

  // In the case when a concurrent GC cycle completes successfully but with an
  // unusually small amount of available memory we will adjust our trigger
  // parameters so that they are more likely to initiate a new cycle.
  // Conversely, when a GC cycle results in an above average amount of available
  // memory, we will adjust the trigger parameters to be less likely to initiate
  // a GC cycle.
  //
  // The z-score we've computed is in no way statistically related to the
  // trigger parameters, but it has the nice property that worse z-scores for
  // available memory indicate making larger adjustments to the trigger
  // parameters. It also results in fewer adjustments as the application
  // stabilizes.
  //
  // In order to avoid making endless and likely unnecessary adjustments to the
  // trigger parameters, the change in available memory (with respect to the
  // average) at the end of a cycle must be beyond these threshold values.
  if (z_score < LOWEST_EXPECTED_AVAILABLE_AT_END ||
      z_score > HIGHEST_EXPECTED_AVAILABLE_AT_END) {
    // The sign is flipped because a negative z-score indicates that the
    // available memory at the end of the cycle is below average. Positive
    // adjustments make the triggers more sensitive (i.e., more likely to fire).
    // The z-score also gives us a measure of just how far below normal. This
    // property allows us to adjust the trigger parameters proportionally.
    //
    // The `100` here is used to attenuate the size of our adjustments. This
    // number was chosen empirically. It also means the adjustments at the end of
    // a concurrent cycle are an order of magnitude smaller than the adjustments
    // made for a degenerated or full GC cycle (which themselves were also
    // chosen empirically).
    adjust_last_trigger_parameters(z_score / -100);
  }
}

void ShenandoahAdaptiveHeuristics::record_success_degenerated() {
  ShenandoahHeuristics::record_success_degenerated();
  // Adjust both trigger's parameters in the case of a degenerated GC because
  // either of them should have triggered earlier to avoid this case.
  adjust_margin_of_error(DEGENERATE_PENALTY_SD);
  adjust_spike_threshold(DEGENERATE_PENALTY_SD);
}

void ShenandoahAdaptiveHeuristics::record_success_full() {
  ShenandoahHeuristics::record_success_full();
  // Adjust both trigger's parameters in the case of a full GC because
  // either of them should have triggered earlier to avoid this case.
  adjust_margin_of_error(FULL_PENALTY_SD);
  adjust_spike_threshold(FULL_PENALTY_SD);
}

static double saturate(double value, double min, double max) {
  return MAX2(MIN2(value, max), min);
}

bool ShenandoahAdaptiveHeuristics::should_start_gc() {
  size_t max_capacity = _generation->max_capacity();
  size_t capacity = _generation->soft_max_capacity();
  size_t available = _generation->available();
  size_t allocated = _generation->bytes_allocated_since_gc_start();

  log_debug(gc)("should_start_gc (%s)? available: " SIZE_FORMAT ", soft_max_capacity: " SIZE_FORMAT
                ", max_capacity: " SIZE_FORMAT ", allocated: " SIZE_FORMAT,
                _generation->name(), available, capacity, max_capacity, allocated);

  // The collector reserve may eat into what the mutator is allowed to use. Make sure we are looking
  // at what is available to the mutator when deciding whether to start a GC.
  size_t usable = ShenandoahHeap::heap()->free_set()->available();
  if (usable < available) {
    log_debug(gc)("Usable (" SIZE_FORMAT "%s) is less than available (" SIZE_FORMAT "%s)",
                  byte_size_in_proper_unit(usable), proper_unit_for_byte_size(usable),
                  byte_size_in_proper_unit(available), proper_unit_for_byte_size(available));
    available = usable;
  }

  // Allocation spikes are a characteristic of both the application ahd the JVM configuration.  On the JVM command line,
  // the application developer may want to supply a hint of the nature of spikes that are inherent in the application
  // workload, and this information would normally be independent of heap size (not a percentage thereof).  On the
  // other hand, some allocation spikes are correlated with JVM configuration.  For example, there are allocation
  // spikes at the starts of concurrent marking and evacuation to refresh all local allocation buffers.  The nature
  // of these spikes is determined by LAB min and max sizes and numbers of threads, but also on frequency of GC passes,
  // and on "periodic" behavior of these threads  If GC frequency is much higher than the periodic trigger for mutator
  // threads, then many of the mutator threads may be able to "sit out" of most GC passes.  Though the thread's stack
  // must be scanned, the thread does not need to refresh its LABs if it sits idle throughout the duration of the GC
  // pass.  The best prediction for this aspect of spikes in allocation patterns is probably recent past history.
  // TODO: and dive deeper into _gc_time_penalties as this may also need to be corrected

  // Check if allocation headroom is still okay. This also factors in:
  //   1. Some space to absorb allocation spikes (ShenandoahAllocSpikeFactor)
  //   2. Accumulated penalties from Degenerated and Full GC
  size_t allocation_headroom = available;
  size_t spike_headroom = capacity / 100 * ShenandoahAllocSpikeFactor;
  size_t penalties      = capacity / 100 * _gc_time_penalties;

  allocation_headroom -= MIN2(allocation_headroom, penalties);
  allocation_headroom -= MIN2(allocation_headroom, spike_headroom);

  // Track allocation rate even if we decide to start a cycle for other reasons.
  double rate = _allocation_rate.sample(allocated);
  _last_trigger = OTHER;

  size_t min_threshold = min_free_threshold();

  if (available < min_threshold) {
    log_info(gc)("Trigger (%s): Free (" SIZE_FORMAT "%s) is below minimum threshold (" SIZE_FORMAT "%s)",
                 _generation->name(),
                 byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom),
                 byte_size_in_proper_unit(min_threshold),       proper_unit_for_byte_size(min_threshold));
    return resize_and_evaluate();
  }

  // Check if we need to learn a bit about the application
  const size_t max_learn = ShenandoahLearningSteps;
  if (_gc_times_learned < max_learn) {
    size_t init_threshold = capacity / 100 * ShenandoahInitFreeThreshold;
    if (available < init_threshold) {
      log_info(gc)("Trigger (%s): Learning " SIZE_FORMAT " of " SIZE_FORMAT ". Free (" SIZE_FORMAT "%s) is below initial threshold (" SIZE_FORMAT "%s)",
                   _generation->name(), _gc_times_learned + 1, max_learn,
                   byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom),
                   byte_size_in_proper_unit(init_threshold),      proper_unit_for_byte_size(init_threshold));
      return true;
    }
  }

  //  Rationale:
  //    The idea is that there is an average allocation rate and there are occasional abnormal bursts (or spikes) of
  //    allocations that exceed the average allocation rate.  What do these spikes look like?
  //
  //    1. At certain phase changes, we may discard large amounts of data and replace it with large numbers of newly
  //       allocated objects.  This "spike" looks more like a phase change.  We were in steady state at M bytes/sec
  //       allocation rate and now we're in a "reinitialization phase" that looks like N bytes/sec.  We need the "spike"
  //       accomodation to give us enough runway to recalibrate our "average allocation rate".
  //
  //   2. The typical workload changes.  "Suddenly", our typical workload of N TPS increases to N+delta TPS.  This means
  //       our average allocation rate needs to be adjusted.  Once again, we need the "spike" accomodation to give us
  //       enough runway to recalibrate our "average allocation rate".
  //
  //    3. Though there is an "average" allocation rate, a given workload's demand for allocation may be very bursty.  We
  //       allocate a bunch of LABs during the 5 ms that follow completion of a GC, then we perform no more allocations for
  //       the next 150 ms.  It seems we want the "spike" to represent the maximum divergence from average within the
  //       period of time between consecutive evaluation of the should_start_gc() service.  Here's the thinking:
  //
  //       a) Between now and the next time I ask whether should_start_gc(), we might experience a spike representing
  //          the anticipated burst of allocations.  If that would put us over budget, then we should start GC immediately.
  //       b) Between now and the anticipated depletion of allocation pool, there may be two or more bursts of allocations.
  //          If there are more than one of these bursts, we can "approximate" that these will be separated by spans of
  //          time with very little or no allocations so the "average" allocation rate should be a suitable approximation
  //          of how this will behave.
  //
  //    For cases 1 and 2, we need to "quickly" recalibrate the average allocation rate whenever we detect a change
  //    in operation mode.  We want some way to decide that the average rate has changed.  Make average allocation rate
  //    computations an independent effort.


  // TODO: Account for inherent delays in responding to GC triggers
  //  1. It has been observed that delays of 200 ms or greater are common between the moment we return true from should_start_gc()
  //     and the moment at which we begin execution of the concurrent reset phase.  Add this time into the calculation of
  //     avg_cycle_time below.  (What is "this time"?  Perhaps we should remember recent history of this delay for the
  //     running workload and use the maximum delay recently seen for "this time".)
  //  2. The frequency of inquiries to should_start_gc() is adaptive, ranging between ShenandoahControlIntervalMin and
  //     ShenandoahControlIntervalMax.  The current control interval (or the max control interval) should also be added into
  //     the calculation of avg_cycle_time below.

  double avg_cycle_time = _gc_cycle_time_history->davg() + (_margin_of_error_sd * _gc_cycle_time_history->dsd());

  double avg_alloc_rate = _allocation_rate.upper_bound(_margin_of_error_sd);
  log_debug(gc)("%s: average GC time: %.2f ms, allocation rate: %.0f %s/s",
    _generation->name(), avg_cycle_time * 1000, byte_size_in_proper_unit(avg_alloc_rate), proper_unit_for_byte_size(avg_alloc_rate));

  if (avg_cycle_time > allocation_headroom / avg_alloc_rate) {

    log_info(gc)("Trigger (%s): Average GC time (%.2f ms) is above the time for average allocation rate (%.0f %sB/s) to deplete free headroom (" SIZE_FORMAT "%s) (margin of error = %.2f)",
                 _generation->name(), avg_cycle_time * 1000,
                 byte_size_in_proper_unit(avg_alloc_rate), proper_unit_for_byte_size(avg_alloc_rate),
                 byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom),
                 _margin_of_error_sd);

    log_info(gc, ergo)("Free headroom: " SIZE_FORMAT "%s (free) - " SIZE_FORMAT "%s (spike) - " SIZE_FORMAT "%s (penalties) = " SIZE_FORMAT "%s",
                       byte_size_in_proper_unit(available),           proper_unit_for_byte_size(available),
                       byte_size_in_proper_unit(spike_headroom),      proper_unit_for_byte_size(spike_headroom),
                       byte_size_in_proper_unit(penalties),           proper_unit_for_byte_size(penalties),
                       byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom));

    _last_trigger = RATE;
    return resize_and_evaluate();
  }

  bool is_spiking = _allocation_rate.is_spiking(rate, _spike_threshold_sd);
  if (is_spiking && avg_cycle_time > allocation_headroom / rate) {
    log_info(gc)("Trigger (%s): Average GC time (%.2f ms) is above the time for instantaneous allocation rate (%.0f %sB/s) to deplete free headroom (" SIZE_FORMAT "%s) (spike threshold = %.2f)",
                 _generation->name(), avg_cycle_time * 1000,
                 byte_size_in_proper_unit(rate), proper_unit_for_byte_size(rate),
                 byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom),

                 _spike_threshold_sd);
    _last_trigger = SPIKE;
    return resize_and_evaluate();
  }

  return ShenandoahHeuristics::should_start_gc();
}

bool ShenandoahAdaptiveHeuristics::resize_and_evaluate() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  if (!heap->mode()->is_generational()) {
    // We only attempt to resize the generations in generational mode.
    return true;
  }

  if (_cycles_since_last_resize <= MINIMUM_RESIZE_INTERVAL) {
    log_info(gc, ergo)("Not resizing %s for another " UINT32_FORMAT " cycles.",
        _generation->name(),  _cycles_since_last_resize);
    return true;
  }

  if (!heap->generation_sizer()->transfer_capacity(_generation)) {
    // We could not enlarge our generation, so we must start a gc cycle.
    log_info(gc, ergo)("Could not increase size of %s, begin gc cycle.", _generation->name());
    return true;
  }

  log_info(gc)("Increased size of %s generation, re-evaluate trigger criteria", _generation->name());
  return should_start_gc();
}

void ShenandoahAdaptiveHeuristics::adjust_last_trigger_parameters(double amount) {
  switch (_last_trigger) {
    case RATE:
      adjust_margin_of_error(amount);
      break;
    case SPIKE:
      adjust_spike_threshold(amount);
      break;
    case OTHER:
      // nothing to adjust here.
      break;
    default:
      ShouldNotReachHere();
  }
}

void ShenandoahAdaptiveHeuristics::adjust_margin_of_error(double amount) {
  _margin_of_error_sd = saturate(_margin_of_error_sd + amount, MINIMUM_CONFIDENCE, MAXIMUM_CONFIDENCE);
  log_debug(gc, ergo)("Margin of error now %.2f", _margin_of_error_sd);
}

void ShenandoahAdaptiveHeuristics::adjust_spike_threshold(double amount) {
  _spike_threshold_sd = saturate(_spike_threshold_sd - amount, MINIMUM_CONFIDENCE, MAXIMUM_CONFIDENCE);
  log_debug(gc, ergo)("Spike threshold now: %.2f", _spike_threshold_sd);
}

ShenandoahAllocationRate::ShenandoahAllocationRate() :
  _last_sample_time(os::elapsedTime()),
  _last_sample_value(0),
  _interval_sec(1.0 / ShenandoahAdaptiveSampleFrequencyHz),
  _rate(int(ShenandoahAdaptiveSampleSizeSeconds * ShenandoahAdaptiveSampleFrequencyHz), ShenandoahAdaptiveDecayFactor),
  _rate_avg(int(ShenandoahAdaptiveSampleSizeSeconds * ShenandoahAdaptiveSampleFrequencyHz), ShenandoahAdaptiveDecayFactor) {
}

double ShenandoahAllocationRate::sample(size_t allocated) {
  double now = os::elapsedTime();
  double rate = 0.0;
  if (now - _last_sample_time > _interval_sec) {
    if (allocated >= _last_sample_value) {
      rate = instantaneous_rate(now, allocated);
      _rate.add(rate);
      _rate_avg.add(_rate.avg());
    }

    _last_sample_time = now;
    _last_sample_value = allocated;
  }
  return rate;
}

double ShenandoahAllocationRate::upper_bound(double sds) const {
  // Here we are using the standard deviation of the computed running
  // average, rather than the standard deviation of the samples that went
  // into the moving average. This is a much more stable value and is tied
  // to the actual statistic in use (moving average over samples of averages).
  return _rate.davg() + (sds * _rate_avg.dsd());
}

void ShenandoahAllocationRate::allocation_counter_reset() {
  _last_sample_time = os::elapsedTime();
  _last_sample_value = 0;
}

bool ShenandoahAllocationRate::is_spiking(double rate, double threshold) const {
  if (rate <= 0.0) {
    return false;
  }

  double sd = _rate.sd();
  if (sd > 0) {
    // There is a small chance that that rate has already been sampled, but it
    // seems not to matter in practice.
    double z_score = (rate - _rate.avg()) / sd;
    if (z_score > threshold) {
      return true;
    }
  }
  return false;
}

double ShenandoahAllocationRate::instantaneous_rate(double time, size_t allocated) const {
  size_t last_value = _last_sample_value;
  double last_time = _last_sample_time;
  size_t allocation_delta = (allocated > last_value) ? (allocated - last_value) : 0;
  double time_delta_sec = time - last_time;
  return (time_delta_sec > 0)  ? (allocation_delta / time_delta_sec) : 0;
}
