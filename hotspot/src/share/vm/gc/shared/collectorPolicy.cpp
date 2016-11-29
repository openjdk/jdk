/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/adaptiveSizePolicy.hpp"
#include "gc/shared/cardTableRS.hpp"
#include "gc/shared/collectorPolicy.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "gc/shared/gcPolicyCounters.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/generationSpec.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/vmGCOperations.hpp"
#include "logging/log.hpp"
#include "memory/universe.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/macros.hpp"

// CollectorPolicy methods

CollectorPolicy::CollectorPolicy() :
    _space_alignment(0),
    _heap_alignment(0),
    _initial_heap_byte_size(InitialHeapSize),
    _max_heap_byte_size(MaxHeapSize),
    _min_heap_byte_size(Arguments::min_heap_size()),
    _size_policy(NULL),
    _should_clear_all_soft_refs(false),
    _all_soft_refs_clear(false)
{}

#ifdef ASSERT
void CollectorPolicy::assert_flags() {
  assert(InitialHeapSize <= MaxHeapSize, "Ergonomics decided on incompatible initial and maximum heap sizes");
  assert(InitialHeapSize % _heap_alignment == 0, "InitialHeapSize alignment");
  assert(MaxHeapSize % _heap_alignment == 0, "MaxHeapSize alignment");
}

void CollectorPolicy::assert_size_info() {
  assert(InitialHeapSize == _initial_heap_byte_size, "Discrepancy between InitialHeapSize flag and local storage");
  assert(MaxHeapSize == _max_heap_byte_size, "Discrepancy between MaxHeapSize flag and local storage");
  assert(_max_heap_byte_size >= _min_heap_byte_size, "Ergonomics decided on incompatible minimum and maximum heap sizes");
  assert(_initial_heap_byte_size >= _min_heap_byte_size, "Ergonomics decided on incompatible initial and minimum heap sizes");
  assert(_max_heap_byte_size >= _initial_heap_byte_size, "Ergonomics decided on incompatible initial and maximum heap sizes");
  assert(_min_heap_byte_size % _heap_alignment == 0, "min_heap_byte_size alignment");
  assert(_initial_heap_byte_size % _heap_alignment == 0, "initial_heap_byte_size alignment");
  assert(_max_heap_byte_size % _heap_alignment == 0, "max_heap_byte_size alignment");
}
#endif // ASSERT

void CollectorPolicy::initialize_flags() {
  assert(_space_alignment != 0, "Space alignment not set up properly");
  assert(_heap_alignment != 0, "Heap alignment not set up properly");
  assert(_heap_alignment >= _space_alignment,
         "heap_alignment: " SIZE_FORMAT " less than space_alignment: " SIZE_FORMAT,
         _heap_alignment, _space_alignment);
  assert(_heap_alignment % _space_alignment == 0,
         "heap_alignment: " SIZE_FORMAT " not aligned by space_alignment: " SIZE_FORMAT,
         _heap_alignment, _space_alignment);

  if (FLAG_IS_CMDLINE(MaxHeapSize)) {
    if (FLAG_IS_CMDLINE(InitialHeapSize) && InitialHeapSize > MaxHeapSize) {
      vm_exit_during_initialization("Initial heap size set to a larger value than the maximum heap size");
    }
    if (_min_heap_byte_size != 0 && MaxHeapSize < _min_heap_byte_size) {
      vm_exit_during_initialization("Incompatible minimum and maximum heap sizes specified");
    }
  }

  // Check heap parameter properties
  if (MaxHeapSize < 2 * M) {
    vm_exit_during_initialization("Too small maximum heap");
  }
  if (InitialHeapSize < M) {
    vm_exit_during_initialization("Too small initial heap");
  }
  if (_min_heap_byte_size < M) {
    vm_exit_during_initialization("Too small minimum heap");
  }

  // User inputs from -Xmx and -Xms must be aligned
  _min_heap_byte_size = align_size_up(_min_heap_byte_size, _heap_alignment);
  size_t aligned_initial_heap_size = align_size_up(InitialHeapSize, _heap_alignment);
  size_t aligned_max_heap_size = align_size_up(MaxHeapSize, _heap_alignment);

  // Write back to flags if the values changed
  if (aligned_initial_heap_size != InitialHeapSize) {
    FLAG_SET_ERGO(size_t, InitialHeapSize, aligned_initial_heap_size);
  }
  if (aligned_max_heap_size != MaxHeapSize) {
    FLAG_SET_ERGO(size_t, MaxHeapSize, aligned_max_heap_size);
  }

  if (FLAG_IS_CMDLINE(InitialHeapSize) && _min_heap_byte_size != 0 &&
      InitialHeapSize < _min_heap_byte_size) {
    vm_exit_during_initialization("Incompatible minimum and initial heap sizes specified");
  }
  if (!FLAG_IS_DEFAULT(InitialHeapSize) && InitialHeapSize > MaxHeapSize) {
    FLAG_SET_ERGO(size_t, MaxHeapSize, InitialHeapSize);
  } else if (!FLAG_IS_DEFAULT(MaxHeapSize) && InitialHeapSize > MaxHeapSize) {
    FLAG_SET_ERGO(size_t, InitialHeapSize, MaxHeapSize);
    if (InitialHeapSize < _min_heap_byte_size) {
      _min_heap_byte_size = InitialHeapSize;
    }
  }

  _initial_heap_byte_size = InitialHeapSize;
  _max_heap_byte_size = MaxHeapSize;

  FLAG_SET_ERGO(size_t, MinHeapDeltaBytes, align_size_up(MinHeapDeltaBytes, _space_alignment));

  DEBUG_ONLY(CollectorPolicy::assert_flags();)
}

