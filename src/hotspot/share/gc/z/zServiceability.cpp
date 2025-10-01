/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/generationCounters.hpp"
#include "gc/shared/hSpaceCounters.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zDriver.hpp"
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
  const size_t old_used = ZHeap::heap()->used_old();
  const size_t young_used = ZHeap::heap()->used_young();

  ZMemoryUsageInfo info;
  info._old_used = MIN2(old_used, capacity);
  info._old_capacity = info._old_used;
  info._young_capacity = capacity - info._old_capacity;
  info._young_used = MIN2(young_used, info._young_capacity);
  return info;
}

// Class to expose perf counters used by jstat.
class ZServiceabilityCounters : public CHeapObj<mtGC> {
private:
  GenerationCounters  _generation_young_counters;
  GenerationCounters  _generation_old_counters;
  HSpaceCounters      _space_young_counters;
  HSpaceCounters      _space_old_counters;
  CollectorCounters   _minor_collection_counters;
  CollectorCounters   _major_collection_counters;

public:
  ZServiceabilityCounters(size_t initial_capacity, size_t min_capacity, size_t max_capacity);

  CollectorCounters* collector_counters(bool minor);

  void update_sizes();
};

ZServiceabilityCounters::ZServiceabilityCounters(size_t initial_capacity, size_t min_capacity, size_t max_capacity)
  : // generation.0
    _generation_young_counters(
        "young"          /* name */,
        0                /* ordinal */,
        1                /* spaces */,
        min_capacity     /* min_capacity */,
        max_capacity     /* max_capacity */,
        initial_capacity /* curr_capacity */),
    // generation.1
    _generation_old_counters(
        "old"        /* name */,
        1            /* ordinal */,
        1            /* spaces */,
        0            /* min_capacity */,
        max_capacity /* max_capacity */,
        0            /* curr_capacity */),
    // generation.0.space.0
    _space_young_counters(
        _generation_young_counters.name_space(),
        "space"          /* name */,
        0                /* ordinal */,
        max_capacity     /* max_capacity */,
        initial_capacity /* init_capacity */),
    // generation.1.space.0
    _space_old_counters(
        _generation_old_counters.name_space(),
        "space"      /* name */,
        0            /* ordinal */,
        max_capacity /* max_capacity */,
        0            /* init_capacity */),
    // gc.collector.0
    _minor_collection_counters(
        "ZGC minor collection pauses" /* name */,
        0                             /* ordinal */),
    // gc.collector.2
    _major_collection_counters(
        "ZGC major collection pauses" /* name */,
        2                             /* ordinal */) {}

CollectorCounters* ZServiceabilityCounters::collector_counters(bool minor) {
  return minor
      ? &_minor_collection_counters
      : &_major_collection_counters;
}

void ZServiceabilityCounters::update_sizes() {
  if (UsePerfData) {
    const ZMemoryUsageInfo info = compute_memory_usage_info();
    _generation_young_counters.update_capacity(info._young_capacity);
    _generation_old_counters.update_capacity(info._old_capacity);
    _space_young_counters.update_capacity(info._young_capacity);
    _space_young_counters.update_used(info._young_used);
    _space_old_counters.update_capacity(info._old_capacity);
    _space_old_counters.update_used(info._old_used);

    MetaspaceCounters::update_performance_counters();
  }
}

ZServiceabilityMemoryPool::ZServiceabilityMemoryPool(const char* name, ZGenerationId id, size_t min_capacity, size_t max_capacity)
  : CollectedMemoryPool(name,
                        min_capacity,
                        max_capacity,
                        id == ZGenerationId::old /* support_usage_threshold */),
    _generation_id(id) {}

size_t ZServiceabilityMemoryPool::used_in_bytes() {
  return ZHeap::heap()->used_generation(_generation_id);
}

MemoryUsage ZServiceabilityMemoryPool::get_memory_usage() {
  const ZMemoryUsageInfo info = compute_memory_usage_info();

  if (_generation_id == ZGenerationId::young) {
    return MemoryUsage(initial_size(), info._young_used, info._young_capacity, max_size());
  } else {
    return MemoryUsage(initial_size(), info._old_used, info._old_capacity, max_size());
  }
}

ZServiceabilityMemoryManager::ZServiceabilityMemoryManager(const char* name,
                                                           MemoryPool* young_memory_pool,
                                                           MemoryPool* old_memory_pool)
  : GCMemoryManager(name) {
  add_pool(young_memory_pool);
  add_pool(old_memory_pool);
}

ZServiceability::ZServiceability(size_t initial_capacity,
                                 size_t min_capacity,
                                 size_t max_capacity)
  : _initial_capacity(initial_capacity),
    _min_capacity(min_capacity),
    _max_capacity(max_capacity),
    _young_memory_pool("ZGC Young Generation", ZGenerationId::young, _min_capacity, _max_capacity),
    _old_memory_pool("ZGC Old Generation", ZGenerationId::old, 0, _max_capacity),
    _minor_cycle_memory_manager("ZGC Minor Cycles", &_young_memory_pool, &_old_memory_pool),
    _major_cycle_memory_manager("ZGC Major Cycles", &_young_memory_pool, &_old_memory_pool),
    _minor_pause_memory_manager("ZGC Minor Pauses", &_young_memory_pool, &_old_memory_pool),
    _major_pause_memory_manager("ZGC Major Pauses", &_young_memory_pool, &_old_memory_pool),
    _counters(nullptr) {}

void ZServiceability::initialize() {
  _counters = new ZServiceabilityCounters(_initial_capacity, _min_capacity, _max_capacity);
}

MemoryPool* ZServiceability::memory_pool(ZGenerationId id) {
  return id == ZGenerationId::young
      ? &_young_memory_pool
      : &_old_memory_pool;
}

GCMemoryManager* ZServiceability::cycle_memory_manager(bool minor) {
  return minor
      ? &_minor_cycle_memory_manager
      : &_major_cycle_memory_manager;
}

GCMemoryManager* ZServiceability::pause_memory_manager(bool minor) {
  return minor
      ? &_minor_pause_memory_manager
      : &_major_pause_memory_manager;
}

ZServiceabilityCounters* ZServiceability::counters() {
  return _counters;
}

bool ZServiceabilityCycleTracer::_minor_is_active;

ZServiceabilityCycleTracer::ZServiceabilityCycleTracer(bool minor)
  : _memory_manager_stats(ZHeap::heap()->serviceability_cycle_memory_manager(minor),
                          minor ? ZDriver::minor()->gc_cause() : ZDriver::major()->gc_cause(),
                          "end of GC cycle",
                          true /* allMemoryPoolsAffected */,
                          true /* recordGCBeginTime */,
                          true /* recordPreGCUsage */,
                          true /* recordPeakUsage */,
                          true /* recordPostGCUsage */,
                          true /* recordAccumulatedGCTime */,
                          true /* recordGCEndTime */,
                          true /* countCollection */) {
  _minor_is_active = minor;
}

ZServiceabilityCycleTracer::~ZServiceabilityCycleTracer() {
  _minor_is_active = false;
}

bool ZServiceabilityCycleTracer::minor_is_active() {
  return _minor_is_active;
}

bool ZServiceabilityPauseTracer::minor_is_active() const {
  // We report pauses at the minor/major collection level instead
  // of the young/old level. At the call-site where ZServiceabilityPauseTracer
  // is used, we don't have that information readily available, so
  // we let ZServiceabilityCycleTracer keep track of that.
  return ZServiceabilityCycleTracer::minor_is_active();
}

ZServiceabilityPauseTracer::ZServiceabilityPauseTracer()
  : _svc_gc_marker(SvcGCMarker::CONCURRENT),
    _counters_stats(ZHeap::heap()->serviceability_counters()->collector_counters(minor_is_active())),
    _memory_manager_stats(ZHeap::heap()->serviceability_pause_memory_manager(minor_is_active()),
                          minor_is_active() ? ZDriver::minor()->gc_cause() : ZDriver::major()->gc_cause(),
                          "end of GC pause",
                          true  /* allMemoryPoolsAffected */,
                          true  /* recordGCBeginTime */,
                          false /* recordPreGCUsage */,
                          false /* recordPeakUsage */,
                          false /* recordPostGCUsage */,
                          true  /* recordAccumulatedGCTime */,
                          true  /* recordGCEndTime */,
                          true  /* countCollection */) {}

ZServiceabilityPauseTracer::~ZServiceabilityPauseTracer()  {
  ZHeap::heap()->serviceability_counters()->update_sizes();
  MemoryService::track_memory_usage();
}
