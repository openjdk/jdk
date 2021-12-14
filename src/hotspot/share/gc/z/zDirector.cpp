/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zDirector.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zHeuristics.hpp"
#include "gc/z/zStat.hpp"
#include "logging/log.hpp"

constexpr double one_in_1000 = 3.290527;
constexpr double sample_interval = 1.0 / ZStatMutatorAllocRate::sample_hz;

ZDirector::ZDirector() :
    _metronome(ZStatMutatorAllocRate::sample_hz) {
  set_name("ZDirector");
  create_and_start();
}

static void sample_mutator_allocation_rate() {
  // Sample allocation rate. This is needed by rule_allocation_rate()
  // below to estimate the time we have until we run out of memory.
  const double bytes_per_second = ZStatMutatorAllocRate::sample_and_reset();

  log_debug(gc, alloc)("Mutator Allocation Rate: %.1fMB/s, Predicted: %.1fMB/s, Avg: %.1f(+/-%.1f)MB/s",
                       bytes_per_second / M,
                       ZStatMutatorAllocRate::predict() / M,
                       ZStatMutatorAllocRate::avg() / M,
                       ZStatMutatorAllocRate::sd() / M);
}

// Minor GC rules

static GCCause::Cause rule_minor_timer() {
  if (ZCollectionIntervalMinor <= 0) {
    // Rule disabled
    return GCCause::_no_gc;
  }

  // Perform GC if timer has expired.
  const double time_since_last_gc = ZCollector::young()->stat_cycle()->time_since_last();
  const double time_until_gc = ZCollectionIntervalMinor - time_since_last_gc;

  log_debug(gc, director)("Rule Minor: Timer, Interval: %.3fs, TimeUntilGC: %.3fs",
                          ZCollectionIntervalMinor, time_until_gc);

  if (time_until_gc > 0) {
    return GCCause::_no_gc;
  }

  return GCCause::_z_timer;
}

static double estimated_gc_workers(double serial_gc_time, double parallelizable_gc_time, double time_until_deadline) {
  const double parallelizable_time_until_deadline = MAX2(time_until_deadline - serial_gc_time, 0.001);
  return parallelizable_gc_time / parallelizable_time_until_deadline;
}

static uint discrete_gc_workers(double gc_workers) {
  return clamp<uint>(ceil(gc_workers), 1, ConcGCThreads);
}

static double select_young_gc_workers(double serial_gc_time, double parallelizable_gc_time, double alloc_rate_sd_percent, double time_until_oom) {
  // Use all workers until we're warm
  if (!ZCollector::old()->stat_cycle()->is_warm()) {
    const double not_warm_gc_workers = ConcGCThreads;
    log_debug(gc, director)("Select Minor GC Workers (Not Warm), GCWorkers: %.3f", not_warm_gc_workers);
    return not_warm_gc_workers;
  }

  // Calculate number of GC workers needed to avoid OOM.
  const double gc_workers = estimated_gc_workers(serial_gc_time, parallelizable_gc_time, time_until_oom);
  const uint actual_gc_workers = discrete_gc_workers(gc_workers);
  const double last_gc_workers = ZCollector::young()->stat_cycle()->last_active_workers();

  // More than 15% division from the average is considered unsteady
  if (alloc_rate_sd_percent >= 0.15) {
    const double half_gc_workers = ConcGCThreads / 2.0;
    const double unsteady_gc_workers = MAX3(gc_workers, last_gc_workers, half_gc_workers);
    log_debug(gc, director)("Select Minor GC Workers (Unsteady), "
                            "AvoidOOMGCWorkers: %.3f, LastGCWorkers: %.3f, HalfGCWorkers: %.3f, GCWorkers: %.3f",
                            gc_workers, last_gc_workers, half_gc_workers, unsteady_gc_workers);
    return unsteady_gc_workers;
  }

  if ((double)actual_gc_workers < last_gc_workers) {
    // Before decreasing number of GC workers compared to the previous GC cycle, check if the
    // next GC cycle will need to increase it again. If so, use the same number of GC workers
    // that will be needed in the next cycle.
    const double gc_duration_delta = (parallelizable_gc_time / actual_gc_workers) - (parallelizable_gc_time / last_gc_workers);
    const double additional_time_for_allocations = ZCollector::young()->stat_cycle()->time_since_last() - gc_duration_delta - sample_interval;
    const double next_time_until_oom = time_until_oom + additional_time_for_allocations;
    const double next_avoid_oom_gc_workers = estimated_gc_workers(serial_gc_time, parallelizable_gc_time, next_time_until_oom);

    // Add 0.5 to increase friction and avoid lowering too eagerly
    const double next_gc_workers = next_avoid_oom_gc_workers + 0.5;
    const double try_lowering_gc_workers = clamp<double>(next_gc_workers, actual_gc_workers, last_gc_workers);

    log_debug(gc, director)("Select Minor GC Workers (Try Lowering), "
                            "AvoidOOMGCWorkers: %.3f, NextAvoidOOMGCWorkers: %.3f, LastGCWorkers: %.3f, GCWorkers: %.3f",
                            gc_workers, next_avoid_oom_gc_workers, last_gc_workers, try_lowering_gc_workers);
    return try_lowering_gc_workers;
  }

  log_debug(gc, director)("Select Minor GC Workers (Normal), "
                          "AvoidOOMGCWorkers: %.3f, LastGCWorkers: %.3f, GCWorkers: %.3f",
                          gc_workers, last_gc_workers, gc_workers);
  return gc_workers;
}

