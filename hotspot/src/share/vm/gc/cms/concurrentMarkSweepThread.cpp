/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "gc/cms/concurrentMarkSweepGeneration.inline.hpp"
#include "gc/cms/concurrentMarkSweepThread.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/referencePendingListLocker.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/vmThread.hpp"

// ======= Concurrent Mark Sweep Thread ========

ConcurrentMarkSweepThread* ConcurrentMarkSweepThread::_cmst = NULL;
CMSCollector* ConcurrentMarkSweepThread::_collector         = NULL;
int  ConcurrentMarkSweepThread::_CMS_flag                   = CMS_nil;

volatile jint ConcurrentMarkSweepThread::_pending_yields    = 0;

ConcurrentMarkSweepThread::ConcurrentMarkSweepThread(CMSCollector* collector)
  : ConcurrentGCThread() {
  assert(UseConcMarkSweepGC,  "UseConcMarkSweepGC should be set");
  assert(_cmst == NULL, "CMS thread already created");
  _cmst = this;
  assert(_collector == NULL, "Collector already set");
  _collector = collector;

  set_name("CMS Main Thread");

  // An old comment here said: "Priority should be just less
  // than that of VMThread".  Since the VMThread runs at
  // NearMaxPriority, the old comment was inaccurate, but
  // changing the default priority to NearMaxPriority-1
  // could change current behavior, so the default of
  // NearMaxPriority stays in place.
  //
  // Note that there's a possibility of the VMThread
  // starving if UseCriticalCMSThreadPriority is on.
  // That won't happen on Solaris for various reasons,
  // but may well happen on non-Solaris platforms.
  create_and_start(UseCriticalCMSThreadPriority ? CriticalPriority : NearMaxPriority);
}

void ConcurrentMarkSweepThread::run_service() {
  assert(this == cmst(), "just checking");

  if (BindCMSThreadToCPU && !os::bind_to_processor(CPUForCMSThread)) {
    log_warning(gc)("Couldn't bind CMS thread to processor " UINTX_FORMAT, CPUForCMSThread);
  }

  {
    MutexLockerEx x(CGC_lock, true);
    set_CMS_flag(CMS_cms_wants_token);
    assert(is_init_completed() && Universe::is_fully_initialized(), "ConcurrentGCThread::run() should have waited for this.");

    // Wait until the surrogate locker thread that will do
    // pending list locking on our behalf has been created.
    // We cannot start the SLT thread ourselves since we need
    // to be a JavaThread to do so.
    CMSLoopCountWarn loopY("CMS::run", "waiting for SLT installation", 2);
    while (!ReferencePendingListLocker::is_initialized() && !should_terminate()) {
      CGC_lock->wait(true, 200);
      loopY.tick();
    }
    clear_CMS_flag(CMS_cms_wants_token);
  }

  while (!should_terminate()) {
    sleepBeforeNextCycle();
    if (should_terminate()) break;
    GCIdMark gc_id_mark;
    GCCause::Cause cause = _collector->_full_gc_requested ?
      _collector->_full_gc_cause : GCCause::_cms_concurrent_mark;
    _collector->collect_in_background(cause);
  }

  // Check that the state of any protocol for synchronization
  // between background (CMS) and foreground collector is "clean"
  // (i.e. will not potentially block the foreground collector,
  // requiring action by us).
  verify_ok_to_terminate();
}

#ifndef PRODUCT
void ConcurrentMarkSweepThread::verify_ok_to_terminate() const {
  assert(!(CGC_lock->owned_by_self() || cms_thread_has_cms_token() ||
           cms_thread_wants_cms_token()),
         "Must renounce all worldly possessions and desires for nirvana");
  _collector->verify_ok_to_terminate();
}
#endif

// create and start a new ConcurrentMarkSweep Thread for given CMS generation
ConcurrentMarkSweepThread* ConcurrentMarkSweepThread::start(CMSCollector* collector) {
  guarantee(_cmst == NULL, "start() called twice!");
  ConcurrentMarkSweepThread* th = new ConcurrentMarkSweepThread(collector);
  assert(_cmst == th, "Where did the just-created CMS thread go?");
  return th;
}

