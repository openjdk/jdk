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
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/collectorPolicy.hpp"
#include "gc/shared/threadLocalAllocBuffer.hpp"
#include "runtime/arguments.hpp"
#include "runtime/commandLineFlagConstraintsGC.hpp"
#include "runtime/commandLineFlagRangeList.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/defaultStream.hpp"

#if INCLUDE_ALL_GCS
#include "gc/g1/g1_globals.hpp"
#include "gc/g1/heapRegionBounds.inline.hpp"
#include "gc/shared/plab.hpp"
#endif // INCLUDE_ALL_GCS
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

#if INCLUDE_ALL_GCS
static Flag::Error ParallelGCThreadsAndCMSWorkQueueDrainThreshold(uint threads, uintx threshold, bool verbose) {
  // CMSWorkQueueDrainThreshold is verified to be less than max_juint
  if (UseConcMarkSweepGC && (threads > (uint)(max_jint / (uint)threshold))) {
    CommandLineError::print(verbose,
                            "ParallelGCThreads (" UINT32_FORMAT ") or CMSWorkQueueDrainThreshold ("
                            UINTX_FORMAT ") is too large\n",
                            threads, threshold);
    return Flag::VIOLATES_CONSTRAINT;
  }
  return Flag::SUCCESS;
}
#endif

