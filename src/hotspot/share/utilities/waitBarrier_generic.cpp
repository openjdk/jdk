/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "utilities/waitBarrier_generic.hpp"
#include "utilities/spinYield.hpp"

// Implements the striped semaphore wait barrier.
//
// In addition to the barrier tag, it uses two counters to keep the semaphore
// count correct and not leave any late thread waiting.
//
// To guarantee progress and safety, we should make sure that new barrier tag
// starts with the completely empty set of waiters and free semaphore. This
// requires either waiting for all threads to leave wait() for current barrier
// tag on disarm(), or waiting for all threads to leave the previous tag before
// reusing the semaphore in arm().
//
// When there are multiple threads, it is normal for some threads to take
// significant time to leave the barrier. Waiting for these threads introduces
// stalls on barrier reuse. If wait on disarm(), this stall is nearly guaranteed
// to happen if some threads are stalled. If we wait on arm(), we can get lucky
// that most threads would be able to catch up, exit wait(), and so we arrive
// to arm() with semaphore ready for reuse.
//
// However, that is insufficient in practice. Therefore, this implementation goes
// a step further and implements the _striped_ semaphores. We maintain several
// semaphores (along with aux counters) in cells. The barrier tags are assigned
// to cells in some simple manner. Most of the current uses have sequential barrier
// tags, so simple modulo works well.
//
// We then operate on a cell like we would operate on a single semaphore: we wait
// at arm() for all threads to catch up before reusing the cell, and only then use it.
// For the cost of maintaining just a few cells, we have enough window for threads
// to catch up.
//
// For extra generality, the implementation uses the strongest barriers for extra safety,
// even when not strictly required to do so for correctness. Extra barrier overhead
// is dominated by the actual wait/notify latency.
//

void GenericWaitBarrier::arm(int barrier_tag) {
  assert(barrier_tag != 0, "Pre-condition: should be arming with armed value");
  assert(Atomic::load(&_barrier_tag) == 0, "Pre-condition: should not be already armed");

  Cell& cell = tag_to_cell(barrier_tag);

  // Prepare target cell for arming.
  // New waiters would still return immediately until barrier is fully armed.

  assert(Atomic::load(&cell._unsignaled_waits) == 0, "Pre-condition: no unsignaled waits");

  // Before we continue to arm, we need to make sure that all threads
  // have left the previous cell. This allows reusing the cell.
  SpinYield sp;
  while (Atomic::load_acquire(&cell._wait_threads) > 0) {
    assert(Atomic::load(&cell._unsignaled_waits) == 0, "Lifecycle sanity: no new waiters");
    sp.wait();
  }

  // Announce the barrier is ready to accept waiters.
  // Make sure accesses to barrier tag are fully ordered.
  // API specifies arm() must provide a trailing fence.
  OrderAccess::fence();
  Atomic::release_store(&_barrier_tag, barrier_tag);
  OrderAccess::fence();
}

int GenericWaitBarrier::Cell::wake_if_needed(int max) {
  // Match the signal counts with the number of unsignaled waits.
  // This would allow semaphore to be reused after we are done with it in
  // this arm-wait-disarm cycle.

  int wakeups = 0;
  while (true) {
    int cur = Atomic::load_acquire(&_unsignaled_waits);
    if (cur == 0) {
      // All done, no more waiters.
      return 0;
    }
    assert(cur > 0, "Sanity");

    int prev = Atomic::cmpxchg(&_unsignaled_waits, cur, cur - 1);
    if (prev != cur) {
      // Contention! Return to caller for early return or backoff.
      return prev;
    }

    // Signal!
    _sem_barrier.signal();

    if (wakeups++ > max) {
      // Over the wakeup limit, break out.
      return prev;
    }
  }
}

void GenericWaitBarrier::disarm() {
  int tag = Atomic::load_acquire(&_barrier_tag);
  assert(tag != 0, "Pre-condition: should be armed");

  // Announce the barrier is disarmed. New waiters would start to return immediately.
  // Make sure accesses to barrier tag are fully ordered.
  OrderAccess::fence();
  Atomic::release_store(&_barrier_tag, 0);
  OrderAccess::fence();

  Cell& cell = tag_to_cell(tag);

  // Wake up all current waiters.
  SpinYield sp;
  while (cell.wake_if_needed(INT_MAX) > 0) {
    sp.wait();
  }

  // API specifies disarm() must provide a trailing fence.
  OrderAccess::fence();

  assert(Atomic::load(&cell._unsignaled_waits) == 0, "Post-condition: no unsignaled waits");
}

void GenericWaitBarrier::wait(int barrier_tag) {
  assert(barrier_tag != 0, "Pre-condition: should be waiting on armed value");

  if (Atomic::load_acquire(&_barrier_tag) != barrier_tag) {
    // Not our current barrier at all, return right away without touching
    // anything. Chances are we catching up with disarm() disarming right now.
    // API specifies wait() must provide a trailing fence.
    OrderAccess::fence();
    return;
  }

  Cell& cell = tag_to_cell(barrier_tag);

  Atomic::add(&cell._wait_threads, 1);

  // There is a subtle race against disarming code.
  //
  // Disarming first lowers the actual barrier tag, and then proceeds to signal
  // threads. If we resume here after disarm() signaled all current waiters, we
  // might go into the wait without a matching signal, and be stuck indefinitely.
  // To avoid this, we check the expected barrier tag right here.
  //
  // Note that we have to do this check *after* incrementing _wait_threads. Otherwise,
  // the disarming code might not notice that we are about to wait, and not deliver
  // additional signal to wake us up. (This is a Dekker-like step in disguise.)

  // Make sure accesses to barrier tag are fully ordered.
  OrderAccess::fence();

  if (Atomic::load_acquire(&_barrier_tag) == barrier_tag) {
    Atomic::add(&cell._unsignaled_waits, 1);

    // Wait for notification.
    cell._sem_barrier.wait();

    // Unblocked! We help out with waking up two siblings. This allows to avalanche
    // the wakeups for many threads, even if some threads are lagging behind.
    // Note that we can only do this *before* decrementing _wait_threads, otherwise
    // we might prematurely wake up threads for another barrier tag. Current arm()
    // sequence protects us from this trouble by waiting until all waiters leave.
    cell.wake_if_needed(2);
  }

  Atomic::sub(&cell._wait_threads, 1);

  // API specifies wait() must provide a trailing fence.
  OrderAccess::fence();
}
