/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "prims/agentList.hpp"

#include "cds/cds_globals.hpp"
#include "jni.h"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/growableArray.hpp"

Agent* AgentList::_list = nullptr;

static inline Agent* head(Agent** list) {
  assert(list != nullptr, "invariant");
  return Atomic::load_acquire(list);
}

void AgentList::add(Agent* agent) {
  assert(agent != nullptr, "invariant");
  Agent* next;
  do {
    next = head(&_list);
    agent->_next = next;
  } while (Atomic::cmpxchg(&_list, next, agent) != next);
}

void AgentList::add(const char* name, char* options, bool absolute_path) {
  add(new Agent(name, options, absolute_path));
}

void AgentList::add_xrun(const char* name, char* options, bool absolute_path) {
  Agent* agent = new Agent(name, options, absolute_path);
  agent->_is_xrun = true;
  add(agent);
}

Agent* AgentList::Iterator::filter(Agent* agent) const {
  while (agent != nullptr) {
    if (_type == JAVA_OR_NATIVE) {
      if (!agent->_is_xrun) {
        return agent;
      }
    }
    if (_type == JAVA) {
      assert(!agent->_is_xrun, "invariant");
      if (agent->is_jplis()) {
        return agent;
      }
    } else if (_type == NATIVE) {
        if (!agent->is_jplis() && !agent->_is_xrun) {
          return agent;
        }
    } else {
      assert(_type == XRUN, "invariant");
      if (agent->_is_xrun) {
        return agent;
      }
    }
    agent = agent->_next;
  }
  return nullptr;
}

// The storage list is a single cas-linked-list, to allow for concurrent iterations. Especially during initial loading of agents,
// there exist an order requirement to iterate oldest -> newest. Our concurrent storage linked-list is newest -> oldest.
// The correct order is preserved by the iterator, by storing a filtered set of entries in a stack.
AgentList::Iterator::Iterator(Agent** list, Type type) : _stack(new GrowableArrayCHeap<Agent*, mtServiceability>(16)), _type(type) {
  Agent* next = head(list);
  while (next != nullptr) {
    next = filter(next);
    if (next != nullptr) {
      _stack->push(next);
      next = next->_next;
    }
  }
}

AgentList::Iterator::~Iterator() {
  delete _stack;
}

bool AgentList::Iterator::has_next() const {
  assert(_stack != nullptr, "invariant");
  return _stack->is_nonempty();
}

const Agent* AgentList::Iterator::next() const {
  assert(has_next(), "invariant");
  return _stack->pop();
}

Agent* AgentList::Iterator::next() {
  return const_cast<Agent*>(const_cast<const Iterator*>(this)->next());
}

AgentList::Iterator AgentList::agents() {
  return Iterator(&_list, Iterator::JAVA_OR_NATIVE);
}

AgentList::Iterator AgentList::java_agents() {
  return Iterator(&_list, Iterator::JAVA);
}

AgentList::Iterator AgentList::native_agents() {
  return Iterator(&_list, Iterator::NATIVE);
}

AgentList::Iterator AgentList::xrun_agents() {
  return Iterator(&_list, Iterator::XRUN);
}

static inline void timestamp_agents(AgentList::Iterator& it) {
  while (it.has_next()) {
    Agent* agent = it.next();
    if (!agent->is_timestamped()) {
      agent->timestamp();
    }
  }
}

// In case an agent did not enable the VMInit callback, it gets a timestamp here.
void AgentList::timestamp() {
  Iterator xrun_it = xrun_agents();
  timestamp_agents(xrun_it);
  Iterator agent_it = agents();
  timestamp_agents(agent_it);
}

static const char* not_found_error_msg = "Could not find agent library ";
static const char* missing_module_error_msg = "\nModule java.instrument may be missing from runtime image.";
static char ebuf[1024];
static char buffer[JVM_MAXPATHLEN];

