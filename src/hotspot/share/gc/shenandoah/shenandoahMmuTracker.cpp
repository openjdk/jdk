/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMmuTracker.hpp"
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
    assert(!thread->has_terminated(), "Cannot get cpu time for terminated thread: %zu", thread->osthread()->thread_id_for_printing());
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

void ShenandoahMmuTracker::update_utilization(size_t gcid, const char* msg) {
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

void ShenandoahMmuTracker::record_young(size_t gcid) {
  update_utilization(gcid, "Concurrent Young GC");
}

void ShenandoahMmuTracker::record_global(size_t gcid) {
  update_utilization(gcid, "Concurrent Global GC");
}

void ShenandoahMmuTracker::record_bootstrap(size_t gcid) {
  // Not likely that this will represent an "ideal" GCU, but doesn't hurt to try
  update_utilization(gcid, "Concurrent Bootstrap GC");
}

void ShenandoahMmuTracker::record_old_marking_increment(bool old_marking_done) {
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

void ShenandoahMmuTracker::record_mixed(size_t gcid) {
  update_utilization(gcid, "Mixed Concurrent GC");
}

void ShenandoahMmuTracker::record_degenerated(size_t gcid, bool is_old_bootstrap) {
  if ((gcid == _most_recent_gcid) && _most_recent_is_full) {
    // Do nothing.  This is a redundant recording for the full gc that just completed.
  } else if (is_old_bootstrap) {
    update_utilization(gcid, "Degenerated Bootstrap Old GC");
  } else {
    update_utilization(gcid, "Degenerated Young GC");
  }
}

void ShenandoahMmuTracker::record_full(size_t gcid) {
  update_utilization(gcid, "Full GC");
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
  log_debug(gc)("Periodic Sample: GCU = %.3f%%, MU = %.3f%% during most recent %.1fs", gcu * 100, mu * 100, time_delta);
}

void ShenandoahMmuTracker::stop() const {
  _mmu_periodic_task->disenroll();
}

void ShenandoahMmuTracker::initialize() {
  // initialize static data
  _active_processors = os::initial_active_processor_count();

  _most_recent_periodic_time_stamp = os::elapsedTime();
  fetch_cpu_times(_most_recent_periodic_gc_time, _most_recent_periodic_mutator_time);
  _mmu_periodic_task->enroll();
}
