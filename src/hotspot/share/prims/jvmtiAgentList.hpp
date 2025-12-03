/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_PRIMS_JVMTIAGENTLIST_HPP
#define SHARE_PRIMS_JVMTIAGENTLIST_HPP

#include "prims/jvmtiAgent.hpp"

class JvmtiEnv;

// Maintains thread-safe linked list of JvmtiAgents.
class JvmtiAgentList : AllStatic {
  friend class JvmtiExport;
 public:
  class Iterator {
    friend class JvmtiAgentList;
   private:
    enum Filter {
      JAVA,
      NATIVE,
      XRUN,
      NOT_XRUN,
      ALL
    };
    const Filter _filter;
    JvmtiAgent* _next;
    Iterator(): _filter(ALL), _next(nullptr) {}
    Iterator(JvmtiAgent* head, Filter filter);
    JvmtiAgent* select(JvmtiAgent* agent) const;
   public:
    bool has_next() const NOT_JVMTI_RETURN_(false);
    JvmtiAgent* next() NOT_JVMTI_RETURN_(nullptr);
  };

 private:
  static JvmtiAgent* _head;

  static JvmtiAgent* head();

  static void initialize();
  static void convert_xrun_agents();

  static void add(JvmtiAgent* agent) NOT_JVMTI_RETURN;

 public:
  static void add(const char* name, const char* options, bool absolute_path) NOT_JVMTI_RETURN;
  static void add_xrun(const char* name, const char* options, bool absolute_path) NOT_JVMTI_RETURN;

  static void load_agents() NOT_JVMTI_RETURN;
  static void load_agent(const char* agent, bool is_absolute_path,
                         const char* options, outputStream* st) NOT_JVMTI_RETURN;
  static void load_xrun_agents() NOT_JVMTI_RETURN;
  static void unload_agents() NOT_JVMTI_RETURN;

  static bool is_static_lib_loaded(const char* name);
  static bool is_dynamic_lib_loaded(void* os_lib);

  static JvmtiAgent* lookup(JvmtiEnv* env, void* f_ptr);

  static Iterator all();
  static Iterator agents() NOT_JVMTI({ Iterator it; return it; });
  static Iterator java_agents();
  static Iterator native_agents();
  static Iterator xrun_agents();
  static bool disable_agent_list() NOT_JVMTI_RETURN_(false);
};

#endif // SHARE_PRIMS_JVMTIAGENTLIST_HPP
