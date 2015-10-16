/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_GLOBALS_SPARC_HPP
#define CPU_SPARC_VM_GLOBALS_SPARC_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Sets the default values for platform dependent flags used by the runtime system.
// (see globals.hpp)

// For sparc we do not do call backs when a thread is in the interpreter, because the
// interpreter dispatch needs at least two instructions - first to load the dispatch address
// in a register, and second to jmp. The swapping of the dispatch table may occur _after_
// the load of the dispatch address and hence the jmp would still go to the location
// according to the prior table. So, we let the thread continue and let it block by itself.
define_pd_global(bool, DontYieldALot,               true);  // yield no more than 100 times per second
define_pd_global(bool, ConvertSleepToYield,         false); // do not convert sleep(0) to yield. Helps GUI
define_pd_global(bool, ShareVtableStubs,            false); // improves performance markedly for mtrt and compress
define_pd_global(bool, NeedsDeoptSuspend,           true); // register window machines need this

define_pd_global(bool, ImplicitNullChecks,          true);  // Generate code for implicit null checks
define_pd_global(bool, TrapBasedNullChecks,         false); // Not needed on sparc.
define_pd_global(bool, UncommonNullCast,            true);  // Uncommon-trap NULLs passed to check cast

define_pd_global(intx, CodeEntryAlignment,    32);
// The default setting 16/16 seems to work best.
// (For _228_jack 16/16 is 2% better than 4/4, 16/4, 32/32, 32/16, or 16/32.)
define_pd_global(intx, OptoLoopAlignment,     16);  // = 4*wordSize
define_pd_global(intx, InlineFrequencyCount,  50);  // we can use more inlining on the SPARC
define_pd_global(intx, InlineSmallCode,       1500);

#define DEFAULT_STACK_YELLOW_PAGES (2)
#define DEFAULT_STACK_RED_PAGES (1)

#ifdef _LP64
// Stack slots are 2X larger in LP64 than in the 32 bit VM.
define_pd_global(intx, ThreadStackSize,       1024);
define_pd_global(intx, VMThreadStackSize,     1024);
#define DEFAULT_STACK_SHADOW_PAGES (10 DEBUG_ONLY(+1))
#else
define_pd_global(intx, ThreadStackSize,       512);
define_pd_global(intx, VMThreadStackSize,     512);
#define DEFAULT_STACK_SHADOW_PAGES (3 DEBUG_ONLY(+1))
#endif // _LP64

#define MIN_STACK_YELLOW_PAGES DEFAULT_STACK_YELLOW_PAGES
#define MIN_STACK_RED_PAGES DEFAULT_STACK_RED_PAGES
#define MIN_STACK_SHADOW_PAGES DEFAULT_STACK_SHADOW_PAGES

define_pd_global(intx, StackYellowPages, DEFAULT_STACK_YELLOW_PAGES);
define_pd_global(intx, StackRedPages, DEFAULT_STACK_RED_PAGES);
define_pd_global(intx, StackShadowPages, DEFAULT_STACK_SHADOW_PAGES);

define_pd_global(bool, RewriteBytecodes,     true);
define_pd_global(bool, RewriteFrequentPairs, true);

define_pd_global(bool, UseMembar,            false);

define_pd_global(bool, PreserveFramePointer, false);

// GC Ergo Flags
define_pd_global(size_t, CMSYoungGenPerWorker, 16*M);  // default max size of CMS young gen, per GC worker thread

define_pd_global(uintx, TypeProfileLevel, 111);

#define ARCH_FLAGS(develop, product, diagnostic, experimental, notproduct, range, constraint) \
                                                                            \
  product(intx, UseVIS, 99,                                                 \
          "Highest supported VIS instructions set on Sparc")                \
                                                                            \
  product(bool, UseCBCond, false,                                           \
          "Use compare and branch instruction on SPARC")                    \
                                                                            \
  product(bool, UseBlockZeroing, false,                                     \
          "Use special cpu instructions for block zeroing")                 \
                                                                            \
  product(intx, BlockZeroingLowLimit, 2048,                                 \
          "Minimum size in bytes when block zeroing will be used")          \
                                                                            \
  product(bool, UseBlockCopy, false,                                        \
          "Use special cpu instructions for block copy")                    \
                                                                            \
  product(intx, BlockCopyLowLimit, 2048,                                    \
          "Minimum size in bytes when block copy will be used")             \
                                                                            \
  develop(bool, UseV8InstrsOnly, false,                                     \
          "Use SPARC-V8 Compliant instruction subset")                      \
                                                                            \
  product(bool, UseNiagaraInstrs, false,                                    \
          "Use Niagara-efficient instruction subset")                       \
                                                                            \
  develop(bool, UseCASForSwap, false,                                       \
          "Do not use swap instructions, but only CAS (in a loop) on SPARC")\
                                                                            \
  product(uintx,  ArraycopySrcPrefetchDistance, 0,                          \
          "Distance to prefetch source array in arracopy")                  \
                                                                            \
  product(uintx,  ArraycopyDstPrefetchDistance, 0,                          \
          "Distance to prefetch destination array in arracopy")             \

#endif // CPU_SPARC_VM_GLOBALS_SPARC_HPP
