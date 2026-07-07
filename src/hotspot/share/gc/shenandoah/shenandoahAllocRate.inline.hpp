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

#include "gc/shenandoah/shenandoahStripedCounter.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "logging/log.hpp"


inline size_t ShenandoahAnticipatedConsumption::baseline_consumption() const {
  return shenandoah_safe_size_cast(_baseline * _duration_seconds);
}

inline size_t ShenandoahAnticipatedConsumption::momentary_consumption() const {
  return shenandoah_safe_size_cast(_momentary * _duration_seconds);
}

inline size_t ShenandoahAnticipatedConsumption::accelerated_consumption() const {
  const double consumption = _predicted_rate * _duration_seconds + 0.5 * _acceleration * _duration_seconds * _duration_seconds;
  return shenandoah_safe_size_cast(consumption);
}

inline void ShenandoahDecayAllocRate::task() {
  _rate->force_update();
}

template<typename Clock>
void ShenandoahAllocRate<Clock>::update_minimum_sample_size(const size_t available) {
  const size_t min_sample_size = clamp(available / ALLOC_SAMPLE_PORTION, ALLOC_SAMPLE_MIN, ALLOC_SAMPLE_MAX);
  log_info(gc, ergo)("Adjust minimum allocation sample size to: " PROPERFMT, PROPERFMTARGS(min_sample_size));
  set_minimum_sample_size(min_sample_size);
}

template<typename Clock>
void ShenandoahAllocRate<Clock>::maybe_take_sample(const size_t per_stripe_threshold) {
  if (!_sample_lock.try_lock()) {
    // Another thread has the lock and will take the sample.
    return;
  }
  // Re-check this thread's own stripe under the lock against the per-stripe threshold. Using the
  // caller's stripe (O(1)) rather than sum() over all stripes (O(N)) keeps the locked path cheap;
  // the caller only reaches here right after its stripe crossed the threshold in add().
  const size_t unsampled_stripe = _unsampled.current_stripe_value();
  if (unsampled_stripe < per_stripe_threshold) {
    // Stripe fell back below its share (another thread already sampled and drained the counter).
    _sample_lock.unlock();
    return;
  }
  const jlong now = Clock::elapsed_counter();
  const jlong elapsed = now - _last_sample_time;
  if (elapsed <= 0) {
    // Avoid sampling nonsense allocation rates.
    _sample_lock.unlock();
    return;
  }
  take_sample(now, elapsed, _unsampled.drain());
  _sample_lock.unlock();
}

template<typename Clock>
void ShenandoahAllocRate<Clock>::allocated(const size_t allocated_bytes) {
  // The striped counter absorbs allocation-path contention; add() returns a cheap lower bound on the
  // running total (this thread's own stripe once striped). We edge-trigger a sample when this stripe
  // crosses its per-stripe share of the threshold, then re-check under the lock in maybe_take_sample.
  // Even if a skewed distribution never trips that check, the periodic force_update() (every 100ms)
  // samples unconditionally, so nothing is missed.
  bool striped = false;
  const size_t unsampled = _unsampled.add(allocated_bytes, striped);
  const size_t minimum_sample_size = _minimum_sample_size.load_relaxed();
  // The hint returned by add() is this thread's own stripe total once striped, i.e. ~1/N of the
  // aggregate. Scale the trigger threshold to a per-stripe share so a stripe crossing its share still
  // fires the (locked) aggregate re-check. Shift by log2(N) instead of dividing. Clamp to >= 1 so a
  // small minimum_sample_size can never make the share 0 (which would disable the fast-path check).
  size_t per_stripe_threshold = minimum_sample_size;
  if (striped) {
    per_stripe_threshold = MAX2(minimum_sample_size >> _unsampled.log_num_stripes(), (size_t) 1);
  }
  // Edge-trigger: fire only on the single add that pushes this stripe across its share, not on every
  // add while it sits above (which would hammer the sample lock).
  if (unsampled >= per_stripe_threshold && unsampled - allocated_bytes < per_stripe_threshold) {
    maybe_take_sample(per_stripe_threshold);
  }
}

template<typename Clock>
void ShenandoahAllocRate<Clock>::force_update() {
  if (!_sample_lock.try_lock()) {
    // Another thread has the lock and will take the sample
    return;
  }

  const jlong now = Clock::elapsed_counter();
  const jlong elapsed = now - _last_sample_time;

  if (elapsed <= 0) {
    // Avoid sampling nonsense allocation rates
    _sample_lock.unlock();
    return;
  }

  take_sample(now, elapsed, _unsampled.drain());

  _sample_lock.unlock();
}

template<typename Clock>
void ShenandoahAllocRate<Clock>::take_sample(jlong now, jlong elapsed, size_t unsampled) {
  assert(_sample_lock.owned_by_self(), "Caller must hold lock");

  _last_sample_time = now;

  const double timestamp = static_cast<double>(_last_sample_time) / Clock::elapsed_frequency();
  const double rate_seconds = static_cast<double>(unsampled) * Clock::elapsed_frequency() / elapsed;

  _baseline.add(timestamp, rate_seconds);
  _recent.add(timestamp, rate_seconds);
  _momentary.add(timestamp, rate_seconds);

  // Careful, still under a lock here
  log_develop_trace(gc, sampling)("Recorded %.3f/s at %.3fs", rate_seconds, timestamp);
}

template<typename Clock>
ShenandoahAnticipatedConsumption ShenandoahAllocRate<Clock>::snapshot(const double time_delta, const double standard_deviations) {
  ShenandoahAnticipatedConsumption result(time_delta);
  MonitorLocker locker(&_sample_lock, Mutex::_no_safepoint_check_flag);

  result._baseline = upper_bound_no_lock(standard_deviations);

  if (_recent.weighted_average() <= _baseline.weighted_average()) {
    // We are not accelerating, just use the momentary average.
    result._momentary = _momentary.weighted_average();
  } else {
    result._acceleration = _recent.slope();
    result._predicted_rate  = _recent.predict_y(_recent.last());
  }

  return result;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCRATE_INLINE_HPP
