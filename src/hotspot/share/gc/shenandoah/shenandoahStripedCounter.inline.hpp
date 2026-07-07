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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHSTRIPEDCOUNTER_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHSTRIPEDCOUNTER_INLINE_HPP

#include "gc/shenandoah/shenandoahStripedCounter.hpp"

#include "runtime/thread.hpp"

// The constructor and destructor are defined out-of-line in shenandoahStripedCounter.cpp so that
// translation units which construct/destroy the counter (possibly only via an enclosing object) do
// not need to include this inline header. Only the hot-path methods are inline here.

inline uint ShenandoahStripedCounter::current_stripe() {
  // Per-thread probe into [0, _num_stripes). Hashing the thread pointer spreads threads across
  // stripes with no syscall or per-CPU query. This is a pure, stable function of (thread pointer,
  // _num_stripes) -- the same thread always maps to the same stripe for this instance -- so there is
  // nothing to cache, and the result is always in range. Correctness does not depend on which stripe
  // a thread gets: every stripe is accounted for when the counter is read (sum/drain).
  const uintptr_t t = (uintptr_t) Thread::current();
  return (uint) ((t ^ (t >> 20) ^ (t >> 9)) & _stripe_mask);
}

inline size_t ShenandoahStripedCounter::current_stripe_value() {
  return _stripes[_striped.load_relaxed() ? current_stripe() : 0].load_relaxed();
}

inline uint ShenandoahStripedCounter::num_stripes() const     { return _num_stripes; }
inline uint ShenandoahStripedCounter::log_num_stripes() const { return _log_num_stripes; }

inline size_t ShenandoahStripedCounter::add(const size_t bytes, bool& striped) {
  // LongAdder-style fast path: while uncontended, accumulate in stripe 0 with a single CAS. A thread
  // that loses that CAS to a competing writer latches _striped, after which everyone routes to their
  // own stripe (a relaxed fetch-add on its own cache line). Stripe 0 keeps accumulating either way.
  if (!_striped.load_relaxed()) {
    const size_t base = _stripes[0].load_relaxed();
    size_t prev;
    if ((prev = _stripes[0].compare_exchange(base, base + bytes, memory_order_relaxed)) == base) {
      // Won the stripe-0 CAS -- uncontended.
      return base + bytes;
    }
    // The counter is add-only, so stripe 0 only ever decreases via drain()/exchange() resetting it
    // to 0. A CAS failure with prev == 0 therefore means a drain raced between our load and our CAS,
    // not a competing writer, so retry once into the freshly-zeroed stripe rather than latch
    // striped mode and permanently lose the fast path over a benign drain race.
    if (prev == 0 && _stripes[0].compare_exchange(0, bytes, memory_order_relaxed) == 0) {
      return bytes;
    }
    // Lost the CAS to a competing writer: real contention. Latch striped mode and fall through to
    // record in our stripe.
    _striped.store_relaxed(true);
  }
  striped = true;
  const uint stripe = current_stripe();
  // Lower bound on the aggregate: this writer's own stripe total.
  return _stripes[stripe].add_then_fetch(bytes, memory_order_relaxed);
}

inline size_t ShenandoahStripedCounter::sum() const {
  // Fast path while uncontended (or single-stripe): all writes are in stripe 0, so it holds the total.
  if (_num_stripes == 1 || !_striped.load_relaxed()) {
    return _stripes[0].load_relaxed();
  }
  size_t total = 0;
  for (uint i = 0; i < _num_stripes; i++) {
    total += _stripes[i].load_relaxed();
  }
  return total;
}

inline size_t ShenandoahStripedCounter::drain() {
  // Fast path while uncontended (or single-stripe): all writes are in stripe 0, so drain only that.
  // A stripe written concurrently with a stale _striped==false read is just drained next time.
  if (_num_stripes == 1 || !_striped.load_relaxed()) {
    return _stripes[0].exchange(0, memory_order_relaxed);
  }
  size_t total = 0;
  for (uint i = 0; i < _num_stripes; i++) {
    total += _stripes[i].exchange(0, memory_order_relaxed);
  }
  return total;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSTRIPEDCOUNTER_INLINE_HPP
