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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP

#include "gc/shenandoah/shenandoahStripedCounter.hpp"
#include "gc/shenandoah/shenandoahWeightedSeq.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/task.hpp"
#include "utilities/globalDefinitions.hpp"

class ShenandoahAllocationClock {
public:
  static jlong elapsed_counter() {
    return os::elapsed_counter();
  }

  static jlong elapsed_frequency() {
    return os::elapsed_frequency();
  }
};

// Snapshot values used by heuristic triggers to avoid lock contention
struct ShenandoahAnticipatedConsumption {
  template<typename Clock> friend class ShenandoahAllocRate;
  explicit ShenandoahAnticipatedConsumption(double duration_seconds)
    : _duration_seconds(duration_seconds)
    , _baseline(0.0)
    , _momentary(0.0)
    , _acceleration(0.0)
    , _predicted_rate(0.0) {
  }

  // Anticipated duration in seconds of next gc cycle
  double duration_seconds() const {
    return _duration_seconds;
  }

  // Consumption in bytes based on baseline allocation rate for the next gc cycle
  size_t baseline_consumption() const;
  double baseline_rate() const {
    return _baseline;
  }

  // Consumption in bytes based on momentary allocation rate for the next gc cycle
  size_t momentary_consumption() const;
  double momentary_rate() const {
    return _momentary;
  }

  // Consumption in bytes based on an accelerating allocation rate for the next gc cycle
  size_t accelerated_consumption() const;

  // The acceleration of the allocation rate (based on slope of linear regression)
  double acceleration() const {
    return _acceleration;
  }

  // Predicated allocation rate based on weighted linear regression
  double predicted_rate() const {
    return _predicted_rate;
  }

private:
  double _duration_seconds;
  double _baseline;
  double _momentary;
  double _acceleration;
  double _predicted_rate;
};

// This class tracks three moving averages of the allocation rate:
//  1. Momentary: this is the shortest and acts as a sort of 'spike' detector
//  2. Recent: larger than momentary, these samples are used to detect 'acceleration' of the rate
//  3. Baseline: the largest sample window, this is meant to establish the baseline allocation rate
//
// Samples are taken whenever the accumulating count of bytes allocated exceeds the
// minimum sample size. The minimum sample size is generally derived from the heap
// capacity. The thinking is that larger heaps require less frequent sampling. Note
// that as the allocation rate increases, the timeliness of the averages and other
// estimates increases.
template<typename Clock = ShenandoahAllocationClock>
class ShenandoahAllocRate {
  static constexpr size_t ALLOC_SAMPLE_PORTION = 128;
  static constexpr size_t ALLOC_SAMPLE_MIN = M;
  static constexpr size_t ALLOC_SAMPLE_MAX = G;

  PaddedMonitor _sample_lock;
  ShenandoahStripedCounter _unsampled;
  // Packed minimum_sample_size and log_per_stripe_threshold for one alloc-path load.
  Atomic<uint64_t> _sample_params;
  jlong _last_sample_time;

  static uint64_t encode_sample_params(const uint32_t minimum_sample_size, const uint32_t log_per_stripe_threshold) {
    return (static_cast<uint64_t>(log_per_stripe_threshold) << 32) |
           minimum_sample_size;
  }

  static size_t decode_min_sample_size(const uint64_t params) {
    return static_cast<uint32_t>(params);
  }

  static uint32_t decode_log_per_stripe_threshold(const uint64_t params) {
    return static_cast<uint32_t>(params >> 32);
  }

  void maybe_take_sample(size_t minimum_sample_size, size_t striped_unsampled);

