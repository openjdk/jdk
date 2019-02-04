/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/cms/cmsCardTable.hpp"
#include "gc/cms/cmsVMOperations.hpp"
#include "gc/cms/compactibleFreeListSpace.hpp"
#include "gc/cms/concurrentMarkSweepGeneration.hpp"
#include "gc/cms/concurrentMarkSweepThread.hpp"
#include "gc/cms/cmsHeap.hpp"
#include "gc/cms/parNewGeneration.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/genMemoryPools.hpp"
#include "gc/shared/genOopClosures.inline.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/workgroup.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/vmThread.hpp"
#include "services/memoryManager.hpp"
#include "utilities/stack.inline.hpp"

class CompactibleFreeListSpacePool : public CollectedMemoryPool {
private:
  CompactibleFreeListSpace* _space;
public:
  CompactibleFreeListSpacePool(CompactibleFreeListSpace* space,
                               const char* name,
                               size_t max_size,
                               bool support_usage_threshold) :
    CollectedMemoryPool(name, space->capacity(), max_size, support_usage_threshold),
    _space(space) {
  }

  MemoryUsage get_memory_usage() {
    size_t max_heap_size   = (available_for_allocation() ? max_size() : 0);
    size_t used      = used_in_bytes();
    size_t committed = _space->capacity();

    return MemoryUsage(initial_size(), used, committed, max_heap_size);
  }

  size_t used_in_bytes() {
    return _space->used();
  }
};

CMSHeap::CMSHeap(GenCollectorPolicy *policy) :
    GenCollectedHeap(policy,
                     Generation::ParNew,
                     Generation::ConcurrentMarkSweep,
                     "ParNew:CMS"),
    _workers(NULL),
    _eden_pool(NULL),
    _survivor_pool(NULL),
    _old_pool(NULL) {
}

jint CMSHeap::initialize() {
  jint status = GenCollectedHeap::initialize();
  if (status != JNI_OK) return status;

  _workers = new WorkGang("GC Thread", ParallelGCThreads,
                          /* are_GC_task_threads */true,
                          /* are_ConcurrentGC_threads */false);
  if (_workers == NULL) {
    return JNI_ENOMEM;
  }
  _workers->initialize_workers();

  // If we are running CMS, create the collector responsible
  // for collecting the CMS generations.
  if (!create_cms_collector()) {
    return JNI_ENOMEM;
  }

  return JNI_OK;
}

CardTableRS* CMSHeap::create_rem_set(const MemRegion& reserved_region) {
  return new CMSCardTable(reserved_region);
}

void CMSHeap::initialize_serviceability() {
  _young_manager = new GCMemoryManager("ParNew", "end of minor GC");
  _old_manager = new GCMemoryManager("ConcurrentMarkSweep", "end of major GC");

  ParNewGeneration* young = young_gen();
  _eden_pool = new ContiguousSpacePool(young->eden(),
                                       "Par Eden Space",
                                       young->max_eden_size(),
                                       false);

  _survivor_pool = new SurvivorContiguousSpacePool(young,
                                                   "Par Survivor Space",
                                                   young->max_survivor_size(),
                                                   false);

  ConcurrentMarkSweepGeneration* old = (ConcurrentMarkSweepGeneration*) old_gen();
  _old_pool = new CompactibleFreeListSpacePool(old->cmsSpace(),
                                               "CMS Old Gen",
                                               old->reserved().byte_size(),
                                               true);

  _young_manager->add_pool(_eden_pool);
  _young_manager->add_pool(_survivor_pool);
  young->set_gc_manager(_young_manager);

  _old_manager->add_pool(_eden_pool);
  _old_manager->add_pool(_survivor_pool);
  _old_manager->add_pool(_old_pool);
  old ->set_gc_manager(_old_manager);

}

CMSHeap* CMSHeap::heap() {
  CollectedHeap* heap = Universe::heap();
  assert(heap != NULL, "Uninitialized access to CMSHeap::heap()");
  assert(heap->kind() == CollectedHeap::CMS, "Invalid name");
  return static_cast<CMSHeap*>(heap);
}

void CMSHeap::gc_threads_do(ThreadClosure* tc) const {
  assert(workers() != NULL, "should have workers here");
  workers()->threads_do(tc);
  ConcurrentMarkSweepThread::threads_do(tc);
}

void CMSHeap::print_gc_threads_on(outputStream* st) const {
  assert(workers() != NULL, "should have workers here");
  workers()->print_worker_threads_on(st);
  ConcurrentMarkSweepThread::print_all_on(st);
}

void CMSHeap::print_on_error(outputStream* st) const {
  GenCollectedHeap::print_on_error(st);
  st->cr();
  CMSCollector::print_on_error(st);
}

bool CMSHeap::create_cms_collector() {
  assert(old_gen()->kind() == Generation::ConcurrentMarkSweep,
         "Unexpected generation kinds");
  CMSCollector* collector =
    new CMSCollector((ConcurrentMarkSweepGeneration*) old_gen(),
                     rem_set(),
                     (ConcurrentMarkSweepPolicy*) gen_policy());

  if (collector == NULL || !collector->completed_initialization()) {
    if (collector) {
      delete collector; // Be nice in embedded situation
    }
    vm_shutdown_during_initialization("Could not create CMS collector");
    return false;
  }
  return true; // success
}

void CMSHeap::collect(GCCause::Cause cause) {
  if (should_do_concurrent_full_gc(cause)) {
    // Mostly concurrent full collection.
    collect_mostly_concurrent(cause);
  } else {
    GenCollectedHeap::collect(cause);
  }
}

bool CMSHeap::should_do_concurrent_full_gc(GCCause::Cause cause) {
  switch (cause) {
    case GCCause::_gc_locker:           return GCLockerInvokesConcurrent;
    case GCCause::_java_lang_system_gc:
    case GCCause::_dcmd_gc_run:         return ExplicitGCInvokesConcurrent;
    default:                            return false;
  }
}

void CMSHeap::collect_mostly_concurrent(GCCause::Cause cause) {
  assert(!Heap_lock->owned_by_self(), "Should not own Heap_lock");

  MutexLocker ml(Heap_lock);
  // Read the GC counts while holding the Heap_lock
  unsigned int full_gc_count_before = total_full_collections();
  unsigned int gc_count_before      = total_collections();
  {
    MutexUnlocker mu(Heap_lock);
    VM_GenCollectFullConcurrent op(gc_count_before, full_gc_count_before, cause);
    VMThread::execute(&op);
  }
}

void CMSHeap::stop() {
  ConcurrentMarkSweepThread::cmst()->stop();
}

void CMSHeap::safepoint_synchronize_begin() {
  ConcurrentMarkSweepThread::synchronize(false);
}

void CMSHeap::safepoint_synchronize_end() {
  ConcurrentMarkSweepThread::desynchronize(false);
}

void CMSHeap::cms_process_roots(StrongRootsScope* scope,
                                bool young_gen_as_roots,
                                ScanningOption so,
                                bool only_strong_roots,
                                OopsInGenClosure* root_closure,
                                CLDClosure* cld_closure) {
  MarkingCodeBlobClosure mark_code_closure(root_closure, !CodeBlobToOopClosure::FixRelocations);
  CLDClosure* weak_cld_closure = only_strong_roots ? NULL : cld_closure;

  process_roots(scope, so, root_closure, cld_closure, weak_cld_closure, &mark_code_closure);

  if (young_gen_as_roots &&
      _process_strong_tasks->try_claim_task(GCH_PS_younger_gens)) {
    root_closure->set_generation(young_gen());
    young_gen()->oop_iterate(root_closure);
    root_closure->reset_generation();
  }

  _process_strong_tasks->all_tasks_completed(scope->n_threads());
}

void CMSHeap::gc_prologue(bool full) {
  always_do_update_barrier = false;
  GenCollectedHeap::gc_prologue(full);
};

void CMSHeap::gc_epilogue(bool full) {
  GenCollectedHeap::gc_epilogue(full);
  always_do_update_barrier = true;
};

GrowableArray<GCMemoryManager*> CMSHeap::memory_managers() {
  GrowableArray<GCMemoryManager*> memory_managers(2);
  memory_managers.append(_young_manager);
  memory_managers.append(_old_manager);
  return memory_managers;
}

GrowableArray<MemoryPool*> CMSHeap::memory_pools() {
  GrowableArray<MemoryPool*> memory_pools(3);
  memory_pools.append(_eden_pool);
  memory_pools.append(_survivor_pool);
  memory_pools.append(_old_pool);
  return memory_pools;
}
