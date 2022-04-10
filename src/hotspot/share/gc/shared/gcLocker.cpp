/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcTrace.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadSMR.hpp"

volatile jint GCLocker::_jni_lock_count = 0;
volatile bool GCLocker::_needs_gc       = false;
unsigned int GCLocker::_total_collections = 0;

#ifdef ASSERT
void GCLocker::verify_critical_count() {
  if (SafepointSynchronize::is_at_safepoint()) {
    int count = 0;
    // Count the number of threads with critical operations in progress
    JavaThreadIteratorWithHandle jtiwh;
    for (; JavaThread *thr = jtiwh.next(); ) {
      if (thr->in_critical()) {
        count++;
      }
    }
    if (_jni_lock_count != count) {
      log_error(gc, verify)("critical counts don't match: %d != %d", _jni_lock_count, count);
      jtiwh.rewind();
      for (; JavaThread *thr = jtiwh.next(); ) {
        if (thr->in_critical()) {
          log_error(gc, verify)(INTPTR_FORMAT " in_critical %d", p2i(thr), thr->in_critical());
        }
      }
    }
    assert(_jni_lock_count == count, "must be equal");
  }
}
#endif

void GCLocker::log_debug_jni(const char* msg) {
  Log(gc, jni) log;
  if (log.is_debug()) {
    ResourceMark rm; // JavaThread::name() allocates to convert to UTF8
    log.debug("%s Thread \"%s\" %d locked.", msg, Thread::current()->name(), _jni_lock_count);
  }
}

bool GCLocker::is_at_safepoint() {
  return SafepointSynchronize::is_at_safepoint();
}

bool GCLocker::check_active_before_gc() {
  assert(SafepointSynchronize::is_at_safepoint(), "only read at safepoint");
  if (is_active() && !_needs_gc) {
    verify_critical_count();
    _needs_gc = true;
    GCLockerTracer::start_gc_locker(_jni_lock_count);
    log_debug_jni("Setting _needs_gc.");
  }
  return is_active();
}

void GCLocker::stall_until_clear() {
  assert(!JavaThread::current()->in_critical(), "Would deadlock");
  MonitorLocker ml(JNICritical_lock);

  if (needs_gc()) {
    GCLockerTracer::inc_stall_count();
    log_debug_jni("Allocation failed. Thread stalled by JNI critical section.");
  }

  // Wait for _needs_gc  to be cleared
  while (needs_gc()) {
    ml.wait();
  }
}

bool GCLocker::should_discard(GCCause::Cause cause, uint total_collections) {
  return (cause == GCCause::_gc_locker) &&
         (_total_collections != total_collections);
}

void GCLocker::stall_until_no_needs_gc(JavaThread* thread) {
  assert(!thread->in_critical(), "shouldn't currently be in a critical region");
  MonitorLocker ml(JNICritical_lock);
  // Block entering threads if there's a pending GC request.
  while (needs_gc()) {
    // There's at least one thread that has not left the critical region (CR)
    // completely. When that last thread (no new threads can enter CR due to the
    // blocking) exits CR, it calls `unlock_critical`, which sets `_needs_gc`
    // to false and wakes up all blocked threads.
    // We would like to assert #threads in CR to be > 0, i.e. `_jni_lock_count > 0`,
    // but that would be too strong -- it's possible that the last thread
    // has called `unlock_critical`, but not yet finished the call, e.g. initiating
    // a GCCause::_gc_locker GC.
    ml.wait();
  }
}

void GCLocker::ack_needs_gc_if_last_thread(JavaThread* thread) {
  assert(!thread->in_critical(), "shouldn't currently be in a critical region");
  assert(needs_gc(), "precondition");
  jint old_value = Atomic::fetch_and_add(&_jni_lock_count, -1);
  assert(old_value > 0, "inv");
  bool is_last_thread = (old_value == 1);
  if (is_last_thread) {
    // We're the last thread out. Request a GC.
    // Capture the current total collections, to allow detection of
    // other collections that make this one unnecessary.  The value of
    // total_collections() is only changed at a safepoint, so there
    // must not be a safepoint between the lock becoming inactive and
    // getting the count, else there may be unnecessary GCLocker GCs.
    _total_collections = Universe::heap()->total_collections();
    GCLockerTracer::report_gc_locker();
    log_debug_jni("Performing GC after exiting critical section.");
    Universe::heap()->collect(GCCause::_gc_locker);
    {
      MutexLocker ml(JNICritical_lock);
      _needs_gc = false;
      JNICritical_lock->notify_all();
    }
  }
}
