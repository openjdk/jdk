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

#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"

/**
 * This interface exposes methods necessary for the heap to interact
 * with the threads responsible for driving the collection cycle.
 */
class ShenandoahController: public ConcurrentGCThread {
private:
  shenandoah_padding(0);
  // A monotonically increasing GC count.
  volatile size_t _gc_id;
  shenandoah_padding(1);

protected:
  // While we could have a single lock for these, it may risk unblocking
  // GC waiters when alloc failure GC cycle finishes. We want instead
  // to make complete explicit cycle for demanding customers.
  Monitor _alloc_failure_waiters_lock;
  Monitor _gc_waiters_lock;

  // Increments the internal GC count.
  void update_gc_id();

public:
  ShenandoahController():
    _gc_id(0),
    _alloc_failure_waiters_lock(Mutex::safepoint-2, "ShenandoahAllocFailureGC_lock", true),
    _gc_waiters_lock(Mutex::safepoint-2, "ShenandoahRequestedGC_lock", true)
  { }

  // Request a collection cycle. This handles "explicit" gc requests
  // like System.gc and "implicit" gc requests, like metaspace oom.
  virtual void request_gc(GCCause::Cause cause) = 0;

  // This cancels the collection cycle and has an option to block
  // until another cycle completes successfully.
  void handle_alloc_failure(const ShenandoahAllocRequest& req, bool block);

  // Invoked for allocation failures during evacuation. This cancels
  // the collection cycle without blocking.
  void handle_alloc_failure_evac(size_t words);

  // Notify threads waiting for GC to complete.
  void notify_alloc_failure_waiters();

  // Return the value of a monotonic increasing GC count, maintained by the control thread.
  size_t get_gc_id();
};
#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLLER_HPP
