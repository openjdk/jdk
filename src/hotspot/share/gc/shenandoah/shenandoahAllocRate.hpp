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
  double _sample_period_seconds;

  TruncatedSeq _baseline;
  TruncatedSeq _recent;
  TruncatedSeq _momentary;
public:
  explicit ShenandoahAllocRate(uint baseline_window_millis = ShenandoahAdaptiveSampleSizeSeconds,
                               uint recent_window_millis = ShenandoahRateAccelerationSampleSize,
                               uint momentary_window_millis = ShenandoahMomentaryAllocationRateSpikeSampleSize,
                               uint sample_period_millis = ShenandoahAdaptiveSampleFrequencyHz)
    : _allocated_bytes_since_last_sample(0)
    , _sample_lock(Mutex::nosafepoint - 2, "ShenandoahAllocSample_lock", true)
    , _last_sample_time(Clock::elapsed_counter())
    , _sample_period_seconds(sample_period_millis / 1000.0)
    , _baseline(baseline_window_millis / sample_period_millis)
    , _recent(recent_window_millis / sample_period_millis)
    , _momentary(momentary_window_millis / sample_period_millis)
  {
  }

  void allocated(size_t allocated_bytes);
  void record_rate_sample(double rate);
  size_t accelerated_consumption(double& acceleration, double& current_rate, double time_delta);

  double average() {
    MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);
    return _baseline.avg();
  }

  double upper_bound(const double standard_deviations) {
    MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);
    return _baseline.avg() + (standard_deviations * _baseline.sd());
  }
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP