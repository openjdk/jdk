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

#include "cds/cdsConfig.hpp"
#include "jni.h"
#include "jvm.h"
#include "memory/resourceArea.hpp"
#include "prims/jvmtiAgent.hpp"
#include "prims/jvmtiAgentInvoker.hpp"
#include "prims/jvmtiAgentLibrary.hpp"
#include "prims/jvmtiAgentLibraryManager.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/threads.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ostream.hpp"

static bool load_agent_library(JvmtiAgent* agent, bool vm_exit_on_error,
                               outputStream* st = nullptr, bool* initial_load = nullptr) {
  assert(agent != nullptr, "invariant");
  if (agent->has_library()) {
    if (initial_load != nullptr) {
      *initial_load = false;
    }
    return true;
  }
  JvmtiAgentLibrary* const library = JvmtiAgentLibraryManager::load(agent, vm_exit_on_error, st, initial_load);
  if (library == nullptr) {
    return false;
  }
  agent->set_library(library);
  return true;
}

static void exit_on_startup_error(const JvmtiAgent* agent, const char* message) {
  vm_exit_during_initialization(message, agent->name());
}

#ifdef ASSERT
static inline bool is_vm_bootstrap() {
  return Threads::number_of_threads() == 0;
}

static void assert_loaded(const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->library() != nullptr, "invariant");
  assert(!agent->is_env_associated(), "invariant");
}

static void assert_on_load(const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(!agent->is_env_associated(), "invariant");
  assert(!agent->is_dynamic(), "invariant");
}
#endif

class AgentJavaThreadEventTransition : StackObj {
 private:
  ResourceMark _rm;
  ThreadToNativeFromVM _transition;
  HandleMark _hm;
  NONCOPYABLE(AgentJavaThreadEventTransition);
 public:
  AgentJavaThreadEventTransition(JavaThread* thread) :
    _rm(thread), _transition(thread), _hm(thread) {}
};

class AgentEventMark : StackObj {
 private:
  JavaThread* const _thread;
  JvmtiThreadState::ExceptionState _saved_exception_state;
  NONCOPYABLE(AgentEventMark);
 public:
  AgentEventMark(JavaThread* thread) : _thread(thread),
    _saved_exception_state(JvmtiThreadState::ES_CLEARED) {
    JvmtiThreadState* const state = thread->jvmti_thread_state();
    // We are before an event. Save the current JVMTI thread exception state.
    if (state != nullptr) {
      _saved_exception_state = state->get_exception_state();
    }
    thread->push_jni_handle_block();
    assert(thread == JavaThread::current(), "thread must be current!");
    thread->frame_anchor()->make_walkable();
  }
  ~AgentEventMark() {
    _thread->pop_jni_handle_block();
    JvmtiThreadState* const state = _thread->jvmti_thread_state();
    // We are continuing after an event. Restore the JVMTI thread exception state.
    if (state != nullptr) {
      state->restore_exception_state(_saved_exception_state);
    }
  }
};

template <typename Target, typename Context>
static jint invoke(Target* target, Context& invocation) {
  assert(target != nullptr, "invariant");
  JavaThread* const thread = JavaThread::current();
  assert(thread != nullptr, "invariant");
  assert(thread->thread_state() == _thread_in_vm, "invariant");

  jint result = JNI_OK;
  {
    AgentEventMark jem(thread);
    AgentJavaThreadEventTransition jet(thread);
    assert(thread->thread_state() == _thread_in_native, "invariant");

    result = invocation(target);

    // May have used JNI.
    if (thread->is_pending_jni_exception_check()) {
      thread->clear_pending_jni_exception_check();
    }
  }

  assert(thread->thread_state() == _thread_in_vm, "invariant");

  // May have used JNI.
  if (thread->has_pending_exception()) {
    thread->clear_pending_exception();
  }
  return result;
}

extern "C" {
  typedef jint(JNICALL* StartupEntry_t)(JavaVM*, char*, void*);
}

static jint invoke_startup(JvmtiAgent* agent, void* entry_point) {
  assert(agent != nullptr, "invariant");
  assert(entry_point != nullptr, "invariant");
  assert(is_vm_bootstrap() || JavaThread::current()->thread_state() == _thread_in_native, "invariant");
  agent->begin_startup();
  if (agent->is_dynamic()) {
    agent->begin_initialization_timing();
  }
  StartupEntry_t target = CAST_TO_FN_PTR(StartupEntry_t, entry_point);
  extern struct JavaVM_ main_vm;
  const jint result = (*target)(&main_vm, const_cast<char*>(agent->options()), nullptr);
  if (agent->is_dynamic()) {
    agent->end_initialization_timing();
  }
  agent->end_startup();
  return result;
}

