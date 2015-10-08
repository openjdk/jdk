/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/arguments.hpp"
#include "runtime/commandLineFlagConstraintsCompiler.hpp"
#include "runtime/commandLineFlagRangeList.hpp"
#include "runtime/globals.hpp"
#include "utilities/defaultStream.hpp"

Flag::Error AliasLevelConstraintFunc(intx value, bool verbose) {
  if ((value <= 1) && (Arguments::mode() == Arguments::_comp)) {
    CommandLineError::print(verbose,
                            "AliasLevel (" INTX_FORMAT ") is not "
                            "compatible with -Xcomp \n",
                            value);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

/**
 * Validate the minimum number of compiler threads needed to run the
 * JVM. The following configurations are possible.
 *
 * 1) The JVM is build using an interpreter only. As a result, the minimum number of
 *    compiler threads is 0.
 * 2) The JVM is build using the compiler(s) and tiered compilation is disabled. As
 *    a result, either C1 or C2 is used, so the minimum number of compiler threads is 1.
 * 3) The JVM is build using the compiler(s) and tiered compilation is enabled. However,
 *    the option "TieredStopAtLevel < CompLevel_full_optimization". As a result, only
 *    C1 can be used, so the minimum number of compiler threads is 1.
 * 4) The JVM is build using the compilers and tiered compilation is enabled. The option
 *    'TieredStopAtLevel = CompLevel_full_optimization' (the default value). As a result,
 *    the minimum number of compiler threads is 2.
 */
Flag::Error CICompilerCountConstraintFunc(intx value, bool verbose) {
  int min_number_of_compiler_threads = 0;
#if !defined(COMPILER1) && !defined(COMPILER2) && !defined(SHARK) && !INCLUDE_JVMCI
  // case 1
#else
  if (!TieredCompilation || (TieredStopAtLevel < CompLevel_full_optimization)) {
    min_number_of_compiler_threads = 1; // case 2 or case 3
  } else {
    min_number_of_compiler_threads = 2;   // case 4 (tiered)
  }
#endif

  // The default CICompilerCount's value is CI_COMPILER_COUNT.
  // With a client VM, -XX:+TieredCompilation causes TieredCompilation
  // to be true here (the option is validated later) and
  // min_number_of_compiler_threads to exceed CI_COMPILER_COUNT.
  min_number_of_compiler_threads = MIN2(min_number_of_compiler_threads, CI_COMPILER_COUNT);

  if (value < (intx)min_number_of_compiler_threads) {
    CommandLineError::print(verbose,
                            "CICompilerCount (" INTX_FORMAT ") must be "
                            "at least %d \n",
                            value, min_number_of_compiler_threads);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}
