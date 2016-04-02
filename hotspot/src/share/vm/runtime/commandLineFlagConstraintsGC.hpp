/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

Flag::Error ParallelGCThreadsConstraintFunc(uint value, bool verbose);
Flag::Error ConcGCThreadsConstraintFunc(uint value, bool verbose);
Flag::Error YoungPLABSizeConstraintFunc(size_t value, bool verbose);
Flag::Error OldPLABSizeConstraintFunc(size_t value, bool verbose);
Flag::Error MinHeapFreeRatioConstraintFunc(uintx value, bool verbose);
Flag::Error MaxHeapFreeRatioConstraintFunc(uintx value, bool verbose);
Flag::Error SoftRefLRUPolicyMSPerMBConstraintFunc(intx value, bool verbose);
Flag::Error MinMetaspaceFreeRatioConstraintFunc(uintx value, bool verbose);
Flag::Error MaxMetaspaceFreeRatioConstraintFunc(uintx value, bool verbose);
Flag::Error InitialTenuringThresholdConstraintFunc(uintx value, bool verbose);
Flag::Error MaxTenuringThresholdConstraintFunc(uintx value, bool verbose);

#if INCLUDE_ALL_GCS
Flag::Error G1RSetRegionEntriesConstraintFunc(intx value, bool verbose);
Flag::Error G1RSetSparseRegionEntriesConstraintFunc(intx value, bool verbose);
Flag::Error G1YoungSurvRateNumRegionsSummaryConstraintFunc(intx value, bool verbose);
Flag::Error G1HeapRegionSizeConstraintFunc(size_t value, bool verbose);
Flag::Error G1NewSizePercentConstraintFunc(uintx value, bool verbose);
Flag::Error G1MaxNewSizePercentConstraintFunc(uintx value, bool verbose);
#endif // INCLUDE_ALL_GCS

Flag::Error ParGCStridesPerThreadConstraintFunc(uintx value, bool verbose);
Flag::Error ParGCCardsPerStrideChunkConstraintFunc(intx value, bool verbose);
Flag::Error CMSOldPLABMinConstraintFunc(size_t value, bool verbose);
Flag::Error CMSOldPLABMaxConstraintFunc(size_t value, bool verbose);
Flag::Error MarkStackSizeConstraintFunc(size_t value, bool verbose);
Flag::Error CMSPrecleanDenominatorConstraintFunc(uintx value, bool verbose);
Flag::Error CMSPrecleanNumeratorConstraintFunc(uintx value, bool verbose);
Flag::Error CMSSamplingGrainConstraintFunc(uintx value, bool verbose);
Flag::Error CMSWorkQueueDrainThresholdConstraintFunc(uintx value, bool verbose);
Flag::Error MaxGCPauseMillisConstraintFunc(uintx value, bool verbose);
Flag::Error GCPauseIntervalMillisConstraintFunc(uintx value, bool verbose);
Flag::Error InitialBootClassLoaderMetaspaceSizeConstraintFunc(size_t value, bool verbose);
Flag::Error InitialHeapSizeConstraintFunc(size_t value, bool verbose);
Flag::Error MaxHeapSizeConstraintFunc(size_t value, bool verbose);
Flag::Error HeapBaseMinAddressConstraintFunc(size_t value, bool verbose);
Flag::Error NewSizeConstraintFunc(size_t value, bool verbose);
Flag::Error MinTLABSizeConstraintFunc(size_t value, bool verbose);
Flag::Error TLABSizeConstraintFunc(size_t value, bool verbose);
Flag::Error TLABWasteIncrementConstraintFunc(uintx value, bool verbose);
Flag::Error SurvivorRatioConstraintFunc(uintx value, bool verbose);
Flag::Error MetaspaceSizeConstraintFunc(size_t value, bool verbose);
Flag::Error MaxMetaspaceSizeConstraintFunc(size_t value, bool verbose);
Flag::Error SurvivorAlignmentInBytesConstraintFunc(intx value, bool verbose);

#endif /* SHARE_VM_RUNTIME_COMMANDLINEFLAGCONSTRAINTSGC_HPP */
