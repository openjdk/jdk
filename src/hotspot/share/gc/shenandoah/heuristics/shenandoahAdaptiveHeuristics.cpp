/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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


#include "gc/shared/gcCause.hpp"
#include "gc/shenandoah/heuristics/shenandoahAdaptiveHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahSpaceInfo.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "runtime/globals.hpp"
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


// To enable detection of GC time trends, we keep separate track of the recent history of gc time.  During initialization,
// for example, the amount of live memory may be increasing, which is likely to cause the GC times to increase.  This history
// allows us to predict increasing GC times rather than always assuming average recent GC time is the best predictor.
const size_t ShenandoahAdaptiveHeuristics::GC_TIME_SAMPLE_SIZE = 3;

// We also keep separate track of recently sampled allocation rates for two purposes:
//  1. The number of samples examined to determine acceleration of allocation is represented by
//     ShenandoahRateAccelerationSampleSize
//  2. The number of most recent samples averaged to determine a momentary allocation spike is represented by
//     ShenandoahMomentaryAllocationRateSpikeSampleSize

// Allocation rates are sampled by the regulator thread, which typically runs every ms.  There may be jitter in the scheduling
// of the regulator thread.  To reduce signal noise and synchronization overhead, we do not sample allocation rate with every
// iteration of the regulator.  We prefer sample time longer than 1 ms so that there can be a statistically significant number
// of allocations occuring within each sample period.  The regulator thread samples allocation rate only if at least
// ShenandoahAccelerationSamplePeriod seconds have passed since it previously sampled the allocation rate.
//
// This trigger responds much more quickly than the traditional trigger, which monitors 100 ms spans.  When acceleration is
// detected, the impact of acceleration on anticipated consumption of available memory is also much more impactful
// than the assumed constant allocation rate consumption of available memory.

ShenandoahAdaptiveHeuristics::ShenandoahAdaptiveHeuristics(ShenandoahSpaceInfo* space_info) :
  ShenandoahHeuristics(space_info),
  _margin_of_error_sd(ShenandoahAdaptiveInitialConfidence),
  _spike_threshold_sd(ShenandoahAdaptiveInitialSpikeThreshold),
  _last_trigger(OTHER),
  _available(Moving_Average_Samples, ShenandoahAdaptiveDecayFactor),
  _free_set(nullptr),
  _is_generational(ShenandoahHeap::heap()->mode()->is_generational()),
  _regulator_thread(nullptr),
  _previous_allocation_timestamp(0.0),
  _gc_time_first_sample_index(0),
  _gc_time_num_samples(0),
  _gc_time_timestamps(NEW_C_HEAP_ARRAY(double, GC_TIME_SAMPLE_SIZE, mtGC)),
  _gc_time_samples(NEW_C_HEAP_ARRAY(double, GC_TIME_SAMPLE_SIZE, mtGC)),
  _gc_time_xy(NEW_C_HEAP_ARRAY(double, GC_TIME_SAMPLE_SIZE, mtGC)),
  _gc_time_xx(NEW_C_HEAP_ARRAY(double, GC_TIME_SAMPLE_SIZE, mtGC)),
  _gc_time_sum_of_timestamps(0),
  _gc_time_sum_of_samples(0),
  _gc_time_sum_of_xy(0),
  _gc_time_sum_of_xx(0),
  _gc_time_m(0.0),
  _gc_time_b(0.0),
  _gc_time_sd(0.0),
  _spike_acceleration_buffer_size(MAX2(ShenandoahRateAccelerationSampleSize, 1+ShenandoahMomentaryAllocationRateSpikeSampleSize)),
  _spike_acceleration_first_sample_index(0),
  _spike_acceleration_num_samples(0),
  _spike_acceleration_rate_samples(NEW_C_HEAP_ARRAY(double, _spike_acceleration_buffer_size, mtGC)),
  _spike_acceleration_rate_timestamps(NEW_C_HEAP_ARRAY(double, _spike_acceleration_buffer_size, mtGC)),
  _most_recent_headroom_at_start_of_idle((size_t) 0) {
  }

ShenandoahAdaptiveHeuristics::~ShenandoahAdaptiveHeuristics() {
  FREE_C_HEAP_ARRAY(double, _spike_acceleration_rate_samples);
  FREE_C_HEAP_ARRAY(double, _spike_acceleration_rate_timestamps);
  FREE_C_HEAP_ARRAY(double, _gc_time_timestamps);
  FREE_C_HEAP_ARRAY(double, _gc_time_samples);
  FREE_C_HEAP_ARRAY(double, _gc_time_xy);
  FREE_C_HEAP_ARRAY(double, _gc_time_xx);
}

void ShenandoahAdaptiveHeuristics::initialize() {
  ShenandoahHeuristics::initialize();
}

void ShenandoahAdaptiveHeuristics::post_initialize() {
  ShenandoahHeuristics::post_initialize();
  _free_set = ShenandoahHeap::heap()->free_set();
  assert(!_is_generational, "ShenandoahGenerationalHeuristics overrides this method");
  _control_thread = ShenandoahHeap::heap()->control_thread();
  size_t global_available = (ShenandoahHeap::heap()->global_generation()->max_capacity() -
                             (ShenandoahHeap::heap()->global_generation()->used() + _free_set->reserved()));
  recalculate_trigger_threshold(global_available);
}

void ShenandoahAdaptiveHeuristics::recalculate_trigger_threshold(size_t mutator_available) {
  // The trigger threshold represents mutator available - "head room".
  // We plan for GC to finish before the amount of allocated memory exceeds trigger threshold.  This is the same  as saying we
  // intend to finish GC before the amount of available memory is less than the allocation headroom.  Headroom is the planned
  // safety buffer to allow a small amount of additional allocation to take place in case we were overly optimistic in delaying
  // our trigger.
  size_t capacity = ShenandoahHeap::heap()->soft_max_capacity();
  size_t spike_headroom = capacity / 100 * ShenandoahAllocSpikeFactor;
  size_t penalties      = capacity / 100 * _gc_time_penalties;

  size_t bytes_allocated_at_start_of_idle_span = _free_set->get_bytes_allocated_since_gc_start();

  // make headroom adjustments
  _headroom_adjustment = spike_headroom + penalties;
  size_t adjusted_mutator_available;
  if (mutator_available >= _headroom_adjustment) {
    adjusted_mutator_available = mutator_available - _headroom_adjustment;
  } else {
    adjusted_mutator_available = 0;
  }

  assert(!_is_generational || !strcmp(_space_info->name(), "Young") || !strcmp(_space_info->name(), "Global"),
         "Assumed young or global space, but got: %s", _space_info->name());
  assert(_is_generational || !strcmp(_space_info->name(), ""), "Assumed global (unnamed) space, but got: %s", _space_info->name());
  log_info(gc)("At start or resumption of idle gc span for %s, mutator available adjusted to: " PROPERFMT
               " after adjusting for spike_headroom: " PROPERFMT " and penalties: " PROPERFMT,
               _is_generational? _space_info->name(): "Global",
               PROPERFMTARGS(adjusted_mutator_available), PROPERFMTARGS(spike_headroom), PROPERFMTARGS(penalties));

  _most_recent_headroom_at_start_of_idle = adjusted_mutator_available;
  // _trigger_threshold is expressed in words
  _trigger_threshold = (bytes_allocated_at_start_of_idle_span + adjusted_mutator_available) / HeapWordSize;
}

