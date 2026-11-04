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
 */

#include "memory/universe.hpp"
#include "prims/jvmtiAgent.hpp"
#include "prims/jvmtiAgentLifecycle.hpp"
#include "prims/jvmtiAgentSet.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/threads.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/pair.hpp"
#if INCLUDE_JFR
#include "jfr/jfr.hpp"
#endif

static inline bool is_vm_bootstrap() {
  return Threads::number_of_threads() == 0;
}

static inline bool is_not_vm_bootstrap() {
  return !is_vm_bootstrap();
}

static inline bool is_live_phase() {
  return JvmtiEnvBase::get_phase() == JVMTI_PHASE_LIVE;
}

#ifdef ASSERT
static void assert_exclusive_access() {
  assert(is_vm_bootstrap() || JvmtiThreadState_lock->owned_by_self(), "invariant");
}
#endif

static JvmtiAgent* volatile _agents = nullptr;

static inline JvmtiAgent* agent_head() {
  return AtomicAccess::load_acquire(&_agents);
}

void JvmtiAgentSet::add(JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(is_vm_bootstrap() || JvmtiThreadState_lock->owned_by_self(), "invariant");
  assert(agent->next() == nullptr, "invariant");
  agent->mark_registered();
  // FIFO queue. Inserted nodes are never removed, and each link changes only once,
  // from null to its successor. The release-store publishes the new node to readers
  // acquiring the head or a successor link.
  JvmtiAgent* volatile* tail_ptr = &_agents;
  while (true) {
    JvmtiAgent* const next = AtomicAccess::load_acquire(tail_ptr);
    if (next == nullptr) {
      AtomicAccess::release_store(tail_ptr, agent);
      return;
    }
    tail_ptr = &next->_next;
  }
}

static inline bool is_element_of(const JvmtiAgent* agent, JvmtiAgentSet::Subset subset) {
  assert(agent != nullptr, "invariant");
  assert(subset == JvmtiAgentSet::XRUN ? agent->is_xrun() && !agent->is_registered() :
    agent->is_registered() && !agent->is_xrun(), "invariant");
  const bool live_only = subset == JvmtiAgentSet::JAVA || subset == JvmtiAgentSet::NATIVE;
  if (live_only && agent->is_disposed()) {
    return false;
  }
  switch (subset) {
    case JvmtiAgentSet::REGISTERED: return true;
    case JvmtiAgentSet::JAVA: return agent->is_java_agent();
    case JvmtiAgentSet::NATIVE: return !agent->is_java_agent();
    case JvmtiAgentSet::XRUN: return agent->is_xrun();
  }
  ShouldNotReachHere();
  return false;
}

using JvmtiAgentList = GrowableArrayCHeap<JvmtiAgent*, mtServiceability>;

// The _xrun_agent_list is a JVM lifetime stable list holding non-converted xrun agents, if any.
// The list provides reachability of xrun agents via JFR, jcmd VM.info, and VM error reporting.
static JvmtiAgentList* _xrun_agent_list = nullptr;

static inline JvmtiAgent* next_xrun_agent(int& idx) {
  assert(idx >= 0, "invariant");
  if (_xrun_agent_list == nullptr || idx == _xrun_agent_list->length()) {
    return nullptr;
  }
  return _xrun_agent_list->at(idx++);
}

JvmtiAgent* JvmtiAgentSet::Iterator::internal_next() const {
  return _subset == XRUN ? next_xrun_agent(_idx) : _next->next();
}

JvmtiAgent* JvmtiAgentSet::Iterator::initial_element() const {
  assert(_idx == 0, "invariant");
  return _subset == XRUN ? next_xrun_agent(_idx) : agent_head();
}

JvmtiAgentSet::Iterator::Iterator(JvmtiAgentSet::Subset subset) :
  _subset(subset), _idx(0), _next(initial_element()) {
  while (_next != nullptr && !is_element_of(_next, _subset)) {
    _next = internal_next();
  }
}

JvmtiAgentSet::Iterator::Iterator() : _subset(), _idx(0), _next(nullptr) {}

bool JvmtiAgentSet::Iterator::has_next() const {
  return _next != nullptr;
}

JvmtiAgent* JvmtiAgentSet::Iterator::next() const {
  assert(has_next(), "invariant");
  JvmtiAgent* result = _next;
  assert(result != nullptr, "invariant");
  do {
    _next = internal_next();
  } while (_next != nullptr && !is_element_of(_next, _subset));
  return result;
}

JvmtiAgentSet::Iterator JvmtiAgentSet::agents() {
  return Iterator(Subset::REGISTERED);
}

JvmtiAgentSet::Iterator JvmtiAgentSet::java_agents() {
  return Iterator(Subset::JAVA);
}

JvmtiAgentSet::Iterator JvmtiAgentSet::native_agents() {
  return Iterator(Subset::NATIVE);
}

JvmtiAgentSet::Iterator JvmtiAgentSet::xrun_agents() {
  return Iterator(Subset::XRUN);
}

using JvmtiStartupRequestEntry = Pair<const Thread*, JvmtiAgent*>;
using JvmtiStartupRequestArray = GrowableArrayCHeap<JvmtiStartupRequestEntry, mtServiceability>;

// The startup request array is a LIFO queue holding JvmtiAgents
// during the invocation of their startup routine.It provides
// reachability for jvmtiEnvs to be associated with its agent.
static JvmtiStartupRequestArray* _startup_requests = nullptr;

static void initialize_startup_requests() {
  assert(is_vm_bootstrap(), "invariant");
  assert(_startup_requests == nullptr, "invariant");
  _startup_requests = new JvmtiStartupRequestArray(16);
}

// A startup request is bound to a scope to support nested startups.
class JvmtiStartupRequestScope : StackObj {
 private:
  JvmtiAgent* const _agent;
  Thread* const _thread;
  NONCOPYABLE(JvmtiStartupRequestScope);
 public:
  JvmtiStartupRequestScope(JvmtiAgent* agent) : _agent(agent), _thread(Thread::current_or_null_safe()) {
    assert(agent != nullptr, "invariant");
    assert(_startup_requests != nullptr, "invariant");
    ConditionalMutexLocker lock(_thread, JvmtiThreadState_lock, is_not_vm_bootstrap());
    _startup_requests->push(JvmtiStartupRequestEntry(_thread, agent));
  }

  ~JvmtiStartupRequestScope() {
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
};

#ifdef ASSERT
static void assert_associate_startup_request(Thread* thread) {
  assert(thread == nullptr || (thread->is_Java_thread() &&
    JavaThread::cast(thread)->thread_state() == _thread_in_vm), "invariant");
  assert_exclusive_access();
}

static void assert_startup_request(const JvmtiAgent* agent) {
  assert(agent->path() != nullptr, "invariant");
  assert(agent->resolver() != nullptr, "invariant");
  assert(agent->has_env(), "invariant");
  assert(agent->is_associated(), "invariant");
}
#endif

static void associate_startup_request(Thread* thread, const jvmtiEnv* env) {
  DEBUG_ONLY(assert_associate_startup_request(thread);)
  assert(_startup_requests != nullptr, "invariant");
  for (int i = _startup_requests->length() - 1; i >= 0; --i) {
    if (_startup_requests->at(i).first == thread) {
      JvmtiAgent* const agent = _startup_requests->at(i).second;
      if (agent->can_associate()) {
        agent->set_env(env);
        DEBUG_ONLY(assert_startup_request(agent);)
      }
      return;
    }
  }
}

// Invoked by jni_GetEnv() upon successful jvmtiEnv* creation.
void JvmtiAgentSet::on_env_creation(const jvmtiEnv* env) {
  assert(env != nullptr, "invariant");
  if (is_vm_bootstrap()) {
    associate_startup_request(nullptr, env);
    return;
  }
  JavaThread* const jt = JavaThread::current();
  assert(jt != nullptr, "invariant");
  MACOS_AARCH64_ONLY(ThreadWXEnable __wx(WXWrite, jt));
  ThreadInVMfromNative __tiv(jt);
  VM_ENTRY_BASE(void, install_jvmti_env, jt)
  DEBUG_ONLY(VMNativeEntryWrapper __vew;)
  MutexLocker lock(jt, JvmtiThreadState_lock);
  associate_startup_request(jt, env);
}

static JvmtiAgent* find_in_startup_request(const jvmtiEnv* env) {
  DEBUG_ONLY(assert_exclusive_access();)
  assert(env != nullptr, "invariant");
  assert(_startup_requests != nullptr, "invariant");
  for (int i = 0; i < _startup_requests->length(); ++i) {
    JvmtiAgent* const agent = _startup_requests->at(i).second;
    if (agent->equals(env)) {
      assert(agent->is_associated(), "invariant");
      return agent;
    }
  }
  return nullptr;
}

JvmtiAgent* JvmtiAgentSet::find(const jvmtiEnv* env) {
  DEBUG_ONLY(assert_exclusive_access();)
  assert(env != nullptr, "invariant");
  Iterator agent_iter = agents();
  while (agent_iter.has_next()) {
    JvmtiAgent* const agent = agent_iter.next();
    if (agent->equals(env)) {
      assert(agent->is_registered() && agent->is_associated(), "invariant");
      return agent;
    }
  }
  return nullptr;
}

void JvmtiAgentSet::on_env_disposal(const jvmtiEnv* env) {
  assert(env != nullptr, "invariant");
  DEBUG_ONLY(assert_exclusive_access();)
  JvmtiAgent* agent = find(env);
  if (agent == nullptr) {
    agent = find_in_startup_request(env);
  }
  if (agent != nullptr) {
    agent->clear_env();
    if (agent->is_registered()) {
      if (agent->is_timing_unstarted()) {
        // Disposed before VMInit. Publish the unavailable timing values.
        // No later initialization timestamp should be assigned to this agent.
        agent->initialization_unavailable();
      }
      report_disposal(agent);
    }
  }
}

void JvmtiAgentSet::report_disposal(JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_disposed(), "invariant");
  assert(agent->has_startup_returned(), "invariant");
  assert(is_vm_bootstrap() || JvmtiThreadState_lock->owned_by_self(), "invariant");
  JFR_ONLY(if (agent->is_timing_published()) { Jfr::on_jvmti_agent_disposal(agent);})
}

