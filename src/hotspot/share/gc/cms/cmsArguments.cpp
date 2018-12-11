/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, Red Hat, Inc. and/or its affiliates.
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
#include "gc/cms/cmsArguments.hpp"
#include "gc/cms/cmsCollectorPolicy.hpp"
#include "gc/cms/cmsHeap.hpp"
#include "gc/cms/compactibleFreeListSpace.hpp"
#include "gc/shared/gcArguments.inline.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/defaultStream.hpp"

size_t CMSArguments::conservative_max_heap_alignment() {
  return GenCollectedHeap::conservative_max_heap_alignment();
}

void CMSArguments::set_parnew_gc_flags() {
  assert(!UseSerialGC && !UseParallelOldGC && !UseParallelGC && !UseG1GC,
         "control point invariant");
  assert(UseConcMarkSweepGC, "CMS is expected to be on here");

  if (FLAG_IS_DEFAULT(ParallelGCThreads)) {
    FLAG_SET_DEFAULT(ParallelGCThreads, WorkerPolicy::parallel_worker_threads());
    assert(ParallelGCThreads > 0, "We should always have at least one thread by default");
  } else if (ParallelGCThreads == 0) {
    jio_fprintf(defaultStream::error_stream(),
        "The ParNew GC can not be combined with -XX:ParallelGCThreads=0\n");
    vm_exit(1);
  }

  // By default YoungPLABSize and OldPLABSize are set to 4096 and 1024 respectively,
  // these settings are default for Parallel Scavenger. For ParNew+Tenured configuration
  // we set them to 1024 and 1024.
  // See CR 6362902.
  if (FLAG_IS_DEFAULT(YoungPLABSize)) {
    FLAG_SET_DEFAULT(YoungPLABSize, (intx)1024);
  }
  if (FLAG_IS_DEFAULT(OldPLABSize)) {
    FLAG_SET_DEFAULT(OldPLABSize, (intx)1024);
  }

  // When using compressed oops, we use local overflow stacks,
  // rather than using a global overflow list chained through
  // the klass word of the object's pre-image.
  if (UseCompressedOops && !ParGCUseLocalOverflow) {
    if (!FLAG_IS_DEFAULT(ParGCUseLocalOverflow)) {
      warning("Forcing +ParGCUseLocalOverflow: needed if using compressed references");
    }
    FLAG_SET_DEFAULT(ParGCUseLocalOverflow, true);
  }
  assert(ParGCUseLocalOverflow || !UseCompressedOops, "Error");
}

