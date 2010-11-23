/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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

// no precompiled headers
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/vmThread.hpp"
#ifdef TARGET_ARCH_x86
# include "assembler_x86.inline.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "assembler_sparc.inline.hpp"
#endif

# include <signal.h>

 // ***************************************************************
 // Platform dependent initialization and cleanup
 // ***************************************************************

void OSThread::pd_initialize() {
  _thread_id                         = 0;
  sigemptyset(&_caller_sigmask);

  _current_callback                  = NULL;
  _current_callback_lock = VM_Version::supports_compare_and_exchange() ? NULL
                    : new Mutex(Mutex::suspend_resume, "Callback_lock", true);

  _saved_interrupt_thread_state      = _thread_new;
  _vm_created_thread                 = false;
}

void OSThread::pd_destroy() {
}

// Synchronous interrupt support
//
// _current_callback == NULL          no pending callback
//                   == 1             callback_in_progress
//                   == other value   pointer to the pending callback
//

// CAS on v8 is implemented by using a global atomic_memory_operation_lock,
// which is shared by other atomic functions. It is OK for normal uses, but
// dangerous if used after some thread is suspended or if used in signal
// handlers. Instead here we use a special per-thread lock to synchronize
// updating _current_callback if we are running on v8. Note in general trying
// to grab locks after a thread is suspended is not safe, but it is safe for
// updating _current_callback, because synchronous interrupt callbacks are
// currently only used in:
// 1. GetThreadPC_Callback - used by WatcherThread to profile VM thread
// There is no overlap between the callbacks, which means we won't try to
// grab a thread's sync lock after the thread has been suspended while holding
// the same lock.

// used after a thread is suspended
static intptr_t compare_and_exchange_current_callback (
       intptr_t callback, intptr_t *addr, intptr_t compare_value, Mutex *sync) {
  if (VM_Version::supports_compare_and_exchange()) {
    return Atomic::cmpxchg_ptr(callback, addr, compare_value);
  } else {
    MutexLockerEx ml(sync, Mutex::_no_safepoint_check_flag);
    if (*addr == compare_value) {
      *addr = callback;
      return compare_value;
    } else {
      return callback;
    }
  }
}

// used in signal handler
static intptr_t exchange_current_callback(intptr_t callback, intptr_t *addr, Mutex *sync) {
  if (VM_Version::supports_compare_and_exchange()) {
    return Atomic::xchg_ptr(callback, addr);
  } else {
    MutexLockerEx ml(sync, Mutex::_no_safepoint_check_flag);
    intptr_t cb = *addr;
    *addr = callback;
    return cb;
  }
}

// one interrupt at a time. spin if _current_callback != NULL
int OSThread::set_interrupt_callback(Sync_Interrupt_Callback * cb) {
  int count = 0;
  while (compare_and_exchange_current_callback(
         (intptr_t)cb, (intptr_t *)&_current_callback, (intptr_t)NULL, _current_callback_lock) != NULL) {
    while (_current_callback != NULL) {
      count++;
#ifdef ASSERT
      if ((WarnOnStalledSpinLock > 0) &&
          (count % WarnOnStalledSpinLock == 0)) {
          warning("_current_callback seems to be stalled: %p", _current_callback);
      }
#endif
      os::yield_all(count);
    }
  }
  return 0;
}

// reset _current_callback, spin if _current_callback is callback_in_progress
void OSThread::remove_interrupt_callback(Sync_Interrupt_Callback * cb) {
  int count = 0;
  while (compare_and_exchange_current_callback(
         (intptr_t)NULL, (intptr_t *)&_current_callback, (intptr_t)cb, _current_callback_lock) != (intptr_t)cb) {
#ifdef ASSERT
    intptr_t p = (intptr_t)_current_callback;
    assert(p == (intptr_t)callback_in_progress ||
           p == (intptr_t)cb, "wrong _current_callback value");
#endif
    while (_current_callback != cb) {
      count++;
#ifdef ASSERT
      if ((WarnOnStalledSpinLock > 0) &&
          (count % WarnOnStalledSpinLock == 0)) {
          warning("_current_callback seems to be stalled: %p", _current_callback);
      }
#endif
      os::yield_all(count);
    }
  }
}

void OSThread::do_interrupt_callbacks_at_interrupt(InterruptArguments *args) {
  Sync_Interrupt_Callback * cb;
  cb = (Sync_Interrupt_Callback *)exchange_current_callback(
        (intptr_t)callback_in_progress, (intptr_t *)&_current_callback, _current_callback_lock);

  if (cb == NULL) {
    // signal is delivered too late (thread is masking interrupt signal??).
    // there is nothing we need to do because requesting thread has given up.
  } else if ((intptr_t)cb == (intptr_t)callback_in_progress) {
    fatal("invalid _current_callback state");
  } else {
    assert(cb->target()->osthread() == this, "wrong target");
    cb->execute(args);
    cb->leave_callback();             // notify the requester
  }

  // restore original _current_callback value
  intptr_t p;
  p = exchange_current_callback((intptr_t)cb, (intptr_t *)&_current_callback, _current_callback_lock);
  assert(p == (intptr_t)callback_in_progress, "just checking");
}

// Called by the requesting thread to send a signal to target thread and
// execute "this" callback from the signal handler.
int OSThread::Sync_Interrupt_Callback::interrupt(Thread * target, int timeout) {
  // Let signals to the vm_thread go even if the Threads_lock is not acquired
  assert(Threads_lock->owned_by_self() || (target == VMThread::vm_thread()),
         "must have threads lock to call this");

  OSThread * osthread = target->osthread();

  // may block if target thread already has a pending callback
  osthread->set_interrupt_callback(this);

  _target = target;

  int rslt = thr_kill(osthread->thread_id(), os::Solaris::SIGasync());
  assert(rslt == 0, "thr_kill != 0");

  bool status = false;
  jlong t1 = os::javaTimeMillis();
  { // don't use safepoint check because we might be the watcher thread.
    MutexLockerEx ml(_sync, Mutex::_no_safepoint_check_flag);
    while (!is_done()) {
      status = _sync->wait(Mutex::_no_safepoint_check_flag, timeout);

      // status == true if timed out
      if (status) break;

      // update timeout
      jlong t2 = os::javaTimeMillis();
      timeout -= t2 - t1;
      t1 = t2;
    }
  }

  // reset current_callback
  osthread->remove_interrupt_callback(this);

  return status;
}

void OSThread::Sync_Interrupt_Callback::leave_callback() {
  if (!_sync->owned_by_self()) {
    // notify requesting thread
    MutexLockerEx ml(_sync, Mutex::_no_safepoint_check_flag);
    _is_done = true;
    _sync->notify_all();
  } else {
    // Current thread is interrupted while it is holding the _sync lock, trying
    // to grab it again will deadlock. The requester will timeout anyway,
    // so just return.
    _is_done = true;
  }
}

// copied from synchronizer.cpp

void OSThread::handle_spinlock_contention(int tries) {
  if (NoYieldsInMicrolock) return;

  if (tries > 10) {
    os::yield_all(tries); // Yield to threads of any priority
  } else if (tries > 5) {
    os::yield();          // Yield to threads of same or higher priority
  }
}