JvmtiAgent* JvmtiAgentSet::find_vm_init_agent(const jvmtiEnv* env) {
  // Dynamic agents are timed around Agent_OnAttach, even if their environment
  // is encountered during VMInit. Do not overwrite their published timing.
  JvmtiAgent* const agent = find(env);
  return agent != nullptr && !agent->is_dynamic() ? agent : nullptr;
}

JvmtiAgent* JvmtiAgentSet::initialization_begin(const jvmtiEnv* env) {
  assert(env != nullptr, "invariant");
  MutexLocker lock(JvmtiThreadState_lock);
  JvmtiAgent* const agent = find_vm_init_agent(env);
  if (agent != nullptr) {
    assert(agent->is_timing_unstarted(), "invariant");
    agent->initialization_begin();
  }
  return agent;
}

void JvmtiAgentSet::initialization_end(JvmtiAgent* agent) {
  if (agent == nullptr) {
    return;
  }
  MutexLocker lock(JvmtiThreadState_lock);
  assert(agent->is_initializing(), "invariant");
  agent->initialization_end();
  if (agent->is_disposed()) {
    report_disposal(agent);
  }
}

// In case an agent did not enable the VMInit callback, or if it is an -Xrun agent,
// publish an initialization timestamp with zero duration. No callback was
// invoked, so zero becomes an established timing rather than an unavailable value.
void JvmtiAgentSet::initialization_begin() {
  MutexLocker lock(JvmtiThreadState_lock);

  const auto initialize_if_unstarted = [](JvmtiAgent* agent) {
    assert(agent != nullptr, "invariant");
    if (agent->is_timing_unstarted()) {
      agent->initialization_without_vm_init_callback();
    }
    assert(agent->is_timing_published() || agent->is_initializing(), "invariant");
  };

  const Iterator agent_iter = agents();
  while (agent_iter.has_next()) {
    initialize_if_unstarted(agent_iter.next());
  }

  const Iterator xrun_agent_iter = xrun_agents();
  while (xrun_agent_iter.has_next()) {
    initialize_if_unstarted(xrun_agent_iter.next());
  }
}

// The on_load_phase_agent_list holds all agents coming in from argument parsing.
// It is a FIFO queue of startup requests that wíll be drained when starting up agents.
static JvmtiAgentList* _on_load_phase_agent_list = nullptr;

static void append(JvmtiAgent* agent, JvmtiAgentList** list) {
  assert(agent != nullptr, "invariant");
  assert(list != nullptr, "invariant");
  if (*list == nullptr) {
    *list = new JvmtiAgentList();
  }
  (*list)->append(agent);
}

