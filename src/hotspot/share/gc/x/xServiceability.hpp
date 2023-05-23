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

#ifndef SHARE_GC_X_XSERVICEABILITY_HPP
#define SHARE_GC_X_XSERVICEABILITY_HPP

#include "gc/shared/collectorCounters.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "memory/allocation.hpp"
#include "services/memoryManager.hpp"
#include "services/memoryPool.hpp"
#include "services/memoryService.hpp"

class XServiceabilityCounters;

class XServiceabilityMemoryPool : public CollectedMemoryPool {
public:
  XServiceabilityMemoryPool(size_t min_capacity, size_t max_capacity);

  virtual size_t used_in_bytes();
  virtual MemoryUsage get_memory_usage();
};

class XServiceabilityMemoryManager : public GCMemoryManager {
public:
  XServiceabilityMemoryManager(const char* name,
                               XServiceabilityMemoryPool* pool);
};

class XServiceability {
private:
  const size_t                 _min_capacity;
  const size_t                 _max_capacity;
  XServiceabilityMemoryPool    _memory_pool;
  XServiceabilityMemoryManager _cycle_memory_manager;
  XServiceabilityMemoryManager _pause_memory_manager;
  XServiceabilityCounters*     _counters;

public:
  XServiceability(size_t min_capacity, size_t max_capacity);

  void initialize();

  MemoryPool* memory_pool();
  GCMemoryManager* cycle_memory_manager();
  GCMemoryManager* pause_memory_manager();
  XServiceabilityCounters* counters();
};

class XServiceabilityCycleTracer : public StackObj {
private:
  TraceMemoryManagerStats _memory_manager_stats;

public:
  XServiceabilityCycleTracer();
};

class XServiceabilityPauseTracer : public StackObj {
private:
  SvcGCMarker             _svc_gc_marker;
  TraceCollectorStats     _counters_stats;
  TraceMemoryManagerStats _memory_manager_stats;

public:
  XServiceabilityPauseTracer();
  ~XServiceabilityPauseTracer();
};

#endif // SHARE_GC_X_XSERVICEABILITY_HPP
