/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/parallel/parallelArguments.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/shared/adaptiveSizePolicy.hpp"
#include "gc/shared/fullGCForwarding.hpp"
#include "gc/shared/gcArguments.hpp"
#include "gc/shared/genArguments.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/powerOfTwo.hpp"

static size_t num_young_spaces() {
  // When using NUMA, we create one MutableNUMASpace for each NUMA node
  const size_t num_eden_spaces = UseNUMA ? os::numa_get_groups_num() : 1;

  // The young generation must have room for eden + two survivors
  return num_eden_spaces + 2;
}

static size_t num_old_spaces() {
  return 1;
}

void ParallelArguments::initialize_alignments() {
  // Initialize card size before initializing alignments
  CardTable::initialize_card_size();
  const size_t card_table_alignment = CardTable::ct_max_alignment_constraint();
  SpaceAlignment = ParallelScavengeHeap::default_space_alignment();

  if (UseLargePages) {
    const size_t total_spaces = num_young_spaces() + num_old_spaces();
    const size_t page_size =  os::page_size_for_region_unaligned(MaxHeapSize, total_spaces);
    ParallelScavengeHeap::set_desired_page_size(page_size);

    if (page_size == os::vm_page_size()) {
      log_warning(gc, heap)("MaxHeapSize (%zu) must be large enough for %zu * page-size; Disabling UseLargePages for heap",
                            MaxHeapSize, total_spaces);
    }

    if (page_size > SpaceAlignment) {
      SpaceAlignment = page_size;
    }

    HeapAlignment = lcm(page_size, card_table_alignment);

  } else {
    assert(is_aligned(SpaceAlignment, os::vm_page_size()), "");
    ParallelScavengeHeap::set_desired_page_size(os::vm_page_size());
    HeapAlignment = card_table_alignment;
  }
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

  if (InitialSurvivorRatio < MinSurvivorRatio) {
    if (FLAG_IS_CMDLINE(InitialSurvivorRatio)) {
      if (FLAG_IS_CMDLINE(MinSurvivorRatio)) {
        jio_fprintf(defaultStream::error_stream(),
          "Inconsistent MinSurvivorRatio vs InitialSurvivorRatio: %zu vs %zu\n", MinSurvivorRatio, InitialSurvivorRatio);
      }
      FLAG_SET_DEFAULT(MinSurvivorRatio, InitialSurvivorRatio);
    } else {
      FLAG_SET_DEFAULT(InitialSurvivorRatio, MinSurvivorRatio);
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

  if (FLAG_IS_DEFAULT(ParallelRefProcEnabled) && ParallelGCThreads > 1) {
    FLAG_SET_DEFAULT(ParallelRefProcEnabled, true);
  }

  FullGCForwarding::initialize_flags(heap_reserved_size_bytes());
}

size_t ParallelArguments::conservative_max_heap_alignment() {
  // The card marking array and the offset arrays for old generations are
  // committed in os pages as well. Make sure they are entirely full (to
  // avoid partial page problems), e.g. if 512 bytes heap corresponds to 1
  // byte entry and the os page size is 4096, the maximum heap size should
  // be 512*4096 = 2MB aligned.

  size_t alignment = CardTable::ct_max_alignment_constraint();

  if (UseLargePages) {
      // In presence of large pages we have to make sure that our
      // alignment is large page aware.
      alignment = lcm(os::large_page_size(), alignment);
  }

  return alignment;
}

CollectedHeap* ParallelArguments::create_heap() {
  return new ParallelScavengeHeap();
}

size_t ParallelArguments::young_gen_size_lower_bound() {
  return num_young_spaces() * SpaceAlignment;
}

size_t ParallelArguments::old_gen_size_lower_bound() {
  return num_old_spaces() * SpaceAlignment;
}

size_t ParallelArguments::heap_reserved_size_bytes() {
  return MaxHeapSize;
}
