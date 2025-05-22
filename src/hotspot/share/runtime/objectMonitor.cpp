/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmSymbols.hpp"
#include "gc/shared/oopStorage.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/support/jfrThreadId.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/weakHandle.inline.hpp"
#include "prims/jvmtiDeferredUpdates.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/atomic.hpp"
#include "runtime/continuationWrapper.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/lightweightSynchronizer.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safefetch.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/threads.hpp"
#include "services/threadService.hpp"
#include "utilities/debug.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/globalCounter.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/preserveException.hpp"
#if INCLUDE_JFR
#include "jfr/support/jfrFlush.hpp"
#endif

#ifdef DTRACE_ENABLED

// Only bother with this argument setup if dtrace is available
// TODO-FIXME: probes should not fire when caller is _blocked.  assert() accordingly.


#define DTRACE_MONITOR_PROBE_COMMON(obj, thread)                           \
  char* bytes = nullptr;                                                   \
  int len = 0;                                                             \
  jlong jtid = SharedRuntime::get_java_tid(thread);                        \
  Symbol* klassname = obj->klass()->name();                                \
  if (klassname != nullptr) {                                              \
    bytes = (char*)klassname->bytes();                                     \
    len = klassname->utf8_length();                                        \
  }

#define DTRACE_MONITOR_WAIT_PROBE(monitor, obj, thread, millis)            \
  {                                                                        \
    if (DTraceMonitorProbes) {                                             \
      DTRACE_MONITOR_PROBE_COMMON(obj, thread);                            \
      HOTSPOT_MONITOR_WAIT(jtid,                                           \
                           (monitor), bytes, len, (millis));               \
    }                                                                      \
  }

#define HOTSPOT_MONITOR_contended__enter HOTSPOT_MONITOR_CONTENDED_ENTER
#define HOTSPOT_MONITOR_contended__entered HOTSPOT_MONITOR_CONTENDED_ENTERED
#define HOTSPOT_MONITOR_contended__exit HOTSPOT_MONITOR_CONTENDED_EXIT
#define HOTSPOT_MONITOR_notify HOTSPOT_MONITOR_NOTIFY
#define HOTSPOT_MONITOR_notifyAll HOTSPOT_MONITOR_NOTIFYALL

#define DTRACE_MONITOR_PROBE(probe, monitor, obj, thread)                  \
  {                                                                        \
    if (DTraceMonitorProbes) {                                             \
      DTRACE_MONITOR_PROBE_COMMON(obj, thread);                            \
      HOTSPOT_MONITOR_##probe(jtid,                                        \
                              (uintptr_t)(monitor), bytes, len);           \
    }                                                                      \
  }

#else //  ndef DTRACE_ENABLED

#define DTRACE_MONITOR_WAIT_PROBE(obj, thread, millis, mon)    {;}
#define DTRACE_MONITOR_PROBE(probe, obj, thread, mon)          {;}

#endif // ndef DTRACE_ENABLED

DEBUG_ONLY(static volatile bool InitDone = false;)

OopStorage* ObjectMonitor::_oop_storage = nullptr;

OopHandle ObjectMonitor::_vthread_list_head;
ParkEvent* ObjectMonitor::_vthread_unparker_ParkEvent = nullptr;

// -----------------------------------------------------------------------------
// Theory of operations -- Monitors lists, thread residency, etc:
//
// * A thread acquires ownership of a monitor by successfully
//   CAS()ing the _owner field from NO_OWNER/DEFLATER_MARKER to
//   its owner_id (return value from owner_id_from()).
//
// * Invariant: A thread appears on at most one monitor list --
//   entry_list or wait_set -- at any one time.
//
// * Contending threads "push" themselves onto the entry_list with CAS
//   and then spin/park.
//   If the thread is a virtual thread it will first attempt to
//   unmount itself. The virtual thread will first try to freeze
//   all frames in the heap. If the operation fails it will just
//   follow the regular path for platform threads. If the operation
//   succeeds, it will push itself onto the entry_list with CAS and then
//   return back to Java to continue the unmount logic.
//
// * After a contending thread eventually acquires the lock it must
//   dequeue itself from the entry_list.
//
// * The exiting thread identifies and unparks an "heir presumptive"
//   tentative successor thread on the entry_list. In case the successor
//   is an unmounted virtual thread, the exiting thread will first try
//   to add it to the list of vthreads waiting to be unblocked, and on
//   success it will unpark the special unblocker thread instead, which
//   will be in charge of submitting the vthread back to the scheduler
//   queue. Critically, the exiting thread doesn't unlink the successor
//   thread from the entry_list. After having been unparked/re-scheduled,
//   the wakee will recontend for ownership of the monitor. The successor
//   (wakee) will either acquire the lock or re-park/unmount itself.
//
//   Succession is provided for by a policy of competitive handoff.
//   The exiting thread does _not_ grant or pass ownership to the
//   successor thread.  (This is also referred to as "handoff succession").
//   Instead the exiting thread releases ownership and possibly wakes
//   a successor, so the successor can (re)compete for ownership of the lock.
//
// * The entry_list forms a queue of threads stalled trying to acquire
//   the lock. Within the entry_list the next pointers always form a
//   consistent singly linked list. At unlock-time when the unlocking
//   thread notices that the tail of the entry_list is not known, we
//   convert the singly linked entry_list into a doubly linked list by
//   assigning the prev pointers and the entry_list_tail pointer.
//
//   Example:
//
//   The first contending thread that "pushed" itself onto entry_list,
//   will be the last thread in the list. Each newly pushed thread in
//   entry_list will be linked through its next pointer, and have its
//   prev pointer set to null. Thus pushing six threads A-F (in that
//   order) onto entry_list, will form a singly linked list, see 1)
//   below.
//
//      1)  entry_list       ->F->E->D->C->B->A->null
//          entry_list_tail  ->null
//
//   Since the successor is chosen in FIFO order, the exiting thread
//   needs to find the tail of the entry_list. This is done by walking
//   from the entry_list head. While walking the list we also assign
//   the prev pointers of each thread, essentially forming a doubly
//   linked list, see 2) below.
//
//      2)  entry_list       ->F<=>E<=>D<=>C<=>B<=>A->null
//          entry_list_tail  ----------------------^
//
//   Once we have formed a doubly linked list it's easy to find the
//   successor (A), wake it up, have it remove itself, and update the
//   tail pointer, as seen in and 3) below.
//
//      3)  entry_list       ->F<=>E<=>D<=>C<=>B->null
//          entry_list_tail  ------------------^
//
//   At any time new threads can add themselves to the entry_list, see
//   4) below.
//
//      4)  entry_list       ->I->H->G->F<=>E<=>D->null
//          entry_list_tail  -------------------^
//
//   At some point in time the thread (F) that wants to remove itself
//   from the end of the list, will not have any prev pointer, see 5)
//   below.
//
//      5)  entry_list       ->I->H->G->F->null
//          entry_list_tail  -----------^
//
//   To resolve this we just start walking from the entry_list head
//   again, forming a new doubly linked list, before removing the
//   thread (F), see 6) and 7) below.
//
//      6)  entry_list       ->I<=>H<=>G<=>F->null
//          entry_list_tail  --------------^
//
//      7)  entry_list       ->I<=>H<=>G->null
//          entry_list_tail  ----------^
//
// * The monitor itself protects all of the operations on the
//   entry_list except for the CAS of a new arrival to the head. Only
//   the monitor owner can read or write the prev links (e.g. to
//   remove itself) or update the tail.
//
// * The monitor entry list operations avoid locks, but strictly speaking
//   they're not lock-free.  Enter is lock-free, exit is not.
//   For a description of 'Methods and apparatus providing non-blocking access
//   to a resource,' see U.S. Pat. No. 7844973.
//
// * The entry_list can have multiple concurrent "pushers" but only
//   one concurrent detaching thread. There is no ABA-problem with
//   this usage of CAS.
//
// * As long as the entry_list_tail is known the odds are good that we
//   should be able to dequeue after acquisition (in the ::enter()
//   epilogue) in constant-time. This is good since a key desideratum
//   is to minimize queue & monitor metadata manipulation that occurs
//   while holding the monitor lock -- that is, we want to minimize
//   monitor lock holds times. Note that even a small amount of fixed
//   spinning will greatly reduce the # of enqueue-dequeue operations
//   on entry_list. That is, spinning relieves contention on the
//   "inner" locks and monitor metadata.
//
//   Insert and delete operations may not operate in constant-time if
//   we have interference because some other thread is adding or
//   removing the head element of entry_list or if we need to convert
//   the singly linked entry_list into a doubly linked list to find the
//   tail.
//
// * The monitor synchronization subsystem avoids the use of native
//   synchronization primitives except for the narrow platform-specific
//   park-unpark abstraction. See the comments in os_posix.cpp regarding
//   the semantics of park-unpark. Put another way, this monitor implementation
//   depends only on atomic operations and park-unpark.
//
// * Waiting threads reside on the wait_set list -- wait() puts
//   the caller onto the wait_set.
//
// * notify() or notifyAll() simply transfers threads from the wait_set
//   to the entry_list. Subsequent exit() operations will
//   unpark/re-schedule the notifyee. Unparking/re-scheduling a
//   notifyee in notify() is inefficient - it's likely the notifyee
//   would simply impale itself on the lock held by the notifier.

// Check that object() and set_object() are called from the right context:
static void check_object_context() {
#ifdef ASSERT
  Thread* self = Thread::current();
  if (self->is_Java_thread()) {
    // Mostly called from JavaThreads so sanity check the thread state.
    JavaThread* jt = JavaThread::cast(self);
    switch (jt->thread_state()) {
    case _thread_in_vm:    // the usual case
    case _thread_in_Java:  // during deopt
      break;
    default:
      fatal("called from an unsafe thread state");
    }
    assert(jt->is_active_Java_thread(), "must be active JavaThread");
  } else {
    // However, ThreadService::get_current_contended_monitor()
    // can call here via the VMThread so sanity check it.
    assert(self->is_VM_thread(), "must be");
  }
#endif // ASSERT
}

ObjectMonitor::ObjectMonitor(oop object) :
  _metadata(0),
  _object(_oop_storage, object),
  _owner(NO_OWNER),
  _previous_owner_tid(0),
  _next_om(nullptr),
  _recursions(0),
  _entry_list(nullptr),
  _entry_list_tail(nullptr),
  _succ(NO_OWNER),
  _SpinDuration(ObjectMonitor::Knob_SpinLimit),
  _contentions(0),
  _wait_set(nullptr),
  _waiters(0),
  _wait_set_lock(0),
  _stack_locker(nullptr)
{ }

ObjectMonitor::~ObjectMonitor() {
  _object.release(_oop_storage);
}

oop ObjectMonitor::object() const {
  check_object_context();
  return _object.resolve();
}

void ObjectMonitor::ExitOnSuspend::operator()(JavaThread* current) {
  if (current->is_suspended()) {
    _om->_recursions = 0;
    _om->clear_successor();
    // Don't need a full fence after clearing successor here because of the call to exit().
    _om->exit(current, false /* not_suspended */);
    _om_exited = true;

    current->set_current_pending_monitor(_om);
  }
}

void ObjectMonitor::ClearSuccOnSuspend::operator()(JavaThread* current) {
  if (current->is_suspended()) {
    if (_om->has_successor(current)) {
      _om->clear_successor();
      OrderAccess::fence(); // always do a full fence when successor is cleared
    }
  }
}

#define assert_mark_word_consistency()                                         \
  assert(UseObjectMonitorTable || object()->mark() == markWord::encode(this),  \
         "object mark must match encoded this: mark=" INTPTR_FORMAT            \
         ", encoded this=" INTPTR_FORMAT, object()->mark().value(),            \
         markWord::encode(this).value());

// -----------------------------------------------------------------------------
// Enter support

bool ObjectMonitor::enter_is_async_deflating() {
  if (is_being_async_deflated()) {
    if (!UseObjectMonitorTable) {
      const oop l_object = object();
      if (l_object != nullptr) {
        // Attempt to restore the header/dmw to the object's header so that
        // we only retry once if the deflater thread happens to be slow.
        install_displaced_markword_in_object(l_object);
      }
    }
    return true;
  }

  return false;
}

bool ObjectMonitor::try_lock_with_contention_mark(JavaThread* locking_thread, ObjectMonitorContentionMark& contention_mark) {
  assert(contention_mark._monitor == this, "must be");
  assert(!is_being_async_deflated(), "must be");

  int64_t prev_owner = try_set_owner_from(NO_OWNER, locking_thread);
  bool success = false;

  if (prev_owner == NO_OWNER) {
    assert(_recursions == 0, "invariant");
    success = true;
  } else if (prev_owner == owner_id_from(locking_thread)) {
    _recursions++;
    success = true;
  } else if (prev_owner == DEFLATER_MARKER) {
    // Racing with deflation.
    prev_owner = try_set_owner_from(DEFLATER_MARKER, locking_thread);
    if (prev_owner == DEFLATER_MARKER) {
      // We successfully cancelled the in-progress async deflation by
      // changing owner from DEFLATER_MARKER to current.  We now extend
      // the lifetime of the contention_mark (e.g. contentions++) here
      // to prevent the deflater thread from winning the last part of
      // the 2-part async deflation protocol after the regular
      // decrement occurs when the contention_mark goes out of
      // scope. ObjectMonitor::deflate_monitor() which is called by
      // the deflater thread will decrement contentions after it
      // recognizes that the async deflation was cancelled.
      contention_mark.extend();
      success = true;
    } else if (prev_owner == NO_OWNER) {
      // At this point we cannot race with deflation as we have both incremented
      // contentions, seen contention > 0 and seen a DEFLATER_MARKER.
      // success will only be false if this races with something other than
      // deflation.
      prev_owner = try_set_owner_from(NO_OWNER, locking_thread);
      success = prev_owner == NO_OWNER;
    }
  }
  assert(!success || has_owner(locking_thread), "must be");

  return success;
}

void ObjectMonitor::enter_for_with_contention_mark(JavaThread* locking_thread, ObjectMonitorContentionMark& contention_mark) {
  // Used by LightweightSynchronizer::inflate_and_enter in deoptimization path to enter for another thread.
  // The monitor is private to or already owned by locking_thread which must be suspended.
  // So this code may only contend with deflation.
  assert(locking_thread == Thread::current() || locking_thread->is_obj_deopt_suspend(), "must be");
  bool success = try_lock_with_contention_mark(locking_thread, contention_mark);

  assert(success, "Failed to enter_for: locking_thread=" INTPTR_FORMAT
         ", this=" INTPTR_FORMAT "{owner=" INT64_FORMAT "}",
         p2i(locking_thread), p2i(this), owner_raw());
}

bool ObjectMonitor::enter_for(JavaThread* locking_thread) {
  // Used by ObjectSynchronizer::enter_for() to enter for another thread.
  // The monitor is private to or already owned by locking_thread which must be suspended.
  // So this code may only contend with deflation.
  assert(locking_thread == Thread::current() || locking_thread->is_obj_deopt_suspend(), "must be");

  // Block out deflation as soon as possible.
  ObjectMonitorContentionMark contention_mark(this);

  // Check for deflation.
  if (enter_is_async_deflating()) {
    return false;
  }

  bool success = try_lock_with_contention_mark(locking_thread, contention_mark);

  assert(success, "Failed to enter_for: locking_thread=" INTPTR_FORMAT
         ", this=" INTPTR_FORMAT "{owner=" INT64_FORMAT "}",
         p2i(locking_thread), p2i(this), owner_raw());
  assert(has_owner(locking_thread), "must be");
  return true;
}

bool ObjectMonitor::try_enter(JavaThread* current, bool check_for_recursion) {
  // TryLock avoids the CAS and handles deflation.
  TryLockResult r = try_lock(current);
  if (r == TryLockResult::Success) {
    assert(_recursions == 0, "invariant");
    return true;
  }

  // If called from SharedRuntime::monitor_exit_helper(), we know that
  // this thread doesn't already own the lock.
  if (!check_for_recursion) {
    return false;
  }

  if (r == TryLockResult::HasOwner && has_owner(current)) {
    _recursions++;
    return true;
  }

  return false;
}

bool ObjectMonitor::spin_enter(JavaThread* current) {
  assert(current == JavaThread::current(), "must be");

  // Check for recursion.
  if (try_enter(current)) {
    return true;
  }

  // Check for deflation.
  if (enter_is_async_deflating()) {
    return false;
  }

  // We've encountered genuine contention.

  // Do one round of spinning.
  // Note that if we acquire the monitor from an initial spin
  // we forgo posting JVMTI events and firing DTRACE probes.
  if (try_spin(current)) {
    assert(has_owner(current), "must be current: owner=" INT64_FORMAT, owner_raw());
    assert(_recursions == 0, "must be 0: recursions=%zd", _recursions);
    assert_mark_word_consistency();
    return true;
  }

  return false;
}

bool ObjectMonitor::enter(JavaThread* current) {
  assert(current == JavaThread::current(), "must be");

  if (spin_enter(current)) {
    return true;
  }

  assert(!has_owner(current), "invariant");
  assert(!has_successor(current), "invariant");
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(current->thread_state() != _thread_blocked, "invariant");

  // Keep is_being_async_deflated stable across the rest of enter
  ObjectMonitorContentionMark contention_mark(this);

  // Check for deflation.
  if (enter_is_async_deflating()) {
    return false;
  }

  // At this point this ObjectMonitor cannot be deflated, finish contended enter
  enter_with_contention_mark(current, contention_mark);
  return true;
}

void ObjectMonitor::notify_contended_enter(JavaThread* current) {
  current->set_current_pending_monitor(this);

  DTRACE_MONITOR_PROBE(contended__enter, this, object(), current);
  if (JvmtiExport::should_post_monitor_contended_enter()) {
    JvmtiExport::post_monitor_contended_enter(current, this);

    // The current thread does not yet own the monitor and does not
    // yet appear on any queues that would get it made the successor.
    // This means that the JVMTI_EVENT_MONITOR_CONTENDED_ENTER event
    // handler cannot accidentally consume an unpark() meant for the
    // ParkEvent associated with this ObjectMonitor.
  }
}

void ObjectMonitor::enter_with_contention_mark(JavaThread* current, ObjectMonitorContentionMark &cm) {
  assert(current == JavaThread::current(), "must be");
  assert(!has_owner(current), "must be");
  assert(cm._monitor == this, "must be");
  assert(!is_being_async_deflated(), "must be");

  JFR_ONLY(JfrConditionalFlush<EventJavaMonitorEnter> flush(current);)
  EventJavaMonitorEnter enter_event;
  if (enter_event.is_started()) {
    enter_event.set_monitorClass(object()->klass());
    // Set an address that is 'unique enough', such that events close in
    // time and with the same address are likely (but not guaranteed) to
    // belong to the same object.
    enter_event.set_address((uintptr_t)this);
  }
  EventVirtualThreadPinned vthread_pinned_event;

  freeze_result result;

  assert(current->current_pending_monitor() == nullptr, "invariant");

  ContinuationEntry* ce = current->last_continuation();
  bool is_virtual = ce != nullptr && ce->is_virtual_thread();
  if (is_virtual) {
    notify_contended_enter(current);
    result = Continuation::try_preempt(current, ce->cont_oop(current));
    if (result == freeze_ok) {
      bool acquired = vthread_monitor_enter(current);
      if (acquired) {
        // We actually acquired the monitor while trying to add the vthread to the
        // _entry_list so cancel preemption. We will still go through the preempt stub
        // but instead of unmounting we will call thaw to continue execution.
        current->set_preemption_cancelled(true);
        if (JvmtiExport::should_post_monitor_contended_entered()) {
          // We are going to call thaw again after this and finish the VMTS
          // transition so no need to do it here. We will post the event there.
          current->set_contended_entered_monitor(this);
        }
      }
      current->set_current_pending_monitor(nullptr);
      DEBUG_ONLY(int state = java_lang_VirtualThread::state(current->vthread()));
      assert((acquired && current->preemption_cancelled() && state == java_lang_VirtualThread::RUNNING) ||
             (!acquired && !current->preemption_cancelled() && state == java_lang_VirtualThread::BLOCKING), "invariant");
      return;
    }
  }

  {
    // Change java thread status to indicate blocked on monitor enter.
    JavaThreadBlockedOnMonitorEnterState jtbmes(current, this);

    if (!is_virtual) { // already notified contended_enter for virtual
      notify_contended_enter(current);
    }
    OSThreadContendState osts(current->osthread());

    assert(current->thread_state() == _thread_in_vm, "invariant");

    for (;;) {
      ExitOnSuspend eos(this);
      {
        ThreadBlockInVMPreprocess<ExitOnSuspend> tbivs(current, eos, true /* allow_suspend */);
        enter_internal(current);
        current->set_current_pending_monitor(nullptr);
        // We can go to a safepoint at the end of this block. If we
        // do a thread dump during that safepoint, then this thread will show
        // as having "-locked" the monitor, but the OS and java.lang.Thread
        // states will still report that the thread is blocked trying to
        // acquire it.
        // If there is a suspend request, ExitOnSuspend will exit the OM
        // and set the OM as pending.
      }
      if (!eos.exited()) {
        // ExitOnSuspend did not exit the OM
        assert(has_owner(current), "invariant");
        break;
      }
    }

    // We've just gotten past the enter-check-for-suspend dance and we now own
    // the monitor free and clear.
  }

  assert(contentions() >= 0, "must not be negative: contentions=%d", contentions());

  // Must either set _recursions = 0 or ASSERT _recursions == 0.
  assert(_recursions == 0, "invariant");
  assert(has_owner(current), "invariant");
  assert(!has_successor(current), "invariant");
  assert_mark_word_consistency();

  // The thread -- now the owner -- is back in vm mode.
  // Report the glorious news via TI,DTrace and jvmstat.
  // The probe effect is non-trivial.  All the reportage occurs
  // while we hold the monitor, increasing the length of the critical
  // section.  Amdahl's parallel speedup law comes vividly into play.
  //
  // Another option might be to aggregate the events (thread local or
  // per-monitor aggregation) and defer reporting until a more opportune
  // time -- such as next time some thread encounters contention but has
  // yet to acquire the lock.  While spinning that thread could
  // spinning we could increment JVMStat counters, etc.

  DTRACE_MONITOR_PROBE(contended__entered, this, object(), current);
  if (JvmtiExport::should_post_monitor_contended_entered()) {
    JvmtiExport::post_monitor_contended_entered(current, this);

    // The current thread already owns the monitor and is not going to
    // call park() for the remainder of the monitor enter protocol. So
    // it doesn't matter if the JVMTI_EVENT_MONITOR_CONTENDED_ENTERED
    // event handler consumed an unpark() issued by the thread that
    // just exited the monitor.
  }
  if (enter_event.should_commit()) {
    enter_event.set_previousOwner(_previous_owner_tid);
    enter_event.commit();
  }

  if (current->current_waiting_monitor() == nullptr) {
    ContinuationEntry* ce = current->last_continuation();
    if (ce != nullptr && ce->is_virtual_thread()) {
      current->post_vthread_pinned_event(&vthread_pinned_event, "Contended monitor enter", result);
    }
  }
}

// Caveat: try_lock() is not necessarily serializing if it returns failure.
// Callers must compensate as needed.

ObjectMonitor::TryLockResult ObjectMonitor::try_lock(JavaThread* current) {
  int64_t own = owner_raw();
  int64_t first_own = own;

  for (;;) {
    if (own == DEFLATER_MARKER) {
      // Block out deflation as soon as possible.
      ObjectMonitorContentionMark contention_mark(this);

      // Check for deflation.
      if (enter_is_async_deflating()) {
        // Treat deflation as interference.
        return TryLockResult::Interference;
      }
      if (try_lock_with_contention_mark(current, contention_mark)) {
        assert(_recursions == 0, "invariant");
        return TryLockResult::Success;
      } else {
        // Deflation won or change of owner; dont spin
        break;
      }
    } else if (own == NO_OWNER) {
      int64_t prev_own = try_set_owner_from(NO_OWNER, current);
      if (prev_own == NO_OWNER) {
        assert(_recursions == 0, "invariant");
        return TryLockResult::Success;
      } else {
        // The lock had been free momentarily, but we lost the race to the lock.
        own = prev_own;
      }
    } else {
      // Retry doesn't make as much sense because the lock was just acquired.
      break;
    }
  }
  return first_own == own ? TryLockResult::HasOwner : TryLockResult::Interference;
}

// Push "current" onto the head of the _entry_list. Once on _entry_list,
// current stays on-queue until it acquires the lock.
void ObjectMonitor::add_to_entry_list(JavaThread* current, ObjectWaiter* node) {
  node->_prev   = nullptr;
  node->TState  = ObjectWaiter::TS_ENTER;

  for (;;) {
    ObjectWaiter* head = Atomic::load(&_entry_list);
    node->_next = head;
    if (Atomic::cmpxchg(&_entry_list, head, node) == head) {
      return;
    }
  }
}

