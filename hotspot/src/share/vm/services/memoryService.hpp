/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_MEMORYSERVICE_HPP
#define SHARE_VM_SERVICES_MEMORYSERVICE_HPP

#include "memory/allocation.hpp"
#include "memory/generation.hpp"
#include "runtime/handles.hpp"
#include "services/memoryUsage.hpp"
#include "gc_interface/gcCause.hpp"

// Forward declaration
class MemoryPool;
class MemoryManager;
class GCMemoryManager;
class CollectedHeap;
class Generation;
class DefNewGeneration;
class PSYoungGen;
class PSOldGen;
class CodeHeap;
class ContiguousSpace;
class CompactibleFreeListSpace;
class GenCollectedHeap;
class ParallelScavengeHeap;
class G1CollectedHeap;

// VM Monitoring and Management Support

class MemoryService : public AllStatic {
private:
  enum {
    init_pools_list_size = 10,
    init_managers_list_size = 5
  };

  // index for minor and major generations
  enum {
    minor = 0,
    major = 1,
    n_gens = 2
  };

  static GrowableArray<MemoryPool*>*    _pools_list;
  static GrowableArray<MemoryManager*>* _managers_list;

  // memory managers for minor and major GC statistics
  static GCMemoryManager*               _major_gc_manager;
  static GCMemoryManager*               _minor_gc_manager;

  // Code heap memory pool
  static MemoryPool*                    _code_heap_pool;

  static MemoryPool*                    _metaspace_pool;
  static MemoryPool*                    _compressed_class_pool;

  static void add_generation_memory_pool(Generation* gen,
                                         MemoryManager* major_mgr,
                                         MemoryManager* minor_mgr);
  static void add_generation_memory_pool(Generation* gen,
                                         MemoryManager* major_mgr) {
    add_generation_memory_pool(gen, major_mgr, NULL);
  }


  static void add_psYoung_memory_pool(PSYoungGen* gen,
                                      MemoryManager* major_mgr,
                                      MemoryManager* minor_mgr);
  static void add_psOld_memory_pool(PSOldGen* gen,
                                    MemoryManager* mgr);

  static void add_g1YoungGen_memory_pool(G1CollectedHeap* g1h,
                                         MemoryManager* major_mgr,
                                         MemoryManager* minor_mgr);
  static void add_g1OldGen_memory_pool(G1CollectedHeap* g1h,
                                       MemoryManager* mgr);

  static MemoryPool* add_space(ContiguousSpace* space,
                               const char* name,
                               bool is_heap,
                               size_t max_size,
                               bool support_usage_threshold);
  static MemoryPool* add_survivor_spaces(DefNewGeneration* gen,
                                         const char* name,
                                         bool is_heap,
                                         size_t max_size,
                                         bool support_usage_threshold);
  static MemoryPool* add_gen(Generation* gen,
                             const char* name,
                             bool is_heap,
                             bool support_usage_threshold);
  static MemoryPool* add_cms_space(CompactibleFreeListSpace* space,
                                   const char* name,
                                   bool is_heap,
                                   size_t max_size,
                                   bool support_usage_threshold);

  static void add_gen_collected_heap_info(GenCollectedHeap* heap);
  static void add_parallel_scavenge_heap_info(ParallelScavengeHeap* heap);
  static void add_g1_heap_info(G1CollectedHeap* g1h);

public:
  static void set_universe_heap(CollectedHeap* heap);
  static void add_code_heap_memory_pool(CodeHeap* heap);
  static void add_metaspace_memory_pools();

  static MemoryPool*    get_memory_pool(instanceHandle pool);
  static MemoryManager* get_memory_manager(instanceHandle mgr);

  static const int num_memory_pools() {
    return _pools_list->length();
  }
  static const int num_memory_managers() {
    return _managers_list->length();
  }

  static MemoryPool* get_memory_pool(int index) {
    return _pools_list->at(index);
  }

  static MemoryManager* get_memory_manager(int index) {
    return _managers_list->at(index);
  }

  static void track_memory_usage();
  static void track_code_cache_memory_usage() {
    track_memory_pool_usage(_code_heap_pool);
  }
  static void track_metaspace_memory_usage() {
    track_memory_pool_usage(_metaspace_pool);
  }
  static void track_compressed_class_memory_usage() {
    track_memory_pool_usage(_compressed_class_pool);
  }
  static void track_memory_pool_usage(MemoryPool* pool);

  static void gc_begin(bool fullGC, bool recordGCBeginTime,
                       bool recordAccumulatedGCTime,
                       bool recordPreGCUsage, bool recordPeakUsage);
  static void gc_end(bool fullGC, bool recordPostGCUsage,
                     bool recordAccumulatedGCTime,
                     bool recordGCEndTime, bool countCollection,
                     GCCause::Cause cause);


  static void oops_do(OopClosure* f);

  static bool get_verbose() { return PrintGC; }
  static bool set_verbose(bool verbose);

  // Create an instance of java/lang/management/MemoryUsage
  static Handle create_MemoryUsage_obj(MemoryUsage usage, TRAPS);

  static const GCMemoryManager* get_minor_gc_manager() {
      return _minor_gc_manager;
  }

  static const GCMemoryManager* get_major_gc_manager() {
      return _major_gc_manager;
  }
};

class TraceMemoryManagerStats : public StackObj {
private:
  bool         _fullGC;
  bool         _recordGCBeginTime;
  bool         _recordPreGCUsage;
  bool         _recordPeakUsage;
  bool         _recordPostGCUsage;
  bool         _recordAccumulatedGCTime;
  bool         _recordGCEndTime;
  bool         _countCollection;
  GCCause::Cause _cause;
public:
  TraceMemoryManagerStats() {}
  TraceMemoryManagerStats(bool fullGC,
                          GCCause::Cause cause,
                          bool recordGCBeginTime = true,
                          bool recordPreGCUsage = true,
                          bool recordPeakUsage = true,
                          bool recordPostGCUsage = true,
                          bool recordAccumulatedGCTime = true,
                          bool recordGCEndTime = true,
                          bool countCollection = true);

  void initialize(bool fullGC,
                  GCCause::Cause cause,
                  bool recordGCBeginTime,
                  bool recordPreGCUsage,
                  bool recordPeakUsage,
                  bool recordPostGCUsage,
                  bool recordAccumulatedGCTime,
                  bool recordGCEndTime,
                  bool countCollection);

  TraceMemoryManagerStats(Generation::Name kind, GCCause::Cause cause);
  ~TraceMemoryManagerStats();
};

#endif // SHARE_VM_SERVICES_MEMORYSERVICE_HPP
