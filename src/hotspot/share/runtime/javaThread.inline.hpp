/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Azul Systems, Inc. All rights reserved.
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

#ifndef SHARE_RUNTIME_JAVATHREAD_INLINE_HPP
#define SHARE_RUNTIME_JAVATHREAD_INLINE_HPP

#include "runtime/javaThread.hpp"

#include "classfile/javaClasses.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "memory/universe.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oopHandle.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/nonJavaThread.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/safepoint.hpp"

inline void JavaThread::set_suspend_flag(SuspendFlags f) {
  uint32_t flags;
  do {
    flags = _suspend_flags;
  }
  while (Atomic::cmpxchg(&_suspend_flags, flags, (flags | f)) != flags);
}
inline void JavaThread::clear_suspend_flag(SuspendFlags f) {
  uint32_t flags;
  do {
    flags = _suspend_flags;
  }
  while (Atomic::cmpxchg(&_suspend_flags, flags, (flags & ~f)) != flags);
}

inline void JavaThread::set_trace_flag() {
  set_suspend_flag(_trace_flag);
}
inline void JavaThread::clear_trace_flag() {
  clear_suspend_flag(_trace_flag);
}
inline void JavaThread::set_obj_deopt_flag() {
  set_suspend_flag(_obj_deopt);
}
inline void JavaThread::clear_obj_deopt_flag() {
  clear_suspend_flag(_obj_deopt);
}

#if INCLUDE_JVMTI
inline void JavaThread::set_carrier_thread_suspended() {
  _carrier_thread_suspended = true;
}
inline void JavaThread::clear_carrier_thread_suspended() {
  _carrier_thread_suspended = false;
}
#endif

class AsyncExceptionHandshake : public AsyncHandshakeClosure {
  OopHandle _exception;
 public:
  AsyncExceptionHandshake(OopHandle& o, const char* name = "AsyncExceptionHandshake")
  : AsyncHandshakeClosure(name), _exception(o) { }

  ~AsyncExceptionHandshake() {
    Thread* current = Thread::current();
    // Can get here from the VMThread via install_async_exception() bail out.
    if (current->is_Java_thread()) {
      guarantee(JavaThread::cast(current)->is_oop_safe(),
                "JavaThread cannot touch oops after its GC barrier is detached.");
    }
    assert(!_exception.is_empty(), "invariant");
    _exception.release(Universe::vm_global());
  }

  void do_thread(Thread* thr) {
    JavaThread* self = JavaThread::cast(thr);
    assert(self == JavaThread::current(), "must be");

    self->handle_async_exception(exception());
  }
  oop exception() {
    assert(!_exception.is_empty(), "invariant");
    return _exception.resolve();
  }
  bool is_async_exception()   { return true; }
};

class UnsafeAccessErrorHandshake : public AsyncHandshakeClosure {
 public:
  UnsafeAccessErrorHandshake() : AsyncHandshakeClosure("UnsafeAccessErrorHandshake") {}
  void do_thread(Thread* thr) {
    JavaThread* self = JavaThread::cast(thr);
    assert(self == JavaThread::current(), "must be");

    self->handshake_state()->handle_unsafe_access_error();
  }
  bool is_async_exception()   { return true; }
};

inline void JavaThread::set_pending_unsafe_access_error() {
  if (!has_async_exception_condition()) {
    Handshake::execute(new UnsafeAccessErrorHandshake(), this);
  }
}

inline bool JavaThread::has_async_exception_condition() {
  return handshake_state()->has_async_exception_operation();
}

inline JavaThread::NoAsyncExceptionDeliveryMark::NoAsyncExceptionDeliveryMark(JavaThread *t) : _target(t) {
  assert(!_target->handshake_state()->async_exceptions_blocked(), "Nesting is not supported");
  _target->handshake_state()->set_async_exceptions_blocked(true);
}
inline JavaThread::NoAsyncExceptionDeliveryMark::~NoAsyncExceptionDeliveryMark() {
  _target->handshake_state()->set_async_exceptions_blocked(false);
}

inline JavaThreadState JavaThread::thread_state() const    {
#if defined(PPC64) || defined (AARCH64) || defined(RISCV64)
  // Use membars when accessing volatile _thread_state. See
  // Threads::create_vm() for size checks.
  return Atomic::load_acquire(&_thread_state);
#else
  return Atomic::load(&_thread_state);
#endif
}

inline void JavaThread::set_thread_state(JavaThreadState s) {
  assert(current_or_null() == nullptr || current_or_null() == this,
         "state change should only be called by the current thread");
#if defined(PPC64) || defined (AARCH64) || defined(RISCV64)
  // Use membars when accessing volatile _thread_state. See
  // Threads::create_vm() for size checks.
  Atomic::release_store(&_thread_state, s);
#else
  Atomic::store(&_thread_state, s);
#endif
}

inline void JavaThread::set_thread_state_fence(JavaThreadState s) {
  set_thread_state(s);
  OrderAccess::fence();
}

ThreadSafepointState* JavaThread::safepoint_state() const  {
  return _safepoint_state;
}

void JavaThread::set_safepoint_state(ThreadSafepointState *state) {
  _safepoint_state = state;
}

bool JavaThread::is_at_poll_safepoint() {
  return _safepoint_state->is_at_poll_safepoint();
}

bool JavaThread::is_vthread_mounted() const {
  return vthread_continuation() != nullptr;
}

const ContinuationEntry* JavaThread::vthread_continuation() const {
  for (ContinuationEntry* c = last_continuation(); c != nullptr; c = c->parent()) {
    if (c->is_virtual_thread())
      return c;
  }
  return nullptr;
}

void JavaThread::enter_critical() {
  assert(Thread::current() == this ||
         (Thread::current()->is_VM_thread() &&
         SafepointSynchronize::is_synchronizing()),
         "this must be current thread or synchronizing");
  _jni_active_critical++;
}

inline void JavaThread::set_done_attaching_via_jni() {
  _jni_attach_state = _attached_via_jni;
  OrderAccess::fence();
}

inline bool JavaThread::is_exiting() const {
  TerminatedTypes l_terminated = Atomic::load_acquire(&_terminated);
  return l_terminated == _thread_exiting ||
         l_terminated == _thread_gc_barrier_detached ||
         check_is_terminated(l_terminated);
}

inline bool JavaThread::is_oop_safe() const {
  TerminatedTypes l_terminated = Atomic::load_acquire(&_terminated);
  return l_terminated != _thread_gc_barrier_detached &&
         !check_is_terminated(l_terminated);
}

inline bool JavaThread::is_terminated() const {
  TerminatedTypes l_terminated = Atomic::load_acquire(&_terminated);
  return check_is_terminated(l_terminated);
}

inline void JavaThread::set_terminated(TerminatedTypes t) {
  Atomic::release_store(&_terminated, t);
}

inline bool JavaThread::is_active_Java_thread() const {
  return on_thread_list() && !is_terminated();
}

// Allow tracking of class initialization monitor use
inline void JavaThread::set_class_to_be_initialized(InstanceKlass* k) {
  assert((k == nullptr && _class_to_be_initialized != nullptr) ||
         (k != nullptr && _class_to_be_initialized == nullptr), "incorrect usage");
  assert(this == Thread::current(), "Only the current thread can set this field");
  _class_to_be_initialized = k;
}

inline InstanceKlass* JavaThread::class_to_be_initialized() const {
  return _class_to_be_initialized;
}

#endif // SHARE_RUNTIME_JAVATHREAD_INLINE_HPP
