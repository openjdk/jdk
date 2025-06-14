/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gcArguments.hpp"
#include "gc/z/zAddressSpaceLimit.hpp"
#include "gc/z/zArguments.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHeuristics.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"

void ZArguments::initialize_alignments() {
  SpaceAlignment = ZGranuleSize;
  HeapAlignment = SpaceAlignment;
}

void ZArguments::initialize_heap_flags_and_sizes() {
  GCArguments::initialize_heap_flags_and_sizes();

  if (!FLAG_IS_CMDLINE(MaxHeapSize) &&
      !FLAG_IS_CMDLINE(MaxRAMPercentage) &&
      !FLAG_IS_CMDLINE(SoftMaxHeapSize)) {
    // We are really just guessing how much memory the program needs.
    // When that is the case, we don't want the soft and hard limits to be the same
    // as it can cause flakyness in the number of GC threads used, in order to keep
    // to a random number we just pulled out of thin air.
    FLAG_SET_ERGO(SoftMaxHeapSize, MaxHeapSize * 90 / 100);
  }
}

void ZArguments::select_max_gc_threads() {
  // Select number of parallel threads
  if (FLAG_IS_DEFAULT(ParallelGCThreads)) {
    FLAG_SET_DEFAULT(ParallelGCThreads, ZHeuristics::nparallel_workers());
  }

  if (ParallelGCThreads == 0) {
    vm_exit_during_initialization("The flag -XX:+UseZGC can not be combined with -XX:ParallelGCThreads=0");
  }

  // The max number of concurrent threads we heuristically want for a generation
  uint max_nworkers_generation;

  if (FLAG_IS_DEFAULT(ConcGCThreads)) {
    max_nworkers_generation = ZHeuristics::nconcurrent_workers();

    // Computed max number of GC threads at a time in the machine
    uint max_nworkers = max_nworkers_generation;

    if (!FLAG_IS_DEFAULT(ZYoungGCThreads)) {
      max_nworkers = MAX2(max_nworkers, ZYoungGCThreads);
    }

    if (!FLAG_IS_DEFAULT(ZOldGCThreads)) {
      max_nworkers = MAX2(max_nworkers, ZOldGCThreads);
    }

    FLAG_SET_DEFAULT(ConcGCThreads, max_nworkers);
  } else {
    max_nworkers_generation = ConcGCThreads;
  }

  if (FLAG_IS_DEFAULT(ZYoungGCThreads)) {
    if (UseDynamicNumberOfGCThreads) {
      FLAG_SET_ERGO(ZYoungGCThreads, max_nworkers_generation);
    } else {
      const uint static_young_threads = MAX2(uint(max_nworkers_generation * 0.9), 1u);
      FLAG_SET_ERGO(ZYoungGCThreads, static_young_threads);
    }
  }

  if (FLAG_IS_DEFAULT(ZOldGCThreads)) {
    if (UseDynamicNumberOfGCThreads) {
      FLAG_SET_ERGO(ZOldGCThreads, max_nworkers_generation);
    } else {
      const uint static_old_threads = MAX2(ConcGCThreads - ZYoungGCThreads, 1u);
      FLAG_SET_ERGO(ZOldGCThreads, static_old_threads);
    }
  }

  if (ConcGCThreads == 0) {
    vm_exit_during_initialization("The flag -XX:+UseZGC can not be combined with -XX:ConcGCThreads=0");
  }

  if (ZYoungGCThreads > ConcGCThreads) {
    vm_exit_during_initialization("The flag -XX:ZYoungGCThreads can't be higher than -XX:ConcGCThreads");
  } else if (ZYoungGCThreads == 0) {
    vm_exit_during_initialization("The flag -XX:ZYoungGCThreads can't be lower than 1");
  }

  if (ZOldGCThreads > ConcGCThreads) {
    vm_exit_during_initialization("The flag -XX:ZOldGCThreads can't be higher than -XX:ConcGCThreads");
  } else if (ZOldGCThreads == 0) {
    vm_exit_during_initialization("The flag -XX:ZOldGCThreads can't be lower than 1");
  }
}