void JvmtiAgentSet::on_argument_parse(const char* name,
                                      const char* options,
                                      bool is_absolute_path,
                                      bool xrun /* false */) {
  assert(is_vm_bootstrap(), "invariant");
  append(new JvmtiAgent(name, options, is_absolute_path, xrun), &_on_load_phase_agent_list);
}

static void destroy(JvmtiAgentList** list) {
  assert(list != nullptr, "invariant");
  if (*list != nullptr) {
    for (int i = 0; i < (*list)->length(); ++i) {
      delete (*list)->at(i);
    }
    delete* list;
    *list = nullptr;
  }
}

void JvmtiAgentSet::disable_agent_list() {
#if INCLUDE_CDS
  assert(!Universe::is_bootstrapping() && !Universe::is_fully_initialized(), "must do this very early");
  assert(!is_live_phase(), "invariant");
  // Pretend that no agents have been added.
  destroy(&_on_load_phase_agent_list);
#endif
}

bool JvmtiAgentSet::startup(JvmtiAgent* agent, outputStream* st) {
  assert(agent != nullptr, "invariant");
  assert(st == nullptr || agent->is_dynamic(), "invariant");
  JvmtiStartupRequestScope scope(agent);
  const bool succeeded = JvmtiAgentLifecycle::startup(agent, st);
  ConditionalMutexLocker lock(Thread::current_or_null_safe(), JvmtiThreadState_lock, is_not_vm_bootstrap());
  if (succeeded && !agent->is_xrun()) {
    JvmtiAgentLifecycle::register_for_native_resolution(agent);
  }
  if (!agent->is_associated() && !agent->is_disposed()) {
    return false;
  }
  if (agent->is_disposed()) {
    if (!agent->is_timing_published()) {
      agent->initialization_unavailable();
    }
    report_disposal(agent);
    return false;
  }
  add(agent);
  return true;
}

void JvmtiAgentSet::startup() {
  assert(_on_load_phase_agent_list != nullptr, "invariant");
  while (_on_load_phase_agent_list->is_nonempty()) {
    JvmtiAgent* const agent = _on_load_phase_agent_list->at(0);
    assert(!agent->is_xrun(), "invariant");
    const bool retained = startup(agent);
    assert(_on_load_phase_agent_list->at(0) == agent, "invariant");
    _on_load_phase_agent_list->remove_at(0);
    if (!retained) {
      delete agent;
    }
  }
  assert(_on_load_phase_agent_list->is_empty(), "invariant");
  destroy(&_on_load_phase_agent_list);
}

void JvmtiAgentSet::startup_xrun() {
  assert(_xrun_agent_list != nullptr, "invariant");
  for (int i = 0; i < _xrun_agent_list->length(); ++i) {
    JvmtiAgent* const agent = _xrun_agent_list->at(i);
    assert(agent->is_xrun(), "invariant");
    startup(agent);
  }
}

// Launch -Xrun agents
void JvmtiAgentSet::startup_xrun_agents() {
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invariant");
  assert(_startup_requests != nullptr, "invariant");
  assert(_on_load_phase_agent_list == nullptr, "invariant");
  if (_xrun_agent_list == nullptr) {
    // No xrun agents specified on the command-line.
    return;
  }
  startup_xrun();
}

static void add_to_xrun_list(JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_xrun(), "invariant");
  assert(!agent->is_registered(), "xrun agents are retained separately from registered agents");
  assert(is_vm_bootstrap(), "invariant");
  append(agent, &_xrun_agent_list);
}

// Attempt to convert -Xrun to -agentlib: if there is no JVM_OnLoad entry point.
static void convert_xrun_agents() {
  assert(is_vm_bootstrap(), "invariant");
  assert(_on_load_phase_agent_list != nullptr, "invariant");
  for (int i = 0; i < _on_load_phase_agent_list->length();) {
    JvmtiAgent* const agent = _on_load_phase_agent_list->at(i);
    if (agent->is_xrun()) {
      JvmtiAgentLifecycle::attempt_xrun_conversion(agent);
      if (agent->is_xrun()) {
        // Conversion did not happen. Move agent to the xrun list.
        _on_load_phase_agent_list->remove_at(i);
        add_to_xrun_list(agent);
        continue;
      }
    }
    ++i;
  }
}

