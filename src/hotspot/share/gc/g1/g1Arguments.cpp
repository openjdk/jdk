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
#include "gc/g1/g1Arguments.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1HeapVerifier.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/cardTableRS.hpp"
#include "gc/shared/gcArguments.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"

static const double MaxRamFractionForYoung = 0.8;
size_t G1Arguments::MaxMemoryForYoung;

static size_t calculate_heap_alignment(size_t space_alignment) {
  size_t card_table_alignment = CardTableRS::ct_max_alignment_constraint();
  size_t page_size = UseLargePages ? os::large_page_size() : os::vm_page_size();
  return MAX3(card_table_alignment, space_alignment, page_size);
}

void G1Arguments::initialize_alignments() {
  // Set up the region size and associated fields.
  //
  // There is a circular dependency here. We base the region size on the heap
  // size, but the heap size should be aligned with the region size. To get
  // around this we use the unaligned values for the heap.
  HeapRegion::setup_heap_region_size(InitialHeapSize, MaxHeapSize);
  HeapRegionRemSet::setup_remset_size();

  SpaceAlignment = HeapRegion::GrainBytes;
  HeapAlignment = calculate_heap_alignment(SpaceAlignment);
}

size_t G1Arguments::conservative_max_heap_alignment() {
  return HeapRegion::max_region_size();
}

void G1Arguments::initialize_verification_types() {
  if (strlen(VerifyGCType) > 0) {
    const char delimiter[] = " ,\n";
    size_t length = strlen(VerifyGCType);
    char* type_list = NEW_C_HEAP_ARRAY(char, length + 1, mtInternal);
    strncpy(type_list, VerifyGCType, length + 1);
    char* save_ptr;

    char* token = strtok_r(type_list, delimiter, &save_ptr);
    while (token != NULL) {
      parse_verification_type(token);
      token = strtok_r(NULL, delimiter, &save_ptr);
    }
    FREE_C_HEAP_ARRAY(char, type_list);
  }
}

void G1Arguments::parse_verification_type(const char* type) {
  if (strcmp(type, "young-normal") == 0) {
    G1HeapVerifier::enable_verification_type(G1HeapVerifier::G1VerifyYoungNormal);
  } else if (strcmp(type, "concurrent-start") == 0) {
    G1HeapVerifier::enable_verification_type(G1HeapVerifier::G1VerifyConcurrentStart);
  } else if (strcmp(type, "mixed") == 0) {
    G1HeapVerifier::enable_verification_type(G1HeapVerifier::G1VerifyMixed);
  } else if (strcmp(type, "remark") == 0) {
    G1HeapVerifier::enable_verification_type(G1HeapVerifier::G1VerifyRemark);
  } else if (strcmp(type, "cleanup") == 0) {
    G1HeapVerifier::enable_verification_type(G1HeapVerifier::G1VerifyCleanup);
  } else if (strcmp(type, "full") == 0) {
    G1HeapVerifier::enable_verification_type(G1HeapVerifier::G1VerifyFull);
  } else {
    log_warning(gc, verify)("VerifyGCType: '%s' is unknown. Available types are: "
                            "young-normal, concurrent-start, mixed, remark, cleanup and full", type);
  }
}

void G1Arguments::initialize() {
  GCArguments::initialize();
  assert(UseG1GC, "Error");
  FLAG_SET_DEFAULT(ParallelGCThreads, WorkerPolicy::parallel_worker_threads());
  if (ParallelGCThreads == 0) {
    assert(!FLAG_IS_DEFAULT(ParallelGCThreads), "The default value for ParallelGCThreads should not be 0.");
    vm_exit_during_initialization("The flag -XX:+UseG1GC can not be combined with -XX:ParallelGCThreads=0", NULL);
  }

  // When dumping the CDS archive we want to reduce fragmentation by
  // triggering a full collection. To get as low fragmentation as
  // possible we only use one worker thread.
  if (DumpSharedSpaces) {
    FLAG_SET_ERGO(ParallelGCThreads, 1);
  }

  if (FLAG_IS_DEFAULT(G1ConcRefinementThreads)) {
    FLAG_SET_ERGO(G1ConcRefinementThreads, ParallelGCThreads);
  }

  // MarkStackSize will be set (if it hasn't been set by the user)
  // when concurrent marking is initialized.
  // Its value will be based upon the number of parallel marking threads.
  // But we do set the maximum mark stack size here.
  if (FLAG_IS_DEFAULT(MarkStackSizeMax)) {
    FLAG_SET_DEFAULT(MarkStackSizeMax, 128 * TASKQUEUE_SIZE);
  }

  if (FLAG_IS_DEFAULT(GCTimeRatio) || GCTimeRatio == 0) {
    // In G1, we want the default GC overhead goal to be higher than
    // it is for PS, or the heap might be expanded too aggressively.
    // We set it here to ~8%.
    FLAG_SET_DEFAULT(GCTimeRatio, 12);
  }

  // Below, we might need to calculate the pause time interval based on
  // the pause target. When we do so we are going to give G1 maximum
  // flexibility and allow it to do pauses when it needs to. So, we'll
  // arrange that the pause interval to be pause time target + 1 to
  // ensure that a) the pause time target is maximized with respect to
  // the pause interval and b) we maintain the invariant that pause
  // time target < pause interval. If the user does not want this
  // maximum flexibility, they will have to set the pause interval
  // explicitly.

  if (FLAG_IS_DEFAULT(MaxGCPauseMillis)) {
    // The default pause time target in G1 is 200ms
    FLAG_SET_DEFAULT(MaxGCPauseMillis, 200);
  }

  // Then, if the interval parameter was not set, set it according to
  // the pause time target (this will also deal with the case when the
  // pause time target is the default value).
  if (FLAG_IS_DEFAULT(GCPauseIntervalMillis)) {
    FLAG_SET_DEFAULT(GCPauseIntervalMillis, MaxGCPauseMillis + 1);
  }

  if (FLAG_IS_DEFAULT(ParallelRefProcEnabled) && ParallelGCThreads > 1) {
    FLAG_SET_DEFAULT(ParallelRefProcEnabled, true);
  }

  log_trace(gc)("MarkStackSize: %uk  MarkStackSizeMax: %uk", (unsigned int) (MarkStackSize / K), (uint) (MarkStackSizeMax / K));

  // By default do not let the target stack size to be more than 1/4 of the entries
  if (FLAG_IS_DEFAULT(GCDrainStackTargetSize)) {
    FLAG_SET_ERGO(GCDrainStackTargetSize, MIN2(GCDrainStackTargetSize, (uintx)TASKQUEUE_SIZE / 4));
  }

#ifdef COMPILER2
  // Enable loop strip mining to offer better pause time guarantees
  if (FLAG_IS_DEFAULT(UseCountedLoopSafepoints)) {
    FLAG_SET_DEFAULT(UseCountedLoopSafepoints, true);
    if (FLAG_IS_DEFAULT(LoopStripMiningIter)) {
      FLAG_SET_DEFAULT(LoopStripMiningIter, 1000);
    }
  }
#endif

  initialize_verification_types();
}

