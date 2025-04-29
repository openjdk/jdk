/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

// Forward decl
class G1CardTableClaimTable;
class G1CollectedHeap;
class G1ConcurrentRefine;
class G1ConcurrentRefineThread;
class G1HeapRegion;
class G1Policy;
class ThreadClosure;
class WorkerTask;
class WorkerThreads;

// Helper class for refinement thread management. Used to start, stop and
// iterate over them.
class G1ConcurrentRefineThreadControl {
  G1ConcurrentRefine* _cr;
  G1ConcurrentRefineThread* _control_thread;

  WorkerThreads* _workers;
  uint _max_num_threads;

  // Create the refinement thread for the given worker id.
  // If initializing is true, ignore InjectGCWorkerCreationFailure.
  G1ConcurrentRefineThread* create_refinement_thread();

  NONCOPYABLE(G1ConcurrentRefineThreadControl);

public:
  G1ConcurrentRefineThreadControl(uint max_num_threads);
  ~G1ConcurrentRefineThreadControl();

  jint initialize(G1ConcurrentRefine* cr);

  void assert_current_thread_is_control_refinement_thread() const NOT_DEBUG_RETURN;

  uint max_num_threads() const { return _max_num_threads; }
  bool is_refinement_enabled() const { return _max_num_threads > 0; }

  // Activate the control thread.
  void activate();

  void run_task(WorkerTask* task, uint num_workers);

  void control_thread_do(ThreadClosure* tc);
  void worker_threads_do(ThreadClosure* tc);
  void stop();
};

// Tracks the current state of re-examining the dirty cards from idle to completion
// (and reset back to idle).
//
// The process steps are as follows:
//
// 1) Swap global card table pointers
//
// 2) Swap Java Thread's card table pointers
//
// 3) Synchronize GC Threads
//      Ensures memory visibility
//
// After this point mutator threads should not mark the refinement table.
//
// 4) Snapshot the heap
//      Determines which regions need to be swept.
//
// 5) Sweep Refinement table
//      Examines non-Clean cards on the refinement table.
//
// 6) Completion Work
//      Calculates statistics about the process to be used in various parts of
//      the garbage collection.
//
// All but step 4 are interruptible by safepoints. In case of a garbage collection,
// the garbage collection will interrupt this process, and go to Idle state.
//
class G1ConcurrentRefineSweepState {

  enum class State : uint {
    Idle,                        // Refinement is doing nothing.
    SwapGlobalCT,                // Swap global card table.
    SwapJavaThreadsCT,           // Swap java thread's card tables.
    SynchronizeGCThreads,        // Synchronize GC thread's memory view.
    SnapshotHeap,                // Take a snapshot of the region's top() values.
    SweepRT,                     // Sweep the refinement table for pending (dirty) cards.
    CompleteRefineWork,          // Cleanup of refinement work, reset to idle.
    Last
  } _state;

  static const char* state_name(State state) {
    static const char* _state_names[] = {
      "Idle",
      "Swap Global Card Table",
      "Swap JavaThread Card Table",
      "Synchronize GC Threads",
      "Snapshot Heap",
      "Sweep Refinement Table",
      "Complete Sweep Work"
    };

    return _state_names[static_cast<uint>(state)];
  }

  // Current heap snapshot.
  G1CardTableClaimTable* _sweep_table;

  // Start times for all states.
  Ticks _state_start[static_cast<uint>(State::Last)];

  void set_state_start_time();
  Tickspan get_duration(State start, State end);

  G1ConcurrentRefineStats _stats;

  // Advances the state to next_state if not interrupted by a changed epoch. Returns
  // to Idle otherwise.
  bool advance_state(State next_state);

  void assert_state(State expected);

  static void snapshot_heap_into(G1CardTableClaimTable* sweep_table);

public:
  G1ConcurrentRefineSweepState(uint max_reserved_regions);
  ~G1ConcurrentRefineSweepState();

  void start_work();

  bool swap_global_card_table();
  bool swap_java_threads_ct();
  bool swap_gc_threads_ct();
  void snapshot_heap(bool concurrent = true);
  void sweep_refinement_table_start();
  bool sweep_refinement_table_step();

  bool complete_work(bool concurrent, bool print_log = true);

  G1CardTableClaimTable* sweep_table() { return _sweep_table; }
  G1ConcurrentRefineStats* stats() { return &_stats; }
  void reset_stats();

  void add_yield_during_sweep_duration(jlong duration);

  bool is_in_progress() const;
  bool are_java_threads_synched() const;
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
// Concurrent refinement is performed by a set of dedicated threads.  If configured
// to not have any dedicated threads (-XX:G1ConcRefinementThreads=0) then no
// refinement work is performed at all.
//
// This class determines the target number of dirty cards pending for the next
// GC.  It also owns the dedicated refinement threads and controls their
// activation in order to achieve that target.
//
// There are two kinds of dedicated refinement threads, a single control
// thread and some number of refinement worker threads.
// The control thread determines whether there is need to do work, and then starts
// an appropriate number of refinement worker threads to get back to the target
// number of pending dirty cards.
//
// The control wakes up periodically whether there is need to do refinement
// work, starting the refinement process as necessary.
//
class G1ConcurrentRefine : public CHeapObj<mtGC> {
  G1Policy* _policy;
  volatile uint _num_threads_wanted;
  size_t _pending_cards_target;
  Ticks _last_adjust;
  Ticks _last_deactivate;
  bool _needs_adjust;
  bool _heap_was_locked;                // The heap has been locked the last time we tried to adjust the number of refinement threads.

  G1ConcurrentRefineThreadsNeeded _threads_needed;
  G1ConcurrentRefineThreadControl _thread_control;

  G1ConcurrentRefineSweepState _sweep_state;

  G1ConcurrentRefine(G1CollectedHeap* g1h);

  jint initialize();

  void assert_current_thread_is_control_refinement_thread() const {
    _thread_control.assert_current_thread_is_control_refinement_thread();
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
                                   double goal_ms);

  uint64_t adjust_threads_period_ms() const;

  void adjust_threads_wanted(size_t available_bytes);

  NONCOPYABLE(G1ConcurrentRefine);

public:
  ~G1ConcurrentRefine();

  G1ConcurrentRefineSweepState& sweep_state() { return _sweep_state; }

  G1ConcurrentRefineSweepState& sweep_state_for_merge();

  void run_with_refinement_workers(WorkerTask* task);

  void notify_region_reclaimed(G1HeapRegion* r);

  // Returns a G1ConcurrentRefine instance if succeeded to create/initialize the
  // G1ConcurrentRefine instance. Otherwise, returns null with error code.
  static G1ConcurrentRefine* create(G1CollectedHeap* g1h, jint* ecode);

  // Stop all the refinement threads.
  void stop();

  // Called at the end of a GC to prepare for refinement during the next
  // concurrent phase.  Updates the target for the number of pending dirty
  // cards.  Updates the mutator refinement threshold.  Ensures the refinement
  // control thread (if it exists) is active, so it will adjust the number
  // of running threads.
  void adjust_after_gc(double logged_cards_scan_time_ms,
                       size_t processed_logged_cards,
                       double goal_ms);

  // Target number of pending dirty cards at the start of the next GC.
  size_t pending_cards_target() const { return _pending_cards_target; }

  // Recalculates the number of refinement threads that should be active in
  // order to meet the pending cards target.
  // Returns true if it could recalculate the number of threads and
  // refinement threads should be started.
  // Returns false if the adjustment period has not expired, or because a timed
  // or requested adjustment could not be performed immediately and so was deferred.
  bool adjust_num_threads_periodically();

  // The amount of time (in ms) the refinement control thread should sleep
  // when it is inactive.  It requests adjustment whenever it is reactivated.
  // precondition: current thread is the refinement control thread.
  uint64_t adjust_threads_wait_ms() const;

  // Record a request for thread adjustment as soon as possible.
  // precondition: current thread is the refinement control thread.
  void record_thread_adjustment_needed();

  // Test whether there is a pending request for thread adjustment.
  // precondition: current thread is the refinement control thread.
  bool is_thread_adjustment_needed() const;

  // Indicate that last refinement adjustment had been deferred due to not
  // obtaining the heap lock.
  bool wait_for_heap_lock() const { return _heap_was_locked; }

  uint num_threads_wanted() const { return _num_threads_wanted; }

  // Iterate over all concurrent refinement threads applying the given closure.
  void threads_do(ThreadClosure *tc);
  // Iterate over specific refinement threads applying the given closure.
  void worker_threads_do(ThreadClosure *tc);
  void control_thread_do(ThreadClosure *tc);
};

#endif // SHARE_GC_G1_G1CONCURRENTREFINE_HPP
