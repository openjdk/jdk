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

#ifndef SHARE_GC_SHARED_COMMANDLINEFLAGCONSTRAINTSGC_HPP
#define SHARE_GC_SHARED_COMMANDLINEFLAGCONSTRAINTSGC_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_CMSGC
#include "gc/cms/jvmFlagConstraintsCMS.hpp"
#endif
#if INCLUDE_G1GC
#include "gc/g1/jvmFlagConstraintsG1.hpp"
#endif
#if INCLUDE_PARALLELGC
#include "gc/parallel/jvmFlagConstraintsParallel.hpp"
#endif

/*
 * Here we have GC arguments constraints functions, which are called automatically
 * whenever flag's value changes. If the constraint fails the function should return
 * an appropriate error value.
 */

JVMFlag::Error ParallelGCThreadsConstraintFunc(uint value, bool verbose);
JVMFlag::Error ConcGCThreadsConstraintFunc(uint value, bool verbose);
JVMFlag::Error YoungPLABSizeConstraintFunc(size_t value, bool verbose);
JVMFlag::Error OldPLABSizeConstraintFunc(size_t value, bool verbose);
JVMFlag::Error MinHeapFreeRatioConstraintFunc(uintx value, bool verbose);
JVMFlag::Error MaxHeapFreeRatioConstraintFunc(uintx value, bool verbose);
JVMFlag::Error SoftRefLRUPolicyMSPerMBConstraintFunc(intx value, bool verbose);
JVMFlag::Error MarkStackSizeConstraintFunc(size_t value, bool verbose);
JVMFlag::Error MinMetaspaceFreeRatioConstraintFunc(uintx value, bool verbose);
JVMFlag::Error MaxMetaspaceFreeRatioConstraintFunc(uintx value, bool verbose);
JVMFlag::Error InitialTenuringThresholdConstraintFunc(uintx value, bool verbose);
JVMFlag::Error MaxTenuringThresholdConstraintFunc(uintx value, bool verbose);

JVMFlag::Error MaxGCPauseMillisConstraintFunc(uintx value, bool verbose);
JVMFlag::Error GCPauseIntervalMillisConstraintFunc(uintx value, bool verbose);
JVMFlag::Error InitialBootClassLoaderMetaspaceSizeConstraintFunc(size_t value, bool verbose);
JVMFlag::Error InitialHeapSizeConstraintFunc(size_t value, bool verbose);
JVMFlag::Error MaxHeapSizeConstraintFunc(size_t value, bool verbose);
JVMFlag::Error HeapBaseMinAddressConstraintFunc(size_t value, bool verbose);
JVMFlag::Error NewSizeConstraintFunc(size_t value, bool verbose);
JVMFlag::Error MinTLABSizeConstraintFunc(size_t value, bool verbose);
JVMFlag::Error TLABSizeConstraintFunc(size_t value, bool verbose);
JVMFlag::Error TLABWasteIncrementConstraintFunc(uintx value, bool verbose);
JVMFlag::Error SurvivorRatioConstraintFunc(uintx value, bool verbose);
JVMFlag::Error MetaspaceSizeConstraintFunc(size_t value, bool verbose);
JVMFlag::Error MaxMetaspaceSizeConstraintFunc(size_t value, bool verbose);
JVMFlag::Error SurvivorAlignmentInBytesConstraintFunc(intx value, bool verbose);

// Internal
JVMFlag::Error MaxPLABSizeBounds(const char* name, size_t value, bool verbose);

#endif // SHARE_GC_SHARED_COMMANDLINEFLAGCONSTRAINTSGC_HPP
