/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "utilities/spinYield.hpp"
#include "utilities/waitBarrier_generic.hpp"

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
// If we wait on disarm(), this stall is nearly guaranteed to happen if some threads
// are de-scheduled by prior wait(). It would be especially bad if there are more
// waiting threads than CPUs: every thread would need to wake up and register itself
// as leaving, before we can unblock from disarm().
//
// If we wait on arm(), we can get lucky that most threads would be able to catch up,
// exit wait(), and so we arrive to arm() with semaphore ready for reuse. However,
// that is still insufficient in practice.
//
// Therefore, this implementation goes a step further and implements the _striped_
// semaphores. We maintain several semaphores in cells. The barrier tags are assigned
// to cells in some simple manner. Most of the current uses have sequential barrier
// tags, so simple modulo works well. We then operate on a cell like we would operate
// on a single semaphore: we wait at arm() for all threads to catch up before reusing
// the cell. For the cost of maintaining just a few cells, we have enough window for
// threads to catch up.
//
// The correctness is guaranteed by using a single atomic state variable per cell,
// with updates always done with CASes:
//
//   [.......... barrier tag ..........][.......... waiters ..........]
//  63                                  31                            0
//
// Cell starts with zero tag and zero waiters. Arming the cell swings barrier tag from
// zero to some tag, while checking that no waiters have appeared. Disarming swings
// the barrier tag back from tag to zero. Every waiter registers itself by incrementing
// the "waiters", while checking that barrier tag is still the same. Every completing waiter
// decrements the "waiters". When all waiters complete, a cell ends up in initial state,
// ready to be armed again. This allows accurate tracking of how many signals
// to issue and does not race with disarm.
//
// The implementation uses the strongest (default) barriers for extra safety, even
// when not strictly required to do so for correctness. Extra barrier overhead is
// dominated by the actual wait/notify latency anyway.
//

void GenericWaitBarrier::arm(int barrier_tag) {
  assert(barrier_tag != 0, "Pre arm: Should be arming with armed value");
  assert(Atomic::load(&_barrier_tag) == 0,
         "Pre arm: Should not be already armed. Tag: %d",
         Atomic::load(&_barrier_tag));
  Atomic::release_store(&_barrier_tag, barrier_tag);

  Cell &cell = tag_to_cell(barrier_tag);
  cell.arm(barrier_tag);

  // API specifies arm() must provide a trailing fence.
  OrderAccess::fence();
}

void GenericWaitBarrier::disarm() {
  int barrier_tag = Atomic::load_acquire(&_barrier_tag);
  assert(barrier_tag != 0, "Pre disarm: Should be armed. Tag: %d", barrier_tag);
  Atomic::release_store(&_barrier_tag, 0);

  Cell &cell = tag_to_cell(barrier_tag);
  cell.disarm(barrier_tag);

  // API specifies disarm() must provide a trailing fence.
  OrderAccess::fence();
}

void GenericWaitBarrier::wait(int barrier_tag) {
  assert(barrier_tag != 0, "Pre wait: Should be waiting on armed value");

  Cell &cell = tag_to_cell(barrier_tag);
  cell.wait(barrier_tag);

  // API specifies wait() must provide a trailing fence.
  OrderAccess::fence();
}

void GenericWaitBarrier::Cell::arm(int32_t requested_tag) {
  // Before we continue to arm, we need to make sure that all threads
  // have left the previous cell.

  int64_t state;

  SpinYield sp;
  while (true) {
    state = Atomic::load_acquire(&_state);
    assert(decode_tag(state) == 0,
           "Pre arm: Should not be armed. "
           "Tag: " INT32_FORMAT "; Waiters: " INT32_FORMAT,
           decode_tag(state), decode_waiters(state));
    if (decode_waiters(state) == 0) {
      break;
    }
    sp.wait();
  }

  // Try to swing cell to armed. This should always succeed after the check above.
  int64_t new_state = encode(requested_tag, 0);
  int64_t prev_state = Atomic::cmpxchg(&_state, state, new_state);
  if (prev_state != state) {
    fatal("Cannot arm the wait barrier. "
          "Tag: " INT32_FORMAT "; Waiters: " INT32_FORMAT,
          decode_tag(prev_state), decode_waiters(prev_state));
  }
}

int GenericWaitBarrier::Cell::signal_if_needed(int max) {
  int signals = 0;
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

    if (++signals >= max) {
      // Signalled requested number of times, break out.
      return prev;
    }
  }
}

void GenericWaitBarrier::Cell::disarm(int32_t expected_tag) {
  int32_t waiters;

  while (true) {
    int64_t state = Atomic::load_acquire(&_state);
    int32_t tag = decode_tag(state);
    waiters = decode_waiters(state);

    assert((tag == expected_tag) && (waiters >= 0),
           "Mid disarm: Should be armed with expected tag and have sane waiters. "
           "Tag: " INT32_FORMAT "; Waiters: " INT32_FORMAT,
           tag, waiters);

    int64_t new_state = encode(0, waiters);
    if (Atomic::cmpxchg(&_state, state, new_state) == state) {
      // Successfully disarmed.
      break;
    }
  }

  // Wake up waiters, if we have at least one.
  // Allow other threads to assist with wakeups, if possible.
  if (waiters > 0) {
    Atomic::release_store(&_outstanding_wakeups, waiters);
    SpinYield sp;
    while (signal_if_needed(INT_MAX) > 0) {
      sp.wait();
    }
  }
  assert(Atomic::load(&_outstanding_wakeups) == 0, "Post disarm: Should not have outstanding wakeups");
}

void GenericWaitBarrier::Cell::wait(int32_t expected_tag) {
  // Try to register ourselves as pending waiter.
  while (true) {
    int64_t state = Atomic::load_acquire(&_state);
    int32_t tag = decode_tag(state);
    if (tag != expected_tag) {
      // Cell tag had changed while waiting here. This means either the cell had
      // been disarmed, or we are late and the cell was armed with a new tag.
      // Exit without touching anything else.
      return;
    }
    int32_t waiters = decode_waiters(state);

    assert((tag == expected_tag) && (waiters >= 0 && waiters < INT32_MAX),
           "Before wait: Should be armed with expected tag and waiters are in range. "
           "Tag: " INT32_FORMAT "; Waiters: " INT32_FORMAT,
           tag, waiters);

    int64_t new_state = encode(tag, waiters + 1);
    if (Atomic::cmpxchg(&_state, state, new_state) == state) {
      // Success! Proceed to wait.
      break;
    }
  }

  // Wait for notification.
  _sem.wait();

  // Unblocked! We help out with waking up two siblings. This allows to avalanche
  // the wakeups for many threads, even if some threads are lagging behind.
  // Note that we can only do this *before* reporting back as completed waiter,
  // otherwise we might prematurely wake up threads for another barrier tag.
  // Current arm() sequence protects us from this trouble by waiting until all waiters
  // leave.
  signal_if_needed(2);

  // Register ourselves as completed waiter before leaving.
  while (true) {
    int64_t state = Atomic::load_acquire(&_state);
    int32_t tag = decode_tag(state);
    int32_t waiters = decode_waiters(state);

    assert((tag == 0) && (waiters > 0),
           "After wait: Should be not armed and have non-complete waiters. "
           "Tag: " INT32_FORMAT "; Waiters: " INT32_FORMAT,
           tag, waiters);

    int64_t new_state = encode(tag, waiters - 1);
    if (Atomic::cmpxchg(&_state, state, new_state) == state) {
      // Success!
      break;
    }
  }
}
