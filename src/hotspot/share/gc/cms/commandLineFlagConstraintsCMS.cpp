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
#include "gc/cms/commandLineFlagConstraintsCMS.hpp"
#include "gc/cms/concurrentMarkSweepGeneration.inline.hpp"
#include "gc/shared/cardTableRS.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/commandLineFlagConstraintsGC.hpp"
#include "memory/universe.hpp"
#include "runtime/commandLineFlagRangeList.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/globalDefinitions.hpp"

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

Flag::Error ParallelGCThreadsConstraintFuncCMS(uint value, bool verbose) {
  // To avoid overflow at ParScanClosure::do_oop_work.
  if (UseConcMarkSweepGC && (value > (max_jint / 10))) {
    CommandLineError::print(verbose,
                            "ParallelGCThreads (" UINT32_FORMAT ") must be "
                            "less than or equal to " UINT32_FORMAT " for CMS GC\n",
                            value, (max_jint / 10));
    return Flag::VIOLATES_CONSTRAINT;
  }
  return ParallelGCThreadsAndCMSWorkQueueDrainThreshold(value, CMSWorkQueueDrainThreshold, verbose);
}
Flag::Error ParGCStridesPerThreadConstraintFunc(uintx value, bool verbose) {
  if (UseConcMarkSweepGC && (value > ((uintx)max_jint / (uintx)ParallelGCThreads))) {
    CommandLineError::print(verbose,
                            "ParGCStridesPerThread (" UINTX_FORMAT ") must be "
                            "less than or equal to ergonomic maximum (" UINTX_FORMAT ")\n",
                            value, ((uintx)max_jint / (uintx)ParallelGCThreads));
    return Flag::VIOLATES_CONSTRAINT;
  }
  return Flag::SUCCESS;
}

Flag::Error ParGCCardsPerStrideChunkConstraintFunc(intx value, bool verbose) {
  if (UseConcMarkSweepGC) {
    // ParGCCardsPerStrideChunk should be compared with card table size.
    size_t heap_size = Universe::heap()->reserved_region().word_size();
    CardTableRS* ct = GenCollectedHeap::heap()->rem_set();
    size_t card_table_size = ct->cards_required(heap_size) - 1; // Valid card table size

    if ((size_t)value > card_table_size) {
      CommandLineError::print(verbose,
                              "ParGCCardsPerStrideChunk (" INTX_FORMAT ") is too large for the heap size and "
                              "must be less than or equal to card table size (" SIZE_FORMAT ")\n",
                              value, card_table_size);
      return Flag::VIOLATES_CONSTRAINT;
    }

    // ParGCCardsPerStrideChunk is used with n_strides(ParallelGCThreads*ParGCStridesPerThread)
    // from CardTableRS::process_stride(). Note that ParGCStridesPerThread is already checked
    // not to make an overflow with ParallelGCThreads from its constraint function.
    uintx n_strides = ParallelGCThreads * ParGCStridesPerThread;
    uintx ergo_max = max_uintx / n_strides;
    if ((uintx)value > ergo_max) {
      CommandLineError::print(verbose,
                              "ParGCCardsPerStrideChunk (" INTX_FORMAT ") must be "
                              "less than or equal to ergonomic maximum (" UINTX_FORMAT ")\n",
                              value, ergo_max);
      return Flag::VIOLATES_CONSTRAINT;
    }
  }
  return Flag::SUCCESS;
}

Flag::Error CMSOldPLABMinConstraintFunc(size_t value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;

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
  return status;
}

Flag::Error CMSOldPLABMaxConstraintFunc(size_t value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;

  if (UseConcMarkSweepGC) {
    status = MaxPLABSizeBounds("CMSOldPLABMax", value, verbose);
  }
  return status;
}

static Flag::Error CMSReservedAreaConstraintFunc(const char* name, size_t value, bool verbose) {
  if (UseConcMarkSweepGC) {
    ConcurrentMarkSweepGeneration* cms = (ConcurrentMarkSweepGeneration*)GenCollectedHeap::heap()->old_gen();
    const size_t ergo_max = cms->cmsSpace()->max_flag_size_for_task_size();
    if (value > ergo_max) {
      CommandLineError::print(verbose,
                              "%s (" SIZE_FORMAT ") must be "
                              "less than or equal to ergonomic maximum (" SIZE_FORMAT ") "
                              "which is based on the maximum size of the old generation of the Java heap\n",
                              name, value, ergo_max);
      return Flag::VIOLATES_CONSTRAINT;
    }
  }

  return Flag::SUCCESS;
}

