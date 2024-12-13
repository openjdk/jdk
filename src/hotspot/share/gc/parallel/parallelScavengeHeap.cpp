/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/objectStartArray.inline.hpp"
#include "gc/parallel/parallelArguments.hpp"
#include "gc/parallel/parallelInitLogger.hpp"
#include "gc/parallel/parallelScavengeHeap.inline.hpp"
#include "gc/parallel/psAdaptiveSizePolicy.hpp"
#include "gc/parallel/psMemoryPool.hpp"
#include "gc/parallel/psParallelCompact.inline.hpp"
#include "gc/parallel/psPromotionManager.hpp"
#include "gc/parallel/psScavenge.hpp"
#include "gc/parallel/psVMOperations.hpp"
#include "gc/shared/fullGCForwarding.inline.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "gc/shared/gcWhen.hpp"
#include "gc/shared/genArguments.hpp"
#include "gc/shared/locationPrinter.inline.hpp"
#include "gc/shared/scavengableNMethods.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceCounters.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/universe.hpp"
#include "nmt/memTracker.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/cpuTimeCounters.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/vmThread.hpp"
#include "services/memoryManager.hpp"
#include "utilities/macros.hpp"
#include "utilities/vmError.hpp"

PSYoungGen*  ParallelScavengeHeap::_young_gen = nullptr;
PSOldGen*    ParallelScavengeHeap::_old_gen = nullptr;
PSAdaptiveSizePolicy* ParallelScavengeHeap::_size_policy = nullptr;
PSGCAdaptivePolicyCounters* ParallelScavengeHeap::_gc_policy_counters = nullptr;

jint ParallelScavengeHeap::initialize() {
  const size_t reserved_heap_size = ParallelArguments::heap_reserved_size_bytes();

  ReservedHeapSpace heap_rs = Universe::reserve_heap(reserved_heap_size, HeapAlignment);

  trace_actual_reserved_page_size(reserved_heap_size, heap_rs);

  initialize_reserved_region(heap_rs);
  // Layout the reserved space for the generations.
  ReservedSpace old_rs   = heap_rs.first_part(MaxOldSize, GenAlignment);
  ReservedSpace young_rs = heap_rs.last_part(MaxOldSize, GenAlignment);
  assert(young_rs.size() == MaxNewSize, "Didn't reserve all of the heap");

  PSCardTable* card_table = new PSCardTable(heap_rs.region());
  card_table->initialize(old_rs.base(), young_rs.base());

  CardTableBarrierSet* const barrier_set = new CardTableBarrierSet(card_table);
  barrier_set->initialize();
  BarrierSet::set_barrier_set(barrier_set);

  // Set up WorkerThreads
  _workers.initialize_workers();

  // Create and initialize the generations.
  _young_gen = new PSYoungGen(
      young_rs,
      NewSize,
      MinNewSize,
      MaxNewSize);
  _old_gen = new PSOldGen(
      old_rs,
      OldSize,
      MinOldSize,
      MaxOldSize,
      "old", 1);

  assert(young_gen()->max_gen_size() == young_rs.size(),"Consistency check");
  assert(old_gen()->max_gen_size() == old_rs.size(), "Consistency check");

  double max_gc_pause_sec = ((double) MaxGCPauseMillis)/1000.0;

  const size_t eden_capacity = _young_gen->eden_space()->capacity_in_bytes();
  const size_t old_capacity = _old_gen->capacity_in_bytes();
  const size_t initial_promo_size = MIN2(eden_capacity, old_capacity);
  _size_policy =
    new PSAdaptiveSizePolicy(eden_capacity,
                             initial_promo_size,
                             young_gen()->to_space()->capacity_in_bytes(),
                             GenAlignment,
                             max_gc_pause_sec,
                             GCTimeRatio
                             );

  assert((old_gen()->virtual_space()->high_boundary() ==
          young_gen()->virtual_space()->low_boundary()),
         "Boundaries must meet");
  // initialize the policy counters - 2 collectors, 2 generations
  _gc_policy_counters =
    new PSGCAdaptivePolicyCounters("ParScav:MSC", 2, 2, _size_policy);

  if (!PSParallelCompact::initialize_aux_data()) {
    return JNI_ENOMEM;
  }

  // Create CPU time counter
  CPUTimeCounters::create_counter(CPUTimeGroups::CPUTimeType::gc_parallel_workers);

  ParallelInitLogger::print();

  FullGCForwarding::initialize(heap_rs.region());

  return JNI_OK;
}