static void vm_exit(const Agent* agent, const char* sub_msg1, const char* sub_msg2) {
  assert(agent != nullptr, "invariant");
  assert(sub_msg1 != nullptr, "invariant");
  assert(!agent->is_instrument_lib() || sub_msg2 != nullptr, "invariant");
  const size_t len = strlen(not_found_error_msg) + strlen(agent->name()) + strlen(sub_msg1) + strlen(&ebuf[0]) + 1 + (agent->is_instrument_lib() ? strlen(sub_msg2) : 0);
  char* buf = NEW_C_HEAP_ARRAY(char, len, mtInternal);
  if (agent->is_instrument_lib()) {
    jio_snprintf(buf, len, "%s%s%s%s%s", not_found_error_msg, agent->name(), sub_msg1, &ebuf[0], sub_msg2);
  } else {
    jio_snprintf(buf, len, "%s%s%s%s", not_found_error_msg, agent->name(), sub_msg1, &ebuf[0]);
  }
  vm_exit_during_initialization(buf, nullptr);
  FREE_C_HEAP_ARRAY(char, buf);
}

static void* load_agent_from_executable(Agent* agent, const char* on_load_symbols[], size_t num_symbol_entries) {
  assert(agent != nullptr, "invariant");
  assert(!agent->is_valid(), "invariant");
  assert(on_load_symbols != nullptr, "invariant");
  return os::find_builtin_agent(agent, &on_load_symbols[0], num_symbol_entries) ? agent->os_lib() : nullptr;
}

static void* load_agent_from_absolute_path(Agent* agent, bool vm_exit_on_error) {
  assert(agent != nullptr, "invariant");
  assert(!agent->is_valid(), "invariant");
  assert(agent->is_absolute_path(), "invariant");
  assert(!agent->is_instrument_lib(), "invariant");
  void* const library = os::dll_load(agent->name(), &ebuf[0], sizeof ebuf);
  if (library == nullptr && vm_exit_on_error) {
    vm_exit(agent, " in absolute path, with error: ", nullptr);
  }
  return library;
}

static void* load_agent_from_relative_path(Agent* agent, bool vm_exit_on_error) {
  assert(agent != nullptr, "invariant");
  assert(!agent->is_valid(), "invariant");
  assert(!agent->is_absolute_path(), "invariant");
  const char* const name = agent->name();
  void* library = nullptr;
  // Try to load the agent from the standard dll directory
  if (os::dll_locate_lib(&buffer[0], sizeof buffer, Arguments::get_dll_dir(), name)) {
    library = os::dll_load(&buffer[0], &ebuf[0], sizeof ebuf);
  }
  if (library == nullptr && os::dll_build_name(&buffer[0], sizeof buffer, name)) {
    // Try the library path directory.
    library = os::dll_load(&buffer[0], &ebuf[0], sizeof ebuf);
    if (library != nullptr) {
      return library;
    }
    if (vm_exit_on_error) {
      vm_exit(agent, " on the library path, with error: ", missing_module_error_msg);
    }
  }
  return library;
}

/*
 * The implementation builds a mapping bewteen JVMTI envs and JPLIS agents,
 * using internal JDK implementation knowledge about the way JPLIS agents
 * store data in their JvmtiEnv local storage.
 *
 * Please see JPLISAgent.c in module java.instrument, see JPLISAgent.h and JPLISAgent.c.
 *
 * jvmtierror = (*jvmtienv)->SetEnvironmentLocalStorage( jvmtienv, &(agent->mNormalEnvironment));
 *
 * It is the pointer to the field agent->mNormalEnvironment that is stored in the jvmtiEnv local storage.
 * It has the following type:
 *
 * struct _JPLISEnvironment {
 *   jvmtiEnv*   mJVMTIEnv;              // the JVM TI environment
 *   JPLISAgent* mAgent;                 // corresponding agent
 *   jboolean    mIsRetransformer;       // indicates if special environment
 * };
 *
 * We mirror this struct to get the mAgent field as an identifier.
 */

struct JPLISEnvironmentMirror {
  jvmtiEnv* mJVMTIEnv; // the JVMTI environment
  const void* mAgent;  // corresponding agent
  jboolean mIsRetransformer; // indicates if special environment
};

static inline const JPLISEnvironmentMirror* get_env_local_storage(JvmtiEnv* env) {
  assert(env != nullptr, "invariant");
  return reinterpret_cast<const JPLISEnvironmentMirror*>(env->get_env_local_storage());
}

