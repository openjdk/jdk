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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLLER_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLLER_HPP

#include "gc/shared/gcCause.hpp"
#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"

/**
 * This interface exposes methods necessary for the heap to interact
 * with the threads responsible for driving the collection cycle.
 */
class ShenandoahController: public ConcurrentGCThread {
private:
  ShenandoahSharedFlag _graceful_shutdown;

  shenandoah_padding(0);
  volatile size_t _allocs_seen;
  shenandoah_padding(1);
  volatile size_t _gc_id;
  shenandoah_padding(2);

protected:
  ShenandoahSharedFlag _alloc_failure_gc;
  ShenandoahSharedFlag _humongous_alloc_failure_gc;

  // While we could have a single lock for these, it may risk unblocking
  // GC waiters when alloc failure GC cycle finishes. We want instead
  // to make complete explicit cycle for demanding customers.
  Monitor _alloc_failure_waiters_lock;
  Monitor _gc_waiters_lock;

public:
  ShenandoahController():
    ConcurrentGCThread(),
    _allocs_seen(0),
    _gc_id(0),
    _alloc_failure_waiters_lock(Mutex::safepoint-2, "ShenandoahAllocFailureGC_lock", true),
    _gc_waiters_lock(Mutex::safepoint-2, "ShenandoahRequestedGC_lock", true)
  { }

  // Request a collection cycle. This handles "explicit" gc requests
  // like System.gc and "implicit" gc requests, like metaspace oom.
  virtual void request_gc(GCCause::Cause cause) = 0;

  // This cancels the collection cycle and has an option to block
  // until another cycle runs and clears the alloc failure gc flag.
  void handle_alloc_failure(ShenandoahAllocRequest& req, bool block);

  // Invoked for allocation failures during evacuation. This cancels
  // the collection cycle without blocking.
  void handle_alloc_failure_evac(size_t words);

  // Return true if setting the flag which indicates allocation failure succeeds.
  bool try_set_alloc_failure_gc(bool is_humongous);

  // Notify threads waiting for GC to complete.
  void notify_alloc_failure_waiters();

  // True if allocation failure flag has been set.
  bool is_alloc_failure_gc();

  // This is called for every allocation. The control thread accumulates
  // this value when idle. During the gc cycle, the control resets it
  // and reports it to the pacer.
  void pacing_notify_alloc(size_t words);
  size_t reset_allocs_seen();

  // These essentially allows to cancel a collection cycle for the
  // purpose of shutting down the JVM, without trying to start a degenerated
  // cycle.
  void prepare_for_graceful_shutdown();
  bool in_graceful_shutdown();


  // Returns the internal gc count used by the control thread. Probably
  // doesn't need to be exposed.
  size_t get_gc_id();
  void update_gc_id();
};
#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLLER_HPP
