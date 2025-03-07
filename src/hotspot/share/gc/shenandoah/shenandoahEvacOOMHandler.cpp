/*
 * Copyright (c) 2018, 2020, Red Hat, Inc. All rights reserved.
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


#include "gc/shenandoah/shenandoahEvacOOMHandler.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/os.hpp"

const jint ShenandoahEvacOOMCounter::OOM_MARKER_MASK = 0x80000000;

ShenandoahEvacOOMCounter::ShenandoahEvacOOMCounter() :
  _bits(0) {
}

void ShenandoahEvacOOMCounter::decrement() {
  assert(unmasked_count() > 0, "sanity");
  // NOTE: It's ok to simply decrement, even with mask set, because unmasked value is positive.
  Atomic::dec(&_bits);
}

void ShenandoahEvacOOMCounter::clear() {
  assert(unmasked_count() == 0, "sanity");
  Atomic::release_store_fence(&_bits, (jint)0);
}

void ShenandoahEvacOOMCounter::set_oom_bit(bool decrement) {
  jint threads_in_evac = Atomic::load_acquire(&_bits);
  while (true) {
    jint newval = decrement
      ? (threads_in_evac - 1) | OOM_MARKER_MASK
      : threads_in_evac | OOM_MARKER_MASK;

    jint other = Atomic::cmpxchg(&_bits, threads_in_evac, newval);
    if (other == threads_in_evac) {
      // Success: wait for other threads to get out of the protocol and return.
      break;
    } else {
      // Failure: try again with updated new value.
      threads_in_evac = other;
    }
  }
}

bool ShenandoahEvacOOMCounter::try_increment()
{
  jint threads_in_evac = Atomic::load_acquire(&_bits);

  while (true) {
    // Cannot enter evacuation if OOM_MARKER_MASK is set.
    if ((threads_in_evac & OOM_MARKER_MASK) != 0) {
      return false;
    }

    jint other = Atomic::cmpxchg(&_bits, threads_in_evac, threads_in_evac + 1);
    if (other == threads_in_evac) {
      // Success: caller may safely enter evacuation
      return true;
    } else {
      threads_in_evac = other;
    }
  }
}

ShenandoahEvacOOMHandler::ShenandoahEvacOOMHandler() :
  _num_counters(calc_num_counters()) {

  assert(_num_counters > 0, "sanity");
  assert(is_power_of_2(_num_counters), "must be");

  _threads_in_evac = NEW_C_HEAP_ARRAY(ShenandoahEvacOOMCounter, _num_counters, mtGC);
  for (int i = 0; i < _num_counters; i++) {
    new (&_threads_in_evac[i]) ShenandoahEvacOOMCounter();
  }
}

int ShenandoahEvacOOMHandler::calc_num_counters() {
  // Scale the number of counter buckets with the number of CPUs to
  // minimise contention.  Also make sure the number is a power of two
  // so we can map hash values to buckets with a simple mask.
  const int nproc = os::active_processor_count();
  const int clamped = MAX2(1, MIN2(nproc, 128));
  return round_up_power_of_2(clamped);
}

uint64_t ShenandoahEvacOOMHandler::hash_pointer(const void* p) {
  // Bit mixing function from MurmurHash3
  uint64_t key = (uintptr_t)p;
  key ^= (key >> 33);
  key *= UINT64_C(0xff51afd7ed558ccd);
  key ^= (key >> 33);
  key *= UINT64_C(0xc4ceb9fe1a85ec53);
  key ^= (key >> 33);
  return key;
}

ShenandoahEvacOOMCounter* ShenandoahEvacOOMHandler::counter_for_thread(Thread* t) {
  const uint64_t key = hash_pointer(t);
  return &_threads_in_evac[key & (_num_counters - 1)];
}

void ShenandoahEvacOOMHandler::wait_for_one_counter(ShenandoahEvacOOMCounter* ptr) {
  // We might be racing against handle_out_of_memory_during_evacuation()
  // setting the OOM_MARKER_MASK bit so we must make sure it is set here
  // *and* the counter is zero.
  while (ptr->load_acquire() != ShenandoahEvacOOMCounter::OOM_MARKER_MASK) {
    os::naked_short_sleep(1);
  }
}

void ShenandoahEvacOOMHandler::wait_for_no_evac_threads() {
  // Once the OOM_MARKER_MASK bit is set the counter can only decrease
  // so it's safe to check each bucket in turn.
  for (int i = 0; i < _num_counters; i++) {
    wait_for_one_counter(&_threads_in_evac[i]);
  }
  // At this point we are sure that no threads can evacuate anything. Raise
  // the thread-local oom_during_evac flag to indicate that any attempt
  // to evacuate should simply return the forwarding pointer instead (which is safe now).
  ShenandoahThreadLocalData::set_oom_during_evac(Thread::current(), true);
}

void ShenandoahEvacOOMHandler::register_thread(Thread* thr) {
  assert(!ShenandoahThreadLocalData::is_oom_during_evac(Thread::current()), "TL oom-during-evac must not be set");

  ShenandoahEvacOOMCounter* counter = counter_for_thread(thr);
  if (!counter->try_increment()) {
    // Counter has OOM_MARKER_MASK set, loop until no more threads in evac
    wait_for_no_evac_threads();
  }
}

void ShenandoahEvacOOMHandler::unregister_thread(Thread* thr) {
  if (!ShenandoahThreadLocalData::is_oom_during_evac(thr)) {
    counter_for_thread(thr)->decrement();
  } else {
    // If we get here, the current thread has already gone through the
    // OOM-during-evac protocol and has thus either never entered or successfully left
    // the evacuation region. Simply flip its TL oom-during-evac flag back off.
    ShenandoahThreadLocalData::set_oom_during_evac(thr, false);
  }
  assert(!ShenandoahThreadLocalData::is_oom_during_evac(thr), "TL oom-during-evac must be turned off");
}

void ShenandoahEvacOOMHandler::handle_out_of_memory_during_evacuation() {
  assert(ShenandoahThreadLocalData::is_evac_allowed(Thread::current()), "sanity");
  assert(!ShenandoahThreadLocalData::is_oom_during_evac(Thread::current()), "TL oom-during-evac must not be set");

  ShenandoahEvacOOMCounter* self = counter_for_thread(Thread::current());
  assert(self->unmasked_count() > 0, "sanity");

  for (int i = 0; i < _num_counters; i++) {
    ShenandoahEvacOOMCounter* counter = &_threads_in_evac[i];
    counter->set_oom_bit(counter == self);
  }

  wait_for_no_evac_threads();
}

void ShenandoahEvacOOMHandler::clear() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at a safepoint");
  for (int i = 0; i < _num_counters; i++) {
    _threads_in_evac[i].clear();
  }
}
