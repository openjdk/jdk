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
#include "gc/shenandoah/shenandoahPadding.hpp"
#include "runtime/atomic.hpp"

/**
 * This interface exposes methods necessary for the heap to interact
 * with the threads responsible for driving the collection cycle.
 */
class ShenandoahController: public ConcurrentGCThread {
private:
  shenandoah_padding(0);
  // A monotonically increasing GC count.
  Atomic<size_t> _gc_id;
  shenandoah_padding(1);

protected:
  const Mutex::Rank WAITERS_LOCK_RANK = Mutex::safepoint - 5;
  const Mutex::Rank CONTROL_LOCK_RANK = Mutex::nosafepoint - 2;

  Monitor _gc_waiters_lock;

  // The number of threads blocked in allocation
  Atomic<size_t> _alloc_waiters_count;

  // Always set under waiters lock, read without a lock
  Atomic<bool> _alloc_stalls;


  // Increments the internal GC count.
  void update_gc_id();

  void notify_gc_waiters();

public:
  ShenandoahController():
    _gc_id(0),
    _gc_waiters_lock(WAITERS_LOCK_RANK, "ShenandoahGCWaiters_lock", true),
    _alloc_waiters_count(0),
    _alloc_stalls(false)
  { }

  // Request a collection cycle. This handles "explicit" gc requests
  // like System.gc and "implicit" gc requests, like metaspace oom.
  virtual void request_gc(GCCause::Cause cause) = 0;

  // This cancels the collection cycle and has an option to block
  // until another cycle completes successfully.
  void handle_alloc_failure(const ShenandoahAllocRequest &req);

  // Return number of threads blocked on allocation
  size_t alloc_waiters_count() const {
    return _alloc_waiters_count.load_relaxed();
  }

  bool alloc_stalls_during_cycle() {
    return _alloc_stalls.exchange(false, memory_order_relaxed);
  }

  // Return the value of a monotonic increasing GC count, maintained by the control thread.
  size_t get_gc_id();
};
#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLLER_HPP
