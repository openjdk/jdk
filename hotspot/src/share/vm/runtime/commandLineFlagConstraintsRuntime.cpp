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
#include "runtime/commandLineFlagConstraintsRuntime.hpp"
#include "runtime/commandLineFlagRangeList.hpp"
#include "runtime/globals.hpp"
#include "utilities/defaultStream.hpp"

Flag::Error ObjectAlignmentInBytesConstraintFunc(intx value, bool verbose) {
  if (!is_power_of_2(value)) {
    CommandLineError::print(verbose,
                            "ObjectAlignmentInBytes (" INTX_FORMAT ") must be "
                            "power of 2\n",
                            value);
    return Flag::VIOLATES_CONSTRAINT;
  }
  // In case page size is very small.
  if (value >= (intx)os::vm_page_size()) {
    CommandLineError::print(verbose,
                            "ObjectAlignmentInBytes (" INTX_FORMAT ") must be "
                            "less than page size " INTX_FORMAT "\n",
                            value, (intx)os::vm_page_size());
    return Flag::VIOLATES_CONSTRAINT;
  }
  return Flag::SUCCESS;
}

// Need to enforce the padding not to break the existing field alignments.
// It is sufficient to check against the largest type size.
Flag::Error ContendedPaddingWidthConstraintFunc(intx value, bool verbose) {
  if ((value != 0) && ((value % BytesPerLong) != 0)) {
    CommandLineError::print(verbose,
                            "ContendedPaddingWidth (" INTX_FORMAT ") must be "
                            "a multiple of %d\n",
                            value, BytesPerLong);
    return Flag::VIOLATES_CONSTRAINT;
  } else {
    return Flag::SUCCESS;
  }
}