// Adjust some sizes to suit CMS and/or ParNew needs; these work well on
// sparc/solaris for certain applications, but would gain from
// further optimization and tuning efforts, and would almost
// certainly gain from analysis of platform and environment.
void CMSArguments::initialize() {
  GCArguments::initialize();

  assert(!UseSerialGC && !UseParallelOldGC && !UseParallelGC, "Error");
  assert(UseConcMarkSweepGC, "CMS is expected to be on here");

  // CMS space iteration, which FLSVerifyAllHeapreferences entails,
  // insists that we hold the requisite locks so that the iteration is
  // MT-safe. For the verification at start-up and shut-down, we don't
  // yet have a good way of acquiring and releasing these locks,
  // which are not visible at the CollectedHeap level. We want to
  // be able to acquire these locks and then do the iteration rather
  // than just disable the lock verification. This will be fixed under
  // bug 4788986.
  if (UseConcMarkSweepGC && FLSVerifyAllHeapReferences) {
    if (VerifyDuringStartup) {
      warning("Heap verification at start-up disabled "
              "(due to current incompatibility with FLSVerifyAllHeapReferences)");
      VerifyDuringStartup = false; // Disable verification at start-up
    }

    if (VerifyBeforeExit) {
      warning("Heap verification at shutdown disabled "
              "(due to current incompatibility with FLSVerifyAllHeapReferences)");
      VerifyBeforeExit = false; // Disable verification at shutdown
    }
  }

  if (!ClassUnloading) {
    FLAG_SET_CMDLINE(bool, CMSClassUnloadingEnabled, false);
  }

  // Set CMS global values
  CompactibleFreeListSpace::set_cms_values();

  // Turn off AdaptiveSizePolicy by default for cms until it is complete.
  disable_adaptive_size_policy("UseConcMarkSweepGC");

  set_parnew_gc_flags();

  size_t max_heap = align_down(MaxHeapSize,
                               CardTableRS::ct_max_alignment_constraint());

  // Now make adjustments for CMS
  intx   tenuring_default = (intx)6;
  size_t young_gen_per_worker = CMSYoungGenPerWorker;

  // Preferred young gen size for "short" pauses:
  // upper bound depends on # of threads and NewRatio.
  const size_t preferred_max_new_size_unaligned =
    MIN2(max_heap/(NewRatio+1), ScaleForWordSize(young_gen_per_worker * ParallelGCThreads));
  size_t preferred_max_new_size =
    align_up(preferred_max_new_size_unaligned, os::vm_page_size());

  // Unless explicitly requested otherwise, size young gen
  // for "short" pauses ~ CMSYoungGenPerWorker*ParallelGCThreads

  // If either MaxNewSize or NewRatio is set on the command line,
  // assume the user is trying to set the size of the young gen.
  if (FLAG_IS_DEFAULT(MaxNewSize) && FLAG_IS_DEFAULT(NewRatio)) {

    // Set MaxNewSize to our calculated preferred_max_new_size unless
    // NewSize was set on the command line and it is larger than
    // preferred_max_new_size.
    if (!FLAG_IS_DEFAULT(NewSize)) {   // NewSize explicitly set at command-line
      FLAG_SET_ERGO(size_t, MaxNewSize, MAX2(NewSize, preferred_max_new_size));
    } else {
      FLAG_SET_ERGO(size_t, MaxNewSize, preferred_max_new_size);
    }
    log_trace(gc, heap)("CMS ergo set MaxNewSize: " SIZE_FORMAT, MaxNewSize);

    // Code along this path potentially sets NewSize and OldSize
    log_trace(gc, heap)("CMS set min_heap_size: " SIZE_FORMAT " initial_heap_size:  " SIZE_FORMAT " max_heap: " SIZE_FORMAT,
                        Arguments::min_heap_size(), InitialHeapSize, max_heap);
    size_t min_new = preferred_max_new_size;
    if (FLAG_IS_CMDLINE(NewSize)) {
      min_new = NewSize;
    }
    if (max_heap > min_new && Arguments::min_heap_size() > min_new) {
      // Unless explicitly requested otherwise, make young gen
      // at least min_new, and at most preferred_max_new_size.
      if (FLAG_IS_DEFAULT(NewSize)) {
        FLAG_SET_ERGO(size_t, NewSize, MAX2(NewSize, min_new));
        FLAG_SET_ERGO(size_t, NewSize, MIN2(preferred_max_new_size, NewSize));
        log_trace(gc, heap)("CMS ergo set NewSize: " SIZE_FORMAT, NewSize);
      }
      // Unless explicitly requested otherwise, size old gen
      // so it's NewRatio x of NewSize.
      if (FLAG_IS_DEFAULT(OldSize)) {
        if (max_heap > NewSize) {
          FLAG_SET_ERGO(size_t, OldSize, MIN2(NewRatio*NewSize, max_heap - NewSize));
          log_trace(gc, heap)("CMS ergo set OldSize: " SIZE_FORMAT, OldSize);
        }
      }
    }
  }
  // Unless explicitly requested otherwise, definitely
  // promote all objects surviving "tenuring_default" scavenges.
  if (FLAG_IS_DEFAULT(MaxTenuringThreshold) &&
      FLAG_IS_DEFAULT(SurvivorRatio)) {
    FLAG_SET_ERGO(uintx, MaxTenuringThreshold, tenuring_default);
  }
  // If we decided above (or user explicitly requested)
  // `promote all' (via MaxTenuringThreshold := 0),
  // prefer minuscule survivor spaces so as not to waste
  // space for (non-existent) survivors
  if (FLAG_IS_DEFAULT(SurvivorRatio) && MaxTenuringThreshold == 0) {
    FLAG_SET_ERGO(uintx, SurvivorRatio, MAX2((uintx)1024, SurvivorRatio));
  }

  // OldPLABSize is interpreted in CMS as not the size of the PLAB in words,
  // but rather the number of free blocks of a given size that are used when
  // replenishing the local per-worker free list caches.
  if (FLAG_IS_DEFAULT(OldPLABSize)) {
    if (!FLAG_IS_DEFAULT(ResizeOldPLAB) && !ResizeOldPLAB) {
      // OldPLAB sizing manually turned off: Use a larger default setting,
      // unless it was manually specified. This is because a too-low value
      // will slow down scavenges.
      FLAG_SET_ERGO(size_t, OldPLABSize, CompactibleFreeListSpaceLAB::_default_static_old_plab_size); // default value before 6631166
    } else {
      FLAG_SET_DEFAULT(OldPLABSize, CompactibleFreeListSpaceLAB::_default_dynamic_old_plab_size); // old CMSParPromoteBlocksToClaim default
    }
  }

  // If either of the static initialization defaults have changed, note this
  // modification.
  if (!FLAG_IS_DEFAULT(OldPLABSize) || !FLAG_IS_DEFAULT(OldPLABWeight)) {
    CompactibleFreeListSpaceLAB::modify_initialization(OldPLABSize, OldPLABWeight);
  }

  log_trace(gc)("MarkStackSize: %uk  MarkStackSizeMax: %uk", (unsigned int) (MarkStackSize / K), (uint) (MarkStackSizeMax / K));
}

void CMSArguments::disable_adaptive_size_policy(const char* collector_name) {
  if (UseAdaptiveSizePolicy) {
    if (FLAG_IS_CMDLINE(UseAdaptiveSizePolicy)) {
      warning("Disabling UseAdaptiveSizePolicy; it is incompatible with %s.",
              collector_name);
    }
    FLAG_SET_DEFAULT(UseAdaptiveSizePolicy, false);
  }
}

CollectedHeap* CMSArguments::create_heap() {
  return create_heap_with_policy<CMSHeap, ConcurrentMarkSweepPolicy>();
}