void ShenandoahAdaptiveHeuristics::start_idle_span() {
  size_t mutator_available = _free_set->available();
  recalculate_trigger_threshold(mutator_available);
}

void ShenandoahAdaptiveHeuristics::resume_idle_span() {
  size_t mutator_available = _free_set->available_holding_lock();
  recalculate_trigger_threshold(mutator_available);
}

// There is no headroom during evacuation and update refs.  This information is not used to trigger the next GC.
// In future implementations, this information may feed into worker surge calculations.
void ShenandoahAdaptiveHeuristics::start_evac_span() {
  size_t mutator_available = _free_set->available_holding_lock();
  _trigger_threshold = mutator_available;
}

void ShenandoahAdaptiveHeuristics::adjust_penalty(intx step) {
  ShenandoahHeuristics::adjust_penalty(step);
}

size_t ShenandoahAdaptiveHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                           RegionData* data, size_t size,
                                                                           size_t actual_free) {
  size_t garbage_threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahGarbageThreshold / 100;

  // The logic for cset selection in adaptive is as follows:
  //
  //   1. We cannot get cset larger than available free space. Otherwise we guarantee OOME
  //      during evacuation, and thus guarantee full GC. In practice, we also want to let the
  //      application allocate during concurrent GC. This is why we limit CSet to some fraction of
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

  size_t capacity    = ShenandoahHeap::heap()->soft_max_capacity();
  size_t max_cset    = (size_t)((1.0 * capacity / 100 * ShenandoahEvacReserve) / ShenandoahEvacWaste);
  size_t free_target = (capacity / 100 * ShenandoahMinFreeThreshold) + max_cset;
  size_t min_garbage = (free_target > actual_free ? (free_target - actual_free) : 0);

  log_info(gc, ergo)("Adaptive CSet Selection. Target Free: %zu%s, Actual Free: "
                     "%zu%s, Max Evacuation: %zu%s, Min Garbage: %zu%s",
                     byte_size_in_proper_unit(free_target), proper_unit_for_byte_size(free_target),
                     byte_size_in_proper_unit(actual_free), proper_unit_for_byte_size(actual_free),
                     byte_size_in_proper_unit(max_cset),    proper_unit_for_byte_size(max_cset),
                     byte_size_in_proper_unit(min_garbage), proper_unit_for_byte_size(min_garbage));

  // Better select garbage-first regions
  QuickSort::sort(data, size, compare_by_garbage);

  size_t cur_cset = 0;
  size_t cur_garbage = 0;

  // Regions are sorted in order of decreasing garbage
  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx].get_region();

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
  return 0;
}

void ShenandoahAdaptiveHeuristics::add_degenerated_gc_time(double timestamp, double gc_time) {
  // Conservatively add sample into linear model If this time is above the predicted concurrent gc time
  if (predict_gc_time(timestamp) < gc_time) {
    add_gc_time(timestamp, gc_time);
  }
}

void ShenandoahAdaptiveHeuristics::add_gc_time(double timestamp, double gc_time) {
  // Update best-fit linear predictor of GC time
  uint index = (_gc_time_first_sample_index + _gc_time_num_samples) % GC_TIME_SAMPLE_SIZE;
  if (_gc_time_num_samples == GC_TIME_SAMPLE_SIZE) {
    _gc_time_sum_of_timestamps -= _gc_time_timestamps[index];
    _gc_time_sum_of_samples -= _gc_time_samples[index];
    _gc_time_sum_of_xy -= _gc_time_xy[index];
    _gc_time_sum_of_xx -= _gc_time_xx[index];
  }
  _gc_time_timestamps[index] = timestamp;
  _gc_time_samples[index] = gc_time;
  _gc_time_xy[index] = timestamp * gc_time;
  _gc_time_xx[index] = timestamp * timestamp;

  _gc_time_sum_of_timestamps += _gc_time_timestamps[index];
  _gc_time_sum_of_samples += _gc_time_samples[index];
  _gc_time_sum_of_xy += _gc_time_xy[index];
  _gc_time_sum_of_xx += _gc_time_xx[index];

  if (_gc_time_num_samples < GC_TIME_SAMPLE_SIZE) {
    _gc_time_num_samples++;
  } else {
    _gc_time_first_sample_index = (_gc_time_first_sample_index + 1) % GC_TIME_SAMPLE_SIZE;
  }

  if (_gc_time_num_samples == 1) {
    // The predictor is constant (horizontal line)
    _gc_time_m = 0;
    _gc_time_b = gc_time;
    _gc_time_sd = 0.0;
  } else if (_gc_time_num_samples == 2) {
    // Two points define a line
    double delta_y = gc_time - _gc_time_samples[_gc_time_first_sample_index];
    double delta_x = timestamp - _gc_time_timestamps[_gc_time_first_sample_index];
    _gc_time_m = delta_y / delta_x;

    // y = mx + b
    // so b = y0 - mx0
    _gc_time_b = gc_time - _gc_time_m * timestamp;
    _gc_time_sd = 0.0;
  } else {
    _gc_time_m = ((_gc_time_num_samples * _gc_time_sum_of_xy - _gc_time_sum_of_timestamps * _gc_time_sum_of_samples) /
                  (_gc_time_num_samples * _gc_time_sum_of_xx - _gc_time_sum_of_timestamps * _gc_time_sum_of_timestamps));
    _gc_time_b = (_gc_time_sum_of_samples - _gc_time_m * _gc_time_sum_of_timestamps) / _gc_time_num_samples;
    double sum_of_squared_deviations = 0.0;
    for (size_t i = 0; i < _gc_time_num_samples; i++) {
      uint index = (_gc_time_first_sample_index + i) % GC_TIME_SAMPLE_SIZE;
      double x = _gc_time_timestamps[index];
      double predicted_y = _gc_time_m * x + _gc_time_b;
      double deviation = predicted_y - _gc_time_samples[index];
      sum_of_squared_deviations += deviation * deviation;
    }
    _gc_time_sd = sqrt(sum_of_squared_deviations / _gc_time_num_samples);
  }
}