// The newest jvmtiEnvs are appended to the list, JvmtiEnvIterator order is from oldest to newest.
static JvmtiEnv* get_last_jplis_jvmtienv() {
  JvmtiEnvIterator it;
  JvmtiEnv* env = it.first();
  assert(env != nullptr, "invariant");
  JvmtiEnv* next = it.next(env);
  while (next != nullptr) {
    assert(env != nullptr, "invariant");
    // get_env_local_storage() lets us find which JVMTI env map to which JPLIS agent.
    if (next->get_env_local_storage() == nullptr) {
      JvmtiEnv* temp = it.next(next);
      if (temp != nullptr) {
        next = temp;
        continue;
      }
      break;
    }
    env = next;
    next = it.next(env);
  }
  assert(env != nullptr, "invariant");
  assert(env->get_env_local_storage() != nullptr, "invariant");
  return env;
}

// Link the last, or most recent jvmtiEnv. that is a JPLIS agent, with the current agent.
void AgentList::convert_to_jplis(Agent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_instrument_lib(), "invariant");
  JvmtiEnv* const env = get_last_jplis_jvmtienv();
  assert(env != nullptr, "invariant");
  const JPLISEnvironmentMirror* const jplis_env = get_env_local_storage(env);
  assert(jplis_env != nullptr, "invaiant");
  assert(reinterpret_cast<JvmtiEnv*>(jplis_env->mJVMTIEnv) == env, "invariant");
  agent->set_jplis(jplis_env->mAgent);
}

// CDS dumping does not support native JVMTI agent.
// CDS dumping supports Java agent if the AllowArchivingWithJavaAgent diagnostic option is specified.
static void check_cds_dump(Agent* agent) {
  assert(agent != nullptr, "invariant");
  assert(Arguments::is_dumping_archive(), "invariant");
  if (!agent->is_instrument_lib()) {
    vm_exit_during_cds_dumping("CDS dumping does not support native JVMTI agent, name", agent->name());
  }
  if (!AllowArchivingWithJavaAgent) {
    vm_exit_during_cds_dumping(
      "Must enable AllowArchivingWithJavaAgent in order to run Java agent during CDS dumping");
  }
}

// type for the Agent_OnLoad and JVM_OnLoad entry points
extern "C" {
  typedef jint(JNICALL* OnLoadEntry_t)(JavaVM*, char*, void*);
}

// Find the OnLoad entry point for -agentlib:  -agentpath:   -Xrun agents.
// num_symbol_entries must be passed-in since only the caller knows the number of symbols in the array.
static OnLoadEntry_t lookup_On_Load_entry_point(Agent* agent, const char* on_load_symbols[], size_t num_symbol_entries) {
  assert(agent != nullptr, "invariant");
  if (!agent->is_valid()) {
    // First check to see if agent is statically linked into executable.
    void* library = load_agent_from_executable(agent, on_load_symbols, num_symbol_entries);
    if (library == nullptr) {
      library = agent->is_absolute_path() ? load_agent_from_absolute_path(agent, true) : load_agent_from_relative_path(agent, true);
    }
    assert(library != nullptr, "invariant");
    agent->set_os_lib(library);
    agent->set_valid();
  }
  assert(agent->is_valid(), "invariant");
  assert(agent->os_lib() != nullptr, "invariant");

  // Find the OnLoad function.
  return CAST_TO_FN_PTR(OnLoadEntry_t, os::find_agent_function(agent, false, on_load_symbols, num_symbol_entries));
}

static OnLoadEntry_t lookup_JVM_OnLoad_entry_point(Agent* lib) {
  const char* on_load_symbols[] = JVM_ONLOAD_SYMBOLS;
  return lookup_On_Load_entry_point(lib, on_load_symbols, sizeof(on_load_symbols) / sizeof(char*));
}

static OnLoadEntry_t lookup_Agent_OnLoad_entry_point(Agent* agent) {
  const char* on_load_symbols[] = AGENT_ONLOAD_SYMBOLS;
  return lookup_On_Load_entry_point(agent, on_load_symbols, sizeof(on_load_symbols) / sizeof(char*));
}

