/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zDirector.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zUtils.hpp"
#include "logging/log.hpp"

const double ZDirector::one_in_1000 = 3.290527;

ZDirector::ZDirector() :
    _metronome(ZStatAllocRate::sample_hz) {
  set_name("ZDirector");
  create_and_start();
}

void ZDirector::sample_allocation_rate() const {
  // Sample allocation rate. This is needed by rule_allocation_rate()
  // below to estimate the time we have until we run out of memory.
  const double bytes_per_second = ZStatAllocRate::sample_and_reset();

  log_debug(gc, alloc)("Allocation Rate: %.3fMB/s, Avg: %.3f(+/-%.3f)MB/s",
                       bytes_per_second / M,
                       ZStatAllocRate::avg() / M,
                       ZStatAllocRate::avg_sd() / M);
}

bool ZDirector::rule_timer() const {
  if (ZCollectionInterval == 0) {
    // Rule disabled
    return false;
  }

  // Perform GC if timer has expired.
  const double time_since_last_gc = ZStatCycle::time_since_last();
  const double time_until_gc = ZCollectionInterval - time_since_last_gc;

  log_debug(gc, director)("Rule: Timer, Interval: %us, TimeUntilGC: %.3fs",
                          ZCollectionInterval, time_until_gc);

  return time_until_gc <= 0;
}

bool ZDirector::rule_warmup() const {
  if (ZStatCycle::is_warm()) {
    // Rule disabled
    return false;
  }

  // Perform GC if heap usage passes 10/20/30% and no other GC has been
  // performed yet. This allows us to get some early samples of the GC
  // duration, which is needed by the other rules.
  const size_t soft_max_capacity = ZHeap::heap()->soft_max_capacity();
  const size_t used = ZHeap::heap()->used();
  const double used_threshold_percent = (ZStatCycle::nwarmup_cycles() + 1) * 0.1;
  const size_t used_threshold = soft_max_capacity * used_threshold_percent;

  log_debug(gc, director)("Rule: Warmup %.0f%%, Used: " SIZE_FORMAT "MB, UsedThreshold: " SIZE_FORMAT "MB",
                          used_threshold_percent * 100, used / M, used_threshold / M);

  return used >= used_threshold;
}

bool ZDirector::rule_allocation_rate() const {
  if (!ZStatCycle::is_normalized_duration_trustable()) {
    // Rule disabled
    return false;
  }

  // Perform GC if the estimated max allocation rate indicates that we
  // will run out of memory. The estimated max allocation rate is based
  // on the moving average of the sampled allocation rate plus a safety
  // margin based on variations in the allocation rate and unforeseen
  // allocation spikes.

  // Calculate amount of free memory available to Java threads. Note that
  // the heap reserve is not available to Java threads and is therefore not
  // considered part of the free memory.
  const size_t soft_max_capacity = ZHeap::heap()->soft_max_capacity();
  const size_t max_reserve = ZHeap::heap()->max_reserve();
  const size_t used = ZHeap::heap()->used();
  const size_t free_with_reserve = soft_max_capacity - MIN2(soft_max_capacity, used);
  const size_t free = free_with_reserve - MIN2(free_with_reserve, max_reserve);

  // Calculate time until OOM given the max allocation rate and the amount
  // of free memory. The allocation rate is a moving average and we multiply
  // that with an allocation spike tolerance factor to guard against unforeseen
  // phase changes in the allocate rate. We then add ~3.3 sigma to account for
  // the allocation rate variance, which means the probability is 1 in 1000
  // that a sample is outside of the confidence interval.
  const double max_alloc_rate = (ZStatAllocRate::avg() * ZAllocationSpikeTolerance) + (ZStatAllocRate::avg_sd() * one_in_1000);
  const double time_until_oom = free / (max_alloc_rate + 1.0); // Plus 1.0B/s to avoid division by zero

  // Calculate max duration of a GC cycle. The duration of GC is a moving
  // average, we add ~3.3 sigma to account for the GC duration variance.
  const AbsSeq& duration_of_gc = ZStatCycle::normalized_duration();
  const double max_duration_of_gc = duration_of_gc.davg() + (duration_of_gc.dsd() * one_in_1000);

  // Calculate time until GC given the time until OOM and max duration of GC.
  // We also deduct the sample interval, so that we don't overshoot the target
  // time and end up starting the GC too late in the next interval.
  const double sample_interval = 1.0 / ZStatAllocRate::sample_hz;
  const double time_until_gc = time_until_oom - max_duration_of_gc - sample_interval;

  log_debug(gc, director)("Rule: Allocation Rate, MaxAllocRate: %.3fMB/s, Free: " SIZE_FORMAT "MB, MaxDurationOfGC: %.3fs, TimeUntilGC: %.3fs",
                          max_alloc_rate / M, free / M, max_duration_of_gc, time_until_gc);

  return time_until_gc <= 0;
}

