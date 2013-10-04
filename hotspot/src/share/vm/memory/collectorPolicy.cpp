/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/shared/adaptiveSizePolicy.hpp"
#include "gc_implementation/shared/gcPolicyCounters.hpp"
#include "gc_implementation/shared/vmGCOperations.hpp"
#include "memory/cardTableRS.hpp"
#include "memory/collectorPolicy.hpp"
#include "memory/gcLocker.inline.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/generationSpec.hpp"
#include "memory/space.hpp"
#include "memory/universe.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/concurrentMarkSweep/cmsAdaptiveSizePolicy.hpp"
#include "gc_implementation/concurrentMarkSweep/cmsGCAdaptivePolicyCounters.hpp"
#endif // INCLUDE_ALL_GCS

// CollectorPolicy methods.

// Align down. If the aligning result in 0, return 'alignment'.
static size_t restricted_align_down(size_t size, size_t alignment) {
  return MAX2(alignment, align_size_down_(size, alignment));
}

void CollectorPolicy::initialize_flags() {
  assert(_max_alignment >= _min_alignment,
         err_msg("max_alignment: " SIZE_FORMAT " less than min_alignment: " SIZE_FORMAT,
                 _max_alignment, _min_alignment));
  assert(_max_alignment % _min_alignment == 0,
         err_msg("max_alignment: " SIZE_FORMAT " not aligned by min_alignment: " SIZE_FORMAT,
                 _max_alignment, _min_alignment));

  if (MaxHeapSize < InitialHeapSize) {
    vm_exit_during_initialization("Incompatible initial and maximum heap sizes specified");
  }

  // Do not use FLAG_SET_ERGO to update MaxMetaspaceSize, since this will
  // override if MaxMetaspaceSize was set on the command line or not.
  // This information is needed later to conform to the specification of the
  // java.lang.management.MemoryUsage API.
  //
  // Ideally, we would be able to set the default value of MaxMetaspaceSize in
  // globals.hpp to the aligned value, but this is not possible, since the
  // alignment depends on other flags being parsed.
  MaxMetaspaceSize = restricted_align_down(MaxMetaspaceSize, _max_alignment);

  if (MetaspaceSize > MaxMetaspaceSize) {
    MetaspaceSize = MaxMetaspaceSize;
  }

  MetaspaceSize = restricted_align_down(MetaspaceSize, _min_alignment);

  assert(MetaspaceSize <= MaxMetaspaceSize, "Must be");

  MinMetaspaceExpansion = restricted_align_down(MinMetaspaceExpansion, _min_alignment);
  MaxMetaspaceExpansion = restricted_align_down(MaxMetaspaceExpansion, _min_alignment);

  MinHeapDeltaBytes = align_size_up(MinHeapDeltaBytes, _min_alignment);

  assert(MetaspaceSize    % _min_alignment == 0, "metapace alignment");
  assert(MaxMetaspaceSize % _max_alignment == 0, "maximum metaspace alignment");
  if (MetaspaceSize < 256*K) {
    vm_exit_during_initialization("Too small initial Metaspace size");
  }
}

void CollectorPolicy::initialize_size_info() {
  // User inputs from -mx and ms must be aligned
  _min_heap_byte_size = align_size_up(Arguments::min_heap_size(), _min_alignment);
  _initial_heap_byte_size = align_size_up(InitialHeapSize, _min_alignment);
  _max_heap_byte_size = align_size_up(MaxHeapSize, _max_alignment);

  // Check heap parameter properties
  if (_initial_heap_byte_size < M) {
    vm_exit_during_initialization("Too small initial heap");
  }
  // Check heap parameter properties
  if (_min_heap_byte_size < M) {
    vm_exit_during_initialization("Too small minimum heap");
  }
  if (_initial_heap_byte_size <= NewSize) {
     // make sure there is at least some room in old space
    vm_exit_during_initialization("Too small initial heap for new size specified");
  }
  if (_max_heap_byte_size < _min_heap_byte_size) {
    vm_exit_during_initialization("Incompatible minimum and maximum heap sizes specified");
  }
  if (_initial_heap_byte_size < _min_heap_byte_size) {
    vm_exit_during_initialization("Incompatible minimum and initial heap sizes specified");
  }
  if (_max_heap_byte_size < _initial_heap_byte_size) {
    vm_exit_during_initialization("Incompatible initial and maximum heap sizes specified");
  }

  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print_cr("Minimum heap " SIZE_FORMAT "  Initial heap "
      SIZE_FORMAT "  Maximum heap " SIZE_FORMAT,
      _min_heap_byte_size, _initial_heap_byte_size, _max_heap_byte_size);
  }
}

bool CollectorPolicy::use_should_clear_all_soft_refs(bool v) {
  bool result = _should_clear_all_soft_refs;
  set_should_clear_all_soft_refs(false);
  return result;
}

GenRemSet* CollectorPolicy::create_rem_set(MemRegion whole_heap,
                                           int max_covered_regions) {
  assert(rem_set_name() == GenRemSet::CardTable, "unrecognized GenRemSet::Name");
  return new CardTableRS(whole_heap, max_covered_regions);
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

size_t CollectorPolicy::compute_max_alignment() {
  // The card marking array and the offset arrays for old generations are
  // committed in os pages as well. Make sure they are entirely full (to
  // avoid partial page problems), e.g. if 512 bytes heap corresponds to 1
  // byte entry and the os page size is 4096, the maximum heap size should
  // be 512*4096 = 2MB aligned.

  // There is only the GenRemSet in Hotspot and only the GenRemSet::CardTable
  // is supported.
  // Requirements of any new remembered set implementations must be added here.
  size_t alignment = GenRemSet::max_alignment_constraint(GenRemSet::CardTable);

  // Parallel GC does its own alignment of the generations to avoid requiring a
  // large page (256M on some platforms) for the permanent generation.  The
  // other collectors should also be updated to do their own alignment and then
  // this use of lcm() should be removed.
  if (UseLargePages && !UseParallelGC) {
      // in presence of large pages we have to make sure that our
      // alignment is large page aware
      alignment = lcm(os::large_page_size(), alignment);
  }

  return alignment;
}

// GenCollectorPolicy methods.

size_t GenCollectorPolicy::scale_by_NewRatio_aligned(size_t base_size) {
  size_t x = base_size / (NewRatio+1);
  size_t new_gen_size = x > _min_alignment ?
                     align_size_down(x, _min_alignment) :
                     _min_alignment;
  return new_gen_size;
}

size_t GenCollectorPolicy::bound_minus_alignment(size_t desired_size,
                                                 size_t maximum_size) {
  size_t alignment = _min_alignment;
  size_t max_minus = maximum_size - alignment;
  return desired_size < max_minus ? desired_size : max_minus;
}


void GenCollectorPolicy::initialize_size_policy(size_t init_eden_size,
                                                size_t init_promo_size,
                                                size_t init_survivor_size) {
  const double max_gc_pause_sec = ((double) MaxGCPauseMillis)/1000.0;
  _size_policy = new AdaptiveSizePolicy(init_eden_size,
                                        init_promo_size,
                                        init_survivor_size,
                                        max_gc_pause_sec,
                                        GCTimeRatio);
}

void GenCollectorPolicy::initialize_flags() {
  // All sizes must be multiples of the generation granularity.
  _min_alignment = (uintx) Generation::GenGrain;
  _max_alignment = compute_max_alignment();

  CollectorPolicy::initialize_flags();

  // All generational heaps have a youngest gen; handle those flags here.

  // Adjust max size parameters
  if (NewSize > MaxNewSize) {
    MaxNewSize = NewSize;
  }
  NewSize = align_size_down(NewSize, _min_alignment);
  MaxNewSize = align_size_down(MaxNewSize, _min_alignment);

  // Check validity of heap flags
  assert(NewSize     % _min_alignment == 0, "eden space alignment");
  assert(MaxNewSize  % _min_alignment == 0, "survivor space alignment");

  if (NewSize < 3 * _min_alignment) {
     // make sure there room for eden and two survivor spaces
    vm_exit_during_initialization("Too small new size specified");
  }
  if (SurvivorRatio < 1 || NewRatio < 1) {
    vm_exit_during_initialization("Invalid young gen ratio specified");
  }
}

void TwoGenerationCollectorPolicy::initialize_flags() {
  GenCollectorPolicy::initialize_flags();

  OldSize = align_size_down(OldSize, _min_alignment);

  if (FLAG_IS_CMDLINE(OldSize) && FLAG_IS_DEFAULT(NewSize)) {
    // NewRatio will be used later to set the young generation size so we use
    // it to calculate how big the heap should be based on the requested OldSize
    // and NewRatio.
    assert(NewRatio > 0, "NewRatio should have been set up earlier");
    size_t calculated_heapsize = (OldSize / NewRatio) * (NewRatio + 1);

    calculated_heapsize = align_size_up(calculated_heapsize, _max_alignment);
    MaxHeapSize = calculated_heapsize;
    InitialHeapSize = calculated_heapsize;
  }
  MaxHeapSize = align_size_up(MaxHeapSize, _max_alignment);

  // adjust max heap size if necessary
  if (NewSize + OldSize > MaxHeapSize) {
    if (FLAG_IS_CMDLINE(MaxHeapSize)) {
      // somebody set a maximum heap size with the intention that we should not
      // exceed it. Adjust New/OldSize as necessary.
      uintx calculated_size = NewSize + OldSize;
      double shrink_factor = (double) MaxHeapSize / calculated_size;
      // align
      NewSize = align_size_down((uintx) (NewSize * shrink_factor), _min_alignment);
      // OldSize is already aligned because above we aligned MaxHeapSize to
      // _max_alignment, and we just made sure that NewSize is aligned to
      // _min_alignment. In initialize_flags() we verified that _max_alignment
      // is a multiple of _min_alignment.
      OldSize = MaxHeapSize - NewSize;
    } else {
      MaxHeapSize = NewSize + OldSize;
    }
  }
  // need to do this again
  MaxHeapSize = align_size_up(MaxHeapSize, _max_alignment);

  // adjust max heap size if necessary
  if (NewSize + OldSize > MaxHeapSize) {
    if (FLAG_IS_CMDLINE(MaxHeapSize)) {
      // somebody set a maximum heap size with the intention that we should not
      // exceed it. Adjust New/OldSize as necessary.
      uintx calculated_size = NewSize + OldSize;
      double shrink_factor = (double) MaxHeapSize / calculated_size;
      // align
      NewSize = align_size_down((uintx) (NewSize * shrink_factor), _min_alignment);
      // OldSize is already aligned because above we aligned MaxHeapSize to
      // _max_alignment, and we just made sure that NewSize is aligned to
      // _min_alignment. In initialize_flags() we verified that _max_alignment
      // is a multiple of _min_alignment.
      OldSize = MaxHeapSize - NewSize;
    } else {
      MaxHeapSize = NewSize + OldSize;
    }
  }
  // need to do this again
  MaxHeapSize = align_size_up(MaxHeapSize, _max_alignment);

  always_do_update_barrier = UseConcMarkSweepGC;

  // Check validity of heap flags
  assert(OldSize     % _min_alignment == 0, "old space alignment");
  assert(MaxHeapSize % _max_alignment == 0, "maximum heap alignment");
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
void GenCollectorPolicy::initialize_size_info() {
  CollectorPolicy::initialize_size_info();

  // _min_alignment is used for alignment within a generation.
  // There is additional alignment done down stream for some
  // collectors that sometimes causes unwanted rounding up of
  // generations sizes.

  // Determine maximum size of gen0

  size_t max_new_size = 0;
  if (FLAG_IS_CMDLINE(MaxNewSize) || FLAG_IS_ERGO(MaxNewSize)) {
    if (MaxNewSize < _min_alignment) {
      max_new_size = _min_alignment;
    }
    if (MaxNewSize >= _max_heap_byte_size) {
      max_new_size = align_size_down(_max_heap_byte_size - _min_alignment,
                                     _min_alignment);
      warning("MaxNewSize (" SIZE_FORMAT "k) is equal to or "
        "greater than the entire heap (" SIZE_FORMAT "k).  A "
        "new generation size of " SIZE_FORMAT "k will be used.",
        MaxNewSize/K, _max_heap_byte_size/K, max_new_size/K);
    } else {
      max_new_size = align_size_down(MaxNewSize, _min_alignment);
    }

  // The case for FLAG_IS_ERGO(MaxNewSize) could be treated
  // specially at this point to just use an ergonomically set
  // MaxNewSize to set max_new_size.  For cases with small
  // heaps such a policy often did not work because the MaxNewSize
  // was larger than the entire heap.  The interpretation given
  // to ergonomically set flags is that the flags are set
  // by different collectors for their own special needs but
  // are not allowed to badly shape the heap.  This allows the
  // different collectors to decide what's best for themselves
  // without having to factor in the overall heap shape.  It
  // can be the case in the future that the collectors would
  // only make "wise" ergonomics choices and this policy could
  // just accept those choices.  The choices currently made are
  // not always "wise".
  } else {
    max_new_size = scale_by_NewRatio_aligned(_max_heap_byte_size);
    // Bound the maximum size by NewSize below (since it historically
    // would have been NewSize and because the NewRatio calculation could
    // yield a size that is too small) and bound it by MaxNewSize above.
    // Ergonomics plays here by previously calculating the desired
    // NewSize and MaxNewSize.
    max_new_size = MIN2(MAX2(max_new_size, NewSize), MaxNewSize);
  }
  assert(max_new_size > 0, "All paths should set max_new_size");

  // Given the maximum gen0 size, determine the initial and
  // minimum gen0 sizes.

  if (_max_heap_byte_size == _min_heap_byte_size) {
    // The maximum and minimum heap sizes are the same so
    // the generations minimum and initial must be the
    // same as its maximum.
    _min_gen0_size = max_new_size;
    _initial_gen0_size = max_new_size;
    _max_gen0_size = max_new_size;
  } else {
    size_t desired_new_size = 0;
    if (!FLAG_IS_DEFAULT(NewSize)) {
      // If NewSize is set ergonomically (for example by cms), it
      // would make sense to use it.  If it is used, also use it
      // to set the initial size.  Although there is no reason
      // the minimum size and the initial size have to be the same,
      // the current implementation gets into trouble during the calculation
      // of the tenured generation sizes if they are different.
      // Note that this makes the initial size and the minimum size
      // generally small compared to the NewRatio calculation.
      _min_gen0_size = NewSize;
      desired_new_size = NewSize;
      max_new_size = MAX2(max_new_size, NewSize);
    } else {
      // For the case where NewSize is the default, use NewRatio
      // to size the minimum and initial generation sizes.
      // Use the default NewSize as the floor for these values.  If
      // NewRatio is overly large, the resulting sizes can be too
      // small.
      _min_gen0_size = MAX2(scale_by_NewRatio_aligned(_min_heap_byte_size), NewSize);
      desired_new_size =
        MAX2(scale_by_NewRatio_aligned(_initial_heap_byte_size), NewSize);
    }

    assert(_min_gen0_size > 0, "Sanity check");
    _initial_gen0_size = desired_new_size;
    _max_gen0_size = max_new_size;

    // At this point the desirable initial and minimum sizes have been
    // determined without regard to the maximum sizes.

    // Bound the sizes by the corresponding overall heap sizes.
    _min_gen0_size = bound_minus_alignment(_min_gen0_size, _min_heap_byte_size);
    _initial_gen0_size = bound_minus_alignment(_initial_gen0_size, _initial_heap_byte_size);
    _max_gen0_size = bound_minus_alignment(_max_gen0_size, _max_heap_byte_size);

    // At this point all three sizes have been checked against the
    // maximum sizes but have not been checked for consistency
    // among the three.

    // Final check min <= initial <= max
    _min_gen0_size = MIN2(_min_gen0_size, _max_gen0_size);
    _initial_gen0_size = MAX2(MIN2(_initial_gen0_size, _max_gen0_size), _min_gen0_size);
    _min_gen0_size = MIN2(_min_gen0_size, _initial_gen0_size);
  }

  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print_cr("1: Minimum gen0 " SIZE_FORMAT "  Initial gen0 "
      SIZE_FORMAT "  Maximum gen0 " SIZE_FORMAT,
      _min_gen0_size, _initial_gen0_size, _max_gen0_size);
  }
}

// Call this method during the sizing of the gen1 to make
// adjustments to gen0 because of gen1 sizing policy.  gen0 initially has
// the most freedom in sizing because it is done before the
// policy for gen1 is applied.  Once gen1 policies have been applied,
// there may be conflicts in the shape of the heap and this method
// is used to make the needed adjustments.  The application of the
// policies could be more sophisticated (iterative for example) but
// keeping it simple also seems a worthwhile goal.
bool TwoGenerationCollectorPolicy::adjust_gen0_sizes(size_t* gen0_size_ptr,
                                                     size_t* gen1_size_ptr,
                                                     const size_t heap_size,
                                                     const size_t min_gen1_size) {
  bool result = false;

  if ((*gen1_size_ptr + *gen0_size_ptr) > heap_size) {
    if ((heap_size < (*gen0_size_ptr + min_gen1_size)) &&
        (heap_size >= min_gen1_size + _min_alignment)) {
      // Adjust gen0 down to accommodate min_gen1_size
      *gen0_size_ptr = heap_size - min_gen1_size;
      *gen0_size_ptr =
        MAX2((uintx)align_size_down(*gen0_size_ptr, _min_alignment), _min_alignment);
      assert(*gen0_size_ptr > 0, "Min gen0 is too large");
      result = true;
    } else {
      *gen1_size_ptr = heap_size - *gen0_size_ptr;
      *gen1_size_ptr =
        MAX2((uintx)align_size_down(*gen1_size_ptr, _min_alignment), _min_alignment);
    }
  }
  return result;
}

// Minimum sizes of the generations may be different than
// the initial sizes.  An inconsistently is permitted here
// in the total size that can be specified explicitly by
// command line specification of OldSize and NewSize and
// also a command line specification of -Xms.  Issue a warning
// but allow the values to pass.

void TwoGenerationCollectorPolicy::initialize_size_info() {
  GenCollectorPolicy::initialize_size_info();

  // At this point the minimum, initial and maximum sizes
  // of the overall heap and of gen0 have been determined.
  // The maximum gen1 size can be determined from the maximum gen0
  // and maximum heap size since no explicit flags exits
  // for setting the gen1 maximum.
  _max_gen1_size = _max_heap_byte_size - _max_gen0_size;
  _max_gen1_size =
    MAX2((uintx)align_size_down(_max_gen1_size, _min_alignment), _min_alignment);
  // If no explicit command line flag has been set for the
  // gen1 size, use what is left for gen1.
  if (FLAG_IS_DEFAULT(OldSize) || FLAG_IS_ERGO(OldSize)) {
    // The user has not specified any value or ergonomics
    // has chosen a value (which may or may not be consistent
    // with the overall heap size).  In either case make
    // the minimum, maximum and initial sizes consistent
    // with the gen0 sizes and the overall heap sizes.
    assert(_min_heap_byte_size > _min_gen0_size,
      "gen0 has an unexpected minimum size");
    _min_gen1_size = _min_heap_byte_size - _min_gen0_size;
    _min_gen1_size =
      MAX2((uintx)align_size_down(_min_gen1_size, _min_alignment), _min_alignment);
    _initial_gen1_size = _initial_heap_byte_size - _initial_gen0_size;
    _initial_gen1_size =
      MAX2((uintx)align_size_down(_initial_gen1_size, _min_alignment), _min_alignment);
  } else {
    // It's been explicitly set on the command line.  Use the
    // OldSize and then determine the consequences.
    _min_gen1_size = OldSize;
    _initial_gen1_size = OldSize;

    // If the user has explicitly set an OldSize that is inconsistent
    // with other command line flags, issue a warning.
    // The generation minimums and the overall heap mimimum should
    // be within one heap alignment.
    if ((_min_gen1_size + _min_gen0_size + _min_alignment) < _min_heap_byte_size) {
      warning("Inconsistency between minimum heap size and minimum "
              "generation sizes: using minimum heap = " SIZE_FORMAT,
              _min_heap_byte_size);
    }
    if ((OldSize > _max_gen1_size)) {
      warning("Inconsistency between maximum heap size and maximum "
              "generation sizes: using maximum heap = " SIZE_FORMAT
              " -XX:OldSize flag is being ignored",
              _max_heap_byte_size);
    }
    // If there is an inconsistency between the OldSize and the minimum and/or
    // initial size of gen0, since OldSize was explicitly set, OldSize wins.
    if (adjust_gen0_sizes(&_min_gen0_size, &_min_gen1_size,
                          _min_heap_byte_size, OldSize)) {
      if (PrintGCDetails && Verbose) {
        gclog_or_tty->print_cr("2: Minimum gen0 " SIZE_FORMAT "  Initial gen0 "
              SIZE_FORMAT "  Maximum gen0 " SIZE_FORMAT,
              _min_gen0_size, _initial_gen0_size, _max_gen0_size);
      }
    }
    // Initial size
    if (adjust_gen0_sizes(&_initial_gen0_size, &_initial_gen1_size,
                          _initial_heap_byte_size, OldSize)) {
      if (PrintGCDetails && Verbose) {
        gclog_or_tty->print_cr("3: Minimum gen0 " SIZE_FORMAT "  Initial gen0 "
          SIZE_FORMAT "  Maximum gen0 " SIZE_FORMAT,
          _min_gen0_size, _initial_gen0_size, _max_gen0_size);
      }
    }
  }
  // Enforce the maximum gen1 size.
  _min_gen1_size = MIN2(_min_gen1_size, _max_gen1_size);

  // Check that min gen1 <= initial gen1 <= max gen1
  _initial_gen1_size = MAX2(_initial_gen1_size, _min_gen1_size);
  _initial_gen1_size = MIN2(_initial_gen1_size, _max_gen1_size);

  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print_cr("Minimum gen1 " SIZE_FORMAT "  Initial gen1 "
      SIZE_FORMAT "  Maximum gen1 " SIZE_FORMAT,
      _min_gen1_size, _initial_gen1_size, _max_gen1_size);
  }
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

  // Loop until the allocation is satisified,
  // or unsatisfied after GC.
  for (int try_count = 1, gclocker_stalled_count = 0; /* return or throw */; try_count += 1) {
    HandleMark hm; // discard any handles allocated in each iteration

    // First allocation attempt is lock-free.
    Generation *gen0 = gch->get_gen(0);
    assert(gen0->supports_inline_contig_alloc(),
      "Otherwise, must do alloc within heap lock");
    if (gen0->should_allocate(size, is_tlab)) {
      result = gen0->par_allocate(size, is_tlab);
      if (result != NULL) {
        assert(gch->is_in_reserved(result), "result not in heap");
        return result;
      }
    }
    unsigned int gc_count_before;  // read inside the Heap_lock locked region
    {
      MutexLocker ml(Heap_lock);
      if (PrintGC && Verbose) {
        gclog_or_tty->print_cr("TwoGenerationCollectorPolicy::mem_allocate_work:"
                      " attempting locked slow path allocation");
      }
      // Note that only large objects get a shot at being
      // allocated in later generations.
      bool first_only = ! should_try_older_generation_allocation(size);

      result = gch->attempt_allocation(size, is_tlab, first_only);
      if (result != NULL) {
        assert(gch->is_in_reserved(result), "result not in heap");
        return result;
      }

      if (GC_locker::is_active_and_needs_gc()) {
        if (is_tlab) {
          return NULL;  // Caller will retry allocating individual object
        }
        if (!gch->is_maximal_no_gc()) {
          // Try and expand heap to satisfy request
          result = expand_heap_and_allocate(size, is_tlab);
          // result could be null if we are out of space
          if (result != NULL) {
            return result;
          }
        }

        if (gclocker_stalled_count > GCLockerRetryAllocationCount) {
          return NULL; // we didn't get to do a GC and we didn't get any memory
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
          GC_locker::stall_until_clear();
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
      gc_count_before = Universe::heap()->total_collections();
    }

    VM_GenCollectForAllocation op(size,
                                  is_tlab,
                                  gc_count_before);
    VMThread::execute(&op);
    if (op.prologue_succeeded()) {
      result = op.result();
      if (op.gc_locked()) {
         assert(result == NULL, "must be NULL if gc_locked() is true");
         continue;  // retry and/or stall as necessary
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
          warning("TwoGenerationCollectorPolicy::mem_allocate_work retries %d times \n\t"
                  " size=%d %s", try_count, size, is_tlab ? "(TLAB)" : "");
    }
  }
}

HeapWord* GenCollectorPolicy::expand_heap_and_allocate(size_t size,
                                                       bool   is_tlab) {
  GenCollectedHeap *gch = GenCollectedHeap::heap();
  HeapWord* result = NULL;
  for (int i = number_of_generations() - 1; i >= 0 && result == NULL; i--) {
    Generation *gen = gch->get_gen(i);
    if (gen->should_allocate(size, is_tlab)) {
      result = gen->expand_and_allocate(size, is_tlab);
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
  if (GC_locker::is_active_and_needs_gc()) {
    // GC locker is active; instead of a collection we will attempt
    // to expand the heap, if there's room for expansion.
    if (!gch->is_maximal_no_gc()) {
      result = expand_heap_and_allocate(size, is_tlab);
    }
    return result;   // could be null if we are out of space
  } else if (!gch->incremental_collection_will_fail(false /* don't consult_young */)) {
    // Do an incremental collection.
    gch->do_collection(false            /* full */,
                       false            /* clear_all_soft_refs */,
                       size             /* size */,
                       is_tlab          /* is_tlab */,
                       number_of_generations() - 1 /* max_level */);
  } else {
    if (Verbose && PrintGCDetails) {
      gclog_or_tty->print(" :: Trying full because partial may fail :: ");
    }
    // Try a full collection; see delta for bug id 6266275
    // for the original code and why this has been simplified
    // with from-space allocation criteria modified and
    // such allocation moved out of the safepoint path.
    gch->do_collection(true             /* full */,
                       false            /* clear_all_soft_refs */,
                       size             /* size */,
                       is_tlab          /* is_tlab */,
                       number_of_generations() - 1 /* max_level */);
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

    gch->do_collection(true             /* full */,
                       true             /* clear_all_soft_refs */,
                       size             /* size */,
                       is_tlab          /* is_tlab */,
                       number_of_generations() - 1 /* max_level */);
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
    MetaWord* result = NULL;
    if (GC_locker::is_active_and_needs_gc()) {
      // If the GC_locker is active, just expand and allocate.
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
        GC_locker::stall_until_clear();
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

    // If GC was locked out, try again.  Check
    // before checking success because the prologue
    // could have succeeded and the GC still have
    // been locked out.
    if (op.gc_locked()) {
      continue;
    }

    if (op.prologue_succeeded()) {
      return op.result();
    }
    loop_count++;
    if ((QueuedAllocationWarningCount > 0) &&
        (loop_count % QueuedAllocationWarningCount == 0)) {
      warning("satisfy_failed_metadata_allocation() retries %d times \n\t"
              " size=%d", loop_count, word_size);
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
  size_t gen0_capacity = gch->get_gen(0)->capacity_before_gc();
  return    (word_size > heap_word_size(gen0_capacity))
         || GC_locker::is_active_and_needs_gc()
         || gch->incremental_collection_failed();
}


//
// MarkSweepPolicy methods
//

MarkSweepPolicy::MarkSweepPolicy() {
  initialize_all();
}

void MarkSweepPolicy::initialize_generations() {
  _generations = NEW_C_HEAP_ARRAY3(GenerationSpecPtr, number_of_generations(), mtGC, 0, AllocFailStrategy::RETURN_NULL);
  if (_generations == NULL)
    vm_exit_during_initialization("Unable to allocate gen spec");

  if (UseParNewGC) {
    _generations[0] = new GenerationSpec(Generation::ParNew, _initial_gen0_size, _max_gen0_size);
  } else {
    _generations[0] = new GenerationSpec(Generation::DefNew, _initial_gen0_size, _max_gen0_size);
  }
  _generations[1] = new GenerationSpec(Generation::MarkSweepCompact, _initial_gen1_size, _max_gen1_size);

  if (_generations[0] == NULL || _generations[1] == NULL)
    vm_exit_during_initialization("Unable to allocate gen spec");
}

void MarkSweepPolicy::initialize_gc_policy_counters() {
  // initialize the policy counters - 2 collectors, 3 generations
  if (UseParNewGC) {
    _gc_policy_counters = new GCPolicyCounters("ParNew:MSC", 2, 3);
  } else {
    _gc_policy_counters = new GCPolicyCounters("Copy:MSC", 2, 3);
  }
}