void CollectorPolicy::initialize_size_info() {
  log_debug(gc, heap)("Minimum heap " SIZE_FORMAT "  Initial heap " SIZE_FORMAT "  Maximum heap " SIZE_FORMAT,
                      _min_heap_byte_size, _initial_heap_byte_size, _max_heap_byte_size);

  DEBUG_ONLY(CollectorPolicy::assert_size_info();)
}

bool CollectorPolicy::use_should_clear_all_soft_refs(bool v) {
  bool result = _should_clear_all_soft_refs;
  set_should_clear_all_soft_refs(false);
  return result;
}

CardTableRS* CollectorPolicy::create_rem_set(MemRegion whole_heap) {
  return new CardTableRS(whole_heap);
}

void CollectorPolicy::cleared_all_soft_refs() {
  // If near gc overhear limit, continue to clear SoftRefs.  SoftRefs may
  // have been cleared in the last collection but if the gc overhear
  // limit continues to be near, SoftRefs should still be cleared.
  if (size_policy() != NULL) {
    _should_clear_all_soft_refs = size_policy()->gc_overhead_limit_near();
  }
  _all_soft_refs_clear = true;
}

size_t CollectorPolicy::compute_heap_alignment() {
  // The card marking array and the offset arrays for old generations are
  // committed in os pages as well. Make sure they are entirely full (to
  // avoid partial page problems), e.g. if 512 bytes heap corresponds to 1
  // byte entry and the os page size is 4096, the maximum heap size should
  // be 512*4096 = 2MB aligned.

  size_t alignment = CardTableRS::ct_max_alignment_constraint();

  if (UseLargePages) {
      // In presence of large pages we have to make sure that our
      // alignment is large page aware.
      alignment = lcm(os::large_page_size(), alignment);
  }

  return alignment;
}

// GenCollectorPolicy methods

GenCollectorPolicy::GenCollectorPolicy() :
    _min_young_size(0),
    _initial_young_size(0),
    _max_young_size(0),
    _min_old_size(0),
    _initial_old_size(0),
    _max_old_size(0),
    _gen_alignment(0),
    _young_gen_spec(NULL),
    _old_gen_spec(NULL)
{}

size_t GenCollectorPolicy::scale_by_NewRatio_aligned(size_t base_size) {
  return align_size_down_bounded(base_size / (NewRatio + 1), _gen_alignment);
}

size_t GenCollectorPolicy::bound_minus_alignment(size_t desired_size,
                                                 size_t maximum_size) {
  size_t max_minus = maximum_size - _gen_alignment;
  return desired_size < max_minus ? desired_size : max_minus;
}


void GenCollectorPolicy::initialize_size_policy(size_t init_eden_size,
                                                size_t init_promo_size,
                                                size_t init_survivor_size) {
  const double max_gc_pause_sec = ((double) MaxGCPauseMillis) / 1000.0;
  _size_policy = new AdaptiveSizePolicy(init_eden_size,
                                        init_promo_size,
                                        init_survivor_size,
                                        max_gc_pause_sec,
                                        GCTimeRatio);
}

size_t GenCollectorPolicy::young_gen_size_lower_bound() {
  // The young generation must be aligned and have room for eden + two survivors
  return align_size_up(3 * _space_alignment, _gen_alignment);
}

size_t GenCollectorPolicy::old_gen_size_lower_bound() {
  return align_size_up(_space_alignment, _gen_alignment);
}

#ifdef ASSERT
void GenCollectorPolicy::assert_flags() {
  CollectorPolicy::assert_flags();
  assert(NewSize >= _min_young_size, "Ergonomics decided on a too small young gen size");
  assert(NewSize <= MaxNewSize, "Ergonomics decided on incompatible initial and maximum young gen sizes");
  assert(FLAG_IS_DEFAULT(MaxNewSize) || MaxNewSize < MaxHeapSize, "Ergonomics decided on incompatible maximum young gen and heap sizes");
  assert(NewSize % _gen_alignment == 0, "NewSize alignment");
  assert(FLAG_IS_DEFAULT(MaxNewSize) || MaxNewSize % _gen_alignment == 0, "MaxNewSize alignment");
  assert(OldSize + NewSize <= MaxHeapSize, "Ergonomics decided on incompatible generation and heap sizes");
  assert(OldSize % _gen_alignment == 0, "OldSize alignment");
}

