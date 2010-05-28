/*
 * Copyright (c) 2001, 2009, Oracle and/or its affiliates. All rights reserved.
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

// CopyrightVersion 1.2

# include "incls/_precompiled.incl"
# include "incls/_concurrentGCThread.cpp.incl"

int  ConcurrentGCThread::_CGC_flag            = CGC_nil;

SuspendibleThreadSet ConcurrentGCThread::_sts;

ConcurrentGCThread::ConcurrentGCThread() :
  _should_terminate(false), _has_terminated(false) {
  _sts.initialize();
};

void ConcurrentGCThread::stopWorldAndDo(VoidClosure* op) {
  MutexLockerEx x(Heap_lock,
                  Mutex::_no_safepoint_check_flag);
  // warning("CGC: about to try stopping world");
  SafepointSynchronize::begin();
  // warning("CGC: successfully stopped world");
  op->do_void();
  SafepointSynchronize::end();
  // warning("CGC: successfully restarted world");
}

void ConcurrentGCThread::safepoint_synchronize() {
  _sts.suspend_all();
}

void ConcurrentGCThread::safepoint_desynchronize() {
  _sts.resume_all();
}

void ConcurrentGCThread::create_and_start() {
  if (os::create_thread(this, os::cgc_thread)) {
    // XXX: need to set this to low priority
    // unless "agressive mode" set; priority
    // should be just less than that of VMThread.
    os::set_priority(this, NearMaxPriority);
    if (!_should_terminate && !DisableStartThread) {
      os::start_thread(this);
    }
  }
}

void ConcurrentGCThread::initialize_in_thread() {
  this->record_stack_base_and_size();
  this->initialize_thread_local_storage();
  this->set_active_handles(JNIHandleBlock::allocate_block());
  // From this time Thread::current() should be working.
  assert(this == Thread::current(), "just checking");
}

void ConcurrentGCThread::wait_for_universe_init() {
  MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
  while (!is_init_completed() && !_should_terminate) {
    CGC_lock->wait(Mutex::_no_safepoint_check_flag, 200);
  }
}

void ConcurrentGCThread::terminate() {
  // Signal that it is terminated
  {
    MutexLockerEx mu(Terminator_lock,
                     Mutex::_no_safepoint_check_flag);
    _has_terminated = true;
    Terminator_lock->notify();
  }

  // Thread destructor usually does this..
  ThreadLocalStorage::set_thread(NULL);
}


void SuspendibleThreadSet::initialize_work() {
  MutexLocker x(STS_init_lock);
  if (!_initialized) {
    _m             = new Monitor(Mutex::leaf,
                                 "SuspendibleThreadSetLock", true);
    _async         = 0;
    _async_stop    = false;
    _async_stopped = 0;
    _initialized   = true;
  }
}

void SuspendibleThreadSet::join() {
  initialize();
  MutexLockerEx x(_m, Mutex::_no_safepoint_check_flag);
  while (_async_stop) _m->wait(Mutex::_no_safepoint_check_flag);
  _async++;
  assert(_async > 0, "Huh.");
}

void SuspendibleThreadSet::leave() {
  assert(_initialized, "Must be initialized.");
  MutexLockerEx x(_m, Mutex::_no_safepoint_check_flag);
  _async--;
  assert(_async >= 0, "Huh.");
  if (_async_stop) _m->notify_all();
}

void SuspendibleThreadSet::yield(const char* id) {
  assert(_initialized, "Must be initialized.");
  if (_async_stop) {
    MutexLockerEx x(_m, Mutex::_no_safepoint_check_flag);
    if (_async_stop) {
      _async_stopped++;
      assert(_async_stopped > 0, "Huh.");
      if (_async_stopped == _async) {
        if (ConcGCYieldTimeout > 0) {
          double now = os::elapsedTime();
          guarantee((now - _suspend_all_start) * 1000.0 <
                    (double)ConcGCYieldTimeout,
                    "Long delay; whodunit?");
        }
      }
      _m->notify_all();
      while (_async_stop) _m->wait(Mutex::_no_safepoint_check_flag);
      _async_stopped--;
      assert(_async >= 0, "Huh");
      _m->notify_all();
    }
  }
}

void SuspendibleThreadSet::suspend_all() {
  initialize();  // If necessary.
  if (ConcGCYieldTimeout > 0) {
    _suspend_all_start = os::elapsedTime();
  }
  MutexLockerEx x(_m, Mutex::_no_safepoint_check_flag);
  assert(!_async_stop, "Only one at a time.");
  _async_stop = true;
  while (_async_stopped < _async) _m->wait(Mutex::_no_safepoint_check_flag);
}

void SuspendibleThreadSet::resume_all() {
  assert(_initialized, "Must be initialized.");
  MutexLockerEx x(_m, Mutex::_no_safepoint_check_flag);
  assert(_async_stopped == _async, "Huh.");
  _async_stop = false;
  _m->notify_all();
}

static void _sltLoop(JavaThread* thread, TRAPS) {
  SurrogateLockerThread* slt = (SurrogateLockerThread*)thread;
  slt->loop();
}

SurrogateLockerThread::SurrogateLockerThread() :
  JavaThread(&_sltLoop),
  _monitor(Mutex::nonleaf, "SLTMonitor"),
  _buffer(empty)
{}

SurrogateLockerThread* SurrogateLockerThread::make(TRAPS) {
  klassOop k =
    SystemDictionary::resolve_or_fail(vmSymbolHandles::java_lang_Thread(),
                                      true, CHECK_NULL);
  instanceKlassHandle klass (THREAD, k);
  instanceHandle thread_oop = klass->allocate_instance_handle(CHECK_NULL);

  const char thread_name[] = "Surrogate Locker Thread (CMS)";
  Handle string = java_lang_String::create_from_str(thread_name, CHECK_NULL);

  // Initialize thread_oop to put it into the system threadGroup
  Handle thread_group (THREAD, Universe::system_thread_group());
  JavaValue result(T_VOID);
  JavaCalls::call_special(&result, thread_oop,
                          klass,
                          vmSymbolHandles::object_initializer_name(),
                          vmSymbolHandles::threadgroup_string_void_signature(),
                          thread_group,
                          string,
                          CHECK_NULL);

  SurrogateLockerThread* res;
  {
    MutexLocker mu(Threads_lock);
    res = new SurrogateLockerThread();

    // At this point it may be possible that no osthread was created for the
    // JavaThread due to lack of memory. We would have to throw an exception
    // in that case. However, since this must work and we do not allow
    // exceptions anyway, check and abort if this fails.
    if (res == NULL || res->osthread() == NULL) {
      vm_exit_during_initialization("java.lang.OutOfMemoryError",
                                    "unable to create new native thread");
    }
    java_lang_Thread::set_thread(thread_oop(), res);
    java_lang_Thread::set_priority(thread_oop(), NearMaxPriority);
    java_lang_Thread::set_daemon(thread_oop());

    res->set_threadObj(thread_oop());
    Threads::add(res);
    Thread::start(res);
  }
  os::yield(); // This seems to help with initial start-up of SLT
  return res;
}

void SurrogateLockerThread::manipulatePLL(SLT_msg_type msg) {
  MutexLockerEx x(&_monitor, Mutex::_no_safepoint_check_flag);
  assert(_buffer == empty, "Should be empty");
  assert(msg != empty, "empty message");
  _buffer = msg;
  while (_buffer != empty) {
    _monitor.notify();
    _monitor.wait(Mutex::_no_safepoint_check_flag);
  }
}

// ======= Surrogate Locker Thread =============

void SurrogateLockerThread::loop() {
  BasicLock pll_basic_lock;
  SLT_msg_type msg;
  debug_only(unsigned int owned = 0;)

  while (/* !isTerminated() */ 1) {
    {
      MutexLocker x(&_monitor);
      // Since we are a JavaThread, we can't be here at a safepoint.
      assert(!SafepointSynchronize::is_at_safepoint(),
             "SLT is a JavaThread");
      // wait for msg buffer to become non-empty
      while (_buffer == empty) {
        _monitor.notify();
        _monitor.wait();
      }
      msg = _buffer;
    }
    switch(msg) {
      case acquirePLL: {
        instanceRefKlass::acquire_pending_list_lock(&pll_basic_lock);
        debug_only(owned++;)
        break;
      }
      case releaseAndNotifyPLL: {
        assert(owned > 0, "Don't have PLL");
        instanceRefKlass::release_and_notify_pending_list_lock(&pll_basic_lock);
        debug_only(owned--;)
        break;
      }
      case empty:
      default: {
        guarantee(false,"Unexpected message in _buffer");
        break;
      }
    }
    {
      MutexLocker x(&_monitor);
      // Since we are a JavaThread, we can't be here at a safepoint.
      assert(!SafepointSynchronize::is_at_safepoint(),
             "SLT is a JavaThread");
      _buffer = empty;
      _monitor.notify();
    }
  }
  assert(!_monitor.owned_by_self(), "Should unlock before exit.");
}


// ===== STS Access From Outside CGCT =====

void ConcurrentGCThread::stsYield(const char* id) {
  assert( Thread::current()->is_ConcurrentGC_thread(),
          "only a conc GC thread can call this" );
  _sts.yield(id);
}

bool ConcurrentGCThread::stsShouldYield() {
  assert( Thread::current()->is_ConcurrentGC_thread(),
          "only a conc GC thread can call this" );
  return _sts.should_yield();
}

void ConcurrentGCThread::stsJoin() {
  assert( Thread::current()->is_ConcurrentGC_thread(),
          "only a conc GC thread can call this" );
  _sts.join();
}

void ConcurrentGCThread::stsLeave() {
  assert( Thread::current()->is_ConcurrentGC_thread(),
          "only a conc GC thread can call this" );
  _sts.leave();
}
