/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/classLoaderDataGraph.inline.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "code/nmethod.hpp"
#include "code/pcDesc.hpp"
#include "code/scopeDesc.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/workgroup.hpp"
#include "interpreter/interpreter.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/atomic.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/sweeper.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/timerTrace.hpp"
#include "services/runtimeService.hpp"
#include "utilities/events.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "c1/c1_globals.hpp"
#endif

template <typename E>
static void set_current_safepoint_id(E* event, int adjustment = 0) {
  assert(event != NULL, "invariant");
  event->set_safepointId(SafepointSynchronize::safepoint_counter() + adjustment);
}

static void post_safepoint_begin_event(EventSafepointBegin* event,
                                       int thread_count,
                                       int critical_thread_count) {
  assert(event != NULL, "invariant");
  assert(event->should_commit(), "invariant");
  set_current_safepoint_id(event);
  event->set_totalThreadCount(thread_count);
  event->set_jniCriticalThreadCount(critical_thread_count);
  event->commit();
}

static void post_safepoint_cleanup_event(EventSafepointCleanup* event) {
  assert(event != NULL, "invariant");
  assert(event->should_commit(), "invariant");
  set_current_safepoint_id(event);
  event->commit();
}

static void post_safepoint_synchronize_event(EventSafepointStateSynchronization* event,
                                             int initial_number_of_threads,
                                             int threads_waiting_to_block,
                                             unsigned int iterations) {
  assert(event != NULL, "invariant");
  if (event->should_commit()) {
    // Group this event together with the ones committed after the counter is increased
    set_current_safepoint_id(event, 1);
    event->set_initialThreadCount(initial_number_of_threads);
    event->set_runningThreadCount(threads_waiting_to_block);
    event->set_iterations(iterations);
    event->commit();
  }
}

static void post_safepoint_wait_blocked_event(EventSafepointWaitBlocked* event,
                                              int initial_threads_waiting_to_block) {
  assert(event != NULL, "invariant");
  assert(event->should_commit(), "invariant");
  set_current_safepoint_id(event);
  event->set_runningThreadCount(initial_threads_waiting_to_block);
  event->commit();
}

static void post_safepoint_cleanup_task_event(EventSafepointCleanupTask* event,
                                              const char* name) {
  assert(event != NULL, "invariant");
  if (event->should_commit()) {
    set_current_safepoint_id(event);
    event->set_name(name);
    event->commit();
  }
}

static void post_safepoint_end_event(EventSafepointEnd* event) {
  assert(event != NULL, "invariant");
  if (event->should_commit()) {
    // Group this event together with the ones committed before the counter increased
    set_current_safepoint_id(event, -1);
    event->commit();
  }
}

// --------------------------------------------------------------------------------------------------
// Implementation of Safepoint begin/end

SafepointSynchronize::SynchronizeState volatile SafepointSynchronize::_state = SafepointSynchronize::_not_synchronized;
volatile int  SafepointSynchronize::_waiting_to_block = 0;
volatile uint64_t SafepointSynchronize::_safepoint_counter = 0;
int SafepointSynchronize::_current_jni_active_count = 0;
long  SafepointSynchronize::_end_of_last_safepoint = 0;
int SafepointSynchronize::_defer_thr_suspend_loop_count = 4000;
static const int safepoint_spin_before_yield = 2000;
static volatile int PageArmed = 0 ;        // safepoint polling page is RO|RW vs PROT_NONE
static volatile int TryingToBlock = 0 ;    // proximate value -- for advisory use only
static bool timeout_error_printed = false;


// Statistic related statics
julong SafepointSynchronize::_coalesced_vmop_count = 0;
static jlong _safepoint_begin_time = 0;
static float _ts_of_current_safepoint = 0.0f;
static volatile int _nof_threads_hit_polling_page = 0;

