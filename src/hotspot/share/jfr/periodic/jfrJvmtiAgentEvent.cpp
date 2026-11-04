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
 *
 */

#include "jfr/jfrEvents.hpp"
#include "jfr/periodic/jfrJvmtiAgentEvent.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "prims/jvmtiAgent.hpp"
#include "prims/jvmtiAgentSet.hpp"
#include "runtime/javaThread.inline.hpp"

// Non-java agents:
// parsed_options gets the agent->options() and agent->name() is returned.
//
// Java agents (processing the agent->options() string):
// If the options string does not contain '=', then the options string is returned.
// if the options string contains '=', a copy is allocated and split at the first '=':
//   the left side is returned, the right side is placed into parsed_options.
static const char* name_and_options(const JvmtiAgent* agent, const char** parsed_options) {
  assert(agent != nullptr, "invariant");
  assert(parsed_options != nullptr, "invariant");
  assert(*parsed_options == nullptr, "invariant");
  const char* const options = agent->options();
  if (!agent->is_java_agent()) {
    *parsed_options = options;
    return agent->name();
  }
  assert(options != nullptr, "invariant");
  const char* const equal_sign = strchr(options, '=');
  if (equal_sign == nullptr) {
    return options;
  }
  const size_t length = strlen(options);
  char* const copy = NEW_RESOURCE_ARRAY(char, length + 1);
  memcpy(copy, options, length + 1);
  const ptrdiff_t equals_relative_position = equal_sign - options;
  assert(*(copy + equals_relative_position) == '=', "invariant");
  *(copy + equals_relative_position) = '\0';
  *parsed_options = copy + equals_relative_position + 1;
  return copy;
}

// Java Mission Control (JMC) uses (Java) Long.MIN_VALUE
// to describe that a long value is undefined.
static jlong jmc_undefined_long = min_jlong;

template <typename AgentEvent>
static void send_agent_event(AgentEvent& event, const JvmtiAgent* agent, const Ticks& timestamp, jlong disposal_time = jmc_undefined_long) {
  assert(agent != nullptr, "invariant");
  if (!agent->is_timing_published()) {
    return;
  }
  event.set_starttime(timestamp);
  event.set_endtime(timestamp);
  const char* options = nullptr;
  const char* const name = name_and_options(agent, &options);
  event.set_name(name);
  event.set_options(options);
  event.set_dynamic(agent->is_dynamic());
  event.set_initializationTime(agent->initialization_time());
  event.set_initializationDuration(agent->initialization_duration());
  event.set_disposalTime(disposal_time);
  event.commit();
}

template <typename AgentEvent>
static void send_agent_disposal_event(AgentEvent& event, const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_disposed(), "invariant");
  ResourceMark rm;
  const Ticks timestamp = Ticks::now();
  send_agent_event(event, agent, timestamp, JfrTime::is_ft_enabled() ? timestamp.ft_value() : timestamp.value());
}

static void send_native_agent_events(JvmtiAgentSet::Iterator& agent_iter, const Ticks& timestamp) {
  while (agent_iter.has_next()) {
    const JvmtiAgent* agent = agent_iter.next();
    assert(!agent->is_java_agent(), "invariant");
    EventNativeAgent event;
    event.set_path(agent->path());
    send_agent_event(event, agent, timestamp);
  }
}

void JfrJvmtiAgentEvent::generate_native_agent_events(const Ticks& timestamp) {
  JvmtiAgentSet::Iterator native_agents_iter = JvmtiAgentSet::native_agents();
  send_native_agent_events(native_agents_iter, timestamp);
  JvmtiAgentSet::Iterator xrun_agents_iter = JvmtiAgentSet::xrun_agents();
  send_native_agent_events(xrun_agents_iter, timestamp);
}

void JfrJvmtiAgentEvent::generate_java_agent_events(const Ticks& timestamp) {
  JvmtiAgentSet::Iterator agent_iter = JvmtiAgentSet::java_agents();
  while (agent_iter.has_next()) {
    const JvmtiAgent* agent = agent_iter.next();
    assert(agent->is_java_agent(), "invariant");
    ResourceMark rm;
    EventJavaAgent event;
    send_agent_event(event, agent, timestamp);
  }
}

// Disposed agents are excluded from periodic iteration. Emit one final event
// so a recording can observe disposal. For disposal during startup or VMInit,
// reporting is deferred until the startup entry point or VMInit callback returns;
// the timestamp is the report time.
//
// Agent disposal can interleave with periodic iteration, i.e. an agent
// can become disposed after iterator selection. For that situation,
// we rely on the periodic batch timestamp to preserve chronology.
void JfrJvmtiAgentEvent::generate_event_on_agent_disposal(const JvmtiAgent* agent) {
  assert(agent != nullptr, "invariant");
  assert(agent->is_disposed(), "invariant");
  if (agent->is_java_agent()) {
    if (EventJavaAgent::is_enabled()) {
      EventJavaAgent event;
      send_agent_disposal_event(event, agent);
    }
    return;
  }
  if (EventNativeAgent::is_enabled()) {
    EventNativeAgent event;
    event.set_path(agent->path());
    send_agent_disposal_event(event, agent);
  }
}