ZDriverRequest rule_minor_allocation_rate_dynamic(double serial_gc_time_passed, double parallel_gc_time_passed) {
  if (!ZCollector::old()->stat_cycle()->is_time_trustable()) {
    // Rule disabled
    return ZDriverRequest(GCCause::_no_gc, ConcGCThreads, 0);
  }

  ZYoungCollector* const young_collector = ZCollector::young();
  ZOldCollector* const old_collector = ZCollector::old();

  // Calculate amount of free memory available. Note that we take the
  // relocation headroom into account to avoid in-place relocation.
  const size_t soft_max_capacity = ZHeap::heap()->soft_max_capacity();
  const size_t used = ZHeap::heap()->used();
  const size_t free_including_headroom = soft_max_capacity - MIN2(soft_max_capacity, used);
  const size_t free = free_including_headroom - MIN2(free_including_headroom, ZHeuristics::relocation_headroom());

  // Calculate time until OOM given the max allocation rate and the amount
  // of free memory. The allocation rate is a moving average and we multiply
  // that with an allocation spike tolerance factor to guard against unforeseen
  // phase changes in the allocate rate. We then add ~3.3 sigma to account for
  // the allocation rate variance, which means the probability is 1 in 1000
  // that a sample is outside of the confidence interval.
  const double alloc_rate_predict = ZStatMutatorAllocRate::predict();
  const double alloc_rate_avg = ZStatMutatorAllocRate::avg();
  const double alloc_rate_sd = ZStatMutatorAllocRate::sd();
  const double alloc_rate_sd_percent = alloc_rate_sd / (alloc_rate_avg + 1.0);
  const double alloc_rate = (MAX2(alloc_rate_predict, alloc_rate_avg) * ZAllocationSpikeTolerance) + (alloc_rate_sd * one_in_1000) + 1.0;
  const double time_until_oom = (free / alloc_rate) / (1.0 + alloc_rate_sd_percent);

  // Calculate max serial/parallel times of a GC cycle. The times are
  // moving averages, we add ~3.3 sigma to account for the variance.
  const double serial_gc_time = fabsd(young_collector->stat_cycle()->serial_time().davg() + (young_collector->stat_cycle()->serial_time().dsd() * one_in_1000) - serial_gc_time_passed);
  const double parallelizable_gc_time = fabsd(young_collector->stat_cycle()->parallelizable_time().davg() + (young_collector->stat_cycle()->parallelizable_time().dsd() * one_in_1000) - parallel_gc_time_passed);

  // Calculate number of GC workers needed to avoid OOM.
  const double gc_workers = select_young_gc_workers(serial_gc_time, parallelizable_gc_time, alloc_rate_sd_percent, time_until_oom);

  // Convert to a discrete number of GC workers within limits.
  const uint actual_gc_workers = discrete_gc_workers(gc_workers);

  // Calculate GC duration given number of GC workers needed.
  const double actual_gc_duration = serial_gc_time + (parallelizable_gc_time / actual_gc_workers);
  const double last_gc_workers = young_collector->stat_cycle()->last_active_workers();

  // Calculate time until GC given the time until OOM and GC duration.
  // We also subtract the sample interval, so that we don't overshoot the
  // target time and end up starting the GC too late in the next interval.
  const double time_until_gc = time_until_oom - actual_gc_duration - sample_interval;

  log_debug(gc, director)("Rule Minor: Allocation Rate (Dynamic GC Workers), "
                          "MaxAllocRate: %.1fMB/s (+/-%.1f%%), Free: " SIZE_FORMAT "MB, GCCPUTime: %.3f, "
                          "GCDuration: %.3fs, TimeUntilOOM: %.3fs, TimeUntilGC: %.3fs, GCWorkers: %.3fs -> %u",
                          alloc_rate / M,
                          alloc_rate_sd_percent * 100,
                          free / M,
                          serial_gc_time + parallelizable_gc_time,
                          serial_gc_time + (parallelizable_gc_time / actual_gc_workers),
                          time_until_oom,
                          time_until_gc,
                          last_gc_workers,
                          actual_gc_workers);

  if ((double)actual_gc_workers <= last_gc_workers && time_until_gc > 0.0) {
    return ZDriverRequest(GCCause::_no_gc, actual_gc_workers, 0);
  }

  return ZDriverRequest(GCCause::_z_allocation_rate, actual_gc_workers, 0);
}

