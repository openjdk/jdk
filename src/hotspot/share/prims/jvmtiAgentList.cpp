/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "prims/jvmtiAgentList.hpp"

#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.inline.hpp"

JvmtiAgent* JvmtiAgentList::_list = nullptr;

// Selection as a function of the filter.
JvmtiAgent* JvmtiAgentList::Iterator::select(JvmtiAgent* agent) const {
  while (agent != nullptr) {
    if (_filter == ALL) {
      return agent;
    } else if (_filter == NOT_XRUN) {
      if (!agent->is_xrun()) {
        return agent;
      }
    } else if (_filter == JAVA) {
      if (agent->is_jplis()) {
        return agent;
      }
    } else if (_filter == NATIVE) {
      if (!agent->is_jplis() && !agent->is_xrun()) {
        return agent;
      }
    } else {
      assert(_filter == XRUN, "invariant");
      if (agent->is_xrun()) {
        return agent;
      }
    }
    agent = agent->next();
  }
  return nullptr;
}

static inline JvmtiAgent* head(JvmtiAgent** list) {
  assert(list != nullptr, "invariant");
  return Atomic::load_acquire(list);
}


// The storage list is a single cas-linked-list, to allow for concurrent iterations.
// Especially during initial loading of agents, there exist an order requirement to iterate oldest -> newest.
// Our concurrent storage linked-list is newest -> oldest.
// The correct order is preserved by the iterator, by storing a filtered set of entries in a stack.
JvmtiAgentList::Iterator::Iterator(JvmtiAgent** list, Filter filter) :
  _stack(new GrowableArrayCHeap<JvmtiAgent*, mtServiceability>(16)), _filter(filter) {
  JvmtiAgent* next = head(list);
  while (next != nullptr) {
    next = select(next);
    if (next != nullptr) {
      _stack->push(next);
      next = next->next();
    }
  }
}

bool JvmtiAgentList::Iterator::has_next() const {
  assert(_stack != nullptr, "invariant");
  return _stack->is_nonempty();
}

const JvmtiAgent* JvmtiAgentList::Iterator::next() const {
  assert(has_next(), "invariant");
  return _stack->pop();
}

JvmtiAgent* JvmtiAgentList::Iterator::next() {
  return const_cast<JvmtiAgent*>(const_cast<const Iterator*>(this)->next());
}

JvmtiAgentList::Iterator JvmtiAgentList::agents() {
  return Iterator(&_list, Iterator::NOT_XRUN);
}

JvmtiAgentList::Iterator JvmtiAgentList::java_agents() {
  return Iterator(&_list, Iterator::JAVA);
}

JvmtiAgentList::Iterator JvmtiAgentList::native_agents() {
  return Iterator(&_list, Iterator::NATIVE);
}

JvmtiAgentList::Iterator JvmtiAgentList::xrun_agents() {
  return Iterator(&_list, Iterator::XRUN);
}

JvmtiAgentList::Iterator JvmtiAgentList::all() {
  return Iterator(&_list, Iterator::ALL);
}

void JvmtiAgentList::add(JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  JvmtiAgent* next;
  do {
    next = head(&_list);
    agent->set_next(next);
  } while (Atomic::cmpxchg(&_list, next, agent) != next);
}

void JvmtiAgentList::add(const char* name, const char* options, bool absolute_path) {
  add(new JvmtiAgent(name, options, absolute_path));
}

void JvmtiAgentList::add_xrun(const char* name, const char* options, bool absolute_path) {
  JvmtiAgent* agent = new JvmtiAgent(name, options, absolute_path);
  agent->set_xrun();
  add(agent);
}

#ifdef ASSERT
static void assert_initialized(JvmtiAgentList::Iterator& it) {
  while (it.has_next()) {
    assert(it.next()->is_initialized(), "invariant");
  }
}
#endif

// In case an agent did not enable the VMInit callback, or if it is an -Xrun agent,
// it gets an initializiation timestamp here.
void JvmtiAgentList::initialize() {
  Iterator it = all();
  while (it.has_next()) {
    JvmtiAgent* agent = it.next();
    if (!agent->is_initialized()) {
      agent->initialization_begin();
    }
  }
  DEBUG_ONLY(Iterator assert_it = all(); assert_initialized(assert_it);)
}

