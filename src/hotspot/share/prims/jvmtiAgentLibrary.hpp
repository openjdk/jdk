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

#ifndef SHARE_PRIMS_JVMTIAGENTLIBRARY_HPP
#define SHARE_PRIMS_JVMTIAGENTLIBRARY_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

// Shared library identity for agent entry points and native-method lookup.
// Agents carry references to libraries; multiple agents may share one library.
// Libraries are interned by library handle or normalized static-agent name and
// retained independently of agent lifetime.
class JvmtiAgentLibrary : public CHeapObj<mtServiceability> {
  friend class JvmtiAgentLibraryManager;
  NONCOPYABLE(JvmtiAgentLibrary);
 private:
  const void* const _handle;
  const char* const _static_name;
  const char* _path;
  bool _native_lookup_registered;
  bool _unload_registered;
  JvmtiAgentLibrary* _native_lookup_next = nullptr;

  JvmtiAgentLibrary(const void* handle, const char* static_name);

  bool match(const void* handle, const char* static_name) const NOT_JVMTI_RETURN_(false);
  bool shares_handle(const JvmtiAgentLibrary* rhs) const NOT_JVMTI_RETURN_(false);
  void* resolve(const char* name) const NOT_JVMTI_RETURN_(nullptr);

 public:
  bool is_static() const NOT_JVMTI_RETURN_(false);
  void* resolve_entry_point(const char* entry_point_name) const NOT_JVMTI_RETURN_(nullptr);

  const char* path() const NOT_JVMTI_RETURN_(nullptr);

  // Resolve and publish the library path for diagnostics.
  // Leave the path unset if it cannot be obtained; agent startup can continue.
  void initialize_path(void* entry_point) NOT_JVMTI_RETURN;
};

#endif // SHARE_PRIMS_JVMTIAGENTLIBRARY_HPP
