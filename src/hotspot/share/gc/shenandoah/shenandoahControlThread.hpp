/*
 * Copyright (c) 2013, 2021, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahPadding.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"

class ShenandoahControlThread: public ConcurrentGCThread {
  friend class VMStructs;

private:
  typedef enum {
    none,
    concurrent_normal,
    stw_degenerated,
    stw_full
  } GCMode;

  // While we could have a single lock for these, it may risk unblocking
  // GC waiters when alloc failure GC cycle finishes. We want instead
  // to make complete explicit cycle for for demanding customers.
  Monitor _alloc_failure_waiters_lock;
  Monitor _gc_waiters_lock;

public:
  void run_service();
  void stop_service();

private:
  ShenandoahSharedFlag _gc_requested;
  ShenandoahSharedFlag _alloc_failure_gc;
  ShenandoahSharedFlag _graceful_shutdown;
  GCCause::Cause       _requested_gc_cause;
  ShenandoahGC::ShenandoahDegenPoint _degen_point;

  shenandoah_padding(0);
  volatile size_t _allocs_seen;
  shenandoah_padding(1);
  volatile size_t _gc_id;
  shenandoah_padding(2);

  bool check_cancellation_or_degen(ShenandoahGC::ShenandoahDegenPoint point);
  void service_concurrent_normal_cycle(GCCause::Cause cause);
  void service_stw_full_cycle(GCCause::Cause cause);
  void service_stw_degenerated_cycle(GCCause::Cause cause, ShenandoahGC::ShenandoahDegenPoint point);

  bool try_set_alloc_failure_gc();
  void notify_alloc_failure_waiters();
  bool is_alloc_failure_gc();

  void reset_gc_id();
  void update_gc_id();
  size_t get_gc_id();

  void notify_gc_waiters();

  // Handle GC request.
  // Blocks until GC is over.
  void handle_requested_gc(GCCause::Cause cause);

public:
  // Constructor
  ShenandoahControlThread();

  // Handle allocation failure from a mutator allocation.
  // Optionally blocks while collector is handling the failure. If the GC
  // threshold has been exceeded, the mutator allocation will not block so
  // that the out of memory error can be raised promptly.
  void handle_alloc_failure(ShenandoahAllocRequest& req, bool block = true);

  // Handle allocation failure from evacuation path.
  void handle_alloc_failure_evac(size_t words);

  void request_gc(GCCause::Cause cause);

  void pacing_notify_alloc(size_t words);

  void start();
  void prepare_for_graceful_shutdown();
  bool in_graceful_shutdown();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLTHREAD_HPP
