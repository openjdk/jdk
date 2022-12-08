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

#include "gc/shenandoah/shenandoahMmuTracker.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "runtime/os.hpp"
#include "runtime/task.hpp"
#include "logging/log.hpp"


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
  ShenandoahHeap::heap()->gc_threads_do(&cl);
  // Include VM thread? Compiler threads? or no - because there
  // is nothing the collector can do about those threads.
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
  _initial_collector_time_s(0.0),
  _initial_process_time_s(0.0),
  _resize_increment(YoungGenerationSizeIncrement / 100.0),
  _mmu_periodic_task(new ShenandoahMmuTask(this)),
  _mmu_average(10, ShenandoahAdaptiveDecayFactor) {
}

ShenandoahMmuTracker::~ShenandoahMmuTracker() {
  _mmu_periodic_task->disenroll();
  delete _mmu_periodic_task;
}

void ShenandoahMmuTracker::record(ShenandoahGeneration* generation) {
  // This is only called by the control thread.
  double collector_time_s = gc_thread_time_seconds();
  double elapsed_gc_time_s = collector_time_s - _initial_collector_time_s;
  generation->add_collection_time(elapsed_gc_time_s);
  _initial_collector_time_s = collector_time_s;
}

void ShenandoahMmuTracker::report() {
  // This is only called by the periodic thread.
  double process_time_s = process_time_seconds();
  double elapsed_process_time_s = process_time_s - _initial_process_time_s;
  _initial_process_time_s = process_time_s;
  double verify_time_s = gc_thread_time_seconds();
  double verify_elapsed = verify_time_s - _initial_verify_collector_time_s;
  _initial_verify_collector_time_s = verify_time_s;
  double verify_mmu = ((elapsed_process_time_s - verify_elapsed) / elapsed_process_time_s) * 100;
  _mmu_average.add(verify_mmu);
  log_info(gc)("Average MMU = %.3f", _mmu_average.davg());
}

bool ShenandoahMmuTracker::adjust_generation_sizes() {
  shenandoah_assert_generational();
  if (_mmu_average.davg() >= double(GCTimeRatio)) {
    return false;
  }

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahOldGeneration *old = heap->old_generation();
  double old_time_s = old->reset_collection_time();
  ShenandoahYoungGeneration *young = heap->young_generation();
  double young_time_s = young->reset_collection_time();
  ShenandoahGeneration *global = heap->global_generation();
  double global_time_s = global->reset_collection_time();

  log_info(gc)("Thread Usr+Sys YOUNG = %.3f, OLD = %.3f, GLOBAL = %.3f", young_time_s, old_time_s, global_time_s);

  if (old_time_s > young_time_s) {
    return transfer_capacity(young, old);
  } else {
    return transfer_capacity(old, young);
  }
}

size_t percentage_of_heap(size_t bytes) {
  size_t heap_capacity = ShenandoahHeap::heap()->max_capacity();
  assert(bytes < heap_capacity, "Must be less than total capacity");
  return size_t(100.0 * double(bytes) / double(heap_capacity));
}

bool ShenandoahMmuTracker::transfer_capacity(ShenandoahGeneration* from, ShenandoahGeneration* to) {
  shenandoah_assert_heaplocked_or_safepoint();

  size_t available_regions = from->free_unaffiliated_regions();
  if (available_regions <= 0) {
    log_info(gc)("%s has no regions available for transfer to %s", from->name(), to->name());
    return false;
  }

  size_t regions_to_transfer = MAX2(1UL, size_t(double(available_regions) * _resize_increment));
  size_t bytes_to_transfer = regions_to_transfer * ShenandoahHeapRegion::region_size_bytes();
  if (from->generation_mode() == YOUNG) {
    size_t new_young_size = from->max_capacity() - bytes_to_transfer;
    if (percentage_of_heap(new_young_size) < ShenandoahMinYoungPercentage) {
      ShenandoahHeap* heap = ShenandoahHeap::heap();
      size_t minimum_size = size_t(ShenandoahMinYoungPercentage / 100.0 * heap->max_capacity());
      if (from->max_capacity() > minimum_size) {
        bytes_to_transfer = from->max_capacity() - minimum_size;
      } else {
        log_info(gc)("Cannot transfer from young: " SIZE_FORMAT "%s, at minimum capacity: " SIZE_FORMAT "%s",
            byte_size_in_proper_unit(from->max_capacity()), proper_unit_for_byte_size(from->max_capacity()),
            byte_size_in_proper_unit(minimum_size), proper_unit_for_byte_size(minimum_size));
        return false;
      }
    }
  } else {
    assert(to->generation_mode() == YOUNG, "Can only transfer between young and old.");
    size_t new_young_size = to->max_capacity() + bytes_to_transfer;
    if (percentage_of_heap(new_young_size) > ShenandoahMaxYoungPercentage) {
      ShenandoahHeap* heap = ShenandoahHeap::heap();
      size_t maximum_size = size_t(ShenandoahMaxYoungPercentage / 100.0 * heap->max_capacity());
      if (maximum_size > to->max_capacity()) {
        bytes_to_transfer = maximum_size - to->max_capacity();
      } else {
        log_info(gc)("Cannot transfer to young: " SIZE_FORMAT "%s, at maximum capacity: " SIZE_FORMAT "%s",
            byte_size_in_proper_unit(to->max_capacity()), proper_unit_for_byte_size(to->max_capacity()),
            byte_size_in_proper_unit(maximum_size), proper_unit_for_byte_size(maximum_size));
        return false;
      }
    }
  }

  assert(bytes_to_transfer <= regions_to_transfer * ShenandoahHeapRegion::region_size_bytes(), "Cannot transfer more than available in free regions.");
  log_info(gc)("Transfer " SIZE_FORMAT "%s from %s to %s", byte_size_in_proper_unit(bytes_to_transfer),
               proper_unit_for_byte_size(bytes_to_transfer), from->name(), to->name());
  from->decrease_capacity(bytes_to_transfer);
  to->increase_capacity(bytes_to_transfer);
  return true;
}

void ShenandoahMmuTracker::initialize() {
  _initial_process_time_s = process_time_seconds();
  _initial_collector_time_s = gc_thread_time_seconds();
  _initial_verify_collector_time_s = _initial_collector_time_s;
  _mmu_periodic_task->enroll();
}