Flag::Error CMSRescanMultipleConstraintFunc(size_t value, bool verbose) {
  Flag::Error status = CMSReservedAreaConstraintFunc("CMSRescanMultiple", value, verbose);

  if (status == Flag::SUCCESS && UseConcMarkSweepGC) {
    // CMSParRemarkTask::do_dirty_card_rescan_tasks requires CompactibleFreeListSpace::rescan_task_size()
    // to be aligned to CardTable::card_size * BitsPerWord.
    // Note that rescan_task_size() will be aligned if CMSRescanMultiple is a multiple of 'HeapWordSize'
    // because rescan_task_size() is CardTable::card_size / HeapWordSize * BitsPerWord.
    if (value % HeapWordSize != 0) {
      CommandLineError::print(verbose,
                              "CMSRescanMultiple (" SIZE_FORMAT ") must be "
                              "a multiple of " SIZE_FORMAT "\n",
                              value, HeapWordSize);
      status = Flag::VIOLATES_CONSTRAINT;
    }
  }

  return status;
}

Flag::Error CMSConcMarkMultipleConstraintFunc(size_t value, bool verbose) {
  return CMSReservedAreaConstraintFunc("CMSConcMarkMultiple", value, verbose);
}

Flag::Error CMSPrecleanDenominatorConstraintFunc(uintx value, bool verbose) {
  if (UseConcMarkSweepGC && (value <= CMSPrecleanNumerator)) {
    CommandLineError::print(verbose,
                            "CMSPrecleanDenominator (" UINTX_FORMAT ") must be "
                            "strickly greater than CMSPrecleanNumerator (" UINTX_FORMAT ")\n",
                            value, CMSPrecleanNumerator);
    return Flag::VIOLATES_CONSTRAINT;
  }
  return Flag::SUCCESS;
}

Flag::Error CMSPrecleanNumeratorConstraintFunc(uintx value, bool verbose) {
  if (UseConcMarkSweepGC && (value >= CMSPrecleanDenominator)) {
    CommandLineError::print(verbose,
                            "CMSPrecleanNumerator (" UINTX_FORMAT ") must be "
                            "less than CMSPrecleanDenominator (" UINTX_FORMAT ")\n",
                            value, CMSPrecleanDenominator);
    return Flag::VIOLATES_CONSTRAINT;
  }
  return Flag::SUCCESS;
}

Flag::Error CMSSamplingGrainConstraintFunc(uintx value, bool verbose) {
  if (UseConcMarkSweepGC) {
    size_t max_capacity = GenCollectedHeap::heap()->young_gen()->max_capacity();
    if (value > max_uintx - max_capacity) {
    CommandLineError::print(verbose,
                            "CMSSamplingGrain (" UINTX_FORMAT ") must be "
                            "less than or equal to ergonomic maximum (" SIZE_FORMAT ")\n",
                            value, max_uintx - max_capacity);
    return Flag::VIOLATES_CONSTRAINT;
    }
  }
  return Flag::SUCCESS;
}

Flag::Error CMSWorkQueueDrainThresholdConstraintFunc(uintx value, bool verbose) {
  if (UseConcMarkSweepGC) {
    return ParallelGCThreadsAndCMSWorkQueueDrainThreshold(ParallelGCThreads, value, verbose);
  }
  return Flag::SUCCESS;
}

Flag::Error CMSBitMapYieldQuantumConstraintFunc(size_t value, bool verbose) {
  // Skip for current default value.
  if (UseConcMarkSweepGC && FLAG_IS_CMDLINE(CMSBitMapYieldQuantum)) {
    // CMSBitMapYieldQuantum should be compared with mark bitmap size.
    ConcurrentMarkSweepGeneration* cms = (ConcurrentMarkSweepGeneration*)GenCollectedHeap::heap()->old_gen();
    size_t bitmap_size = cms->collector()->markBitMap()->sizeInWords();

    if (value > bitmap_size) {
      CommandLineError::print(verbose,
                              "CMSBitMapYieldQuantum (" SIZE_FORMAT ") must "
                              "be less than or equal to bitmap size (" SIZE_FORMAT ") "
                              "whose size corresponds to the size of old generation of the Java heap\n",
                              value, bitmap_size);
      return Flag::VIOLATES_CONSTRAINT;
    }
  }
  return Flag::SUCCESS;
}

Flag::Error OldPLABSizeConstraintFuncCMS(size_t value, bool verbose) {
  if (value == 0) {
    CommandLineError::print(verbose,
                            "OldPLABSize (" SIZE_FORMAT ") must be greater than 0",
                            value);
    return Flag::VIOLATES_CONSTRAINT;
  }
  // For CMS, OldPLABSize is the number of free blocks of a given size that are used when
  // replenishing the local per-worker free list caches.
  // For more details, please refer to Arguments::set_cms_and_parnew_gc_flags().
  return MaxPLABSizeBounds("OldPLABSize", value, verbose);
}
