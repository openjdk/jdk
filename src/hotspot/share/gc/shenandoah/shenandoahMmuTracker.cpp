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
#include "precompiled.hpp"

#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahMmuTracker.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "logging/log.hpp"
#include "runtime/os.hpp"
#include "runtime/task.hpp"


class ShenandoahMmuTask : public PeriodicTask {
  ShenandoahMmuTracker* _mmu_tracker;
public:
  ShenandoahMmuTask(ShenandoahMmuTracker* mmu_tracker) :
    PeriodicTask(GCPauseIntervalMillis), _mmu_tracker(mmu_tracker) {}

  virtual void task() override {
    _mmu_tracker->report();
  }
};

class ThreadTimeAccumulator : public ThreadClosure {
 public:
  size_t total_time;
  ThreadTimeAccumulator() : total_time(0) {}
  virtual void do_thread(Thread* thread) override {
    total_time += os::thread_cpu_time(thread);
  }
};

double ShenandoahMmuTracker::gc_thread_time_seconds() {
  ThreadTimeAccumulator cl;
  // We include only the gc threads because those are the only threads
  // we are responsible for.
  ShenandoahHeap::heap()->gc_threads_do(&cl);
  return double(cl.total_time) / NANOSECS_PER_SEC;
}

double ShenandoahMmuTracker::process_time_seconds() {
  double process_real_time(0.0), process_user_time(0.0), process_system_time(0.0);
  bool valid = os::getTimesSecs(&process_real_time, &process_user_time, &process_system_time);
  if (valid) {
    return process_user_time + process_system_time;
  }
  return 0.0;
}

ShenandoahMmuTracker::ShenandoahMmuTracker() :
    _generational_reference_time_s(0.0),
    _process_reference_time_s(0.0),
    _mmu_periodic_task(new ShenandoahMmuTask(this)),
    _mmu_average(10, ShenandoahAdaptiveDecayFactor) {
}

ShenandoahMmuTracker::~ShenandoahMmuTracker() {
  _mmu_periodic_task->disenroll();
  delete _mmu_periodic_task;
}

void ShenandoahMmuTracker::record(ShenandoahGeneration* generation) {
  shenandoah_assert_control_or_vm_thread();
  double collector_time_s = gc_thread_time_seconds();
  double elapsed_gc_time_s = collector_time_s - _generational_reference_time_s;
  generation->add_collection_time(elapsed_gc_time_s);
  _generational_reference_time_s = collector_time_s;
}

void ShenandoahMmuTracker::report() {
  // This is only called by the periodic thread.
  double process_time_s = process_time_seconds();
  double elapsed_process_time_s = process_time_s - _process_reference_time_s;
  if (elapsed_process_time_s <= 0.01) {
    // No cpu time for this interval?
    return;
  }

  _process_reference_time_s = process_time_s;
  double collector_time_s = gc_thread_time_seconds();
  double elapsed_collector_time_s = collector_time_s - _collector_reference_time_s;
  _collector_reference_time_s = collector_time_s;
  double minimum_mutator_utilization = ((elapsed_process_time_s - elapsed_collector_time_s) / elapsed_process_time_s) * 100;
  _mmu_average.add(minimum_mutator_utilization);
  log_info(gc)("Average MMU = %.3f", _mmu_average.davg());
}

void ShenandoahMmuTracker::initialize() {
  _process_reference_time_s = process_time_seconds();
  _generational_reference_time_s = gc_thread_time_seconds();
  _collector_reference_time_s = _generational_reference_time_s;
  _mmu_periodic_task->enroll();
}

