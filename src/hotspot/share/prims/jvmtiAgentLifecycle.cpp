/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/cdsConfig.hpp"
#include "jni.h"
#include "jvm.h"
#include "logging/log.hpp"
#include "prims/jvmtiAgent.hpp"
#include "prims/jvmtiAgentLifecycle.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiResolver.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threads.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ostream.hpp"

static inline char* allocate() {
  return NEW_C_HEAP_ARRAY(char, JVM_MAXPATHLEN, mtServiceability);
}

static inline void deallocate(char* buffer) {
  FREE_C_HEAP_ARRAY(buffer);
}

class JvmtiLifecycleBufferRAII : StackObj {
 private:
  char* _buffer;
  NONCOPYABLE(JvmtiLifecycleBufferRAII);
 public:
  JvmtiLifecycleBufferRAII() : _buffer(allocate()) {}
  JvmtiLifecycleBufferRAII(char* buffer) : _buffer(buffer) {}
  ~JvmtiLifecycleBufferRAII() { deallocate(_buffer); }
  char* buffer() { return _buffer; }
  const char* buffer() const { return _buffer; }
};

bool JvmtiAgentLifecycle::is_static(const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->name() != nullptr, "invariant");
  const char* const startup_symbol = agent->is_dynamic() ? "Agent_OnAttach" : agent->is_xrun() ? "JVM_OnLoad" : "Agent_OnLoad";
  assert(startup_symbol != nullptr, "invariant");
  bool result = JvmtiResolver::has_static_entry_point(agent->name(), agent->is_absolute_path(), startup_symbol);
  if (!result && agent->is_xrun()) {
    result = JvmtiResolver::has_static_entry_point(agent->name(), agent->is_absolute_path(), "Agent_OnLoad");
  }
  return result;
}

static bool resolve_to_platform_specific_absolute_path_in_std_dll_dir(const char* os_lib_name, char* buffer) {
  assert(os_lib_name != nullptr, "invariant");
  assert(buffer != nullptr, "invariant");
  return os::dll_locate_lib(buffer, JVM_MAXPATHLEN, Arguments::get_dll_dir(), os_lib_name);
}

static bool build_platform_specific_library_name(const char* os_lib_name, char* buffer) {
  assert(os_lib_name != nullptr, "invariant");
  assert(buffer != nullptr, "invariant");
  return os::dll_build_name(buffer, JVM_MAXPATHLEN, os_lib_name);
}

static char* prepare_library_load_name(const JvmtiAgent* agent, bool* in_std_dll_dir = nullptr) {
  assert(agent != nullptr, "invariant");
  assert(!agent->is_static(), "invariant");
  if (in_std_dll_dir != nullptr) {
    *in_std_dll_dir = false;
  }
  const char* const name = agent->name();
  char* const buffer = allocate();
  if (agent->is_absolute_path()) {
    const size_t length = strlen(name);
    if (length >= JVM_MAXPATHLEN) {
      log_error(jvmti)("Agent library path is too long: %zu bytes (maximum %zu bytes)",
                       length, static_cast<size_t>(JVM_MAXPATHLEN - 1));
      deallocate(buffer);
      return nullptr;
    }
    memcpy(buffer, name, length + 1);
    return buffer;
  }
  if (resolve_to_platform_specific_absolute_path_in_std_dll_dir(name, buffer)) {
    if (in_std_dll_dir != nullptr) {
      *in_std_dll_dir = true;
    }
    return buffer;
  }
  if (build_platform_specific_library_name(name, buffer)) {
    return buffer;
  }
  deallocate(buffer);
  return nullptr;
}

static const char* not_found_error_msg = "Could not find agent library ";
static const char* library_path_error_msg = "Unable to resolve loaded agent library path";
static const char* missing_module_error_msg = "\nModule java.instrument may be missing from runtime image.";

static void exit_on_load_error(const JvmtiAgent* agent, const char* detail, const char* error = "") {
  const char* const message = agent->is_instrument_lib() ? missing_module_error_msg : "";
  const size_t len = strlen(not_found_error_msg) + strlen(agent->name()) + strlen(detail) +
    strlen(error) + strlen(message) + 1;
  char* const buf = NEW_C_HEAP_ARRAY(char, len, mtServiceability);
  jio_snprintf(buf, len, "%s%s%s%s%s", not_found_error_msg, agent->name(), detail, error, message);
  vm_exit_during_initialization(buf, nullptr);
}

