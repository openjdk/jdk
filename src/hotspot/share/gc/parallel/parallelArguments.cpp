/*
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
#include "gc/parallel/parallelArguments.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/shared/adaptiveSizePolicy.hpp"
#include "gc/shared/collectorPolicy.hpp"
#include "gc/shared/gcArguments.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/defaultStream.hpp"

size_t ParallelArguments::conservative_max_heap_alignment() {
  return CollectorPolicy::compute_heap_alignment();
}

void ParallelArguments::initialize() {
  GCArguments::initialize();
  assert(UseParallelGC || UseParallelOldGC, "Error");
  // Enable ParallelOld unless it was explicitly disabled (cmd line or rc file).
  if (FLAG_IS_DEFAULT(UseParallelOldGC)) {
    FLAG_SET_DEFAULT(UseParallelOldGC, true);
  }
  FLAG_SET_DEFAULT(UseParallelGC, true);

  // If no heap maximum was requested explicitly, use some reasonable fraction
  // of the physical memory, up to a maximum of 1GB.
  FLAG_SET_DEFAULT(ParallelGCThreads,
                   Abstract_VM_Version::parallel_worker_threads());
  if (ParallelGCThreads == 0) {
    jio_fprintf(defaultStream::error_stream(),
        "The Parallel GC can not be combined with -XX:ParallelGCThreads=0\n");
    vm_exit(1);
  }

  if (UseAdaptiveSizePolicy) {
    // We don't want to limit adaptive heap sizing's freedom to adjust the heap
    // unless the user actually sets these flags.
    if (FLAG_IS_DEFAULT(MinHeapFreeRatio)) {
      FLAG_SET_DEFAULT(MinHeapFreeRatio, 0);
    }
    if (FLAG_IS_DEFAULT(MaxHeapFreeRatio)) {
      FLAG_SET_DEFAULT(MaxHeapFreeRatio, 100);
    }
  }

  // If InitialSurvivorRatio or MinSurvivorRatio were not specified, but the
  // SurvivorRatio has been set, reset their default values to SurvivorRatio +
  // 2.  By doing this we make SurvivorRatio also work for Parallel Scavenger.
  // See CR 6362902 for details.
  if (!FLAG_IS_DEFAULT(SurvivorRatio)) {
    if (FLAG_IS_DEFAULT(InitialSurvivorRatio)) {
       FLAG_SET_DEFAULT(InitialSurvivorRatio, SurvivorRatio + 2);
    }
    if (FLAG_IS_DEFAULT(MinSurvivorRatio)) {
      FLAG_SET_DEFAULT(MinSurvivorRatio, SurvivorRatio + 2);
    }
  }

  if (UseParallelOldGC) {
    // Par compact uses lower default values since they are treated as
    // minimums.  These are different defaults because of the different
    // interpretation and are not ergonomically set.
    if (FLAG_IS_DEFAULT(MarkSweepDeadRatio)) {
      FLAG_SET_DEFAULT(MarkSweepDeadRatio, 1);
    }
  }
}

CollectedHeap* ParallelArguments::create_heap() {
  return create_heap_with_policy<ParallelScavengeHeap, GenerationSizer>();
}
