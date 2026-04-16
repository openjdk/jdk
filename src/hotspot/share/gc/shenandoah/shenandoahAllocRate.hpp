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

  volatile size_t _allocated_bytes_since_last_sample;
  Monitor _sample_lock;
  jlong _last_sample_time;
  TruncatedSeq _sampled_times;
  TruncatedSeq _sampled_bytes;
  TruncatedSeq _sampled_rates;
  size_t _minimum_sample_size;

public:
  explicit ShenandoahAllocRate(size_t minimum_sample_size = MINIMUM_SAMPLE_SIZE)
    : _allocated_bytes_since_last_sample(0)
    , _sample_lock(Mutex::nosafepoint - 2, "ShenandoahAllocSample_lock", true)
    , _last_sample_time(0)
    , _sampled_times(100)
    , _sampled_bytes(100)
    , _sampled_rates(100)
    , _minimum_sample_size(minimum_sample_size) {
  }

  void set_minimum_sample_size(const size_t minimum_sample_size) {
    _minimum_sample_size = minimum_sample_size;
  }

  void allocated(const size_t allocated_bytes) {
    size_t unsampled = AtomicAccess::add(&_allocated_bytes_since_last_sample, allocated_bytes);
    if (unsampled < _minimum_sample_size) {
      // Not enough to sample yet
      return;
    }

    if (!_sample_lock.try_lock()) {
      // Another thread has the lock and will take the sample
      return;
    }

    unsampled = AtomicAccess::load(&_allocated_bytes_since_last_sample);
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
    AtomicAccess::sub(&_allocated_bytes_since_last_sample, unsampled);

    _sampled_times.add(elapsed);
    _sampled_bytes.add(unsampled);

    const double total_time  = _sampled_times.sum();
    const double total_bytes = _sampled_bytes.sum();
    const double elapsed_seconds = total_time / Clock::elapsed_frequency();
    const double bytes_per_second = total_bytes / elapsed_seconds;

    _sampled_rates.add(bytes_per_second);

    _sample_lock.unlock();
  }

  const TruncatedSeq& rate() const {
    return _sampled_rates;
  }

  double average() {
    MonitorLocker locker(&_sample_lock);
    return _sampled_rates.avg();
  }

  double upper_bound(const double standard_deviations) {
    MonitorLocker locker(&_sample_lock);
    const double max_rate = MAX2(_sampled_rates.predict_next(), _sampled_rates.davg());
    return max_rate + (standard_deviations * _sampled_rates.dsd());
  }

  double predict_next() {
    MonitorLocker locker(&_sample_lock);
    return _sampled_rates.predict_next();
  }
};


#endif //SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_HPP