// Push "current" onto the head of the entry_list.
// If the _entry_list was changed during our push operation, we try to
// lock the monitor. Returns true if we locked the monitor, and false
// if we added current to _entry_list. Once on _entry_list, current
// stays on-queue until it acquires the lock.
bool ObjectMonitor::try_lock_or_add_to_entry_list(JavaThread* current, ObjectWaiter* node) {
  node->_prev   = nullptr;
  node->TState  = ObjectWaiter::TS_ENTER;

  for (;;) {
    ObjectWaiter* head = Atomic::load(&_entry_list);
    node->_next = head;
    if (Atomic::cmpxchg(&_entry_list, head, node) == head) {
      return false;
    }

    // Interference - the CAS failed because _entry_list changed.  Before
    // retrying the CAS retry taking the lock as it may now be free.
    if (try_lock(current) == TryLockResult::Success) {
      assert(!has_successor(current), "invariant");
      assert(has_owner(current), "invariant");
      return true;
    }
  }
}

static void post_monitor_deflate_event(EventJavaMonitorDeflate* event,
                                       const oop obj) {
  assert(event != nullptr, "invariant");
  if (obj == nullptr) {
    // Accept the case when obj was already garbage-collected.
    // Emit the event anyway, but without details.
    event->set_monitorClass(nullptr);
    event->set_address(0);
  } else {
    const Klass* monitor_klass = obj->klass();
    if (ObjectMonitor::is_jfr_excluded(monitor_klass)) {
      return;
    }
    event->set_monitorClass(monitor_klass);
    event->set_address((uintptr_t)(void*)obj);
  }
  event->commit();
}

// Deflate the specified ObjectMonitor if not in-use. Returns true if it
// was deflated and false otherwise.
//
// The async deflation protocol sets owner to DEFLATER_MARKER and
// makes contentions negative as signals to contending threads that
// an async deflation is in progress. There are a number of checks
// as part of the protocol to make sure that the calling thread has
// not lost the race to a contending thread.
//
// The ObjectMonitor has been successfully async deflated when:
//   (contentions < 0)
// Contending threads that see that condition know to retry their operation.
//
bool ObjectMonitor::deflate_monitor(Thread* current) {
  if (is_busy()) {
    // Easy checks are first - the ObjectMonitor is busy so no deflation.
    return false;
  }

  EventJavaMonitorDeflate event;

  const oop obj = object_peek();

  if (obj == nullptr) {
    // If the object died, we can recycle the monitor without racing with
    // Java threads. The GC already broke the association with the object.
    set_owner_from_raw(NO_OWNER, DEFLATER_MARKER);
    assert(contentions() >= 0, "must be non-negative: contentions=%d", contentions());
    _contentions = INT_MIN; // minimum negative int
  } else {
    // Attempt async deflation protocol.

    // Set a null owner to DEFLATER_MARKER to force any contending thread
    // through the slow path. This is just the first part of the async
    // deflation dance.
    if (try_set_owner_from_raw(NO_OWNER, DEFLATER_MARKER) != NO_OWNER) {
      // The owner field is no longer null so we lost the race since the
      // ObjectMonitor is now busy.
      return false;
    }

    if (contentions() > 0 || _waiters != 0) {
      // Another thread has raced to enter the ObjectMonitor after
      // is_busy() above or has already entered and waited on
      // it which makes it busy so no deflation. Restore owner to
      // null if it is still DEFLATER_MARKER.
      if (try_set_owner_from_raw(DEFLATER_MARKER, NO_OWNER) != DEFLATER_MARKER) {
        // Deferred decrement for the JT enter_internal() that cancelled the async deflation.
        add_to_contentions(-1);
      }
      return false;
    }

    // Make a zero contentions field negative to force any contending threads
    // to retry. This is the second part of the async deflation dance.
    if (Atomic::cmpxchg(&_contentions, 0, INT_MIN) != 0) {
      // Contentions was no longer 0 so we lost the race since the
      // ObjectMonitor is now busy. Restore owner to null if it is
      // still DEFLATER_MARKER:
      if (try_set_owner_from_raw(DEFLATER_MARKER, NO_OWNER) != DEFLATER_MARKER) {
        // Deferred decrement for the JT enter_internal() that cancelled the async deflation.
        add_to_contentions(-1);
      }
      return false;
    }
  }

  // Sanity checks for the races:
  guarantee(owner_is_DEFLATER_MARKER(), "must be deflater marker");
  guarantee(contentions() < 0, "must be negative: contentions=%d",
            contentions());
  guarantee(_waiters == 0, "must be 0: waiters=%d", _waiters);
  ObjectWaiter* w = Atomic::load(&_entry_list);
  guarantee(w == nullptr,
            "must be no entering threads: entry_list=" INTPTR_FORMAT,
            p2i(w));

  if (obj != nullptr) {
    if (log_is_enabled(Trace, monitorinflation)) {
      ResourceMark rm;
      log_trace(monitorinflation)("deflate_monitor: object=" INTPTR_FORMAT
                                  ", mark=" INTPTR_FORMAT ", type='%s'",
                                  p2i(obj), obj->mark().value(),
                                  obj->klass()->external_name());
    }
  }

  if (UseObjectMonitorTable) {
    LightweightSynchronizer::deflate_monitor(current, obj, this);
  } else if (obj != nullptr) {
    // Install the old mark word if nobody else has already done it.
    install_displaced_markword_in_object(obj);
  }

  if (event.should_commit()) {
    post_monitor_deflate_event(&event, obj);
  }

  // We leave owner == DEFLATER_MARKER and contentions < 0
  // to force any racing threads to retry.
  return true;  // Success, ObjectMonitor has been deflated.
}

// Install the displaced mark word (dmw) of a deflating ObjectMonitor
// into the header of the object associated with the monitor. This
// idempotent method is called by a thread that is deflating a
// monitor and by other threads that have detected a race with the
// deflation process.
void ObjectMonitor::install_displaced_markword_in_object(const oop obj) {
  assert(!UseObjectMonitorTable, "ObjectMonitorTable has no dmw");
  // This function must only be called when (owner == DEFLATER_MARKER
  // && contentions <= 0), but we can't guarantee that here because
  // those values could change when the ObjectMonitor gets moved from
  // the global free list to a per-thread free list.

  guarantee(obj != nullptr, "must be non-null");

  // Separate loads in is_being_async_deflated(), which is almost always
  // called before this function, from the load of dmw/header below.

  // _contentions and dmw/header may get written by different threads.
  // Make sure to observe them in the same order when having several observers.
  OrderAccess::loadload_for_IRIW();

  const oop l_object = object_peek();
  if (l_object == nullptr) {
    // ObjectMonitor's object ref has already been cleared by async
    // deflation or GC so we're done here.
    return;
  }
  assert(l_object == obj, "object=" INTPTR_FORMAT " must equal obj="
         INTPTR_FORMAT, p2i(l_object), p2i(obj));

  markWord dmw = header();
  // The dmw has to be neutral (not null, not locked and not marked).
  assert(dmw.is_neutral(), "must be neutral: dmw=" INTPTR_FORMAT, dmw.value());

  // Install displaced mark word if the object's header still points
  // to this ObjectMonitor. More than one racing caller to this function
  // can rarely reach this point, but only one can win.
  markWord res = obj->cas_set_mark(dmw, markWord::encode(this));
  if (res != markWord::encode(this)) {
    // This should be rare so log at the Info level when it happens.
    log_info(monitorinflation)("install_displaced_markword_in_object: "
                               "failed cas_set_mark: new_mark=" INTPTR_FORMAT
                               ", old_mark=" INTPTR_FORMAT ", res=" INTPTR_FORMAT,
                               dmw.value(), markWord::encode(this).value(),
                               res.value());
  }

  // Note: It does not matter which thread restored the header/dmw
  // into the object's header. The thread deflating the monitor just
  // wanted the object's header restored and it is. The threads that
  // detected a race with the deflation process also wanted the
  // object's header restored before they retry their operation and
  // because it is restored they will only retry once.
}

// Convert the fields used by is_busy() to a string that can be
// used for diagnostic output.
const char* ObjectMonitor::is_busy_to_string(stringStream* ss) {
  ss->print("is_busy: waiters=%d"
            ", contentions=%d"
            ", owner=" INT64_FORMAT
            ", entry_list=" PTR_FORMAT,
            _waiters,
            (contentions() > 0 ? contentions() : 0),
            owner_is_DEFLATER_MARKER()
                // We report null instead of DEFLATER_MARKER here because is_busy()
                // ignores DEFLATER_MARKER values.
                ? NO_OWNER
                : owner_raw(),
            p2i(_entry_list));
  return ss->base();
}

void ObjectMonitor::enter_internal(JavaThread* current) {
  assert(current->thread_state() == _thread_blocked, "invariant");

  // Try the lock - TATAS
  if (try_lock(current) == TryLockResult::Success) {
    assert(!has_successor(current), "invariant");
    assert(has_owner(current), "invariant");
    return;
  }

  assert(InitDone, "Unexpectedly not initialized");

  // We try one round of spinning *before* enqueueing current.
  //
  // If the _owner is ready but OFFPROC we could use a YieldTo()
  // operation to donate the remainder of this thread's quantum
  // to the owner.  This has subtle but beneficial affinity
  // effects.

  if (try_spin(current)) {
    assert(has_owner(current), "invariant");
    assert(!has_successor(current), "invariant");
    return;
  }

  // The Spin failed -- Enqueue and park the thread ...
  assert(!has_successor(current), "invariant");
  assert(!has_owner(current), "invariant");

  // Enqueue "current" on ObjectMonitor's _entry_list.
  //
  // Node acts as a proxy for current.
  // As an aside, if were to ever rewrite the synchronization code mostly
  // in Java, WaitNodes, ObjectMonitors, and Events would become 1st-class
  // Java objects.  This would avoid awkward lifecycle and liveness issues,
  // as well as eliminate a subset of ABA issues.
  // TODO: eliminate ObjectWaiter and enqueue either Threads or Events.

  ObjectWaiter node(current);
  current->_ParkEvent->reset();

  if (try_lock_or_add_to_entry_list(current, &node)) {
    return; // We got the lock.
  }
  // This thread is now added to the _entry_list.

  // The lock might have been released while this thread was occupied queueing
  // itself onto _entry_list.  To close the race and avoid "stranding" and
  // progress-liveness failure we must resample-retry _owner before parking.
  // Note the Dekker/Lamport duality: ST _entry_list; MEMBAR; LD Owner.
  // In this case the ST-MEMBAR is accomplished with CAS().
  //
  // TODO: Defer all thread state transitions until park-time.
  // Since state transitions are heavy and inefficient we'd like
  // to defer the state transitions until absolutely necessary,
  // and in doing so avoid some transitions ...

  // For virtual threads that are pinned, do a timed-park instead to
  // alleviate some deadlocks cases where the succesor is an unmounted
  // virtual thread that cannot run. This can happen in particular when
  // this virtual thread is currently loading/initializing a class, and
  // all other carriers have a vthread pinned to it waiting for said class
  // to be loaded/initialized.
  static int MAX_RECHECK_INTERVAL = 1000;
  int recheck_interval = 1;
  bool do_timed_parked = false;
  ContinuationEntry* ce = current->last_continuation();
  if (ce != nullptr && ce->is_virtual_thread()) {
    do_timed_parked = true;
  }

  for (;;) {

    if (try_lock(current) == TryLockResult::Success) {
      break;
    }
    assert(!has_owner(current), "invariant");

    // park self
    if (do_timed_parked) {
      current->_ParkEvent->park((jlong) recheck_interval);
      // Increase the recheck_interval, but clamp the value.
      recheck_interval *= 8;
      if (recheck_interval > MAX_RECHECK_INTERVAL) {
        recheck_interval = MAX_RECHECK_INTERVAL;
      }
    } else {
      current->_ParkEvent->park();
    }

    if (try_lock(current) == TryLockResult::Success) {
      break;
    }

    // The lock is still contested.

    // Assuming this is not a spurious wakeup we'll normally find _succ == current.
    // We can defer clearing _succ until after the spin completes
    // try_spin() must tolerate being called with _succ == current.
    // Try yet another round of adaptive spinning.
    if (try_spin(current)) {
      break;
    }

    // We can find that we were unpark()ed and redesignated _succ while
    // we were spinning.  That's harmless.  If we iterate and call park(),
    // park() will consume the event and return immediately and we'll
    // just spin again.  This pattern can repeat, leaving _succ to simply
    // spin on a CPU.

    if (has_successor(current)) clear_successor();

    // Invariant: after clearing _succ a thread *must* retry _owner before parking.
    OrderAccess::fence();
  }

  // Egress :
  // Current has acquired the lock -- Unlink current from the _entry_list.
  unlink_after_acquire(current, &node);
  if (has_successor(current)) {
    clear_successor();
    // Note that we don't need to do OrderAccess::fence() after clearing
    // _succ here, since we own the lock.
  }

  // We've acquired ownership with CAS().
  // CAS is serializing -- it has MEMBAR/FENCE-equivalent semantics.
  // But since the CAS() this thread may have also stored into _succ
  // or entry_list.  These meta-data updates must be visible __before
  // this thread subsequently drops the lock.
  // Consider what could occur if we didn't enforce this constraint --
  // STs to monitor meta-data and user-data could reorder with (become
  // visible after) the ST in exit that drops ownership of the lock.
  // Some other thread could then acquire the lock, but observe inconsistent
  // or old monitor meta-data and heap data.  That violates the JMM.
  // To that end, the exit() operation must have at least STST|LDST
  // "release" barrier semantics.  Specifically, there must be at least a
  // STST|LDST barrier in exit() before the ST of null into _owner that drops
  // the lock.   The barrier ensures that changes to monitor meta-data and data
  // protected by the lock will be visible before we release the lock, and
  // therefore before some other thread (CPU) has a chance to acquire the lock.
  // See also: http://gee.cs.oswego.edu/dl/jmm/cookbook.html.
  //
  // Critically, any prior STs to _succ or entry_list must be visible before
  // the ST of null into _owner in the *subsequent* (following) corresponding
  // monitorexit.

  return;
}

// reenter_internal() is a specialized inline form of the latter half of the
// contended slow-path from enter_internal().  We use reenter_internal() only for
// monitor reentry in wait().
//
// In the future we should reconcile enter_internal() and reenter_internal().

void ObjectMonitor::reenter_internal(JavaThread* current, ObjectWaiter* currentNode) {
  assert(current != nullptr, "invariant");
  assert(current->thread_state() != _thread_blocked, "invariant");
  assert(currentNode != nullptr, "invariant");
  assert(currentNode->_thread == current, "invariant");
  assert(_waiters > 0, "invariant");
  assert_mark_word_consistency();

  for (;;) {
    ObjectWaiter::TStates v = currentNode->TState;
    guarantee(v == ObjectWaiter::TS_ENTER, "invariant");
    assert(!has_owner(current), "invariant");

    // This thread has been notified so try to reacquire the lock.
    if (try_lock(current) == TryLockResult::Success) {
      break;
    }

    // If that fails, spin again.  Note that spin count may be zero so the above TryLock
    // is necessary.
    if (try_spin(current)) {
        break;
    }

    {
      OSThreadContendState osts(current->osthread());

      assert(current->thread_state() == _thread_in_vm, "invariant");

      {
        ClearSuccOnSuspend csos(this);
        ThreadBlockInVMPreprocess<ClearSuccOnSuspend> tbivs(current, csos, true /* allow_suspend */);
        current->_ParkEvent->park();
      }
    }

    // Try again, but just so we distinguish between futile wakeups and
    // successful wakeups.  The following test isn't algorithmically
    // necessary, but it helps us maintain sensible statistics.
    if (try_lock(current) == TryLockResult::Success) {
      break;
    }

    // The lock is still contested.

    // Assuming this is not a spurious wakeup we'll normally
    // find that _succ == current.
    if (has_successor(current)) clear_successor();

    // Invariant: after clearing _succ a contending thread
    // *must* retry  _owner before parking.
    OrderAccess::fence();
  }

  // Current has acquired the lock -- Unlink current from the _entry_list.
  assert(has_owner(current), "invariant");
  assert_mark_word_consistency();
  unlink_after_acquire(current, currentNode);
  if (has_successor(current)) clear_successor();
  assert(!has_successor(current), "invariant");
  currentNode->TState = ObjectWaiter::TS_RUN;
  OrderAccess::fence();      // see comments at the end of enter_internal()
}

// This method is called from two places:
// - On monitorenter contention with a null waiter.
// - After Object.wait() times out or the target is interrupted to reenter the
//   monitor, with the existing waiter.
// For the Object.wait() case we do not delete the ObjectWaiter in case we
// succesfully acquire the monitor since we are going to need it on return.
bool ObjectMonitor::vthread_monitor_enter(JavaThread* current, ObjectWaiter* waiter) {
  if (try_lock(current) == TryLockResult::Success) {
    assert(has_owner(current), "invariant");
    assert(!has_successor(current), "invariant");
    return true;
  }

  oop vthread = current->vthread();
  ObjectWaiter* node = waiter != nullptr ? waiter : new ObjectWaiter(vthread, this);
  if (try_lock_or_add_to_entry_list(current, node)) {
    // We got the lock.
    if (waiter == nullptr) delete node;  // for Object.wait() don't delete yet
    return true;
  }
  // This thread is now added to the entry_list.

  // We have to try once more since owner could have exited monitor and checked
  // _entry_list before we added the node to the queue.
  if (try_lock(current) == TryLockResult::Success) {
    assert(has_owner(current), "invariant");
    unlink_after_acquire(current, node);
    if (has_successor(current)) clear_successor();
    if (waiter == nullptr) delete node;  // for Object.wait() don't delete yet
    return true;
  }

  assert(java_lang_VirtualThread::state(vthread) == java_lang_VirtualThread::RUNNING, "wrong state for vthread");
  java_lang_VirtualThread::set_state(vthread, java_lang_VirtualThread::BLOCKING);

  // We didn't succeed in acquiring the monitor so increment _contentions and
  // save ObjectWaiter* in the vthread since we will need it when resuming execution.
  add_to_contentions(1);
  java_lang_VirtualThread::set_objectWaiter(vthread, node);
  return false;
}

// Called from thaw code to resume the monitor operation that caused the vthread
// to be unmounted. Method returns true if the monitor is successfully acquired,
// which marks the end of the monitor operation, otherwise it returns false.
bool ObjectMonitor::resume_operation(JavaThread* current, ObjectWaiter* node, ContinuationWrapper& cont) {
  assert(java_lang_VirtualThread::state(current->vthread()) == java_lang_VirtualThread::RUNNING, "wrong state for vthread");
  assert(!has_owner(current), "");

  if (node->is_wait() && !node->at_reenter()) {
    bool acquired_monitor = vthread_wait_reenter(current, node, cont);
    if (acquired_monitor) return true;
  }

  // Retry acquiring monitor...

  int state = node->TState;
  guarantee(state == ObjectWaiter::TS_ENTER, "invariant");

  if (try_lock(current) == TryLockResult::Success) {
    vthread_epilog(current, node);
    return true;
  }

  oop vthread = current->vthread();
  if (has_successor(current)) clear_successor();

  // Invariant: after clearing _succ a thread *must* retry acquiring the monitor.
  OrderAccess::fence();

  if (try_lock(current) == TryLockResult::Success) {
    vthread_epilog(current, node);
    return true;
  }

  // We will return to Continuation.run() and unmount so set the right state.
  java_lang_VirtualThread::set_state(vthread, java_lang_VirtualThread::BLOCKING);

  return false;
}

void ObjectMonitor::vthread_epilog(JavaThread* current, ObjectWaiter* node) {
  assert(has_owner(current), "invariant");
  add_to_contentions(-1);

  if (has_successor(current)) clear_successor();

  guarantee(_recursions == 0, "invariant");

  if (node->is_wait()) {
    _recursions = node->_recursions;   // restore the old recursion count
    _waiters--;                        // decrement the number of waiters

    if (node->_interrupted) {
      // We will throw at thaw end after finishing the mount transition.
      current->set_pending_interrupted_exception(true);
    }
  }

  unlink_after_acquire(current, node);
  delete node;

  // Clear the ObjectWaiter* from the vthread.
  java_lang_VirtualThread::set_objectWaiter(current->vthread(), nullptr);

  if (JvmtiExport::should_post_monitor_contended_entered()) {
    // We are going to call thaw again after this and finish the VMTS
    // transition so no need to do it here. We will post the event there.
    current->set_contended_entered_monitor(this);
  }
}

// Convert entry_list into a doubly linked list by assigning the prev
// pointers and the entry_list_tail pointer (if needed). Within the
// entry_list the next pointers always form a consistent singly linked
// list. When this function is called, the entry_list will be either
// singly linked, or starting as singly linked (at the head), but
// ending as doubly linked (at the tail).
void ObjectMonitor::entry_list_build_dll(JavaThread* current) {
  assert(has_owner(current), "invariant");
  ObjectWaiter* prev = nullptr;
  // Need acquire here to match the implicit release of the cmpxchg
  // that updated entry_list, so we can access w->prev().
  ObjectWaiter* w = Atomic::load_acquire(&_entry_list);
  assert(w != nullptr, "should only be called when entry list is not empty");
  while (w != nullptr) {
    assert(w->TState == ObjectWaiter::TS_ENTER, "invariant");
    assert(w->prev() == nullptr || w->prev() == prev, "invariant");
    if (w->prev() != nullptr) {
      break;
    }
    w->_prev = prev;
    prev = w;
    w = w->next();
  }
  if (w == nullptr) {
    // We converted the entire entry_list from a singly linked list
    // into a doubly linked list. Now we just need to set the tail
    // pointer.
    assert(prev != nullptr && prev->next() == nullptr, "invariant");
    assert(_entry_list_tail == nullptr || _entry_list_tail == prev, "invariant");
    _entry_list_tail = prev;
  } else {
#ifdef ASSERT
    // We stopped iterating through the _entry_list when we found a
    // node that had its prev pointer set. I.e. we converted the first
    // part of the entry_list from a singly linked list into a doubly
    // linked list. Now we just want to make sure the rest of the list
    // is doubly linked. But first we check that we have a tail
    // pointer, because if the end of the entry_list is doubly linked
    // and we don't have the tail pointer, something is broken.
    assert(_entry_list_tail != nullptr, "invariant");
    while (w != nullptr) {
      assert(w->TState == ObjectWaiter::TS_ENTER, "invariant");
      assert(w->prev() == prev, "invariant");
      prev = w;
      w = w->next();
    }
    assert(_entry_list_tail == prev, "invariant");
#endif
  }
}

// Return the tail of the _entry_list. If the tail is currently not
// known, it can be found by first calling entry_list_build_dll().
ObjectWaiter* ObjectMonitor::entry_list_tail(JavaThread* current) {
  assert(has_owner(current), "invariant");
  ObjectWaiter* w = _entry_list_tail;
  if (w != nullptr) {
    return w;
  }
  entry_list_build_dll(current);
  w = _entry_list_tail;
  assert(w != nullptr, "invariant");
  return w;
}

// By convention we unlink a contending thread from _entry_list
// immediately after the thread acquires the lock in ::enter().
// The head of _entry_list is volatile but the interior is stable.
// In addition, current.TState is stable.

