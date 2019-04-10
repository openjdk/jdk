/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "classfile/classLoader.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/scopeDesc.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compileTask.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "gc/shared/workgroup.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/linkResolver.hpp"
#include "interpreter/oopMapCache.hpp"
#include "jfr/jfrEvents.hpp"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "logging/log.hpp"
#include "logging/logConfiguration.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "oops/verifyOopClosure.hpp"
#include "prims/jvm_misc.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/flags/jvmFlagConstraintList.hpp"
#include "runtime/flags/jvmFlagRangeList.hpp"
#include "runtime/flags/jvmFlagWriteableList.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/handshake.hpp"
#include "runtime/init.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/jniPeriodicChecker.hpp"
#include "runtime/memprofiler.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/osThread.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/statSampler.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/sweeper.hpp"
#include "runtime/task.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadCritical.hpp"
#include "runtime/threadSMR.inline.hpp"
#include "runtime/threadStatisticalInfo.hpp"
#include "runtime/timer.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/vframe.inline.hpp"
#include "runtime/vframeArray.hpp"
#include "runtime/vframe_hp.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vm_version.hpp"
#include "services/attachListener.hpp"
#include "services/management.hpp"
#include "services/memTracker.hpp"
#include "services/threadService.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/events.hpp"
#include "utilities/macros.hpp"
#include "utilities/preserveException.hpp"
#include "utilities/singleWriterSynchronizer.hpp"
#include "utilities/vmError.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmciCompiler.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "logging/logHandle.hpp"
#endif
#ifdef COMPILER1
#include "c1/c1_Compiler.hpp"
#endif
#ifdef COMPILER2
#include "opto/c2compiler.hpp"
#include "opto/idealGraphPrinter.hpp"
#endif
#if INCLUDE_RTM_OPT
#include "runtime/rtmLocking.hpp"
#endif
#if INCLUDE_JFR
#include "jfr/jfr.hpp"
#endif

// Initialization after module runtime initialization
void universe_post_module_init();  // must happen after call_initPhase2

#ifdef DTRACE_ENABLED

// Only bother with this argument setup if dtrace is available

  #define HOTSPOT_THREAD_PROBE_start HOTSPOT_THREAD_START
  #define HOTSPOT_THREAD_PROBE_stop HOTSPOT_THREAD_STOP

  #define DTRACE_THREAD_PROBE(probe, javathread)                           \
    {                                                                      \
      ResourceMark rm(this);                                               \
      int len = 0;                                                         \
      const char* name = (javathread)->get_thread_name();                  \
      len = strlen(name);                                                  \
      HOTSPOT_THREAD_PROBE_##probe(/* probe = start, stop */               \
        (char *) name, len,                                                \
        java_lang_Thread::thread_id((javathread)->threadObj()),            \
        (uintptr_t) (javathread)->osthread()->thread_id(),                 \
        java_lang_Thread::is_daemon((javathread)->threadObj()));           \
    }

#else //  ndef DTRACE_ENABLED

  #define DTRACE_THREAD_PROBE(probe, javathread)

#endif // ndef DTRACE_ENABLED

#ifndef USE_LIBRARY_BASED_TLS_ONLY
// Current thread is maintained as a thread-local variable
THREAD_LOCAL_DECL Thread* Thread::_thr_current = NULL;
#endif

// ======= Thread ========
// Support for forcing alignment of thread objects for biased locking
void* Thread::allocate(size_t size, bool throw_excpt, MEMFLAGS flags) {
  if (UseBiasedLocking) {
    const int alignment = markOopDesc::biased_lock_alignment;
    size_t aligned_size = size + (alignment - sizeof(intptr_t));
    void* real_malloc_addr = throw_excpt? AllocateHeap(aligned_size, flags, CURRENT_PC)
                                          : AllocateHeap(aligned_size, flags, CURRENT_PC,
                                                         AllocFailStrategy::RETURN_NULL);
    void* aligned_addr     = align_up(real_malloc_addr, alignment);
    assert(((uintptr_t) aligned_addr + (uintptr_t) size) <=
           ((uintptr_t) real_malloc_addr + (uintptr_t) aligned_size),
           "JavaThread alignment code overflowed allocated storage");
    if (aligned_addr != real_malloc_addr) {
      log_info(biasedlocking)("Aligned thread " INTPTR_FORMAT " to " INTPTR_FORMAT,
                              p2i(real_malloc_addr),
                              p2i(aligned_addr));
    }
    ((Thread*) aligned_addr)->_real_malloc_address = real_malloc_addr;
    return aligned_addr;
  } else {
    return throw_excpt? AllocateHeap(size, flags, CURRENT_PC)
                       : AllocateHeap(size, flags, CURRENT_PC, AllocFailStrategy::RETURN_NULL);
  }
}

void Thread::operator delete(void* p) {
  if (UseBiasedLocking) {
    FreeHeap(((Thread*) p)->_real_malloc_address);
  } else {
    FreeHeap(p);
  }
}

void JavaThread::smr_delete() {
  if (_on_thread_list) {
    ThreadsSMRSupport::smr_delete(this);
  } else {
    delete this;
  }
}

// Base class for all threads: VMThread, WatcherThread, ConcurrentMarkSweepThread,
// JavaThread

DEBUG_ONLY(Thread* Thread::_starting_thread = NULL;)

Thread::Thread() {

  DEBUG_ONLY(_run_state = PRE_CALL_RUN;)

  // stack and get_thread
  set_stack_base(NULL);
  set_stack_size(0);
  set_self_raw_id(0);
  set_lgrp_id(-1);
  DEBUG_ONLY(clear_suspendible_thread();)

  // allocated data structures
  set_osthread(NULL);
  set_resource_area(new (mtThread)ResourceArea());
  DEBUG_ONLY(_current_resource_mark = NULL;)
  set_handle_area(new (mtThread) HandleArea(NULL));
  set_metadata_handles(new (ResourceObj::C_HEAP, mtClass) GrowableArray<Metadata*>(30, true));
  set_active_handles(NULL);
  set_free_handle_block(NULL);
  set_last_handle_mark(NULL);
  DEBUG_ONLY(_missed_ic_stub_refill_verifier = NULL);

  // Initial value of zero ==> never claimed.
  _threads_do_token = 0;
  _threads_hazard_ptr = NULL;
  _threads_list_ptr = NULL;
  _nested_threads_hazard_ptr_cnt = 0;
  _rcu_counter = 0;

  // the handle mark links itself to last_handle_mark
  new HandleMark(this);

  // plain initialization
  debug_only(_owned_locks = NULL;)
  debug_only(_allow_allocation_count = 0;)
  NOT_PRODUCT(_allow_safepoint_count = 0;)
  NOT_PRODUCT(_skip_gcalot = false;)
  _jvmti_env_iteration_count = 0;
  set_allocated_bytes(0);
  _vm_operation_started_count = 0;
  _vm_operation_completed_count = 0;
  _current_pending_monitor = NULL;
  _current_pending_monitor_is_from_java = true;
  _current_waiting_monitor = NULL;
  _num_nested_signal = 0;
  omFreeList = NULL;
  omFreeCount = 0;
  omFreeProvision = 32;
  omInUseList = NULL;
  omInUseCount = 0;

#ifdef ASSERT
  _visited_for_critical_count = false;
#endif

  _SR_lock = new Monitor(Mutex::suspend_resume, "SR_lock", true,
                         Monitor::_safepoint_check_sometimes);
  _suspend_flags = 0;

  // thread-specific hashCode stream generator state - Marsaglia shift-xor form
  _hashStateX = os::random();
  _hashStateY = 842502087;
  _hashStateZ = 0x8767;    // (int)(3579807591LL & 0xffff) ;
  _hashStateW = 273326509;

  _OnTrap   = 0;
  _Stalled  = 0;
  _TypeTag  = 0x2BAD;

  // Many of the following fields are effectively final - immutable
  // Note that nascent threads can't use the Native Monitor-Mutex
  // construct until the _MutexEvent is initialized ...
  // CONSIDER: instead of using a fixed set of purpose-dedicated ParkEvents
  // we might instead use a stack of ParkEvents that we could provision on-demand.
  // The stack would act as a cache to avoid calls to ParkEvent::Allocate()
  // and ::Release()
  _ParkEvent   = ParkEvent::Allocate(this);
  _SleepEvent  = ParkEvent::Allocate(this);
  _MuxEvent    = ParkEvent::Allocate(this);

#ifdef CHECK_UNHANDLED_OOPS
  if (CheckUnhandledOops) {
    _unhandled_oops = new UnhandledOops(this);
  }
#endif // CHECK_UNHANDLED_OOPS
#ifdef ASSERT
  if (UseBiasedLocking) {
    assert((((uintptr_t) this) & (markOopDesc::biased_lock_alignment - 1)) == 0, "forced alignment of thread object failed");
    assert(this == _real_malloc_address ||
           this == align_up(_real_malloc_address, (int)markOopDesc::biased_lock_alignment),
           "bug in forced alignment of thread objects");
  }
#endif // ASSERT

  // Notify the barrier set that a thread is being created. The initial
  // thread is created before the barrier set is available.  The call to
  // BarrierSet::on_thread_create() for this thread is therefore deferred
  // to BarrierSet::set_barrier_set().
  BarrierSet* const barrier_set = BarrierSet::barrier_set();
  if (barrier_set != NULL) {
    barrier_set->on_thread_create(this);
  } else {
    // Only the main thread should be created before the barrier set
    // and that happens just before Thread::current is set. No other thread
    // can attach as the VM is not created yet, so they can't execute this code.
    // If the main thread creates other threads before the barrier set that is an error.
    assert(Thread::current_or_null() == NULL, "creating thread before barrier set");
  }
}

void Thread::initialize_thread_current() {
#ifndef USE_LIBRARY_BASED_TLS_ONLY
  assert(_thr_current == NULL, "Thread::current already initialized");
  _thr_current = this;
#endif
  assert(ThreadLocalStorage::thread() == NULL, "ThreadLocalStorage::thread already initialized");
  ThreadLocalStorage::set_thread(this);
  assert(Thread::current() == ThreadLocalStorage::thread(), "TLS mismatch!");
}

void Thread::clear_thread_current() {
  assert(Thread::current() == ThreadLocalStorage::thread(), "TLS mismatch!");
#ifndef USE_LIBRARY_BASED_TLS_ONLY
  _thr_current = NULL;
#endif
  ThreadLocalStorage::set_thread(NULL);
}

void Thread::record_stack_base_and_size() {
  // Note: at this point, Thread object is not yet initialized. Do not rely on
  // any members being initialized. Do not rely on Thread::current() being set.
  // If possible, refrain from doing anything which may crash or assert since
  // quite probably those crash dumps will be useless.
  set_stack_base(os::current_stack_base());
  set_stack_size(os::current_stack_size());

#ifdef SOLARIS
  if (os::is_primordial_thread()) {
    os::Solaris::correct_stack_boundaries_for_primordial_thread(this);
  }
#endif

  // Set stack limits after thread is initialized.
  if (is_Java_thread()) {
    ((JavaThread*) this)->set_stack_overflow_limit();
    ((JavaThread*) this)->set_reserved_stack_activation(stack_base());
  }
}

#if INCLUDE_NMT
void Thread::register_thread_stack_with_NMT() {
  MemTracker::record_thread_stack(stack_end(), stack_size());
}
#endif // INCLUDE_NMT

void Thread::call_run() {
  DEBUG_ONLY(_run_state = CALL_RUN;)

  // At this point, Thread object should be fully initialized and
  // Thread::current() should be set.

  assert(Thread::current_or_null() != NULL, "current thread is unset");
  assert(Thread::current_or_null() == this, "current thread is wrong");

  // Perform common initialization actions

  register_thread_stack_with_NMT();

  JFR_ONLY(Jfr::on_thread_start(this);)

  log_debug(os, thread)("Thread " UINTX_FORMAT " stack dimensions: "
    PTR_FORMAT "-" PTR_FORMAT " (" SIZE_FORMAT "k).",
    os::current_thread_id(), p2i(stack_base() - stack_size()),
    p2i(stack_base()), stack_size()/1024);

  // Perform <ChildClass> initialization actions
  DEBUG_ONLY(_run_state = PRE_RUN;)
  this->pre_run();

  // Invoke <ChildClass>::run()
  DEBUG_ONLY(_run_state = RUN;)
  this->run();
  // Returned from <ChildClass>::run(). Thread finished.

  // Perform common tear-down actions

  assert(Thread::current_or_null() != NULL, "current thread is unset");
  assert(Thread::current_or_null() == this, "current thread is wrong");

  // Perform <ChildClass> tear-down actions
  DEBUG_ONLY(_run_state = POST_RUN;)
  this->post_run();

  // Note: at this point the thread object may already have deleted itself,
  // so from here on do not dereference *this*. Not all thread types currently
  // delete themselves when they terminate. But no thread should ever be deleted
  // asynchronously with respect to its termination - that is what _run_state can
  // be used to check.

  assert(Thread::current_or_null() == NULL, "current thread still present");
}

Thread::~Thread() {

  // Attached threads will remain in PRE_CALL_RUN, as will threads that don't actually
  // get started due to errors etc. Any active thread should at least reach post_run
  // before it is deleted (usually in post_run()).
  assert(_run_state == PRE_CALL_RUN ||
         _run_state == POST_RUN, "Active Thread deleted before post_run(): "
         "_run_state=%d", (int)_run_state);

  // Notify the barrier set that a thread is being destroyed. Note that a barrier
  // set might not be available if we encountered errors during bootstrapping.
  BarrierSet* const barrier_set = BarrierSet::barrier_set();
  if (barrier_set != NULL) {
    barrier_set->on_thread_destroy(this);
  }

  // stack_base can be NULL if the thread is never started or exited before
  // record_stack_base_and_size called. Although, we would like to ensure
  // that all started threads do call record_stack_base_and_size(), there is
  // not proper way to enforce that.
#if INCLUDE_NMT
  if (_stack_base != NULL) {
    MemTracker::release_thread_stack(stack_end(), stack_size());
#ifdef ASSERT
    set_stack_base(NULL);
#endif
  }
#endif // INCLUDE_NMT

  // deallocate data structures
  delete resource_area();
  // since the handle marks are using the handle area, we have to deallocated the root
  // handle mark before deallocating the thread's handle area,
  assert(last_handle_mark() != NULL, "check we have an element");
  delete last_handle_mark();
  assert(last_handle_mark() == NULL, "check we have reached the end");

  // It's possible we can encounter a null _ParkEvent, etc., in stillborn threads.
  // We NULL out the fields for good hygiene.
  ParkEvent::Release(_ParkEvent); _ParkEvent   = NULL;
  ParkEvent::Release(_SleepEvent); _SleepEvent  = NULL;
  ParkEvent::Release(_MuxEvent); _MuxEvent    = NULL;

  delete handle_area();
  delete metadata_handles();

  // SR_handler uses this as a termination indicator -
  // needs to happen before os::free_thread()
  delete _SR_lock;
  _SR_lock = NULL;

  // osthread() can be NULL, if creation of thread failed.
  if (osthread() != NULL) os::free_thread(osthread());

  // Clear Thread::current if thread is deleting itself and it has not
  // already been done. This must be done before the memory is deallocated.
  // Needed to ensure JNI correctly detects non-attached threads.
  if (this == Thread::current_or_null()) {
    Thread::clear_thread_current();
  }

  CHECK_UNHANDLED_OOPS_ONLY(if (CheckUnhandledOops) delete unhandled_oops();)
}

#ifdef ASSERT
// A JavaThread is considered "dangling" if it is not the current
// thread, has been added the Threads list, the system is not at a
// safepoint and the Thread is not "protected".
//
void Thread::check_for_dangling_thread_pointer(Thread *thread) {
  assert(!thread->is_Java_thread() || Thread::current() == thread ||
         !((JavaThread *) thread)->on_thread_list() ||
         SafepointSynchronize::is_at_safepoint() ||
         ThreadsSMRSupport::is_a_protected_JavaThread_with_lock((JavaThread *) thread),
         "possibility of dangling Thread pointer");
}
#endif

ThreadPriority Thread::get_priority(const Thread* const thread) {
  ThreadPriority priority;
  // Can return an error!
  (void)os::get_priority(thread, priority);
  assert(MinPriority <= priority && priority <= MaxPriority, "non-Java priority found");
  return priority;
}

void Thread::set_priority(Thread* thread, ThreadPriority priority) {
  debug_only(check_for_dangling_thread_pointer(thread);)
  // Can return an error!
  (void)os::set_priority(thread, priority);
}


void Thread::start(Thread* thread) {
  // Start is different from resume in that its safety is guaranteed by context or
  // being called from a Java method synchronized on the Thread object.
  if (!DisableStartThread) {
    if (thread->is_Java_thread()) {
      // Initialize the thread state to RUNNABLE before starting this thread.
      // Can not set it after the thread started because we do not know the
      // exact thread state at that time. It could be in MONITOR_WAIT or
      // in SLEEPING or some other state.
      java_lang_Thread::set_thread_status(((JavaThread*)thread)->threadObj(),
                                          java_lang_Thread::RUNNABLE);
    }
    os::start_thread(thread);
  }
}

// Enqueue a VM_Operation to do the job for us - sometime later
void Thread::send_async_exception(oop java_thread, oop java_throwable) {
  VM_ThreadStop* vm_stop = new VM_ThreadStop(java_thread, java_throwable);
  VMThread::execute(vm_stop);
}


// Check if an external suspend request has completed (or has been
// cancelled). Returns true if the thread is externally suspended and
// false otherwise.
//
// The bits parameter returns information about the code path through
// the routine. Useful for debugging:
//
// set in is_ext_suspend_completed():
// 0x00000001 - routine was entered
// 0x00000010 - routine return false at end
// 0x00000100 - thread exited (return false)
// 0x00000200 - suspend request cancelled (return false)
// 0x00000400 - thread suspended (return true)
// 0x00001000 - thread is in a suspend equivalent state (return true)
// 0x00002000 - thread is native and walkable (return true)
// 0x00004000 - thread is native_trans and walkable (needed retry)
//
// set in wait_for_ext_suspend_completion():
// 0x00010000 - routine was entered
// 0x00020000 - suspend request cancelled before loop (return false)
// 0x00040000 - thread suspended before loop (return true)
// 0x00080000 - suspend request cancelled in loop (return false)
// 0x00100000 - thread suspended in loop (return true)
// 0x00200000 - suspend not completed during retry loop (return false)

// Helper class for tracing suspend wait debug bits.
//
// 0x00000100 indicates that the target thread exited before it could
// self-suspend which is not a wait failure. 0x00000200, 0x00020000 and
// 0x00080000 each indicate a cancelled suspend request so they don't
// count as wait failures either.
#define DEBUG_FALSE_BITS (0x00000010 | 0x00200000)

class TraceSuspendDebugBits : public StackObj {
 private:
  JavaThread * jt;
  bool         is_wait;
  bool         called_by_wait;  // meaningful when !is_wait
  uint32_t *   bits;

 public:
  TraceSuspendDebugBits(JavaThread *_jt, bool _is_wait, bool _called_by_wait,
                        uint32_t *_bits) {
    jt             = _jt;
    is_wait        = _is_wait;
    called_by_wait = _called_by_wait;
    bits           = _bits;
  }

  ~TraceSuspendDebugBits() {
    if (!is_wait) {
#if 1
      // By default, don't trace bits for is_ext_suspend_completed() calls.
      // That trace is very chatty.
      return;
#else
      if (!called_by_wait) {
        // If tracing for is_ext_suspend_completed() is enabled, then only
        // trace calls to it from wait_for_ext_suspend_completion()
        return;
      }
#endif
    }

    if (AssertOnSuspendWaitFailure || TraceSuspendWaitFailures) {
      if (bits != NULL && (*bits & DEBUG_FALSE_BITS) != 0) {
        MutexLocker ml(Threads_lock);  // needed for get_thread_name()
        ResourceMark rm;

        tty->print_cr(
                      "Failed wait_for_ext_suspend_completion(thread=%s, debug_bits=%x)",
                      jt->get_thread_name(), *bits);

        guarantee(!AssertOnSuspendWaitFailure, "external suspend wait failed");
      }
    }
  }
};
#undef DEBUG_FALSE_BITS


bool JavaThread::is_ext_suspend_completed(bool called_by_wait, int delay,
                                          uint32_t *bits) {
  TraceSuspendDebugBits tsdb(this, false /* !is_wait */, called_by_wait, bits);

  bool did_trans_retry = false;  // only do thread_in_native_trans retry once
  bool do_trans_retry;           // flag to force the retry

  *bits |= 0x00000001;

  do {
    do_trans_retry = false;

    if (is_exiting()) {
      // Thread is in the process of exiting. This is always checked
      // first to reduce the risk of dereferencing a freed JavaThread.
      *bits |= 0x00000100;
      return false;
    }

    if (!is_external_suspend()) {
      // Suspend request is cancelled. This is always checked before
      // is_ext_suspended() to reduce the risk of a rogue resume
      // confusing the thread that made the suspend request.
      *bits |= 0x00000200;
      return false;
    }

    if (is_ext_suspended()) {
      // thread is suspended
      *bits |= 0x00000400;
      return true;
    }

    // Now that we no longer do hard suspends of threads running
    // native code, the target thread can be changing thread state
    // while we are in this routine:
    //
    //   _thread_in_native -> _thread_in_native_trans -> _thread_blocked
    //
    // We save a copy of the thread state as observed at this moment
    // and make our decision about suspend completeness based on the
    // copy. This closes the race where the thread state is seen as
    // _thread_in_native_trans in the if-thread_blocked check, but is
    // seen as _thread_blocked in if-thread_in_native_trans check.
    JavaThreadState save_state = thread_state();

    if (save_state == _thread_blocked && is_suspend_equivalent()) {
      // If the thread's state is _thread_blocked and this blocking
      // condition is known to be equivalent to a suspend, then we can
      // consider the thread to be externally suspended. This means that
      // the code that sets _thread_blocked has been modified to do
      // self-suspension if the blocking condition releases. We also
      // used to check for CONDVAR_WAIT here, but that is now covered by
      // the _thread_blocked with self-suspension check.
      //
      // Return true since we wouldn't be here unless there was still an
      // external suspend request.
      *bits |= 0x00001000;
      return true;
    } else if (save_state == _thread_in_native && frame_anchor()->walkable()) {
      // Threads running native code will self-suspend on native==>VM/Java
      // transitions. If its stack is walkable (should always be the case
      // unless this function is called before the actual java_suspend()
      // call), then the wait is done.
      *bits |= 0x00002000;
      return true;
    } else if (!called_by_wait && !did_trans_retry &&
               save_state == _thread_in_native_trans &&
               frame_anchor()->walkable()) {
      // The thread is transitioning from thread_in_native to another
      // thread state. check_safepoint_and_suspend_for_native_trans()
      // will force the thread to self-suspend. If it hasn't gotten
      // there yet we may have caught the thread in-between the native
      // code check above and the self-suspend. Lucky us. If we were
      // called by wait_for_ext_suspend_completion(), then it
      // will be doing the retries so we don't have to.
      //
      // Since we use the saved thread state in the if-statement above,
      // there is a chance that the thread has already transitioned to
      // _thread_blocked by the time we get here. In that case, we will
      // make a single unnecessary pass through the logic below. This
      // doesn't hurt anything since we still do the trans retry.

      *bits |= 0x00004000;

      // Once the thread leaves thread_in_native_trans for another
      // thread state, we break out of this retry loop. We shouldn't
      // need this flag to prevent us from getting back here, but
      // sometimes paranoia is good.
      did_trans_retry = true;

      // We wait for the thread to transition to a more usable state.
      for (int i = 1; i <= SuspendRetryCount; i++) {
        // We used to do an "os::yield_all(i)" call here with the intention
        // that yielding would increase on each retry. However, the parameter
        // is ignored on Linux which means the yield didn't scale up. Waiting
        // on the SR_lock below provides a much more predictable scale up for
        // the delay. It also provides a simple/direct point to check for any
        // safepoint requests from the VMThread

        // temporarily drops SR_lock while doing wait with safepoint check
        // (if we're a JavaThread - the WatcherThread can also call this)
        // and increase delay with each retry
        SR_lock()->wait(!Thread::current()->is_Java_thread(), i * delay);

        // check the actual thread state instead of what we saved above
        if (thread_state() != _thread_in_native_trans) {
          // the thread has transitioned to another thread state so
          // try all the checks (except this one) one more time.
          do_trans_retry = true;
          break;
        }
      } // end retry loop


    }
  } while (do_trans_retry);

  *bits |= 0x00000010;
  return false;
}

// Wait for an external suspend request to complete (or be cancelled).
// Returns true if the thread is externally suspended and false otherwise.
//
bool JavaThread::wait_for_ext_suspend_completion(int retries, int delay,
                                                 uint32_t *bits) {
  TraceSuspendDebugBits tsdb(this, true /* is_wait */,
                             false /* !called_by_wait */, bits);

  // local flag copies to minimize SR_lock hold time
  bool is_suspended;
  bool pending;
  uint32_t reset_bits;

  // set a marker so is_ext_suspend_completed() knows we are the caller
  *bits |= 0x00010000;

  // We use reset_bits to reinitialize the bits value at the top of
  // each retry loop. This allows the caller to make use of any
  // unused bits for their own marking purposes.
  reset_bits = *bits;

  {
    MutexLockerEx ml(SR_lock(), Mutex::_no_safepoint_check_flag);
    is_suspended = is_ext_suspend_completed(true /* called_by_wait */,
                                            delay, bits);
    pending = is_external_suspend();
  }
  // must release SR_lock to allow suspension to complete

  if (!pending) {
    // A cancelled suspend request is the only false return from
    // is_ext_suspend_completed() that keeps us from entering the
    // retry loop.
    *bits |= 0x00020000;
    return false;
  }

  if (is_suspended) {
    *bits |= 0x00040000;
    return true;
  }

  for (int i = 1; i <= retries; i++) {
    *bits = reset_bits;  // reinit to only track last retry

    // We used to do an "os::yield_all(i)" call here with the intention
    // that yielding would increase on each retry. However, the parameter
    // is ignored on Linux which means the yield didn't scale up. Waiting
    // on the SR_lock below provides a much more predictable scale up for
    // the delay. It also provides a simple/direct point to check for any
    // safepoint requests from the VMThread

    {
      MutexLocker ml(SR_lock());
      // wait with safepoint check (if we're a JavaThread - the WatcherThread
      // can also call this)  and increase delay with each retry
      SR_lock()->wait(!Thread::current()->is_Java_thread(), i * delay);

      is_suspended = is_ext_suspend_completed(true /* called_by_wait */,
                                              delay, bits);

      // It is possible for the external suspend request to be cancelled
      // (by a resume) before the actual suspend operation is completed.
      // Refresh our local copy to see if we still need to wait.
      pending = is_external_suspend();
    }

    if (!pending) {
      // A cancelled suspend request is the only false return from
      // is_ext_suspend_completed() that keeps us from staying in the
      // retry loop.
      *bits |= 0x00080000;
      return false;
    }

    if (is_suspended) {
      *bits |= 0x00100000;
      return true;
    }
  } // end retry loop

  // thread did not suspend after all our retries
  *bits |= 0x00200000;
  return false;
}

// Called from API entry points which perform stack walking. If the
// associated JavaThread is the current thread, then wait_for_suspend
// is not used. Otherwise, it determines if we should wait for the
// "other" thread to complete external suspension. (NOTE: in future
// releases the suspension mechanism should be reimplemented so this
// is not necessary.)
//
bool
JavaThread::is_thread_fully_suspended(bool wait_for_suspend, uint32_t *bits) {
  if (this != JavaThread::current()) {
    // "other" threads require special handling.
    if (wait_for_suspend) {
      // We are allowed to wait for the external suspend to complete
      // so give the other thread a chance to get suspended.
      if (!wait_for_ext_suspend_completion(SuspendRetryCount,
                                           SuspendRetryDelay, bits)) {
        // Didn't make it so let the caller know.
        return false;
      }
    }
    // We aren't allowed to wait for the external suspend to complete
    // so if the other thread isn't externally suspended we need to
    // let the caller know.
    else if (!is_ext_suspend_completed_with_lock(bits)) {
      return false;
    }
  }

  return true;
}

#ifndef PRODUCT
void JavaThread::record_jump(address target, address instr, const char* file,
                             int line) {

  // This should not need to be atomic as the only way for simultaneous
  // updates is via interrupts. Even then this should be rare or non-existent
  // and we don't care that much anyway.

  int index = _jmp_ring_index;
  _jmp_ring_index = (index + 1) & (jump_ring_buffer_size - 1);
  _jmp_ring[index]._target = (intptr_t) target;
  _jmp_ring[index]._instruction = (intptr_t) instr;
  _jmp_ring[index]._file = file;
  _jmp_ring[index]._line = line;
}
#endif // PRODUCT

void Thread::interrupt(Thread* thread) {
  debug_only(check_for_dangling_thread_pointer(thread);)
  os::interrupt(thread);
}

bool Thread::is_interrupted(Thread* thread, bool clear_interrupted) {
  debug_only(check_for_dangling_thread_pointer(thread);)
  // Note:  If clear_interrupted==false, this simply fetches and
  // returns the value of the field osthread()->interrupted().
  return os::is_interrupted(thread, clear_interrupted);
}


// GC Support
bool Thread::claim_par_threads_do(uintx claim_token) {
  uintx token = _threads_do_token;
  if (token != claim_token) {
    uintx res = Atomic::cmpxchg(claim_token, &_threads_do_token, token);
    if (res == token) {
      return true;
    }
    guarantee(res == claim_token, "invariant");
  }
  return false;
}

void Thread::oops_do(OopClosure* f, CodeBlobClosure* cf) {
  active_handles()->oops_do(f);
  // Do oop for ThreadShadow
  f->do_oop((oop*)&_pending_exception);
  handle_area()->oops_do(f);

  // We scan thread local monitor lists here, and the remaining global
  // monitors in ObjectSynchronizer::oops_do().
  ObjectSynchronizer::thread_local_used_oops_do(this, f);
}

void Thread::metadata_handles_do(void f(Metadata*)) {
  // Only walk the Handles in Thread.
  if (metadata_handles() != NULL) {
    for (int i = 0; i< metadata_handles()->length(); i++) {
      f(metadata_handles()->at(i));
    }
  }
}

void Thread::print_on(outputStream* st, bool print_extended_info) const {
  // get_priority assumes osthread initialized
  if (osthread() != NULL) {
    int os_prio;
    if (os::get_native_priority(this, &os_prio) == OS_OK) {
      st->print("os_prio=%d ", os_prio);
    }

    st->print("cpu=%.2fms ",
              os::thread_cpu_time(const_cast<Thread*>(this), true) / 1000000.0
              );
    st->print("elapsed=%.2fs ",
              _statistical_info.getElapsedTime() / 1000.0
              );
    if (is_Java_thread() && (PrintExtendedThreadInfo || print_extended_info)) {
      size_t allocated_bytes = (size_t) const_cast<Thread*>(this)->cooked_allocated_bytes();
      st->print("allocated=" SIZE_FORMAT "%s ",
                byte_size_in_proper_unit(allocated_bytes),
                proper_unit_for_byte_size(allocated_bytes)
                );
      st->print("defined_classes=" INT64_FORMAT " ", _statistical_info.getDefineClassCount());
    }

    st->print("tid=" INTPTR_FORMAT " ", p2i(this));
    osthread()->print_on(st);
  }
  ThreadsSMRSupport::print_info_on(this, st);
  st->print(" ");
  debug_only(if (WizardMode) print_owned_locks_on(st);)
}

// Thread::print_on_error() is called by fatal error handler. Don't use
// any lock or allocate memory.
void Thread::print_on_error(outputStream* st, char* buf, int buflen) const {
  assert(!(is_Compiler_thread() || is_Java_thread()), "Can't call name() here if it allocates");

  if (is_VM_thread())                 { st->print("VMThread"); }
  else if (is_GC_task_thread())       { st->print("GCTaskThread"); }
  else if (is_Watcher_thread())       { st->print("WatcherThread"); }
  else if (is_ConcurrentGC_thread())  { st->print("ConcurrentGCThread"); }
  else                                { st->print("Thread"); }

  if (is_Named_thread()) {
    st->print(" \"%s\"", name());
  }

  st->print(" [stack: " PTR_FORMAT "," PTR_FORMAT "]",
            p2i(stack_end()), p2i(stack_base()));

  if (osthread()) {
    st->print(" [id=%d]", osthread()->thread_id());
  }

  ThreadsSMRSupport::print_info_on(this, st);
}

void Thread::print_value_on(outputStream* st) const {
  if (is_Named_thread()) {
    st->print(" \"%s\" ", name());
  }
  st->print(INTPTR_FORMAT, p2i(this));   // print address
}

#ifdef ASSERT
void Thread::print_owned_locks_on(outputStream* st) const {
  Monitor *cur = _owned_locks;
  if (cur == NULL) {
    st->print(" (no locks) ");
  } else {
    st->print_cr(" Locks owned:");
    while (cur) {
      cur->print_on(st);
      cur = cur->next();
    }
  }
}

static int ref_use_count  = 0;

bool Thread::owns_locks_but_compiled_lock() const {
  for (Monitor *cur = _owned_locks; cur; cur = cur->next()) {
    if (cur != Compile_lock) return true;
  }
  return false;
}


#endif

#ifndef PRODUCT

// The flag: potential_vm_operation notifies if this particular safepoint state could potentially
// invoke the vm-thread (e.g., an oop allocation). In that case, we also have to make sure that
// no locks which allow_vm_block's are held
void Thread::check_for_valid_safepoint_state(bool potential_vm_operation) {
  // Check if current thread is allowed to block at a safepoint
  if (!(_allow_safepoint_count == 0)) {
    fatal("Possible safepoint reached by thread that does not allow it");
  }
  if (is_Java_thread() && ((JavaThread*)this)->thread_state() != _thread_in_vm) {
    fatal("LEAF method calling lock?");
  }

#ifdef ASSERT
  if (potential_vm_operation && is_Java_thread()
      && !Universe::is_bootstrapping()) {
    // Make sure we do not hold any locks that the VM thread also uses.
    // This could potentially lead to deadlocks
    for (Monitor *cur = _owned_locks; cur; cur = cur->next()) {
      // Threads_lock is special, since the safepoint synchronization will not start before this is
      // acquired. Hence, a JavaThread cannot be holding it at a safepoint. So is VMOperationRequest_lock,
      // since it is used to transfer control between JavaThreads and the VMThread
      // Do not *exclude* any locks unless you are absolutely sure it is correct. Ask someone else first!
      if ((cur->allow_vm_block() &&
           cur != Threads_lock &&
           cur != Compile_lock &&               // Temporary: should not be necessary when we get separate compilation
           cur != VMOperationRequest_lock &&
           cur != VMOperationQueue_lock) ||
           cur->rank() == Mutex::special) {
        fatal("Thread holding lock at safepoint that vm can block on: %s", cur->name());
      }
    }
  }

  if (GCALotAtAllSafepoints) {
    // We could enter a safepoint here and thus have a gc
    InterfaceSupport::check_gc_alot();
  }
#endif
}
#endif

bool Thread::is_in_stack(address adr) const {
  assert(Thread::current() == this, "is_in_stack can only be called from current thread");
  address end = os::current_stack_pointer();
  // Allow non Java threads to call this without stack_base
  if (_stack_base == NULL) return true;
  if (stack_base() >= adr && adr >= end) return true;

  return false;
}

bool Thread::is_in_usable_stack(address adr) const {
  size_t stack_guard_size = os::uses_stack_guard_pages() ? JavaThread::stack_guard_zone_size() : 0;
  size_t usable_stack_size = _stack_size - stack_guard_size;

  return ((adr < stack_base()) && (adr >= stack_base() - usable_stack_size));
}


// We had to move these methods here, because vm threads get into ObjectSynchronizer::enter
// However, there is a note in JavaThread::is_lock_owned() about the VM threads not being
// used for compilation in the future. If that change is made, the need for these methods
// should be revisited, and they should be removed if possible.

bool Thread::is_lock_owned(address adr) const {
  return on_local_stack(adr);
}

bool Thread::set_as_starting_thread() {
  assert(_starting_thread == NULL, "already initialized: "
         "_starting_thread=" INTPTR_FORMAT, p2i(_starting_thread));
  // NOTE: this must be called inside the main thread.
  DEBUG_ONLY(_starting_thread = this;)
  return os::create_main_thread((JavaThread*)this);
}

static void initialize_class(Symbol* class_name, TRAPS) {
  Klass* klass = SystemDictionary::resolve_or_fail(class_name, true, CHECK);
  InstanceKlass::cast(klass)->initialize(CHECK);
}


// Creates the initial ThreadGroup
static Handle create_initial_thread_group(TRAPS) {
  Handle system_instance = JavaCalls::construct_new_instance(
                            SystemDictionary::ThreadGroup_klass(),
                            vmSymbols::void_method_signature(),
                            CHECK_NH);
  Universe::set_system_thread_group(system_instance());

  Handle string = java_lang_String::create_from_str("main", CHECK_NH);
  Handle main_instance = JavaCalls::construct_new_instance(
                            SystemDictionary::ThreadGroup_klass(),
                            vmSymbols::threadgroup_string_void_signature(),
                            system_instance,
                            string,
                            CHECK_NH);
  return main_instance;
}

// Creates the initial Thread
static oop create_initial_thread(Handle thread_group, JavaThread* thread,
                                 TRAPS) {
  InstanceKlass* ik = SystemDictionary::Thread_klass();
  assert(ik->is_initialized(), "must be");
  instanceHandle thread_oop = ik->allocate_instance_handle(CHECK_NULL);

  // Cannot use JavaCalls::construct_new_instance because the java.lang.Thread
  // constructor calls Thread.current(), which must be set here for the
  // initial thread.
  java_lang_Thread::set_thread(thread_oop(), thread);
  java_lang_Thread::set_priority(thread_oop(), NormPriority);
  thread->set_threadObj(thread_oop());

  Handle string = java_lang_String::create_from_str("main", CHECK_NULL);

  JavaValue result(T_VOID);
  JavaCalls::call_special(&result, thread_oop,
                          ik,
                          vmSymbols::object_initializer_name(),
                          vmSymbols::threadgroup_string_void_signature(),
                          thread_group,
                          string,
                          CHECK_NULL);
  return thread_oop();
}

char java_runtime_name[128] = "";
char java_runtime_version[128] = "";

// extract the JRE name from java.lang.VersionProps.java_runtime_name
static const char* get_java_runtime_name(TRAPS) {
  Klass* k = SystemDictionary::find(vmSymbols::java_lang_VersionProps(),
                                    Handle(), Handle(), CHECK_AND_CLEAR_NULL);
  fieldDescriptor fd;
  bool found = k != NULL &&
               InstanceKlass::cast(k)->find_local_field(vmSymbols::java_runtime_name_name(),
                                                        vmSymbols::string_signature(), &fd);
  if (found) {
    oop name_oop = k->java_mirror()->obj_field(fd.offset());
    if (name_oop == NULL) {
      return NULL;
    }
    const char* name = java_lang_String::as_utf8_string(name_oop,
                                                        java_runtime_name,
                                                        sizeof(java_runtime_name));
    return name;
  } else {
    return NULL;
  }
}

// extract the JRE version from java.lang.VersionProps.java_runtime_version
static const char* get_java_runtime_version(TRAPS) {
  Klass* k = SystemDictionary::find(vmSymbols::java_lang_VersionProps(),
                                    Handle(), Handle(), CHECK_AND_CLEAR_NULL);
  fieldDescriptor fd;
  bool found = k != NULL &&
               InstanceKlass::cast(k)->find_local_field(vmSymbols::java_runtime_version_name(),
                                                        vmSymbols::string_signature(), &fd);
  if (found) {
    oop name_oop = k->java_mirror()->obj_field(fd.offset());
    if (name_oop == NULL) {
      return NULL;
    }
    const char* name = java_lang_String::as_utf8_string(name_oop,
                                                        java_runtime_version,
                                                        sizeof(java_runtime_version));
    return name;
  } else {
    return NULL;
  }
}

// General purpose hook into Java code, run once when the VM is initialized.
// The Java library method itself may be changed independently from the VM.
static void call_postVMInitHook(TRAPS) {
  Klass* klass = SystemDictionary::resolve_or_null(vmSymbols::jdk_internal_vm_PostVMInitHook(), THREAD);
  if (klass != NULL) {
    JavaValue result(T_VOID);
    JavaCalls::call_static(&result, klass, vmSymbols::run_method_name(),
                           vmSymbols::void_method_signature(),
                           CHECK);
  }
}

void JavaThread::allocate_threadObj(Handle thread_group, const char* thread_name,
                                    bool daemon, TRAPS) {
  assert(thread_group.not_null(), "thread group should be specified");
  assert(threadObj() == NULL, "should only create Java thread object once");

  InstanceKlass* ik = SystemDictionary::Thread_klass();
  assert(ik->is_initialized(), "must be");
  instanceHandle thread_oop = ik->allocate_instance_handle(CHECK);

  // We are called from jni_AttachCurrentThread/jni_AttachCurrentThreadAsDaemon.
  // We cannot use JavaCalls::construct_new_instance because the java.lang.Thread
  // constructor calls Thread.current(), which must be set here.
  java_lang_Thread::set_thread(thread_oop(), this);
  java_lang_Thread::set_priority(thread_oop(), NormPriority);
  set_threadObj(thread_oop());

  JavaValue result(T_VOID);
  if (thread_name != NULL) {
    Handle name = java_lang_String::create_from_str(thread_name, CHECK);
    // Thread gets assigned specified name and null target
    JavaCalls::call_special(&result,
                            thread_oop,
                            ik,
                            vmSymbols::object_initializer_name(),
                            vmSymbols::threadgroup_string_void_signature(),
                            thread_group,
                            name,
                            THREAD);
  } else {
    // Thread gets assigned name "Thread-nnn" and null target
    // (java.lang.Thread doesn't have a constructor taking only a ThreadGroup argument)
    JavaCalls::call_special(&result,
                            thread_oop,
                            ik,
                            vmSymbols::object_initializer_name(),
                            vmSymbols::threadgroup_runnable_void_signature(),
                            thread_group,
                            Handle(),
                            THREAD);
  }


  if (daemon) {
    java_lang_Thread::set_daemon(thread_oop());
  }

  if (HAS_PENDING_EXCEPTION) {
    return;
  }

  Klass* group =  SystemDictionary::ThreadGroup_klass();
  Handle threadObj(THREAD, this->threadObj());

  JavaCalls::call_special(&result,
                          thread_group,
                          group,
                          vmSymbols::add_method_name(),
                          vmSymbols::thread_void_signature(),
                          threadObj,          // Arg 1
                          THREAD);
}

// List of all NonJavaThreads and safe iteration over that list.

class NonJavaThread::List {
public:
  NonJavaThread* volatile _head;
  SingleWriterSynchronizer _protect;

  List() : _head(NULL), _protect() {}
};

NonJavaThread::List NonJavaThread::_the_list;

NonJavaThread::Iterator::Iterator() :
  _protect_enter(_the_list._protect.enter()),
  _current(OrderAccess::load_acquire(&_the_list._head))
{}

NonJavaThread::Iterator::~Iterator() {
  _the_list._protect.exit(_protect_enter);
}

void NonJavaThread::Iterator::step() {
  assert(!end(), "precondition");
  _current = OrderAccess::load_acquire(&_current->_next);
}

NonJavaThread::NonJavaThread() : Thread(), _next(NULL) {
  assert(BarrierSet::barrier_set() != NULL, "NonJavaThread created too soon!");
}

NonJavaThread::~NonJavaThread() { }

void NonJavaThread::add_to_the_list() {
  MutexLockerEx ml(NonJavaThreadsList_lock, Mutex::_no_safepoint_check_flag);
  // Initialize BarrierSet-related data before adding to list.
  BarrierSet::barrier_set()->on_thread_attach(this);
  OrderAccess::release_store(&_next, _the_list._head);
  OrderAccess::release_store(&_the_list._head, this);
}

void NonJavaThread::remove_from_the_list() {
  {
    MutexLockerEx ml(NonJavaThreadsList_lock, Mutex::_no_safepoint_check_flag);
    // Cleanup BarrierSet-related data before removing from list.
    BarrierSet::barrier_set()->on_thread_detach(this);
    NonJavaThread* volatile* p = &_the_list._head;
    for (NonJavaThread* t = *p; t != NULL; p = &t->_next, t = *p) {
      if (t == this) {
        *p = _next;
        break;
      }
    }
  }
  // Wait for any in-progress iterators.  Concurrent synchronize is not
  // allowed, so do it while holding a dedicated lock.  Outside and distinct
  // from NJTList_lock in case an iteration attempts to lock it.
  MutexLockerEx ml(NonJavaThreadsListSync_lock, Mutex::_no_safepoint_check_flag);
  _the_list._protect.synchronize();
  _next = NULL;                 // Safe to drop the link now.
}

void NonJavaThread::pre_run() {
  add_to_the_list();

  // This is slightly odd in that NamedThread is a subclass, but
  // in fact name() is defined in Thread
  assert(this->name() != NULL, "thread name was not set before it was started");
  this->set_native_thread_name(this->name());
}

void NonJavaThread::post_run() {
  JFR_ONLY(Jfr::on_thread_exit(this);)
  remove_from_the_list();
  // Ensure thread-local-storage is cleared before termination.
  Thread::clear_thread_current();
}

// NamedThread --  non-JavaThread subclasses with multiple
// uniquely named instances should derive from this.
NamedThread::NamedThread() :
  NonJavaThread(),
  _name(NULL),
  _processed_thread(NULL),
  _gc_id(GCId::undefined())
{}