// For backwards compatibility with -Xrun, convert Xrun agents with no JVM_OnLoad,
// but which have an Agent_OnLoad, to be treated like -agentpath
void AgentList::convert_xrun_agents() {
  Iterator it = xrun_agents();
  while (it.has_next()) {
    Agent* const agent = it.next();
    assert(agent->_is_xrun, "invariant");
    OnLoadEntry_t on_load_entry = lookup_JVM_OnLoad_entry_point(agent);
    // If there is an JVM_OnLoad function it will get called later,
    // otherwise see if there is an Agent_OnLoad.
    if (on_load_entry == nullptr) {
      on_load_entry = lookup_Agent_OnLoad_entry_point(agent);
      if (on_load_entry == nullptr) {
        vm_exit_during_initialization("Could not find JVM_OnLoad or Agent_OnLoad function in the library", agent->name());
      }
      agent->_is_xrun = false; // converted
    }
  }
}

// Invokes Agent_OnLoad for -agentlib:.. -agentpath:  and converted -Xrun agents.
// Called very early -- before JavaThreads exist
void AgentList::load_agents() {
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invalid init sequence");
  extern struct JavaVM_ main_vm;

  // Convert -Xrun to -agentlib: if there is no JVM_OnLoad
  convert_xrun_agents();

  JvmtiExport::enter_onload_phase();

  Iterator it = agents();
  while (it.has_next()) {
    Agent* agent = it.next();
    if (Arguments::is_dumping_archive()) {
      check_cds_dump(agent);
    }

    OnLoadEntry_t on_load_entry = lookup_Agent_OnLoad_entry_point(agent);

    if (on_load_entry == nullptr) {
      vm_exit_during_initialization("Could not find Agent_OnLoad function in the agent library", agent->name());
    }
    // Invoke the Agent_OnLoad function
    if ((*on_load_entry)(&main_vm, const_cast<char*>(agent->options()), nullptr) != JNI_OK) {
      vm_exit_during_initialization("agent library failed Agent_OnLoad", agent->name());
    }

    // Convert the instrument lib to the actual JPLIS / javaagent it represents.
    if (agent->is_instrument_lib()) {
      convert_to_jplis(agent);
    }
  }

  JvmtiExport::enter_primordial_phase();
}

extern "C" {
  typedef void (JNICALL* Agent_OnUnload_t)(JavaVM*);
}

// Called after the VM is initialized for -Xrun agents which have not been converted to JVMTI agents
void AgentList::invoke_JVM_OnLoad() {
  extern struct JavaVM_ main_vm;
  Iterator it = xrun_agents();
  while (it.has_next()) {
    Agent* agent = it.next();
    assert(agent->_is_xrun, "invariant");
    OnLoadEntry_t on_load_entry = lookup_JVM_OnLoad_entry_point(agent);
    if (on_load_entry == nullptr) {
      vm_exit_during_initialization("Could not find JVM_OnLoad function in -Xrun library", agent->name());
    }
    // Invoke the JVM_OnLoad function
    JavaThread* thread = JavaThread::current();
    ThreadToNativeFromVM ttn(thread);
    HandleMark hm(thread);
    const jint err = (*on_load_entry)(&main_vm, const_cast<char*>(agent->options()), NULL);
    if (err != JNI_OK) {
      vm_exit_during_initialization("-Xrun library failed to init", agent->name());
    }
  }
}

// Launch -Xrun agents eagerly at startup.
void AgentList::load_xrun_agents_at_startup() {
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invalid init sequence");
  assert(EagerXrunInit, "invariant");
  invoke_JVM_OnLoad();
}

// Launch -Xrun agents
void AgentList::load_xrun_agents() {
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invalid init sequence");
  assert(!EagerXrunInit, "invariant");
  invoke_JVM_OnLoad();
}

// Use this for JavaThreads and state is  _thread_in_vm.
class AgentJavaThreadEventTransition : StackObj {
 private:
  ResourceMark _rm;
  ThreadToNativeFromVM _transition;
  HandleMark _hm;
 public:
  AgentJavaThreadEventTransition(JavaThread* thread) : _rm(), _transition(thread), _hm(thread) {};
};

