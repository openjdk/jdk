/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "compiler/compilerDefinitions.hpp"
#include "gc/shared/gcConfig.hpp"
#include "jvm.h"
#include "jvmci/jvmci.hpp"
#include "jvmci/jvmci_globals.hpp"
#include "logging/log.hpp"
#include "runtime/arguments.hpp"
#include "runtime/flags/jvmFlagAccess.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/ostream.hpp"

fileStream* JVMCIGlobals::_jni_config_file = nullptr;

// Return true if jvmci flags are consistent.
bool JVMCIGlobals::check_jvmci_flags_are_consistent() {

#ifndef PRODUCT
#define APPLY_JVMCI_FLAGS(params3, params4) \
  JVMCI_FLAGS(params4, params3, params4, params3, IGNORE_RANGE, IGNORE_CONSTRAINT)
#define JVMCI_DECLARE_CHECK4(type, name, value, ...) bool name##checked = false;
#define JVMCI_DECLARE_CHECK3(type, name, ...)        bool name##checked = false;
#define JVMCI_FLAG_CHECKED(name)                          name##checked = true;
  APPLY_JVMCI_FLAGS(JVMCI_DECLARE_CHECK3, JVMCI_DECLARE_CHECK4)
#else
#define JVMCI_FLAG_CHECKED(name)
#endif

  // Checks that a given flag is not set if a given guard flag is false.
#define CHECK_NOT_SET(FLAG, GUARD)                     \
  JVMCI_FLAG_CHECKED(FLAG)                             \
  if (!GUARD && !FLAG_IS_DEFAULT(FLAG)) {              \
    jio_fprintf(defaultStream::error_stream(),         \
        "Improperly specified VM option '%s': '%s' must be enabled\n", #FLAG, #GUARD); \
    return false;                                      \
  }

  if (EnableJVMCIProduct) {
    if (FLAG_IS_DEFAULT(UseJVMCICompiler)) {
      FLAG_SET_DEFAULT(UseJVMCICompiler, true);
    }
  }

  JVMCI_FLAG_CHECKED(UseJVMCICompiler)
  JVMCI_FLAG_CHECKED(EnableJVMCI)
  JVMCI_FLAG_CHECKED(EnableJVMCIProduct)
  JVMCI_FLAG_CHECKED(UseGraalJIT)
  JVMCI_FLAG_CHECKED(JVMCIEventLogLevel)
  JVMCI_FLAG_CHECKED(JVMCITraceLevel)
  JVMCI_FLAG_CHECKED(JVMCICounterSize)
  JVMCI_FLAG_CHECKED(JVMCICountersExcludeCompiler)
  JVMCI_FLAG_CHECKED(JVMCINMethodSizeLimit)
  JVMCI_FLAG_CHECKED(JVMCIPrintProperties)
  JVMCI_FLAG_CHECKED(JVMCIThreadsPerNativeLibraryRuntime)
  JVMCI_FLAG_CHECKED(JVMCICompilerIdleDelay)
  JVMCI_FLAG_CHECKED(UseJVMCINativeLibrary)
  JVMCI_FLAG_CHECKED(JVMCINativeLibraryThreadFraction)
  JVMCI_FLAG_CHECKED(JVMCILibPath)
  JVMCI_FLAG_CHECKED(JVMCINativeLibraryErrorFile)
  JVMCI_FLAG_CHECKED(JVMCILibDumpJNIConfig)

  CHECK_NOT_SET(BootstrapJVMCI,               UseJVMCICompiler)
  CHECK_NOT_SET(PrintBootstrap,               UseJVMCICompiler)
  CHECK_NOT_SET(JVMCIThreads,                 UseJVMCICompiler)
  CHECK_NOT_SET(JVMCIHostThreads,             UseJVMCICompiler)
  CHECK_NOT_SET(LibJVMCICompilerThreadHidden, UseJVMCICompiler)

  if (FLAG_IS_DEFAULT(UseJVMCINativeLibrary) && !UseJVMCINativeLibrary) {
    if (JVMCI::shared_library_exists()) {
      // If a JVMCI native library is present,
      // we enable UseJVMCINativeLibrary by default.
      FLAG_SET_DEFAULT(UseJVMCINativeLibrary, true);
    }
  }

  if (UseJVMCICompiler) {
    if (!UseJVMCINativeLibrary && !EnableJVMCI) {
      jio_fprintf(defaultStream::error_stream(), "Using JVMCI compiler requires -XX:+EnableJVMCI when no JVMCI shared library is available\n");
      FLAG_SET_DEFAULT(EnableJVMCI, true);

      //return false;
    }
    if (BootstrapJVMCI && UseJVMCINativeLibrary) {
      jio_fprintf(defaultStream::error_stream(), "-XX:+BootstrapJVMCI is not compatible with -XX:+UseJVMCINativeLibrary\n");
      return false;
    }
    if (BootstrapJVMCI && (TieredStopAtLevel < CompLevel_full_optimization)) {
      jio_fprintf(defaultStream::error_stream(),
          "-XX:+BootstrapJVMCI is not compatible with -XX:TieredStopAtLevel=%d\n", TieredStopAtLevel);
      return false;
    }
  }

  JVMCI_FLAG_CHECKED(EagerJVMCI)

#ifndef COMPILER2
  JVMCI_FLAG_CHECKED(EnableVectorAggressiveReboxing)
  JVMCI_FLAG_CHECKED(EnableVectorReboxing)
  JVMCI_FLAG_CHECKED(EnableVectorSupport)
  JVMCI_FLAG_CHECKED(MaxVectorSize)
  JVMCI_FLAG_CHECKED(ReduceInitialCardMarks)
  JVMCI_FLAG_CHECKED(UseMultiplyToLenIntrinsic)
  JVMCI_FLAG_CHECKED(UseSquareToLenIntrinsic)
  JVMCI_FLAG_CHECKED(UseMulAddIntrinsic)
  JVMCI_FLAG_CHECKED(UseMontgomeryMultiplyIntrinsic)
  JVMCI_FLAG_CHECKED(UseMontgomerySquareIntrinsic)
#endif // !COMPILER2
       //
  JVMCI_FLAG_CHECKED(UseVectorStubs)

#ifndef PRODUCT
#define JVMCI_CHECK4(type, name, value, ...) assert(name##checked, #name " flag not checked");
#define JVMCI_CHECK3(type, name, ...)        assert(name##checked, #name " flag not checked");
  // Ensures that all JVMCI flags are checked by this method.
  APPLY_JVMCI_FLAGS(JVMCI_CHECK3, JVMCI_CHECK4)
#undef APPLY_JVMCI_FLAGS
#undef JVMCI_DECLARE_CHECK3
#undef JVMCI_DECLARE_CHECK4
#undef JVMCI_CHECK3
#undef JVMCI_CHECK4
#undef JVMCI_FLAG_CHECKED
#endif // PRODUCT
#undef CHECK_NOT_SET

  if (JVMCILibDumpJNIConfig != nullptr) {
    _jni_config_file = new(mtJVMCI) fileStream(JVMCILibDumpJNIConfig);
    if (_jni_config_file == nullptr || !_jni_config_file->is_open()) {
      jio_fprintf(defaultStream::error_stream(),
          "Could not open file for dumping JVMCI shared library JNI config: %s\n", JVMCILibDumpJNIConfig);
      return false;
    }
  }

  return true;
}

// Convert JVMCI flags from experimental to product
bool JVMCIGlobals::enable_jvmci_product_mode(JVMFlagOrigin origin, bool use_graal_jit) {
  const char *JVMCIFlags[] = {
    "EnableJVMCI",
    "EnableJVMCIProduct",
    "UseJVMCICompiler",
    "JVMCIThreadsPerNativeLibraryRuntime",
    "JVMCICompilerIdleDelay",
    "JVMCIPrintProperties",
    "EagerJVMCI",
    "JVMCIThreads",
    "JVMCICounterSize",
    "JVMCICountersExcludeCompiler",
    "JVMCINMethodSizeLimit",
    "JVMCIEventLogLevel",
    "JVMCITraceLevel",
    "JVMCILibPath",
    "JVMCILibDumpJNIConfig",
    "UseJVMCINativeLibrary",
    "JVMCINativeLibraryThreadFraction",
    "JVMCINativeLibraryErrorFile",
    "LibJVMCICompilerThreadHidden",
    nullptr
  };

  for (int i = 0; JVMCIFlags[i] != nullptr; i++) {
    JVMFlag *jvmciFlag = (JVMFlag *)JVMFlag::find_declared_flag(JVMCIFlags[i]);
    if (jvmciFlag == nullptr) {
      return false;
    }
    jvmciFlag->clear_experimental();
    jvmciFlag->set_product();
  }

  bool value = true;
  JVMFlag *jvmciEnableFlag = JVMFlag::find_flag("EnableJVMCIProduct");
  if (JVMFlagAccess::set_bool(jvmciEnableFlag, &value, origin) != JVMFlag::SUCCESS) {
    return false;
  }
  if (use_graal_jit) {
    JVMFlag *useGraalJITFlag = JVMFlag::find_flag("UseGraalJIT");
    if (JVMFlagAccess::set_bool(useGraalJITFlag, &value, origin) != JVMFlag::SUCCESS) {
      return false;
    }
  }

  // Effect of EnableJVMCIProduct on changing defaults of
  // UseJVMCICompiler is deferred to check_jvmci_flags_are_consistent
  // so that setting these flags explicitly (e.g. on the command line)
  // takes precedence.

  return true;
}
