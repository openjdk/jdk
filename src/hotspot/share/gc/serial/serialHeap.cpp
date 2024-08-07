/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "compiler/oopMap.hpp"
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/serialFullGC.hpp"
#include "gc/serial/serialHeap.inline.hpp"
#include "gc/serial/serialMemoryPools.hpp"
#include "gc/serial/serialVMOperations.hpp"
#include "gc/serial/tenuredGeneration.inline.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/collectorCounters.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcInitLogger.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "gc/shared/gcPolicyCounters.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/genArguments.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/shared/locationPrinter.inline.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "gc/shared/oopStorageParState.inline.hpp"
#include "gc/shared/oopStorageSet.inline.hpp"
#include "gc/shared/scavengableNMethods.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "gc/shared/workerThread.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceCounters.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/threads.hpp"
#include "runtime/vmThread.hpp"
#include "services/memoryManager.hpp"
#include "services/memoryService.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"
#include "utilities/stack.inline.hpp"
#include "utilities/vmError.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#endif

SerialHeap* SerialHeap::heap() {
  return named_heap<SerialHeap>(CollectedHeap::Serial);
}

SerialHeap::SerialHeap() :
    CollectedHeap(),
    _young_gen(nullptr),
    _old_gen(nullptr),
    _rem_set(nullptr),
    _gc_policy_counters(new GCPolicyCounters("Copy:MSC", 2, 2)),
    _young_manager(nullptr),
    _old_manager(nullptr),
    _eden_pool(nullptr),
    _survivor_pool(nullptr),
    _old_pool(nullptr) {
  _young_manager = new GCMemoryManager("Copy");
  _old_manager = new GCMemoryManager("MarkSweepCompact");
}

void SerialHeap::initialize_serviceability() {
  DefNewGeneration* young = young_gen();

  // Add a memory pool for each space and young gen doesn't
  // support low memory detection as it is expected to get filled up.
  _eden_pool = new ContiguousSpacePool(young->eden(),
                                       "Eden Space",
                                       young->max_eden_size(),
                                       false /* support_usage_threshold */);
  _survivor_pool = new SurvivorContiguousSpacePool(young,
                                                   "Survivor Space",
                                                   young->max_survivor_size(),
                                                   false /* support_usage_threshold */);
  TenuredGeneration* old = old_gen();
  _old_pool = new TenuredGenerationPool(old, "Tenured Gen", true);

  _young_manager->add_pool(_eden_pool);
  _young_manager->add_pool(_survivor_pool);
  young->set_gc_manager(_young_manager);

  _old_manager->add_pool(_eden_pool);
  _old_manager->add_pool(_survivor_pool);
  _old_manager->add_pool(_old_pool);
  old->set_gc_manager(_old_manager);
}

GrowableArray<GCMemoryManager*> SerialHeap::memory_managers() {
  GrowableArray<GCMemoryManager*> memory_managers(2);
  memory_managers.append(_young_manager);
  memory_managers.append(_old_manager);
  return memory_managers;
}

GrowableArray<MemoryPool*> SerialHeap::memory_pools() {
  GrowableArray<MemoryPool*> memory_pools(3);
  memory_pools.append(_eden_pool);
  memory_pools.append(_survivor_pool);
  memory_pools.append(_old_pool);
  return memory_pools;
}

void SerialHeap::safepoint_synchronize_begin() {
  if (UseStringDeduplication) {
    SuspendibleThreadSet::synchronize();
  }
}

void SerialHeap::safepoint_synchronize_end() {
  if (UseStringDeduplication) {
    SuspendibleThreadSet::desynchronize();
  }
}

HeapWord* SerialHeap::allocate_loaded_archive_space(size_t word_size) {
  MutexLocker ml(Heap_lock);
  return old_gen()->allocate(word_size);
}

void SerialHeap::complete_loaded_archive_space(MemRegion archive_space) {
  assert(old_gen()->used_region().contains(archive_space), "Archive space not contained in old gen");
  old_gen()->complete_loaded_archive_space(archive_space);
}

void SerialHeap::pin_object(JavaThread* thread, oop obj) {
  GCLocker::lock_critical(thread);
}

void SerialHeap::unpin_object(JavaThread* thread, oop obj) {
  GCLocker::unlock_critical(thread);
}