void ConcurrentMarkSweepThread::stop_service() {
  // Now post a notify on CGC_lock so as to nudge
  // CMS thread(s) that might be slumbering in
  // sleepBeforeNextCycle.
  MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
  CGC_lock->notify_all();
}

void ConcurrentMarkSweepThread::threads_do(ThreadClosure* tc) {
  assert(tc != NULL, "Null ThreadClosure");
  if (cmst() != NULL && !cmst()->has_terminated()) {
    tc->do_thread(cmst());
  }
  assert(Universe::is_fully_initialized(),
         "Called too early, make sure heap is fully initialized");
  if (_collector != NULL) {
    AbstractWorkGang* gang = _collector->conc_workers();
    if (gang != NULL) {
      gang->threads_do(tc);
    }
  }
}

void ConcurrentMarkSweepThread::print_all_on(outputStream* st) {
  if (cmst() != NULL && !cmst()->has_terminated()) {
    cmst()->print_on(st);
    st->cr();
  }
  if (_collector != NULL) {
    AbstractWorkGang* gang = _collector->conc_workers();
    if (gang != NULL) {
      gang->print_worker_threads_on(st);
    }
  }
}

void ConcurrentMarkSweepThread::synchronize(bool is_cms_thread) {
  assert(UseConcMarkSweepGC, "just checking");

  MutexLockerEx x(CGC_lock,
                  Mutex::_no_safepoint_check_flag);
  if (!is_cms_thread) {
    assert(Thread::current()->is_VM_thread(), "Not a VM thread");
    CMSSynchronousYieldRequest yr;
    while (CMS_flag_is_set(CMS_cms_has_token)) {
      // indicate that we want to get the token
      set_CMS_flag(CMS_vm_wants_token);
      CGC_lock->wait(true);
    }
    // claim the token and proceed
    clear_CMS_flag(CMS_vm_wants_token);
    set_CMS_flag(CMS_vm_has_token);
  } else {
    assert(Thread::current()->is_ConcurrentGC_thread(),
           "Not a CMS thread");
    // The following barrier assumes there's only one CMS thread.
    // This will need to be modified is there are more CMS threads than one.
    while (CMS_flag_is_set(CMS_vm_has_token | CMS_vm_wants_token)) {
      set_CMS_flag(CMS_cms_wants_token);
      CGC_lock->wait(true);
    }
    // claim the token
    clear_CMS_flag(CMS_cms_wants_token);
    set_CMS_flag(CMS_cms_has_token);
  }
}

void ConcurrentMarkSweepThread::desynchronize(bool is_cms_thread) {
  assert(UseConcMarkSweepGC, "just checking");

  MutexLockerEx x(CGC_lock,
                  Mutex::_no_safepoint_check_flag);
  if (!is_cms_thread) {
    assert(Thread::current()->is_VM_thread(), "Not a VM thread");
    assert(CMS_flag_is_set(CMS_vm_has_token), "just checking");
    clear_CMS_flag(CMS_vm_has_token);
    if (CMS_flag_is_set(CMS_cms_wants_token)) {
      // wake-up a waiting CMS thread
      CGC_lock->notify();
    }
    assert(!CMS_flag_is_set(CMS_vm_has_token | CMS_vm_wants_token),
           "Should have been cleared");
  } else {
    assert(Thread::current()->is_ConcurrentGC_thread(),
           "Not a CMS thread");
    assert(CMS_flag_is_set(CMS_cms_has_token), "just checking");
    clear_CMS_flag(CMS_cms_has_token);
    if (CMS_flag_is_set(CMS_vm_wants_token)) {
      // wake-up a waiting VM thread
      CGC_lock->notify();
    }
    assert(!CMS_flag_is_set(CMS_cms_has_token | CMS_cms_wants_token),
           "Should have been cleared");
  }
}

