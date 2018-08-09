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

#ifndef SHARE_GC_Z_ZSERVICEABILITY_HPP
#define SHARE_GC_Z_ZSERVICEABILITY_HPP

#include "gc/shared/collectorCounters.hpp"
#include "memory/allocation.hpp"
#include "services/memoryManager.hpp"
#include "services/memoryPool.hpp"
#include "services/memoryService.hpp"

class ZServiceabilityCounters;

class ZServiceabilityMemoryPool : public CollectedMemoryPool {
public:
  ZServiceabilityMemoryPool(size_t min_capacity, size_t max_capacity);

  virtual size_t used_in_bytes();
  virtual MemoryUsage get_memory_usage();
};

class ZServiceabilityMemoryManager : public GCMemoryManager {
public:
  ZServiceabilityMemoryManager(ZServiceabilityMemoryPool* pool);
};

class ZServiceability {
private:
  const size_t                 _min_capacity;
  const size_t                 _max_capacity;
  ZServiceabilityMemoryPool    _memory_pool;
  ZServiceabilityMemoryManager _memory_manager;
  ZServiceabilityCounters*     _counters;

public:
  ZServiceability(size_t min_capacity, size_t max_capacity);

  void initialize();

  MemoryPool* memory_pool();
  GCMemoryManager* memory_manager();
  ZServiceabilityCounters* counters();
};

class ZServiceabilityMemoryUsageTracker {
public:
  ~ZServiceabilityMemoryUsageTracker();
};

class ZServiceabilityManagerStatsTracer {
private:
  TraceMemoryManagerStats _stats;

public:
  ZServiceabilityManagerStatsTracer(bool is_gc_begin, bool is_gc_end);
};

class ZServiceabilityCountersTracer {
private:
  TraceCollectorStats _stats;

public:
  ZServiceabilityCountersTracer();
  ~ZServiceabilityCountersTracer();
};

template <bool IsGCStart, bool IsGCEnd>
class ZServiceabilityTracer : public StackObj {
private:
  ZServiceabilityMemoryUsageTracker _memory_usage_tracker;
  ZServiceabilityManagerStatsTracer _manager_stats_tracer;
  ZServiceabilityCountersTracer     _counters_tracer;

public:
  ZServiceabilityTracer() :
      _memory_usage_tracker(),
      _manager_stats_tracer(IsGCStart, IsGCEnd),
      _counters_tracer() {}
};

typedef ZServiceabilityTracer<true,  false> ZServiceabilityMarkStartTracer;
typedef ZServiceabilityTracer<false, false> ZServiceabilityMarkEndTracer;
typedef ZServiceabilityTracer<false, true>  ZServiceabilityRelocateStartTracer;

#endif // SHARE_GC_Z_ZSERVICEABILITY_HPP
