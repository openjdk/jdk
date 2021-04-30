/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "gc/z/zAddressSpaceLimit.hpp"
#include "gc/z/zArguments.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHeuristics.hpp"
#include "gc/shared/gcArguments.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"

void ZArguments::initialize_alignments() {
  SpaceAlignment = ZGranuleSize;
  HeapAlignment = SpaceAlignment;
}

void ZArguments::initialize() {
  GCArguments::initialize();

  // Check mark stack size
  const size_t mark_stack_space_limit = ZAddressSpaceLimit::mark_stack();
  if (ZMarkStackSpaceLimit > mark_stack_space_limit) {
    if (!FLAG_IS_DEFAULT(ZMarkStackSpaceLimit)) {
      vm_exit_during_initialization("ZMarkStackSpaceLimit too large for limited address space");
    }
    FLAG_SET_DEFAULT(ZMarkStackSpaceLimit, mark_stack_space_limit);
  }

  // Enable NUMA by default
  if (FLAG_IS_DEFAULT(UseNUMA)) {
    FLAG_SET_DEFAULT(UseNUMA, true);
  }

  // Select number of parallel threads
  if (FLAG_IS_DEFAULT(ParallelGCThreads)) {
    FLAG_SET_DEFAULT(ParallelGCThreads, ZHeuristics::nparallel_workers());
  }

  if (ParallelGCThreads == 0) {
    vm_exit_during_initialization("The flag -XX:+UseZGC can not be combined with -XX:ParallelGCThreads=0");
  }

  // Select number of concurrent threads
  if (FLAG_IS_DEFAULT(ConcGCThreads)) {
    FLAG_SET_DEFAULT(ConcGCThreads, ZHeuristics::nconcurrent_workers());
  }

  if (ConcGCThreads == 0) {
    vm_exit_during_initialization("The flag -XX:+UseZGC can not be combined with -XX:ConcGCThreads=0");
  }

  // Backwards compatible alias for ZCollectionIntervalMajor
  if (!FLAG_IS_DEFAULT(ZCollectionInterval)) {
    FLAG_SET_ERGO_IF_DEFAULT(ZCollectionIntervalMajor, ZCollectionInterval);
  }

  if (!FLAG_IS_DEFAULT(ZTenuringThreshold) && ZTenuringThreshold != -1) {
    FLAG_SET_ERGO_IF_DEFAULT(MaxTenuringThreshold, ZTenuringThreshold);
    if (MaxTenuringThreshold == 0) {
      FLAG_SET_ERGO_IF_DEFAULT(AlwaysTenure, true);
    }
  }

  if (FLAG_IS_DEFAULT(MaxTenuringThreshold)) {
    uint tenuring_threshold;
    for (tenuring_threshold = 0; tenuring_threshold < MaxTenuringThreshold; ++tenuring_threshold) {
      // Reduce the number of object ages, if the resulting garbage is too high
      const size_t medium_page_overhead = ZPageSizeMedium * tenuring_threshold;
      const size_t small_page_overhead = ZPageSizeSmall * ConcGCThreads * tenuring_threshold;
      if (small_page_overhead + medium_page_overhead >= ZHeuristics::significant_young_overhead()) {
        break;
      }
    }
    FLAG_SET_DEFAULT(MaxTenuringThreshold, tenuring_threshold);
    if (tenuring_threshold == 0 && FLAG_IS_DEFAULT(AlwaysTenure)) {
      // Some flag constraint function says AlwaysTenure must be true iff MaxTenuringThreshold == 0
      FLAG_SET_DEFAULT(AlwaysTenure, true);
    }
  }

  if (!FLAG_IS_DEFAULT(ZTenuringThreshold) && NeverTenure) {
    vm_exit_during_initialization(err_msg("ZTenuringThreshold and NeverTenure are incompatible"));
  }

  // Large page size must match granule size
  if (!FLAG_IS_DEFAULT(LargePageSizeInBytes) && LargePageSizeInBytes != ZGranuleSize) {
    vm_exit_during_initialization(err_msg("Incompatible -XX:LargePageSizeInBytes, only "
                                          SIZE_FORMAT "M large pages are supported by ZGC",
                                          ZGranuleSize / M));
  }

  if (!FLAG_IS_DEFAULT(ZTenuringThreshold) && ZTenuringThreshold > static_cast<int>(MaxTenuringThreshold)) {
    vm_exit_during_initialization(err_msg("ZTenuringThreshold must be be within bounds of "
                                          "MaxTenuringThreshold"));
  }

  // The heuristics used when UseDynamicNumberOfGCThreads is
  // enabled defaults to using a ZAllocationSpikeTolerance of 1.
  if (UseDynamicNumberOfGCThreads && FLAG_IS_DEFAULT(ZAllocationSpikeTolerance)) {
    FLAG_SET_DEFAULT(ZAllocationSpikeTolerance, 1);
  }

#ifdef COMPILER2
  // Enable loop strip mining by default
  if (FLAG_IS_DEFAULT(UseCountedLoopSafepoints)) {
    FLAG_SET_DEFAULT(UseCountedLoopSafepoints, true);
    if (FLAG_IS_DEFAULT(LoopStripMiningIter)) {
      FLAG_SET_DEFAULT(LoopStripMiningIter, 1000);
    }
  }
#endif

  // CompressedOops not supported
  FLAG_SET_DEFAULT(UseCompressedOops, false);

  // More events
  if (FLAG_IS_DEFAULT(LogEventsBufferEntries)) {
    FLAG_SET_DEFAULT(LogEventsBufferEntries, 250);
  }

  // Verification before startup and after exit not (yet) supported
  FLAG_SET_DEFAULT(VerifyDuringStartup, false);
  FLAG_SET_DEFAULT(VerifyBeforeExit, false);

  if (VerifyBeforeGC || VerifyDuringGC || VerifyAfterGC) {
    FLAG_SET_DEFAULT(ZVerifyRoots, true);
    FLAG_SET_DEFAULT(ZVerifyObjects, true);
  }

#ifdef ASSERT
  // This check slows down testing too much. Turn it off for now.
  if (FLAG_IS_DEFAULT(VerifyDependencies)) {
    FLAG_SET_DEFAULT(VerifyDependencies, false);
  }
#endif
}

size_t ZArguments::heap_virtual_to_physical_ratio() {
  return ZVirtualToPhysicalRatio;
}

size_t ZArguments::conservative_max_heap_alignment() {
  return 0;
}

CollectedHeap* ZArguments::create_heap() {
  return new ZCollectedHeap();
}

bool ZArguments::is_supported() const {
  return is_os_supported();
}
