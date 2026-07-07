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

#include "memory/padded.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/powerOfTwo.hpp"

inline ShenandoahStripedCounter::ShenandoahStripedCounter()
  : _striped(false)
    // Round the CPU count down to a power of two so current_stripe() can mask instead of modulo.
    // Rounding down keeps the count <= number of cores. At least 1 stripe.
  , _num_stripes(round_down_power_of_2((uint) MAX2(os::processor_count(), 1)))
  , _stripe_mask(_num_stripes - 1)
  , _log_num_stripes(log2i_exact(_num_stripes)) {
  // create_unfreeable aligns both the base and per-element stride to a cache line and
  // default-constructs each Atomic to 0.
  _stripes = PaddedArray<Atomic<size_t>, mtGC>::create_unfreeable(_num_stripes);
}

inline ShenandoahStripedCounter::~ShenandoahStripedCounter() {
  // _stripes is created "unfreeable" (raw chunk not tracked); nothing to free. Counters live as long
  // as the owner, which for the sole current user (per-heap alloc rate) is the process lifetime.
}

inline uint ShenandoahStripedCounter::current_stripe() {
  // Per-thread probe into [0, _num_stripes). Hashing the thread pointer spreads threads across
  // stripes with no syscall or per-CPU query. This is a pure, stable function of (thread pointer,
  // _num_stripes) -- the same thread always maps to the same stripe for this instance -- so there is
  // nothing to cache, and the result is always in range. Correctness does not depend on the choice
  // because reads sum all stripes.
  const uintptr_t t = (uintptr_t) Thread::current();
  return (uint) ((t ^ (t >> 20) ^ (t >> 9)) & _stripe_mask);
}

inline size_t ShenandoahStripedCounter::add(const size_t bytes, bool& striped) {
  // LongAdder-style fast path: while uncontended, accumulate in stripe 0 with a single CAS. The
  // first thread to lose that CAS latches _striped, after which everyone routes to their own stripe
  // (a relaxed fetch-add on its own cache line). Stripe 0 keeps accumulating either way.
  if (!_striped.load_relaxed()) {
    const size_t base = _stripes[0].load_relaxed();
    if (_stripes[0].compare_exchange(base, base + bytes, memory_order_relaxed) == base) {
      // Won the stripe-0 CAS -- uncontended.
      return base + bytes;
    }
    // Lost the CAS: contention. Latch striped mode and fall through to record in our stripe.
    _striped.store_relaxed(true);
  }
  striped = true;
  const uint stripe = current_stripe();
  // Lower bound on the aggregate: this writer's own stripe total.
  return _stripes[stripe].add_then_fetch(bytes, memory_order_relaxed);
}

inline size_t ShenandoahStripedCounter::sum() const {
  size_t total = 0;
  for (uint i = 0; i < _num_stripes; i++) {
    total += _stripes[i].load_relaxed();
  }
  return total;
}

inline size_t ShenandoahStripedCounter::drain() {
  // exchange(0) so concurrent adds after this point accumulate toward the next epoch rather than being lost.
  size_t total = 0;
  for (uint i = 0; i < _num_stripes; i++) {
    total += _stripes[i].exchange(0, memory_order_relaxed);
  }
  return total;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSTRIPEDCOUNTER_INLINE_HPP
