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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHSTRIPEDCOUNTER_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHSTRIPEDCOUNTER_HPP

#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"

// A contended-counter optimized for many concurrent writers and infrequent reads.
// Each writer accumulates into a stripe chosen by its thread hash, each on its own cache line to
// avoid false sharing. Stripes are shared when live writers outnumber stripes (num_stripes <= CPU
// count). The value of the counter is always sum(stripes).
//
// Reads (sum) are approximate under concurrent writes and exact when quiescent.
// This counter is monotonic per epoch: add() only increases it; drain() atomically reads and resets
// to begin a new epoch (0), preserving concurrent adds that race with the drain.
class ShenandoahStripedCounter : public CHeapObj<mtGC> {
  typedef PaddedEnd<Atomic<size_t>> PaddedCounter;

  PaddedCounter* _stripes;   // _num_stripes entries
  // Number of stripes: a power of two, rounded down from the CPU count. Keeping it a power of two
  // lets current_stripe() map a thread hash into range with a mask (& _stripe_mask) instead of a
  // modulo on the hot path.
  uint32_t const _num_stripes;
  uint32_t const _stripe_mask; // _num_stripes - 1
  uint32_t const _log_num_stripes;

  // The stripe this thread uses.
  uint32_t current_stripe() const;

public:
  ShenandoahStripedCounter();
  ~ShenandoahStripedCounter();

  // Add `bytes` to the current stripe of the counter and return the resulting total of the current stripe.
  size_t add(size_t bytes);

  // Current total of all stripes of the counter. No reset.
  // Approximate under concurrent writes.
  size_t sum() const;

  // Current value of the calling thread's own stripe. O(1), no reset.
  size_t current_stripe_value() const;

  // Read the total and atomically reset it to zero, returning the amount consumed.
  // Concurrent adds racing with the drain accumulate toward the next epoch rather than being lost.
  size_t drain();

  // Number of stripes (a power of two, <= CPU count), and its base-2 log. Exposed so a caller can
  // scale a threshold to a per-stripe share with a shift (>> log_num_stripes) instead of a divide.
  uint32_t num_stripes() const;
  uint32_t log_num_stripes() const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSTRIPEDCOUNTER_HPP
