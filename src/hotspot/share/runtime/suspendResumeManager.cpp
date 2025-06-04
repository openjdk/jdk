/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "prims/jvmtiThreadState.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/suspendResumeManager.hpp"

// This is the closure that prevents a suspended JavaThread from
// escaping the suspend request.
class ThreadSelfSuspensionHandshake : public AsyncHandshakeClosure {
public:
  ThreadSelfSuspensionHandshake() : AsyncHandshakeClosure("ThreadSelfSuspensionHandshake") {}
  void do_thread(Thread* thr) {
    JavaThread* current = JavaThread::cast(thr);
    assert(current == Thread::current(), "Must be self executed.");
    JavaThreadState jts = current->thread_state();

    current->set_thread_state(_thread_blocked);
    current->suspend_resume_manager()->do_owner_suspend();
    current->set_thread_state(jts);
    current->suspend_resume_manager()->set_async_suspend_handshake(false);
  }
  virtual bool is_suspend() { return true; }
};

// This is the closure that synchronously honors the suspend request.
class SuspendThreadHandshake : public HandshakeClosure {
  bool _register_vthread_SR;
  bool _did_suspend;
public:
  SuspendThreadHandshake(bool register_vthread_SR) : HandshakeClosure("SuspendThread"),
    _register_vthread_SR(register_vthread_SR), _did_suspend(false) {
  }
  void do_thread(Thread* thr) {
    JavaThread* target = JavaThread::cast(thr);
    _did_suspend = target->suspend_resume_manager()->suspend_with_handshake(_register_vthread_SR);
  }
  bool did_suspend() { return _did_suspend; }
};

void SuspendResumeManager::set_suspended(bool is_suspend, bool register_vthread_SR) {
#if INCLUDE_JVMTI
  if (register_vthread_SR) {
    assert(_handshakee->is_vthread_mounted(), "sanity check");
    if (is_suspend) {
      JvmtiVTSuspender::register_vthread_suspend(_handshakee->vthread());
    }
    else {
      JvmtiVTSuspender::register_vthread_resume(_handshakee->vthread());
    }
  }
#endif
  Atomic::store(&_suspended, is_suspend);
}

bool SuspendResumeManager::suspend(bool register_vthread_SR) {
  JVMTI_ONLY(assert(!_handshakee->is_in_VTMS_transition(), "no suspend allowed in VTMS transition");)
    JavaThread* self = JavaThread::current();
  if (_handshakee == self) {
    // If target is the current thread we can bypass the handshake machinery
    // and just suspend directly
    ThreadBlockInVM tbivm(self);
    MutexLocker ml(_state_lock, Mutex::_no_safepoint_check_flag);
    set_suspended(true, register_vthread_SR);
    do_owner_suspend();
    return true;
  } else {
    SuspendThreadHandshake st(register_vthread_SR);
    Handshake::execute(&st, _handshakee);
    return st.did_suspend();
  }
}

bool SuspendResumeManager::resume(bool register_vthread_SR) {
  MutexLocker ml(_state_lock, Mutex::_no_safepoint_check_flag);
  if (!is_suspended()) {
    assert(!_handshakee->is_suspended(), "cannot be suspended without a suspend request");
    return false;
  }
  // Resume the thread.
  set_suspended(false, register_vthread_SR);
  _state_lock->notify();
  return true;
}

void SuspendResumeManager::do_owner_suspend() {
  assert(Thread::current() == _handshakee, "should call from _handshakee");
  assert(_state_lock->owned_by_self(), "Lock must be held");
  assert(!_handshakee->has_last_Java_frame() || _handshakee->frame_anchor()->walkable(), "should have walkable stack");
  assert(_handshakee->thread_state() == _thread_blocked, "Caller should have transitioned to _thread_blocked");

  while (is_suspended()) {
    log_trace(thread, suspend)("JavaThread:" INTPTR_FORMAT " suspended", p2i(_handshakee));
    _state_lock->wait_without_safepoint_check();
  }
  log_trace(thread, suspend)("JavaThread:" INTPTR_FORMAT " resumed", p2i(_handshakee));
}

bool SuspendResumeManager::suspend_with_handshake(bool register_vthread_SR) {
  assert(_handshakee->threadObj() != nullptr, "cannot suspend with a null threadObj");
  if (_handshakee->is_exiting()) {
    log_trace(thread, suspend)("JavaThread:" INTPTR_FORMAT " exiting", p2i(_handshakee));
    return false;
  }
  if (has_async_suspend_handshake()) {
    if (is_suspended()) {
      // Target is already suspended.
      log_trace(thread, suspend)("JavaThread:" INTPTR_FORMAT " already suspended", p2i(_handshakee));
      return false;
    } else {
      // Target is going to wake up and leave suspension.
      // Let's just stop the thread from doing that.
      log_trace(thread, suspend)("JavaThread:" INTPTR_FORMAT " re-suspended", p2i(_handshakee));
      set_suspended(true, register_vthread_SR);
      return true;
    }
  }
  // no suspend request
  assert(!is_suspended(), "cannot be suspended without a suspend request");
  // Thread is safe, so it must execute the request, thus we can count it as suspended
  // from this point.
  set_suspended(true, register_vthread_SR);
  set_async_suspend_handshake(true);
  log_trace(thread, suspend)("JavaThread:" INTPTR_FORMAT " suspended, arming ThreadSuspension", p2i(_handshakee));
  ThreadSelfSuspensionHandshake* ts = new ThreadSelfSuspensionHandshake();
  Handshake::execute(ts, _handshakee);
  return true;
}

SuspendResumeManager::SuspendResumeManager(JavaThread* thread, Monitor* state_lock) : _handshakee(thread), _state_lock(state_lock), _suspended(false), _async_suspend_handshake(false) {}