void GenCollectorPolicy::assert_size_info() {
  CollectorPolicy::assert_size_info();
  // GenCollectorPolicy::initialize_size_info may update the MaxNewSize
  assert(MaxNewSize < MaxHeapSize, "Ergonomics decided on incompatible maximum young and heap sizes");
  assert(NewSize == _initial_young_size, "Discrepancy between NewSize flag and local storage");
  assert(MaxNewSize == _max_young_size, "Discrepancy between MaxNewSize flag and local storage");
  assert(OldSize == _initial_old_size, "Discrepancy between OldSize flag and local storage");
  assert(_min_young_size <= _initial_young_size, "Ergonomics decided on incompatible minimum and initial young gen sizes");
  assert(_initial_young_size <= _max_young_size, "Ergonomics decided on incompatible initial and maximum young gen sizes");
  assert(_min_young_size % _gen_alignment == 0, "_min_young_size alignment");
  assert(_initial_young_size % _gen_alignment == 0, "_initial_young_size alignment");
  assert(_max_young_size % _gen_alignment == 0, "_max_young_size alignment");
  assert(_min_young_size <= bound_minus_alignment(_min_young_size, _min_heap_byte_size),
      "Ergonomics made minimum young generation larger than minimum heap");
  assert(_initial_young_size <=  bound_minus_alignment(_initial_young_size, _initial_heap_byte_size),
      "Ergonomics made initial young generation larger than initial heap");
  assert(_max_young_size <= bound_minus_alignment(_max_young_size, _max_heap_byte_size),
      "Ergonomics made maximum young generation lager than maximum heap");
  assert(_min_old_size <= _initial_old_size, "Ergonomics decided on incompatible minimum and initial old gen sizes");
  assert(_initial_old_size <= _max_old_size, "Ergonomics decided on incompatible initial and maximum old gen sizes");
  assert(_max_old_size % _gen_alignment == 0, "_max_old_size alignment");
  assert(_initial_old_size % _gen_alignment == 0, "_initial_old_size alignment");
  assert(_max_heap_byte_size <= (_max_young_size + _max_old_size), "Total maximum heap sizes must be sum of generation maximum sizes");
  assert(_min_young_size + _min_old_size <= _min_heap_byte_size, "Minimum generation sizes exceed minimum heap size");
  assert(_initial_young_size + _initial_old_size == _initial_heap_byte_size, "Initial generation sizes should match initial heap size");
  assert(_max_young_size + _max_old_size == _max_heap_byte_size, "Maximum generation sizes should match maximum heap size");
}
#endif // ASSERT

