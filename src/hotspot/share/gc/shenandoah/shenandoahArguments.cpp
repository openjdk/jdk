/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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
#include "gc/shared/gcArguments.inline.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "gc/shenandoah/shenandoahArguments.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"
#include "utilities/defaultStream.hpp"

void ShenandoahArguments::initialize() {
#if !(defined AARCH64 || defined AMD64 || defined IA32)
  vm_exit_during_initialization("Shenandoah GC is not supported on this platform.");
#endif

#ifdef IA32
  log_warning(gc)("Shenandoah GC is not fully supported on this platform:");
  log_warning(gc)("  concurrent modes are not supported, only STW cycles are enabled;");
  log_warning(gc)("  arch-specific barrier code is not implemented, disabling barriers;");

  FLAG_SET_DEFAULT(ShenandoahGCHeuristics,           "passive");

  FLAG_SET_DEFAULT(ShenandoahSATBBarrier,            false);
  FLAG_SET_DEFAULT(ShenandoahKeepAliveBarrier,       false);
  FLAG_SET_DEFAULT(ShenandoahStoreValEnqueueBarrier, false);
  FLAG_SET_DEFAULT(ShenandoahCASBarrier,             false);
  FLAG_SET_DEFAULT(ShenandoahCloneBarrier,           false);
#endif

#ifdef _LP64
  // The optimized ObjArrayChunkedTask takes some bits away from the full 64 addressable
  // bits, fail if we ever attempt to address more than we can. Only valid on 64bit.
  if (MaxHeapSize >= ObjArrayChunkedTask::max_addressable()) {
    jio_fprintf(defaultStream::error_stream(),
                "Shenandoah GC cannot address more than " SIZE_FORMAT " bytes, and " SIZE_FORMAT " bytes heap requested.",
                ObjArrayChunkedTask::max_addressable(), MaxHeapSize);
    vm_exit(1);
  }
#endif

  if (UseLargePages && (MaxHeapSize / os::large_page_size()) < ShenandoahHeapRegion::MIN_NUM_REGIONS) {
    warning("Large pages size (" SIZE_FORMAT "K) is too large to afford page-sized regions, disabling uncommit",
            os::large_page_size() / K);
    FLAG_SET_DEFAULT(ShenandoahUncommit, false);
  }

  // Enable NUMA by default. While Shenandoah is not NUMA-aware, enabling NUMA makes
  // storage allocation code NUMA-aware, and NUMA interleaving makes the storage
  // allocated in consistent manner (interleaving) to minimize run-to-run variance.
  if (FLAG_IS_DEFAULT(UseNUMA)) {
    FLAG_SET_DEFAULT(UseNUMA, true);
    FLAG_SET_DEFAULT(UseNUMAInterleaving, true);
  }

  FLAG_SET_DEFAULT(ParallelGCThreads,
                   WorkerPolicy::parallel_worker_threads());

  if (FLAG_IS_DEFAULT(ConcGCThreads)) {
    uint conc_threads = MAX2((uint) 1, ParallelGCThreads);
    FLAG_SET_DEFAULT(ConcGCThreads, conc_threads);
  }

  if (FLAG_IS_DEFAULT(ParallelRefProcEnabled)) {
    FLAG_SET_DEFAULT(ParallelRefProcEnabled, true);
  }

  if (ShenandoahRegionSampling && FLAG_IS_DEFAULT(PerfDataMemorySize)) {
    // When sampling is enabled, max out the PerfData memory to get more
    // Shenandoah data in, including Matrix.
    FLAG_SET_DEFAULT(PerfDataMemorySize, 2048*K);
  }

#ifdef COMPILER2
  // Shenandoah cares more about pause times, rather than raw throughput.
  if (FLAG_IS_DEFAULT(UseCountedLoopSafepoints)) {
    FLAG_SET_DEFAULT(UseCountedLoopSafepoints, true);
    if (FLAG_IS_DEFAULT(LoopStripMiningIter)) {
      FLAG_SET_DEFAULT(LoopStripMiningIter, 1000);
    }
  }
#ifdef ASSERT
  // C2 barrier verification is only reliable when all default barriers are enabled
  if (ShenandoahVerifyOptoBarriers &&
          (!FLAG_IS_DEFAULT(ShenandoahSATBBarrier)            ||
           !FLAG_IS_DEFAULT(ShenandoahKeepAliveBarrier)       ||
           !FLAG_IS_DEFAULT(ShenandoahStoreValEnqueueBarrier) ||
           !FLAG_IS_DEFAULT(ShenandoahCASBarrier)             ||
           !FLAG_IS_DEFAULT(ShenandoahCloneBarrier)
          )) {
    warning("Unusual barrier configuration, disabling C2 barrier verification");
    FLAG_SET_DEFAULT(ShenandoahVerifyOptoBarriers, false);
  }
#else
  guarantee(!ShenandoahVerifyOptoBarriers, "Should be disabled");
#endif // ASSERT
#endif // COMPILER2

  if (AlwaysPreTouch) {
    // Shenandoah handles pre-touch on its own. It does not let the
    // generic storage code to do the pre-touch before Shenandoah has
    // a chance to do it on its own.
    FLAG_SET_DEFAULT(AlwaysPreTouch, false);
    FLAG_SET_DEFAULT(ShenandoahAlwaysPreTouch, true);
  }

  // Record more information about previous cycles for improved debugging pleasure
  if (FLAG_IS_DEFAULT(LogEventsBufferEntries)) {
    FLAG_SET_DEFAULT(LogEventsBufferEntries, 250);
  }

  if (ShenandoahAlwaysPreTouch) {
    if (!FLAG_IS_DEFAULT(ShenandoahUncommit)) {
      warning("AlwaysPreTouch is enabled, disabling ShenandoahUncommit");
    }
    FLAG_SET_DEFAULT(ShenandoahUncommit, false);
  }

  if ((InitialHeapSize == MaxHeapSize) && ShenandoahUncommit) {
    log_info(gc)("Min heap equals to max heap, disabling ShenandoahUncommit");
    FLAG_SET_DEFAULT(ShenandoahUncommit, false);
  }

  // If class unloading is disabled, no unloading for concurrent cycles as well.
  // If class unloading is enabled, users should opt-in for unloading during
  // concurrent cycles.
  if (!ClassUnloading || !FLAG_IS_CMDLINE(ClassUnloadingWithConcurrentMark)) {
    log_info(gc)("Consider -XX:+ClassUnloadingWithConcurrentMark if large pause times "
                 "are observed on class-unloading sensitive workloads");
    FLAG_SET_DEFAULT(ClassUnloadingWithConcurrentMark, false);
  }

  // AOT is not supported yet
  if (UseAOT) {
    if (!FLAG_IS_DEFAULT(UseAOT)) {
      warning("Shenandoah does not support AOT at this moment, disabling UseAOT");
    }
    FLAG_SET_DEFAULT(UseAOT, false);
  }

  // TLAB sizing policy makes resizing decisions before each GC cycle. It averages
  // historical data, assigning more recent data the weight according to TLABAllocationWeight.
  // Current default is good for generational collectors that run frequent young GCs.
  // With Shenandoah, GC cycles are much less frequent, so we need we need sizing policy
  // to converge faster over smaller number of resizing decisions.
  if (FLAG_IS_DEFAULT(TLABAllocationWeight)) {
    FLAG_SET_DEFAULT(TLABAllocationWeight, 90);
  }

  // Shenandoah needs more C2 nodes to compile some methods with lots of barriers.
  // NodeLimitFudgeFactor needs to stay the same relative to MaxNodeLimit.
#ifdef COMPILER2
  if (FLAG_IS_DEFAULT(MaxNodeLimit)) {
    FLAG_SET_DEFAULT(MaxNodeLimit, MaxNodeLimit * 3);
    FLAG_SET_DEFAULT(NodeLimitFudgeFactor, NodeLimitFudgeFactor * 3);
  }
#endif

  // Make sure safepoint deadlocks are failing predictably. This sets up VM to report
  // fatal error after 10 seconds of wait for safepoint syncronization (not the VM
  // operation itself). There is no good reason why Shenandoah would spend that
  // much time synchronizing.
#ifdef ASSERT
  FLAG_SET_DEFAULT(SafepointTimeout, true);
  FLAG_SET_DEFAULT(SafepointTimeoutDelay, 10000);
  FLAG_SET_DEFAULT(AbortVMOnSafepointTimeout, true);
#endif
}

size_t ShenandoahArguments::conservative_max_heap_alignment() {
  size_t align = ShenandoahMaxRegionSize;
  if (UseLargePages) {
    align = MAX2(align, os::large_page_size());
  }
  return align;
}

CollectedHeap* ShenandoahArguments::create_heap() {
  return create_heap_with_policy<ShenandoahHeap, ShenandoahCollectorPolicy>();
}
