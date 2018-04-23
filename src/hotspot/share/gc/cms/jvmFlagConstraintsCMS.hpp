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

#ifndef SHARE_GC_CMS_COMMANDLINEFLAGCONSTRAINTSCMS_HPP
#define SHARE_GC_CMS_COMMANDLINEFLAGCONSTRAINTSCMS_HPP

#include "runtime/globals.hpp"
#include "utilities/globalDefinitions.hpp"

// CMS Flag Constraints
JVMFlag::Error ParGCStridesPerThreadConstraintFunc(uintx value, bool verbose);
JVMFlag::Error ParGCCardsPerStrideChunkConstraintFunc(intx value, bool verbose);
JVMFlag::Error CMSOldPLABMinConstraintFunc(size_t value, bool verbose);
JVMFlag::Error CMSOldPLABMaxConstraintFunc(size_t value, bool verbose);
JVMFlag::Error CMSRescanMultipleConstraintFunc(size_t value, bool verbose);
JVMFlag::Error CMSConcMarkMultipleConstraintFunc(size_t value, bool verbose);
JVMFlag::Error CMSPrecleanDenominatorConstraintFunc(uintx value, bool verbose);
JVMFlag::Error CMSPrecleanNumeratorConstraintFunc(uintx value, bool verbose);
JVMFlag::Error CMSSamplingGrainConstraintFunc(uintx value, bool verbose);
JVMFlag::Error CMSWorkQueueDrainThresholdConstraintFunc(uintx value, bool verbose);
JVMFlag::Error CMSBitMapYieldQuantumConstraintFunc(size_t value, bool verbose);

// CMS Subconstraints
JVMFlag::Error ParallelGCThreadsConstraintFuncCMS(uint value, bool verbose);
JVMFlag::Error OldPLABSizeConstraintFuncCMS(size_t value, bool verbose);

#endif // SHARE_GC_CMS_COMMANDLINEFLAGCONSTRAINTSCMS_HPP