void GenCollectorPolicy::initialize_flags() {
  CollectorPolicy::initialize_flags();

  assert(_gen_alignment != 0, "Generation alignment not set up properly");
  assert(_heap_alignment >= _gen_alignment,
         "heap_alignment: " SIZE_FORMAT " less than gen_alignment: " SIZE_FORMAT,
         _heap_alignment, _gen_alignment);
  assert(_gen_alignment % _space_alignment == 0,
         "gen_alignment: " SIZE_FORMAT " not aligned by space_alignment: " SIZE_FORMAT,
         _gen_alignment, _space_alignment);
  assert(_heap_alignment % _gen_alignment == 0,
         "heap_alignment: " SIZE_FORMAT " not aligned by gen_alignment: " SIZE_FORMAT,
         _heap_alignment, _gen_alignment);

  // All generational heaps have a young gen; handle those flags here

  // Make sure the heap is large enough for two generations
  size_t smallest_new_size = young_gen_size_lower_bound();
  size_t smallest_heap_size = align_size_up(smallest_new_size + old_gen_size_lower_bound(),
                                           _heap_alignment);
  if (MaxHeapSize < smallest_heap_size) {
    FLAG_SET_ERGO(size_t, MaxHeapSize, smallest_heap_size);
    _max_heap_byte_size = MaxHeapSize;
  }
  // If needed, synchronize _min_heap_byte size and _initial_heap_byte_size
  if (_min_heap_byte_size < smallest_heap_size) {
    _min_heap_byte_size = smallest_heap_size;
    if (InitialHeapSize < _min_heap_byte_size) {
      FLAG_SET_ERGO(size_t, InitialHeapSize, smallest_heap_size);
      _initial_heap_byte_size = smallest_heap_size;
    }
  }

  // Make sure NewSize allows an old generation to fit even if set on the command line
  if (FLAG_IS_CMDLINE(NewSize) && NewSize >= _initial_heap_byte_size) {
    log_warning(gc, ergo)("NewSize was set larger than initial heap size, will use initial heap size.");
    FLAG_SET_ERGO(size_t, NewSize, bound_minus_alignment(NewSize, _initial_heap_byte_size));
  }

  // Now take the actual NewSize into account. We will silently increase NewSize
  // if the user specified a smaller or unaligned value.
  size_t bounded_new_size = bound_minus_alignment(NewSize, MaxHeapSize);
  bounded_new_size = MAX2(smallest_new_size, (size_t)align_size_down(bounded_new_size, _gen_alignment));
  if (bounded_new_size != NewSize) {
    FLAG_SET_ERGO(size_t, NewSize, bounded_new_size);
  }
  _min_young_size = smallest_new_size;
  _initial_young_size = NewSize;

  if (!FLAG_IS_DEFAULT(MaxNewSize)) {
    if (MaxNewSize >= MaxHeapSize) {
      // Make sure there is room for an old generation
      size_t smaller_max_new_size = MaxHeapSize - _gen_alignment;
      if (FLAG_IS_CMDLINE(MaxNewSize)) {
        log_warning(gc, ergo)("MaxNewSize (" SIZE_FORMAT "k) is equal to or greater than the entire "
                              "heap (" SIZE_FORMAT "k).  A new max generation size of " SIZE_FORMAT "k will be used.",
                              MaxNewSize/K, MaxHeapSize/K, smaller_max_new_size/K);
      }
      FLAG_SET_ERGO(size_t, MaxNewSize, smaller_max_new_size);
      if (NewSize > MaxNewSize) {
        FLAG_SET_ERGO(size_t, NewSize, MaxNewSize);
        _initial_young_size = NewSize;
      }
    } else if (MaxNewSize < _initial_young_size) {
      FLAG_SET_ERGO(size_t, MaxNewSize, _initial_young_size);
    } else if (!is_size_aligned(MaxNewSize, _gen_alignment)) {
      FLAG_SET_ERGO(size_t, MaxNewSize, align_size_down(MaxNewSize, _gen_alignment));
    }
    _max_young_size = MaxNewSize;
  }

  if (NewSize > MaxNewSize) {
    // At this point this should only happen if the user specifies a large NewSize and/or
    // a small (but not too small) MaxNewSize.
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      log_warning(gc, ergo)("NewSize (" SIZE_FORMAT "k) is greater than the MaxNewSize (" SIZE_FORMAT "k). "
                            "A new max generation size of " SIZE_FORMAT "k will be used.",
                            NewSize/K, MaxNewSize/K, NewSize/K);
    }
    FLAG_SET_ERGO(size_t, MaxNewSize, NewSize);
    _max_young_size = MaxNewSize;
  }

  if (SurvivorRatio < 1 || NewRatio < 1) {
    vm_exit_during_initialization("Invalid young gen ratio specified");
  }

  if (OldSize < old_gen_size_lower_bound()) {
    FLAG_SET_ERGO(size_t, OldSize, old_gen_size_lower_bound());
  }
  if (!is_size_aligned(OldSize, _gen_alignment)) {
    FLAG_SET_ERGO(size_t, OldSize, align_size_down(OldSize, _gen_alignment));
  }

  if (FLAG_IS_CMDLINE(OldSize) && FLAG_IS_DEFAULT(MaxHeapSize)) {
    // NewRatio will be used later to set the young generation size so we use
    // it to calculate how big the heap should be based on the requested OldSize
    // and NewRatio.
    assert(NewRatio > 0, "NewRatio should have been set up earlier");
    size_t calculated_heapsize = (OldSize / NewRatio) * (NewRatio + 1);

    calculated_heapsize = align_size_up(calculated_heapsize, _heap_alignment);
    FLAG_SET_ERGO(size_t, MaxHeapSize, calculated_heapsize);
    _max_heap_byte_size = MaxHeapSize;
    FLAG_SET_ERGO(size_t, InitialHeapSize, calculated_heapsize);
    _initial_heap_byte_size = InitialHeapSize;
  }

  // Adjust NewSize and OldSize or MaxHeapSize to match each other
  if (NewSize + OldSize > MaxHeapSize) {
    if (FLAG_IS_CMDLINE(MaxHeapSize)) {
      // Somebody has set a maximum heap size with the intention that we should not
      // exceed it. Adjust New/OldSize as necessary.
      size_t calculated_size = NewSize + OldSize;
      double shrink_factor = (double) MaxHeapSize / calculated_size;
      size_t smaller_new_size = align_size_down((size_t)(NewSize * shrink_factor), _gen_alignment);
      FLAG_SET_ERGO(size_t, NewSize, MAX2(young_gen_size_lower_bound(), smaller_new_size));
      _initial_young_size = NewSize;

      // OldSize is already aligned because above we aligned MaxHeapSize to
      // _heap_alignment, and we just made sure that NewSize is aligned to
      // _gen_alignment. In initialize_flags() we verified that _heap_alignment
      // is a multiple of _gen_alignment.
      FLAG_SET_ERGO(size_t, OldSize, MaxHeapSize - NewSize);
    } else {
      FLAG_SET_ERGO(size_t, MaxHeapSize, align_size_up(NewSize + OldSize, _heap_alignment));
      _max_heap_byte_size = MaxHeapSize;
    }
  }

  // Update NewSize, if possible, to avoid sizing the young gen too small when only
  // OldSize is set on the command line.
  if (FLAG_IS_CMDLINE(OldSize) && !FLAG_IS_CMDLINE(NewSize)) {
    if (OldSize < _initial_heap_byte_size) {
      size_t new_size = _initial_heap_byte_size - OldSize;
      // Need to compare against the flag value for max since _max_young_size
      // might not have been set yet.
      if (new_size >= _min_young_size && new_size <= MaxNewSize) {
        FLAG_SET_ERGO(size_t, NewSize, new_size);
        _initial_young_size = NewSize;
      }
    }
  }

  always_do_update_barrier = UseConcMarkSweepGC;

  DEBUG_ONLY(GenCollectorPolicy::assert_flags();)
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

