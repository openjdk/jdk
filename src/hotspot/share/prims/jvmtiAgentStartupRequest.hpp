/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_PRIMS_JVMTIAGENTSTARTUPREQUEST_HPP
#define SHARE_PRIMS_JVMTIAGENTSTARTUPREQUEST_HPP

#include "jvmtifiles/jvmti.h"
#include "memory/allocation.hpp"
#include "utilities/macros.hpp"

class JvmtiAgent;
class Thread;

// A startup request is bound to a scope to support nested startup invocations.
class JvmtiAgentStartupRequest : StackObj {
  friend class JvmtiAgentManager;
 private:
  JvmtiAgent* const _agent;
  Thread* const _thread;
  NONCOPYABLE(JvmtiAgentStartupRequest);

  JvmtiAgentStartupRequest(JvmtiAgent* agent);
  ~JvmtiAgentStartupRequest();

  static void initialize() NOT_JVMTI_RETURN;
  static bool is_initialized() NOT_JVMTI_RETURN_(false);

  static void on_env_creation(const jvmtiEnv* env) NOT_JVMTI_RETURN;
  static JvmtiAgent* find(const jvmtiEnv* env) NOT_JVMTI_RETURN_(nullptr);
};

#endif // SHARE_PRIMS_JVMTIAGENTSTARTUPREQUEST_HPP
