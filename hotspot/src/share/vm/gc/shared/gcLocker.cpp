/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcLocker.inline.hpp"
#include "memory/resourceArea.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/thread.inline.hpp"

volatile jint GCLocker::_jni_lock_count = 0;
volatile bool GCLocker::_needs_gc       = false;
volatile bool GCLocker::_doing_gc       = false;

#ifdef ASSERT
volatile jint GCLocker::_debug_jni_lock_count = 0;
#endif


#ifdef ASSERT
void GCLocker::verify_critical_count() {
  if (SafepointSynchronize::is_at_safepoint()) {
    assert(!needs_gc() || _debug_jni_lock_count == _jni_lock_count, "must agree");
    int count = 0;
    // Count the number of threads with critical operations in progress
    for (JavaThread* thr = Threads::first(); thr; thr = thr->next()) {
      if (thr->in_critical()) {
        count++;
      }
    }
    if (_jni_lock_count != count) {
      log_error(gc, verify)("critical counts don't match: %d != %d", _jni_lock_count, count);
      for (JavaThread* thr = Threads::first(); thr; thr = thr->next()) {
        if (thr->in_critical()) {
          log_error(gc, verify)(INTPTR_FORMAT " in_critical %d", p2i(thr), thr->in_critical());
        }
      }
    }
    assert(_jni_lock_count == count, "must be equal");
  }
}

// In debug mode track the locking state at all times
void GCLocker::increment_debug_jni_lock_count() {
  assert(_debug_jni_lock_count >= 0, "bad value");
  Atomic::inc(&_debug_jni_lock_count);
}

void GCLocker::decrement_debug_jni_lock_count() {
  assert(_debug_jni_lock_count > 0, "bad value");
  Atomic::dec(&_debug_jni_lock_count);
}
#endif

void GCLocker::log_debug_jni(const char* msg) {
  Log(gc, jni) log;
  if (log.is_debug()) {
    ResourceMark rm; // JavaThread::name() allocates to convert to UTF8
    log.debug("%s Thread \"%s\" %d locked.", msg, Thread::current()->name(), _jni_lock_count);
  }
}

bool GCLocker::check_active_before_gc() {
  assert(SafepointSynchronize::is_at_safepoint(), "only read at safepoint");
  if (is_active() && !_needs_gc) {
    verify_critical_count();
    _needs_gc = true;
    log_debug_jni("Setting _needs_gc.");
  }
  return is_active();
}

void GCLocker::stall_until_clear() {
  assert(!JavaThread::current()->in_critical(), "Would deadlock");
  MutexLocker   ml(JNICritical_lock);

  if (needs_gc()) {
    log_debug_jni("Allocation failed. Thread stalled by JNI critical section.");
  }

  // Wait for _needs_gc  to be cleared
  while (needs_gc()) {
    JNICritical_lock->wait();
  }
}

void GCLocker::jni_lock(JavaThread* thread) {
  assert(!thread->in_critical(), "shouldn't currently be in a critical region");
  MutexLocker mu(JNICritical_lock);
  // Block entering threads if we know at least one thread is in a
  // JNI critical region and we need a GC.
  // We check that at least one thread is in a critical region before
  // blocking because blocked threads are woken up by a thread exiting
  // a JNI critical region.
  while (is_active_and_needs_gc() || _doing_gc) {
    JNICritical_lock->wait();
  }
  thread->enter_critical();
  _jni_lock_count++;
  increment_debug_jni_lock_count();
}

void GCLocker::jni_unlock(JavaThread* thread) {
  assert(thread->in_last_critical(), "should be exiting critical region");
  MutexLocker mu(JNICritical_lock);
  _jni_lock_count--;
  decrement_debug_jni_lock_count();
  thread->exit_critical();
  if (needs_gc() && !is_active_internal()) {
    // We're the last thread out. Cause a GC to occur.
    _doing_gc = true;
    {
      // Must give up the lock while at a safepoint
      MutexUnlocker munlock(JNICritical_lock);
      log_debug_jni("Performing GC after exiting critical section.");
      Universe::heap()->collect(GCCause::_gc_locker);
    }
    _doing_gc = false;
    _needs_gc = false;
    JNICritical_lock->notify_all();
  }
}

// Implementation of NoGCVerifier

#ifdef ASSERT

NoGCVerifier::NoGCVerifier(bool verifygc) {
  _verifygc = verifygc;
  if (_verifygc) {
    CollectedHeap* h = Universe::heap();
    assert(!h->is_gc_active(), "GC active during NoGCVerifier");
    _old_invocations = h->total_collections();
  }
}


NoGCVerifier::~NoGCVerifier() {
  if (_verifygc) {
    CollectedHeap* h = Universe::heap();
    assert(!h->is_gc_active(), "GC active during NoGCVerifier");
    if (_old_invocations != h->total_collections()) {
      fatal("collection in a NoGCVerifier secured function");
    }
  }
}

PauseNoGCVerifier::PauseNoGCVerifier(NoGCVerifier * ngcv) {
  _ngcv = ngcv;
  if (_ngcv->_verifygc) {
    // if we were verifying, then make sure that nothing is
    // wrong before we "pause" verification
    CollectedHeap* h = Universe::heap();
    assert(!h->is_gc_active(), "GC active during NoGCVerifier");
    if (_ngcv->_old_invocations != h->total_collections()) {
      fatal("collection in a NoGCVerifier secured function");
    }
  }
}


PauseNoGCVerifier::~PauseNoGCVerifier() {
  if (_ngcv->_verifygc) {
    // if we were verifying before, then reenable verification
    CollectedHeap* h = Universe::heap();
    assert(!h->is_gc_active(), "GC active during NoGCVerifier");
    _ngcv->_old_invocations = h->total_collections();
  }
}


// JRT_LEAF rules:
// A JRT_LEAF method may not interfere with safepointing by
//   1) acquiring or blocking on a Mutex or JavaLock - checked
//   2) allocating heap memory - checked
//   3) executing a VM operation - checked
//   4) executing a system call (including malloc) that could block or grab a lock
//   5) invoking GC
//   6) reaching a safepoint
//   7) running too long
// Nor may any method it calls.
JRTLeafVerifier::JRTLeafVerifier()
  : NoSafepointVerifier(true, JRTLeafVerifier::should_verify_GC())
{
}

JRTLeafVerifier::~JRTLeafVerifier()
{
}

bool JRTLeafVerifier::should_verify_GC() {
  switch (JavaThread::current()->thread_state()) {
  case _thread_in_Java:
    // is in a leaf routine, there must be no safepoint.
    return true;
  case _thread_in_native:
    // A native thread is not subject to safepoints.
    // Even while it is in a leaf routine, GC is ok
    return false;
  default:
    // Leaf routines cannot be called from other contexts.
    ShouldNotReachHere();
    return false;
  }
}
#endif
