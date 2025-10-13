/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/serial/generation.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/genArguments.hpp"
#include "logging/log.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"

size_t MinNewSize = 0;

size_t MinOldSize = 0;
size_t MaxOldSize = 0;

// If InitialHeapSize or MinHeapSize is not set on cmdline, this variable,
// together with NewSize, is used to derive them.
// Using the same value when it was a configurable flag to avoid breakage.
// See more in JDK-8346005
size_t OldSize = ScaleForWordSize(4*M);

size_t GenArguments::conservative_max_heap_alignment() { return (size_t)Generation::GenGrain; }

static size_t young_gen_size_lower_bound() {
  // The young generation must be aligned and have room for eden + two survivors
  return 3 * SpaceAlignment;
}

static size_t old_gen_size_lower_bound() {
  return SpaceAlignment;
}

size_t GenArguments::scale_by_NewRatio_aligned(size_t base_size, size_t alignment) {
  return align_down_bounded(base_size / (NewRatio + 1), alignment);
}

static size_t bound_minus_alignment(size_t desired_size,
                                    size_t maximum_size,
                                    size_t alignment) {
  size_t max_minus = maximum_size - alignment;
  return MIN2(desired_size, max_minus);
}

void GenArguments::initialize_alignments() {
  // Initialize card size before initializing alignments
  CardTable::initialize_card_size();
  SpaceAlignment = (size_t)Generation::GenGrain;
  HeapAlignment = compute_heap_alignment();
}