ShenandoahGenerationSizer::ShenandoahGenerationSizer(ShenandoahMmuTracker* mmu_tracker)
  : _sizer_kind(SizerDefaults),
    _use_adaptive_sizing(true),
    _min_desired_young_size(0),
    _max_desired_young_size(0),
    _resize_increment(YoungGenerationSizeIncrement / 100.0),
    _mmu_tracker(mmu_tracker) {

  if (FLAG_IS_CMDLINE(NewRatio)) {
    if (FLAG_IS_CMDLINE(NewSize) || FLAG_IS_CMDLINE(MaxNewSize)) {
      log_warning(gc, ergo)("-XX:NewSize and -XX:MaxNewSize override -XX:NewRatio");
    } else {
      _sizer_kind = SizerNewRatio;
      _use_adaptive_sizing = false;
      return;
    }
  }

  if (NewSize > MaxNewSize) {
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      log_warning(gc, ergo)("NewSize (" SIZE_FORMAT "k) is greater than the MaxNewSize (" SIZE_FORMAT "k). "
                            "A new max generation size of " SIZE_FORMAT "k will be used.",
                            NewSize/K, MaxNewSize/K, NewSize/K);
    }
    FLAG_SET_ERGO(MaxNewSize, NewSize);
  }

  if (FLAG_IS_CMDLINE(NewSize)) {
    _min_desired_young_size = MAX2(NewSize, ShenandoahHeapRegion::region_size_bytes());
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      _max_desired_young_size = MAX2(MaxNewSize, ShenandoahHeapRegion::region_size_bytes());
      _sizer_kind = SizerMaxAndNewSize;
      _use_adaptive_sizing = _min_desired_young_size != _max_desired_young_size;
    } else {
      _sizer_kind = SizerNewSizeOnly;
    }
  } else if (FLAG_IS_CMDLINE(MaxNewSize)) {
    _max_desired_young_size = MAX2(MaxNewSize, ShenandoahHeapRegion::region_size_bytes());
    _sizer_kind = SizerMaxNewSizeOnly;
  }
}

size_t ShenandoahGenerationSizer::calculate_min_size(size_t heap_size) {
  size_t default_value = (heap_size * ShenandoahMinYoungPercentage) / 100;
  return MAX2(ShenandoahHeapRegion::region_size_bytes(), default_value);
}

size_t ShenandoahGenerationSizer::calculate_max_size(size_t heap_size) {
  size_t default_value = (heap_size * ShenandoahMaxYoungPercentage) / 100;
  return MAX2(ShenandoahHeapRegion::region_size_bytes(), default_value);
}

void ShenandoahGenerationSizer::recalculate_min_max_young_length(size_t heap_size) {
  assert(heap_size > 0, "Heap must be initialized");

  switch (_sizer_kind) {
    case SizerDefaults:
      _min_desired_young_size = calculate_min_size(heap_size);
      _max_desired_young_size = calculate_max_size(heap_size);
      break;
    case SizerNewSizeOnly:
      _max_desired_young_size = calculate_max_size(heap_size);
      _max_desired_young_size = MAX2(_min_desired_young_size, _max_desired_young_size);
      break;
    case SizerMaxNewSizeOnly:
      _min_desired_young_size = calculate_min_size(heap_size);
      _min_desired_young_size = MIN2(_min_desired_young_size, _max_desired_young_size);
      break;
    case SizerMaxAndNewSize:
      // Do nothing. Values set on the command line, don't update them at runtime.
      break;
    case SizerNewRatio:
      _min_desired_young_size = MAX2((heap_size / (NewRatio + 1)), ShenandoahHeapRegion::region_size_bytes());
      _max_desired_young_size = _min_desired_young_size;
      break;
    default:
      ShouldNotReachHere();
  }

  assert(_min_desired_young_size <= _max_desired_young_size, "Invalid min/max young gen size values");
}

void ShenandoahGenerationSizer::heap_size_changed(size_t heap_size) {
  recalculate_min_max_young_length(heap_size);
}

bool ShenandoahGenerationSizer::adjust_generation_sizes() {
  shenandoah_assert_generational();
  if (!use_adaptive_sizing()) {
    return false;
  }

  if (_mmu_tracker->average() >= double(GCTimeRatio)) {
    return false;
  }

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahOldGeneration *old = heap->old_generation();
  ShenandoahYoungGeneration *young = heap->young_generation();
  ShenandoahGeneration *global = heap->global_generation();
  double old_time_s = old->reset_collection_time();
  double young_time_s = young->reset_collection_time();
  double global_time_s = global->reset_collection_time();

  const double transfer_threshold = 3.0;
  double delta = young_time_s - old_time_s;

  log_info(gc)("Thread Usr+Sys YOUNG = %.3f, OLD = %.3f, GLOBAL = %.3f", young_time_s, old_time_s, global_time_s);

  if (abs(delta) <= transfer_threshold) {
    log_info(gc, ergo)("Difference (%.3f) for thread utilization for each generation is under threshold (%.3f)", abs(delta), transfer_threshold);
    return false;
  }

  if (delta > 0) {
    // young is busier than old, increase size of young to raise MMU
    return transfer_capacity(old, young);
  } else {
    // old is busier than young, increase size of old to raise MMU
    return transfer_capacity(young, old);
  }
}

bool ShenandoahGenerationSizer::transfer_capacity(ShenandoahGeneration* from, ShenandoahGeneration* to) {
  shenandoah_assert_heaplocked_or_safepoint();

  size_t available_regions = from->free_unaffiliated_regions();
  if (available_regions <= 0) {
    log_info(gc)("%s has no regions available for transfer to %s", from->name(), to->name());
    return false;
  }

  size_t regions_to_transfer = MAX2(1u, uint(double(available_regions) * _resize_increment));
  size_t bytes_to_transfer = regions_to_transfer * ShenandoahHeapRegion::region_size_bytes();
  if (from->generation_mode() == YOUNG) {
    bytes_to_transfer = adjust_transfer_from_young(from, bytes_to_transfer);
  } else {
    bytes_to_transfer = adjust_transfer_to_young(to, bytes_to_transfer);
  }

  if (bytes_to_transfer == 0) {
    log_debug(gc)("No capacity available to transfer from: %s (" SIZE_FORMAT "%s) to: %s (" SIZE_FORMAT "%s)",
                  from->name(), byte_size_in_proper_unit(from->max_capacity()), proper_unit_for_byte_size(from->max_capacity()),
                  to->name(), byte_size_in_proper_unit(to->max_capacity()), proper_unit_for_byte_size(to->max_capacity()));
    return false;
  }

  assert(bytes_to_transfer <= regions_to_transfer * ShenandoahHeapRegion::region_size_bytes(), "Cannot transfer more than available in free regions.");
  log_info(gc)("Transfer " SIZE_FORMAT "%s from %s to %s", byte_size_in_proper_unit(bytes_to_transfer),
               proper_unit_for_byte_size(bytes_to_transfer), from->name(), to->name());
  from->decrease_capacity(bytes_to_transfer);
  to->increase_capacity(bytes_to_transfer);
  return true;
}

size_t round_down_to_multiple_of_region_size(size_t bytes) {
  return (bytes / ShenandoahHeapRegion::region_size_bytes()) * ShenandoahHeapRegion::region_size_bytes();
}

size_t ShenandoahGenerationSizer::adjust_transfer_from_young(ShenandoahGeneration* from, size_t bytes_to_transfer) const {
  assert(from->generation_mode() == YOUNG, "Expect to transfer from young");
  size_t new_young_size = from->max_capacity() - bytes_to_transfer;
  size_t minimum_size = min_young_size();
  // Check that we are not going to violate the minimum size constraint.
  if (new_young_size < minimum_size) {
    assert(minimum_size <= from->max_capacity(), "Young is under minimum capacity.");
    // If the transfer violates the minimum size and there is still some capacity to transfer,
    // adjust the transfer to take the size to the minimum. Note that this may be zero.
    bytes_to_transfer = round_down_to_multiple_of_region_size(from->max_capacity() - minimum_size);
  }
  return bytes_to_transfer;
}

size_t ShenandoahGenerationSizer::adjust_transfer_to_young(ShenandoahGeneration* to, size_t bytes_to_transfer) const {
  assert(to->generation_mode() == YOUNG, "Can only transfer between young and old.");
  size_t new_young_size = to->max_capacity() + bytes_to_transfer;
  size_t maximum_size = max_young_size();
  // Check that we are not going to violate the maximum size constraint.
  if (new_young_size > maximum_size) {
    assert(maximum_size >= to->max_capacity(), "Young is over maximum capacity");
    // If the transfer violates the maximum size and there is still some capacity to transfer,
    // adjust the transfer to take the size to the maximum. Note that this may be zero.
    bytes_to_transfer = round_down_to_multiple_of_region_size(maximum_size - to->max_capacity());
  }
  return bytes_to_transfer;
}
