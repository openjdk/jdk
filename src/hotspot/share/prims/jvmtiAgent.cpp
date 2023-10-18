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
#include "prims/jvmtiAgent.hpp"

#include "cds/cds_globals.hpp"
#include "jni.h"
#include "jvm_io.h"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiAgentList.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/defaultStream.hpp"

static inline const char* copy_string(const char* str) {
  return str != nullptr ? os::strdup(str, mtServiceability) : nullptr;
}

// Returns the lhs before '=', parsed_options output param gets the rhs.
static const char* split_options_and_allocate_copy(const char* options, const char** parsed_options) {
  assert(options != nullptr, "invariant");
  assert(parsed_options != nullptr, "invariant");
  const char* const equal_sign = strchr(options, '=');
  const size_t length = strlen(options);
  size_t name_length = length;
  if (equal_sign != nullptr) {
    name_length = equal_sign - options;
    const size_t options_length = length - name_length - 1;
    *parsed_options = copy_string(equal_sign + 1);
  } else {
    *parsed_options = nullptr;
    name_length = length;
  }
  char* const name = AllocateHeap(name_length + 1, mtServiceability);
  jio_snprintf(name, name_length + 1, "%s", options);
  assert(strncmp(name, options, name_length) == 0, "invariant");
  return name;
}

JvmtiAgent::JvmtiAgent(const char* name, const char* options, bool is_absolute_path, bool dynamic /* false */) :
  _initialization_time(),
  _initialization_duration(),
  _next(nullptr),
  _name(copy_string(name)),
  _options(copy_string(options)),
  _os_lib(nullptr),
  _os_lib_path(nullptr),
#ifdef AIX
  _inode(0),
  _device(0),
#endif
  _jplis(nullptr),
  _loaded(false),
  _absolute_path(is_absolute_path),
  _static_lib(false),
  _instrument_lib(strcmp(name, "instrument") == 0),
  _dynamic(dynamic),
  _xrun(false) {}

JvmtiAgent* JvmtiAgent::next() const {
  return _next;
}

void JvmtiAgent::set_next(JvmtiAgent* agent) {
  _next = agent;
}

const char* JvmtiAgent::name() const {
  return _name;
}

const char* JvmtiAgent::options() const {
  return _options;
}

void* JvmtiAgent::os_lib() const {
  return _os_lib;
}

void JvmtiAgent::set_os_lib(void* os_lib) {
  _os_lib = os_lib;
}

void JvmtiAgent::set_os_lib_path(const char* path) {
  assert(path != nullptr, "invariant");
  if (_os_lib_path == nullptr) {
    _os_lib_path = copy_string(path);
  }
  assert(strcmp(_os_lib_path, path) == 0, "invariant");
}

const char* JvmtiAgent::os_lib_path() const {
  return _os_lib_path;
}

#ifdef AIX
void JvmtiAgent::set_inode(ino64_t inode) {
  _inode = inode;
}

void JvmtiAgent::set_device(dev64_t device) {
  _device = device;
}

ino64_t JvmtiAgent::inode() const {
  return _inode;
}

dev64_t JvmtiAgent::device() const {
  return _device;
}
#endif

bool JvmtiAgent::is_loaded() const {
  return _loaded;
}

void JvmtiAgent::set_loaded() {
  _loaded = true;
}

bool JvmtiAgent::is_absolute_path() const {
  return _absolute_path;
}

bool JvmtiAgent::is_static_lib() const {
  return _static_lib;
}

void JvmtiAgent::set_static_lib() {
  _static_lib = true;
}

bool JvmtiAgent::is_dynamic() const {
  return _dynamic;
}

bool JvmtiAgent:: is_instrument_lib() const {
  return _instrument_lib;
}

bool JvmtiAgent::is_xrun() const {
  return _xrun;
}

void JvmtiAgent::set_xrun() {
  _xrun = true;
}

bool JvmtiAgent::is_jplis() const {
  return _jplis != nullptr;
}

const Ticks& JvmtiAgent::initialization_time() const {
  return _initialization_time;
}

const Tickspan& JvmtiAgent::initialization_duration() const {
  return _initialization_duration;
}

bool JvmtiAgent::is_initialized() const {
  return _initialization_time.value() != 0;
}

void JvmtiAgent::initialization_begin() {
  assert(!is_initialized(), "invariant");
  _initialization_time = Ticks::now();
}

void JvmtiAgent::initialization_end() {
  assert(is_initialized(), "invariant");
  assert(_initialization_duration.value() == 0, "invariant");
  _initialization_duration = Ticks::now() - initialization_time();
}

/*
 * The implementation builds a mapping bewteen JvmtiEnvs and JPLIS agents,
 * using internal JDK implementation knowledge about the way JPLIS agents
 * store data in their JvmtiEnv local storage.
 *
 * Please see JPLISAgent.h and JPLISAgent.c in module java.instrument.
 *
 * jvmtierror = (*jvmtienv)->SetEnvironmentLocalStorage( jvmtienv, &(agent->mNormalEnvironment));
 *
 * It is the pointer to the field agent->mNormalEnvironment that is stored in the jvmtiEnv local storage.
 * It has the following type:
 *
 * struct _JPLISEnvironment {
 *   jvmtiEnv*   mJVMTIEnv;         // the JVMTI environment
 *   JPLISAgent* mAgent;            // corresponding agent
 *   jboolean    mIsRetransformer;  // indicates if special environment
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

bool JvmtiAgent::is_jplis(JvmtiEnv* env) const {
  assert(env != nullptr, "invariant");
  assert(is_instrument_lib(), "invariant");
  const JPLISEnvironmentMirror* const jplis_env = get_env_local_storage(env);
  return jplis_env != nullptr && _jplis == jplis_env->mAgent;
}

void JvmtiAgent::set_jplis(const void* jplis) {
  assert(jplis != nullptr, "invaiant");
  assert(is_instrument_lib(), "invariant");
  assert(_jplis == nullptr, "invariant");
  if (_options != nullptr) {
    // For JPLIS agents, update with the java name and options.
    os::free(const_cast<char*>(_name));
    const char* options = _options;
    _name = split_options_and_allocate_copy(options, &_options);
    os::free(const_cast<char*>(options));
  }
  _jplis = jplis;
}

static const char* not_found_error_msg = "Could not find agent library ";
static const char* missing_module_error_msg = "\nModule java.instrument may be missing from runtime image.";
static char ebuf[1024];
static char buffer[JVM_MAXPATHLEN];

static void vm_exit(const JvmtiAgent* agent, const char* sub_msg1, const char* sub_msg2) {
  assert(agent != nullptr, "invariant");
  assert(sub_msg1 != nullptr, "invariant");
  assert(!agent->is_instrument_lib() || sub_msg2 != nullptr, "invariant");
  const size_t len = strlen(not_found_error_msg) + strlen(agent->name()) + strlen(sub_msg1) + strlen(&ebuf[0]) + 1 + (agent->is_instrument_lib() ? strlen(sub_msg2) : 0);
  char* buf = NEW_C_HEAP_ARRAY(char, len, mtServiceability);
  if (agent->is_instrument_lib()) {
    jio_snprintf(buf, len, "%s%s%s%s%s", not_found_error_msg, agent->name(), sub_msg1, &ebuf[0], sub_msg2);
  } else {
    jio_snprintf(buf, len, "%s%s%s%s", not_found_error_msg, agent->name(), sub_msg1, &ebuf[0]);
  }
  vm_exit_during_initialization(buf, nullptr);
  FREE_C_HEAP_ARRAY(char, buf);
}

#ifdef ASSERT
static void assert_preload(const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(!agent->is_loaded(), "invariant");
}
#endif

// Check for a statically linked-in agent, i.e. in the executable.
// This should be the first function called when loading an agent. It is a bit special:
// For statically linked agents we can't rely on os_lib == nullptr because
// statically linked agents could have a handle of RTLD_DEFAULT which == 0 on some platforms.
// If this function returns true, then agent->is_static_lib() && agent->is_loaded().
static bool load_agent_from_executable(JvmtiAgent* agent, const char* on_load_symbols[], size_t num_symbol_entries) {
  DEBUG_ONLY(assert_preload(agent);)
  assert(on_load_symbols != nullptr, "invariant");
  return os::find_builtin_agent(agent, &on_load_symbols[0], num_symbol_entries);
}

#ifdef AIX
// save the inode and device of the library's file as a signature. This signature can be used
// in the same way as the library handle as a signature on other platforms.
static void save_library_signature(JvmtiAgent* agent, const char* name) {
  struct stat64x libstat;
  if (0 == os::Aix::stat64x_via_LIBPATH(name, &libstat)) {
    agent->set_inode(libstat.st_ino);
    agent->set_device(libstat.st_dev);
  } else {
    assert(false, "stat64x failed");
  }
}
#endif

// Load the library from the absolute path of the agent, if available.
static void* load_agent_from_absolute_path(JvmtiAgent* agent, bool vm_exit_on_error) {
  DEBUG_ONLY(assert_preload(agent);)
  assert(agent->is_absolute_path(), "invariant");
  assert(!agent->is_instrument_lib(), "invariant");
  void* const library = os::dll_load(agent->name(), &ebuf[0], sizeof ebuf);
  if (library == nullptr && vm_exit_on_error) {
    vm_exit(agent, " in absolute path, with error: ", nullptr);
  }
  AIX_ONLY(if (library != nullptr) save_library_signature(agent, agent->name());)
  return library;
}

// Agents with relative paths are loaded from the standard dll directory.
static void* load_agent_from_relative_path(JvmtiAgent* agent, bool vm_exit_on_error) {
  DEBUG_ONLY(assert_preload(agent);)
  assert(!agent->is_absolute_path(), "invariant");
  const char* const name = agent->name();
  void* library = nullptr;
  // Try to load the agent from the standard dll directory
  if (os::dll_locate_lib(&buffer[0], sizeof buffer, Arguments::get_dll_dir(), name)) {
    library = os::dll_load(&buffer[0], &ebuf[0], sizeof ebuf);
    AIX_ONLY(if (library != nullptr) save_library_signature(agent, &buffer[0]);)
  }
  if (library == nullptr && os::dll_build_name(&buffer[0], sizeof buffer, name)) {
    // Try the library path directory.
    library = os::dll_load(&buffer[0], &ebuf[0], sizeof ebuf);
    if (library != nullptr) {
      AIX_ONLY(save_library_signature(agent, &buffer[0]);)
      return library;
    }
    if (vm_exit_on_error) {
      vm_exit(agent, " on the library path, with error: ", missing_module_error_msg);
    }
  }
  return library;
}

// For absolute and relative paths.
static void* load_library(JvmtiAgent* agent, const char* on_symbols[], size_t num_symbol_entries, bool vm_exit_on_error) {
  return agent->is_absolute_path() ? load_agent_from_absolute_path(agent, vm_exit_on_error) :
                                     load_agent_from_relative_path(agent, vm_exit_on_error);
}

// Type for the Agent_OnLoad and JVM_OnLoad entry points.
extern "C" {
  typedef jint(JNICALL* OnLoadEntry_t)(JavaVM*, char*, void*);
}

// Find the OnLoad entry point for -agentlib:  -agentpath:   -Xrun agents.
// num_symbol_entries must be passed-in since only the caller knows the number of symbols in the array.
static OnLoadEntry_t lookup_On_Load_entry_point(JvmtiAgent* agent, const char* on_load_symbols[], size_t num_symbol_entries) {
  assert(agent != nullptr, "invariant");
  if (!agent->is_loaded()) {
    if (!load_agent_from_executable(agent, on_load_symbols, num_symbol_entries)) {
      void* const library = load_library(agent, on_load_symbols, num_symbol_entries, /* vm exit on error */ true);
      assert(library != nullptr, "invariant");
      agent->set_os_lib(library);
      agent->set_loaded();
    }
  }
  assert(agent->is_loaded(), "invariant");
  // Find the OnLoad function.
  return CAST_TO_FN_PTR(OnLoadEntry_t, os::find_agent_function(agent, false, on_load_symbols, num_symbol_entries));
}

