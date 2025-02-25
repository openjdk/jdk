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
#include "gc/shenandoah/shenandoahGenerationType.hpp"
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

private:
  Monitor _control_lock;
  Monitor _regulator_lock;

  ShenandoahSharedFlag _allow_old_preemption;
  ShenandoahSharedFlag _preemption_requested;

  GCCause::Cause  _requested_gc_cause;
  volatile ShenandoahGenerationType _requested_generation;
  ShenandoahGC::ShenandoahDegenPoint _degen_point;
  ShenandoahGeneration* _degen_generation;

  shenandoah_padding(0);
  volatile GCMode _mode;
  shenandoah_padding(1);

public:
  ShenandoahGenerationalControlThread();

  void run_service() override;
  void stop_service() override;

  void request_gc(GCCause::Cause cause) override;

  // Return true if the request to start a concurrent GC for the given generation succeeded.
  bool request_concurrent_gc(ShenandoahGenerationType generation);

  GCMode gc_mode() {
    return _mode;
  }
private:

  // Returns true if the cycle has been cancelled or degenerated.
  bool check_cancellation_or_degen(ShenandoahGC::ShenandoahDegenPoint point);

  // Returns true if the old generation marking completed (i.e., final mark executed for old generation).
  bool resume_concurrent_old_cycle(ShenandoahOldGeneration* generation, GCCause::Cause cause);
  void service_concurrent_cycle(ShenandoahGeneration* generation, GCCause::Cause cause, bool reset_old_bitmap_specially);
  void service_stw_full_cycle(GCCause::Cause cause);
  void service_stw_degenerated_cycle(GCCause::Cause cause, ShenandoahGC::ShenandoahDegenPoint point);

  void notify_gc_waiters();

  // Handle GC request.
  // Blocks until GC is over.
  void handle_requested_gc(GCCause::Cause cause);

  bool is_explicit_gc(GCCause::Cause cause) const;
  bool is_implicit_gc(GCCause::Cause cause) const;

  // Returns true if the old generation marking was interrupted to allow a young cycle.
  bool preempt_old_marking(ShenandoahGenerationType generation);

  void process_phase_timings(const ShenandoahGenerationalHeap* heap);

  void service_concurrent_normal_cycle(ShenandoahGenerationalHeap* heap,
                                       ShenandoahGenerationType generation,
                                       GCCause::Cause cause);

  void service_concurrent_old_cycle(ShenandoahGenerationalHeap* heap,
                                    GCCause::Cause &cause);

  void set_gc_mode(GCMode new_mode);

  static const char* gc_mode_name(GCMode mode);

  void notify_control_thread();

  void service_concurrent_cycle(ShenandoahHeap* heap,
                                ShenandoahGeneration* generation,
                                GCCause::Cause &cause,
                                bool do_old_gc_bootstrap);

};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALCONTROLTHREAD_HPP