double ShenandoahAdaptiveHeuristics::predict_gc_time(double timestamp_at_start) {
  return _gc_time_m * timestamp_at_start + _gc_time_b + _gc_time_sd * _margin_of_error_sd;;
}

void ShenandoahAdaptiveHeuristics::add_rate_to_acceleration_history(double timestamp, double rate) {
  uint new_sample_index =
    (_spike_acceleration_first_sample_index + _spike_acceleration_num_samples) % _spike_acceleration_buffer_size;
  _spike_acceleration_rate_timestamps[new_sample_index] = timestamp;
  _spike_acceleration_rate_samples[new_sample_index] = rate;
  if (_spike_acceleration_num_samples == _spike_acceleration_buffer_size) {
    _spike_acceleration_first_sample_index++;
    if (_spike_acceleration_first_sample_index == _spike_acceleration_buffer_size) {
      _spike_acceleration_first_sample_index = 0;
    }
  } else {
    _spike_acceleration_num_samples++;
  }
}

void ShenandoahAdaptiveHeuristics::record_cycle_start() {
  ShenandoahHeuristics::record_cycle_start();
  _allocation_rate.allocation_counter_reset();
}

void ShenandoahAdaptiveHeuristics::record_success_concurrent() {
  ShenandoahHeuristics::record_success_concurrent();
  double now = os::elapsedTime();

  // Should we not add GC time if this was an abbreviated cycle?
  add_gc_time(_cycle_start, elapsed_cycle_time());

  size_t available = _space_info->available();

  double z_score = 0.0;
  double available_sd = _available.sd();
  if (available_sd > 0) {
    double available_avg = _available.avg();
    z_score = (double(available) - available_avg) / available_sd;
    log_debug(gc, ergo)("Available: %zu %sB, z-score=%.3f. Average available: %.1f %sB +/- %.1f %sB.",
                        byte_size_in_proper_unit(available), proper_unit_for_byte_size(available),
                        z_score,
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

void ShenandoahAdaptiveHeuristics::record_degenerated() {
  ShenandoahHeuristics::record_degenerated();
  add_degenerated_gc_time(_precursor_cycle_start, elapsed_degenerated_cycle_time());
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

#define KELVIN_START_GC
#ifdef KELVIN_START_GC
const size_t MaxRejectedTriggers = 256;
typedef struct gc_start_info {
  double time_stamp;
  size_t capacity;
  size_t available;
  size_t allocated;
  size_t min_threshold;
  size_t learned_steps;
  double avg_alloc_rate;
  size_t allocatable_words;
  double avg_cycle_time;
  double predicted_future_accelerated_gc_time;
  size_t allocated_bytes_since_last_sample;
  double instantaneous_rate_words_per_second;
  double current_rate_by_acceleration;;
  size_t consumption_accelerated;
  double acceleration;
  double predicted_future_gc_time;
  double future_planned_gc_time;
  double avg_time_to_deplete_available;
  bool is_spiking;
  double rate;
  double spike_time_to_deplete_available;
} TriggerInfo;

// Most recently logged data represents the accepted trigger
static void dumpTriggerInfo(size_t first_trigger, size_t rejected_triggers, TriggerInfo* trigger_log) {
  static const char* const header[] = {
    "\n",
    "                                                  Min          Learned        Allocatable               Predicted             Spike       Current                Acceleration  Planned         Is            Spike",
    "  TimeStamp             Available    Allocated    Threshold    Steps          (bytes)                   Accelerated           Alloc       Rate by                (MB/s^2)      GC      Avg     Spiking       Time",
    "  |       Capacity      (Bytes)      (Bytes)      (Bytes)      |     Avg       |              Avg       GC     Allocated      Rate        Accel      Accelerated |      Future Time    Time    |   Rate      to",
    "  |       (Bytes)       |            |            |            |     Alloc     |              Cycle     Time   Since          (MB/s)      (MB/s)     Consumption |      GC     (s)     to      |   (MB/s)    Deplete",
    "  |       |             |            |            |            |     Rate      |              Time      (s)    Last           |           |          (bytes)     |      Time   |       Deplete |   |         Available",
    "  |       |             |            |            |            |     (MB/s)    |              (s)       |      Sample         |           |          |           |      (s)    |       Avail   |   |         (s)",
    "  |       |             |            |            |            |     |         |              |         |      (bytes)        |           |          |           |      |      |       (s)     |   |         |",
    "  |       |             |            |            |            |     |         |              |         |      |              |           |          |           |      |      |       |       |   |         |",
    "  v       v             v            v            v            v     v         v              v         v      v              v           v          v           v      v      v       v       v   v         v"
  };
  for (unsigned int i = 0; i < sizeof(header) / sizeof(void*); i++) {
    log_info(gc)("%s", header[i]);
  }
  for (unsigned int i = 0; i < rejected_triggers; i++) {
    size_t __index = (first_trigger + i) % MaxRejectedTriggers;
    log_info(gc)("%8.3f %12zu %12zu %12zu %12zu %4zu "
                 "%9.3f %12zu %8.3f %8.3f %12zu %9.3f %9.3f %12zu %9.3f %8.3f %8.3f %8.3f "
                 "%3s %8.3f %8.3f",
                 trigger_log[__index].time_stamp,
                 trigger_log[__index].capacity,
                 trigger_log[__index].available,
                 trigger_log[__index].allocated,
                 trigger_log[__index].min_threshold,
                 trigger_log[__index].learned_steps,
                 trigger_log[__index].avg_alloc_rate / (1024*1024),
                 trigger_log[__index].allocatable_words * HeapWordSize,
                 trigger_log[__index].avg_cycle_time,
                 trigger_log[__index].predicted_future_accelerated_gc_time,
                 trigger_log[__index].allocated_bytes_since_last_sample,
                 (trigger_log[__index].instantaneous_rate_words_per_second * HeapWordSize) / (1024*1024),
                 (trigger_log[__index].current_rate_by_acceleration * HeapWordSize) / (1024*1024),
                 trigger_log[__index].consumption_accelerated * HeapWordSize,
                 (trigger_log[__index].acceleration * HeapWordSize) / (1024*1024),
                 trigger_log[__index].predicted_future_gc_time,
                 trigger_log[__index].future_planned_gc_time,
                 trigger_log[__index].avg_time_to_deplete_available,
                 trigger_log[__index].is_spiking? "yes": "no",
                 trigger_log[__index].rate / (1024*1024),
                 trigger_log[__index].spike_time_to_deplete_available);
  }
}
#endif


//  Rationale:
//    The idea is that there is an average allocation rate and there are occasional abnormal bursts (or spikes) of
//    allocations that exceed the average allocation rate.  What do these spikes look like?
//
//    1. At certain phase changes, we may discard large amounts of data and replace it with large numbers of newly
//       allocated objects.  This "spike" looks more like a phase change.  We were in steady state at M bytes/sec
//       allocation rate and now we're in a "reinitialization phase" that looks like N bytes/sec.  We need the "spike"
//       accommodation to give us enough runway to recalibrate our "average allocation rate".
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
//    in operation mode.  We want some way to decide that the average rate has changed, while keeping average
//    allocation rate computation independent.
bool ShenandoahAdaptiveHeuristics::should_start_gc() {

#ifdef KELVIN_START_GC
  static TriggerInfo rejected_trigger_log[MaxRejectedTriggers];
  static size_t rejected_trigger_count = 0;
  static size_t first_rejected_trigger = 0;

#define ForceAppendTriggerInfo(ts, cap, avail, alloced, mt, ls, aar, aw, act, pfagt, absls, \
                          irwps, crba, ca, accel, pfgt, fpgt, attda, is, r, sttda)          \
  if (rejected_trigger_count >= MaxRejectedTriggers) {                                      \
    first_rejected_trigger++;                                                               \
    if (first_rejected_trigger >= MaxRejectedTriggers)  {                                   \
      first_rejected_trigger = 0;                                                           \
    }                                                                                       \
  } else {                                                                                  \
    rejected_trigger_count++;                                                               \
  }                                                                                         \
  {                                                                                         \
    size_t __j;                                                                             \
    __j = (first_rejected_trigger + rejected_trigger_count - 1) % MaxRejectedTriggers;      \
    rejected_trigger_log[__j].time_stamp = ts;                                              \
    rejected_trigger_log[__j].capacity = cap;                                               \
    rejected_trigger_log[__j].available = avail;                                            \
    rejected_trigger_log[__j].allocated = alloced;                                          \
    rejected_trigger_log[__j].min_threshold = mt;                                           \
    rejected_trigger_log[__j].learned_steps = ls;                                           \
    rejected_trigger_log[__j].avg_alloc_rate = aar;                                         \
    rejected_trigger_log[__j].allocatable_words = aw;                                       \
    rejected_trigger_log[__j].avg_cycle_time = act;                                         \
    rejected_trigger_log[__j].predicted_future_accelerated_gc_time = pfagt;                 \
    rejected_trigger_log[__j].allocated_bytes_since_last_sample = absls;                    \
    rejected_trigger_log[__j].instantaneous_rate_words_per_second = irwps;                  \
    rejected_trigger_log[__j].current_rate_by_acceleration = crba;                          \
    rejected_trigger_log[__j].consumption_accelerated = ca;                                 \
    rejected_trigger_log[__j].acceleration = accel;                                         \
    rejected_trigger_log[__j].predicted_future_gc_time = pfgt;                              \
    rejected_trigger_log[__j].future_planned_gc_time = fpgt;                                \
    rejected_trigger_log[__j].avg_time_to_deplete_available = attda;                        \
    rejected_trigger_log[__j].is_spiking = is;                                              \
    rejected_trigger_log[__j].rate = r;                                                     \
    rejected_trigger_log[__j].spike_time_to_deplete_available = sttda;                      \
  }

  // We do not append trigger info for non-consequential sample periods
#define AppendTriggerInfo(ts, cap, avail, alloced, mt, ls, aar, aw, act, pfagt, absls,      \
                          irwps, crba, ca, accel, pfgt, fpgt, attda, is, r, sttda)          \
  if (((absls) > 0) || ((r) > 0)) {                                                         \
    ForceAppendTriggerInfo(ts, cap, avail, alloced, mt, ls, aar, aw, act, pfagt, absls,     \
                          irwps, crba, ca, accel, pfgt, fpgt, attda, is, r, sttda)          \
  }

#define DumpTriggerInfo(ts, cap, avail, alloced, mt, ls, aar, aw, act, pfagt, absls,        \
                        irwps, crba, ca, accel, pfgt, fpgt, attda, is, r, sttda)            \
  ForceAppendTriggerInfo(ts, cap, avail, alloced, mt, ls,                                   \
                    aar, aw, act, pfagt, absls, irwps, crba, ca, accel,                     \
                    pfgt, fpgt, attda, is, r, sttda);                                       \
  dumpTriggerInfo(first_rejected_trigger, rejected_trigger_count, rejected_trigger_log);    \
  rejected_trigger_count = 0;                                                               \
  first_rejected_trigger = 0;

#else
#define AppendTriggerInfo(ts, cap, avail, alloced, mt, ls, aar, aw, act, pfagt, absls,      \
                          irwps, crba, ca, accel, pfgt, fpgt, attda, is, r, sr, sttda) ;

#define DumpTriggerInfo(ts, cap, avail, alloced, mt, ls, aar, aw, act, pfagt, absls,        \
                        irwps, crba, ca, accel, pfgt, fpgt, attda, is, r, sr, sttda) ;
