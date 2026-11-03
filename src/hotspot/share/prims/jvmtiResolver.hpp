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

#ifndef SHARE_PRIMS_JVMTIRESOLVER_HPP
#define SHARE_PRIMS_JVMTIRESOLVER_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

template <typename E, MemTag MT>
class GrowableArrayCHeap;

class JavaThread;

// Shared resolution identity for agent entry points and native-method lookup.
// Agents carry references to resolvers; multiple agents may share one resolver.
// Resolvers are interned by library handle or normalized static-agent name and
// retained independently of agent lifetime.
//
// Native-method lookup requires successful startup, while unload eligibility
// also includes agents whose startup entry point returned an error.
//
// intern() reports whether a resolver was newly inserted, allowing the caller
// to distinguish first encounters from repeated loads when deciding whether
// to issue a dynamic-agent-loading warning.
class JvmtiResolver : public CHeapObj<mtServiceability> {
  friend class JvmtiAgentLifecycle;
  friend class JvmtiNativeLookupIterator;
  friend class NativeLookup;
  NONCOPYABLE(JvmtiResolver);
 private:
  const void* const _handle;
  const char* const _static_name;
  bool _native_lookup_registered;
  bool _unload_registered;

  JvmtiResolver(const void* handle, const char* static_name);
  bool match(const void* handle, const char* static_name) const NOT_JVMTI_RETURN_(false);
  bool is_registered_for_native_lookup() const NOT_JVMTI_RETURN_(false);
  bool shares_handle(const JvmtiResolver* rhs) const NOT_JVMTI_RETURN_(false);
  void* resolve(const char* name) const NOT_JVMTI_RETURN_(nullptr);
  void register_for_native_resolution() NOT_JVMTI_RETURN;
  void register_for_unload() NOT_JVMTI_RETURN;
  static int lookup(const void* handle, const char* static_name) NOT_JVMTI_RETURN_(0);
  static address lookup_native_entry(const char* name) NOT_JVMTI_RETURN_(nullptr);

 public:
  bool is_static() const NOT_JVMTI_RETURN_(false);
  bool is_registered_for_unload() const NOT_JVMTI_RETURN_(false);
  void* resolve_entry_point(const char* entry_point_name) const NOT_JVMTI_RETURN_(nullptr);

  static JvmtiResolver* intern(const void* handle,
                               const char* agent_name,
                               bool is_absolute_path,
                               bool is_static,
                               bool* inserted = nullptr) NOT_JVMTI_RETURN_(nullptr);

  static bool has_static_entry_point(const char* agent_name,
                                     bool is_absolute_path,
                                     const char* entry_point_name) NOT_JVMTI_RETURN_(false);

  // Returns a snapshot of resolved Agent_OnUnload entry points, or nullptr if
  // no resolvers exist. The caller owns the returned array and must delete it,
  // even if empty. Entries may be null when no unload entry point was found.
  static GrowableArrayCHeap<void*, mtServiceability>*
    Agent_OnUnload_entry_points(JavaThread* jt) NOT_JVMTI_RETURN_(nullptr);
};

#endif // SHARE_PRIMS_JVMTIRESOLVER_HPP
