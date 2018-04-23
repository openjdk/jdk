/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/collectorPolicy.hpp"
#include "gc/shared/commandLineFlagConstraintsGC.hpp"
#include "gc/shared/plab.hpp"
#include "gc/shared/threadLocalAllocBuffer.hpp"
#include "runtime/arguments.hpp"
#include "runtime/commandLineFlagRangeList.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc/cms/commandLineFlagConstraintsCMS.hpp"
#include "gc/g1/commandLineFlagConstraintsG1.hpp"
#include "gc/parallel/commandLineFlagConstraintsParallel.hpp"
#endif
#ifdef COMPILER1
#include "c1/c1_globals.hpp"
#endif // COMPILER1
#ifdef COMPILER2
#include "opto/c2_globals.hpp"
#endif // COMPILER2

// Some flags that have default values that indicate that the
// JVM should automatically determine an appropriate value
// for that flag.  In those cases it is only appropriate for the
// constraint checking to be done if the user has specified the
// value(s) of the flag(s) on the command line.  In the constraint
// checking functions,  FLAG_IS_CMDLINE() is used to check if
// the flag has been set by the user and so should be checked.

// As ParallelGCThreads differs among GC modes, we need constraint function.
Flag::Error ParallelGCThreadsConstraintFunc(uint value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;

#if INCLUDE_ALL_GCS
  status = ParallelGCThreadsConstraintFuncParallel(value, verbose);
  if (status != Flag::SUCCESS) {
    return status;
  }

  status = ParallelGCThreadsConstraintFuncCMS(value, verbose);
  if (status != Flag::SUCCESS) {
    return status;
  }
#endif

  return status;
}

