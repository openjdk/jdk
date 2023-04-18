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

template <typename, MEMFLAGS>
class GrowableArrayCHeap;
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
    Iterator(JvmtiAgent** list, Filter filter);
    JvmtiAgent* select(JvmtiAgent* agent) const;
   public:
    bool has_next() const;
    JvmtiAgent* next();
    const JvmtiAgent* next() const;
    ~Iterator();
  };

 private:
  static JvmtiAgent* _list;

  static Iterator all();
  static void initialize();
  static void convert_xrun_agents();

 public:
  static void add(JvmtiAgent* agent);
  static void add(const char* name, char* options, bool absolute_path);
  static void add_xrun(const char* name, char* options, bool absolute_path);

  static void load_agents();
  static jint load_agent(const char* agent, const char* absParam,
                         const char* options, outputStream* st);
  static void load_xrun_agents();
  static void unload_agents();

  static JvmtiAgent* lookup(JvmtiEnv* env, void* f_ptr);

  static Iterator agents();
  static Iterator java_agents();
  static Iterator native_agents();
  static Iterator xrun_agents();
};

#endif // SHARE_PRIMS_JVMTIAGENTLIST_HPP
