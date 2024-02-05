/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Azul Systems, Inc. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "classfile/javaThreadStatus.hpp"
#include "gc/shared/barrierSet.hpp"
#include "jfr/jfrEvents.hpp"
#include "jvm.h"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "nmt/memTracker.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/nonJavaThread.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadSMR.inline.hpp"
#include "utilities/macros.hpp"
#include "utilities/spinYield.hpp"
#if INCLUDE_JFR
#include "jfr/jfr.hpp"
#endif

#ifndef USE_LIBRARY_BASED_TLS_ONLY
// Current thread is maintained as a thread-local variable
THREAD_LOCAL Thread* Thread::_thr_current = nullptr;
#endif

// ======= Thread ========
// Base class for all threads: VMThread, WatcherThread, ConcurrentMarkSweepThread,
// JavaThread

DEBUG_ONLY(Thread* Thread::_starting_thread = nullptr;)

Thread::Thread() {

  DEBUG_ONLY(_run_state = PRE_CALL_RUN;)

  // stack and get_thread
  set_stack_base(nullptr);
  set_stack_size(0);
  set_lgrp_id(-1);
  DEBUG_ONLY(clear_suspendible_thread();)
  DEBUG_ONLY(clear_indirectly_suspendible_thread();)
  DEBUG_ONLY(clear_indirectly_safepoint_thread();)

  // allocated data structures
  set_osthread(nullptr);
  set_resource_area(new (mtThread)ResourceArea());
  DEBUG_ONLY(_current_resource_mark = nullptr;)
  set_handle_area(new (mtThread) HandleArea(nullptr));
  set_metadata_handles(new (mtClass) GrowableArray<Metadata*>(30, mtClass));
  set_last_handle_mark(nullptr);
  DEBUG_ONLY(_missed_ic_stub_refill_verifier = nullptr);

  // Initial value of zero ==> never claimed.
  _threads_do_token = 0;
  _threads_hazard_ptr = nullptr;
  _threads_list_ptr = nullptr;
  _nested_threads_hazard_ptr_cnt = 0;
  _rcu_counter = 0;

  // the handle mark links itself to last_handle_mark
  new HandleMark(this);

  // plain initialization
  debug_only(_owned_locks = nullptr;)
  NOT_PRODUCT(_skip_gcalot = false;)
  _jvmti_env_iteration_count = 0;
  set_allocated_bytes(0);
  _current_pending_raw_monitor = nullptr;
  _vm_error_callbacks = nullptr;

  // thread-specific hashCode stream generator state - Marsaglia shift-xor form
  _hashStateX = os::random();
  _hashStateY = 842502087;
  _hashStateZ = 0x8767;    // (int)(3579807591LL & 0xffff) ;
  _hashStateW = 273326509;

  // Many of the following fields are effectively final - immutable
  // Note that nascent threads can't use the Native Monitor-Mutex
  // construct until the _MutexEvent is initialized ...
  // CONSIDER: instead of using a fixed set of purpose-dedicated ParkEvents
  // we might instead use a stack of ParkEvents that we could provision on-demand.
  // The stack would act as a cache to avoid calls to ParkEvent::Allocate()
  // and ::Release()
  _ParkEvent   = ParkEvent::Allocate(this);

#ifdef CHECK_UNHANDLED_OOPS
  if (CheckUnhandledOops) {
    _unhandled_oops = new UnhandledOops(this);
  }
#endif // CHECK_UNHANDLED_OOPS

  // Notify the barrier set that a thread is being created. The initial
  // thread is created before the barrier set is available.  The call to
  // BarrierSet::on_thread_create() for this thread is therefore deferred
  // to BarrierSet::set_barrier_set().
  BarrierSet* const barrier_set = BarrierSet::barrier_set();
  if (barrier_set != nullptr) {
    barrier_set->on_thread_create(this);
  } else {
    // Only the main thread should be created before the barrier set
    // and that happens just before Thread::current is set. No other thread
    // can attach as the VM is not created yet, so they can't execute this code.
    // If the main thread creates other threads before the barrier set that is an error.
    assert(Thread::current_or_null() == nullptr, "creating thread before barrier set");
  }

  MACOS_AARCH64_ONLY(DEBUG_ONLY(_wx_init = false));
}

void Thread::initialize_tlab() {
  if (UseTLAB) {
    tlab().initialize();
  }
}

void Thread::initialize_thread_current() {
#ifndef USE_LIBRARY_BASED_TLS_ONLY
  assert(_thr_current == nullptr, "Thread::current already initialized");
  _thr_current = this;
#endif
  assert(ThreadLocalStorage::thread() == nullptr, "ThreadLocalStorage::thread already initialized");
  ThreadLocalStorage::set_thread(this);
  assert(Thread::current() == ThreadLocalStorage::thread(), "TLS mismatch!");
}

