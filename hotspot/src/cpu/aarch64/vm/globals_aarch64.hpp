/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2015, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_VM_GLOBALS_AARCH64_HPP
#define CPU_AARCH64_VM_GLOBALS_AARCH64_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Sets the default values for platform dependent flags used by the runtime system.
// (see globals.hpp)

define_pd_global(bool, ConvertSleepToYield,      true);
define_pd_global(bool, ShareVtableStubs,         true);
define_pd_global(bool, NeedsDeoptSuspend,        false); // only register window machines need this

define_pd_global(bool, ImplicitNullChecks,       true);  // Generate code for implicit null checks
define_pd_global(bool, TrapBasedNullChecks,  false);
define_pd_global(bool, UncommonNullCast,         true);  // Uncommon-trap NULLs past to check cast

// See 4827828 for this change. There is no globals_core_i486.hpp. I can't
// assign a different value for C2 without touching a number of files. Use
// #ifdef to minimize the change as it's late in Mantis. -- FIXME.
// c1 doesn't have this problem because the fix to 4858033 assures us
// the the vep is aligned at CodeEntryAlignment whereas c2 only aligns
// the uep and the vep doesn't get real alignment but just slops on by
// only assured that the entry instruction meets the 5 byte size requirement.
#ifdef COMPILER2
define_pd_global(intx, CodeEntryAlignment,       64);
#else
define_pd_global(intx, CodeEntryAlignment,       16);
#endif // COMPILER2
define_pd_global(intx, OptoLoopAlignment,        16);
define_pd_global(intx, InlineFrequencyCount,     100);

#define DEFAULT_STACK_YELLOW_PAGES (2)
#define DEFAULT_STACK_RED_PAGES (1)
#define DEFAULT_STACK_SHADOW_PAGES (4 DEBUG_ONLY(+5))

#define MIN_STACK_YELLOW_PAGES 1
#define MIN_STACK_RED_PAGES    1
#define MIN_STACK_SHADOW_PAGES 1

define_pd_global(intx, StackYellowPages, DEFAULT_STACK_YELLOW_PAGES);
define_pd_global(intx, StackRedPages, DEFAULT_STACK_RED_PAGES);
define_pd_global(intx, StackShadowPages, DEFAULT_STACK_SHADOW_PAGES);

define_pd_global(bool, RewriteBytecodes,     true);
define_pd_global(bool, RewriteFrequentPairs, true);

define_pd_global(bool, UseMembar,            true);

define_pd_global(bool, PreserveFramePointer, false);

// GC Ergo Flags
define_pd_global(uintx, CMSYoungGenPerWorker, 64*M);  // default max size of CMS young gen, per GC worker thread

define_pd_global(uintx, TypeProfileLevel, 111);

// avoid biased locking while we are bootstrapping the aarch64 build
define_pd_global(bool, UseBiasedLocking, false);

#if defined(COMPILER1) || defined(COMPILER2)
define_pd_global(intx, InlineSmallCode,          1000);
#endif

#ifdef BUILTIN_SIM
#define UseBuiltinSim           true
#define ARCH_FLAGS(develop, product, diagnostic, experimental, notproduct, range, constraint) \
                                                                        \
  product(bool, NotifySimulator, UseBuiltinSim,                         \
         "tell the AArch64 sim where we are in method code")            \
                                                                        \
  product(bool, UseSimulatorCache, false,                               \
         "tell sim to cache memory updates until exclusive op occurs")  \
                                                                        \
  product(bool, DisableBCCheck, true,                                   \
          "tell sim not to invoke bccheck callback")                    \
                                                                        \
  product(bool, NearCpool, true,                                        \
         "constant pool is close to instructions")                      \
                                                                        \
  product(bool, UseBarriersForVolatile, false,                          \
          "Use memory barriers to implement volatile accesses")         \
                                                                        \
  product(bool, UseCRC32, false,                                        \
          "Use CRC32 instructions for CRC32 computation")               \

// Don't attempt to use Neon on builtin sim until builtin sim supports it
#define UseCRC32 false

#else
#define UseBuiltinSim           false
#define NotifySimulator         false
#define UseSimulatorCache       false
#define DisableBCCheck          true
#define ARCH_FLAGS(develop, product, diagnostic, experimental, notproduct, range, constraint) \
                                                                        \
  product(bool, NearCpool, true,                                        \
         "constant pool is close to instructions")                      \
                                                                        \
  product(bool, UseBarriersForVolatile, false,                          \
          "Use memory barriers to implement volatile accesses")         \
  product(bool, UseNeon, false,                                         \
          "Use Neon for CRC32 computation")                             \
  product(bool, UseCRC32, false,                                        \
          "Use CRC32 instructions for CRC32 computation")               \
  product(bool, TraceTraps, false, "Trace all traps the signal handler")

#endif


#endif // CPU_AARCH64_VM_GLOBALS_AARCH64_HPP
