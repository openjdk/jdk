/*
 * Copyright (c) 2022, Amazon, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHMMUTRACKER_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHMMUTRACKER_HPP

#include "runtime/mutex.hpp"
#include "utilities/numberSeq.hpp"

class ShenandoahGeneration;
class ShenandoahMmuTask;

/**
 * This class is responsible for tracking and adjusting the minimum mutator
 * utilization (MMU). MMU is defined as the percentage of CPU time available
 * to mutator threads over an arbitrary, fixed interval of time. MMU is measured
 * by summing all of the time given to the GC threads and comparing this too
 * the total CPU time for the process. There are OS APIs to support this on
 * all major platforms.
 *
 * The time spent by GC threads is attributed to the young or old generation.
 * The time given to the controller and regulator threads is attributed to the
 * global generation. At the end of every collection, the average MMU is inspected.
 * If it is below `GCTimeRatio`, this class will attempt to increase the capacity
 * of the generation that is consuming the most CPU time. The assumption being
 * that increasing memory will reduce the collection frequency and raise the
 * MMU.
 */
class ShenandoahMmuTracker {

  double _initial_collector_time_s;
  double _initial_process_time_s;
  double _initial_verify_collector_time_s;

  double _resize_increment;

  ShenandoahMmuTask* _mmu_periodic_task;
  TruncatedSeq _mmu_average;

  bool transfer_capacity(ShenandoahGeneration* from, ShenandoahGeneration* to);

  static double gc_thread_time_seconds();
  static double process_time_seconds();

public:
  explicit ShenandoahMmuTracker();
  ~ShenandoahMmuTracker();

  // This enrolls the periodic task after everything is initialized.
  void initialize();

  // This is called at the start and end of a GC cycle. The GC thread times
  // will be accumulated in this generation. Note that the bootstrap cycle
  // for an old collection should be counted against the old generation.
  // When the collector is idle, it still runs a regulator and a control.
  // The times for these threads are attributed to the global generation.
  void record(ShenandoahGeneration* generation);

  // This is called by the periodic task timer. The interval is defined by
  // GCPauseIntervalMillis and defaults to 5 seconds. This method computes
  // the MMU over the elapsed interval and records it in a running average.
  // This method also logs the average MMU.
  void report();

  // This is invoked at the end of a collection. This happens on a safepoint
  // to avoid any races with allocators (and to avoid interfering with
  // allocators by taking the heap lock). The amount of capacity to move
  // from one generation to another is controlled by YoungGenerationSizeIncrement
  // and defaults to 20% of the heap. The minimum and maximum sizes of the
  // young generation are controlled by ShenandoahMinYoungPercentage and
  // ShenandoahMaxYoungPercentage, respectively. The method returns true
  // when and adjustment is made, false otherwise.
  bool adjust_generation_sizes();
};



#endif //SHARE_GC_SHENANDOAH_SHENANDOAHMMUTRACKER_HPP