static OnLoadEntry_t lookup_JVM_OnLoad_entry_point(JvmtiAgent* lib) {
  const char* on_load_symbols[] = JVM_ONLOAD_SYMBOLS;
  return lookup_On_Load_entry_point(lib, on_load_symbols, sizeof(on_load_symbols) / sizeof(char*));
}

static OnLoadEntry_t lookup_Agent_OnLoad_entry_point(JvmtiAgent* agent) {
  const char* on_load_symbols[] = AGENT_ONLOAD_SYMBOLS;
  return lookup_On_Load_entry_point(agent, on_load_symbols, sizeof(on_load_symbols) / sizeof(char*));
}

void JvmtiAgent::convert_xrun_agent() {
  assert(is_xrun(), "invariant");
  assert(!is_loaded(), "invariant");
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invalid init sequence");
  OnLoadEntry_t on_load_entry = lookup_JVM_OnLoad_entry_point(this);
  // If there is an JVM_OnLoad function it will get called later,
  // otherwise see if there is an Agent_OnLoad.
  if (on_load_entry == nullptr) {
    on_load_entry = lookup_Agent_OnLoad_entry_point(this);
    if (on_load_entry == nullptr) {
      vm_exit_during_initialization("Could not find JVM_OnLoad or Agent_OnLoad function in the library", name());
    }
    _xrun = false; // converted
  }
}

