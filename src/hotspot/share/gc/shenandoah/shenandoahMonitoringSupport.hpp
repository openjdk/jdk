/*
 * Copyright (c) 2015, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHMONITORINGSUPPORT_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHMONITORINGSUPPORT_HPP

#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "memory/allocation.hpp"
#include "runtime/task.hpp"

class GenerationCounters;
class HSpaceCounters;
class ShenandoahHeap;
class CollectorCounters;
class ShenandoahHeapRegionCounters;
class ShenandoahMonitoringSupport;

class ShenandoahPeriodicCountersUpdateTask : public PeriodicTask {
private:
  ShenandoahSharedFlag _do_counters_update;
  ShenandoahSharedFlag _force_counters_update;
  ShenandoahMonitoringSupport* const _monitoring_support;

public:
  explicit ShenandoahPeriodicCountersUpdateTask(ShenandoahMonitoringSupport* monitoring_support) :
    PeriodicTask(100),
    _monitoring_support(monitoring_support) { }

  void task() override;

  void handle_counters_update();
  void handle_force_counters_update();
  void set_forced_counters_update(bool value);
  void notify_heap_changed();
};

class ShenandoahMonitoringSupport : public CHeapObj<mtGC> {
private:
  CollectorCounters*   _partial_counters;
  CollectorCounters*   _full_counters;

  GenerationCounters* _young_counters;
  GenerationCounters* _heap_counters;

  HSpaceCounters* _space_counters;

  ShenandoahHeapRegionCounters* _heap_region_counters;
  ShenandoahPeriodicCountersUpdateTask _counters_update_task;

public:
  explicit ShenandoahMonitoringSupport(ShenandoahHeap* heap);
  CollectorCounters* stw_collection_counters();
  CollectorCounters* full_stw_collection_counters();
  CollectorCounters* concurrent_collection_counters();
  CollectorCounters* partial_collection_counters();

  void notify_heap_changed();
  void set_forced_counters_update(bool value);
  void handle_force_counters_update();

  void update_counters();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHMONITORINGSUPPORT_HPP
