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
#include "prims/jvmtiAgentInvoker.hpp"
#include "prims/jvmtiAgentLibrary.hpp"
#include "prims/jvmtiAgentLibraryManager.hpp"
#include "prims/jvmtiAgentManager.hpp"
#include "prims/jvmtiAgentStartupRequest.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/globals.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/thread.hpp"
#include "runtime/threads.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
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

using JvmtiAgentList = GrowableArrayCHeap<JvmtiAgent*, mtServiceability>;

// The _xrun_agent_list is a JVM lifetime stable list holding non-converted xrun agents, if any.
// The list provides reachability of xrun agents via JFR, jcmd VM.info, and VM error reporting.
static JvmtiAgentList* _xrun_agent_list = nullptr;

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

void JvmtiAgentManager::on_argument_parse(const char* name,
                                          const char* options,
                                          bool is_absolute_path,
                                          bool xrun /* false */) {
  assert(is_vm_bootstrap(), "invariant");
  append(new JvmtiAgent(name, options, is_absolute_path, xrun), &_on_load_phase_agent_list);
}

void JvmtiAgentManager::disable_agent_list() {
#if INCLUDE_CDS
  assert(!Universe::is_bootstrapping() && !Universe::is_fully_initialized(), "must do this very early");
  assert(!is_live_phase(), "invariant");
  // Pretend that no agents have been added.
  destroy(&_on_load_phase_agent_list);
#endif
}

static JvmtiAgent* volatile _agents = nullptr;

static inline JvmtiAgent* agent_head() {
  return AtomicAccess::load_acquire(&_agents);
}

void JvmtiAgentManager::add(JvmtiAgent* agent) {
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

static inline bool is_element_of(const JvmtiAgent* agent, JvmtiAgentManager::Selection selection) {
  assert(agent != nullptr, "invariant");
  assert(selection == JvmtiAgentManager::XRUN ? agent->is_xrun() && !agent->is_registered() :
    agent->is_registered() && !agent->is_xrun(), "invariant");
  const bool live_only = selection == JvmtiAgentManager::LIVE_JAVA || selection == JvmtiAgentManager::LIVE_NATIVE;
  if (live_only && agent->is_env_disposed()) {
    return false;
  }
  switch (selection) {
    case JvmtiAgentManager::REGISTERED: return true;
    case JvmtiAgentManager::LIVE_JAVA: return agent->is_java_agent();
    case JvmtiAgentManager::LIVE_NATIVE: return !agent->is_java_agent();
    case JvmtiAgentManager::XRUN: return agent->is_xrun();
  }
  ShouldNotReachHere();
  return false;
}

static inline JvmtiAgent* next_xrun_agent(int& idx) {
  assert(idx >= 0, "invariant");
  if (_xrun_agent_list == nullptr || idx == _xrun_agent_list->length()) {
    return nullptr;
  }
  return _xrun_agent_list->at(idx++);
}

JvmtiAgent* JvmtiAgentManager::Iterator::internal_next() const {
  return _selection == XRUN ? next_xrun_agent(_idx) : _next->next();
}

JvmtiAgent* JvmtiAgentManager::Iterator::initial_element() const {
  assert(_idx == 0, "invariant");
  return _selection == XRUN ? next_xrun_agent(_idx) : agent_head();
}

JvmtiAgentManager::Iterator::Iterator(JvmtiAgentManager::Selection selection) :
  _selection(selection), _idx(0), _next(initial_element()) {
  while (_next != nullptr && !is_element_of(_next, _selection)) {
    _next = internal_next();
  }
}

JvmtiAgentManager::Iterator::Iterator() : _selection(), _idx(0), _next(nullptr) {}

bool JvmtiAgentManager::Iterator::has_next() const {
  return _next != nullptr;
}

JvmtiAgent* JvmtiAgentManager::Iterator::next() const {
  assert(has_next(), "invariant");
  JvmtiAgent* result = _next;
  assert(result != nullptr, "invariant");
  do {
    _next = internal_next();
  } while (_next != nullptr && !is_element_of(_next, _selection));
  return result;
}

JvmtiAgentManager::Iterator JvmtiAgentManager::agents() {
  return Iterator(Selection::REGISTERED);
}

JvmtiAgentManager::Iterator JvmtiAgentManager::java_agents() {
  return Iterator(Selection::LIVE_JAVA);
}

JvmtiAgentManager::Iterator JvmtiAgentManager::native_agents() {
  return Iterator(Selection::LIVE_NATIVE);
}

JvmtiAgentManager::Iterator JvmtiAgentManager::xrun_agents() {
  return Iterator(Selection::XRUN);
}

static JvmtiAgent* find_registered_agent(const jvmtiEnv* env) {
  DEBUG_ONLY(assert_exclusive_access();)
  assert(env != nullptr, "invariant");
  JvmtiAgentManager::Iterator agent_iter = JvmtiAgentManager::agents();
  while (agent_iter.has_next()) {
    JvmtiAgent* const agent = agent_iter.next();
    if (agent->matches_env(env)) {
      assert(agent->is_registered() && agent->is_env_associated(), "invariant");
      return agent;
    }
  }
  return nullptr;
}

static void report_disposal(JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_env_disposed(), "invariant");
  assert(agent->has_startup_returned(), "invariant");
  assert(is_vm_bootstrap() || JvmtiThreadState_lock->owned_by_self(), "invariant");
  JFR_ONLY(if (agent->is_timing_finalized()) { Jfr::on_jvmti_agent_disposal(agent);})
}