NamedThread::~NamedThread() {
  if (_name != NULL) {
    FREE_C_HEAP_ARRAY(char, _name);
    _name = NULL;
  }
}

void NamedThread::set_name(const char* format, ...) {
  guarantee(_name == NULL, "Only get to set name once.");
  _name = NEW_C_HEAP_ARRAY(char, max_name_len, mtThread);
  guarantee(_name != NULL, "alloc failure");
  va_list ap;
  va_start(ap, format);
  jio_vsnprintf(_name, max_name_len, format, ap);
  va_end(ap);
}

void NamedThread::print_on(outputStream* st) const {
  st->print("\"%s\" ", name());
  Thread::print_on(st);
  st->cr();
}


// ======= WatcherThread ========

// The watcher thread exists to simulate timer interrupts.  It should
// be replaced by an abstraction over whatever native support for
// timer interrupts exists on the platform.

WatcherThread* WatcherThread::_watcher_thread   = NULL;
bool WatcherThread::_startable = false;
volatile bool  WatcherThread::_should_terminate = false;

WatcherThread::WatcherThread() : NonJavaThread() {
  assert(watcher_thread() == NULL, "we can only allocate one WatcherThread");
  if (os::create_thread(this, os::watcher_thread)) {
    _watcher_thread = this;

    // Set the watcher thread to the highest OS priority which should not be
    // used, unless a Java thread with priority java.lang.Thread.MAX_PRIORITY
    // is created. The only normal thread using this priority is the reference
    // handler thread, which runs for very short intervals only.
    // If the VMThread's priority is not lower than the WatcherThread profiling
    // will be inaccurate.
    os::set_priority(this, MaxPriority);
    if (!DisableStartThread) {
      os::start_thread(this);
    }
  }
}

int WatcherThread::sleep() const {
  // The WatcherThread does not participate in the safepoint protocol
  // for the PeriodicTask_lock because it is not a JavaThread.
  MutexLockerEx ml(PeriodicTask_lock, Mutex::_no_safepoint_check_flag);

  if (_should_terminate) {
    // check for termination before we do any housekeeping or wait
    return 0;  // we did not sleep.
  }

  // remaining will be zero if there are no tasks,
  // causing the WatcherThread to sleep until a task is
  // enrolled
  int remaining = PeriodicTask::time_to_wait();
  int time_slept = 0;

  // we expect this to timeout - we only ever get unparked when
  // we should terminate or when a new task has been enrolled
  OSThreadWaitState osts(this->osthread(), false /* not Object.wait() */);

  jlong time_before_loop = os::javaTimeNanos();

  while (true) {
    bool timedout = PeriodicTask_lock->wait(Mutex::_no_safepoint_check_flag,
                                            remaining);
    jlong now = os::javaTimeNanos();

    if (remaining == 0) {
      // if we didn't have any tasks we could have waited for a long time
      // consider the time_slept zero and reset time_before_loop
      time_slept = 0;
      time_before_loop = now;
    } else {
      // need to recalculate since we might have new tasks in _tasks
      time_slept = (int) ((now - time_before_loop) / 1000000);
    }

    // Change to task list or spurious wakeup of some kind
    if (timedout || _should_terminate) {
      break;
    }

    remaining = PeriodicTask::time_to_wait();
    if (remaining == 0) {
      // Last task was just disenrolled so loop around and wait until
      // another task gets enrolled
      continue;
    }

    remaining -= time_slept;
    if (remaining <= 0) {
      break;
    }
  }

  return time_slept;
}

void WatcherThread::run() {
  assert(this == watcher_thread(), "just checking");

  this->set_active_handles(JNIHandleBlock::allocate_block());
  while (true) {
    assert(watcher_thread() == Thread::current(), "thread consistency check");
    assert(watcher_thread() == this, "thread consistency check");

    // Calculate how long it'll be until the next PeriodicTask work
    // should be done, and sleep that amount of time.
    int time_waited = sleep();

    if (VMError::is_error_reported()) {
      // A fatal error has happened, the error handler(VMError::report_and_die)
      // should abort JVM after creating an error log file. However in some
      // rare cases, the error handler itself might deadlock. Here periodically
      // check for error reporting timeouts, and if it happens, just proceed to
      // abort the VM.

      // This code is in WatcherThread because WatcherThread wakes up
      // periodically so the fatal error handler doesn't need to do anything;
      // also because the WatcherThread is less likely to crash than other
      // threads.

      for (;;) {
        // Note: we use naked sleep in this loop because we want to avoid using
        // any kind of VM infrastructure which may be broken at this point.
        if (VMError::check_timeout()) {
          // We hit error reporting timeout. Error reporting was interrupted and
          // will be wrapping things up now (closing files etc). Give it some more
          // time, then quit the VM.
          os::naked_short_sleep(200);
          // Print a message to stderr.
          fdStream err(defaultStream::output_fd());
          err.print_raw_cr("# [ timer expired, abort... ]");
          // skip atexit/vm_exit/vm_abort hooks
          os::die();
        }

        // Wait a second, then recheck for timeout.
        os::naked_short_sleep(999);
      }
    }

    if (_should_terminate) {
      // check for termination before posting the next tick
      break;
    }

    PeriodicTask::real_time_tick(time_waited);
  }

  // Signal that it is terminated
  {
    MutexLockerEx mu(Terminator_lock, Mutex::_no_safepoint_check_flag);
    _watcher_thread = NULL;
    Terminator_lock->notify_all();
  }
}

void WatcherThread::start() {
  assert(PeriodicTask_lock->owned_by_self(), "PeriodicTask_lock required");

  if (watcher_thread() == NULL && _startable) {
    _should_terminate = false;
    // Create the single instance of WatcherThread
    new WatcherThread();
  }
}

void WatcherThread::make_startable() {
  assert(PeriodicTask_lock->owned_by_self(), "PeriodicTask_lock required");
  _startable = true;
}

void WatcherThread::stop() {
  {
    // Follow normal safepoint aware lock enter protocol since the
    // WatcherThread is stopped by another JavaThread.
    MutexLocker ml(PeriodicTask_lock);
    _should_terminate = true;

    WatcherThread* watcher = watcher_thread();
    if (watcher != NULL) {
      // unpark the WatcherThread so it can see that it should terminate
      watcher->unpark();
    }
  }

  MutexLocker mu(Terminator_lock);

  while (watcher_thread() != NULL) {
    // This wait should make safepoint checks, wait without a timeout,
    // and wait as a suspend-equivalent condition.
    Terminator_lock->wait(!Mutex::_no_safepoint_check_flag, 0,
                          Mutex::_as_suspend_equivalent_flag);
  }
}

void WatcherThread::unpark() {
  assert(PeriodicTask_lock->owned_by_self(), "PeriodicTask_lock required");
  PeriodicTask_lock->notify();
}

void WatcherThread::print_on(outputStream* st) const {
  st->print("\"%s\" ", name());
  Thread::print_on(st);
  st->cr();
}

// ======= JavaThread ========

#if INCLUDE_JVMCI

jlong* JavaThread::_jvmci_old_thread_counters;

bool jvmci_counters_include(JavaThread* thread) {
  return !JVMCICountersExcludeCompiler || !thread->is_Compiler_thread();
}

void JavaThread::collect_counters(typeArrayOop array) {
  if (JVMCICounterSize > 0) {
    JavaThreadIteratorWithHandle jtiwh;
    for (int i = 0; i < array->length(); i++) {
      array->long_at_put(i, _jvmci_old_thread_counters[i]);
    }
    for (; JavaThread *tp = jtiwh.next(); ) {
      if (jvmci_counters_include(tp)) {
        for (int i = 0; i < array->length(); i++) {
          array->long_at_put(i, array->long_at(i) + tp->_jvmci_counters[i]);
        }
      }
    }
  }
}

#endif // INCLUDE_JVMCI

// A JavaThread is a normal Java thread

void JavaThread::initialize() {
  // Initialize fields

  set_saved_exception_pc(NULL);
  set_threadObj(NULL);
  _anchor.clear();
  set_entry_point(NULL);
  set_jni_functions(jni_functions());
  set_callee_target(NULL);
  set_vm_result(NULL);
  set_vm_result_2(NULL);
  set_vframe_array_head(NULL);
  set_vframe_array_last(NULL);
  set_deferred_locals(NULL);
  set_deopt_mark(NULL);
  set_deopt_compiled_method(NULL);
  clear_must_deopt_id();
  set_monitor_chunks(NULL);
  set_next(NULL);
  _on_thread_list = false;
  set_thread_state(_thread_new);
  _terminated = _not_terminated;
  _array_for_gc = NULL;
  _suspend_equivalent = false;
  _in_deopt_handler = 0;
  _doing_unsafe_access = false;
  _stack_guard_state = stack_guard_unused;
#if INCLUDE_JVMCI
  _pending_monitorenter = false;
  _pending_deoptimization = -1;
  _pending_failed_speculation = 0;
  _pending_transfer_to_interpreter = false;
  _adjusting_comp_level = false;
  _in_retryable_allocation = false;
  _jvmci._alternate_call_target = NULL;
  assert(_jvmci._implicit_exception_pc == NULL, "must be");
  if (JVMCICounterSize > 0) {
    _jvmci_counters = NEW_C_HEAP_ARRAY(jlong, JVMCICounterSize, mtInternal);
    memset(_jvmci_counters, 0, sizeof(jlong) * JVMCICounterSize);
  } else {
    _jvmci_counters = NULL;
  }
#endif // INCLUDE_JVMCI
  _reserved_stack_activation = NULL;  // stack base not known yet
  (void)const_cast<oop&>(_exception_oop = oop(NULL));
  _exception_pc  = 0;
  _exception_handler_pc = 0;
  _is_method_handle_return = 0;
  _jvmti_thread_state= NULL;
  _should_post_on_exceptions_flag = JNI_FALSE;
  _interp_only_mode    = 0;
  _special_runtime_exit_condition = _no_async_condition;
  _pending_async_exception = NULL;
  _thread_stat = NULL;
  _thread_stat = new ThreadStatistics();
  _blocked_on_compilation = false;
  _jni_active_critical = 0;
  _pending_jni_exception_check_fn = NULL;
  _do_not_unlock_if_synchronized = false;
  _cached_monitor_info = NULL;
  _parker = Parker::Allocate(this);

#ifndef PRODUCT
  _jmp_ring_index = 0;
  for (int ji = 0; ji < jump_ring_buffer_size; ji++) {
    record_jump(NULL, NULL, NULL, 0);
  }
#endif // PRODUCT

  // Setup safepoint state info for this thread
  ThreadSafepointState::create(this);

  debug_only(_java_call_counter = 0);

  // JVMTI PopFrame support
  _popframe_condition = popframe_inactive;
  _popframe_preserved_args = NULL;
  _popframe_preserved_args_size = 0;
  _frames_to_pop_failed_realloc = 0;

  if (SafepointMechanism::uses_thread_local_poll()) {
    SafepointMechanism::initialize_header(this);
  }

  _class_to_be_initialized = NULL;

  pd_initialize();
}

JavaThread::JavaThread(bool is_attaching_via_jni) :
                       Thread() {
  initialize();
  if (is_attaching_via_jni) {
    _jni_attach_state = _attaching_via_jni;
  } else {
    _jni_attach_state = _not_attaching_via_jni;
  }
  assert(deferred_card_mark().is_empty(), "Default MemRegion ctor");
}

bool JavaThread::reguard_stack(address cur_sp) {
  if (_stack_guard_state != stack_guard_yellow_reserved_disabled
      && _stack_guard_state != stack_guard_reserved_disabled) {
    return true; // Stack already guarded or guard pages not needed.
  }

  if (register_stack_overflow()) {
    // For those architectures which have separate register and
    // memory stacks, we must check the register stack to see if
    // it has overflowed.
    return false;
  }

  // Java code never executes within the yellow zone: the latter is only
  // there to provoke an exception during stack banging.  If java code
  // is executing there, either StackShadowPages should be larger, or
  // some exception code in c1, c2 or the interpreter isn't unwinding
  // when it should.
  guarantee(cur_sp > stack_reserved_zone_base(),
            "not enough space to reguard - increase StackShadowPages");
  if (_stack_guard_state == stack_guard_yellow_reserved_disabled) {
    enable_stack_yellow_reserved_zone();
    if (reserved_stack_activation() != stack_base()) {
      set_reserved_stack_activation(stack_base());
    }
  } else if (_stack_guard_state == stack_guard_reserved_disabled) {
    set_reserved_stack_activation(stack_base());
    enable_stack_reserved_zone();
  }
  return true;
}

bool JavaThread::reguard_stack(void) {
  return reguard_stack(os::current_stack_pointer());
}


void JavaThread::block_if_vm_exited() {
  if (_terminated == _vm_exited) {
    // _vm_exited is set at safepoint, and Threads_lock is never released
    // we will block here forever
    Threads_lock->lock_without_safepoint_check();
    ShouldNotReachHere();
  }
}


// Remove this ifdef when C1 is ported to the compiler interface.
static void compiler_thread_entry(JavaThread* thread, TRAPS);
static void sweeper_thread_entry(JavaThread* thread, TRAPS);

JavaThread::JavaThread(ThreadFunction entry_point, size_t stack_sz) :
                       Thread() {
  initialize();
  _jni_attach_state = _not_attaching_via_jni;
  set_entry_point(entry_point);
  // Create the native thread itself.
  // %note runtime_23
  os::ThreadType thr_type = os::java_thread;
  thr_type = entry_point == &compiler_thread_entry ? os::compiler_thread :
                                                     os::java_thread;
  os::create_thread(this, thr_type, stack_sz);
  // The _osthread may be NULL here because we ran out of memory (too many threads active).
  // We need to throw and OutOfMemoryError - however we cannot do this here because the caller
  // may hold a lock and all locks must be unlocked before throwing the exception (throwing
  // the exception consists of creating the exception object & initializing it, initialization
  // will leave the VM via a JavaCall and then all locks must be unlocked).
  //
  // The thread is still suspended when we reach here. Thread must be explicit started
  // by creator! Furthermore, the thread must also explicitly be added to the Threads list
  // by calling Threads:add. The reason why this is not done here, is because the thread
  // object must be fully initialized (take a look at JVM_Start)
}

JavaThread::~JavaThread() {

  // JSR166 -- return the parker to the free list
  Parker::Release(_parker);
  _parker = NULL;

  // Free any remaining  previous UnrollBlock
  vframeArray* old_array = vframe_array_last();

  if (old_array != NULL) {
    Deoptimization::UnrollBlock* old_info = old_array->unroll_block();
    old_array->set_unroll_block(NULL);
    delete old_info;
    delete old_array;
  }

  GrowableArray<jvmtiDeferredLocalVariableSet*>* deferred = deferred_locals();
  if (deferred != NULL) {
    // This can only happen if thread is destroyed before deoptimization occurs.
    assert(deferred->length() != 0, "empty array!");
    do {
      jvmtiDeferredLocalVariableSet* dlv = deferred->at(0);
      deferred->remove_at(0);
      // individual jvmtiDeferredLocalVariableSet are CHeapObj's
      delete dlv;
    } while (deferred->length() != 0);
    delete deferred;
  }

  // All Java related clean up happens in exit
  ThreadSafepointState::destroy(this);
  if (_thread_stat != NULL) delete _thread_stat;

#if INCLUDE_JVMCI
  if (JVMCICounterSize > 0) {
    if (jvmci_counters_include(this)) {
      for (int i = 0; i < JVMCICounterSize; i++) {
        _jvmci_old_thread_counters[i] += _jvmci_counters[i];
      }
    }
    FREE_C_HEAP_ARRAY(jlong, _jvmci_counters);
  }
#endif // INCLUDE_JVMCI
}


// First JavaThread specific code executed by a new Java thread.
void JavaThread::pre_run() {
  // empty - see comments in run()
}

// The main routine called by a new Java thread. This isn't overridden
// by subclasses, instead different subclasses define a different "entry_point"
// which defines the actual logic for that kind of thread.
void JavaThread::run() {
  // initialize thread-local alloc buffer related fields
  this->initialize_tlab();

  // Used to test validity of stack trace backs.
  // This can't be moved into pre_run() else we invalidate
  // the requirement that thread_main_inner is lower on
  // the stack. Consequently all the initialization logic
  // stays here in run() rather than pre_run().
  this->record_base_of_stack_pointer();

  this->create_stack_guard_pages();

  this->cache_global_variables();

  // Thread is now sufficiently initialized to be handled by the safepoint code as being
  // in the VM. Change thread state from _thread_new to _thread_in_vm
  ThreadStateTransition::transition(this, _thread_new, _thread_in_vm);
  // Before a thread is on the threads list it is always safe, so after leaving the
  // _thread_new we should emit a instruction barrier. The distance to modified code
  // from here is probably far enough, but this is consistent and safe.
  OrderAccess::cross_modify_fence();

  assert(JavaThread::current() == this, "sanity check");
  assert(!Thread::current()->owns_locks(), "sanity check");

  DTRACE_THREAD_PROBE(start, this);

  // This operation might block. We call that after all safepoint checks for a new thread has
  // been completed.
  this->set_active_handles(JNIHandleBlock::allocate_block());

  if (JvmtiExport::should_post_thread_life()) {
    JvmtiExport::post_thread_start(this);

  }

  // We call another function to do the rest so we are sure that the stack addresses used
  // from there will be lower than the stack base just computed.
  thread_main_inner();
}

void JavaThread::thread_main_inner() {
  assert(JavaThread::current() == this, "sanity check");
  assert(this->threadObj() != NULL, "just checking");

  // Execute thread entry point unless this thread has a pending exception
  // or has been stopped before starting.
  // Note: Due to JVM_StopThread we can have pending exceptions already!
  if (!this->has_pending_exception() &&
      !java_lang_Thread::is_stillborn(this->threadObj())) {
    {
      ResourceMark rm(this);
      this->set_native_thread_name(this->get_thread_name());
    }
    HandleMark hm(this);
    this->entry_point()(this, this);
  }

  DTRACE_THREAD_PROBE(stop, this);

  // Cleanup is handled in post_run()
}

// Shared teardown for all JavaThreads
void JavaThread::post_run() {
  this->exit(false);
  // Defer deletion to here to ensure 'this' is still referenceable in call_run
  // for any shared tear-down.
  this->smr_delete();
}

static void ensure_join(JavaThread* thread) {
  // We do not need to grab the Threads_lock, since we are operating on ourself.
  Handle threadObj(thread, thread->threadObj());
  assert(threadObj.not_null(), "java thread object must exist");
  ObjectLocker lock(threadObj, thread);
  // Ignore pending exception (ThreadDeath), since we are exiting anyway
  thread->clear_pending_exception();
  // Thread is exiting. So set thread_status field in  java.lang.Thread class to TERMINATED.
  java_lang_Thread::set_thread_status(threadObj(), java_lang_Thread::TERMINATED);
  // Clear the native thread instance - this makes isAlive return false and allows the join()
  // to complete once we've done the notify_all below
  java_lang_Thread::set_thread(threadObj(), NULL);
  lock.notify_all(thread);
  // Ignore pending exception (ThreadDeath), since we are exiting anyway
  thread->clear_pending_exception();
}

static bool is_daemon(oop threadObj) {
  return (threadObj != NULL && java_lang_Thread::is_daemon(threadObj));
}

