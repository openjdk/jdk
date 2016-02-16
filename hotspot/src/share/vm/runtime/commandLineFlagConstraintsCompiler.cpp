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
#include "oops/metadata.hpp"
#include "runtime/os.hpp"
#include "code/relocInfo.hpp"
#include "interpreter/invocationCounter.hpp"
#include "runtime/arguments.hpp"
#include "runtime/commandLineFlagConstraintsCompiler.hpp"
#include "runtime/commandLineFlagRangeList.hpp"
#include "runtime/globals.hpp"
#include "utilities/defaultStream.hpp"

Flag::Error AliasLevelConstraintFunc(intx value, bool verbose) {
  if ((value <= 1) && (Arguments::mode() == Arguments::_comp || Arguments::mode() == Arguments::_mixed)) {
    CommandLineError::print(verbose,
                            "AliasLevel (" INTX_FORMAT ") is not "
                            "compatible with -Xcomp or -Xmixed\n",
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

Flag::Error AllocatePrefetchDistanceConstraintFunc(intx value, bool verbose) {
  if (value < 0) {
    CommandLineError::print(verbose,
                            "Unable to determine system-specific value for AllocatePrefetchDistance. "
                            "Please provide appropriate value, if unsure, use 0 to disable prefetching\n");
    return Flag::VIOLATES_CONSTRAINT;
  }

  return Flag::SUCCESS;
}

Flag::Error AllocatePrefetchInstrConstraintFunc(intx value, bool verbose) {
  intx max_value = max_intx;
#if defined(SPARC)
  max_value = 1;
#elif defined(X86)
  max_value = 3;
#endif
  if (value < 0 || value > max_value) {
    CommandLineError::print(verbose,
                            "AllocatePrefetchInstr (" INTX_FORMAT ") must be "
                            "between 0 and " INTX_FORMAT "\n", value, max_value);
    return Flag::VIOLATES_CONSTRAINT;
  }

  return Flag::SUCCESS;
}

Flag::Error AllocatePrefetchStepSizeConstraintFunc(intx value, bool verbose) {
  intx max_value = 512;
  if (value < 1 || value > max_value) {
    CommandLineError::print(verbose,
                            "AllocatePrefetchStepSize (" INTX_FORMAT ") "
                            "must be between 1 and %d\n",
                            AllocatePrefetchStepSize,
                            max_value);
    return Flag::VIOLATES_CONSTRAINT;
  }

  if (AllocatePrefetchDistance % AllocatePrefetchStepSize != 0) {
    CommandLineError::print(verbose,
                            "AllocatePrefetchDistance (" INTX_FORMAT ") "
                            "%% AllocatePrefetchStepSize (" INTX_FORMAT ") "
                            "= " INTX_FORMAT " "
                            "must be 0\n",
                            AllocatePrefetchDistance, AllocatePrefetchStepSize,
                            AllocatePrefetchDistance % AllocatePrefetchStepSize);
    return Flag::VIOLATES_CONSTRAINT;
  }

  /* The limit of 64 for the quotient of AllocatePrefetchDistance and AllocatePrefetchSize
   * originates from the limit of 64 for AllocatePrefetchLines/AllocateInstancePrefetchLines.
   * If AllocatePrefetchStyle == 2, the quotient from above is used in PhaseMacroExpand::prefetch_allocation()
   * to determine the number of lines to prefetch. For other values of AllocatePrefetchStyle,
   * AllocatePrefetchDistance and AllocatePrefetchSize is used. For consistency, all these
   * quantities must have the same limit (64 in this case).
   */
  if (AllocatePrefetchDistance / AllocatePrefetchStepSize > 64) {
    CommandLineError::print(verbose,
                            "AllocatePrefetchDistance (" INTX_FORMAT ") too large or "
                            "AllocatePrefetchStepSize (" INTX_FORMAT ") too small; "
                            "try decreasing/increasing values so that "
                            "AllocatePrefetchDistance / AllocatePrefetchStepSize <= 64\n",
                            AllocatePrefetchDistance, AllocatePrefetchStepSize,
                            AllocatePrefetchDistance % AllocatePrefetchStepSize);
    return Flag::VIOLATES_CONSTRAINT;
  }

  return Flag::SUCCESS;
}

Flag::Error CompileThresholdConstraintFunc(intx value, bool verbose) {
  if (value < 0 || value > INT_MAX >> InvocationCounter::count_shift) {
    CommandLineError::print(verbose,
                            "CompileThreshold (" INTX_FORMAT ") "
                            "must be between 0 and %d\n",
                            value,
                            INT_MAX >> InvocationCounter::count_shift);
    return Flag::VIOLATES_CONSTRAINT;
  }

  return Flag::SUCCESS;
}

Flag::Error OnStackReplacePercentageConstraintFunc(intx value, bool verbose) {
  int backward_branch_limit;
  if (ProfileInterpreter) {
    if (OnStackReplacePercentage < InterpreterProfilePercentage) {
      CommandLineError::print(verbose,
                              "OnStackReplacePercentage (" INTX_FORMAT ") must be "
                              "larger than InterpreterProfilePercentage (" INTX_FORMAT ")\n",
                              OnStackReplacePercentage, InterpreterProfilePercentage);
      return Flag::VIOLATES_CONSTRAINT;
    }

    backward_branch_limit = ((CompileThreshold * (OnStackReplacePercentage - InterpreterProfilePercentage)) / 100)
                            << InvocationCounter::count_shift;

    if (backward_branch_limit < 0) {
      CommandLineError::print(verbose,
                              "CompileThreshold * (InterpreterProfilePercentage - OnStackReplacePercentage) / 100 = "
                              INTX_FORMAT " "
                              "must be between 0 and " INTX_FORMAT ", try changing "
                              "CompileThreshold, InterpreterProfilePercentage, and/or OnStackReplacePercentage\n",
                              (CompileThreshold * (OnStackReplacePercentage - InterpreterProfilePercentage)) / 100,
                              INT_MAX >> InvocationCounter::count_shift);
      return Flag::VIOLATES_CONSTRAINT;
    }
  } else {
    if (OnStackReplacePercentage < 0 ) {
      CommandLineError::print(verbose,
                              "OnStackReplacePercentage (" INTX_FORMAT ") must be "
                              "non-negative\n", OnStackReplacePercentage);
      return Flag::VIOLATES_CONSTRAINT;
    }

    backward_branch_limit = ((CompileThreshold * OnStackReplacePercentage) / 100)
                            << InvocationCounter::count_shift;

    if (backward_branch_limit < 0) {
      CommandLineError::print(verbose,
                              "CompileThreshold * OnStackReplacePercentage / 100 = " INTX_FORMAT " "
                              "must be between 0 and " INTX_FORMAT ", try changing "
                              "CompileThreshold and/or OnStackReplacePercentage\n",
                              (CompileThreshold * OnStackReplacePercentage) / 100,
                              INT_MAX >> InvocationCounter::count_shift);
      return Flag::VIOLATES_CONSTRAINT;
    }
  }
  return Flag::SUCCESS;
}

Flag::Error CodeCacheSegmentSizeConstraintFunc(uintx value, bool verbose) {
  if (CodeCacheSegmentSize < (uintx)CodeEntryAlignment) {
    CommandLineError::print(verbose,
                            "CodeCacheSegmentSize  (" UINTX_FORMAT ") must be "
                            "larger than or equal to CodeEntryAlignment (" INTX_FORMAT ") "
                            "to align entry points\n",
                            CodeCacheSegmentSize, CodeEntryAlignment);
    return Flag::VIOLATES_CONSTRAINT;
  }

  if (CodeCacheSegmentSize < sizeof(jdouble)) {
    CommandLineError::print(verbose,
                            "CodeCacheSegmentSize  (" UINTX_FORMAT ") must be "
                            "at least " SIZE_FORMAT " to align constants\n",
                            CodeCacheSegmentSize, sizeof(jdouble));
    return Flag::VIOLATES_CONSTRAINT;
  }

#ifdef COMPILER2
  if (CodeCacheSegmentSize < (uintx)OptoLoopAlignment) {
    CommandLineError::print(verbose,
                            "CodeCacheSegmentSize  (" UINTX_FORMAT ") must be "
                            "larger than or equal to OptoLoopAlignment (" INTX_FORMAT ") "
                            "to align inner loops\n",
                            CodeCacheSegmentSize, OptoLoopAlignment);
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif

  return Flag::SUCCESS;
}

Flag::Error CompilerThreadPriorityConstraintFunc(intx value, bool verbose) {
#ifdef SOLARIS
  if ((value < MinimumPriority || value > MaximumPriority) &&
      (value != -1) && (value != -FXCriticalPriority)) {
    CommandLineError::print(verbose,
                            "CompileThreadPriority (" INTX_FORMAT ") must be "
                            "between %d and %d inclusively or -1 (means no change) "
                            "or %d (special value for critical thread class/priority)\n",
                            value, MinimumPriority, MaximumPriority, -FXCriticalPriority);
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif

  return Flag::SUCCESS;
}

Flag::Error CodeEntryAlignmentConstraintFunc(intx value, bool verbose) {
#ifdef SPARC
  if (CodeEntryAlignment % relocInfo::addr_unit() != 0) {
    CommandLineError::print(verbose,
                            "CodeEntryAlignment (" INTX_FORMAT ") must be "
                            "multiple of NOP size\n", CodeEntryAlignment);
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif

  if (!is_power_of_2(value)) {
    CommandLineError::print(verbose,
                            "CodeEntryAlignment (" INTX_FORMAT ") must be "
                            "a power of two\n", CodeEntryAlignment);
    return Flag::VIOLATES_CONSTRAINT;
  }

  if (CodeEntryAlignment < 16) {
      CommandLineError::print(verbose,
                              "CodeEntryAlignment (" INTX_FORMAT ") must be "
                              "greater than or equal to %d\n",
                              CodeEntryAlignment, 16);
      return Flag::VIOLATES_CONSTRAINT;
  }

  return Flag::SUCCESS;
}

Flag::Error OptoLoopAlignmentConstraintFunc(intx value, bool verbose) {
  if (!is_power_of_2(value)) {
    CommandLineError::print(verbose,
                            "OptoLoopAlignment (" INTX_FORMAT ") "
                            "must be a power of two\n",
                            value);
    return Flag::VIOLATES_CONSTRAINT;
  }

#ifdef SPARC
  if (OptoLoopAlignment % relocInfo::addr_unit() != 0) {
    CommandLineError::print(verbose,
                            "OptoLoopAlignment (" INTX_FORMAT ") must be "
                            "multiple of NOP size\n");
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif

  return Flag::SUCCESS;
}

Flag::Error ArraycopyDstPrefetchDistanceConstraintFunc(uintx value, bool verbose) {
  if (value != 0) {
    CommandLineError::print(verbose,
                            "ArraycopyDstPrefetchDistance (" UINTX_FORMAT ") must be 0\n",
                            value);
    return Flag::VIOLATES_CONSTRAINT;
  }

  return Flag::SUCCESS;
}

Flag::Error ArraycopySrcPrefetchDistanceConstraintFunc(uintx value, bool verbose) {
  if (value != 0) {
    CommandLineError::print(verbose,
                            "ArraycopySrcPrefetchDistance (" UINTX_FORMAT ") must be 0\n",
                            value);
    return Flag::VIOLATES_CONSTRAINT;
  }

  return Flag::SUCCESS;
}

Flag::Error TypeProfileLevelConstraintFunc(uintx value, bool verbose) {
  for (int i = 0; i < 3; i++) {
    if (value % 10 > 2) {
      CommandLineError::print(verbose,
                              "Invalid value (" UINTX_FORMAT ") "
                              "in TypeProfileLevel at position %d\n", value, i);
      return Flag::VIOLATES_CONSTRAINT;
    }
    value = value / 10;
  }

  return Flag::SUCCESS;
}

#ifdef COMPILER2
Flag::Error InteriorEntryAlignmentConstraintFunc(intx value, bool verbose) {
  if (InteriorEntryAlignment > CodeEntryAlignment) {
    CommandLineError::print(verbose,
                           "InteriorEntryAlignment (" INTX_FORMAT ") must be "
                           "less than or equal to CodeEntryAlignment (" INTX_FORMAT ")\n",
                           InteriorEntryAlignment, CodeEntryAlignment);
    return Flag::VIOLATES_CONSTRAINT;
  }

#ifdef SPARC
  if (InteriorEntryAlignment % relocInfo::addr_unit() != 0) {
    CommandLineError::print(verbose,
                            "InteriorEntryAlignment (" INTX_FORMAT ") must be "
                            "multiple of NOP size\n");
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif

  if (!is_power_of_2(value)) {
     CommandLineError::print(verbose,
                             "InteriorEntryAlignment (" INTX_FORMAT ") must be "
                             "a power of two\n", InteriorEntryAlignment);
     return Flag::VIOLATES_CONSTRAINT;
   }

  int minimum_alignment = 16;
#if defined(SPARC) || (defined(X86) && !defined(AMD64))
  minimum_alignment = 4;
#endif

  if (InteriorEntryAlignment < minimum_alignment) {
    CommandLineError::print(verbose,
                            "InteriorEntryAlignment (" INTX_FORMAT ") must be "
                            "greater than or equal to %d\n",
                            InteriorEntryAlignment, minimum_alignment);
    return Flag::VIOLATES_CONSTRAINT;
  }

  return Flag::SUCCESS;
}

Flag::Error NodeLimitFudgeFactorConstraintFunc(intx value, bool verbose) {
  if (value < MaxNodeLimit * 2 / 100 || value > MaxNodeLimit * 40 / 100) {
    CommandLineError::print(verbose,
                            "NodeLimitFudgeFactor must be between 2%% and 40%% "
                            "of MaxNodeLimit (" INTX_FORMAT ")\n",
                            MaxNodeLimit);
    return Flag::VIOLATES_CONSTRAINT;
  }

  return Flag::SUCCESS;
}
#endif // COMPILER2
