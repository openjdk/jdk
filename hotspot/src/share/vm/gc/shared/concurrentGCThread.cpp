/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/concurrentGCThread.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/os.hpp"

ConcurrentGCThread::ConcurrentGCThread() :
  _should_terminate(false), _has_terminated(false) {
};

void ConcurrentGCThread::create_and_start() {
  if (os::create_thread(this, os::cgc_thread)) {
    // XXX: need to set this to low priority
    // unless "aggressive mode" set; priority
    // should be just less than that of VMThread.
    os::set_priority(this, NearMaxPriority);
    if (!_should_terminate && !DisableStartThread) {
      os::start_thread(this);
    }
  }
}

void ConcurrentGCThread::initialize_in_thread() {
  this->record_stack_base_and_size();
  this->initialize_named_thread();
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
  assert(_should_terminate, "Should only be called on terminate request.");
  // Signal that it is terminated
  {
    MutexLockerEx mu(Terminator_lock,
                     Mutex::_no_safepoint_check_flag);
    _has_terminated = true;
    Terminator_lock->notify();
  }
}

static void _sltLoop(JavaThread* thread, TRAPS) {
  SurrogateLockerThread* slt = (SurrogateLockerThread*)thread;
  slt->loop();
}

SurrogateLockerThread::SurrogateLockerThread() :
  JavaThread(&_sltLoop),
  _monitor(Mutex::nonleaf, "SLTMonitor", false,
           Monitor::_safepoint_check_sometimes),
  _buffer(empty)
{}

SurrogateLockerThread* SurrogateLockerThread::make(TRAPS) {
  Klass* k =
    SystemDictionary::resolve_or_fail(vmSymbols::java_lang_Thread(),
                                      true, CHECK_NULL);
  instanceKlassHandle klass (THREAD, k);
  instanceHandle thread_oop = klass->allocate_instance_handle(CHECK_NULL);

  const char thread_name[] = "Surrogate Locker Thread (Concurrent GC)";
  Handle string = java_lang_String::create_from_str(thread_name, CHECK_NULL);

  // Initialize thread_oop to put it into the system threadGroup
  Handle thread_group (THREAD, Universe::system_thread_group());
  JavaValue result(T_VOID);
  JavaCalls::call_special(&result, thread_oop,
                          klass,
                          vmSymbols::object_initializer_name(),
                          vmSymbols::threadgroup_string_void_signature(),
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
                                    os::native_thread_creation_failed_msg());
    }
    java_lang_Thread::set_thread(thread_oop(), res);
    java_lang_Thread::set_priority(thread_oop(), NearMaxPriority);
    java_lang_Thread::set_daemon(thread_oop());

    res->set_threadObj(thread_oop());
    Threads::add(res);
    Thread::start(res);
  }
  os::naked_yield(); // This seems to help with initial start-up of SLT
  return res;
}

void SurrogateLockerThread::report_missing_slt() {
  vm_exit_during_initialization(
    "GC before GC support fully initialized: "
    "SLT is needed but has not yet been created.");
  ShouldNotReachHere();
}

void SurrogateLockerThread::manipulatePLL(SLT_msg_type msg) {
  MutexLockerEx x(&_monitor, Mutex::_no_safepoint_check_flag);
  assert(_buffer == empty, "Should be empty");
  assert(msg != empty, "empty message");
  assert(!Heap_lock->owned_by_self(), "Heap_lock owned by requesting thread");

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
        InstanceRefKlass::acquire_pending_list_lock(&pll_basic_lock);
        debug_only(owned++;)
        break;
      }
      case releaseAndNotifyPLL: {
        assert(owned > 0, "Don't have PLL");
        InstanceRefKlass::release_and_notify_pending_list_lock(&pll_basic_lock);
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