static void on_load_error(const JvmtiAgent* agent, bool vm_exit_on_error,
                          const char* error, outputStream* st = nullptr) {
  if (vm_exit_on_error) {
    const char* const detail = agent->is_absolute_path() ?
      " in absolute path, with error: " : " on the library path, with error: ";
    exit_on_load_error(agent, detail, error);
  }
  st->print_cr("%s was not loaded.", agent->name());
  if (*error != '\0') {
    st->print_cr("%s", error);
  }
}

static const void* load_library(const JvmtiAgent* agent, bool vm_exit_on_error, outputStream* st) {
  assert(agent != nullptr, "invariant");
  assert(vm_exit_on_error || st != nullptr, "invariant");
  if (agent->is_static()) {
    return os::get_default_process_handle();
  }
  bool in_std_dll_dir = false;
  JvmtiLifecycleBufferRAII path(prepare_library_load_name(agent, &in_std_dll_dir));
  if (path.buffer() == nullptr) {
    if (vm_exit_on_error) {
      exit_on_load_error(agent, ", unable to resolve library path");
    }
    st->print_cr("Unable to resolve agent library path: path is invalid or too long.");
    return nullptr;
  }
  char ebuf[1024] = {0};
  const void* handle = os::dll_load(path.buffer(), &ebuf[0], sizeof ebuf);
  if (handle == nullptr && in_std_dll_dir) {
    // On failure loading a file from the JDK directory,
    // retain the normal library-search fallback for relative names.
    if (build_platform_specific_library_name(agent->name(), path.buffer())) {
      handle = os::dll_load(path.buffer(), &ebuf[0], sizeof ebuf);
    }
  }
  if (handle == nullptr) {
    on_load_error(agent, vm_exit_on_error, &ebuf[0], st);
    return nullptr;
  }
  return handle;
}

static bool load(JvmtiAgent* agent, bool vm_exit_on_error,
                 outputStream* st = nullptr, bool* initial_load = nullptr) {
  assert(agent != nullptr, "invariant");
  assert(vm_exit_on_error || st != nullptr, "invariant");
  if (agent->resolver() != nullptr) {
    if (initial_load != nullptr) {
      *initial_load = false;
    }
    return true;
  }
  const void* const handle = load_library(agent, vm_exit_on_error, st);
  if (handle == nullptr) {
    return false;
  }
  agent->set_resolver(JvmtiResolver::intern(handle,
                                            agent->name(),
                                            agent->is_absolute_path(),
                                            agent->is_static(),
                                            initial_load));
  return true;
}

static inline void* resolve_entry_point(const JvmtiAgent* agent, const char* entry_point_name) {
  assert(agent != nullptr, "invariant");
  return agent->resolver()->resolve_entry_point(entry_point_name);
}

static bool set_agent_path(JvmtiAgent* agent, void* entry_point) {
  assert(entry_point != nullptr, "invariant");
  address pc = static_cast<address>(entry_point);
#ifdef HAVE_FUNCTION_DESCRIPTORS
  pc = static_cast<address>(os::resolve_function_descriptor(pc));
#endif
  JvmtiLifecycleBufferRAII path;
  if (pc == nullptr || !os::dll_address_to_library_name(pc, path.buffer(), JVM_MAXPATHLEN, nullptr) || path.buffer()[0] == '\0') {
    log_error(jvmti)("%s", library_path_error_msg);
    return false;
  }
  agent->set_path(path.buffer());
  return true;
}

#ifdef ASSERT
static inline bool is_vm_bootstrap() {
  return Threads::number_of_threads() == 0;
}

static void assert_attempt_xrun_conversion(const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_xrun(), "invariant");
  assert(agent->resolver() == nullptr, "invariant");
  assert(!agent->is_associated(), "invariant");
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invariant");
}
#endif

static void exit_on_startup_error(const JvmtiAgent* agent, const char* message) {
  vm_exit_during_initialization(message, agent->name());
}

