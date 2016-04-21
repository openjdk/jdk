/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
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

define_pd_global(bool, ShareVtableStubs,         true);
define_pd_global(bool, NeedsDeoptSuspend,        false); // only register window machines need this

define_pd_global(bool, ImplicitNullChecks,       true);  // Generate code for implicit null checks
define_pd_global(bool, TrapBasedNullChecks,  false);
define_pd_global(bool, UncommonNullCast,         true);  // Uncommon-trap NULLs past to check cast

define_pd_global(intx, CodeEntryAlignment,       64);
define_pd_global(intx, OptoLoopAlignment,        16);
define_pd_global(intx, InlineFrequencyCount,     100);

#define DEFAULT_STACK_YELLOW_PAGES (2)
#define DEFAULT_STACK_RED_PAGES (1)
#define DEFAULT_STACK_SHADOW_PAGES (4 DEBUG_ONLY(+5))
#define DEFAULT_STACK_RESERVED_PAGES (0)

#define MIN_STACK_YELLOW_PAGES 1
#define MIN_STACK_RED_PAGES    1
#define MIN_STACK_SHADOW_PAGES 1
#define MIN_STACK_RESERVED_PAGES (0)

define_pd_global(intx, StackYellowPages, DEFAULT_STACK_YELLOW_PAGES);
define_pd_global(intx, StackRedPages, DEFAULT_STACK_RED_PAGES);
define_pd_global(intx, StackShadowPages, DEFAULT_STACK_SHADOW_PAGES);
define_pd_global(intx, StackReservedPages, DEFAULT_STACK_RESERVED_PAGES);

define_pd_global(bool, RewriteBytecodes,     true);
define_pd_global(bool, RewriteFrequentPairs, true);

define_pd_global(bool, UseMembar,            true);

define_pd_global(bool, PreserveFramePointer, false);

// GC Ergo Flags
define_pd_global(uintx, CMSYoungGenPerWorker, 64*M);  // default max size of CMS young gen, per GC worker thread

define_pd_global(uintx, TypeProfileLevel, 111);

// No performance work done here yet.
define_pd_global(bool, CompactStrings, false);

// avoid biased locking while we are bootstrapping the aarch64 build
define_pd_global(bool, UseBiasedLocking, false);

// Clear short arrays bigger than one word in an arch-specific way
define_pd_global(intx, InitArrayShortSize, BytesPerLong);

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
                                                                        \
  product(bool, UseLSE, false,                                          \
          "Use LSE instructions")                                       \

// Don't attempt to use Neon on builtin sim until builtin sim supports it
#define UseCRC32 false
#define UseSIMDForMemoryOps    false

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
  product(bool, UseSIMDForMemoryOps, false,                             \
          "Use SIMD instructions in generated memory move code")        \
  product(bool, UseLSE, false,                                          \
          "Use LSE instructions")                                       \
  product(bool, TraceTraps, false, "Trace all traps the signal handler")

#endif


#endif // CPU_AARCH64_VM_GLOBALS_AARCH64_HPP