jint SerialHeap::initialize() {
  // Allocate space for the heap.

  ReservedHeapSpace heap_rs = allocate(HeapAlignment);

  if (!heap_rs.is_reserved()) {
    vm_shutdown_during_initialization(
      "Could not reserve enough space for object heap");
    return JNI_ENOMEM;
  }

  initialize_reserved_region(heap_rs);

  ReservedSpace young_rs = heap_rs.first_part(MaxNewSize);
  ReservedSpace old_rs = heap_rs.last_part(MaxNewSize);

  _rem_set = new CardTableRS(heap_rs.region());
  _rem_set->initialize(young_rs.base(), old_rs.base());

  CardTableBarrierSet *bs = new CardTableBarrierSet(_rem_set);
  bs->initialize();
  BarrierSet::set_barrier_set(bs);

  _young_gen = new DefNewGeneration(young_rs, NewSize, MinNewSize, MaxNewSize);
  _old_gen = new TenuredGeneration(old_rs, OldSize, MinOldSize, MaxOldSize, rem_set());

  GCInitLogger::print();

  return JNI_OK;
}

ReservedHeapSpace SerialHeap::allocate(size_t alignment) {
  // Now figure out the total size.
  const size_t pageSize = UseLargePages ? os::large_page_size() : os::vm_page_size();
  assert(alignment % pageSize == 0, "Must be");

  // Check for overflow.
  size_t total_reserved = MaxNewSize + MaxOldSize;
  if (total_reserved < MaxNewSize) {
    vm_exit_during_initialization("The size of the object heap + VM data exceeds "
                                  "the maximum representable size");
  }
  assert(total_reserved % alignment == 0,
         "Gen size; total_reserved=" SIZE_FORMAT ", alignment="
         SIZE_FORMAT, total_reserved, alignment);

  ReservedHeapSpace heap_rs = Universe::reserve_heap(total_reserved, alignment);
  size_t used_page_size = heap_rs.page_size();

  os::trace_page_sizes("Heap",
                       MinHeapSize,
                       total_reserved,
                       heap_rs.base(),
                       heap_rs.size(),
                       used_page_size);

  return heap_rs;
}

class GenIsScavengable : public BoolObjectClosure {
public:
  bool do_object_b(oop obj) {
    return SerialHeap::heap()->is_in_young(obj);
  }
};

static GenIsScavengable _is_scavengable;

void SerialHeap::post_initialize() {
  CollectedHeap::post_initialize();

  DefNewGeneration* def_new_gen = (DefNewGeneration*)_young_gen;

  def_new_gen->ref_processor_init();

  SerialFullGC::initialize();

  ScavengableNMethods::initialize(&_is_scavengable);
}

PreGenGCValues SerialHeap::get_pre_gc_values() const {
  const DefNewGeneration* const def_new_gen = (DefNewGeneration*) young_gen();

  return PreGenGCValues(def_new_gen->used(),
                        def_new_gen->capacity(),
                        def_new_gen->eden()->used(),
                        def_new_gen->eden()->capacity(),
                        def_new_gen->from()->used(),
                        def_new_gen->from()->capacity(),
                        old_gen()->used(),
                        old_gen()->capacity());
}

size_t SerialHeap::capacity() const {
  return _young_gen->capacity() + _old_gen->capacity();
}

size_t SerialHeap::used() const {
  return _young_gen->used() + _old_gen->used();
}

size_t SerialHeap::max_capacity() const {
  return _young_gen->max_capacity() + _old_gen->max_capacity();
}

// Return true if any of the following is true:
// . the allocation won't fit into the current young gen heap
// . gc locker is occupied (jni critical section)
// . heap memory is tight -- the most recent previous collection
//   was a full collection because a partial collection (would
//   have) failed and is likely to fail again
bool SerialHeap::should_try_older_generation_allocation(size_t word_size) const {
  size_t young_capacity = _young_gen->capacity_before_gc();
  return    (word_size > heap_word_size(young_capacity))
         || GCLocker::is_active_and_needs_gc();
}

HeapWord* SerialHeap::expand_heap_and_allocate(size_t size, bool is_tlab) {
  HeapWord* result = nullptr;
  if (_old_gen->should_allocate(size, is_tlab)) {
    result = _old_gen->expand_and_allocate(size);
  }
  if (result == nullptr) {
    if (_young_gen->should_allocate(size, is_tlab)) {
      // Young-gen is not expanded.
      result = _young_gen->allocate(size);
    }
  }
  assert(result == nullptr || is_in_reserved(result), "result not in heap");
  return result;
}