// Minimum sizes of the generations may be different than
// the initial sizes.  An inconsistency is permitted here
// in the total size that can be specified explicitly by
// command line specification of OldSize and NewSize and
// also a command line specification of -Xms.  Issue a warning
// but allow the values to pass.
void GenCollectorPolicy::initialize_size_info() {
  CollectorPolicy::initialize_size_info();

  _initial_young_size = NewSize;
  _max_young_size = MaxNewSize;
  _initial_old_size = OldSize;

  // Determine maximum size of the young generation.

  if (FLAG_IS_DEFAULT(MaxNewSize)) {
    _max_young_size = scale_by_NewRatio_aligned(_max_heap_byte_size);
    // Bound the maximum size by NewSize below (since it historically
    // would have been NewSize and because the NewRatio calculation could
    // yield a size that is too small) and bound it by MaxNewSize above.
    // Ergonomics plays here by previously calculating the desired
    // NewSize and MaxNewSize.
    _max_young_size = MIN2(MAX2(_max_young_size, _initial_young_size), MaxNewSize);
  }

  // Given the maximum young size, determine the initial and
  // minimum young sizes.

  if (_max_heap_byte_size == _initial_heap_byte_size) {
    // The maximum and initial heap sizes are the same so the generation's
    // initial size must be the same as it maximum size. Use NewSize as the
    // size if set on command line.
    _max_young_size = FLAG_IS_CMDLINE(NewSize) ? NewSize : _max_young_size;
    _initial_young_size = _max_young_size;

    // Also update the minimum size if min == initial == max.
    if (_max_heap_byte_size == _min_heap_byte_size) {
      _min_young_size = _max_young_size;
    }
  } else {
    if (FLAG_IS_CMDLINE(NewSize)) {
      // If NewSize is set on the command line, we should use it as
      // the initial size, but make sure it is within the heap bounds.
      _initial_young_size =
        MIN2(_max_young_size, bound_minus_alignment(NewSize, _initial_heap_byte_size));
      _min_young_size = bound_minus_alignment(_initial_young_size, _min_heap_byte_size);
    } else {
      // For the case where NewSize is not set on the command line, use
      // NewRatio to size the initial generation size. Use the current
      // NewSize as the floor, because if NewRatio is overly large, the resulting
      // size can be too small.
      _initial_young_size =
        MIN2(_max_young_size, MAX2(scale_by_NewRatio_aligned(_initial_heap_byte_size), NewSize));
    }
  }

  log_trace(gc, heap)("1: Minimum young " SIZE_FORMAT "  Initial young " SIZE_FORMAT "  Maximum young " SIZE_FORMAT,
                      _min_young_size, _initial_young_size, _max_young_size);

  // At this point the minimum, initial and maximum sizes
  // of the overall heap and of the young generation have been determined.
  // The maximum old size can be determined from the maximum young
  // and maximum heap size since no explicit flags exist
  // for setting the old generation maximum.
  _max_old_size = MAX2(_max_heap_byte_size - _max_young_size, _gen_alignment);

  // If no explicit command line flag has been set for the
  // old generation size, use what is left.
  if (!FLAG_IS_CMDLINE(OldSize)) {
    // The user has not specified any value but the ergonomics
    // may have chosen a value (which may or may not be consistent
    // with the overall heap size).  In either case make
    // the minimum, maximum and initial sizes consistent
    // with the young sizes and the overall heap sizes.
    _min_old_size = _gen_alignment;
    _initial_old_size = MIN2(_max_old_size, MAX2(_initial_heap_byte_size - _initial_young_size, _min_old_size));
    // _max_old_size has already been made consistent above.
  } else {
    // OldSize has been explicitly set on the command line. Use it
    // for the initial size but make sure the minimum allow a young
    // generation to fit as well.
    // If the user has explicitly set an OldSize that is inconsistent
    // with other command line flags, issue a warning.
    // The generation minimums and the overall heap minimum should
    // be within one generation alignment.
    if (_initial_old_size > _max_old_size) {
      log_warning(gc, ergo)("Inconsistency between maximum heap size and maximum "
                            "generation sizes: using maximum heap = " SIZE_FORMAT
                            ", -XX:OldSize flag is being ignored",
                            _max_heap_byte_size);
      _initial_old_size = _max_old_size;
    }

    _min_old_size = MIN2(_initial_old_size, _min_heap_byte_size - _min_young_size);
  }

  // The initial generation sizes should match the initial heap size,
  // if not issue a warning and resize the generations. This behavior
  // differs from JDK8 where the generation sizes have higher priority
  // than the initial heap size.
  if ((_initial_old_size + _initial_young_size) != _initial_heap_byte_size) {
    log_warning(gc, ergo)("Inconsistency between generation sizes and heap size, resizing "
                          "the generations to fit the heap.");

    size_t desired_young_size = _initial_heap_byte_size - _initial_old_size;
    if (_initial_heap_byte_size < _initial_old_size) {
      // Old want all memory, use minimum for young and rest for old
      _initial_young_size = _min_young_size;
      _initial_old_size = _initial_heap_byte_size - _min_young_size;
    } else if (desired_young_size > _max_young_size) {
      // Need to increase both young and old generation
      _initial_young_size = _max_young_size;
      _initial_old_size = _initial_heap_byte_size - _max_young_size;
    } else if (desired_young_size < _min_young_size) {
      // Need to decrease both young and old generation
      _initial_young_size = _min_young_size;
      _initial_old_size = _initial_heap_byte_size - _min_young_size;
    } else {
      // The young generation boundaries allow us to only update the
      // young generation.
      _initial_young_size = desired_young_size;
    }

    log_trace(gc, heap)("2: Minimum young " SIZE_FORMAT "  Initial young " SIZE_FORMAT "  Maximum young " SIZE_FORMAT,
                    _min_young_size, _initial_young_size, _max_young_size);
  }

  // Write back to flags if necessary.
  if (NewSize != _initial_young_size) {
    FLAG_SET_ERGO(size_t, NewSize, _initial_young_size);
  }

  if (MaxNewSize != _max_young_size) {
    FLAG_SET_ERGO(size_t, MaxNewSize, _max_young_size);
  }

  if (OldSize != _initial_old_size) {
    FLAG_SET_ERGO(size_t, OldSize, _initial_old_size);
  }

  log_trace(gc, heap)("Minimum old " SIZE_FORMAT "  Initial old " SIZE_FORMAT "  Maximum old " SIZE_FORMAT,
                  _min_old_size, _initial_old_size, _max_old_size);

  DEBUG_ONLY(GenCollectorPolicy::assert_size_info();)
}

HeapWord* GenCollectorPolicy::mem_allocate_work(size_t size,
                                        bool is_tlab,
                                        bool* gc_overhead_limit_was_exceeded) {
  GenCollectedHeap *gch = GenCollectedHeap::heap();

  debug_only(gch->check_for_valid_allocation_state());
  assert(gch->no_gc_in_progress(), "Allocation during gc not allowed");

  // In general gc_overhead_limit_was_exceeded should be false so
  // set it so here and reset it to true only if the gc time
  // limit is being exceeded as checked below.
  *gc_overhead_limit_was_exceeded = false;

  HeapWord* result = NULL;

  // Loop until the allocation is satisfied, or unsatisfied after GC.
  for (uint try_count = 1, gclocker_stalled_count = 0; /* return or throw */; try_count += 1) {
    HandleMark hm; // Discard any handles allocated in each iteration.

    // First allocation attempt is lock-free.
    Generation *young = gch->young_gen();
    assert(young->supports_inline_contig_alloc(),
      "Otherwise, must do alloc within heap lock");
    if (young->should_allocate(size, is_tlab)) {
      result = young->par_allocate(size, is_tlab);
      if (result != NULL) {
        assert(gch->is_in_reserved(result), "result not in heap");
        return result;
      }
    }
    uint gc_count_before;  // Read inside the Heap_lock locked region.
    {
      MutexLocker ml(Heap_lock);
      log_trace(gc, alloc)("GenCollectorPolicy::mem_allocate_work: attempting locked slow path allocation");
      // Note that only large objects get a shot at being
      // allocated in later generations.
      bool first_only = ! should_try_older_generation_allocation(size);

      result = gch->attempt_allocation(size, is_tlab, first_only);
      if (result != NULL) {
        assert(gch->is_in_reserved(result), "result not in heap");
        return result;
      }

      if (GCLocker::is_active_and_needs_gc()) {
        if (is_tlab) {
          return NULL;  // Caller will retry allocating individual object.
        }
        if (!gch->is_maximal_no_gc()) {
          // Try and expand heap to satisfy request.
          result = expand_heap_and_allocate(size, is_tlab);
          // Result could be null if we are out of space.
          if (result != NULL) {
            return result;
          }
        }

        if (gclocker_stalled_count > GCLockerRetryAllocationCount) {
          return NULL; // We didn't get to do a GC and we didn't get any memory.
        }

        // If this thread is not in a jni critical section, we stall
        // the requestor until the critical section has cleared and
        // GC allowed. When the critical section clears, a GC is
        // initiated by the last thread exiting the critical section; so
        // we retry the allocation sequence from the beginning of the loop,
        // rather than causing more, now probably unnecessary, GC attempts.
        JavaThread* jthr = JavaThread::current();
        if (!jthr->in_critical()) {
          MutexUnlocker mul(Heap_lock);
          // Wait for JNI critical section to be exited
          GCLocker::stall_until_clear();
          gclocker_stalled_count += 1;
          continue;
        } else {
          if (CheckJNICalls) {
            fatal("Possible deadlock due to allocating while"
                  " in jni critical section");
          }
          return NULL;
        }
      }

      // Read the gc count while the heap lock is held.
      gc_count_before = gch->total_collections();
    }

    VM_GenCollectForAllocation op(size, is_tlab, gc_count_before);
    VMThread::execute(&op);
    if (op.prologue_succeeded()) {
      result = op.result();
      if (op.gc_locked()) {
         assert(result == NULL, "must be NULL if gc_locked() is true");
         continue;  // Retry and/or stall as necessary.
      }

      // Allocation has failed and a collection
      // has been done.  If the gc time limit was exceeded the
      // this time, return NULL so that an out-of-memory
      // will be thrown.  Clear gc_overhead_limit_exceeded
      // so that the overhead exceeded does not persist.

      const bool limit_exceeded = size_policy()->gc_overhead_limit_exceeded();
      const bool softrefs_clear = all_soft_refs_clear();

      if (limit_exceeded && softrefs_clear) {
        *gc_overhead_limit_was_exceeded = true;
        size_policy()->set_gc_overhead_limit_exceeded(false);
        if (op.result() != NULL) {
          CollectedHeap::fill_with_object(op.result(), size);
        }
        return NULL;
      }
      assert(result == NULL || gch->is_in_reserved(result),
             "result not in heap");
      return result;
    }

    // Give a warning if we seem to be looping forever.
    if ((QueuedAllocationWarningCount > 0) &&
        (try_count % QueuedAllocationWarningCount == 0)) {
          log_warning(gc, ergo)("GenCollectorPolicy::mem_allocate_work retries %d times,"
                                " size=" SIZE_FORMAT " %s", try_count, size, is_tlab ? "(TLAB)" : "");
    }
  }
}

HeapWord* GenCollectorPolicy::expand_heap_and_allocate(size_t size,
                                                       bool   is_tlab) {
  GenCollectedHeap *gch = GenCollectedHeap::heap();
  HeapWord* result = NULL;
  Generation *old = gch->old_gen();
  if (old->should_allocate(size, is_tlab)) {
    result = old->expand_and_allocate(size, is_tlab);
  }
  if (result == NULL) {
    Generation *young = gch->young_gen();
    if (young->should_allocate(size, is_tlab)) {
      result = young->expand_and_allocate(size, is_tlab);
    }
  }
  assert(result == NULL || gch->is_in_reserved(result), "result not in heap");
  return result;
}

HeapWord* GenCollectorPolicy::satisfy_failed_allocation(size_t size,
                                                        bool   is_tlab) {
  GenCollectedHeap *gch = GenCollectedHeap::heap();
  GCCauseSetter x(gch, GCCause::_allocation_failure);
  HeapWord* result = NULL;

  assert(size != 0, "Precondition violated");
  if (GCLocker::is_active_and_needs_gc()) {
    // GC locker is active; instead of a collection we will attempt
    // to expand the heap, if there's room for expansion.
    if (!gch->is_maximal_no_gc()) {
      result = expand_heap_and_allocate(size, is_tlab);
    }
    return result;   // Could be null if we are out of space.
  } else if (!gch->incremental_collection_will_fail(false /* don't consult_young */)) {
    // Do an incremental collection.
    gch->do_collection(false,                     // full
                       false,                     // clear_all_soft_refs
                       size,                      // size
                       is_tlab,                   // is_tlab
                       GenCollectedHeap::OldGen); // max_generation
  } else {
    log_trace(gc)(" :: Trying full because partial may fail :: ");
    // Try a full collection; see delta for bug id 6266275
    // for the original code and why this has been simplified
    // with from-space allocation criteria modified and
    // such allocation moved out of the safepoint path.
    gch->do_collection(true,                      // full
                       false,                     // clear_all_soft_refs
                       size,                      // size
                       is_tlab,                   // is_tlab
                       GenCollectedHeap::OldGen); // max_generation
  }

  result = gch->attempt_allocation(size, is_tlab, false /*first_only*/);

  if (result != NULL) {
    assert(gch->is_in_reserved(result), "result not in heap");
    return result;
  }

  // OK, collection failed, try expansion.
  result = expand_heap_and_allocate(size, is_tlab);
  if (result != NULL) {
    return result;
  }

  // If we reach this point, we're really out of memory. Try every trick
  // we can to reclaim memory. Force collection of soft references. Force
  // a complete compaction of the heap. Any additional methods for finding
  // free memory should be here, especially if they are expensive. If this
  // attempt fails, an OOM exception will be thrown.
  {
    UIntFlagSetting flag_change(MarkSweepAlwaysCompactCount, 1); // Make sure the heap is fully compacted

    gch->do_collection(true,                      // full
                       true,                      // clear_all_soft_refs
                       size,                      // size
                       is_tlab,                   // is_tlab
                       GenCollectedHeap::OldGen); // max_generation
  }

  result = gch->attempt_allocation(size, is_tlab, false /* first_only */);
  if (result != NULL) {
    assert(gch->is_in_reserved(result), "result not in heap");
    return result;
  }

  assert(!should_clear_all_soft_refs(),
    "Flag should have been handled and cleared prior to this point");

  // What else?  We might try synchronous finalization later.  If the total
  // space available is large enough for the allocation, then a more
  // complete compaction phase than we've tried so far might be
  // appropriate.
  return NULL;
}

