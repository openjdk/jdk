/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCache.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "compiler/compilerDefinitions.hpp"
#include "gc/shared/gcConfig.hpp"
#include "utilities/defaultStream.hpp"

const char* compilertype2name_tab[compiler_number_of_types] = {
  "",
  "c1",
  "c2",
  "jvmci"
};

#ifdef TIERED
bool CompilationModeFlag::_quick_only = false;
bool CompilationModeFlag::_high_only = false;
bool CompilationModeFlag::_high_only_quick_internal = false;


bool CompilationModeFlag::initialize() {
  if (CompilationMode != NULL) {
    if (strcmp(CompilationMode, "default") == 0) {
      // Do nothing, just support the "default" keyword.
    } else if (strcmp(CompilationMode, "quick-only") == 0) {
      _quick_only = true;
    } else if (strcmp(CompilationMode, "high-only") == 0) {
      _high_only = true;
    } else if (strcmp(CompilationMode, "high-only-quick-internal") == 0) {
      _high_only_quick_internal = true;
    } else {
      jio_fprintf(defaultStream::error_stream(), "Unsupported compilation mode '%s', supported modes are: quick-only, high-only, high-only-quick-internal\n", CompilationMode);
      return false;
    }
  }
  return true;
}

#endif

#if defined(COMPILER2)
CompLevel  CompLevel_highest_tier      = CompLevel_full_optimization;  // pure C2 and tiered or JVMCI and tiered
#elif defined(COMPILER1)
CompLevel  CompLevel_highest_tier      = CompLevel_simple;             // pure C1 or JVMCI
#else
CompLevel  CompLevel_highest_tier      = CompLevel_none;
#endif

#if defined(COMPILER2)
CompMode  Compilation_mode             = CompMode_server;
#elif defined(COMPILER1)
CompMode  Compilation_mode             = CompMode_client;
#else
CompMode  Compilation_mode             = CompMode_none;
#endif

// Returns threshold scaled with CompileThresholdScaling
intx CompilerConfig::scaled_compile_threshold(intx threshold) {
  return scaled_compile_threshold(threshold, CompileThresholdScaling);
}

// Returns freq_log scaled with CompileThresholdScaling
intx CompilerConfig::scaled_freq_log(intx freq_log) {
  return scaled_freq_log(freq_log, CompileThresholdScaling);
}

// Returns threshold scaled with the value of scale.
// If scale < 0.0, threshold is returned without scaling.
intx CompilerConfig::scaled_compile_threshold(intx threshold, double scale) {
  if (scale == 1.0 || scale < 0.0) {
    return threshold;
  } else {
    return (intx)(threshold * scale);
  }
}

// Returns freq_log scaled with the value of scale.
// Returned values are in the range of [0, InvocationCounter::number_of_count_bits + 1].
// If scale < 0.0, freq_log is returned without scaling.
intx CompilerConfig::scaled_freq_log(intx freq_log, double scale) {
  // Check if scaling is necessary or if negative value was specified.
  if (scale == 1.0 || scale < 0.0) {
    return freq_log;
  }
  // Check values to avoid calculating log2 of 0.
  if (scale == 0.0 || freq_log == 0) {
    return 0;
  }
  // Determine the maximum notification frequency value currently supported.
  // The largest mask value that the interpreter/C1 can handle is
  // of length InvocationCounter::number_of_count_bits. Mask values are always
  // one bit shorter then the value of the notification frequency. Set
  // max_freq_bits accordingly.
  intx max_freq_bits = InvocationCounter::number_of_count_bits + 1;
  intx scaled_freq = scaled_compile_threshold((intx)1 << freq_log, scale);
  if (scaled_freq == 0) {
    // Return 0 right away to avoid calculating log2 of 0.
    return 0;
  } else if (scaled_freq > nth_bit(max_freq_bits)) {
    return max_freq_bits;
  } else {
    return log2_intptr(scaled_freq);
  }
}

