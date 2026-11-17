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

class JvmtiAgentManager;
class JvmtiAgentLibrary;

class JvmtiAgent : public CHeapObj<mtServiceability> {
  friend class JvmtiAgentManager;
  NONCOPYABLE(JvmtiAgent);

  // Startup state : invocation of the startup function.
  enum class Startup : int8_t { NotEntered, Executing, Returned };
  // Jvmti environment state : association of a jvmtiEnv.
  enum class EnvAssociation : int8_t { None, Associated, Disposed };
  // Timing state : progression of the timing record, safe to read only when Finalized.
  enum class TimingState : int8_t { Pending, Measuring, Finalized };

 private:
  const char* const _name;
  const char* const _options;
  const bool _is_absolute_path;
  const bool _instrument_lib;
  const bool _dynamic;
  bool _xrun;

  JvmtiAgentLibrary* _library;
  Startup _startup;

  JvmtiAgent* _next;
  bool _registered;

  const jvmtiEnv* _env;
  EnvAssociation _association;

  TimingState _timing;
  // Unavailable timing starts as min_jlong.
  Ticks _initialization_time;
  Tickspan _initialization_duration;

  JvmtiAgent(const char* name, const char* options, bool is_absolute_path, bool xrun = false);

  void mark_registered() NOT_JVMTI_RETURN;

  bool is_timing_pending() const NOT_JVMTI_RETURN_(false);
  bool is_timing_measuring() const NOT_JVMTI_RETURN_(false);
  void finalize_timing() NOT_JVMTI_RETURN;
  void finalize_unavailable_timing() NOT_JVMTI_RETURN;
  void finalize_timing_without_vm_init_callback() NOT_JVMTI_RETURN;

 public:
  ~JvmtiAgent();

  const char* name() const NOT_JVMTI_RETURN_(nullptr);
  const char* options() const NOT_JVMTI_RETURN_(nullptr);
  bool is_absolute_path() const NOT_JVMTI_RETURN_(false);
  bool is_dynamic() const NOT_JVMTI_RETURN_(false);
  bool is_instrument_lib() const NOT_JVMTI_RETURN_(false);
  bool is_java_agent() const NOT_JVMTI_RETURN_(false);
  bool is_xrun() const NOT_JVMTI_RETURN_(false);
  void convert_from_xrun() NOT_JVMTI_RETURN;

  JvmtiAgentLibrary* library() const NOT_JVMTI_RETURN_(nullptr);
  void set_library(JvmtiAgentLibrary* library) NOT_JVMTI_RETURN;
  bool has_library() const NOT_JVMTI_RETURN_(false);

  void begin_startup() NOT_JVMTI_RETURN;
  void end_startup() NOT_JVMTI_RETURN;
  bool has_startup_returned() const NOT_JVMTI_RETURN_(false);

  bool is_registered() const NOT_JVMTI_RETURN_(false);
  JvmtiAgent* next() const NOT_JVMTI_RETURN_(nullptr);

  bool can_associate_env() const NOT_JVMTI_RETURN_(false);
  bool is_env_associated() const NOT_JVMTI_RETURN_(false);
  bool is_env_disposed() const NOT_JVMTI_RETURN_(false);
  bool has_env() const NOT_JVMTI_RETURN_(false);
  void associate_env(const jvmtiEnv* env) NOT_JVMTI_RETURN;
  void mark_env_disposed() NOT_JVMTI_RETURN;
  bool matches_env(const jvmtiEnv* rhs_env) const NOT_JVMTI_RETURN_(false);

  void begin_initialization_timing() NOT_JVMTI_RETURN;
  void end_initialization_timing() NOT_JVMTI_RETURN;

  // Readers must observe is_timing_finalized() before accessing timing fields.
  bool is_timing_finalized() const NOT_JVMTI_RETURN_(false);
  bool has_initialization_time() const NOT_JVMTI_RETURN_(false);
  const Ticks& initialization_time() const NOT_JVMTI({ return _initialization_time; });
  const Tickspan& initialization_duration() const NOT_JVMTI({ return _initialization_duration; });
};

#endif // SHARE_PRIMS_JVMTIAGENT_HPP