MetaWord* CollectorPolicy::satisfy_failed_metadata_allocation(
                                                 ClassLoaderData* loader_data,
                                                 size_t word_size,
                                                 Metaspace::MetadataType mdtype) {
  uint loop_count = 0;
  uint gc_count = 0;
  uint full_gc_count = 0;

  assert(!Heap_lock->owned_by_self(), "Should not be holding the Heap_lock");

  do {
    MetaWord* result = loader_data->metaspace_non_null()->allocate(word_size, mdtype);
    if (result != NULL) {
      return result;
    }

    if (GCLocker::is_active_and_needs_gc()) {
      // If the GCLocker is active, just expand and allocate.
      // If that does not succeed, wait if this thread is not
      // in a critical section itself.
      result =
        loader_data->metaspace_non_null()->expand_and_allocate(word_size,
                                                               mdtype);
      if (result != NULL) {
        return result;
      }
      JavaThread* jthr = JavaThread::current();
      if (!jthr->in_critical()) {
        // Wait for JNI critical section to be exited
        GCLocker::stall_until_clear();
        // The GC invoked by the last thread leaving the critical
        // section will be a young collection and a full collection
        // is (currently) needed for unloading classes so continue
        // to the next iteration to get a full GC.
        continue;
      } else {
        if (CheckJNICalls) {
          fatal("Possible deadlock due to allocating while"
                " in jni critical section");
        }
        return NULL;
      }
    }

    {  // Need lock to get self consistent gc_count's
      MutexLocker ml(Heap_lock);
      gc_count      = Universe::heap()->total_collections();
      full_gc_count = Universe::heap()->total_full_collections();
    }

    // Generate a VM operation
    VM_CollectForMetadataAllocation op(loader_data,
                                       word_size,
                                       mdtype,
                                       gc_count,
                                       full_gc_count,
                                       GCCause::_metadata_GC_threshold);
    VMThread::execute(&op);

    // If GC was locked out, try again. Check before checking success because the
    // prologue could have succeeded and the GC still have been locked out.
    if (op.gc_locked()) {
      continue;
    }

    if (op.prologue_succeeded()) {
      return op.result();
    }
    loop_count++;
    if ((QueuedAllocationWarningCount > 0) &&
        (loop_count % QueuedAllocationWarningCount == 0)) {
      log_warning(gc, ergo)("satisfy_failed_metadata_allocation() retries %d times,"
                            " size=" SIZE_FORMAT, loop_count, word_size);
    }
  } while (true);  // Until a GC is done
}

// Return true if any of the following is true:
// . the allocation won't fit into the current young gen heap
// . gc locker is occupied (jni critical section)
// . heap memory is tight -- the most recent previous collection
//   was a full collection because a partial collection (would
//   have) failed and is likely to fail again
bool GenCollectorPolicy::should_try_older_generation_allocation(
        size_t word_size) const {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  size_t young_capacity = gch->young_gen()->capacity_before_gc();
  return    (word_size > heap_word_size(young_capacity))
         || GCLocker::is_active_and_needs_gc()
         || gch->incremental_collection_failed();
}


//
// MarkSweepPolicy methods
//

void MarkSweepPolicy::initialize_alignments() {
  _space_alignment = _gen_alignment = (size_t)Generation::GenGrain;
  _heap_alignment = compute_heap_alignment();
}

void MarkSweepPolicy::initialize_generations() {
  _young_gen_spec = new GenerationSpec(Generation::DefNew, _initial_young_size, _max_young_size, _gen_alignment);
  _old_gen_spec   = new GenerationSpec(Generation::MarkSweepCompact, _initial_old_size, _max_old_size, _gen_alignment);
}

void MarkSweepPolicy::initialize_gc_policy_counters() {
  // Initialize the policy counters - 2 collectors, 3 generations.
  _gc_policy_counters = new GCPolicyCounters("Copy:MSC", 2, 3);
}