#ifdef TIERED
void set_client_compilation_mode() {
  Compilation_mode = CompMode_client;
  CompLevel_highest_tier = CompLevel_simple;
  FLAG_SET_ERGO(TieredCompilation, false);
  FLAG_SET_ERGO(ProfileInterpreter, false);
#if INCLUDE_JVMCI
  FLAG_SET_ERGO(EnableJVMCI, false);
  FLAG_SET_ERGO(UseJVMCICompiler, false);
#endif
#if INCLUDE_AOT
  FLAG_SET_ERGO(UseAOT, false);
#endif
  if (FLAG_IS_DEFAULT(NeverActAsServerClassMachine)) {
    FLAG_SET_ERGO(NeverActAsServerClassMachine, true);
  }
  if (FLAG_IS_DEFAULT(InitialCodeCacheSize)) {
    FLAG_SET_ERGO(InitialCodeCacheSize, 160*K);
  }
  if (FLAG_IS_DEFAULT(ReservedCodeCacheSize)) {
    FLAG_SET_ERGO(ReservedCodeCacheSize, 32*M);
  }
  if (FLAG_IS_DEFAULT(NonProfiledCodeHeapSize)) {
    FLAG_SET_ERGO(NonProfiledCodeHeapSize, 27*M);
  }
  if (FLAG_IS_DEFAULT(ProfiledCodeHeapSize)) {
    FLAG_SET_ERGO(ProfiledCodeHeapSize, 0);
  }
  if (FLAG_IS_DEFAULT(NonNMethodCodeHeapSize)) {
    FLAG_SET_ERGO(NonNMethodCodeHeapSize, 5*M);
  }
  if (FLAG_IS_DEFAULT(CodeCacheExpansionSize)) {
    FLAG_SET_ERGO(CodeCacheExpansionSize, 32*K);
  }
  if (FLAG_IS_DEFAULT(MetaspaceSize)) {
    FLAG_SET_ERGO(MetaspaceSize, MIN2(12*M, MaxMetaspaceSize));
  }
  if (FLAG_IS_DEFAULT(MaxRAM)) {
    // Do not use FLAG_SET_ERGO to update MaxRAM, as this will impact
    // heap setting done based on available phys_mem (see Arguments::set_heap_size).
    FLAG_SET_DEFAULT(MaxRAM, 1ULL*G);
  }
  if (FLAG_IS_DEFAULT(CompileThreshold)) {
    FLAG_SET_ERGO(CompileThreshold, 1500);
  }
  if (FLAG_IS_DEFAULT(OnStackReplacePercentage)) {
    FLAG_SET_ERGO(OnStackReplacePercentage, 933);
  }
  if (FLAG_IS_DEFAULT(CICompilerCount)) {
    FLAG_SET_ERGO(CICompilerCount, 1);
  }
}

bool compilation_mode_selected() {
  return !FLAG_IS_DEFAULT(TieredCompilation) ||
         !FLAG_IS_DEFAULT(TieredStopAtLevel) ||
         !FLAG_IS_DEFAULT(UseAOT)
         JVMCI_ONLY(|| !FLAG_IS_DEFAULT(EnableJVMCI)
                    || !FLAG_IS_DEFAULT(UseJVMCICompiler));
}

void select_compilation_mode_ergonomically() {
#if defined(_WINDOWS) && !defined(_LP64)
  if (FLAG_IS_DEFAULT(NeverActAsServerClassMachine)) {
    FLAG_SET_ERGO(NeverActAsServerClassMachine, true);
  }
#endif
  if (NeverActAsServerClassMachine) {
    set_client_compilation_mode();
  }
}


void CompilerConfig::set_tiered_flags() {
  // Increase the code cache size - tiered compiles a lot more.
  if (FLAG_IS_DEFAULT(ReservedCodeCacheSize)) {
    FLAG_SET_ERGO(ReservedCodeCacheSize,
                  MIN2(CODE_CACHE_DEFAULT_LIMIT, (size_t)ReservedCodeCacheSize * 5));
  }
  // Enable SegmentedCodeCache if TieredCompilation is enabled, ReservedCodeCacheSize >= 240M
  // and the code cache contains at least 8 pages (segmentation disables advantage of huge pages).
  if (FLAG_IS_DEFAULT(SegmentedCodeCache) && ReservedCodeCacheSize >= 240*M &&
      8 * CodeCache::page_size() <= ReservedCodeCacheSize) {
    FLAG_SET_ERGO(SegmentedCodeCache, true);
  }
  if (!UseInterpreter) { // -Xcomp
    Tier3InvokeNotifyFreqLog = 0;
    Tier4InvocationThreshold = 0;
  }

  if (CompileThresholdScaling < 0) {
    vm_exit_during_initialization("Negative value specified for CompileThresholdScaling", NULL);
  }

  if (CompilationModeFlag::disable_intermediate()) {
    if (FLAG_IS_DEFAULT(Tier0ProfilingStartPercentage)) {
      FLAG_SET_DEFAULT(Tier0ProfilingStartPercentage, 33);
    }
  }

  // Scale tiered compilation thresholds.
  // CompileThresholdScaling == 0.0 is equivalent to -Xint and leaves compilation thresholds unchanged.
  if (!FLAG_IS_DEFAULT(CompileThresholdScaling) && CompileThresholdScaling > 0.0) {
    FLAG_SET_ERGO(Tier0InvokeNotifyFreqLog, scaled_freq_log(Tier0InvokeNotifyFreqLog));
    FLAG_SET_ERGO(Tier0BackedgeNotifyFreqLog, scaled_freq_log(Tier0BackedgeNotifyFreqLog));

    FLAG_SET_ERGO(Tier3InvocationThreshold, scaled_compile_threshold(Tier3InvocationThreshold));
    FLAG_SET_ERGO(Tier3MinInvocationThreshold, scaled_compile_threshold(Tier3MinInvocationThreshold));
    FLAG_SET_ERGO(Tier3CompileThreshold, scaled_compile_threshold(Tier3CompileThreshold));
    FLAG_SET_ERGO(Tier3BackEdgeThreshold, scaled_compile_threshold(Tier3BackEdgeThreshold));

    // Tier2{Invocation,MinInvocation,Compile,Backedge}Threshold should be scaled here
    // once these thresholds become supported.

    FLAG_SET_ERGO(Tier2InvokeNotifyFreqLog, scaled_freq_log(Tier2InvokeNotifyFreqLog));
    FLAG_SET_ERGO(Tier2BackedgeNotifyFreqLog, scaled_freq_log(Tier2BackedgeNotifyFreqLog));

    FLAG_SET_ERGO(Tier3InvokeNotifyFreqLog, scaled_freq_log(Tier3InvokeNotifyFreqLog));
    FLAG_SET_ERGO(Tier3BackedgeNotifyFreqLog, scaled_freq_log(Tier3BackedgeNotifyFreqLog));

    FLAG_SET_ERGO(Tier23InlineeNotifyFreqLog, scaled_freq_log(Tier23InlineeNotifyFreqLog));

    FLAG_SET_ERGO(Tier4InvocationThreshold, scaled_compile_threshold(Tier4InvocationThreshold));
    FLAG_SET_ERGO(Tier4MinInvocationThreshold, scaled_compile_threshold(Tier4MinInvocationThreshold));
    FLAG_SET_ERGO(Tier4CompileThreshold, scaled_compile_threshold(Tier4CompileThreshold));
    FLAG_SET_ERGO(Tier4BackEdgeThreshold, scaled_compile_threshold(Tier4BackEdgeThreshold));

    if (CompilationModeFlag::disable_intermediate()) {
      FLAG_SET_ERGO(Tier40InvocationThreshold, scaled_compile_threshold(Tier40InvocationThreshold));
      FLAG_SET_ERGO(Tier40MinInvocationThreshold, scaled_compile_threshold(Tier40MinInvocationThreshold));
      FLAG_SET_ERGO(Tier40CompileThreshold, scaled_compile_threshold(Tier40CompileThreshold));
      FLAG_SET_ERGO(Tier40BackEdgeThreshold, scaled_compile_threshold(Tier40BackEdgeThreshold));
    }

#if INCLUDE_AOT
    if (UseAOT) {
      FLAG_SET_ERGO(Tier3AOTInvocationThreshold, scaled_compile_threshold(Tier3AOTInvocationThreshold));
      FLAG_SET_ERGO(Tier3AOTMinInvocationThreshold, scaled_compile_threshold(Tier3AOTMinInvocationThreshold));
      FLAG_SET_ERGO(Tier3AOTCompileThreshold, scaled_compile_threshold(Tier3AOTCompileThreshold));
      FLAG_SET_ERGO(Tier3AOTBackEdgeThreshold, scaled_compile_threshold(Tier3AOTBackEdgeThreshold));

      if (CompilationModeFlag::disable_intermediate()) {
        FLAG_SET_ERGO(Tier0AOTInvocationThreshold, scaled_compile_threshold(Tier0AOTInvocationThreshold));
        FLAG_SET_ERGO(Tier0AOTMinInvocationThreshold, scaled_compile_threshold(Tier0AOTMinInvocationThreshold));
        FLAG_SET_ERGO(Tier0AOTCompileThreshold, scaled_compile_threshold(Tier0AOTCompileThreshold));
        FLAG_SET_ERGO(Tier0AOTBackEdgeThreshold, scaled_compile_threshold(Tier0AOTBackEdgeThreshold));
      }
    }
#endif // INCLUDE_AOT
  }

  // Reduce stack usage due to inlining of methods which require much stack.
  // (High tier compiler can inline better based on profiling information.)
  if (FLAG_IS_DEFAULT(C1InlineStackLimit) &&
      TieredStopAtLevel == CompLevel_full_optimization && !CompilationModeFlag::quick_only()) {
    FLAG_SET_DEFAULT(C1InlineStackLimit, 5);
  }
}