// Called after the VM is initialized for -Xrun agents which have not been converted to JVMTI agents.
static bool invoke_JVM_OnLoad(JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_xrun(), "invariant");
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invalid init sequence");
  OnLoadEntry_t on_load_entry = lookup_JVM_OnLoad_entry_point(agent);
  if (on_load_entry == nullptr) {
    vm_exit_during_initialization("Could not find JVM_OnLoad function in -Xrun library", agent->name());
  }
  // Invoke the JVM_OnLoad function
  JavaThread* thread = JavaThread::current();
  ThreadToNativeFromVM ttn(thread);
  HandleMark hm(thread);
  extern struct JavaVM_ main_vm;
  const jint err = (*on_load_entry)(&main_vm, const_cast<char*>(agent->options()), nullptr);
  if (err != JNI_OK) {
    vm_exit_during_initialization("-Xrun library failed to init", agent->name());
  }
  return true;
}

// The newest jvmtiEnv is appended to the list,
// hence the JvmtiEnvIterator order is from oldest to newest.
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

// Associate the last, i.e. most recent, JvmtiEnv that is a JPLIS agent with the current agent.
static void convert_to_jplis(JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_instrument_lib(), "invariant");
  JvmtiEnv* const env = get_last_jplis_jvmtienv();
  assert(env != nullptr, "invariant");
  const JPLISEnvironmentMirror* const jplis_env = get_env_local_storage(env);
  assert(jplis_env != nullptr, "invaiant");
  assert(reinterpret_cast<JvmtiEnv*>(jplis_env->mJVMTIEnv) == env, "invariant");
  agent->set_jplis(jplis_env->mAgent);
}

// Use this for JavaThreads and state is _thread_in_vm.
class AgentJavaThreadEventTransition : StackObj {
 private:
  ResourceMark _rm;
  ThreadToNativeFromVM _transition;
  HandleMark _hm;
 public:
  AgentJavaThreadEventTransition(JavaThread* thread) : _rm(), _transition(thread), _hm(thread) {};
};

class AgentEventMark : StackObj {
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
    if (state != nullptr) {
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
    if (state != nullptr) {
      // Restore the jvmti thread exception state.
      state->restore_exception_state(_saved_exception_state);
    }
  }
};

class AgentThreadEventMark : public AgentEventMark {
 private:
  jobject _jthread;
 public:
  AgentThreadEventMark(JavaThread* thread) : AgentEventMark(thread),
                                             _jthread(JNIHandles::make_local(thread, thread->threadObj())) {}
  jthread jni_thread() { return (jthread)_jthread; }
};

static void unload_library(JvmtiAgent* agent, void* library) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_loaded(), "invariant");
  if (!agent->is_static_lib()) {
    assert(library != nullptr, "invariant");
    os::dll_unload(library);
  }
}

// type for the Agent_OnAttach entry point
extern "C" {
  typedef jint(JNICALL* OnAttachEntry_t)(JavaVM*, char*, void*);
}