static GCCause::Cause rule_minor_allocation_rate_static() {
  if (!ZCollector::old()->stat_cycle()->is_time_trustable()) {
    // Rule disabled
    return GCCause::_no_gc;
  }

  // Perform GC if the estimated max allocation rate indicates that we
  // will run out of memory. The estimated max allocation rate is based
  // on the moving average of the sampled allocation rate plus a safety
  // margin based on variations in the allocation rate and unforeseen
  // allocation spikes.

  ZYoungCollector* const young_collector = ZCollector::young();
  ZOldCollector* const old_collector = ZCollector::old();

  // Calculate amount of free memory available. Note that we take the
  // relocation headroom into account to avoid in-place relocation.
  const size_t soft_max_capacity = ZHeap::heap()->soft_max_capacity();
  const size_t used = ZHeap::heap()->used();
  const size_t free_including_headroom = soft_max_capacity - MIN2(soft_max_capacity, used);
  const size_t free = free_including_headroom - MIN2(free_including_headroom, ZHeuristics::relocation_headroom());

  // Calculate time until OOM given the max allocation rate and the amount
  // of free memory. The allocation rate is a moving average and we multiply
  // that with an allocation spike tolerance factor to guard against unforeseen
  // phase changes in the allocate rate. We then add ~3.3 sigma to account for
  // the allocation rate variance, which means the probability is 1 in 1000
  // that a sample is outside of the confidence interval.
  const double max_alloc_rate = (ZStatMutatorAllocRate::avg() * ZAllocationSpikeTolerance) + (ZStatMutatorAllocRate::sd() * one_in_1000);
  const double time_until_oom = free / (max_alloc_rate + 1.0); // Plus 1.0B/s to avoid division by zero

  // Calculate max serial/parallel times of a GC cycle. The times are
  // moving averages, we add ~3.3 sigma to account for the variance.
  const double serial_gc_time = young_collector->stat_cycle()->serial_time().davg() + (young_collector->stat_cycle()->serial_time().dsd() * one_in_1000);
  const double parallelizable_gc_time = young_collector->stat_cycle()->parallelizable_time().davg() + (young_collector->stat_cycle()->parallelizable_time().dsd() * one_in_1000);

  // Calculate GC duration given number of GC workers needed.
  const double gc_duration = serial_gc_time + (parallelizable_gc_time / ConcGCThreads);

  // Calculate time until GC given the time until OOM and max duration of GC.
  // We also deduct the sample interval, so that we don't overshoot the target
  // time and end up starting the GC too late in the next interval.
  const double time_until_gc = time_until_oom - gc_duration - sample_interval;

  log_debug(gc, director)("Rule Minor: Allocation Rate (Static GC Workers), MaxAllocRate: %.1fMB/s, Free: " SIZE_FORMAT "MB, GCDuration: %.3fs, TimeUntilGC: %.3fs",
                          max_alloc_rate / M, free / M, gc_duration, time_until_gc);

  if (time_until_gc > 0) {
    return GCCause::_no_gc;
  }

  return GCCause::_z_allocation_rate;
}

static GCCause::Cause rule_minor_allocation_rate() {
  if (ZHeap::heap()->is_alloc_stalling_for_old()) {
    // Don't collect young if we have threads stalled waiting for an old collection
    return GCCause::_no_gc;
  }

  if (UseDynamicNumberOfGCThreads) {
    return rule_minor_allocation_rate_dynamic(0.0, 0.0).cause();
  } else {
    return rule_minor_allocation_rate_static();
  }
}