#endif // TIERED

#if INCLUDE_JVMCI
void set_jvmci_specific_flags() {
  if (UseJVMCICompiler) {
    Compilation_mode = CompMode_server;

    if (FLAG_IS_DEFAULT(TypeProfileWidth)) {
      FLAG_SET_DEFAULT(TypeProfileWidth, 8);
    }
    if (FLAG_IS_DEFAULT(TypeProfileLevel)) {
      FLAG_SET_DEFAULT(TypeProfileLevel, 0);
    }

    if (UseJVMCINativeLibrary) {
      // SVM compiled code requires more stack space
      if (FLAG_IS_DEFAULT(CompilerThreadStackSize)) {
        // Duplicate logic in the implementations of os::create_thread
        // so that we can then double the computed stack size. Once
        // the stack size requirements of SVM are better understood,
        // this logic can be pushed down into os::create_thread.
        int stack_size = CompilerThreadStackSize;
        if (stack_size == 0) {
          stack_size = VMThreadStackSize;
        }
        if (stack_size != 0) {
          FLAG_SET_DEFAULT(CompilerThreadStackSize, stack_size * 2);
        }
      }
    } else {
#ifdef TIERED
      if (!TieredCompilation) {
         warning("Disabling tiered compilation with non-native JVMCI compiler is not recommended. "
                 "Turning on tiered compilation and disabling intermediate compilation levels instead. ");
         FLAG_SET_ERGO(TieredCompilation, true);
         if (CompilationModeFlag::normal()) {
           CompilationModeFlag::set_high_only_quick_internal(true);
         }
         if (CICompilerCount < 2 && CompilationModeFlag::quick_internal()) {
            warning("Increasing number of compiler threads for JVMCI compiler.");
            FLAG_SET_ERGO(CICompilerCount, 2);
         }
      }
#else // TIERED
      // Adjust the on stack replacement percentage to avoid early
      // OSR compilations while JVMCI itself is warming up
      if (FLAG_IS_DEFAULT(OnStackReplacePercentage)) {
        FLAG_SET_DEFAULT(OnStackReplacePercentage, 933);
      }
#endif // !TIERED
      // JVMCI needs values not less than defaults
      if (FLAG_IS_DEFAULT(ReservedCodeCacheSize)) {
        FLAG_SET_DEFAULT(ReservedCodeCacheSize, MAX2(64*M, ReservedCodeCacheSize));
      }
      if (FLAG_IS_DEFAULT(InitialCodeCacheSize)) {
        FLAG_SET_DEFAULT(InitialCodeCacheSize, MAX2(16*M, InitialCodeCacheSize));
      }
      if (FLAG_IS_DEFAULT(MetaspaceSize)) {
        FLAG_SET_DEFAULT(MetaspaceSize, MIN2(MAX2(12*M, MetaspaceSize), MaxMetaspaceSize));
      }
      if (FLAG_IS_DEFAULT(NewSizeThreadIncrease)) {
        FLAG_SET_DEFAULT(NewSizeThreadIncrease, MAX2(4*K, NewSizeThreadIncrease));
      }
    } // !UseJVMCINativeLibrary
  } // UseJVMCICompiler
}
#endif // INCLUDE_JVMCI

