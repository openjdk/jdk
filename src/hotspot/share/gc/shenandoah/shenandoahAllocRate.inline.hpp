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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_INLINE_HPP

#include "gc/shenandoah/shenandoahAllocRate.hpp"

#include "logging/log.hpp"

#include <cmath>

template<typename Clock>
void ShenandoahAllocRate<Clock>::update_minimum_sample_size(const size_t available) {
  const size_t min_sample_size = clamp(available / ALLOC_SAMPLE_PORTION, ALLOC_SAMPLE_MIN, ALLOC_SAMPLE_MAX);
  log_info(gc, ergo)("Adjust minimum allocation sample size to: " PROPERFMT, PROPERFMTARGS(min_sample_size));
  set_minimum_sample_size(min_sample_size);
}

template<typename Clock>
void ShenandoahAllocRate<Clock>::allocated(const size_t allocated_bytes) {
  size_t unsampled = _allocated_bytes_since_last_sample.add_then_fetch(allocated_bytes);
  const size_t minimum_sample_size = _minimum_sample_size.load_acquire();
  if (unsampled < minimum_sample_size) {
    // Not enough to sample yet
    return;
  }

  if (!_sample_lock.try_lock()) {
    // Another thread has the lock and will take the sample
    return;
  }

  unsampled = _allocated_bytes_since_last_sample.load_relaxed();
  if (unsampled < minimum_sample_size) {
    // Another thread has sampled and reset the allocated bytes under the lock
    _sample_lock.unlock();
    return;
  }

  const jlong now = Clock::elapsed_counter();
  const jlong elapsed = now - _last_sample_time;

  if (elapsed <= 0) {
    // Avoid sampling nonsense allocation rates
    _sample_lock.unlock();
    return;
  }

  _last_sample_time = now;

  // We are recording this sample, deduct it from the counter. It may be increased
  // concurrently by other threads outside the lock, so we still use an atomic access.
  _allocated_bytes_since_last_sample.sub_then_fetch(unsampled);

  auto timestamp = static_cast<double>(_last_sample_time) / Clock::elapsed_frequency();
  auto rate_seconds = static_cast<double>(unsampled) * Clock::elapsed_frequency() / elapsed;

  log_debug(gc, sampling)("Recorded %.3f/s at %.3fs", rate_seconds, timestamp);
  _baseline.add(timestamp, rate_seconds);
  _recent.add(timestamp, rate_seconds);
  _momentary.add(timestamp, rate_seconds);

  _sample_lock.unlock();
}

template<typename Clock>
size_t ShenandoahAllocRate<Clock>::accelerated_consumption(double& acceleration, double& current_rate, double time_delta) {
  MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);

  acceleration = 0.0;
  current_rate = _momentary.weighted_average();
  if (_recent.weighted_average() > _baseline.weighted_average()) {
    const double slope = _recent.slope();
    if (slope > 0 && std::isfinite(slope)) {
      acceleration = slope;
      current_rate = _recent.predict_y(_recent.last());
    }
  }

  return static_cast<size_t>(current_rate * time_delta + 0.5 * acceleration * time_delta * time_delta);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_INLINE_HPP