static GCCause::Cause rule_minor_high_usage() {
  if (ZDriver::minor()->is_busy()) {
    // If there is already an ongoing GC, then let's leave it
    return GCCause::_no_gc;
  }
  // Perform GC if the amount of free memory is 5% or less. This is a preventive
  // meassure in the case where the application has a very low allocation rate,
  // such that the allocation rate rule doesn't trigger, but the amount of free
  // memory is still slowly but surely heading towards zero. In this situation,
  // we start a GC cycle to avoid a potential allocation stall later.

  // Calculate amount of free memory available. Note that we take the
  // relocation headroom into account to avoid in-place relocation.
  const size_t soft_max_capacity = ZHeap::heap()->soft_max_capacity();
  const size_t used = ZHeap::heap()->used();
  const size_t free_including_headroom = soft_max_capacity - MIN2(soft_max_capacity, used);
  const size_t free = free_including_headroom - MIN2(free_including_headroom, ZHeuristics::relocation_headroom());
  const double free_percent = percent_of(free, soft_max_capacity);

  log_debug(gc, director)("Rule Minor: High Usage, Free: " SIZE_FORMAT "MB(%.1f%%)",
                          free / M, free_percent);

  if (free_percent > 5.0) {
    return GCCause::_no_gc;
  }

  return GCCause::_z_high_usage;
}

// Major GC rules

static GCCause::Cause rule_major_timer() {
  if (ZCollectionIntervalMajor <= 0) {
    // Rule disabled
    return GCCause::_no_gc;
  }

  // Perform GC if timer has expired.
  const double time_since_last_gc = ZCollector::old()->stat_cycle()->time_since_last();
  const double time_until_gc = ZCollectionIntervalMajor - time_since_last_gc;

  log_debug(gc, director)("Rule Major: Timer, Interval: %.3fs, TimeUntilGC: %.3fs",
                          ZCollectionIntervalMajor, time_until_gc);

  if (time_until_gc > 0) {
    return GCCause::_no_gc;
  }

  return GCCause::_z_timer;
}

static GCCause::Cause rule_major_warmup() {
  if (ZCollector::old()->stat_cycle()->is_warm()) {
    // Rule disabled
    return GCCause::_no_gc;
  }

  // Perform GC if heap usage passes 10/20/30% and no other GC has been
  // performed yet. This allows us to get some early samples of the GC
  // duration, which is needed by the other rules.
  const size_t soft_max_capacity = ZHeap::heap()->soft_max_capacity();
  const size_t used = ZHeap::heap()->used();
  const double used_threshold_percent = (ZCollector::old()->stat_cycle()->nwarmup_cycles() + 1) * 0.1;
  const size_t used_threshold = soft_max_capacity * used_threshold_percent;

  log_debug(gc, director)("Rule Major: Warmup %.0f%%, Used: " SIZE_FORMAT "MB, UsedThreshold: " SIZE_FORMAT "MB",
                          used_threshold_percent * 100, used / M, used_threshold / M);

  if (used < used_threshold) {
    return GCCause::_no_gc;
  }

  return GCCause::_z_warmup;
}

