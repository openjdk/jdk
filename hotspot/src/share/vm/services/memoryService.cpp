/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/parallel/mutableSpace.hpp"
#include "gc/serial/defNewGeneration.hpp"
#include "gc/serial/tenuredGeneration.hpp"
#include "gc/shared/collectorPolicy.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/generation.hpp"
#include "gc/shared/generationSpec.hpp"
#include "memory/heap.hpp"
#include "memory/memRegion.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/javaCalls.hpp"
#include "services/classLoadingService.hpp"
#include "services/lowMemoryDetector.hpp"
#include "services/management.hpp"
#include "services/memoryManager.hpp"
#include "services/memoryPool.hpp"
#include "services/memoryService.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc/cms/concurrentMarkSweepGeneration.hpp"
#include "gc/cms/parNewGeneration.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/parallel/psYoungGen.hpp"
#include "services/g1MemoryPool.hpp"
#include "services/psMemoryPool.hpp"
#endif // INCLUDE_ALL_GCS

GrowableArray<MemoryPool*>* MemoryService::_pools_list =
  new (ResourceObj::C_HEAP, mtInternal) GrowableArray<MemoryPool*>(init_pools_list_size, true);
GrowableArray<MemoryManager*>* MemoryService::_managers_list =
  new (ResourceObj::C_HEAP, mtInternal) GrowableArray<MemoryManager*>(init_managers_list_size, true);

GCMemoryManager* MemoryService::_minor_gc_manager      = NULL;
GCMemoryManager* MemoryService::_major_gc_manager      = NULL;
MemoryManager*   MemoryService::_code_cache_manager    = NULL;
GrowableArray<MemoryPool*>* MemoryService::_code_heap_pools =
    new (ResourceObj::C_HEAP, mtInternal) GrowableArray<MemoryPool*>(init_code_heap_pools_size, true);
MemoryPool*      MemoryService::_metaspace_pool        = NULL;
MemoryPool*      MemoryService::_compressed_class_pool = NULL;

class GcThreadCountClosure: public ThreadClosure {
 private:
  int _count;
 public:
  GcThreadCountClosure() : _count(0) {};
  void do_thread(Thread* thread);
  int count() { return _count; }
};

void GcThreadCountClosure::do_thread(Thread* thread) {
  _count++;
}

void MemoryService::set_universe_heap(CollectedHeap* heap) {
  CollectedHeap::Name kind = heap->kind();
  switch (kind) {
    case CollectedHeap::GenCollectedHeap : {
      add_gen_collected_heap_info(GenCollectedHeap::heap());
      break;
    }
#if INCLUDE_ALL_GCS
    case CollectedHeap::ParallelScavengeHeap : {
      add_parallel_scavenge_heap_info(ParallelScavengeHeap::heap());
      break;
    }
    case CollectedHeap::G1CollectedHeap : {
      add_g1_heap_info(G1CollectedHeap::heap());
      break;
    }
#endif // INCLUDE_ALL_GCS
    default: {
      guarantee(false, "Unrecognized kind of heap");
    }
  }

  // set the GC thread count
  GcThreadCountClosure gctcc;
  heap->gc_threads_do(&gctcc);
  int count = gctcc.count();
  if (count > 0) {
    _minor_gc_manager->set_num_gc_threads(count);
    _major_gc_manager->set_num_gc_threads(count);
  }

  // All memory pools and memory managers are initialized.
  //
  _minor_gc_manager->initialize_gc_stat_info();
  _major_gc_manager->initialize_gc_stat_info();
}