void ZArguments::initialize() {
  GCArguments::initialize();

  // NUMA settings
  if (FLAG_IS_DEFAULT(ZFakeNUMA)) {
    // Enable NUMA by default
    if (FLAG_IS_DEFAULT(UseNUMA)) {
      FLAG_SET_DEFAULT(UseNUMA, true);
    }
  } else {
    if (UseNUMA) {
      if (!FLAG_IS_DEFAULT(UseNUMA)) {
        warning("ZFakeNUMA is enabled; turning off UseNUMA");
      }
      FLAG_SET_ERGO(UseNUMA, false);
    }
  }

  select_max_gc_threads();

  // Backwards compatible alias for ZCollectionIntervalMajor
  if (!FLAG_IS_DEFAULT(ZCollectionInterval)) {
    FLAG_SET_ERGO_IF_DEFAULT(ZCollectionIntervalMajor, ZCollectionInterval);
  }

  // Set an initial TLAB size to avoid depending on the current capacity
  if (FLAG_IS_DEFAULT(TLABSize)) {
    FLAG_SET_DEFAULT(TLABSize, 256*K);
  }

  // Set medium page size here because MaxTenuringThreshold may use it.
  ZHeuristics::set_medium_page_size();

  if (!FLAG_IS_DEFAULT(ZTenuringThreshold) && ZTenuringThreshold != -1) {
    FLAG_SET_ERGO_IF_DEFAULT(MaxTenuringThreshold, (uint)ZTenuringThreshold);
    if (MaxTenuringThreshold == 0) {
      FLAG_SET_ERGO_IF_DEFAULT(AlwaysTenure, true);
    }
  }

  if (FLAG_IS_DEFAULT(MaxTenuringThreshold)) {
    uint tenuring_threshold;
    for (tenuring_threshold = 0; tenuring_threshold < MaxTenuringThreshold; ++tenuring_threshold) {
      // Reduce the number of object ages, if the resulting garbage is too high
      const size_t per_age_overhead = ZHeuristics::relocation_headroom();
      if (per_age_overhead * tenuring_threshold >= ZHeuristics::significant_young_overhead()) {
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
                                          "%zuM large pages are supported by ZGC",
                                          ZGranuleSize / M));
  }

  if (!FLAG_IS_DEFAULT(ZTenuringThreshold) && ZTenuringThreshold > static_cast<int>(MaxTenuringThreshold)) {
    vm_exit_during_initialization(err_msg("ZTenuringThreshold must be be within bounds of "
                                          "MaxTenuringThreshold"));
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

size_t ZArguments::conservative_max_heap_alignment() {
  return 0;
}

size_t ZArguments::heap_virtual_to_physical_ratio() {
  return ZVirtualToPhysicalRatio;
}

CollectedHeap* ZArguments::create_heap() {
  // ZCollectedHeap has an alignment greater than or equal to ZCacheLineSize,
  // which may be larger than std::max_align_t. Instead of using operator new,
  // align the storage manually and construct the ZCollectedHeap using operator
  // placement new.

  static_assert(alignof(ZCollectedHeap) >= ZCacheLineSize,
                "ZCollectedHeap is no longer ZCacheLineSize aligned");

  // Allocate aligned storage for ZCollectedHeap
  const size_t alignment = alignof(ZCollectedHeap);
  const size_t size = sizeof(ZCollectedHeap);
  void* const addr = reinterpret_cast<void*>(ZUtils::alloc_aligned_unfreeable(alignment, size));

  // Construct ZCollectedHeap in the aligned storage
  return ::new (addr) ZCollectedHeap();
}

bool ZArguments::is_supported() const {
  return is_os_supported();
}