static double calculate_extra_young_gc_time() {
  if (!ZCollector::old()->stat_cycle()->is_time_trustable()) {
    return 0.0;
  }

  // Calculate amount of free memory available. Note that we take the
  // relocation headroom into account to avoid in-place relocation.
  ZYoungCollector* const young_collector = ZCollector::young();
  ZOldCollector* const old_collector = ZCollector::old();
  const size_t soft_max_capacity = ZHeap::heap()->soft_max_capacity();
  const size_t used = ZHeap::heap()->used();
  const size_t free_including_headroom = soft_max_capacity - MIN2(soft_max_capacity, used);
  const size_t free = free_including_headroom - MIN2(free_including_headroom, ZHeuristics::relocation_headroom());
  const size_t live_last_old_gc = old_collector->stat_heap()->live_at_mark_end();
  const size_t live_last_young_gc = young_collector->stat_heap()->live_at_mark_end();
  const size_t old_used = ZGeneration::old()->used();
  const size_t old_garbage = old_used - live_last_old_gc;
  const size_t young_used = ZGeneration::young()->used();
  const size_t young_available = young_used + free;

  // Calculate max serial/parallel times of a young GC cycle. The times are
  // moving averages, we add ~3.3 sigma to account for the variance.
  const double young_serial_gc_time = young_collector->stat_cycle()->serial_time().davg() + (young_collector->stat_cycle()->parallelizable_time().dsd() * one_in_1000);
  const double young_parallelizable_gc_time = young_collector->stat_cycle()->parallelizable_time().davg() + (young_collector->stat_cycle()->parallelizable_time().dsd() * one_in_1000);

  // Calculate young GC time and duration given number of GC workers needed.
  const double young_gc_time = young_serial_gc_time + young_parallelizable_gc_time;

  // Calculate how much memory young collections are predicted to free.
  size_t freeable_per_young_gc = young_available - live_last_young_gc;

  // Since young collections are not instant, we have to start them
  // before running out of memory, so that the application can allocate
  // while the GC works. In a back-to-back scenario, the ratio of
  // allocated bytes vs reclaimed bytes is typically 50-50. Therefore
  // the freeable bytes per young GC is typically half of the theoretically
  // ultimate case of young collections being instant. This is an
  // approximation of the truth. More exact estimations of allocation
  // rate might yield more precise heuristics when we don't back-to-back
  // collect the young generation.
  freeable_per_young_gc /= 2;

  // Calculate current YC time and predicted YC time after an old collection.
  const double current_young_gc_time_per_bytes_freed = double(young_gc_time) / double(freeable_per_young_gc);
  const double potential_young_gc_time_per_bytes_freed = double(young_gc_time) / double(freeable_per_young_gc + old_garbage);

  // Calculate extra time per young collection inflicted by *not* doing an
  // old collection that frees up memory in the old generation.
  const double extra_young_gc_time_per_bytes_freed = current_young_gc_time_per_bytes_freed - potential_young_gc_time_per_bytes_freed;
  const double extra_young_gc_time = extra_young_gc_time_per_bytes_freed * (freeable_per_young_gc + old_garbage);

  return extra_young_gc_time;
}

static GCCause::Cause rule_major_allocation_rate() {
  if (!ZCollector::old()->stat_cycle()->is_time_trustable()) {
    // Rule disabled
    return GCCause::_no_gc;
  }
  // Perform GC if the estimated max allocation rate indicates that we
  // will run out of memory. The estimated max allocation rate is based
  // on the moving average of the sampled allocation rate plus a safety
  // margin based on variations in the allocation rate and unforeseen
  // allocation spikes.

  // Calculate amount of free memory available. Note that we take the
  // relocation headroom into account to avoid in-place relocation.
  ZOldCollector* const old_collector = ZCollector::old();

  // Calculate max serial/parallel times of an old GC cycle. The times are
  // moving averages, we add ~3.3 sigma to account for the variance.
  const double old_serial_gc_time = old_collector->stat_cycle()->serial_time().davg() + (old_collector->stat_cycle()->serial_time().dsd() * one_in_1000);
  const double old_parallelizable_gc_time = old_collector->stat_cycle()->parallelizable_time().davg() + (old_collector->stat_cycle()->parallelizable_time().dsd() * one_in_1000);

  // Calculate old GC time.
  const double old_gc_time = old_serial_gc_time + old_parallelizable_gc_time;

  // Calculate extra time per young collection inflicted by *not* doing an
  // old collection that frees up memory in the old generation.
  const double extra_young_gc_time = calculate_extra_young_gc_time();

  // Doing an old collection makes subsequent young collections more efficient.
  // Calculate the number of young collections ahead that we will try to amortize
  // the cost of doing an old collection for.
  int lookahead = ZCollectedHeap::heap()->total_collections() - old_collector->total_collections_at_end();

  // Calculate extra young collection overhead predicted for a number of future
  // young collections, due to not freeing up memory in the old generation.
  double extra_young_gc_time_for_lookahead = extra_young_gc_time * lookahead;

  log_debug(gc, director)("Rule Major: Allocation Rate, ExtraYoungGCTime: %.3fs, OldGCTime: %.3fs, Lookahead: %d, ExtraYoungGCTimeForLookahead: %.3fs",
                          extra_young_gc_time, old_gc_time, lookahead, extra_young_gc_time_for_lookahead);


  if (extra_young_gc_time_for_lookahead > old_gc_time) {
    // If we continue doing as many minor collections as we already did since the
    // last major collection (N), without doing a major collection, then the minor
    // GC effort of freeing up memory for another N cycles, plus the effort of doing,
    // a major GC combined, is lower compared to the extra GC overhead per minor
    // collection, freeing an equal amount of memory, at a higher GC frequency.
    // In other words, the cost for minor collections of not doing a major collection
    // will seemingly be greater than the cost of doing a major collection and getting
    // cheaper minor collections for a time to come.
    return GCCause::_z_allocation_rate;
  }

  return GCCause::_no_gc;
}

