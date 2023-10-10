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
  explicit ShenandoahMmuTask(ShenandoahMmuTracker* mmu_tracker) :
    PeriodicTask(GCPauseIntervalMillis), _mmu_tracker(mmu_tracker) {}

  void task() override {
    _mmu_tracker->report();
  }
};

class ThreadTimeAccumulator : public ThreadClosure {
 public:
  size_t total_time;
  ThreadTimeAccumulator() : total_time(0) {}
  void do_thread(Thread* thread) override {
    total_time += os::thread_cpu_time(thread);
  }
};

ShenandoahMmuTracker::ShenandoahMmuTracker() :
    _most_recent_timestamp(0.0),
    _most_recent_gc_time(0.0),
    _most_recent_gcu(0.0),
    _most_recent_mutator_time(0.0),
    _most_recent_mu(0.0),
    _most_recent_periodic_time_stamp(0.0),
    _most_recent_periodic_gc_time(0.0),
    _most_recent_periodic_mutator_time(0.0),
    _mmu_periodic_task(new ShenandoahMmuTask(this)) {
}

ShenandoahMmuTracker::~ShenandoahMmuTracker() {
  _mmu_periodic_task->disenroll();
  delete _mmu_periodic_task;
}

void ShenandoahMmuTracker::fetch_cpu_times(double &gc_time, double &mutator_time) {
  ThreadTimeAccumulator cl;
  // We include only the gc threads because those are the only threads
  // we are responsible for.
  ShenandoahHeap::heap()->gc_threads_do(&cl);
  double most_recent_gc_thread_time = double(cl.total_time) / NANOSECS_PER_SEC;
  gc_time = most_recent_gc_thread_time;

  double process_real_time(0.0), process_user_time(0.0), process_system_time(0.0);
  bool valid = os::getTimesSecs(&process_real_time, &process_user_time, &process_system_time);
  assert(valid, "don't know why this would not be valid");
  mutator_time =(process_user_time + process_system_time) - most_recent_gc_thread_time;
}

void ShenandoahMmuTracker::update_utilization(ShenandoahGeneration* generation, size_t gcid, const char *msg) {
  double current = os::elapsedTime();
  _most_recent_gcid = gcid;
  _most_recent_is_full = false;

  if (gcid == 0) {
    fetch_cpu_times(_most_recent_gc_time, _most_recent_mutator_time);

    _most_recent_timestamp = current;
  } else {
    double gc_cycle_period = current - _most_recent_timestamp;
    _most_recent_timestamp = current;

    double gc_thread_time, mutator_thread_time;
    fetch_cpu_times(gc_thread_time, mutator_thread_time);
    double gc_time = gc_thread_time - _most_recent_gc_time;
    _most_recent_gc_time = gc_thread_time;
    _most_recent_gcu = gc_time / (_active_processors * gc_cycle_period);
    double mutator_time = mutator_thread_time - _most_recent_mutator_time;
    _most_recent_mutator_time = mutator_thread_time;
    _most_recent_mu = mutator_time / (_active_processors * gc_cycle_period);
    log_info(gc, ergo)("At end of %s: GCU: %.1f%%, MU: %.1f%% during period of %.3fs",
                       msg, _most_recent_gcu * 100, _most_recent_mu * 100, gc_cycle_period);
  }
}

void ShenandoahMmuTracker::record_young(ShenandoahGeneration* generation, size_t gcid) {
  update_utilization(generation, gcid, "Concurrent Young GC");
}

void ShenandoahMmuTracker::record_global(ShenandoahGeneration* generation, size_t gcid) {
  update_utilization(generation, gcid, "Concurrent Global GC");
}

void ShenandoahMmuTracker::record_bootstrap(ShenandoahGeneration* generation, size_t gcid, bool candidates_for_mixed) {
  // Not likely that this will represent an "ideal" GCU, but doesn't hurt to try
  update_utilization(generation, gcid, "Concurrent Bootstrap GC");
}

void ShenandoahMmuTracker::record_old_marking_increment(ShenandoahGeneration* generation, size_t gcid, bool old_marking_done,
                                                        bool has_old_candidates) {
  // No special processing for old marking
  double now = os::elapsedTime();
  double duration = now - _most_recent_timestamp;

  double gc_time, mutator_time;
  fetch_cpu_times(gc_time, mutator_time);
  double gcu = (gc_time - _most_recent_gc_time) / duration;
  double mu = (mutator_time - _most_recent_mutator_time) / duration;
  log_info(gc, ergo)("At end of %s: GCU: %.1f%%, MU: %.1f%% for duration %.3fs (totals to be subsumed in next gc report)",
                     old_marking_done? "last OLD marking increment": "OLD marking increment",
                     gcu * 100, mu * 100, duration);
}

