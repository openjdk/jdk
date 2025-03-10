/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1YOUNGGCPOSTEVACUATETASKS_HPP
#define SHARE_GC_G1_G1YOUNGGCPOSTEVACUATETASKS_HPP

#include "gc/g1/g1BatchedTask.hpp"

class FreeCSetStats;

class G1CollectedHeap;
class G1EvacFailureRegions;
class G1EvacInfo;
class G1ParScanThreadStateSet;

// First set of post evacuate collection set tasks containing ("s" means serial):
// - Merge PSS (s)
// - Recalculate Used (s)
// - Sample Collection Set Candidates (s)
// - Clear Card Table
// - Restore evac failure regions (on evacuation failure)
class G1PostEvacuateCollectionSetCleanupTask1 : public G1BatchedTask {
  class MergePssTask;
  class RecalculateUsedTask;
  class SampleCollectionSetCandidatesTask;
  class RestoreEvacFailureRegionsTask;

public:
  G1PostEvacuateCollectionSetCleanupTask1(G1ParScanThreadStateSet* per_thread_states,
                                          G1EvacFailureRegions* evac_failure_regions);
};

// Second set of post evacuate collection set tasks containing (s means serial):
// - Eagerly Reclaim Humongous Objects (s)
// - Update Derived Pointers (s)
// - Clear Retained Region Data (on evacuation failure)
// - Redirty Logged Cards
// - Free Collection Set
// - Resize TLABs
// - Reset the reusable PartialArrayStateManager.
class G1PostEvacuateCollectionSetCleanupTask2 : public G1BatchedTask {
  class EagerlyReclaimHumongousObjectsTask;
#if COMPILER2_OR_JVMCI
  class UpdateDerivedPointersTask;
#endif

  class ProcessEvacuationFailedRegionsTask;
  class RedirtyLoggedCardsTask;
  class FreeCollectionSetTask;
  class ResizeTLABsTask;
  class ResetPartialArrayStateManagerTask;

public:
  G1PostEvacuateCollectionSetCleanupTask2(G1ParScanThreadStateSet* per_thread_states,
                                          G1EvacInfo* evacuation_info,
                                          G1EvacFailureRegions* evac_failure_regions);
};

#endif // SHARE_GC_G1_G1YOUNGGCPOSTEVACUATETASKS_HPP
