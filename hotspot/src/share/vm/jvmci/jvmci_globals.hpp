/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JVMCI_JVMCIGLOBALS_HPP
#define SHARE_VM_JVMCI_JVMCIGLOBALS_HPP

#include "runtime/globals.hpp"

//
// Defines all global flags used by the JVMCI compiler. Only flags that need
// to be accessible to the JVMCI C++ code should be defined here.
//
#define JVMCI_FLAGS(develop, \
                    develop_pd, \
                    product, \
                    product_pd, \
                    diagnostic, \
                    diagnostic_pd, \
                    experimental, \
                    notproduct, \
                    range, \
                    constraint, \
                    writeable) \
                                                                            \
  experimental(bool, EnableJVMCI, false,                                    \
          "Enable JVMCI")                                                   \
                                                                            \
  experimental(bool, UseJVMCICompiler, false,                               \
          "Use JVMCI as the default compiler")                              \
                                                                            \
  experimental(bool, JVMCIPrintProperties, false,                           \
          "Prints properties used by the JVMCI compiler and exits")         \
                                                                            \
  experimental(bool, BootstrapJVMCI, false,                                 \
          "Bootstrap JVMCI before running Java main method")                \
                                                                            \
  experimental(bool, PrintBootstrap, true,                                  \
          "Print JVMCI bootstrap progress and summary")                     \
                                                                            \
  experimental(intx, JVMCIThreads, 1,                                       \
          "Force number of JVMCI compiler threads to use")                  \
          range(1, max_jint)                                                \
                                                                            \
  experimental(intx, JVMCIHostThreads, 1,                                   \
          "Force number of compiler threads for JVMCI host compiler")       \
          range(1, max_jint)                                                \
                                                                            \
  NOT_COMPILER2(product(intx, MaxVectorSize, 64,                            \
          "Max vector size in bytes, "                                      \
          "actual size could be less depending on elements type"))          \
                                                                            \
  NOT_COMPILER2(product(bool, ReduceInitialCardMarks, true,                 \
          "Defer write barriers of young objects"))                         \
                                                                            \
  experimental(intx, JVMCITraceLevel, 0,                                    \
          "Trace level for JVMCI: "                                         \
          "1 means emit a message for each CompilerToVM call,"              \
          "levels greater than 1 provide progressively greater detail")     \
                                                                            \
  experimental(intx, JVMCICounterSize, 0,                                   \
          "Reserved size for benchmark counters")                           \
          range(0, max_jint)                                                \
                                                                            \
  experimental(bool, JVMCICountersExcludeCompiler, true,                    \
          "Exclude JVMCI compiler threads from benchmark counters")         \
                                                                            \
  develop(bool, JVMCIUseFastLocking, true,                                  \
          "Use fast inlined locking code")                                  \
                                                                            \
  experimental(intx, JVMCINMethodSizeLimit, (80*K)*wordSize,                \
          "Maximum size of a compiled method.")                             \
                                                                            \
  experimental(intx, MethodProfileWidth, 0,                                 \
          "Number of methods to record in call profile")                    \
                                                                            \
  develop(bool, TraceUncollectedSpeculations, false,                        \
          "Print message when a failed speculation was not collected")


// Read default values for JVMCI globals

JVMCI_FLAGS(DECLARE_DEVELOPER_FLAG, \
            DECLARE_PD_DEVELOPER_FLAG, \
            DECLARE_PRODUCT_FLAG, \
            DECLARE_PD_PRODUCT_FLAG, \
            DECLARE_DIAGNOSTIC_FLAG, \
            DECLARE_PD_DIAGNOSTIC_FLAG, \
            DECLARE_EXPERIMENTAL_FLAG, \
            DECLARE_NOTPRODUCT_FLAG, \
            IGNORE_RANGE, \
            IGNORE_CONSTRAINT, \
            IGNORE_WRITEABLE)

class JVMCIGlobals {
 public:
  // Return true if jvmci flags are consistent. If not consistent,
  // an error message describing the inconsistency is printed before
  // returning false.
  static bool check_jvmci_flags_are_consistent();
};
#endif // SHARE_VM_JVMCI_JVMCIGLOBALS_HPP