#endif

  size_t capacity = ShenandoahHeap::heap()->soft_max_capacity();
  size_t available = _space_info->soft_mutator_available();
  size_t allocated = _space_info->bytes_allocated_since_gc_start();

  double avg_cycle_time = 0;
  double avg_alloc_rate = 0;
  double now = get_most_recent_wake_time();
  size_t allocatable_words = this->allocatable(available);
  double predicted_future_accelerated_gc_time = 0.0;
  size_t allocated_bytes_since_last_sample = 0;
  double instantaneous_rate_words_per_second = 0.0;
  size_t consumption_accelerated = 0;
  double acceleration = 0.0;
  double current_rate_by_acceleration = 0.0;
  size_t min_threshold = min_free_threshold();

#ifdef KELVIN_START_GC
  size_t learned_steps = _gc_times_learned;
#endif

  double predicted_future_gc_time = 0;
  double future_planned_gc_time = 0;
  bool future_planned_gc_time_is_average = false;
  double avg_time_to_deplete_available = 0.0;
  bool is_spiking = false;
  double spike_time_to_deplete_available = 0.0;

  log_debug(gc, ergo)("should_start_gc calculation: available: " PROPERFMT ", soft_max_capacity: "  PROPERFMT ", "
                "allocated_since_gc_start: "  PROPERFMT,
                PROPERFMTARGS(available), PROPERFMTARGS(capacity), PROPERFMTARGS(allocated));

  // Track allocation rate even if we decide to start a cycle for other reasons.
  double rate = _allocation_rate.sample(allocated);

  if (_start_gc_is_pending) {
    log_trigger("GC start is already pending");
    DumpTriggerInfo(now, capacity, available, allocated, min_threshold, learned_steps, avg_alloc_rate, allocatable_words,
                    avg_cycle_time, predicted_future_accelerated_gc_time, allocated_bytes_since_last_sample,
                    instantaneous_rate_words_per_second, current_rate_by_acceleration, consumption_accelerated,
                    acceleration, predicted_future_gc_time,
                    future_planned_gc_time, avg_time_to_deplete_available, is_spiking, rate, spike_time_to_deplete_available);
    return true;
  }

  _last_trigger = OTHER;

  if (available < min_threshold) {
    log_trigger("Free (Soft) (" PROPERFMT ") is below minimum threshold (" PROPERFMT ")",
                 PROPERFMTARGS(available), PROPERFMTARGS(min_threshold));
    accept_trigger_with_type(OTHER);
    DumpTriggerInfo(now, capacity, available, allocated, min_threshold, learned_steps, avg_alloc_rate, allocatable_words,
                    avg_cycle_time, predicted_future_accelerated_gc_time, allocated_bytes_since_last_sample,
                    instantaneous_rate_words_per_second, current_rate_by_acceleration, consumption_accelerated,
                    acceleration, predicted_future_gc_time,
                    future_planned_gc_time, avg_time_to_deplete_available, is_spiking, rate, spike_time_to_deplete_available);
    return true;
  }

  // Check if we need to learn a bit about the application
  const size_t max_learn = ShenandoahLearningSteps;
  if (_gc_times_learned < max_learn) {
    size_t init_threshold = capacity / 100 * ShenandoahInitFreeThreshold;
    if (available < init_threshold) {
      log_trigger("Learning %zu of %zu. Free (%zu%s) is below initial threshold (%zu%s)",
                   _gc_times_learned + 1, max_learn,
                   byte_size_in_proper_unit(available), proper_unit_for_byte_size(available),
                   byte_size_in_proper_unit(init_threshold), proper_unit_for_byte_size(init_threshold));
      accept_trigger_with_type(OTHER);
      DumpTriggerInfo(now, capacity, available, allocated, min_threshold, learned_steps, avg_alloc_rate, allocatable_words,
                      avg_cycle_time, predicted_future_accelerated_gc_time, allocated_bytes_since_last_sample,
                      instantaneous_rate_words_per_second, current_rate_by_acceleration, consumption_accelerated,
                      acceleration, predicted_future_gc_time,
                      future_planned_gc_time, avg_time_to_deplete_available, is_spiking, rate, spike_time_to_deplete_available);
      return true;
    }
  }

  avg_cycle_time = _gc_cycle_time_history->davg() + (_margin_of_error_sd * _gc_cycle_time_history->dsd());
  avg_alloc_rate = _allocation_rate.upper_bound(_margin_of_error_sd);
  if ((now - _previous_allocation_timestamp) >= ShenandoahAccelerationSamplePeriod) {
    predicted_future_accelerated_gc_time =
      predict_gc_time(now + MAX2(get_planned_sleep_interval(), ShenandoahAccelerationSamplePeriod));
    double future_accelerated_planned_gc_time;
    bool future_accelerated_planned_gc_time_is_average;
    if (predicted_future_accelerated_gc_time > avg_cycle_time) {
      future_accelerated_planned_gc_time = predicted_future_accelerated_gc_time;
      future_accelerated_planned_gc_time_is_average = false;
    } else {
      future_accelerated_planned_gc_time = avg_cycle_time;
      future_accelerated_planned_gc_time_is_average = true;
    }
    allocated_bytes_since_last_sample = _free_set->get_bytes_allocated_since_previous_sample();
    instantaneous_rate_words_per_second =
      (allocated_bytes_since_last_sample / HeapWordSize) / (now - _previous_allocation_timestamp);

    _previous_allocation_timestamp = now;
    add_rate_to_acceleration_history(now, instantaneous_rate_words_per_second);
    current_rate_by_acceleration = instantaneous_rate_words_per_second;
    consumption_accelerated =
      accelerated_consumption(acceleration, current_rate_by_acceleration, avg_alloc_rate / HeapWordSize,
                              ShenandoahAccelerationSamplePeriod + future_accelerated_planned_gc_time);

    // Note that even a single thread that wakes up and begins to allocate excessively can manifest as accelerating allocation
    // rate. This thread will initially allocate a TLAB of minimum size.  Then it will allocate a TLAB twice as big a bit later,
    // and then twice as big again after another short delay.  When a phase change causes many threads to increase their
    // allocation behavior, this effect is multiplied, and compounded by jitter in the times that individual threads experience
    // the phase change.
    //
    // The following trace represents an actual workload, with allocation rates sampled at 10 Hz, the default behavior before
    // introduction of accelerated allocation rate detection.  Though the allocation rate is seen to be increasing at times
    // 101.907 and 102.007 and 102.108, the newly sampled allocation rate is not enough to trigger GC because the headroom is
    // still quite large.  In fact, GC is not triggered until time 102.409s, and this GC degenerates.
    //
    //    Sample Time (s)      Allocation Rate (MB/s)       Headroom (GB)
    //       101.807                       0.0                  26.93
    //                                                                  <--- accelerated spike can trigger here, around time 101.9s
    //       101.907                     477.6                  26.85
    //       102.007                   3,206.0                  26.35
    //       102.108                  23,797.8                  24.19
    //       102.208                  24,164.5                  21.83
    //       102.309                  23,965.0                  19.47
    //       102.409                  24,624.35                 17.05   <--- without accelerated rate detection, we trigger here
    //
    // Though the above measurements are from actual workload, the following details regarding sampled allocation rates at 3ms
    // period were not measured directly for this run-time sample.  These are hypothetical, though they represent a plausible
    // result that correlates with the actual measurements.
    //
    // For most of the 100 ms time span that precedes the sample at 101.907, the allocation rate still remains at zero.  The phase
    // change that causes increasing allocations occurs near the end ot this time segment.  When sampled with a 3 ms period,
    // acceration of allocation can be triggered at approximately time 101.88s.
    //
    // In the default configuration, accelerated allocation rate is detected by examining a sequence of 5 allocation rate samples.
    //
    // Even a single allocation rate sample above the norm can be interpreted as acceleration of allocation rate.  For example, the
    // the best-fit line for the following samples has an acceleration rate of 3,553.3 MB/s/s.  This is not enough to trigger GC,
    // especially given the abundance of Headroom at this moment in time.
    //
    //    TimeStamp (s)     Alloc rate (MB/s)
    //    101.857                 0
    //    101.860                 0
    //    101.863                 0
    //    101.866                 0
    //    101.869                53.3
    //
    // At the next sample time, we will compute a slightly higher acceration, 9,150 MB/s/s.  This is also insufficient to trigger
    // GC.
    //
    //    TimeStamp (s)     Alloc rate (MB/s)
    //    101.860                 0
    //    101.863                 0
    //    101.866                 0
    //    101.869                53.3
    //    101.872               110.6
    //
    // Eventually, we will observe a full history of accelerating rate samples, computing acceleration of 18,500 MB/s/s.  This will
    // trigger GC over 500 ms earlier than was previously possible.
    //
    //    TimeStamp (s)     Alloc rate (MB/s)
    //    101.866                 0
    //    101.869                53.3
    //    101.872               110.6
    //    101.875               165.9
    //    101.878               221.2
    //
    // The accelerated rate heuristic is based on the following idea:
    //
    //    Assume allocation rate is accelerating at a constant rate.  If we postpone the spike trigger until the subsequent
    //    sample point, will there be enough memory to satisfy allocations that occur during the anticipated concurrent GC
    //    cycle?  If not, we should trigger right now.
    //
    // Outline of this heuristic triggering technique:
    //
    //  1. We remember the N (e.g. N=3) most recent samples of spike allocation rate r0, r1, r2 samples at t0, t1, and t2
    //  2. if r1 < r0 or r2 < r1, approximate Acceleration = 0.0, Rate = Average(r0, r1, r2)
    //  3. Otherwise, use least squares method to compute best-fit line of rate vs time
    //  4. The slope of this line represents Acceleration. The y-intercept of this line represents "initial rate"
    //  5. Use r2 to rrpresent CurrentRate
    //  6. Use Consumption = CurrentRate * GCTime + 1/2 * Acceleration * GCTime * GCTime
    //     (See High School physics discussions on constant acceleration: D = v0 * t + 1/2 * a * t^2)
    //  7. if Consumption exceeds headroom, trigger now
    //
    // Though larger sample size may improve quality of predictor, it would delay our trigger response as well.  Smaller sample
    // sizes are more susceptible to false triggers based on random noise.  The default configuration uses a sample size of 5,
    // spanning 15ms of execution.

    if (consumption_accelerated > allocatable_words) {
      size_t size_t_alloc_rate = (size_t) current_rate_by_acceleration * HeapWordSize;
      if (acceleration > 0) {
        size_t size_t_acceleration = (size_t) acceleration * HeapWordSize;
        log_trigger("Accelerated consumption (%zu%s) exceeds free headroom (%zu%s) at "
                    "current rate (%zu%s/s) with acceleration (%zu%s/s/s) for planned %s GC time (%.2f ms)",
                    byte_size_in_proper_unit(consumption_accelerated * HeapWordSize), proper_unit_for_byte_size(consumption_accelerated * HeapWordSize),
                    byte_size_in_proper_unit(allocatable_words * HeapWordSize), proper_unit_for_byte_size(allocatable_words * HeapWordSize),
                    byte_size_in_proper_unit(size_t_alloc_rate), proper_unit_for_byte_size(size_t_alloc_rate),
                    byte_size_in_proper_unit(size_t_acceleration), proper_unit_for_byte_size(size_t_acceleration),
                    future_accelerated_planned_gc_time_is_average? "(from average)": "(by linear prediction)",
                    future_accelerated_planned_gc_time * 1000);
      } else {
        log_trigger("Momentary spike consumption (%zu%s) exceeds free headroom (%zu%s) at "
                    "current rate (%zu%s/s) for planned %s GC time (%.2f ms) (spike threshold = %.2f)",
                    byte_size_in_proper_unit(consumption_accelerated * HeapWordSize), proper_unit_for_byte_size(consumption_accelerated * HeapWordSize),
                    byte_size_in_proper_unit(allocatable_words * HeapWordSize), proper_unit_for_byte_size(allocatable_words * HeapWordSize),
                    byte_size_in_proper_unit(size_t_alloc_rate), proper_unit_for_byte_size(size_t_alloc_rate),
                    future_accelerated_planned_gc_time_is_average? "(from average)": "(by linear prediction)",
                    future_accelerated_planned_gc_time * 1000, _spike_threshold_sd);


      }
      _spike_acceleration_num_samples = 0;
      _spike_acceleration_first_sample_index = 0;

      // Count this as a form of RATE trigger for purposes of adjusting heuristic triggering configuration because this
      // trigger is influenced more by margin_of_error_sd than by spike_threshold_sd.
      accept_trigger_with_type(RATE);
      DumpTriggerInfo(now, capacity, available, allocated, min_threshold, learned_steps, avg_alloc_rate, allocatable_words,
                      avg_cycle_time, predicted_future_accelerated_gc_time, allocated_bytes_since_last_sample,
                      instantaneous_rate_words_per_second, current_rate_by_acceleration, consumption_accelerated,
                      acceleration, predicted_future_gc_time,
                      future_planned_gc_time, avg_time_to_deplete_available, is_spiking, rate, spike_time_to_deplete_available);
      return true;
    }
  }

  // Suppose we don't trigger now, but decide to trigger in the next regulator cycle.  What will be the GC time then?
  predicted_future_gc_time = predict_gc_time(now + get_planned_sleep_interval());
  if (predicted_future_gc_time > avg_cycle_time) {
    future_planned_gc_time = predicted_future_gc_time;
    future_planned_gc_time_is_average = false;
  } else {
    future_planned_gc_time = avg_cycle_time;
    future_planned_gc_time_is_average = true;
  }

  log_debug(gc)("%s: average GC time: %.2f ms, predicted GC time: %.2f ms, allocation rate: %.0f %s/s",
                _space_info->name(), avg_cycle_time * 1000, predicted_future_gc_time * 1000,
                byte_size_in_proper_unit(avg_alloc_rate), proper_unit_for_byte_size(avg_alloc_rate));
  size_t allocatable_bytes = allocatable_words * HeapWordSize;
  avg_time_to_deplete_available = allocatable_bytes / avg_alloc_rate;

  if (future_planned_gc_time > avg_time_to_deplete_available) {
    log_trigger("%s GC time (%.2f ms) is above the time for average allocation rate (%.0f %sB/s)"
                " to deplete free headroom (%zu%s) (margin of error = %.2f)",
                future_planned_gc_time_is_average? "Average": "Linear prediction of", future_planned_gc_time * 1000,
                byte_size_in_proper_unit(avg_alloc_rate),    proper_unit_for_byte_size(avg_alloc_rate),
                byte_size_in_proper_unit(allocatable_bytes), proper_unit_for_byte_size(allocatable_bytes),
                _margin_of_error_sd);

    size_t spike_headroom = capacity / 100 * ShenandoahAllocSpikeFactor;
    size_t penalties      = capacity / 100 * _gc_time_penalties;
    size_t allocation_headroom = available;
    allocation_headroom -= MIN2(allocation_headroom, spike_headroom);
    allocation_headroom -= MIN2(allocation_headroom, penalties);
    log_info(gc, ergo)("Free headroom: %zu%s (free) - %zu%s (spike) - %zu%s (penalties) = %zu%s",
                       byte_size_in_proper_unit(available),           proper_unit_for_byte_size(available),
                       byte_size_in_proper_unit(spike_headroom),      proper_unit_for_byte_size(spike_headroom),
                       byte_size_in_proper_unit(penalties),           proper_unit_for_byte_size(penalties),
                       byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom));
    accept_trigger_with_type(RATE);
    DumpTriggerInfo(now, capacity, available, allocated, min_threshold, learned_steps, avg_alloc_rate, allocatable_words,
                    avg_cycle_time, predicted_future_accelerated_gc_time, allocated_bytes_since_last_sample,
                    instantaneous_rate_words_per_second, current_rate_by_acceleration, consumption_accelerated,
                    acceleration, predicted_future_gc_time,
                    future_planned_gc_time, avg_time_to_deplete_available, is_spiking, rate, spike_time_to_deplete_available);
    return true;
  }

  is_spiking = _allocation_rate.is_spiking(rate, _spike_threshold_sd);
  spike_time_to_deplete_available = (rate == 0)? 0: allocatable_bytes / rate;
  if (is_spiking && (rate != 0) && (future_planned_gc_time > spike_time_to_deplete_available)) {
    log_trigger("%s GC time (%.2f ms) is above the time for instantaneous allocation rate (%.0f %sB/s)"
                " to deplete free headroom (%zu%s) (spike threshold = %.2f)",
                future_planned_gc_time_is_average? "Average": "Linear prediction of", future_planned_gc_time * 1000,
                byte_size_in_proper_unit(rate),        proper_unit_for_byte_size(rate),
                byte_size_in_proper_unit(allocatable_bytes), proper_unit_for_byte_size(allocatable_bytes),
                _spike_threshold_sd);
    accept_trigger_with_type(SPIKE);
    DumpTriggerInfo(now, capacity, available, allocated, min_threshold, learned_steps, avg_alloc_rate, allocatable_words,
                    avg_cycle_time, predicted_future_accelerated_gc_time, allocated_bytes_since_last_sample,
                    instantaneous_rate_words_per_second, current_rate_by_acceleration, consumption_accelerated,
                    acceleration, predicted_future_gc_time,
                    future_planned_gc_time, avg_time_to_deplete_available, is_spiking, rate, spike_time_to_deplete_available);
    return true;
  }

