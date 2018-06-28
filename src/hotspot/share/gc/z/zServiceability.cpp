/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

class ZOldGenerationCounters : public GenerationCounters {
public:
  ZOldGenerationCounters(const char* name, size_t min_capacity, size_t max_capacity) :
    // The "1, 1" parameters are for the n-th generation (=1) with 1 space.
    GenerationCounters(name,
                       1 /* ordinal */,
                       1 /* spaces */,
                       min_capacity /* min_capacity */,
                       max_capacity /* max_capacity */,
                       min_capacity /* curr_capacity */) {}

  virtual void update_all() {
    size_t committed = ZHeap::heap()->capacity();
    _current_size->set_value(committed);
  }
};

// Class to expose perf counters used by jstat.
class ZServiceabilityCounters : public CHeapObj<mtGC> {
private:
  ZOldGenerationCounters _old_collection_counters;
  HSpaceCounters         _old_space_counters;

public:
  ZServiceabilityCounters(size_t min_capacity, size_t max_capacity);

  void update_sizes();
};

ZServiceabilityCounters::ZServiceabilityCounters(size_t min_capacity, size_t max_capacity) :
    // generation.1
    _old_collection_counters("old",
                             min_capacity,
                             max_capacity),
    // generation.1.space.0
    _old_space_counters(_old_collection_counters.name_space(),
                        "space",
                        0 /* ordinal */,
                        max_capacity /* max_capacity */,
                        min_capacity /* init_capacity */) {}

void ZServiceabilityCounters::update_sizes() {
  if (UsePerfData) {
    size_t capacity = ZHeap::heap()->capacity();
    size_t used = MIN2(ZHeap::heap()->used(), capacity);

    _old_space_counters.update_capacity(capacity);
    _old_space_counters.update_used(used);

    _old_collection_counters.update_all();

    MetaspaceCounters::update_performance_counters();
    CompressedClassSpaceCounters::update_performance_counters();
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

ZServiceabilityMemoryManager::ZServiceabilityMemoryManager(ZServiceabilityMemoryPool* pool)
    : GCMemoryManager("ZGC", "end of major GC") {
  add_pool(pool);
}

ZServiceability::ZServiceability(size_t min_capacity, size_t max_capacity) :
    _min_capacity(min_capacity),
    _max_capacity(max_capacity),
    _memory_pool(_min_capacity, _max_capacity),
    _memory_manager(&_memory_pool),
    _counters(NULL) {}

void ZServiceability::initialize() {
  _counters = new ZServiceabilityCounters(_min_capacity, _max_capacity);
}

MemoryPool* ZServiceability::memory_pool() {
  return &_memory_pool;
}

GCMemoryManager* ZServiceability::memory_manager() {
  return &_memory_manager;
}

ZServiceabilityCounters* ZServiceability::counters() {
  return _counters;
}

ZServiceabilityMemoryUsageTracker::~ZServiceabilityMemoryUsageTracker() {
  MemoryService::track_memory_usage();
}

ZServiceabilityManagerStatsTracer::ZServiceabilityManagerStatsTracer(bool is_gc_begin, bool is_gc_end) :
    _stats(ZHeap::heap()->serviceability_memory_manager(),
           ZCollectedHeap::heap()->gc_cause() /* cause */,
           true        /* allMemoryPoolsAffected */,
           is_gc_begin /* recordGCBeginTime */,
           is_gc_begin /* recordPreGCUsage */,
           true        /* recordPeakUsage */,
           is_gc_end   /* recordPostGCusage */,
           true        /* recordAccumulatedGCTime */,
           is_gc_end   /* recordGCEndTime */,
           is_gc_end   /* countCollection */) {}

ZServiceabilityCountersTracer::ZServiceabilityCountersTracer() {
  // Nothing to trace with TraceCollectorStats, since ZGC has
  // neither a young collector nor a full collector.
}

ZServiceabilityCountersTracer::~ZServiceabilityCountersTracer() {
  ZHeap::heap()->serviceability_counters()->update_sizes();
}
