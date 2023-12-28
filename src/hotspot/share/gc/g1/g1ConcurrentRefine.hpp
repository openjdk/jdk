/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CONCURRENTREFINE_HPP
#define SHARE_GC_G1_G1CONCURRENTREFINE_HPP

#include "gc/g1/g1ConcurrentRefineStats.hpp"
#include "gc/g1/g1ConcurrentRefineThreadsNeeded.hpp"
#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Forward decl
class G1ConcurrentRefine;
class G1ConcurrentRefineThread;
class G1DirtyCardQueueSet;
class G1Policy;
class ThreadClosure;

// Helper class for refinement thread management. Used to start, stop and
// iterate over them.
class G1ConcurrentRefineThreadControl {
  G1ConcurrentRefine* _cr;
  G1ConcurrentRefineThread** _threads;
  uint _max_num_threads;

  // Create the refinement thread for the given worker id.
  // If initializing is true, ignore InjectGCWorkerCreationFailure.
  G1ConcurrentRefineThread* create_refinement_thread(uint worker_id, bool initializing);

  NONCOPYABLE(G1ConcurrentRefineThreadControl);

public:
  G1ConcurrentRefineThreadControl();
  ~G1ConcurrentRefineThreadControl();

  jint initialize(G1ConcurrentRefine* cr, uint max_num_threads);

  void assert_current_thread_is_primary_refinement_thread() const NOT_DEBUG_RETURN;

  uint max_num_threads() const { return _max_num_threads; }

  // Activate the indicated thread.  If the thread has not yet been allocated,
  // allocate and then activate.  If allocation is needed and fails, return
  // false.  Otherwise return true.
  // precondition: worker_id < max_num_threads().
  // precondition: current thread is not the designated worker.
  bool activate(uint worker_id);

  void worker_threads_do(ThreadClosure* tc);
  void stop();
};

// Controls concurrent refinement.
//
// Mutator threads produce dirty cards, which need to be examined for updates
// to the remembered sets (refinement).  There is a pause-time budget for
// processing these dirty cards (see -XX:G1RSetUpdatingPauseTimePercent).  The
// purpose of concurrent refinement is to (attempt to) ensure the number of
// pending dirty cards at the start of a GC can be processed within that time
// budget.
//
// Concurrent refinement is performed by a combination of dedicated threads
// and by mutator threads as they produce dirty cards.  If configured to not
// have any dedicated threads (-XX:G1ConcRefinementThreads=0) then all
// concurrent refinement work is performed by mutator threads.  When there are
// dedicated threads, they generally do most of the concurrent refinement
// work, to minimize throughput impact of refinement work on mutator threads.
//
// This class determines the target number of dirty cards pending for the next
// GC.  It also owns the dedicated refinement threads and controls their
// activation in order to achieve that target.
//
// There are two kinds of dedicated refinement threads, a single primary
// thread and some number of secondary threads.  When active, all refinement
// threads take buffers of dirty cards from the dirty card queue and process
// them.  Between buffers they query this owning object to find out whether
// they should continue running, deactivating themselves if not.
//
// The primary thread drives the control system that determines how many
// refinement threads should be active.  If inactive, it wakes up periodically
// to recalculate the number of active threads needed, and activates
// additional threads as necessary.  While active it also periodically
// recalculates the number wanted and activates more threads if needed.  It
// also reduces the number of wanted threads when the target has been reached,
// triggering deactivations.
class G1ConcurrentRefine : public CHeapObj<mtGC> {
  G1Policy* _policy;
  volatile uint _threads_wanted;
  size_t _pending_cards_target;
  Ticks _last_adjust;
  Ticks _last_deactivate;
  bool _needs_adjust;
  G1ConcurrentRefineThreadsNeeded _threads_needed;
  G1ConcurrentRefineThreadControl _thread_control;
  G1DirtyCardQueueSet& _dcqs;

  G1ConcurrentRefine(G1Policy* policy);

  static uint worker_id_offset();

  jint initialize();

  void assert_current_thread_is_primary_refinement_thread() const {
    _thread_control.assert_current_thread_is_primary_refinement_thread();
  }

  // For the first few collection cycles we don't have a target (and so don't
  // do any concurrent refinement), because there hasn't been enough pause
  // time refinement work to be done to make useful predictions.  We use
  // SIZE_MAX as a special marker value to indicate we're in this state.
  static const size_t PendingCardsTargetUninitialized = SIZE_MAX;
  bool is_pending_cards_target_initialized() const {
    return _pending_cards_target != PendingCardsTargetUninitialized;
  }

  void update_pending_cards_target(double logged_cards_scan_time_ms,
                                   size_t processed_logged_cards,
                                   size_t predicted_thread_buffer_cards,
                                   double goal_ms);

  uint64_t adjust_threads_period_ms() const;
  bool is_in_last_adjustment_period() const;

  class RemSetSamplingClosure;  // Helper class for adjusting young length.
  void adjust_young_list_target_length();

  void adjust_threads_wanted(size_t available_bytes);

  NONCOPYABLE(G1ConcurrentRefine);

public:
  ~G1ConcurrentRefine();

  // Returns a G1ConcurrentRefine instance if succeeded to create/initialize the
  // G1ConcurrentRefine instance. Otherwise, returns null with error code.
  static G1ConcurrentRefine* create(G1Policy* policy, jint* ecode);

  // Stop all the refinement threads.
  void stop();

  // Called at the end of a GC to prepare for refinement during the next
  // concurrent phase.  Updates the target for the number of pending dirty
  // cards.  Updates the mutator refinement threshold.  Ensures the primary
  // refinement thread (if it exists) is active, so it will adjust the number
  // of running threads.
  void adjust_after_gc(double logged_cards_scan_time_ms,
                       size_t processed_logged_cards,
                       size_t predicted_thread_buffer_cards,
                       double goal_ms);

  // Target number of pending dirty cards at the start of the next GC.
  size_t pending_cards_target() const { return _pending_cards_target; }

  // May recalculate the number of refinement threads that should be active in
  // order to meet the pending cards target.  Returns true if adjustment was
  // performed, and clears any pending request.  Returns false if the
  // adjustment period has not expired, or because a timed or requested
  // adjustment could not be performed immediately and so was deferred.
  // precondition: current thread is the primary refinement thread.
  bool adjust_threads_periodically();

  // The amount of time (in ms) the primary refinement thread should sleep
  // when it is inactive.  It requests adjustment whenever it is reactivated.
  // precondition: current thread is the primary refinement thread.
  uint64_t adjust_threads_wait_ms() const;

  // Record a request for thread adjustment as soon as possible.
  // precondition: current thread is the primary refinement thread.
  void record_thread_adjustment_needed();

  // Test whether there is a pending request for thread adjustment.
  // precondition: current thread is the primary refinement thread.
  bool is_thread_adjustment_needed() const;

  // Reduce the number of active threads wanted.
  // precondition: current thread is the primary refinement thread.
  void reduce_threads_wanted();

  // Test whether the thread designated by worker_id should be active.
  bool is_thread_wanted(uint worker_id) const;

  // Return total of concurrent refinement stats for the
  // ConcurrentRefineThreads.  Also reset the stats for the threads.
  G1ConcurrentRefineStats get_and_reset_refinement_stats();

  // Perform a single refinement step; called by the refinement
  // threads.  Returns true if there was refinement work available.
  // Updates stats.
  bool try_refinement_step(uint worker_id,
                           size_t stop_at,
                           G1ConcurrentRefineStats* stats);

  // Iterate over all concurrent refinement threads applying the given closure.
  void threads_do(ThreadClosure *tc);
};

#endif // SHARE_GC_G1_G1CONCURRENTREFINE_HPP
