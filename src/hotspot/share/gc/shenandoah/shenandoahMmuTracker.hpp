/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * to mutator threads over an arbitrary, fixed interval of time. This interval
 * defaults to 5 seconds and is configured by GCPauseIntervalMillis. The class
 * maintains a decaying average of the last 10 values. The MMU is measured
 * by summing all of the time given to the GC threads and comparing this to
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
private:
  // These variables hold recent snapshots of cumulative quantities that are used for calculating
  // CPU time consumed by GC and mutator threads during each GC cycle.
  double _most_recent_timestamp;
  double _most_recent_gc_time;
  double _most_recent_gcu;
  double _most_recent_mutator_time;
  double _most_recent_mu;

  // These variables hold recent snapshots of cumulative quantities that are used for reporting
  // periodic consumption of CPU time by GC and mutator threads.
  double _most_recent_periodic_time_stamp;
  double _most_recent_periodic_gc_time;
  double _most_recent_periodic_mutator_time;

  size_t _most_recent_gcid;
  uint _active_processors;

  bool _most_recent_is_full;

  ShenandoahMmuTask* _mmu_periodic_task;
  TruncatedSeq _mmu_average;

  void update_utilization(ShenandoahGeneration* generation, size_t gcid, const char* msg);
  static void fetch_cpu_times(double &gc_time, double &mutator_time);

public:
  explicit ShenandoahMmuTracker();
  ~ShenandoahMmuTracker();

  // This enrolls the periodic task after everything is initialized.
  void initialize();

  // At completion of each GC cycle (not including interrupted cycles), we invoke one of the following to record the
  // GC utilization during this cycle.  Incremental efforts spent in an interrupted GC cycle will be accumulated into
  // the CPU time reports for the subsequent completed [degenerated or full] GC cycle.
  //
  // We may redundantly record degen and full in the case that a degen upgrades to full.  When this happens, we will invoke
  // both record_full() and record_degenerated() with the same value of gcid.  record_full() is called first and the log
  // reports such a cycle as a FULL cycle.
  void record_young(ShenandoahGeneration* generation, size_t gcid);
  void record_global(ShenandoahGeneration* generation, size_t gcid);
  void record_bootstrap(ShenandoahGeneration* generation, size_t gcid, bool has_old_candidates);
  void record_old_marking_increment(ShenandoahGeneration* generation, size_t gcid, bool old_marking_done, bool has_old_candidates);
  void record_mixed(ShenandoahGeneration* generation, size_t gcid, bool is_mixed_done);
  void record_full(ShenandoahGeneration* generation, size_t gcid);
  void record_degenerated(ShenandoahGeneration* generation, size_t gcid, bool is_old_boostrap, bool is_mixed_done);

  // This is called by the periodic task timer. The interval is defined by
  // GCPauseIntervalMillis and defaults to 5 seconds. This method computes
  // the MMU over the elapsed interval and records it in a running average.
  void report();
};

class ShenandoahGenerationSizer {
private:
  enum SizerKind {
    SizerDefaults,
    SizerNewSizeOnly,
    SizerMaxNewSizeOnly,
    SizerMaxAndNewSize,
    SizerNewRatio
  };
  SizerKind _sizer_kind;

  size_t _min_desired_young_regions;
  size_t _max_desired_young_regions;

  static size_t calculate_min_young_regions(size_t heap_region_count);
  static size_t calculate_max_young_regions(size_t heap_region_count);

  // Update the given values for minimum and maximum young gen length in regions
  // given the number of heap regions depending on the kind of sizing algorithm.
  void recalculate_min_max_young_length(size_t heap_region_count);

public:
  ShenandoahGenerationSizer();

  // Calculate the maximum length of the young gen given the number of regions
  // depending on the sizing algorithm.
  void heap_size_changed(size_t heap_size);

  // Minimum size of young generation in bytes as multiple of region size.
  size_t min_young_size() const;
  size_t min_young_regions() const {
    return _min_desired_young_regions;
  }

  // Maximum size of young generation in bytes as multiple of region size.
  size_t max_young_size() const;
  size_t max_young_regions() const {
    return _max_desired_young_regions;
  }

  bool transfer_to_young(size_t regions) const;
  bool transfer_to_old(size_t regions) const;

  // force transfer is used when we promote humongous objects.  May violate min/max limits on generation sizes
  void force_transfer_to_old(size_t regions) const;
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHMMUTRACKER_HPP
