/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_parallelScavengeHeap.cpp.incl"

PSYoungGen*  ParallelScavengeHeap::_young_gen = NULL;
PSOldGen*    ParallelScavengeHeap::_old_gen = NULL;
PSPermGen*   ParallelScavengeHeap::_perm_gen = NULL;
PSAdaptiveSizePolicy* ParallelScavengeHeap::_size_policy = NULL;
PSGCAdaptivePolicyCounters* ParallelScavengeHeap::_gc_policy_counters = NULL;
ParallelScavengeHeap* ParallelScavengeHeap::_psh = NULL;
GCTaskManager* ParallelScavengeHeap::_gc_task_manager = NULL;

static void trace_gen_sizes(const char* const str,
                            size_t pg_min, size_t pg_max,
                            size_t og_min, size_t og_max,
                            size_t yg_min, size_t yg_max)
{
  if (TracePageSizes) {
    tty->print_cr("%s:  " SIZE_FORMAT "," SIZE_FORMAT " "
                  SIZE_FORMAT "," SIZE_FORMAT " "
                  SIZE_FORMAT "," SIZE_FORMAT " "
                  SIZE_FORMAT,
                  str, pg_min / K, pg_max / K,
                  og_min / K, og_max / K,
                  yg_min / K, yg_max / K,
                  (pg_max + og_max + yg_max) / K);
  }
}