void JvmtiAgentLifecycle::attempt_xrun_conversion(JvmtiAgent* agent) {
  DEBUG_ONLY(assert_attempt_xrun_conversion(agent);)

  // 1. Ensure the OS library is loaded.
  load(agent, true);

  // 2. Lookup Xrun entry point.
  const void* entry_point = resolve_entry_point(agent, "JVM_OnLoad");
  if (entry_point == nullptr) {
    // 3. Lookup JVMTI entry point.
    entry_point = resolve_entry_point(agent, "Agent_OnLoad");
    if (entry_point == nullptr) {
      exit_on_startup_error(agent, "Unable to convert -Xrun agent because no Agent_OnLoad entry point in library");
    }
    // 4. An Agent_OnLoad entry point was found, so the agent
    //    is reclassified, from an xrun agent to a normal agent.
    agent->convert_from_xrun();
  }
  assert(entry_point != nullptr, "invariant");
}

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
    agent->initialization_begin();
  }
  StartupEntry_t target = CAST_TO_FN_PTR(StartupEntry_t, entry_point);
  extern struct JavaVM_ main_vm;
  const jint result = (*target)(&main_vm, const_cast<char*>(agent->options()), nullptr);
  if (agent->is_dynamic()) {
    agent->initialization_end();
  }
  agent->end_startup();
  return result;
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
  assert(agent->resolver() != nullptr, "invariant");
  if (!FLAG_IS_CMDLINE(EnableDynamicAgentLoading) && !agent->is_instrument_lib()) {
    jio_fprintf(defaultStream::error_stream(),
      "WARNING: A JVM TI agent has been loaded dynamically (%s)\n"
      "WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\n"
      "WARNING: Dynamic loading of agents will be disallowed by default in a future release\n", agent->name());
  }
}

#ifdef ASSERT
static void assert_loaded(const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->path() == nullptr, "invariant");
  assert(agent->resolver() != nullptr, "invariant");
  assert(!agent->is_associated(), "invariant");
}
#endif

bool JvmtiAgentLifecycle::locate_and_invoke_Agent_OnAttach(JvmtiAgent* agent, outputStream* st, bool initial_load) {
  DEBUG_ONLY(assert_loaded(agent);)
  assert(st != nullptr, "invariant");

  // 1. Issue a warning message on initial load.
  if (initial_load) {
    print_dynamic_startup_warning(agent);
  }

  // 2. Locate the Agent_OnAttach entry point.
  void* const entry_point = resolve_entry_point(agent, "Agent_OnAttach");
  if (entry_point == nullptr) {
    st->print_cr("%s is not available in %s", "Agent_OnAttach", agent->name());
    return false;
  }

  // 3. Initialize the descriptive path before entering agent code.
  if (!set_agent_path(agent, entry_point)) {
    st->print_cr("%s", library_path_error_msg);
    return false;
  }

  // 4. Invoke the Agent_OnAttach entry point.
  JvmtiInvokeOnAttachContext invocation_context(entry_point);
  const jint result = invoke(agent, invocation_context);
  register_for_unload(agent);

  // 5. Process return value.
  st->print_cr("return code: %d", result);
  if (result != JNI_OK) {
    // Do not unload the OS library after a failed Agent_OnAttach invocation.
    // The library is third-party code and may already have started native
    // threads that can execute code in it. Its lifetime is therefore the VM.
    return false;
  }

  return true;
}

#ifdef ASSERT
static void assert_agent_on_attach(const JvmtiAgent* agent, const outputStream* st) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_dynamic(), "invariant");
  assert(agent->resolver() == nullptr, "invariant");
  assert(agent->path() == nullptr, "invariant");
  assert(!agent->is_associated(), "invariant");
  assert(!agent->is_xrun(), "invariant");
  assert(st != nullptr, "invariant");
  assert(EnableDynamicAgentLoading, "invariant");
}
#endif

bool JvmtiAgentLifecycle::invoke_Agent_OnAttach(JvmtiAgent* agent, outputStream* st) {
  DEBUG_ONLY(assert_agent_on_attach(agent, st);)

  // 1. Ensure the OS library is loaded.
  bool initial_load = false;
  if (!load(agent, /* vm_exit_on_error */ false, st, &initial_load)) {
    return false;
  }

  // 2. Find and invoke the OnAttach entry point in the OS library.
  return locate_and_invoke_Agent_OnAttach(agent, st, initial_load);
}