bool CompilerConfig::check_args_consistency(bool status) {
  // Check lower bounds of the code cache
  // Template Interpreter code is approximately 3X larger in debug builds.
  uint min_code_cache_size = CodeCacheMinimumUseSpace DEBUG_ONLY(* 3);
  if (ReservedCodeCacheSize < InitialCodeCacheSize) {
    jio_fprintf(defaultStream::error_stream(),
                "Invalid ReservedCodeCacheSize: %dK. Must be at least InitialCodeCacheSize=%dK.\n",
                ReservedCodeCacheSize/K, InitialCodeCacheSize/K);
    status = false;
  } else if (ReservedCodeCacheSize < min_code_cache_size) {
    jio_fprintf(defaultStream::error_stream(),
                "Invalid ReservedCodeCacheSize=%dK. Must be at least %uK.\n", ReservedCodeCacheSize/K,
                min_code_cache_size/K);
    status = false;
  } else if (ReservedCodeCacheSize > CODE_CACHE_SIZE_LIMIT) {
    // Code cache size larger than CODE_CACHE_SIZE_LIMIT is not supported.
    jio_fprintf(defaultStream::error_stream(),
                "Invalid ReservedCodeCacheSize=%dM. Must be at most %uM.\n", ReservedCodeCacheSize/M,
                CODE_CACHE_SIZE_LIMIT/M);
    status = false;
  } else if (NonNMethodCodeHeapSize < min_code_cache_size) {
    jio_fprintf(defaultStream::error_stream(),
                "Invalid NonNMethodCodeHeapSize=%dK. Must be at least %uK.\n", NonNMethodCodeHeapSize/K,
                min_code_cache_size/K);
    status = false;
  }

#ifdef _LP64
  if (!FLAG_IS_DEFAULT(CICompilerCount) && !FLAG_IS_DEFAULT(CICompilerCountPerCPU) && CICompilerCountPerCPU) {
    warning("The VM option CICompilerCountPerCPU overrides CICompilerCount.");
  }
#endif

  if (BackgroundCompilation && ReplayCompiles) {
    if (!FLAG_IS_DEFAULT(BackgroundCompilation)) {
      warning("BackgroundCompilation disabled due to ReplayCompiles option.");
    }
    FLAG_SET_CMDLINE(BackgroundCompilation, false);
  }

#ifdef COMPILER2
  if (PostLoopMultiversioning && !RangeCheckElimination) {
    if (!FLAG_IS_DEFAULT(PostLoopMultiversioning)) {
      warning("PostLoopMultiversioning disabled because RangeCheckElimination is disabled.");
    }
    FLAG_SET_CMDLINE(PostLoopMultiversioning, false);
  }
  if (UseCountedLoopSafepoints && LoopStripMiningIter == 0) {
    if (!FLAG_IS_DEFAULT(UseCountedLoopSafepoints) || !FLAG_IS_DEFAULT(LoopStripMiningIter)) {
      warning("When counted loop safepoints are enabled, LoopStripMiningIter must be at least 1 (a safepoint every 1 iteration): setting it to 1");
    }
    LoopStripMiningIter = 1;
  } else if (!UseCountedLoopSafepoints && LoopStripMiningIter > 0) {
    if (!FLAG_IS_DEFAULT(UseCountedLoopSafepoints) || !FLAG_IS_DEFAULT(LoopStripMiningIter)) {
      warning("Disabling counted safepoints implies no loop strip mining: setting LoopStripMiningIter to 0");
    }
    LoopStripMiningIter = 0;
  }
#endif // COMPILER2

  if (Arguments::is_interpreter_only()) {
    if (UseCompiler) {
      if (!FLAG_IS_DEFAULT(UseCompiler)) {
        warning("UseCompiler disabled due to -Xint.");
      }
      FLAG_SET_CMDLINE(UseCompiler, false);
    }
    if (ProfileInterpreter) {
      if (!FLAG_IS_DEFAULT(ProfileInterpreter)) {
        warning("ProfileInterpreter disabled due to -Xint.");
      }
      FLAG_SET_CMDLINE(ProfileInterpreter, false);
    }
    if (TieredCompilation) {
      if (!FLAG_IS_DEFAULT(TieredCompilation)) {
        warning("TieredCompilation disabled due to -Xint.");
      }
      FLAG_SET_CMDLINE(TieredCompilation, false);
    }
#if INCLUDE_JVMCI
    if (EnableJVMCI) {
      if (!FLAG_IS_DEFAULT(EnableJVMCI) || !FLAG_IS_DEFAULT(UseJVMCICompiler)) {
        warning("JVMCI Compiler disabled due to -Xint.");
      }
      FLAG_SET_CMDLINE(EnableJVMCI, false);
      FLAG_SET_CMDLINE(UseJVMCICompiler, false);
    }
#endif
  } else {
#if INCLUDE_JVMCI
    status = status && JVMCIGlobals::check_jvmci_flags_are_consistent();
#endif
  }
  return status;
}

