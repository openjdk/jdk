/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CONCURRENTREFINETHREAD_HPP
#define SHARE_GC_G1_G1CONCURRENTREFINETHREAD_HPP

#include "gc/g1/g1ConcurrentRefineStats.hpp"
#include "gc/shared/concurrentGCThread.hpp"
#include "runtime/mutex.hpp"
#include "utilities/globalDefinitions.hpp"

// Forward Decl.
class G1ConcurrentRefine;

// One or more G1 Concurrent Refinement Threads may be active if concurrent
// refinement is in progress.
class G1ConcurrentRefineThread: public ConcurrentGCThread {
  friend class VMStructs;
  friend class G1CollectedHeap;

  double _vtime_start;  // Initial virtual time.
  double _vtime_accum;  // Accumulated virtual time.

  Monitor _notifier;
  bool _requested_active;

  G1ConcurrentRefineStats _refinement_stats;

  uint _worker_id;

  G1ConcurrentRefine* _cr;

  NONCOPYABLE(G1ConcurrentRefineThread);

protected:
  G1ConcurrentRefineThread(G1ConcurrentRefine* cr, uint worker_id);

  Monitor* notifier() { return &_notifier; }
  bool requested_active() const { return _requested_active; }

  // Returns !should_terminate().
  // precondition: this is the current thread.
  virtual bool wait_for_completed_buffers() = 0;

  // Deactivate if appropriate.  Returns true if deactivated.
  // precondition: this is the current thread.
  virtual bool maybe_deactivate();

  // Attempt to do some refinement work.
  // precondition: this is the current thread.
  virtual void do_refinement_step() = 0;

  // Update concurrent refine threads stats.
  // If we are in Primary thread, we additionally update CPU time tracking.
  virtual void track_usage() {
    if (os::supports_vtime()) {
      _vtime_accum = (os::elapsedVTime() - _vtime_start);
    } else {
      _vtime_accum = 0.0;
    }
  };

  // Helper for do_refinement_step implementations.  Try to perform some
  // refinement work, limited by stop_at.  Returns true if any refinement work
  // was performed, false if no work available per stop_at.
  // precondition: this is the current thread.
  bool try_refinement_step(size_t stop_at);

  void report_active(const char* reason) const;
  void report_inactive(const char* reason, const G1ConcurrentRefineStats& stats) const;

  G1ConcurrentRefine* cr() const { return _cr; }

  void run_service() override;
  void stop_service() override;

public:
  static G1ConcurrentRefineThread* create(G1ConcurrentRefine* cr, uint worker_id);
  virtual ~G1ConcurrentRefineThread() = default;

  uint worker_id() const { return _worker_id; }

  // Activate this thread.
  // precondition: this is not the current thread.
  void activate();

  G1ConcurrentRefineStats* refinement_stats() {
    return &_refinement_stats;
  }

  const G1ConcurrentRefineStats* refinement_stats() const {
    return &_refinement_stats;
  }

  // Total virtual time so far.
  double vtime_accum() { return _vtime_accum; }
};

#endif // SHARE_GC_G1_G1CONCURRENTREFINETHREAD_HPP
