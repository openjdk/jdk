/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "code/nmethod.hpp"
#include "code/pcDesc.hpp"
#include "code/scopeDesc.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/sweeper.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/timerTrace.hpp"
#include "services/runtimeService.hpp"
#include "trace/tracing.hpp"
#include "trace/traceMacros.hpp"
#include "utilities/events.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc/cms/concurrentMarkSweepThread.hpp"
#include "gc/g1/suspendibleThreadSet.hpp"
#endif // INCLUDE_ALL_GCS
#ifdef COMPILER1
#include "c1/c1_globals.hpp"
#endif

// --------------------------------------------------------------------------------------------------
// Implementation of Safepoint begin/end

SafepointSynchronize::SynchronizeState volatile SafepointSynchronize::_state = SafepointSynchronize::_not_synchronized;
volatile int  SafepointSynchronize::_waiting_to_block = 0;
volatile int SafepointSynchronize::_safepoint_counter = 0;
int SafepointSynchronize::_current_jni_active_count = 0;
long  SafepointSynchronize::_end_of_last_safepoint = 0;
static volatile int PageArmed = 0 ;        // safepoint polling page is RO|RW vs PROT_NONE
static volatile int TryingToBlock = 0 ;    // proximate value -- for advisory use only
static bool timeout_error_printed = false;