#ifdef KELVIN_START_GC
  if (ShenandoahHeuristics::should_start_gc()) {
    // ShenandoahHeuristics::should_start_gc() has accepted trigger, or declined it.
    DumpTriggerInfo(now, capacity, available, allocated, min_threshold, learned_steps, avg_alloc_rate, allocatable_words,
                    avg_cycle_time, predicted_future_accelerated_gc_time, allocated_bytes_since_last_sample,
                    instantaneous_rate_words_per_second, current_rate_by_acceleration, consumption_accelerated,
                    acceleration, predicted_future_gc_time,
                    future_planned_gc_time, avg_time_to_deplete_available, is_spiking, rate, spike_time_to_deplete_available);
    return true;
  } else {
    AppendTriggerInfo(now, capacity, available, allocated, min_threshold, learned_steps, avg_alloc_rate, allocatable_words,
                      avg_cycle_time, predicted_future_accelerated_gc_time, allocated_bytes_since_last_sample,
                      instantaneous_rate_words_per_second, current_rate_by_acceleration, consumption_accelerated,
                      acceleration, predicted_future_gc_time,
                      future_planned_gc_time, avg_time_to_deplete_available, is_spiking, rate, spike_time_to_deplete_available);
    return false;
  }
#else
  return ShenandoahHeuristics::should_start_gc();
