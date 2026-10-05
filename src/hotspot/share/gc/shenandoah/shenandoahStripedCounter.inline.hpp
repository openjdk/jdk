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

inline uint32_t ShenandoahStripedCounter::current_stripe() const {
  if (_num_stripes == 1u) {
    return 0u;
  }
  // Per-thread probe into [0, _num_stripes). Hashing the thread pointer spreads threads across
  // stripes. This is a pure, stable function of (thread pointer,  _num_stripes)
  const uintptr_t t = (uintptr_t) Thread::current();
  return (uint32_t) ((t ^ (t >> 20) ^ (t >> 9)) & _stripe_mask);
}

inline uint32_t ShenandoahStripedCounter::num_stripes() const {
  return _num_stripes;
}

inline uint32_t ShenandoahStripedCounter::log_num_stripes() const {
  return _log_num_stripes;
}

inline size_t ShenandoahStripedCounter::add(const size_t bytes) {
  return _stripes[current_stripe()].add_then_fetch(bytes, memory_order_relaxed);
}

inline size_t ShenandoahStripedCounter::sum() const {
  size_t total = 0;
  for (uint32_t i = 0; i < _num_stripes; i++) {
    total += _stripes[i].load_relaxed();
  }
  return total;
}

inline size_t ShenandoahStripedCounter::current_stripe_value() const {
  return _stripes[current_stripe()].load_relaxed();
}

inline size_t ShenandoahStripedCounter::drain() {
  size_t total = 0;
  for (uint32_t i = 0; i < _num_stripes; i++) {
    total += _stripes[i].exchange(0, memory_order_relaxed);
  }
  return total;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSTRIPEDCOUNTER_INLINE_HPP
