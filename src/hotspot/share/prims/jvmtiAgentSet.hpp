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

#ifndef SHARE_PRIMS_JVMTIAGENTSET_HPP
#define SHARE_PRIMS_JVMTIAGENTSET_HPP

#include "jvmtifiles/jvmti.h"
#include "memory/allStatic.hpp"
#include "utilities/macros.hpp"

class JvmtiAgent;
class outputStream;

class JvmtiAgentSet : AllStatic {
  friend class JvmtiExport;
 private:

  static void initialization_begin();
  static void add(JvmtiAgent* agent);
  static void report_disposal(JvmtiAgent* agent);
  static JvmtiAgent* find(const jvmtiEnv* env);
  static JvmtiAgent* find_vm_init_agent(const jvmtiEnv* env);
  static bool startup(JvmtiAgent* agent, outputStream* st = nullptr);
 public:
  static void on_argument_parse(const char* name,
                                const char* options,
                                bool is_absolute_path,
                                bool xrun = false) NOT_JVMTI_RETURN;

  static void startup_agent(const char* name,
                            bool is_absolute_path,
                            const char* options,
                            outputStream* st) NOT_JVMTI_RETURN;

  static void startup_agents() NOT_JVMTI_RETURN;
  static void startup_xrun_agents() NOT_JVMTI_RETURN;
  static void disable_agent_list() NOT_JVMTI_RETURN;
  static void print(outputStream* st) NOT_JVMTI_RETURN;

  static void on_env_creation(const jvmtiEnv* env) NOT_JVMTI_RETURN;
  static void on_env_disposal(const jvmtiEnv* env) NOT_JVMTI_RETURN;

  static JvmtiAgent* initialization_begin(const jvmtiEnv* env) NOT_JVMTI_RETURN_(nullptr);
  static void initialization_end(JvmtiAgent* agent) NOT_JVMTI_RETURN;

  // Our candidate set includes all JvmtiAgent instances, but instances need not be retained.
  // For instances that are retained, the supported subsets are as follows:
  // JAVA and NATIVE partition live REGISTERED agents; XRUN is disjoint from REGISTERED.
  // JAVA and NATIVE iterators exclude disposed agents; REGISTERED and XRUN do not.
  enum Subset {
    REGISTERED,
    JAVA,
    NATIVE,
    XRUN
  };

  class Iterator {
    friend class JvmtiAgentSet;
   private:
    const Subset _subset;
    mutable int _idx;
    mutable JvmtiAgent* _next;
    JvmtiAgent* internal_next() const;
    JvmtiAgent* initial_element() const;
    Iterator(Subset subset);
    Iterator();
   public:
    bool has_next() const NOT_JVMTI_RETURN_(false);
    JvmtiAgent* next() const NOT_JVMTI_RETURN_(nullptr);
  };

  static Iterator agents() NOT_JVMTI({ Iterator it; return it; });
  static Iterator java_agents() NOT_JVMTI({ Iterator it; return it; });
  static Iterator native_agents() NOT_JVMTI({ Iterator it; return it; });
  static Iterator xrun_agents() NOT_JVMTI({ Iterator it; return it; });

 private:
  static void startup();
  static void startup_xrun();
};

#endif // SHARE_PRIMS_JVMTIAGENTSET_HPP