HeapWord* SerialHeap::mem_allocate_work(size_t size,
                                        bool is_tlab) {

  HeapWord* result = nullptr;

  // Loop until the allocation is satisfied, or unsatisfied after GC.
  for (uint try_count = 1, gclocker_stalled_count = 0; /* return or throw */; try_count += 1) {

    // First allocation attempt is lock-free.
    DefNewGeneration *young = _young_gen;
    if (young->should_allocate(size, is_tlab)) {
      result = young->par_allocate(size);
      if (result != nullptr) {
        assert(is_in_reserved(result), "result not in heap");
        return result;
      }
    }
    uint gc_count_before;  // Read inside the Heap_lock locked region.
    {
      MutexLocker ml(Heap_lock);
      log_trace(gc, alloc)("SerialHeap::mem_allocate_work: attempting locked slow path allocation");
      // Note that only large objects get a shot at being
      // allocated in later generations.
      bool first_only = !should_try_older_generation_allocation(size);

      result = attempt_allocation(size, is_tlab, first_only);
      if (result != nullptr) {
        assert(is_in_reserved(result), "result not in heap");
        return result;
      }

      if (GCLocker::is_active_and_needs_gc()) {
        if (is_tlab) {
          return nullptr;  // Caller will retry allocating individual object.
        }
        if (!is_maximal_no_gc()) {
          // Try and expand heap to satisfy request.
          result = expand_heap_and_allocate(size, is_tlab);
          // Result could be null if we are out of space.
          if (result != nullptr) {
            return result;
          }
        }

        if (gclocker_stalled_count > GCLockerRetryAllocationCount) {
          return nullptr; // We didn't get to do a GC and we didn't get any memory.
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
          return nullptr;
        }
      }

      // Read the gc count while the heap lock is held.
      gc_count_before = total_collections();
    }

    VM_SerialCollectForAllocation op(size, is_tlab, gc_count_before);
    VMThread::execute(&op);
    if (op.prologue_succeeded()) {
      result = op.result();
      if (op.gc_locked()) {
         assert(result == nullptr, "must be null if gc_locked() is true");
         continue;  // Retry and/or stall as necessary.
      }

      assert(result == nullptr || is_in_reserved(result),
             "result not in heap");
      return result;
    }

    // Give a warning if we seem to be looping forever.
    if ((QueuedAllocationWarningCount > 0) &&
        (try_count % QueuedAllocationWarningCount == 0)) {
          log_warning(gc, ergo)("SerialHeap::mem_allocate_work retries %d times,"
                                " size=" SIZE_FORMAT " %s", try_count, size, is_tlab ? "(TLAB)" : "");
    }
  }
}

HeapWord* SerialHeap::attempt_allocation(size_t size,
                                         bool is_tlab,
                                         bool first_only) {
  HeapWord* res = nullptr;

  if (_young_gen->should_allocate(size, is_tlab)) {
    res = _young_gen->allocate(size);
    if (res != nullptr || first_only) {
      return res;
    }
  }

  if (_old_gen->should_allocate(size, is_tlab)) {
    res = _old_gen->allocate(size);
  }

  return res;
}

HeapWord* SerialHeap::mem_allocate(size_t size,
                                   bool* gc_overhead_limit_was_exceeded) {
  return mem_allocate_work(size,
                           false /* is_tlab */);
}

bool SerialHeap::must_clear_all_soft_refs() {
  return _gc_cause == GCCause::_metadata_GC_clear_soft_refs ||
         _gc_cause == GCCause::_wb_full_gc;
}

bool SerialHeap::is_young_gc_safe() const {
  if (!_young_gen->to()->is_empty()) {
    return false;
  }
  return _old_gen->promotion_attempt_is_safe(_young_gen->used());
}

bool SerialHeap::do_young_collection(bool clear_soft_refs) {
  if (!is_young_gc_safe()) {
    return false;
  }
  IsSTWGCActiveMark gc_active_mark;
  SvcGCMarker sgcm(SvcGCMarker::MINOR);
  GCIdMark gc_id_mark;
  GCTraceCPUTime tcpu(_young_gen->gc_tracer());
  GCTraceTime(Info, gc) t("Pause Young", nullptr, gc_cause(), true);
  TraceCollectorStats tcs(_young_gen->counters());
  TraceMemoryManagerStats tmms(_young_gen->gc_manager(), gc_cause(), "end of minor GC");
  print_heap_before_gc();
  const PreGenGCValues pre_gc_values = get_pre_gc_values();

  increment_total_collections(false);
  const bool should_verify = total_collections() >= VerifyGCStartAt;
  if (should_verify && VerifyBeforeGC) {
    prepare_for_verify();
    Universe::verify("Before GC");
  }
  gc_prologue(false);
  COMPILER2_OR_JVMCI_PRESENT(DerivedPointerTable::clear());

  save_marks();

  bool result = _young_gen->collect(clear_soft_refs);

  COMPILER2_OR_JVMCI_PRESENT(DerivedPointerTable::update_pointers());

  // Only update stats for successful young-gc
  if (result) {
    _old_gen->update_promote_stats();
  }

  if (should_verify && VerifyAfterGC) {
    Universe::verify("After GC");
  }

  _young_gen->compute_new_size();

  print_heap_change(pre_gc_values);

  // Track memory usage and detect low memory after GC finishes
  MemoryService::track_memory_usage();

  gc_epilogue(false);

  print_heap_after_gc();

  return result;
}

void SerialHeap::register_nmethod(nmethod* nm) {
  ScavengableNMethods::register_nmethod(nm);
}

void SerialHeap::unregister_nmethod(nmethod* nm) {
  ScavengableNMethods::unregister_nmethod(nm);
}

void SerialHeap::verify_nmethod(nmethod* nm) {
  ScavengableNMethods::verify_nmethod(nm);
}

void SerialHeap::prune_scavengable_nmethods() {
  ScavengableNMethods::prune_nmethods_not_into_young();
}

void SerialHeap::prune_unlinked_nmethods() {
  ScavengableNMethods::prune_unlinked_nmethods();
}

HeapWord* SerialHeap::satisfy_failed_allocation(size_t size, bool is_tlab) {
  assert(size != 0, "precondition");

  HeapWord* result = nullptr;

  GCLocker::check_active_before_gc();
  if (GCLocker::is_active_and_needs_gc()) {
    // GC locker is active; instead of a collection we will attempt
    // to expand the heap, if there's room for expansion.
    if (!is_maximal_no_gc()) {
      result = expand_heap_and_allocate(size, is_tlab);
    }
    return result;   // Could be null if we are out of space.
  }

  // If young-gen can handle this allocation, attempt young-gc firstly.
  bool should_run_young_gc = _young_gen->should_allocate(size, is_tlab);
  collect_at_safepoint(!should_run_young_gc);

  result = attempt_allocation(size, is_tlab, false /*first_only*/);
  if (result != nullptr) {
    return result;
  }

  // OK, collection failed, try expansion.
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
    UIntFlagSetting flag_change(MarkSweepAlwaysCompactCount, 1); // Make sure the heap is fully compacted
    const bool clear_all_soft_refs = true;
    do_full_collection_no_gc_locker(clear_all_soft_refs);
  }

  result = attempt_allocation(size, is_tlab, false /* first_only */);
  if (result != nullptr) {
    return result;
  }
  // The previous full-gc can shrink the heap, so re-expand it.
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

void SerialHeap::process_roots(ScanningOption so,
                               OopClosure* strong_roots,
                               CLDClosure* strong_cld_closure,
                               CLDClosure* weak_cld_closure,
                               NMethodToOopClosure* code_roots) {
  // General roots.
  assert(code_roots != nullptr, "code root closure should always be set");

  ClassLoaderDataGraph::roots_cld_do(strong_cld_closure, weak_cld_closure);

  // Only process code roots from thread stacks if we aren't visiting the entire CodeCache anyway
  NMethodToOopClosure* roots_from_code_p = (so & SO_AllCodeCache) ? nullptr : code_roots;

  Threads::oops_do(strong_roots, roots_from_code_p);

  OopStorageSet::strong_oops_do(strong_roots);

  if (so & SO_ScavengeCodeCache) {
    assert(code_roots != nullptr, "must supply closure for code cache");

    // We only visit parts of the CodeCache when scavenging.
    ScavengableNMethods::nmethods_do(code_roots);
  }
  if (so & SO_AllCodeCache) {
    assert(code_roots != nullptr, "must supply closure for code cache");

    // CMSCollector uses this to do intermediate-strength collections.
    // We scan the entire code cache, since CodeCache::do_unloading is not called.
    CodeCache::nmethods_do(code_roots);
  }
}

template <typename OopClosureType>
static void oop_iterate_from(OopClosureType* blk, ContiguousSpace* space, HeapWord** from) {
  assert(*from != nullptr, "precondition");
  HeapWord* t;
  HeapWord* p = *from;

  const intx interval = PrefetchScanIntervalInBytes;
  do {
    t = space->top();
    while (p < t) {
      Prefetch::write(p, interval);
      p += cast_to_oop(p)->oop_iterate_size(blk);
    }
  } while (t < space->top());

  *from = space->top();
}

void SerialHeap::scan_evacuated_objs(YoungGenScanClosure* young_cl,
                                     OldGenScanClosure* old_cl) {
  ContiguousSpace* to_space = young_gen()->to();
  do {
    oop_iterate_from(young_cl, to_space, &_young_gen_saved_top);
    oop_iterate_from(old_cl, old_gen()->space(), &_old_gen_saved_top);
    // Recheck to-space only, because postcondition of oop_iterate_from is no
    // unscanned objs
  } while (_young_gen_saved_top != to_space->top());
  guarantee(young_gen()->promo_failure_scan_is_complete(), "Failed to finish scan");
}

void SerialHeap::try_collect_at_safepoint(bool full) {
  assert(SafepointSynchronize::is_at_safepoint(), "precondition");
  if (GCLocker::check_active_before_gc()) {
    return;
  }
  collect_at_safepoint(full);
}

void SerialHeap::collect_at_safepoint(bool full) {
  assert(!GCLocker::is_active(), "precondition");
  bool clear_soft_refs = must_clear_all_soft_refs();

  if (!full) {
    bool success = do_young_collection(clear_soft_refs);
    if (success) {
      return;
    }
    // Upgrade to Full-GC if young-gc fails
  }
  do_full_collection_no_gc_locker(clear_soft_refs);
}

// public collection interfaces
void SerialHeap::collect(GCCause::Cause cause) {
  // The caller doesn't have the Heap_lock
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");

  unsigned int gc_count_before;
  unsigned int full_gc_count_before;

  {
    MutexLocker ml(Heap_lock);
    // Read the GC count while holding the Heap_lock
    gc_count_before      = total_collections();
    full_gc_count_before = total_full_collections();
  }

  if (GCLocker::should_discard(cause, gc_count_before)) {
    return;
  }

  bool should_run_young_gc =  (cause == GCCause::_wb_young_gc)
                           || (cause == GCCause::_gc_locker)
                DEBUG_ONLY(|| (cause == GCCause::_scavenge_alot));

  while (true) {
    VM_SerialGCCollect op(!should_run_young_gc,
                          gc_count_before,
                          full_gc_count_before,
                          cause);
    VMThread::execute(&op);

    if (!GCCause::is_explicit_full_gc(cause)) {
      return;
    }

    {
      MutexLocker ml(Heap_lock);
      // Read the GC count while holding the Heap_lock
      if (full_gc_count_before != total_full_collections()) {
        return;
      }
    }

    if (GCLocker::is_active_and_needs_gc()) {
      // If GCLocker is active, wait until clear before retrying.
      GCLocker::stall_until_clear();
    }
  }
}

void SerialHeap::do_full_collection(bool clear_all_soft_refs) {
  if (GCLocker::check_active_before_gc()) {
    return;
  }
  do_full_collection_no_gc_locker(clear_all_soft_refs);
}

