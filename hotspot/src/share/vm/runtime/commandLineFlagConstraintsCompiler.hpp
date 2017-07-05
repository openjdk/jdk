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

#ifndef SHARE_VM_RUNTIME_COMMANDLINEFLAGCONSTRAINTSCOMPILER_HPP
#define SHARE_VM_RUNTIME_COMMANDLINEFLAGCONSTRAINTSCOMPILER_HPP

#include "runtime/globals.hpp"
#include "utilities/globalDefinitions.hpp"

/*
 * Here we have compiler arguments constraints functions, which are called automatically
 * whenever flag's value changes. If the constraint fails the function should return
 * an appropriate error value.
 */

Flag::Error AliasLevelConstraintFunc(intx value, bool verbose);

Flag::Error CICompilerCountConstraintFunc(intx value, bool verbose);

Flag::Error AllocatePrefetchDistanceConstraintFunc(intx value, bool verbose);

Flag::Error AllocatePrefetchInstrConstraintFunc(intx value, bool verbose);

Flag::Error AllocatePrefetchStepSizeConstraintFunc(intx value, bool verbose);

Flag::Error CompileThresholdConstraintFunc(intx value, bool verbose);

Flag::Error OnStackReplacePercentageConstraintFunc(intx value, bool verbose);

Flag::Error CodeCacheSegmentSizeConstraintFunc(uintx value, bool verbose);

Flag::Error CompilerThreadPriorityConstraintFunc(intx value, bool verbose);

Flag::Error CodeEntryAlignmentConstraintFunc(intx value, bool verbose);

Flag::Error OptoLoopAlignmentConstraintFunc(intx value, bool verbose);

Flag::Error ArraycopyDstPrefetchDistanceConstraintFunc(uintx value, bool verbose);

Flag::Error ArraycopySrcPrefetchDistanceConstraintFunc(uintx value, bool verbose);

Flag::Error TypeProfileLevelConstraintFunc(uintx value, bool verbose);

#ifdef COMPILER2
Flag::Error InteriorEntryAlignmentConstraintFunc(intx value, bool verbose);

Flag::Error NodeLimitFudgeFactorConstraintFunc(intx value, bool verbose);
#endif

#endif /* SHARE_VM_RUNTIME_COMMANDLINEFLAGCONSTRAINTSCOMPILER_HPP */
