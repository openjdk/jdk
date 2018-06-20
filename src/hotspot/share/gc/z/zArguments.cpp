/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zArguments.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zCollectorPolicy.hpp"
#include "gc/z/zWorkers.hpp"
#include "gc/shared/gcArguments.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"

size_t ZArguments::conservative_max_heap_alignment() {
  return 0;
}

void ZArguments::initialize() {
  GCArguments::initialize();

  // Enable NUMA by default
  if (FLAG_IS_DEFAULT(UseNUMA)) {
    FLAG_SET_DEFAULT(UseNUMA, true);
  }

  // Disable biased locking by default
  if (FLAG_IS_DEFAULT(UseBiasedLocking)) {
    FLAG_SET_DEFAULT(UseBiasedLocking, false);
  }

  // Select number of parallel threads
  if (FLAG_IS_DEFAULT(ParallelGCThreads)) {
    FLAG_SET_DEFAULT(ParallelGCThreads, ZWorkers::calculate_nparallel());
  }

  if (ParallelGCThreads == 0) {
    vm_exit_during_initialization("The flag -XX:+UseZGC can not be combined with -XX:ParallelGCThreads=0");
  }

  // Select number of concurrent threads
  if (FLAG_IS_DEFAULT(ConcGCThreads)) {
    FLAG_SET_DEFAULT(ConcGCThreads, ZWorkers::calculate_nconcurrent());
  }

  if (ConcGCThreads == 0) {
    vm_exit_during_initialization("The flag -XX:+UseZGC can not be combined with -XX:ConcGCThreads=0");
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

  // To avoid asserts in set_active_workers()
  FLAG_SET_DEFAULT(UseDynamicNumberOfGCThreads, true);

  // CompressedOops/UseCompressedClassPointers not supported
  FLAG_SET_DEFAULT(UseCompressedOops, false);
  FLAG_SET_DEFAULT(UseCompressedClassPointers, false);

  // ClassUnloading not (yet) supported
  FLAG_SET_DEFAULT(ClassUnloading, false);
  FLAG_SET_DEFAULT(ClassUnloadingWithConcurrentMark, false);

  // Verification before startup and after exit not (yet) supported
  FLAG_SET_DEFAULT(VerifyDuringStartup, false);
  FLAG_SET_DEFAULT(VerifyBeforeExit, false);

  // Verification before heap iteration not (yet) supported, for the
  // same reason we need fixup_partial_loads
  FLAG_SET_DEFAULT(VerifyBeforeIteration, false);

  // Verification of stacks not (yet) supported, for the same reason
  // we need fixup_partial_loads
  DEBUG_ONLY(FLAG_SET_DEFAULT(VerifyStack, false));
}

CollectedHeap* ZArguments::create_heap() {
  return create_heap_with_policy<ZCollectedHeap, ZCollectorPolicy>();
}
