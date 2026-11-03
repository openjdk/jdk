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

#ifndef SHARE_PRIMS_JVMTIAGENTLIFECYCLE_HPP
#define SHARE_PRIMS_JVMTIAGENTLIFECYCLE_HPP

#include "memory/allStatic.hpp"
#include "utilities/macros.hpp"

class JavaThread;
class JvmtiAgent;
class outputStream;

class JvmtiAgentLifecycle : AllStatic {
  friend class JvmtiAgentSet;
 private:
  static void register_for_native_resolution(const JvmtiAgent* agent) NOT_JVMTI_RETURN;
  static void register_for_unload(const JvmtiAgent* agent) NOT_JVMTI_RETURN;

  static bool invoke_JVM_OnLoad(JvmtiAgent* agent) NOT_JVMTI_RETURN_(false);
  static bool invoke_Agent_OnLoad(JvmtiAgent* agent) NOT_JVMTI_RETURN_(false);
  static bool invoke_Agent_OnAttach(JvmtiAgent* agent,
                                    outputStream* st) NOT_JVMTI_RETURN_(false);

  static bool locate_and_invoke_Agent_OnLoad(JvmtiAgent* agent) NOT_JVMTI_RETURN_(false);
  static bool locate_and_invoke_Agent_OnAttach(JvmtiAgent* agent,
                                               outputStream* st,
                                               bool initial_load) NOT_JVMTI_RETURN_(false);
 public:
  static bool is_static(const JvmtiAgent* agent) NOT_JVMTI_RETURN_(false);
  static void attempt_xrun_conversion(JvmtiAgent* agent) NOT_JVMTI_RETURN;
  static bool startup(JvmtiAgent* agent, outputStream* st = nullptr) NOT_JVMTI_RETURN_(false);
  static void invoke_Agent_OnUnload_entry_points(JavaThread* jt) NOT_JVMTI_RETURN;
};

#endif // SHARE_PRIMS_JVMTIAGENTLIFECYCLE_HPP