bool JvmtiAgentLifecycle::locate_and_invoke_Agent_OnLoad(JvmtiAgent* agent) {
  DEBUG_ONLY(assert_loaded(agent);)

  // 1. Locate the Agent_OnLoad entry point.
  void* const entry_point = resolve_entry_point(agent, "Agent_OnLoad");
  if (entry_point == nullptr) {
    exit_on_startup_error(agent,
      "Unable to start agent because no Agent_OnLoad entry point in library");
  }

  // 2. Initialize the descriptive path before invoking the entry point.
  if (!set_agent_path(agent, entry_point)) {
    vm_exit_during_initialization(library_path_error_msg);
  }

  // 3. Invoke the Agent_OnLoad entry point.
  const jint result = invoke_startup(agent, entry_point);
  register_for_unload(agent);
  if (result != JNI_OK) {
    exit_on_startup_error(agent, "agent library failed Agent_OnLoad");
  }

  return true;
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
static void assert_on_load(const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->path() == nullptr, "invariant");
  assert(!agent->is_associated(), "invariant");
  assert(!agent->is_dynamic(), "invariant");
}

static void assert_agent_on_load(const JvmtiAgent* agent) {
  assert_on_load(agent);
  assert(!agent->is_xrun(), "invariant");
  assert(is_vm_bootstrap(), "invariant");
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_ONLOAD, "invariant");
}

static void assert_jvm_on_load(const JvmtiAgent* agent) {
  assert_on_load(agent);
  assert(agent->resolver() != nullptr, "invariant");
  assert(agent->is_xrun(), "invariant");
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invariant");
}
#endif

bool JvmtiAgentLifecycle::invoke_Agent_OnLoad(JvmtiAgent* agent) {
  DEBUG_ONLY(assert_agent_on_load(agent);)
  CDS_ONLY(check_cds_dump();)

  // 1. Ensure the OS library is loaded.
  load(agent, true);

  // 2. Find and invoke the Agent_OnLoad entry point in the OS library.
  return locate_and_invoke_Agent_OnLoad(agent);
}

bool JvmtiAgentLifecycle::invoke_JVM_OnLoad(JvmtiAgent* agent) {
  DEBUG_ONLY(assert_jvm_on_load(agent);)

  // 1. The library must already have been loaded because of the failed
  //    xrun conversion attempt during startup.

  // 2. The JVM_OnLoad entry point must exist because of the failed xrun
  //    conversion attempt during startup.
  void* const entry_point = resolve_entry_point(agent, "JVM_OnLoad");
  assert(entry_point != nullptr, "invariant");

  // 3. Initialize the descriptive path before invoking the entry point..
  if (!set_agent_path(agent, entry_point)) {
    vm_exit_during_initialization(library_path_error_msg);
  }

  // 4. Invoke the JVM_OnLoad entry point.
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

bool JvmtiAgentLifecycle::startup(JvmtiAgent* agent, outputStream* st) {
  assert(agent != nullptr, "invariant");
  if (agent->is_xrun()) {
    return invoke_JVM_OnLoad(agent);
  }
  return agent->is_dynamic() ? invoke_Agent_OnAttach(agent, st) : invoke_Agent_OnLoad(agent);
}

static inline JvmtiResolver* get_resolver(const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->has_startup_returned(), "invariant");
  JvmtiResolver* const resolver = agent->resolver();
  assert(resolver != nullptr, "invariant");
  return resolver;
}

// The resolver associated with this agent is registered for use by NativeLookup.
void JvmtiAgentLifecycle::register_for_native_resolution(const JvmtiAgent* agent) {
  get_resolver(agent)->register_for_native_resolution();
}

// The resolver associated with this agent is registered for Agent_OnUnload on VM shutdown.
void JvmtiAgentLifecycle::register_for_unload(const JvmtiAgent* agent) {
  get_resolver(agent)->register_for_unload();
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
void JvmtiAgentLifecycle::invoke_Agent_OnUnload_entry_points(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  const JvmtiVoidList* const entry_points = JvmtiResolver::Agent_OnUnload_entry_points(jt);
  if (entry_points != nullptr) {
    invoke(entry_points);
    delete entry_points;
  }
}