#endif
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

size_t ShenandoahAdaptiveHeuristics::min_free_threshold() {
  return ShenandoahHeap::heap()->soft_max_capacity() / 100 * ShenandoahMinFreeThreshold;
}

// This is called each time a new rate sample has been gathered, as governed by MINMUM_ALLOC_RATE_SAMPLE_INTERVAL.
// There is no adjustment for standard deviation of the accelerated rate prediction.
size_t ShenandoahAdaptiveHeuristics::accelerated_consumption(double& acceleration, double& current_rate,
                                                             double avg_alloc_rate_words_per_second,
                                                             double predicted_cycle_time) const
{
  double *x_array = (double *) alloca(ShenandoahRateAccelerationSampleSize * sizeof(double));
  double *y_array = (double *) alloca(ShenandoahRateAccelerationSampleSize * sizeof(double));
  double x_sum = 0.0;
  double y_sum = 0.0;

  assert(_spike_acceleration_num_samples > 0, "At minimum, we should have sample from this period");

  double weighted_average_alloc;
  if (_spike_acceleration_num_samples >= ShenandoahRateAccelerationSampleSize) {
    double weighted_y_sum = 0;
    double total_weight = 0;
    double previous_x = 0;
    uint delta = _spike_acceleration_num_samples - ShenandoahRateAccelerationSampleSize;
    for (uint i = 0; i < ShenandoahRateAccelerationSampleSize; i++) {
      uint index = (_spike_acceleration_first_sample_index + delta + i) % _spike_acceleration_buffer_size;
      x_array[i] = _spike_acceleration_rate_timestamps[index];
      x_sum += x_array[i];
      y_array[i] = _spike_acceleration_rate_samples[index];
      if (i > 0) {
        // first sample not included in weighted average because it has no weight.
        double sample_weight = x_array[i] - x_array[i-1];
        weighted_y_sum = y_array[i] * sample_weight;
        total_weight += sample_weight;
      }
      y_sum += y_array[i];
    }
    weighted_average_alloc = (total_weight > 0)? weighted_y_sum / total_weight: 0;
  } else {
    weighted_average_alloc = 0;
  }

  double momentary_rate;
  if (_spike_acceleration_num_samples > ShenandoahMomentaryAllocationRateSpikeSampleSize) {
    // Num samples must be strictly greater than sample size, because we need one extra sample to compute rate and weights
    double weighted_y_sum = 0;
    double total_weight = 0;
    double sum_for_average = 0.0;
    uint delta = _spike_acceleration_num_samples - ShenandoahMomentaryAllocationRateSpikeSampleSize;
    for (uint i = 0; i < ShenandoahMomentaryAllocationRateSpikeSampleSize; i++) {
      uint sample_index = (_spike_acceleration_first_sample_index + delta + i) % _spike_acceleration_buffer_size;
      uint preceding_index = (sample_index == 0)? _spike_acceleration_buffer_size - 1: sample_index - 1;
      double sample_weight = (_spike_acceleration_rate_timestamps[sample_index]
                              - _spike_acceleration_rate_timestamps[preceding_index]);
      weighted_y_sum += _spike_acceleration_rate_samples[sample_index] * sample_weight;
      total_weight += sample_weight;
    }
    momentary_rate = weighted_y_sum / total_weight;
    bool is_spiking = _allocation_rate.is_spiking(momentary_rate, _spike_threshold_sd);
    if (!is_spiking) {
      // Disable momentary spike trigger unless allocation rate delta from average exceeds sd
      momentary_rate = 0.0;
    }
  } else {
    momentary_rate = 0.0;
  }

  // By default, use momentary_rate for current rate and zero acceleration. Overwrite iff best-fit line has positive slope.
  current_rate = momentary_rate;
  acceleration = 0.0;
  if ((_spike_acceleration_num_samples >= ShenandoahRateAccelerationSampleSize)
      && (weighted_average_alloc >= avg_alloc_rate_words_per_second))  {
    // If the average rate across the acceleration samples is below the overall average, this sample is not eligible to
    //  represent acceleration of allocation rate.  We may just be catching up with allocations after a lull.

    double *xy_array = (double *) alloca(ShenandoahRateAccelerationSampleSize * sizeof(double));
    double *x2_array = (double *) alloca(ShenandoahRateAccelerationSampleSize * sizeof(double));
    double xy_sum = 0.0;
    double x2_sum = 0.0;
    for (uint i = 0; i < ShenandoahRateAccelerationSampleSize; i++) {
      xy_array[i] = x_array[i] * y_array[i];
      xy_sum += xy_array[i];
      x2_array[i] = x_array[i] * x_array[i];
      x2_sum += x2_array[i];
    }
    // Find the best-fit least-squares linear representation of rate vs time
    double m;                 /* slope */
    double b;                 /* y-intercept */

    m = ((ShenandoahRateAccelerationSampleSize * xy_sum - x_sum * y_sum)
         / (ShenandoahRateAccelerationSampleSize * x2_sum - x_sum * x_sum));
    b = (y_sum - m * x_sum) / ShenandoahRateAccelerationSampleSize;

    if (m > 0) {
      double proposed_current_rate = m * x_array[ShenandoahRateAccelerationSampleSize - 1] + b;
      acceleration = m;
      current_rate = proposed_current_rate;
    }
    // else, leave current_rate = y_max, acceleration = 0
  }
  // and here also, leave current_rate = y_max, acceleration = 0

  double time_delta = get_planned_sleep_interval() + predicted_cycle_time;
  size_t words_to_be_consumed = (size_t) (current_rate * time_delta + 0.5 * acceleration * time_delta * time_delta);
  return words_to_be_consumed;
}