// Wait until any cms_lock event
void ConcurrentMarkSweepThread::wait_on_cms_lock(long t_millis) {
  MutexLockerEx x(CGC_lock,
                  Mutex::_no_safepoint_check_flag);
  if (should_terminate() || _collector->_full_gc_requested) {
    return;
  }
  set_CMS_flag(CMS_cms_wants_token);   // to provoke notifies
  CGC_lock->wait(Mutex::_no_safepoint_check_flag, t_millis);
  clear_CMS_flag(CMS_cms_wants_token);
  assert(!CMS_flag_is_set(CMS_cms_has_token | CMS_cms_wants_token),
         "Should not be set");
}

// Wait until the next synchronous GC, a concurrent full gc request,
// or a timeout, whichever is earlier.
void ConcurrentMarkSweepThread::wait_on_cms_lock_for_scavenge(long t_millis) {
  // Wait time in millis or 0 value representing infinite wait for a scavenge
  assert(t_millis >= 0, "Wait time for scavenge should be 0 or positive");

  GenCollectedHeap* gch = GenCollectedHeap::heap();
  double start_time_secs = os::elapsedTime();
  double end_time_secs = start_time_secs + (t_millis / ((double) MILLIUNITS));

  // Total collections count before waiting loop
  unsigned int before_count;
  {
    MutexLockerEx hl(Heap_lock, Mutex::_no_safepoint_check_flag);
    before_count = gch->total_collections();
  }

  unsigned int loop_count = 0;

  while(!should_terminate()) {
    double now_time = os::elapsedTime();
    long wait_time_millis;

    if(t_millis != 0) {
      // New wait limit
      wait_time_millis = (long) ((end_time_secs - now_time) * MILLIUNITS);
      if(wait_time_millis <= 0) {
        // Wait time is over
        break;
      }
    } else {
      // No wait limit, wait if necessary forever
      wait_time_millis = 0;
    }

    // Wait until the next event or the remaining timeout
    {
      MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);

      if (should_terminate() || _collector->_full_gc_requested) {
        return;
      }
      set_CMS_flag(CMS_cms_wants_token);   // to provoke notifies
      assert(t_millis == 0 || wait_time_millis > 0, "Sanity");
      CGC_lock->wait(Mutex::_no_safepoint_check_flag, wait_time_millis);
      clear_CMS_flag(CMS_cms_wants_token);
      assert(!CMS_flag_is_set(CMS_cms_has_token | CMS_cms_wants_token),
             "Should not be set");
    }

    // Extra wait time check before entering the heap lock to get the collection count
    if(t_millis != 0 && os::elapsedTime() >= end_time_secs) {
      // Wait time is over
      break;
    }

    // Total collections count after the event
    unsigned int after_count;
    {
      MutexLockerEx hl(Heap_lock, Mutex::_no_safepoint_check_flag);
      after_count = gch->total_collections();
    }

    if(before_count != after_count) {
      // There was a collection - success
      break;
    }

    // Too many loops warning
    if(++loop_count == 0) {
      log_warning(gc)("wait_on_cms_lock_for_scavenge() has looped %u times", loop_count - 1);
    }
  }
}

void ConcurrentMarkSweepThread::sleepBeforeNextCycle() {
  while (!should_terminate()) {
    if(CMSWaitDuration >= 0) {
      // Wait until the next synchronous GC, a concurrent full gc
      // request or a timeout, whichever is earlier.
      wait_on_cms_lock_for_scavenge(CMSWaitDuration);
    } else {
      // Wait until any cms_lock event or check interval not to call shouldConcurrentCollect permanently
      wait_on_cms_lock(CMSCheckInterval);
    }
    // Check if we should start a CMS collection cycle
    if (_collector->shouldConcurrentCollect()) {
      return;
    }
    // .. collection criterion not yet met, let's go back
    // and wait some more
  }
}
