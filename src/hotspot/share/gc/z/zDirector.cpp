/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zHeuristics.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zStat.hpp"
#include "logging/log.hpp"

ZDirector* ZDirector::_director;

constexpr double one_in_1000 = 3.290527;

struct ZWorkerResizeStats {
  bool   _is_active;
  double _serial_gc_time_passed;
  double _parallel_gc_time_passed;
  uint   _nworkers_current;
};

struct ZDirectorHeapStats {
  size_t _soft_max_heap_size;
  size_t _used;
  uint   _total_collections;
};

struct ZDirectorGenerationGeneralStats {
  size_t _used;
  uint   _total_collections_at_start;
};

struct ZDirectorGenerationStats {
  ZStatCycleStats                 _cycle;
  ZStatWorkersStats               _workers;
  ZWorkerResizeStats              _resize;
  ZStatHeapStats                  _stat_heap;
  ZDirectorGenerationGeneralStats _general;
};

struct ZDirectorStats {
  ZStatMutatorAllocRateStats _mutator_alloc_rate;
  ZDirectorHeapStats         _heap;
  ZDirectorGenerationStats   _young_stats;
  ZDirectorGenerationStats   _old_stats;
};

ZDirector::ZDirector()
  : _monitor(),
    _stopped(false) {
  _director = this;
  set_name("ZDirector");
  create_and_start();
}

// Minor GC rules

static bool rule_minor_timer(const ZDirectorStats& stats) {
  if (ZCollectionIntervalMinor <= 0) {
    // Rule disabled
    return false;
  }

  // Perform GC if timer has expired.
  const double time_since_last_gc = stats._young_stats._cycle._time_since_last;
  const double time_until_gc = ZCollectionIntervalMinor - time_since_last_gc;

  log_debug(gc, director)("Rule Minor: Timer, Interval: %.3fs, TimeUntilGC: %.3fs",
                          ZCollectionIntervalMinor, time_until_gc);

  return time_until_gc <= 0;
}

static double estimated_gc_workers(double serial_gc_time, double parallelizable_gc_time, double time_until_deadline) {
  const double parallelizable_time_until_deadline = MAX2(time_until_deadline - serial_gc_time, 0.001);
  return parallelizable_gc_time / parallelizable_time_until_deadline;
}

static uint discrete_young_gc_workers(double gc_workers) {
  return clamp<uint>((uint)ceil(gc_workers), 1, ZYoungGCThreads);
}

static double select_young_gc_workers(const ZDirectorStats& stats, double serial_gc_time, double parallelizable_gc_time, double alloc_rate_sd_percent, double time_until_oom) {
  // Use all workers until we're warm
  if (!stats._old_stats._cycle._is_warm) {
    const double not_warm_gc_workers = ZYoungGCThreads;
    log_debug(gc, director)("Select Minor GC Workers (Not Warm), GCWorkers: %.3f", not_warm_gc_workers);
    return not_warm_gc_workers;
  }

  // Calculate number of GC workers needed to avoid OOM.
  const double gc_workers = estimated_gc_workers(serial_gc_time, parallelizable_gc_time, time_until_oom);
  const uint actual_gc_workers = discrete_young_gc_workers(gc_workers);
  const double last_gc_workers = stats._young_stats._cycle._last_active_workers;

  if ((double)actual_gc_workers < last_gc_workers) {
    // Before decreasing number of GC workers compared to the previous GC cycle, check if the
    // next GC cycle will need to increase it again. If so, use the same number of GC workers
    // that will be needed in the next cycle.
    const double gc_duration_delta = (parallelizable_gc_time / actual_gc_workers) - (parallelizable_gc_time / last_gc_workers);
    const double additional_time_for_allocations = stats._young_stats._cycle._time_since_last - gc_duration_delta;
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

static ZDriverRequest rule_minor_allocation_rate_dynamic(const ZDirectorStats& stats,
                                                         double serial_gc_time_passed,
                                                         double parallel_gc_time_passed,
                                                         bool conservative_alloc_rate,
                                                         size_t capacity) {
  if (!stats._old_stats._cycle._is_time_trustable) {
    // Rule disabled
    return ZDriverRequest(GCCause::_no_gc, ZYoungGCThreads, 0);
  }

  // Calculate amount of free memory available. Note that we take the
  // relocation headroom into account to avoid in-place relocation.
  const size_t used = stats._heap._used;
  const size_t free_including_headroom = capacity - MIN2(capacity, used);
  const size_t free = free_including_headroom - MIN2(free_including_headroom, ZHeuristics::relocation_headroom());

  // Calculate time until OOM given the max allocation rate and the amount
  // of free memory. The allocation rate is a moving average and we multiply
  // that with an allocation spike tolerance factor to guard against unforeseen
  // phase changes in the allocate rate. We then add ~3.3 sigma to account for
  // the allocation rate variance, which means the probability is 1 in 1000
  // that a sample is outside of the confidence interval.
  const ZStatMutatorAllocRateStats alloc_rate_stats = stats._mutator_alloc_rate;
  const double alloc_rate_predict = alloc_rate_stats._predict;
  const double alloc_rate_avg = alloc_rate_stats._avg;
  const double alloc_rate_sd = alloc_rate_stats._sd;
  const double alloc_rate_sd_percent = alloc_rate_sd / (alloc_rate_avg + 1.0);
  const double alloc_rate_conservative = (MAX2(alloc_rate_predict, alloc_rate_avg) * ZAllocationSpikeTolerance) + (alloc_rate_sd * one_in_1000) + 1.0;
  const double alloc_rate = conservative_alloc_rate ? alloc_rate_conservative : alloc_rate_stats._avg;
  const double time_until_oom = (free / alloc_rate) / (1.0 + alloc_rate_sd_percent);

  // Calculate max serial/parallel times of a GC cycle. The times are
  // moving averages, we add ~3.3 sigma to account for the variance.
  const double serial_gc_time = fabsd(stats._young_stats._cycle._avg_serial_time + (stats._young_stats._cycle._sd_serial_time * one_in_1000) - serial_gc_time_passed);
  const double parallelizable_gc_time = fabsd(stats._young_stats._cycle._avg_parallelizable_time + (stats._young_stats._cycle._sd_parallelizable_time * one_in_1000) - parallel_gc_time_passed);

  // Calculate number of GC workers needed to avoid OOM.
  const double gc_workers = select_young_gc_workers(stats, serial_gc_time, parallelizable_gc_time, alloc_rate_sd_percent, time_until_oom);

  // Convert to a discrete number of GC workers within limits.
  const uint actual_gc_workers = discrete_young_gc_workers(gc_workers);

  // Calculate GC duration given number of GC workers needed.
  const double actual_gc_duration = serial_gc_time + (parallelizable_gc_time / actual_gc_workers);

  // Calculate time until GC given the time until OOM and GC duration.
  const double time_until_gc = time_until_oom - actual_gc_duration;

  log_debug(gc, director)("Rule Minor: Allocation Rate (Dynamic GC Workers), "
                          "MaxAllocRate: %.1fMB/s (+/-%.1f%%), Free: " SIZE_FORMAT "MB, GCCPUTime: %.3f, "
                          "GCDuration: %.3fs, TimeUntilOOM: %.3fs, TimeUntilGC: %.3fs, GCWorkers: %u",
                          alloc_rate / M,
                          alloc_rate_sd_percent * 100,
                          free / M,
                          serial_gc_time + parallelizable_gc_time,
                          serial_gc_time + (parallelizable_gc_time / actual_gc_workers),
                          time_until_oom,
                          time_until_gc,
                          actual_gc_workers);

  // Bail out if we are not "close" to needing the GC to start yet, where
  // close is 5% of the time left until OOM. If we don't check that we
  // are "close", then the heuristics instead add more threads and we
  // end up not triggering GCs until we have the max number of threads.
  if (time_until_gc > time_until_oom * 0.05) {
    return ZDriverRequest(GCCause::_no_gc, actual_gc_workers, 0);
  }

  return ZDriverRequest(GCCause::_z_allocation_rate, actual_gc_workers, 0);
}

static ZDriverRequest rule_soft_minor_allocation_rate_dynamic(const ZDirectorStats& stats,
                                                              double serial_gc_time_passed,
                                                              double parallel_gc_time_passed) {
    return rule_minor_allocation_rate_dynamic(stats,
                                              0.0 /* serial_gc_time_passed */,
                                              0.0 /* parallel_gc_time_passed */,
                                              false /* conservative_alloc_rate */,
                                              stats._heap._soft_max_heap_size /* capacity */);
}

static ZDriverRequest rule_semi_hard_minor_allocation_rate_dynamic(const ZDirectorStats& stats,
                                                                   double serial_gc_time_passed,
                                                                   double parallel_gc_time_passed) {
  return rule_minor_allocation_rate_dynamic(stats,
                                            0.0 /* serial_gc_time_passed */,
                                            0.0 /* parallel_gc_time_passed */,
                                            false /* conservative_alloc_rate */,
                                            ZHeap::heap()->max_capacity() /* capacity */);
}

static ZDriverRequest rule_hard_minor_allocation_rate_dynamic(const ZDirectorStats& stats,
                                                              double serial_gc_time_passed,
                                                              double parallel_gc_time_passed) {
  return rule_minor_allocation_rate_dynamic(stats,
                                            0.0 /* serial_gc_time_passed */,
                                            0.0 /* parallel_gc_time_passed */,
                                            true /* conservative_alloc_rate */,
                                            ZHeap::heap()->max_capacity() /* capacity */);
}

static bool rule_minor_allocation_rate_static(const ZDirectorStats& stats) {
  if (!stats._old_stats._cycle._is_time_trustable) {
    // Rule disabled
    return false;
  }

  // Perform GC if the estimated max allocation rate indicates that we
  // will run out of memory. The estimated max allocation rate is based
  // on the moving average of the sampled allocation rate plus a safety
  // margin based on variations in the allocation rate and unforeseen
  // allocation spikes.

  // Calculate amount of free memory available. Note that we take the
  // relocation headroom into account to avoid in-place relocation.
  const size_t soft_max_capacity = stats._heap._soft_max_heap_size;
  const size_t used = stats._heap._used;
  const size_t free_including_headroom = soft_max_capacity - MIN2(soft_max_capacity, used);
  const size_t free = free_including_headroom - MIN2(free_including_headroom, ZHeuristics::relocation_headroom());

  // Calculate time until OOM given the max allocation rate and the amount
  // of free memory. The allocation rate is a moving average and we multiply
  // that with an allocation spike tolerance factor to guard against unforeseen
  // phase changes in the allocate rate. We then add ~3.3 sigma to account for
  // the allocation rate variance, which means the probability is 1 in 1000
  // that a sample is outside of the confidence interval.
  const ZStatMutatorAllocRateStats alloc_rate_stats = stats._mutator_alloc_rate;
  const double max_alloc_rate = (alloc_rate_stats._avg * ZAllocationSpikeTolerance) + (alloc_rate_stats._sd * one_in_1000);
  const double time_until_oom = free / (max_alloc_rate + 1.0); // Plus 1.0B/s to avoid division by zero

  // Calculate max serial/parallel times of a GC cycle. The times are
  // moving averages, we add ~3.3 sigma to account for the variance.
  const double serial_gc_time = stats._young_stats._cycle._avg_serial_time + (stats._young_stats._cycle._sd_serial_time * one_in_1000);
  const double parallelizable_gc_time = stats._young_stats._cycle._avg_parallelizable_time + (stats._young_stats._cycle._sd_parallelizable_time * one_in_1000);

  // Calculate GC duration given number of GC workers needed.
  const double gc_duration = serial_gc_time + (parallelizable_gc_time / ZYoungGCThreads);

  // Calculate time until GC given the time until OOM and max duration of GC.
  // We also deduct the sample interval, so that we don't overshoot the target
  // time and end up starting the GC too late in the next interval.
  const double time_until_gc = time_until_oom - gc_duration;

  log_debug(gc, director)("Rule Minor: Allocation Rate (Static GC Workers), MaxAllocRate: %.1fMB/s, Free: " SIZE_FORMAT "MB, GCDuration: %.3fs, TimeUntilGC: %.3fs",
                          max_alloc_rate / M, free / M, gc_duration, time_until_gc);

  return time_until_gc <= 0;
}

static bool is_young_small(const ZDirectorStats& stats) {
  // Calculate amount of freeable memory available.
  const size_t soft_max_capacity = stats._heap._soft_max_heap_size;
  const size_t young_used = stats._young_stats._general._used;

  const double young_used_percent = percent_of(young_used, soft_max_capacity);

  // If the freeable memory isn't even 5% of the heap, we can't expect to free up
  // all that much memory, so let's not even try - it will likely be a wasted effort
  // that takes away CPU power to the hopefullt more profitable major colelction.
  return young_used_percent <= 5.0;
}

template <typename PrintFn = void(*)(size_t, double)>
static bool is_high_usage(const ZDirectorStats& stats, PrintFn* print_function = nullptr) {
  // Calculate amount of free memory available. Note that we take the
  // relocation headroom into account to avoid in-place relocation.
  const size_t soft_max_capacity = stats._heap._soft_max_heap_size;
  const size_t used = stats._heap._used;
  const size_t free_including_headroom = soft_max_capacity - MIN2(soft_max_capacity, used);
  const size_t free = free_including_headroom - MIN2(free_including_headroom, ZHeuristics::relocation_headroom());
  const double free_percent = percent_of(free, soft_max_capacity);

  if (print_function != nullptr) {
    (*print_function)(free, free_percent);
  }

  // The heap has high usage if there is less than 5% free memory left
  return free_percent <= 5.0;
}

static bool is_major_urgent(const ZDirectorStats& stats) {
  return is_young_small(stats) && is_high_usage(stats);
}

static bool rule_minor_allocation_rate(const ZDirectorStats& stats) {
  if (ZCollectionIntervalOnly) {
    // Rule disabled
    return false;
  }

  if (ZHeap::heap()->is_alloc_stalling_for_old()) {
    // Don't collect young if we have threads stalled waiting for an old collection
    return false;
  }

  if (is_young_small(stats)) {
    return false;
  }

  if (UseDynamicNumberOfGCThreads) {
    if (rule_soft_minor_allocation_rate_dynamic(stats,
                                                0.0 /* serial_gc_time_passed */,
                                                0.0 /* parallel_gc_time_passed */).cause() != GCCause::_no_gc) {
      return true;
    }

    if (rule_hard_minor_allocation_rate_dynamic(stats,
                                                0.0 /* serial_gc_time_passed */,
                                                0.0 /* parallel_gc_time_passed */).cause() != GCCause::_no_gc) {
      return true;
    }

    return false;
  }

  return rule_minor_allocation_rate_static(stats);
}

static bool rule_minor_high_usage(const ZDirectorStats& stats) {
  if (ZCollectionIntervalOnly) {
    // Rule disabled
    return false;
  }

  if (is_young_small(stats)) {
    return false;
  }

  // Perform GC if the amount of free memory is small. This is a preventive
  // measure in the case where the application has a very low allocation rate,
  // such that the allocation rate rule doesn't trigger, but the amount of free
  // memory is still slowly but surely heading towards zero. In this situation,
  // we start a GC cycle to avoid a potential allocation stall later.

  const size_t soft_max_capacity = stats._heap._soft_max_heap_size;
  const size_t used = stats._heap._used;
  const size_t free_including_headroom = soft_max_capacity - MIN2(soft_max_capacity, used);
  const size_t free = free_including_headroom - MIN2(free_including_headroom, ZHeuristics::relocation_headroom());
  const double free_percent = percent_of(free, soft_max_capacity);

  auto print_function = [&](size_t free, double free_percent) {
    log_debug(gc, director)("Rule Minor: High Usage, Free: " SIZE_FORMAT "MB(%.1f%%)",
                            free / M, free_percent);
  };

  return is_high_usage(stats, &print_function);
}

// Major GC rules

static bool rule_major_timer(const ZDirectorStats& stats) {
  if (ZCollectionIntervalMajor <= 0) {
    // Rule disabled
    return false;
  }

  // Perform GC if timer has expired.
  const double time_since_last_gc = stats._old_stats._cycle._time_since_last;
  const double time_until_gc = ZCollectionIntervalMajor - time_since_last_gc;

  log_debug(gc, director)("Rule Major: Timer, Interval: %.3fs, TimeUntilGC: %.3fs",
                          ZCollectionIntervalMajor, time_until_gc);

  return time_until_gc <= 0;
}

static bool rule_major_warmup(const ZDirectorStats& stats) {
  if (ZCollectionIntervalOnly) {
    // Rule disabled
    return false;
  }

  if (stats._old_stats._cycle._is_warm) {
    // Rule disabled
    return false;
  }

  // Perform GC if heap usage passes 10/20/30% and no other GC has been
  // performed yet. This allows us to get some early samples of the GC
  // duration, which is needed by the other rules.
  const size_t soft_max_capacity = stats._heap._soft_max_heap_size;
  const size_t used = stats._heap._used;
  const double used_threshold_percent = (stats._old_stats._cycle._nwarmup_cycles + 1) * 0.1;
  const size_t used_threshold = (size_t)(soft_max_capacity * used_threshold_percent);

  log_debug(gc, director)("Rule Major: Warmup %.0f%%, Used: " SIZE_FORMAT "MB, UsedThreshold: " SIZE_FORMAT "MB",
                          used_threshold_percent * 100, used / M, used_threshold / M);

  return used >= used_threshold;
}

static double gc_time(ZDirectorGenerationStats generation_stats) {
  // Calculate max serial/parallel times of a generation GC cycle. The times are
  // moving averages, we add ~3.3 sigma to account for the variance.
  const double serial_gc_time = generation_stats._cycle._avg_serial_time + (generation_stats._cycle._sd_serial_time * one_in_1000);
  const double parallelizable_gc_time = generation_stats._cycle._avg_parallelizable_time + (generation_stats._cycle._sd_parallelizable_time * one_in_1000);

  // Calculate young GC time and duration given number of GC workers needed.
  return serial_gc_time + parallelizable_gc_time;
}

static double calculate_extra_young_gc_time(const ZDirectorStats& stats) {
  if (!stats._old_stats._cycle._is_time_trustable) {
    return 0.0;
  }

  // Calculate amount of free memory available. Note that we take the
  // relocation headroom into account to avoid in-place relocation.
  const size_t old_used = stats._old_stats._general._used;
  const size_t old_live = stats._old_stats._stat_heap._live_at_mark_end;
  const size_t old_garbage = old_used - old_live;

  const double young_gc_time = gc_time(stats._young_stats);

  // Calculate how much memory young collections are predicted to free.
  const size_t reclaimed_per_young_gc = stats._young_stats._stat_heap._reclaimed_avg;

  // Calculate current YC time and predicted YC time after an old collection.
  const double current_young_gc_time_per_bytes_freed = double(young_gc_time) / double(reclaimed_per_young_gc);
  const double potential_young_gc_time_per_bytes_freed = double(young_gc_time) / double(reclaimed_per_young_gc + old_garbage);

  // Calculate extra time per young collection inflicted by *not* doing an
  // old collection that frees up memory in the old generation.
  const double extra_young_gc_time_per_bytes_freed = current_young_gc_time_per_bytes_freed - potential_young_gc_time_per_bytes_freed;
  const double extra_young_gc_time = extra_young_gc_time_per_bytes_freed * (reclaimed_per_young_gc + old_garbage);

  return extra_young_gc_time;
}

static bool rule_major_allocation_rate(const ZDirectorStats& stats) {
  if (!stats._old_stats._cycle._is_time_trustable) {
    // Rule disabled
    return false;
  }

  // Calculate GC time.
  const double old_gc_time = gc_time(stats._old_stats);
  const double young_gc_time = gc_time(stats._young_stats);

  // Calculate how much memory collections are predicted to free.
  const size_t reclaimed_per_young_gc = stats._young_stats._stat_heap._reclaimed_avg;
  const size_t reclaimed_per_old_gc = stats._old_stats._stat_heap._reclaimed_avg;

  // Calculate the GC cost for each reclaimed byte
  const double current_young_gc_time_per_bytes_freed = double(young_gc_time) / double(reclaimed_per_young_gc);
  const double current_old_gc_time_per_bytes_freed = reclaimed_per_old_gc == 0 ? std::numeric_limits<double>::infinity()
                                                                               : (double(old_gc_time) / double(reclaimed_per_old_gc));

  // Calculate extra time per young collection inflicted by *not* doing an
  // old collection that frees up memory in the old generation.
  const double extra_young_gc_time = calculate_extra_young_gc_time(stats);

  // Doing an old collection makes subsequent young collections more efficient.
  // Calculate the number of young collections ahead that we will try to amortize
  // the cost of doing an old collection for.
  const uint lookahead = stats._heap._total_collections - stats._old_stats._general._total_collections_at_start;

  // Calculate extra young collection overhead predicted for a number of future
  // young collections, due to not freeing up memory in the old generation.
  const double extra_young_gc_time_for_lookahead = extra_young_gc_time * lookahead;

  log_debug(gc, director)("Rule Major: Allocation Rate, ExtraYoungGCTime: %.3fs, OldGCTime: %.3fs, Lookahead: %u, ExtraYoungGCTimeForLookahead: %.3fs",
                          extra_young_gc_time, old_gc_time, lookahead, extra_young_gc_time_for_lookahead);

  // If we continue doing as many minor collections as we already did since the
  // last major collection (N), without doing a major collection, then the minor
  // GC effort of freeing up memory for another N cycles, plus the effort of doing,
  // a major GC combined, is lower compared to the extra GC overhead per minor
  // collection, freeing an equal amount of memory, at a higher GC frequency.
  // In other words, the cost for minor collections of not doing a major collection
  // will seemingly be greater than the cost of doing a major collection and getting
  // cheaper minor collections for a time to come.
  const bool can_amortize_time_cost = extra_young_gc_time_for_lookahead > old_gc_time;

  // If the garbage is cheaper to reap in the old generation, then it makes sense
  // to upgrade minor collections to major collections.
  const bool old_garbage_is_cheaper = current_old_gc_time_per_bytes_freed < current_young_gc_time_per_bytes_freed;

  return can_amortize_time_cost || old_garbage_is_cheaper || is_major_urgent(stats);
}

static double calculate_young_to_old_worker_ratio(const ZDirectorStats& stats) {
  if (!stats._old_stats._cycle._is_time_trustable) {
    return 1.0;
  }

  const double young_gc_time = gc_time(stats._young_stats);
  const double old_gc_time = gc_time(stats._old_stats);
  const size_t reclaimed_per_young_gc = stats._young_stats._stat_heap._reclaimed_avg;
  const size_t reclaimed_per_old_gc = stats._old_stats._stat_heap._reclaimed_avg;
  const double current_young_bytes_freed_per_gc_time = double(reclaimed_per_young_gc) / double(young_gc_time);
  const double current_old_bytes_freed_per_gc_time = double(reclaimed_per_old_gc) / double(old_gc_time);

  if (current_young_bytes_freed_per_gc_time == 0.0) {
    if (current_old_bytes_freed_per_gc_time == 0.0) {
      // Neither young nor old collections have reclaimed any memory.
      // Give them equal priority.
      return 1.0;
    }

    // Only old collections have reclaimed memory.
    // Prioritize old.
    return ZOldGCThreads;
  }

  const double old_vs_young_efficiency_ratio = current_old_bytes_freed_per_gc_time / current_young_bytes_freed_per_gc_time;

  return old_vs_young_efficiency_ratio;
}

static bool rule_major_proactive(const ZDirectorStats& stats) {
  if (ZCollectionIntervalOnly) {
    // Rule disabled
    return false;
  }

  if (!ZProactive) {
    // Rule disabled
    return false;
  }

  if (!stats._old_stats._cycle._is_warm) {
    // Rule disabled
    return false;
  }

  // Perform GC if the impact of doing so, in terms of application throughput
  // reduction, is considered acceptable. This rule allows us to keep the heap
  // size down and allow reference processing to happen even when we have a lot
  // of free space on the heap.

  // Only consider doing a proactive GC if the heap usage has grown by at least
  // 10% of the max capacity since the previous GC, or more than 5 minutes has
  // passed since the previous GC. This helps avoid superfluous GCs when running
  // applications with very low allocation rate.
  const size_t used_after_last_gc = stats._old_stats._stat_heap._used_at_relocate_end;
  const size_t used_increase_threshold = (size_t)(stats._heap._soft_max_heap_size * 0.10); // 10%
  const size_t used_threshold = used_after_last_gc + used_increase_threshold;
  const size_t used = stats._heap._used;
  const double time_since_last_gc = stats._old_stats._cycle._time_since_last;
  const double time_since_last_gc_threshold = 5 * 60; // 5 minutes
  if (used < used_threshold && time_since_last_gc < time_since_last_gc_threshold) {
    // Don't even consider doing a proactive GC
    log_debug(gc, director)("Rule Major: Proactive, UsedUntilEnabled: " SIZE_FORMAT "MB, TimeUntilEnabled: %.3fs",
                            (used_threshold - used) / M,
                            time_since_last_gc_threshold - time_since_last_gc);
    return false;
  }

  const double assumed_throughput_drop_during_gc = 0.50; // 50%
  const double acceptable_throughput_drop = 0.01;        // 1%
  const double serial_old_gc_time = stats._old_stats._cycle._avg_serial_time + (stats._old_stats._cycle._sd_serial_time * one_in_1000);
  const double parallelizable_old_gc_time = stats._old_stats._cycle._avg_parallelizable_time + (stats._old_stats._cycle._sd_parallelizable_time * one_in_1000);
  const double serial_young_gc_time = stats._young_stats._cycle._avg_serial_time + (stats._young_stats._cycle._sd_serial_time * one_in_1000);
  const double parallelizable_young_gc_time = stats._young_stats._cycle._avg_parallelizable_time + (stats._young_stats._cycle._sd_parallelizable_time * one_in_1000);
  const double serial_gc_time = serial_old_gc_time + serial_young_gc_time;
  const double parallelizable_gc_time = parallelizable_old_gc_time + parallelizable_young_gc_time;
  const double gc_duration = serial_gc_time + parallelizable_gc_time;
  const double acceptable_gc_interval = gc_duration * ((assumed_throughput_drop_during_gc / acceptable_throughput_drop) - 1.0);
  const double time_until_gc = acceptable_gc_interval - time_since_last_gc;

  log_debug(gc, director)("Rule Major: Proactive, AcceptableGCInterval: %.3fs, TimeSinceLastGC: %.3fs, TimeUntilGC: %.3fs",
                          acceptable_gc_interval, time_since_last_gc, time_until_gc);

  return time_until_gc <= 0;
}

static GCCause::Cause make_minor_gc_decision(const ZDirectorStats& stats) {
  if (ZDriver::minor()->is_busy()) {
    return GCCause::_no_gc;
  }

  if (ZDriver::major()->is_busy() && !stats._old_stats._resize._is_active) {
    return GCCause::_no_gc;
  }

  if (rule_minor_timer(stats)) {
    return GCCause::_z_timer;
  }

  if (rule_minor_allocation_rate(stats)) {
    return GCCause::_z_allocation_rate;
  }

  if (rule_minor_high_usage(stats)) {
    return GCCause::_z_high_usage;
  }

  return GCCause::_no_gc;
}

static GCCause::Cause make_major_gc_decision(const ZDirectorStats& stats) {
  if (ZDriver::major()->is_busy()) {
    return GCCause::_no_gc;
  }

  if (rule_major_timer(stats)) {
    return GCCause::_z_timer;
  }

  if (rule_major_warmup(stats)) {
    return GCCause::_z_warmup;
  }

  if (rule_major_proactive(stats)) {
    return GCCause::_z_proactive;
  }

  return GCCause::_no_gc;
}

static ZWorkerResizeStats sample_worker_resize_stats(ZStatCycleStats& cycle_stats, ZStatWorkersStats& worker_stats, ZWorkers* workers) {
  ZLocker<ZLock> locker(workers->resizing_lock());

  if (!workers->is_active()) {
    // If the workers are not active, it isn't safe to read stats
    // from the stat_cycle, so return early.
    return {
      false, // _is_active
      0.0,   // _serial_gc_time_passed
      0.0,   // _parallel_gc_time_passed
      0      // _nworkers_current
    };
  }

  const double parallel_gc_duration_passed = worker_stats._accumulated_duration;
  const double parallel_gc_time_passed = worker_stats._accumulated_time;
  const double serial_gc_time_passed = cycle_stats._duration_since_start - parallel_gc_duration_passed;
  const uint active_nworkers = workers->active_workers();

  return {
    true,                    // _is_active
    serial_gc_time_passed,   // _serial_gc_time_passed
    parallel_gc_time_passed, // _parallel_gc_time_passed
    active_nworkers          // _nworkers_current
  };
}

// Output information for select_worker_threads
struct ZWorkerCounts {
  uint _young_workers;
  uint _old_workers;
};

enum class ZWorkerSelectionType {
  start_major,
  minor_during_old,
  normal
};

static ZWorkerCounts select_worker_threads(const ZDirectorStats& stats, uint young_workers, ZWorkerSelectionType type) {
  const uint active_young_workers = stats._young_stats._resize._nworkers_current;
  const uint active_old_workers = stats._old_stats._resize._nworkers_current;

  if (ZHeap::heap()->is_alloc_stalling()) {
    // Boost GC threads when stalling
    return {ZYoungGCThreads, ZOldGCThreads};
  } else if (active_young_workers + active_old_workers > ConcGCThreads) {
    // Threads are boosted, due to stalling recently; retain that boosting
    return {active_young_workers, active_old_workers};
  }

  const double young_to_old_ratio = calculate_young_to_old_worker_ratio(stats);
  uint old_workers = clamp(uint(young_workers * young_to_old_ratio), 1u, ZOldGCThreads);

  if (type != ZWorkerSelectionType::normal && old_workers + young_workers > ConcGCThreads) {
    // We need to somehow clamp the GC threads so the two generations don't exceed ConcGCThreads
    const double old_ratio = (young_to_old_ratio / (1.0 + young_to_old_ratio));
    const double young_ratio = 1.0 - old_ratio;
    const uint young_workers_clamped = clamp(uint(ConcGCThreads * young_ratio), 1u, ZYoungGCThreads);
    const uint old_workers_clamped = clamp(ConcGCThreads - young_workers_clamped, 1u, ZOldGCThreads);

    if (type == ZWorkerSelectionType::start_major) {
      // Adjust down the old workers so the next minor during major will be less sad
      old_workers = old_workers_clamped;
      // Since collecting the old generation depends on the initial young collection
      // finishing, we don't want it to have fewer workers than the old generation.
      young_workers = MAX2(old_workers, young_workers);
    } else if (type == ZWorkerSelectionType::minor_during_old) {
      // Adjust young and old workers for minor during old to fit within ConcGCThreads
      young_workers = young_workers_clamped;
      old_workers = old_workers_clamped;
    }
  }

  return {young_workers, old_workers};
}

static void adjust_gc(const ZDirectorStats& stats) {
  if (!UseDynamicNumberOfGCThreads) {
    return;
  }

  const ZWorkerResizeStats young_resize_stats = stats._young_stats._resize;
  const ZWorkerResizeStats old_resize_stats = stats._old_stats._resize;

  if (!young_resize_stats._is_active) {
    // Young generation collection is not running. We only resize the number
    // of threads when the young generation is running. The number of threads
    // for the old generation is modelled as a ratio of the number of threads
    // needed in the young generation. If we don't need to GC the young generation
    // at all, then we don't have anything to scale with, and the allocation
    // pressure on the GC can't be that high. If it is, a minor collection will
    // start, and inform us how to scale the old threads.
    return;
  }

  const ZDriverRequest request = rule_semi_hard_minor_allocation_rate_dynamic(stats,
                                                                              young_resize_stats._serial_gc_time_passed,
                                                                              young_resize_stats._parallel_gc_time_passed);
  if (request.cause() == GCCause::_no_gc) {
    // No urgency
    return;
  }

  uint desired_young_workers = MAX2(request.young_nworkers(), young_resize_stats._nworkers_current);

  if (desired_young_workers > young_resize_stats._nworkers_current) {
    // We need to increase workers
    const uint needed_young_increase = desired_young_workers - young_resize_stats._nworkers_current;
    // We want to increase by more than the minimum amount to ensure that
    // there are enough margins, but also to avoid too frequent resizing.
    const uint desired_young_increase = needed_young_increase * 2;
    desired_young_workers = MIN2(young_resize_stats._nworkers_current + desired_young_increase, ZYoungGCThreads);
  }

  const uint young_current_workers = young_resize_stats._nworkers_current;
  const uint old_current_workers = old_resize_stats._nworkers_current;

  const bool minor_during_old = old_resize_stats._is_active;
  ZWorkerSelectionType type = minor_during_old ? ZWorkerSelectionType::minor_during_old
                                               : ZWorkerSelectionType::normal;

  const ZWorkerCounts selection = select_worker_threads(stats, desired_young_workers, type);

  if (old_resize_stats._is_active && old_current_workers != selection._old_workers) {
    ZGeneration::old()->workers()->request_resize_workers(selection._old_workers);
  }
  if (young_current_workers != selection._young_workers) {
    ZGeneration::young()->workers()->request_resize_workers(selection._young_workers);
  }
}

static ZWorkerCounts initial_workers(const ZDirectorStats& stats, ZWorkerSelectionType type) {
  if (!UseDynamicNumberOfGCThreads) {
    return {ZYoungGCThreads, ZOldGCThreads};
  }

  const ZDriverRequest soft_request = rule_soft_minor_allocation_rate_dynamic(stats, 0.0 /* serial_gc_time_passed */, 0.0 /* parallel_gc_time_passed */);
  const ZDriverRequest hard_request = rule_hard_minor_allocation_rate_dynamic(stats, 0.0 /* serial_gc_time_passed */, 0.0 /* parallel_gc_time_passed */);
  const uint young_workers = MAX3(1u, soft_request.young_nworkers(), hard_request.young_nworkers());

  return select_worker_threads(stats, young_workers, type);
}

static void start_major_gc(const ZDirectorStats& stats, GCCause::Cause cause) {
  const ZWorkerCounts selection = initial_workers(stats, ZWorkerSelectionType::start_major);
  const ZDriverRequest request(cause, selection._young_workers, selection._old_workers);
  ZDriver::major()->collect(request);
}

static void start_minor_gc(const ZDirectorStats& stats, GCCause::Cause cause) {
  const ZWorkerSelectionType type = ZDriver::major()->is_busy()
      ? ZWorkerSelectionType::minor_during_old
      : ZWorkerSelectionType::normal;
  const ZWorkerCounts selection = initial_workers(stats, type);

  if (UseDynamicNumberOfGCThreads && ZDriver::major()->is_busy()) {
    const ZWorkerResizeStats old_resize_stats = stats._old_stats._resize;
    const uint old_current_workers = old_resize_stats._nworkers_current;

    if (old_current_workers != selection._old_workers) {
      ZGeneration::old()->workers()->request_resize_workers(selection._old_workers);
    }
  }

  const ZDriverRequest request(cause, selection._young_workers, 0);
  ZDriver::minor()->collect(request);
}

static bool start_gc(const ZDirectorStats& stats) {
  // Try start major collections first as they include a minor collection
  const GCCause::Cause major_cause = make_major_gc_decision(stats);
  if (major_cause != GCCause::_no_gc) {
    start_major_gc(stats, major_cause);
    return true;
  }

  const GCCause::Cause minor_cause = make_minor_gc_decision(stats);
  if (minor_cause != GCCause::_no_gc) {
    if (!ZDriver::major()->is_busy() && rule_major_allocation_rate(stats)) {
      // Merge minor GC into major GC
      start_major_gc(stats, GCCause::_z_allocation_rate);
    } else {
      start_minor_gc(stats, minor_cause);
    }

    return true;
  }

  return false;
}

void ZDirector::evaluate_rules() {
  ZLocker<ZConditionLock> locker(&_director->_monitor);
  _director->_monitor.notify();
}

bool ZDirector::wait_for_tick() {
  const uint64_t interval_ms = MILLIUNITS / DecisionHz;

  ZLocker<ZConditionLock> locker(&_monitor);

  if (_stopped) {
    // Stopped
    return false;
  }

  // Wait
  _monitor.wait(interval_ms);
  return true;
}

static ZDirectorHeapStats sample_heap_stats() {
  const ZHeap* const heap = ZHeap::heap();
  const ZCollectedHeap* const collected_heap = ZCollectedHeap::heap();
  return {
    heap->soft_max_capacity(),
    heap->used(),
    collected_heap->total_collections()
  };
}

// This function samples all the stat values used by the heuristics to compute what to do.
// This is where synchronization code goes to ensure that the values we read are valid.
static ZDirectorStats sample_stats() {
  ZGenerationYoung* young = ZGeneration::young();
  ZGenerationOld* old = ZGeneration::old();
  const ZStatMutatorAllocRateStats mutator_alloc_rate = ZStatMutatorAllocRate::stats();
  const ZDirectorHeapStats heap = sample_heap_stats();

  ZStatCycleStats young_cycle = young->stat_cycle()->stats();
  ZStatCycleStats old_cycle = old->stat_cycle()->stats();

  ZStatWorkersStats young_workers = young->stat_workers()->stats();
  ZStatWorkersStats old_workers = old->stat_workers()->stats();

  ZWorkerResizeStats young_resize = sample_worker_resize_stats(young_cycle, young_workers, young->workers());
  ZWorkerResizeStats old_resize = sample_worker_resize_stats(old_cycle, old_workers, old->workers());

  ZStatHeapStats young_stat_heap = young->stat_heap()->stats();
  ZStatHeapStats old_stat_heap = old->stat_heap()->stats();

  ZDirectorGenerationGeneralStats young_generation = { ZHeap::heap()->used_young(), 0 };
  ZDirectorGenerationGeneralStats old_generation = { ZHeap::heap()->used_old(), old->total_collections_at_start() };

  return {
    mutator_alloc_rate,
    heap,
    {
      young_cycle,
      young_workers,
      young_resize,
      young_stat_heap,
      young_generation
    },
    {
      old_cycle,
      old_workers,
      old_resize,
      old_stat_heap,
      old_generation
    }
  };
}

void ZDirector::run_thread() {
  // Main loop
  while (wait_for_tick()) {
    ZDirectorStats stats = sample_stats();
    if (!start_gc(stats)) {
      adjust_gc(stats);
    }
  }
}

void ZDirector::terminate() {
  ZLocker<ZConditionLock> locker(&_monitor);
  _stopped = true;
  _monitor.notify();
}