// For any new cleanup additions, please check to see if they need to be applied to
// cleanup_failed_attach_current_thread as well.
void JavaThread::exit(bool destroy_vm, ExitType exit_type) {
  assert(this == JavaThread::current(), "thread consistency check");

  elapsedTimer _timer_exit_phase1;
  elapsedTimer _timer_exit_phase2;
  elapsedTimer _timer_exit_phase3;
  elapsedTimer _timer_exit_phase4;

  if (log_is_enabled(Debug, os, thread, timer)) {
    _timer_exit_phase1.start();
  }

  HandleMark hm(this);
  Handle uncaught_exception(this, this->pending_exception());
  this->clear_pending_exception();
  Handle threadObj(this, this->threadObj());
  assert(threadObj.not_null(), "Java thread object should be created");

  // FIXIT: This code should be moved into else part, when reliable 1.2/1.3 check is in place
  {
    EXCEPTION_MARK;

    CLEAR_PENDING_EXCEPTION;
  }
  if (!destroy_vm) {
    if (uncaught_exception.not_null()) {
      EXCEPTION_MARK;
      // Call method Thread.dispatchUncaughtException().
      Klass* thread_klass = SystemDictionary::Thread_klass();
      JavaValue result(T_VOID);
      JavaCalls::call_virtual(&result,
                              threadObj, thread_klass,
                              vmSymbols::dispatchUncaughtException_name(),
                              vmSymbols::throwable_void_signature(),
                              uncaught_exception,
                              THREAD);
      if (HAS_PENDING_EXCEPTION) {
        ResourceMark rm(this);
        jio_fprintf(defaultStream::error_stream(),
                    "\nException: %s thrown from the UncaughtExceptionHandler"
                    " in thread \"%s\"\n",
                    pending_exception()->klass()->external_name(),
                    get_thread_name());
        CLEAR_PENDING_EXCEPTION;
      }
    }
    JFR_ONLY(Jfr::on_java_thread_dismantle(this);)

    // Call Thread.exit(). We try 3 times in case we got another Thread.stop during
    // the execution of the method. If that is not enough, then we don't really care. Thread.stop
    // is deprecated anyhow.
    if (!is_Compiler_thread()) {
      int count = 3;
      while (java_lang_Thread::threadGroup(threadObj()) != NULL && (count-- > 0)) {
        EXCEPTION_MARK;
        JavaValue result(T_VOID);
        Klass* thread_klass = SystemDictionary::Thread_klass();
        JavaCalls::call_virtual(&result,
                                threadObj, thread_klass,
                                vmSymbols::exit_method_name(),
                                vmSymbols::void_method_signature(),
                                THREAD);
        CLEAR_PENDING_EXCEPTION;
      }
    }
    // notify JVMTI
    if (JvmtiExport::should_post_thread_life()) {
      JvmtiExport::post_thread_end(this);
    }

    // We have notified the agents that we are exiting, before we go on,
    // we must check for a pending external suspend request and honor it
    // in order to not surprise the thread that made the suspend request.
    while (true) {
      {
        MutexLockerEx ml(SR_lock(), Mutex::_no_safepoint_check_flag);
        if (!is_external_suspend()) {
          set_terminated(_thread_exiting);
          ThreadService::current_thread_exiting(this, is_daemon(threadObj()));
          break;
        }
        // Implied else:
        // Things get a little tricky here. We have a pending external
        // suspend request, but we are holding the SR_lock so we
        // can't just self-suspend. So we temporarily drop the lock
        // and then self-suspend.
      }

      ThreadBlockInVM tbivm(this);
      java_suspend_self();

      // We're done with this suspend request, but we have to loop around
      // and check again. Eventually we will get SR_lock without a pending
      // external suspend request and will be able to mark ourselves as
      // exiting.
    }
    // no more external suspends are allowed at this point
  } else {
    assert(!is_terminated() && !is_exiting(), "must not be exiting");
    // before_exit() has already posted JVMTI THREAD_END events
  }

  if (log_is_enabled(Debug, os, thread, timer)) {
    _timer_exit_phase1.stop();
    _timer_exit_phase2.start();
  }

  // Capture daemon status before the thread is marked as terminated.
  bool daemon = is_daemon(threadObj());

  // Notify waiters on thread object. This has to be done after exit() is called
  // on the thread (if the thread is the last thread in a daemon ThreadGroup the
  // group should have the destroyed bit set before waiters are notified).
  ensure_join(this);
  assert(!this->has_pending_exception(), "ensure_join should have cleared");

  if (log_is_enabled(Debug, os, thread, timer)) {
    _timer_exit_phase2.stop();
    _timer_exit_phase3.start();
  }
  // 6282335 JNI DetachCurrentThread spec states that all Java monitors
  // held by this thread must be released. The spec does not distinguish
  // between JNI-acquired and regular Java monitors. We can only see
  // regular Java monitors here if monitor enter-exit matching is broken.
  //
  // ensure_join() ignores IllegalThreadStateExceptions, and so does
  // ObjectSynchronizer::release_monitors_owned_by_thread().
  if (exit_type == jni_detach) {
    // Sanity check even though JNI DetachCurrentThread() would have
    // returned JNI_ERR if there was a Java frame. JavaThread exit
    // should be done executing Java code by the time we get here.
    assert(!this->has_last_Java_frame(),
           "should not have a Java frame when detaching or exiting");
    ObjectSynchronizer::release_monitors_owned_by_thread(this);
    assert(!this->has_pending_exception(), "release_monitors should have cleared");
  }

  // These things needs to be done while we are still a Java Thread. Make sure that thread
  // is in a consistent state, in case GC happens
  JFR_ONLY(Jfr::on_thread_exit(this);)

  if (active_handles() != NULL) {
    JNIHandleBlock* block = active_handles();
    set_active_handles(NULL);
    JNIHandleBlock::release_block(block);
  }

  if (free_handle_block() != NULL) {
    JNIHandleBlock* block = free_handle_block();
    set_free_handle_block(NULL);
    JNIHandleBlock::release_block(block);
  }

  // These have to be removed while this is still a valid thread.
  remove_stack_guard_pages();

  if (UseTLAB) {
    tlab().retire();
  }

  if (JvmtiEnv::environments_might_exist()) {
    JvmtiExport::cleanup_thread(this);
  }

  // We must flush any deferred card marks and other various GC barrier
  // related buffers (e.g. G1 SATB buffer and G1 dirty card queue buffer)
  // before removing a thread from the list of active threads.
  BarrierSet::barrier_set()->on_thread_detach(this);

  log_info(os, thread)("JavaThread %s (tid: " UINTX_FORMAT ").",
    exit_type == JavaThread::normal_exit ? "exiting" : "detaching",
    os::current_thread_id());

  if (log_is_enabled(Debug, os, thread, timer)) {
    _timer_exit_phase3.stop();
    _timer_exit_phase4.start();
  }
  // Remove from list of active threads list, and notify VM thread if we are the last non-daemon thread
  Threads::remove(this, daemon);

  if (log_is_enabled(Debug, os, thread, timer)) {
    _timer_exit_phase4.stop();
    ResourceMark rm(this);
    log_debug(os, thread, timer)("name='%s'"
                                 ", exit-phase1=" JLONG_FORMAT
                                 ", exit-phase2=" JLONG_FORMAT
                                 ", exit-phase3=" JLONG_FORMAT
                                 ", exit-phase4=" JLONG_FORMAT,
                                 get_thread_name(),
                                 _timer_exit_phase1.milliseconds(),
                                 _timer_exit_phase2.milliseconds(),
                                 _timer_exit_phase3.milliseconds(),
                                 _timer_exit_phase4.milliseconds());
  }
}

void JavaThread::cleanup_failed_attach_current_thread(bool is_daemon) {
  if (active_handles() != NULL) {
    JNIHandleBlock* block = active_handles();
    set_active_handles(NULL);
    JNIHandleBlock::release_block(block);
  }

  if (free_handle_block() != NULL) {
    JNIHandleBlock* block = free_handle_block();
    set_free_handle_block(NULL);
    JNIHandleBlock::release_block(block);
  }

  // These have to be removed while this is still a valid thread.
  remove_stack_guard_pages();

  if (UseTLAB) {
    tlab().retire();
  }

  BarrierSet::barrier_set()->on_thread_detach(this);

  Threads::remove(this, is_daemon);
  this->smr_delete();
}

JavaThread* JavaThread::active() {
  Thread* thread = Thread::current();
  if (thread->is_Java_thread()) {
    return (JavaThread*) thread;
  } else {
    assert(thread->is_VM_thread(), "this must be a vm thread");
    VM_Operation* op = ((VMThread*) thread)->vm_operation();
    JavaThread *ret=op == NULL ? NULL : (JavaThread *)op->calling_thread();
    assert(ret->is_Java_thread(), "must be a Java thread");
    return ret;
  }
}

bool JavaThread::is_lock_owned(address adr) const {
  if (Thread::is_lock_owned(adr)) return true;

  for (MonitorChunk* chunk = monitor_chunks(); chunk != NULL; chunk = chunk->next()) {
    if (chunk->contains(adr)) return true;
  }

  return false;
}


void JavaThread::add_monitor_chunk(MonitorChunk* chunk) {
  chunk->set_next(monitor_chunks());
  set_monitor_chunks(chunk);
}

void JavaThread::remove_monitor_chunk(MonitorChunk* chunk) {
  guarantee(monitor_chunks() != NULL, "must be non empty");
  if (monitor_chunks() == chunk) {
    set_monitor_chunks(chunk->next());
  } else {
    MonitorChunk* prev = monitor_chunks();
    while (prev->next() != chunk) prev = prev->next();
    prev->set_next(chunk->next());
  }
}

// JVM support.

// Note: this function shouldn't block if it's called in
// _thread_in_native_trans state (such as from
// check_special_condition_for_native_trans()).
void JavaThread::check_and_handle_async_exceptions(bool check_unsafe_error) {

  if (has_last_Java_frame() && has_async_condition()) {
    // If we are at a polling page safepoint (not a poll return)
    // then we must defer async exception because live registers
    // will be clobbered by the exception path. Poll return is
    // ok because the call we a returning from already collides
    // with exception handling registers and so there is no issue.
    // (The exception handling path kills call result registers but
    //  this is ok since the exception kills the result anyway).

    if (is_at_poll_safepoint()) {
      // if the code we are returning to has deoptimized we must defer
      // the exception otherwise live registers get clobbered on the
      // exception path before deoptimization is able to retrieve them.
      //
      RegisterMap map(this, false);
      frame caller_fr = last_frame().sender(&map);
      assert(caller_fr.is_compiled_frame(), "what?");
      if (caller_fr.is_deoptimized_frame()) {
        log_info(exceptions)("deferred async exception at compiled safepoint");
        return;
      }
    }
  }

  JavaThread::AsyncRequests condition = clear_special_runtime_exit_condition();
  if (condition == _no_async_condition) {
    // Conditions have changed since has_special_runtime_exit_condition()
    // was called:
    // - if we were here only because of an external suspend request,
    //   then that was taken care of above (or cancelled) so we are done
    // - if we were here because of another async request, then it has
    //   been cleared between the has_special_runtime_exit_condition()
    //   and now so again we are done
    return;
  }

  // Check for pending async. exception
  if (_pending_async_exception != NULL) {
    // Only overwrite an already pending exception, if it is not a threadDeath.
    if (!has_pending_exception() || !pending_exception()->is_a(SystemDictionary::ThreadDeath_klass())) {

      // We cannot call Exceptions::_throw(...) here because we cannot block
      set_pending_exception(_pending_async_exception, __FILE__, __LINE__);

      LogTarget(Info, exceptions) lt;
      if (lt.is_enabled()) {
        ResourceMark rm;
        LogStream ls(lt);
        ls.print("Async. exception installed at runtime exit (" INTPTR_FORMAT ")", p2i(this));
          if (has_last_Java_frame()) {
            frame f = last_frame();
           ls.print(" (pc: " INTPTR_FORMAT " sp: " INTPTR_FORMAT " )", p2i(f.pc()), p2i(f.sp()));
          }
        ls.print_cr(" of type: %s", _pending_async_exception->klass()->external_name());
      }
      _pending_async_exception = NULL;
      clear_has_async_exception();
    }
  }

  if (check_unsafe_error &&
      condition == _async_unsafe_access_error && !has_pending_exception()) {
    condition = _no_async_condition;  // done
    switch (thread_state()) {
    case _thread_in_vm: {
      JavaThread* THREAD = this;
      THROW_MSG(vmSymbols::java_lang_InternalError(), "a fault occurred in an unsafe memory access operation");
    }
    case _thread_in_native: {
      ThreadInVMfromNative tiv(this);
      JavaThread* THREAD = this;
      THROW_MSG(vmSymbols::java_lang_InternalError(), "a fault occurred in an unsafe memory access operation");
    }
    case _thread_in_Java: {
      ThreadInVMfromJava tiv(this);
      JavaThread* THREAD = this;
      THROW_MSG(vmSymbols::java_lang_InternalError(), "a fault occurred in a recent unsafe memory access operation in compiled Java code");
    }
    default:
      ShouldNotReachHere();
    }
  }

  assert(condition == _no_async_condition || has_pending_exception() ||
         (!check_unsafe_error && condition == _async_unsafe_access_error),
         "must have handled the async condition, if no exception");
}

void JavaThread::handle_special_runtime_exit_condition(bool check_asyncs) {
  //
  // Check for pending external suspend.
  // If JNIEnv proxies are allowed, don't self-suspend if the target
  // thread is not the current thread. In older versions of jdbx, jdbx
  // threads could call into the VM with another thread's JNIEnv so we
  // can be here operating on behalf of a suspended thread (4432884).
  bool do_self_suspend = is_external_suspend_with_lock();
  if (do_self_suspend && (!AllowJNIEnvProxy || this == JavaThread::current())) {
    frame_anchor()->make_walkable(this);
    java_suspend_self_with_safepoint_check();
  }

  // We might be here for reasons in addition to the self-suspend request
  // so check for other async requests.
  if (check_asyncs) {
    check_and_handle_async_exceptions();
  }

  JFR_ONLY(SUSPEND_THREAD_CONDITIONAL(this);)
}

void JavaThread::send_thread_stop(oop java_throwable)  {
  assert(Thread::current()->is_VM_thread(), "should be in the vm thread");
  assert(Threads_lock->is_locked(), "Threads_lock should be locked by safepoint code");
  assert(SafepointSynchronize::is_at_safepoint(), "all threads are stopped");

  // Do not throw asynchronous exceptions against the compiler thread
  // (the compiler thread should not be a Java thread -- fix in 1.4.2)
  if (!can_call_java()) return;

  {
    // Actually throw the Throwable against the target Thread - however
    // only if there is no thread death exception installed already.
    if (_pending_async_exception == NULL || !_pending_async_exception->is_a(SystemDictionary::ThreadDeath_klass())) {
      // If the topmost frame is a runtime stub, then we are calling into
      // OptoRuntime from compiled code. Some runtime stubs (new, monitor_exit..)
      // must deoptimize the caller before continuing, as the compiled  exception handler table
      // may not be valid
      if (has_last_Java_frame()) {
        frame f = last_frame();
        if (f.is_runtime_frame() || f.is_safepoint_blob_frame()) {
          // BiasedLocking needs an updated RegisterMap for the revoke monitors pass
          RegisterMap reg_map(this, UseBiasedLocking);
          frame compiled_frame = f.sender(&reg_map);
          if (!StressCompiledExceptionHandlers && compiled_frame.can_be_deoptimized()) {
            Deoptimization::deoptimize(this, compiled_frame, &reg_map);
          }
        }
      }

      // Set async. pending exception in thread.
      set_pending_async_exception(java_throwable);

      if (log_is_enabled(Info, exceptions)) {
         ResourceMark rm;
        log_info(exceptions)("Pending Async. exception installed of type: %s",
                             InstanceKlass::cast(_pending_async_exception->klass())->external_name());
      }
      // for AbortVMOnException flag
      Exceptions::debug_check_abort(_pending_async_exception->klass()->external_name());
    }
  }


  // Interrupt thread so it will wake up from a potential wait()
  Thread::interrupt(this);
}

// External suspension mechanism.
//
// Tell the VM to suspend a thread when ever it knows that it does not hold on
// to any VM_locks and it is at a transition
// Self-suspension will happen on the transition out of the vm.
// Catch "this" coming in from JNIEnv pointers when the thread has been freed
//
// Guarantees on return:
//   + Target thread will not execute any new bytecode (that's why we need to
//     force a safepoint)
//   + Target thread will not enter any new monitors
//
void JavaThread::java_suspend() {
  ThreadsListHandle tlh;
  if (!tlh.includes(this) || threadObj() == NULL || is_exiting()) {
    return;
  }

  { MutexLockerEx ml(SR_lock(), Mutex::_no_safepoint_check_flag);
    if (!is_external_suspend()) {
      // a racing resume has cancelled us; bail out now
      return;
    }

    // suspend is done
    uint32_t debug_bits = 0;
    // Warning: is_ext_suspend_completed() may temporarily drop the
    // SR_lock to allow the thread to reach a stable thread state if
    // it is currently in a transient thread state.
    if (is_ext_suspend_completed(false /* !called_by_wait */,
                                 SuspendRetryDelay, &debug_bits)) {
      return;
    }
  }

  if (Thread::current() == this) {
    // Safely self-suspend.
    // If we don't do this explicitly it will implicitly happen
    // before we transition back to Java, and on some other thread-state
    // transition paths, but not as we exit a JVM TI SuspendThread call.
    // As SuspendThread(current) must not return (until resumed) we must
    // self-suspend here.
    ThreadBlockInVM tbivm(this);
    java_suspend_self();
  } else {
    VM_ThreadSuspend vm_suspend;
    VMThread::execute(&vm_suspend);
  }
}

// Part II of external suspension.
// A JavaThread self suspends when it detects a pending external suspend
// request. This is usually on transitions. It is also done in places
// where continuing to the next transition would surprise the caller,
// e.g., monitor entry.
//
// Returns the number of times that the thread self-suspended.
//
// Note: DO NOT call java_suspend_self() when you just want to block current
//       thread. java_suspend_self() is the second stage of cooperative
//       suspension for external suspend requests and should only be used
//       to complete an external suspend request.
//
int JavaThread::java_suspend_self() {
  assert(thread_state() == _thread_blocked, "wrong state for java_suspend_self()");
  int ret = 0;

  // we are in the process of exiting so don't suspend
  if (is_exiting()) {
    clear_external_suspend();
    return ret;
  }

  assert(_anchor.walkable() ||
         (is_Java_thread() && !((JavaThread*)this)->has_last_Java_frame()),
         "must have walkable stack");

  MutexLockerEx ml(SR_lock(), Mutex::_no_safepoint_check_flag);

  assert(!this->is_ext_suspended(),
         "a thread trying to self-suspend should not already be suspended");

  if (this->is_suspend_equivalent()) {
    // If we are self-suspending as a result of the lifting of a
    // suspend equivalent condition, then the suspend_equivalent
    // flag is not cleared until we set the ext_suspended flag so
    // that wait_for_ext_suspend_completion() returns consistent
    // results.
    this->clear_suspend_equivalent();
  }

  // A racing resume may have cancelled us before we grabbed SR_lock
  // above. Or another external suspend request could be waiting for us
  // by the time we return from SR_lock()->wait(). The thread
  // that requested the suspension may already be trying to walk our
  // stack and if we return now, we can change the stack out from under
  // it. This would be a "bad thing (TM)" and cause the stack walker
  // to crash. We stay self-suspended until there are no more pending
  // external suspend requests.
  while (is_external_suspend()) {
    ret++;
    this->set_ext_suspended();

    // _ext_suspended flag is cleared by java_resume()
    while (is_ext_suspended()) {
      this->SR_lock()->wait(Mutex::_no_safepoint_check_flag);
    }
  }
  return ret;
}

// Helper routine to set up the correct thread state before calling java_suspend_self.
// This is called when regular thread-state transition helpers can't be used because
// we can be in various states, in particular _thread_in_native_trans.
// Because this thread is external suspended the safepoint code will count it as at
// a safepoint, regardless of what its actual current thread-state is. But
// is_ext_suspend_completed() may be waiting to see a thread transition from
// _thread_in_native_trans to _thread_blocked. So we set the thread state directly
// to _thread_blocked. The problem with setting thread state directly is that a
// safepoint could happen just after java_suspend_self() returns after being resumed,
// and the VM thread will see the _thread_blocked state. So we must check for a safepoint
// after restoring the state to make sure we won't leave while a safepoint is in progress.
// However, not all initial-states are allowed when performing a safepoint check, as we
// should never be blocking at a safepoint whilst in those states. Of these 'bad' states
// only _thread_in_native is possible when executing this code (based on our two callers).
// A thread that is _thread_in_native is already safepoint-safe and so it doesn't matter
// whether the VMThread sees the _thread_blocked state, or the _thread_in_native state,
// and so we don't need the explicit safepoint check.

void JavaThread::java_suspend_self_with_safepoint_check() {
  assert(this == Thread::current(), "invariant");
  JavaThreadState state = thread_state();
  set_thread_state(_thread_blocked);
  java_suspend_self();
  set_thread_state_fence(state);
  // Since we are not using a regular thread-state transition helper here,
  // we must manually emit the instruction barrier after leaving a safe state.
  OrderAccess::cross_modify_fence();
  if (state != _thread_in_native) {
    SafepointMechanism::block_if_requested(this);
  }
}

#ifdef ASSERT
// Verify the JavaThread has not yet been published in the Threads::list, and
// hence doesn't need protection from concurrent access at this stage.
void JavaThread::verify_not_published() {
  // Cannot create a ThreadsListHandle here and check !tlh.includes(this)
  // since an unpublished JavaThread doesn't participate in the
  // Thread-SMR protocol for keeping a ThreadsList alive.
  assert(!on_thread_list(), "JavaThread shouldn't have been published yet!");
}
#endif

// Slow path when the native==>VM/Java barriers detect a safepoint is in
// progress or when _suspend_flags is non-zero.
// Current thread needs to self-suspend if there is a suspend request and/or
// block if a safepoint is in progress.
// Async exception ISN'T checked.
// Note only the ThreadInVMfromNative transition can call this function
// directly and when thread state is _thread_in_native_trans
void JavaThread::check_safepoint_and_suspend_for_native_trans(JavaThread *thread) {
  assert(thread->thread_state() == _thread_in_native_trans, "wrong state");

  JavaThread *curJT = JavaThread::current();
  bool do_self_suspend = thread->is_external_suspend();

  assert(!curJT->has_last_Java_frame() || curJT->frame_anchor()->walkable(), "Unwalkable stack in native->vm transition");

  // If JNIEnv proxies are allowed, don't self-suspend if the target
  // thread is not the current thread. In older versions of jdbx, jdbx
  // threads could call into the VM with another thread's JNIEnv so we
  // can be here operating on behalf of a suspended thread (4432884).
  if (do_self_suspend && (!AllowJNIEnvProxy || curJT == thread)) {
    thread->java_suspend_self_with_safepoint_check();
  } else {
    SafepointMechanism::block_if_requested(curJT);
  }

  if (thread->is_deopt_suspend()) {
    thread->clear_deopt_suspend();
    RegisterMap map(thread, false);
    frame f = thread->last_frame();
    while (f.id() != thread->must_deopt_id() && ! f.is_first_frame()) {
      f = f.sender(&map);
    }
    if (f.id() == thread->must_deopt_id()) {
      thread->clear_must_deopt_id();
      f.deoptimize(thread);
    } else {
      fatal("missed deoptimization!");
    }
  }

  JFR_ONLY(SUSPEND_THREAD_CONDITIONAL(thread);)
}

// Slow path when the native==>VM/Java barriers detect a safepoint is in
// progress or when _suspend_flags is non-zero.
// Current thread needs to self-suspend if there is a suspend request and/or
// block if a safepoint is in progress.
// Also check for pending async exception (not including unsafe access error).
// Note only the native==>VM/Java barriers can call this function and when
// thread state is _thread_in_native_trans.
void JavaThread::check_special_condition_for_native_trans(JavaThread *thread) {
  check_safepoint_and_suspend_for_native_trans(thread);

  if (thread->has_async_exception()) {
    // We are in _thread_in_native_trans state, don't handle unsafe
    // access error since that may block.
    thread->check_and_handle_async_exceptions(false);
  }
}

// This is a variant of the normal
// check_special_condition_for_native_trans with slightly different
// semantics for use by critical native wrappers.  It does all the
// normal checks but also performs the transition back into
// thread_in_Java state.  This is required so that critical natives
// can potentially block and perform a GC if they are the last thread
// exiting the GCLocker.
void JavaThread::check_special_condition_for_native_trans_and_transition(JavaThread *thread) {
  check_special_condition_for_native_trans(thread);

  // Finish the transition
  thread->set_thread_state(_thread_in_Java);

  if (thread->do_critical_native_unlock()) {
    ThreadInVMfromJavaNoAsyncException tiv(thread);
    GCLocker::unlock_critical(thread);
    thread->clear_critical_native_unlock();
  }
}

// We need to guarantee the Threads_lock here, since resumes are not
// allowed during safepoint synchronization
// Can only resume from an external suspension
void JavaThread::java_resume() {
  assert_locked_or_safepoint(Threads_lock);

  // Sanity check: thread is gone, has started exiting or the thread
  // was not externally suspended.
  ThreadsListHandle tlh;
  if (!tlh.includes(this) || is_exiting() || !is_external_suspend()) {
    return;
  }

  MutexLockerEx ml(SR_lock(), Mutex::_no_safepoint_check_flag);

  clear_external_suspend();

  if (is_ext_suspended()) {
    clear_ext_suspended();
    SR_lock()->notify_all();
  }
}

size_t JavaThread::_stack_red_zone_size = 0;
size_t JavaThread::_stack_yellow_zone_size = 0;
size_t JavaThread::_stack_reserved_zone_size = 0;
size_t JavaThread::_stack_shadow_zone_size = 0;