void ObjectMonitor::unlink_after_acquire(JavaThread* current, ObjectWaiter* currentNode) {
  assert(has_owner(current), "invariant");
  assert((!currentNode->is_vthread() && currentNode->thread() == current) ||
         (currentNode->is_vthread() && currentNode->vthread() == current->vthread()), "invariant");

  // Check if we are unlinking the last element in the _entry_list.
  // This is by far the most common case.
  if (currentNode->next() == nullptr) {
    assert(_entry_list_tail == nullptr || _entry_list_tail == currentNode, "invariant");

    ObjectWaiter* w = Atomic::load(&_entry_list);
    if (w == currentNode) {
      // The currentNode is the only element in _entry_list.
      if (Atomic::cmpxchg(&_entry_list, w, (ObjectWaiter*)nullptr) == w) {
        _entry_list_tail = nullptr;
        currentNode->set_bad_pointers();
        return;
      }
      // The CAS above can fail from interference IFF a contending
      // thread "pushed" itself onto entry_list. So fall-through to
      // building the doubly linked list.
      assert(currentNode->prev() == nullptr, "invariant");
    }
    if (currentNode->prev() == nullptr) {
      // Build the doubly linked list to get hold of
      // currentNode->prev().
      entry_list_build_dll(current);
      assert(currentNode->prev() != nullptr, "must be");
      assert(_entry_list_tail == currentNode, "must be");
    }
    // The currentNode is the last element in _entry_list and we know
    // which element is the previous one.
    assert(_entry_list != currentNode, "invariant");
    _entry_list_tail = currentNode->prev();
    _entry_list_tail->_next = nullptr;
    currentNode->set_bad_pointers();
    return;
  }

  // If we get here it means the current thread enqueued itself on the
  // _entry_list but was then able to "steal" the lock before the
  // chosen successor was able to. Consequently currentNode must be an
  // interior node in the _entry_list, or the head.
  assert(currentNode->next() != nullptr, "invariant");
  assert(currentNode != _entry_list_tail, "invariant");

  // Check if we are in the singly linked portion of the
  // _entry_list. If we are the head then we try to remove ourselves,
  // else we convert to the doubly linked list.
  if (currentNode->prev() == nullptr) {
    ObjectWaiter* w = Atomic::load(&_entry_list);

    assert(w != nullptr, "invariant");
    if (w == currentNode) {
      ObjectWaiter* next = currentNode->next();
      // currentNode is at the head of _entry_list.
      if (Atomic::cmpxchg(&_entry_list, w, next) == w) {
        // The CAS above sucsessfully unlinked currentNode from the
        // head of the _entry_list.
        assert(_entry_list != w, "invariant");
        next->_prev = nullptr;
        currentNode->set_bad_pointers();
        return;
      } else {
        // The CAS above can fail from interference IFF a contending
        // thread "pushed" itself onto _entry_list, in which case
        // currentNode must now be in the interior of the
        // list. Fall-through to building the doubly linked list.
        assert(_entry_list != currentNode, "invariant");
      }
    }
    // Build the doubly linked list to get hold of currentNode->prev().
    entry_list_build_dll(current);
    assert(currentNode->prev() != nullptr, "must be");
  }

  // We now know we are unlinking currentNode from the interior of a
  // doubly linked list.
  assert(currentNode->next() != nullptr, "");
  assert(currentNode->prev() != nullptr, "");
  assert(currentNode != _entry_list, "");
  assert(currentNode != _entry_list_tail, "");

  ObjectWaiter* nxt = currentNode->next();
  ObjectWaiter* prv = currentNode->prev();
  assert(nxt->TState == ObjectWaiter::TS_ENTER, "invariant");
  assert(prv->TState == ObjectWaiter::TS_ENTER, "invariant");

  nxt->_prev = prv;
  prv->_next = nxt;
  currentNode->set_bad_pointers();
}

// -----------------------------------------------------------------------------
// Exit support
//
// exit()
// ~~~~~~
// Note that the collector can't reclaim the objectMonitor or deflate
// the object out from underneath the thread calling ::exit() as the
// thread calling ::exit() never transitions to a stable state.
// This inhibits GC, which in turn inhibits asynchronous (and
// inopportune) reclamation of "this".
//
// We'd like to assert that: (THREAD->thread_state() != _thread_blocked) ;
// There's one exception to the claim above, however.  enter_internal() can call
// exit() to drop a lock if the acquirer has been externally suspended.
// In that case exit() is called with _thread_state == _thread_blocked,
// but the monitor's _contentions field is > 0, which inhibits reclamation.
//
// This is the exit part of the locking protocol, often implemented in
// C2_MacroAssembler::fast_unlock()
//
//   1. A release barrier ensures that changes to monitor meta-data
//      (_succ, _entry_list) and data protected by the lock will be
//      visible before we release the lock.
//   2. Release the lock by clearing the owner.
//   3. A storeload MEMBAR is needed between releasing the owner and
//      subsequently reading meta-data to safely determine if the lock is
//      contended (step 4) without an elected successor (step 5).
//   4. If _entry_list is null, we are done, since there is no
//      other thread waiting on the lock to wake up. I.e. there is no
//      contention.
//   5. If there is a successor (_succ is non-null), we are done. The
//      responsibility for guaranteeing progress-liveness has now implicitly
//      been moved from the exiting thread to the successor.
//   6. There are waiters in the entry list (_entry_list is non-null),
//      but there is no successor (_succ is null), so we need to
//      wake up (unpark) a waiting thread to avoid stranding.
//
// Note that since only the current lock owner can manipulate the
// _entry_list (except for pushing new threads to the head), we need to
// reacquire the lock before we can wake up (unpark) a waiting thread.
//
// The CAS() in enter provides for safety and exclusion, while the
// MEMBAR in exit provides for progress and avoids stranding.
//
// There is also the risk of a futile wake-up. If we drop the lock
// another thread can reacquire the lock immediately, and we can
// then wake a thread unnecessarily. This is benign, and we've
// structured the code so the windows are short and the frequency
// of such futile wakups is low.

void ObjectMonitor::exit(JavaThread* current, bool not_suspended) {
  if (!has_owner(current)) {
    // Apparent unbalanced locking ...
    // Naively we'd like to throw IllegalMonitorStateException.
    // As a practical matter we can neither allocate nor throw an
    // exception as ::exit() can be called from leaf routines.
    // see x86_32.ad Fast_Unlock() and the I1 and I2 properties.
    // Upon deeper reflection, however, in a properly run JVM the only
    // way we should encounter this situation is in the presence of
    // unbalanced JNI locking. TODO: CheckJNICalls.
    // See also: CR4414101
#ifdef ASSERT
    LogStreamHandle(Error, monitorinflation) lsh;
    lsh.print_cr("ERROR: ObjectMonitor::exit(): thread=" INTPTR_FORMAT
                  " is exiting an ObjectMonitor it does not own.", p2i(current));
    lsh.print_cr("The imbalance is possibly caused by JNI locking.");
    print_debug_style_on(&lsh);
    assert(false, "Non-balanced monitor enter/exit!");
#endif
    return;
  }

  if (_recursions != 0) {
    _recursions--;        // this is simple recursive enter
    return;
  }

#if INCLUDE_JFR
  // get the owner's thread id for the MonitorEnter event
  // if it is enabled and the thread isn't suspended
  if (not_suspended && EventJavaMonitorEnter::is_enabled()) {
    _previous_owner_tid = JFR_THREAD_ID(current);
  }
#endif

  for (;;) {
    // If there is a successor we should release the lock as soon as
    // possible, so that the successor can acquire the lock. If there is
    // no successor, we might need to wake up a waiting thread.
    if (!has_successor()) {
      ObjectWaiter* w = Atomic::load(&_entry_list);
      if (w != nullptr) {
        // Other threads are blocked trying to acquire the lock and
        // there is no successor, so it appears that an heir-
        // presumptive (successor) must be made ready. Since threads
        // are woken up in FIFO order, we need to find the tail of the
        // entry_list.
        w = entry_list_tail(current);
        // I'd like to write: guarantee (w->_thread != current).
        // But in practice an exiting thread may find itself on the entry_list.
        // Let's say thread T1 calls O.wait().  Wait() enqueues T1 on O's waitset and
        // then calls exit().  Exit release the lock by setting O._owner to null.
        // Let's say T1 then stalls.  T2 acquires O and calls O.notify().  The
        // notify() operation moves T1 from O's waitset to O's entry_list. T2 then
        // release the lock "O".  T1 resumes immediately after the ST of null into
        // _owner, above.  T1 notices that the entry_list is populated, so it
        // reacquires the lock and then finds itself on the entry_list.
        // Given all that, we have to tolerate the circumstance where "w" is
        // associated with current.
        assert(w->TState == ObjectWaiter::TS_ENTER, "invariant");
        exit_epilog(current, w);
        return;
      }
    }

    // Drop the lock.
    // release semantics: prior loads and stores from within the critical section
    // must not float (reorder) past the following store that drops the lock.
    // Uses a storeload to separate release_store(owner) from the
    // successor check. The try_set_owner_from() below uses cmpxchg() so
    // we get the fence down there.
    release_clear_owner(current);
    OrderAccess::storeload();

    // Normally the exiting thread is responsible for ensuring succession,
    // but if this thread observes other successors are ready or other
    // entering threads are spinning after it has stored null into _owner
    // then it can exit without waking a successor.  The existence of
    // spinners or ready successors guarantees proper succession (liveness).
    // Responsibility passes to the ready or running successors.  The exiting
    // thread delegates the duty.  More precisely, if a successor already
    // exists this thread is absolved of the responsibility of waking
    // (unparking) one.

    // The _succ variable is critical to reducing futile wakeup frequency.
    // _succ identifies the "heir presumptive" thread that has been made
    // ready (unparked) but that has not yet run.  We need only one such
    // successor thread to guarantee progress.
    // See http://www.usenix.org/events/jvm01/full_papers/dice/dice.pdf
    // section 3.3 "Futile Wakeup Throttling" for details.
    //
    // Note that spinners in Enter() also set _succ non-null.
    // In the current implementation spinners opportunistically set
    // _succ so that exiting threads might avoid waking a successor.
    // Which means that the exiting thread could exit immediately without
    // waking a successor, if it observes a successor after it has dropped
    // the lock.  Note that the dropped lock needs to become visible to the
    // spinner.

    if (_entry_list == nullptr || has_successor()) {
      return;
    }

    // Only the current lock owner can manipulate the entry_list
    // (except for pushing new threads to the head), therefore we need
    // to reacquire the lock. If we fail to reacquire the lock the
    // responsibility for ensuring succession falls to the new owner.

    if (try_lock(current) != TryLockResult::Success) {
      // Some other thread acquired the lock (or the monitor was
      // deflated). Either way we are done.
      return;
    }

    guarantee(has_owner(current), "invariant");
  }
}

void ObjectMonitor::exit_epilog(JavaThread* current, ObjectWaiter* Wakee) {
  assert(has_owner(current), "invariant");

  // Exit protocol:
  // 1. ST _succ = wakee
  // 2. membar #loadstore|#storestore;
  // 2. ST _owner = nullptr
  // 3. unpark(wakee)

  oop vthread = nullptr;
  ParkEvent * Trigger;
  if (!Wakee->is_vthread()) {
    JavaThread* t = Wakee->thread();
    assert(t != nullptr, "");
    Trigger = t->_ParkEvent;
    set_successor(t);
  } else {
    vthread = Wakee->vthread();
    assert(vthread != nullptr, "");
    Trigger = ObjectMonitor::vthread_unparker_ParkEvent();
    set_successor(vthread);
  }

  // Hygiene -- once we've set _owner = nullptr we can't safely dereference Wakee again.
  // The thread associated with Wakee may have grabbed the lock and "Wakee" may be
  // out-of-scope (non-extant).
  Wakee  = nullptr;

  // Drop the lock.
  // Uses a fence to separate release_store(owner) from the LD in unpark().
  release_clear_owner(current);
  OrderAccess::fence();

  DTRACE_MONITOR_PROBE(contended__exit, this, object(), current);

  if (vthread == nullptr) {
    // Platform thread case.
    Trigger->unpark();
  } else if (java_lang_VirtualThread::set_onWaitingList(vthread, vthread_list_head())) {
    // Virtual thread case.
    Trigger->unpark();
  }
}

// Exits the monitor returning recursion count. _owner should
// be set to current's owner_id, i.e. no ANONYMOUS_OWNER allowed.
intx ObjectMonitor::complete_exit(JavaThread* current) {
  assert(InitDone, "Unexpectedly not initialized");
  guarantee(has_owner(current), "complete_exit not owner");

  intx save = _recursions; // record the old recursion count
  _recursions = 0;         // set the recursion level to be 0
  exit(current);           // exit the monitor
  guarantee(!has_owner(current), "invariant");
  return save;
}

// Checks that the current THREAD owns this monitor and causes an
// immediate return if it doesn't. We don't use the CHECK macro
// because we want the IMSE to be the only exception that is thrown
// from the call site when false is returned. Any other pending
// exception is ignored.
#define CHECK_OWNER()                                                  \
  do {                                                                 \
    if (!check_owner(THREAD)) {                                        \
       assert(HAS_PENDING_EXCEPTION, "expected a pending IMSE here."); \
       return;                                                         \
     }                                                                 \
  } while (false)

// Returns true if the specified thread owns the ObjectMonitor.
// Otherwise returns false and throws IllegalMonitorStateException
// (IMSE). If there is a pending exception and the specified thread
// is not the owner, that exception will be replaced by the IMSE.
bool ObjectMonitor::check_owner(TRAPS) {
  JavaThread* current = THREAD;
  int64_t cur = owner_raw();
  if (cur == owner_id_from(current)) {
    return true;
  }
  THROW_MSG_(vmSymbols::java_lang_IllegalMonitorStateException(),
             "current thread is not owner", false);
}