// Roll all threads forward to a safepoint and suspend them all
void SafepointSynchronize::begin() {
  EventSafepointBegin begin_event;
  Thread* myThread = Thread::current();
  assert(myThread->is_VM_thread(), "Only VM thread may execute a safepoint");

  if (log_is_enabled(Debug, safepoint, stats)) {
    _safepoint_begin_time = os::javaTimeNanos();
    _ts_of_current_safepoint = tty->time_stamp().seconds();
    _nof_threads_hit_polling_page = 0;
  }

  Universe::heap()->safepoint_synchronize_begin();

  // By getting the Threads_lock, we assure that no threads are about to start or
  // exit. It is released again in SafepointSynchronize::end().
  Threads_lock->lock();

  assert( _state == _not_synchronized, "trying to safepoint synchronize with wrong state");

  int nof_threads = Threads::number_of_threads();

  log_debug(safepoint)("Safepoint synchronization initiated. (%d threads)", nof_threads);

  RuntimeService::record_safepoint_begin();

  MutexLocker mu(Safepoint_lock);

  // Reset the count of active JNI critical threads
  _current_jni_active_count = 0;

  // Set number of threads to wait for, before we initiate the callbacks
  _waiting_to_block = nof_threads;
  TryingToBlock     = 0 ;
  int still_running = nof_threads;

  // Save the starting time, so that it can be compared to see if this has taken
  // too long to complete.
  jlong safepoint_limit_time = 0;
  timeout_error_printed = false;

  // Begin the process of bringing the system to a safepoint.
  // Java threads can be in several different states and are
  // stopped by different mechanisms:
  //
  //  1. Running interpreted
  //     The interpreter dispatch table is changed to force it to
  //     check for a safepoint condition between bytecodes.
  //  2. Running in native code
  //     When returning from the native code, a Java thread must check
  //     the safepoint _state to see if we must block.  If the
  //     VM thread sees a Java thread in native, it does
  //     not wait for this thread to block.  The order of the memory
  //     writes and reads of both the safepoint state and the Java
  //     threads state is critical.  In order to guarantee that the
  //     memory writes are serialized with respect to each other,
  //     the VM thread issues a memory barrier instruction.
  //  3. Running compiled Code
  //     Compiled code reads a global (Safepoint Polling) page that
  //     is set to fault if we are trying to get to a safepoint.
  //  4. Blocked
  //     A thread which is blocked will not be allowed to return from the
  //     block condition until the safepoint operation is complete.
  //  5. In VM or Transitioning between states
  //     If a Java thread is currently running in the VM or transitioning
  //     between states, the safepointing code will wait for the thread to
  //     block itself when it attempts transitions to a new state.
  //
  {
    EventSafepointStateSynchronization sync_event;
    int initial_running = 0;

    _state            = _synchronizing;

    if (SafepointMechanism::uses_thread_local_poll()) {
      // Arming the per thread poll while having _state != _not_synchronized means safepointing
      log_trace(safepoint)("Setting thread local yield flag for threads");
      OrderAccess::storestore(); // storestore, global state -> local state
      for (JavaThreadIteratorWithHandle jtiwh; JavaThread *cur = jtiwh.next(); ) {
        // Make sure the threads start polling, it is time to yield.
        SafepointMechanism::arm_local_poll(cur);
      }
    }
    OrderAccess::fence(); // storestore|storeload, global state -> local state

    if (SafepointMechanism::uses_global_page_poll()) {
      // Make interpreter safepoint aware
      Interpreter::notice_safepoints();

      // Make polling safepoint aware
      guarantee (PageArmed == 0, "invariant") ;
      PageArmed = 1 ;
      os::make_polling_page_unreadable();
    }

    // Consider using active_processor_count() ... but that call is expensive.
    int ncpus = os::processor_count() ;
    unsigned int iterations = 0;

    {
      JavaThreadIteratorWithHandle jtiwh;
#ifdef ASSERT
      for (; JavaThread *cur = jtiwh.next(); ) {
        assert(cur->safepoint_state()->is_running(), "Illegal initial state");
        // Clear the visited flag to ensure that the critical counts are collected properly.
        cur->set_visited_for_critical_count(false);
      }
#endif // ASSERT

      if (SafepointTimeout)
        safepoint_limit_time = os::javaTimeNanos() + (jlong)SafepointTimeoutDelay * MICROUNITS;

      // Iterate through all threads until it have been determined how to stop them all at a safepoint
      int steps = 0 ;
      while(still_running > 0) {
        jtiwh.rewind();
        for (; JavaThread *cur = jtiwh.next(); ) {
          assert(!cur->is_ConcurrentGC_thread(), "A concurrent GC thread is unexpectly being suspended");
          ThreadSafepointState *cur_state = cur->safepoint_state();
          if (cur_state->is_running()) {
            cur_state->examine_state_of_thread();
            if (!cur_state->is_running()) {
              still_running--;
              // consider adjusting steps downward:
              //   steps = 0
              //   steps -= NNN
              //   steps >>= 1
              //   steps = MIN(steps, 2000-100)
              //   if (iterations != 0) steps -= NNN
            }
            LogTarget(Trace, safepoint) lt;
            if (lt.is_enabled()) {
              ResourceMark rm;
              LogStream ls(lt);
              cur_state->print_on(&ls);
            }
          }
        }

        if (iterations == 0) {
          initial_running = still_running;
          if (log_is_enabled(Debug, safepoint, stats)) {
            begin_statistics(nof_threads, still_running);
          }
        }

        if (still_running > 0) {
          // Check for if it takes to long
          if (SafepointTimeout && safepoint_limit_time < os::javaTimeNanos()) {
            print_safepoint_timeout(_spinning_timeout);
          }

          // Spin to avoid context switching.
          // There's a tension between allowing the mutators to run (and rendezvous)
          // vs spinning.  As the VM thread spins, wasting cycles, it consumes CPU that
          // a mutator might otherwise use profitably to reach a safepoint.  Excessive
          // spinning by the VM thread on a saturated system can increase rendezvous latency.
          // Blocking or yielding incur their own penalties in the form of context switching
          // and the resultant loss of $ residency.
          //
          // Further complicating matters is that yield() does not work as naively expected
          // on many platforms -- yield() does not guarantee that any other ready threads
          // will run.   As such we revert to naked_short_sleep() after some number of iterations.
          // nakes_short_sleep() is implemented as a short unconditional sleep.
          // Typical operating systems round a "short" sleep period up to 10 msecs, so sleeping
          // can actually increase the time it takes the VM thread to detect that a system-wide
          // stop-the-world safepoint has been reached.  In a pathological scenario such as that
          // described in CR6415670 the VMthread may sleep just before the mutator(s) become safe.
          // In that case the mutators will be stalled waiting for the safepoint to complete and the
          // the VMthread will be sleeping, waiting for the mutators to rendezvous.  The VMthread
          // will eventually wake up and detect that all mutators are safe, at which point
          // we'll again make progress.
          //
          // Beware too that that the VMThread typically runs at elevated priority.
          // Its default priority is higher than the default mutator priority.
          // Obviously, this complicates spinning.
          //
          // Note too that on Windows XP SwitchThreadTo() has quite different behavior than Sleep(0).
          // Sleep(0) will _not yield to lower priority threads, while SwitchThreadTo() will.
          //
          // See the comments in synchronizer.cpp for additional remarks on spinning.
          //
          // In the future we might:
          // -- Modify the safepoint scheme to avoid potentially unbounded spinning.
          //    This is tricky as the path used by a thread exiting the JVM (say on
          //    on JNI call-out) simply stores into its state field.  The burden
          //    is placed on the VM thread, which must poll (spin).
          // -- Find something useful to do while spinning.  If the safepoint is GC-related
          //    we might aggressively scan the stacks of threads that are already safe.
          // -- YieldTo() any still-running mutators that are ready but OFFPROC.
          // -- Check system saturation.  If the system is not fully saturated then
          //    simply spin and avoid sleep/yield.
          // -- As still-running mutators rendezvous they could unpark the sleeping
          //    VMthread.  This works well for still-running mutators that become
          //    safe.  The VMthread must still poll for mutators that call-out.
          // -- Drive the policy on time-since-begin instead of iterations.
          // -- Consider making the spin duration a function of the # of CPUs:
          //    Spin = (((ncpus-1) * M) + K) + F(still_running)
          //    Alternately, instead of counting iterations of the outer loop
          //    we could count the # of threads visited in the inner loop, above.
          // -- On windows consider using the return value from SwitchThreadTo()
          //    to drive subsequent spin/SwitchThreadTo()/Sleep(N) decisions.

          if (int(iterations) == -1) { // overflow - something is wrong.
            // We can only overflow here when we are using global
            // polling pages. We keep this guarantee in its original
            // form so that searches of the bug database for this
            // failure mode find the right bugs.
            guarantee (PageArmed == 0, "invariant");
          }

          // Instead of (ncpus > 1) consider either (still_running < (ncpus + EPSILON)) or
          // ((still_running + _waiting_to_block - TryingToBlock)) < ncpus)
          ++steps ;
          if (ncpus > 1 && steps < safepoint_spin_before_yield) {
            SpinPause() ;     // MP-Polite spin
          } else
            if (steps < _defer_thr_suspend_loop_count) {
              os::naked_yield() ;
            } else {
              os::naked_short_sleep(1);
            }

          iterations ++ ;
        }
        assert(iterations < (uint)max_jint, "We have been iterating in the safepoint loop too long");
      }
    } // ThreadsListHandle destroyed here.
    assert(still_running == 0, "sanity check");

    if (log_is_enabled(Debug, safepoint, stats)) {
      update_statistics_on_spin_end();
    }
    if (sync_event.should_commit()) {
      post_safepoint_synchronize_event(&sync_event, initial_running, _waiting_to_block, iterations);
    }
  }

  // wait until all threads are stopped
  {
    EventSafepointWaitBlocked wait_blocked_event;
    int initial_waiting_to_block = _waiting_to_block;

    while (_waiting_to_block > 0) {
      log_debug(safepoint)("Waiting for %d thread(s) to block", _waiting_to_block);
      if (!SafepointTimeout || timeout_error_printed) {
        Safepoint_lock->wait(true);  // true, means with no safepoint checks
      } else {
        // Compute remaining time
        jlong remaining_time = safepoint_limit_time - os::javaTimeNanos();

        // If there is no remaining time, then there is an error
        if (remaining_time < 0 || Safepoint_lock->wait(true, remaining_time / MICROUNITS)) {
          print_safepoint_timeout(_blocking_timeout);
        }
      }
    }
    assert(_waiting_to_block == 0, "sanity check");

#ifndef PRODUCT
    if (SafepointTimeout) {
      jlong current_time = os::javaTimeNanos();
      if (safepoint_limit_time < current_time) {
        log_warning(safepoint)("# SafepointSynchronize: Finished after "
                      INT64_FORMAT_W(6) " ms",
                      (int64_t)((current_time - safepoint_limit_time) / MICROUNITS +
                                (jlong)SafepointTimeoutDelay));
      }
    }
#endif

    assert((_safepoint_counter & 0x1) == 0, "must be even");
    assert(Threads_lock->owned_by_self(), "must hold Threads_lock");
    _safepoint_counter ++;

    // Record state
    _state = _synchronized;

    OrderAccess::fence();
    if (wait_blocked_event.should_commit()) {
      post_safepoint_wait_blocked_event(&wait_blocked_event, initial_waiting_to_block);
    }
  }

#ifdef ASSERT
  // Make sure all the threads were visited.
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *cur = jtiwh.next(); ) {
    assert(cur->was_visited_for_critical_count(), "missed a thread");
  }
#endif // ASSERT

  // Update the count of active JNI critical regions
  GCLocker::set_jni_lock_count(_current_jni_active_count);

  log_info(safepoint)("Entering safepoint region: %s", VMThread::vm_safepoint_description());

  RuntimeService::record_safepoint_synchronized();
  if (log_is_enabled(Debug, safepoint, stats)) {
    update_statistics_on_sync_end(os::javaTimeNanos());
  }

  // Call stuff that needs to be run when a safepoint is just about to be completed
  {
    EventSafepointCleanup cleanup_event;
    do_cleanup_tasks();
    if (cleanup_event.should_commit()) {
      post_safepoint_cleanup_event(&cleanup_event);
    }
  }

  if (log_is_enabled(Debug, safepoint, stats)) {
    // Record how much time spend on the above cleanup tasks
    update_statistics_on_cleanup_end(os::javaTimeNanos());
  }

  if (begin_event.should_commit()) {
    post_safepoint_begin_event(&begin_event, nof_threads, _current_jni_active_count);
  }
}

// Wake up all threads, so they are ready to resume execution after the safepoint
// operation has been carried out
void SafepointSynchronize::end() {
  assert(Threads_lock->owned_by_self(), "must hold Threads_lock");
  assert((_safepoint_counter & 0x1) == 1, "must be odd");
  EventSafepointEnd event;
  _safepoint_counter ++;
  // memory fence isn't required here since an odd _safepoint_counter
  // value can do no harm and a fence is issued below anyway.

  DEBUG_ONLY(Thread* myThread = Thread::current();)
  assert(myThread->is_VM_thread(), "Only VM thread can execute a safepoint");

  if (log_is_enabled(Debug, safepoint, stats)) {
    end_statistics(os::javaTimeNanos());
  }

  {
    JavaThreadIteratorWithHandle jtiwh;
#ifdef ASSERT
    // A pending_exception cannot be installed during a safepoint.  The threads
    // may install an async exception after they come back from a safepoint into
    // pending_exception after they unblock.  But that should happen later.
    for (; JavaThread *cur = jtiwh.next(); ) {
      assert (!(cur->has_pending_exception() &&
                cur->safepoint_state()->is_at_poll_safepoint()),
              "safepoint installed a pending exception");
    }
#endif // ASSERT

    if (PageArmed) {
      assert(SafepointMechanism::uses_global_page_poll(), "sanity");
      // Make polling safepoint aware
      os::make_polling_page_readable();
      PageArmed = 0 ;
    }

    if (SafepointMechanism::uses_global_page_poll()) {
      // Remove safepoint check from interpreter
      Interpreter::ignore_safepoints();
    }

    {
      MutexLocker mu(Safepoint_lock);

      assert(_state == _synchronized, "must be synchronized before ending safepoint synchronization");

      if (SafepointMechanism::uses_thread_local_poll()) {
        _state = _not_synchronized;
        OrderAccess::storestore(); // global state -> local state
        jtiwh.rewind();
        for (; JavaThread *current = jtiwh.next(); ) {
          ThreadSafepointState* cur_state = current->safepoint_state();
          cur_state->restart(); // TSS _running
          SafepointMechanism::disarm_local_poll(current);
        }
        log_info(safepoint)("Leaving safepoint region");
      } else {
        // Set to not synchronized, so the threads will not go into the signal_thread_blocked method
        // when they get restarted.
        _state = _not_synchronized;
        OrderAccess::fence();

        log_info(safepoint)("Leaving safepoint region");

        // Start suspended threads
        jtiwh.rewind();
        for (; JavaThread *current = jtiwh.next(); ) {
          ThreadSafepointState* cur_state = current->safepoint_state();
          assert(cur_state->type() != ThreadSafepointState::_running, "Thread not suspended at safepoint");
          cur_state->restart();
          assert(cur_state->is_running(), "safepoint state has not been reset");
        }
      }

      RuntimeService::record_safepoint_end();

      // Release threads lock, so threads can be created/destroyed again.
      // It will also release all threads blocked in signal_thread_blocked.
      Threads_lock->unlock();
    }
  } // ThreadsListHandle destroyed here.

  Universe::heap()->safepoint_synchronize_end();
  // record this time so VMThread can keep track how much time has elapsed
  // since last safepoint.
  _end_of_last_safepoint = os::javaTimeMillis();
  if (event.should_commit()) {
    post_safepoint_end_event(&event);
  }
}

bool SafepointSynchronize::is_cleanup_needed() {
  // Need a safepoint if there are many monitors to deflate.
  if (ObjectSynchronizer::is_cleanup_needed()) return true;
  // Need a safepoint if some inline cache buffers is non-empty
  if (!InlineCacheBuffer::is_empty()) return true;
  return false;
}

class ParallelSPCleanupThreadClosure : public ThreadClosure {
private:
  CodeBlobClosure* _nmethod_cl;
  DeflateMonitorCounters* _counters;

public:
  ParallelSPCleanupThreadClosure(DeflateMonitorCounters* counters) :
    _nmethod_cl(UseCodeAging ? NMethodSweeper::prepare_reset_hotness_counters() : NULL),
    _counters(counters) {}

  void do_thread(Thread* thread) {
    ObjectSynchronizer::deflate_thread_local_monitors(thread, _counters);
    if (_nmethod_cl != NULL && thread->is_Java_thread() &&
        ! thread->is_Code_cache_sweeper_thread()) {
      JavaThread* jt = (JavaThread*) thread;
      jt->nmethods_do(_nmethod_cl);
    }
  }
};

class ParallelSPCleanupTask : public AbstractGangTask {
private:
  SubTasksDone _subtasks;
  ParallelSPCleanupThreadClosure _cleanup_threads_cl;
  uint _num_workers;
  DeflateMonitorCounters* _counters;
public:
  ParallelSPCleanupTask(uint num_workers, DeflateMonitorCounters* counters) :
    AbstractGangTask("Parallel Safepoint Cleanup"),
    _subtasks(SubTasksDone(SafepointSynchronize::SAFEPOINT_CLEANUP_NUM_TASKS)),
    _cleanup_threads_cl(ParallelSPCleanupThreadClosure(counters)),
    _num_workers(num_workers),
    _counters(counters) {}

  void work(uint worker_id) {
    // All threads deflate monitors and mark nmethods (if necessary).
    Threads::possibly_parallel_threads_do(true, &_cleanup_threads_cl);

    if (_subtasks.try_claim_task(SafepointSynchronize::SAFEPOINT_CLEANUP_DEFLATE_MONITORS)) {
      const char* name = "deflating global idle monitors";
      EventSafepointCleanupTask event;
      TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
      ObjectSynchronizer::deflate_idle_monitors(_counters);
      if (event.should_commit()) {
        post_safepoint_cleanup_task_event(&event, name);
      }
    }

    if (_subtasks.try_claim_task(SafepointSynchronize::SAFEPOINT_CLEANUP_UPDATE_INLINE_CACHES)) {
      const char* name = "updating inline caches";
      EventSafepointCleanupTask event;
      TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
      InlineCacheBuffer::update_inline_caches();
      if (event.should_commit()) {
        post_safepoint_cleanup_task_event(&event, name);
      }
    }

    if (_subtasks.try_claim_task(SafepointSynchronize::SAFEPOINT_CLEANUP_COMPILATION_POLICY)) {
      const char* name = "compilation policy safepoint handler";
      EventSafepointCleanupTask event;
      TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
      CompilationPolicy::policy()->do_safepoint_work();
      if (event.should_commit()) {
        post_safepoint_cleanup_task_event(&event, name);
      }
    }

    if (_subtasks.try_claim_task(SafepointSynchronize::SAFEPOINT_CLEANUP_SYMBOL_TABLE_REHASH)) {
      if (SymbolTable::needs_rehashing()) {
        const char* name = "rehashing symbol table";
        EventSafepointCleanupTask event;
        TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
        SymbolTable::rehash_table();
        if (event.should_commit()) {
          post_safepoint_cleanup_task_event(&event, name);
        }
      }
    }

    if (_subtasks.try_claim_task(SafepointSynchronize::SAFEPOINT_CLEANUP_STRING_TABLE_REHASH)) {
      if (StringTable::needs_rehashing()) {
        const char* name = "rehashing string table";
        EventSafepointCleanupTask event;
        TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
        StringTable::rehash_table();
        if (event.should_commit()) {
          post_safepoint_cleanup_task_event(&event, name);
        }
      }
    }

    if (_subtasks.try_claim_task(SafepointSynchronize::SAFEPOINT_CLEANUP_CLD_PURGE)) {
      // CMS delays purging the CLDG until the beginning of the next safepoint and to
      // make sure concurrent sweep is done
      const char* name = "purging class loader data graph";
      EventSafepointCleanupTask event;
      TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
      ClassLoaderDataGraph::purge_if_needed();
      if (event.should_commit()) {
        post_safepoint_cleanup_task_event(&event, name);
      }
    }

    if (_subtasks.try_claim_task(SafepointSynchronize::SAFEPOINT_CLEANUP_SYSTEM_DICTIONARY_RESIZE)) {
      const char* name = "resizing system dictionaries";
      EventSafepointCleanupTask event;
      TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
      ClassLoaderDataGraph::resize_if_needed();
      if (event.should_commit()) {
        post_safepoint_cleanup_task_event(&event, name);
      }
    }

    _subtasks.all_tasks_completed(_num_workers);
  }
};