void GenArguments::initialize_heap_flags_and_sizes() {
  GCArguments::initialize_heap_flags_and_sizes();

  assert(SpaceAlignment != 0, "Generation alignment not set up properly");
  assert(HeapAlignment >= SpaceAlignment,
         "HeapAlignment: %zu less than SpaceAlignment: %zu",
         HeapAlignment, SpaceAlignment);
  assert(HeapAlignment % SpaceAlignment == 0,
         "HeapAlignment: %zu not aligned by SpaceAlignment: %zu",
         HeapAlignment, SpaceAlignment);

  // All generational heaps have a young gen; handle those flags here

  // Make sure the heap is large enough for two generations
  size_t smallest_new_size = young_gen_size_lower_bound();
  size_t smallest_heap_size = align_up(smallest_new_size + old_gen_size_lower_bound(),
                                       HeapAlignment);
  if (MaxHeapSize < smallest_heap_size) {
    FLAG_SET_ERGO(MaxHeapSize, smallest_heap_size);
  }
  // If needed, synchronize MinHeapSize size and InitialHeapSize
  if (MinHeapSize < smallest_heap_size) {
    FLAG_SET_ERGO(MinHeapSize, smallest_heap_size);
    if (InitialHeapSize < MinHeapSize) {
      FLAG_SET_ERGO(InitialHeapSize, smallest_heap_size);
    }
  }

  // Make sure NewSize allows an old generation to fit even if set on the command line
  if (FLAG_IS_CMDLINE(NewSize) && NewSize >= InitialHeapSize) {
    size_t revised_new_size = bound_minus_alignment(NewSize, InitialHeapSize, SpaceAlignment);
    log_warning(gc, ergo)("NewSize (%zuk) is equal to or greater than initial heap size (%zuk).  A new "
                          "NewSize of %zuk will be used to accomodate an old generation.",
                          NewSize/K, InitialHeapSize/K, revised_new_size/K);
    FLAG_SET_ERGO(NewSize, revised_new_size);
  }

  // Now take the actual NewSize into account. We will silently increase NewSize
  // if the user specified a smaller or unaligned value.
  size_t bounded_new_size = bound_minus_alignment(NewSize, MaxHeapSize, SpaceAlignment);
  bounded_new_size = MAX2(smallest_new_size, align_down(bounded_new_size, SpaceAlignment));
  if (bounded_new_size != NewSize) {
    FLAG_SET_ERGO(NewSize, bounded_new_size);
  }
  MinNewSize = smallest_new_size;

  if (!FLAG_IS_DEFAULT(MaxNewSize)) {
    if (MaxNewSize >= MaxHeapSize) {
      // Make sure there is room for an old generation
      size_t smaller_max_new_size = MaxHeapSize - SpaceAlignment;
      if (FLAG_IS_CMDLINE(MaxNewSize)) {
        log_warning(gc, ergo)("MaxNewSize (%zuk) is equal to or greater than the entire "
                              "heap (%zuk).  A new max generation size of %zuk will be used.",
                              MaxNewSize/K, MaxHeapSize/K, smaller_max_new_size/K);
      }
      FLAG_SET_ERGO(MaxNewSize, smaller_max_new_size);
      if (NewSize > MaxNewSize) {
        FLAG_SET_ERGO(NewSize, MaxNewSize);
      }
    } else if (MaxNewSize < NewSize) {
      FLAG_SET_ERGO(MaxNewSize, NewSize);
    } else if (!is_aligned(MaxNewSize, SpaceAlignment)) {
      FLAG_SET_ERGO(MaxNewSize, align_down(MaxNewSize, SpaceAlignment));
    }
  }

  if (NewSize > MaxNewSize) {
    // At this point this should only happen if the user specifies a large NewSize and/or
    // a small (but not too small) MaxNewSize.
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      log_warning(gc, ergo)("NewSize (%zuk) is greater than the MaxNewSize (%zuk). "
                            "A new max generation size of %zuk will be used.",
                            NewSize/K, MaxNewSize/K, NewSize/K);
    }
    FLAG_SET_ERGO(MaxNewSize, NewSize);
  }

  if (SurvivorRatio < 1 || NewRatio < 1) {
    vm_exit_during_initialization("Invalid young gen ratio specified");
  }

  OldSize = old_gen_size_lower_bound();

  // Adjust NewSize and OldSize or MaxHeapSize to match each other
  if (NewSize + OldSize > MaxHeapSize) {
    if (FLAG_IS_CMDLINE(MaxHeapSize)) {
      // Somebody has set a maximum heap size with the intention that we should not
      // exceed it. Adjust New/OldSize as necessary.
      size_t calculated_size = NewSize + OldSize;
      double shrink_factor = (double) MaxHeapSize / calculated_size;
      size_t smaller_new_size = align_down((size_t)(NewSize * shrink_factor), SpaceAlignment);
      FLAG_SET_ERGO(NewSize, MAX2(young_gen_size_lower_bound(), smaller_new_size));

      // OldSize is already aligned because above we aligned MaxHeapSize to
      // HeapAlignment, and we just made sure that NewSize is aligned to
      // SpaceAlignment. In initialize_flags() we verified that HeapAlignment
      // is a multiple of SpaceAlignment.
      OldSize = MaxHeapSize - NewSize;
    } else {
      FLAG_SET_ERGO(MaxHeapSize, align_up(NewSize + OldSize, HeapAlignment));
    }
  }

  DEBUG_ONLY(assert_flags();)
}

// Values set on the command line win over any ergonomically
// set command line parameters.
// Ergonomic choice of parameters are done before this
// method is called.  Values for command line parameters such as NewSize
// and MaxNewSize feed those ergonomic choices into this method.
// This method makes the final generation sizings consistent with
// themselves and with overall heap sizings.
// In the absence of explicitly set command line flags, policies
// such as the use of NewRatio are used to size the generation.

void GenArguments::initialize_size_info() {
  GCArguments::initialize_size_info();

  size_t max_young_size = MaxNewSize;

  // Determine maximum size of the young generation.

  if (FLAG_IS_DEFAULT(MaxNewSize)) {
    max_young_size = scale_by_NewRatio_aligned(MaxHeapSize, SpaceAlignment);
    // Bound the maximum size by NewSize below (since it historically
    // would have been NewSize and because the NewRatio calculation could
    // yield a size that is too small) and bound it by MaxNewSize above.
    // Ergonomics plays here by previously calculating the desired
    // NewSize and MaxNewSize.
    max_young_size = clamp(max_young_size, NewSize, MaxNewSize);
  }

  // Given the maximum young size, determine the initial and
  // minimum young sizes.
  size_t initial_young_size = NewSize;

  if (MaxHeapSize == InitialHeapSize) {
    // The maximum and initial heap sizes are the same so the generation's
    // initial size must be the same as it maximum size. Use NewSize as the
    // size if set on command line.
    max_young_size = FLAG_IS_CMDLINE(NewSize) ? NewSize : max_young_size;
    initial_young_size = max_young_size;

    // Also update the minimum size if min == initial == max.
    if (MaxHeapSize == MinHeapSize) {
      MinNewSize = max_young_size;
    }
  } else {
    if (FLAG_IS_CMDLINE(NewSize)) {
      // If NewSize is set on the command line, we should use it as
      // the initial size, but make sure it is within the heap bounds.
      initial_young_size =
        MIN2(max_young_size, bound_minus_alignment(NewSize, InitialHeapSize, SpaceAlignment));
      MinNewSize = bound_minus_alignment(initial_young_size, MinHeapSize, SpaceAlignment);
    } else {
      // For the case where NewSize is not set on the command line, use
      // NewRatio to size the initial generation size. Use the current
      // NewSize as the floor, because if NewRatio is overly large, the resulting
      // size can be too small.
      initial_young_size =
        clamp(scale_by_NewRatio_aligned(InitialHeapSize, SpaceAlignment), NewSize, max_young_size);

      // Derive MinNewSize from MinHeapSize
      MinNewSize = MIN2(scale_by_NewRatio_aligned(MinHeapSize, SpaceAlignment), initial_young_size);
    }
  }

  log_trace(gc, heap)("1: Minimum young %zu  Initial young %zu  Maximum young %zu",
                      MinNewSize, initial_young_size, max_young_size);

  // At this point the minimum, initial and maximum sizes
  // of the overall heap and of the young generation have been determined.
  // The maximum old size can be determined from the maximum young
  // and maximum heap size since no explicit flags exist
  // for setting the old generation maximum.
  MaxOldSize = MAX2(MaxHeapSize - max_young_size, SpaceAlignment);
  MinOldSize = MIN3(MaxOldSize,
                    InitialHeapSize - initial_young_size,
                    MinHeapSize - MinNewSize);

  size_t initial_old_size = clamp(InitialHeapSize - initial_young_size, MinOldSize, MaxOldSize);;

  // The initial generation sizes should match the initial heap size,
  // if not issue a warning and resize the generations. This behavior
  // differs from JDK8 where the generation sizes have higher priority
  // than the initial heap size.
  if ((initial_old_size + initial_young_size) != InitialHeapSize) {
    log_warning(gc, ergo)("Inconsistency between generation sizes and heap size, resizing "
                          "the generations to fit the heap.");

    size_t desired_young_size = InitialHeapSize - initial_old_size;
    if (InitialHeapSize < initial_old_size) {
      // Old want all memory, use minimum for young and rest for old
      initial_young_size = MinNewSize;
      initial_old_size = InitialHeapSize - MinNewSize;
    } else if (desired_young_size > max_young_size) {
      // Need to increase both young and old generation
      initial_young_size = max_young_size;
      initial_old_size = InitialHeapSize - max_young_size;
    } else if (desired_young_size < MinNewSize) {
      // Need to decrease both young and old generation
      initial_young_size = MinNewSize;
      initial_old_size = InitialHeapSize - MinNewSize;
    } else {
      // The young generation boundaries allow us to only update the
      // young generation.
      initial_young_size = desired_young_size;
    }

    log_trace(gc, heap)("2: Minimum young %zu  Initial young %zu  Maximum young %zu",
                        MinNewSize, initial_young_size, max_young_size);
  }

  // Write back to flags if necessary.
  if (NewSize != initial_young_size) {
    FLAG_SET_ERGO(NewSize, initial_young_size);
  }

  if (MaxNewSize != max_young_size) {
    FLAG_SET_ERGO(MaxNewSize, max_young_size);
  }

  if (OldSize != initial_old_size) {
    OldSize = initial_old_size;
  }

  log_trace(gc, heap)("Minimum old %zu  Initial old %zu  Maximum old %zu",
                      MinOldSize, OldSize, MaxOldSize);

  DEBUG_ONLY(assert_size_info();)
}

#ifdef ASSERT
void GenArguments::assert_flags() {
  GCArguments::assert_flags();
  assert(NewSize >= MinNewSize, "Ergonomics decided on a too small young gen size");
  assert(NewSize <= MaxNewSize, "Ergonomics decided on incompatible initial and maximum young gen sizes");
  assert(FLAG_IS_DEFAULT(MaxNewSize) || MaxNewSize < MaxHeapSize, "Ergonomics decided on incompatible maximum young gen and heap sizes");
  assert(NewSize % SpaceAlignment == 0, "NewSize alignment");
  assert(FLAG_IS_DEFAULT(MaxNewSize) || MaxNewSize % SpaceAlignment == 0, "MaxNewSize alignment");
  assert(OldSize + NewSize <= MaxHeapSize, "Ergonomics decided on incompatible generation and heap sizes");
  assert(OldSize % SpaceAlignment == 0, "OldSize alignment");
}

void GenArguments::assert_size_info() {
  GCArguments::assert_size_info();
  // GenArguments::initialize_size_info may update the MaxNewSize
  assert(MaxNewSize < MaxHeapSize, "Ergonomics decided on incompatible maximum young and heap sizes");
  assert(MinNewSize <= NewSize, "Ergonomics decided on incompatible minimum and initial young gen sizes");
  assert(NewSize <= MaxNewSize, "Ergonomics decided on incompatible initial and maximum young gen sizes");
  assert(MinNewSize % SpaceAlignment == 0, "_min_young_size alignment");
  assert(NewSize % SpaceAlignment == 0, "_initial_young_size alignment");
  assert(MaxNewSize % SpaceAlignment == 0, "MaxNewSize alignment");
  assert(MinNewSize <= bound_minus_alignment(MinNewSize, MinHeapSize, SpaceAlignment),
      "Ergonomics made minimum young generation larger than minimum heap");
  assert(NewSize <=  bound_minus_alignment(NewSize, InitialHeapSize, SpaceAlignment),
      "Ergonomics made initial young generation larger than initial heap");
  assert(MaxNewSize <= bound_minus_alignment(MaxNewSize, MaxHeapSize, SpaceAlignment),
      "Ergonomics made maximum young generation lager than maximum heap");
  assert(MinOldSize <= OldSize, "Ergonomics decided on incompatible minimum and initial old gen sizes");
  assert(OldSize <= MaxOldSize, "Ergonomics decided on incompatible initial and maximum old gen sizes");
  assert(MaxOldSize % SpaceAlignment == 0, "MaxOldSize alignment");
  assert(OldSize % SpaceAlignment == 0, "OldSize alignment");
  assert(MaxHeapSize <= (MaxNewSize + MaxOldSize), "Total maximum heap sizes must be sum of generation maximum sizes");
  assert(MinNewSize + MinOldSize <= MinHeapSize, "Minimum generation sizes exceed minimum heap size");
  assert(NewSize + OldSize == InitialHeapSize, "Initial generation sizes should match initial heap size");
  assert(MaxNewSize + MaxOldSize == MaxHeapSize, "Maximum generation sizes should match maximum heap size");
}
#endif // ASSERT