static void post_monitor_wait_event(EventJavaMonitorWait* event,
                                    ObjectMonitor* monitor,
                                    uint64_t notifier_tid,
                                    jlong timeout,
                                    bool timedout) {
  assert(event != nullptr, "invariant");
  assert(monitor != nullptr, "invariant");
  const Klass* monitor_klass = monitor->object()->klass();
  if (ObjectMonitor::is_jfr_excluded(monitor_klass)) {
    return;
  }
  event->set_monitorClass(monitor_klass);
  event->set_timeout(timeout);
  // Set an address that is 'unique enough', such that events close in
  // time and with the same address are likely (but not guaranteed) to
  // belong to the same object.
  event->set_address((uintptr_t)monitor);
  event->set_notifier(notifier_tid);
  event->set_timedOut(timedout);
  event->commit();
}

static void vthread_monitor_waited_event(JavaThread* current, ObjectWaiter* node, ContinuationWrapper& cont, EventJavaMonitorWait* event, jboolean timed_out) {
  // Since we might safepoint set the anchor so that the stack can we walked.
  assert(current->last_continuation() != nullptr, "");
  JavaFrameAnchor* anchor = current->frame_anchor();
  anchor->set_last_Java_sp(current->last_continuation()->entry_sp());
  anchor->set_last_Java_pc(current->last_continuation()->entry_pc());

  ContinuationWrapper::SafepointOp so(current, cont);

  JRT_BLOCK
    if (event->should_commit()) {
      long timeout = java_lang_VirtualThread::timeout(current->vthread());
      post_monitor_wait_event(event, node->_monitor, node->_notifier_tid, timeout, timed_out);
    }
    if (JvmtiExport::should_post_monitor_waited()) {
      // We mark this call in case of an upcall to Java while posting the event.
      // If somebody walks the stack in that case, processing the enterSpecial
      // frame should not include processing callee arguments since there is no
      // actual callee (see nmethod::preserve_callee_argument_oops()).
      ThreadOnMonitorWaitedEvent tmwe(current);
      JvmtiExport::vthread_post_monitor_waited(current, node->_monitor, timed_out);
    }
  JRT_BLOCK_END
  current->frame_anchor()->clear();
}

// -----------------------------------------------------------------------------
// Wait/Notify/NotifyAll
//
// Note: a subset of changes to ObjectMonitor::wait()
// will need to be replicated in complete_exit
void ObjectMonitor::wait(jlong millis, bool interruptible, TRAPS) {
  JavaThread* current = THREAD;

  assert(InitDone, "Unexpectedly not initialized");

  CHECK_OWNER();  // Throws IMSE if not owner.

  EventJavaMonitorWait wait_event;
  EventVirtualThreadPinned vthread_pinned_event;

  // check for a pending interrupt
  if (interruptible && current->is_interrupted(true) && !HAS_PENDING_EXCEPTION) {
    JavaThreadInObjectWaitState jtiows(current, millis != 0, interruptible);

    if (JvmtiExport::should_post_monitor_wait()) {
      JvmtiExport::post_monitor_wait(current, object(), millis);
    }
    // post monitor waited event.  Note that this is past-tense, we are done waiting.
    if (JvmtiExport::should_post_monitor_waited()) {
      // Note: 'false' parameter is passed here because the
      // wait was not timed out due to thread interrupt.
      JvmtiExport::post_monitor_waited(current, this, false);

      // In this short circuit of the monitor wait protocol, the
      // current thread never drops ownership of the monitor and
      // never gets added to the wait queue so the current thread
      // cannot be made the successor. This means that the
      // JVMTI_EVENT_MONITOR_WAITED event handler cannot accidentally
      // consume an unpark() meant for the ParkEvent associated with
      // this ObjectMonitor.
    }
    if (wait_event.should_commit()) {
      post_monitor_wait_event(&wait_event, this, 0, millis, false);
    }
    THROW(vmSymbols::java_lang_InterruptedException());
    return;
  }

  freeze_result result;
  ContinuationEntry* ce = current->last_continuation();
  bool is_virtual = ce != nullptr && ce->is_virtual_thread();
  if (is_virtual) {
    if (interruptible && JvmtiExport::should_post_monitor_wait()) {
      JvmtiExport::post_monitor_wait(current, object(), millis);
    }
    current->set_current_waiting_monitor(this);
    result = Continuation::try_preempt(current, ce->cont_oop(current));
    if (result == freeze_ok) {
      vthread_wait(current, millis);
      current->set_current_waiting_monitor(nullptr);
      return;
    }
  }
  // The jtiows does nothing for non-interruptible.
  JavaThreadInObjectWaitState jtiows(current, millis != 0, interruptible);

  if (!is_virtual) { // it was already set for virtual thread
    if (interruptible && JvmtiExport::should_post_monitor_wait()) {
      JvmtiExport::post_monitor_wait(current, object(), millis);

      // The current thread already owns the monitor and it has not yet
      // been added to the wait queue so the current thread cannot be
      // made the successor. This means that the JVMTI_EVENT_MONITOR_WAIT
      // event handler cannot accidentally consume an unpark() meant for
      // the ParkEvent associated with this ObjectMonitor.
    }
    current->set_current_waiting_monitor(this);
  }
  // create a node to be put into the queue
  // Critically, after we reset() the event but prior to park(), we must check
  // for a pending interrupt.
  ObjectWaiter node(current);
  node.TState = ObjectWaiter::TS_WAIT;
  current->_ParkEvent->reset();
  OrderAccess::fence();          // ST into Event; membar ; LD interrupted-flag

  // Enter the waiting queue, which is a circular doubly linked list in this case
  // but it could be a priority queue or any data structure.
  // _wait_set_lock protects the wait queue.  Normally the wait queue is accessed only
  // by the owner of the monitor *except* in the case where park()
  // returns because of a timeout of interrupt.  Contention is exceptionally rare
  // so we use a simple spin-lock instead of a heavier-weight blocking lock.

  Thread::SpinAcquire(&_wait_set_lock);
  add_waiter(&node);
  Thread::SpinRelease(&_wait_set_lock);

  intx save = _recursions;     // record the old recursion count
  _waiters++;                  // increment the number of waiters
  _recursions = 0;             // set the recursion level to be 1
  exit(current);               // exit the monitor
  guarantee(!has_owner(current), "invariant");

  // The thread is on the wait_set list - now park() it.
  // On MP systems it's conceivable that a brief spin before we park
  // could be profitable.
  //
  // TODO-FIXME: change the following logic to a loop of the form
  //   while (!timeout && !interrupted && _notified == 0) park()

  int ret = OS_OK;
  int WasNotified = 0;

  // Need to check interrupt state whilst still _thread_in_vm
  bool interrupted = interruptible && current->is_interrupted(false);

  { // State transition wrappers
    OSThread* osthread = current->osthread();
    OSThreadWaitState osts(osthread, true);

    assert(current->thread_state() == _thread_in_vm, "invariant");

    {
      ClearSuccOnSuspend csos(this);
      ThreadBlockInVMPreprocess<ClearSuccOnSuspend> tbivs(current, csos, true /* allow_suspend */);
      if (interrupted || HAS_PENDING_EXCEPTION) {
        // Intentionally empty
      } else if (!node._notified) {
        if (millis <= 0) {
          current->_ParkEvent->park();
        } else {
          ret = current->_ParkEvent->park(millis);
        }
      }
    }

    // Node may be on the wait_set, or on the entry_list, or in transition
    // from the wait_set to the entry_list.
    // See if we need to remove Node from the wait_set.
    // We use double-checked locking to avoid grabbing _wait_set_lock
    // if the thread is not on the wait queue.
    //
    // Note that we don't need a fence before the fetch of TState.
    // In the worst case we'll fetch a old-stale value of TS_WAIT previously
    // written by the is thread. (perhaps the fetch might even be satisfied
    // by a look-aside into the processor's own store buffer, although given
    // the length of the code path between the prior ST and this load that's
    // highly unlikely).  If the following LD fetches a stale TS_WAIT value
    // then we'll acquire the lock and then re-fetch a fresh TState value.
    // That is, we fail toward safety.

    if (node.TState == ObjectWaiter::TS_WAIT) {
      Thread::SpinAcquire(&_wait_set_lock);
      if (node.TState == ObjectWaiter::TS_WAIT) {
        dequeue_specific_waiter(&node);       // unlink from wait_set
        assert(!node._notified, "invariant");
        node.TState = ObjectWaiter::TS_RUN;
      }
      Thread::SpinRelease(&_wait_set_lock);
    }

    // The thread is now either on off-list (TS_RUN),
    // or on the entry_list (TS_ENTER).
    // The Node's TState variable is stable from the perspective of this thread.
    // No other threads will asynchronously modify TState.
    guarantee(node.TState != ObjectWaiter::TS_WAIT, "invariant");
    OrderAccess::loadload();
    if (has_successor(current)) clear_successor();
    WasNotified = node._notified;

    // Reentry phase -- reacquire the monitor.
    // re-enter contended monitor after object.wait().
    // retain OBJECT_WAIT state until re-enter successfully completes
    // Thread state is thread_in_vm and oop access is again safe,
    // although the raw address of the object may have changed.
    // (Don't cache naked oops over safepoints, of course).

    // post monitor waited event. Note that this is past-tense, we are done waiting.
    if (JvmtiExport::should_post_monitor_waited()) {
      JvmtiExport::post_monitor_waited(current, this, ret == OS_TIMEOUT);

      if (node._notified && has_successor(current)) {
        // In this part of the monitor wait-notify-reenter protocol it
        // is possible (and normal) for another thread to do a fastpath
        // monitor enter-exit while this thread is still trying to get
        // to the reenter portion of the protocol.
        //
        // The ObjectMonitor was notified and the current thread is
        // the successor which also means that an unpark() has already
        // been done. The JVMTI_EVENT_MONITOR_WAITED event handler can
        // consume the unpark() that was done when the successor was
        // set because the same ParkEvent is shared between Java
        // monitors and JVM/TI RawMonitors (for now).
        //
        // We redo the unpark() to ensure forward progress, i.e., we
        // don't want all pending threads hanging (parked) with none
        // entering the unlocked monitor.
        current->_ParkEvent->unpark();
      }
    }

    if (wait_event.should_commit()) {
      post_monitor_wait_event(&wait_event, this, node._notifier_tid, millis, ret == OS_TIMEOUT);
    }

    OrderAccess::fence();

    assert(!has_owner(current), "invariant");
    ObjectWaiter::TStates v = node.TState;
    if (v == ObjectWaiter::TS_RUN) {
      // We use the NoPreemptMark for the very rare case where the previous
      // preempt attempt failed due to OOM. The preempt on monitor contention
      // could succeed but we can't unmount now.
      NoPreemptMark npm(current);
      enter(current);
    } else {
      guarantee(v == ObjectWaiter::TS_ENTER, "invariant");
      reenter_internal(current, &node);
      node.wait_reenter_end(this);
    }

    // current has reacquired the lock.
    // Lifecycle - the node representing current must not appear on any queues.
    // Node is about to go out-of-scope, but even if it were immortal we wouldn't
    // want residual elements associated with this thread left on any lists.
    guarantee(node.TState == ObjectWaiter::TS_RUN, "invariant");
    assert(has_owner(current), "invariant");
    assert(!has_successor(current), "invariant");
  } // OSThreadWaitState()

  current->set_current_waiting_monitor(nullptr);

  guarantee(_recursions == 0, "invariant");
  int relock_count = JvmtiDeferredUpdates::get_and_reset_relock_count_after_wait(current);
  _recursions =   save          // restore the old recursion count
                + relock_count; //  increased by the deferred relock count
  current->inc_held_monitor_count(relock_count); // Deopt never entered these counts.
  _waiters--;             // decrement the number of waiters

  // Verify a few postconditions
  assert(has_owner(current), "invariant");
  assert(!has_successor(current), "invariant");
  assert_mark_word_consistency();

  if (ce != nullptr && ce->is_virtual_thread()) {
    current->post_vthread_pinned_event(&vthread_pinned_event, "Object.wait", result);
  }

  // check if the notification happened
  if (!WasNotified) {
    // no, it could be timeout or Thread.interrupt() or both
    // check for interrupt event, otherwise it is timeout
    if (interruptible && current->is_interrupted(true) && !HAS_PENDING_EXCEPTION) {
      THROW(vmSymbols::java_lang_InterruptedException());
    }
  }

  // NOTE: Spurious wake up will be consider as timeout.
  // Monitor notify has precedence over thread interrupt.
}

// Consider:
// If the lock is cool (entry_list == null && succ == null) and we're on an MP system
// then instead of transferring a thread from the wait_set to the entry_list
// we might just dequeue a thread from the wait_set and directly unpark() it.

