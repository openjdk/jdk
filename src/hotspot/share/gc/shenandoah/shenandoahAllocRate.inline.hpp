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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP_INLINE_HPP

#include "gc/shenandoah/shenandoahAllocRate.hpp"

#include "logging/log.hpp"

template<typename Clock>
void ShenandoahAllocRate<Clock>::allocated(const size_t allocated_bytes) {
  _allocated_bytes_since_last_sample.add_then_fetch(allocated_bytes);
}

template<typename Clock>
void ShenandoahAllocRate<Clock>::record_sample() {
  const size_t unsampled = _allocated_bytes_since_last_sample.load_relaxed();
  const jlong now = Clock::elapsed_counter();
  const double elapsed = static_cast<double>(now - _last_sample_time) / Clock::elapsed_frequency();

  _last_sample_time = now;

  // We are recording this sample, deduct it from the counter. It may be increased
  // concurrently by other threads, so use an atomic access.
  _allocated_bytes_since_last_sample.sub_then_fetch(unsampled);

  auto rate_seconds = static_cast<double>(unsampled) / elapsed;
  record_rate_sample(rate_seconds);
}

template<typename Clock>
void ShenandoahAllocRate<Clock>::record_rate_sample(double rate) {
  MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);

  _baseline.add(rate);
  _recent.add(rate);
  _momentary.add(rate);
}

template<typename Clock>
size_t ShenandoahAllocRate<Clock>::accelerated_consumption(double& acceleration, double& current_rate, double time_delta) {

  if (time_delta <= 0.0) {
    log_warning(gc, sampling)("time_delta is: %.3f", time_delta);
    return 0;
  }

  MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);

  // By default, use momentary_rate for current rate and zero acceleration. Overwrite iff best-fit line has positive slope.
  current_rate = _momentary.avg();
  acceleration = 0.0;
  if (_recent.avg() > _baseline.avg())  {
    // If the average rate across the acceleration samples is below the overall average, this sample is not eligible to
    // represent acceleration of allocation rate. We may just be catching up with allocations after a lull.
    double slope(0), intercept(0);
    _recent.fit_line(slope, intercept);
    if (slope > 0) {
      acceleration = slope / _sample_period_seconds;
      current_rate = slope * (_recent.num() - 1) + intercept;
    }
  }

  // How much do we expect to consume at the current rate and predicted acceleration
  return static_cast<size_t>(current_rate * time_delta + 0.5 * acceleration * time_delta * time_delta);
}

inline void ShenandoahAllocationRateThread::stop_service() {
  log_debug(gc, thread)("%s: Stop requested.", name());
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP_INLINE_HPP