bool ZDirector::rule_proactive() const {
  if (!ZProactive || !ZStatCycle::is_warm()) {
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
  const size_t used_after_last_gc = ZStatHeap::used_at_relocate_end();
  const size_t used_increase_threshold = ZHeap::heap()->soft_max_capacity() * 0.10; // 10%
  const size_t used_threshold = used_after_last_gc + used_increase_threshold;
  const size_t used = ZHeap::heap()->used();
  const double time_since_last_gc = ZStatCycle::time_since_last();
  const double time_since_last_gc_threshold = 5 * 60; // 5 minutes
  if (used < used_threshold && time_since_last_gc < time_since_last_gc_threshold) {
    // Don't even consider doing a proactive GC
    log_debug(gc, director)("Rule: Proactive, UsedUntilEnabled: " SIZE_FORMAT "MB, TimeUntilEnabled: %.3fs",
                            (used_threshold - used) / M,
                            time_since_last_gc_threshold - time_since_last_gc);
    return false;
  }

  const double assumed_throughput_drop_during_gc = 0.50; // 50%
  const double acceptable_throughput_drop = 0.01;        // 1%
  const AbsSeq& duration_of_gc = ZStatCycle::normalized_duration();
  const double max_duration_of_gc = duration_of_gc.davg() + (duration_of_gc.dsd() * one_in_1000);
  const double acceptable_gc_interval = max_duration_of_gc * ((assumed_throughput_drop_during_gc / acceptable_throughput_drop) - 1.0);
  const double time_until_gc = acceptable_gc_interval - time_since_last_gc;

  log_debug(gc, director)("Rule: Proactive, AcceptableGCInterval: %.3fs, TimeSinceLastGC: %.3fs, TimeUntilGC: %.3fs",
                          acceptable_gc_interval, time_since_last_gc, time_until_gc);

  return time_until_gc <= 0;
}

bool ZDirector::rule_high_usage() const {
  // Perform GC if the amount of free memory is 5% or less. This is a preventive
  // meassure in the case where the application has a very low allocation rate,
  // such that the allocation rate rule doesn't trigger, but the amount of free
  // memory is still slowly but surely heading towards zero. In this situation,
  // we start a GC cycle to avoid a potential allocation stall later.

  // Calculate amount of free memory available to Java threads. Note that
  // the heap reserve is not available to Java threads and is therefore not
  // considered part of the free memory.
  const size_t soft_max_capacity = ZHeap::heap()->soft_max_capacity();
  const size_t max_reserve = ZHeap::heap()->max_reserve();
  const size_t used = ZHeap::heap()->used();
  const size_t free_with_reserve = soft_max_capacity - MIN2(soft_max_capacity, used);
  const size_t free = free_with_reserve - MIN2(free_with_reserve, max_reserve);
  const double free_percent = percent_of(free, soft_max_capacity);

  log_debug(gc, director)("Rule: High Usage, Free: " SIZE_FORMAT "MB(%.1f%%)",
                          free / M, free_percent);

  return free_percent <= 5.0;
}

GCCause::Cause ZDirector::make_gc_decision() const {
  // Rule 0: Timer
  if (rule_timer()) {
    return GCCause::_z_timer;
  }

  // Rule 1: Warmup
  if (rule_warmup()) {
    return GCCause::_z_warmup;
  }

  // Rule 2: Allocation rate
  if (rule_allocation_rate()) {
    return GCCause::_z_allocation_rate;
  }

  // Rule 3: Proactive
  if (rule_proactive()) {
    return GCCause::_z_proactive;
  }

  // Rule 4: High usage
  if (rule_high_usage()) {
    return GCCause::_z_high_usage;
  }

  // No GC
  return GCCause::_no_gc;
}

void ZDirector::run_service() {
  // Main loop
  while (_metronome.wait_for_tick()) {
    sample_allocation_rate();
    const GCCause::Cause cause = make_gc_decision();
    if (cause != GCCause::_no_gc) {
      ZCollectedHeap::heap()->collect(cause);
    }
  }
}

void ZDirector::stop_service() {
  _metronome.stop();
}