// Add memory pools for GenCollectedHeap
// This function currently only supports two generations collected heap.
// The collector for GenCollectedHeap will have two memory managers.
void MemoryService::add_gen_collected_heap_info(GenCollectedHeap* heap) {
  CollectorPolicy* policy = heap->collector_policy();

  assert(policy->is_generation_policy(), "Only support two generations");
  GenCollectorPolicy* gen_policy = policy->as_generation_policy();
  if (gen_policy != NULL) {
    Generation::Name kind = gen_policy->young_gen_spec()->name();
    switch (kind) {
      case Generation::DefNew:
        _minor_gc_manager = MemoryManager::get_copy_memory_manager();
        break;
#if INCLUDE_ALL_GCS
      case Generation::ParNew:
        _minor_gc_manager = MemoryManager::get_parnew_memory_manager();
        break;
#endif // INCLUDE_ALL_GCS
      default:
        guarantee(false, "Unrecognized generation spec");
        break;
    }
    if (policy->is_mark_sweep_policy()) {
      _major_gc_manager = MemoryManager::get_msc_memory_manager();
#if INCLUDE_ALL_GCS
    } else if (policy->is_concurrent_mark_sweep_policy()) {
      _major_gc_manager = MemoryManager::get_cms_memory_manager();
#endif // INCLUDE_ALL_GCS
    } else {
      guarantee(false, "Unknown two-gen policy");
    }
  } else {
    guarantee(false, "Non two-gen policy");
  }
  _managers_list->append(_minor_gc_manager);
  _managers_list->append(_major_gc_manager);

  add_generation_memory_pool(heap->young_gen(), _major_gc_manager, _minor_gc_manager);
  add_generation_memory_pool(heap->old_gen(), _major_gc_manager);
}

#if INCLUDE_ALL_GCS
// Add memory pools for ParallelScavengeHeap
// This function currently only supports two generations collected heap.
// The collector for ParallelScavengeHeap will have two memory managers.
void MemoryService::add_parallel_scavenge_heap_info(ParallelScavengeHeap* heap) {
  // Two managers to keep statistics about _minor_gc_manager and _major_gc_manager GC.
  _minor_gc_manager = MemoryManager::get_psScavenge_memory_manager();
  _major_gc_manager = MemoryManager::get_psMarkSweep_memory_manager();
  _managers_list->append(_minor_gc_manager);
  _managers_list->append(_major_gc_manager);

  add_psYoung_memory_pool(heap->young_gen(), _major_gc_manager, _minor_gc_manager);
  add_psOld_memory_pool(heap->old_gen(), _major_gc_manager);
}

void MemoryService::add_g1_heap_info(G1CollectedHeap* g1h) {
  assert(UseG1GC, "sanity");

  _minor_gc_manager = MemoryManager::get_g1YoungGen_memory_manager();
  _major_gc_manager = MemoryManager::get_g1OldGen_memory_manager();
  _managers_list->append(_minor_gc_manager);
  _managers_list->append(_major_gc_manager);

  add_g1YoungGen_memory_pool(g1h, _major_gc_manager, _minor_gc_manager);
  add_g1OldGen_memory_pool(g1h, _major_gc_manager);
}
#endif // INCLUDE_ALL_GCS

MemoryPool* MemoryService::add_gen(Generation* gen,
                                   const char* name,
                                   bool is_heap,
                                   bool support_usage_threshold) {

  MemoryPool::PoolType type = (is_heap ? MemoryPool::Heap : MemoryPool::NonHeap);
  GenerationPool* pool = new GenerationPool(gen, name, type, support_usage_threshold);
  _pools_list->append(pool);
  return (MemoryPool*) pool;
}

MemoryPool* MemoryService::add_space(ContiguousSpace* space,
                                     const char* name,
                                     bool is_heap,
                                     size_t max_size,
                                     bool support_usage_threshold) {
  MemoryPool::PoolType type = (is_heap ? MemoryPool::Heap : MemoryPool::NonHeap);
  ContiguousSpacePool* pool = new ContiguousSpacePool(space, name, type, max_size, support_usage_threshold);

  _pools_list->append(pool);
  return (MemoryPool*) pool;
}

MemoryPool* MemoryService::add_survivor_spaces(DefNewGeneration* young_gen,
                                               const char* name,
                                               bool is_heap,
                                               size_t max_size,
                                               bool support_usage_threshold) {
  MemoryPool::PoolType type = (is_heap ? MemoryPool::Heap : MemoryPool::NonHeap);
  SurvivorContiguousSpacePool* pool = new SurvivorContiguousSpacePool(young_gen, name, type, max_size, support_usage_threshold);

  _pools_list->append(pool);
  return (MemoryPool*) pool;
}

#if INCLUDE_ALL_GCS
MemoryPool* MemoryService::add_cms_space(CompactibleFreeListSpace* space,
                                         const char* name,
                                         bool is_heap,
                                         size_t max_size,
                                         bool support_usage_threshold) {
  MemoryPool::PoolType type = (is_heap ? MemoryPool::Heap : MemoryPool::NonHeap);
  CompactibleFreeListSpacePool* pool = new CompactibleFreeListSpacePool(space, name, type, max_size, support_usage_threshold);
  _pools_list->append(pool);
  return (MemoryPool*) pool;
}
#endif // INCLUDE_ALL_GCS

// Add memory pool(s) for one generation
void MemoryService::add_generation_memory_pool(Generation* gen,
                                               MemoryManager* major_mgr,
                                               MemoryManager* minor_mgr) {
  guarantee(gen != NULL, "No generation for memory pool");
  Generation::Name kind = gen->kind();
  int index = _pools_list->length();

  switch (kind) {
    case Generation::DefNew: {
      assert(major_mgr != NULL && minor_mgr != NULL, "Should have two managers");
      DefNewGeneration* young_gen = (DefNewGeneration*) gen;
      // Add a memory pool for each space and young gen doesn't
      // support low memory detection as it is expected to get filled up.
      MemoryPool* eden = add_space(young_gen->eden(),
                                   "Eden Space",
                                   true, /* is_heap */
                                   young_gen->max_eden_size(),
                                   false /* support_usage_threshold */);
      MemoryPool* survivor = add_survivor_spaces(young_gen,
                                                 "Survivor Space",
                                                 true, /* is_heap */
                                                 young_gen->max_survivor_size(),
                                                 false /* support_usage_threshold */);
      break;
    }

#if INCLUDE_ALL_GCS
    case Generation::ParNew:
    {
      assert(major_mgr != NULL && minor_mgr != NULL, "Should have two managers");
      // Add a memory pool for each space and young gen doesn't
      // support low memory detection as it is expected to get filled up.
      ParNewGeneration* parnew_gen = (ParNewGeneration*) gen;
      MemoryPool* eden = add_space(parnew_gen->eden(),
                                   "Par Eden Space",
                                   true /* is_heap */,
                                   parnew_gen->max_eden_size(),
                                   false /* support_usage_threshold */);
      MemoryPool* survivor = add_survivor_spaces(parnew_gen,
                                                 "Par Survivor Space",
                                                 true, /* is_heap */
                                                 parnew_gen->max_survivor_size(),
                                                 false /* support_usage_threshold */);

      break;
    }
#endif // INCLUDE_ALL_GCS

    case Generation::MarkSweepCompact: {
      assert(major_mgr != NULL && minor_mgr == NULL, "Should have only one manager");
      add_gen(gen,
              "Tenured Gen",
              true, /* is_heap */
              true  /* support_usage_threshold */);
      break;
    }

#if INCLUDE_ALL_GCS
    case Generation::ConcurrentMarkSweep:
    {
      assert(major_mgr != NULL && minor_mgr == NULL, "Should have only one manager");
      ConcurrentMarkSweepGeneration* cms = (ConcurrentMarkSweepGeneration*) gen;
      MemoryPool* pool = add_cms_space(cms->cmsSpace(),
                                       "CMS Old Gen",
                                       true, /* is_heap */
                                       cms->reserved().byte_size(),
                                       true  /* support_usage_threshold */);
      break;
    }
#endif // INCLUDE_ALL_GCS

    default:
      assert(false, "should not reach here");
      // no memory pool added for others
      break;
  }

  assert(major_mgr != NULL, "Should have at least one manager");
  // Link managers and the memory pools together
  for (int i = index; i < _pools_list->length(); i++) {
    MemoryPool* pool = _pools_list->at(i);
    major_mgr->add_pool(pool);
    if (minor_mgr != NULL) {
      minor_mgr->add_pool(pool);
    }
  }
}


#if INCLUDE_ALL_GCS
void MemoryService::add_psYoung_memory_pool(PSYoungGen* young_gen, MemoryManager* major_mgr, MemoryManager* minor_mgr) {
  assert(major_mgr != NULL && minor_mgr != NULL, "Should have two managers");

  // Add a memory pool for each space and young gen doesn't
  // support low memory detection as it is expected to get filled up.
  EdenMutableSpacePool* eden = new EdenMutableSpacePool(young_gen,
                                                        young_gen->eden_space(),
                                                        "PS Eden Space",
                                                        MemoryPool::Heap,
                                                        false /* support_usage_threshold */);

  SurvivorMutableSpacePool* survivor = new SurvivorMutableSpacePool(young_gen,
                                                                    "PS Survivor Space",
                                                                    MemoryPool::Heap,
                                                                    false /* support_usage_threshold */);

  major_mgr->add_pool(eden);
  major_mgr->add_pool(survivor);
  minor_mgr->add_pool(eden);
  minor_mgr->add_pool(survivor);
  _pools_list->append(eden);
  _pools_list->append(survivor);
}

void MemoryService::add_psOld_memory_pool(PSOldGen* old_gen, MemoryManager* mgr) {
  PSGenerationPool* old_gen_pool = new PSGenerationPool(old_gen,
                                                       "PS Old Gen",
                                                       MemoryPool::Heap,
                                                       true /* support_usage_threshold */);
  mgr->add_pool(old_gen_pool);
  _pools_list->append(old_gen_pool);
}

void MemoryService::add_g1YoungGen_memory_pool(G1CollectedHeap* g1h,
                                               MemoryManager* major_mgr,
                                               MemoryManager* minor_mgr) {
  assert(major_mgr != NULL && minor_mgr != NULL, "should have two managers");

  G1EdenPool* eden = new G1EdenPool(g1h);
  G1SurvivorPool* survivor = new G1SurvivorPool(g1h);

  major_mgr->add_pool(eden);
  major_mgr->add_pool(survivor);
  minor_mgr->add_pool(eden);
  minor_mgr->add_pool(survivor);
  _pools_list->append(eden);
  _pools_list->append(survivor);
}

void MemoryService::add_g1OldGen_memory_pool(G1CollectedHeap* g1h,
                                             MemoryManager* mgr) {
  assert(mgr != NULL, "should have one manager");

  G1OldGenPool* old_gen = new G1OldGenPool(g1h);
  mgr->add_pool(old_gen);
  _pools_list->append(old_gen);
}
#endif // INCLUDE_ALL_GCS

void MemoryService::add_code_heap_memory_pool(CodeHeap* heap, const char* name) {
  // Create new memory pool for this heap
  MemoryPool* code_heap_pool = new CodeHeapPool(heap, name, true /* support_usage_threshold */);

  // Append to lists
  _code_heap_pools->append(code_heap_pool);
  _pools_list->append(code_heap_pool);

  if (_code_cache_manager == NULL) {
    // Create CodeCache memory manager
    _code_cache_manager = MemoryManager::get_code_cache_memory_manager();
    _managers_list->append(_code_cache_manager);
  }

  _code_cache_manager->add_pool(code_heap_pool);
}

void MemoryService::add_metaspace_memory_pools() {
  MemoryManager* mgr = MemoryManager::get_metaspace_memory_manager();

  _metaspace_pool = new MetaspacePool();
  mgr->add_pool(_metaspace_pool);
  _pools_list->append(_metaspace_pool);

  if (UseCompressedClassPointers) {
    _compressed_class_pool = new CompressedKlassSpacePool();
    mgr->add_pool(_compressed_class_pool);
    _pools_list->append(_compressed_class_pool);
  }

  _managers_list->append(mgr);
}

