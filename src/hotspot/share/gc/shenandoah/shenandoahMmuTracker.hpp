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

  double _generational_reference_time_s;
  double _process_reference_time_s;
  double _collector_reference_time_s;

  ShenandoahMmuTask* _mmu_periodic_task;
  TruncatedSeq _mmu_average;

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

  double average() {
    return _mmu_average.davg();
  }
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

  // False when using a fixed young generation size due to command-line options,
  // true otherwise.
  bool _use_adaptive_sizing;

  size_t _min_desired_young_size;
  size_t _max_desired_young_size;

  double _resize_increment;
  ShenandoahMmuTracker* _mmu_tracker;

  static size_t calculate_min_size(size_t heap_size);
  static size_t calculate_max_size(size_t heap_size);

  // Update the given values for minimum and maximum young gen length in regions
  // given the number of heap regions depending on the kind of sizing algorithm.
  void recalculate_min_max_young_length(size_t heap_size);

  // This will attempt to transfer capacity from one generation to the other. It
  // returns true if a transfer is made, false otherwise.
  bool transfer_capacity(ShenandoahGeneration* from, ShenandoahGeneration* to);

  // These two methods are responsible for enforcing the minimum and maximum
  // constraints for the size of the generations.
  size_t adjust_transfer_from_young(ShenandoahGeneration* from, size_t bytes_to_transfer) const;
  size_t adjust_transfer_to_young(ShenandoahGeneration* to, size_t bytes_to_transfer) const;

public:
  ShenandoahGenerationSizer(ShenandoahMmuTracker* mmu_tracker);

  // Calculate the maximum length of the young gen given the number of regions
  // depending on the sizing algorithm.
  void heap_size_changed(size_t heap_size);

  size_t min_young_size() const {
    return _min_desired_young_size;
  }
  size_t max_young_size() const {
    return _max_desired_young_size;
  }

  bool use_adaptive_sizing() const {
    return _use_adaptive_sizing;
  }

  // This is invoked at the end of a collection. This happens on a safepoint
  // to avoid any races with allocators (and to avoid interfering with
  // allocators by taking the heap lock). The amount of capacity to move
  // from one generation to another is controlled by YoungGenerationSizeIncrement
  // and defaults to 20% of the available capacity of the donor generation.
  // The minimum and maximum sizes of the young generation are controlled by
  // ShenandoahMinYoungPercentage and ShenandoahMaxYoungPercentage, respectively.
  // The method returns true when an adjustment is made, false otherwise.
  bool adjust_generation_sizes();
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHMMUTRACKER_HPP