void Thread::clear_thread_current() {
  assert(Thread::current() == ThreadLocalStorage::thread(), "TLS mismatch!");
#ifndef USE_LIBRARY_BASED_TLS_ONLY
  _thr_current = nullptr;
#endif
  ThreadLocalStorage::set_thread(nullptr);
}

void Thread::record_stack_base_and_size() {
  // Note: at this point, Thread object is not yet initialized. Do not rely on
  // any members being initialized. Do not rely on Thread::current() being set.
  // If possible, refrain from doing anything which may crash or assert since
  // quite probably those crash dumps will be useless.
  address base;
  size_t size;
  os::current_stack_base_and_size(&base, &size);
  set_stack_base(base);
  set_stack_size(size);

  // Set stack limits after thread is initialized.
  if (is_Java_thread()) {
    JavaThread::cast(this)->stack_overflow_state()->initialize(stack_base(), stack_end());
  }
}

void Thread::register_thread_stack_with_NMT() {
  MemTracker::record_thread_stack(stack_end(), stack_size());
}

void Thread::unregister_thread_stack_with_NMT() {
  MemTracker::release_thread_stack(stack_end(), stack_size());
}

void Thread::call_run() {
  DEBUG_ONLY(_run_state = CALL_RUN;)

  // At this point, Thread object should be fully initialized and
  // Thread::current() should be set.

  assert(Thread::current_or_null() != nullptr, "current thread is unset");
  assert(Thread::current_or_null() == this, "current thread is wrong");

  // Perform common initialization actions

  MACOS_AARCH64_ONLY(this->init_wx());

  register_thread_stack_with_NMT();

  JFR_ONLY(Jfr::on_thread_start(this);)

  log_debug(os, thread)("Thread " UINTX_FORMAT " stack dimensions: "
    PTR_FORMAT "-" PTR_FORMAT " (" SIZE_FORMAT "k).",
    os::current_thread_id(), p2i(stack_end()),
    p2i(stack_base()), stack_size()/1024);

  // Perform <ChildClass> initialization actions
  DEBUG_ONLY(_run_state = PRE_RUN;)
  this->pre_run();

  // Invoke <ChildClass>::run()
  DEBUG_ONLY(_run_state = RUN;)
  this->run();
  // Returned from <ChildClass>::run(). Thread finished.

  // Perform common tear-down actions

  assert(Thread::current_or_null() != nullptr, "current thread is unset");
  assert(Thread::current_or_null() == this, "current thread is wrong");

  // Perform <ChildClass> tear-down actions
  DEBUG_ONLY(_run_state = POST_RUN;)
  this->post_run();

  // Note: at this point the thread object may already have deleted itself,
  // so from here on do not dereference *this*. Not all thread types currently
  // delete themselves when they terminate. But no thread should ever be deleted
  // asynchronously with respect to its termination - that is what _run_state can
  // be used to check.

  assert(Thread::current_or_null() == nullptr, "current thread still present");
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
  if (barrier_set != nullptr) {
    barrier_set->on_thread_destroy(this);
  }

  // deallocate data structures
  delete resource_area();
  // since the handle marks are using the handle area, we have to deallocated the root
  // handle mark before deallocating the thread's handle area,
  assert(last_handle_mark() != nullptr, "check we have an element");
  delete last_handle_mark();
  assert(last_handle_mark() == nullptr, "check we have reached the end");

  ParkEvent::Release(_ParkEvent);
  // Set to null as a termination indicator for has_terminated().
  Atomic::store(&_ParkEvent, (ParkEvent*)nullptr);

  delete handle_area();
  delete metadata_handles();

  // osthread() can be null, if creation of thread failed.
  if (osthread() != nullptr) os::free_thread(osthread());

  // Clear Thread::current if thread is deleting itself and it has not
  // already been done. This must be done before the memory is deallocated.
  // Needed to ensure JNI correctly detects non-attached threads.
  if (this == Thread::current_or_null()) {
    Thread::clear_thread_current();
  }

  CHECK_UNHANDLED_OOPS_ONLY(if (CheckUnhandledOops) delete unhandled_oops();)
}

#ifdef ASSERT
// A JavaThread is considered dangling if it not handshake-safe with respect to
// the current thread, it is not on a ThreadsList, or not at safepoint.
void Thread::check_for_dangling_thread_pointer(Thread *thread) {
  assert(!thread->is_Java_thread() ||
         JavaThread::cast(thread)->is_handshake_safe_for(Thread::current()) ||
         !JavaThread::cast(thread)->on_thread_list() ||
         SafepointSynchronize::is_at_safepoint() ||
         ThreadsSMRSupport::is_a_protected_JavaThread_with_lock(JavaThread::cast(thread)),
         "possibility of dangling Thread pointer");
}
#endif