static uint rule_major_allocation_rate_threads() {
  ZYoungCollector* const young_collector = ZCollector::young();
  ZOldCollector* const old_collector = ZCollector::old();

  // Calculate max serial/parallel times of an old GC cycle. The times are
  // moving averages, we add ~3.3 sigma to account for the variance.
  const double old_serial_gc_time = old_collector->stat_cycle()->serial_time().davg() + (old_collector->stat_cycle()->serial_time().dsd() * one_in_1000);
  const double old_parallelizable_gc_time = old_collector->stat_cycle()->parallelizable_time().davg() + (old_collector->stat_cycle()->parallelizable_time().dsd() * one_in_1000);
  const double last_old_gc_workers = old_collector->stat_cycle()->last_active_workers();
  const double old_parallelizable_gc_duration = old_parallelizable_gc_time / last_old_gc_workers;

  const double extra_young_gc_time = calculate_extra_young_gc_time();

  // Calculate max serial/parallel times of a GC cycle. The times are
  // moving averages, we add ~3.3 sigma to account for the variance.
  const double serial_young_gc_time = young_collector->stat_cycle()->serial_time().davg() + (young_collector->stat_cycle()->serial_time().dsd() * one_in_1000);
  const double parallelizable_young_gc_time = young_collector->stat_cycle()->parallelizable_time().davg() + (young_collector->stat_cycle()->parallelizable_time().dsd() * one_in_1000);

  // Calculate GC duration given number of GC workers needed.
  const double last_young_gc_workers = young_collector->stat_cycle()->last_active_workers();

  const double young_gc_duration = serial_young_gc_time + parallelizable_young_gc_time / last_young_gc_workers;

  // Calculate how much amortized extra young GC time can be reduced by putting
  // an equal amount of GC time towards finishing old faster instead.
  uint num_threads = 1;
  for (uint i = 2; i <= ConcGCThreads; ++i) {
    uint extra_threads = i - num_threads;
    double baseline_old_duration = old_parallelizable_gc_time / num_threads + old_serial_gc_time;
    double potential_old_duration = old_parallelizable_gc_time / i + old_serial_gc_time;
    double potential_reduced_old_duration = baseline_old_duration - potential_old_duration;
    uint potential_reduced_yc_count = uint(potential_reduced_old_duration / young_gc_duration);
    double reduced_extra_young_gc_time = extra_young_gc_time * potential_reduced_yc_count;
    double extra_old_gc_time = extra_threads * old_parallelizable_gc_duration;
    if (reduced_extra_young_gc_time > extra_old_gc_time) {
      num_threads = i;
    }
  }

  return num_threads;
}

