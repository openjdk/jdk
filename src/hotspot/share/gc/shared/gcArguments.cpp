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

#include "gc/shared/cardTable.hpp"
#include "gc/shared/gcArguments.hpp"
#include "gc/shared/genArguments.hpp"
#include "logging/log.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/os.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"

size_t HeapAlignment = 0;
size_t SpaceAlignment = 0;

void GCArguments::initialize() {
  if (FullGCALot && FLAG_IS_DEFAULT(MarkSweepAlwaysCompactCount)) {
    MarkSweepAlwaysCompactCount = 1;  // Move objects every gc.
  }

  if (GCTimeLimit == 100) {
    // Turn off gc-overhead-limit-exceeded checks
    FLAG_SET_DEFAULT(UseGCOverheadLimit, false);
  }

  if (MinHeapFreeRatio == 100) {
    // Keeping the heap 100% free is hard ;-) so limit it to 99%.
    FLAG_SET_ERGO(MinHeapFreeRatio, 99);
  }

  if (!ClassUnloading) {
    // If class unloading is disabled, also disable concurrent class unloading.
    FLAG_SET_CMDLINE(ClassUnloadingWithConcurrentMark, false);
  }
}

size_t GCArguments::limit_heap_by_allocatable_memory(size_t limit) {
  // Limits the given heap size by the maximum amount of virtual
  // memory this process is currently allowed to use. It also takes
  // the virtual-to-physical ratio of the current GC into account.
  size_t fraction = MaxVirtMemFraction * heap_virtual_to_physical_ratio();
  size_t max_allocatable = os::commit_memory_limit();

  return MIN2(limit, max_allocatable / fraction);
}

// Use static initialization to get the default before parsing
static const size_t DefaultHeapBaseMinAddress = HeapBaseMinAddress;

static size_t clamp_by_size_t_max(uint64_t value) {
  return (size_t)MIN2(value, (uint64_t)std::numeric_limits<size_t>::max());
}

void GCArguments::set_heap_size() {
  // Check if the user has configured any limit on the amount of RAM we may use.
  bool has_ram_limit = !FLAG_IS_DEFAULT(MaxRAMPercentage) ||
                       !FLAG_IS_DEFAULT(MinRAMPercentage) ||
                       !FLAG_IS_DEFAULT(InitialRAMPercentage);

  const physical_memory_size_type avail_mem = os::physical_memory();

  // If the maximum heap size has not been set with -Xmx, then set it as
  // fraction of the size of physical memory, respecting the maximum and
  // minimum sizes of the heap.
  if (FLAG_IS_DEFAULT(MaxHeapSize)) {
    uint64_t min_memory = (uint64_t)(((double)avail_mem * MinRAMPercentage) / 100);
    uint64_t max_memory = (uint64_t)(((double)avail_mem * MaxRAMPercentage) / 100);

    const size_t reasonable_min = clamp_by_size_t_max(min_memory);
    size_t reasonable_max = clamp_by_size_t_max(max_memory);

    if (reasonable_min < MaxHeapSize) {
      // Small physical memory, so use a minimum fraction of it for the heap
      reasonable_max = reasonable_min;
    } else {
      // Not-small physical memory, so require a heap at least
      // as large as MaxHeapSize
      reasonable_max = MAX2(reasonable_max, MaxHeapSize);
    }

    if (!FLAG_IS_DEFAULT(ErgoHeapSizeLimit) && ErgoHeapSizeLimit != 0) {
      // Limit the heap size to ErgoHeapSizeLimit
      reasonable_max = MIN2(reasonable_max, ErgoHeapSizeLimit);
    }

    reasonable_max = limit_heap_by_allocatable_memory(reasonable_max);

    if (!FLAG_IS_DEFAULT(InitialHeapSize)) {
      // An initial heap size was specified on the command line,
      // so be sure that the maximum size is consistent.  Done
      // after call to limit_heap_by_allocatable_memory because that
      // method might reduce the allocation size.
      reasonable_max = MAX2(reasonable_max, InitialHeapSize);
    } else if (!FLAG_IS_DEFAULT(MinHeapSize)) {
      reasonable_max = MAX2(reasonable_max, MinHeapSize);
    }

#ifdef _LP64
    if (UseCompressedOops) {
      // HeapBaseMinAddress can be greater than default but not less than.
      if (!FLAG_IS_DEFAULT(HeapBaseMinAddress)) {
        if (HeapBaseMinAddress < DefaultHeapBaseMinAddress) {
          // matches compressed oops printing flags
          log_debug(gc, heap, coops)("HeapBaseMinAddress must be at least %zu "
                                     "(%zuG) which is greater than value given %zu",
                                     DefaultHeapBaseMinAddress,
                                     DefaultHeapBaseMinAddress/G,
                                     HeapBaseMinAddress);
          FLAG_SET_ERGO(HeapBaseMinAddress, DefaultHeapBaseMinAddress);
        }
      }

      uintptr_t heap_end = HeapBaseMinAddress + MaxHeapSize;
      uintptr_t max_coop_heap = Arguments::max_heap_for_compressed_oops();

      // Limit the heap size to the maximum possible when using compressed oops
      if (heap_end < max_coop_heap) {
        // Heap should be above HeapBaseMinAddress to get zero based compressed
        // oops but it should be not less than default MaxHeapSize.
        max_coop_heap -= HeapBaseMinAddress;
      }

      // If the user has configured any limit on the amount of RAM we may use,
      // then disable compressed oops if the calculated max exceeds max_coop_heap
      // and UseCompressedOops was not specified.
      if (reasonable_max > max_coop_heap) {
        if (FLAG_IS_ERGO(UseCompressedOops) && has_ram_limit) {
          log_debug(gc, heap, coops)("UseCompressedOops disabled due to "
                                     "max heap %zu > compressed oop heap %zu. "
                                     "Please check the setting of MaxRAMPercentage %5.2f.",
                                     reasonable_max, (size_t)max_coop_heap, MaxRAMPercentage);
          FLAG_SET_ERGO(UseCompressedOops, false);
        } else {
          reasonable_max = max_coop_heap;
        }
      }
    }
#endif // _LP64

    log_trace(gc, heap)("  Maximum heap size %zu", reasonable_max);
    FLAG_SET_ERGO(MaxHeapSize, reasonable_max);
  }

  // If the minimum or initial heap_size have not been set or requested to be set
  // ergonomically, set them accordingly.
  if (InitialHeapSize == 0 || MinHeapSize == 0) {
    size_t reasonable_minimum = clamp_by_size_t_max((uint64_t)OldSize + (uint64_t)NewSize);
    reasonable_minimum = MIN2(reasonable_minimum, MaxHeapSize);
    reasonable_minimum = limit_heap_by_allocatable_memory(reasonable_minimum);

    if (InitialHeapSize == 0) {
      uint64_t initial_memory = (uint64_t)(((double)avail_mem * InitialRAMPercentage) / 100);
      size_t reasonable_initial = clamp_by_size_t_max(initial_memory);
      reasonable_initial = limit_heap_by_allocatable_memory(reasonable_initial);

      reasonable_initial = MAX3(reasonable_initial, reasonable_minimum, MinHeapSize);
      reasonable_initial = MIN2(reasonable_initial, MaxHeapSize);

      FLAG_SET_ERGO(InitialHeapSize, (size_t)reasonable_initial);
      log_trace(gc, heap)("  Initial heap size %zu", InitialHeapSize);
    }

    // If the minimum heap size has not been set (via -Xms or -XX:MinHeapSize),
    // synchronize with InitialHeapSize to avoid errors with the default value.
    if (MinHeapSize == 0) {
      FLAG_SET_ERGO(MinHeapSize, MIN2(reasonable_minimum, InitialHeapSize));
      log_trace(gc, heap)("  Minimum heap size %zu", MinHeapSize);
    }
  }
}

void GCArguments::initialize_heap_sizes() {
  initialize_alignments();
  initialize_heap_flags_and_sizes();
  initialize_size_info();
}

#ifdef ASSERT
void GCArguments::assert_flags() {
  assert(InitialHeapSize <= MaxHeapSize, "Ergonomics decided on incompatible initial and maximum heap sizes");
  assert(InitialHeapSize % HeapAlignment == 0, "InitialHeapSize alignment");
  assert(MaxHeapSize % HeapAlignment == 0, "MaxHeapSize alignment");
}

void GCArguments::assert_size_info() {
  assert(MaxHeapSize >= MinHeapSize, "Ergonomics decided on incompatible minimum and maximum heap sizes");
  assert(InitialHeapSize >= MinHeapSize, "Ergonomics decided on incompatible initial and minimum heap sizes");
  assert(MaxHeapSize >= InitialHeapSize, "Ergonomics decided on incompatible initial and maximum heap sizes");
  assert(MinHeapSize % HeapAlignment == 0, "MinHeapSize alignment");
  assert(InitialHeapSize % HeapAlignment == 0, "InitialHeapSize alignment");
  assert(MaxHeapSize % HeapAlignment == 0, "MaxHeapSize alignment");
}
#endif // ASSERT

void GCArguments::initialize_size_info() {
  log_debug(gc, heap)("Minimum heap %zu  Initial heap %zu  Maximum heap %zu",
                      MinHeapSize, InitialHeapSize, MaxHeapSize);

  DEBUG_ONLY(assert_size_info();)
}

void GCArguments::initialize_heap_flags_and_sizes() {
  assert(SpaceAlignment != 0, "Space alignment not set up properly");
  assert(HeapAlignment != 0, "Heap alignment not set up properly");
  assert(HeapAlignment >= SpaceAlignment,
         "HeapAlignment: %zu less than SpaceAlignment: %zu",
         HeapAlignment, SpaceAlignment);
  assert(HeapAlignment % SpaceAlignment == 0,
         "HeapAlignment: %zu not aligned by SpaceAlignment: %zu",
         HeapAlignment, SpaceAlignment);

  if (FLAG_IS_CMDLINE(MaxHeapSize)) {
    if (FLAG_IS_CMDLINE(InitialHeapSize) && InitialHeapSize > MaxHeapSize) {
      vm_exit_during_initialization("Initial heap size set to a larger value than the maximum heap size");
    }
    if (FLAG_IS_CMDLINE(MinHeapSize) && MaxHeapSize < MinHeapSize) {
      vm_exit_during_initialization("Incompatible minimum and maximum heap sizes specified");
    }
  }

  if (FLAG_IS_CMDLINE(InitialHeapSize) && FLAG_IS_CMDLINE(MinHeapSize) &&
      InitialHeapSize < MinHeapSize) {
    vm_exit_during_initialization("Incompatible minimum and initial heap sizes specified");
  }

  // Check heap parameter properties
  if (MaxHeapSize < 2 * M) {
    vm_exit_during_initialization("Too small maximum heap");
  }
  if (InitialHeapSize < M) {
    vm_exit_during_initialization("Too small initial heap");
  }
  if (MinHeapSize < M) {
    vm_exit_during_initialization("Too small minimum heap");
  }

  // User inputs from -Xmx and -Xms must be aligned
  // Write back to flags if the values changed
  if (!is_aligned(MinHeapSize, HeapAlignment)) {
    FLAG_SET_ERGO(MinHeapSize, align_up(MinHeapSize, HeapAlignment));
  }
  if (!is_aligned(InitialHeapSize, HeapAlignment)) {
    FLAG_SET_ERGO(InitialHeapSize, align_up(InitialHeapSize, HeapAlignment));
  }
  if (!is_aligned(MaxHeapSize, HeapAlignment)) {
    FLAG_SET_ERGO(MaxHeapSize, align_up(MaxHeapSize, HeapAlignment));
  }

  if (!FLAG_IS_DEFAULT(InitialHeapSize) && InitialHeapSize > MaxHeapSize) {
    FLAG_SET_ERGO(MaxHeapSize, InitialHeapSize);
  } else if (!FLAG_IS_DEFAULT(MaxHeapSize) && InitialHeapSize > MaxHeapSize) {
    FLAG_SET_ERGO(InitialHeapSize, MaxHeapSize);
    if (InitialHeapSize < MinHeapSize) {
      FLAG_SET_ERGO(MinHeapSize, InitialHeapSize);
    }
  }

  if (FLAG_IS_DEFAULT(SoftMaxHeapSize)) {
    FLAG_SET_ERGO(SoftMaxHeapSize, MaxHeapSize);
  }

  FLAG_SET_ERGO(MinHeapDeltaBytes, align_up(MinHeapDeltaBytes, SpaceAlignment));

  if (checked_cast<uint>(ObjectAlignmentInBytes) > GCCardSizeInBytes) {
    err_msg message("ObjectAlignmentInBytes %u is larger than GCCardSizeInBytes %u",
                    ObjectAlignmentInBytes, GCCardSizeInBytes);
    vm_exit_during_initialization("Invalid combination of GCCardSizeInBytes and ObjectAlignmentInBytes",
                                  message);
  }

  DEBUG_ONLY(assert_flags();)
}

size_t GCArguments::heap_virtual_to_physical_ratio() {
  return 1;
}