ShenandoahAllocationRate::ShenandoahAllocationRate() :
  _last_sample_time(os::elapsedTime()),
  _last_sample_value(0),
  _interval_sec(1.0 / ShenandoahAdaptiveSampleFrequencyHz),
  _rate(int(ShenandoahAdaptiveSampleSizeSeconds * ShenandoahAdaptiveSampleFrequencyHz), ShenandoahAdaptiveDecayFactor),
  _rate_avg(int(ShenandoahAdaptiveSampleSizeSeconds * ShenandoahAdaptiveSampleFrequencyHz), ShenandoahAdaptiveDecayFactor) {
}

double ShenandoahAllocationRate::force_sample(size_t allocated, size_t &unaccounted_bytes_allocated) {
  const double MinSampleTime = 0.002;    // Do not sample if time since last update is less than 2 ms
  double now = os::elapsedTime();
  double time_since_last_update = now - _last_sample_time;
  if (time_since_last_update < MinSampleTime) {
    unaccounted_bytes_allocated = allocated - _last_sample_value;
    _last_sample_value = 0;
    return 0.0;
  } else {
    double rate = instantaneous_rate(now, allocated);
    _rate.add(rate);
    _rate_avg.add(_rate.avg());
    _last_sample_time = now;
    _last_sample_value = allocated;
    unaccounted_bytes_allocated = 0;
    return rate;
  }
}

double ShenandoahAllocationRate::sample(size_t allocated) {
  double now = os::elapsedTime();
  double rate = 0.0;
  if (now - _last_sample_time > _interval_sec) {
    rate = instantaneous_rate(now, allocated);
    _rate.add(rate);
    _rate_avg.add(_rate.avg());
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
    // There is a small chance that that rate has already been sampled, but it seems not to matter in practice.
    // Note that z_score reports how close the rate is to the average.  A value between -1 and 1 means we are within one
    // standard deviation.  A value between -3 and +3 means we are within 3 standard deviations.  We only check for z_score
    // greater than threshold because we are looking for an allocation spike which is greater than the mean.
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