// Is the target JavaThread protected by the calling Thread or by some other
// mechanism?
//
bool Thread::is_JavaThread_protected(const JavaThread* target) {
  Thread* current_thread = Thread::current();

  // Do the simplest check first:
  if (SafepointSynchronize::is_at_safepoint()) {
    // The target is protected since JavaThreads cannot exit
    // while we're at a safepoint.
    return true;
  }

  // If the target hasn't been started yet then it is trivially
  // "protected". We assume the caller is the thread that will do
  // the starting.
  if (target->osthread() == nullptr || target->osthread()->get_state() <= INITIALIZED) {
    return true;
  }

  // Now make the simple checks based on who the caller is:
  if (current_thread == target || Threads_lock->owner() == current_thread) {
    // Target JavaThread is self or calling thread owns the Threads_lock.
    // Second check is the same as Threads_lock->owner_is_self(),
    // but we already have the current thread so check directly.
    return true;
  }

  // Check the ThreadsLists associated with the calling thread (if any)
  // to see if one of them protects the target JavaThread:
  if (is_JavaThread_protected_by_TLH(target)) {
    return true;
  }

  // Use this debug code with -XX:+UseNewCode to diagnose locations that
  // are missing a ThreadsListHandle or other protection mechanism:
  // guarantee(!UseNewCode, "current_thread=" INTPTR_FORMAT " is not protecting target="
  //           INTPTR_FORMAT, p2i(current_thread), p2i(target));

  // Note: Since 'target' isn't protected by a TLH, the call to
  // target->is_handshake_safe_for() may crash, but we have debug bits so
  // we'll be able to figure out what protection mechanism is missing.
  assert(target->is_handshake_safe_for(current_thread), "JavaThread=" INTPTR_FORMAT
         " is not protected and not handshake safe.", p2i(target));

  // The target JavaThread is not protected so it is not safe to query:
  return false;
}

// Is the target JavaThread protected by a ThreadsListHandle (TLH) associated
// with the calling Thread?
//
bool Thread::is_JavaThread_protected_by_TLH(const JavaThread* target) {
  Thread* current_thread = Thread::current();

  // Check the ThreadsLists associated with the calling thread (if any)
  // to see if one of them protects the target JavaThread:
  for (SafeThreadsListPtr* stlp = current_thread->_threads_list_ptr;
       stlp != nullptr; stlp = stlp->previous()) {
    if (stlp->list()->includes(target)) {
      // The target JavaThread is protected by this ThreadsList:
      return true;
    }
  }

  // The target JavaThread is not protected by a TLH so it is not safe to query:
  return false;
}

void Thread::set_priority(Thread* thread, ThreadPriority priority) {
  debug_only(check_for_dangling_thread_pointer(thread);)
  // Can return an error!
  (void)os::set_priority(thread, priority);
}


void Thread::start(Thread* thread) {
  // Start is different from resume in that its safety is guaranteed by context or
  // being called from a Java method synchronized on the Thread object.
  if (thread->is_Java_thread()) {
    // Initialize the thread state to RUNNABLE before starting this thread.
    // Can not set it after the thread started because we do not know the
    // exact thread state at that time. It could be in MONITOR_WAIT or
    // in SLEEPING or some other state.
    java_lang_Thread::set_thread_status(JavaThread::cast(thread)->threadObj(),
                                        JavaThreadStatus::RUNNABLE);
  }
  os::start_thread(thread);
}

// GC Support
bool Thread::claim_par_threads_do(uintx claim_token) {
  uintx token = _threads_do_token;
  if (token != claim_token) {
    uintx res = Atomic::cmpxchg(&_threads_do_token, token, claim_token);
    if (res == token) {
      return true;
    }
    guarantee(res == claim_token, "invariant");
  }
  return false;
}

void Thread::oops_do_no_frames(OopClosure* f, CodeBlobClosure* cf) {
  // Do oop for ThreadShadow
  f->do_oop((oop*)&_pending_exception);
  handle_area()->oops_do(f);
}

// If the caller is a NamedThread, then remember, in the current scope,
// the given JavaThread in its _processed_thread field.
class RememberProcessedThread: public StackObj {
  NamedThread* _cur_thr;
public:
  RememberProcessedThread(Thread* thread) {
    Thread* self = Thread::current();
    if (self->is_Named_thread()) {
      _cur_thr = (NamedThread *)self;
      assert(_cur_thr->processed_thread() == nullptr, "nesting not supported");
      _cur_thr->set_processed_thread(thread);
    } else {
      _cur_thr = nullptr;
    }
  }