// Roll all threads forward to a safepoint and suspend them all
void SafepointSynchronize::begin() {
  EventSafepointBegin begin_event;
  Thread* myThread = Thread::current();
  assert(myThread->is_VM_thread(), "Only VM thread may execute a safepoint");

  if (PrintSafepointStatistics || PrintSafepointStatisticsTimeout > 0) {
    _safepoint_begin_time = os::javaTimeNanos();
    _ts_of_current_safepoint = tty->time_stamp().seconds();
  }

#if INCLUDE_ALL_GCS
  if (UseConcMarkSweepGC) {
    // In the future we should investigate whether CMS can use the
    // more-general mechanism below.  DLD (01/05).
    ConcurrentMarkSweepThread::synchronize(false);
  } else if (UseG1GC) {
    SuspendibleThreadSet::synchronize();
  }
#endif // INCLUDE_ALL_GCS

  // By getting the Threads_lock, we assure that no threads are about to start or
  // exit. It is released again in SafepointSynchronize::end().
  Threads_lock->lock();

  assert( _state == _not_synchronized, "trying to safepoint synchronize with wrong state");

  int nof_threads = Threads::number_of_threads();

  log_debug(safepoint)("Safepoint synchronization initiated. (%d)", nof_threads);

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

  // PrintSafepointStatisticsTimeout can be specified separately. When
  // specified, PrintSafepointStatistics will be set to true in
  // deferred_initialize_stat method. The initialization has to be done
  // early enough to avoid any races. See bug 6880029 for details.
  if (PrintSafepointStatistics || PrintSafepointStatisticsTimeout > 0) {
    deferred_initialize_stat();
  }

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
  //     the VM thread issues a memory barrier instruction
  //     (on MP systems).  In order to avoid the overhead of issuing
  //     a memory barrier for each Java thread making native calls, each Java
  //     thread performs a write to a single memory page after changing
  //     the thread state.  The VM thread performs a sequence of
  //     mprotect OS calls which forces all previous writes from all
  //     Java threads to be serialized.  This is done in the
  //     os::serialize_thread_states() call.  This has proven to be
  //     much more efficient than executing a membar instruction
  //     on every call to native code.
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
    EventSafepointStateSync sync_event;
    int initial_running = 0;

    _state            = _synchronizing;
    OrderAccess::fence();

    // Flush all thread states to memory
    if (!UseMembar) {
      os::serialize_thread_states();
    }

    // Make interpreter safepoint aware
    Interpreter::notice_safepoints();

    if (DeferPollingPageLoopCount < 0) {
      // Make polling safepoint aware
      guarantee (PageArmed == 0, "invariant") ;
      PageArmed = 1 ;
      os::make_polling_page_unreadable();
    }

    // Consider using active_processor_count() ... but that call is expensive.
    int ncpus = os::processor_count() ;

#ifdef ASSERT
    for (JavaThread *cur = Threads::first(); cur != NULL; cur = cur->next()) {
      assert(cur->safepoint_state()->is_running(), "Illegal initial state");
      // Clear the visited flag to ensure that the critical counts are collected properly.
      cur->set_visited_for_critical_count(false);
    }
#endif // ASSERT

    if (SafepointTimeout)
      safepoint_limit_time = os::javaTimeNanos() + (jlong)SafepointTimeoutDelay * MICROUNITS;

    // Iterate through all threads until it have been determined how to stop them all at a safepoint
    unsigned int iterations = 0;
    int steps = 0 ;
    while(still_running > 0) {
      for (JavaThread *cur = Threads::first(); cur != NULL; cur = cur->next()) {
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
          if (log_is_enabled(Trace, safepoint)) {
            ResourceMark rm;
            cur_state->print_on(Log(safepoint)::trace_stream());
          }
        }
      }

      if (iterations == 0) {
        initial_running = still_running;
        if (PrintSafepointStatistics) {
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
        // 1. Modify the safepoint scheme to avoid potentially unbounded spinning.
        //    This is tricky as the path used by a thread exiting the JVM (say on
        //    on JNI call-out) simply stores into its state field.  The burden
        //    is placed on the VM thread, which must poll (spin).
        // 2. Find something useful to do while spinning.  If the safepoint is GC-related
        //    we might aggressively scan the stacks of threads that are already safe.
        // 3. Use Solaris schedctl to examine the state of the still-running mutators.
        //    If all the mutators are ONPROC there's no reason to sleep or yield.
        // 4. YieldTo() any still-running mutators that are ready but OFFPROC.
        // 5. Check system saturation.  If the system is not fully saturated then
        //    simply spin and avoid sleep/yield.
        // 6. As still-running mutators rendezvous they could unpark the sleeping
        //    VMthread.  This works well for still-running mutators that become
        //    safe.  The VMthread must still poll for mutators that call-out.
        // 7. Drive the policy on time-since-begin instead of iterations.
        // 8. Consider making the spin duration a function of the # of CPUs:
        //    Spin = (((ncpus-1) * M) + K) + F(still_running)
        //    Alternately, instead of counting iterations of the outer loop
        //    we could count the # of threads visited in the inner loop, above.
        // 9. On windows consider using the return value from SwitchThreadTo()
        //    to drive subsequent spin/SwitchThreadTo()/Sleep(N) decisions.

        if (int(iterations) == DeferPollingPageLoopCount) {
          guarantee (PageArmed == 0, "invariant") ;
          PageArmed = 1 ;
          os::make_polling_page_unreadable();
        }

        // Instead of (ncpus > 1) consider either (still_running < (ncpus + EPSILON)) or
        // ((still_running + _waiting_to_block - TryingToBlock)) < ncpus)
        ++steps ;
        if (ncpus > 1 && steps < SafepointSpinBeforeYield) {
          SpinPause() ;     // MP-Polite spin
        } else
          if (steps < DeferThrSuspendLoopCount) {
            os::naked_yield() ;
          } else {
            os::naked_short_sleep(1);
          }

        iterations ++ ;
      }
      assert(iterations < (uint)max_jint, "We have been iterating in the safepoint loop too long");
    }
    assert(still_running == 0, "sanity check");

    if (PrintSafepointStatistics) {
      update_statistics_on_spin_end();
    }

    if (sync_event.should_commit()) {
      sync_event.set_safepointId(safepoint_counter());
      sync_event.set_initialThreadCount(initial_running);
      sync_event.set_runningThreadCount(_waiting_to_block);
      sync_event.set_iterations(iterations);
      sync_event.commit();
    }
  } //EventSafepointStateSync

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
        tty->print_cr("# SafepointSynchronize: Finished after "
                      INT64_FORMAT_W(6) " ms",
                      ((current_time - safepoint_limit_time) / MICROUNITS +
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
      wait_blocked_event.set_safepointId(safepoint_counter());
      wait_blocked_event.set_runningThreadCount(initial_waiting_to_block);
      wait_blocked_event.commit();
    }
  } // EventSafepointWaitBlocked

