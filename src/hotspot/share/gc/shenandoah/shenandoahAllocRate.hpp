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
private:
  static constexpr size_t MINIMUM_SAMPLE_SIZE = 1024 * 1024;

  Atomic<size_t> _allocated_bytes_since_last_sample;
  Monitor _sample_lock;
  jlong _last_sample_time;
  TruncatedSeq _sampled_times;
  TruncatedSeq _sampled_bytes;
  TruncatedSeq _sampled_rates;
  size_t _minimum_sample_size;

  // Keep track of SPIKE_ACCELERATION_SAMPLE_SIZE most recent spike allocation rate measurements. Note that it is
  // typical to experience a small spike following end of GC cycle, as mutator threads refresh their TLABs.  But
  // there is generally an abundance of memory at this time as well, so this will not generally trigger GC.
  uint _buffer_size;
  uint _first_sample_index;
  uint _num_samples;

  double* const _rate_samples;
  double* const _rate_timestamps;

public:
  explicit ShenandoahAllocRate(size_t minimum_sample_size = MINIMUM_SAMPLE_SIZE)
    : _allocated_bytes_since_last_sample(0)
    , _sample_lock(Mutex::nosafepoint - 2, "ShenandoahAllocSample_lock", true)
    , _last_sample_time(0)
    , _sampled_times(100)
    , _sampled_bytes(100)
    , _sampled_rates(100)
    , _minimum_sample_size(minimum_sample_size)
    , _buffer_size(MAX2(ShenandoahRateAccelerationSampleSize, 1+ShenandoahMomentaryAllocationRateSpikeSampleSize))
    , _first_sample_index(0)
    , _num_samples(0)
    , _rate_samples(NEW_C_HEAP_ARRAY(double, _buffer_size, mtGC))
    , _rate_timestamps(NEW_C_HEAP_ARRAY(double, _buffer_size, mtGC))
  {
  }

  ~ShenandoahAllocRate() {
    FREE_C_HEAP_ARRAY(double, _rate_samples);
    FREE_C_HEAP_ARRAY(double, _rate_timestamps);
  }

  void set_minimum_sample_size(const size_t minimum_sample_size) {
    _minimum_sample_size = minimum_sample_size;
  }

  void allocated(const size_t allocated_bytes) {
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

    _sampled_times.add(elapsed);
    _sampled_bytes.add(unsampled);

    tty->print_cr("Sampling: %zu, Elapsed: %zu", unsampled, elapsed);

    const double total_time  = _sampled_times.sum();
    const double total_bytes = _sampled_bytes.sum();
    const double elapsed_seconds = total_time / Clock::elapsed_frequency();
    const double bytes_per_second = total_bytes / elapsed_seconds;

    tty->print_cr("bytes_per_second: %.2f", bytes_per_second);

    _sampled_rates.add(bytes_per_second);
    record_rate_sample(_last_sample_time, unsampled / elapsed / Clock::elapsed_frequency());

    _sample_lock.unlock();
  }

  void record_rate_sample(double timestamp, double rate) {
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

  size_t accelerated_consumption(double& acceleration, double& current_rate,
                                 double avg_alloc_rate_words_per_second, double time_delta) const {
    double *x_array = (double *) alloca(ShenandoahRateAccelerationSampleSize * sizeof(double));
    double *y_array = (double *) alloca(ShenandoahRateAccelerationSampleSize * sizeof(double));
    double x_sum = 0.0;
    double y_sum = 0.0;

    assert(_num_samples > 0, "At minimum, we should have sample from this period");

    double weighted_average_alloc;
    if (_num_samples >= ShenandoahRateAccelerationSampleSize) {
      double weighted_y_sum = 0;
      double total_weight = 0;
      uint delta = _num_samples - ShenandoahRateAccelerationSampleSize;
      for (uint i = 0; i < ShenandoahRateAccelerationSampleSize; i++) {
        uint index = (_first_sample_index + delta + i) % _buffer_size;
        x_array[i] = _rate_timestamps[index];
        x_sum += x_array[i];
        y_array[i] = _rate_samples[index];
        if (i > 0) {
          // first sample not included in weighted average because it has no weight.
          double sample_weight = x_array[i] - x_array[i-1];
          weighted_y_sum += y_array[i] * sample_weight;
          total_weight += sample_weight;
        }
        y_sum += y_array[i];
      }
      weighted_average_alloc = (total_weight > 0)? weighted_y_sum / total_weight: 0;
    } else {
      weighted_average_alloc = 0;
    }

    double momentary_rate;
    if (_num_samples > ShenandoahMomentaryAllocationRateSpikeSampleSize) {
      // Num samples must be strictly greater than sample size, because we need one extra sample to compute rate and weights
      // In this context, the weight of a y value (an allocation rate) is the duration for which this allocation rate was
      // active (the time since previous y value was reported).  An allocation rate measured over a span of 300 ms (e.g. during
      // concurrent GC) has much more "weight" than an allocation rate measured over a span of 15 s.
      double weighted_y_sum = 0;
      double total_weight = 0;
      uint delta = _num_samples - ShenandoahMomentaryAllocationRateSpikeSampleSize;
      for (uint i = 0; i < ShenandoahMomentaryAllocationRateSpikeSampleSize; i++) {
        uint sample_index = (_first_sample_index + delta + i) % _buffer_size;
        uint preceding_index = (sample_index == 0)? _buffer_size - 1: sample_index - 1;
        double sample_weight = (_rate_timestamps[sample_index]
                                - _rate_timestamps[preceding_index]);
        weighted_y_sum += _rate_samples[sample_index] * sample_weight;
        total_weight += sample_weight;
      }
      momentary_rate = weighted_y_sum / total_weight;
    } else {
      momentary_rate = 0.0;
    }

    // By default, use momentary_rate for current rate and zero acceleration. Overwrite iff best-fit line has positive slope.
    current_rate = momentary_rate;
    acceleration = 0.0;
    if ((_num_samples >= ShenandoahRateAccelerationSampleSize)
        && (weighted_average_alloc >= avg_alloc_rate_words_per_second))  {
      // If the average rate across the acceleration samples is below the overall average, this sample is not eligible to
      //  represent acceleration of allocation rate.  We may just be catching up with allocations after a lull.

      double *xy_array = (double *) alloca(ShenandoahRateAccelerationSampleSize * sizeof(double));
      double *x2_array = (double *) alloca(ShenandoahRateAccelerationSampleSize * sizeof(double));
      double xy_sum = 0.0;
      double x2_sum = 0.0;
      for (uint i = 0; i < ShenandoahRateAccelerationSampleSize; i++) {
        xy_array[i] = x_array[i] * y_array[i];
        xy_sum += xy_array[i];
        x2_array[i] = x_array[i] * x_array[i];
        x2_sum += x2_array[i];
      }
      // Find the best-fit least-squares linear representation of rate vs time
      double m;                 /* slope */
      double b;                 /* y-intercept */

      m = ((ShenandoahRateAccelerationSampleSize * xy_sum - x_sum * y_sum)
           / (ShenandoahRateAccelerationSampleSize * x2_sum - x_sum * x_sum));
      b = (y_sum - m * x_sum) / ShenandoahRateAccelerationSampleSize;

      if (m > 0) {
        double proposed_current_rate = m * x_array[ShenandoahRateAccelerationSampleSize - 1] + b;
        acceleration = m;
        current_rate = proposed_current_rate;
      }
      // else, leave current_rate = momentary_rate, acceleration = 0
    }
    // and here also, leave current_rate = momentary_rate, acceleration = 0

    size_t words_to_be_consumed = (size_t) (current_rate * time_delta + 0.5 * acceleration * time_delta * time_delta);
    return words_to_be_consumed;
  }

  const TruncatedSeq& rate() const {
    return _sampled_rates;
  }

  double last_sampled_value() {
    MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);
    return _sampled_rates.last();
  }

  double average() {
    MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);
    return _sampled_rates.avg();
  }

  double upper_bound(const double standard_deviations) {
    MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);
    const double max_rate = MAX2(_sampled_rates.predict_next(), _sampled_rates.davg());
    return max_rate + (standard_deviations * _sampled_rates.dsd());
  }

  double predict_next() {
    MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);
    return _sampled_rates.predict_next();
  }

  void reset_samples() {
    _num_samples = 0;
    _first_sample_index = 0;
  }
};


#endif //SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP