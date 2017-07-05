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
#include "runtime/arguments.hpp"
#include "runtime/commandLineFlagConstraintsGC.hpp"
#include "runtime/commandLineFlagRangeList.hpp"
#include "runtime/globals.hpp"
#include "utilities/defaultStream.hpp"

#if INCLUDE_ALL_GCS
#include "gc/g1/g1_globals.hpp"
#include "gc/g1/heapRegionBounds.inline.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/shared/plab.hpp"
#endif // INCLUDE_ALL_GCS
#ifdef COMPILER1
#include "c1/c1_globals.hpp"
#endif // COMPILER1
#ifdef COMPILER2
#include "opto/c2_globals.hpp"
#endif // COMPILER2

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
                            "less than ergonomic PLAB maximum size (" SIZE_FORMAT ")\n",
                            name, value, PLAB::min_size());
    return Flag::VIOLATES_CONSTRAINT;
  }
#endif // INCLUDE_ALL_GCS
  return Flag::SUCCESS;
}

static Flag::Error MinMaxPLABSizeBounds(const char* name, size_t value, bool verbose) {
  if (MinPLABSizeBounds(name, value, verbose) == Flag::SUCCESS) {
    return MaxPLABSizeBounds(name, value, verbose);
  }
  return Flag::VIOLATES_CONSTRAINT;
}

Flag::Error YoungPLABSizeConstraintFunc(size_t value, bool verbose) {
  return MinMaxPLABSizeBounds("YoungPLABSize", value, verbose);
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

// GC workaround for "-XX:+UseConcMarkSweepGC"
// which sets InitialTenuringThreshold to 7 but leaves MaxTenuringThreshold remaining at 6
// and therefore would invalidate the constraint
#define UseConcMarkSweepGCWorkaroundIfNeeded(initial, max) { \
  if ((initial == 7) && (max == 6)) { \
    return Flag::SUCCESS; \
  } \
}

Flag::Error InitialTenuringThresholdConstraintFunc(uintx value, bool verbose) {
  UseConcMarkSweepGCWorkaroundIfNeeded(value, MaxTenuringThreshold);

  if (value > MaxTenuringThreshold) {
    CommandLineError::print(verbose,
                            "InitialTenuringThreshold (" UINTX_FORMAT ") must be "
                            "less than or equal to MaxTenuringThreshold (" UINTX_FORMAT ")\n",
                            value, MaxTenuringThreshold);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error MaxTenuringThresholdConstraintFunc(uintx value, bool verbose) {
  UseConcMarkSweepGCWorkaroundIfNeeded(InitialTenuringThreshold, value);

  if (value < InitialTenuringThreshold) {
    CommandLineError::print(verbose,
                            "MaxTenuringThreshold (" UINTX_FORMAT ") must be "
                            "greater than or equal to InitialTenuringThreshold (" UINTX_FORMAT ")\n",
                            value, InitialTenuringThreshold);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

#if INCLUDE_ALL_GCS
Flag::Error G1NewSizePercentConstraintFunc(uintx value, bool verbose) {
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

Flag::Error CMSOldPLABMinConstraintFunc(size_t value, bool verbose) {
  if (value > CMSOldPLABMax) {
    CommandLineError::print(verbose,
                            "CMSOldPLABMin (" SIZE_FORMAT ") must be "
                            "less than or equal to CMSOldPLABMax (" SIZE_FORMAT ")\n",
                            value, CMSOldPLABMax);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error CMSPrecleanDenominatorConstraintFunc(uintx value, bool verbose) {
  if (value <= CMSPrecleanNumerator) {
    CommandLineError::print(verbose,
                            "CMSPrecleanDenominator (" UINTX_FORMAT ") must be "
                            "strickly greater than CMSPrecleanNumerator (" UINTX_FORMAT ")\n",
                            value, CMSPrecleanNumerator);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}

Flag::Error CMSPrecleanNumeratorConstraintFunc(uintx value, bool verbose) {
  if (value > (CMSPrecleanDenominator - 1)) {
    CommandLineError::print(verbose,
                            "CMSPrecleanNumerator (" UINTX_FORMAT ") must be "
                            "less than or equal to CMSPrecleanDenominator - 1 (" UINTX_FORMAT ")\n",
                            value, CMSPrecleanDenominator - 1);
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
