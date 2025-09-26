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

#include "unittest.hpp"
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

class ShenandoahMockClock {
public:
  static jlong Counter;
  static jlong elapsed_counter() {
    const jlong result = Counter;
    Counter += 10;
    return result;
  }

  static jlong elapsed_frequency() {
    return 1;
  }
};

class ShenandoahSlowClock {
public:
  static jlong elapsed_counter() {
    return 1;
  }

  static jlong elapsed_frequency() {
    return 1;
  }
};

jlong ShenandoahMockClock::Counter = 0;

template<typename Clock = ShenandoahAllocationClock>
class ShenandoahAllocationRate2 {
private:
  static constexpr size_t MINIMUM_SAMPLE_SIZE = 1024;

  volatile size_t _allocated_bytes_since_last_sample;
  Monitor _sample_lock;
  jlong _last_sample_time;
  TruncatedSeq _sampled_times;
  TruncatedSeq _sampled_bytes;
  TruncatedSeq _sampled_rates;

public:
  explicit ShenandoahAllocationRate2()
    : _allocated_bytes_since_last_sample(0)
    , _sample_lock(Mutex::nosafepoint - 2, "ShenandoahAllocSample_lock", true)
    , _last_sample_time(0)
    , _sampled_times(100)
    , _sampled_bytes(100)
    , _sampled_rates(100) {
  }

  void allocated(const size_t allocated_bytes) {
    size_t unsampled = AtomicAccess::add(&_allocated_bytes_since_last_sample, allocated_bytes);
    if (unsampled < MINIMUM_SAMPLE_SIZE) {
      return;
    }

    if (!_sample_lock.try_lock()) {
      // Another thread has the lock and will take the sample
      return;
    }

    unsampled = AtomicAccess::load(&_allocated_bytes_since_last_sample);
    if (unsampled < MINIMUM_SAMPLE_SIZE) {
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

  double average() const {
    return _sampled_rates.avg();
  }
};

TEST_VM(ShenandoahAllocationRateTest, ignore_too_small_sample) {
  ShenandoahAllocationRate2<ShenandoahMockClock> rate;
  rate.allocated(512);
  EXPECT_EQ(rate.average(), 0);
}

TEST_VM(ShenandoahAllocationRateTest, ignore_too_small_elapsed_time) {
  ShenandoahAllocationRate2<ShenandoahSlowClock> rate;
  rate.allocated(2048);
  rate.allocated(2048);
  EXPECT_EQ(rate.average(), 2048);
}

TEST_VM(ShenandoahAllocationRateTest, ten_second_average) {
  ShenandoahAllocationRate2<ShenandoahMockClock> rate;
  rate.allocated(2048); // t = 0
  rate.allocated(2048); // t = 10
  EXPECT_EQ(rate.average(), 409.6);
}