void JavaThread::create_stack_guard_pages() {
  if (!os::uses_stack_guard_pages() ||
      _stack_guard_state != stack_guard_unused ||
      (DisablePrimordialThreadGuardPages && os::is_primordial_thread())) {
      log_info(os, thread)("Stack guard page creation for thread "
                           UINTX_FORMAT " disabled", os::current_thread_id());
    return;
  }
  address low_addr = stack_end();
  size_t len = stack_guard_zone_size();

  assert(is_aligned(low_addr, os::vm_page_size()), "Stack base should be the start of a page");
  assert(is_aligned(len, os::vm_page_size()), "Stack size should be a multiple of page size");

  int must_commit = os::must_commit_stack_guard_pages();
  // warning("Guarding at " PTR_FORMAT " for len " SIZE_FORMAT "\n", low_addr, len);

  if (must_commit && !os::create_stack_guard_pages((char *) low_addr, len)) {
    log_warning(os, thread)("Attempt to allocate stack guard pages failed.");
    return;
  }

  if (os::guard_memory((char *) low_addr, len)) {
    _stack_guard_state = stack_guard_enabled;
  } else {
    log_warning(os, thread)("Attempt to protect stack guard pages failed ("
      PTR_FORMAT "-" PTR_FORMAT ").", p2i(low_addr), p2i(low_addr + len));
    if (os::uncommit_memory((char *) low_addr, len)) {
      log_warning(os, thread)("Attempt to deallocate stack guard pages failed.");
    }
    return;
  }

  log_debug(os, thread)("Thread " UINTX_FORMAT " stack guard pages activated: "
    PTR_FORMAT "-" PTR_FORMAT ".",
    os::current_thread_id(), p2i(low_addr), p2i(low_addr + len));
}

void JavaThread::remove_stack_guard_pages() {
  assert(Thread::current() == this, "from different thread");
  if (_stack_guard_state == stack_guard_unused) return;
  address low_addr = stack_end();
  size_t len = stack_guard_zone_size();

  if (os::must_commit_stack_guard_pages()) {
    if (os::remove_stack_guard_pages((char *) low_addr, len)) {
      _stack_guard_state = stack_guard_unused;
    } else {
      log_warning(os, thread)("Attempt to deallocate stack guard pages failed ("
        PTR_FORMAT "-" PTR_FORMAT ").", p2i(low_addr), p2i(low_addr + len));
      return;
    }
  } else {
    if (_stack_guard_state == stack_guard_unused) return;
    if (os::unguard_memory((char *) low_addr, len)) {
      _stack_guard_state = stack_guard_unused;
    } else {
      log_warning(os, thread)("Attempt to unprotect stack guard pages failed ("
        PTR_FORMAT "-" PTR_FORMAT ").", p2i(low_addr), p2i(low_addr + len));
      return;
    }
  }

  log_debug(os, thread)("Thread " UINTX_FORMAT " stack guard pages removed: "
    PTR_FORMAT "-" PTR_FORMAT ".",
    os::current_thread_id(), p2i(low_addr), p2i(low_addr + len));
}

void JavaThread::enable_stack_reserved_zone() {
  assert(_stack_guard_state == stack_guard_reserved_disabled, "inconsistent state");

  // The base notation is from the stack's point of view, growing downward.
  // We need to adjust it to work correctly with guard_memory()
  address base = stack_reserved_zone_base() - stack_reserved_zone_size();

  guarantee(base < stack_base(),"Error calculating stack reserved zone");
  guarantee(base < os::current_stack_pointer(),"Error calculating stack reserved zone");

  if (os::guard_memory((char *) base, stack_reserved_zone_size())) {
    _stack_guard_state = stack_guard_enabled;
  } else {
    warning("Attempt to guard stack reserved zone failed.");
  }
  enable_register_stack_guard();
}

void JavaThread::disable_stack_reserved_zone() {
  assert(_stack_guard_state == stack_guard_enabled, "inconsistent state");

  // Simply return if called for a thread that does not use guard pages.
  if (_stack_guard_state != stack_guard_enabled) return;

  // The base notation is from the stack's point of view, growing downward.
  // We need to adjust it to work correctly with guard_memory()
  address base = stack_reserved_zone_base() - stack_reserved_zone_size();

  if (os::unguard_memory((char *)base, stack_reserved_zone_size())) {
    _stack_guard_state = stack_guard_reserved_disabled;
  } else {
    warning("Attempt to unguard stack reserved zone failed.");
  }
  disable_register_stack_guard();
}

void JavaThread::enable_stack_yellow_reserved_zone() {
  assert(_stack_guard_state != stack_guard_unused, "must be using guard pages.");
  assert(_stack_guard_state != stack_guard_enabled, "already enabled");

  // The base notation is from the stacks point of view, growing downward.
  // We need to adjust it to work correctly with guard_memory()
  address base = stack_red_zone_base();

  guarantee(base < stack_base(), "Error calculating stack yellow zone");
  guarantee(base < os::current_stack_pointer(), "Error calculating stack yellow zone");

  if (os::guard_memory((char *) base, stack_yellow_reserved_zone_size())) {
    _stack_guard_state = stack_guard_enabled;
  } else {
    warning("Attempt to guard stack yellow zone failed.");
  }
  enable_register_stack_guard();
}

void JavaThread::disable_stack_yellow_reserved_zone() {
  assert(_stack_guard_state != stack_guard_unused, "must be using guard pages.");
  assert(_stack_guard_state != stack_guard_yellow_reserved_disabled, "already disabled");

  // Simply return if called for a thread that does not use guard pages.
  if (_stack_guard_state == stack_guard_unused) return;

  // The base notation is from the stacks point of view, growing downward.
  // We need to adjust it to work correctly with guard_memory()
  address base = stack_red_zone_base();

  if (os::unguard_memory((char *)base, stack_yellow_reserved_zone_size())) {
    _stack_guard_state = stack_guard_yellow_reserved_disabled;
  } else {
    warning("Attempt to unguard stack yellow zone failed.");
  }
  disable_register_stack_guard();
}

void JavaThread::enable_stack_red_zone() {
  // The base notation is from the stacks point of view, growing downward.
  // We need to adjust it to work correctly with guard_memory()
  assert(_stack_guard_state != stack_guard_unused, "must be using guard pages.");
  address base = stack_red_zone_base() - stack_red_zone_size();

  guarantee(base < stack_base(), "Error calculating stack red zone");
  guarantee(base < os::current_stack_pointer(), "Error calculating stack red zone");

  if (!os::guard_memory((char *) base, stack_red_zone_size())) {
    warning("Attempt to guard stack red zone failed.");
  }
}

void JavaThread::disable_stack_red_zone() {
  // The base notation is from the stacks point of view, growing downward.
  // We need to adjust it to work correctly with guard_memory()
  assert(_stack_guard_state != stack_guard_unused, "must be using guard pages.");
  address base = stack_red_zone_base() - stack_red_zone_size();
  if (!os::unguard_memory((char *)base, stack_red_zone_size())) {
    warning("Attempt to unguard stack red zone failed.");
  }
}

void JavaThread::frames_do(void f(frame*, const RegisterMap* map)) {
  // ignore is there is no stack
  if (!has_last_Java_frame()) return;
  // traverse the stack frames. Starts from top frame.
  for (StackFrameStream fst(this); !fst.is_done(); fst.next()) {
    frame* fr = fst.current();
    f(fr, fst.register_map());
  }
}


#ifndef PRODUCT
// Deoptimization
// Function for testing deoptimization
void JavaThread::deoptimize() {
  // BiasedLocking needs an updated RegisterMap for the revoke monitors pass
  StackFrameStream fst(this, UseBiasedLocking);
  bool deopt = false;           // Dump stack only if a deopt actually happens.
  bool only_at = strlen(DeoptimizeOnlyAt) > 0;
  // Iterate over all frames in the thread and deoptimize
  for (; !fst.is_done(); fst.next()) {
    if (fst.current()->can_be_deoptimized()) {

      if (only_at) {
        // Deoptimize only at particular bcis.  DeoptimizeOnlyAt
        // consists of comma or carriage return separated numbers so
        // search for the current bci in that string.
        address pc = fst.current()->pc();
        nmethod* nm =  (nmethod*) fst.current()->cb();
        ScopeDesc* sd = nm->scope_desc_at(pc);
        char buffer[8];
        jio_snprintf(buffer, sizeof(buffer), "%d", sd->bci());
        size_t len = strlen(buffer);
        const char * found = strstr(DeoptimizeOnlyAt, buffer);
        while (found != NULL) {
          if ((found[len] == ',' || found[len] == '\n' || found[len] == '\0') &&
              (found == DeoptimizeOnlyAt || found[-1] == ',' || found[-1] == '\n')) {
            // Check that the bci found is bracketed by terminators.
            break;
          }
          found = strstr(found + 1, buffer);
        }
        if (!found) {
          continue;
        }
      }

      if (DebugDeoptimization && !deopt) {
        deopt = true; // One-time only print before deopt
        tty->print_cr("[BEFORE Deoptimization]");
        trace_frames();
        trace_stack();
      }
      Deoptimization::deoptimize(this, *fst.current(), fst.register_map());
    }
  }

  if (DebugDeoptimization && deopt) {
    tty->print_cr("[AFTER Deoptimization]");
    trace_frames();
  }
}


// Make zombies
void JavaThread::make_zombies() {
  for (StackFrameStream fst(this); !fst.is_done(); fst.next()) {
    if (fst.current()->can_be_deoptimized()) {
      // it is a Java nmethod
      nmethod* nm = CodeCache::find_nmethod(fst.current()->pc());
      nm->make_not_entrant();
    }
  }
}
#endif // PRODUCT


void JavaThread::deoptimized_wrt_marked_nmethods() {
  if (!has_last_Java_frame()) return;
  // BiasedLocking needs an updated RegisterMap for the revoke monitors pass
  StackFrameStream fst(this, UseBiasedLocking);
  for (; !fst.is_done(); fst.next()) {
    if (fst.current()->should_be_deoptimized()) {
      Deoptimization::deoptimize(this, *fst.current(), fst.register_map());
    }
  }
}


// If the caller is a NamedThread, then remember, in the current scope,
// the given JavaThread in its _processed_thread field.
class RememberProcessedThread: public StackObj {
  NamedThread* _cur_thr;
 public:
  RememberProcessedThread(JavaThread* jthr) {
    Thread* thread = Thread::current();
    if (thread->is_Named_thread()) {
      _cur_thr = (NamedThread *)thread;
      _cur_thr->set_processed_thread(jthr);
    } else {
      _cur_thr = NULL;
    }
  }

  ~RememberProcessedThread() {
    if (_cur_thr) {
      _cur_thr->set_processed_thread(NULL);
    }
  }
};

void JavaThread::oops_do(OopClosure* f, CodeBlobClosure* cf) {
  // Verify that the deferred card marks have been flushed.
  assert(deferred_card_mark().is_empty(), "Should be empty during GC");

  // Traverse the GCHandles
  Thread::oops_do(f, cf);

  assert((!has_last_Java_frame() && java_call_counter() == 0) ||
         (has_last_Java_frame() && java_call_counter() > 0), "wrong java_sp info!");

  if (has_last_Java_frame()) {
    // Record JavaThread to GC thread
    RememberProcessedThread rpt(this);

    // traverse the registered growable array
    if (_array_for_gc != NULL) {
      for (int index = 0; index < _array_for_gc->length(); index++) {
        f->do_oop(_array_for_gc->adr_at(index));
      }
    }

    // Traverse the monitor chunks
    for (MonitorChunk* chunk = monitor_chunks(); chunk != NULL; chunk = chunk->next()) {
      chunk->oops_do(f);
    }

    // Traverse the execution stack
    for (StackFrameStream fst(this); !fst.is_done(); fst.next()) {
      fst.current()->oops_do(f, cf, fst.register_map());
    }
  }

  // callee_target is never live across a gc point so NULL it here should
  // it still contain a methdOop.

  set_callee_target(NULL);

  assert(vframe_array_head() == NULL, "deopt in progress at a safepoint!");
  // If we have deferred set_locals there might be oops waiting to be
  // written
  GrowableArray<jvmtiDeferredLocalVariableSet*>* list = deferred_locals();
  if (list != NULL) {
    for (int i = 0; i < list->length(); i++) {
      list->at(i)->oops_do(f);
    }
  }

  // Traverse instance variables at the end since the GC may be moving things
  // around using this function
  f->do_oop((oop*) &_threadObj);
  f->do_oop((oop*) &_vm_result);
  f->do_oop((oop*) &_exception_oop);
  f->do_oop((oop*) &_pending_async_exception);

  if (jvmti_thread_state() != NULL) {
    jvmti_thread_state()->oops_do(f);
  }
}

#ifdef ASSERT
void JavaThread::verify_states_for_handshake() {
  // This checks that the thread has a correct frame state during a handshake.
  assert((!has_last_Java_frame() && java_call_counter() == 0) ||
         (has_last_Java_frame() && java_call_counter() > 0),
         "unexpected frame info: has_last_frame=%d, java_call_counter=%d",
         has_last_Java_frame(), java_call_counter());
}
#endif

void JavaThread::nmethods_do(CodeBlobClosure* cf) {
  assert((!has_last_Java_frame() && java_call_counter() == 0) ||
         (has_last_Java_frame() && java_call_counter() > 0),
         "unexpected frame info: has_last_frame=%d, java_call_counter=%d",
         has_last_Java_frame(), java_call_counter());

  if (has_last_Java_frame()) {
    // Traverse the execution stack
    for (StackFrameStream fst(this); !fst.is_done(); fst.next()) {
      fst.current()->nmethods_do(cf);
    }
  }
}

void JavaThread::metadata_do(MetadataClosure* f) {
  if (has_last_Java_frame()) {
    // Traverse the execution stack to call f() on the methods in the stack
    for (StackFrameStream fst(this); !fst.is_done(); fst.next()) {
      fst.current()->metadata_do(f);
    }
  } else if (is_Compiler_thread()) {
    // need to walk ciMetadata in current compile tasks to keep alive.
    CompilerThread* ct = (CompilerThread*)this;
    if (ct->env() != NULL) {
      ct->env()->metadata_do(f);
    }
    CompileTask* task = ct->task();
    if (task != NULL) {
      task->metadata_do(f);
    }
  }
}

// Printing
const char* _get_thread_state_name(JavaThreadState _thread_state) {
  switch (_thread_state) {
  case _thread_uninitialized:     return "_thread_uninitialized";
  case _thread_new:               return "_thread_new";
  case _thread_new_trans:         return "_thread_new_trans";
  case _thread_in_native:         return "_thread_in_native";
  case _thread_in_native_trans:   return "_thread_in_native_trans";
  case _thread_in_vm:             return "_thread_in_vm";
  case _thread_in_vm_trans:       return "_thread_in_vm_trans";
  case _thread_in_Java:           return "_thread_in_Java";
  case _thread_in_Java_trans:     return "_thread_in_Java_trans";
  case _thread_blocked:           return "_thread_blocked";
  case _thread_blocked_trans:     return "_thread_blocked_trans";
  default:                        return "unknown thread state";
  }
}

#ifndef PRODUCT
void JavaThread::print_thread_state_on(outputStream *st) const {
  st->print_cr("   JavaThread state: %s", _get_thread_state_name(_thread_state));
};
void JavaThread::print_thread_state() const {
  print_thread_state_on(tty);
}
#endif // PRODUCT

// Called by Threads::print() for VM_PrintThreads operation
void JavaThread::print_on(outputStream *st, bool print_extended_info) const {
  st->print_raw("\"");
  st->print_raw(get_thread_name());
  st->print_raw("\" ");
  oop thread_oop = threadObj();
  if (thread_oop != NULL) {
    st->print("#" INT64_FORMAT " ", (int64_t)java_lang_Thread::thread_id(thread_oop));
    if (java_lang_Thread::is_daemon(thread_oop))  st->print("daemon ");
    st->print("prio=%d ", java_lang_Thread::priority(thread_oop));
  }
  Thread::print_on(st, print_extended_info);
  // print guess for valid stack memory region (assume 4K pages); helps lock debugging
  st->print_cr("[" INTPTR_FORMAT "]", (intptr_t)last_Java_sp() & ~right_n_bits(12));
  if (thread_oop != NULL) {
    st->print_cr("   java.lang.Thread.State: %s", java_lang_Thread::thread_status_name(thread_oop));
  }
#ifndef PRODUCT
  _safepoint_state->print_on(st);
#endif // PRODUCT
  if (is_Compiler_thread()) {
    CompileTask *task = ((CompilerThread*)this)->task();
    if (task != NULL) {
      st->print("   Compiling: ");
      task->print(st, NULL, true, false);
    } else {
      st->print("   No compile task");
    }
    st->cr();
  }
}

void JavaThread::print_name_on_error(outputStream* st, char *buf, int buflen) const {
  st->print("%s", get_thread_name_string(buf, buflen));
}

// Called by fatal error handler. The difference between this and
// JavaThread::print() is that we can't grab lock or allocate memory.
void JavaThread::print_on_error(outputStream* st, char *buf, int buflen) const {
  st->print("JavaThread \"%s\"", get_thread_name_string(buf, buflen));
  oop thread_obj = threadObj();
  if (thread_obj != NULL) {
    if (java_lang_Thread::is_daemon(thread_obj)) st->print(" daemon");
  }
  st->print(" [");
  st->print("%s", _get_thread_state_name(_thread_state));
  if (osthread()) {
    st->print(", id=%d", osthread()->thread_id());
  }
  st->print(", stack(" PTR_FORMAT "," PTR_FORMAT ")",
            p2i(stack_end()), p2i(stack_base()));
  st->print("]");

  ThreadsSMRSupport::print_info_on(this, st);
  return;
}

// Verification

static void frame_verify(frame* f, const RegisterMap *map) { f->verify(map); }

void JavaThread::verify() {
  // Verify oops in the thread.
  oops_do(&VerifyOopClosure::verify_oop, NULL);

  // Verify the stack frames.
  frames_do(frame_verify);
}

// CR 6300358 (sub-CR 2137150)
// Most callers of this method assume that it can't return NULL but a
// thread may not have a name whilst it is in the process of attaching to
// the VM - see CR 6412693, and there are places where a JavaThread can be
// seen prior to having it's threadObj set (eg JNI attaching threads and
// if vm exit occurs during initialization). These cases can all be accounted
// for such that this method never returns NULL.
const char* JavaThread::get_thread_name() const {
#ifdef ASSERT
  // early safepoints can hit while current thread does not yet have TLS
  if (!SafepointSynchronize::is_at_safepoint()) {
    Thread *cur = Thread::current();
    if (!(cur->is_Java_thread() && cur == this)) {
      // Current JavaThreads are allowed to get their own name without
      // the Threads_lock.
      assert_locked_or_safepoint(Threads_lock);
    }
  }
#endif // ASSERT
  return get_thread_name_string();
}

// Returns a non-NULL representation of this thread's name, or a suitable
// descriptive string if there is no set name
const char* JavaThread::get_thread_name_string(char* buf, int buflen) const {
  const char* name_str;
  oop thread_obj = threadObj();
  if (thread_obj != NULL) {
    oop name = java_lang_Thread::name(thread_obj);
    if (name != NULL) {
      if (buf == NULL) {
        name_str = java_lang_String::as_utf8_string(name);
      } else {
        name_str = java_lang_String::as_utf8_string(name, buf, buflen);
      }
    } else if (is_attaching_via_jni()) { // workaround for 6412693 - see 6404306
      name_str = "<no-name - thread is attaching>";
    } else {
      name_str = Thread::name();
    }
  } else {
    name_str = Thread::name();
  }
  assert(name_str != NULL, "unexpected NULL thread name");
  return name_str;
}


const char* JavaThread::get_threadgroup_name() const {
  debug_only(if (JavaThread::current() != this) assert_locked_or_safepoint(Threads_lock);)
  oop thread_obj = threadObj();
  if (thread_obj != NULL) {
    oop thread_group = java_lang_Thread::threadGroup(thread_obj);
    if (thread_group != NULL) {
      // ThreadGroup.name can be null
      return java_lang_ThreadGroup::name(thread_group);
    }
  }
  return NULL;
}

const char* JavaThread::get_parent_name() const {
  debug_only(if (JavaThread::current() != this) assert_locked_or_safepoint(Threads_lock);)
  oop thread_obj = threadObj();
  if (thread_obj != NULL) {
    oop thread_group = java_lang_Thread::threadGroup(thread_obj);
    if (thread_group != NULL) {
      oop parent = java_lang_ThreadGroup::parent(thread_group);
      if (parent != NULL) {
        // ThreadGroup.name can be null
        return java_lang_ThreadGroup::name(parent);
      }
    }
  }
  return NULL;
}

ThreadPriority JavaThread::java_priority() const {
  oop thr_oop = threadObj();
  if (thr_oop == NULL) return NormPriority; // Bootstrapping
  ThreadPriority priority = java_lang_Thread::priority(thr_oop);
  assert(MinPriority <= priority && priority <= MaxPriority, "sanity check");
  return priority;
}

void JavaThread::prepare(jobject jni_thread, ThreadPriority prio) {

  assert(Threads_lock->owner() == Thread::current(), "must have threads lock");
  // Link Java Thread object <-> C++ Thread

  // Get the C++ thread object (an oop) from the JNI handle (a jthread)
  // and put it into a new Handle.  The Handle "thread_oop" can then
  // be used to pass the C++ thread object to other methods.

  // Set the Java level thread object (jthread) field of the
  // new thread (a JavaThread *) to C++ thread object using the
  // "thread_oop" handle.

  // Set the thread field (a JavaThread *) of the
  // oop representing the java_lang_Thread to the new thread (a JavaThread *).

  Handle thread_oop(Thread::current(),
                    JNIHandles::resolve_non_null(jni_thread));
  assert(InstanceKlass::cast(thread_oop->klass())->is_linked(),
         "must be initialized");
  set_threadObj(thread_oop());
  java_lang_Thread::set_thread(thread_oop(), this);

  if (prio == NoPriority) {
    prio = java_lang_Thread::priority(thread_oop());
    assert(prio != NoPriority, "A valid priority should be present");
  }

  // Push the Java priority down to the native thread; needs Threads_lock
  Thread::set_priority(this, prio);

  // Add the new thread to the Threads list and set it in motion.
  // We must have threads lock in order to call Threads::add.
  // It is crucial that we do not block before the thread is
  // added to the Threads list for if a GC happens, then the java_thread oop
  // will not be visited by GC.
  Threads::add(this);
}

oop JavaThread::current_park_blocker() {
  // Support for JSR-166 locks
  oop thread_oop = threadObj();
  if (thread_oop != NULL &&
      JDK_Version::current().supports_thread_park_blocker()) {
    return java_lang_Thread::park_blocker(thread_oop);
  }
  return NULL;
}


void JavaThread::print_stack_on(outputStream* st) {
  if (!has_last_Java_frame()) return;
  ResourceMark rm;
  HandleMark   hm;

  RegisterMap reg_map(this);
  vframe* start_vf = last_java_vframe(&reg_map);
  int count = 0;
  for (vframe* f = start_vf; f != NULL; f = f->sender()) {
    if (f->is_java_frame()) {
      javaVFrame* jvf = javaVFrame::cast(f);
      java_lang_Throwable::print_stack_element(st, jvf->method(), jvf->bci());

      // Print out lock information
      if (JavaMonitorsInStackTrace) {
        jvf->print_lock_info_on(st, count);
      }
    } else {
      // Ignore non-Java frames
    }

    // Bail-out case for too deep stacks if MaxJavaStackTraceDepth > 0
    count++;
    if (MaxJavaStackTraceDepth > 0 && MaxJavaStackTraceDepth == count) return;
  }
}


// JVMTI PopFrame support
void JavaThread::popframe_preserve_args(ByteSize size_in_bytes, void* start) {
  assert(_popframe_preserved_args == NULL, "should not wipe out old PopFrame preserved arguments");
  if (in_bytes(size_in_bytes) != 0) {
    _popframe_preserved_args = NEW_C_HEAP_ARRAY(char, in_bytes(size_in_bytes), mtThread);
    _popframe_preserved_args_size = in_bytes(size_in_bytes);
    Copy::conjoint_jbytes(start, _popframe_preserved_args, _popframe_preserved_args_size);
  }
}

void* JavaThread::popframe_preserved_args() {
  return _popframe_preserved_args;
}

ByteSize JavaThread::popframe_preserved_args_size() {
  return in_ByteSize(_popframe_preserved_args_size);
}

WordSize JavaThread::popframe_preserved_args_size_in_words() {
  int sz = in_bytes(popframe_preserved_args_size());
  assert(sz % wordSize == 0, "argument size must be multiple of wordSize");
  return in_WordSize(sz / wordSize);
}

void JavaThread::popframe_free_preserved_args() {
  assert(_popframe_preserved_args != NULL, "should not free PopFrame preserved arguments twice");
  FREE_C_HEAP_ARRAY(char, (char*) _popframe_preserved_args);
  _popframe_preserved_args = NULL;
  _popframe_preserved_args_size = 0;
}

#ifndef PRODUCT

void JavaThread::trace_frames() {
  tty->print_cr("[Describe stack]");
  int frame_no = 1;
  for (StackFrameStream fst(this); !fst.is_done(); fst.next()) {
    tty->print("  %d. ", frame_no++);
    fst.current()->print_value_on(tty, this);
    tty->cr();
  }
}

class PrintAndVerifyOopClosure: public OopClosure {
 protected:
  template <class T> inline void do_oop_work(T* p) {
    oop obj = RawAccess<>::oop_load(p);
    if (obj == NULL) return;
    tty->print(INTPTR_FORMAT ": ", p2i(p));
    if (oopDesc::is_oop_or_null(obj)) {
      if (obj->is_objArray()) {
        tty->print_cr("valid objArray: " INTPTR_FORMAT, p2i(obj));
      } else {
        obj->print();
      }
    } else {
      tty->print_cr("invalid oop: " INTPTR_FORMAT, p2i(obj));
    }
    tty->cr();
  }
 public:
  virtual void do_oop(oop* p) { do_oop_work(p); }
  virtual void do_oop(narrowOop* p)  { do_oop_work(p); }
};


static void oops_print(frame* f, const RegisterMap *map) {
  PrintAndVerifyOopClosure print;
  f->print_value();
  f->oops_do(&print, NULL, (RegisterMap*)map);
}

// Print our all the locations that contain oops and whether they are
// valid or not.  This useful when trying to find the oldest frame
// where an oop has gone bad since the frame walk is from youngest to
// oldest.
void JavaThread::trace_oops() {
  tty->print_cr("[Trace oops]");
  frames_do(oops_print);
}


#ifdef ASSERT
// Print or validate the layout of stack frames
void JavaThread::print_frame_layout(int depth, bool validate_only) {
  ResourceMark rm;
  PRESERVE_EXCEPTION_MARK;
  FrameValues values;
  int frame_no = 0;
  for (StackFrameStream fst(this, false); !fst.is_done(); fst.next()) {
    fst.current()->describe(values, ++frame_no);
    if (depth == frame_no) break;
  }
  if (validate_only) {
    values.validate();
  } else {
    tty->print_cr("[Describe stack layout]");
    values.print(this);
  }
}
#endif

void JavaThread::trace_stack_from(vframe* start_vf) {
  ResourceMark rm;
  int vframe_no = 1;
  for (vframe* f = start_vf; f; f = f->sender()) {
    if (f->is_java_frame()) {
      javaVFrame::cast(f)->print_activation(vframe_no++);
    } else {
      f->print();
    }
    if (vframe_no > StackPrintLimit) {
      tty->print_cr("...<more frames>...");
      return;
    }
  }
}


void JavaThread::trace_stack() {
  if (!has_last_Java_frame()) return;
  ResourceMark rm;
  HandleMark   hm;
  RegisterMap reg_map(this);
  trace_stack_from(last_java_vframe(&reg_map));
}


#endif // PRODUCT


javaVFrame* JavaThread::last_java_vframe(RegisterMap *reg_map) {
  assert(reg_map != NULL, "a map must be given");
  frame f = last_frame();
  for (vframe* vf = vframe::new_vframe(&f, reg_map, this); vf; vf = vf->sender()) {
    if (vf->is_java_frame()) return javaVFrame::cast(vf);
  }
  return NULL;
}


Klass* JavaThread::security_get_caller_class(int depth) {
  vframeStream vfst(this);
  vfst.security_get_caller_frame(depth);
  if (!vfst.at_end()) {
    return vfst.method()->method_holder();
  }
  return NULL;
}

static void compiler_thread_entry(JavaThread* thread, TRAPS) {
  assert(thread->is_Compiler_thread(), "must be compiler thread");
  CompileBroker::compiler_thread_loop();
}

static void sweeper_thread_entry(JavaThread* thread, TRAPS) {
  NMethodSweeper::sweeper_loop();
}

// Create a CompilerThread
CompilerThread::CompilerThread(CompileQueue* queue,
                               CompilerCounters* counters)
                               : JavaThread(&compiler_thread_entry) {
  _env   = NULL;
  _log   = NULL;
  _task  = NULL;
  _queue = queue;
  _counters = counters;
  _buffer_blob = NULL;
  _compiler = NULL;

  // Compiler uses resource area for compilation, let's bias it to mtCompiler
  resource_area()->bias_to(mtCompiler);

#ifndef PRODUCT
  _ideal_graph_printer = NULL;
#endif
}

CompilerThread::~CompilerThread() {
  // Delete objects which were allocated on heap.
  delete _counters;
}

bool CompilerThread::can_call_java() const {
  return _compiler != NULL && _compiler->is_jvmci();
}

// Create sweeper thread
CodeCacheSweeperThread::CodeCacheSweeperThread()
: JavaThread(&sweeper_thread_entry) {
  _scanned_compiled_method = NULL;
}

void CodeCacheSweeperThread::oops_do(OopClosure* f, CodeBlobClosure* cf) {
  JavaThread::oops_do(f, cf);
  if (_scanned_compiled_method != NULL && cf != NULL) {
    // Safepoints can occur when the sweeper is scanning an nmethod so
    // process it here to make sure it isn't unloaded in the middle of
    // a scan.
    cf->do_code_blob(_scanned_compiled_method);
  }
}

void CodeCacheSweeperThread::nmethods_do(CodeBlobClosure* cf) {
  JavaThread::nmethods_do(cf);
  if (_scanned_compiled_method != NULL && cf != NULL) {
    // Safepoints can occur when the sweeper is scanning an nmethod so
    // process it here to make sure it isn't unloaded in the middle of
    // a scan.
    cf->do_code_blob(_scanned_compiled_method);
  }
}


// ======= Threads ========

// The Threads class links together all active threads, and provides
// operations over all threads. It is protected by the Threads_lock,
// which is also used in other global contexts like safepointing.
// ThreadsListHandles are used to safely perform operations on one
// or more threads without the risk of the thread exiting during the
// operation.
//
// Note: The Threads_lock is currently more widely used than we
// would like. We are actively migrating Threads_lock uses to other
// mechanisms in order to reduce Threads_lock contention.

JavaThread* Threads::_thread_list = NULL;
int         Threads::_number_of_threads = 0;
int         Threads::_number_of_non_daemon_threads = 0;
int         Threads::_return_code = 0;
uintx       Threads::_thread_claim_token = 1; // Never zero.
size_t      JavaThread::_stack_size_at_create = 0;

#ifdef ASSERT
bool        Threads::_vm_complete = false;
#endif

static inline void *prefetch_and_load_ptr(void **addr, intx prefetch_interval) {
  Prefetch::read((void*)addr, prefetch_interval);
  return *addr;
}

// Possibly the ugliest for loop the world has seen. C++ does not allow
// multiple types in the declaration section of the for loop. In this case
// we are only dealing with pointers and hence can cast them. It looks ugly
// but macros are ugly and therefore it's fine to make things absurdly ugly.
#define DO_JAVA_THREADS(LIST, X)                                                                                          \
    for (JavaThread *MACRO_scan_interval = (JavaThread*)(uintptr_t)PrefetchScanIntervalInBytes,                           \
             *MACRO_list = (JavaThread*)(LIST),                                                                           \
             **MACRO_end = ((JavaThread**)((ThreadsList*)MACRO_list)->threads()) + ((ThreadsList*)MACRO_list)->length(),  \
             **MACRO_current_p = (JavaThread**)((ThreadsList*)MACRO_list)->threads(),                                     \
             *X = (JavaThread*)prefetch_and_load_ptr((void**)MACRO_current_p, (intx)MACRO_scan_interval);                 \
         MACRO_current_p != MACRO_end;                                                                                    \
         MACRO_current_p++,                                                                                               \
             X = (JavaThread*)prefetch_and_load_ptr((void**)MACRO_current_p, (intx)MACRO_scan_interval))

// All JavaThreads
#define ALL_JAVA_THREADS(X) DO_JAVA_THREADS(ThreadsSMRSupport::get_java_thread_list(), X)

// All NonJavaThreads (i.e., every non-JavaThread in the system).
void Threads::non_java_threads_do(ThreadClosure* tc) {
  NoSafepointVerifier nsv(!SafepointSynchronize::is_at_safepoint(), false);
  for (NonJavaThread::Iterator njti; !njti.end(); njti.step()) {
    tc->do_thread(njti.current());
  }
}

// All JavaThreads
void Threads::java_threads_do(ThreadClosure* tc) {
  assert_locked_or_safepoint(Threads_lock);
  // ALL_JAVA_THREADS iterates through all JavaThreads.
  ALL_JAVA_THREADS(p) {
    tc->do_thread(p);
  }
}

void Threads::java_threads_and_vm_thread_do(ThreadClosure* tc) {
  assert_locked_or_safepoint(Threads_lock);
  java_threads_do(tc);
  tc->do_thread(VMThread::vm_thread());
}

// All JavaThreads + all non-JavaThreads (i.e., every thread in the system).
void Threads::threads_do(ThreadClosure* tc) {
  assert_locked_or_safepoint(Threads_lock);
  java_threads_do(tc);
  non_java_threads_do(tc);
}

void Threads::possibly_parallel_threads_do(bool is_par, ThreadClosure* tc) {
  uintx claim_token = Threads::thread_claim_token();
  ALL_JAVA_THREADS(p) {
    if (p->claim_threads_do(is_par, claim_token)) {
      tc->do_thread(p);
    }
  }
  VMThread* vmt = VMThread::vm_thread();
  if (vmt->claim_threads_do(is_par, claim_token)) {
    tc->do_thread(vmt);
  }
}

// The system initialization in the library has three phases.
//
// Phase 1: java.lang.System class initialization
//     java.lang.System is a primordial class loaded and initialized
//     by the VM early during startup.  java.lang.System.<clinit>
//     only does registerNatives and keeps the rest of the class
//     initialization work later until thread initialization completes.
//
//     System.initPhase1 initializes the system properties, the static
//     fields in, out, and err. Set up java signal handlers, OS-specific
//     system settings, and thread group of the main thread.
static void call_initPhase1(TRAPS) {
  Klass* klass =  SystemDictionary::resolve_or_fail(vmSymbols::java_lang_System(), true, CHECK);
  JavaValue result(T_VOID);
  JavaCalls::call_static(&result, klass, vmSymbols::initPhase1_name(),
                                         vmSymbols::void_method_signature(), CHECK);
}

// Phase 2. Module system initialization
//     This will initialize the module system.  Only java.base classes
//     can be loaded until phase 2 completes.
//
//     Call System.initPhase2 after the compiler initialization and jsr292
//     classes get initialized because module initialization runs a lot of java
//     code, that for performance reasons, should be compiled.  Also, this will
//     enable the startup code to use lambda and other language features in this
//     phase and onward.
//
//     After phase 2, The VM will begin search classes from -Xbootclasspath/a.
static void call_initPhase2(TRAPS) {
  TraceTime timer("Initialize module system", TRACETIME_LOG(Info, startuptime));

  Klass* klass = SystemDictionary::resolve_or_fail(vmSymbols::java_lang_System(), true, CHECK);

  JavaValue result(T_INT);
  JavaCallArguments args;
  args.push_int(DisplayVMOutputToStderr);
  args.push_int(log_is_enabled(Debug, init)); // print stack trace if exception thrown
  JavaCalls::call_static(&result, klass, vmSymbols::initPhase2_name(),
                                         vmSymbols::boolean_boolean_int_signature(), &args, CHECK);
  if (result.get_jint() != JNI_OK) {
    vm_exit_during_initialization(); // no message or exception
  }

  universe_post_module_init();
}

// Phase 3. final setup - set security manager, system class loader and TCCL
//
//     This will instantiate and set the security manager, set the system class
//     loader as well as the thread context class loader.  The security manager
//     and system class loader may be a custom class loaded from -Xbootclasspath/a,
//     other modules or the application's classpath.
static void call_initPhase3(TRAPS) {
  Klass* klass = SystemDictionary::resolve_or_fail(vmSymbols::java_lang_System(), true, CHECK);
  JavaValue result(T_VOID);
  JavaCalls::call_static(&result, klass, vmSymbols::initPhase3_name(),
                                         vmSymbols::void_method_signature(), CHECK);
}

void Threads::initialize_java_lang_classes(JavaThread* main_thread, TRAPS) {
  TraceTime timer("Initialize java.lang classes", TRACETIME_LOG(Info, startuptime));

  if (EagerXrunInit && Arguments::init_libraries_at_startup()) {
    create_vm_init_libraries();
  }

  initialize_class(vmSymbols::java_lang_String(), CHECK);

  // Inject CompactStrings value after the static initializers for String ran.
  java_lang_String::set_compact_strings(CompactStrings);

  // Initialize java_lang.System (needed before creating the thread)
  initialize_class(vmSymbols::java_lang_System(), CHECK);
  // The VM creates & returns objects of this class. Make sure it's initialized.
  initialize_class(vmSymbols::java_lang_Class(), CHECK);
  initialize_class(vmSymbols::java_lang_ThreadGroup(), CHECK);
  Handle thread_group = create_initial_thread_group(CHECK);
  Universe::set_main_thread_group(thread_group());
  initialize_class(vmSymbols::java_lang_Thread(), CHECK);
  oop thread_object = create_initial_thread(thread_group, main_thread, CHECK);
  main_thread->set_threadObj(thread_object);

  // Set thread status to running since main thread has
  // been started and running.
  java_lang_Thread::set_thread_status(thread_object,
                                      java_lang_Thread::RUNNABLE);

  // The VM creates objects of this class.
  initialize_class(vmSymbols::java_lang_Module(), CHECK);

#ifdef ASSERT
  InstanceKlass *k = SystemDictionary::UnsafeConstants_klass();
  assert(k->is_not_initialized(), "UnsafeConstants should not already be initialized");
#endif

  // initialize the hardware-specific constants needed by Unsafe
  initialize_class(vmSymbols::jdk_internal_misc_UnsafeConstants(), CHECK);
  jdk_internal_misc_UnsafeConstants::set_unsafe_constants();

  // The VM preresolves methods to these classes. Make sure that they get initialized
  initialize_class(vmSymbols::java_lang_reflect_Method(), CHECK);
  initialize_class(vmSymbols::java_lang_ref_Finalizer(), CHECK);

  // Phase 1 of the system initialization in the library, java.lang.System class initialization
  call_initPhase1(CHECK);

  // get the Java runtime name after java.lang.System is initialized
  JDK_Version::set_runtime_name(get_java_runtime_name(THREAD));
  JDK_Version::set_runtime_version(get_java_runtime_version(THREAD));

  // an instance of OutOfMemory exception has been allocated earlier
  initialize_class(vmSymbols::java_lang_OutOfMemoryError(), CHECK);
  initialize_class(vmSymbols::java_lang_NullPointerException(), CHECK);
  initialize_class(vmSymbols::java_lang_ClassCastException(), CHECK);
  initialize_class(vmSymbols::java_lang_ArrayStoreException(), CHECK);
  initialize_class(vmSymbols::java_lang_ArithmeticException(), CHECK);
  initialize_class(vmSymbols::java_lang_StackOverflowError(), CHECK);
  initialize_class(vmSymbols::java_lang_IllegalMonitorStateException(), CHECK);
  initialize_class(vmSymbols::java_lang_IllegalArgumentException(), CHECK);
}

void Threads::initialize_jsr292_core_classes(TRAPS) {
  TraceTime timer("Initialize java.lang.invoke classes", TRACETIME_LOG(Info, startuptime));

  initialize_class(vmSymbols::java_lang_invoke_MethodHandle(), CHECK);
  initialize_class(vmSymbols::java_lang_invoke_ResolvedMethodName(), CHECK);
  initialize_class(vmSymbols::java_lang_invoke_MemberName(), CHECK);
  initialize_class(vmSymbols::java_lang_invoke_MethodHandleNatives(), CHECK);
}

jint Threads::create_vm(JavaVMInitArgs* args, bool* canTryAgain) {
  extern void JDK_Version_init();

  // Preinitialize version info.
  VM_Version::early_initialize();

  // Check version
  if (!is_supported_jni_version(args->version)) return JNI_EVERSION;

  // Initialize library-based TLS
  ThreadLocalStorage::init();

  // Initialize the output stream module
  ostream_init();

  // Process java launcher properties.
  Arguments::process_sun_java_launcher_properties(args);

  // Initialize the os module
  os::init();

  // Record VM creation timing statistics
  TraceVmCreationTime create_vm_timer;
  create_vm_timer.start();

  // Initialize system properties.
  Arguments::init_system_properties();

  // So that JDK version can be used as a discriminator when parsing arguments
  JDK_Version_init();

  // Update/Initialize System properties after JDK version number is known
  Arguments::init_version_specific_system_properties();

  // Make sure to initialize log configuration *before* parsing arguments
  LogConfiguration::initialize(create_vm_timer.begin_time());

  // Parse arguments
  // Note: this internally calls os::init_container_support()
  jint parse_result = Arguments::parse(args);
  if (parse_result != JNI_OK) return parse_result;

  os::init_before_ergo();

  jint ergo_result = Arguments::apply_ergo();
  if (ergo_result != JNI_OK) return ergo_result;

  // Final check of all ranges after ergonomics which may change values.
  if (!JVMFlagRangeList::check_ranges()) {
    return JNI_EINVAL;
  }

  // Final check of all 'AfterErgo' constraints after ergonomics which may change values.
  bool constraint_result = JVMFlagConstraintList::check_constraints(JVMFlagConstraint::AfterErgo);
  if (!constraint_result) {
    return JNI_EINVAL;
  }

  JVMFlagWriteableList::mark_startup();

  if (PauseAtStartup) {
    os::pause();
  }

  HOTSPOT_VM_INIT_BEGIN();

  // Timing (must come after argument parsing)
  TraceTime timer("Create VM", TRACETIME_LOG(Info, startuptime));

  // Initialize the os module after parsing the args
  jint os_init_2_result = os::init_2();
  if (os_init_2_result != JNI_OK) return os_init_2_result;

#ifdef CAN_SHOW_REGISTERS_ON_ASSERT
  // Initialize assert poison page mechanism.
  if (ShowRegistersOnAssert) {
    initialize_assert_poison();
  }
#endif // CAN_SHOW_REGISTERS_ON_ASSERT

  SafepointMechanism::initialize();

  jint adjust_after_os_result = Arguments::adjust_after_os();
  if (adjust_after_os_result != JNI_OK) return adjust_after_os_result;

  // Initialize output stream logging
  ostream_init_log();

  // Convert -Xrun to -agentlib: if there is no JVM_OnLoad
  // Must be before create_vm_init_agents()
  if (Arguments::init_libraries_at_startup()) {
    convert_vm_init_libraries_to_agents();
  }

  // Launch -agentlib/-agentpath and converted -Xrun agents
  if (Arguments::init_agents_at_startup()) {
    create_vm_init_agents();
  }

  // Initialize Threads state
  _thread_list = NULL;
  _number_of_threads = 0;
  _number_of_non_daemon_threads = 0;

  // Initialize global data structures and create system classes in heap
  vm_init_globals();

#if INCLUDE_JVMCI
  if (JVMCICounterSize > 0) {
    JavaThread::_jvmci_old_thread_counters = NEW_C_HEAP_ARRAY(jlong, JVMCICounterSize, mtInternal);
    memset(JavaThread::_jvmci_old_thread_counters, 0, sizeof(jlong) * JVMCICounterSize);
  } else {
    JavaThread::_jvmci_old_thread_counters = NULL;
  }
#endif // INCLUDE_JVMCI

  // Attach the main thread to this os thread
  JavaThread* main_thread = new JavaThread();
  main_thread->set_thread_state(_thread_in_vm);
  main_thread->initialize_thread_current();
  // must do this before set_active_handles
  main_thread->record_stack_base_and_size();
  main_thread->register_thread_stack_with_NMT();
  main_thread->set_active_handles(JNIHandleBlock::allocate_block());

  if (!main_thread->set_as_starting_thread()) {
    vm_shutdown_during_initialization(
                                      "Failed necessary internal allocation. Out of swap space");
    main_thread->smr_delete();
    *canTryAgain = false; // don't let caller call JNI_CreateJavaVM again
    return JNI_ENOMEM;
  }

  // Enable guard page *after* os::create_main_thread(), otherwise it would
  // crash Linux VM, see notes in os_linux.cpp.
  main_thread->create_stack_guard_pages();

  // Initialize Java-Level synchronization subsystem
  ObjectMonitor::Initialize();

  // Initialize global modules
  jint status = init_globals();
  if (status != JNI_OK) {
    main_thread->smr_delete();
    *canTryAgain = false; // don't let caller call JNI_CreateJavaVM again
    return status;
  }

  JFR_ONLY(Jfr::on_vm_init();)

  // Should be done after the heap is fully created
  main_thread->cache_global_variables();

  HandleMark hm;

  { MutexLocker mu(Threads_lock);
    Threads::add(main_thread);
  }

  // Any JVMTI raw monitors entered in onload will transition into
  // real raw monitor. VM is setup enough here for raw monitor enter.
  JvmtiExport::transition_pending_onload_raw_monitors();

  // Create the VMThread
  { TraceTime timer("Start VMThread", TRACETIME_LOG(Info, startuptime));

  VMThread::create();
    Thread* vmthread = VMThread::vm_thread();

    if (!os::create_thread(vmthread, os::vm_thread)) {
      vm_exit_during_initialization("Cannot create VM thread. "
                                    "Out of system resources.");
    }

    // Wait for the VM thread to become ready, and VMThread::run to initialize
    // Monitors can have spurious returns, must always check another state flag
    {
      MutexLocker ml(Notify_lock);
      os::start_thread(vmthread);
      while (vmthread->active_handles() == NULL) {
        Notify_lock->wait();
      }
    }
  }

  assert(Universe::is_fully_initialized(), "not initialized");
  if (VerifyDuringStartup) {
    // Make sure we're starting with a clean slate.
    VM_Verify verify_op;
    VMThread::execute(&verify_op);
  }

  // We need this to update the java.vm.info property in case any flags used
  // to initially define it have been changed. This is needed for both CDS and
  // AOT, since UseSharedSpaces and UseAOT may be changed after java.vm.info
  // is initially computed. See Abstract_VM_Version::vm_info_string().
  // This update must happen before we initialize the java classes, but
  // after any initialization logic that might modify the flags.
  Arguments::update_vm_info_property(VM_Version::vm_info_string());

  Thread* THREAD = Thread::current();

  // Always call even when there are not JVMTI environments yet, since environments
  // may be attached late and JVMTI must track phases of VM execution
  JvmtiExport::enter_early_start_phase();

  // Notify JVMTI agents that VM has started (JNI is up) - nop if no agents.
  JvmtiExport::post_early_vm_start();

  initialize_java_lang_classes(main_thread, CHECK_JNI_ERR);

  quicken_jni_functions();

  // No more stub generation allowed after that point.
  StubCodeDesc::freeze();

  // Set flag that basic initialization has completed. Used by exceptions and various
  // debug stuff, that does not work until all basic classes have been initialized.
  set_init_completed();

  LogConfiguration::post_initialize();
  Metaspace::post_initialize();

  HOTSPOT_VM_INIT_END();

  // record VM initialization completion time
#if INCLUDE_MANAGEMENT
  Management::record_vm_init_completed();
#endif // INCLUDE_MANAGEMENT

  // Signal Dispatcher needs to be started before VMInit event is posted
  os::initialize_jdk_signal_support(CHECK_JNI_ERR);

  // Start Attach Listener if +StartAttachListener or it can't be started lazily
  if (!DisableAttachMechanism) {
    AttachListener::vm_start();
    if (StartAttachListener || AttachListener::init_at_startup()) {
      AttachListener::init();
    }
  }

  // Launch -Xrun agents
  // Must be done in the JVMTI live phase so that for backward compatibility the JDWP
  // back-end can launch with -Xdebug -Xrunjdwp.
  if (!EagerXrunInit && Arguments::init_libraries_at_startup()) {
    create_vm_init_libraries();
  }

  if (CleanChunkPoolAsync) {
    Chunk::start_chunk_pool_cleaner_task();
  }

  // initialize compiler(s)
#if defined(COMPILER1) || COMPILER2_OR_JVMCI
#if INCLUDE_JVMCI
  bool force_JVMCI_intialization = false;
  if (EnableJVMCI) {
    // Initialize JVMCI eagerly when it is explicitly requested.
    // Or when JVMCIPrintProperties is enabled.
    // The JVMCI Java initialization code will read this flag and
    // do the printing if it's set.
    force_JVMCI_intialization = EagerJVMCI || JVMCIPrintProperties;

    if (!force_JVMCI_intialization) {
      // 8145270: Force initialization of JVMCI runtime otherwise requests for blocking
      // compilations via JVMCI will not actually block until JVMCI is initialized.
      force_JVMCI_intialization = UseJVMCICompiler && (!UseInterpreter || !BackgroundCompilation);
    }
  }
#endif
  CompileBroker::compilation_init_phase1(CHECK_JNI_ERR);
  // Postpone completion of compiler initialization to after JVMCI
  // is initialized to avoid timeouts of blocking compilations.
  if (JVMCI_ONLY(!force_JVMCI_intialization) NOT_JVMCI(true)) {
    CompileBroker::compilation_init_phase2();
  }
#endif

  // Pre-initialize some JSR292 core classes to avoid deadlock during class loading.
  // It is done after compilers are initialized, because otherwise compilations of
  // signature polymorphic MH intrinsics can be missed
  // (see SystemDictionary::find_method_handle_intrinsic).
  initialize_jsr292_core_classes(CHECK_JNI_ERR);

  // This will initialize the module system.  Only java.base classes can be
  // loaded until phase 2 completes
  call_initPhase2(CHECK_JNI_ERR);

  // Always call even when there are not JVMTI environments yet, since environments
  // may be attached late and JVMTI must track phases of VM execution
  JvmtiExport::enter_start_phase();

  // Notify JVMTI agents that VM has started (JNI is up) - nop if no agents.
  JvmtiExport::post_vm_start();

  // Final system initialization including security manager and system class loader
  call_initPhase3(CHECK_JNI_ERR);

  // cache the system and platform class loaders
  SystemDictionary::compute_java_loaders(CHECK_JNI_ERR);

#if INCLUDE_CDS
  if (DumpSharedSpaces) {
    // capture the module path info from the ModuleEntryTable
    ClassLoader::initialize_module_path(THREAD);
  }
#endif

#if INCLUDE_JVMCI
  if (force_JVMCI_intialization) {
    JVMCIRuntime::force_initialization(CHECK_JNI_ERR);
    CompileBroker::compilation_init_phase2();
  }
#endif

  // Always call even when there are not JVMTI environments yet, since environments
  // may be attached late and JVMTI must track phases of VM execution
  JvmtiExport::enter_live_phase();

  // Make perfmemory accessible
  PerfMemory::set_accessible(true);

  // Notify JVMTI agents that VM initialization is complete - nop if no agents.
  JvmtiExport::post_vm_initialized();

  JFR_ONLY(Jfr::on_vm_start();)

#if INCLUDE_MANAGEMENT
  Management::initialize(THREAD);

  if (HAS_PENDING_EXCEPTION) {
    // management agent fails to start possibly due to
    // configuration problem and is responsible for printing
    // stack trace if appropriate. Simply exit VM.
    vm_exit(1);
  }
#endif // INCLUDE_MANAGEMENT

  if (MemProfiling)                   MemProfiler::engage();
  StatSampler::engage();
  if (CheckJNICalls)                  JniPeriodicChecker::engage();

  BiasedLocking::init();

#if INCLUDE_RTM_OPT
  RTMLockingCounters::init();
#endif

  if (JDK_Version::current().post_vm_init_hook_enabled()) {
    call_postVMInitHook(THREAD);
    // The Java side of PostVMInitHook.run must deal with all
    // exceptions and provide means of diagnosis.
    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
    }
  }

  {
    MutexLocker ml(PeriodicTask_lock);
    // Make sure the WatcherThread can be started by WatcherThread::start()
    // or by dynamic enrollment.
    WatcherThread::make_startable();
    // Start up the WatcherThread if there are any periodic tasks
    // NOTE:  All PeriodicTasks should be registered by now. If they
    //   aren't, late joiners might appear to start slowly (we might
    //   take a while to process their first tick).
    if (PeriodicTask::num_tasks() > 0) {
      WatcherThread::start();
    }
  }

  create_vm_timer.end();
#ifdef ASSERT
  _vm_complete = true;
#endif

  if (DumpSharedSpaces) {
    MetaspaceShared::preload_and_dump(CHECK_JNI_ERR);
    ShouldNotReachHere();
  }

  return JNI_OK;
}

// type for the Agent_OnLoad and JVM_OnLoad entry points
extern "C" {
  typedef jint (JNICALL *OnLoadEntry_t)(JavaVM *, char *, void *);
}
// Find a command line agent library and return its entry point for
//         -agentlib:  -agentpath:   -Xrun
// num_symbol_entries must be passed-in since only the caller knows the number of symbols in the array.
static OnLoadEntry_t lookup_on_load(AgentLibrary* agent,
                                    const char *on_load_symbols[],
                                    size_t num_symbol_entries) {
  OnLoadEntry_t on_load_entry = NULL;
  void *library = NULL;

  if (!agent->valid()) {
    char buffer[JVM_MAXPATHLEN];
    char ebuf[1024] = "";
    const char *name = agent->name();
    const char *msg = "Could not find agent library ";

    // First check to see if agent is statically linked into executable
    if (os::find_builtin_agent(agent, on_load_symbols, num_symbol_entries)) {
      library = agent->os_lib();
    } else if (agent->is_absolute_path()) {
      library = os::dll_load(name, ebuf, sizeof ebuf);
      if (library == NULL) {
        const char *sub_msg = " in absolute path, with error: ";
        size_t len = strlen(msg) + strlen(name) + strlen(sub_msg) + strlen(ebuf) + 1;
        char *buf = NEW_C_HEAP_ARRAY(char, len, mtThread);
        jio_snprintf(buf, len, "%s%s%s%s", msg, name, sub_msg, ebuf);
        // If we can't find the agent, exit.
        vm_exit_during_initialization(buf, NULL);
        FREE_C_HEAP_ARRAY(char, buf);
      }
    } else {
      // Try to load the agent from the standard dll directory
      if (os::dll_locate_lib(buffer, sizeof(buffer), Arguments::get_dll_dir(),
                             name)) {
        library = os::dll_load(buffer, ebuf, sizeof ebuf);
      }
      if (library == NULL) { // Try the library path directory.
        if (os::dll_build_name(buffer, sizeof(buffer), name)) {
          library = os::dll_load(buffer, ebuf, sizeof ebuf);
        }
        if (library == NULL) {
          const char *sub_msg = " on the library path, with error: ";
          const char *sub_msg2 = "\nModule java.instrument may be missing from runtime image.";

          size_t len = strlen(msg) + strlen(name) + strlen(sub_msg) +
                       strlen(ebuf) + strlen(sub_msg2) + 1;
          char *buf = NEW_C_HEAP_ARRAY(char, len, mtThread);
          if (!agent->is_instrument_lib()) {
            jio_snprintf(buf, len, "%s%s%s%s", msg, name, sub_msg, ebuf);
          } else {
            jio_snprintf(buf, len, "%s%s%s%s%s", msg, name, sub_msg, ebuf, sub_msg2);
          }
          // If we can't find the agent, exit.
          vm_exit_during_initialization(buf, NULL);
          FREE_C_HEAP_ARRAY(char, buf);
        }
      }
    }
    agent->set_os_lib(library);
    agent->set_valid();
  }

  // Find the OnLoad function.
  on_load_entry =
    CAST_TO_FN_PTR(OnLoadEntry_t, os::find_agent_function(agent,
                                                          false,
                                                          on_load_symbols,
                                                          num_symbol_entries));
  return on_load_entry;
}

// Find the JVM_OnLoad entry point
static OnLoadEntry_t lookup_jvm_on_load(AgentLibrary* agent) {
  const char *on_load_symbols[] = JVM_ONLOAD_SYMBOLS;
  return lookup_on_load(agent, on_load_symbols, sizeof(on_load_symbols) / sizeof(char*));
}

// Find the Agent_OnLoad entry point
static OnLoadEntry_t lookup_agent_on_load(AgentLibrary* agent) {
  const char *on_load_symbols[] = AGENT_ONLOAD_SYMBOLS;
  return lookup_on_load(agent, on_load_symbols, sizeof(on_load_symbols) / sizeof(char*));
}

// For backwards compatibility with -Xrun
// Convert libraries with no JVM_OnLoad, but which have Agent_OnLoad to be
// treated like -agentpath:
// Must be called before agent libraries are created
void Threads::convert_vm_init_libraries_to_agents() {
  AgentLibrary* agent;
  AgentLibrary* next;

  for (agent = Arguments::libraries(); agent != NULL; agent = next) {
    next = agent->next();  // cache the next agent now as this agent may get moved off this list
    OnLoadEntry_t on_load_entry = lookup_jvm_on_load(agent);

    // If there is an JVM_OnLoad function it will get called later,
    // otherwise see if there is an Agent_OnLoad
    if (on_load_entry == NULL) {
      on_load_entry = lookup_agent_on_load(agent);
      if (on_load_entry != NULL) {
        // switch it to the agent list -- so that Agent_OnLoad will be called,
        // JVM_OnLoad won't be attempted and Agent_OnUnload will
        Arguments::convert_library_to_agent(agent);
      } else {
        vm_exit_during_initialization("Could not find JVM_OnLoad or Agent_OnLoad function in the library", agent->name());
      }
    }
  }
}

// Create agents for -agentlib:  -agentpath:  and converted -Xrun
// Invokes Agent_OnLoad
// Called very early -- before JavaThreads exist
void Threads::create_vm_init_agents() {
  extern struct JavaVM_ main_vm;
  AgentLibrary* agent;

  JvmtiExport::enter_onload_phase();

  for (agent = Arguments::agents(); agent != NULL; agent = agent->next()) {
    // CDS dumping does not support native JVMTI agent.
    // CDS dumping supports Java agent if the AllowArchivingWithJavaAgent diagnostic option is specified.
    if (DumpSharedSpaces) {
      if(!agent->is_instrument_lib()) {
        vm_exit_during_cds_dumping("CDS dumping does not support native JVMTI agent, name", agent->name());
      } else if (!AllowArchivingWithJavaAgent) {
        vm_exit_during_cds_dumping(
          "Must enable AllowArchivingWithJavaAgent in order to run Java agent during CDS dumping");
      }
    }

    OnLoadEntry_t  on_load_entry = lookup_agent_on_load(agent);

    if (on_load_entry != NULL) {
      // Invoke the Agent_OnLoad function
      jint err = (*on_load_entry)(&main_vm, agent->options(), NULL);
      if (err != JNI_OK) {
        vm_exit_during_initialization("agent library failed to init", agent->name());
      }
    } else {
      vm_exit_during_initialization("Could not find Agent_OnLoad function in the agent library", agent->name());
    }
  }

  JvmtiExport::enter_primordial_phase();
}

extern "C" {
  typedef void (JNICALL *Agent_OnUnload_t)(JavaVM *);
}

void Threads::shutdown_vm_agents() {
  // Send any Agent_OnUnload notifications
  const char *on_unload_symbols[] = AGENT_ONUNLOAD_SYMBOLS;
  size_t num_symbol_entries = ARRAY_SIZE(on_unload_symbols);
  extern struct JavaVM_ main_vm;
  for (AgentLibrary* agent = Arguments::agents(); agent != NULL; agent = agent->next()) {

    // Find the Agent_OnUnload function.
    Agent_OnUnload_t unload_entry = CAST_TO_FN_PTR(Agent_OnUnload_t,
                                                   os::find_agent_function(agent,
                                                   false,
                                                   on_unload_symbols,
                                                   num_symbol_entries));

    // Invoke the Agent_OnUnload function
    if (unload_entry != NULL) {
      JavaThread* thread = JavaThread::current();
      ThreadToNativeFromVM ttn(thread);
      HandleMark hm(thread);
      (*unload_entry)(&main_vm);
    }
  }
}

// Called for after the VM is initialized for -Xrun libraries which have not been converted to agent libraries
// Invokes JVM_OnLoad
void Threads::create_vm_init_libraries() {
  extern struct JavaVM_ main_vm;
  AgentLibrary* agent;

  for (agent = Arguments::libraries(); agent != NULL; agent = agent->next()) {
    OnLoadEntry_t on_load_entry = lookup_jvm_on_load(agent);

    if (on_load_entry != NULL) {
      // Invoke the JVM_OnLoad function
      JavaThread* thread = JavaThread::current();
      ThreadToNativeFromVM ttn(thread);
      HandleMark hm(thread);
      jint err = (*on_load_entry)(&main_vm, agent->options(), NULL);
      if (err != JNI_OK) {
        vm_exit_during_initialization("-Xrun library failed to init", agent->name());
      }
    } else {
      vm_exit_during_initialization("Could not find JVM_OnLoad function in -Xrun library", agent->name());
    }
  }
}


// Last thread running calls java.lang.Shutdown.shutdown()
void JavaThread::invoke_shutdown_hooks() {
  HandleMark hm(this);

  // We could get here with a pending exception, if so clear it now.
  if (this->has_pending_exception()) {
    this->clear_pending_exception();
  }

  EXCEPTION_MARK;
  Klass* shutdown_klass =
    SystemDictionary::resolve_or_null(vmSymbols::java_lang_Shutdown(),
                                      THREAD);
  if (shutdown_klass != NULL) {
    // SystemDictionary::resolve_or_null will return null if there was
    // an exception.  If we cannot load the Shutdown class, just don't
    // call Shutdown.shutdown() at all.  This will mean the shutdown hooks
    // won't be run.  Note that if a shutdown hook was registered,
    // the Shutdown class would have already been loaded
    // (Runtime.addShutdownHook will load it).
    JavaValue result(T_VOID);
    JavaCalls::call_static(&result,
                           shutdown_klass,
                           vmSymbols::shutdown_method_name(),
                           vmSymbols::void_method_signature(),
                           THREAD);
  }
  CLEAR_PENDING_EXCEPTION;
}

// Threads::destroy_vm() is normally called from jni_DestroyJavaVM() when
// the program falls off the end of main(). Another VM exit path is through
// vm_exit() when the program calls System.exit() to return a value or when
// there is a serious error in VM. The two shutdown paths are not exactly
// the same, but they share Shutdown.shutdown() at Java level and before_exit()
// and VM_Exit op at VM level.
//
// Shutdown sequence:
//   + Shutdown native memory tracking if it is on
//   + Wait until we are the last non-daemon thread to execute
//     <-- every thing is still working at this moment -->
//   + Call java.lang.Shutdown.shutdown(), which will invoke Java level
//        shutdown hooks
//   + Call before_exit(), prepare for VM exit
//      > run VM level shutdown hooks (they are registered through JVM_OnExit(),
//        currently the only user of this mechanism is File.deleteOnExit())
//      > stop StatSampler, watcher thread, CMS threads,
//        post thread end and vm death events to JVMTI,
//        stop signal thread
//   + Call JavaThread::exit(), it will:
//      > release JNI handle blocks, remove stack guard pages
//      > remove this thread from Threads list
//     <-- no more Java code from this thread after this point -->
//   + Stop VM thread, it will bring the remaining VM to a safepoint and stop
//     the compiler threads at safepoint
//     <-- do not use anything that could get blocked by Safepoint -->
//   + Disable tracing at JNI/JVM barriers
//   + Set _vm_exited flag for threads that are still running native code
//   + Call exit_globals()
//      > deletes tty
//      > deletes PerfMemory resources
//   + Delete this thread
//   + Return to caller

bool Threads::destroy_vm() {
  JavaThread* thread = JavaThread::current();

#ifdef ASSERT
  _vm_complete = false;
#endif
  // Wait until we are the last non-daemon thread to execute
  { MutexLocker nu(Threads_lock);
    while (Threads::number_of_non_daemon_threads() > 1)
      // This wait should make safepoint checks, wait without a timeout,
      // and wait as a suspend-equivalent condition.
      Threads_lock->wait(!Mutex::_no_safepoint_check_flag, 0,
                         Mutex::_as_suspend_equivalent_flag);
  }

  EventShutdown e;
  if (e.should_commit()) {
    e.set_reason("No remaining non-daemon Java threads");
    e.commit();
  }

  // Hang forever on exit if we are reporting an error.
  if (ShowMessageBoxOnError && VMError::is_error_reported()) {
    os::infinite_sleep();
  }
  os::wait_for_keypress_at_exit();

  // run Java level shutdown hooks
  thread->invoke_shutdown_hooks();

  before_exit(thread);

  thread->exit(true);

  // Stop VM thread.
  {
    // 4945125 The vm thread comes to a safepoint during exit.
    // GC vm_operations can get caught at the safepoint, and the
    // heap is unparseable if they are caught. Grab the Heap_lock
    // to prevent this. The GC vm_operations will not be able to
    // queue until after the vm thread is dead. After this point,
    // we'll never emerge out of the safepoint before the VM exits.

    MutexLockerEx ml(Heap_lock, Mutex::_no_safepoint_check_flag);

    VMThread::wait_for_vm_thread_exit();
    assert(SafepointSynchronize::is_at_safepoint(), "VM thread should exit at Safepoint");
    VMThread::destroy();
  }

  // Now, all Java threads are gone except daemon threads. Daemon threads
  // running Java code or in VM are stopped by the Safepoint. However,
  // daemon threads executing native code are still running.  But they
  // will be stopped at native=>Java/VM barriers. Note that we can't
  // simply kill or suspend them, as it is inherently deadlock-prone.

  VM_Exit::set_vm_exited();

  // Clean up ideal graph printers after the VMThread has started
  // the final safepoint which will block all the Compiler threads.
  // Note that this Thread has already logically exited so the
  // clean_up() function's use of a JavaThreadIteratorWithHandle
  // would be a problem except set_vm_exited() has remembered the
  // shutdown thread which is granted a policy exception.
#if defined(COMPILER2) && !defined(PRODUCT)
  IdealGraphPrinter::clean_up();
#endif

  notify_vm_shutdown();

  // exit_globals() will delete tty
  exit_globals();

  // We are after VM_Exit::set_vm_exited() so we can't call
  // thread->smr_delete() or we will block on the Threads_lock.
  // Deleting the shutdown thread here is safe because another
  // JavaThread cannot have an active ThreadsListHandle for
  // this JavaThread.
  delete thread;

#if INCLUDE_JVMCI
  if (JVMCICounterSize > 0) {
    FREE_C_HEAP_ARRAY(jlong, JavaThread::_jvmci_old_thread_counters);
  }
#endif

  LogConfiguration::finalize();

  return true;
}


jboolean Threads::is_supported_jni_version_including_1_1(jint version) {
  if (version == JNI_VERSION_1_1) return JNI_TRUE;
  return is_supported_jni_version(version);
}


jboolean Threads::is_supported_jni_version(jint version) {
  if (version == JNI_VERSION_1_2) return JNI_TRUE;
  if (version == JNI_VERSION_1_4) return JNI_TRUE;
  if (version == JNI_VERSION_1_6) return JNI_TRUE;
  if (version == JNI_VERSION_1_8) return JNI_TRUE;
  if (version == JNI_VERSION_9) return JNI_TRUE;
  if (version == JNI_VERSION_10) return JNI_TRUE;
  return JNI_FALSE;
}


void Threads::add(JavaThread* p, bool force_daemon) {
  // The threads lock must be owned at this point
  assert(Threads_lock->owned_by_self(), "must have threads lock");

  BarrierSet::barrier_set()->on_thread_attach(p);

  p->set_next(_thread_list);
  _thread_list = p;

  // Once a JavaThread is added to the Threads list, smr_delete() has
  // to be used to delete it. Otherwise we can just delete it directly.
  p->set_on_thread_list();

  _number_of_threads++;
  oop threadObj = p->threadObj();
  bool daemon = true;
  // Bootstrapping problem: threadObj can be null for initial
  // JavaThread (or for threads attached via JNI)
  if ((!force_daemon) && !is_daemon((threadObj))) {
    _number_of_non_daemon_threads++;
    daemon = false;
  }

  ThreadService::add_thread(p, daemon);

  // Maintain fast thread list
  ThreadsSMRSupport::add_thread(p);

  // Possible GC point.
  Events::log(p, "Thread added: " INTPTR_FORMAT, p2i(p));
}