void JvmtiAgentManager::on_env_creation(const jvmtiEnv* env) {
  JvmtiAgentStartupRequest::on_env_creation(env);
}

void JvmtiAgentManager::on_env_disposal(const jvmtiEnv* env) {
  assert(env != nullptr, "invariant");
  DEBUG_ONLY(assert_exclusive_access();)
  JvmtiAgent* agent = find_registered_agent(env);
  if (agent == nullptr) {
    agent = JvmtiAgentStartupRequest::find(env);
  }
  if (agent != nullptr) {
    agent->mark_env_disposed();
    if (agent->is_registered()) {
      if (agent->is_timing_pending()) {
        // Disposed before VMInit. Publish the unavailable timing values.
        // No later initialization timestamp should be assigned to this agent.
        agent->finalize_unavailable_timing();
      }
      report_disposal(agent);
    }
  }
}

static JvmtiAgent* find_vm_init_agent(const jvmtiEnv* env) {
  // Dynamic agents are timed around Agent_OnAttach, even if their environment
  // is encountered during VMInit. Do not overwrite their published timing.
  JvmtiAgent* const agent = find_registered_agent(env);
  return agent != nullptr && !agent->is_dynamic() ? agent : nullptr;
}

JvmtiAgent* JvmtiAgentManager::begin_initialization_timing(const jvmtiEnv* env) {
  assert(env != nullptr, "invariant");
  MutexLocker lock(JvmtiThreadState_lock);
  JvmtiAgent* const agent = find_vm_init_agent(env);
  if (agent != nullptr) {
    assert(agent->is_timing_pending(), "invariant");
    agent->begin_initialization_timing();
  }
  return agent;
}

void JvmtiAgentManager::end_initialization_timing(JvmtiAgent* agent) {
  if (agent == nullptr) {
    return;
  }
  MutexLocker lock(JvmtiThreadState_lock);
  assert(agent->is_timing_measuring(), "invariant");
  agent->end_initialization_timing();
  if (agent->is_env_disposed()) {
    report_disposal(agent);
  }
}

// In case an agent did not enable the VMInit callback, or if it is an -Xrun agent,
// publish an initialization timestamp with zero duration. No callback was
// invoked, so zero becomes an established timing rather than an unavailable value.
void JvmtiAgentManager::finalize_pending_initialization_timing() {
  MutexLocker lock(JvmtiThreadState_lock);

  const auto finalize_if_timing_pending = [](JvmtiAgent* agent) {
    assert(agent != nullptr, "invariant");
    if (agent->is_timing_pending()) {
      agent->finalize_timing_without_vm_init_callback();
    }
    assert(agent->is_timing_finalized() || agent->is_timing_measuring(), "invariant");
  };

  const Iterator agent_iter = agents();
  while (agent_iter.has_next()) {
    finalize_if_timing_pending(agent_iter.next());
  }

  const Iterator xrun_agent_iter = xrun_agents();
  while (xrun_agent_iter.has_next()) {
    finalize_if_timing_pending(xrun_agent_iter.next());
  }
}