jint ParallelScavengeHeap::initialize() {
  // Cannot be initialized until after the flags are parsed
  GenerationSizer flag_parser;

  size_t yg_min_size = flag_parser.min_young_gen_size();
  size_t yg_max_size = flag_parser.max_young_gen_size();
  size_t og_min_size = flag_parser.min_old_gen_size();
  size_t og_max_size = flag_parser.max_old_gen_size();
  // Why isn't there a min_perm_gen_size()?
  size_t pg_min_size = flag_parser.perm_gen_size();
  size_t pg_max_size = flag_parser.max_perm_gen_size();

  trace_gen_sizes("ps heap raw",
                  pg_min_size, pg_max_size,
                  og_min_size, og_max_size,
                  yg_min_size, yg_max_size);

  // The ReservedSpace ctor used below requires that the page size for the perm
  // gen is <= the page size for the rest of the heap (young + old gens).
  const size_t og_page_sz = os::page_size_for_region(yg_min_size + og_min_size,
                                                     yg_max_size + og_max_size,
                                                     8);
  const size_t pg_page_sz = MIN2(os::page_size_for_region(pg_min_size,
                                                          pg_max_size, 16),
                                 og_page_sz);

  const size_t pg_align = set_alignment(_perm_gen_alignment,  pg_page_sz);
  const size_t og_align = set_alignment(_old_gen_alignment,   og_page_sz);
  const size_t yg_align = set_alignment(_young_gen_alignment, og_page_sz);

  // Update sizes to reflect the selected page size(s).
  //
  // NEEDS_CLEANUP.  The default TwoGenerationCollectorPolicy uses NewRatio; it
  // should check UseAdaptiveSizePolicy.  Changes from generationSizer could
  // move to the common code.
  yg_min_size = align_size_up(yg_min_size, yg_align);
  yg_max_size = align_size_up(yg_max_size, yg_align);
  size_t yg_cur_size = align_size_up(flag_parser.young_gen_size(), yg_align);
  yg_cur_size = MAX2(yg_cur_size, yg_min_size);

  og_min_size = align_size_up(og_min_size, og_align);
  og_max_size = align_size_up(og_max_size, og_align);
  size_t og_cur_size = align_size_up(flag_parser.old_gen_size(), og_align);
  og_cur_size = MAX2(og_cur_size, og_min_size);

  pg_min_size = align_size_up(pg_min_size, pg_align);
  pg_max_size = align_size_up(pg_max_size, pg_align);
  size_t pg_cur_size = pg_min_size;

  trace_gen_sizes("ps heap rnd",
                  pg_min_size, pg_max_size,
                  og_min_size, og_max_size,
                  yg_min_size, yg_max_size);

  const size_t total_reserved = pg_max_size + og_max_size + yg_max_size;
  char* addr = Universe::preferred_heap_base(total_reserved, Universe::UnscaledNarrowOop);

  // The main part of the heap (old gen + young gen) can often use a larger page
  // size than is needed or wanted for the perm gen.  Use the "compound
  // alignment" ReservedSpace ctor to avoid having to use the same page size for
  // all gens.

  ReservedHeapSpace heap_rs(pg_max_size, pg_align, og_max_size + yg_max_size,
                            og_align, addr);

  if (UseCompressedOops) {
    if (addr != NULL && !heap_rs.is_reserved()) {
      // Failed to reserve at specified address - the requested memory
      // region is taken already, for example, by 'java' launcher.
      // Try again to reserver heap higher.
      addr = Universe::preferred_heap_base(total_reserved, Universe::ZeroBasedNarrowOop);
      ReservedHeapSpace heap_rs0(pg_max_size, pg_align, og_max_size + yg_max_size,
                                 og_align, addr);
      if (addr != NULL && !heap_rs0.is_reserved()) {
        // Failed to reserve at specified address again - give up.
        addr = Universe::preferred_heap_base(total_reserved, Universe::HeapBasedNarrowOop);
        assert(addr == NULL, "");
        ReservedHeapSpace heap_rs1(pg_max_size, pg_align, og_max_size + yg_max_size,
                                   og_align, addr);
        heap_rs = heap_rs1;
      } else {
        heap_rs = heap_rs0;
      }
    }
  }

  os::trace_page_sizes("ps perm", pg_min_size, pg_max_size, pg_page_sz,
                       heap_rs.base(), pg_max_size);
  os::trace_page_sizes("ps main", og_min_size + yg_min_size,
                       og_max_size + yg_max_size, og_page_sz,
                       heap_rs.base() + pg_max_size,
                       heap_rs.size() - pg_max_size);
  if (!heap_rs.is_reserved()) {
    vm_shutdown_during_initialization(
      "Could not reserve enough space for object heap");
    return JNI_ENOMEM;
  }

  _reserved = MemRegion((HeapWord*)heap_rs.base(),
                        (HeapWord*)(heap_rs.base() + heap_rs.size()));

  CardTableExtension* const barrier_set = new CardTableExtension(_reserved, 3);
  _barrier_set = barrier_set;
  oopDesc::set_bs(_barrier_set);
  if (_barrier_set == NULL) {
    vm_shutdown_during_initialization(
      "Could not reserve enough space for barrier set");
    return JNI_ENOMEM;
  }

  // Initial young gen size is 4 Mb
  //
  // XXX - what about flag_parser.young_gen_size()?
  const size_t init_young_size = align_size_up(4 * M, yg_align);
  yg_cur_size = MAX2(MIN2(init_young_size, yg_max_size), yg_cur_size);

  // Split the reserved space into perm gen and the main heap (everything else).
  // The main heap uses a different alignment.
  ReservedSpace perm_rs = heap_rs.first_part(pg_max_size);
  ReservedSpace main_rs = heap_rs.last_part(pg_max_size, og_align);

  // Make up the generations
  // Calculate the maximum size that a generation can grow.  This
  // includes growth into the other generation.  Note that the
  // parameter _max_gen_size is kept as the maximum
  // size of the generation as the boundaries currently stand.
  // _max_gen_size is still used as that value.
  double max_gc_pause_sec = ((double) MaxGCPauseMillis)/1000.0;
  double max_gc_minor_pause_sec = ((double) MaxGCMinorPauseMillis)/1000.0;

  _gens = new AdjoiningGenerations(main_rs,
                                   og_cur_size,
                                   og_min_size,
                                   og_max_size,
                                   yg_cur_size,
                                   yg_min_size,
                                   yg_max_size,
                                   yg_align);

  _old_gen = _gens->old_gen();
  _young_gen = _gens->young_gen();

  const size_t eden_capacity = _young_gen->eden_space()->capacity_in_bytes();
  const size_t old_capacity = _old_gen->capacity_in_bytes();
  const size_t initial_promo_size = MIN2(eden_capacity, old_capacity);
  _size_policy =
    new PSAdaptiveSizePolicy(eden_capacity,
                             initial_promo_size,
                             young_gen()->to_space()->capacity_in_bytes(),
                             intra_heap_alignment(),
                             max_gc_pause_sec,
                             max_gc_minor_pause_sec,
                             GCTimeRatio
                             );

  _perm_gen = new PSPermGen(perm_rs,
                            pg_align,
                            pg_cur_size,
                            pg_cur_size,
                            pg_max_size,
                            "perm", 2);

  assert(!UseAdaptiveGCBoundary ||
    (old_gen()->virtual_space()->high_boundary() ==
     young_gen()->virtual_space()->low_boundary()),
    "Boundaries must meet");
  // initialize the policy counters - 2 collectors, 3 generations
  _gc_policy_counters =
    new PSGCAdaptivePolicyCounters("ParScav:MSC", 2, 3, _size_policy);
  _psh = this;

  // Set up the GCTaskManager
  _gc_task_manager = GCTaskManager::create(ParallelGCThreads);

  if (UseParallelOldGC && !PSParallelCompact::initialize()) {
    return JNI_ENOMEM;
  }

  return JNI_OK;
}

void ParallelScavengeHeap::post_initialize() {
  // Need to init the tenuring threshold
  PSScavenge::initialize();
  if (UseParallelOldGC) {
    PSParallelCompact::post_initialize();
  } else {
    PSMarkSweep::initialize();
  }
  PSPromotionManager::initialize();
}

void ParallelScavengeHeap::update_counters() {
  young_gen()->update_counters();
  old_gen()->update_counters();
  perm_gen()->update_counters();
}

size_t ParallelScavengeHeap::capacity() const {
  size_t value = young_gen()->capacity_in_bytes() + old_gen()->capacity_in_bytes();
  return value;
}

size_t ParallelScavengeHeap::used() const {
  size_t value = young_gen()->used_in_bytes() + old_gen()->used_in_bytes();
  return value;
}

bool ParallelScavengeHeap::is_maximal_no_gc() const {
  return old_gen()->is_maximal_no_gc() && young_gen()->is_maximal_no_gc();
}


size_t ParallelScavengeHeap::permanent_capacity() const {
  return perm_gen()->capacity_in_bytes();
}

size_t ParallelScavengeHeap::permanent_used() const {
  return perm_gen()->used_in_bytes();
}

size_t ParallelScavengeHeap::max_capacity() const {
  size_t estimated = reserved_region().byte_size();
  estimated -= perm_gen()->reserved().byte_size();
  if (UseAdaptiveSizePolicy) {
    estimated -= _size_policy->max_survivor_size(young_gen()->max_size());
  } else {
    estimated -= young_gen()->to_space()->capacity_in_bytes();
  }
  return MAX2(estimated, capacity());
}

bool ParallelScavengeHeap::is_in(const void* p) const {
  if (young_gen()->is_in(p)) {
    return true;
  }

  if (old_gen()->is_in(p)) {
    return true;
  }

  if (perm_gen()->is_in(p)) {
    return true;
  }

  return false;
}

bool ParallelScavengeHeap::is_in_reserved(const void* p) const {
  if (young_gen()->is_in_reserved(p)) {
    return true;
  }

  if (old_gen()->is_in_reserved(p)) {
    return true;
  }

  if (perm_gen()->is_in_reserved(p)) {
    return true;
  }

  return false;
}

// There are two levels of allocation policy here.
//
// When an allocation request fails, the requesting thread must invoke a VM
// operation, transfer control to the VM thread, and await the results of a
// garbage collection. That is quite expensive, and we should avoid doing it
// multiple times if possible.
//
// To accomplish this, we have a basic allocation policy, and also a
// failed allocation policy.
//
// The basic allocation policy controls how you allocate memory without
// attempting garbage collection. It is okay to grab locks and
// expand the heap, if that can be done without coming to a safepoint.
// It is likely that the basic allocation policy will not be very
// aggressive.
//
// The failed allocation policy is invoked from the VM thread after
// the basic allocation policy is unable to satisfy a mem_allocate
// request. This policy needs to cover the entire range of collection,
// heap expansion, and out-of-memory conditions. It should make every
// attempt to allocate the requested memory.

// Basic allocation policy. Should never be called at a safepoint, or
// from the VM thread.
//
// This method must handle cases where many mem_allocate requests fail
// simultaneously. When that happens, only one VM operation will succeed,
// and the rest will not be executed. For that reason, this method loops
// during failed allocation attempts. If the java heap becomes exhausted,
// we rely on the size_policy object to force a bail out.
HeapWord* ParallelScavengeHeap::mem_allocate(
                                     size_t size,
                                     bool is_noref,
                                     bool is_tlab,
                                     bool* gc_overhead_limit_was_exceeded) {
  assert(!SafepointSynchronize::is_at_safepoint(), "should not be at safepoint");
  assert(Thread::current() != (Thread*)VMThread::vm_thread(), "should not be in vm thread");
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");

  HeapWord* result = young_gen()->allocate(size, is_tlab);

  uint loop_count = 0;
  uint gc_count = 0;

  while (result == NULL) {
    // We don't want to have multiple collections for a single filled generation.
    // To prevent this, each thread tracks the total_collections() value, and if
    // the count has changed, does not do a new collection.
    //
    // The collection count must be read only while holding the heap lock. VM
    // operations also hold the heap lock during collections. There is a lock
    // contention case where thread A blocks waiting on the Heap_lock, while
    // thread B is holding it doing a collection. When thread A gets the lock,
    // the collection count has already changed. To prevent duplicate collections,
    // The policy MUST attempt allocations during the same period it reads the
    // total_collections() value!
    {
      MutexLocker ml(Heap_lock);
      gc_count = Universe::heap()->total_collections();

      result = young_gen()->allocate(size, is_tlab);

      // (1) If the requested object is too large to easily fit in the
      //     young_gen, or
      // (2) If GC is locked out via GCLocker, young gen is full and
      //     the need for a GC already signalled to GCLocker (done
      //     at a safepoint),
      // ... then, rather than force a safepoint and (a potentially futile)
      // collection (attempt) for each allocation, try allocation directly
      // in old_gen. For case (2) above, we may in the future allow
      // TLAB allocation directly in the old gen.
      if (result != NULL) {
        return result;
      }
      if (!is_tlab &&
          size >= (young_gen()->eden_space()->capacity_in_words(Thread::current()) / 2)) {
        result = old_gen()->allocate(size, is_tlab);
        if (result != NULL) {
          return result;
        }
      }
      if (GC_locker::is_active_and_needs_gc()) {
        // GC is locked out. If this is a TLAB allocation,
        // return NULL; the requestor will retry allocation
        // of an idividual object at a time.
        if (is_tlab) {
          return NULL;
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
    }

    if (result == NULL) {

      // Exit the loop if if the gc time limit has been exceeded.
      // The allocation must have failed above (result must be NULL),
      // and the most recent collection must have exceeded the
      // gc time limit.  Exit the loop so that an out-of-memory
      // will be thrown (returning a NULL will do that), but
      // clear gc_time_limit_exceeded so that the next collection
      // will succeeded if the applications decides to handle the
      // out-of-memory and tries to go on.
      *gc_overhead_limit_was_exceeded = size_policy()->gc_time_limit_exceeded();
      if (size_policy()->gc_time_limit_exceeded()) {
        size_policy()->set_gc_time_limit_exceeded(false);
        if (PrintGCDetails && Verbose) {
        gclog_or_tty->print_cr("ParallelScavengeHeap::mem_allocate: "
          "return NULL because gc_time_limit_exceeded is set");
        }
        return NULL;
      }

      // Generate a VM operation
      VM_ParallelGCFailedAllocation op(size, is_tlab, gc_count);
      VMThread::execute(&op);

      // Did the VM operation execute? If so, return the result directly.
      // This prevents us from looping until time out on requests that can
      // not be satisfied.
      if (op.prologue_succeeded()) {
        assert(Universe::heap()->is_in_or_null(op.result()),
          "result not in heap");

        // If GC was locked out during VM operation then retry allocation
        // and/or stall as necessary.
        if (op.gc_locked()) {
          assert(op.result() == NULL, "must be NULL if gc_locked() is true");
          continue;  // retry and/or stall as necessary
        }
        // If a NULL result is being returned, an out-of-memory
        // will be thrown now.  Clear the gc_time_limit_exceeded
        // flag to avoid the following situation.
        //      gc_time_limit_exceeded is set during a collection
        //      the collection fails to return enough space and an OOM is thrown
        //      the next GC is skipped because the gc_time_limit_exceeded
        //        flag is set and another OOM is thrown
        if (op.result() == NULL) {
          size_policy()->set_gc_time_limit_exceeded(false);
        }
        return op.result();
      }
    }

    // The policy object will prevent us from looping forever. If the
    // time spent in gc crosses a threshold, we will bail out.
    loop_count++;
    if ((result == NULL) && (QueuedAllocationWarningCount > 0) &&
        (loop_count % QueuedAllocationWarningCount == 0)) {
      warning("ParallelScavengeHeap::mem_allocate retries %d times \n\t"
              " size=%d %s", loop_count, size, is_tlab ? "(TLAB)" : "");
    }
  }

  return result;
}

// Failed allocation policy. Must be called from the VM thread, and
// only at a safepoint! Note that this method has policy for allocation
// flow, and NOT collection policy. So we do not check for gc collection
// time over limit here, that is the responsibility of the heap specific
// collection methods. This method decides where to attempt allocations,
// and when to attempt collections, but no collection specific policy.
HeapWord* ParallelScavengeHeap::failed_mem_allocate(size_t size, bool is_tlab) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == (Thread*)VMThread::vm_thread(), "should be in vm thread");
  assert(!Universe::heap()->is_gc_active(), "not reentrant");
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");

  size_t mark_sweep_invocation_count = total_invocations();

  // We assume (and assert!) that an allocation at this point will fail
  // unless we collect.

  // First level allocation failure, scavenge and allocate in young gen.
  GCCauseSetter gccs(this, GCCause::_allocation_failure);
  PSScavenge::invoke();
  HeapWord* result = young_gen()->allocate(size, is_tlab);

  // Second level allocation failure.
  //   Mark sweep and allocate in young generation.
  if (result == NULL) {
    // There is some chance the scavenge method decided to invoke mark_sweep.
    // Don't mark sweep twice if so.
    if (mark_sweep_invocation_count == total_invocations()) {
      invoke_full_gc(false);
      result = young_gen()->allocate(size, is_tlab);
    }
  }

  // Third level allocation failure.
  //   After mark sweep and young generation allocation failure,
  //   allocate in old generation.
  if (result == NULL && !is_tlab) {
    result = old_gen()->allocate(size, is_tlab);
  }

  // Fourth level allocation failure. We're running out of memory.
  //   More complete mark sweep and allocate in young generation.
  if (result == NULL) {
    invoke_full_gc(true);
    result = young_gen()->allocate(size, is_tlab);
  }

  // Fifth level allocation failure.
  //   After more complete mark sweep, allocate in old generation.
  if (result == NULL && !is_tlab) {
    result = old_gen()->allocate(size, is_tlab);
  }

  return result;
}

//
// This is the policy loop for allocating in the permanent generation.
// If the initial allocation fails, we create a vm operation which will
// cause a collection.
HeapWord* ParallelScavengeHeap::permanent_mem_allocate(size_t size) {
  assert(!SafepointSynchronize::is_at_safepoint(), "should not be at safepoint");
  assert(Thread::current() != (Thread*)VMThread::vm_thread(), "should not be in vm thread");
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");

  HeapWord* result;

  uint loop_count = 0;
  uint gc_count = 0;
  uint full_gc_count = 0;

  do {
    // We don't want to have multiple collections for a single filled generation.
    // To prevent this, each thread tracks the total_collections() value, and if
    // the count has changed, does not do a new collection.
    //
    // The collection count must be read only while holding the heap lock. VM
    // operations also hold the heap lock during collections. There is a lock
    // contention case where thread A blocks waiting on the Heap_lock, while
    // thread B is holding it doing a collection. When thread A gets the lock,
    // the collection count has already changed. To prevent duplicate collections,
    // The policy MUST attempt allocations during the same period it reads the
    // total_collections() value!
    {
      MutexLocker ml(Heap_lock);
      gc_count      = Universe::heap()->total_collections();
      full_gc_count = Universe::heap()->total_full_collections();

      result = perm_gen()->allocate_permanent(size);

      if (result != NULL) {
        return result;
      }

      if (GC_locker::is_active_and_needs_gc()) {
        // If this thread is not in a jni critical section, we stall
        // the requestor until the critical section has cleared and
        // GC allowed. When the critical section clears, a GC is
        // initiated by the last thread exiting the critical section; so
        // we retry the allocation sequence from the beginning of the loop,
        // rather than causing more, now probably unnecessary, GC attempts.
        JavaThread* jthr = JavaThread::current();
        if (!jthr->in_critical()) {
          MutexUnlocker mul(Heap_lock);
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
    }

    if (result == NULL) {

      // Exit the loop if the gc time limit has been exceeded.
      // The allocation must have failed above (result must be NULL),
      // and the most recent collection must have exceeded the
      // gc time limit.  Exit the loop so that an out-of-memory
      // will be thrown (returning a NULL will do that), but
      // clear gc_time_limit_exceeded so that the next collection
      // will succeeded if the applications decides to handle the
      // out-of-memory and tries to go on.
      if (size_policy()->gc_time_limit_exceeded()) {
        size_policy()->set_gc_time_limit_exceeded(false);
        if (PrintGCDetails && Verbose) {
        gclog_or_tty->print_cr("ParallelScavengeHeap::permanent_mem_allocate: "
          "return NULL because gc_time_limit_exceeded is set");
        }
        assert(result == NULL, "Allocation did not fail");
        return NULL;
      }

      // Generate a VM operation
      VM_ParallelGCFailedPermanentAllocation op(size, gc_count, full_gc_count);
      VMThread::execute(&op);

      // Did the VM operation execute? If so, return the result directly.
      // This prevents us from looping until time out on requests that can
      // not be satisfied.
      if (op.prologue_succeeded()) {
        assert(Universe::heap()->is_in_permanent_or_null(op.result()),
          "result not in heap");
        // If GC was locked out during VM operation then retry allocation
        // and/or stall as necessary.
        if (op.gc_locked()) {
          assert(op.result() == NULL, "must be NULL if gc_locked() is true");
          continue;  // retry and/or stall as necessary
        }
        // If a NULL results is being returned, an out-of-memory
        // will be thrown now.  Clear the gc_time_limit_exceeded
        // flag to avoid the following situation.
        //      gc_time_limit_exceeded is set during a collection
        //      the collection fails to return enough space and an OOM is thrown
        //      the next GC is skipped because the gc_time_limit_exceeded
        //        flag is set and another OOM is thrown
        if (op.result() == NULL) {
          size_policy()->set_gc_time_limit_exceeded(false);
        }
        return op.result();
      }
    }

    // The policy object will prevent us from looping forever. If the
    // time spent in gc crosses a threshold, we will bail out.
    loop_count++;
    if ((QueuedAllocationWarningCount > 0) &&
        (loop_count % QueuedAllocationWarningCount == 0)) {
      warning("ParallelScavengeHeap::permanent_mem_allocate retries %d times \n\t"
              " size=%d", loop_count, size);
    }
  } while (result == NULL);

  return result;
}

//
// This is the policy code for permanent allocations which have failed
// and require a collection. Note that just as in failed_mem_allocate,
// we do not set collection policy, only where & when to allocate and
// collect.
HeapWord* ParallelScavengeHeap::failed_permanent_mem_allocate(size_t size) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == (Thread*)VMThread::vm_thread(), "should be in vm thread");
  assert(!Universe::heap()->is_gc_active(), "not reentrant");
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");
  assert(size > perm_gen()->free_in_words(), "Allocation should fail");

  // We assume (and assert!) that an allocation at this point will fail
  // unless we collect.

  // First level allocation failure.  Mark-sweep and allocate in perm gen.
  GCCauseSetter gccs(this, GCCause::_allocation_failure);
  invoke_full_gc(false);
  HeapWord* result = perm_gen()->allocate_permanent(size);

  // Second level allocation failure. We're running out of memory.
  if (result == NULL) {
    invoke_full_gc(true);
    result = perm_gen()->allocate_permanent(size);
  }

  return result;
}

void ParallelScavengeHeap::ensure_parsability(bool retire_tlabs) {
  CollectedHeap::ensure_parsability(retire_tlabs);
  young_gen()->eden_space()->ensure_parsability();
}

size_t ParallelScavengeHeap::unsafe_max_alloc() {
  return young_gen()->eden_space()->free_in_bytes();
}

size_t ParallelScavengeHeap::tlab_capacity(Thread* thr) const {
  return young_gen()->eden_space()->tlab_capacity(thr);
}

size_t ParallelScavengeHeap::unsafe_max_tlab_alloc(Thread* thr) const {
  return young_gen()->eden_space()->unsafe_max_tlab_alloc(thr);
}

HeapWord* ParallelScavengeHeap::allocate_new_tlab(size_t size) {
  return young_gen()->allocate(size, true);
}

void ParallelScavengeHeap::fill_all_tlabs(bool retire) {
  CollectedHeap::fill_all_tlabs(retire);
}

void ParallelScavengeHeap::accumulate_statistics_all_tlabs() {
  CollectedHeap::accumulate_statistics_all_tlabs();
}

void ParallelScavengeHeap::resize_all_tlabs() {
  CollectedHeap::resize_all_tlabs();
}

bool ParallelScavengeHeap::can_elide_initializing_store_barrier(oop new_obj) {
  // We don't need barriers for stores to objects in the
  // young gen and, a fortiori, for initializing stores to
  // objects therein.
  return is_in_young(new_obj);
}

// This method is used by System.gc() and JVMTI.
void ParallelScavengeHeap::collect(GCCause::Cause cause) {
  assert(!Heap_lock->owned_by_self(),
    "this thread should not own the Heap_lock");

  unsigned int gc_count      = 0;
  unsigned int full_gc_count = 0;
  {
    MutexLocker ml(Heap_lock);
    // This value is guarded by the Heap_lock
    gc_count      = Universe::heap()->total_collections();
    full_gc_count = Universe::heap()->total_full_collections();
  }

  VM_ParallelGCSystemGC op(gc_count, full_gc_count, cause);
  VMThread::execute(&op);
}

// This interface assumes that it's being called by the
// vm thread. It collects the heap assuming that the
// heap lock is already held and that we are executing in
// the context of the vm thread.
void ParallelScavengeHeap::collect_as_vm_thread(GCCause::Cause cause) {
  assert(Thread::current()->is_VM_thread(), "Precondition#1");
  assert(Heap_lock->is_locked(), "Precondition#2");
  GCCauseSetter gcs(this, cause);
  switch (cause) {
    case GCCause::_heap_inspection:
    case GCCause::_heap_dump: {
      HandleMark hm;
      invoke_full_gc(false);
      break;
    }
    default: // XXX FIX ME
      ShouldNotReachHere();
  }
}


void ParallelScavengeHeap::oop_iterate(OopClosure* cl) {
  Unimplemented();
}

void ParallelScavengeHeap::object_iterate(ObjectClosure* cl) {
  young_gen()->object_iterate(cl);
  old_gen()->object_iterate(cl);
  perm_gen()->object_iterate(cl);
}

void ParallelScavengeHeap::permanent_oop_iterate(OopClosure* cl) {
  Unimplemented();
}

void ParallelScavengeHeap::permanent_object_iterate(ObjectClosure* cl) {
  perm_gen()->object_iterate(cl);
}

HeapWord* ParallelScavengeHeap::block_start(const void* addr) const {
  if (young_gen()->is_in_reserved(addr)) {
    assert(young_gen()->is_in(addr),
           "addr should be in allocated part of young gen");
    if (Debugging)  return NULL;  // called from find() in debug.cpp
    Unimplemented();
  } else if (old_gen()->is_in_reserved(addr)) {
    assert(old_gen()->is_in(addr),
           "addr should be in allocated part of old gen");
    return old_gen()->start_array()->object_start((HeapWord*)addr);
  } else if (perm_gen()->is_in_reserved(addr)) {
    assert(perm_gen()->is_in(addr),
           "addr should be in allocated part of perm gen");
    return perm_gen()->start_array()->object_start((HeapWord*)addr);
  }
  return 0;
}

size_t ParallelScavengeHeap::block_size(const HeapWord* addr) const {
  return oop(addr)->size();
}

bool ParallelScavengeHeap::block_is_obj(const HeapWord* addr) const {
  return block_start(addr) == addr;
}

jlong ParallelScavengeHeap::millis_since_last_gc() {
  return UseParallelOldGC ?
    PSParallelCompact::millis_since_last_gc() :
    PSMarkSweep::millis_since_last_gc();
}

void ParallelScavengeHeap::prepare_for_verify() {
  ensure_parsability(false);  // no need to retire TLABs for verification
}

void ParallelScavengeHeap::print() const { print_on(tty); }

void ParallelScavengeHeap::print_on(outputStream* st) const {
  young_gen()->print_on(st);
  old_gen()->print_on(st);
  perm_gen()->print_on(st);
}

void ParallelScavengeHeap::gc_threads_do(ThreadClosure* tc) const {
  PSScavenge::gc_task_manager()->threads_do(tc);
}

void ParallelScavengeHeap::print_gc_threads_on(outputStream* st) const {
  PSScavenge::gc_task_manager()->print_threads_on(st);
}

void ParallelScavengeHeap::print_tracing_info() const {
  if (TraceGen0Time) {
    double time = PSScavenge::accumulated_time()->seconds();
    tty->print_cr("[Accumulated GC generation 0 time %3.7f secs]", time);
  }
  if (TraceGen1Time) {
    double time = PSMarkSweep::accumulated_time()->seconds();
    tty->print_cr("[Accumulated GC generation 1 time %3.7f secs]", time);
  }
}


void ParallelScavengeHeap::verify(bool allow_dirty, bool silent, bool option /* ignored */) {
  // Why do we need the total_collections()-filter below?
  if (total_collections() > 0) {
    if (!silent) {
      gclog_or_tty->print("permanent ");
    }
    perm_gen()->verify(allow_dirty);

    if (!silent) {
      gclog_or_tty->print("tenured ");
    }
    old_gen()->verify(allow_dirty);

    if (!silent) {
      gclog_or_tty->print("eden ");
    }
    young_gen()->verify(allow_dirty);
  }
  if (!silent) {
    gclog_or_tty->print("ref_proc ");
  }
  ReferenceProcessor::verify();
}

void ParallelScavengeHeap::print_heap_change(size_t prev_used) {
  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print(" "  SIZE_FORMAT
                        "->" SIZE_FORMAT
                        "("  SIZE_FORMAT ")",
                        prev_used, used(), capacity());
  } else {
    gclog_or_tty->print(" "  SIZE_FORMAT "K"
                        "->" SIZE_FORMAT "K"
                        "("  SIZE_FORMAT "K)",
                        prev_used / K, used() / K, capacity() / K);
  }
}

ParallelScavengeHeap* ParallelScavengeHeap::heap() {
  assert(_psh != NULL, "Uninitialized access to ParallelScavengeHeap::heap()");
  assert(_psh->kind() == CollectedHeap::ParallelScavengeHeap, "not a parallel scavenge heap");
  return _psh;
}

// Before delegating the resize to the young generation,
// the reserved space for the young and old generations
// may be changed to accomodate the desired resize.
void ParallelScavengeHeap::resize_young_gen(size_t eden_size,
    size_t survivor_size) {
  if (UseAdaptiveGCBoundary) {
    if (size_policy()->bytes_absorbed_from_eden() != 0) {
      size_policy()->reset_bytes_absorbed_from_eden();
      return;  // The generation changed size already.
    }
    gens()->adjust_boundary_for_young_gen_needs(eden_size, survivor_size);
  }

  // Delegate the resize to the generation.
  _young_gen->resize(eden_size, survivor_size);
}

// Before delegating the resize to the old generation,
// the reserved space for the young and old generations
// may be changed to accomodate the desired resize.
void ParallelScavengeHeap::resize_old_gen(size_t desired_free_space) {
  if (UseAdaptiveGCBoundary) {
    if (size_policy()->bytes_absorbed_from_eden() != 0) {
      size_policy()->reset_bytes_absorbed_from_eden();
      return;  // The generation changed size already.
    }
    gens()->adjust_boundary_for_old_gen_needs(desired_free_space);
  }

  // Delegate the resize to the generation.
  _old_gen->resize(desired_free_space);
}

ParallelScavengeHeap::ParStrongRootsScope::ParStrongRootsScope() {
  // nothing particular
}

ParallelScavengeHeap::ParStrongRootsScope::~ParStrongRootsScope() {
  // nothing particular
}

#ifndef PRODUCT
void ParallelScavengeHeap::record_gen_tops_before_GC() {
  if (ZapUnusedHeapArea) {
    young_gen()->record_spaces_top();
    old_gen()->record_spaces_top();
    perm_gen()->record_spaces_top();
  }
}

void ParallelScavengeHeap::gen_mangle_unused_area() {
  if (ZapUnusedHeapArea) {
    young_gen()->eden_space()->mangle_unused_area();
    young_gen()->to_space()->mangle_unused_area();
    young_gen()->from_space()->mangle_unused_area();
    old_gen()->object_space()->mangle_unused_area();
    perm_gen()->object_space()->mangle_unused_area();
  }
}
#endif
