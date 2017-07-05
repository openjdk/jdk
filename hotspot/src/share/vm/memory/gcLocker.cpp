/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/gcLocker.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/sharedHeap.hpp"

volatile jint GC_locker::_jni_lock_count = 0;
volatile jint GC_locker::_lock_count     = 0;
volatile bool GC_locker::_needs_gc       = false;
volatile bool GC_locker::_doing_gc       = false;

void GC_locker::stall_until_clear() {
  assert(!JavaThread::current()->in_critical(), "Would deadlock");
  if (PrintJNIGCStalls && PrintGCDetails) {
    ResourceMark rm; // JavaThread::name() allocates to convert to UTF8
    gclog_or_tty->print_cr(
      "Allocation failed. Thread \"%s\" is stalled by JNI critical section.",
      JavaThread::current()->name());
  }
  MutexLocker   ml(JNICritical_lock);
  // Wait for _needs_gc  to be cleared
  while (GC_locker::needs_gc()) {
    JNICritical_lock->wait();
  }
}

void GC_locker::jni_lock_slow() {
  MutexLocker mu(JNICritical_lock);
  // Block entering threads if we know at least one thread is in a
  // JNI critical region and we need a GC.
  // We check that at least one thread is in a critical region before
  // blocking because blocked threads are woken up by a thread exiting
  // a JNI critical region.
  while ((is_jni_active() && needs_gc()) || _doing_gc) {
    JNICritical_lock->wait();
  }
  jni_lock();
}

void GC_locker::jni_unlock_slow() {
  MutexLocker mu(JNICritical_lock);
  jni_unlock();
  if (needs_gc() && !is_jni_active()) {
    // We're the last thread out. Cause a GC to occur.
    // GC will also check is_active, so this check is not
    // strictly needed. It's added here to make it clear that
    // the GC will NOT be performed if any other caller
    // of GC_locker::lock() still needs GC locked.
    if (!is_active()) {
      _doing_gc = true;
      {
        // Must give up the lock while at a safepoint
        MutexUnlocker munlock(JNICritical_lock);
        Universe::heap()->collect(GCCause::_gc_locker);
      }
      _doing_gc = false;
    }
    clear_needs_gc();
    JNICritical_lock->notify_all();
  }
}

// Implementation of No_GC_Verifier

#ifdef ASSERT

No_GC_Verifier::No_GC_Verifier(bool verifygc) {
  _verifygc = verifygc;
  if (_verifygc) {
    CollectedHeap* h = Universe::heap();
    assert(!h->is_gc_active(), "GC active during No_GC_Verifier");
    _old_invocations = h->total_collections();
  }
}


No_GC_Verifier::~No_GC_Verifier() {
  if (_verifygc) {
    CollectedHeap* h = Universe::heap();
    assert(!h->is_gc_active(), "GC active during No_GC_Verifier");
    if (_old_invocations != h->total_collections()) {
      fatal("collection in a No_GC_Verifier secured function");
    }
  }
}

Pause_No_GC_Verifier::Pause_No_GC_Verifier(No_GC_Verifier * ngcv) {
  _ngcv = ngcv;
  if (_ngcv->_verifygc) {
    // if we were verifying, then make sure that nothing is
    // wrong before we "pause" verification
    CollectedHeap* h = Universe::heap();
    assert(!h->is_gc_active(), "GC active during No_GC_Verifier");
    if (_ngcv->_old_invocations != h->total_collections()) {
      fatal("collection in a No_GC_Verifier secured function");
    }
  }
}


Pause_No_GC_Verifier::~Pause_No_GC_Verifier() {
  if (_ngcv->_verifygc) {
    // if we were verifying before, then reenable verification
    CollectedHeap* h = Universe::heap();
    assert(!h->is_gc_active(), "GC active during No_GC_Verifier");
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
JRT_Leaf_Verifier::JRT_Leaf_Verifier()
  : No_Safepoint_Verifier(true, JRT_Leaf_Verifier::should_verify_GC())
{
}

JRT_Leaf_Verifier::~JRT_Leaf_Verifier()
{
}

bool JRT_Leaf_Verifier::should_verify_GC() {
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