static size_t calculate_reasonable_max_memory_for_young(FormatBuffer<100> &calc_str, double max_ram_fraction_for_young) {
  julong phys_mem;
  // If MaxRam is specified, we use that as maximum physical memory available.
  if (FLAG_IS_DEFAULT(MaxRAM)) {
    phys_mem = os::physical_memory();
    calc_str.append("Physical_Memory");
  } else {
    phys_mem = (julong)MaxRAM;
    calc_str.append("MaxRAM");
  }

  julong reasonable_max = phys_mem;

  // If either MaxRAMFraction or MaxRAMPercentage is specified, we use them to calculate
  // reasonable max size of young generation.
  if (!FLAG_IS_DEFAULT(MaxRAMFraction)) {
    reasonable_max = (julong)(phys_mem / MaxRAMFraction);
    calc_str.append(" / MaxRAMFraction");
  }  else if (!FLAG_IS_DEFAULT(MaxRAMPercentage)) {
    reasonable_max = (julong)((phys_mem * MaxRAMPercentage) / 100);
    calc_str.append(" * MaxRAMPercentage / 100");
  }  else {
    // We use our own fraction to calculate max size of young generation.
    reasonable_max = phys_mem * max_ram_fraction_for_young;
    calc_str.append(" * %0.2f", max_ram_fraction_for_young);
  }

  return (size_t)reasonable_max;
}

void G1Arguments::initialize_heap_flags_and_sizes() {
  if (AllocateOldGenAt != NULL) {
    initialize_heterogeneous();
  }

  GCArguments::initialize_heap_flags_and_sizes();
}

void G1Arguments::initialize_heterogeneous() {
  FormatBuffer<100> calc_str("");

  MaxMemoryForYoung = calculate_reasonable_max_memory_for_young(calc_str, MaxRamFractionForYoung);

  if (MaxNewSize > MaxMemoryForYoung) {
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      log_warning(gc, ergo)("Setting MaxNewSize to " SIZE_FORMAT " based on dram available (calculation = align(%s))",
                            MaxMemoryForYoung, calc_str.buffer());
    } else {
      log_info(gc, ergo)("Setting MaxNewSize to " SIZE_FORMAT " based on dram available (calculation = align(%s)). "
                         "Dram usage can be lowered by setting MaxNewSize to a lower value", MaxMemoryForYoung, calc_str.buffer());
    }
    MaxNewSize = MaxMemoryForYoung;
  }
  if (NewSize > MaxMemoryForYoung) {
    if (FLAG_IS_CMDLINE(NewSize)) {
      log_warning(gc, ergo)("Setting NewSize to " SIZE_FORMAT " based on dram available (calculation = align(%s))",
                            MaxMemoryForYoung, calc_str.buffer());
    }
    NewSize = MaxMemoryForYoung;
  }

}

CollectedHeap* G1Arguments::create_heap() {
  return new G1CollectedHeap();
}

bool G1Arguments::is_heterogeneous_heap() {
  return AllocateOldGenAt != NULL;
}

size_t G1Arguments::reasonable_max_memory_for_young() {
  return MaxMemoryForYoung;
}

size_t G1Arguments::heap_reserved_size_bytes() {
  return (is_heterogeneous_heap() ? 2 : 1) * MaxHeapSize;
}

size_t G1Arguments::heap_max_size_bytes() {
  return MaxHeapSize;
}