JvmtiAgentManager::StartResult JvmtiAgentManager::start(JvmtiAgent* agent, outputStream* st) {
  assert(agent != nullptr, "invariant");
  assert(st == nullptr || agent->is_dynamic(), "invariant");
  JvmtiAgentStartupRequest request(agent);
  const bool succeeded = JvmtiAgentInvoker::invoke_startup(agent, st);
  ConditionalMutexLocker lock(Thread::current_or_null_safe(), JvmtiThreadState_lock, is_not_vm_bootstrap());
  if (succeeded && !agent->is_xrun()) {
    JvmtiAgentLibraryManager::register_for_native_lookup(agent->library());
  }
  if (!agent->is_env_associated() && !agent->is_env_disposed()) {
    return StartResult::Discarded;
  }
  if (agent->is_env_disposed()) {
    if (!agent->is_timing_finalized()) {
      agent->finalize_unavailable_timing();
    }
    report_disposal(agent);
    return StartResult::Discarded;
  }
  add(agent);
  return StartResult::Retained;
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
      JvmtiAgentInvoker::attempt_xrun_conversion(agent);
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

void JvmtiAgentManager::start() {
  assert(_on_load_phase_agent_list != nullptr, "invariant");
  while (_on_load_phase_agent_list->is_nonempty()) {
    JvmtiAgent* const agent = _on_load_phase_agent_list->at(0);
    assert(!agent->is_xrun(), "invariant");
    const StartResult result = start(agent);
    assert(_on_load_phase_agent_list->at(0) == agent, "invariant");
    _on_load_phase_agent_list->remove_at(0);
    if (result == StartResult::Discarded) {
      delete agent;
    }
  }
  assert(_on_load_phase_agent_list->is_empty(), "invariant");
  destroy(&_on_load_phase_agent_list);
}

// Invokes Agent_OnLoad for -agentlib:.. -agentpath:  and converted -Xrun agents.
// Called very early -- before JavaThreads exist
void JvmtiAgentManager::start_agents() {
  assert(is_vm_bootstrap(), "invariant");
  JvmtiAgentStartupRequest::initialize();
  if (_on_load_phase_agent_list == nullptr) {
    // No agents specified on the command-line.
    return;
  }
  convert_xrun_agents();
  JvmtiPhaseTransition transition;
  start();
}

void JvmtiAgentManager::start_xrun() {
  assert(_xrun_agent_list != nullptr, "invariant");
  for (int i = 0; i < _xrun_agent_list->length(); ++i) {
    JvmtiAgent* const agent = _xrun_agent_list->at(i);
    assert(agent->is_xrun(), "invariant");
    start(agent);
  }
}

// Launch -Xrun agents
void JvmtiAgentManager::start_xrun_agents() {
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invariant");
  assert(JvmtiAgentStartupRequest::is_initialized(), "invariant");
  assert(_on_load_phase_agent_list == nullptr, "invariant");
  if (_xrun_agent_list == nullptr) {
    // No xrun agents specified on the command-line.
    return;
  }
  start_xrun();
}

// Invokes Agent_OnAttach for agents loaded dynamically during runtime.
void JvmtiAgentManager::start_agent(const char* name,
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

  if (start(agent, st) == StartResult::Discarded) {
    delete agent;
  }
}

void JvmtiAgentManager::invoke_Agent_OnUnload_entry_points(JavaThread* jt) {
  JvmtiAgentInvoker::invoke_Agent_OnUnload_entry_points(jt);
}

static inline bool is_initialized(const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  return agent->is_timing_finalized() && agent->has_initialization_time();
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
  const char* loadinfo = agent->has_library() ? "loaded" : "not loaded";
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
  const char* pathinfo = agent->has_library() ? agent->library()->path() : nullptr;
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
void JvmtiAgentManager::print(outputStream* st) {
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