class AgentEventMark : public StackObj {
 private:
  JavaThread* _thread;
  JNIEnv* _jni_env;
  JvmtiThreadState::ExceptionState _saved_exception_state;

 public:
  AgentEventMark(JavaThread* thread) : _thread(thread),
    _jni_env(thread->jni_environment()),
    _saved_exception_state(JvmtiThreadState::ES_CLEARED) {
    JvmtiThreadState* state = thread->jvmti_thread_state();
    // we are before an event.
    // Save current jvmti thread exception state.
    if (state != NULL) {
      _saved_exception_state = state->get_exception_state();
    }
    thread->push_jni_handle_block();
    assert(thread == JavaThread::current(), "thread must be current!");
    thread->frame_anchor()->make_walkable();
  }

  ~AgentEventMark() {
    _thread->pop_jni_handle_block();
    JvmtiThreadState* state = _thread->jvmti_thread_state();
    // we are continuing after an event.
    if (state != NULL) {
      // Restore the jvmti thread exception state.
      state->restore_exception_state(_saved_exception_state);
    }
  }
};

class AgentThreadEventMark : public AgentEventMark {
 private:
  jobject _jthread;
 public:
  AgentThreadEventMark(JavaThread* thread) : AgentEventMark(thread) {
    _jthread = JNIHandles::make_local(thread, thread->threadObj());
  };
  jthread jni_thread() { return (jthread)_jthread; }
};

static void unload_and_delete(Agent* agent, void* library) {
  assert(agent != nullptr, "invariant");
  if (!agent->is_static_lib()) {
    os::dll_unload(library);
  }
  delete agent;
}

// type for the Agent_OnAttach entry point
extern "C" {
  typedef jint(JNICALL* OnAttachEntry_t)(JavaVM*, char*, void*);
}

// Implementation for loading an agent dynamically during runtime, by invoking Agent_OnAttach.
jint AgentList::load_agent(const char* agent_name, const char* absParam,
                                  const char* options, outputStream* st) {
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_LIVE, "not in live phase!");
  const char* on_attach_symbols[] = AGENT_ONATTACH_SYMBOLS;
  const size_t num_symbol_entries = ARRAY_SIZE(on_attach_symbols);

  // The abs parameter should be "true" or "false"
  const bool is_absolute_path = (absParam != nullptr) && (strcmp(absParam, "true") == 0);
  // Initially marked as invalid. It will be set to valid if we can find the agent
  Agent* agent = new Agent(agent_name, options, is_absolute_path);
  agent->_is_dynamic = true;

  // Check for statically linked in agent. If not found then if the path is
  // absolute we attempt to load the library. Otherwise we try to load it from the standard dll directory.
  void* library = load_agent_from_executable(agent, &on_attach_symbols[0], num_symbol_entries);
  if (library == nullptr) {
    library = agent->is_absolute_path() ? load_agent_from_absolute_path(agent, /* vm exit on error */ false) :
                                          load_agent_from_relative_path(agent, /* vm exit on error */ false);
  }
  if (library != nullptr) {
    agent->set_os_lib_path(&buffer[0]);
    agent->set_os_lib(library);
    agent->set_valid();
  } else {
    st->print_cr("%s was not loaded.", agent_name);
    if (*ebuf != '\0') {
      st->print_cr("%s", ebuf);
    }
    return JNI_ERR;
  }

  assert(library != nullptr, "invariant");
  assert(agent->is_valid(), "invariant");

  // The library was loaded so we attempt to lookup and invoke the Agent_OnAttach function
  OnAttachEntry_t on_attach_entry = CAST_TO_FN_PTR(OnAttachEntry_t,
                                                   os::find_agent_function(agent, false, &on_attach_symbols[0], num_symbol_entries));

  if (on_attach_entry == nullptr) {
    // Agent_OnAttach missing - unload library
    unload_and_delete(agent, library);
    st->print_cr("%s is not available in %s", on_attach_symbols[0], agent->name());
    return JNI_ERR;
  }

  // Invoke the Agent_OnAttach function
  JavaThread* THREAD = JavaThread::current(); // For exception macros.
  jint result = JNI_ERR;
  {
    extern struct JavaVM_ main_vm;
    AgentThreadEventMark jem(THREAD);
    AgentJavaThreadEventTransition jet(THREAD);

    agent->initialization_begin();

    result = (*on_attach_entry)(&main_vm, (char*)options, NULL);

    agent->initialization_end();

    // Agent_OnAttach may have used JNI
    if (THREAD->is_pending_jni_exception_check()) {
      THREAD->clear_pending_jni_exception_check();
    }
  }

  // Agent_OnAttach may have used JNI
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
  }

  if (result == JNI_OK) {
    if (agent->is_instrument_lib()) {
      // Convert the instrument lib to the actual JPLIS / javaagent it represents.
      convert_to_jplis(agent);
    }
    // If OnAttach returns JNI_OK then we add it to the list of
    // agents so that we can iterate over it and call Agent_OnUnload later.
    add(agent);
  } else {
    unload_and_delete(agent, library);
  }
  st->print_cr("return code: %d", result);
  // Agent_OnAttach executed so completion status is JNI_OK
  return JNI_OK;
}

