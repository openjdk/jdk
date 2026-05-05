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

#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "gc/shenandoah/shenandoahWeightedSeq.hpp"
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

template<typename Clock = ShenandoahAllocationClock>
class ShenandoahAllocRate {
  Atomic<size_t> _allocated_bytes_since_last_sample;
  Monitor _sample_lock;
  jlong _last_sample_time;
  size_t _minimum_sample_size;


  ShenandoahWeightedSeq _baseline;
  ShenandoahWeightedSeq _recent;
  ShenandoahWeightedSeq _momentary;

public:
  explicit ShenandoahAllocRate(const uint minimum_sample_size = 1024 * 1024,
                               const uint baseline_window_size = ShenandoahAllocRateSampleWindow,
                               const uint recent_window_size = ShenandoahRecentAllocRateSampleWindow,
                               const uint momentary_window_size = ShenandoahMomentaryAllocRateSampleWindow)
    : _allocated_bytes_since_last_sample(0)
    , _sample_lock(Mutex::nosafepoint - 2, "ShenandoahAllocSample_lock", true)
    , _last_sample_time(Clock::elapsed_counter())
    , _minimum_sample_size(minimum_sample_size)
    , _baseline(baseline_window_size)
    , _recent(recent_window_size)
    , _momentary(momentary_window_size)
  {
  }

  void set_minimum_sample_size(const size_t minimum_sample_size) {
    _minimum_sample_size = minimum_sample_size;
  }

  void allocated(size_t allocated_bytes);

  size_t accelerated_consumption(double& acceleration, double& current_rate, double time_delta);

  double average() {
    MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);
    return _baseline.weighted_average();
  }

  double upper_bound(const double standard_deviations) {
    MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);
    return _baseline.weighted_average() + (standard_deviations * _baseline.weighted_sd());
  }
};

typedef ShenandoahAllocRate<> ShenandoahAllocationRate;

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP
