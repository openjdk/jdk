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
  size_t unsampled = _allocated_bytes_since_last_sample.add_then_fetch(allocated_bytes);
  if (unsampled < _minimum_sample_size) {
    // Not enough to sample yet
    return;
  }

  if (!_sample_lock.try_lock()) {
    // Another thread has the lock and will take the sample
    return;
  }

  unsampled = _allocated_bytes_since_last_sample.load_relaxed();
  if (unsampled < _minimum_sample_size) {
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

  _accumulated_bytes += unsampled;
  _accumulated_duration += elapsed;

  if (now - _last_cumulative_sample_time > _cumulative_sample_period) {
    _sampled_rates.add(static_cast<double>(_accumulated_bytes) / _accumulated_duration * Clock::elapsed_frequency());
    _last_cumulative_sample_time = now;
    _accumulated_bytes = 0;
    _accumulated_duration = 0;
  }

  record_rate_sample(_last_sample_time / Clock::elapsed_frequency(), static_cast<double>(unsampled) / elapsed * Clock::elapsed_frequency());

  _sample_lock.unlock();
}

template<typename Clock>
void ShenandoahAllocRate<Clock>::record_rate_sample(double timestamp, double rate) {
  const uint new_sample_index = (_first_sample_index + _num_samples) % _buffer_size;
  _rate_timestamps[new_sample_index] = timestamp;
  _rate_samples[new_sample_index] = rate;
  if (_num_samples == _buffer_size) {
    _first_sample_index++;
    if (_first_sample_index == _buffer_size) {
      _first_sample_index = 0;
    }
  } else {
    _num_samples++;
  }
}

template<typename Clock>
size_t ShenandoahAllocRate<Clock>::accelerated_consumption(double& acceleration, double& current_rate,
                                                           double baseline_bytes_per_second, double time_delta) {

  MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);

  double x_sum = 0.0;
  double y_sum = 0.0;
  double xy_sum = 0.0;
  double x2_sum = 0.0;
  double weighted_y_sum = 0;
  double total_weight = 0;
  bool momentary_done = false;
  double momentary_rate = 0.0;
  uint momentary_oldest_index = 0;

  assert(_num_samples > 0, "At minimum, we should have sample from this period");
  const uint count = MIN2(ShenandoahRateAccelerationSampleSize, _num_samples);
  const uint newest = (_first_sample_index + _num_samples - 1) % _buffer_size;
  uint index = newest;
  for (uint i = 0; i < count; i++) {
    const uint preceding_index = index == 0 ? _buffer_size - 1 : index - 1;

    x_sum += _rate_timestamps[index];
    y_sum += _rate_samples[index];
    x2_sum += _rate_timestamps[index] * _rate_timestamps[index];
    xy_sum +=  _rate_timestamps[index] * _rate_samples[index];

    if (i != count - 1) {
      // oldest value will not have a preceding element to compute weight, so skip it.
      const double sample_weight = _rate_timestamps[index] - _rate_timestamps[preceding_index];
      weighted_y_sum += _rate_samples[index] * sample_weight;
      total_weight += sample_weight;
    }

    if (!momentary_done && i >= ShenandoahMomentaryAllocationRateSpikeSampleSize) {
      momentary_rate = total_weight > 0 ? weighted_y_sum / total_weight: 0;
      momentary_done = true;
      momentary_oldest_index = preceding_index;
    }
    index = preceding_index;
  }

  if (log_is_enabled(Debug, gc, ergo)) {
    const uint latest = _rate_timestamps[newest];
    const uint oldest = _rate_timestamps[index];
    const uint oldest_momemntary = _rate_timestamps[momentary_oldest_index];
    log_debug(gc, ergo)("Momentary samples span last: %.2fs, Acceleration samples span last %.2f",
      static_cast<double>(latest - oldest_momemntary) / Clock::elapsed_frequency(),
      static_cast<double>(latest - oldest) / Clock::elapsed_frequency());
  }

  const double weighted_average_alloc = total_weight > 0 ? weighted_y_sum / total_weight: 0;

  // By default, use momentary_rate for current rate and zero acceleration. Overwrite iff best-fit line has positive slope.
  current_rate = momentary_rate;
  acceleration = 0.0;
  if (_num_samples >= ShenandoahRateAccelerationSampleSize
      && weighted_average_alloc >= baseline_bytes_per_second)  {
    // If the average rate across the acceleration samples is below the overall average, this sample is not eligible to
    //  represent acceleration of allocation rate.  We may just be catching up with allocations after a lull.

    // Find the best-fit least-squares linear representation of rate vs time
    const double slope = (ShenandoahRateAccelerationSampleSize * xy_sum - x_sum * y_sum)
                       / (ShenandoahRateAccelerationSampleSize * x2_sum - x_sum * x_sum);
    const double y_intercept = (y_sum - slope * x_sum) / ShenandoahRateAccelerationSampleSize;

    if (slope > 0) {
      const double proposed_current_rate = slope * _rate_timestamps[newest] + y_intercept;
      acceleration = slope;
      current_rate = proposed_current_rate;
    }
  }

  const size_t words_to_be_consumed = static_cast<size_t>(current_rate * time_delta + 0.5 * acceleration * time_delta * time_delta);
  return words_to_be_consumed;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP_INLINE_HPP