void SerialHeap::do_full_collection_no_gc_locker(bool clear_all_soft_refs) {
  IsSTWGCActiveMark gc_active_mark;
  SvcGCMarker sgcm(SvcGCMarker::FULL);
  GCIdMark gc_id_mark;
  GCTraceCPUTime tcpu(SerialFullGC::gc_tracer());
  GCTraceTime(Info, gc) t("Pause Full", nullptr, gc_cause(), true);
  TraceCollectorStats tcs(_old_gen->counters());
  TraceMemoryManagerStats tmms(_old_gen->gc_manager(), gc_cause(), "end of major GC");
  const PreGenGCValues pre_gc_values = get_pre_gc_values();
  print_heap_before_gc();

  increment_total_collections(true);
  const bool should_verify = total_collections() >= VerifyGCStartAt;
  if (should_verify && VerifyBeforeGC) {
    prepare_for_verify();
    Universe::verify("Before GC");
  }

  gc_prologue(true);
  COMPILER2_OR_JVMCI_PRESENT(DerivedPointerTable::clear());
  CodeCache::on_gc_marking_cycle_start();
  ClassUnloadingContext ctx(1 /* num_nmethod_unlink_workers */,
                            false /* unregister_nmethods_during_purge */,
                            false /* lock_nmethod_free_separately */);

  STWGCTimer* gc_timer = SerialFullGC::gc_timer();
  gc_timer->register_gc_start();

  SerialOldTracer* gc_tracer = SerialFullGC::gc_tracer();
  gc_tracer->report_gc_start(gc_cause(), gc_timer->gc_start());

  pre_full_gc_dump(gc_timer);

  SerialFullGC::invoke_at_safepoint(clear_all_soft_refs);

  post_full_gc_dump(gc_timer);

  gc_timer->register_gc_end();

  gc_tracer->report_gc_end(gc_timer->gc_end(), gc_timer->time_partitions());
  CodeCache::on_gc_marking_cycle_finish();
  CodeCache::arm_all_nmethods();
  COMPILER2_OR_JVMCI_PRESENT(DerivedPointerTable::update_pointers());

  // Adjust generation sizes.
  _old_gen->compute_new_size();
  _young_gen->compute_new_size();

  // Delete metaspaces for unloaded class loaders and clean up loader_data graph
  ClassLoaderDataGraph::purge(/*at_safepoint*/true);
  DEBUG_ONLY(MetaspaceUtils::verify();)

  // Need to clear claim bits for the next mark.
  ClassLoaderDataGraph::clear_claimed_marks();

  _old_gen->update_promote_stats();

  // Resize the metaspace capacity after full collections
  MetaspaceGC::compute_new_size();

  print_heap_change(pre_gc_values);

  // Track memory usage and detect low memory after GC finishes
  MemoryService::track_memory_usage();

  // Need to tell the epilogue code we are done with Full GC, regardless what was
  // the initial value for "complete" flag.
  gc_epilogue(true);

  print_heap_after_gc();

  if (should_verify && VerifyAfterGC) {
    Universe::verify("After GC");
  }
}

bool SerialHeap::is_in_young(const void* p) const {
  bool result = p < _old_gen->reserved().start();
  assert(result == _young_gen->is_in_reserved(p),
         "incorrect test - result=%d, p=" PTR_FORMAT, result, p2i(p));
  return result;
}

bool SerialHeap::requires_barriers(stackChunkOop obj) const {
  return !is_in_young(obj);
}

// Returns "TRUE" iff "p" points into the committed areas of the heap.
bool SerialHeap::is_in(const void* p) const {
  return _young_gen->is_in(p) || _old_gen->is_in(p);
}

void SerialHeap::object_iterate(ObjectClosure* cl) {
  _young_gen->object_iterate(cl);
  _old_gen->object_iterate(cl);
}

HeapWord* SerialHeap::block_start(const void* addr) const {
  assert(is_in_reserved(addr), "block_start of address outside of heap");
  if (_young_gen->is_in_reserved(addr)) {
    assert(_young_gen->is_in(addr), "addr should be in allocated part of generation");
    return _young_gen->block_start(addr);
  }

  assert(_old_gen->is_in_reserved(addr), "Some generation should contain the address");
  assert(_old_gen->is_in(addr), "addr should be in allocated part of generation");
  return _old_gen->block_start(addr);
}

bool SerialHeap::block_is_obj(const HeapWord* addr) const {
  assert(is_in_reserved(addr), "block_is_obj of address outside of heap");
  assert(block_start(addr) == addr, "addr must be a block start");

  if (_young_gen->is_in_reserved(addr)) {
    return _young_gen->eden()->is_in(addr)
        || _young_gen->from()->is_in(addr)
        || _young_gen->to()  ->is_in(addr);
  }

  assert(_old_gen->is_in_reserved(addr), "must be in old-gen");
  return addr < _old_gen->space()->top();
}

size_t SerialHeap::tlab_capacity(Thread* thr) const {
  // Only young-gen supports tlab allocation.
  return _young_gen->tlab_capacity();
}

size_t SerialHeap::tlab_used(Thread* thr) const {
  return _young_gen->tlab_used();
}

size_t SerialHeap::unsafe_max_tlab_alloc(Thread* thr) const {
  return _young_gen->unsafe_max_tlab_alloc();
}

HeapWord* SerialHeap::allocate_new_tlab(size_t min_size,
                                        size_t requested_size,
                                        size_t* actual_size) {
  HeapWord* result = mem_allocate_work(requested_size /* size */,
                                       true /* is_tlab */);
  if (result != nullptr) {
    *actual_size = requested_size;
  }

  return result;
}

void SerialHeap::prepare_for_verify() {
  ensure_parsability(false);        // no need to retire TLABs
}

bool SerialHeap::is_maximal_no_gc() const {
  // We don't expand young-gen except at a GC.
  return _old_gen->is_maximal_no_gc();
}

void SerialHeap::save_marks() {
  _young_gen_saved_top = _young_gen->to()->top();
  _old_gen_saved_top = _old_gen->space()->top();
}

void SerialHeap::verify(VerifyOption option /* ignored */) {
  log_debug(gc, verify)("%s", _old_gen->name());
  _old_gen->verify();

  log_debug(gc, verify)("%s", _young_gen->name());
  _young_gen->verify();

  log_debug(gc, verify)("RemSet");
  rem_set()->verify();
}

void SerialHeap::print_on(outputStream* st) const {
  if (_young_gen != nullptr) {
    _young_gen->print_on(st);
  }
  if (_old_gen != nullptr) {
    _old_gen->print_on(st);
  }
  MetaspaceUtils::print_on(st);
}

void SerialHeap::gc_threads_do(ThreadClosure* tc) const {
}

bool SerialHeap::print_location(outputStream* st, void* addr) const {
  return BlockLocationPrinter<SerialHeap>::print_location(st, addr);
}

void SerialHeap::print_tracing_info() const {
 // Does nothing
}

void SerialHeap::print_heap_change(const PreGenGCValues& pre_gc_values) const {
  const DefNewGeneration* const def_new_gen = (DefNewGeneration*) young_gen();

  log_info(gc, heap)(HEAP_CHANGE_FORMAT" "
                     HEAP_CHANGE_FORMAT" "
                     HEAP_CHANGE_FORMAT,
                     HEAP_CHANGE_FORMAT_ARGS(def_new_gen->short_name(),
                                             pre_gc_values.young_gen_used(),
                                             pre_gc_values.young_gen_capacity(),
                                             def_new_gen->used(),
                                             def_new_gen->capacity()),
                     HEAP_CHANGE_FORMAT_ARGS("Eden",
                                             pre_gc_values.eden_used(),
                                             pre_gc_values.eden_capacity(),
                                             def_new_gen->eden()->used(),
                                             def_new_gen->eden()->capacity()),
                     HEAP_CHANGE_FORMAT_ARGS("From",
                                             pre_gc_values.from_used(),
                                             pre_gc_values.from_capacity(),
                                             def_new_gen->from()->used(),
                                             def_new_gen->from()->capacity()));
  log_info(gc, heap)(HEAP_CHANGE_FORMAT,
                     HEAP_CHANGE_FORMAT_ARGS(old_gen()->short_name(),
                                             pre_gc_values.old_gen_used(),
                                             pre_gc_values.old_gen_capacity(),
                                             old_gen()->used(),
                                             old_gen()->capacity()));
  MetaspaceUtils::print_metaspace_change(pre_gc_values.metaspace_sizes());
}

void SerialHeap::gc_prologue(bool full) {
  // Fill TLAB's and such
  ensure_parsability(true);   // retire TLABs

  _old_gen->gc_prologue();
};

void SerialHeap::gc_epilogue(bool full) {
#if COMPILER2_OR_JVMCI
  assert(DerivedPointerTable::is_empty(), "derived pointer present");
#endif // COMPILER2_OR_JVMCI

  resize_all_tlabs();

  _young_gen->gc_epilogue(full);
  _old_gen->gc_epilogue();

  MetaspaceCounters::update_performance_counters();
};
