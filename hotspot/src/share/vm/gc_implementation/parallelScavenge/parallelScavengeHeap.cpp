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
#include "gc_implementation/parallelScavenge/adjoiningGenerations.hpp"
#include "gc_implementation/parallelScavenge/adjoiningVirtualSpaces.hpp"
#include "gc_implementation/parallelScavenge/cardTableExtension.hpp"
#include "gc_implementation/parallelScavenge/gcTaskManager.hpp"
#include "gc_implementation/parallelScavenge/generationSizer.hpp"
#include "gc_implementation/parallelScavenge/parallelScavengeHeap.inline.hpp"
#include "gc_implementation/parallelScavenge/psAdaptiveSizePolicy.hpp"
#include "gc_implementation/parallelScavenge/psMarkSweep.hpp"
#include "gc_implementation/parallelScavenge/psParallelCompact.hpp"
#include "gc_implementation/parallelScavenge/psPromotionManager.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.hpp"
#include "gc_implementation/parallelScavenge/vmPSOperations.hpp"
#include "gc_implementation/shared/gcHeapSummary.hpp"
#include "gc_implementation/shared/gcWhen.hpp"
#include "memory/gcLocker.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/vmThread.hpp"
#include "services/memTracker.hpp"
#include "utilities/vmError.hpp"

PSYoungGen*  ParallelScavengeHeap::_young_gen = NULL;
PSOldGen*    ParallelScavengeHeap::_old_gen = NULL;
PSAdaptiveSizePolicy* ParallelScavengeHeap::_size_policy = NULL;
PSGCAdaptivePolicyCounters* ParallelScavengeHeap::_gc_policy_counters = NULL;
ParallelScavengeHeap* ParallelScavengeHeap::_psh = NULL;
GCTaskManager* ParallelScavengeHeap::_gc_task_manager = NULL;

jint ParallelScavengeHeap::initialize() {
  CollectedHeap::pre_initialize();

  // Initialize collector policy
  _collector_policy = new GenerationSizer();
  _collector_policy->initialize_all();

  const size_t heap_size = _collector_policy->max_heap_byte_size();

  ReservedSpace heap_rs = Universe::reserve_heap(heap_size, _collector_policy->heap_alignment());
  MemTracker::record_virtual_memory_type((address)heap_rs.base(), mtJavaHeap);

  os::trace_page_sizes("ps main", _collector_policy->min_heap_byte_size(),
                       heap_size, generation_alignment(),
                       heap_rs.base(),
                       heap_rs.size());
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

  // Make up the generations
  // Calculate the maximum size that a generation can grow.  This
  // includes growth into the other generation.  Note that the
  // parameter _max_gen_size is kept as the maximum
  // size of the generation as the boundaries currently stand.
  // _max_gen_size is still used as that value.
  double max_gc_pause_sec = ((double) MaxGCPauseMillis)/1000.0;
  double max_gc_minor_pause_sec = ((double) MaxGCMinorPauseMillis)/1000.0;

  _gens = new AdjoiningGenerations(heap_rs, _collector_policy, generation_alignment());

  _old_gen = _gens->old_gen();
  _young_gen = _gens->young_gen();

  const size_t eden_capacity = _young_gen->eden_space()->capacity_in_bytes();
  const size_t old_capacity = _old_gen->capacity_in_bytes();
  const size_t initial_promo_size = MIN2(eden_capacity, old_capacity);
  _size_policy =
    new PSAdaptiveSizePolicy(eden_capacity,
                             initial_promo_size,
                             young_gen()->to_space()->capacity_in_bytes(),
                             _collector_policy->gen_alignment(),
                             max_gc_pause_sec,
                             max_gc_minor_pause_sec,
                             GCTimeRatio
                             );

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
  MetaspaceCounters::update_performance_counters();
  CompressedClassSpaceCounters::update_performance_counters();
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


size_t ParallelScavengeHeap::max_capacity() const {
  size_t estimated = reserved_region().byte_size();
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

  return false;
}

bool ParallelScavengeHeap::is_in_reserved(const void* p) const {
  if (young_gen()->is_in_reserved(p)) {
    return true;
  }

  if (old_gen()->is_in_reserved(p)) {
    return true;
  }

  return false;
}

bool ParallelScavengeHeap::is_scavengable(const void* addr) {
  return is_in_young((oop)addr);
}

#ifdef ASSERT
// Don't implement this by using is_in_young().  This method is used
// in some cases to check that is_in_young() is correct.
bool ParallelScavengeHeap::is_in_partial_collection(const void *p) {
  assert(is_in_reserved(p) || p == NULL,
    "Does not work if address is non-null and outside of the heap");
  // The order of the generations is old (low addr), young (high addr)
  return p >= old_gen()->reserved().end();
}
#endif

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
                                     bool* gc_overhead_limit_was_exceeded) {
  assert(!SafepointSynchronize::is_at_safepoint(), "should not be at safepoint");
  assert(Thread::current() != (Thread*)VMThread::vm_thread(), "should not be in vm thread");
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");

  // In general gc_overhead_limit_was_exceeded should be false so
  // set it so here and reset it to true only if the gc time
  // limit is being exceeded as checked below.
  *gc_overhead_limit_was_exceeded = false;

  HeapWord* result = young_gen()->allocate(size);

  uint loop_count = 0;
  uint gc_count = 0;
  int gclocker_stalled_count = 0;

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

      result = young_gen()->allocate(size);
      if (result != NULL) {
        return result;
      }

      // If certain conditions hold, try allocating from the old gen.
      result = mem_allocate_old_gen(size);
      if (result != NULL) {
        return result;
      }

      if (gclocker_stalled_count > GCLockerRetryAllocationCount) {
        return NULL;
      }

      // Failed to allocate without a gc.
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
    }

    if (result == NULL) {
      // Generate a VM operation
      VM_ParallelGCFailedAllocation op(size, gc_count);
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

        // Exit the loop if the gc time limit has been exceeded.
        // The allocation must have failed above ("result" guarding
        // this path is NULL) and the most recent collection has exceeded the
        // gc overhead limit (although enough may have been collected to
        // satisfy the allocation).  Exit the loop so that an out-of-memory
        // will be thrown (return a NULL ignoring the contents of
        // op.result()),
        // but clear gc_overhead_limit_exceeded so that the next collection
        // starts with a clean slate (i.e., forgets about previous overhead
        // excesses).  Fill op.result() with a filler object so that the
        // heap remains parsable.
        const bool limit_exceeded = size_policy()->gc_overhead_limit_exceeded();
        const bool softrefs_clear = collector_policy()->all_soft_refs_clear();

        if (limit_exceeded && softrefs_clear) {
          *gc_overhead_limit_was_exceeded = true;
          size_policy()->set_gc_overhead_limit_exceeded(false);
          if (PrintGCDetails && Verbose) {
            gclog_or_tty->print_cr("ParallelScavengeHeap::mem_allocate: "
              "return NULL because gc_overhead_limit_exceeded is set");
          }
          if (op.result() != NULL) {
            CollectedHeap::fill_with_object(op.result(), size);
          }
          return NULL;
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
              " size=%d", loop_count, size);
    }
  }

  return result;
}

// A "death march" is a series of ultra-slow allocations in which a full gc is
// done before each allocation, and after the full gc the allocation still
// cannot be satisfied from the young gen.  This routine detects that condition;
// it should be called after a full gc has been done and the allocation
// attempted from the young gen. The parameter 'addr' should be the result of
// that young gen allocation attempt.
void
ParallelScavengeHeap::death_march_check(HeapWord* const addr, size_t size) {
  if (addr != NULL) {
    _death_march_count = 0;  // death march has ended
  } else if (_death_march_count == 0) {
    if (should_alloc_in_eden(size)) {
      _death_march_count = 1;    // death march has started
    }
  }
}

HeapWord* ParallelScavengeHeap::mem_allocate_old_gen(size_t size) {
  if (!should_alloc_in_eden(size) || GC_locker::is_active_and_needs_gc()) {
    // Size is too big for eden, or gc is locked out.
    return old_gen()->allocate(size);
  }

  // If a "death march" is in progress, allocate from the old gen a limited
  // number of times before doing a GC.
  if (_death_march_count > 0) {
    if (_death_march_count < 64) {
      ++_death_march_count;
      return old_gen()->allocate(size);
    } else {
      _death_march_count = 0;
    }
  }
  return NULL;
}

void ParallelScavengeHeap::do_full_collection(bool clear_all_soft_refs) {
  if (UseParallelOldGC) {
    // The do_full_collection() parameter clear_all_soft_refs
    // is interpreted here as maximum_compaction which will
    // cause SoftRefs to be cleared.
    bool maximum_compaction = clear_all_soft_refs;
    PSParallelCompact::invoke(maximum_compaction);
  } else {
    PSMarkSweep::invoke(clear_all_soft_refs);
  }
}

// Failed allocation policy. Must be called from the VM thread, and
// only at a safepoint! Note that this method has policy for allocation
// flow, and NOT collection policy. So we do not check for gc collection
// time over limit here, that is the responsibility of the heap specific
// collection methods. This method decides where to attempt allocations,
// and when to attempt collections, but no collection specific policy.
HeapWord* ParallelScavengeHeap::failed_mem_allocate(size_t size) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == (Thread*)VMThread::vm_thread(), "should be in vm thread");
  assert(!Universe::heap()->is_gc_active(), "not reentrant");
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");

  // We assume that allocation in eden will fail unless we collect.

  // First level allocation failure, scavenge and allocate in young gen.
  GCCauseSetter gccs(this, GCCause::_allocation_failure);
  const bool invoked_full_gc = PSScavenge::invoke();
  HeapWord* result = young_gen()->allocate(size);

  // Second level allocation failure.
  //   Mark sweep and allocate in young generation.
  if (result == NULL && !invoked_full_gc) {
    do_full_collection(false);
    result = young_gen()->allocate(size);
  }

  death_march_check(result, size);

  // Third level allocation failure.
  //   After mark sweep and young generation allocation failure,
  //   allocate in old generation.
  if (result == NULL) {
    result = old_gen()->allocate(size);
  }

  // Fourth level allocation failure. We're running out of memory.
  //   More complete mark sweep and allocate in young generation.
  if (result == NULL) {
    do_full_collection(true);
    result = young_gen()->allocate(size);
  }

  // Fifth level allocation failure.
  //   After more complete mark sweep, allocate in old generation.
  if (result == NULL) {
    result = old_gen()->allocate(size);
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
  return young_gen()->allocate(size);
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

void ParallelScavengeHeap::oop_iterate(ExtendedOopClosure* cl) {
  Unimplemented();
}

void ParallelScavengeHeap::object_iterate(ObjectClosure* cl) {
  young_gen()->object_iterate(cl);
  old_gen()->object_iterate(cl);
}


HeapWord* ParallelScavengeHeap::block_start(const void* addr) const {
  if (young_gen()->is_in_reserved(addr)) {
    assert(young_gen()->is_in(addr),
           "addr should be in allocated part of young gen");
    // called from os::print_location by find or VMError
    if (Debugging || VMError::fatal_error_in_progress())  return NULL;
    Unimplemented();
  } else if (old_gen()->is_in_reserved(addr)) {
    assert(old_gen()->is_in(addr),
           "addr should be in allocated part of old gen");
    return old_gen()->start_array()->object_start((HeapWord*)addr);
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

PSHeapSummary ParallelScavengeHeap::create_ps_heap_summary() {
  PSOldGen* old = old_gen();
  HeapWord* old_committed_end = (HeapWord*)old->virtual_space()->committed_high_addr();
  VirtualSpaceSummary old_summary(old->reserved().start(), old_committed_end, old->reserved().end());
  SpaceSummary old_space(old->reserved().start(), old_committed_end, old->used_in_bytes());

  PSYoungGen* young = young_gen();
  VirtualSpaceSummary young_summary(young->reserved().start(),
    (HeapWord*)young->virtual_space()->committed_high_addr(), young->reserved().end());

  MutableSpace* eden = young_gen()->eden_space();
  SpaceSummary eden_space(eden->bottom(), eden->end(), eden->used_in_bytes());

  MutableSpace* from = young_gen()->from_space();
  SpaceSummary from_space(from->bottom(), from->end(), from->used_in_bytes());

  MutableSpace* to = young_gen()->to_space();
  SpaceSummary to_space(to->bottom(), to->end(), to->used_in_bytes());

  VirtualSpaceSummary heap_summary = create_heap_space_summary();
  return PSHeapSummary(heap_summary, used(), old_summary, old_space, young_summary, eden_space, from_space, to_space);
}

void ParallelScavengeHeap::print_on(outputStream* st) const {
  young_gen()->print_on(st);
  old_gen()->print_on(st);
  MetaspaceAux::print_on(st);
}

void ParallelScavengeHeap::print_on_error(outputStream* st) const {
  this->CollectedHeap::print_on_error(st);

  if (UseParallelOldGC) {
    st->cr();
    PSParallelCompact::print_on_error(st);
  }
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
    double time = UseParallelOldGC ? PSParallelCompact::accumulated_time()->seconds() : PSMarkSweep::accumulated_time()->seconds();
    tty->print_cr("[Accumulated GC generation 1 time %3.7f secs]", time);
  }
}


void ParallelScavengeHeap::verify(bool silent, VerifyOption option /* ignored */) {
  // Why do we need the total_collections()-filter below?
  if (total_collections() > 0) {
    if (!silent) {
      gclog_or_tty->print("tenured ");
    }
    old_gen()->verify();

    if (!silent) {
      gclog_or_tty->print("eden ");
    }
    young_gen()->verify();
  }
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

void ParallelScavengeHeap::trace_heap(GCWhen::Type when, GCTracer* gc_tracer) {
  const PSHeapSummary& heap_summary = create_ps_heap_summary();
  const MetaspaceSummary& metaspace_summary = create_metaspace_summary();
  gc_tracer->report_gc_heap_summary(when, heap_summary, metaspace_summary);
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
  }
}

void ParallelScavengeHeap::gen_mangle_unused_area() {
  if (ZapUnusedHeapArea) {
    young_gen()->eden_space()->mangle_unused_area();
    young_gen()->to_space()->mangle_unused_area();
    young_gen()->from_space()->mangle_unused_area();
    old_gen()->object_space()->mangle_unused_area();
  }
}
#endif
