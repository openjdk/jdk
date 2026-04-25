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

#include "runtime/atomicAccess.hpp"
#include "runtime/mutex.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/numberSeq.hpp"

class ShenandoahAllocationClock {
public:
  static jlong elapsed_counter() {
    return os::elapsed_counter();
  }

  static jlong elapsed_frequency() {
    return os::elapsed_frequency();
  }
};

template<typename Clock = ShenandoahAllocationClock>
class ShenandoahAllocRate {
  Atomic<size_t> _allocated_bytes_since_last_sample;
  Monitor _sample_lock;
  jlong _last_sample_time;

  // Keep track of SPIKE_ACCELERATION_SAMPLE_SIZE most recent spike allocation rate measurements. Note that it is
  // typical to experience a small spike following end of GC cycle, as mutator threads refresh their TLABs.  But
  // there is generally an abundance of memory at this time as well, so this will not generally trigger GC.
  uint _first_sample_index;
  uint _num_samples;
  uint _buffer_size;
  uint _recent_window_size;
  uint _momentary_sample_size;
  double _sample_period_seconds;

  double* const _rate_samples;
  double* const _rate_timestamps;

  bool _recompute;
  double _baseline_average;
  double _recent_average;
  double _momentary_average;
  double _acceleration;

public:
  explicit ShenandoahAllocRate(uint baseline_window_millis = ShenandoahAdaptiveSampleSizeSeconds,
                               uint recent_window_millis = ShenandoahRateAccelerationSampleSize,
                               uint momentary_window_millis = ShenandoahMomentaryAllocationRateSpikeSampleSize,
                               uint sample_period_millis = ShenandoahAdaptiveSampleFrequencyHz)
    : _allocated_bytes_since_last_sample(0)
    , _sample_lock(Mutex::nosafepoint - 2, "ShenandoahAllocSample_lock", true)
    , _last_sample_time(Clock::elapsed_counter())
    , _first_sample_index(0)
    , _num_samples(0)
    , _buffer_size(baseline_window_millis / sample_period_millis)
    , _recent_window_size(recent_window_millis / sample_period_millis)
    , _momentary_sample_size(momentary_window_millis / sample_period_millis)
    , _sample_period_seconds(sample_period_millis / 1000.0)
    , _rate_samples(NEW_C_HEAP_ARRAY(double, _buffer_size, mtGC))
    , _rate_timestamps(NEW_C_HEAP_ARRAY(double, _buffer_size, mtGC))
    , _recompute(false)
    , _baseline_average(0.0)
    , _recent_average(0.0)
    , _momentary_average(0.0)
    , _acceleration(0.0)
  {
  }

  ~ShenandoahAllocRate() {
    FREE_C_HEAP_ARRAY(double, _rate_samples);
    FREE_C_HEAP_ARRAY(double, _rate_timestamps);
  }

  void allocated(size_t allocated_bytes);
  void record_rate_sample(double timestamp, double rate);
  size_t accelerated_consumption(double& acceleration, double& current_rate, double time_delta);

  double average() {
    MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);
    // TODO: cache results
    return _baseline_average;
  }

  double upper_bound(const double standard_deviations) {
    MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);
    // TODO: compute standard deviation along with average
    return _baseline_average;
  }
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP