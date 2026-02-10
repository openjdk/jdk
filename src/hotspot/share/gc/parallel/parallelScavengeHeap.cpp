/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/barrierSetNMethod.hpp"
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
#include "memory/reservedSpace.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/cpuTimeCounters.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/vmThread.hpp"
#include "services/memoryManager.hpp"
#include "utilities/macros.hpp"
#include "utilities/vmError.hpp"

PSAdaptiveSizePolicy* ParallelScavengeHeap::_size_policy = nullptr;
GCPolicyCounters* ParallelScavengeHeap::_gc_policy_counters = nullptr;
size_t ParallelScavengeHeap::_desired_page_size = 0;

jint ParallelScavengeHeap::initialize() {
  const size_t reserved_heap_size = ParallelArguments::heap_reserved_size_bytes();

  assert(_desired_page_size != 0, "Should be initialized");
  ReservedHeapSpace heap_rs = Universe::reserve_heap(reserved_heap_size, HeapAlignment, _desired_page_size);
  // Adjust SpaceAlignment based on actually used large page size.
  if (UseLargePages) {
    SpaceAlignment = MAX2(heap_rs.page_size(), default_space_alignment());
  }
  assert(is_aligned(SpaceAlignment, heap_rs.page_size()), "inv");

  trace_actual_reserved_page_size(reserved_heap_size, heap_rs);

  initialize_reserved_region(heap_rs);
  // Layout the reserved space for the generations.
  ReservedSpace old_rs   = heap_rs.first_part(MaxOldSize, SpaceAlignment);
  ReservedSpace young_rs = heap_rs.last_part(MaxOldSize, SpaceAlignment);
  assert(young_rs.size() == MaxNewSize, "Didn't reserve all of the heap");

  PSCardTable* card_table = new PSCardTable(_reserved);
  card_table->initialize(old_rs.base(), young_rs.base());

  CardTableBarrierSet* const barrier_set = new CardTableBarrierSet(card_table);
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
      MaxOldSize);

  assert(young_gen()->max_gen_size() == young_rs.size(),"Consistency check");
  assert(old_gen()->max_gen_size() == old_rs.size(), "Consistency check");

  double max_gc_pause_sec = ((double) MaxGCPauseMillis)/1000.0;

  _size_policy = new PSAdaptiveSizePolicy(SpaceAlignment,
                                          max_gc_pause_sec);

  assert((old_gen()->virtual_space()->high_boundary() ==
          young_gen()->virtual_space()->low_boundary()),
         "Boundaries must meet");
  // initialize the policy counters - 2 collectors, 2 generations
  _gc_policy_counters = new GCPolicyCounters("ParScav:MSC", 2, 2);

  if (!PSParallelCompact::initialize_aux_data()) {
    return JNI_ENOMEM;
  }

  // Create CPU time counter
  CPUTimeCounters::create_counter(CPUTimeGroups::CPUTimeType::gc_parallel_workers);

  ParallelInitLogger::print();

  FullGCForwarding::initialize(_reserved);

  return JNI_OK;
}

void ParallelScavengeHeap::initialize_serviceability() {

  _eden_pool = new PSEdenSpacePool(_young_gen,
                                   _young_gen->eden_space(),
                                   "PS Eden Space",
                                   false /* support_usage_threshold */);

  _survivor_pool = new PSSurvivorSpacePool(_young_gen,
                                           "PS Survivor Space",
                                           false /* support_usage_threshold */);

  _old_pool = new PSOldGenerationPool(_old_gen,
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
  GCLocker::initialize();
}

void ParallelScavengeHeap::gc_epilogue(bool full) {
  if (_is_heap_almost_full) {
    // Reset emergency state if eden is empty after a young/full gc
    if (_young_gen->eden_space()->is_empty()) {
      log_debug(gc)("Leaving memory constrained state; back to normal");
      _is_heap_almost_full = false;
    }
  } else {
    if (full && !_young_gen->eden_space()->is_empty()) {
      log_debug(gc)("Non-empty young-gen after full-gc; in memory constrained state");
      _is_heap_almost_full = true;
    }
  }
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
HeapWord* ParallelScavengeHeap::mem_allocate(size_t size) {
  assert(!SafepointSynchronize::is_at_safepoint(), "should not be at safepoint");
  assert(Thread::current() != (Thread*)VMThread::vm_thread(), "should not be in vm thread");
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");

  bool is_tlab = false;
  return mem_allocate_work(size, is_tlab);
}

HeapWord* ParallelScavengeHeap::mem_allocate_cas_noexpand(size_t size, bool is_tlab) {
  // Try young-gen first.
  HeapWord* result = young_gen()->allocate(size);
  if (result != nullptr) {
    return result;
  }

  // Try allocating from the old gen for non-TLAB and large allocations.
  if (!is_tlab) {
    if (!should_alloc_in_eden(size)) {
      result = old_gen()->cas_allocate_noexpand(size);
      if (result != nullptr) {
        return result;
      }
    }
  }

  // In extreme cases, try allocating in from space also.
  if (_is_heap_almost_full) {
    result = young_gen()->from_space()->cas_allocate(size);
    if (result != nullptr) {
      return result;
    }
    if (!is_tlab) {
      result = old_gen()->cas_allocate_noexpand(size);
      if (result != nullptr) {
        return result;
      }
    }
  }

  return nullptr;
}

HeapWord* ParallelScavengeHeap::mem_allocate_work(size_t size, bool is_tlab) {
  for (uint loop_count = 0; /* empty */; ++loop_count) {
    HeapWord* result;
    {
      ConditionalMutexLocker locker(Heap_lock, !is_init_completed());
      result = mem_allocate_cas_noexpand(size, is_tlab);
      if (result != nullptr) {
        return result;
      }
    }

    // Read total_collections() under the lock so that multiple
    // allocation-failures result in one GC.
    uint gc_count;
    {
      MutexLocker ml(Heap_lock);

      // Re-try after acquiring the lock, because a GC might have occurred
      // while waiting for this lock.
      result = mem_allocate_cas_noexpand(size, is_tlab);
      if (result != nullptr) {
        return result;
      }

      if (!is_init_completed()) {
        // Double checked locking, this ensure that is_init_completed() does not
        // transition while expanding the heap.
        MonitorLocker ml(InitCompleted_lock, Monitor::_no_safepoint_check_flag);
        if (!is_init_completed()) {
          // Can't do GC; try heap expansion to satisfy the request.
          result = expand_heap_and_allocate(size, is_tlab);
          if (result != nullptr) {
            return result;
          }
        }
      }

      gc_count = total_collections();
    }

    {
      VM_ParallelCollectForAllocation op(size, is_tlab, gc_count);
      VMThread::execute(&op);

      if (op.gc_succeeded()) {
        assert(is_in_or_null(op.result()), "result not in heap");
        return op.result();
      }
    }

    // Was the gc-overhead reached inside the safepoint? If so, this mutator
    // should return null as well for global consistency.
    if (_gc_overhead_counter >= GCOverheadLimitThreshold) {
      return nullptr;
    }

    if ((QueuedAllocationWarningCount > 0) &&
        (loop_count % QueuedAllocationWarningCount == 0)) {
      log_warning(gc)("ParallelScavengeHeap::mem_allocate retries %d times, size=%zu", loop_count, size);
    }
  }
}

void ParallelScavengeHeap::do_full_collection(bool clear_all_soft_refs) {
  // No need for max-compaction in this context.
  const bool should_do_max_compaction = false;
  PSParallelCompact::invoke(clear_all_soft_refs, should_do_max_compaction);
}

bool ParallelScavengeHeap::should_attempt_young_gc() const {
  const bool ShouldRunYoungGC = true;
  const bool ShouldRunFullGC = false;

  if (!_young_gen->to_space()->is_empty()) {
    log_debug(gc, ergo)("To-space is not empty; run full-gc instead.");
    return ShouldRunFullGC;
  }

  // Check if the predicted promoted bytes will overflow free space in old-gen.
  PSAdaptiveSizePolicy* policy = _size_policy;

  size_t avg_promoted = (size_t) policy->padded_average_promoted_in_bytes();
  size_t promotion_estimate = MIN2(avg_promoted, _young_gen->used_in_bytes());
  // Total free size after possible old gen expansion
  size_t free_in_old_gen_with_expansion = _old_gen->max_gen_size() - _old_gen->used_in_bytes();

  log_trace(gc, ergo)("average_promoted %zu; padded_average_promoted %zu",
              (size_t) policy->average_promoted_in_bytes(),
              (size_t) policy->padded_average_promoted_in_bytes());

  if (promotion_estimate >= free_in_old_gen_with_expansion) {
    log_debug(gc, ergo)("Run full-gc; predicted promotion size >= max free space in old-gen: %zu >= %zu",
      promotion_estimate, free_in_old_gen_with_expansion);
    return ShouldRunFullGC;
  }

  if (UseAdaptiveSizePolicy) {
    // Also checking OS has enough free memory to commit and expand old-gen.
    // Otherwise, the recorded gc-pause-time might be inflated to include time
    // of OS preparing free memory, resulting in inaccurate young-gen resizing.
    assert(_old_gen->committed().byte_size() >= _old_gen->used_in_bytes(), "inv");
    // Use uint64_t instead of size_t for 32bit compatibility.
    uint64_t free_mem_in_os;
    if (os::free_memory(free_mem_in_os)) {
      size_t actual_free = (size_t)MIN2(_old_gen->committed().byte_size() - _old_gen->used_in_bytes() + free_mem_in_os,
                                        (uint64_t)SIZE_MAX);
      if (promotion_estimate > actual_free) {
        log_debug(gc, ergo)("Run full-gc; predicted promotion size > free space in old-gen and OS: %zu > %zu",
          promotion_estimate, actual_free);
        return ShouldRunFullGC;
      }
    }
  }

  // No particular reasons to run full-gc, so young-gc.
  return ShouldRunYoungGC;
}

static bool check_gc_heap_free_limit(size_t free_bytes, size_t capacity_bytes) {
  return (free_bytes * 100 / capacity_bytes) < GCHeapFreeLimit;
}

bool ParallelScavengeHeap::check_gc_overhead_limit() {
  assert(SafepointSynchronize::is_at_safepoint(), "precondition");

  if (UseGCOverheadLimit) {
    // The goal here is to return null prematurely so that apps can exit
    // gracefully when GC takes the most time.
    bool little_mutator_time = _size_policy->mutator_time_percent() * 100 < (100 - GCTimeLimit);
    bool little_free_space = check_gc_heap_free_limit(_young_gen->free_in_bytes(), _young_gen->capacity_in_bytes())
                          && check_gc_heap_free_limit(  _old_gen->free_in_bytes(),   _old_gen->capacity_in_bytes());

    log_debug(gc)("GC Overhead Limit: GC Time %f Free Space Young %f Old %f Counter %zu",
                  (100 - _size_policy->mutator_time_percent()),
                  percent_of(_young_gen->free_in_bytes(), _young_gen->capacity_in_bytes()),
                  percent_of(_old_gen->free_in_bytes(), _old_gen->capacity_in_bytes()),
                  _gc_overhead_counter);

    if (little_mutator_time && little_free_space) {
      _gc_overhead_counter++;
      if (_gc_overhead_counter >= GCOverheadLimitThreshold) {
        return true;
      }
    } else {
      _gc_overhead_counter = 0;
    }
  }
  return false;
}

HeapWord* ParallelScavengeHeap::expand_heap_and_allocate(size_t size, bool is_tlab) {
#ifdef ASSERT
  assert(Heap_lock->is_locked(), "precondition");
  if (is_init_completed()) {
    assert(SafepointSynchronize::is_at_safepoint(), "precondition");
    assert(Thread::current()->is_VM_thread(), "precondition");
  } else {
    assert(Thread::current()->is_Java_thread(), "precondition");
    assert(Heap_lock->owned_by_self(), "precondition");
  }
#endif

  HeapWord* result = young_gen()->expand_and_allocate(size);

  if (result == nullptr && !is_tlab) {
    result = old_gen()->expand_and_allocate(size);
  }

  return result;   // Could be null if we are out of space.
}

HeapWord* ParallelScavengeHeap::satisfy_failed_allocation(size_t size, bool is_tlab) {
  assert(size != 0, "precondition");

  HeapWord* result = nullptr;

  if (!_is_heap_almost_full) {
    // If young-gen can handle this allocation, attempt young-gc firstly, as young-gc is usually cheaper.
    bool should_run_young_gc = is_tlab || should_alloc_in_eden(size);

    collect_at_safepoint(!should_run_young_gc);

    // If gc-overhead is reached, we will skip allocation.
    if (!check_gc_overhead_limit()) {
      result = expand_heap_and_allocate(size, is_tlab);
      if (result != nullptr) {
        return result;
      }
    }
  }

  // Last resort GC; clear soft refs and do max-compaction before throwing OOM.
  {
    const bool clear_all_soft_refs = true;
    const bool should_do_max_compaction = true;
    PSParallelCompact::invoke(clear_all_soft_refs, should_do_max_compaction);
  }

  if (check_gc_overhead_limit()) {
    log_info(gc)("GC Overhead Limit exceeded too often (%zu).", GCOverheadLimitThreshold);
    return nullptr;
  }

  result = expand_heap_and_allocate(size, is_tlab);

  return result;
}

void ParallelScavengeHeap::ensure_parsability(bool retire_tlabs) {
  CollectedHeap::ensure_parsability(retire_tlabs);
  young_gen()->eden_space()->ensure_parsability();
}

size_t ParallelScavengeHeap::tlab_capacity() const {
  return young_gen()->eden_space()->tlab_capacity();
}

size_t ParallelScavengeHeap::tlab_used() const {
  return young_gen()->eden_space()->tlab_used();
}

size_t ParallelScavengeHeap::unsafe_max_tlab_alloc() const {
  return young_gen()->eden_space()->unsafe_max_tlab_alloc();
}

HeapWord* ParallelScavengeHeap::allocate_new_tlab(size_t min_size, size_t requested_size, size_t* actual_size) {
  HeapWord* result = mem_allocate_work(requested_size /* size */,
                                       true /* is_tlab */);
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

  VM_ParallelGCCollect op(gc_count, full_gc_count, cause);
  VMThread::execute(&op);
}

void ParallelScavengeHeap::collect_at_safepoint(bool is_full) {
  assert(!GCLocker::is_active(), "precondition");
  bool clear_soft_refs = GCCause::should_clear_all_soft_refs(_gc_cause);

  if (!is_full && should_attempt_young_gc()) {
    bool young_gc_success = PSScavenge::invoke(clear_soft_refs);
    if (young_gc_success) {
      return;
    }
    log_debug(gc, heap)("Upgrade to Full-GC since Young-gc failed.");
  }

  const bool should_do_max_compaction = false;
  PSParallelCompact::invoke(clear_soft_refs, should_do_max_compaction);
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
  Atomic<size_t> _claimed_index;

public:
  static const size_t InvalidIndex = SIZE_MAX;
  static const size_t EdenIndex = 0;
  static const size_t SurvivorIndex = 1;
  static const size_t NumNonOldGenClaims = 2;

  HeapBlockClaimer() : _claimed_index(EdenIndex) { }
  // Claim the block and get the block index.
  size_t claim_and_get_block() {
    size_t block_index;
    block_index = _claimed_index.fetch_then_add(1u);

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

void ParallelScavengeHeap::print_heap_on(outputStream* st) const {
  if (young_gen() != nullptr) {
    young_gen()->print_on(st);
  }
  if (old_gen() != nullptr) {
    old_gen()->print_on(st);
  }
}

void ParallelScavengeHeap::print_gc_on(outputStream* st) const {
  BarrierSet* bs = BarrierSet::barrier_set();
  if (bs != nullptr) {
    bs->print_on(st);
  }
  st->cr();

  PSParallelCompact::print_on(st);
}

void ParallelScavengeHeap::gc_threads_do(ThreadClosure* tc) const {
  ParallelScavengeHeap::heap()->workers().threads_do(tc);
}

void ParallelScavengeHeap::print_tracing_info() const {
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
  log_debug(gc, verify)("Tenured");
  old_gen()->verify();

  log_debug(gc, verify)("Eden");
  young_gen()->verify();

  log_debug(gc, verify)("CardTable");
  card_table()->verify_all_young_refs_imprecise();
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

static size_t calculate_free_from_free_ratio_flag(size_t live, uintx free_percent) {
  assert(free_percent != 100, "precondition");
  // We want to calculate how much free memory there can be based on the
  // live size.
  //   percent * (free + live) = free
  // =>
  //   free = (live * percent) / (1 - percent)

  const double percent = free_percent / 100.0;
  return live * percent / (1.0 - percent);
}

size_t ParallelScavengeHeap::calculate_desired_old_gen_capacity(size_t old_gen_live_size) {
  // If min free percent is 100%, the old-gen should always be in its max capacity
  if (MinHeapFreeRatio == 100) {
    return _old_gen->max_gen_size();
  }

  // Using recorded data to calculate the new capacity of old-gen to avoid
  // excessive expansion but also keep footprint low

  size_t promoted_estimate = _size_policy->padded_average_promoted_in_bytes();
  // Should have at least this free room for the next young-gc promotion.
  size_t free_size = promoted_estimate;

  size_t largest_live_size = MAX2((size_t)_size_policy->peak_old_gen_used_estimate(), old_gen_live_size);
  free_size += largest_live_size - old_gen_live_size;

  // Respect free percent
  if (MinHeapFreeRatio != 0) {
    size_t min_free = calculate_free_from_free_ratio_flag(old_gen_live_size, MinHeapFreeRatio);
    free_size = MAX2(free_size, min_free);
  }

  if (MaxHeapFreeRatio != 100) {
    size_t max_free = calculate_free_from_free_ratio_flag(old_gen_live_size, MaxHeapFreeRatio);
    free_size = MIN2(max_free, free_size);
  }

  return old_gen_live_size + free_size;
}

void ParallelScavengeHeap::resize_old_gen_after_full_gc() {
  size_t current_capacity = _old_gen->capacity_in_bytes();
  size_t desired_capacity = calculate_desired_old_gen_capacity(old_gen()->used_in_bytes());

  // If MinHeapFreeRatio is at its default value; shrink cautiously. Otherwise, users expect prompt shrinking.
  if (FLAG_IS_DEFAULT(MinHeapFreeRatio)) {
    if (desired_capacity < current_capacity) {
      // Shrinking
      if (total_full_collections() < AdaptiveSizePolicyReadyThreshold) {
        // No enough data for shrinking
        return;
      }
    }
  }

  _old_gen->resize(desired_capacity);
}

void ParallelScavengeHeap::resize_after_young_gc(bool is_survivor_overflowing) {
  _young_gen->resize_after_young_gc(is_survivor_overflowing);

  // Consider if should shrink old-gen
  if (!is_survivor_overflowing) {
    assert(old_gen()->capacity_in_bytes() >= old_gen()->min_gen_size(), "inv");

    // Old gen min_gen_size constraint.
    const size_t max_shrink_bytes_gen_size_constraint = old_gen()->capacity_in_bytes() - old_gen()->min_gen_size();

    // Per-step delta to avoid too aggressive shrinking.
    const size_t max_shrink_bytes_per_step_constraint = SpaceAlignment;

    // Combining the above two constraints.
    const size_t max_shrink_bytes = MIN2(max_shrink_bytes_gen_size_constraint,
                                         max_shrink_bytes_per_step_constraint);

    size_t shrink_bytes = _size_policy->compute_old_gen_shrink_bytes(old_gen()->free_in_bytes(), max_shrink_bytes);

    assert(old_gen()->capacity_in_bytes() >= shrink_bytes, "inv");
    assert(old_gen()->capacity_in_bytes() - shrink_bytes >= old_gen()->min_gen_size(), "inv");

    if (shrink_bytes != 0) {
      if (MinHeapFreeRatio != 0) {
        size_t new_capacity = old_gen()->capacity_in_bytes() - shrink_bytes;
        size_t new_free_size = old_gen()->free_in_bytes() - shrink_bytes;
        if ((double)new_free_size / new_capacity * 100 < MinHeapFreeRatio) {
          // Would violate MinHeapFreeRatio
          return;
        }
      }
      old_gen()->shrink(shrink_bytes);
    }
  }
}

void ParallelScavengeHeap::resize_after_full_gc() {
  resize_old_gen_after_full_gc();
  // We don't resize young-gen after full-gc because:
  // 1. eden-size directly affects young-gc frequency (GCTimeRatio), and we
  // don't have enough info to determine its desired size.
  // 2. eden can contain live objs after a full-gc, which is unsafe for
  // resizing. We will perform expansion on allocation if needed, in
  // satisfy_failed_allocation().
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
  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  bs_nm->disarm(nm);
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
  GCLocker::enter(thread);
}

void ParallelScavengeHeap::unpin_object(JavaThread* thread, oop obj) {
  GCLocker::exit(thread);
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
