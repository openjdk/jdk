/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_concurrentMarkSweepThread.cpp.incl"

// ======= Concurrent Mark Sweep Thread ========

// The CMS thread is created when Concurrent Mark Sweep is used in the
// older of two generations in a generational memory system.

ConcurrentMarkSweepThread*
     ConcurrentMarkSweepThread::_cmst     = NULL;
CMSCollector* ConcurrentMarkSweepThread::_collector = NULL;
bool ConcurrentMarkSweepThread::_should_terminate = false;
int  ConcurrentMarkSweepThread::_CMS_flag         = CMS_nil;

volatile jint ConcurrentMarkSweepThread::_pending_yields      = 0;
volatile jint ConcurrentMarkSweepThread::_pending_decrements  = 0;

volatile bool ConcurrentMarkSweepThread::_icms_enabled   = false;
volatile bool ConcurrentMarkSweepThread::_should_run     = false;
// When icms is enabled, the icms thread is stopped until explicitly
// started.
volatile bool ConcurrentMarkSweepThread::_should_stop    = true;

SurrogateLockerThread*
     ConcurrentMarkSweepThread::_slt = NULL;
SurrogateLockerThread::SLT_msg_type
     ConcurrentMarkSweepThread::_sltBuffer = SurrogateLockerThread::empty;
Monitor*
     ConcurrentMarkSweepThread::_sltMonitor = NULL;

ConcurrentMarkSweepThread::ConcurrentMarkSweepThread(CMSCollector* collector)
  : ConcurrentGCThread() {
  assert(UseConcMarkSweepGC,  "UseConcMarkSweepGC should be set");
  assert(_cmst == NULL, "CMS thread already created");
  _cmst = this;
  assert(_collector == NULL, "Collector already set");
  _collector = collector;

  set_name("Concurrent Mark-Sweep GC Thread");

  if (os::create_thread(this, os::cgc_thread)) {
    // XXX: need to set this to low priority
    // unless "agressive mode" set; priority
    // should be just less than that of VMThread.
    os::set_priority(this, NearMaxPriority);
    if (!DisableStartThread) {
      os::start_thread(this);
    }
  }
  _sltMonitor = SLT_lock;
  set_icms_enabled(CMSIncrementalMode);
}

void ConcurrentMarkSweepThread::run() {
  assert(this == cmst(), "just checking");

  this->record_stack_base_and_size();
  this->initialize_thread_local_storage();
  this->set_active_handles(JNIHandleBlock::allocate_block());
  // From this time Thread::current() should be working.
  assert(this == Thread::current(), "just checking");
  if (BindCMSThreadToCPU && !os::bind_to_processor(CPUForCMSThread)) {
    warning("Couldn't bind CMS thread to processor %u", CPUForCMSThread);
  }
  // Wait until Universe::is_fully_initialized()
  {
    CMSLoopCountWarn loopX("CMS::run", "waiting for "
                           "Universe::is_fully_initialized()", 2);
    MutexLockerEx x(CGC_lock, true);
    set_CMS_flag(CMS_cms_wants_token);
    // Wait until Universe is initialized and all initialization is completed.
    while (!is_init_completed() && !Universe::is_fully_initialized() &&
           !_should_terminate) {
      CGC_lock->wait(true, 200);
      loopX.tick();
    }
    // Wait until the surrogate locker thread that will do
    // pending list locking on our behalf has been created.
    // We cannot start the SLT thread ourselves since we need
    // to be a JavaThread to do so.
    CMSLoopCountWarn loopY("CMS::run", "waiting for SLT installation", 2);
    while (_slt == NULL && !_should_terminate) {
      CGC_lock->wait(true, 200);
      loopY.tick();
    }
    clear_CMS_flag(CMS_cms_wants_token);
  }

  while (!_should_terminate) {
    sleepBeforeNextCycle();
    if (_should_terminate) break;
    _collector->collect_in_background(false);  // !clear_all_soft_refs
  }
  assert(_should_terminate, "just checking");
  // Check that the state of any protocol for synchronization
  // between background (CMS) and foreground collector is "clean"
  // (i.e. will not potentially block the foreground collector,
  // requiring action by us).
  verify_ok_to_terminate();
  // Signal that it is terminated
  {
    MutexLockerEx mu(Terminator_lock,
                     Mutex::_no_safepoint_check_flag);
    assert(_cmst == this, "Weird!");
    _cmst = NULL;
    Terminator_lock->notify();
  }

  // Thread destructor usually does this..
  ThreadLocalStorage::set_thread(NULL);
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
  if (!_should_terminate) {
    assert(cmst() == NULL, "start() called twice?");
    ConcurrentMarkSweepThread* th = new ConcurrentMarkSweepThread(collector);
    assert(cmst() == th, "Where did the just-created CMS thread go?");
    return th;
  }
  return NULL;
}

void ConcurrentMarkSweepThread::stop() {
  if (CMSIncrementalMode) {
    // Disable incremental mode and wake up the thread so it notices the change.
    disable_icms();
    start_icms();
  }
  // it is ok to take late safepoints here, if needed
  {
    MutexLockerEx x(Terminator_lock);
    _should_terminate = true;
  }
  { // Now post a notify on CGC_lock so as to nudge
    // CMS thread(s) that might be slumbering in
    // sleepBeforeNextCycle.
    MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
    CGC_lock->notify_all();
  }
  { // Now wait until (all) CMS thread(s) have exited
    MutexLockerEx x(Terminator_lock);
    while(cmst() != NULL) {
      Terminator_lock->wait();
    }
  }
}

void ConcurrentMarkSweepThread::threads_do(ThreadClosure* tc) {
  assert(tc != NULL, "Null ThreadClosure");
  if (_cmst != NULL) {
    tc->do_thread(_cmst);
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

void ConcurrentMarkSweepThread::print_on(outputStream* st) const {
  st->print("\"%s\" ", name());
  Thread::print_on(st);
  st->cr();
}

void ConcurrentMarkSweepThread::print_all_on(outputStream* st) {
  if (_cmst != NULL) {
    _cmst->print_on(st);
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

// Wait until the next synchronous GC, a concurrent full gc request,
// or a timeout, whichever is earlier.
void ConcurrentMarkSweepThread::wait_on_cms_lock(long t_millis) {
  MutexLockerEx x(CGC_lock,
                  Mutex::_no_safepoint_check_flag);
  if (_should_terminate || _collector->_full_gc_requested) {
    return;
  }
  set_CMS_flag(CMS_cms_wants_token);   // to provoke notifies
  CGC_lock->wait(Mutex::_no_safepoint_check_flag, t_millis);
  clear_CMS_flag(CMS_cms_wants_token);
  assert(!CMS_flag_is_set(CMS_cms_has_token | CMS_cms_wants_token),
         "Should not be set");
}

void ConcurrentMarkSweepThread::sleepBeforeNextCycle() {
  while (!_should_terminate) {
    if (CMSIncrementalMode) {
      icms_wait();
      return;
    } else {
      // Wait until the next synchronous GC, a concurrent full gc
      // request or a timeout, whichever is earlier.
      wait_on_cms_lock(CMSWaitDuration);
    }
    // Check if we should start a CMS collection cycle
    if (_collector->shouldConcurrentCollect()) {
      return;
    }
    // .. collection criterion not yet met, let's go back
    // and wait some more
  }
}

// Incremental CMS
void ConcurrentMarkSweepThread::start_icms() {
  assert(UseConcMarkSweepGC && CMSIncrementalMode, "just checking");
  MutexLockerEx x(iCMS_lock, Mutex::_no_safepoint_check_flag);
  trace_state("start_icms");
  _should_run = true;
  iCMS_lock->notify_all();
}

void ConcurrentMarkSweepThread::stop_icms() {
  assert(UseConcMarkSweepGC && CMSIncrementalMode, "just checking");
  MutexLockerEx x(iCMS_lock, Mutex::_no_safepoint_check_flag);
  if (!_should_stop) {
    trace_state("stop_icms");
    _should_stop = true;
    _should_run = false;
    asynchronous_yield_request();
    iCMS_lock->notify_all();
  }
}

void ConcurrentMarkSweepThread::icms_wait() {
  assert(UseConcMarkSweepGC && CMSIncrementalMode, "just checking");
  if (_should_stop && icms_enabled()) {
    MutexLockerEx x(iCMS_lock, Mutex::_no_safepoint_check_flag);
    trace_state("pause_icms");
    _collector->stats().stop_cms_timer();
    while(!_should_run && icms_enabled()) {
      iCMS_lock->wait(Mutex::_no_safepoint_check_flag);
    }
    _collector->stats().start_cms_timer();
    _should_stop = false;
    trace_state("pause_icms end");
  }
}

// Note: this method, although exported by the ConcurrentMarkSweepThread,
// which is a non-JavaThread, can only be called by a JavaThread.
// Currently this is done at vm creation time (post-vm-init) by the
// main/Primordial (Java)Thread.
// XXX Consider changing this in the future to allow the CMS thread
// itself to create this thread?
void ConcurrentMarkSweepThread::makeSurrogateLockerThread(TRAPS) {
  assert(UseConcMarkSweepGC, "SLT thread needed only for CMS GC");
  assert(_slt == NULL, "SLT already created");
  _slt = SurrogateLockerThread::make(THREAD);
}