// As ConcGCThreads should be smaller than ParallelGCThreads,
// we need constraint function.
Flag::Error ConcGCThreadsConstraintFunc(uint value, bool verbose) {
#if INCLUDE_ALL_GCS
  // CMS and G1 GCs use ConcGCThreads.
  if ((UseConcMarkSweepGC || UseG1GC) && (value > ParallelGCThreads)) {
    CommandLineError::print(verbose,
                            "ConcGCThreads (" UINT32_FORMAT ") must be "
                            "less than or equal to ParallelGCThreads (" UINT32_FORMAT ")\n",
                            value, ParallelGCThreads);
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif
  return Flag::SUCCESS;
}

static Flag::Error MinPLABSizeBounds(const char* name, size_t value, bool verbose) {
#if INCLUDE_ALL_GCS
  if ((UseConcMarkSweepGC || UseG1GC || UseParallelGC) && (value < PLAB::min_size())) {
    CommandLineError::print(verbose,
                            "%s (" SIZE_FORMAT ") must be "
                            "greater than or equal to ergonomic PLAB minimum size (" SIZE_FORMAT ")\n",
                            name, value, PLAB::min_size());
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif // INCLUDE_ALL_GCS
  return Flag::SUCCESS;
}

Flag::Error MaxPLABSizeBounds(const char* name, size_t value, bool verbose) {
#if INCLUDE_ALL_GCS
  if ((UseConcMarkSweepGC || UseG1GC || UseParallelGC) && (value > PLAB::max_size())) {
    CommandLineError::print(verbose,
                            "%s (" SIZE_FORMAT ") must be "
                            "less than or equal to ergonomic PLAB maximum size (" SIZE_FORMAT ")\n",
                            name, value, PLAB::max_size());
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif // INCLUDE_ALL_GCS
  return Flag::SUCCESS;
}

static Flag::Error MinMaxPLABSizeBounds(const char* name, size_t value, bool verbose) {
  Flag::Error status = MinPLABSizeBounds(name, value, verbose);

  if (status == Flag::SUCCESS) {
    return MaxPLABSizeBounds(name, value, verbose);
  }
  return status;
}

Flag::Error YoungPLABSizeConstraintFunc(size_t value, bool verbose) {
  return MinMaxPLABSizeBounds("YoungPLABSize", value, verbose);
}

Flag::Error OldPLABSizeConstraintFunc(size_t value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;

#if INCLUDE_ALL_GCS
  if (UseConcMarkSweepGC) {
    return OldPLABSizeConstraintFuncCMS(value, verbose);
  } else {
    status = MinMaxPLABSizeBounds("OldPLABSize", value, verbose);
  }
#endif
  return status;
}

Flag::Error MinHeapFreeRatioConstraintFunc(uintx value, bool verbose) {
  if (value > MaxHeapFreeRatio) {
    CommandLineError::print(verbose,
                            "MinHeapFreeRatio (" UINTX_FORMAT ") must be "
                            "less than or equal to MaxHeapFreeRatio (" UINTX_FORMAT ")\n",
                            value, MaxHeapFreeRatio);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error MaxHeapFreeRatioConstraintFunc(uintx value, bool verbose) {
  if (value < MinHeapFreeRatio) {
    CommandLineError::print(verbose,
                            "MaxHeapFreeRatio (" UINTX_FORMAT ") must be "
                            "greater than or equal to MinHeapFreeRatio (" UINTX_FORMAT ")\n",
                            value, MinHeapFreeRatio);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

static Flag::Error CheckMaxHeapSizeAndSoftRefLRUPolicyMSPerMB(size_t maxHeap, intx softRef, bool verbose) {
  if ((softRef > 0) && ((maxHeap / M) > (max_uintx / softRef))) {
    CommandLineError::print(verbose,
                            "Desired lifetime of SoftReferences cannot be expressed correctly. "
                            "MaxHeapSize (" SIZE_FORMAT ") or SoftRefLRUPolicyMSPerMB "
                            "(" INTX_FORMAT ") is too large\n",
                            maxHeap, softRef);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error SoftRefLRUPolicyMSPerMBConstraintFunc(intx value, bool verbose) {
  return CheckMaxHeapSizeAndSoftRefLRUPolicyMSPerMB(MaxHeapSize, value, verbose);
}

Flag::Error MarkStackSizeConstraintFunc(size_t value, bool verbose) {
  if (value > MarkStackSizeMax) {
    CommandLineError::print(verbose,
                            "MarkStackSize (" SIZE_FORMAT ") must be "
                            "less than or equal to MarkStackSizeMax (" SIZE_FORMAT ")\n",
                            value, MarkStackSizeMax);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error MinMetaspaceFreeRatioConstraintFunc(uintx value, bool verbose) {
  if (value > MaxMetaspaceFreeRatio) {
    CommandLineError::print(verbose,
                            "MinMetaspaceFreeRatio (" UINTX_FORMAT ") must be "
                            "less than or equal to MaxMetaspaceFreeRatio (" UINTX_FORMAT ")\n",
                            value, MaxMetaspaceFreeRatio);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error MaxMetaspaceFreeRatioConstraintFunc(uintx value, bool verbose) {
  if (value < MinMetaspaceFreeRatio) {
    CommandLineError::print(verbose,
                            "MaxMetaspaceFreeRatio (" UINTX_FORMAT ") must be "
                            "greater than or equal to MinMetaspaceFreeRatio (" UINTX_FORMAT ")\n",
                            value, MinMetaspaceFreeRatio);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error InitialTenuringThresholdConstraintFunc(uintx value, bool verbose) {
#if INCLUDE_ALL_GCS
  Flag::Error status = InitialTenuringThresholdConstraintFuncParallel(value, verbose);
  if (status != Flag::SUCCESS) {
    return status;
  }
#endif

  return Flag::SUCCESS;
}

Flag::Error MaxTenuringThresholdConstraintFunc(uintx value, bool verbose) {
#if INCLUDE_ALL_GCS
  Flag::Error status = MaxTenuringThresholdConstraintFuncParallel(value, verbose);
  if (status != Flag::SUCCESS) {
    return status;
  }
#endif

  // MaxTenuringThreshold=0 means NeverTenure=false && AlwaysTenure=true
  if ((value == 0) && (NeverTenure || !AlwaysTenure)) {
    CommandLineError::print(verbose,
                            "MaxTenuringThreshold (0) should match to NeverTenure=false "
                            "&& AlwaysTenure=true. But we have NeverTenure=%s "
                            "AlwaysTenure=%s\n",
                            NeverTenure ? "true" : "false",
                            AlwaysTenure ? "true" : "false");
    return Flag::VIOLATES_CONSTRAINT;
  }
  return Flag::SUCCESS;
}

Flag::Error MaxGCPauseMillisConstraintFunc(uintx value, bool verbose) {
#if INCLUDE_ALL_GCS
  Flag::Error status = MaxGCPauseMillisConstraintFuncG1(value, verbose);
  if (status != Flag::SUCCESS) {
    return status;
  }
#endif

  return Flag::SUCCESS;
}

Flag::Error GCPauseIntervalMillisConstraintFunc(uintx value, bool verbose) {
#if INCLUDE_ALL_GCS
  Flag::Error status = GCPauseIntervalMillisConstraintFuncG1(value, verbose);
  if (status != Flag::SUCCESS) {
    return status;
  }
#endif

  return Flag::SUCCESS;
}

Flag::Error InitialBootClassLoaderMetaspaceSizeConstraintFunc(size_t value, bool verbose) {
  size_t aligned_max = align_down(max_uintx/2, Metaspace::reserve_alignment_words());
  if (value > aligned_max) {
    CommandLineError::print(verbose,
                            "InitialBootClassLoaderMetaspaceSize (" SIZE_FORMAT ") must be "
                            "less than or equal to aligned maximum value (" SIZE_FORMAT ")\n",
                            value, aligned_max);
    return Flag::VIOLATES_CONSTRAINT;
  }
  return Flag::SUCCESS;
}

// To avoid an overflow by 'align_up(value, alignment)'.
static Flag::Error MaxSizeForAlignment(const char* name, size_t value, size_t alignment, bool verbose) {
  size_t aligned_max = ((max_uintx - alignment) & ~(alignment-1));
  if (value > aligned_max) {
    CommandLineError::print(verbose,
                            "%s (" SIZE_FORMAT ") must be "
                            "less than or equal to aligned maximum value (" SIZE_FORMAT ")\n",
                            name, value, aligned_max);
    return Flag::VIOLATES_CONSTRAINT;
  }
  return Flag::SUCCESS;
}

static Flag::Error MaxSizeForHeapAlignment(const char* name, size_t value, bool verbose) {
  size_t heap_alignment;

#if INCLUDE_ALL_GCS
  if (UseG1GC) {
    // For G1 GC, we don't know until G1CollectorPolicy is created.
    heap_alignment = MaxSizeForHeapAlignmentG1();
  } else
#endif
  {
    heap_alignment = CollectorPolicy::compute_heap_alignment();
  }

  return MaxSizeForAlignment(name, value, heap_alignment, verbose);
}

Flag::Error InitialHeapSizeConstraintFunc(size_t value, bool verbose) {
  return MaxSizeForHeapAlignment("InitialHeapSize", value, verbose);
}

Flag::Error MaxHeapSizeConstraintFunc(size_t value, bool verbose) {
  Flag::Error status = MaxSizeForHeapAlignment("MaxHeapSize", value, verbose);

  if (status == Flag::SUCCESS) {
    status = CheckMaxHeapSizeAndSoftRefLRUPolicyMSPerMB(value, SoftRefLRUPolicyMSPerMB, verbose);
  }
  return status;
}

Flag::Error HeapBaseMinAddressConstraintFunc(size_t value, bool verbose) {
  // If an overflow happened in Arguments::set_heap_size(), MaxHeapSize will have too large a value.
  // Check for this by ensuring that MaxHeapSize plus the requested min base address still fit within max_uintx.
  if (UseCompressedOops && FLAG_IS_ERGO(MaxHeapSize) && (value > (max_uintx - MaxHeapSize))) {
    CommandLineError::print(verbose,
                            "HeapBaseMinAddress (" SIZE_FORMAT ") or MaxHeapSize (" SIZE_FORMAT ") is too large. "
                            "Sum of them must be less than or equal to maximum of size_t (" SIZE_FORMAT ")\n",
                            value, MaxHeapSize, max_uintx);
    return Flag::VIOLATES_CONSTRAINT;
  }

  return MaxSizeForHeapAlignment("HeapBaseMinAddress", value, verbose);
}

Flag::Error NewSizeConstraintFunc(size_t value, bool verbose) {
#if INCLUDE_ALL_GCS
  Flag::Error status = NewSizeConstraintFuncG1(value, verbose);
  if (status != Flag::SUCCESS) {
    return status;
  }
#endif

  return Flag::SUCCESS;
}

Flag::Error MinTLABSizeConstraintFunc(size_t value, bool verbose) {
  // At least, alignment reserve area is needed.
  if (value < ThreadLocalAllocBuffer::alignment_reserve_in_bytes()) {
    CommandLineError::print(verbose,
                            "MinTLABSize (" SIZE_FORMAT ") must be "
                            "greater than or equal to reserved area in TLAB (" SIZE_FORMAT ")\n",
                            value, ThreadLocalAllocBuffer::alignment_reserve_in_bytes());
    return Flag::VIOLATES_CONSTRAINT;
  }
  if (value > (ThreadLocalAllocBuffer::max_size() * HeapWordSize)) {
    CommandLineError::print(verbose,
                            "MinTLABSize (" SIZE_FORMAT ") must be "
                            "less than or equal to ergonomic TLAB maximum (" SIZE_FORMAT ")\n",
                            value, ThreadLocalAllocBuffer::max_size() * HeapWordSize);
    return Flag::VIOLATES_CONSTRAINT;
  }
  return Flag::SUCCESS;
}

Flag::Error TLABSizeConstraintFunc(size_t value, bool verbose) {
  // Skip for default value of zero which means set ergonomically.
  if (FLAG_IS_CMDLINE(TLABSize)) {
    if (value < MinTLABSize) {
      CommandLineError::print(verbose,
                              "TLABSize (" SIZE_FORMAT ") must be "
                              "greater than or equal to MinTLABSize (" SIZE_FORMAT ")\n",
                              value, MinTLABSize);
      return Flag::VIOLATES_CONSTRAINT;
    }
    if (value > (ThreadLocalAllocBuffer::max_size() * HeapWordSize)) {
      CommandLineError::print(verbose,
                              "TLABSize (" SIZE_FORMAT ") must be "
                              "less than or equal to ergonomic TLAB maximum size (" SIZE_FORMAT ")\n",
                              value, (ThreadLocalAllocBuffer::max_size() * HeapWordSize));
      return Flag::VIOLATES_CONSTRAINT;
    }
  }
  return Flag::SUCCESS;
}

// We will protect overflow from ThreadLocalAllocBuffer::record_slow_allocation(),
// so AfterMemoryInit type is enough to check.
Flag::Error TLABWasteIncrementConstraintFunc(uintx value, bool verbose) {
  if (UseTLAB) {
    size_t refill_waste_limit = Thread::current()->tlab().refill_waste_limit();

    // Compare with 'max_uintx' as ThreadLocalAllocBuffer::_refill_waste_limit is 'size_t'.
    if (refill_waste_limit > (max_uintx - value)) {
      CommandLineError::print(verbose,
                              "TLABWasteIncrement (" UINTX_FORMAT ") must be "
                              "less than or equal to ergonomic TLAB waste increment maximum size(" SIZE_FORMAT ")\n",
                              value, (max_uintx - refill_waste_limit));
      return Flag::VIOLATES_CONSTRAINT;
    }
  }
  return Flag::SUCCESS;
}

Flag::Error SurvivorRatioConstraintFunc(uintx value, bool verbose) {
  if (FLAG_IS_CMDLINE(SurvivorRatio) &&
      (value > (MaxHeapSize / Universe::heap()->collector_policy()->space_alignment()))) {
    CommandLineError::print(verbose,
                            "SurvivorRatio (" UINTX_FORMAT ") must be "
                            "less than or equal to ergonomic SurvivorRatio maximum (" SIZE_FORMAT ")\n",
                            value,
                            (MaxHeapSize / Universe::heap()->collector_policy()->space_alignment()));
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error MetaspaceSizeConstraintFunc(size_t value, bool verbose) {
  if (value > MaxMetaspaceSize) {
    CommandLineError::print(verbose,
                            "MetaspaceSize (" SIZE_FORMAT ") must be "
                            "less than or equal to MaxMetaspaceSize (" SIZE_FORMAT ")\n",
                            value, MaxMetaspaceSize);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error MaxMetaspaceSizeConstraintFunc(size_t value, bool verbose) {
  if (value < MetaspaceSize) {
    CommandLineError::print(verbose,
                            "MaxMetaspaceSize (" SIZE_FORMAT ") must be "
                            "greater than or equal to MetaspaceSize (" SIZE_FORMAT ")\n",
                            value, MaxMetaspaceSize);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error SurvivorAlignmentInBytesConstraintFunc(intx value, bool verbose) {
  if (value != 0) {
    if (!is_power_of_2(value)) {
      CommandLineError::print(verbose,
                              "SurvivorAlignmentInBytes (" INTX_FORMAT ") must be "
                              "power of 2\n",
                              value);
      return Flag::VIOLATES_CONSTRAINT;
    }
    if (value < ObjectAlignmentInBytes) {
      CommandLineError::print(verbose,
                              "SurvivorAlignmentInBytes (" INTX_FORMAT ") must be "
                              "greater than or equal to ObjectAlignmentInBytes (" INTX_FORMAT ")\n",
                              value, ObjectAlignmentInBytes);
      return Flag::VIOLATES_CONSTRAINT;
    }
  }
  return Flag::SUCCESS;
}
