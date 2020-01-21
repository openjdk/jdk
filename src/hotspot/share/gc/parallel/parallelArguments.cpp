/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcArguments.hpp"
#include "gc/shared/genArguments.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "utilities/defaultStream.hpp"

static const double MaxRamFractionForYoung = 0.8;

size_t ParallelArguments::conservative_max_heap_alignment() {
  return compute_heap_alignment();
}

void ParallelArguments::initialize() {
  GCArguments::initialize();
  assert(UseParallelGC, "Error");

  // If no heap maximum was requested explicitly, use some reasonable fraction
  // of the physical memory, up to a maximum of 1GB.
  FLAG_SET_DEFAULT(ParallelGCThreads,
                   WorkerPolicy::parallel_worker_threads());
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

  // Par compact uses lower default values since they are treated as
  // minimums.  These are different defaults because of the different
  // interpretation and are not ergonomically set.
  if (FLAG_IS_DEFAULT(MarkSweepDeadRatio)) {
    FLAG_SET_DEFAULT(MarkSweepDeadRatio, 1);
  }
}

// The alignment used for boundary between young gen and old gen
static size_t default_gen_alignment() {
  return 64 * K * HeapWordSize;
}

void ParallelArguments::initialize_alignments() {
  SpaceAlignment = GenAlignment = default_gen_alignment();
  HeapAlignment = compute_heap_alignment();
}

void ParallelArguments::initialize_heap_flags_and_sizes_one_pass() {
  // Do basic sizing work
  GenArguments::initialize_heap_flags_and_sizes();

  // The survivor ratio's are calculated "raw", unlike the
  // default gc, which adds 2 to the ratio value. We need to
  // make sure the values are valid before using them.
  if (MinSurvivorRatio < 3) {
    FLAG_SET_ERGO(MinSurvivorRatio, 3);
  }

  if (InitialSurvivorRatio < 3) {
    FLAG_SET_ERGO(InitialSurvivorRatio, 3);
  }
}

void ParallelArguments::initialize_heap_flags_and_sizes() {
  if (is_heterogeneous_heap()) {
    initialize_heterogeneous();
  }

  initialize_heap_flags_and_sizes_one_pass();

  const size_t max_page_sz = os::page_size_for_region_aligned(MaxHeapSize, 8);
  const size_t min_pages = 4; // 1 for eden + 1 for each survivor + 1 for old
  const size_t min_page_sz = os::page_size_for_region_aligned(MinHeapSize, min_pages);
  const size_t page_sz = MIN2(max_page_sz, min_page_sz);

  // Can a page size be something else than a power of two?
  assert(is_power_of_2((intptr_t)page_sz), "must be a power of 2");
  size_t new_alignment = align_up(page_sz, GenAlignment);
  if (new_alignment != GenAlignment) {
    GenAlignment = new_alignment;
    SpaceAlignment = new_alignment;
    // Redo everything from the start
    initialize_heap_flags_and_sizes_one_pass();
  }
}

// Check the available dram memory to limit NewSize and MaxNewSize before
// calling base class initialize_flags().
void ParallelArguments::initialize_heterogeneous() {
  FormatBuffer<100> calc_str("");

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
  } else if (!FLAG_IS_DEFAULT(MaxRAMPercentage)) {
    reasonable_max = (julong)((phys_mem * MaxRAMPercentage) / 100);
    calc_str.append(" * MaxRAMPercentage / 100");
  } else {
    // We use our own fraction to calculate max size of young generation.
    reasonable_max = phys_mem * MaxRamFractionForYoung;
    calc_str.append(" * %0.2f", MaxRamFractionForYoung);
  }
  reasonable_max = align_up(reasonable_max, GenAlignment);

  if (MaxNewSize > reasonable_max) {
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      log_warning(gc, ergo)("Setting MaxNewSize to " SIZE_FORMAT " based on dram available (calculation = align(%s))",
                            (size_t)reasonable_max, calc_str.buffer());
    } else {
      log_info(gc, ergo)("Setting MaxNewSize to " SIZE_FORMAT " based on dram available (calculation = align(%s)). "
                         "Dram usage can be lowered by setting MaxNewSize to a lower value", (size_t)reasonable_max, calc_str.buffer());
    }
    MaxNewSize = reasonable_max;
  }
  if (NewSize > reasonable_max) {
    if (FLAG_IS_CMDLINE(NewSize)) {
      log_warning(gc, ergo)("Setting NewSize to " SIZE_FORMAT " based on dram available (calculation = align(%s))",
                            (size_t)reasonable_max, calc_str.buffer());
    }
    NewSize = reasonable_max;
  }
}

bool ParallelArguments::is_heterogeneous_heap() {
  return AllocateOldGenAt != NULL;
}

size_t ParallelArguments::heap_reserved_size_bytes() {
  if (!is_heterogeneous_heap() || !UseAdaptiveGCBoundary) {
    return MaxHeapSize;
  }

  // Heterogeneous heap and adaptive size gc boundary

  // This is the size that young gen can grow to, when UseAdaptiveGCBoundary is true.
  size_t max_yg_size = MaxHeapSize - MinOldSize;
  // This is the size that old gen can grow to, when UseAdaptiveGCBoundary is true.
  size_t max_old_size = MaxHeapSize - MinNewSize;

  return max_yg_size + max_old_size;
}

size_t ParallelArguments::heap_max_size_bytes() {
  return MaxHeapSize;
}

CollectedHeap* ParallelArguments::create_heap() {
  return new ParallelScavengeHeap();
}