MemoryManager* MemoryService::get_memory_manager(instanceHandle mh) {
  for (int i = 0; i < _managers_list->length(); i++) {
    MemoryManager* mgr = _managers_list->at(i);
    if (mgr->is_manager(mh)) {
      return mgr;
    }
  }
  return NULL;
}

MemoryPool* MemoryService::get_memory_pool(instanceHandle ph) {
  for (int i = 0; i < _pools_list->length(); i++) {
    MemoryPool* pool = _pools_list->at(i);
    if (pool->is_pool(ph)) {
      return pool;
    }
  }
  return NULL;
}

void MemoryService::track_memory_usage() {
  // Track the peak memory usage
  for (int i = 0; i < _pools_list->length(); i++) {
    MemoryPool* pool = _pools_list->at(i);
    pool->record_peak_memory_usage();
  }

  // Detect low memory
  LowMemoryDetector::detect_low_memory();
}

void MemoryService::track_memory_pool_usage(MemoryPool* pool) {
  // Track the peak memory usage
  pool->record_peak_memory_usage();

  // Detect low memory
  if (LowMemoryDetector::is_enabled(pool)) {
    LowMemoryDetector::detect_low_memory(pool);
  }
}

void MemoryService::gc_begin(bool fullGC, bool recordGCBeginTime,
                             bool recordAccumulatedGCTime,
                             bool recordPreGCUsage, bool recordPeakUsage) {

  GCMemoryManager* mgr;
  if (fullGC) {
    mgr = _major_gc_manager;
  } else {
    mgr = _minor_gc_manager;
  }
  assert(mgr->is_gc_memory_manager(), "Sanity check");
  mgr->gc_begin(recordGCBeginTime, recordPreGCUsage, recordAccumulatedGCTime);

  // Track the peak memory usage when GC begins
  if (recordPeakUsage) {
    for (int i = 0; i < _pools_list->length(); i++) {
      MemoryPool* pool = _pools_list->at(i);
      pool->record_peak_memory_usage();
    }
  }
}

void MemoryService::gc_end(bool fullGC, bool recordPostGCUsage,
                           bool recordAccumulatedGCTime,
                           bool recordGCEndTime, bool countCollection,
                           GCCause::Cause cause) {

  GCMemoryManager* mgr;
  if (fullGC) {
    mgr = (GCMemoryManager*) _major_gc_manager;
  } else {
    mgr = (GCMemoryManager*) _minor_gc_manager;
  }
  assert(mgr->is_gc_memory_manager(), "Sanity check");

  // register the GC end statistics and memory usage
  mgr->gc_end(recordPostGCUsage, recordAccumulatedGCTime, recordGCEndTime,
              countCollection, cause);
}

void MemoryService::oops_do(OopClosure* f) {
  int i;

  for (i = 0; i < _pools_list->length(); i++) {
    MemoryPool* pool = _pools_list->at(i);
    pool->oops_do(f);
  }
  for (i = 0; i < _managers_list->length(); i++) {
    MemoryManager* mgr = _managers_list->at(i);
    mgr->oops_do(f);
  }
}

bool MemoryService::set_verbose(bool verbose) {
  MutexLocker m(Management_lock);
  // verbose will be set to the previous value
  Flag::Error error = CommandLineFlags::boolAtPut("PrintGC", &verbose, Flag::MANAGEMENT);
  assert(error==Flag::SUCCESS, "Setting PrintGC flag failed with error %s", Flag::flag_error_str(error));
  ClassLoadingService::reset_trace_class_unloading();

  return verbose;
}

Handle MemoryService::create_MemoryUsage_obj(MemoryUsage usage, TRAPS) {
  Klass* k = Management::java_lang_management_MemoryUsage_klass(CHECK_NH);
  instanceKlassHandle ik(THREAD, k);

  instanceHandle obj = ik->allocate_instance_handle(CHECK_NH);

  JavaValue result(T_VOID);
  JavaCallArguments args(10);
  args.push_oop(obj);                         // receiver
  args.push_long(usage.init_size_as_jlong()); // Argument 1
  args.push_long(usage.used_as_jlong());      // Argument 2
  args.push_long(usage.committed_as_jlong()); // Argument 3
  args.push_long(usage.max_size_as_jlong());  // Argument 4

  JavaCalls::call_special(&result,
                          ik,
                          vmSymbols::object_initializer_name(),
                          vmSymbols::long_long_long_long_void_signature(),
                          &args,
                          CHECK_NH);
  return obj;
}
//
// GC manager type depends on the type of Generation. Depending on the space
// availability and vm options the gc uses major gc manager or minor gc
// manager or both. The type of gc manager depends on the generation kind.
// For DefNew and ParNew generation doing scavenge gc uses minor gc manager (so
// _fullGC is set to false ) and for other generation kinds doing
// mark-sweep-compact uses major gc manager (so _fullGC is set to true).
TraceMemoryManagerStats::TraceMemoryManagerStats(Generation::Name kind, GCCause::Cause cause) {
  switch (kind) {
    case Generation::DefNew:
#if INCLUDE_ALL_GCS
    case Generation::ParNew:
#endif // INCLUDE_ALL_GCS
      _fullGC=false;
      break;
    case Generation::MarkSweepCompact:
#if INCLUDE_ALL_GCS
    case Generation::ConcurrentMarkSweep:
#endif // INCLUDE_ALL_GCS
      _fullGC=true;
      break;
    default:
      assert(false, "Unrecognized gc generation kind.");
  }
  // this has to be called in a stop the world pause and represent
  // an entire gc pause, start to finish:
  initialize(_fullGC, cause,true, true, true, true, true, true, true);
}
TraceMemoryManagerStats::TraceMemoryManagerStats(bool fullGC,
                                                 GCCause::Cause cause,
                                                 bool recordGCBeginTime,
                                                 bool recordPreGCUsage,
                                                 bool recordPeakUsage,
                                                 bool recordPostGCUsage,
                                                 bool recordAccumulatedGCTime,
                                                 bool recordGCEndTime,
                                                 bool countCollection) {
    initialize(fullGC, cause, recordGCBeginTime, recordPreGCUsage, recordPeakUsage,
             recordPostGCUsage, recordAccumulatedGCTime, recordGCEndTime,
             countCollection);
}

// for a subclass to create then initialize an instance before invoking
// the MemoryService
void TraceMemoryManagerStats::initialize(bool fullGC,
                                         GCCause::Cause cause,
                                         bool recordGCBeginTime,
                                         bool recordPreGCUsage,
                                         bool recordPeakUsage,
                                         bool recordPostGCUsage,
                                         bool recordAccumulatedGCTime,
                                         bool recordGCEndTime,
                                         bool countCollection) {
  _fullGC = fullGC;
  _recordGCBeginTime = recordGCBeginTime;
  _recordPreGCUsage = recordPreGCUsage;
  _recordPeakUsage = recordPeakUsage;
  _recordPostGCUsage = recordPostGCUsage;
  _recordAccumulatedGCTime = recordAccumulatedGCTime;
  _recordGCEndTime = recordGCEndTime;
  _countCollection = countCollection;
  _cause = cause;

  MemoryService::gc_begin(_fullGC, _recordGCBeginTime, _recordAccumulatedGCTime,
                          _recordPreGCUsage, _recordPeakUsage);
}

TraceMemoryManagerStats::~TraceMemoryManagerStats() {
  MemoryService::gc_end(_fullGC, _recordPostGCUsage, _recordAccumulatedGCTime,
                        _recordGCEndTime, _countCollection, _cause);
}
