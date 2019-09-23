/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_JVMCI_JVMCI_HPP
#define SHARE_JVMCI_JVMCI_HPP

#include "compiler/compilerDefinitions.hpp"
#include "utilities/exceptions.hpp"

class BoolObjectClosure;
class constantPoolHandle;
class JavaThread;
class JVMCIEnv;
class JVMCIRuntime;
class Metadata;
class MetadataHandleBlock;
class OopClosure;
class OopStorage;

struct _jmetadata;
typedef struct _jmetadata *jmetadata;

class JVMCI : public AllStatic {
  friend class JVMCIRuntime;
  friend class JVMCIEnv;

 private:
  // Handles to Metadata objects.
  static MetadataHandleBlock* _metadata_handles;

  // Access to the HotSpotJVMCIRuntime used by the CompileBroker.
  static JVMCIRuntime* _compiler_runtime;

  // Access to the HotSpotJVMCIRuntime used by Java code running on the
  // HotSpot heap. It will be the same as _compiler_runtime if
  // UseJVMCINativeLibrary is false
  static JVMCIRuntime* _java_runtime;

 public:
  enum CodeInstallResult {
     ok,
     dependencies_failed,
     cache_full,
     code_too_large
  };

  static void do_unloading(bool unloading_occurred);

  static void metadata_do(void f(Metadata*));

  static void shutdown();

  static bool shutdown_called();

  static bool is_compiler_initialized();

  /**
   * Determines if the VM is sufficiently booted to initialize JVMCI.
   */
  static bool can_initialize_JVMCI();

  static void initialize_globals();

  static void initialize_compiler(TRAPS);

  static jobject make_global(const Handle& obj);
  static void destroy_global(jobject handle);
  static bool is_global_handle(jobject handle);

  static jmetadata allocate_handle(const methodHandle& handle);
  static jmetadata allocate_handle(const constantPoolHandle& handle);

  static void release_handle(jmetadata handle);

  static JVMCIRuntime* compiler_runtime() { return _compiler_runtime; }
  static JVMCIRuntime* java_runtime()     { return _java_runtime; }
};

#endif // SHARE_JVMCI_JVMCI_HPP
