/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "compiler/compilerDefinitions.hpp"

const char* compilertype2name_tab[compiler_number_of_types] = {
  "",
  "c1",
  "c2",
  "jvmci",
  "shark"
};

#if defined(COMPILER2) || defined(SHARK)
CompLevel  CompLevel_highest_tier      = CompLevel_full_optimization;  // pure C2 and tiered or JVMCI and tiered
#elif defined(COMPILER1)
CompLevel  CompLevel_highest_tier      = CompLevel_simple;             // pure C1 or JVMCI
#else
CompLevel  CompLevel_highest_tier      = CompLevel_none;
#endif

#if defined(TIERED)
CompLevel  CompLevel_initial_compile   = CompLevel_full_profile;        // tiered
#elif defined(COMPILER1) || INCLUDE_JVMCI
CompLevel  CompLevel_initial_compile   = CompLevel_simple;              // pure C1 or JVMCI
#elif defined(COMPILER2) || defined(SHARK)
CompLevel  CompLevel_initial_compile   = CompLevel_full_optimization;   // pure C2
#else
CompLevel  CompLevel_initial_compile   = CompLevel_none;
#endif

#if defined(COMPILER2)
CompMode  Compilation_mode             = CompMode_server;
#elif defined(COMPILER1)
CompMode  Compilation_mode             = CompMode_client;
#else
CompMode  Compilation_mode             = CompMode_none;
#endif

#ifdef TIERED
void set_client_compilation_mode() {
  Compilation_mode = CompMode_client;
  CompLevel_highest_tier = CompLevel_simple;
  CompLevel_initial_compile = CompLevel_simple;
  FLAG_SET_ERGO(bool, TieredCompilation, false);
  FLAG_SET_ERGO(bool, ProfileInterpreter, false);
#if INCLUDE_JVMCI
  FLAG_SET_ERGO(bool, EnableJVMCI, false);
  FLAG_SET_ERGO(bool, UseJVMCICompiler, false);
#endif
#if INCLUDE_AOT
  FLAG_SET_ERGO(bool, UseAOT, false);
#endif
  if (FLAG_IS_DEFAULT(NeverActAsServerClassMachine)) {
    FLAG_SET_ERGO(bool, NeverActAsServerClassMachine, true);
  }
  if (FLAG_IS_DEFAULT(InitialCodeCacheSize)) {
    FLAG_SET_ERGO(uintx, InitialCodeCacheSize, 160*K);
  }
  if (FLAG_IS_DEFAULT(ReservedCodeCacheSize)) {
    FLAG_SET_ERGO(uintx, ReservedCodeCacheSize, 32*M);
  }
  if (FLAG_IS_DEFAULT(NonProfiledCodeHeapSize)) {
    FLAG_SET_ERGO(uintx, NonProfiledCodeHeapSize, 27*M);
  }
  if (FLAG_IS_DEFAULT(ProfiledCodeHeapSize)) {
    FLAG_SET_ERGO(uintx, ProfiledCodeHeapSize, 0);
  }
  if (FLAG_IS_DEFAULT(NonNMethodCodeHeapSize)) {
    FLAG_SET_ERGO(uintx, NonNMethodCodeHeapSize, 5*M);
  }
  if (FLAG_IS_DEFAULT(CodeCacheExpansionSize)) {
    FLAG_SET_ERGO(uintx, CodeCacheExpansionSize, 32*K);
  }
  if (FLAG_IS_DEFAULT(MetaspaceSize)) {
    FLAG_SET_ERGO(size_t, MetaspaceSize, 12*M);
  }
  if (FLAG_IS_DEFAULT(MaxRAM)) {
    // Do not use FLAG_SET_ERGO to update MaxRAM, as this will impact
    // heap setting done based on available phys_mem (see Arguments::set_heap_size).
    FLAG_SET_DEFAULT(MaxRAM, 1ULL*G);
  }
  if (FLAG_IS_DEFAULT(CompileThreshold)) {
    FLAG_SET_ERGO(intx, CompileThreshold, 1500);
  }
  if (FLAG_IS_DEFAULT(OnStackReplacePercentage)) {
    FLAG_SET_ERGO(intx, OnStackReplacePercentage, 933);
  }
  if (FLAG_IS_DEFAULT(CICompilerCount)) {
    FLAG_SET_ERGO(intx, CICompilerCount, 1);
  }
}
#endif // TIERED