class JvmtiPhaseTransition : public StackObj {
public:
  JvmtiPhaseTransition() {
    assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invalid init sequence");
    JvmtiExport::enter_onload_phase();
  }
  ~JvmtiPhaseTransition() {
    assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_ONLOAD, "invariant");
    JvmtiExport::enter_primordial_phase();
  }
};

// Invokes Agent_OnLoad for -agentlib:.. -agentpath:  and converted -Xrun agents.
// Called very early -- before JavaThreads exist
void JvmtiAgentSet::startup_agents() {
  assert(is_vm_bootstrap(), "invariant");
  initialize_startup_requests();
  if (_on_load_phase_agent_list == nullptr) {
    // No agents specified on the command-line.
    return;
  }
  convert_xrun_agents();
  JvmtiPhaseTransition transition;
  startup();
}

// Invokes Agent_OnAttach for agents loaded dynamically during runtime.
void JvmtiAgentSet::startup_agent(const char* name,
                                  bool is_absolute_path,
                                  const char* options,
                                  outputStream* st) {
  assert(st != nullptr, "invariant");
  if (!is_live_phase()) {
    st->print_cr("Dynamic agent loading is only permitted in the live phase");
    return;
  }

  if (!EnableDynamicAgentLoading) {
    st->print_cr("Dynamic agent loading is not enabled. "
      "Use -XX:+EnableDynamicAgentLoading to launch target VM.");
    return;
  }

  JvmtiAgent* const agent = new JvmtiAgent(name, options, is_absolute_path);
  assert(agent->is_dynamic(), "invariant");

  if (!startup(agent, st)) {
    delete agent;
  }
}

static inline bool is_initialized(const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  return agent->is_timing_published() && agent->has_initialization_time();
}

static void print(const JvmtiAgent* agent, outputStream* st, bool& has_agents) {
  assert(agent != nullptr, "invariant");
  assert(st != nullptr, "invariant");
  if (!has_agents) {
    st->print_cr("JVMTI agents:");
    has_agents = true;
  }
  const char* dyninfo = agent->is_dynamic() ? "dynamic " : "";
  const char* instrumentinfo = agent->is_instrument_lib() ? "instrumentlib " : "";
  const char* loadinfo = agent->has_resolver() ? "loaded" : "not loaded";
  const char* initinfo = is_initialized(agent) ? "initialized" : "not initialized";
  const char* nameinfo = agent->name();
  const char* optionsinfo = agent->options();
  size_t name_length = strlen(nameinfo);
  if (agent->is_java_agent() && optionsinfo != nullptr) {
    // Java-agent representation without allocation.
    nameinfo = optionsinfo;
    const char* const equal_sign = strchr(nameinfo, '=');
    name_length = equal_sign != nullptr ? static_cast<size_t>(equal_sign - nameinfo) : strlen(nameinfo);
    optionsinfo = equal_sign != nullptr ? equal_sign + 1 : nullptr;
  }
  const char* pathinfo = agent->path();
  if (optionsinfo == nullptr) optionsinfo = "none";
  if (pathinfo == nullptr) pathinfo = "none";
  st->print_raw(nameinfo, name_length);
  st->print_cr(" path:%s, %s, %s, %s%soptions:%s", pathinfo,
                                                   loadinfo,
                                                   initinfo,
                                                   dyninfo,
                                                   instrumentinfo,
                                                   optionsinfo);
}

static void print(const JvmtiAgentList* list, outputStream* st, bool& has_agents) {
  assert(st != nullptr, "invariant");
  if (list != nullptr) {
    for (int i = 0; i < list->length(); ++i) {
      ::print(list->at(i), st, has_agents);
    }
  }
}
// Invoked by jcmd VM.info and VM error reporting.
void JvmtiAgentSet::print(outputStream* st) {
  assert(st != nullptr, "invariant");
  bool has_agents = false;
  const Iterator agent_iter = agents();
  while (agent_iter.has_next()) {
    ::print(agent_iter.next(), st, has_agents);
  }
  if (is_vm_bootstrap()) {
    ::print(_on_load_phase_agent_list, st, has_agents);
  }
  ::print(_xrun_agent_list, st, has_agents);
  if (!has_agents) {
    st->print_cr("JVMTI agents: none");
  }
}