void JvmtiAgentList::convert_xrun_agents() {
  Iterator it = xrun_agents();
  while (it.has_next()) {
    it.next()->convert_xrun_agent();
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

static void load_agents(JvmtiAgentList::Iterator& it) {
  while (it.has_next()) {
    it.next()->load();
  }
}

// Invokes Agent_OnLoad for -agentlib:.. -agentpath:  and converted -Xrun agents.
// Called very early -- before JavaThreads exist
void JvmtiAgentList::load_agents() {
  // Convert -Xrun to -agentlib: if there is no JVM_OnLoad
  convert_xrun_agents();
  JvmtiPhaseTransition transition;
  Iterator it = agents();
  ::load_agents(it);
}

// Launch -Xrun agents
void JvmtiAgentList::load_xrun_agents() {
  assert(JvmtiEnvBase::get_phase() == JVMTI_PHASE_PRIMORDIAL, "invalid init sequence");
  Iterator it = xrun_agents();
  ::load_agents(it);
}

// Invokes Agent_OnAttach for agents loaded dynamically during runtime.
void JvmtiAgentList::load_agent(const char* agent_name, bool is_absolute_path,
                                const char* options, outputStream* st) {
  JvmtiAgent* const agent = new JvmtiAgent(agent_name, options, is_absolute_path, /* dynamic agent */ true);
  if (agent->load(st)) {
    add(agent);
  } else {
    delete agent;
  }
}

// Send any Agent_OnUnload notifications
void JvmtiAgentList::unload_agents() {
  Iterator it = agents();
  while (it.has_next()) {
    it.next()->unload();
  }
}

// Return true if a statically linked agent is on the list
bool JvmtiAgentList::is_static_lib_loaded(const char* name) {
  JvmtiAgentList::Iterator it = JvmtiAgentList::agents();
  while (it.has_next()) {
    JvmtiAgent* const agent = it.next();
    if (agent->is_static_lib() && strcmp(agent->name(), name) == 0) {
      return true;
    }
  }
  return false;
}

// Return true if a agent library on the list
bool JvmtiAgentList::is_dynamic_lib_loaded(void* os_lib) {
  JvmtiAgentList::Iterator it = JvmtiAgentList::agents();
  while (it.has_next()) {
    JvmtiAgent* const agent = it.next();
    if (!agent->is_static_lib() && agent->os_lib() == os_lib) {
      return true;
    }
  }
  return false;
}

static bool match(JvmtiEnv* env, const JvmtiAgent* agent, const void* os_module_address) {
  assert(env != nullptr, "invariant");
  assert(agent != nullptr, "invariant");
  if (agent->is_static_lib()) {
    return os::get_default_process_handle() == os_module_address;
  }
  if (agent->os_lib() != os_module_address) {
    return false;
  }
  return agent->is_instrument_lib() ? agent->is_jplis(env) : true;
}

// The function pointer is a JVMTI callback function.
// Find the os module (dll) that exports this function.
// Now we can map a JVMTI env to its corresponding agent.
JvmtiAgent* JvmtiAgentList::lookup(JvmtiEnv* env, void* f_ptr) {
  assert(env != nullptr, "invariant");
  assert(f_ptr != nullptr, "invariant");
  static char buffer[JVM_MAXPATHLEN];
  int offset;
  if (!os::dll_address_to_library_name(reinterpret_cast<address>(f_ptr), &buffer[0], JVM_MAXPATHLEN, &offset)) {
    return nullptr;
  }
  assert(buffer[0] != '\0', "invariant");
  const void* const os_module_address = reinterpret_cast<address>(f_ptr) - offset;

  JvmtiAgentList::Iterator it = JvmtiAgentList::agents();
  while (it.has_next()) {
    JvmtiAgent* const agent = it.next();
    if (match(env, agent, os_module_address)) {
      agent->set_os_lib_path(&buffer[0]);
      return agent;
    }
  }
  return nullptr;
}
