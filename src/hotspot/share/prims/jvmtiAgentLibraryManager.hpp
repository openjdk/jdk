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

#ifndef SHARE_PRIMS_JVMTIAGENTLIBRARYMANAGER_HPP
#define SHARE_PRIMS_JVMTIAGENTLIBRARYMANAGER_HPP

#include "memory/allStatic.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

template <typename E, MemTag MT>
class GrowableArrayCHeap;

class JavaThread;
class JvmtiAgent;
class JvmtiAgentLibrary;
class outputStream;

// Manages shared agent libraries and their native-lookup and unload eligibility.
class JvmtiAgentLibraryManager : AllStatic {
  friend class NativeLookup;
 private:
  static int lookup(const void* handle, const char* static_name) NOT_JVMTI_RETURN_(0);
  static JvmtiAgentLibrary* intern(const void* handle,
                                   const char* static_name,
                                   bool& inserted) NOT_JVMTI_RETURN_(nullptr);
  static address lookup_native_entry(const char* name) NOT_JVMTI_RETURN_(nullptr);

 public:
  // Loads the agent's library or reuses an interned library.
  // On success, initial_load, if supplied, reports whether the library was newly
  // interned, allowing callers to issue dynamic-agent-loading warnings only once.
  static JvmtiAgentLibrary* load(const JvmtiAgent* agent,
                                 bool vm_exit_on_error,
                                 outputStream* st = nullptr,
                                 bool* initial_load = nullptr) NOT_JVMTI_RETURN_(nullptr);

  // Enables native-method lookup after successful agent startup.
  static void register_for_native_lookup(JvmtiAgentLibrary* library) NOT_JVMTI_RETURN;

  // Enables Agent_OnUnload notification after a startup entry point returns, including error returns.
  static void register_for_unload_notification(JvmtiAgentLibrary* library) NOT_JVMTI_RETURN;

  // Returns a snapshot of resolved Agent_OnUnload entry points, or nullptr if
  // no libraries exist. The caller owns the returned array and must delete it,
  // even if empty. Entries may be null when no unload entry point was found.
  static GrowableArrayCHeap<void*, mtServiceability>*
    Agent_OnUnload_entry_points(JavaThread* jt) NOT_JVMTI_RETURN_(nullptr);
};

#endif // SHARE_PRIMS_JVMTIAGENTLIBRARYMANAGER_HPP