#ifdef ASSERT
  for (JavaThread *cur = Threads::first(); cur != NULL; cur = cur->next()) {
    // make sure all the threads were visited
    assert(cur->was_visited_for_critical_count(), "missed a thread");
  }
#endif // ASSERT

  // Update the count of active JNI critical regions
  GCLocker::set_jni_lock_count(_current_jni_active_count);

  if (log_is_enabled(Debug, safepoint)) {
    VM_Operation *op = VMThread::vm_operation();
    log_debug(safepoint)("Entering safepoint region: %s",
                         (op != NULL) ? op->name() : "no vm operation");
  }

  RuntimeService::record_safepoint_synchronized();
  if (PrintSafepointStatistics) {
    update_statistics_on_sync_end(os::javaTimeNanos());
  }

  // Call stuff that needs to be run when a safepoint is just about to be completed
  {
    EventSafepointCleanup cleanup_event;
    do_cleanup_tasks();
    if (cleanup_event.should_commit()) {
      cleanup_event.set_safepointId(safepoint_counter());
      cleanup_event.commit();
    }
  }

  if (PrintSafepointStatistics) {
    // Record how much time spend on the above cleanup tasks
    update_statistics_on_cleanup_end(os::javaTimeNanos());
  }
  if (begin_event.should_commit()) {
    begin_event.set_safepointId(safepoint_counter());
    begin_event.set_totalThreadCount(nof_threads);
    begin_event.set_jniCriticalThreadCount(_current_jni_active_count);
    begin_event.commit();
  }
}

// Wake up all threads, so they are ready to resume execution after the safepoint
// operation has been carried out
void SafepointSynchronize::end() {
  EventSafepointEnd event;
  int safepoint_id = safepoint_counter(); // Keep the odd counter as "id"

  assert(Threads_lock->owned_by_self(), "must hold Threads_lock");
  assert((_safepoint_counter & 0x1) == 1, "must be odd");
  _safepoint_counter ++;
  // memory fence isn't required here since an odd _safepoint_counter
  // value can do no harm and a fence is issued below anyway.

  DEBUG_ONLY(Thread* myThread = Thread::current();)
  assert(myThread->is_VM_thread(), "Only VM thread can execute a safepoint");

  if (PrintSafepointStatistics) {
    end_statistics(os::javaTimeNanos());
  }

#ifdef ASSERT
  // A pending_exception cannot be installed during a safepoint.  The threads
  // may install an async exception after they come back from a safepoint into
  // pending_exception after they unblock.  But that should happen later.
  for(JavaThread *cur = Threads::first(); cur; cur = cur->next()) {
    assert (!(cur->has_pending_exception() &&
              cur->safepoint_state()->is_at_poll_safepoint()),
            "safepoint installed a pending exception");
  }
#endif // ASSERT

  if (PageArmed) {
    // Make polling safepoint aware
    os::make_polling_page_readable();
    PageArmed = 0 ;
  }

  // Remove safepoint check from interpreter
  Interpreter::ignore_safepoints();

  {
    MutexLocker mu(Safepoint_lock);

    assert(_state == _synchronized, "must be synchronized before ending safepoint synchronization");

    // Set to not synchronized, so the threads will not go into the signal_thread_blocked method
    // when they get restarted.
    _state = _not_synchronized;
    OrderAccess::fence();

    log_debug(safepoint)("Leaving safepoint region");

    // Start suspended threads
    for(JavaThread *current = Threads::first(); current; current = current->next()) {
      // A problem occurring on Solaris is when attempting to restart threads
      // the first #cpus - 1 go well, but then the VMThread is preempted when we get
      // to the next one (since it has been running the longest).  We then have
      // to wait for a cpu to become available before we can continue restarting
      // threads.
      // FIXME: This causes the performance of the VM to degrade when active and with
      // large numbers of threads.  Apparently this is due to the synchronous nature
      // of suspending threads.
      //
      // TODO-FIXME: the comments above are vestigial and no longer apply.
      // Furthermore, using solaris' schedctl in this particular context confers no benefit
      if (VMThreadHintNoPreempt) {
        os::hint_no_preempt();
      }
      ThreadSafepointState* cur_state = current->safepoint_state();
      assert(cur_state->type() != ThreadSafepointState::_running, "Thread not suspended at safepoint");
      cur_state->restart();
      assert(cur_state->is_running(), "safepoint state has not been reset");
    }

    RuntimeService::record_safepoint_end();

    // Release threads lock, so threads can be created/destroyed again. It will also starts all threads
    // blocked in signal_thread_blocked
    Threads_lock->unlock();

  }
#if INCLUDE_ALL_GCS
  // If there are any concurrent GC threads resume them.
  if (UseConcMarkSweepGC) {
    ConcurrentMarkSweepThread::desynchronize(false);
  } else if (UseG1GC) {
    SuspendibleThreadSet::desynchronize();
  }
#endif // INCLUDE_ALL_GCS
  // record this time so VMThread can keep track how much time has elapsed
  // since last safepoint.
  _end_of_last_safepoint = os::javaTimeMillis();

  if (event.should_commit()) {
    event.set_safepointId(safepoint_id);
    event.commit();
  }
}

bool SafepointSynchronize::is_cleanup_needed() {
  // Need a safepoint if some inline cache buffers is non-empty
  if (!InlineCacheBuffer::is_empty()) return true;
  return false;
}

static void event_safepoint_cleanup_task_commit(EventSafepointCleanupTask& event, const char* name) {
  if (event.should_commit()) {
    event.set_safepointId(SafepointSynchronize::safepoint_counter());
    event.set_name(name);
    event.commit();
  }
}

// Various cleaning tasks that should be done periodically at safepoints
void SafepointSynchronize::do_cleanup_tasks() {
  {
    const char* name = "deflating idle monitors";
    EventSafepointCleanupTask event;
    TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
    ObjectSynchronizer::deflate_idle_monitors();
    event_safepoint_cleanup_task_commit(event, name);
  }

  {
    const char* name = "updating inline caches";
    EventSafepointCleanupTask event;
    TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
    InlineCacheBuffer::update_inline_caches();
    event_safepoint_cleanup_task_commit(event, name);
  }
  {
    const char* name = "compilation policy safepoint handler";
    EventSafepointCleanupTask event;
    TraceTime timer("compilation policy safepoint handler", TRACETIME_LOG(Info, safepoint, cleanup));
    CompilationPolicy::policy()->do_safepoint_work();
    event_safepoint_cleanup_task_commit(event, name);
  }

  {
    const char* name = "mark nmethods";
    EventSafepointCleanupTask event;
    TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
    NMethodSweeper::mark_active_nmethods();
    event_safepoint_cleanup_task_commit(event, name);
  }

  if (SymbolTable::needs_rehashing()) {
    const char* name = "rehashing symbol table";
    EventSafepointCleanupTask event;
    TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
    SymbolTable::rehash_table();
    event_safepoint_cleanup_task_commit(event, name);
  }

  if (StringTable::needs_rehashing()) {
    const char* name = "rehashing string table";
    EventSafepointCleanupTask event;
    TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
    StringTable::rehash_table();
    event_safepoint_cleanup_task_commit(event, name);
  }

  {
    // CMS delays purging the CLDG until the beginning of the next safepoint and to
    // make sure concurrent sweep is done
    const char* name = "purging class loader data graph";
    EventSafepointCleanupTask event;
    TraceTime timer(name, TRACETIME_LOG(Info, safepoint, cleanup));
    ClassLoaderDataGraph::purge_if_needed();
    event_safepoint_cleanup_task_commit(event, name);
  }
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
  assert(SafepointSynchronize::is_synchronizing(), "polling encountered outside safepoint synchronization");

  if (ShowSafepointMsgs) {
    tty->print("handle_polling_page_exception: ");
  }

  if (PrintSafepointStatistics) {
    inc_page_trap_count();
  }

  ThreadSafepointState* state = thread->safepoint_state();

  state->handle_polling_page_exception();
}