void ParallelScavengeHeap::initialize_serviceability() {

  _eden_pool = new EdenMutableSpacePool(_young_gen,
                                        _young_gen->eden_space(),
                                        "PS Eden Space",
                                        false /* support_usage_threshold */);

  _survivor_pool = new SurvivorMutableSpacePool(_young_gen,
                                                "PS Survivor Space",
                                                false /* support_usage_threshold */);

  _old_pool = new PSGenerationPool(_old_gen,
                                   "PS Old Gen",
                                   true /* support_usage_threshold */);

  _young_manager = new GCMemoryManager("PS Scavenge");
  _old_manager = new GCMemoryManager("PS MarkSweep");

  _old_manager->add_pool(_eden_pool);
  _old_manager->add_pool(_survivor_pool);
  _old_manager->add_pool(_old_pool);

  _young_manager->add_pool(_eden_pool);
  _young_manager->add_pool(_survivor_pool);

}

void ParallelScavengeHeap::safepoint_synchronize_begin() {
  if (UseStringDeduplication) {
    SuspendibleThreadSet::synchronize();
  }
}

void ParallelScavengeHeap::safepoint_synchronize_end() {
  if (UseStringDeduplication) {
    SuspendibleThreadSet::desynchronize();
  }
}
class PSIsScavengable : public BoolObjectClosure {
  bool do_object_b(oop obj) {
    return ParallelScavengeHeap::heap()->is_in_young(obj);
  }
};

static PSIsScavengable _is_scavengable;

void ParallelScavengeHeap::post_initialize() {
  CollectedHeap::post_initialize();
  // Need to init the tenuring threshold
  PSScavenge::initialize();
  PSParallelCompact::post_initialize();
  PSPromotionManager::initialize();

  ScavengableNMethods::initialize(&_is_scavengable);
}

void ParallelScavengeHeap::update_counters() {
  young_gen()->update_counters();
  old_gen()->update_counters();
  MetaspaceCounters::update_performance_counters();
  update_parallel_worker_threads_cpu_time();
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
  // We don't expand young-gen except at a GC.
  return old_gen()->is_maximal_no_gc();
}


size_t ParallelScavengeHeap::max_capacity() const {
  size_t estimated = reserved_region().byte_size();
  if (UseAdaptiveSizePolicy) {
    estimated -= _size_policy->max_survivor_size(young_gen()->max_gen_size());
  } else {
    estimated -= young_gen()->to_space()->capacity_in_bytes();
  }
  return MAX2(estimated, capacity());
}

bool ParallelScavengeHeap::is_in(const void* p) const {
  return young_gen()->is_in(p) || old_gen()->is_in(p);
}

bool ParallelScavengeHeap::is_in_reserved(const void* p) const {
  return young_gen()->is_in_reserved(p) || old_gen()->is_in_reserved(p);
}

bool ParallelScavengeHeap::requires_barriers(stackChunkOop p) const {
  return !is_in_young(p);
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
HeapWord* ParallelScavengeHeap::mem_allocate(size_t size,
                                             bool* gc_overhead_limit_was_exceeded) {
  assert(!SafepointSynchronize::is_at_safepoint(), "should not be at safepoint");
  assert(Thread::current() != (Thread*)VMThread::vm_thread(), "should not be in vm thread");
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");

  bool is_tlab = false;
  return mem_allocate_work(size, is_tlab, gc_overhead_limit_was_exceeded);
}

HeapWord* ParallelScavengeHeap::mem_allocate_work(size_t size,
                                                  bool is_tlab,
                                                  bool* gc_overhead_limit_was_exceeded) {

  // In general gc_overhead_limit_was_exceeded should be false so
  // set it so here and reset it to true only if the gc time
  // limit is being exceeded as checked below.
  *gc_overhead_limit_was_exceeded = false;

  HeapWord* result = young_gen()->allocate(size);

  uint loop_count = 0;
  uint gc_count = 0;
  uint gclocker_stalled_count = 0;

  while (result == nullptr) {
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
      gc_count = total_collections();

      result = young_gen()->allocate(size);
      if (result != nullptr) {
        return result;
      }

      // If certain conditions hold, try allocating from the old gen.
      if (!is_tlab) {
        result = mem_allocate_old_gen(size);
        if (result != nullptr) {
          return result;
        }
      }

      if (gclocker_stalled_count > GCLockerRetryAllocationCount) {
        return nullptr;
      }

      // Failed to allocate without a gc.
      if (GCLocker::is_active_and_needs_gc()) {
        // If this thread is not in a jni critical section, we stall
        // the requestor until the critical section has cleared and
        // GC allowed. When the critical section clears, a GC is
        // initiated by the last thread exiting the critical section; so
        // we retry the allocation sequence from the beginning of the loop,
        // rather than causing more, now probably unnecessary, GC attempts.
        JavaThread* jthr = JavaThread::current();
        if (!jthr->in_critical()) {
          MutexUnlocker mul(Heap_lock);
          GCLocker::stall_until_clear();
          gclocker_stalled_count += 1;
          continue;
        } else {
          if (CheckJNICalls) {
            fatal("Possible deadlock due to allocating while"
                  " in jni critical section");
          }
          return nullptr;
        }
      }
    }

    if (result == nullptr) {
      // Generate a VM operation
      VM_ParallelCollectForAllocation op(size, is_tlab, gc_count);
      VMThread::execute(&op);

      // Did the VM operation execute? If so, return the result directly.
      // This prevents us from looping until time out on requests that can
      // not be satisfied.
      if (op.prologue_succeeded()) {
        assert(is_in_or_null(op.result()), "result not in heap");

        // If GC was locked out during VM operation then retry allocation
        // and/or stall as necessary.
        if (op.gc_locked()) {
          assert(op.result() == nullptr, "must be null if gc_locked() is true");
          continue;  // retry and/or stall as necessary
        }

        // Exit the loop if the gc time limit has been exceeded.
        // The allocation must have failed above ("result" guarding
        // this path is null) and the most recent collection has exceeded the
        // gc overhead limit (although enough may have been collected to
        // satisfy the allocation).  Exit the loop so that an out-of-memory
        // will be thrown (return a null ignoring the contents of
        // op.result()),
        // but clear gc_overhead_limit_exceeded so that the next collection
        // starts with a clean slate (i.e., forgets about previous overhead
        // excesses).  Fill op.result() with a filler object so that the
        // heap remains parsable.
        const bool limit_exceeded = size_policy()->gc_overhead_limit_exceeded();
        const bool softrefs_clear = soft_ref_policy()->all_soft_refs_clear();

        if (limit_exceeded && softrefs_clear) {
          *gc_overhead_limit_was_exceeded = true;
          size_policy()->set_gc_overhead_limit_exceeded(false);
          log_trace(gc)("ParallelScavengeHeap::mem_allocate: return null because gc_overhead_limit_exceeded is set");
          if (op.result() != nullptr) {
            CollectedHeap::fill_with_object(op.result(), size);
          }
          return nullptr;
        }

        return op.result();
      }
    }

    // The policy object will prevent us from looping forever. If the
    // time spent in gc crosses a threshold, we will bail out.
    loop_count++;
    if ((result == nullptr) && (QueuedAllocationWarningCount > 0) &&
        (loop_count % QueuedAllocationWarningCount == 0)) {
      log_warning(gc)("ParallelScavengeHeap::mem_allocate retries %d times", loop_count);
      log_warning(gc)("\tsize=" SIZE_FORMAT, size);
    }
  }

  return result;
}

HeapWord* ParallelScavengeHeap::allocate_old_gen_and_record(size_t size) {
  assert_locked_or_safepoint(Heap_lock);
  HeapWord* res = old_gen()->allocate(size);
  if (res != nullptr) {
    _size_policy->tenured_allocation(size * HeapWordSize);
  }
  return res;
}

HeapWord* ParallelScavengeHeap::mem_allocate_old_gen(size_t size) {
  if (!should_alloc_in_eden(size) || GCLocker::is_active_and_needs_gc()) {
    // Size is too big for eden, or gc is locked out.
    return allocate_old_gen_and_record(size);
  }

  return nullptr;
}

void ParallelScavengeHeap::do_full_collection(bool clear_all_soft_refs) {
  if (GCLocker::check_active_before_gc()) {
    return;
  }
  PSParallelCompact::invoke(clear_all_soft_refs);
}

HeapWord* ParallelScavengeHeap::expand_heap_and_allocate(size_t size, bool is_tlab) {
  HeapWord* result = nullptr;

  result = young_gen()->allocate(size);
  if (result == nullptr && !is_tlab) {
    result = old_gen()->expand_and_allocate(size);
  }
  return result;   // Could be null if we are out of space.
}

HeapWord* ParallelScavengeHeap::satisfy_failed_allocation(size_t size, bool is_tlab) {
  assert(size != 0, "precondition");

  HeapWord* result = nullptr;

  GCLocker::check_active_before_gc();
  if (GCLocker::is_active_and_needs_gc()) {
    return expand_heap_and_allocate(size, is_tlab);
  }

  // If young-gen can handle this allocation, attempt young-gc firstly.
  bool should_run_young_gc = is_tlab || should_alloc_in_eden(size);
  collect_at_safepoint(!should_run_young_gc);

  result = expand_heap_and_allocate(size, is_tlab);
  if (result != nullptr) {
    return result;
  }

  // If we reach this point, we're really out of memory. Try every trick
  // we can to reclaim memory. Force collection of soft references. Force
  // a complete compaction of the heap. Any additional methods for finding
  // free memory should be here, especially if they are expensive. If this
  // attempt fails, an OOM exception will be thrown.
  {
    // Make sure the heap is fully compacted
    uintx old_interval = HeapMaximumCompactionInterval;
    HeapMaximumCompactionInterval = 0;

    const bool clear_all_soft_refs = true;
    PSParallelCompact::invoke(clear_all_soft_refs);

    // Restore
    HeapMaximumCompactionInterval = old_interval;
  }

  result = expand_heap_and_allocate(size, is_tlab);
  if (result != nullptr) {
    return result;
  }

  // What else?  We might try synchronous finalization later.  If the total
  // space available is large enough for the allocation, then a more
  // complete compaction phase than we've tried so far might be
  // appropriate.
  return nullptr;
}


void ParallelScavengeHeap::ensure_parsability(bool retire_tlabs) {
  CollectedHeap::ensure_parsability(retire_tlabs);
  young_gen()->eden_space()->ensure_parsability();
}

size_t ParallelScavengeHeap::tlab_capacity(Thread* thr) const {
  return young_gen()->eden_space()->tlab_capacity(thr);
}

size_t ParallelScavengeHeap::tlab_used(Thread* thr) const {
  return young_gen()->eden_space()->tlab_used(thr);
}

size_t ParallelScavengeHeap::unsafe_max_tlab_alloc(Thread* thr) const {
  return young_gen()->eden_space()->unsafe_max_tlab_alloc(thr);
}

HeapWord* ParallelScavengeHeap::allocate_new_tlab(size_t min_size, size_t requested_size, size_t* actual_size) {
  bool dummy;
  HeapWord* result = mem_allocate_work(requested_size /* size */,
                                       true /* is_tlab */,
                                       &dummy);
  if (result != nullptr) {
    *actual_size = requested_size;
  }

  return result;
}

void ParallelScavengeHeap::resize_all_tlabs() {
  CollectedHeap::resize_all_tlabs();
}

void ParallelScavengeHeap::prune_scavengable_nmethods() {
  ScavengableNMethods::prune_nmethods_not_into_young();
}

void ParallelScavengeHeap::prune_unlinked_nmethods() {
  ScavengableNMethods::prune_unlinked_nmethods();
}

void ParallelScavengeHeap::collect(GCCause::Cause cause) {
  assert(!Heap_lock->owned_by_self(),
    "this thread should not own the Heap_lock");

  uint gc_count      = 0;
  uint full_gc_count = 0;
  {
    MutexLocker ml(Heap_lock);
    // This value is guarded by the Heap_lock
    gc_count      = total_collections();
    full_gc_count = total_full_collections();
  }

  if (GCLocker::should_discard(cause, gc_count)) {
    return;
  }

  while (true) {
    VM_ParallelGCCollect op(gc_count, full_gc_count, cause);
    VMThread::execute(&op);

    if (!GCCause::is_explicit_full_gc(cause)) {
      return;
    }

    {
      MutexLocker ml(Heap_lock);
      if (full_gc_count != total_full_collections()) {
        return;
      }
    }

    if (GCLocker::is_active_and_needs_gc()) {
      // If GCLocker is active, wait until clear before retrying.
      GCLocker::stall_until_clear();
    }
  }
}

void ParallelScavengeHeap::try_collect_at_safepoint(bool full) {
  assert(SafepointSynchronize::is_at_safepoint(), "precondition");
  if (GCLocker::check_active_before_gc()) {
    return;
  }
  collect_at_safepoint(full);
}

bool ParallelScavengeHeap::must_clear_all_soft_refs() {
  return _gc_cause == GCCause::_metadata_GC_clear_soft_refs ||
         _gc_cause == GCCause::_wb_full_gc;
}

void ParallelScavengeHeap::collect_at_safepoint(bool full) {
  assert(!GCLocker::is_active(), "precondition");
  bool clear_soft_refs = must_clear_all_soft_refs();

  if (!full) {
    bool success = PSScavenge::invoke(clear_soft_refs);
    if (success) {
      return;
    }
    // Upgrade to Full-GC if young-gc fails
  }
  PSParallelCompact::invoke(clear_soft_refs);
}

void ParallelScavengeHeap::object_iterate(ObjectClosure* cl) {
  young_gen()->object_iterate(cl);
  old_gen()->object_iterate(cl);
}

// The HeapBlockClaimer is used during parallel iteration over the heap,
// allowing workers to claim heap areas ("blocks"), gaining exclusive rights to these.
// The eden and survivor spaces are treated as single blocks as it is hard to divide
// these spaces.
// The old space is divided into fixed-size blocks.
class HeapBlockClaimer : public StackObj {
  size_t _claimed_index;

public:
  static const size_t InvalidIndex = SIZE_MAX;
  static const size_t EdenIndex = 0;
  static const size_t SurvivorIndex = 1;
  static const size_t NumNonOldGenClaims = 2;

  HeapBlockClaimer() : _claimed_index(EdenIndex) { }
  // Claim the block and get the block index.
  size_t claim_and_get_block() {
    size_t block_index;
    block_index = Atomic::fetch_then_add(&_claimed_index, 1u);

    PSOldGen* old_gen = ParallelScavengeHeap::heap()->old_gen();
    size_t num_claims = old_gen->num_iterable_blocks() + NumNonOldGenClaims;

    return block_index < num_claims ? block_index : InvalidIndex;
  }
};

void ParallelScavengeHeap::object_iterate_parallel(ObjectClosure* cl,
                                                   HeapBlockClaimer* claimer) {
  size_t block_index = claimer->claim_and_get_block();
  // Iterate until all blocks are claimed
  if (block_index == HeapBlockClaimer::EdenIndex) {
    young_gen()->eden_space()->object_iterate(cl);
    block_index = claimer->claim_and_get_block();
  }
  if (block_index == HeapBlockClaimer::SurvivorIndex) {
    young_gen()->from_space()->object_iterate(cl);
    young_gen()->to_space()->object_iterate(cl);
    block_index = claimer->claim_and_get_block();
  }
  while (block_index != HeapBlockClaimer::InvalidIndex) {
    old_gen()->object_iterate_block(cl, block_index - HeapBlockClaimer::NumNonOldGenClaims);
    block_index = claimer->claim_and_get_block();
  }
}

class PSScavengeParallelObjectIterator : public ParallelObjectIteratorImpl {
private:
  ParallelScavengeHeap*  _heap;
  HeapBlockClaimer      _claimer;

public:
  PSScavengeParallelObjectIterator() :
      _heap(ParallelScavengeHeap::heap()),
      _claimer() {}

  virtual void object_iterate(ObjectClosure* cl, uint worker_id) {
    _heap->object_iterate_parallel(cl, &_claimer);
  }
};

ParallelObjectIteratorImpl* ParallelScavengeHeap::parallel_object_iterator(uint thread_num) {
  return new PSScavengeParallelObjectIterator();
}

HeapWord* ParallelScavengeHeap::block_start(const void* addr) const {
  if (young_gen()->is_in_reserved(addr)) {
    assert(young_gen()->is_in(addr),
           "addr should be in allocated part of young gen");
    // called from os::print_location by find or VMError
    if (DebuggingContext::is_enabled() || VMError::is_error_reported()) {
      return nullptr;
    }
    Unimplemented();
  } else if (old_gen()->is_in_reserved(addr)) {
    assert(old_gen()->is_in(addr),
           "addr should be in allocated part of old gen");
    return old_gen()->start_array()->object_start((HeapWord*)addr);
  }
  return nullptr;
}

bool ParallelScavengeHeap::block_is_obj(const HeapWord* addr) const {
  return block_start(addr) == addr;
}

void ParallelScavengeHeap::prepare_for_verify() {
  ensure_parsability(false);  // no need to retire TLABs for verification
}

PSHeapSummary ParallelScavengeHeap::create_ps_heap_summary() {
  PSOldGen* old = old_gen();
  HeapWord* old_committed_end = (HeapWord*)old->virtual_space()->committed_high_addr();
  HeapWord* old_reserved_start = old->reserved().start();
  HeapWord* old_reserved_end = old->reserved().end();
  VirtualSpaceSummary old_summary(old_reserved_start, old_committed_end, old_reserved_end);
  SpaceSummary old_space(old_reserved_start, old_committed_end, old->used_in_bytes());

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

bool ParallelScavengeHeap::print_location(outputStream* st, void* addr) const {
  return BlockLocationPrinter<ParallelScavengeHeap>::print_location(st, addr);
}

void ParallelScavengeHeap::print_on(outputStream* st) const {
  if (young_gen() != nullptr) {
    young_gen()->print_on(st);
  }
  if (old_gen() != nullptr) {
    old_gen()->print_on(st);
  }
  MetaspaceUtils::print_on(st);
}

void ParallelScavengeHeap::print_on_error(outputStream* st) const {
  this->CollectedHeap::print_on_error(st);

  st->cr();
  PSParallelCompact::print_on_error(st);
}

void ParallelScavengeHeap::gc_threads_do(ThreadClosure* tc) const {
  ParallelScavengeHeap::heap()->workers().threads_do(tc);
}

void ParallelScavengeHeap::print_tracing_info() const {
  AdaptiveSizePolicyOutput::print();
  log_debug(gc, heap, exit)("Accumulated young generation GC time %3.7f secs", PSScavenge::accumulated_time()->seconds());
  log_debug(gc, heap, exit)("Accumulated old generation GC time %3.7f secs", PSParallelCompact::accumulated_time()->seconds());
}

PreGenGCValues ParallelScavengeHeap::get_pre_gc_values() const {
  const PSYoungGen* const young = young_gen();
  const MutableSpace* const eden = young->eden_space();
  const MutableSpace* const from = young->from_space();
  const PSOldGen* const old = old_gen();

  return PreGenGCValues(young->used_in_bytes(),
                        young->capacity_in_bytes(),
                        eden->used_in_bytes(),
                        eden->capacity_in_bytes(),
                        from->used_in_bytes(),
                        from->capacity_in_bytes(),
                        old->used_in_bytes(),
                        old->capacity_in_bytes());
}

void ParallelScavengeHeap::print_heap_change(const PreGenGCValues& pre_gc_values) const {
  const PSYoungGen* const young = young_gen();
  const MutableSpace* const eden = young->eden_space();
  const MutableSpace* const from = young->from_space();
  const PSOldGen* const old = old_gen();

  log_info(gc, heap)(HEAP_CHANGE_FORMAT" "
                     HEAP_CHANGE_FORMAT" "
                     HEAP_CHANGE_FORMAT,
                     HEAP_CHANGE_FORMAT_ARGS(young->name(),
                                             pre_gc_values.young_gen_used(),
                                             pre_gc_values.young_gen_capacity(),
                                             young->used_in_bytes(),
                                             young->capacity_in_bytes()),
                     HEAP_CHANGE_FORMAT_ARGS("Eden",
                                             pre_gc_values.eden_used(),
                                             pre_gc_values.eden_capacity(),
                                             eden->used_in_bytes(),
                                             eden->capacity_in_bytes()),
                     HEAP_CHANGE_FORMAT_ARGS("From",
                                             pre_gc_values.from_used(),
                                             pre_gc_values.from_capacity(),
                                             from->used_in_bytes(),
                                             from->capacity_in_bytes()));
  log_info(gc, heap)(HEAP_CHANGE_FORMAT,
                     HEAP_CHANGE_FORMAT_ARGS(old->name(),
                                             pre_gc_values.old_gen_used(),
                                             pre_gc_values.old_gen_capacity(),
                                             old->used_in_bytes(),
                                             old->capacity_in_bytes()));
  MetaspaceUtils::print_metaspace_change(pre_gc_values.metaspace_sizes());
}

void ParallelScavengeHeap::verify(VerifyOption option /* ignored */) {
  // Why do we need the total_collections()-filter below?
  if (total_collections() > 0) {
    log_debug(gc, verify)("Tenured");
    old_gen()->verify();

    log_debug(gc, verify)("Eden");
    young_gen()->verify();

    log_debug(gc, verify)("CardTable");
    card_table()->verify_all_young_refs_imprecise();
  }
}

void ParallelScavengeHeap::trace_actual_reserved_page_size(const size_t reserved_heap_size, const ReservedSpace rs) {
  // Check if Info level is enabled, since os::trace_page_sizes() logs on Info level.
  if(log_is_enabled(Info, pagesize)) {
    const size_t page_size = rs.page_size();
    os::trace_page_sizes("Heap",
                         MinHeapSize,
                         reserved_heap_size,
                         rs.base(),
                         rs.size(),
                         page_size);
  }
}

void ParallelScavengeHeap::trace_heap(GCWhen::Type when, const GCTracer* gc_tracer) {
  const PSHeapSummary& heap_summary = create_ps_heap_summary();
  gc_tracer->report_gc_heap_summary(when, heap_summary);

  const MetaspaceSummary& metaspace_summary = create_metaspace_summary();
  gc_tracer->report_metaspace_summary(when, metaspace_summary);
}

CardTableBarrierSet* ParallelScavengeHeap::barrier_set() {
  return barrier_set_cast<CardTableBarrierSet>(BarrierSet::barrier_set());
}

PSCardTable* ParallelScavengeHeap::card_table() {
  return static_cast<PSCardTable*>(barrier_set()->card_table());
}

void ParallelScavengeHeap::resize_young_gen(size_t eden_size,
                                            size_t survivor_size) {
  // Delegate the resize to the generation.
  _young_gen->resize(eden_size, survivor_size);
}

void ParallelScavengeHeap::resize_old_gen(size_t desired_free_space) {
  // Delegate the resize to the generation.
  _old_gen->resize(desired_free_space);
}

HeapWord* ParallelScavengeHeap::allocate_loaded_archive_space(size_t size) {
  return _old_gen->allocate(size);
}

void ParallelScavengeHeap::complete_loaded_archive_space(MemRegion archive_space) {
  assert(_old_gen->object_space()->used_region().contains(archive_space),
         "Archive space not contained in old gen");
  _old_gen->complete_loaded_archive_space(archive_space);
}

void ParallelScavengeHeap::register_nmethod(nmethod* nm) {
  ScavengableNMethods::register_nmethod(nm);
}

void ParallelScavengeHeap::unregister_nmethod(nmethod* nm) {
  ScavengableNMethods::unregister_nmethod(nm);
}

void ParallelScavengeHeap::verify_nmethod(nmethod* nm) {
  ScavengableNMethods::verify_nmethod(nm);
}

GrowableArray<GCMemoryManager*> ParallelScavengeHeap::memory_managers() {
  GrowableArray<GCMemoryManager*> memory_managers(2);
  memory_managers.append(_young_manager);
  memory_managers.append(_old_manager);
  return memory_managers;
}

GrowableArray<MemoryPool*> ParallelScavengeHeap::memory_pools() {
  GrowableArray<MemoryPool*> memory_pools(3);
  memory_pools.append(_eden_pool);
  memory_pools.append(_survivor_pool);
  memory_pools.append(_old_pool);
  return memory_pools;
}

void ParallelScavengeHeap::pin_object(JavaThread* thread, oop obj) {
  GCLocker::lock_critical(thread);
}

void ParallelScavengeHeap::unpin_object(JavaThread* thread, oop obj) {
  GCLocker::unlock_critical(thread);
}

void ParallelScavengeHeap::update_parallel_worker_threads_cpu_time() {
  assert(Thread::current()->is_VM_thread(),
         "Must be called from VM thread to avoid races");
  if (!UsePerfData || !os::is_thread_cpu_time_supported()) {
    return;
  }

  // Ensure ThreadTotalCPUTimeClosure destructor is called before publishing gc
  // time.
  {
    ThreadTotalCPUTimeClosure tttc(CPUTimeGroups::CPUTimeType::gc_parallel_workers);
    // Currently parallel worker threads in GCTaskManager never terminate, so it
    // is safe for VMThread to read their CPU times. If upstream changes this
    // behavior, we should rethink if it is still safe.
    gc_threads_do(&tttc);
  }

  CPUTimeCounters::publish_gc_total_cpu_time();
}
