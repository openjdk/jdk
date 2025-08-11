/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/flags/jvmFlag.hpp"
#include "runtime/flags/jvmFlagLimit.hpp"
#include "runtime/flags/jvmFlagConstraintsRuntime.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/task.hpp"
#include "utilities/powerOfTwo.hpp"

JVMFlag::Error AOTCacheConstraintFunc(ccstr value, bool verbose) {
  if (value == nullptr) {
    JVMFlag::printError(verbose, "AOTCache cannot be empty\n");
    return JVMFlag::VIOLATES_CONSTRAINT;
  }
  return JVMFlag::SUCCESS;
}

JVMFlag::Error AOTCacheOutputConstraintFunc(ccstr value, bool verbose) {
  if (value == nullptr) {
    JVMFlag::printError(verbose, "AOTCacheOutput cannot be empty\n");
    return JVMFlag::VIOLATES_CONSTRAINT;
  }
  return JVMFlag::SUCCESS;
}

JVMFlag::Error AOTConfigurationConstraintFunc(ccstr value, bool verbose) {
  if (value == nullptr) {
    JVMFlag::printError(verbose, "AOTConfiguration cannot be empty\n");
    return JVMFlag::VIOLATES_CONSTRAINT;
  }
  return JVMFlag::SUCCESS;
}

JVMFlag::Error AOTModeConstraintFunc(ccstr value, bool verbose) {
  if (value == nullptr) {
    JVMFlag::printError(verbose, "AOTMode cannot be empty\n");
    return JVMFlag::VIOLATES_CONSTRAINT;
  }
  if (strcmp(value, "off") != 0 &&
      strcmp(value, "record") != 0 &&
      strcmp(value, "create") != 0 &&
      strcmp(value, "auto") != 0 &&
      strcmp(value, "on") != 0) {
    JVMFlag::printError(verbose,
                        "Unrecognized value %s for AOTMode. Must be one of the following: "
                        "off, record, create, auto, on\n",
                        value);
    return JVMFlag::VIOLATES_CONSTRAINT;
  }
  return JVMFlag::SUCCESS;
}

JVMFlag::Error ObjectAlignmentInBytesConstraintFunc(int value, bool verbose) {
  if (!is_power_of_2(value)) {
    JVMFlag::printError(verbose,
                        "ObjectAlignmentInBytes (%d) must be "
                        "power of 2\n",
                        value);
    return JVMFlag::VIOLATES_CONSTRAINT;
  }
  // In case page size is very small.
  if (value >= (intx)os::vm_page_size()) {
    JVMFlag::printError(verbose,
                        "ObjectAlignmentInBytes (%d) must be "
                        "less than page size (%zu)\n",
                        value, os::vm_page_size());
    return JVMFlag::VIOLATES_CONSTRAINT;
  }
  return JVMFlag::SUCCESS;
}

// Need to enforce the padding not to break the existing field alignments.
// It is sufficient to check against the largest type size.
JVMFlag::Error ContendedPaddingWidthConstraintFunc(int value, bool verbose) {
  if ((value % BytesPerLong) != 0) {
    JVMFlag::printError(verbose,
                        "ContendedPaddingWidth (%d) must be "
                        "a multiple of %d\n",
                        value, BytesPerLong);
    return JVMFlag::VIOLATES_CONSTRAINT;
  } else {
    return JVMFlag::SUCCESS;
  }
}

JVMFlag::Error VMPageSizeConstraintFunc(size_t value, bool verbose) {
  size_t min = os::vm_page_size();
  if (value < min) {
    JVMFlag::printError(verbose,
                        "%s %s=%zu is outside the allowed range [ %zu"
                        " ... %zu ]\n",
                        JVMFlagLimit::last_checked_flag()->type_string(),
                        JVMFlagLimit::last_checked_flag()->name(),
                        value, min, max_uintx);
    return JVMFlag::VIOLATES_CONSTRAINT;
  }

  return JVMFlag::SUCCESS;
}

JVMFlag::Error NUMAInterleaveGranularityConstraintFunc(size_t value, bool verbose) {
  size_t min = os::vm_allocation_granularity();
  size_t max = NOT_LP64(2*G) LP64_ONLY(8192*G);

  if (value < min || value > max) {
    JVMFlag::printError(verbose,
                        "size_t NUMAInterleaveGranularity=%zu is outside the allowed range [ %zu"
                        " ... %zu ]\n", value, min, max);
    return JVMFlag::VIOLATES_CONSTRAINT;
  }

  return JVMFlag::SUCCESS;
}

JVMFlag::Error OnSpinWaitInstNameConstraintFunc(ccstr value, bool verbose) {
#ifdef AARCH64
  if (value == nullptr) {
    JVMFlag::printError(verbose, "OnSpinWaitInst cannot be empty\n");
    return JVMFlag::VIOLATES_CONSTRAINT;
  }

  if (strcmp(value, "nop")   != 0 &&
      strcmp(value, "isb")   != 0 &&
      strcmp(value, "yield") != 0 &&
      strcmp(value, "sb")    != 0 &&
      strcmp(value, "none")  != 0) {
    JVMFlag::printError(verbose,
                        "Unrecognized value %s for OnSpinWaitInst. Must be one of the following: "
                        "nop, isb, yield, sb, none\n",
                        value);
    return JVMFlag::VIOLATES_CONSTRAINT;
  }
#endif
  return JVMFlag::SUCCESS;
}
