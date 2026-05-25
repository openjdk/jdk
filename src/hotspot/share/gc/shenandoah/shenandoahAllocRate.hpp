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

#include "gc/shenandoah/shenandoahWeightedSeq.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
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
  Atomic<size_t> _allocated_bytes_since_last_sample;
  Atomic<size_t> _minimum_sample_size; // bytes, read by mutator, updated by gc
  jlong _last_sample_time;

  ShenandoahWeightedSeq _baseline;
  ShenandoahWeightedSeq _recent;
  ShenandoahWeightedSeq _momentary;

public:
  explicit ShenandoahAllocRate(const uint minimum_sample_size = ALLOC_SAMPLE_MIN,
                               const uint baseline_window_size = ShenandoahAllocRateSampleWindow,
                               const uint recent_window_size = ShenandoahRecentAllocRateSampleWindow,
                               const uint momentary_window_size = ShenandoahMomentaryAllocRateSampleWindow)
    : _sample_lock(Mutex::nosafepoint - 2, "ShenandoahAllocSample_lock", true)
    , _allocated_bytes_since_last_sample(0)
    , _minimum_sample_size(minimum_sample_size)
    , _last_sample_time(Clock::elapsed_counter())
    , _baseline(baseline_window_size)
    , _recent(recent_window_size)
    , _momentary(momentary_window_size)
  {
  }

  // Update minimum sample size based on the given available bytes
  void update_minimum_sample_size(size_t available);

  // Set minimum sample size in bytes
  void set_minimum_sample_size(const size_t minimum_sample_size) {
    _minimum_sample_size.store_relaxed(minimum_sample_size);
  }

  // Indicate that this many bytes have been allocated (by the mutator).
  void allocated(size_t allocated_bytes);

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
  double upper_bound_no_lock(const double standard_deviations) const {
    assert(_sample_lock.is_locked(), "Caller must hold lock");
    return _baseline.weighted_average() + standard_deviations * _baseline.weighted_sd();
  }
};

typedef ShenandoahAllocRate<> ShenandoahAllocationRate;

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP
