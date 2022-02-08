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

#include "gc/shared/concurrentGCThread.hpp"
#include "memory/padded.hpp"
#include "runtime/semaphore.hpp"
#include "utilities/macros.hpp"

// Forward Decl.
class G1ConcurrentRefine;
class G1ConcurrentRefineStats;

// One or more G1 Concurrent Refinement Threads may be active if concurrent
// refinement is in progress.
class G1ConcurrentRefineThread: public ConcurrentGCThread {
  friend class VMStructs;
  friend class G1CollectedHeap;

  double _vtime_start;  // Initial virtual time.
  double _vtime_accum;  // Accumulated virtual time.

  G1ConcurrentRefineStats* _refinement_stats;

  uint _worker_id;

  G1ConcurrentRefine* _cr;

  NONCOPYABLE(G1ConcurrentRefineThread);

protected:
  G1ConcurrentRefineThread(G1ConcurrentRefine* cr, uint worker_id);

  // Returns !should_terminate().
  // precondition: this is the current thread.
  virtual bool wait_for_completed_buffers() = 0;

  // Called when no refinement work found for this thread.
  // Returns true if should deactivate.
  // precondition: this is the current thread.
  virtual bool maybe_deactivate() = 0;

  G1ConcurrentRefine* cr() const { return _cr; }

  void run_service() override;
  void stop_service() override;

public:
  static G1ConcurrentRefineThread* create(G1ConcurrentRefine* cr, uint worker_id);
  virtual ~G1ConcurrentRefineThread();

  // Activate this thread.
  // precondition: this is not the current thread.
  virtual void activate() = 0;

  G1ConcurrentRefineStats* refinement_stats() const {
    return _refinement_stats;
  }

  // Total virtual time so far.
  double vtime_accum() { return _vtime_accum; }
};

// Singleton special refinement thread, registered with the dirty card queue.
// This thread supports notification of increases to the number of cards in
// the dirty card queue, which may trigger activation of this thread when it
// is not already running.
class G1PrimaryConcurrentRefineThread final : public G1ConcurrentRefineThread {
  // Support for activation.  The thread waits on this semaphore when idle.
  // Calls to activate signal it to wake the thread.
  Semaphore _notifier;
  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE, 0);
  // Used as both the activation threshold and also the "is active" state.
  // The value is SIZE_MAX when the thread is active, otherwise the threshold
  // for signaling the semaphore.
  volatile size_t _threshold;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, sizeof(size_t));

  bool wait_for_completed_buffers() override;
  bool maybe_deactivate() override;

  G1PrimaryConcurrentRefineThread(G1ConcurrentRefine* cr);

  void stop_service() override;

public:
  static G1PrimaryConcurrentRefineThread* create(G1ConcurrentRefine* cr);

  void activate() override;

  // Used by the write barrier support to activate the thread if needed when
  // there are new refinement buffers.
  void notify(size_t num_cards);
  void update_notify_threshold(size_t threshold);
};

#endif // SHARE_GC_G1_G1CONCURRENTREFINETHREAD_HPP
