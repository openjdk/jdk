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
// To guarantee progress and safety, we need to make sure that new barrier tag
// starts with the completely empty set of waiters and free semaphore. This
// requires either waiting for all threads to leave wait() for current barrier
// tag on disarm(), or waiting for all threads to leave the previous tag before
// reusing the semaphore in arm().
//
// When there are multiple threads, it is normal for some threads to take
// significant time to leave the barrier. Waiting for these threads introduces
// stalls on barrier reuse.
//
// If wait on disarm(), this stall is nearly guaranteed to happen if some threads
// are de-scheduled by prior wait(). It would be especially bad if there are more
// waiting threads than CPUs: every thread would need to wake up and register itself
// as leaving.
//
// If we wait on arm(), we can get lucky that most threads would be able to catch up,
// exit wait(), and so we arrive to arm() with semaphore ready for reuse. However,
// that is still insufficient in practice.
//
// Therefore, this implementation goes a step further and implements the _striped_
// semaphores. We maintain several semaphores in cells. The barrier tags are assigned
// to cells in some simple manner. Most of the current uses have sequential barrier
// tags, so simple modulo works well.
//
// We then operate on a cell like we would operate on a single semaphore: we wait
// at arm() for all threads to catch up before reusing the cell. For the cost of
// maintaining just a few cells, we have enough window for threads to catch up.
//
// The correctness is guaranteed by using a single atomic state variable per cell,
// with updates always done with CASes. For the cell state, positive values mean
// the cell is armed and maybe has waiters. Negative values mean the cell is disarmed
// and maybe has completing waiters. The cell starts with "-1". Arming a cell swings
// from "-1" to "+1". Every new waiter swings from (n) to (n+1), as long as "n" is
// positive. Disarm swings from (n) to (-n). Every completing waiter swings from (n)
// to (n+1), where "n" is guaranteed to stay negative. When all waiters complete,
// a cell ends up at "-1" again. This allows accurate tracking of how many signals
// to issue and does not race with disarm.
//
// The implementation uses the strongest (default) barriers for extra safety, even
// when not strictly required to do so for correctness. Extra barrier overhead is
// dominated by the actual wait/notify latency anyway.
//

void GenericWaitBarrier::arm(int barrier_tag) {
  assert(barrier_tag != 0, "Pre arm: Should be arming with armed value");
  assert(Atomic::load(&_barrier_tag) == 0, "Pre arm: Should not be already armed. Tag: %d", Atomic::load(&_barrier_tag));
  Atomic::release_store(&_barrier_tag, barrier_tag);

  Cell& cell = tag_to_cell(barrier_tag);

  assert(Atomic::load_acquire(&cell._state) < 0, "Pre arm: should be disarmed");

  // Before we continue to arm, we need to make sure that all threads
  // have left the previous cell. This means the cell status have rolled to -1.
  SpinYield sp;
  while (Atomic::load_acquire(&cell._state) < -1) {
    sp.wait();
  }

  // Try to swing cell to armed. This should always succeed after the check above.
  int ps = Atomic::cmpxchg(&cell._state, -1, 1);
  if (ps != -1) {
    fatal("Mid arm: Cannot arm the wait barrier. State: %d", ps);
  }

  // API specifies arm() must provide a trailing fence.
  OrderAccess::fence();

  assert(Atomic::load(&cell._state) > 0, "Post arm: should be armed");
}

int GenericWaitBarrier::Cell::wake_if_needed(int max) {
  int wakeups = 0;
  while (true) {
    int cur = Atomic::load_acquire(&_outstanding_wakeups);
    if (cur == 0) {
      // All done, no more waiters.
      return 0;
    }
    assert(cur > 0, "Sanity");

    int prev = Atomic::cmpxchg(&_outstanding_wakeups, cur, cur - 1);
    if (prev != cur) {
      // Contention, return to caller for early return or backoff.
      return prev;
    }

    // Signal!
    _sem.signal();

    if (wakeups++ > max) {
      // Over the wakeup limit, break out.
      return prev;
    }
  }
}

void GenericWaitBarrier::disarm() {
  int tag = Atomic::load_acquire(&_barrier_tag);
  assert(tag != 0, "Pre disarm: Should be armed. Tag: %d", tag);
  Atomic::release_store(&_barrier_tag, 0);

  Cell& cell = tag_to_cell(tag);

  SpinYield sp;
  while (true) {
    int s = Atomic::load_acquire(&cell._state);
    assert(s > 0, "Mid disarm: Should be armed. State: %d", s);
    if (Atomic::cmpxchg(&cell._state, s, -s) == s) {
      // Successfully disarmed. Wake up waiters, if we have at least one.
      // Allow other threads to assist with wakeups, if possible.
      int waiters = s - 1;
      if (waiters > 0) {
        Atomic::release_store(&cell._outstanding_wakeups, waiters);
        while (cell.wake_if_needed(INT_MAX) > 0) {
          sp.wait();
        }
      }
      break;
    }
    sp.wait();
  }

  assert(Atomic::load(&cell._outstanding_wakeups) == 0, "Post disarm: Should not have outstanding wakeups");
  assert(Atomic::load(&cell._state) < 0, "Post disarm: Should be disarmed");

  // API specifies disarm() must provide a trailing fence.
  OrderAccess::fence();
}

void GenericWaitBarrier::wait(int barrier_tag) {
  assert(barrier_tag != 0, "Pre wait: Should be waiting on armed value");

  Cell& cell = tag_to_cell(barrier_tag);

  // Try to register ourselves as pending waiter. If we got disarmed while
  // trying to register, return immediately.
  {
    int s;
    do {
      s = Atomic::load_acquire(&cell._state);
      if (s < 0) {
        // API specifies disarm() must provide a trailing fence.
        OrderAccess::fence();
        return;
      }
      assert(s > 0, "Before wait: Should be armed. State: %d", s);
    } while (Atomic::cmpxchg(&cell._state, s, s + 1) != s);
  }

  // Wait for notification.
  cell._sem.wait();

  // Unblocked! We help out with waking up two siblings. This allows to avalanche
  // the wakeups for many threads, even if some threads are lagging behind.
  // Note that we can only do this *before* reporting back as completed waiter,
  // otherwise we might prematurely wake up threads for another barrier tag.
  // Current arm() sequence protects us from this trouble by waiting until all waiters
  // leave.
  cell.wake_if_needed(2);

  // Register ourselves as completed waiter before leaving.
  {
    int s;
    do {
      s = Atomic::load_acquire(&cell._state);
      assert(s < -1, "After wait: Should be disarmed and have returning waiters. State: %d", s);
    } while (Atomic::cmpxchg(&cell._state, s, s + 1) != s);
  }

  // API specifies wait() must provide a trailing fence.
  OrderAccess::fence();
}
