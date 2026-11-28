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

#ifndef SHARE_PRIMS_JVMTIAGENTMANAGER_HPP
#define SHARE_PRIMS_JVMTIAGENTMANAGER_HPP

#include "memory/allStatic.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"

class JavaThread;
class JvmtiAgent;
class JvmtiEnvBase;
class outputStream;

class JvmtiAgentManager : AllStatic {
  friend class JvmtiExport;
 private:
  static void add(JvmtiAgent* agent);

  static void finalize_pending_initialization_timing();

  enum class StartResult { Retained, Discarded };
  static StartResult start(JvmtiAgent* agent, outputStream* st = nullptr);
  static void start();

 public:
  static void on_argument_parse(const char* name,
                                const char* options,
                                bool is_absolute_path,
                                bool xrun = false) NOT_JVMTI_RETURN;
  static void disable_agent_list() NOT_JVMTI_RETURN;

  // Our candidate set includes all JvmtiAgent instances, but instances need not be retained.
  // For instances that are retained, the supported selections are as follows:
  // LIVE_JAVA and LIVE_NATIVE partition live REGISTERED agents; XRUN is disjoint from REGISTERED.
  // LIVE_JAVA and LIVE_NATIVE iterators exclude disposed agents; REGISTERED and XRUN do not.
  enum Selection {
    REGISTERED,
    LIVE_JAVA,
    LIVE_NATIVE,
    XRUN
  };

  class Iterator {
    friend class JvmtiAgentManager;
   private:
    const Selection _selection;
    mutable int _idx;
    mutable JvmtiAgent* _next;
    JvmtiAgent* internal_next() const;
    JvmtiAgent* initial_element() const;
    Iterator(Selection selection);
    Iterator();
   public:
    bool has_next() const NOT_JVMTI_RETURN_(false);
    JvmtiAgent* next() const NOT_JVMTI_RETURN_(nullptr);
  };

  static Iterator agents() NOT_JVMTI({ Iterator it; return it; });
  static Iterator java_agents() NOT_JVMTI({ Iterator it; return it; });
  static Iterator native_agents() NOT_JVMTI({ Iterator it; return it; });
  static Iterator xrun_agents() NOT_JVMTI({ Iterator it; return it; });

  static void on_env_creation(JavaThread* thread, JvmtiEnvBase* env) NOT_JVMTI_RETURN;
  static void on_env_disposal(JvmtiAgent* agent) NOT_JVMTI_RETURN;

  static JvmtiAgent* prepare_vm_init_timing(const JvmtiEnvBase* env) NOT_JVMTI_RETURN_(nullptr);
  static void finish_vm_init_timing(JvmtiAgent* agent, bool callback_completed,
                                    const Ticks& start, const Ticks& end) NOT_JVMTI_RETURN;

  static void start_agents() NOT_JVMTI_RETURN;
  static void start_xrun_agents() NOT_JVMTI_RETURN;
  static void start_agent(const char* name,
                          bool is_absolute_path,
                          const char* options,
                          outputStream* st) NOT_JVMTI_RETURN;

  static void invoke_Agent_OnUnload_entry_points(JavaThread* jt) NOT_JVMTI_RETURN;

  static void print(outputStream* st) NOT_JVMTI_RETURN;
};

#endif // SHARE_PRIMS_JVMTIAGENTMANAGER_HPP
