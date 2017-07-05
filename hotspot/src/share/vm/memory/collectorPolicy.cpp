/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_collectorPolicy.cpp.incl"

// CollectorPolicy methods.

void CollectorPolicy::initialize_flags() {
  if (PermSize > MaxPermSize) {
    MaxPermSize = PermSize;
  }
  PermSize = align_size_down(PermSize, min_alignment());
  MaxPermSize = align_size_up(MaxPermSize, max_alignment());

  MinPermHeapExpansion = align_size_down(MinPermHeapExpansion, min_alignment());
  MaxPermHeapExpansion = align_size_down(MaxPermHeapExpansion, min_alignment());

  MinHeapDeltaBytes = align_size_up(MinHeapDeltaBytes, min_alignment());

  SharedReadOnlySize = align_size_up(SharedReadOnlySize, max_alignment());
  SharedReadWriteSize = align_size_up(SharedReadWriteSize, max_alignment());
  SharedMiscDataSize = align_size_up(SharedMiscDataSize, max_alignment());

  assert(PermSize    % min_alignment() == 0, "permanent space alignment");
  assert(MaxPermSize % max_alignment() == 0, "maximum permanent space alignment");
  assert(SharedReadOnlySize % max_alignment() == 0, "read-only space alignment");
  assert(SharedReadWriteSize % max_alignment() == 0, "read-write space alignment");
  assert(SharedMiscDataSize % max_alignment() == 0, "misc-data space alignment");
  if (PermSize < M) {
    vm_exit_during_initialization("Too small initial permanent heap");
  }
}

void CollectorPolicy::initialize_size_info() {
  // User inputs from -mx and ms are aligned
  _initial_heap_byte_size = align_size_up(Arguments::initial_heap_size(),
                                          min_alignment());
  _min_heap_byte_size = align_size_up(Arguments::min_heap_size(),
                                          min_alignment());
  _max_heap_byte_size = align_size_up(MaxHeapSize, max_alignment());

  // Check validity of heap parameters from launcher
  if (_initial_heap_byte_size == 0) {
    _initial_heap_byte_size = NewSize + OldSize;
  } else {
    Universe::check_alignment(_initial_heap_byte_size, min_alignment(),
                            "initial heap");
  }
  if (_min_heap_byte_size == 0) {
    _min_heap_byte_size = NewSize + OldSize;
  } else {
    Universe::check_alignment(_min_heap_byte_size, min_alignment(),
                            "initial heap");
  }

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
}

void CollectorPolicy::initialize_perm_generation(PermGen::Name pgnm) {
  _permanent_generation =
    new PermanentGenerationSpec(pgnm, PermSize, MaxPermSize,
                                SharedReadOnlySize,
                                SharedReadWriteSize,
                                SharedMiscDataSize,
                                SharedMiscCodeSize);
  if (_permanent_generation == NULL) {
    vm_exit_during_initialization("Unable to allocate gen spec");
  }
}


GenRemSet* CollectorPolicy::create_rem_set(MemRegion whole_heap,
                                           int max_covered_regions) {
  switch (rem_set_name()) {
  case GenRemSet::CardTable: {
    if (barrier_set_name() != BarrierSet::CardTableModRef)
      vm_exit_during_initialization("Mismatch between RS and BS.");
    CardTableRS* res = new CardTableRS(whole_heap, max_covered_regions);
    return res;
  }
  default:
    guarantee(false, "unrecognized GenRemSet::Name");
    return NULL;
  }
}

// GenCollectorPolicy methods.

void GenCollectorPolicy::initialize_size_policy(size_t init_eden_size,
                                                size_t init_promo_size,
                                                size_t init_survivor_size) {
  double max_gc_minor_pause_sec = ((double) MaxGCMinorPauseMillis)/1000.0;
  _size_policy = new AdaptiveSizePolicy(init_eden_size,
                                        init_promo_size,
                                        init_survivor_size,
                                        max_gc_minor_pause_sec,
                                        GCTimeRatio);
}

size_t GenCollectorPolicy::compute_max_alignment() {
  // The card marking array and the offset arrays for old generations are
  // committed in os pages as well. Make sure they are entirely full (to
  // avoid partial page problems), e.g. if 512 bytes heap corresponds to 1
  // byte entry and the os page size is 4096, the maximum heap size should
  // be 512*4096 = 2MB aligned.
  size_t alignment = GenRemSet::max_alignment_constraint(rem_set_name());

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

void GenCollectorPolicy::initialize_flags() {
  // All sizes must be multiples of the generation granularity.
  set_min_alignment((uintx) Generation::GenGrain);
  set_max_alignment(compute_max_alignment());
  assert(max_alignment() >= min_alignment() &&
         max_alignment() % min_alignment() == 0,
         "invalid alignment constraints");

  CollectorPolicy::initialize_flags();

  // All generational heaps have a youngest gen; handle those flags here.

  // Adjust max size parameters
  if (NewSize > MaxNewSize) {
    MaxNewSize = NewSize;
  }
  NewSize = align_size_down(NewSize, min_alignment());
  MaxNewSize = align_size_down(MaxNewSize, min_alignment());

  // Check validity of heap flags
  assert(NewSize     % min_alignment() == 0, "eden space alignment");
  assert(MaxNewSize  % min_alignment() == 0, "survivor space alignment");

  if (NewSize < 3*min_alignment()) {
     // make sure there room for eden and two survivor spaces
    vm_exit_during_initialization("Too small new size specified");
  }
  if (SurvivorRatio < 1 || NewRatio < 1) {
    vm_exit_during_initialization("Invalid heap ratio specified");
  }
}

void TwoGenerationCollectorPolicy::initialize_flags() {
  GenCollectorPolicy::initialize_flags();

  OldSize = align_size_down(OldSize, min_alignment());
  if (NewSize + OldSize > MaxHeapSize) {
    MaxHeapSize = NewSize + OldSize;
  }
  MaxHeapSize = align_size_up(MaxHeapSize, max_alignment());

  always_do_update_barrier = UseConcMarkSweepGC;
  BlockOffsetArrayUseUnallocatedBlock =
      BlockOffsetArrayUseUnallocatedBlock || ParallelGCThreads > 0;

  // Check validity of heap flags
  assert(OldSize     % min_alignment() == 0, "old space alignment");
  assert(MaxHeapSize % max_alignment() == 0, "maximum heap alignment");
}

void GenCollectorPolicy::initialize_size_info() {
  CollectorPolicy::initialize_size_info();

  // Minimum sizes of the generations may be different than
  // the initial sizes.
  if (!FLAG_IS_DEFAULT(NewSize)) {
    _min_gen0_size = NewSize;
  } else {
    _min_gen0_size = align_size_down(_min_heap_byte_size / (NewRatio+1),
                                     min_alignment());
    // We bound the minimum size by NewSize below (since it historically
    // would have been NewSize and because the NewRatio calculation could
    // yield a size that is too small) and bound it by MaxNewSize above.
    // This is not always best.  The NewSize calculated by CMS (which has
    // a fixed minimum of 16m) can sometimes be "too" large.  Consider
    // the case where -Xmx32m.  The CMS calculated NewSize would be about
    // half the entire heap which seems too large.  But the counter
    // example is seen when the client defaults for NewRatio are used.
    // An initial young generation size of 640k was observed
    // with -Xmx128m -XX:MaxNewSize=32m when NewSize was not used
    // as a lower bound as with
    // _min_gen0_size = MIN2(_min_gen0_size, MaxNewSize);
    // and 640k seemed too small a young generation.
    _min_gen0_size = MIN2(MAX2(_min_gen0_size, NewSize), MaxNewSize);
  }

  // Parameters are valid, compute area sizes.
  size_t max_new_size = align_size_down(_max_heap_byte_size / (NewRatio+1),
                                        min_alignment());
  max_new_size = MIN2(MAX2(max_new_size, _min_gen0_size), MaxNewSize);

  // desired_new_size is used to set the initial size.  The
  // initial size must be greater than the minimum size.
  size_t desired_new_size =
    align_size_down(_initial_heap_byte_size / (NewRatio+1),
                  min_alignment());

  size_t new_size = MIN2(MAX2(desired_new_size, _min_gen0_size), max_new_size);

  _initial_gen0_size = new_size;
  _max_gen0_size = max_new_size;
}

void TwoGenerationCollectorPolicy::initialize_size_info() {
  GenCollectorPolicy::initialize_size_info();

  // Minimum sizes of the generations may be different than
  // the initial sizes.  An inconsistently is permitted here
  // in the total size that can be specified explicitly by
  // command line specification of OldSize and NewSize and
  // also a command line specification of -Xms.  Issue a warning
  // but allow the values to pass.
  if (!FLAG_IS_DEFAULT(OldSize)) {
    _min_gen1_size = OldSize;
    // The generation minimums and the overall heap mimimum should
    // be within one heap alignment.
    if ((_min_gen1_size + _min_gen0_size + max_alignment()) <
         _min_heap_byte_size) {
      warning("Inconsistency between minimum heap size and minimum "
        "generation sizes: using min heap = " SIZE_FORMAT,
        _min_heap_byte_size);
    }
  } else {
    _min_gen1_size = _min_heap_byte_size - _min_gen0_size;
  }

  _initial_gen1_size = _initial_heap_byte_size - _initial_gen0_size;
  _max_gen1_size = _max_heap_byte_size - _max_gen0_size;
}

HeapWord* GenCollectorPolicy::mem_allocate_work(size_t size,
                                        bool is_tlab,
                                        bool* gc_overhead_limit_was_exceeded) {
  GenCollectedHeap *gch = GenCollectedHeap::heap();

  debug_only(gch->check_for_valid_allocation_state());
  assert(gch->no_gc_in_progress(), "Allocation during gc not allowed");
  HeapWord* result = NULL;

  // Loop until the allocation is satisified,
  // or unsatisfied after GC.
  for (int try_count = 1; /* return or throw */; try_count += 1) {
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

      // There are NULL's returned for different circumstances below.
      // In general gc_overhead_limit_was_exceeded should be false so
      // set it so here and reset it to true only if the gc time
      // limit is being exceeded as checked below.
      *gc_overhead_limit_was_exceeded = false;

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

    // Allocation has failed and a collection is about
    // to be done.  If the gc time limit was exceeded the
    // last time a collection was done, return NULL so
    // that an out-of-memory will be thrown.  Clear
    // gc_time_limit_exceeded so that subsequent attempts
    // at a collection will be made.
    if (size_policy()->gc_time_limit_exceeded()) {
      *gc_overhead_limit_was_exceeded = true;
      size_policy()->set_gc_time_limit_exceeded(false);
      return NULL;
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
  } else if (!gch->incremental_collection_will_fail()) {
    // The gc_prologues have not executed yet.  The value
    // for incremental_collection_will_fail() is the remanent
    // of the last collection.
    // Do an incremental collection.
    gch->do_collection(false            /* full */,
                       false            /* clear_all_soft_refs */,
                       size             /* size */,
                       is_tlab          /* is_tlab */,
                       number_of_generations() - 1 /* max_level */);
  } else {
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
    IntFlagSetting flag_change(MarkSweepAlwaysCompactCount, 1); // Make sure the heap is fully compacted

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

  // What else?  We might try synchronous finalization later.  If the total
  // space available is large enough for the allocation, then a more
  // complete compaction phase than we've tried so far might be
  // appropriate.
  return NULL;
}

size_t GenCollectorPolicy::large_typearray_limit() {
  return FastAllocateSizeLimit;
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
         || (GC_locker::is_active_and_needs_gc())
         || (   gch->last_incremental_collection_failed()
             && gch->incremental_collection_will_fail());
}


//
// MarkSweepPolicy methods
//

MarkSweepPolicy::MarkSweepPolicy() {
  initialize_all();
}

void MarkSweepPolicy::initialize_generations() {
  initialize_perm_generation(PermGen::MarkSweepCompact);
  _generations = new GenerationSpecPtr[number_of_generations()];
  if (_generations == NULL)
    vm_exit_during_initialization("Unable to allocate gen spec");

  if (UseParNewGC && ParallelGCThreads > 0) {
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
  if (UseParNewGC && ParallelGCThreads > 0) {
    _gc_policy_counters = new GCPolicyCounters("ParNew:MSC", 2, 3);
  }
  else {
    _gc_policy_counters = new GCPolicyCounters("Copy:MSC", 2, 3);
  }
}
