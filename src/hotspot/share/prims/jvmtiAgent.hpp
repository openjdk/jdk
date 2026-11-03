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

#ifndef SHARE_PRIMS_JVMTIAGENT_HPP
#define SHARE_PRIMS_JVMTIAGENT_HPP

#include "jvmtifiles/jvmti.h"
#include "memory/allocation.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"

class JvmtiAgentSet;
class JvmtiResolver;

class JvmtiAgent : public CHeapObj<mtServiceability> {
  friend class JvmtiAgentLifecycle;
  friend class JvmtiAgentSet;

  // Startup state : invocation of the startup function.
  enum class Startup : int8_t { NotEntered, Executing, Returned };
  // Jvmti environment state : association of a jvmtiEnv.
  enum class EnvAssociation : int8_t { None, Associated, Disposed };
  // Timing state : evolution of timing data, safe to access only under state Published.
  enum class Timing : int8_t { Unstarted, Measuring, Published };

 private:
  JvmtiAgent* _next;
  // Unavailable timing starts as min_jlong.
  Ticks _initialization_time;
  Tickspan _initialization_duration;
  const char* const _name;
  const char* const _options;
  const char* _path;
  JvmtiResolver* _resolver;
  const jvmtiEnv* _env;
  Startup _startup;
  EnvAssociation _association;
  Timing _timing;
  const bool _is_absolute_path;
  const bool _instrument_lib;
  const bool _dynamic;
  bool _xrun;
  bool _registered;
  const bool _static;

  void mark_registered() NOT_JVMTI_RETURN;

  bool is_timing_unstarted() const NOT_JVMTI_RETURN_(false);
  bool is_initializing() const NOT_JVMTI_RETURN_(false);
  void publish_timing() NOT_JVMTI_RETURN;
  void initialization_unavailable() NOT_JVMTI_RETURN;
  void initialization_without_vm_init_callback() NOT_JVMTI_RETURN;

 public:
  JvmtiAgent(const char* name, const char* options, bool is_absolute_path, bool xrun = false);
  ~JvmtiAgent();

  const char* name() const NOT_JVMTI_RETURN_(nullptr);
  const char* options() const NOT_JVMTI_RETURN_(nullptr);
  bool is_absolute_path() const NOT_JVMTI_RETURN_(false);
  bool is_dynamic() const NOT_JVMTI_RETURN_(false);
  bool is_static() const NOT_JVMTI_RETURN_(false);
  bool is_instrument_lib() const NOT_JVMTI_RETURN_(false);
  bool is_java_agent() const NOT_JVMTI_RETURN_(false);
  bool is_xrun() const NOT_JVMTI_RETURN_(false);
  void convert_from_xrun() NOT_JVMTI_RETURN;

  JvmtiResolver* resolver() const NOT_JVMTI_RETURN_(nullptr);
  void set_resolver(JvmtiResolver* resolver) NOT_JVMTI_RETURN;
  bool has_resolver() const NOT_JVMTI_RETURN_(false);
  const char* path() const NOT_JVMTI_RETURN_(nullptr);
  void set_path(const char* path) NOT_JVMTI_RETURN;
  bool has_path() const NOT_JVMTI_RETURN_(false);

  void begin_startup() NOT_JVMTI_RETURN;
  void end_startup() NOT_JVMTI_RETURN;
  bool has_startup_returned() const NOT_JVMTI_RETURN_(false);

  bool is_registered() const NOT_JVMTI_RETURN_(false);
  JvmtiAgent* next() const NOT_JVMTI_RETURN_(nullptr);

  bool can_associate() const NOT_JVMTI_RETURN_(false);
  bool is_associated() const NOT_JVMTI_RETURN_(false);
  bool is_disposed() const NOT_JVMTI_RETURN_(false);
  bool has_env() const NOT_JVMTI_RETURN_(false);
  void set_env(const jvmtiEnv* env) NOT_JVMTI_RETURN;
  void clear_env() NOT_JVMTI_RETURN;
  bool equals(const jvmtiEnv* rhs_env) const NOT_JVMTI_RETURN_(false);

  void initialization_begin() NOT_JVMTI_RETURN;
  void initialization_end() NOT_JVMTI_RETURN;

  // Readers must observe is_timing_published() before accessing timing fields.
  bool is_timing_published() const NOT_JVMTI_RETURN_(false);
  bool has_initialization_time() const NOT_JVMTI_RETURN_(false);
  const Ticks& initialization_time() const NOT_JVMTI({ return _initialization_time; });
  const Tickspan& initialization_duration() const NOT_JVMTI({ return _initialization_duration; });
};

#endif // SHARE_PRIMS_JVMTIAGENT_HPP