  ~RememberProcessedThread() {
    if (_cur_thr) {
      assert(_cur_thr->processed_thread() != nullptr, "nesting not supported");
      _cur_thr->set_processed_thread(nullptr);
    }
  }
};

void Thread::oops_do(OopClosure* f, CodeBlobClosure* cf) {
  // Record JavaThread to GC thread
  RememberProcessedThread rpt(this);
  oops_do_no_frames(f, cf);
  oops_do_frames(f, cf);
}

void Thread::metadata_handles_do(void f(Metadata*)) {
  // Only walk the Handles in Thread.
  if (metadata_handles() != nullptr) {
    for (int i = 0; i< metadata_handles()->length(); i++) {
      f(metadata_handles()->at(i));
    }
  }
}

void Thread::print_on(outputStream* st, bool print_extended_info) const {
  // get_priority assumes osthread initialized
  if (osthread() != nullptr) {
    int os_prio;
    if (os::get_native_priority(this, &os_prio) == OS_OK) {
      st->print("os_prio=%d ", os_prio);
    }

    st->print("cpu=%.2fms ",
              (double)os::thread_cpu_time(const_cast<Thread*>(this), true) / 1000000.0
              );
    st->print("elapsed=%.2fs ",
              (double)_statistical_info.getElapsedTime() / 1000.0
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
    if (!is_Java_thread() || !JavaThread::cast(this)->is_vthread_mounted()) {
      osthread()->print_on(st);
    }
  }
  ThreadsSMRSupport::print_info_on(this, st);
  st->print(" ");
  debug_only(if (WizardMode) print_owned_locks_on(st);)
}

void Thread::print() const { print_on(tty); }

// Thread::print_on_error() is called by fatal error handler. Don't use
// any lock or allocate memory.
void Thread::print_on_error(outputStream* st, char* buf, int buflen) const {
  assert(!(is_Compiler_thread() || is_Java_thread()), "Can't call name() here if it allocates");

  st->print("%s \"%s\"", type_name(), name());

  OSThread* os_thr = osthread();
  if (os_thr != nullptr) {
    st->fill_to(67);
    if (os_thr->get_state() != ZOMBIE) {
      st->print(" [id=%d, stack(" PTR_FORMAT "," PTR_FORMAT ") (" PROPERFMT ")]",
                osthread()->thread_id(), p2i(stack_end()), p2i(stack_base()),
                PROPERFMTARGS(stack_size()));
    } else {
      st->print(" terminated");
    }
  } else {
    st->print(" unknown state (no osThread)");
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
  Mutex* cur = _owned_locks;
  if (cur == nullptr) {
    st->print(" (no locks) ");
  } else {
    st->print_cr(" Locks owned:");
    while (cur) {
      cur->print_on(st);
      cur = cur->next();
    }
  }
}
#endif // ASSERT

// We had to move these methods here, because vm threads get into ObjectSynchronizer::enter
// However, there is a note in JavaThread::is_lock_owned() about the VM threads not being
// used for compilation in the future. If that change is made, the need for these methods
// should be revisited, and they should be removed if possible.

bool Thread::is_lock_owned(address adr) const {
  assert(LockingMode != LM_LIGHTWEIGHT, "should not be called with new lightweight locking");
  return is_in_full_stack(adr);
}

bool Thread::set_as_starting_thread() {
  assert(_starting_thread == nullptr, "already initialized: "
         "_starting_thread=" INTPTR_FORMAT, p2i(_starting_thread));
  // NOTE: this must be called inside the main thread.
  DEBUG_ONLY(_starting_thread = this;)
  return os::create_main_thread(JavaThread::cast(this));
}

// Ad-hoc mutual exclusion primitives: SpinLock
//
// We employ SpinLocks _only for low-contention, fixed-length
// short-duration critical sections where we're concerned
// about native mutex_t or HotSpot Mutex:: latency.
//
// TODO-FIXME: ListLock should be of type SpinLock.
// We should make this a 1st-class type, integrated into the lock
// hierarchy as leaf-locks.  Critically, the SpinLock structure
// should have sufficient padding to avoid false-sharing and excessive
// cache-coherency traffic.


typedef volatile int SpinLockT;

void Thread::SpinAcquire(volatile int * adr, const char * LockName) {
  if (Atomic::cmpxchg(adr, 0, 1) == 0) {
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
    if (Atomic::cmpxchg(adr, 0, 1) == 0) return;
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