// Loading the agent by invoking Agent_OnAttach.
// This function is called before the agent is added to JvmtiAgentList.
static bool invoke_Agent_OnAttach(JvmtiAgent* agent, outputStream* st) {
  if (!EnableDynamicAgentLoading) {
    st->print_cr("Dynamic agent loading is not enabled. "
                 "Use -XX:+EnableDynamicAgentLoading to launch target VM.");
    return false;
  }
  DEBUG_ONLY(assert_preload(agent);)
  assert(agent->is_dynamic(), "invariant");
  assert(st != nullptr, "invariant");
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_LIVE, "not in live phase!");
  const char* on_attach_symbols[] = AGENT_ONATTACH_SYMBOLS;
  const size_t num_symbol_entries = ARRAY_SIZE(on_attach_symbols);
  void* library = nullptr;
  bool previously_loaded;
  if (load_agent_from_executable(agent, &on_attach_symbols[0], num_symbol_entries)) {
    previously_loaded = JvmtiAgentList::is_static_lib_loaded(agent->name());
  } else {
    library = load_library(agent, &on_attach_symbols[0], num_symbol_entries, /* vm_exit_on_error */ false);
    if (library == nullptr) {
      st->print_cr("%s was not loaded.", agent->name());
      if (*ebuf != '\0') {
        st->print_cr("%s", &ebuf[0]);
      }
      return false;
    }
    agent->set_os_lib_path(&buffer[0]);
    agent->set_os_lib(library);
    agent->set_loaded();
  #ifdef AIX
    previously_loaded = JvmtiAgentList::is_dynamic_lib_loaded(agent->device(), agent->inode());
  #else
    previously_loaded = JvmtiAgentList::is_dynamic_lib_loaded(library);
  #endif
  }

  // Print warning if agent was not previously loaded and EnableDynamicAgentLoading not enabled on the command line.
  if (!previously_loaded && !FLAG_IS_CMDLINE(EnableDynamicAgentLoading) && !agent->is_instrument_lib()) {
    jio_fprintf(defaultStream::error_stream(),
      "WARNING: A JVM TI agent has been loaded dynamically (%s)\n"
      "WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning\n"
      "WARNING: Dynamic loading of agents will be disallowed by default in a future release\n", agent->name());
  }

  assert(agent->is_loaded(), "invariant");
  // The library was loaded so we attempt to lookup and invoke the Agent_OnAttach function.
  OnAttachEntry_t on_attach_entry = CAST_TO_FN_PTR(OnAttachEntry_t,
                                                   os::find_agent_function(agent, false, &on_attach_symbols[0], num_symbol_entries));

  if (on_attach_entry == nullptr) {
    st->print_cr("%s is not available in %s", on_attach_symbols[0], agent->name());
    unload_library(agent, library);
    return false;
  }

  // Invoke the Agent_OnAttach function
  JavaThread* thread = JavaThread::current();
  jint result = JNI_ERR;
  {
    extern struct JavaVM_ main_vm;
    AgentThreadEventMark jem(thread);
    AgentJavaThreadEventTransition jet(thread);

    agent->initialization_begin();

    result = (*on_attach_entry)(&main_vm, (char*)agent->options(), nullptr);

    agent->initialization_end();

    // Agent_OnAttach may have used JNI
    if (thread->is_pending_jni_exception_check()) {
      thread->clear_pending_jni_exception_check();
    }
  }

  // Agent_OnAttach may have used JNI
  if (thread->has_pending_exception()) {
    thread->clear_pending_exception();
  }

  st->print_cr("return code: %d", result);

  if (result != JNI_OK) {
    unload_library(agent, library);
    return false;
  }

  if (agent->is_instrument_lib()) {
    // Convert the instrument lib to the actual JPLIS / javaagent it represents.
    convert_to_jplis(agent);
  }
  return true;
}

// CDS dumping does not support native JVMTI agent.
// CDS dumping supports Java agent if the AllowArchivingWithJavaAgent diagnostic option is specified.
static void check_cds_dump(JvmtiAgent* agent) {
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

// Loading the agent by invoking Agent_OnLoad.
static bool invoke_Agent_OnLoad(JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(!agent->is_xrun(), "invariant");
  assert(!agent->is_dynamic(), "invariant");
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_ONLOAD, "invariant");
  if (Arguments::is_dumping_archive()) {
    check_cds_dump(agent);
  }
  OnLoadEntry_t on_load_entry = lookup_Agent_OnLoad_entry_point(agent);
  if (on_load_entry == nullptr) {
    vm_exit_during_initialization("Could not find Agent_OnLoad function in the agent library", agent->name());
  }
  // Invoke the Agent_OnLoad function
  extern struct JavaVM_ main_vm;
  if ((*on_load_entry)(&main_vm, const_cast<char*>(agent->options()), nullptr) != JNI_OK) {
    vm_exit_during_initialization("agent library failed Agent_OnLoad", agent->name());
  }
  // Convert the instrument lib to the actual JPLIS / javaagent it represents.
  if (agent->is_instrument_lib()) {
    convert_to_jplis(agent);
  }
  return true;
}

bool JvmtiAgent::load(outputStream* st /* nullptr */) {
  if (is_xrun()) {
    return invoke_JVM_OnLoad(this);
  }
  return is_dynamic() ? invoke_Agent_OnAttach(this, st) : invoke_Agent_OnLoad(this);
}

extern "C" {
  typedef void (JNICALL* Agent_OnUnload_t)(JavaVM*);
}

void JvmtiAgent::unload() {
  const char* on_unload_symbols[] = AGENT_ONUNLOAD_SYMBOLS;
  // Find the Agent_OnUnload function.
  Agent_OnUnload_t unload_entry = CAST_TO_FN_PTR(Agent_OnUnload_t,
                                                 os::find_agent_function(this, false, &on_unload_symbols[0], ARRAY_SIZE(on_unload_symbols)));
  if (unload_entry != nullptr) {
    // Invoke the Agent_OnUnload function
    JavaThread* thread = JavaThread::current();
    ThreadToNativeFromVM ttn(thread);
    HandleMark hm(thread);
    extern struct JavaVM_ main_vm;
    (*unload_entry)(&main_vm);
  }
}