static GCCause::Cause rule_major_proactive() {
  if (!ZProactive || !ZCollector::old()->stat_cycle()->is_warm()) {
    // Rule disabled
    return GCCause::_no_gc;
  }

  // Perform GC if the impact of doing so, in terms of application throughput
  // reduction, is considered acceptable. This rule allows us to keep the heap
  // size down and allow reference processing to happen even when we have a lot
  // of free space on the heap.

  // Only consider doing a proactive GC if the heap usage has grown by at least
  // 10% of the max capacity since the previous GC, or more than 5 minutes has
  // passed since the previous GC. This helps avoid superfluous GCs when running
  // applications with very low allocation rate.
  const size_t used_after_last_gc = ZCollector::old()->stat_heap()->used_at_relocate_end();
  const size_t used_increase_threshold = ZHeap::heap()->soft_max_capacity() * 0.10; // 10%
  const size_t used_threshold = used_after_last_gc + used_increase_threshold;
  const size_t used = ZHeap::heap()->used();
  const double time_since_last_gc = ZCollector::old()->stat_cycle()->time_since_last();
  const double time_since_last_gc_threshold = 5 * 60; // 5 minutes
  if (used < used_threshold && time_since_last_gc < time_since_last_gc_threshold) {
    // Don't even consider doing a proactive GC
    log_debug(gc, director)("Rule Major: Proactive, UsedUntilEnabled: " SIZE_FORMAT "MB, TimeUntilEnabled: %.3fs",
                            (used_threshold - used) / M,
                            time_since_last_gc_threshold - time_since_last_gc);
    return GCCause::_no_gc;
  }

  const double assumed_throughput_drop_during_gc = 0.50; // 50%
  const double acceptable_throughput_drop = 0.01;        // 1%
  const double serial_gc_time = ZCollector::old()->stat_cycle()->serial_time().davg() + (ZCollector::old()->stat_cycle()->serial_time().dsd() * one_in_1000);
  const double parallelizable_gc_time = ZCollector::old()->stat_cycle()->parallelizable_time().davg() + (ZCollector::old()->stat_cycle()->parallelizable_time().dsd() * one_in_1000);
  const double gc_duration = serial_gc_time + (parallelizable_gc_time / ConcGCThreads);
  const double acceptable_gc_interval = gc_duration * ((assumed_throughput_drop_during_gc / acceptable_throughput_drop) - 1.0);
  const double time_until_gc = acceptable_gc_interval - time_since_last_gc;

  log_debug(gc, director)("Rule Major: Proactive, AcceptableGCInterval: %.3fs, TimeSinceLastGC: %.3fs, TimeUntilGC: %.3fs",
                          acceptable_gc_interval, time_since_last_gc, time_until_gc);

  if (time_until_gc > 0) {
    return GCCause::_no_gc;
  }

  return GCCause::_z_proactive;
}

static GCCause::Cause make_minor_gc_decision() {
  // List of rules
  using ZDirectorRule = GCCause::Cause (*)();
  const ZDirectorRule rules[] = {
    rule_minor_timer,
    rule_minor_allocation_rate,
    rule_minor_high_usage
  };

  // Execute rules
  for (size_t i = 0; i < ARRAY_SIZE(rules); i++) {
    const GCCause::Cause cause = rules[i]();
    if (ZCollectionIntervalOnly && cause != GCCause::_z_timer) {
      continue;
    }
    if (cause != GCCause::_no_gc) {
      return cause;
    }
  }

  return GCCause::_no_gc;
}

static GCCause::Cause make_major_gc_decision() {
  // List of rules
  using ZDirectorRule = GCCause::Cause (*)();
  const ZDirectorRule rules[] = {
    rule_major_warmup,
    rule_major_timer,
    rule_major_proactive,
  };

  // Execute rules
  for (size_t i = 0; i < ARRAY_SIZE(rules); i++) {
    const GCCause::Cause cause = rules[i]();
    if (ZCollectionIntervalOnly && cause != GCCause::_z_timer) {
      continue;
    }
    if (cause != GCCause::_no_gc) {
      return cause;
    }
  }

  return GCCause::_no_gc;
}

struct ZCollectorResizeInfo {
  bool _is_active;
  uint _current_nworkers;
  uint _desired_nworkers;
};

static ZCollectorResizeInfo wanted_young_nworkers() {
  ZYoungCollector* collector = ZCollector::young();
  ZWorkerResizeStats stats = collector->workers()->resize_stats(collector->stat_cycle());

  if (!stats._is_active) {
    // Collection is not running
    return {_is_active: stats._is_active, _current_nworkers: stats._nworkers_current, _desired_nworkers: 0};
  }

  ZDriverRequest request = rule_minor_allocation_rate_dynamic(stats._serial_gc_time_passed, stats._parallel_gc_time_passed);
  if (request.cause() == GCCause::_no_gc) {
    // No urgency
    return {_is_active: stats._is_active, _current_nworkers: stats._nworkers_current, _desired_nworkers: 0};
  }

  return {_is_active: stats._is_active, _current_nworkers: stats._nworkers_current, _desired_nworkers: request.young_nworkers()};
}

static ZCollectorResizeInfo wanted_old_nworkers() {
  ZOldCollector* collector = ZCollector::old();
  ZWorkerResizeStats stats = collector->workers()->resize_stats(collector->stat_cycle());

  if (!stats._is_active) {
    // Collection is not running
    return {_is_active: stats._is_active, _current_nworkers: stats._nworkers_current, _desired_nworkers: 0};
  }

  GCCause::Cause cause = rule_major_allocation_rate();
  if (cause == GCCause::_no_gc) {
    // No urgency
    return {_is_active: stats._is_active, _current_nworkers: stats._nworkers_current, _desired_nworkers: 0};
  }

  return {_is_active: stats._is_active, _current_nworkers: stats._nworkers_current, _desired_nworkers: rule_major_allocation_rate_threads()};
}