void ShenandoahMmuTracker::record_mixed(ShenandoahGeneration* generation, size_t gcid, bool is_mixed_done) {
  update_utilization(generation, gcid, "Mixed Concurrent GC");
}

void ShenandoahMmuTracker::record_degenerated(ShenandoahGeneration* generation,
                                              size_t gcid, bool is_old_bootstrap, bool is_mixed_done) {
  if ((gcid == _most_recent_gcid) && _most_recent_is_full) {
    // Do nothing.  This is a redundant recording for the full gc that just completed.
    // TODO: avoid making the call to record_degenerated() in the case that this degenerated upgraded to full gc.
  } else if (is_old_bootstrap) {
    update_utilization(generation, gcid, "Degenerated Bootstrap Old GC");
  } else {
    update_utilization(generation, gcid, "Degenerated Young GC");
  }
}

void ShenandoahMmuTracker::record_full(ShenandoahGeneration* generation, size_t gcid) {
  update_utilization(generation, gcid, "Full GC");
  _most_recent_is_full = true;
}

void ShenandoahMmuTracker::report() {
  // This is only called by the periodic thread.
  double current = os::elapsedTime();
  double time_delta = current - _most_recent_periodic_time_stamp;
  _most_recent_periodic_time_stamp = current;

  double gc_time, mutator_time;
  fetch_cpu_times(gc_time, mutator_time);

  double gc_delta = gc_time - _most_recent_periodic_gc_time;
  _most_recent_periodic_gc_time = gc_time;

  double mutator_delta = mutator_time - _most_recent_periodic_mutator_time;
  _most_recent_periodic_mutator_time = mutator_time;

  double mu = mutator_delta / (_active_processors * time_delta);
  double gcu = gc_delta / (_active_processors * time_delta);
  log_info(gc)("Periodic Sample: GCU = %.3f%%, MU = %.3f%% during most recent %.1fs", gcu * 100, mu * 100, time_delta);
}

void ShenandoahMmuTracker::initialize() {
  // initialize static data
  _active_processors = os::initial_active_processor_count();

  _most_recent_periodic_time_stamp = os::elapsedTime();
  fetch_cpu_times(_most_recent_periodic_gc_time, _most_recent_periodic_mutator_time);
  _mmu_periodic_task->enroll();
}

ShenandoahGenerationSizer::ShenandoahGenerationSizer()
  : _sizer_kind(SizerDefaults),
    _min_desired_young_regions(0),
    _max_desired_young_regions(0) {

  if (FLAG_IS_CMDLINE(NewRatio)) {
    if (FLAG_IS_CMDLINE(NewSize) || FLAG_IS_CMDLINE(MaxNewSize)) {
      log_warning(gc, ergo)("-XX:NewSize and -XX:MaxNewSize override -XX:NewRatio");
    } else {
      _sizer_kind = SizerNewRatio;
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
    _min_desired_young_regions = MAX2(uint(NewSize / ShenandoahHeapRegion::region_size_bytes()), 1U);
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      _max_desired_young_regions = MAX2(uint(MaxNewSize / ShenandoahHeapRegion::region_size_bytes()), 1U);
      _sizer_kind = SizerMaxAndNewSize;
    } else {
      _sizer_kind = SizerNewSizeOnly;
    }
  } else if (FLAG_IS_CMDLINE(MaxNewSize)) {
    _max_desired_young_regions = MAX2(uint(MaxNewSize / ShenandoahHeapRegion::region_size_bytes()), 1U);
    _sizer_kind = SizerMaxNewSizeOnly;
  }
}

size_t ShenandoahGenerationSizer::calculate_min_young_regions(size_t heap_region_count) {
  size_t min_young_regions = (heap_region_count * ShenandoahMinYoungPercentage) / 100;
  return MAX2(min_young_regions, (size_t) 1U);
}

size_t ShenandoahGenerationSizer::calculate_max_young_regions(size_t heap_region_count) {
  size_t max_young_regions = (heap_region_count * ShenandoahMaxYoungPercentage) / 100;
  return MAX2(max_young_regions, (size_t) 1U);
}

void ShenandoahGenerationSizer::recalculate_min_max_young_length(size_t heap_region_count) {
  assert(heap_region_count > 0, "Heap must be initialized");

  switch (_sizer_kind) {
    case SizerDefaults:
      _min_desired_young_regions = calculate_min_young_regions(heap_region_count);
      _max_desired_young_regions = calculate_max_young_regions(heap_region_count);
      break;
    case SizerNewSizeOnly:
      _max_desired_young_regions = calculate_max_young_regions(heap_region_count);
      _max_desired_young_regions = MAX2(_min_desired_young_regions, _max_desired_young_regions);
      break;
    case SizerMaxNewSizeOnly:
      _min_desired_young_regions = calculate_min_young_regions(heap_region_count);
      _min_desired_young_regions = MIN2(_min_desired_young_regions, _max_desired_young_regions);
      break;
    case SizerMaxAndNewSize:
      // Do nothing. Values set on the command line, don't update them at runtime.
      break;
    case SizerNewRatio:
      _min_desired_young_regions = MAX2(uint(heap_region_count / (NewRatio + 1)), 1U);
      _max_desired_young_regions = _min_desired_young_regions;
      break;
    default:
      ShouldNotReachHere();
  }

  assert(_min_desired_young_regions <= _max_desired_young_regions, "Invalid min/max young gen size values");
}

void ShenandoahGenerationSizer::heap_size_changed(size_t heap_size) {
  recalculate_min_max_young_length(heap_size / ShenandoahHeapRegion::region_size_bytes());
}

// Returns true iff transfer is successful
bool ShenandoahGenerationSizer::transfer_to_old(size_t regions) const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahGeneration* old_gen = heap->old_generation();
  ShenandoahGeneration* young_gen = heap->young_generation();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t bytes_to_transfer = regions * region_size_bytes;

  if (young_gen->free_unaffiliated_regions() < regions) {
    return false;
  } else if (old_gen->max_capacity() + bytes_to_transfer > heap->max_size_for(old_gen)) {
    return false;
  } else if (young_gen->max_capacity() - bytes_to_transfer < heap->min_size_for(young_gen)) {
    return false;
  } else {
    young_gen->decrease_capacity(bytes_to_transfer);
    old_gen->increase_capacity(bytes_to_transfer);
    size_t new_size = old_gen->max_capacity();
    log_info(gc)("Transfer " SIZE_FORMAT " region(s) from %s to %s, yielding increased size: " SIZE_FORMAT "%s",
                 regions, young_gen->name(), old_gen->name(),
                 byte_size_in_proper_unit(new_size), proper_unit_for_byte_size(new_size));
    return true;
  }
}

// This is used when promoting humongous or highly utilized regular regions in place.  It is not required in this situation
// that the transferred regions be unaffiliated.
void ShenandoahGenerationSizer::force_transfer_to_old(size_t regions) const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahGeneration* old_gen = heap->old_generation();
  ShenandoahGeneration* young_gen = heap->young_generation();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t bytes_to_transfer = regions * region_size_bytes;

  young_gen->decrease_capacity(bytes_to_transfer);
  old_gen->increase_capacity(bytes_to_transfer);
  size_t new_size = old_gen->max_capacity();
  log_info(gc)("Forcing transfer of " SIZE_FORMAT " region(s) from %s to %s, yielding increased size: " SIZE_FORMAT "%s",
               regions, young_gen->name(), old_gen->name(),
               byte_size_in_proper_unit(new_size), proper_unit_for_byte_size(new_size));
}


bool ShenandoahGenerationSizer::transfer_to_young(size_t regions) const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahGeneration* old_gen = heap->old_generation();
  ShenandoahGeneration* young_gen = heap->young_generation();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t bytes_to_transfer = regions * region_size_bytes;

  if (old_gen->free_unaffiliated_regions() < regions) {
    return false;
  } else if (young_gen->max_capacity() + bytes_to_transfer > heap->max_size_for(young_gen)) {
    return false;
  } else if (old_gen->max_capacity() - bytes_to_transfer < heap->min_size_for(old_gen)) {
    return false;
  } else {
    old_gen->decrease_capacity(bytes_to_transfer);
    young_gen->increase_capacity(bytes_to_transfer);
    size_t new_size = young_gen->max_capacity();
    log_info(gc)("Transfer " SIZE_FORMAT " region(s) from %s to %s, yielding increased size: " SIZE_FORMAT "%s",
                 regions, old_gen->name(), young_gen->name(),
                 byte_size_in_proper_unit(new_size), proper_unit_for_byte_size(new_size));
    return true;
  }
}

size_t ShenandoahGenerationSizer::min_young_size() const {
  return min_young_regions() * ShenandoahHeapRegion::region_size_bytes();
}

size_t ShenandoahGenerationSizer::max_young_size() const {
  return max_young_regions() * ShenandoahHeapRegion::region_size_bytes();
}
