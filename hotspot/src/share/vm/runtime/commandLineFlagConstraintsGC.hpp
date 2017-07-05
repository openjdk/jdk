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

#ifndef SHARE_VM_RUNTIME_COMMANDLINEFLAGCONSTRAINTSGC_HPP
#define SHARE_VM_RUNTIME_COMMANDLINEFLAGCONSTRAINTSGC_HPP

#include "runtime/globals.hpp"
#include "utilities/globalDefinitions.hpp"

/*
 * Here we have GC arguments constraints functions, which are called automatically
 * whenever flag's value changes. If the constraint fails the function should return
 * an appropriate error value.
 */

Flag::Error YoungPLABSizeConstraintFunc(bool verbose, size_t* value);

Flag::Error MinHeapFreeRatioConstraintFunc(bool verbose, uintx* value);
Flag::Error MaxHeapFreeRatioConstraintFunc(bool verbose, uintx* value);

Flag::Error MinMetaspaceFreeRatioConstraintFunc(bool verbose, uintx* value);
Flag::Error MaxMetaspaceFreeRatioConstraintFunc(bool verbose, uintx* value);

Flag::Error InitialTenuringThresholdConstraintFunc(bool verbose, uintx* value);
Flag::Error MaxTenuringThresholdConstraintFunc(bool verbose, uintx* value);

#if INCLUDE_ALL_GCS
Flag::Error G1NewSizePercentConstraintFunc(bool verbose, uintx* value);
Flag::Error G1MaxNewSizePercentConstraintFunc(bool verbose, uintx* value);
#endif // INCLUDE_ALL_GCS

Flag::Error CMSOldPLABMinConstraintFunc(bool verbose, size_t* value);

Flag::Error CMSPrecleanDenominatorConstraintFunc(bool verbose, uintx* value);
Flag::Error CMSPrecleanNumeratorConstraintFunc(bool verbose, uintx* value);

Flag::Error SurvivorAlignmentInBytesConstraintFunc(bool verbose, intx* value);

#endif /* SHARE_VM_RUNTIME_COMMANDLINEFLAGCONSTRAINTSGC_HPP */