void SafepointSynchronize::print_safepoint_timeout(SafepointTimeoutReason reason) {
  if (!timeout_error_printed) {
    timeout_error_printed = true;
    // Print out the thread info which didn't reach the safepoint for debugging
    // purposes (useful when there are lots of threads in the debugger).
    tty->cr();
    tty->print_cr("# SafepointSynchronize::begin: Timeout detected:");
    if (reason ==  _spinning_timeout) {
      tty->print_cr("# SafepointSynchronize::begin: Timed out while spinning to reach a safepoint.");
    } else if (reason == _blocking_timeout) {
      tty->print_cr("# SafepointSynchronize::begin: Timed out while waiting for threads to stop.");
    }

    tty->print_cr("# SafepointSynchronize::begin: Threads which did not reach the safepoint:");
    ThreadSafepointState *cur_state;
    ResourceMark rm;
    for(JavaThread *cur_thread = Threads::first(); cur_thread;
        cur_thread = cur_thread->next()) {
      cur_state = cur_thread->safepoint_state();

      if (cur_thread->thread_state() != _thread_blocked &&
          ((reason == _spinning_timeout && cur_state->is_running()) ||
           (reason == _blocking_timeout && !cur_state->has_called_back()))) {
        tty->print("# ");
        cur_thread->print();
        tty->cr();
      }
    }
    tty->print_cr("# SafepointSynchronize::begin: (End of list)");
  }

  // To debug the long safepoint, specify both DieOnSafepointTimeout &
  // ShowMessageBoxOnError.
  if (DieOnSafepointTimeout) {
    VM_Operation *op = VMThread::vm_operation();
    fatal("Safepoint sync time longer than " INTX_FORMAT "ms detected when executing %s.",
          SafepointTimeoutDelay,
          op != NULL ? op->name() : "no vm operation");
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

// Block the thread at the safepoint poll or poll return.
void ThreadSafepointState::handle_polling_page_exception() {

  // Check state.  block() will set thread state to thread_in_vm which will
  // cause the safepoint state _type to become _call_back.
  assert(type() == ThreadSafepointState::_running,
         "polling page exception on thread not running state");

  // Step 1: Find the nmethod from the return address
  if (ShowSafepointMsgs && Verbose) {
    tty->print_cr("Polling page exception at " INTPTR_FORMAT, p2i(thread()->saved_exception_pc()));
  }
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
      assert(result == NULL || result->is_oop(), "must be oop");
      return_value = Handle(thread(), result);
      assert(Universe::heap()->is_in_or_null(result), "must be heap pointer");
    }

    // Block the thread
    SafepointSynchronize::block(thread());

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
    SafepointSynchronize::block(thread());
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
SafepointSynchronize::SafepointStats*  SafepointSynchronize::_safepoint_stats = NULL;
jlong  SafepointSynchronize::_safepoint_begin_time = 0;
int    SafepointSynchronize::_cur_stat_index = 0;
julong SafepointSynchronize::_safepoint_reasons[VM_Operation::VMOp_Terminating];
julong SafepointSynchronize::_coalesced_vmop_count = 0;
jlong  SafepointSynchronize::_max_sync_time = 0;
jlong  SafepointSynchronize::_max_vmop_time = 0;
float  SafepointSynchronize::_ts_of_current_safepoint = 0.0f;

static jlong  cleanup_end_time = 0;
static bool   need_to_track_page_armed_status = false;
static bool   init_done = false;

// Helper method to print the header.
static void print_header() {
  tty->print("         vmop                    "
             "[threads: total initially_running wait_to_block]    ");
  tty->print("[time: spin block sync cleanup vmop] ");

  // no page armed status printed out if it is always armed.
  if (need_to_track_page_armed_status) {
    tty->print("page_armed ");
  }

  tty->print_cr("page_trap_count");
}

void SafepointSynchronize::deferred_initialize_stat() {
  if (init_done) return;

  // If PrintSafepointStatisticsTimeout is specified, the statistics data will
  // be printed right away, in which case, _safepoint_stats will regress to
  // a single element array. Otherwise, it is a circular ring buffer with default
  // size of PrintSafepointStatisticsCount.
  int stats_array_size;
  if (PrintSafepointStatisticsTimeout > 0) {
    stats_array_size = 1;
    PrintSafepointStatistics = true;
  } else {
    stats_array_size = PrintSafepointStatisticsCount;
  }
  _safepoint_stats = (SafepointStats*)os::malloc(stats_array_size
                                                 * sizeof(SafepointStats), mtInternal);
  guarantee(_safepoint_stats != NULL,
            "not enough memory for safepoint instrumentation data");

  if (DeferPollingPageLoopCount >= 0) {
    need_to_track_page_armed_status = true;
  }
  init_done = true;
}

void SafepointSynchronize::begin_statistics(int nof_threads, int nof_running) {
  assert(init_done, "safepoint statistics array hasn't been initialized");
  SafepointStats *spstat = &_safepoint_stats[_cur_stat_index];

  spstat->_time_stamp = _ts_of_current_safepoint;

  VM_Operation *op = VMThread::vm_operation();
  spstat->_vmop_type = (op != NULL ? op->type() : -1);
  if (op != NULL) {
    _safepoint_reasons[spstat->_vmop_type]++;
  }

  spstat->_nof_total_threads = nof_threads;
  spstat->_nof_initial_running_threads = nof_running;
  spstat->_nof_threads_hit_page_trap = 0;

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
  SafepointStats *spstat = &_safepoint_stats[_cur_stat_index];

  jlong cur_time = os::javaTimeNanos();

  spstat->_nof_threads_wait_to_block = _waiting_to_block;
  if (spstat->_nof_initial_running_threads != 0) {
    spstat->_time_to_spin = cur_time - spstat->_time_to_spin;
  }

  if (need_to_track_page_armed_status) {
    spstat->_page_armed = (PageArmed == 1);
  }

  // Records the start time of waiting for to block. Updated when block is done.
  if (_waiting_to_block != 0) {
    spstat->_time_to_wait_to_block = cur_time;
  } else {
    spstat->_time_to_wait_to_block = 0;
  }
}

void SafepointSynchronize::update_statistics_on_sync_end(jlong end_time) {
  SafepointStats *spstat = &_safepoint_stats[_cur_stat_index];

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
  SafepointStats *spstat = &_safepoint_stats[_cur_stat_index];

  // Record how long spent in cleanup tasks.
  spstat->_time_to_do_cleanups = end_time - spstat->_time_to_do_cleanups;

  cleanup_end_time = end_time;
}

void SafepointSynchronize::end_statistics(jlong vmop_end_time) {
  SafepointStats *spstat = &_safepoint_stats[_cur_stat_index];

  // Update the vm operation time.
  spstat->_time_to_exec_vmop = vmop_end_time -  cleanup_end_time;
  if (spstat->_time_to_exec_vmop > _max_vmop_time) {
    _max_vmop_time = spstat->_time_to_exec_vmop;
  }
  // Only the sync time longer than the specified
  // PrintSafepointStatisticsTimeout will be printed out right away.
  // By default, it is -1 meaning all samples will be put into the list.
  if ( PrintSafepointStatisticsTimeout > 0) {
    if (spstat->_time_to_sync > (jlong)PrintSafepointStatisticsTimeout * MICROUNITS) {
      print_statistics();
    }
  } else {
    // The safepoint statistics will be printed out when the _safepoin_stats
    // array fills up.
    if (_cur_stat_index == PrintSafepointStatisticsCount - 1) {
      print_statistics();
      _cur_stat_index = 0;
    } else {
      _cur_stat_index++;
    }
  }
}

void SafepointSynchronize::print_statistics() {
  SafepointStats* sstats = _safepoint_stats;

  for (int index = 0; index <= _cur_stat_index; index++) {
    if (index % 30 == 0) {
      print_header();
    }
    sstats = &_safepoint_stats[index];
    tty->print("%.3f: ", sstats->_time_stamp);
    tty->print("%-26s       ["
               INT32_FORMAT_W(8) INT32_FORMAT_W(11) INT32_FORMAT_W(15)
               "    ]    ",
               sstats->_vmop_type == -1 ? "no vm operation" :
               VM_Operation::name(sstats->_vmop_type),
               sstats->_nof_total_threads,
               sstats->_nof_initial_running_threads,
               sstats->_nof_threads_wait_to_block);
    // "/ MICROUNITS " is to convert the unit from nanos to millis.
    tty->print("  ["
               INT64_FORMAT_W(6) INT64_FORMAT_W(6)
               INT64_FORMAT_W(6) INT64_FORMAT_W(6)
               INT64_FORMAT_W(6) "    ]  ",
               sstats->_time_to_spin / MICROUNITS,
               sstats->_time_to_wait_to_block / MICROUNITS,
               sstats->_time_to_sync / MICROUNITS,
               sstats->_time_to_do_cleanups / MICROUNITS,
               sstats->_time_to_exec_vmop / MICROUNITS);

    if (need_to_track_page_armed_status) {
      tty->print(INT32_FORMAT "         ", sstats->_page_armed);
    }
    tty->print_cr(INT32_FORMAT "   ", sstats->_nof_threads_hit_page_trap);
  }
}

// This method will be called when VM exits. It will first call
// print_statistics to print out the rest of the sampling.  Then
// it tries to summarize the sampling.
void SafepointSynchronize::print_stat_on_exit() {
  if (_safepoint_stats == NULL) return;

  SafepointStats *spstat = &_safepoint_stats[_cur_stat_index];

  // During VM exit, end_statistics may not get called and in that
  // case, if the sync time is less than PrintSafepointStatisticsTimeout,
  // don't print it out.
  // Approximate the vm op time.
  _safepoint_stats[_cur_stat_index]._time_to_exec_vmop =
    os::javaTimeNanos() - cleanup_end_time;

  if ( PrintSafepointStatisticsTimeout < 0 ||
       spstat->_time_to_sync > (jlong)PrintSafepointStatisticsTimeout * MICROUNITS) {
    print_statistics();
  }
  tty->cr();

  // Print out polling page sampling status.
  if (!need_to_track_page_armed_status) {
    tty->print_cr("Polling page always armed");
  } else {
    tty->print_cr("Defer polling page loop count = " INTX_FORMAT "\n",
                  DeferPollingPageLoopCount);
  }

  for (int index = 0; index < VM_Operation::VMOp_Terminating; index++) {
    if (_safepoint_reasons[index] != 0) {
      tty->print_cr("%-26s" UINT64_FORMAT_W(10), VM_Operation::name(index),
                    _safepoint_reasons[index]);
    }
  }

  tty->print_cr(UINT64_FORMAT_W(5) " VM operations coalesced during safepoint",
                _coalesced_vmop_count);
  tty->print_cr("Maximum sync time  " INT64_FORMAT_W(5) " ms",
                _max_sync_time / MICROUNITS);
  tty->print_cr("Maximum vm operation time (except for Exit VM operation)  "
                INT64_FORMAT_W(5) " ms",
                _max_vmop_time / MICROUNITS);
}

// ------------------------------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT

void SafepointSynchronize::print_state() {
  if (_state == _not_synchronized) {
    tty->print_cr("not synchronized");
  } else if (_state == _synchronizing || _state == _synchronized) {
    tty->print_cr("State: %s", (_state == _synchronizing) ? "synchronizing" :
                  "synchronized");

    for(JavaThread *cur = Threads::first(); cur; cur = cur->next()) {
       cur->safepoint_state()->print();
    }
  }
}

void SafepointSynchronize::safepoint_msg(const char* format, ...) {
  if (ShowSafepointMsgs) {
    va_list ap;
    va_start(ap, format);
    tty->vprint_cr(format, ap);
    va_end(ap);
  }
}

#endif // !PRODUCT
