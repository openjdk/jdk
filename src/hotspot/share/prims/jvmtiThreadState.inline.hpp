/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_PRIMS_JVMTITHREADSTATE_INLINE_HPP
#define SHARE_PRIMS_JVMTITHREADSTATE_INLINE_HPP

#include "prims/jvmtiThreadState.hpp"

#include "classfile/javaClasses.hpp"
#include "prims/jvmtiEnvThreadState.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/safepointVerifiers.hpp"

// JvmtiEnvThreadStateIterator implementation

inline JvmtiEnvThreadStateIterator::JvmtiEnvThreadStateIterator(JvmtiThreadState* thread_state) {
  state = thread_state;
  Thread::current()->entering_jvmti_env_iteration();
}

inline JvmtiEnvThreadStateIterator::~JvmtiEnvThreadStateIterator() {
  Thread::current()->leaving_jvmti_env_iteration();
}

inline JvmtiEnvThreadState* JvmtiEnvThreadStateIterator::first() {
  return state->head_env_thread_state();
}

inline JvmtiEnvThreadState* JvmtiEnvThreadStateIterator::next(JvmtiEnvThreadState* ets) {
  return ets->next();
}

// JvmtiThreadState implementation

JvmtiEnvThreadState* JvmtiThreadState::env_thread_state(JvmtiEnvBase *env) {
  JvmtiEnvThreadStateIterator it(this);
  for (JvmtiEnvThreadState* ets = it.first(); ets != nullptr; ets = it.next(ets)) {
    if ((JvmtiEnvBase*)(ets->get_env()) == env) {
      return ets;
    }
  }
  return nullptr;
}

JvmtiEnvThreadState* JvmtiThreadState::head_env_thread_state() {
  return _head_env_thread_state;
}

void JvmtiThreadState::set_head_env_thread_state(JvmtiEnvThreadState* ets) {
  _head_env_thread_state = ets;
}

inline JvmtiThreadState* JvmtiThreadState::state_for_while_locked(JavaThread *thread, oop thread_oop) {
  assert(JvmtiThreadState_lock->is_locked(), "sanity check");
  assert(thread != nullptr || thread_oop != nullptr, "sanity check");

  NoSafepointVerifier nsv;  // oop is safe to use.

  // In a case of unmounted virtual thread the thread can be null.
  JvmtiThreadState *state = thread == nullptr ? nullptr : thread->jvmti_thread_state();

  if (state == nullptr && thread != nullptr &&
      (thread->is_exiting() ||
       (thread->threadObj() == nullptr && thread->is_attaching_via_jni())
      )) {
    // Don't add a JvmtiThreadState to a thread that is exiting, or is attaching
    // and does not yet have a Java level thread object allocated.
    return nullptr;
  }

  // Make sure we don't see an incomplete state. An incomplete state can cause
  // a duplicate JvmtiThreadState being created below and bound to the 'thread'
  // incorrectly, which leads to stale JavaThread* from the JvmtiThreadState
  // after the thread exits.
  assert(state == nullptr || state->get_thread_oop() != nullptr,
         "incomplete state");

  if (thread_oop == nullptr) {  // Then thread should not be null (see assert above).
    thread_oop = thread->jvmti_vthread() != nullptr ? thread->jvmti_vthread() : thread->threadObj();
  }
  if (state == nullptr || state->get_thread_oop() != thread_oop) {
    // Check if java_lang_Thread already has a link to the JvmtiThreadState.
    if (thread_oop != nullptr) {  // thread_oop can be null during early VMStart.
      state = java_lang_Thread::jvmti_thread_state(thread_oop);
    }
    if (state == nullptr) {  // Need to create state.
      state = new JvmtiThreadState(thread, thread_oop);
    }
  }
  assert(state != nullptr, "sanity check");
  return state;
}

inline JvmtiThreadState* JvmtiThreadState::state_for(JavaThread *thread, Handle thread_handle) {
  // In a case of unmounted virtual thread the thread can be null.
  JvmtiThreadState* state = thread_handle == nullptr ? thread->jvmti_thread_state() :
                                                java_lang_Thread::jvmti_thread_state(thread_handle());
  if (state == nullptr) {
    MutexLocker mu(JvmtiThreadState_lock);
    // check again with the lock held
    state = state_for_while_locked(thread, thread_handle());
    JvmtiEventController::recompute_thread_filtered(state);
  } else {
    // Check possible safepoint even if state is non-null.
    // (Note: the thread argument isn't the current thread)
    DEBUG_ONLY(JavaThread::current()->check_possible_safepoint());
  }
  return state;
}

inline JavaThread* JvmtiThreadState::get_thread_or_saved() {
  // Use _thread_saved if cthread is detached from JavaThread (_thread == null).
  return (_thread == nullptr && !is_virtual()) ? _thread_saved : _thread;
}

inline void JvmtiThreadState::set_should_post_on_exceptions(bool val) {
  get_thread_or_saved()->set_should_post_on_exceptions_flag(val ? JNI_TRUE : JNI_FALSE);
}

inline void JvmtiThreadState::unbind_from(JvmtiThreadState* state, JavaThread* thread) {
  if (state == nullptr) {
    return;
  }
  // Save thread's interp_only_mode.
  state->_saved_interp_only_mode = thread->get_interp_only_mode();
  state->set_thread(nullptr);  // Make sure stale _thread value is never used.
}

inline void JvmtiThreadState::bind_to(JvmtiThreadState* state, JavaThread* thread) {
  // Restore thread's interp_only_mode.
  thread->set_interp_only_mode(state == nullptr ? 0 : state->_saved_interp_only_mode);

  // Make continuation notice the interp_only_mode change.
  Continuation::set_cont_fastpath_thread_state(thread);

  // Bind JavaThread to JvmtiThreadState.
  thread->set_jvmti_thread_state(state);

  if (state != nullptr) {
    // Bind to JavaThread.
    state->set_thread(thread);
  }
}
#endif // SHARE_PRIMS_JVMTITHREADSTATE_INLINE_HPP