#ifdef ASSERT
static void assert_attempt_xrun_conversion(const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_xrun(), "invariant");
  assert(agent->library() == nullptr, "invariant");
  assert(!agent->is_env_associated(), "invariant");
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invariant");
}
#endif

void JvmtiAgentInvoker::attempt_xrun_conversion(JvmtiAgent* agent) {
  DEBUG_ONLY(assert_attempt_xrun_conversion(agent);)

  // 1. Ensure the OS library is loaded.
  load_agent_library(agent, true);

  // 2. Lookup Xrun entry point.
  const void* entry_point = agent->library()->resolve_entry_point("JVM_OnLoad");
  if (entry_point == nullptr) {
    // 3. Lookup JVMTI entry point.
    entry_point = agent->library()->resolve_entry_point("Agent_OnLoad");
    if (entry_point == nullptr) {
      exit_on_startup_error(agent, "Unable to convert -Xrun agent because no Agent_OnLoad entry point in library");
    }
    // 4. An Agent_OnLoad entry point was found, so the agent
    //    is reclassified, from an xrun agent to a normal agent.
    agent->convert_from_xrun();
  }
  assert(entry_point != nullptr, "invariant");
}

#if INCLUDE_CDS
static void check_cds_dump() {
  if (CDSConfig::is_dumping_archive()) {
    if (CDSConfig::new_aot_flags_used()) {
      // Agents are allowed with -XX:AOTMode=record and -XX:AOTMode=required/auto.
      // Agents are completely disabled when -XX:AOTMode=create (see cdsConfig.cpp).
      assert(!CDSConfig::is_dumping_final_static_archive(), "agents should have been disabled with -XX:AOTMode=create");
    } else if (CDSConfig::is_dumping_classic_static_archive() || CDSConfig::is_dumping_dynamic_archive()) {
      // Classic CDS (static or dynamic dump). Disallow agents.
      vm_exit_during_cds_dumping("JVMTI agents are not allowed when dumping CDS archives");
    }
  }
}
#endif

#ifdef ASSERT
static void assert_agent_on_load(const JvmtiAgent* agent) {
  assert_on_load(agent);
  assert(!agent->is_xrun(), "invariant");
  assert(is_vm_bootstrap(), "invariant");
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_ONLOAD, "invariant");
}
#endif

static bool locate_and_invoke_Agent_OnLoad(JvmtiAgent* agent) {
  DEBUG_ONLY(assert_loaded(agent);)

  // 1. Locate the Agent_OnLoad entry point.
  void* const entry_point = agent->library()->resolve_entry_point("Agent_OnLoad");
  if (entry_point == nullptr) {
    exit_on_startup_error(agent,
      "Unable to start agent because no Agent_OnLoad entry point in library");
  }

  agent->library()->initialize_path(entry_point);

  // 2. Invoke the Agent_OnLoad entry point.
  const jint result = invoke_startup(agent, entry_point);
  JvmtiAgentLibraryManager::register_for_unload_notification(agent->library());
  if (result != JNI_OK) {
    exit_on_startup_error(agent, "agent library failed Agent_OnLoad");
  }

  return true;
}

static bool invoke_Agent_OnLoad(JvmtiAgent* agent) {
  DEBUG_ONLY(assert_agent_on_load(agent);)
  CDS_ONLY(check_cds_dump();)

  // 1. Ensure the OS library is loaded.
  load_agent_library(agent, true);

  // 2. Find and invoke the Agent_OnLoad entry point in the OS library.
  return locate_and_invoke_Agent_OnLoad(agent);
}

#ifdef ASSERT
static void assert_jvm_on_load(const JvmtiAgent* agent) {
  assert_on_load(agent);
  assert(agent->library() != nullptr, "invariant");
  assert(agent->is_xrun(), "invariant");
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invariant");
}
#endif

static bool invoke_JVM_OnLoad(JvmtiAgent* agent) {
  DEBUG_ONLY(assert_jvm_on_load(agent);)

  // 1. The library must already have been loaded because of the failed
  //    xrun conversion attempt during startup.

  // 2. The JVM_OnLoad entry point must exist because of the failed xrun
  //    conversion attempt during startup.
  void* const entry_point = agent->library()->resolve_entry_point("JVM_OnLoad");
  assert(entry_point != nullptr, "invariant");

  agent->library()->initialize_path(entry_point);

  // 3. Invoke the JVM_OnLoad entry point.
  JavaThread* const thread = JavaThread::current();
  assert(thread != nullptr, "invariant");
  jint result;
  {
    ThreadToNativeFromVM transition(thread);
    HandleMark mark(thread);
    result = invoke_startup(agent, entry_point);
  }

  if (result != JNI_OK) {
    exit_on_startup_error(agent, "-Xrun library failed to init");
  }

  return true;
}

