/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_VM_GLOBALS_X86_HPP
#define CPU_X86_VM_GLOBALS_X86_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Sets the default values for platform dependent flags used by the runtime system.
// (see globals.hpp)

define_pd_global(bool, ConvertSleepToYield,      true);
define_pd_global(bool, ShareVtableStubs,         true);
define_pd_global(bool, CountInterpCalls,         true);
define_pd_global(bool, NeedsDeoptSuspend,        false); // only register window machines need this

define_pd_global(bool, ImplicitNullChecks,       true);  // Generate code for implicit null checks
define_pd_global(bool, TrapBasedNullChecks,      false); // Not needed on x86.
define_pd_global(bool, UncommonNullCast,         true);  // Uncommon-trap NULLs passed to check cast

// See 4827828 for this change. There is no globals_core_i486.hpp. I can't
// assign a different value for C2 without touching a number of files. Use
// #ifdef to minimize the change as it's late in Mantis. -- FIXME.
// c1 doesn't have this problem because the fix to 4858033 assures us
// the the vep is aligned at CodeEntryAlignment whereas c2 only aligns
// the uep and the vep doesn't get real alignment but just slops on by
// only assured that the entry instruction meets the 5 byte size requirement.
#ifdef COMPILER2
define_pd_global(intx, CodeEntryAlignment,       32);
#else
define_pd_global(intx, CodeEntryAlignment,       16);
#endif // COMPILER2
define_pd_global(intx, OptoLoopAlignment,        16);
define_pd_global(intx, InlineFrequencyCount,     100);
define_pd_global(intx, InlineSmallCode,          1000);

define_pd_global(intx, StackYellowPages, NOT_WINDOWS(2) WINDOWS_ONLY(3));
define_pd_global(intx, StackRedPages, 1);
#ifdef AMD64
// Very large C++ stack frames using solaris-amd64 optimized builds
// due to lack of optimization caused by C++ compiler bugs
define_pd_global(intx, StackShadowPages, NOT_WIN64(20) WIN64_ONLY(6) DEBUG_ONLY(+2));
#else
define_pd_global(intx, StackShadowPages, 4 DEBUG_ONLY(+5));
#endif // AMD64

define_pd_global(intx, PreInflateSpin,           10);

define_pd_global(bool, RewriteBytecodes,     true);
define_pd_global(bool, RewriteFrequentPairs, true);

#ifdef _ALLBSD_SOURCE
define_pd_global(bool, UseMembar,            true);
#else
define_pd_global(bool, UseMembar,            false);
#endif

// GC Ergo Flags
define_pd_global(uintx, CMSYoungGenPerWorker, 64*M);  // default max size of CMS young gen, per GC worker thread

define_pd_global(uintx, TypeProfileLevel, 111);

#define ARCH_FLAGS(develop, product, diagnostic, experimental, notproduct) \
                                                                            \
  develop(bool, IEEEPrecision, true,                                        \
          "Enables IEEE precision (for INTEL only)")                        \
                                                                            \
  product(intx, FenceInstruction, 0,                                        \
          "(Unsafe,Unstable) Experimental")                                 \
                                                                            \
  product(intx,  ReadPrefetchInstr, 0,                                      \
          "Prefetch instruction to prefetch ahead")                         \
                                                                            \
  product(bool, UseStoreImmI16, true,                                       \
          "Use store immediate 16-bits value instruction on x86")           \
                                                                            \
  product(intx, UseAVX, 99,                                                 \
          "Highest supported AVX instructions set on x86/x64")              \
                                                                            \
  product(bool, UseCLMUL, false,                                            \
          "Control whether CLMUL instructions can be used on x86/x64")      \
                                                                            \
  diagnostic(bool, UseIncDec, true,                                         \
          "Use INC, DEC instructions on x86")                               \
                                                                            \
  product(bool, UseNewLongLShift, false,                                    \
          "Use optimized bitwise shift left")                               \
                                                                            \
  product(bool, UseAddressNop, false,                                       \
          "Use '0F 1F [addr]' NOP instructions on x86 cpus")                \
                                                                            \
  product(bool, UseXmmLoadAndClearUpper, true,                              \
          "Load low part of XMM register and clear upper part")             \
                                                                            \
  product(bool, UseXmmRegToRegMoveAll, false,                               \
          "Copy all XMM register bits when moving value between registers") \
                                                                            \
  product(bool, UseXmmI2D, false,                                           \
          "Use SSE2 CVTDQ2PD instruction to convert Integer to Double")     \
                                                                            \
  product(bool, UseXmmI2F, false,                                           \
          "Use SSE2 CVTDQ2PS instruction to convert Integer to Float")      \
                                                                            \
  product(bool, UseUnalignedLoadStores, false,                              \
          "Use SSE2 MOVDQU instruction for Arraycopy")                      \
                                                                            \
  product(bool, UseFastStosb, false,                                        \
          "Use fast-string operation for zeroing: rep stosb")               \
                                                                            \
  /* Use Restricted Transactional Memory for lock eliding */                \
  product(bool, UseRTMLocking, false,                                       \
          "Enable RTM lock eliding for inflated locks in compiled code")    \
                                                                            \
  experimental(bool, UseRTMForStackLocks, false,                            \
          "Enable RTM lock eliding for stack locks in compiled code")       \
                                                                            \
  product(bool, UseRTMDeopt, false,                                         \
          "Perform deopt and recompilation based on RTM abort ratio")       \
                                                                            \
  product(uintx, RTMRetryCount, 5,                                          \
          "Number of RTM retries on lock abort or busy")                    \
                                                                            \
  experimental(intx, RTMSpinLoopCount, 100,                                 \
          "Spin count for lock to become free before RTM retry")            \
                                                                            \
  experimental(intx, RTMAbortThreshold, 1000,                               \
          "Calculate abort ratio after this number of aborts")              \
                                                                            \
  experimental(intx, RTMLockingThreshold, 10000,                            \
          "Lock count at which to do RTM lock eliding without "             \
          "abort ratio calculation")                                        \
                                                                            \
  experimental(intx, RTMAbortRatio, 50,                                     \
          "Lock abort ratio at which to stop use RTM lock eliding")         \
                                                                            \
  experimental(intx, RTMTotalCountIncrRate, 64,                             \
          "Increment total RTM attempted lock count once every n times")    \
                                                                            \
  experimental(intx, RTMLockingCalculationDelay, 0,                         \
          "Number of milliseconds to wait before start calculating aborts " \
          "for RTM locking")                                                \
                                                                            \
  experimental(bool, UseRTMXendForLockBusy, true,                           \
          "Use RTM Xend instead of Xabort when lock busy")                  \
                                                                            \
  /* assembler */                                                           \
  product(bool, Use486InstrsOnly, false,                                    \
          "Use 80486 Compliant instruction subset")                         \
                                                                            \
  product(bool, UseCountLeadingZerosInstruction, false,                     \
          "Use count leading zeros instruction")                            \
                                                                            \
  product(bool, UseCountTrailingZerosInstruction, false,                    \
          "Use count trailing zeros instruction")                           \
                                                                            \
  product(bool, UseBMI1Instructions, false,                                 \
          "Use BMI instructions")

#endif // CPU_X86_VM_GLOBALS_X86_HPP