void CompilerConfig::ergo_initialize() {
  if (Arguments::is_interpreter_only()) {
    return; // Nothing to do.
  }

#ifdef TIERED
  if (!compilation_mode_selected()) {
    select_compilation_mode_ergonomically();
  }
#endif

#if INCLUDE_JVMCI
  // Check that JVMCI compiler supports selested GC.
  // Should be done after GCConfig::initialize() was called.
  JVMCIGlobals::check_jvmci_supported_gc();

  // Do JVMCI specific settings
  set_jvmci_specific_flags();
#endif

#ifdef TIERED
  if (TieredCompilation) {
    set_tiered_flags();
  } else
#endif
  {
    // Scale CompileThreshold
    // CompileThresholdScaling == 0.0 is equivalent to -Xint and leaves CompileThreshold unchanged.
    if (!FLAG_IS_DEFAULT(CompileThresholdScaling) && CompileThresholdScaling > 0.0) {
      FLAG_SET_ERGO(CompileThreshold, scaled_compile_threshold(CompileThreshold));
    }
  }

  if (FLAG_IS_DEFAULT(SweeperThreshold)) {
    if ((SweeperThreshold * ReservedCodeCacheSize / 100) > (1.2 * M)) {
      // Cap default SweeperThreshold value to an equivalent of 1.2 Mb
      FLAG_SET_ERGO(SweeperThreshold, (1.2 * M * 100) / ReservedCodeCacheSize);
    }
  }

  if (UseOnStackReplacement && !UseLoopCounter) {
    warning("On-stack-replacement requires loop counters; enabling loop counters");
    FLAG_SET_DEFAULT(UseLoopCounter, true);
  }

#ifdef COMPILER2
  if (!EliminateLocks) {
    EliminateNestedLocks = false;
  }
  if (!Inline) {
    IncrementalInline = false;
  }
#ifndef PRODUCT
  if (!IncrementalInline) {
    AlwaysIncrementalInline = false;
  }
  if (PrintIdealGraphLevel > 0) {
    FLAG_SET_ERGO(PrintIdealGraph, true);
  }
#endif
  if (!UseTypeSpeculation && FLAG_IS_DEFAULT(TypeProfileLevel)) {
    // nothing to use the profiling, turn if off
    FLAG_SET_DEFAULT(TypeProfileLevel, 0);
  }
  if (!FLAG_IS_DEFAULT(OptoLoopAlignment) && FLAG_IS_DEFAULT(MaxLoopPad)) {
    FLAG_SET_DEFAULT(MaxLoopPad, OptoLoopAlignment-1);
  }
  if (FLAG_IS_DEFAULT(LoopStripMiningIterShortLoop)) {
    // blind guess
    LoopStripMiningIterShortLoop = LoopStripMiningIter / 10;
  }
#endif // COMPILER2
}

static CompLevel highest_compile_level() {
  return TieredCompilation ? MIN2((CompLevel) TieredStopAtLevel, CompLevel_highest_tier) : CompLevel_highest_tier;
}

bool is_c1_or_interpreter_only() {
  if (Arguments::is_interpreter_only()) {
    return true;
  }

#if INCLUDE_AOT
  if (UseAOT) {
    return false;
  }
#endif

  if (highest_compile_level() < CompLevel_full_optimization) {
#if INCLUDE_JVMCI
    if (TieredCompilation) {
       return true;
    }
    // This happens on jvm variant with C2 disabled and JVMCI
    // enabled.
    return !UseJVMCICompiler;
#else
    return true;
#endif
  }

#ifdef TIERED
  // The quick-only compilation mode is c1 only. However,
  // CompilationModeFlag only takes effect with TieredCompilation
  // enabled.
  if (TieredCompilation && CompilationModeFlag::quick_only()) {
    return true;
  }
#endif
  return false;
}