static void change_gc_decision(ZCollectorResizeInfo young_info, ZCollectorResizeInfo old_info) {
  ZYoungCollector* young_collector = ZCollector::young();
  ZOldCollector* old_collector = ZCollector::old();

  if (young_info._is_active && old_info._is_active) {
    // Need at least 1 thread for old collector
    uint max_young_threads = ConcGCThreads - 1;
    young_info._desired_nworkers = clamp(young_info._desired_nworkers, 0u, max_young_threads);
    // Adjust old threads so we don't have more than ConcGCThreads in total
    uint max_old_threads = ConcGCThreads - MAX2(young_info._current_nworkers, young_info._desired_nworkers);
    old_info._desired_nworkers = clamp(old_info._desired_nworkers, 0u, max_old_threads);
  }

  bool need_more_young_workers = young_info._current_nworkers < young_info._desired_nworkers;
  bool need_more_old_workers = old_info._current_nworkers < old_info._desired_nworkers;
  bool too_many_total_threads = MAX2(young_info._current_nworkers, young_info._desired_nworkers) + old_info._current_nworkers > ConcGCThreads;

  if (old_info._desired_nworkers != 0 && need_more_old_workers || too_many_total_threads) {
    // Need to change major workers
    old_collector->workers()->request_resize_workers(old_info._desired_nworkers);
  }

  if (young_info._desired_nworkers != 0 && need_more_young_workers) {
    // We need more workers than we currently use; trigger worker resize
    young_collector->workers()->request_resize_workers(young_info._desired_nworkers);
  }
}

static void change_gc_decision() {
  if (!UseDynamicNumberOfGCThreads) {
    return;
  }

  change_gc_decision(wanted_young_nworkers(), wanted_old_nworkers());
}


static uint initial_young_workers() {
  if (UseDynamicNumberOfGCThreads) {
    uint wanted_nworkers = rule_minor_allocation_rate_dynamic(0.0, 0.0).young_nworkers();
    if (ZDriver::major()->is_busy()) {
      // Give at least 1 thread to old collector.
      wanted_nworkers = clamp(wanted_nworkers, 1u, ConcGCThreads - 1);

      // Force old collector to yield threads if it has too many
      ZCollectorResizeInfo young_info = {_is_active: true, _current_nworkers: wanted_nworkers, _desired_nworkers: wanted_nworkers};
      change_gc_decision(young_info, wanted_old_nworkers());
    }
    return wanted_nworkers;
  } else {
    return ConcGCThreads / 2;
  }
}

static uint initial_old_workers() {
  if (UseDynamicNumberOfGCThreads) {
    return rule_major_allocation_rate_threads();
  } else {
    return ConcGCThreads / 2;
  }
}

static bool make_gc_decision() {
  // Check for major collections first as they include a minor collection
  if (!ZDriver::major()->is_busy()) {
    const GCCause::Cause cause = make_major_gc_decision();
    if (cause != GCCause::_no_gc) {
      ZDriverRequest request(cause, initial_young_workers(), initial_old_workers());
      ZDriver::major()->collect(request);
      return true;
    }
  }

  if (!ZDriver::minor()->is_busy()) {
    const GCCause::Cause minor_cause = make_minor_gc_decision();
    const GCCause::Cause major_cause = rule_major_allocation_rate();
    if (minor_cause != GCCause::_no_gc) {
      if (!ZDriver::major()->is_busy() &&
          major_cause == GCCause::_z_allocation_rate) {
        // Try merging major allocation rate GCs with another minor GC.
        const ZDriverRequest request(major_cause, initial_young_workers(), initial_old_workers());
        ZDriver::major()->collect(request);
      } else {
        const ZDriverRequest request(minor_cause, initial_young_workers(), 0);
        ZDriver::minor()->collect(request);
      }
      return true;
    }
  }

  return false;
}

void ZDirector::run_service() {
  // Main loop
  while (_metronome.wait_for_tick()) {
    sample_mutator_allocation_rate();
    if (!make_gc_decision()) {
      change_gc_decision();
    }
  }
}

void ZDirector::stop_service() {
  _metronome.stop();
}
