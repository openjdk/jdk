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

// A contended-counter optimized for many concurrent writers and infrequent reads, in the manner of
// java.util.concurrent's LongAdder. A single counter (stripe 0) carries the uncontended case; the
// first writer to observe contention (a lost CAS on stripe 0) latches the counter into "striped"
// mode, after which writers accumulate into their own per-thread stripe on its own cache line. The
// value is always sum(stripes). This keeps a single cache line for low writer counts while
// eliminating the cache-line ping-pong a shared counter suffers when many threads increment at once.
//
// Differences from LongAdder, all intentional for this use:
//  - The stripe count is fixed for the counter's life (a power of two, rounded down from the CPU
//    count); the table never grows or re-hashes. Stripes use atomic fetch-add (add_then_fetch), so
//    an update can never "fail" and there is no per-op signal to drive resizing. A fixed table with
//    a per-thread probe spreads threads well enough.
//  - The stripe index is a per-thread pseudo-random probe (a hash of the thread pointer), recomputed
//    on each call -- it is a pure function of the thread pointer and the stripe count, so it needs no
//    caching. It never queries the current CPU, so there is no os::processor_id()/sched_getcpu()
//    syscall on the hot path. The power-of-two count lets the probe use a mask instead of a modulo.
//  - Once striped, the counter stays striped for its lifetime (LongAdder likewise never abandons its
//    cell table). There is no reset back to base-only mode.
//
// Reads (sum) are approximate under concurrent writes and exact when quiescent, matching LongAdder.
// This counter is monotonic per epoch: add() only increases it; drain() atomically reads and resets
// to begin a new epoch, preserving concurrent adds that race with the drain.
//
// Currently scoped to Shenandoah; the implementation has no Shenandoah dependency and could be
// promoted to shared runtime code (e.g. as a general striped counter) if another user appears.
class ShenandoahStripedCounter : public CHeapObj<mtGC> {
  typedef PaddedEnd<Atomic<size_t>> PaddedCounter;

  // Stripe 0 doubles as the uncontended "base": while not striped, writers CAS into _stripes[0].
  // Once a writer observes contention there, _striped latches and writers fan out to their own
  // stripe. There is no separate base counter -- _stripes[0] is already on its own cache line
  // (PaddedArray), so it serves the role without an extra field.
  Atomic<bool>    _striped;   // latched true once _stripes[0] contention is seen
  PaddedCounter*  _stripes;   // _num_stripes entries; [0] is the uncontended base
  // Number of stripes: a power of two, rounded down from the CPU count. Keeping it a power of two
  // lets current_stripe() map a thread hash into range with a mask (& _stripe_mask) instead of a
  // modulo on the hot path. Rounding down (rather than up) keeps it <= CPU count, so we never
  // allocate more stripes than cores.
  uint const      _num_stripes;
  uint const      _stripe_mask; // _num_stripes - 1
  uint const      _log_num_stripes;

  // The stripe this thread uses: a per-thread probe into [0, _num_stripes). Computed inline from the
  // thread pointer each call -- a pure, stable function of (thread, _num_stripes), so it needs no
  // caching (the result never changes for a given thread+instance) and is always in range for this
  // instance. Cheap: a hash and a mask, and no per-CPU query / syscall.
  uint current_stripe();

public:
  ShenandoahStripedCounter();
  ~ShenandoahStripedCounter();

  // Add `bytes` to the counter and return a lower bound on the resulting total. Before striping this
  // is the exact total (stripe 0); once striped it is this writer's own stripe total (~1/N of the
  // aggregate). `striped` is set to whether the counter is in striped mode, so the caller can scale a
  // trigger threshold accordingly. The exact total is only observable via sum().
  size_t add(size_t bytes, bool& striped);

  // Current total (only stripe 0 while uncontended, otherwise the sum of all stripes). No reset.
  // Approximate under concurrent writes.
  size_t sum() const;

  // Read the total and atomically reset it to zero, returning the amount consumed (only stripe 0
  // while uncontended, otherwise all stripes). Concurrent adds racing with the drain accumulate
  // toward the next epoch rather than being lost.
  size_t drain();

  // Value currently held in the calling thread's own stripe (no reset). Before striping every writer
  // uses stripe 0, so this returns that shared total; once striped it is this thread's stripe. Lets a
  // caller re-check a per-stripe trigger threshold in O(1) rather than summing all stripes.
  size_t current_stripe_value();

  // Number of stripes (a power of two, <= CPU count), and its base-2 log. Exposed so a caller can
  // scale a threshold to a per-stripe share with a shift (>> log_num_stripes) instead of a divide.
  uint num_stripes() const;
  uint log_num_stripes() const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSTRIPEDCOUNTER_HPP
