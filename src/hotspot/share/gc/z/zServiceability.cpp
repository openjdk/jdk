/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "gc/shared/generationCounters.hpp"
#include "gc/shared/hSpaceCounters.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zServiceability.hpp"
#include "memory/metaspaceCounters.hpp"
#include "runtime/perfData.hpp"

struct ZMemoryUsageInfo {
  size_t _young_used;
  size_t _young_capacity;
  size_t _old_used;
  size_t _old_capacity;
};

static ZMemoryUsageInfo compute_memory_usage_info() {
  const size_t capacity = ZHeap::heap()->capacity();
  const size_t old_used = ZHeap::heap()->old_generation()->used_total();
  const size_t young_used = ZHeap::heap()->young_generation()->used_total();

  ZMemoryUsageInfo info;
  info._old_used = MIN2(old_used, capacity);
  info._old_capacity = info._old_used;
  info._young_capacity = capacity - info._old_capacity;
  info._young_used = MIN2(young_used, info._young_capacity);
  return info;
}

class ZGenerationCounters : public GenerationCounters {
public:
  ZGenerationCounters(const char* name, int ordinal, int spaces,
                      size_t min_capacity, size_t max_capacity, size_t curr_capacity) :
      GenerationCounters(name, ordinal, spaces,
                         min_capacity, max_capacity, curr_capacity) {}

  void update_capacity(size_t capacity) {
    _current_size->set_value(capacity);
  }
};

// Class to expose perf counters used by jstat.
class ZServiceabilityCounters : public CHeapObj<mtGC> {
private:
  ZGenerationCounters _young_generation_counters;
  ZGenerationCounters _old_generation_counters;
  HSpaceCounters      _young_space_counters;
  HSpaceCounters      _old_space_counters;
  CollectorCounters   _young_collector_counters;
  CollectorCounters   _old_collector_counters;

public:
  ZServiceabilityCounters(size_t initial_capacity, size_t min_capacity, size_t max_capacity);

  CollectorCounters* collector_counters(ZCollectorId collector_id);

  void update_sizes(ZCollectorId collector_id);
};

ZServiceabilityCounters::ZServiceabilityCounters(size_t initial_capacity, size_t min_capacity, size_t max_capacity) :
    // generation.0
    _young_generation_counters(
        "young"          /* name */,
        0                /* ordinal */,
        1                /* spaces */,
        min_capacity     /* min_capacity */,
        max_capacity     /* max_capacity */,
        initial_capacity /* curr_capacity */),
    // generation.1
    _old_generation_counters(
        "old"        /* name */,
        1            /* ordinal */,
        1            /* spaces */,
        0            /* min_capacity */,
        max_capacity /* max_capacity */,
        0            /* curr_capacity */),
    // generation.0.space.0
    _young_space_counters(
        _young_generation_counters.name_space(),
        "space"          /* name */,
        0                /* ordinal */,
        max_capacity     /* max_capacity */,
        initial_capacity /* init_capacity */),
    // generation.1.space.0
    _old_space_counters(
        _old_generation_counters.name_space(),
        "space"      /* name */,
        0            /* ordinal */,
        max_capacity /* max_capacity */,
        0            /* init_capacity */),
    // gc.collector.0
    _young_collector_counters(
        "Z young collection pauses" /* name */,
        0                           /* ordinal */),
    // gc.collector.2
    _old_collector_counters(
        "Z old collection pauses" /* name */,
        2                         /* ordinal */) {}

CollectorCounters* ZServiceabilityCounters::collector_counters(ZCollectorId collector_id) {
  return collector_id == ZCollectorId::young
      ? &_young_collector_counters
      : &_old_collector_counters;
}

void ZServiceabilityCounters::update_sizes(ZCollectorId collector_id) {
  if (UsePerfData) {
    ZMemoryUsageInfo info = compute_memory_usage_info();
    _young_generation_counters.update_capacity(info._young_capacity);
    _old_generation_counters.update_capacity(info._old_capacity);
    _young_space_counters.update_capacity(info._young_capacity);
    _young_space_counters.update_used(info._young_used);
    _old_space_counters.update_capacity(info._old_capacity);
    _old_space_counters.update_used(info._old_used);

    MetaspaceCounters::update_performance_counters();
  }
}

  ZServiceabilityMemoryPool::ZServiceabilityMemoryPool(const char* name, const ZGeneration* generation, size_t min_capacity, size_t max_capacity) :
    CollectedMemoryPool(name,
                        min_capacity,
                        max_capacity,
                        generation->is_old() /* support_usage_threshold */),
    _generation(generation) {}