// As ParallelGCThreads differs among GC modes, we need constraint function.
Flag::Error ParallelGCThreadsConstraintFunc(uint value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;

#if INCLUDE_ALL_GCS
  // Parallel GC passes ParallelGCThreads when creating GrowableArray as 'int' type parameter.
  // So can't exceed with "max_jint"
  if (UseParallelGC && (value > (uint)max_jint)) {
    CommandLineError::print(verbose,
                            "ParallelGCThreads (" UINT32_FORMAT ") must be "
                            "less than or equal to " UINT32_FORMAT " for Parallel GC\n",
                            value, max_jint);
    return Flag::VIOLATES_CONSTRAINT;
  }
  // To avoid overflow at ParScanClosure::do_oop_work.
  if (UseConcMarkSweepGC && (value > (max_jint / 10))) {
    CommandLineError::print(verbose,
                            "ParallelGCThreads (" UINT32_FORMAT ") must be "
                            "less than or equal to " UINT32_FORMAT " for CMS GC\n",
                            value, (max_jint / 10));
    return Flag::VIOLATES_CONSTRAINT;
  }
  status = ParallelGCThreadsAndCMSWorkQueueDrainThreshold(value, CMSWorkQueueDrainThreshold, verbose);
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
  if ((UseConcMarkSweepGC || UseG1GC) && (value < PLAB::min_size())) {
    CommandLineError::print(verbose,
                            "%s (" SIZE_FORMAT ") must be "
                            "greater than or equal to ergonomic PLAB minimum size (" SIZE_FORMAT ")\n",
                            name, value, PLAB::min_size());
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif // INCLUDE_ALL_GCS
  return Flag::SUCCESS;
}

static Flag::Error MaxPLABSizeBounds(const char* name, size_t value, bool verbose) {
#if INCLUDE_ALL_GCS
  if ((UseConcMarkSweepGC || UseG1GC) && (value > PLAB::max_size())) {
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
    if (value == 0) {
      CommandLineError::print(verbose,
                              "OldPLABSize (" SIZE_FORMAT ") must be greater than 0",
                              value);
      return Flag::VIOLATES_CONSTRAINT;
    }
    // For CMS, OldPLABSize is the number of free blocks of a given size that are used when
    // replenishing the local per-worker free list caches.
    // For more details, please refer to Arguments::set_cms_and_parnew_gc_flags().
    status = MaxPLABSizeBounds("OldPLABSize", value, verbose);
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
  // InitialTenuringThreshold is only used for ParallelGC.
  if (UseParallelGC && (value > MaxTenuringThreshold)) {
      CommandLineError::print(verbose,
                              "InitialTenuringThreshold (" UINTX_FORMAT ") must be "
                              "less than or equal to MaxTenuringThreshold (" UINTX_FORMAT ")\n",
                              value, MaxTenuringThreshold);
      return Flag::VIOLATES_CONSTRAINT;
  }
#endif
  return Flag::SUCCESS;
}

Flag::Error MaxTenuringThresholdConstraintFunc(uintx value, bool verbose) {
#if INCLUDE_ALL_GCS
  // As only ParallelGC uses InitialTenuringThreshold,
  // we don't need to compare InitialTenuringThreshold with MaxTenuringThreshold.
  if (UseParallelGC && (value < InitialTenuringThreshold)) {
    CommandLineError::print(verbose,
                            "MaxTenuringThreshold (" UINTX_FORMAT ") must be "
                            "greater than or equal to InitialTenuringThreshold (" UINTX_FORMAT ")\n",
                            value, InitialTenuringThreshold);
    return Flag::VIOLATES_CONSTRAINT;
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

#if INCLUDE_ALL_GCS
Flag::Error G1RSetRegionEntriesConstraintFunc(intx value, bool verbose) {
  if (!UseG1GC) return Flag::SUCCESS;

  // Default value of G1RSetRegionEntries=0 means will be set ergonomically.
  // Minimum value is 1.
  if (FLAG_IS_CMDLINE(G1RSetRegionEntries) && (value < 1)) {
    CommandLineError::print(verbose,
                            "G1RSetRegionEntries (" INTX_FORMAT ") must be "
                            "greater than or equal to 1\n",
                            value);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error G1RSetSparseRegionEntriesConstraintFunc(intx value, bool verbose) {
  if (!UseG1GC) return Flag::SUCCESS;

  // Default value of G1RSetSparseRegionEntries=0 means will be set ergonomically.
  // Minimum value is 1.
  if (FLAG_IS_CMDLINE(G1RSetSparseRegionEntries) && (value < 1)) {
    CommandLineError::print(verbose,
                            "G1RSetSparseRegionEntries (" INTX_FORMAT ") must be "
                            "greater than or equal to 1\n",
                            value);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error G1YoungSurvRateNumRegionsSummaryConstraintFunc(intx value, bool verbose) {
  if (!UseG1GC) return Flag::SUCCESS;

  if (value > (intx)HeapRegionBounds::target_number()) {
    CommandLineError::print(verbose,
                            "G1YoungSurvRateNumRegionsSummary (" INTX_FORMAT ") must be "
                            "less than or equal to region count (" SIZE_FORMAT ")\n",
                            value, HeapRegionBounds::target_number());
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error G1HeapRegionSizeConstraintFunc(size_t value, bool verbose) {
  if (!UseG1GC) return Flag::SUCCESS;

  // Default value of G1HeapRegionSize=0 means will be set ergonomically.
  if (FLAG_IS_CMDLINE(G1HeapRegionSize) && (value < HeapRegionBounds::min_size())) {
    CommandLineError::print(verbose,
                            "G1HeapRegionSize (" SIZE_FORMAT ") must be "
                            "greater than or equal to ergonomic heap region minimum size\n",
                            value);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error G1NewSizePercentConstraintFunc(uintx value, bool verbose) {
  if (!UseG1GC) return Flag::SUCCESS;

  if (value > G1MaxNewSizePercent) {
    CommandLineError::print(verbose,
                            "G1NewSizePercent (" UINTX_FORMAT ") must be "
                            "less than or equal to G1MaxNewSizePercent (" UINTX_FORMAT ")\n",
                            value, G1MaxNewSizePercent);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error G1MaxNewSizePercentConstraintFunc(uintx value, bool verbose) {
  if (!UseG1GC) return Flag::SUCCESS;

  if (value < G1NewSizePercent) {
    CommandLineError::print(verbose,
                            "G1MaxNewSizePercent (" UINTX_FORMAT ") must be "
                            "greater than or equal to G1NewSizePercent (" UINTX_FORMAT ")\n",
                            value, G1NewSizePercent);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}
#endif // INCLUDE_ALL_GCS

Flag::Error ParGCStridesPerThreadConstraintFunc(uintx value, bool verbose) {
#if INCLUDE_ALL_GCS
  if (UseConcMarkSweepGC && (value > ((uintx)max_jint / (uintx)ParallelGCThreads))) {
    CommandLineError::print(verbose,
                            "ParGCStridesPerThread (" UINTX_FORMAT ") must be "
                            "less than or equal to ergonomic maximum (" UINTX_FORMAT ")\n",
                            value, ((uintx)max_jint / (uintx)ParallelGCThreads));
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif
  return Flag::SUCCESS;
}

Flag::Error CMSOldPLABMinConstraintFunc(size_t value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;

#if INCLUDE_ALL_GCS
  if (UseConcMarkSweepGC) {
    if (value > CMSOldPLABMax) {
      CommandLineError::print(verbose,
                              "CMSOldPLABMin (" SIZE_FORMAT ") must be "
                              "less than or equal to CMSOldPLABMax (" SIZE_FORMAT ")\n",
                              value, CMSOldPLABMax);
      return Flag::VIOLATES_CONSTRAINT;
    }
    status = MaxPLABSizeBounds("CMSOldPLABMin", value, verbose);
  }
#endif
  return status;
}

Flag::Error CMSOldPLABMaxConstraintFunc(size_t value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;

#if INCLUDE_ALL_GCS
  if (UseConcMarkSweepGC) {
    status = MaxPLABSizeBounds("CMSOldPLABMax", value, verbose);
  }
#endif
  return status;
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

Flag::Error CMSPrecleanDenominatorConstraintFunc(uintx value, bool verbose) {
#if INCLUDE_ALL_GCS
  if (UseConcMarkSweepGC && (value <= CMSPrecleanNumerator)) {
    CommandLineError::print(verbose,
                            "CMSPrecleanDenominator (" UINTX_FORMAT ") must be "
                            "strickly greater than CMSPrecleanNumerator (" UINTX_FORMAT ")\n",
                            value, CMSPrecleanNumerator);
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif
  return Flag::SUCCESS;
}

Flag::Error CMSPrecleanNumeratorConstraintFunc(uintx value, bool verbose) {
#if INCLUDE_ALL_GCS
  if (UseConcMarkSweepGC && (value >= CMSPrecleanDenominator)) {
    CommandLineError::print(verbose,
                            "CMSPrecleanNumerator (" UINTX_FORMAT ") must be "
                            "less than CMSPrecleanDenominator (" UINTX_FORMAT ")\n",
                            value, CMSPrecleanDenominator);
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif
  return Flag::SUCCESS;
}

Flag::Error CMSWorkQueueDrainThresholdConstraintFunc(uintx value, bool verbose) {
#if INCLUDE_ALL_GCS
  if (UseConcMarkSweepGC) {
    return ParallelGCThreadsAndCMSWorkQueueDrainThreshold(ParallelGCThreads, value, verbose);
  }
#endif
  return Flag::SUCCESS;
}

Flag::Error MaxGCPauseMillisConstraintFunc(uintx value, bool verbose) {
#if INCLUDE_ALL_GCS
  if (UseG1GC && FLAG_IS_CMDLINE(MaxGCPauseMillis) && (value >= GCPauseIntervalMillis)) {
    CommandLineError::print(verbose,
                            "MaxGCPauseMillis (" UINTX_FORMAT ") must be "
                            "less than GCPauseIntervalMillis (" UINTX_FORMAT ")\n",
                            value, GCPauseIntervalMillis);
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif

  return Flag::SUCCESS;
}

Flag::Error GCPauseIntervalMillisConstraintFunc(uintx value, bool verbose) {
#if INCLUDE_ALL_GCS
  if (UseG1GC) {
    if (FLAG_IS_CMDLINE(GCPauseIntervalMillis)) {
      if (value < 1) {
        CommandLineError::print(verbose,
                                "GCPauseIntervalMillis (" UINTX_FORMAT ") must be "
                                "greater than or equal to 1\n",
                                value);
        return Flag::VIOLATES_CONSTRAINT;
      }
      if (value <= MaxGCPauseMillis) {
        CommandLineError::print(verbose,
                                "GCPauseIntervalMillis (" UINTX_FORMAT ") must be "
                                "greater than MaxGCPauseMillis (" UINTX_FORMAT ")\n",
                                value, MaxGCPauseMillis);
        return Flag::VIOLATES_CONSTRAINT;
      }
    }
  }
#endif
  return Flag::SUCCESS;
}

Flag::Error InitialBootClassLoaderMetaspaceSizeConstraintFunc(size_t value, bool verbose) {
  size_t aligned_max = (size_t)align_size_down(max_uintx/2, Metaspace::reserve_alignment_words());
  if (value > aligned_max) {
    CommandLineError::print(verbose,
                            "InitialBootClassLoaderMetaspaceSize (" SIZE_FORMAT ") must be "
                            "less than or equal to aligned maximum value (" SIZE_FORMAT ")\n",
                            value, aligned_max);
    return Flag::VIOLATES_CONSTRAINT;
  }
  return Flag::SUCCESS;
}

// To avoid an overflow by 'align_size_up(value, alignment)'.
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
  // For G1 GC, we don't know until G1CollectorPolicy is created.
  size_t heap_alignment;

#if INCLUDE_ALL_GCS
  if (UseG1GC) {
    heap_alignment = HeapRegionBounds::max_size();
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

Flag::Error NUMAInterleaveGranularityConstraintFunc(size_t value, bool verbose) {
  if (UseNUMA && UseNUMAInterleaving) {
    size_t min_interleave_granularity = UseLargePages ? os::large_page_size() : os::vm_allocation_granularity();
    return MaxSizeForAlignment("NUMAInterleaveGranularity", value, min_interleave_granularity, verbose);
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error NewSizeConstraintFunc(size_t value, bool verbose) {
#ifdef _LP64
#if INCLUDE_ALL_GCS
  // Overflow would happen for uint type variable of YoungGenSizer::_min_desired_young_length
  // when the value to be assigned exceeds uint range.
  // i.e. result of '(uint)(NewSize / region size(1~32MB))'
  // So maximum of NewSize should be 'max_juint * 1M'
  if (UseG1GC && (value > (max_juint * 1 * M))) {
    CommandLineError::print(verbose,
                            "NewSize (" SIZE_FORMAT ") must be less than ergonomic maximum value\n",
                            value);
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif // INCLUDE_ALL_GCS
#endif // _LP64
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
  } else {
    return Flag::SUCCESS;
  }
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
