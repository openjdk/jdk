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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALCONTROLTHREAD_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALCONTROLTHREAD_HPP

#include "gc/shared/gcCause.hpp"
#include "gc/shenandoah/shenandoahController.hpp"
#include "gc/shenandoah/shenandoahGC.hpp"
#include "gc/shenandoah/shenandoahPadding.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"

class ShenandoahOldGeneration;
class ShenandoahGeneration;
class ShenandoahGenerationalHeap;
class ShenandoahHeap;

class ShenandoahGenerationalControlThread: public ShenandoahController {
  friend class VMStructs;

public:
  typedef enum {
    none,
    concurrent_normal,
    stw_degenerated,
    stw_full,
    bootstrapping_old,
    servicing_old,
    stopped
  } GCMode;

  class ShenandoahGCRequest {
  public:
    ShenandoahGCRequest() : generation(nullptr), cause(GCCause::_no_gc) {}
    ShenandoahGeneration* generation;
    GCCause::Cause cause;
  };

private:
  // This lock is used to coordinate setting the _requested_gc_cause and _requested generation.
  // It is important that these be changed together and have a consistent view.
  Monitor _request_lock;

  // Used to coordinate waiting for the control thread to change state
  Monitor _gc_mode_lock;

  // This is true when the old generation cycle is in an interruptible phase (i.e., marking or
  // preparing for mark).
  ShenandoahSharedFlag _allow_old_preemption;

  // Represents a normal (non cancellation) gc request. This can be set by mutators (System.gc,
  // whitebox gc, etc.) or by the regulator thread when the heuristics want to start a cycle.
  GCCause::Cause  _requested_gc_cause;

  // This is the generation the request should operate on.
  ShenandoahGeneration* _requested_generation;

  // Only the control thread knows the correct degeneration point. This is used to have the
  // control thread resume a STW cycle from the point where the concurrent cycle was cancelled.
  ShenandoahGC::ShenandoahDegenPoint _degen_point;

  // A reference to the heap
  ShenandoahGenerationalHeap* _heap;

  // This is used to keep track of whether to age objects during the current cycle.
  uint _age_period;

  // The mode is read frequently by requesting threads and only ever written by the control thread.
  shenandoah_padding(0);
  volatile GCMode _mode;
  shenandoah_padding(1);

public:
  ShenandoahGenerationalControlThread();

  void run_service() override;
  void stop_service() override;

  void request_gc(GCCause::Cause cause) override;

  // Return true if the request to start a concurrent GC for the given generation succeeded.
  bool request_concurrent_gc(ShenandoahGeneration* generation);

  // Returns the current state of the control thread
  GCMode gc_mode() {
    return _mode;
  }
private:
  // Returns true if the cycle has been cancelled or degenerated.
  bool check_cancellation_or_degen(ShenandoahGC::ShenandoahDegenPoint point);

  // Executes one GC cycle
  void run_gc_cycle(ShenandoahGCRequest request);

  // Returns true if the old generation marking completed (i.e., final mark executed for old generation).
  bool resume_concurrent_old_cycle(ShenandoahOldGeneration* generation, GCCause::Cause cause);
  void service_concurrent_cycle(ShenandoahGeneration* generation, GCCause::Cause cause, bool reset_old_bitmap_specially);
  void service_stw_full_cycle(GCCause::Cause cause);
  void service_stw_degenerated_cycle(ShenandoahGCRequest request);
  void service_concurrent_normal_cycle(ShenandoahGCRequest request);
  void service_concurrent_old_cycle(ShenandoahGCRequest cause);

  void notify_gc_waiters();

  // Blocks until at least one global GC cycle is complete.
  void handle_requested_gc(GCCause::Cause cause);

  // Returns true if the old generation marking was interrupted to allow a young cycle.
  bool preempt_old_marking(ShenandoahGeneration* generation);

  void process_phase_timings();

  void set_gc_mode(GCMode new_mode);
  static const char* gc_mode_name(GCMode mode);

  // Takes the request lock and updates the requested cause and generation, then notifies the lock's waiters.
  void notify_control_thread(GCCause::Cause cause, ShenandoahGeneration* generation);

  void maybe_set_aging_cycle();
  void check_for_request(ShenandoahGCRequest& request);

  void prepare_for_allocation_failure_request(ShenandoahGCRequest& request);
  void prepare_for_explicit_gc_request(ShenandoahGCRequest& request);
  void prepare_for_concurrent_gc_request(ShenandoahGCRequest& request);
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALCONTROLTHREAD_HPP
