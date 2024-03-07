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
 *
 */

#ifndef SHARE_PRIMS_JVMTIAGENTLIST_HPP
#define SHARE_PRIMS_JVMTIAGENTLIST_HPP

#include "memory/allocation.hpp"
#include "prims/jvmtiAgent.hpp"
#include "utilities/growableArray.hpp"

class JvmtiEnv;

// Maintains a single cas linked-list of JvmtiAgents.
class JvmtiAgentList : AllStatic {
  friend class Iterator;
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
    GrowableArrayCHeap<JvmtiAgent*, mtServiceability>* _stack;
    const Filter _filter;
    Iterator() : _stack(nullptr), _filter(ALL) {}
    Iterator(JvmtiAgent** list, Filter filter);
    JvmtiAgent* select(JvmtiAgent* agent) const;
   public:
    bool has_next() const NOT_JVMTI_RETURN_(false);
    JvmtiAgent* next() NOT_JVMTI_RETURN_(nullptr);
    const JvmtiAgent* next() const NOT_JVMTI_RETURN_(nullptr);
    ~Iterator() { delete _stack; }
  };

 private:
  static JvmtiAgent* _list;

  static Iterator all();
  static void initialize();
  static void convert_xrun_agents();

 public:
  static void add(JvmtiAgent* agent) NOT_JVMTI_RETURN;
  static void add(const char* name, char* options, bool absolute_path) NOT_JVMTI_RETURN;
  static void add_xrun(const char* name, char* options, bool absolute_path) NOT_JVMTI_RETURN;

  static void load_agents() NOT_JVMTI_RETURN;
  static jint load_agent(const char* agent, const char* absParam,
                         const char* options, outputStream* st) NOT_JVMTI_RETURN_(0);
  static void load_xrun_agents() NOT_JVMTI_RETURN;
  static void unload_agents() NOT_JVMTI_RETURN;

  static bool is_static_lib_loaded(const char* name);
  static bool is_dynamic_lib_loaded(void* os_lib);

  static JvmtiAgent* lookup(JvmtiEnv* env, void* f_ptr);

  static Iterator agents() NOT_JVMTI({ Iterator it; return it; });
  static Iterator java_agents();
  static Iterator native_agents();
  static Iterator xrun_agents();
};

#endif // SHARE_PRIMS_JVMTIAGENTLIST_HPP