// Send any Agent_OnUnload notifications
void AgentList::unload_agents() {
  extern struct JavaVM_ main_vm;
  const char* on_unload_symbols[] = AGENT_ONUNLOAD_SYMBOLS;
  size_t num_symbol_entries = ARRAY_SIZE(on_unload_symbols);
  Iterator it = agents();
  while (it.has_next()) {
    Agent* agent = it.next();
    // Find the Agent_OnUnload function.
    Agent_OnUnload_t unload_entry = CAST_TO_FN_PTR(Agent_OnUnload_t,
                                                   os::find_agent_function(agent,
                                                   false,
                                                   &on_unload_symbols[0],
                                                   num_symbol_entries));
    // Invoke the Agent_OnUnload function
    if (unload_entry != nullptr) {
      JavaThread* thread = JavaThread::current();
      ThreadToNativeFromVM ttn(thread);
      HandleMark hm(thread);
      (*unload_entry)(&main_vm);
    }
  }
}

static bool is_env_jplis_agent(JvmtiEnv* env, const Agent* agent) {
  assert(env != nullptr, "invariant");
  assert(agent != nullptr, "invariant");
  assert(agent->is_instrument_lib(), "invariant");
  const JPLISEnvironmentMirror* const jplis_env = get_env_local_storage(env);
  return jplis_env != nullptr ? agent->is_jplis(jplis_env->mAgent) : false;
}

static bool match(JvmtiEnv* env, const Agent* agent, const void* os_module_address) {
  assert(env != nullptr, "invariant");
  assert(agent != nullptr, "invariant");
  if (agent->os_lib() != os_module_address) {
    return false;
  }
  if (agent->is_instrument_lib()) {
    return is_env_jplis_agent(env, agent);
  }
  // The agent maps to the correct os library.
  // But if this is another JvmtiEnv for the same agent, we can't time it twice.
  return !agent->is_timestamped();
}

// The function pointer is a JVMTI callback function.
// Find the os module (dll) that exports this function.
// Now we can map a JVMTI env to its corresponding agent.
// Some agents create multiple JVMTI envs, but we only maintain a single 1-1 mapping to an agent where we can.
Agent* AgentList::lookup(JvmtiEnv* env, void* f_ptr) {
  assert(env != nullptr, "invariant");
  assert(f_ptr != nullptr, "invariant");
  int offset;
  if (!os::dll_address_to_library_name(reinterpret_cast<address>(f_ptr), &buffer[0], JVM_MAXPATHLEN, &offset)) {
    return nullptr;
  }
  assert(buffer[0] != '\0', "invariant");
  assert(offset >= 0, "invariant");
  const void* const os_module_address = reinterpret_cast<address>(f_ptr) - offset;

  AgentList::Iterator it = AgentList::agents();
  while (it.has_next()) {
    Agent* const agent = it.next();
    if (match(env, agent, os_module_address)) {
      agent->set_os_lib_path(&buffer[0]);
      return agent;
    }
  }
  return nullptr;
}
