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
  // Access to the HotSpotJVMCIRuntime used by the CompileBroker.
  static JVMCIRuntime* _compiler_runtime;

  // True when at least one JVMCIRuntime::initialize_HotSpotJVMCIRuntime()
  // execution has completed successfully.
  static volatile bool _is_initialized;

  // Handle created when loading the JVMCI shared library with os::dll_load.
  // Must hold JVMCI_lock when initializing.
  static void* _shared_library_handle;

  // Argument to os::dll_load when loading JVMCI shared library
  static char* _shared_library_path;

  // Records whether JVMCI::shutdown has been called.
  static volatile bool _in_shutdown;

  // Access to the HotSpot heap based JVMCIRuntime
  static JVMCIRuntime* _java_runtime;

 public:
  enum CodeInstallResult {
     ok,
     dependencies_failed,
     cache_full,
     code_too_large
  };

  // Gets the handle to the loaded JVMCI shared library, loading it
  // first if not yet loaded and `load` is true. The path from
  // which the library is loaded is returned in `path`. If
  // `load` is true then JVMCI_lock must be locked.
  static void* get_shared_library(char*& path, bool load);

  static void do_unloading(bool unloading_occurred);

  static void metadata_do(void f(Metadata*));

  static void shutdown();

  // Returns whether JVMCI::shutdown has been called.
  static bool in_shutdown();

  static bool is_compiler_initialized();

  /**
   * Determines if the VM is sufficiently booted to initialize JVMCI.
   */
  static bool can_initialize_JVMCI();

  static void initialize_globals();

  static void initialize_compiler(TRAPS);

  static JVMCIRuntime* compiler_runtime() { return _compiler_runtime; }
  // Gets the single runtime for JVMCI on the Java heap. This is the only
  // JVMCI runtime available when !UseJVMCINativeLibrary.
  static JVMCIRuntime* java_runtime()     { return _java_runtime; }
};

#endif // SHARE_JVMCI_JVMCI_HPP
