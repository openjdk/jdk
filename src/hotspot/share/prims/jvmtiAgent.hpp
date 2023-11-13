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

#ifndef SHARE_PRIMS_JVMTIAGENT_HPP
#define SHARE_PRIMS_JVMTIAGENT_HPP

#include "memory/allocation.hpp"
#include "utilities/ticks.hpp"

class JvmtiEnv;
class outputStream;

// Represents an agent launched on the command-line by -agentlib, -agentpath or -Xrun.
// Also agents loaded dynamically during runtime, for example using the Attach API.
class JvmtiAgent : public CHeapObj<mtServiceability> {
  friend class JvmtiAgentList;
 private:
  Ticks _initialization_time;
  Tickspan _initialization_duration;
  JvmtiAgent* _next;
  const char* _name;
  const char* _options;
  void* _os_lib;
  const char* _os_lib_path;
#ifdef AIX
  ino64_t _inode;
  dev64_t _device;
#endif
  const void* _jplis;
  bool _loaded;
  bool _absolute_path;
  bool _static_lib;
  bool _instrument_lib;
  bool _dynamic;
  bool _xrun;

  JvmtiAgent* next() const;
  void set_next(JvmtiAgent* agent);
  void convert_xrun_agent();
  void set_xrun();

 public:
  JvmtiAgent(const char* name, const char* options, bool is_absolute_path, bool dynamic = false);
  const char* name() const NOT_JVMTI_RETURN_(nullptr);
  const char* options() const;
  bool is_absolute_path() const NOT_JVMTI_RETURN_(false);
  void* os_lib() const NOT_JVMTI_RETURN_(nullptr);
  void set_os_lib(void* os_lib) NOT_JVMTI_RETURN;
  const char* os_lib_path() const;
  void set_os_lib_path(const char* path) NOT_JVMTI_RETURN;
  bool is_static_lib() const NOT_JVMTI_RETURN_(false);
  void set_static_lib() NOT_JVMTI_RETURN;
  bool is_dynamic() const;
  bool is_xrun() const;
  bool is_instrument_lib() const;
  bool is_loaded() const NOT_JVMTI_RETURN_(false);
  void set_loaded() NOT_JVMTI_RETURN;
  bool is_jplis() const;
  bool is_jplis(JvmtiEnv* env) const;
  void set_jplis(const void* jplis);
  bool is_initialized() const;
  void initialization_begin();
  void initialization_end();
  const Ticks& initialization_time() const;
  const Tickspan& initialization_duration() const;
#ifdef AIX
  void set_inode(ino64_t inode);
  void set_device(dev64_t device);
  unsigned long inode() const;
  unsigned long device() const;
#endif

  bool load(outputStream* st = nullptr);
  void unload();
};

#endif // SHARE_PRIMS_JVMTIAGENT_HPP
