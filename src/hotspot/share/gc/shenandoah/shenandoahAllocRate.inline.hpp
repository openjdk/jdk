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

#include "gc/shenandoah/shenandoahUtils.hpp"
#include "logging/log.hpp"
#include "runtime/thread.inline.hpp"


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
uint ShenandoahAllocRate<Clock>::current_stripe() {
  // Fast path: no syscall, no atomics. If we still own our cached stripe, use it. Ownership is a
  // plain load of the affinity slot; a stale read only risks an occasional needless slow-path call,
  // never incorrect accounting (sampling sums all stripes).
  if (_cached_stripe < _num_stripes && _stripe_affinity[_cached_stripe]._thread == _self) {
    return _cached_stripe;
  }
  return current_stripe_slow();
}

template<typename Clock>
uint ShenandoahAllocRate<Clock>::current_stripe_slow() {
  // First call on this thread: cache our identity.
  if (_self == nullptr) {
    _self = Thread::current();
  }

#if defined(__APPLE__) && defined(AARCH64)
  // os::processor_id() has no real implementation on macOS/aarch64 (it always returns 0), which
  // would collapse every thread onto stripe 0. Pick a stripe from a per-thread pseudo-random value
  // instead: it does not track CPU locality, but spreading threads across stripes is all that is
  // needed to remove the cache-line contention.
  const uintptr_t t = (uintptr_t) _self;
  uint stripe = (uint) ((t ^ (t >> 20) ^ (t >> 9)) % _num_stripes);
#else
  // Linux and friends: index by the CPU we are running on. This is the (relatively expensive)
  // syscall/vDSO call, but the fast path above keeps it off the per-allocation path -- we only get
  // here on the first allocation or after migrating to a CPU whose stripe we no longer own.
  uint stripe = os::processor_id();
#endif

  // Claim the stripe. Whoever writes last owns it; a losing writer will simply miss the fast path on
  // its next allocation and re-claim (possibly a different stripe). This is a best-effort spreading
  // heuristic, so a plain store is sufficient -- no atomic needed.
  _stripe_affinity[stripe]._thread = _self;
  _cached_stripe = stripe;
  return stripe;
}

template<typename Clock>
size_t ShenandoahAllocRate<Clock>::add_to_stripe(const size_t allocated_bytes) {
  const uint stripe = current_stripe();
  return _allocated_bytes_since_last_sample[stripe].add_then_fetch(allocated_bytes, memory_order_relaxed);
}

template<typename Clock>
size_t ShenandoahAllocRate<Clock>::sum_stripes() const {
  size_t total = 0;
  for (uint i = 0; i < _num_stripes; i++) {
    total += _allocated_bytes_since_last_sample[i].load_relaxed();
  }
  return total;
}

template<typename Clock>
size_t ShenandoahAllocRate<Clock>::drain_stripes() {
  size_t total = 0;
  for (uint i = 0; i < _num_stripes; i++) {
    // exchange(0) so concurrent adds after this point accumulate toward the next sample rather
    // than being lost (mirrors the original sub_then_fetch-what-we-read behavior, per stripe).
    total += _allocated_bytes_since_last_sample[i].exchange(0, memory_order_relaxed);
  }
  return total;
}

template<typename Clock>
void ShenandoahAllocRate<Clock>::allocated(const size_t allocated_bytes) {
  // Hot path: bump only this CPU's stripe and compare against its proportional share of the
  // sample threshold. A stripe reaching its share is a cheap, contention-free signal that the
  // aggregate is likely near the threshold; the sampling path below re-checks the true sum under
  // the lock. Even if allocation is skewed such that no single stripe reaches its share, the
  // periodic force_update() (every 100ms) sums all stripes and samples, so nothing is missed.
  const size_t stripe_unsampled = add_to_stripe(allocated_bytes);
  const size_t minimum_sample_size = _minimum_sample_size.load_relaxed();
  const size_t stripe_share = MAX2(minimum_sample_size / _num_stripes, (size_t) 1);
  // Edge-trigger: attempt the (contended) sample lock only on the single allocation that pushes
  // this stripe across its share, not on every allocation while it sits above. Without this, once a
  // stripe is above its share every subsequent allocation would hammer _sample_lock.try_lock(),
  // turning the removed counter contention into lock contention. After a sample drains the stripes
  // back to zero, the stripe must climb to its share again before it can re-trigger.
  const bool crossed_share = stripe_unsampled >= stripe_share &&
                             (stripe_unsampled - allocated_bytes) < stripe_share;
  if (!crossed_share) {
    return;
  }

  if (!_sample_lock.try_lock()) {
    // Another thread has the lock and will take the sample
    return;
  }

  const size_t unsampled = sum_stripes();
  if (unsampled < minimum_sample_size) {
    // Not enough in aggregate yet (or another thread already sampled and reset the stripes).
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

  take_sample(now, elapsed, drain_stripes());

  _sample_lock.unlock();
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

  take_sample(now, elapsed, drain_stripes());

  _sample_lock.unlock();
}

template<typename Clock>
void ShenandoahAllocRate<Clock>::take_sample(jlong now, jlong elapsed, size_t unsampled) {
  assert(_sample_lock.owned_by_self(), "Caller must hold lock");

  _last_sample_time = now;

  // The caller has already drained the per-CPU stripes (drain_stripes) to obtain `unsampled`, so
  // there is nothing to deduct here; concurrent adds after the drain accumulate toward the next
  // sample in their respective stripes.

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