// Various cleaning tasks that should be done periodically at safepoints.
void SafepointSynchronize::do_cleanup_tasks() {

  TraceTime timer("safepoint cleanup tasks", TRACETIME_LOG(Info, safepoint, cleanup));

  // Prepare for monitor deflation.
  DeflateMonitorCounters deflate_counters;
  ObjectSynchronizer::prepare_deflate_idle_monitors(&deflate_counters);

  CollectedHeap* heap = Universe::heap();
  assert(heap != NULL, "heap not initialized yet?");
  WorkGang* cleanup_workers = heap->get_safepoint_workers();
  if (cleanup_workers != NULL) {
    // Parallel cleanup using GC provided thread pool.
    uint num_cleanup_workers = cleanup_workers->active_workers();
    ParallelSPCleanupTask cleanup(num_cleanup_workers, &deflate_counters);
    StrongRootsScope srs(num_cleanup_workers);
    cleanup_workers->run_task(&cleanup);
  } else {
    // Serial cleanup using VMThread.
    ParallelSPCleanupTask cleanup(1, &deflate_counters);
    StrongRootsScope srs(1);
    cleanup.work(0);
  }

  // Needs to be done single threaded by the VMThread.  This walks
  // the thread stacks looking for references to metadata before
  // deciding to remove it from the metaspaces.
  if (ClassLoaderDataGraph::should_clean_metaspaces_and_reset()) {
    const char* name = "cleanup live ClassLoaderData metaspaces";
    TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
    ClassLoaderDataGraph::walk_metadata_and_clean_metaspaces();
  }

  // Finish monitor deflation.
  ObjectSynchronizer::finish_deflate_idle_monitors(&deflate_counters);

  assert(InlineCacheBuffer::is_empty(), "should have cleaned up ICBuffer");
}


bool SafepointSynchronize::safepoint_safe(JavaThread *thread, JavaThreadState state) {
  switch(state) {
  case _thread_in_native:
    // native threads are safe if they have no java stack or have walkable stack
    return !thread->has_last_Java_frame() || thread->frame_anchor()->walkable();

   // blocked threads should have already have walkable stack
  case _thread_blocked:
    assert(!thread->has_last_Java_frame() || thread->frame_anchor()->walkable(), "blocked and not walkable");
    return true;

  default:
    return false;
  }
}


// See if the thread is running inside a lazy critical native and
// update the thread critical count if so.  Also set a suspend flag to
// cause the native wrapper to return into the JVM to do the unlock
// once the native finishes.
void SafepointSynchronize::check_for_lazy_critical_native(JavaThread *thread, JavaThreadState state) {
  if (state == _thread_in_native &&
      thread->has_last_Java_frame() &&
      thread->frame_anchor()->walkable()) {
    // This thread might be in a critical native nmethod so look at
    // the top of the stack and increment the critical count if it
    // is.
    frame wrapper_frame = thread->last_frame();
    CodeBlob* stub_cb = wrapper_frame.cb();
    if (stub_cb != NULL &&
        stub_cb->is_nmethod() &&
        stub_cb->as_nmethod_or_null()->is_lazy_critical_native()) {
      // A thread could potentially be in a critical native across
      // more than one safepoint, so only update the critical state on
      // the first one.  When it returns it will perform the unlock.
      if (!thread->do_critical_native_unlock()) {
#ifdef ASSERT
        if (!thread->in_critical()) {
          GCLocker::increment_debug_jni_lock_count();
        }
#endif
        thread->enter_critical();
        // Make sure the native wrapper calls back on return to
        // perform the needed critical unlock.
        thread->set_critical_native_unlock();
      }
    }
  }
}



// -------------------------------------------------------------------------------------------------------
// Implementation of Safepoint callback point

void SafepointSynchronize::block(JavaThread *thread) {
  assert(thread != NULL, "thread must be set");
  assert(thread->is_Java_thread(), "not a Java thread");

  // Threads shouldn't block if they are in the middle of printing, but...
  ttyLocker::break_tty_lock_for_safepoint(os::current_thread_id());

  // Only bail from the block() call if the thread is gone from the
  // thread list; starting to exit should still block.
  if (thread->is_terminated()) {
     // block current thread if we come here from native code when VM is gone
     thread->block_if_vm_exited();

     // otherwise do nothing
     return;
  }

  JavaThreadState state = thread->thread_state();
  thread->frame_anchor()->make_walkable(thread);

  // Check that we have a valid thread_state at this point
  switch(state) {
    case _thread_in_vm_trans:
    case _thread_in_Java:        // From compiled code

      // We are highly likely to block on the Safepoint_lock. In order to avoid blocking in this case,
      // we pretend we are still in the VM.
      thread->set_thread_state(_thread_in_vm);

      if (is_synchronizing()) {
         Atomic::inc (&TryingToBlock) ;
      }

      // We will always be holding the Safepoint_lock when we are examine the state
      // of a thread. Hence, the instructions between the Safepoint_lock->lock() and
      // Safepoint_lock->unlock() are happening atomic with regards to the safepoint code
      Safepoint_lock->lock_without_safepoint_check();
      if (is_synchronizing()) {
        // Decrement the number of threads to wait for and signal vm thread
        assert(_waiting_to_block > 0, "sanity check");
        _waiting_to_block--;
        thread->safepoint_state()->set_has_called_back(true);

        DEBUG_ONLY(thread->set_visited_for_critical_count(true));
        if (thread->in_critical()) {
          // Notice that this thread is in a critical section
          increment_jni_active_count();
        }

        // Consider (_waiting_to_block < 2) to pipeline the wakeup of the VM thread
        if (_waiting_to_block == 0) {
          Safepoint_lock->notify_all();
        }
      }

      // We transition the thread to state _thread_blocked here, but
      // we can't do our usual check for external suspension and then
      // self-suspend after the lock_without_safepoint_check() call
      // below because we are often called during transitions while
      // we hold different locks. That would leave us suspended while
      // holding a resource which results in deadlocks.
      thread->set_thread_state(_thread_blocked);
      Safepoint_lock->unlock();

      // We now try to acquire the threads lock. Since this lock is hold by the VM thread during
      // the entire safepoint, the threads will all line up here during the safepoint.
      Threads_lock->lock_without_safepoint_check();
      // restore original state. This is important if the thread comes from compiled code, so it
      // will continue to execute with the _thread_in_Java state.
      thread->set_thread_state(state);
      Threads_lock->unlock();
      break;

    case _thread_in_native_trans:
    case _thread_blocked_trans:
    case _thread_new_trans:
      if (thread->safepoint_state()->type() == ThreadSafepointState::_call_back) {
        thread->print_thread_state();
        fatal("Deadlock in safepoint code.  "
              "Should have called back to the VM before blocking.");
      }

      // We transition the thread to state _thread_blocked here, but
      // we can't do our usual check for external suspension and then
      // self-suspend after the lock_without_safepoint_check() call
      // below because we are often called during transitions while
      // we hold different locks. That would leave us suspended while
      // holding a resource which results in deadlocks.
      thread->set_thread_state(_thread_blocked);

      // It is not safe to suspend a thread if we discover it is in _thread_in_native_trans. Hence,
      // the safepoint code might still be waiting for it to block. We need to change the state here,
      // so it can see that it is at a safepoint.

      // Block until the safepoint operation is completed.
      Threads_lock->lock_without_safepoint_check();

      // Restore state
      thread->set_thread_state(state);

      Threads_lock->unlock();
      break;

    default:
     fatal("Illegal threadstate encountered: %d", state);
  }

  // Check for pending. async. exceptions or suspends - except if the
  // thread was blocked inside the VM. has_special_runtime_exit_condition()
  // is called last since it grabs a lock and we only want to do that when
  // we must.
  //
  // Note: we never deliver an async exception at a polling point as the
  // compiler may not have an exception handler for it. The polling
  // code will notice the async and deoptimize and the exception will
  // be delivered. (Polling at a return point is ok though). Sure is
  // a lot of bother for a deprecated feature...
  //
  // We don't deliver an async exception if the thread state is
  // _thread_in_native_trans so JNI functions won't be called with
  // a surprising pending exception. If the thread state is going back to java,
  // async exception is checked in check_special_condition_for_native_trans().

  if (state != _thread_blocked_trans &&
      state != _thread_in_vm_trans &&
      thread->has_special_runtime_exit_condition()) {
    thread->handle_special_runtime_exit_condition(
      !thread->is_at_poll_safepoint() && (state != _thread_in_native_trans));
  }
}

// ------------------------------------------------------------------------------------------------------
// Exception handlers


void SafepointSynchronize::handle_polling_page_exception(JavaThread *thread) {
  assert(thread->is_Java_thread(), "polling reference encountered by VM thread");
  assert(thread->thread_state() == _thread_in_Java, "should come from Java code");
  if (!ThreadLocalHandshakes) {
    assert(SafepointSynchronize::is_synchronizing(), "polling encountered outside safepoint synchronization");
  }

  if (log_is_enabled(Debug, safepoint, stats)) {
    Atomic::inc(&_nof_threads_hit_polling_page);
  }

  ThreadSafepointState* state = thread->safepoint_state();

  state->handle_polling_page_exception();
}


void SafepointSynchronize::print_safepoint_timeout(SafepointTimeoutReason reason) {
  if (!timeout_error_printed) {
    timeout_error_printed = true;
    // Print out the thread info which didn't reach the safepoint for debugging
    // purposes (useful when there are lots of threads in the debugger).
    LogTarget(Warning, safepoint) lt;
    if (lt.is_enabled()) {
      ResourceMark rm;
      LogStream ls(lt);

      ls.cr();
      ls.print_cr("# SafepointSynchronize::begin: Timeout detected:");
      if (reason ==  _spinning_timeout) {
        ls.print_cr("# SafepointSynchronize::begin: Timed out while spinning to reach a safepoint.");
      } else if (reason == _blocking_timeout) {
        ls.print_cr("# SafepointSynchronize::begin: Timed out while waiting for threads to stop.");
      }

      ls.print_cr("# SafepointSynchronize::begin: Threads which did not reach the safepoint:");
      ThreadSafepointState *cur_state;
      for (JavaThreadIteratorWithHandle jtiwh; JavaThread *cur_thread = jtiwh.next(); ) {
        cur_state = cur_thread->safepoint_state();

        if (cur_thread->thread_state() != _thread_blocked &&
          ((reason == _spinning_timeout && cur_state->is_running()) ||
             (reason == _blocking_timeout && !cur_state->has_called_back()))) {
          ls.print("# ");
          cur_thread->print_on(&ls);
          ls.cr();
        }
      }
      ls.print_cr("# SafepointSynchronize::begin: (End of list)");
    }
  }

  // To debug the long safepoint, specify both AbortVMOnSafepointTimeout &
  // ShowMessageBoxOnError.
  if (AbortVMOnSafepointTimeout) {
    fatal("Safepoint sync time longer than " INTX_FORMAT "ms detected when executing %s.",
          SafepointTimeoutDelay, VMThread::vm_safepoint_description());
  }
}


// -------------------------------------------------------------------------------------------------------
// Implementation of ThreadSafepointState

ThreadSafepointState::ThreadSafepointState(JavaThread *thread) {
  _thread = thread;
  _type   = _running;
  _has_called_back = false;
  _at_poll_safepoint = false;
}

void ThreadSafepointState::create(JavaThread *thread) {
  ThreadSafepointState *state = new ThreadSafepointState(thread);
  thread->set_safepoint_state(state);
}

void ThreadSafepointState::destroy(JavaThread *thread) {
  if (thread->safepoint_state()) {
    delete(thread->safepoint_state());
    thread->set_safepoint_state(NULL);
  }
}

void ThreadSafepointState::examine_state_of_thread() {
  assert(is_running(), "better be running or just have hit safepoint poll");

  JavaThreadState state = _thread->thread_state();

  // Save the state at the start of safepoint processing.
  _orig_thread_state = state;

  // Check for a thread that is suspended. Note that thread resume tries
  // to grab the Threads_lock which we own here, so a thread cannot be
  // resumed during safepoint synchronization.

  // We check to see if this thread is suspended without locking to
  // avoid deadlocking with a third thread that is waiting for this
  // thread to be suspended. The third thread can notice the safepoint
  // that we're trying to start at the beginning of its SR_lock->wait()
  // call. If that happens, then the third thread will block on the
  // safepoint while still holding the underlying SR_lock. We won't be
  // able to get the SR_lock and we'll deadlock.
  //
  // We don't need to grab the SR_lock here for two reasons:
  // 1) The suspend flags are both volatile and are set with an
  //    Atomic::cmpxchg() call so we should see the suspended
  //    state right away.
  // 2) We're being called from the safepoint polling loop; if
  //    we don't see the suspended state on this iteration, then
  //    we'll come around again.
  //
  bool is_suspended = _thread->is_ext_suspended();
  if (is_suspended) {
    roll_forward(_at_safepoint);
    return;
  }

  // Some JavaThread states have an initial safepoint state of
  // running, but are actually at a safepoint. We will happily
  // agree and update the safepoint state here.
  if (SafepointSynchronize::safepoint_safe(_thread, state)) {
    SafepointSynchronize::check_for_lazy_critical_native(_thread, state);
    roll_forward(_at_safepoint);
    return;
  }

  if (state == _thread_in_vm) {
    roll_forward(_call_back);
    return;
  }

  // All other thread states will continue to run until they
  // transition and self-block in state _blocked
  // Safepoint polling in compiled code causes the Java threads to do the same.
  // Note: new threads may require a malloc so they must be allowed to finish

  assert(is_running(), "examine_state_of_thread on non-running thread");
  return;
}

// Returns true is thread could not be rolled forward at present position.
void ThreadSafepointState::roll_forward(suspend_type type) {
  _type = type;

  switch(_type) {
    case _at_safepoint:
      SafepointSynchronize::signal_thread_at_safepoint();
      DEBUG_ONLY(_thread->set_visited_for_critical_count(true));
      if (_thread->in_critical()) {
        // Notice that this thread is in a critical section
        SafepointSynchronize::increment_jni_active_count();
      }
      break;

    case _call_back:
      set_has_called_back(false);
      break;

    case _running:
    default:
      ShouldNotReachHere();
  }
}

void ThreadSafepointState::restart() {
  switch(type()) {
    case _at_safepoint:
    case _call_back:
      break;

    case _running:
    default:
       tty->print_cr("restart thread " INTPTR_FORMAT " with state %d",
                     p2i(_thread), _type);
       _thread->print();
      ShouldNotReachHere();
  }
  _type = _running;
  set_has_called_back(false);
}


void ThreadSafepointState::print_on(outputStream *st) const {
  const char *s = NULL;

  switch(_type) {
    case _running                : s = "_running";              break;
    case _at_safepoint           : s = "_at_safepoint";         break;
    case _call_back              : s = "_call_back";            break;
    default:
      ShouldNotReachHere();
  }

  st->print_cr("Thread: " INTPTR_FORMAT
              "  [0x%2x] State: %s _has_called_back %d _at_poll_safepoint %d",
               p2i(_thread), _thread->osthread()->thread_id(), s, _has_called_back,
               _at_poll_safepoint);

  _thread->print_thread_state_on(st);
}

// ---------------------------------------------------------------------------------------------------------------------

// Block the thread at poll or poll return for safepoint/handshake.
void ThreadSafepointState::handle_polling_page_exception() {

  // Check state.  block() will set thread state to thread_in_vm which will
  // cause the safepoint state _type to become _call_back.
  suspend_type t = type();
  assert(!SafepointMechanism::uses_global_page_poll() || t == ThreadSafepointState::_running,
         "polling page exception on thread not running state: %u", uint(t));

  // Step 1: Find the nmethod from the return address
  address real_return_addr = thread()->saved_exception_pc();

  CodeBlob *cb = CodeCache::find_blob(real_return_addr);
  assert(cb != NULL && cb->is_compiled(), "return address should be in nmethod");
  CompiledMethod* nm = (CompiledMethod*)cb;

  // Find frame of caller
  frame stub_fr = thread()->last_frame();
  CodeBlob* stub_cb = stub_fr.cb();
  assert(stub_cb->is_safepoint_stub(), "must be a safepoint stub");
  RegisterMap map(thread(), true);
  frame caller_fr = stub_fr.sender(&map);

  // Should only be poll_return or poll
  assert( nm->is_at_poll_or_poll_return(real_return_addr), "should not be at call" );

  // This is a poll immediately before a return. The exception handling code
  // has already had the effect of causing the return to occur, so the execution
  // will continue immediately after the call. In addition, the oopmap at the
  // return point does not mark the return value as an oop (if it is), so
  // it needs a handle here to be updated.
  if( nm->is_at_poll_return(real_return_addr) ) {
    // See if return type is an oop.
    bool return_oop = nm->method()->is_returning_oop();
    Handle return_value;
    if (return_oop) {
      // The oop result has been saved on the stack together with all
      // the other registers. In order to preserve it over GCs we need
      // to keep it in a handle.
      oop result = caller_fr.saved_oop_result(&map);
      assert(oopDesc::is_oop_or_null(result), "must be oop");
      return_value = Handle(thread(), result);
      assert(Universe::heap()->is_in_or_null(result), "must be heap pointer");
    }

    // Block the thread
    SafepointMechanism::block_if_requested(thread());

    // restore oop result, if any
    if (return_oop) {
      caller_fr.set_saved_oop_result(&map, return_value());
    }
  }

  // This is a safepoint poll. Verify the return address and block.
  else {
    set_at_poll_safepoint(true);

    // verify the blob built the "return address" correctly
    assert(real_return_addr == caller_fr.pc(), "must match");

    // Block the thread
    SafepointMechanism::block_if_requested(thread());
    set_at_poll_safepoint(false);

    // If we have a pending async exception deoptimize the frame
    // as otherwise we may never deliver it.
    if (thread()->has_async_condition()) {
      ThreadInVMfromJavaNoAsyncException __tiv(thread());
      Deoptimization::deoptimize_frame(thread(), caller_fr.id());
    }

    // If an exception has been installed we must check for a pending deoptimization
    // Deoptimize frame if exception has been thrown.

    if (thread()->has_pending_exception() ) {
      RegisterMap map(thread(), true);
      frame caller_fr = stub_fr.sender(&map);
      if (caller_fr.is_deoptimized_frame()) {
        // The exception patch will destroy registers that are still
        // live and will be needed during deoptimization. Defer the
        // Async exception should have deferred the exception until the
        // next safepoint which will be detected when we get into
        // the interpreter so if we have an exception now things
        // are messed up.

        fatal("Exception installed and deoptimization is pending");
      }
    }
  }
}


//
//                     Statistics & Instrumentations
//
struct SafepointStats {
    float  _time_stamp;                        // record when the current safepoint occurs in seconds
    int    _vmop_type;                         // tyep of VM operation triggers the safepoint
    int    _nof_total_threads;                 // total number of Java threads
    int    _nof_initial_running_threads;       // total number of initially seen running threads
    int    _nof_threads_wait_to_block;         // total number of threads waiting for to block
    bool   _page_armed;                        // true if polling page is armed, false otherwise
    int _nof_threads_hit_page_trap;            // total number of threads hitting the page trap
    jlong  _time_to_spin;                      // total time in millis spent in spinning
    jlong  _time_to_wait_to_block;             // total time in millis spent in waiting for to block
    jlong  _time_to_do_cleanups;               // total time in millis spent in performing cleanups
    jlong  _time_to_sync;                      // total time in millis spent in getting to _synchronized
    jlong  _time_to_exec_vmop;                 // total time in millis spent in vm operation itself
};

static const int _statistics_header_count = 30;
static int _cur_stat_index = 0;
static SafepointStats safepoint_stats = {0};  // zero initialize
static SafepointStats* spstat = &safepoint_stats;

static julong _safepoint_reasons[VM_Operation::VMOp_Terminating];
static jlong  _max_sync_time = 0;
static jlong  _max_vmop_time = 0;

static jlong  cleanup_end_time = 0;

void SafepointSynchronize::begin_statistics(int nof_threads, int nof_running) {

  spstat->_time_stamp = _ts_of_current_safepoint;

  VM_Operation *op = VMThread::vm_operation();
  spstat->_vmop_type = op != NULL ? op->type() : VM_Operation::VMOp_None;
  _safepoint_reasons[spstat->_vmop_type]++;

  spstat->_nof_total_threads = nof_threads;
  spstat->_nof_initial_running_threads = nof_running;

  // Records the start time of spinning. The real time spent on spinning
  // will be adjusted when spin is done. Same trick is applied for time
  // spent on waiting for threads to block.
  if (nof_running != 0) {
    spstat->_time_to_spin = os::javaTimeNanos();
  }  else {
    spstat->_time_to_spin = 0;
  }
}

void SafepointSynchronize::update_statistics_on_spin_end() {
  jlong cur_time = os::javaTimeNanos();

  spstat->_nof_threads_wait_to_block = _waiting_to_block;
  if (spstat->_nof_initial_running_threads != 0) {
    spstat->_time_to_spin = cur_time - spstat->_time_to_spin;
  }

  // Records the start time of waiting for to block. Updated when block is done.
  if (_waiting_to_block != 0) {
    spstat->_time_to_wait_to_block = cur_time;
  } else {
    spstat->_time_to_wait_to_block = 0;
  }
}

void SafepointSynchronize::update_statistics_on_sync_end(jlong end_time) {

  if (spstat->_nof_threads_wait_to_block != 0) {
    spstat->_time_to_wait_to_block = end_time -
      spstat->_time_to_wait_to_block;
  }

  // Records the end time of sync which will be used to calculate the total
  // vm operation time. Again, the real time spending in syncing will be deducted
  // from the start of the sync time later when end_statistics is called.
  spstat->_time_to_sync = end_time - _safepoint_begin_time;
  if (spstat->_time_to_sync > _max_sync_time) {
    _max_sync_time = spstat->_time_to_sync;
  }

  spstat->_time_to_do_cleanups = end_time;
}

void SafepointSynchronize::update_statistics_on_cleanup_end(jlong end_time) {

  // Record how long spent in cleanup tasks.
  spstat->_time_to_do_cleanups = end_time - spstat->_time_to_do_cleanups;
  cleanup_end_time = end_time;
}

void SafepointSynchronize::end_statistics(jlong vmop_end_time) {

  // Update the vm operation time.
  spstat->_time_to_exec_vmop = vmop_end_time -  cleanup_end_time;
  if (spstat->_time_to_exec_vmop > _max_vmop_time) {
    _max_vmop_time = spstat->_time_to_exec_vmop;
  }

  spstat->_nof_threads_hit_page_trap = _nof_threads_hit_polling_page;

  print_statistics();
}

// Helper method to print the header.
static void print_header(outputStream* st) {
  // The number of spaces is significant here, and should match the format
  // specifiers in print_statistics().

  st->print("          vmop                            "
            "[ threads:    total initially_running wait_to_block ]"
            "[ time:    spin   block    sync cleanup    vmop ] ");

  st->print_cr("page_trap_count");
}

// This prints a nice table.  To get the statistics to not shift due to the logging uptime
// decorator, use the option as: -Xlog:safepoint+stats=debug:[outputfile]:none
void SafepointSynchronize::print_statistics() {
  LogTarget(Debug, safepoint, stats) lt;
  assert (lt.is_enabled(), "should only be called when printing statistics is enabled");
  LogStream ls(lt);

  // Print header every 30 entries
  if ((_cur_stat_index % _statistics_header_count) == 0) {
    print_header(&ls);
    _cur_stat_index = 1;  // wrap
  } else {
    _cur_stat_index++;
  }

  ls.print("%8.3f: ", spstat->_time_stamp);
  ls.print("%-28s  [          "
           INT32_FORMAT_W(8) " " INT32_FORMAT_W(17) " " INT32_FORMAT_W(13) " "
           "]",
           VM_Operation::name(spstat->_vmop_type),
           spstat->_nof_total_threads,
           spstat->_nof_initial_running_threads,
           spstat->_nof_threads_wait_to_block);
  // "/ MICROUNITS " is to convert the unit from nanos to millis.
  ls.print("[       "
           INT64_FORMAT_W(7) " " INT64_FORMAT_W(7) " "
           INT64_FORMAT_W(7) " " INT64_FORMAT_W(7) " "
           INT64_FORMAT_W(7) " ] ",
           (int64_t)(spstat->_time_to_spin / MICROUNITS),
           (int64_t)(spstat->_time_to_wait_to_block / MICROUNITS),
           (int64_t)(spstat->_time_to_sync / MICROUNITS),
           (int64_t)(spstat->_time_to_do_cleanups / MICROUNITS),
           (int64_t)(spstat->_time_to_exec_vmop / MICROUNITS));

  ls.print_cr(INT32_FORMAT_W(15) " ", spstat->_nof_threads_hit_page_trap);
}

// This method will be called when VM exits. This tries to summarize the sampling.
// Current thread may already be deleted, so don't use ResourceMark.
void SafepointSynchronize::print_stat_on_exit() {

  for (int index = 0; index < VM_Operation::VMOp_Terminating; index++) {
    if (_safepoint_reasons[index] != 0) {
      log_debug(safepoint, stats)("%-28s" UINT64_FORMAT_W(10), VM_Operation::name(index),
                _safepoint_reasons[index]);
    }
  }

  log_debug(safepoint, stats)("VM operations coalesced during safepoint " INT64_FORMAT,
                              _coalesced_vmop_count);
  log_debug(safepoint, stats)("Maximum sync time  " INT64_FORMAT" ms",
                              (int64_t)(_max_sync_time / MICROUNITS));
  log_debug(safepoint, stats)("Maximum vm operation time (except for Exit VM operation)  "
                              INT64_FORMAT " ms",
                              (int64_t)(_max_vmop_time / MICROUNITS));
}
