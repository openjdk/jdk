/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/referencePendingListLocker.hpp"
#include "memory/universe.hpp"
#include "runtime/javaCalls.hpp"
#include "utilities/preserveException.hpp"

ReferencePendingListLockerThread::ReferencePendingListLockerThread() :
  JavaThread(&start),
  _monitor(Monitor::nonleaf, "ReferencePendingListLocker", false, Monitor::_safepoint_check_sometimes),
  _message(NONE) {}

ReferencePendingListLockerThread* ReferencePendingListLockerThread::create(TRAPS) {
  // Create Java thread objects
  instanceKlassHandle thread_klass = SystemDictionary::resolve_or_fail(vmSymbols::java_lang_Thread(), true, CHECK_NULL);
  instanceHandle thread_object = thread_klass->allocate_instance_handle(CHECK_NULL);
  Handle thread_name = java_lang_String::create_from_str("Reference Pending List Locker", CHECK_NULL);
  Handle thread_group = Universe::system_thread_group();
  JavaValue result(T_VOID);
  JavaCalls::call_special(&result,
                          thread_object,
                          thread_klass,
                          vmSymbols::object_initializer_name(),
                          vmSymbols::threadgroup_string_void_signature(),
                          thread_group,
                          thread_name,
                          CHECK_NULL);

  {
    MutexLocker ml(Threads_lock);

    // Allocate thread
    ReferencePendingListLockerThread* thread = new ReferencePendingListLockerThread();
    if (thread == NULL || thread->osthread() == NULL) {
      vm_exit_during_initialization("java.lang.OutOfMemoryError",
                                    os::native_thread_creation_failed_msg());
    }

    // Initialize thread
    java_lang_Thread::set_thread(thread_object(), thread);
    java_lang_Thread::set_priority(thread_object(), NearMaxPriority);
    java_lang_Thread::set_daemon(thread_object());
    thread->set_threadObj(thread_object());

    // Start thread
    Threads::add(thread);
    Thread::start(thread);

    return thread;
  }
}

void ReferencePendingListLockerThread::start(JavaThread* thread, TRAPS) {
  ReferencePendingListLockerThread* locker_thread = static_cast<ReferencePendingListLockerThread*>(thread);
  locker_thread->receive_and_handle_messages();
}

bool ReferencePendingListLockerThread::is_hidden_from_external_view() const {
  return true;
}

void ReferencePendingListLockerThread::send_message(Message message) {
  assert(message != NONE, "Should not be none");
  MonitorLockerEx ml(&_monitor, Monitor::_no_safepoint_check_flag);

  // Wait for completion of current message
  while (_message != NONE) {
    ml.wait(Monitor::_no_safepoint_check_flag);
  }

  // Send new message
  _message = message;
  ml.notify_all();

  // Wait for completion of new message
  while (_message != NONE) {
    ml.wait(Monitor::_no_safepoint_check_flag);
  }
}

void ReferencePendingListLockerThread::receive_and_handle_messages() {
  ReferencePendingListLocker pending_list_locker;
  MonitorLockerEx ml(&_monitor);

  // Main loop, never terminates
  for (;;) {
    // Wait for message
    while (_message == NONE) {
      ml.wait();
    }

    // Handle message
    if (_message == LOCK) {
      pending_list_locker.lock();
    } else if (_message == UNLOCK) {
      pending_list_locker.unlock();
    } else {
      ShouldNotReachHere();
    }

    // Clear message
    _message = NONE;
    ml.notify_all();
  }
}

void ReferencePendingListLockerThread::lock() {
  send_message(LOCK);
}

void ReferencePendingListLockerThread::unlock() {
  send_message(UNLOCK);
}

bool ReferencePendingListLocker::_is_initialized = false;
ReferencePendingListLockerThread* ReferencePendingListLocker::_locker_thread = NULL;

void ReferencePendingListLocker::initialize(bool needs_locker_thread, TRAPS) {
  if (needs_locker_thread) {
    _locker_thread = ReferencePendingListLockerThread::create(CHECK);
  }

  _is_initialized = true;
}

bool ReferencePendingListLocker::is_initialized() {
  return _is_initialized;
}

bool ReferencePendingListLocker::is_locked_by_self() {
  oop pending_list_lock = java_lang_ref_Reference::pending_list_lock();
  if (pending_list_lock == NULL) {
    return false;
  }

  JavaThread* thread = JavaThread::current();
  Handle handle(thread, pending_list_lock);
  return ObjectSynchronizer::current_thread_holds_lock(thread, handle);
}

void ReferencePendingListLocker::lock() {
  assert(!Heap_lock->owned_by_self(), "Heap_lock must not be owned by requesting thread");

  if (Thread::current()->is_Java_thread()) {
    assert(java_lang_ref_Reference::pending_list_lock() != NULL, "Not initialized");

    // We may enter this with a pending exception
    PRESERVE_EXCEPTION_MARK;

    HandleMark hm;
    Handle handle(THREAD, java_lang_ref_Reference::pending_list_lock());

    assert(!is_locked_by_self(), "Should not be locked by self");

    // Lock
    ObjectSynchronizer::fast_enter(handle, &_basic_lock, false, THREAD);

    assert(is_locked_by_self(), "Locking failed");

    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
    }
  } else {
    // Delegate operation to locker thread
    assert(_locker_thread != NULL, "Locker thread not created");
    _locker_thread->lock();
  }
}

void ReferencePendingListLocker::unlock() {
  if (Thread::current()->is_Java_thread()) {
    assert(java_lang_ref_Reference::pending_list_lock() != NULL, "Not initialized");

    // We may enter this with a pending exception
    PRESERVE_EXCEPTION_MARK;

    HandleMark hm;
    Handle handle(THREAD, java_lang_ref_Reference::pending_list_lock());

    assert(is_locked_by_self(), "Should be locked by self");

    // Notify waiters if the pending list is non-empty
    if (java_lang_ref_Reference::pending_list() != NULL) {
      ObjectSynchronizer::notifyall(handle, THREAD);
    }

    // Unlock
    ObjectSynchronizer::fast_exit(handle(), &_basic_lock, THREAD);

    assert(!is_locked_by_self(), "Unlocking failed");

    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
    }
  } else {
    // Delegate operation to locker thread
    assert(_locker_thread != NULL, "Locker thread not created");
    _locker_thread->unlock();
  }
}
