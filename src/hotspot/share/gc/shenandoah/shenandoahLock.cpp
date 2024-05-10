/*
 * Copyright (c) 2019, Red Hat, Inc. All rights reserved.
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

#include "runtime/os.hpp"

#include "gc/shenandoah/shenandoahLock.hpp"
#include "runtime/atomic.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/os.inline.hpp"

// These are inline variants of Thread::SpinAcquire with optional blocking in VM.

class ShenandoahNoBlockOp : public StackObj {
public:
  ShenandoahNoBlockOp(JavaThread* java_thread) {
    assert(java_thread == nullptr, "Should not pass anything");
  }
};

void ShenandoahLock::contended_lock(bool allow_block_for_safepoint) {
  Thread* thread = Thread::current();
  if (allow_block_for_safepoint && thread->is_Java_thread()) {
    contended_lock_internal<ThreadBlockInVM>(JavaThread::cast(thread));
  } else {
    contended_lock_internal<ShenandoahNoBlockOp>(nullptr);
  }
}

static float random_delay_nanos(float max_nanos) {
  // rand() / (float) RAND_MAX produces random float number between 0 and 1.0, inclusive
  return (rand()/(float) RAND_MAX) * max_nanos;
}

// Enforce exponentially longer decays for successive collisions on lock access.
// Requested delay is random value between (0, max_delay)

#define BaselineDelayNanos  32768     // Start with max_delay equal to ~33 microseconds * decay_factor
#define MinimumDelayNanos    4096     // Delay no less than   ~4 microseconds
#define MaximumDelayNanos  262144     // Delay no more than ~262 microseconds

// Multiply the decay_factor by the BaselineDelayNanos, subject to limits imposed by MinimumDelayNanos and MaximumumDelayNanos.
// With InitialDecayFactor = 1.0625, decay_factor progresses through:
//   1.0625, 1.1289, 1.1995, 1.2744, 1.3541, 1.4387, 1.5286, 1.6242, 1.7257, 1.8335, 1.9481, 2.0699, 2.1993, 2.3367, 2.4828, ...

#define InitialDecayFactor 1.0625
#define YieldRetries            0

template<typename BlockOp>
void ShenandoahLock::contended_lock_internal(JavaThread* java_thread) {
  float decay_factor = InitialDecayFactor;
  float decay = 1.0;
  size_t most_recent_contendors = 0;
  size_t max_contendors = 0;
#undef KELVIN_DEBUG
#ifdef KELVIN_DEBUG
  size_t retry_count = 0;
  jlong most_recent_calculated_delay = 0;
  jlong most_recent_truncated_delay = 0;
#endif

  // Unlike ethernet, we know how many contendors are competing. Backoff more aggressively if there are more contendors.
  Atomic::inc(&_contendors);

  // Try some yields between sleeping
  int yield_count = YieldRetries;
  while (Atomic::load(&_state) == locked || Atomic::cmpxchg(&_state, unlocked, locked) != unlocked) {
    BlockOp block(java_thread);
    if (yield_count-- > 0) {
      os::naked_yield();
      // We'll retry the lock at top of loop
    } else {
      size_t contendors = Atomic::load(&_contendors);
#ifdef KELVIN_DEBUG
      retry_count++;
#endif
      if (contendors > max_contendors) {
        max_contendors = contendors;
      } else if (contendors * 2 < most_recent_contendors) {
        // Signicant decrease in contention, allow a shorter wait time
        decay_factor = InitialDecayFactor;
        decay /= (decay_factor * decay_factor);
        if (decay < 1.0) {
          decay = 1.0;
        }
#ifdef KELVIN_DEBUG
        Thread* t = Thread::current();
        log_info(gc)("Contending %s%s%sthread " PTR_FORMAT " decreasing decay from %.3f to %.3f after contention decreased from "
                     SIZE_FORMAT " to " SIZE_FORMAT " after " SIZE_FORMAT " retries",
                     t->is_Java_thread()? "Java ": "", t->is_VM_thread()? "VM ": "", t->is_Worker_thread()? "Worker ": "", p2i(t),
                     decay * decay_factor * decay_factor, decay,
                     most_recent_contendors, contendors, retry_count);
#endif
      } else {
        // Note that first contendors to arrive will experience less aggressive decay, so are more likely to proceed first.
        for (size_t decay_aggression = contendors; decay_aggression > 0; decay_aggression /= 4) {
          decay *= decay_factor;
        }
      }
      most_recent_contendors = contendors;
      jlong nanos = (jlong) random_delay_nanos(decay * BaselineDelayNanos);
#ifdef KELVIN_DEBUG
      most_recent_calculated_delay = nanos;
#endif
      if (nanos > MaximumDelayNanos) {
        nanos = MaximumDelayNanos;
        // The decay is too large, resulting in decays longer than MaximumDelayNanos.  Shrink it.
        decay /= InitialDecayFactor;
        // After we reach max, stop expanding decay.  This helps maintain random distribution of sleep times.
        // Otherwise, we'll be repeatedly sleeping MaximumDelayNanos hereafter.
        decay_factor = 1.0;
      } else if (nanos < MinimumDelayNanos) {
        nanos = MinimumDelayNanos;
      }
#ifdef KELVIN_DEBUG
      most_recent_truncated_delay = nanos;
#endif
      os::naked_short_nanosleep(nanos);
      yield_count = YieldRetries;
    }
  }
#ifdef KELVIN_DEBUG
  if (retry_count > 128) {
    Thread* t = Thread::current();
    log_info(gc)("Excessive lock contention by %s%s%sthread " PTR_FORMAT
                 ", MR delay calculated: " SIZE_FORMAT " and truncated: " SIZE_FORMAT,
                 t->is_Java_thread()? "Java ": "", t->is_VM_thread()? "VM ": "", t->is_Worker_thread()? "Worker ": "", p2i(t),
                 (size_t) most_recent_calculated_delay, (size_t) most_recent_truncated_delay);
    log_info(gc)(" Retries: " SIZE_FORMAT ", Maximum contendors: " SIZE_FORMAT
                 ", Most recent contention: " SIZE_FORMAT ", decay: %.3f, decay_factor: %.3f",
                 retry_count, max_contendors, most_recent_contendors, decay, decay_factor);
  }
#endif
  Atomic::dec(&_contendors);
}

#undef BaselineDelayNanos
#undef MinimumDelayNanos
#undef MaximumDelayNanos
#undef InitialDecayFactor
#undef YieldRetries

ShenandoahSimpleLock::ShenandoahSimpleLock() {
  assert(os::mutex_init_done(), "Too early!");
}

void ShenandoahSimpleLock::lock() {
  _lock.lock();
}

void ShenandoahSimpleLock::unlock() {
  _lock.unlock();
}

ShenandoahReentrantLock::ShenandoahReentrantLock() :
  ShenandoahSimpleLock(), _owner(nullptr), _count(0) {
  assert(os::mutex_init_done(), "Too early!");
}

ShenandoahReentrantLock::~ShenandoahReentrantLock() {
  assert(_count == 0, "Unbalance");
}

void ShenandoahReentrantLock::lock() {
  Thread* const thread = Thread::current();
  Thread* const owner = Atomic::load(&_owner);

  if (owner != thread) {
    ShenandoahSimpleLock::lock();
    Atomic::store(&_owner, thread);
  }

  _count++;
}

void ShenandoahReentrantLock::unlock() {
  assert(owned_by_self(), "Invalid owner");
  assert(_count > 0, "Invalid count");

  _count--;

  if (_count == 0) {
    Atomic::store(&_owner, (Thread*)nullptr);
    ShenandoahSimpleLock::unlock();
  }
}

bool ShenandoahReentrantLock::owned_by_self() const {
  Thread* const thread = Thread::current();
  Thread* const owner = Atomic::load(&_owner);
  return owner == thread;
}
