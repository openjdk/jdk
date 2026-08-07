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
public:
  enum ShenandoahCollectorPhase {
    UNSET,
    INITIALIZING,
    ROOTS,
    MARK,
    EVAC,
    UPDATE_REFS,
    PHASE_LIMIT
  };

private:
  shenandoah_padding(0);
  // A monotonically increasing GC count.
  Atomic<size_t> _gc_id;
  shenandoah_padding(1);

  // Written by control thread, read by mutators
  Atomic<ShenandoahCollectorPhase> _phase;

protected:
  const Mutex::Rank WAITERS_LOCK_RANK = Mutex::safepoint - 5;
  const Mutex::Rank CONTROL_LOCK_RANK = Mutex::nosafepoint - 2;

  Monitor _gc_waiters_lock;

  // The number of stalls experienced during the cycle. Incremented by mutators, reset by control thread.
  shenandoah_padding(2);
  Atomic<size_t> _alloc_stall_count;
  shenandoah_padding(3);

  // Written by control thread and mutators, clamped between ConcGCThreads and ParallelGCThreads.
  Atomic<size_t> _concurrent_worker_count;

  // Increments the internal GC count.
  void update_gc_id();

  // Increase worker count when a stall is reported. Called from mutator thread.
  void increase_concurrent_worker_count();

  // Decrease worker count if no stalls were detected in a cycle. Called from control thread.
  void decrease_concurrent_worker_count();

  // Returns the total number of allocation stalls during a cycle
  size_t alloc_stall_count() const {
    return _alloc_stall_count.load_relaxed();
  }

public:
  ShenandoahController();

  // Request a collection cycle. This handles "explicit" gc requests
  // like System.gc and "implicit" gc requests, like metaspace oom.
  virtual void request_gc(GCCause::Cause cause) = 0;

  // Notify threads that the gc has reclaimed memory (or that it completed a cycle, or it's exiting).
  void notify_gc_waiters();

  // This cancels the collection cycle and has an option to block
  // until another cycle completes successfully.
  void handle_alloc_failure(const ShenandoahAllocRequest &req);

  // Return suggested number of concurrent worker threads
  size_t concurrent_worker_count() const {
    return _concurrent_worker_count.load_relaxed();
  }

  // Return the value of a monotonic increasing GC count, maintained by the control thread.
  size_t get_gc_id() const;

  ShenandoahCollectorPhase get_phase() const {
    return _phase.load_relaxed();
  }

  void set_phase(ShenandoahCollectorPhase phase) {
    assert(ShenandoahCollectorPhase::UNSET <= phase, "Phase out of bounds: %d", phase);
    assert(phase < ShenandoahCollectorPhase::PHASE_LIMIT, "Phase out of bounds %d", phase);
    _phase.store_relaxed(phase);
  }

  static const char* collector_phase_to_string(ShenandoahCollectorPhase phase);
};
#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLLER_HPP