bool ObjectMonitor::notify_internal(JavaThread* current) {
  bool did_notify = false;
  Thread::SpinAcquire(&_wait_set_lock);
  ObjectWaiter* iterator = dequeue_waiter();
  if (iterator != nullptr) {
    guarantee(iterator->TState == ObjectWaiter::TS_WAIT, "invariant");
    guarantee(!iterator->_notified, "invariant");

    if (iterator->is_vthread()) {
      oop vthread = iterator->vthread();
      java_lang_VirtualThread::set_notified(vthread, true);
      int old_state = java_lang_VirtualThread::state(vthread);
      // If state is not WAIT/TIMED_WAIT then target could still be on
      // unmount transition, or wait could have already timed-out or target
      // could have been interrupted. In the first case, the target itself
      // will set the state to BLOCKED at the end of the unmount transition.
      // In the other cases the target would have been already unblocked so
      // there is nothing to do.
      if (old_state == java_lang_VirtualThread::WAIT ||
          old_state == java_lang_VirtualThread::TIMED_WAIT) {
        java_lang_VirtualThread::cmpxchg_state(vthread, old_state, java_lang_VirtualThread::BLOCKED);
      }
    }

    iterator->_notified = true;
    iterator->_notifier_tid = JFR_THREAD_ID(current);
    did_notify = true;
    add_to_entry_list(current, iterator);

    // _wait_set_lock protects the wait queue, not the entry_list.  We could
    // move the add-to-entry_list operation, above, outside the critical section
    // protected by _wait_set_lock.  In practice that's not useful.  With the
    // exception of  wait() timeouts and interrupts the monitor owner
    // is the only thread that grabs _wait_set_lock.  There's almost no contention
    // on _wait_set_lock so it's not profitable to reduce the length of the
    // critical section.

    if (!iterator->is_vthread()) {
      iterator->wait_reenter_begin(this);
    }
  }
  Thread::SpinRelease(&_wait_set_lock);
  return did_notify;
}

static void post_monitor_notify_event(EventJavaMonitorNotify* event,
                                      ObjectMonitor* monitor,
                                      int notified_count) {
  assert(event != nullptr, "invariant");
  assert(monitor != nullptr, "invariant");
  const Klass* monitor_klass = monitor->object()->klass();
  if (ObjectMonitor::is_jfr_excluded(monitor_klass)) {
    return;
  }
  event->set_monitorClass(monitor_klass);
  // Set an address that is 'unique enough', such that events close in
  // time and with the same address are likely (but not guaranteed) to
  // belong to the same object.
  event->set_address((uintptr_t)monitor);
  event->set_notifiedCount(notified_count);
  event->commit();
}

// Consider: a not-uncommon synchronization bug is to use notify() when
// notifyAll() is more appropriate, potentially resulting in stranded
// threads; this is one example of a lost wakeup. A useful diagnostic
// option is to force all notify() operations to behave as notifyAll().
//
// Note: We can also detect many such problems with a "minimum wait".
// When the "minimum wait" is set to a small non-zero timeout value
// and the program does not hang whereas it did absent "minimum wait",
// that suggests a lost wakeup bug.

void ObjectMonitor::notify(TRAPS) {
  JavaThread* current = THREAD;
  CHECK_OWNER();  // Throws IMSE if not owner.
  if (_wait_set == nullptr) {
    return;
  }

  quick_notify(current);
}

void ObjectMonitor::quick_notify(JavaThread* current) {
  assert(has_owner(current), "Precondition");

  EventJavaMonitorNotify event;
  DTRACE_MONITOR_PROBE(notify, this, object(), current);
  int tally = notify_internal(current) ? 1 : 0;

  if ((tally > 0) && event.should_commit()) {
    post_monitor_notify_event(&event, this, /* notified_count = */ tally);
  }
}

// notifyAll() transfers the waiters one-at-a-time from the waitset to
// the entry_list. If the waitset is "ABCD" (where A was added first
// and D last) and the entry_list is ->X->Y->Z. After a notifyAll()
// the waitset will be empty and the entry_list will be
// ->D->C->B->A->X->Y->Z, and the next choosen successor will be Z.

void ObjectMonitor::notifyAll(TRAPS) {
  JavaThread* current = THREAD;
  CHECK_OWNER();  // Throws IMSE if not owner.
  if (_wait_set == nullptr) {
    return;
  }

  quick_notifyAll(current);
}

void ObjectMonitor::quick_notifyAll(JavaThread* current) {
  assert(has_owner(current), "Precondition");

  EventJavaMonitorNotify event;
  DTRACE_MONITOR_PROBE(notifyAll, this, object(), current);
  int tally = 0;
  while (_wait_set != nullptr) {
    if (notify_internal(current)) {
      tally++;
    }
  }

  if ((tally > 0) && event.should_commit()) {
    post_monitor_notify_event(&event, this, /* notified_count = */ tally);
  }
}

void ObjectMonitor::vthread_wait(JavaThread* current, jlong millis) {
  oop vthread = current->vthread();
  ObjectWaiter* node = new ObjectWaiter(vthread, this);
  node->_is_wait = true;
  node->TState = ObjectWaiter::TS_WAIT;
  java_lang_VirtualThread::set_notified(vthread, false);  // Reset notified flag

  // Enter the waiting queue, which is a circular doubly linked list in this case
  // but it could be a priority queue or any data structure.
  // _wait_set_lock protects the wait queue.  Normally the wait queue is accessed only
  // by the owner of the monitor *except* in the case where park()
  // returns because of a timeout or interrupt.  Contention is exceptionally rare
  // so we use a simple spin-lock instead of a heavier-weight blocking lock.

  Thread::SpinAcquire(&_wait_set_lock);
  add_waiter(node);
  Thread::SpinRelease(&_wait_set_lock);

  node->_recursions = _recursions;   // record the old recursion count
  _recursions = 0;                   // set the recursion level to be 0
  _waiters++;                        // increment the number of waiters
  exit(current);                     // exit the monitor
  guarantee(!has_owner(current), "invariant");

  assert(java_lang_VirtualThread::state(vthread) == java_lang_VirtualThread::RUNNING, "wrong state for vthread");
  java_lang_VirtualThread::set_state(vthread, millis == 0 ? java_lang_VirtualThread::WAITING : java_lang_VirtualThread::TIMED_WAITING);
  java_lang_VirtualThread::set_timeout(vthread, millis);

  // Save the ObjectWaiter* in the vthread since we will need it when resuming execution.
  java_lang_VirtualThread::set_objectWaiter(vthread, node);
}

bool ObjectMonitor::vthread_wait_reenter(JavaThread* current, ObjectWaiter* node, ContinuationWrapper& cont) {
  // The first time we run after being preempted on Object.wait() we
  // need to check if we were interrupted or the wait timed-out, and
  // in that case remove ourselves from the _wait_set queue.
  if (node->TState == ObjectWaiter::TS_WAIT) {
    Thread::SpinAcquire(&_wait_set_lock);
    if (node->TState == ObjectWaiter::TS_WAIT) {
      dequeue_specific_waiter(node);       // unlink from wait_set
      assert(!node->_notified, "invariant");
      node->TState = ObjectWaiter::TS_RUN;
    }
    Thread::SpinRelease(&_wait_set_lock);
  }

  // If this was an interrupted case, set the _interrupted boolean so that
  // once we re-acquire the monitor we know if we need to throw IE or not.
  ObjectWaiter::TStates state = node->TState;
  bool was_notified = state == ObjectWaiter::TS_ENTER;
  assert(was_notified || state == ObjectWaiter::TS_RUN, "");
  node->_interrupted = !was_notified && current->is_interrupted(false);

  // Post JFR and JVMTI events.
  EventJavaMonitorWait wait_event;
  if (wait_event.should_commit() || JvmtiExport::should_post_monitor_waited()) {
    vthread_monitor_waited_event(current, node, cont, &wait_event, !was_notified && !node->_interrupted);
  }

  // Mark that we are at reenter so that we don't call this method again.
  node->_at_reenter = true;

  if (!was_notified) {
    bool acquired = vthread_monitor_enter(current, node);
    if (acquired) {
      guarantee(_recursions == 0, "invariant");
      _recursions = node->_recursions;   // restore the old recursion count
      _waiters--;                        // decrement the number of waiters

      if (node->_interrupted) {
        // We will throw at thaw end after finishing the mount transition.
        current->set_pending_interrupted_exception(true);
      }

      delete node;
      // Clear the ObjectWaiter* from the vthread.
      java_lang_VirtualThread::set_objectWaiter(current->vthread(), nullptr);
      return true;
    }
  } else {
    // Already moved to _entry_list by notifier, so just add to contentions.
    add_to_contentions(1);
  }
  return false;
}

// -----------------------------------------------------------------------------
// Adaptive Spinning Support
//
// Adaptive spin-then-block - rational spinning
//
// Note that we spin "globally" on _owner with a classic SMP-polite TATAS
// algorithm.
//
// Broadly, we can fix the spin frequency -- that is, the % of contended lock
// acquisition attempts where we opt to spin --  at 100% and vary the spin count
// (duration) or we can fix the count at approximately the duration of
// a context switch and vary the frequency.   Of course we could also
// vary both satisfying K == Frequency * Duration, where K is adaptive by monitor.
// For a description of 'Adaptive spin-then-block mutual exclusion in
// multi-threaded processing,' see U.S. Pat. No. 8046758.
//
// This implementation varies the duration "D", where D varies with
// the success rate of recent spin attempts. (D is capped at approximately
// length of a round-trip context switch).  The success rate for recent
// spin attempts is a good predictor of the success rate of future spin
// attempts.  The mechanism adapts automatically to varying critical
// section length (lock modality), system load and degree of parallelism.
// D is maintained per-monitor in _SpinDuration and is initialized
// optimistically.  Spin frequency is fixed at 100%.
//
// Note that _SpinDuration is volatile, but we update it without locks
// or atomics.  The code is designed so that _SpinDuration stays within
// a reasonable range even in the presence of races.  The arithmetic
// operations on _SpinDuration are closed over the domain of legal values,
// so at worst a race will install and older but still legal value.
// At the very worst this introduces some apparent non-determinism.
// We might spin when we shouldn't or vice-versa, but since the spin
// count are relatively short, even in the worst case, the effect is harmless.
//
// Care must be taken that a low "D" value does not become an
// an absorbing state.  Transient spinning failures -- when spinning
// is overall profitable -- should not cause the system to converge
// on low "D" values.  We want spinning to be stable and predictable
// and fairly responsive to change and at the same time we don't want
// it to oscillate, become metastable, be "too" non-deterministic,
// or converge on or enter undesirable stable absorbing states.
//
// We implement a feedback-based control system -- using past behavior
// to predict future behavior.  We face two issues: (a) if the
// input signal is random then the spin predictor won't provide optimal
// results, and (b) if the signal frequency is too high then the control
// system, which has some natural response lag, will "chase" the signal.
// (b) can arise from multimodal lock hold times.  Transient preemption
// can also result in apparent bimodal lock hold times.
// Although sub-optimal, neither condition is particularly harmful, as
// in the worst-case we'll spin when we shouldn't or vice-versa.
// The maximum spin duration is rather short so the failure modes aren't bad.
// To be conservative, I've tuned the gain in system to bias toward
// _not spinning.  Relatedly, the system can sometimes enter a mode where it
// "rings" or oscillates between spinning and not spinning.  This happens
// when spinning is just on the cusp of profitability, however, so the
// situation is not dire.  The state is benign -- there's no need to add
// hysteresis control to damp the transition rate between spinning and
// not spinning.

int ObjectMonitor::Knob_SpinLimit    = 5000;   // derived by an external tool

static int Knob_Bonus               = 100;     // spin success bonus
static int Knob_Penalty             = 200;     // spin failure penalty
static int Knob_Poverty             = 1000;
static int Knob_FixedSpin           = 0;
static int Knob_PreSpin             = 10;      // 20-100 likely better, but it's not better in my testing.

inline static int adjust_up(int spin_duration) {
  int x = spin_duration;
  if (x < ObjectMonitor::Knob_SpinLimit) {
    if (x < Knob_Poverty) {
      x = Knob_Poverty;
    }
    return x + Knob_Bonus;
  } else {
    return spin_duration;
  }
}

inline static int adjust_down(int spin_duration) {
  // TODO: Use an AIMD-like policy to adjust _SpinDuration.
  // AIMD is globally stable.
  int x = spin_duration;
  if (x > 0) {
    // Consider an AIMD scheme like: x -= (x >> 3) + 100
    // This is globally sample and tends to damp the response.
    x -= Knob_Penalty;
    if (x < 0) { x = 0; }
    return x;
  } else {
    return spin_duration;
  }
}

