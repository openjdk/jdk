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

#ifndef SHARE_PRIMS_AGENT_HPP
#define SHARE_PRIMS_AGENT_HPP

#include "memory/allocation.hpp"
#include "utilities/ticks.hpp"

// For use by -agentlib, -agentpath and -Xrun
// The terminology classifies -agentlib and -agentpath as "JVMTI agents".
// -Xrun are classified as "xrun agents" (JVMPI)
class Agent : public CHeapObj<mtServiceability> {
  friend class AgentList;
 private:
  Ticks _init;
  Tickspan _init_time;
  Agent* _next;
  const char* _name;
  const char* _options;
  void* _os_lib;
  const char* _os_lib_path;
  const void* _jplis;
  bool _valid;
  bool _is_absolute_path;
  bool _is_static_lib;
  bool _is_dynamic;
  bool _is_instrument_lib;
  bool _is_xrun;

  Agent* next() const;
  void set_jplis(const void* jplis);

 public:
  const char* name() const;
  const char* options() const;
  bool is_absolute_path() const;
  void* os_lib() const;
  void set_os_lib(void* os_lib);
  void set_os_lib_path(const char* path);
  const char* os_lib_path() const;
  bool is_static_lib() const;
  void set_static_lib();
  bool is_dynamic() const;
  bool is_instrument_lib() const;
  bool is_valid() const;
  void set_valid();
  const Ticks& initialization() const;
  const Tickspan& initialization_time() const;
  bool is_jplis() const;
  bool is_jplis(const void* jplis) const;
  bool is_timestamped() const;
  void timestamp();
  void initialization_begin();
  void initialization_end();
  Agent(const char* name, const char* options, bool is_absolute_path);
};

#endif // SHARE_PRIMS_AGENT_HPP