size_t ZServiceabilityMemoryPool::used_in_bytes() {
  return _generation->used_total();
}

MemoryUsage ZServiceabilityMemoryPool::get_memory_usage() {
  ZMemoryUsageInfo info = compute_memory_usage_info();

  if (_generation->is_young()) {
    return MemoryUsage(initial_size(), info._young_used, info._young_capacity, max_size());
  } else {
    return MemoryUsage(initial_size(), info._old_used, info._old_capacity, max_size());
  }
}

ZServiceabilityMemoryManager::ZServiceabilityMemoryManager(const char* name,
                                                           const char* end_message,
                                                           ZServiceabilityMemoryPool* pool) :
    GCMemoryManager(name, end_message) {
  add_pool(pool);
}

ZServiceability::ZServiceability(size_t initial_capacity,
                                 size_t min_capacity,
                                 size_t max_capacity,
                                 const ZGeneration* young_generation,
                                 const ZGeneration* old_generation) :
    _initial_capacity(initial_capacity),
    _min_capacity(min_capacity),
    _max_capacity(max_capacity),
    _young_memory_pool("ZYoungGeneration", young_generation, _min_capacity, _max_capacity),
    _old_memory_pool("ZOldGeneration", old_generation, 0, _max_capacity),
    _young_cycle_memory_manager("ZGC Young Cycles", "end of GC cycle", &_young_memory_pool),
    _old_cycle_memory_manager("ZGC Old Cycles", "end of GC cycle", &_old_memory_pool),
    _young_pause_memory_manager("ZGC Young Pauses", "end of GC pause", &_young_memory_pool),
    _old_pause_memory_manager("ZGC Old Pauses", "end of GC pause", &_old_memory_pool),
    _counters(NULL) {}

void ZServiceability::initialize() {
  _counters = new ZServiceabilityCounters(_initial_capacity, _min_capacity, _max_capacity);
}

MemoryPool* ZServiceability::memory_pool(ZGenerationId generation_id) {
  return generation_id == ZGenerationId::young
      ? &_young_memory_pool
      : &_old_memory_pool;
}

GCMemoryManager* ZServiceability::cycle_memory_manager(ZCollectorId collector_id) {
  return collector_id == ZCollectorId::young
      ? &_young_cycle_memory_manager
      : &_old_cycle_memory_manager;
}

GCMemoryManager* ZServiceability::pause_memory_manager(ZCollectorId collector_id) {
  return collector_id == ZCollectorId::young
      ? &_young_pause_memory_manager
      : &_old_pause_memory_manager;
}

ZServiceabilityCounters* ZServiceability::counters() {
  return _counters;
}

ZServiceabilityCycleTracer::ZServiceabilityCycleTracer(ZCollectorId collector_id) :
    _memory_manager_stats(ZHeap::heap()->serviceability_cycle_memory_manager(collector_id),
                          ZCollectedHeap::heap()->gc_cause(),
                          true /* allMemoryPoolsAffected */,
                          true /* recordGCBeginTime */,
                          true /* recordPreGCUsage */,
                          true /* recordPeakUsage */,
                          true /* recordPostGCUsage */,
                          true /* recordAccumulatedGCTime */,
                          true /* recordGCEndTime */,
                          true /* countCollection */) {}

ZServiceabilityPauseTracer::ZServiceabilityPauseTracer(ZCollectorId collector_id) :
    _collector_id(collector_id),
    _svc_gc_marker(SvcGCMarker::CONCURRENT),
    _counters_stats(ZHeap::heap()->serviceability_counters()->collector_counters(collector_id)),
    _memory_manager_stats(ZHeap::heap()->serviceability_pause_memory_manager(collector_id),
                          ZCollectedHeap::heap()->gc_cause(),
                          true  /* allMemoryPoolsAffected */,
                          true  /* recordGCBeginTime */,
                          false /* recordPreGCUsage */,
                          false /* recordPeakUsage */,
                          false /* recordPostGCUsage */,
                          true  /* recordAccumulatedGCTime */,
                          true  /* recordGCEndTime */,
                          true  /* countCollection */) {}

ZServiceabilityPauseTracer::~ZServiceabilityPauseTracer()  {
  ZHeap::heap()->serviceability_counters()->update_sizes(_collector_id);
  MemoryService::track_memory_usage();
}