class JvmtiInvokeOnAttachContext {
 private:
  void* const _entry_point;
 public:
  JvmtiInvokeOnAttachContext(void* entry_point) : _entry_point(entry_point) {}
  jint operator()(JvmtiAgent* agent) {
    return invoke_startup(agent, _entry_point);
  }
};

static void print_dynamic_startup_warning(const JvmtiAgent* agent) {
  assert(agent->library() != nullptr, "invariant");
  if (!FLAG_IS_CMDLINE(EnableDynamicAgentLoading) && !agent->is_instrument_lib()) {
    jio_fprintf(defaultStream::error_stream(),
      "WARNING: A JVM TI agent has been loaded dynamically (%s)\n"
      "WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\n"
      "WARNING: Dynamic loading of agents will be disallowed by default in a future release\n", agent->name());
  }
}

#ifdef ASSERT
static void assert_agent_on_attach(const JvmtiAgent* agent, const outputStream* st) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_dynamic(), "invariant");
  assert(agent->library() == nullptr, "invariant");
  assert(!agent->is_env_associated(), "invariant");
  assert(!agent->is_xrun(), "invariant");
  assert(st != nullptr, "invariant");
  assert(EnableDynamicAgentLoading, "invariant");
}
#endif

static bool locate_and_invoke_Agent_OnAttach(JvmtiAgent* agent, outputStream* st, bool initial_load) {
  DEBUG_ONLY(assert_loaded(agent);)
  assert(st != nullptr, "invariant");

  // 1. Issue a warning message on initial load.
  if (initial_load) {
    print_dynamic_startup_warning(agent);
  }

  // 2. Locate the Agent_OnAttach entry point.
  void* const entry_point = agent->library()->resolve_entry_point("Agent_OnAttach");
  if (entry_point == nullptr) {
    st->print_cr("%s is not available in %s", "Agent_OnAttach", agent->name());
    return false;
  }

  agent->library()->initialize_path(entry_point);

  // 3. Invoke the Agent_OnAttach entry point.
  JvmtiInvokeOnAttachContext invocation_context(entry_point);
  const jint result = invoke(agent, invocation_context);
  JvmtiAgentLibraryManager::register_for_unload_notification(agent->library());

  // 4. Process return value.
  st->print_cr("return code: %d", result);
  if (result != JNI_OK) {
    // Do not unload the OS library after a failed Agent_OnAttach invocation.
    // The library is third-party code and may already have started native
    // threads that can execute code in it. Its lifetime is therefore the VM.
    return false;
  }

  return true;
}

static bool invoke_Agent_OnAttach(JvmtiAgent* agent, outputStream* st) {
  DEBUG_ONLY(assert_agent_on_attach(agent, st);)

  // 1. Ensure the OS library is loaded.
  bool initial_load = false;
  if (!load_agent_library(agent, /* vm_exit_on_error */ false, st, &initial_load)) {
    return false;
  }

  // 2. Find and invoke the OnAttach entry point in the OS library.
  return locate_and_invoke_Agent_OnAttach(agent, st, initial_load);
}

bool JvmtiAgentInvoker::invoke_startup(JvmtiAgent* agent, outputStream* st) {
  assert(agent != nullptr, "invariant");
  if (agent->is_xrun()) {
    return invoke_JVM_OnLoad(agent);
  }
  return agent->is_dynamic() ? invoke_Agent_OnAttach(agent, st) : invoke_Agent_OnLoad(agent);
}

extern "C" {
  typedef void (JNICALL* OnUnloadEntry_t)(JavaVM*);
}

class JvmtiInvokeOnUnloadContext {
 public:
  jint operator()(void* entry_point) {
    assert(entry_point != nullptr, "invariant");
    OnUnloadEntry_t target = CAST_TO_FN_PTR(OnUnloadEntry_t, entry_point);
    extern struct JavaVM_ main_vm;
    (*target)(&main_vm);
    return JNI_OK;
  }
};

using JvmtiVoidList = GrowableArrayCHeap<void*, mtServiceability>;

// Invoke each non-empty Agent_OnUnload entry point.
static void invoke(const JvmtiVoidList* entry_points) {
  assert(entry_points != nullptr, "invariant");
  JvmtiInvokeOnUnloadContext invocation_context;
  for (int i = 0; i < entry_points->length(); ++i) {
    if (entry_points->at(i) == nullptr) {
      continue;
    }
    invoke(entry_points->at(i), invocation_context);
  }
}

// Called from before_exit(), only once, by a single thread,
// the thread executing the VM shutdown sequence.
void JvmtiAgentInvoker::invoke_Agent_OnUnload_entry_points(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  const JvmtiVoidList* const entry_points = JvmtiAgentLibraryManager::Agent_OnUnload_entry_points(jt);
  if (entry_points != nullptr) {
    invoke(entry_points);
    delete entry_points;
  }
}