void Threads::remove(JavaThread* p, bool is_daemon) {

  // Reclaim the ObjectMonitors from the omInUseList and omFreeList of the moribund thread.
  ObjectSynchronizer::omFlush(p);

  // Extra scope needed for Thread_lock, so we can check
  // that we do not remove thread without safepoint code notice
  { MutexLocker ml(Threads_lock);

    assert(ThreadsSMRSupport::get_java_thread_list()->includes(p), "p must be present");

    // Maintain fast thread list
    ThreadsSMRSupport::remove_thread(p);

    JavaThread* current = _thread_list;
    JavaThread* prev    = NULL;

    while (current != p) {
      prev    = current;
      current = current->next();
    }

    if (prev) {
      prev->set_next(current->next());
    } else {
      _thread_list = p->next();
    }

    _number_of_threads--;
    if (!is_daemon) {
      _number_of_non_daemon_threads--;

      // Only one thread left, do a notify on the Threads_lock so a thread waiting
      // on destroy_vm will wake up.
      if (number_of_non_daemon_threads() == 1) {
        Threads_lock->notify_all();
      }
    }
    ThreadService::remove_thread(p, is_daemon);

    // Make sure that safepoint code disregard this thread. This is needed since
    // the thread might mess around with locks after this point. This can cause it
    // to do callbacks into the safepoint code. However, the safepoint code is not aware
    // of this thread since it is removed from the queue.
    p->set_terminated_value();
  } // unlock Threads_lock

  // Since Events::log uses a lock, we grab it outside the Threads_lock
  Events::log(p, "Thread exited: " INTPTR_FORMAT, p2i(p));
}

// Operations on the Threads list for GC.  These are not explicitly locked,
// but the garbage collector must provide a safe context for them to run.
// In particular, these things should never be called when the Threads_lock
// is held by some other thread. (Note: the Safepoint abstraction also
// uses the Threads_lock to guarantee this property. It also makes sure that
// all threads gets blocked when exiting or starting).

void Threads::oops_do(OopClosure* f, CodeBlobClosure* cf) {
  ALL_JAVA_THREADS(p) {
    p->oops_do(f, cf);
  }
  VMThread::vm_thread()->oops_do(f, cf);
}

void Threads::change_thread_claim_token() {
  if (++_thread_claim_token == 0) {
    // On overflow of the token counter, there is a risk of future
    // collisions between a new global token value and a stale token
    // for a thread, because not all iterations visit all threads.
    // (Though it's pretty much a theoretical concern for non-trivial
    // token counter sizes.)  To deal with the possibility, reset all
    // the thread tokens to zero on global token overflow.
    struct ResetClaims : public ThreadClosure {
      virtual void do_thread(Thread* t) {
        t->claim_threads_do(false, 0);
      }
    } reset_claims;
    Threads::threads_do(&reset_claims);
    // On overflow, update the global token to non-zero, to
    // avoid the special "never claimed" initial thread value.
    _thread_claim_token = 1;
  }
}

#ifdef ASSERT
void assert_thread_claimed(const char* kind, Thread* t, uintx expected) {
  const uintx token = t->threads_do_token();
  assert(token == expected,
         "%s " PTR_FORMAT " has incorrect value " UINTX_FORMAT " != "
         UINTX_FORMAT, kind, p2i(t), token, expected);
}

void Threads::assert_all_threads_claimed() {
  ALL_JAVA_THREADS(p) {
    assert_thread_claimed("Thread", p, _thread_claim_token);
  }
  assert_thread_claimed("VMThread", VMThread::vm_thread(), _thread_claim_token);
}
#endif // ASSERT

class ParallelOopsDoThreadClosure : public ThreadClosure {
private:
  OopClosure* _f;
  CodeBlobClosure* _cf;
public:
  ParallelOopsDoThreadClosure(OopClosure* f, CodeBlobClosure* cf) : _f(f), _cf(cf) {}
  void do_thread(Thread* t) {
    t->oops_do(_f, _cf);
  }
};

void Threads::possibly_parallel_oops_do(bool is_par, OopClosure* f, CodeBlobClosure* cf) {
  ParallelOopsDoThreadClosure tc(f, cf);
  possibly_parallel_threads_do(is_par, &tc);
}

void Threads::nmethods_do(CodeBlobClosure* cf) {
  ALL_JAVA_THREADS(p) {
    // This is used by the code cache sweeper to mark nmethods that are active
    // on the stack of a Java thread. Ignore the sweeper thread itself to avoid
    // marking CodeCacheSweeperThread::_scanned_compiled_method as active.
    if(!p->is_Code_cache_sweeper_thread()) {
      p->nmethods_do(cf);
    }
  }
}

void Threads::metadata_do(MetadataClosure* f) {
  ALL_JAVA_THREADS(p) {
    p->metadata_do(f);
  }
}

class ThreadHandlesClosure : public ThreadClosure {
  void (*_f)(Metadata*);
 public:
  ThreadHandlesClosure(void f(Metadata*)) : _f(f) {}
  virtual void do_thread(Thread* thread) {
    thread->metadata_handles_do(_f);
  }
};

void Threads::metadata_handles_do(void f(Metadata*)) {
  // Only walk the Handles in Thread.
  ThreadHandlesClosure handles_closure(f);
  threads_do(&handles_closure);
}

void Threads::deoptimized_wrt_marked_nmethods() {
  ALL_JAVA_THREADS(p) {
    p->deoptimized_wrt_marked_nmethods();
  }
}


// Get count Java threads that are waiting to enter the specified monitor.
GrowableArray<JavaThread*>* Threads::get_pending_threads(ThreadsList * t_list,
                                                         int count,
                                                         address monitor) {
  GrowableArray<JavaThread*>* result = new GrowableArray<JavaThread*>(count);

  int i = 0;
  DO_JAVA_THREADS(t_list, p) {
    if (!p->can_call_java()) continue;

    address pending = (address)p->current_pending_monitor();
    if (pending == monitor) {             // found a match
      if (i < count) result->append(p);   // save the first count matches
      i++;
    }
  }

  return result;
}


JavaThread *Threads::owning_thread_from_monitor_owner(ThreadsList * t_list,
                                                      address owner) {
  // NULL owner means not locked so we can skip the search
  if (owner == NULL) return NULL;

  DO_JAVA_THREADS(t_list, p) {
    // first, see if owner is the address of a Java thread
    if (owner == (address)p) return p;
  }

  // Cannot assert on lack of success here since this function may be
  // used by code that is trying to report useful problem information
  // like deadlock detection.
  if (UseHeavyMonitors) return NULL;

  // If we didn't find a matching Java thread and we didn't force use of
  // heavyweight monitors, then the owner is the stack address of the
  // Lock Word in the owning Java thread's stack.
  //
  JavaThread* the_owner = NULL;
  DO_JAVA_THREADS(t_list, q) {
    if (q->is_lock_owned(owner)) {
      the_owner = q;
      break;
    }
  }

  // cannot assert on lack of success here; see above comment
  return the_owner;
}

// Threads::print_on() is called at safepoint by VM_PrintThreads operation.
void Threads::print_on(outputStream* st, bool print_stacks,
                       bool internal_format, bool print_concurrent_locks,
                       bool print_extended_info) {
  char buf[32];
  st->print_raw_cr(os::local_time_string(buf, sizeof(buf)));

  st->print_cr("Full thread dump %s (%s %s):",
               VM_Version::vm_name(),
               VM_Version::vm_release(),
               VM_Version::vm_info_string());
  st->cr();

#if INCLUDE_SERVICES
  // Dump concurrent locks
  ConcurrentLocksDump concurrent_locks;
  if (print_concurrent_locks) {
    concurrent_locks.dump_at_safepoint();
  }
#endif // INCLUDE_SERVICES

  ThreadsSMRSupport::print_info_on(st);
  st->cr();

  ALL_JAVA_THREADS(p) {
    ResourceMark rm;
    p->print_on(st, print_extended_info);
    if (print_stacks) {
      if (internal_format) {
        p->trace_stack();
      } else {
        p->print_stack_on(st);
      }
    }
    st->cr();
#if INCLUDE_SERVICES
    if (print_concurrent_locks) {
      concurrent_locks.print_locks_on(p, st);
    }
#endif // INCLUDE_SERVICES
  }

  VMThread::vm_thread()->print_on(st);
  st->cr();
  Universe::heap()->print_gc_threads_on(st);
  WatcherThread* wt = WatcherThread::watcher_thread();
  if (wt != NULL) {
    wt->print_on(st);
    st->cr();
  }

  st->flush();
}

void Threads::print_on_error(Thread* this_thread, outputStream* st, Thread* current, char* buf,
                             int buflen, bool* found_current) {
  if (this_thread != NULL) {
    bool is_current = (current == this_thread);
    *found_current = *found_current || is_current;
    st->print("%s", is_current ? "=>" : "  ");

    st->print(PTR_FORMAT, p2i(this_thread));
    st->print(" ");
    this_thread->print_on_error(st, buf, buflen);
    st->cr();
  }
}

class PrintOnErrorClosure : public ThreadClosure {
  outputStream* _st;
  Thread* _current;
  char* _buf;
  int _buflen;
  bool* _found_current;
 public:
  PrintOnErrorClosure(outputStream* st, Thread* current, char* buf,
                      int buflen, bool* found_current) :
   _st(st), _current(current), _buf(buf), _buflen(buflen), _found_current(found_current) {}

  virtual void do_thread(Thread* thread) {
    Threads::print_on_error(thread, _st, _current, _buf, _buflen, _found_current);
  }
};

// Threads::print_on_error() is called by fatal error handler. It's possible
// that VM is not at safepoint and/or current thread is inside signal handler.
// Don't print stack trace, as the stack may not be walkable. Don't allocate
// memory (even in resource area), it might deadlock the error handler.
void Threads::print_on_error(outputStream* st, Thread* current, char* buf,
                             int buflen) {
  ThreadsSMRSupport::print_info_on(st);
  st->cr();

  bool found_current = false;
  st->print_cr("Java Threads: ( => current thread )");
  ALL_JAVA_THREADS(thread) {
    print_on_error(thread, st, current, buf, buflen, &found_current);
  }
  st->cr();

  st->print_cr("Other Threads:");
  print_on_error(VMThread::vm_thread(), st, current, buf, buflen, &found_current);
  print_on_error(WatcherThread::watcher_thread(), st, current, buf, buflen, &found_current);

  PrintOnErrorClosure print_closure(st, current, buf, buflen, &found_current);
  Universe::heap()->gc_threads_do(&print_closure);

  if (!found_current) {
    st->cr();
    st->print("=>" PTR_FORMAT " (exited) ", p2i(current));
    current->print_on_error(st, buf, buflen);
    st->cr();
  }
  st->cr();

  st->print_cr("Threads with active compile tasks:");
  print_threads_compiling(st, buf, buflen);
}

void Threads::print_threads_compiling(outputStream* st, char* buf, int buflen, bool short_form) {
  ALL_JAVA_THREADS(thread) {
    if (thread->is_Compiler_thread()) {
      CompilerThread* ct = (CompilerThread*) thread;

      // Keep task in local variable for NULL check.
      // ct->_task might be set to NULL by concurring compiler thread
      // because it completed the compilation. The task is never freed,
      // though, just returned to a free list.
      CompileTask* task = ct->task();
      if (task != NULL) {
        thread->print_name_on_error(st, buf, buflen);
        st->print("  ");
        task->print(st, NULL, short_form, true);
      }
    }
  }
}


// Internal SpinLock and Mutex
// Based on ParkEvent

// Ad-hoc mutual exclusion primitives: SpinLock and Mux
//
// We employ SpinLocks _only for low-contention, fixed-length
// short-duration critical sections where we're concerned
// about native mutex_t or HotSpot Mutex:: latency.
// The mux construct provides a spin-then-block mutual exclusion
// mechanism.
//
// Testing has shown that contention on the ListLock guarding gFreeList
// is common.  If we implement ListLock as a simple SpinLock it's common
// for the JVM to devolve to yielding with little progress.  This is true
// despite the fact that the critical sections protected by ListLock are
// extremely short.
//
// TODO-FIXME: ListLock should be of type SpinLock.
// We should make this a 1st-class type, integrated into the lock
// hierarchy as leaf-locks.  Critically, the SpinLock structure
// should have sufficient padding to avoid false-sharing and excessive
// cache-coherency traffic.


typedef volatile int SpinLockT;

void Thread::SpinAcquire(volatile int * adr, const char * LockName) {
  if (Atomic::cmpxchg (1, adr, 0) == 0) {
    return;   // normal fast-path return
  }

  // Slow-path : We've encountered contention -- Spin/Yield/Block strategy.
  int ctr = 0;
  int Yields = 0;
  for (;;) {
    while (*adr != 0) {
      ++ctr;
      if ((ctr & 0xFFF) == 0 || !os::is_MP()) {
        if (Yields > 5) {
          os::naked_short_sleep(1);
        } else {
          os::naked_yield();
          ++Yields;
        }
      } else {
        SpinPause();
      }
    }
    if (Atomic::cmpxchg(1, adr, 0) == 0) return;
  }
}

void Thread::SpinRelease(volatile int * adr) {
  assert(*adr != 0, "invariant");
  OrderAccess::fence();      // guarantee at least release consistency.
  // Roach-motel semantics.
  // It's safe if subsequent LDs and STs float "up" into the critical section,
  // but prior LDs and STs within the critical section can't be allowed
  // to reorder or float past the ST that releases the lock.
  // Loads and stores in the critical section - which appear in program
  // order before the store that releases the lock - must also appear
  // before the store that releases the lock in memory visibility order.
  // Conceptually we need a #loadstore|#storestore "release" MEMBAR before
  // the ST of 0 into the lock-word which releases the lock, so fence
  // more than covers this on all platforms.
  *adr = 0;
}

// muxAcquire and muxRelease:
//
// *  muxAcquire and muxRelease support a single-word lock-word construct.
//    The LSB of the word is set IFF the lock is held.
//    The remainder of the word points to the head of a singly-linked list
//    of threads blocked on the lock.
//
// *  The current implementation of muxAcquire-muxRelease uses its own
//    dedicated Thread._MuxEvent instance.  If we're interested in
//    minimizing the peak number of extant ParkEvent instances then
//    we could eliminate _MuxEvent and "borrow" _ParkEvent as long
//    as certain invariants were satisfied.  Specifically, care would need
//    to be taken with regards to consuming unpark() "permits".
//    A safe rule of thumb is that a thread would never call muxAcquire()
//    if it's enqueued (cxq, EntryList, WaitList, etc) and will subsequently
//    park().  Otherwise the _ParkEvent park() operation in muxAcquire() could
//    consume an unpark() permit intended for monitorenter, for instance.
//    One way around this would be to widen the restricted-range semaphore
//    implemented in park().  Another alternative would be to provide
//    multiple instances of the PlatformEvent() for each thread.  One
//    instance would be dedicated to muxAcquire-muxRelease, for instance.
//
// *  Usage:
//    -- Only as leaf locks
//    -- for short-term locking only as muxAcquire does not perform
//       thread state transitions.
//
// Alternatives:
// *  We could implement muxAcquire and muxRelease with MCS or CLH locks
//    but with parking or spin-then-park instead of pure spinning.
// *  Use Taura-Oyama-Yonenzawa locks.
// *  It's possible to construct a 1-0 lock if we encode the lockword as
//    (List,LockByte).  Acquire will CAS the full lockword while Release
//    will STB 0 into the LockByte.  The 1-0 scheme admits stranding, so
//    acquiring threads use timers (ParkTimed) to detect and recover from
//    the stranding window.  Thread/Node structures must be aligned on 256-byte
//    boundaries by using placement-new.
// *  Augment MCS with advisory back-link fields maintained with CAS().
//    Pictorially:  LockWord -> T1 <-> T2 <-> T3 <-> ... <-> Tn <-> Owner.
//    The validity of the backlinks must be ratified before we trust the value.
//    If the backlinks are invalid the exiting thread must back-track through the
//    the forward links, which are always trustworthy.
// *  Add a successor indication.  The LockWord is currently encoded as
//    (List, LOCKBIT:1).  We could also add a SUCCBIT or an explicit _succ variable
//    to provide the usual futile-wakeup optimization.
//    See RTStt for details.
//


const intptr_t LOCKBIT = 1;

void Thread::muxAcquire(volatile intptr_t * Lock, const char * LockName) {
  intptr_t w = Atomic::cmpxchg(LOCKBIT, Lock, (intptr_t)0);
  if (w == 0) return;
  if ((w & LOCKBIT) == 0 && Atomic::cmpxchg(w|LOCKBIT, Lock, w) == w) {
    return;
  }

  ParkEvent * const Self = Thread::current()->_MuxEvent;
  assert((intptr_t(Self) & LOCKBIT) == 0, "invariant");
  for (;;) {
    int its = (os::is_MP() ? 100 : 0) + 1;

    // Optional spin phase: spin-then-park strategy
    while (--its >= 0) {
      w = *Lock;
      if ((w & LOCKBIT) == 0 && Atomic::cmpxchg(w|LOCKBIT, Lock, w) == w) {
        return;
      }
    }

    Self->reset();
    Self->OnList = intptr_t(Lock);
    // The following fence() isn't _strictly necessary as the subsequent
    // CAS() both serializes execution and ratifies the fetched *Lock value.
    OrderAccess::fence();
    for (;;) {
      w = *Lock;
      if ((w & LOCKBIT) == 0) {
        if (Atomic::cmpxchg(w|LOCKBIT, Lock, w) == w) {
          Self->OnList = 0;   // hygiene - allows stronger asserts
          return;
        }
        continue;      // Interference -- *Lock changed -- Just retry
      }
      assert(w & LOCKBIT, "invariant");
      Self->ListNext = (ParkEvent *) (w & ~LOCKBIT);
      if (Atomic::cmpxchg(intptr_t(Self)|LOCKBIT, Lock, w) == w) break;
    }

    while (Self->OnList != 0) {
      Self->park();
    }
  }
}

void Thread::muxAcquireW(volatile intptr_t * Lock, ParkEvent * ev) {
  intptr_t w = Atomic::cmpxchg(LOCKBIT, Lock, (intptr_t)0);
  if (w == 0) return;
  if ((w & LOCKBIT) == 0 && Atomic::cmpxchg(w|LOCKBIT, Lock, w) == w) {
    return;
  }

  ParkEvent * ReleaseAfter = NULL;
  if (ev == NULL) {
    ev = ReleaseAfter = ParkEvent::Allocate(NULL);
  }
  assert((intptr_t(ev) & LOCKBIT) == 0, "invariant");
  for (;;) {
    guarantee(ev->OnList == 0, "invariant");
    int its = (os::is_MP() ? 100 : 0) + 1;

    // Optional spin phase: spin-then-park strategy
    while (--its >= 0) {
      w = *Lock;
      if ((w & LOCKBIT) == 0 && Atomic::cmpxchg(w|LOCKBIT, Lock, w) == w) {
        if (ReleaseAfter != NULL) {
          ParkEvent::Release(ReleaseAfter);
        }
        return;
      }
    }

    ev->reset();
    ev->OnList = intptr_t(Lock);
    // The following fence() isn't _strictly necessary as the subsequent
    // CAS() both serializes execution and ratifies the fetched *Lock value.
    OrderAccess::fence();
    for (;;) {
      w = *Lock;
      if ((w & LOCKBIT) == 0) {
        if (Atomic::cmpxchg(w|LOCKBIT, Lock, w) == w) {
          ev->OnList = 0;
          // We call ::Release while holding the outer lock, thus
          // artificially lengthening the critical section.
          // Consider deferring the ::Release() until the subsequent unlock(),
          // after we've dropped the outer lock.
          if (ReleaseAfter != NULL) {
            ParkEvent::Release(ReleaseAfter);
          }
          return;
        }
        continue;      // Interference -- *Lock changed -- Just retry
      }
      assert(w & LOCKBIT, "invariant");
      ev->ListNext = (ParkEvent *) (w & ~LOCKBIT);
      if (Atomic::cmpxchg(intptr_t(ev)|LOCKBIT, Lock, w) == w) break;
    }

    while (ev->OnList != 0) {
      ev->park();
    }
  }
}

// Release() must extract a successor from the list and then wake that thread.
// It can "pop" the front of the list or use a detach-modify-reattach (DMR) scheme
// similar to that used by ParkEvent::Allocate() and ::Release().  DMR-based
// Release() would :
// (A) CAS() or swap() null to *Lock, releasing the lock and detaching the list.
// (B) Extract a successor from the private list "in-hand"
// (C) attempt to CAS() the residual back into *Lock over null.
//     If there were any newly arrived threads and the CAS() would fail.
//     In that case Release() would detach the RATs, re-merge the list in-hand
//     with the RATs and repeat as needed.  Alternately, Release() might
//     detach and extract a successor, but then pass the residual list to the wakee.
//     The wakee would be responsible for reattaching and remerging before it
//     competed for the lock.
//
// Both "pop" and DMR are immune from ABA corruption -- there can be
// multiple concurrent pushers, but only one popper or detacher.
// This implementation pops from the head of the list.  This is unfair,
// but tends to provide excellent throughput as hot threads remain hot.
// (We wake recently run threads first).
//
// All paths through muxRelease() will execute a CAS.
// Release consistency -- We depend on the CAS in muxRelease() to provide full
// bidirectional fence/MEMBAR semantics, ensuring that all prior memory operations
// executed within the critical section are complete and globally visible before the
// store (CAS) to the lock-word that releases the lock becomes globally visible.
void Thread::muxRelease(volatile intptr_t * Lock)  {
  for (;;) {
    const intptr_t w = Atomic::cmpxchg((intptr_t)0, Lock, LOCKBIT);
    assert(w & LOCKBIT, "invariant");
    if (w == LOCKBIT) return;
    ParkEvent * const List = (ParkEvent *) (w & ~LOCKBIT);
    assert(List != NULL, "invariant");
    assert(List->OnList == intptr_t(Lock), "invariant");
    ParkEvent * const nxt = List->ListNext;
    guarantee((intptr_t(nxt) & LOCKBIT) == 0, "invariant");

    // The following CAS() releases the lock and pops the head element.
    // The CAS() also ratifies the previously fetched lock-word value.
    if (Atomic::cmpxchg(intptr_t(nxt), Lock, w) != w) {
      continue;
    }
    List->OnList = 0;
    OrderAccess::fence();
    List->unpark();
    return;
  }
}


void Threads::verify() {
  ALL_JAVA_THREADS(p) {
    p->verify();
  }
  VMThread* thread = VMThread::vm_thread();
  if (thread != NULL) thread->verify();
}
