/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "prims/jvmtiAgent.hpp"
#include "prims/jvmtiAgentLibrary.hpp"
#include "prims/jvmtiAgentStartupRequest.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/threads.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/pair.hpp"

static inline bool is_vm_bootstrap() {
  return Threads::number_of_threads() == 0;
}

static inline bool is_not_vm_bootstrap() {
  return !is_vm_bootstrap();
}

#ifdef ASSERT
static void assert_exclusive_access() {
  assert(is_vm_bootstrap() || JvmtiThreadState_lock->owned_by_self(), "invariant");
}
#endif

using JvmtiStartupRequestEntry = Pair<const Thread*, JvmtiAgent*>;
using JvmtiStartupRequestArray = GrowableArrayCHeap<JvmtiStartupRequestEntry, mtServiceability>;

// The startup request array is a LIFO queue holding JvmtiAgents
// during the invocation of their startup routine.It provides
// reachability for jvmtiEnvs to be associated with its agent.
static JvmtiStartupRequestArray* _startup_requests = nullptr;

void JvmtiAgentStartupRequest::initialize() {
  assert(is_vm_bootstrap(), "invariant");
  assert(_startup_requests == nullptr, "invariant");
  _startup_requests = new JvmtiStartupRequestArray(16);
}

bool JvmtiAgentStartupRequest::is_initialized() {
  return _startup_requests != nullptr;
}

JvmtiAgentStartupRequest::JvmtiAgentStartupRequest(JvmtiAgent* agent) : _agent(agent), _thread(Thread::current_or_null_safe()) {
  assert(agent != nullptr, "invariant");
  assert(_startup_requests != nullptr, "invariant");
  ConditionalMutexLocker lock(_thread, JvmtiThreadState_lock, is_not_vm_bootstrap());
  _startup_requests->push(JvmtiStartupRequestEntry(_thread, agent));
}

JvmtiAgentStartupRequest::~JvmtiAgentStartupRequest() {
  ConditionalMutexLocker lock(_thread, JvmtiThreadState_lock, is_not_vm_bootstrap());
  // Startup requests form a logical per-thread stack supporting nested startups.
  // Search from the top of the stack.
  for (int i = _startup_requests->length() - 1; i >= 0; --i) {
    if (_startup_requests->at(i).first == _thread) {
      assert(_startup_requests->at(i).second == _agent, "invariant");
      _startup_requests->remove_at(i);
      return;
    }
  }
  assert(false, "startup request must remain present until scope exit");
}

#ifdef ASSERT
static void assert_associate_startup_request(Thread* thread) {
  assert(thread == nullptr || (thread->is_Java_thread() &&
    JavaThread::cast(thread)->thread_state() == _thread_in_vm), "invariant");
  assert_exclusive_access();
}

static void assert_startup_request(const JvmtiAgent* agent) {
  assert(agent->library() != nullptr, "invariant");
  assert(agent->library()->path() != nullptr, "invariant");
  assert(agent->has_env(), "invariant");
  assert(agent->is_env_associated(), "invariant");
}
#endif

static void associate_env(Thread* thread, const jvmtiEnv* env) {
  DEBUG_ONLY(assert_associate_startup_request(thread);)
  assert(_startup_requests != nullptr, "invariant");
  for (int i = _startup_requests->length() - 1; i >= 0; --i) {
    if (_startup_requests->at(i).first == thread) {
      JvmtiAgent* const agent = _startup_requests->at(i).second;
      if (agent->can_associate_env()) {
        agent->associate_env(env);
        DEBUG_ONLY(assert_startup_request(agent);)
      }
      return;
    }
  }
}

// Invoked by jni_GetEnv() upon successful jvmtiEnv* creation.
void JvmtiAgentStartupRequest::on_env_creation(const jvmtiEnv* env) {
  assert(env != nullptr, "invariant");
  if (is_vm_bootstrap()) {
    associate_env(nullptr, env);
    return;
  }
  JavaThread* const jt = JavaThread::current();
  assert(jt != nullptr, "invariant");
  MACOS_AARCH64_ONLY(ThreadWXEnable __wx(WXWrite, jt));
  ThreadInVMfromNative __tiv(jt);
  VM_ENTRY_BASE(void, install_jvmti_env, jt)
  DEBUG_ONLY(VMNativeEntryWrapper __vew;)
  MutexLocker lock(jt, JvmtiThreadState_lock);
  associate_env(jt, env);
}

JvmtiAgent* JvmtiAgentStartupRequest::find(const jvmtiEnv* env) {
  DEBUG_ONLY(assert_exclusive_access();)
  assert(env != nullptr, "invariant");
  assert(_startup_requests != nullptr, "invariant");
  for (int i = 0; i < _startup_requests->length(); ++i) {
    JvmtiAgent* const agent = _startup_requests->at(i).second;
    if (agent->matches_env(env)) {
      assert(agent->is_env_associated(), "invariant");
      return agent;
    }
  }
  return nullptr;
}
