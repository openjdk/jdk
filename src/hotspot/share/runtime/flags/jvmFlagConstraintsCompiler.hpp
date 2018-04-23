/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_JVMFLAGCONSTRAINTSCOMPILER_HPP
#define SHARE_VM_RUNTIME_JVMFLAGCONSTRAINTSCOMPILER_HPP

#include "runtime/flags/jvmFlag.hpp"

/*
 * Here we have compiler arguments constraints functions, which are called automatically
 * whenever flag's value changes. If the constraint fails the function should return
 * an appropriate error value.
 */

JVMFlag::Error AliasLevelConstraintFunc(intx value, bool verbose);

JVMFlag::Error CICompilerCountConstraintFunc(intx value, bool verbose);

JVMFlag::Error AllocatePrefetchDistanceConstraintFunc(intx value, bool verbose);

JVMFlag::Error AllocatePrefetchInstrConstraintFunc(intx value, bool verbose);

JVMFlag::Error AllocatePrefetchStepSizeConstraintFunc(intx value, bool verbose);

JVMFlag::Error CompileThresholdConstraintFunc(intx value, bool verbose);

JVMFlag::Error OnStackReplacePercentageConstraintFunc(intx value, bool verbose);

JVMFlag::Error CodeCacheSegmentSizeConstraintFunc(uintx value, bool verbose);

JVMFlag::Error CompilerThreadPriorityConstraintFunc(intx value, bool verbose);

JVMFlag::Error CodeEntryAlignmentConstraintFunc(intx value, bool verbose);

JVMFlag::Error OptoLoopAlignmentConstraintFunc(intx value, bool verbose);

JVMFlag::Error ArraycopyDstPrefetchDistanceConstraintFunc(uintx value, bool verbose);

JVMFlag::Error ArraycopySrcPrefetchDistanceConstraintFunc(uintx value, bool verbose);

JVMFlag::Error TypeProfileLevelConstraintFunc(uintx value, bool verbose);

JVMFlag::Error InitArrayShortSizeConstraintFunc(intx value, bool verbose);

#ifdef COMPILER2
JVMFlag::Error InteriorEntryAlignmentConstraintFunc(intx value, bool verbose);

JVMFlag::Error NodeLimitFudgeFactorConstraintFunc(intx value, bool verbose);
#endif

JVMFlag::Error RTMTotalCountIncrRateConstraintFunc(int value, bool verbose);

#endif /* SHARE_VM_RUNTIME_JVMFLAGCONSTRAINTSCOMPILER_HPP */