bool ObjectMonitor::short_fixed_spin(JavaThread* current, int spin_count, bool adapt) {
  for (int ctr = 0; ctr < spin_count; ctr++) {
    TryLockResult status = try_lock(current);
    if (status == TryLockResult::Success) {
      if (adapt) {
        _SpinDuration = adjust_up(_SpinDuration);
      }
      return true;
    } else if (status == TryLockResult::Interference) {
      break;
    }
    SpinPause();
  }
  return false;
}

// Spinning: Fixed frequency (100%), vary duration
bool ObjectMonitor::try_spin(JavaThread* current) {

  // Dumb, brutal spin.  Good for comparative measurements against adaptive spinning.
  int knob_fixed_spin = Knob_FixedSpin;  // 0 (don't spin: default), 2000 good test
  if (knob_fixed_spin > 0) {
    return short_fixed_spin(current, knob_fixed_spin, false);
  }

  // Admission control - verify preconditions for spinning
  //
  // We always spin a little bit, just to prevent _SpinDuration == 0 from
  // becoming an absorbing state.  Put another way, we spin briefly to
  // sample, just in case the system load, parallelism, contention, or lock
  // modality changed.

  int knob_pre_spin = Knob_PreSpin; // 10 (default), 100, 1000 or 2000
  if (short_fixed_spin(current, knob_pre_spin, true)) {
    return true;
  }

  //
  // Consider the following alternative:
  // Periodically set _SpinDuration = _SpinLimit and try a long/full
  // spin attempt.  "Periodically" might mean after a tally of
  // the # of failed spin attempts (or iterations) reaches some threshold.
  // This takes us into the realm of 1-out-of-N spinning, where we
  // hold the duration constant but vary the frequency.

  int ctr = _SpinDuration;
  if (ctr <= 0) return false;

  // We're good to spin ... spin ingress.
  // CONSIDER: use Prefetch::write() to avoid RTS->RTO upgrades
  // when preparing to LD...CAS _owner, etc and the CAS is likely
  // to succeed.
  if (!has_successor()) {
    set_successor(current);
  }
  int64_t prv = NO_OWNER;

  // There are three ways to exit the following loop:
  // 1.  A successful spin where this thread has acquired the lock.
  // 2.  Spin failure with prejudice
  // 3.  Spin failure without prejudice

  while (--ctr >= 0) {

    // Periodic polling -- Check for pending GC
    // Threads may spin while they're unsafe.
    // We don't want spinning threads to delay the JVM from reaching
    // a stop-the-world safepoint or to steal cycles from GC.
    // If we detect a pending safepoint we abort in order that
    // (a) this thread, if unsafe, doesn't delay the safepoint, and (b)
    // this thread, if safe, doesn't steal cycles from GC.
    // This is in keeping with the "no loitering in runtime" rule.
    // We periodically check to see if there's a safepoint pending.
    if ((ctr & 0xFF) == 0) {
      // Can't call SafepointMechanism::should_process() since that
      // might update the poll values and we could be in a thread_blocked
      // state here which is not allowed so just check the poll.
      if (SafepointMechanism::local_poll_armed(current)) {
        break;
      }
      SpinPause();
    }

    // Probe _owner with TATAS
    // If this thread observes the monitor transition or flicker
    // from locked to unlocked to locked, then the odds that this
    // thread will acquire the lock in this spin attempt go down
    // considerably.  The same argument applies if the CAS fails
    // or if we observe _owner change from one non-null value to
    // another non-null value.   In such cases we might abort
    // the spin without prejudice or apply a "penalty" to the
    // spin count-down variable "ctr", reducing it by 100, say.

    int64_t ox = owner_raw();
    if (ox == NO_OWNER) {
      ox = try_set_owner_from(NO_OWNER, current);
      if (ox == NO_OWNER) {
        // The CAS succeeded -- this thread acquired ownership
        // Take care of some bookkeeping to exit spin state.
        if (has_successor(current)) {
          clear_successor();
        }

        // Increase _SpinDuration :
        // The spin was successful (profitable) so we tend toward
        // longer spin attempts in the future.
        // CONSIDER: factor "ctr" into the _SpinDuration adjustment.
        // If we acquired the lock early in the spin cycle it
        // makes sense to increase _SpinDuration proportionally.
        // Note that we don't clamp SpinDuration precisely at SpinLimit.
        _SpinDuration = adjust_up(_SpinDuration);
        return true;
      }

      // The CAS failed ... we can take any of the following actions:
      // * penalize: ctr -= CASPenalty
      // * exit spin with prejudice -- abort without adapting spinner
      // * exit spin without prejudice.
      // * Since CAS is high-latency, retry again immediately.
      break;
    }

    // Did lock ownership change hands ?
    if (ox != prv && prv != NO_OWNER) {
      break;
    }
    prv = ox;

    if (!has_successor()) {
      set_successor(current);
    }
  }

  // Spin failed with prejudice -- reduce _SpinDuration.
  if (ctr < 0) {
    _SpinDuration = adjust_down(_SpinDuration);
  }

  if (has_successor(current)) {
    clear_successor();
    // Invariant: after setting succ=null a contending thread
    // must recheck-retry _owner before parking.  This usually happens
    // in the normal usage of try_spin(), but it's safest
    // to make try_spin() as foolproof as possible.
    OrderAccess::fence();
    if (try_lock(current) == TryLockResult::Success) {
      return true;
    }
  }

  return false;
}


// -----------------------------------------------------------------------------
// wait_set management ...

ObjectWaiter::ObjectWaiter(JavaThread* current) {
  _next     = nullptr;
  _prev     = nullptr;
  _thread   = current;
  _monitor  = nullptr;
  _notifier_tid = 0;
  _recursions = 0;
  TState    = TS_RUN;
  _notified = false;
  _is_wait  = false;
  _at_reenter = false;
  _interrupted = false;
  _active   = false;
}

ObjectWaiter::ObjectWaiter(oop vthread, ObjectMonitor* mon) : ObjectWaiter(nullptr) {
  assert(oopDesc::is_oop(vthread), "");
  _vthread = OopHandle(JavaThread::thread_oop_storage(), vthread);
  _monitor = mon;
}

ObjectWaiter::~ObjectWaiter() {
  if (is_vthread()) {
    assert(vthread() != nullptr, "");
    _vthread.release(JavaThread::thread_oop_storage());
  }
}

oop ObjectWaiter::vthread() const {
  return _vthread.resolve();
}

void ObjectWaiter::wait_reenter_begin(ObjectMonitor * const mon) {
  _active = JavaThreadBlockedOnMonitorEnterState::wait_reenter_begin(_thread, mon);
}

void ObjectWaiter::wait_reenter_end(ObjectMonitor * const mon) {
  JavaThreadBlockedOnMonitorEnterState::wait_reenter_end(_thread, _active);
}

inline void ObjectMonitor::add_waiter(ObjectWaiter* node) {
  assert(node != nullptr, "should not add null node");
  assert(node->_prev == nullptr, "node already in list");
  assert(node->_next == nullptr, "node already in list");
  // put node at end of queue (circular doubly linked list)
  if (_wait_set == nullptr) {
    _wait_set = node;
    node->_prev = node;
    node->_next = node;
  } else {
    ObjectWaiter* head = _wait_set;
    ObjectWaiter* tail = head->_prev;
    assert(tail->_next == head, "invariant check");
    tail->_next = node;
    head->_prev = node;
    node->_next = head;
    node->_prev = tail;
  }
}

inline ObjectWaiter* ObjectMonitor::dequeue_waiter() {
  // dequeue the very first waiter
  ObjectWaiter* waiter = _wait_set;
  if (waiter) {
    dequeue_specific_waiter(waiter);
  }
  return waiter;
}

inline void ObjectMonitor::dequeue_specific_waiter(ObjectWaiter* node) {
  assert(node != nullptr, "should not dequeue nullptr node");
  assert(node->_prev != nullptr, "node already removed from list");
  assert(node->_next != nullptr, "node already removed from list");
  // when the waiter has woken up because of interrupt,
  // timeout or other spurious wake-up, dequeue the
  // waiter from waiting list
  ObjectWaiter* next = node->_next;
  if (next == node) {
    assert(node->_prev == node, "invariant check");
    _wait_set = nullptr;
  } else {
    ObjectWaiter* prev = node->_prev;
    assert(prev->_next == node, "invariant check");
    assert(next->_prev == node, "invariant check");
    next->_prev = prev;
    prev->_next = next;
    if (_wait_set == node) {
      _wait_set = next;
    }
  }
  node->_next = nullptr;
  node->_prev = nullptr;
}

// -----------------------------------------------------------------------------

// One-shot global initialization for the sync subsystem.
// We could also defer initialization and initialize on-demand
// the first time we call ObjectSynchronizer::inflate().
// Initialization would be protected - like so many things - by
// the MonitorCache_lock.

void ObjectMonitor::Initialize() {
  assert(!InitDone, "invariant");

  if (!os::is_MP()) {
    Knob_SpinLimit = 0;
    Knob_PreSpin   = 0;
    Knob_FixedSpin = -1;
  }

  _oop_storage = OopStorageSet::create_weak("ObjectSynchronizer Weak", mtSynchronizer);

  DEBUG_ONLY(InitDone = true;)
}

// We can't call this during Initialize() because BarrierSet needs to be set.
void ObjectMonitor::Initialize2() {
  _vthread_list_head = OopHandle(JavaThread::thread_oop_storage(), nullptr);
  _vthread_unparker_ParkEvent = ParkEvent::Allocate(nullptr);
}

void ObjectMonitor::print_on(outputStream* st) const {
  // The minimal things to print for markWord printing, more can be added for debugging and logging.
  st->print("{contentions=0x%08x,waiters=0x%08x"
            ",recursions=%zd,owner=" INT64_FORMAT "}",
            contentions(), waiters(), recursions(),
            owner_raw());
}
void ObjectMonitor::print() const { print_on(tty); }

#ifdef ASSERT
// Print the ObjectMonitor like a debugger would:
//
// (ObjectMonitor) 0x00007fdfb6012e40 = {
//   _metadata = 0x0000000000000001
//   _object = 0x000000070ff45fd0
//   _pad_buf0 = {
//     [0] = '\0'
//     ...
//     [43] = '\0'
//   }
//   _owner = 0x0000000000000000
//   _previous_owner_tid = 0
//   _pad_buf1 = {
//     [0] = '\0'
//     ...
//     [47] = '\0'
//   }
//   _next_om = 0x0000000000000000
//   _recursions = 0
//   _entry_list = 0x0000000000000000
//   _entry_list_tail = 0x0000000000000000
//   _succ = 0x0000000000000000
//   _SpinDuration = 5000
//   _contentions = 0
//   _wait_set = 0x0000700009756248
//   _waiters = 1
//   _wait_set_lock = 0
// }
//
void ObjectMonitor::print_debug_style_on(outputStream* st) const {
  st->print_cr("(ObjectMonitor*) " INTPTR_FORMAT " = {", p2i(this));
  st->print_cr("  _metadata = " INTPTR_FORMAT, _metadata);
  st->print_cr("  _object = " INTPTR_FORMAT, p2i(object_peek()));
  st->print_cr("  _pad_buf0 = {");
  st->print_cr("    [0] = '\\0'");
  st->print_cr("    ...");
  st->print_cr("    [%d] = '\\0'", (int)sizeof(_pad_buf0) - 1);
  st->print_cr("  }");
  st->print_cr("  _owner = " INT64_FORMAT, owner_raw());
  st->print_cr("  _previous_owner_tid = " UINT64_FORMAT, _previous_owner_tid);
  st->print_cr("  _pad_buf1 = {");
  st->print_cr("    [0] = '\\0'");
  st->print_cr("    ...");
  st->print_cr("    [%d] = '\\0'", (int)sizeof(_pad_buf1) - 1);
  st->print_cr("  }");
  st->print_cr("  _next_om = " INTPTR_FORMAT, p2i(next_om()));
  st->print_cr("  _recursions = %zd", _recursions);
  st->print_cr("  _entry_list = " INTPTR_FORMAT, p2i(_entry_list));
  st->print_cr("  _entry_list_tail = " INTPTR_FORMAT, p2i(_entry_list_tail));
  st->print_cr("  _succ = " INT64_FORMAT, successor());
  st->print_cr("  _SpinDuration = %d", _SpinDuration);
  st->print_cr("  _contentions = %d", contentions());
  st->print_cr("  _wait_set = " INTPTR_FORMAT, p2i(_wait_set));
  st->print_cr("  _waiters = %d", _waiters);
  st->print_cr("  _wait_set_lock = %d", _wait_set_lock);
  st->print_cr("}");
}
#endif