  ShenandoahWeightedSeq _baseline;
  ShenandoahWeightedSeq _recent;
  ShenandoahWeightedSeq _momentary;

public:
  explicit ShenandoahAllocRate(const uint minimum_sample_size = ALLOC_SAMPLE_MIN,
                               const uint baseline_window_size = ShenandoahAllocRateSampleWindow,
                               const uint recent_window_size = ShenandoahRecentAllocRateSampleWindow,
                               const uint momentary_window_size = ShenandoahMomentaryAllocRateSampleWindow)
    : _sample_lock(Mutex::nosafepoint - 2, "ShenandoahAllocSample_lock", true)
    , _last_sample_time(Clock::elapsed_counter())
    , _baseline(baseline_window_size)
    , _recent(recent_window_size)
    , _momentary(momentary_window_size)
  {
    set_minimum_sample_size(minimum_sample_size);
  }

  // Update minimum sample size based on the given available bytes
  void update_minimum_sample_size(size_t available);

  // Set minimum sample size and its per-stripe trigger shift.
  void set_minimum_sample_size(size_t minimum_sample_size);

  // Indicate that this many bytes have been allocated (by the mutator).
  void allocated(size_t allocated_bytes);

  // Shenandoah currently evaluates triggers on a dedicated thread to lighten the workload
  // for allocators. However, this means that when there isn't enough allocations to update
  // the rate, the heuristics will continue to see a high allocation rate. This method is
  // for heuristics to periodically force the rate to update and decay the allocation rate.
  void force_update();

  // Returns a snapshot of the parameters necessary to evaluate allocation rate triggers.
  // Note that momentary consumption and accelerated consumption may both be zero, but may
  // not both be non-zero. The `time_delta` parameter is the anticipated duration of the
  // next gc cycle. The `standard_deviations` parameter is the margin of error applied to
  // the baseline allocation rate expressed as a multiple of the standard deviation.
  ShenandoahAnticipatedConsumption snapshot(double time_delta, double standard_deviations);

  // Returns the weighted average of the samples.
  double weighted_average() {
    MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);
    return _baseline.weighted_average();
  }

  // Returns the upper bound of the confidence interval about the mean in terms of the given deviation.
  double upper_bound(const double standard_deviations) {
    MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);
    return upper_bound_no_lock(standard_deviations);
  }

private:
  // Log2 of the per-stripe trigger threshold.
  uint32_t log_per_stripe_threshold_for(size_t minimum_sample_size) const;

  // Fast, lock-free: did this add carry the calling thread's stripe across a per-stripe threshold
  // multiple? The threshold is a power of two, so a crossing is a change in the bits above it.
  static bool striped_threshold_exceeded(size_t striped_unsampled, size_t previous_striped_unsampled, uint32_t log_per_stripe_threshold) {
    return (striped_unsampled >> log_per_stripe_threshold) > (previous_striped_unsampled >> log_per_stripe_threshold);
  }

  // Whether the unsampled bytes are still below the sampling floor. Must be called under the sample
  // lock: drains only happen under the lock, so reading the live stripe value and sum() here filters
  // out false positives from a concurrent drain that already reset the counter.
  bool unsampled_below_floor(size_t minimum_sample_size, size_t striped_unsampled) const {
    assert(_sample_lock.owned_by_self(), "Caller must hold lock");
    return (_unsampled.num_stripes() > 1 && _unsampled.current_stripe_value() < striped_unsampled) ||
           _unsampled.sum() < minimum_sample_size;
  }

  // Record the sample under the sample lock
  void take_sample(jlong now, jlong elapsed, size_t unsampled);

  double upper_bound_no_lock(const double standard_deviations) const {
    assert(_sample_lock.owned_by_self(), "Caller must hold lock");
    return _baseline.weighted_average() + standard_deviations * _baseline.weighted_sd();
  }
};

typedef ShenandoahAllocRate<> ShenandoahAllocationRate;

// See description of `force_update`
class ShenandoahDecayAllocRate : public PeriodicTask {
  static constexpr size_t DECAY_INTERVAL_MS = 100;
  ShenandoahAllocationRate* _rate;
public:
  ShenandoahDecayAllocRate(ShenandoahAllocationRate* rate) : PeriodicTask(DECAY_INTERVAL_MS), _rate(rate) {}
  void task() override;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP
