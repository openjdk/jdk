/*
 * Copyright (c) 2013, 2021, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLTHREAD_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLTHREAD_HPP

#include "gc/shared/gcCause.hpp"
#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shenandoah/shenandoahGC.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahPadding.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "runtime/task.hpp"
#include "utilities/ostream.hpp"

// Periodic task is useful for doing asynchronous things that do not require (heap) locks,
// or synchronization with other parts of collector. These could run even when ShenandoahConcurrentThread
// is busy driving the GC cycle.
class ShenandoahPeriodicTask : public PeriodicTask {
private:
  ShenandoahControlThread* _thread;
public:
  ShenandoahPeriodicTask(ShenandoahControlThread* thread) :
          PeriodicTask(100), _thread(thread) {}
  virtual void task();
};

// Periodic task to notify blocked paced waiters.
class ShenandoahPeriodicPacerNotify : public PeriodicTask {
public:
  ShenandoahPeriodicPacerNotify() : PeriodicTask(PeriodicTask::min_interval) {}
  virtual void task();
};

class ShenandoahControlThread: public ConcurrentGCThread {
  friend class VMStructs;

private:
  // While we could have a single lock for these, it may risk unblocking
  // GC waiters when alloc failure GC cycle finishes. We want instead
  // to make complete explicit cycle for demanding customers.
  Monitor _alloc_failure_waiters_lock;
  Monitor _gc_waiters_lock;
  Monitor _control_lock;
  Monitor _regulator_lock;
  ShenandoahPeriodicTask _periodic_task;
  ShenandoahPeriodicPacerNotify _periodic_pacer_notify_task;

public:
  typedef enum {
    none,
    concurrent_normal,
    stw_degenerated,
    stw_full,
    bootstrapping_old,
    servicing_old
  } GCMode;

  void run_service();
  void stop_service();

  size_t get_gc_id();

private:
  ShenandoahSharedFlag _allow_old_preemption;
  ShenandoahSharedFlag _preemption_requested;
  ShenandoahSharedFlag _gc_requested;
  ShenandoahSharedFlag _alloc_failure_gc;
  ShenandoahSharedFlag _graceful_shutdown;
  ShenandoahSharedFlag _do_counters_update;
  ShenandoahSharedFlag _force_counters_update;
  GCCause::Cause       _requested_gc_cause;
  ShenandoahGenerationType _requested_generation;
  ShenandoahGC::ShenandoahDegenPoint _degen_point;
  ShenandoahGeneration* _degen_generation;

  shenandoah_padding(0);
  volatile size_t _allocs_seen;
  shenandoah_padding(1);
  volatile size_t _gc_id;
  shenandoah_padding(2);
  volatile GCMode _mode;
  shenandoah_padding(3);

  // Returns true if the cycle has been cancelled or degenerated.
  bool check_cancellation_or_degen(ShenandoahGC::ShenandoahDegenPoint point);

  // Returns true if the old generation marking completed (i.e., final mark executed for old generation).
  bool resume_concurrent_old_cycle(ShenandoahGeneration* generation, GCCause::Cause cause);
  void service_concurrent_cycle(ShenandoahGeneration* generation, GCCause::Cause cause, bool reset_old_bitmap_specially);
  void service_stw_full_cycle(GCCause::Cause cause);

  // Return true if degenerated cycle finishes normally.  Return false if the degenerated cycle transformed itself
  // into a full GC.
  bool service_stw_degenerated_cycle(GCCause::Cause cause, ShenandoahGC::ShenandoahDegenPoint point);
  void service_uncommit(double shrink_before, size_t shrink_until);

  // Return true if setting the flag which indicates allocation failure succeeds.
  bool try_set_alloc_failure_gc();
  // Notify threads waiting for GC to complete.
  void notify_alloc_failure_waiters();
  // True if allocation failure flag has been set.
  bool is_alloc_failure_gc();

  void reset_gc_id();
  void update_gc_id();

  void notify_gc_waiters();

  // Handle GC request.
  // Blocks until GC is over.
  void handle_requested_gc(GCCause::Cause cause);

  bool is_explicit_gc(GCCause::Cause cause) const;
  bool is_implicit_gc(GCCause::Cause cause) const;

  // Returns true if the old generation marking was interrupted to allow a young cycle.
  bool preempt_old_marking(ShenandoahGenerationType generation);

  // Returns true if the soft maximum heap has been changed using management APIs.
  bool check_soft_max_changed() const;

  void process_phase_timings(const ShenandoahHeap* heap);

public:
  // Constructor
  ShenandoahControlThread();
  ~ShenandoahControlThread();

  // Handle allocation failure from normal allocation.
  // Blocks until memory is available.
  void handle_alloc_failure(ShenandoahAllocRequest& req);

  // Handle allocation failure from evacuation path.
  // Optionally blocks while collector is handling the failure.
  void handle_alloc_failure_evac(size_t words);

  void request_gc(GCCause::Cause cause);
  // Return true if the request to start a concurrent GC for the given generation succeeded.
  bool request_concurrent_gc(ShenandoahGenerationType generation);

  void handle_counters_update();
  void handle_force_counters_update();
  void set_forced_counters_update(bool value);

  void notify_heap_changed();

  void pacing_notify_alloc(size_t words);

  void start();
  void prepare_for_graceful_shutdown();
  bool in_graceful_shutdown();

  void service_concurrent_normal_cycle(ShenandoahHeap* heap,
                                       const ShenandoahGenerationType generation,
                                       GCCause::Cause cause);

  void service_concurrent_old_cycle(ShenandoahHeap* heap,
                                    GCCause::Cause &cause);

  void set_gc_mode(GCMode new_mode);
  GCMode gc_mode() {
    return _mode;
  }

  static ShenandoahGenerationType select_global_generation();

 private:
  static const char* gc_mode_name(GCMode mode);
  void notify_control_thread();

  void service_concurrent_cycle(ShenandoahHeap* heap,
                                ShenandoahGeneration* generation,
                                GCCause::Cause &cause,
                                bool do_old_gc_bootstrap);

};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLTHREAD_HPP
