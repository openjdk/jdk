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
  CollectorCounters   _minor_collector_counters;
  CollectorCounters   _major_collector_counters;

public:
  ZServiceabilityCounters(size_t min_capacity, size_t max_capacity);

  CollectorCounters* collector_counters(ZCollectorId collector_id);

  void update_sizes(ZCollectorId collector_id);
};

ZServiceabilityCounters::ZServiceabilityCounters(size_t min_capacity, size_t max_capacity) :
    // generation.0
    _young_generation_counters(
        "young"      /* name */,
        0            /* ordinal */,
        1            /* spaces */,
        min_capacity /* min_capacity */,
        max_capacity /* max_capacity */,
        min_capacity /* curr_capacity */),
    // generation.1
    _old_generation_counters(
        "old"        /* name */,
        1            /* ordinal */,
        1            /* spaces */,
        min_capacity /* min_capacity */,
        max_capacity /* max_capacity */,
        min_capacity /* curr_capacity */),
    // generation.0.space.0
    _young_space_counters(
        _young_generation_counters.name_space(),
        "space"      /* name */,
        0            /* ordinal */,
        max_capacity /* max_capacity */,
        min_capacity /* init_capacity */),
    // generation.1.space.0
    _old_space_counters(
        _old_generation_counters.name_space(),
        "space"      /* name */,
        0            /* ordinal */,
        max_capacity /* max_capacity */,
        min_capacity /* init_capacity */),
    // gc.collector.0
    _minor_collector_counters(
        "Z minor_concurrent cycle pauses" /* name */,
        0                                 /* ordinal */),
    // gc.collector.2
    _major_collector_counters(
        "Z major_concurrent cycle pauses" /* name */,
        2                                 /* ordinal */) {}

CollectorCounters* ZServiceabilityCounters::collector_counters(ZCollectorId collector_id) {
  return collector_id == ZCollectorId::_minor
      ? &_minor_collector_counters
      : &_major_collector_counters;
}

void ZServiceabilityCounters::update_sizes(ZCollectorId collector_id) {
  if (UsePerfData) {
    const size_t capacity = ZHeap::heap()->capacity();

    if  (collector_id == ZCollectorId::_minor) {
      const size_t used = MIN2(ZHeap::heap()->young_generation()->used(), capacity);

      _young_generation_counters.update_capacity(capacity);
      _young_space_counters.update_capacity(capacity);
      _young_space_counters.update_used(used);
    } else {
      const size_t used = MIN2(ZHeap::heap()->old_generation()->used(), capacity);

      _old_generation_counters.update_capacity(capacity);
      _old_space_counters.update_capacity(capacity);
      _old_space_counters.update_used(used);
    }

    MetaspaceCounters::update_performance_counters();
  }
}

ZServiceabilityMemoryPool::ZServiceabilityMemoryPool(size_t min_capacity, size_t max_capacity) :
    CollectedMemoryPool("ZHeap",
                        min_capacity,
                        max_capacity,
                        true /* support_usage_threshold */) {}

size_t ZServiceabilityMemoryPool::used_in_bytes() {
  return ZHeap::heap()->used();
}

MemoryUsage ZServiceabilityMemoryPool::get_memory_usage() {
  const size_t committed = ZHeap::heap()->capacity();
  const size_t used      = MIN2(ZHeap::heap()->used(), committed);

  return MemoryUsage(initial_size(), used, committed, max_size());
}

ZServiceabilityMemoryManager::ZServiceabilityMemoryManager(const char* name,
                                                           const char* end_message,
                                                           ZServiceabilityMemoryPool* pool) :
    GCMemoryManager(name, end_message) {
  add_pool(pool);
}

ZServiceability::ZServiceability(size_t min_capacity, size_t max_capacity) :
    _min_capacity(min_capacity),
    _max_capacity(max_capacity),
    _memory_pool(_min_capacity, _max_capacity),
    _minor_cycle_memory_manager("ZGC Minor Cycles", "end of GC cycle", &_memory_pool),
    _major_cycle_memory_manager("ZGC Major Cycles", "end of GC cycle", &_memory_pool),
    _minor_pause_memory_manager("ZGC Minor Pauses", "end of GC pause", &_memory_pool),
    _major_pause_memory_manager("ZGC Major Pauses", "end of GC pause", &_memory_pool),
    _counters(NULL) {}

void ZServiceability::initialize() {
  _counters = new ZServiceabilityCounters(_min_capacity, _max_capacity);
}

MemoryPool* ZServiceability::memory_pool() {
  return &_memory_pool;
}

GCMemoryManager* ZServiceability::cycle_memory_manager(ZCollectorId collector_id) {
  return collector_id == ZCollectorId::_minor
      ? &_minor_cycle_memory_manager
      : &_major_cycle_memory_manager;
}

GCMemoryManager* ZServiceability::pause_memory_manager(ZCollectorId collector_id) {
  return collector_id == ZCollectorId::_minor
      ? &_minor_pause_memory_manager
      : &_major_pause_memory_manager;
}

ZServiceabilityCounters* ZServiceability::counters() {
  return _counters;
}

ZServiceabilityCycleTracer::ZServiceabilityCycleTracer(ZCollectorId collector_id) :
    _memory_manager_stats(ZHeap::heap()->serviceability_cycle_memory_manager(collector_id),
                          ZCollectedHeap::heap()->gc_cause(),
                          true  /* allMemoryPoolsAffected */,
                          true  /* recordGCBeginTime */,
                          true  /* recordPreGCUsage */,
                          true  /* recordPeakUsage */,
                          true  /* recordPostGCUsage */,
                          true  /* recordAccumulatedGCTime */,
                          true  /* recordGCEndTime */,
                          true  /* countCollection */) {}

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
