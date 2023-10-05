/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/vmSymbols.hpp"
#include "gc/shared/oopStorage.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/support/jfrThreadId.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/weakHandle.inline.hpp"
#include "prims/jvmtiDeferredUpdates.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/handshake.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/osThread.hpp"
#include "runtime/perfData.hpp"
#include "runtime/safefetch.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "services/threadService.hpp"
#include "utilities/dtrace.hpp"
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

// Tunables ...
// The knob* variables are effectively final.  Once set they should
// never be modified hence.  Consider using __read_mostly with GCC.

int ObjectMonitor::Knob_SpinLimit    = 5000;    // derived by an external tool -

static int Knob_Bonus               = 100;     // spin success bonus
static int Knob_BonusB              = 100;     // spin success bonus
static int Knob_Penalty             = 200;     // spin failure penalty
static int Knob_Poverty             = 1000;
static int Knob_FixedSpin           = 0;
static int Knob_PreSpin             = 10;      // 20-100 likely better

DEBUG_ONLY(static volatile bool InitDone = false;)

OopStorage* ObjectMonitor::_weak_oop_storage;
OopStorage* ObjectMonitor::_strong_oop_storage;

// -----------------------------------------------------------------------------
// Theory of operations -- Monitors lists, thread residency, etc:
//
// * A thread acquires ownership of a monitor by successfully
//   CAS()ing the _owner field from null to non-null.
//
// * Invariant: A thread appears on at most one monitor list --
//   cxq, EntryList or WaitSet -- at any one time.
//
// * Contending threads "push" themselves onto the cxq with CAS
//   and then spin/park.
//
// * After a contending thread eventually acquires the lock it must
//   dequeue itself from the entry list
//
// * The exiting thread identifies and unparks an "heir presumptive"
//   tentative successor thread on the EntryList.  Critically, the
//   exiting thread doesn't unlink the successor thread from the EntryList.
//   After having been unparked, the wakee will recontend for ownership of
//   the monitor.   The successor (wakee) will either acquire the lock or
//   re-park itself.
//
//   Succession is provided for by a policy of competitive handoff.
//   The exiting thread does _not_ grant or pass ownership to the
//   successor thread.  (This is also referred to as "handoff" succession").
//   Instead the exiting thread releases ownership and possibly wakes
//   a successor, so the successor can (re)compete for ownership of the lock.
//   If the EntryList is empty but the cxq is populated the exiting
//   thread will drain the cxq into the EntryList.  It does so by
//   by detaching the cxq (installing null with CAS) and folding
//   the threads from the cxq into the EntryList.  The EntryList is
//   doubly linked, while the cxq is singly linked because of the
//   CAS-based "push" used to enqueue recently arrived threads (RATs).
//
// * Concurrency invariants:
//
//   -- only the monitor owner may access or mutate the EntryList.
//      The mutex property of the monitor itself protects the EntryList
//      from concurrent interference.
//   -- Only the monitor owner may detach the cxq.
//
// * The monitor entry list operations avoid locks, but strictly speaking
//   they're not lock-free.  Enter is lock-free, exit is not.
//   For a description of 'Methods and apparatus providing non-blocking access
//   to a resource,' see U.S. Pat. No. 7844973.
//
// * The cxq can have multiple concurrent "pushers" but only one concurrent
//   detaching thread.  This mechanism is immune from the ABA corruption.
//   More precisely, the CAS-based "push" onto cxq is ABA-oblivious.
//
// * Taken together, the cxq and the EntryList constitute or form a
//   single logical queue of threads stalled trying to acquire the lock.
//   We use two distinct lists to improve the odds of a constant-time
//   dequeue operation after acquisition (in the ::enter() epilogue) and
//   to reduce heat on the list ends.  (c.f. Michael Scott's "2Q" algorithm).
//   A key desideratum is to minimize queue & monitor metadata manipulation
//   that occurs while holding the monitor lock -- that is, we want to
//   minimize monitor lock holds times.  Note that even a small amount of
//   fixed spinning will greatly reduce the # of enqueue-dequeue operations
//   on EntryList|cxq.  That is, spinning relieves contention on the "inner"
//   locks and monitor metadata.
//
//   Cxq points to the set of Recently Arrived Threads attempting entry.
//   Because we push threads onto _cxq with CAS, the RATs must take the form of
//   a singly-linked LIFO.  We drain _cxq into EntryList  at unlock-time when
//   the unlocking thread notices that EntryList is null but _cxq is != null.
//
//   The EntryList is ordered by the prevailing queue discipline and
//   can be organized in any convenient fashion, such as a doubly-linked list or
//   a circular doubly-linked list.  Critically, we want insert and delete operations
//   to operate in constant-time.  If we need a priority queue then something akin
//   to Solaris' sleepq would work nicely.  Viz.,
//   http://agg.eng/ws/on10_nightly/source/usr/src/uts/common/os/sleepq.c.
//   Queue discipline is enforced at ::exit() time, when the unlocking thread
//   drains the cxq into the EntryList, and orders or reorders the threads on the
//   EntryList accordingly.
//
//   Barring "lock barging", this mechanism provides fair cyclic ordering,
//   somewhat similar to an elevator-scan.
//
// * The monitor synchronization subsystem avoids the use of native
//   synchronization primitives except for the narrow platform-specific
//   park-unpark abstraction.  See the comments in os_solaris.cpp regarding
//   the semantics of park-unpark.  Put another way, this monitor implementation
//   depends only on atomic operations and park-unpark.  The monitor subsystem
//   manages all RUNNING->BLOCKED and BLOCKED->READY transitions while the
//   underlying OS manages the READY<->RUN transitions.
//
// * Waiting threads reside on the WaitSet list -- wait() puts
//   the caller onto the WaitSet.
//
// * notify() or notifyAll() simply transfers threads from the WaitSet to
//   either the EntryList or cxq.  Subsequent exit() operations will
//   unpark the notifyee.  Unparking a notifee in notify() is inefficient -
//   it's likely the notifyee would simply impale itself on the lock held
//   by the notifier.
//
// * An interesting alternative is to encode cxq as (List,LockByte) where
//   the LockByte is 0 iff the monitor is owned.  _owner is simply an auxiliary
//   variable, like _recursions, in the scheme.  The threads or Events that form
//   the list would have to be aligned in 256-byte addresses.  A thread would
//   try to acquire the lock or enqueue itself with CAS, but exiting threads
//   could use a 1-0 protocol and simply STB to set the LockByte to 0.
//   Note that is is *not* word-tearing, but it does presume that full-word
//   CAS operations are coherent with intermix with STB operations.  That's true
//   on most common processors.
//
// * See also http://blogs.sun.com/dave

ObjectWaiterList::ObjectWaiterList(OopStorage* oop_storage)
  : _head(oop_storage->allocate()),
    _tail(oop_storage->allocate()) {}

void ObjectWaiterList::release_handles(OopStorage* oop_storage) {
  oop_storage->release(_head);
  oop_storage->release(_tail);
}

oop ObjectWaiterList::next(oop node) {
  return java_lang_Thread::sync_next(node);
}

void ObjectWaiterList::set_next(oop node, oop next) {
  java_lang_Thread::set_sync_next(node, next);
}

oop ObjectWaiterList::head() const {
  return NativeAccess<MO_ACQUIRE>::oop_load(_head);
}

oop ObjectWaiterList::tail() const {
  return NativeAccess<>::oop_load(_tail);
}

bool ObjectWaiterList::cas_head(oop expected, oop new_value) {
  return NativeAccess<>::oop_atomic_cmpxchg(_head, expected, new_value) == expected;
}

void ObjectWaiterList::set_tail(oop new_value) {
  NativeAccess<>::oop_store(_tail, new_value);
}

bool ObjectWaiterList::is_empty() const {
  return head() == nullptr;
}

bool ObjectWaiterList::is_in_queue(oop waiter) {
  return next(waiter) != nullptr;
}

oop ObjectWaiterList::prev(oop node, oop root) {
  oop c = root;
  oop c_p = nullptr;
  for (;;) {
    oop c_n = next(c);
    if (c == node) {
      assert(next(c_p) == node, "invariant");
      return c_p;
    }
    c_p = c;
    c = c_n;
  }
}

void ObjectWaiterList::build_tail(oop h) {
  // Invert the list from head, and call it tail
  oop c = h;
  oop c_p = nullptr;

  for (;;) {
    oop c_n = next(c);
    if (c_p != nullptr) {
      set_next(c, c_p);
    }
    c_p = c;
    if (c_n == c) {
      // Self-loop denotes the end of the head list
      break;
    }
    c = c_n;
  }

  set_tail(c_p);
}

oop ObjectWaiterList::tail_dequeue() {
  oop t = tail();

  if (t == nullptr) {
    // No tail nodes available
    return nullptr;
  }

  oop t_n = next(t);
  oop t_n_n = next(t_n);

  if (t == t_n_n) {
    // The normal world and the upside down meet - periculum est prope
    // We discard the last element of the upside down, and break the
    // cycle
    set_tail(nullptr);
    set_next(t_n, t_n);
  } else {
    // Set next tail
    set_tail(t_n);
  }

  set_next(t, nullptr);

  return t;
}

oop ObjectWaiterList::last() {
  // Simplicissimus est; index inanis
  guarantee(!is_empty(), "List should not be empty");

  oop t = tail();

  if (t != nullptr) {
    return t;
  }

  oop h = head();
  oop h_n = next(h);

  if (h_n == h) {
    // The head is the first and last entry
    return h;
  }

  // Ultima nodi est specialis
  build_tail(h);

  return tail();
}

oop ObjectWaiterList::dequeue() {
  // Simplicissimus est; index inanis
  assert(!is_empty(), "List should not be empty");

  oop t = tail_dequeue();
  if (t != nullptr) {
    return t;
  }

  oop h = head();
  oop h_n = next(h);

  if (h_n == h) {
    // The head is the last entry; this is the only time that dequeue mutates the head
    // which has the visible effect of making the list is_empty().
    if (cas_head(h, nullptr)) {
      set_next(h, nullptr);
      return h;
    } else {
      // We can only fail due to concurrent enqueuing, which strictly grows the queue
      h = head();
    }
  }

  // Ultima nodi est specialis
  build_tail(h);

  // Index conversus est
  t = tail_dequeue();
  return t;
}

// Returns if this was the first element in the queue
bool ObjectWaiterList::enqueue(oop waiter) {
  // The enqueuer enqueues entries at the head
  // It is not concerned with the tail at all
  for (;;) {
    oop h = head();
    set_next(waiter, h == nullptr ? waiter : h);
    if (cas_head(h, waiter)) {
      return h == nullptr;
    }
  }
}

bool ObjectWaiterList::try_unlink(oop waiter) {
  if (is_empty()) {
    // Simplicissima causa
    return false;
  }

  oop h = head();
  oop h_n = next(h);

  if (h_n == h) {
    // Only one waiter; let's see if it's our waiter
    if (h != waiter) {
      return false;
    }
    oop result = dequeue();
    assert(result == waiter, "Someone snuck past in the queue");
    return true;
  }

  // Search through the tail
  oop t = tail();
  oop last_tail_p = nullptr;

  if (t != nullptr) {
    oop c = t;
    oop c_p = nullptr;

    for (;;) {
      oop c_n = next(c);
      oop c_n_n = next(c_n);
      bool is_last = c == c_n_n;
      bool is_first = c_p == nullptr;
      if (c == waiter) {
        // Vicimus sortitio
        if (is_last && is_first) {
          // Inconcinnus situ
          set_tail(nullptr);
          set_next(c_n, c_n);
        } else if (is_first) {
          set_tail(c_n);
        } else if (is_last) {
          set_next(c_n, c_p);
          set_next(c_p, c_n);
        } else {
          set_next(c_p, c_n);
        }
        set_next(c, nullptr);
        return true;
      }
      if (is_last) {
        last_tail_p = c_p;
        break;
      }
      c_p = c;
      c = c_n;
    }
  }

  // Search through the head
  oop c = h;
  oop c_p = nullptr;

  oop c_n;
  oop c_n_n;
  bool is_last;
  bool is_first;

  for (;;) {
    c_n = next(c);
    c_n_n = next(c_n);
    is_last = c == c_n_n;
    is_first = c_p == nullptr;
    if (c == waiter) {
      if (is_first && is_last) {
        // World join point
        if (!cas_head(c, c_n)) {
          // Not first any longer; find concurrently enqueued head
          c_p = prev(c, head());
          continue;
        } else {
          if (c_n == t) {
            // Tail only had one single node; remove tail
            set_next(c_n, c_n);
            set_tail(nullptr);
          } else {
            // At least two nodes in the tail; move the boundary
            set_next(c_n, last_tail_p);
            assert(next(last_tail_p) == c_n, "invariant");
          }
        }
        assert(c != c_n, "Single element list should have been handled at the top");
      } else if (is_first) {
        // No world join point - luxuriosa situ
        if (!cas_head(c, c_n)) {
          // Not first any longer; find concurrently enqueued head
          c_p = prev(c, head());
          continue;
        }
      } else if (is_last) {
        // In the head list, the last element either joins the end of tail, or it just ends
        if (c == c_n) {
          set_next(c_p, c_p);
        } else {
          set_next(c_p, c_n);
          set_next(c_n, c_p);
        }
      } else {
        // Vicimus sortitio
        set_next(c_p, c_n);
      }
      set_next(c, nullptr);
      return true;
    }
    if (is_last) {
      break;
    }
    c_p = c;
    c = c_n;
  }

  // nulla fortuna hodie
  return false;
}

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
  _header(markWord::zero()),
  _object(_weak_oop_storage, object),
  _owner(nullptr),
  _previous_owner_tid(0),
  _next_om(nullptr),
  _recursions(0),
  _enter_queue(_strong_oop_storage),
  _succ(_strong_oop_storage->allocate()),
  _Spinner(0),
  _SpinDuration(ObjectMonitor::Knob_SpinLimit),
  _contentions(0),
  _waiter_queue(_strong_oop_storage),
  _waiters(0),
  _waiter_dequeue_lock()
{ }

ObjectMonitor::~ObjectMonitor() {
  if (!_object.is_null()) {
    // Release object's oop storage if it hasn't already been done.
    release_objects();
  }
}

oop ObjectMonitor::object() const {
  check_object_context();
  if (_object.is_null()) {
    return nullptr;
  }
  return _object.resolve();
}

oop ObjectMonitor::object_peek() const {
  if (_object.is_null()) {
    return nullptr;
  }
  return _object.peek();
}

void ObjectMonitor::set_succ(oop successor) {
  NativeAccess<MO_RELAXED>::oop_store(_succ, successor);
}

oop ObjectMonitor::succ() const {
  return NativeAccess<MO_RELAXED>::oop_load(_succ);
}

void ObjectMonitor::ClearSuccOnSuspend::operator()(JavaThread* current) {
  if (current->is_suspended()) {
    if (_om->succ() == current->vthread()) {
      _om->set_succ(nullptr);
      OrderAccess::fence(); // always do a full fence when successor is cleared
    }
  }
}

// -----------------------------------------------------------------------------
// Enter support

bool ObjectMonitor::enter(JavaThread* current) {
  // The following code is ordered to check the most common cases first
  assert(!ObjectWaiterList::is_in_queue(current->vthread()), "invariant");

  void* track_owner = (void*)ANONYMOUS_OWNER;

  if (TryLock(current, &track_owner) > 0) {
    assert(_recursions == 0, "invariant");
    return true;
  }

  if (track_owner == owner_for(current)) {
    // TODO-FIXME: check for integer overflow!  BUGID 6557169.
    _recursions++;
    return true;
  }

  if (LockingMode != LM_LIGHTWEIGHT && current->is_lock_owned((address)track_owner)) {
    assert(_recursions == 0, "internal state error");
    _recursions = 1;
    set_owner_from_BasicLock(track_owner, current);  // Convert from BasicLock* to Thread*.
    return true;
  }

  // We've encountered genuine contention.
  assert(current->_Stalled == 0, "invariant");
  current->_Stalled = intptr_t(this);

  // Try one round of spinning *before* enqueueing current
  // and before going through the awkward and expensive state
  // transitions.  The following spin is strictly optional ...
  // Note that if we acquire the monitor from an initial spin
  // we forgo posting JVMTI events and firing DTRACE probes.
  if (TrySpin(current, &track_owner) > 0) {
    assert(owner_raw() == owner_for(current), "must be current: owner=" INTPTR_FORMAT, p2i(owner_raw()));
    assert(_recursions == 0, "must be 0: recursions=" INTX_FORMAT, _recursions);
    assert(object()->mark() == markWord::encode(this),
           "object mark must match encoded this: mark=" INTPTR_FORMAT
           ", encoded this=" INTPTR_FORMAT, object()->mark().value(),
           markWord::encode(this).value());
    current->_Stalled = 0;
    return true;
  }

  assert(owner_raw() != owner_for(current), "invariant");
  assert(succ() != current->vthread(), "invariant");
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(current->thread_state() != _thread_blocked, "invariant");

  // Keep track of contention for deflation, as well as JVM/TI and M&M queries.
  add_to_contentions(1);
  if (is_being_async_deflated()) {
    // Async deflation is in progress and our contentions increment
    // above lost the race to async deflation. Undo the work and
    // force the caller to retry.
    const oop l_object = object();
    if (l_object != nullptr) {
      // Attempt to restore the header/dmw to the object's header so that
      // we only retry once if the deflater thread happens to be slow.
      install_displaced_markword_in_object(l_object);
    }
    current->_Stalled = 0;
    add_to_contentions(-1);
    return false;
  }

  JFR_ONLY(JfrConditionalFlush<EventJavaMonitorEnter> flush(current);)
  EventJavaMonitorEnter event;
  if (event.is_started()) {
    event.set_monitorClass(object()->klass());
    // Set an address that is 'unique enough', such that events close in
    // time and with the same address are likely (but not guaranteed) to
    // belong to the same object.
    event.set_address((uintptr_t)this);
  }

  { // Change java thread status to indicate blocked on monitor enter.
    JavaThreadBlockedOnMonitorEnterState jtbmes(current, this);

    assert(current->current_pending_monitor() == nullptr, "invariant");
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

    OSThreadContendState osts(current->osthread());

    assert(current->thread_state() == _thread_in_vm, "invariant");

    if (!EnterI(current)) {
      for (;;) {
        {
          // Park self
          ThreadBlockInVM tbiv(current, true /* allow_suspend */);
          current->_ParkEvent->park();
        }

        if (EnterISuccessor(current)) {
          break;
        }
      }
    }

    current->set_current_pending_monitor(nullptr);

    // We've just gotten past the enter-check-for-suspend dance and we now own
    // the monitor free and clear.
  }

  add_to_contentions(-1);
  assert(contentions() >= 0, "must not be negative: contentions=%d", contentions());
  current->_Stalled = 0;

  // Must either set _recursions = 0 or ASSERT _recursions == 0.
  assert(_recursions == 0, "invariant");
  assert(owner_raw() == owner_for(current), "invariant");
  assert(succ() != current->vthread(), "invariant");
  assert(object()->mark() == markWord::encode(this), "invariant");

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
  if (event.should_commit()) {
    event.set_previousOwner(_previous_owner_tid);
    event.commit();
  }
  OM_PERFDATA_OP(ContendedLockAttempts, inc());
  assert(!ObjectWaiterList::is_in_queue(current->vthread()) &&
         !_enter_queue.try_unlink(current->vthread()),
         "We own the lock; we should not be on any list");
  return true;
}

// Caveat: TryLock() is not necessarily serializing if it returns failure.
// Callers must compensate as needed.

int ObjectMonitor::TryLock(JavaThread* current, void** track_owner) {
  void* own = owner_raw();
  if (own == nullptr) {
    if (try_set_owner_from(nullptr, current) == nullptr) {
      assert(_recursions == 0, "invariant");
      return 1;
    }
  } else if (own == DEFLATER_MARKER) {
    add_to_contentions(1);
    if (!is_being_async_deflated() && try_set_owner_from(DEFLATER_MARKER, current) == DEFLATER_MARKER) {
      // TODO: Fix comment
      // Cancelled the in-progress async deflation by changing owner from
      // DEFLATER_MARKER to current. As part of the contended enter protocol,
      // contentions was incremented to a positive value before TryLock()
      // was called and that prevents the deflater thread from winning the
      // last part of the 2-part async deflation protocol. After entering
      // the lock completes,, contentions is decremented because the caller
      // now owns the monitor. We bump contentions an extra time here to
      // prevent the deflater thread from winning the last part of the
      // 2-part async deflation protocol after the regular decrement
      // occurs in enter(). The deflater thread will decrement contentions
      // after it recognizes that the async deflation was cancelled.
      return 1;
    }
    add_to_contentions(-1);
  } else if (track_owner != nullptr) {
    if (*track_owner == (void*)ANONYMOUS_OWNER && *track_owner != own) {
      // Found so far unique non-anonymous owner
      *track_owner = own;
    } else if (*track_owner != (void*)ANONYMOUS_OWNER && *track_owner != own) {
      // Observed ownership change
      *track_owner = nullptr;
    }
  }

  // The lock had been free momentarily, but we lost the race to the lock.
  // Interference -- the CAS failed.
  // We can either return -1 or retry.
  // Retry doesn't make as much sense because the lock was just acquired.
  return -1;
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
bool ObjectMonitor::deflate_monitor() {
  if (is_busy()) {
    // Easy checks are first - the ObjectMonitor is busy so no deflation.
    return false;
  }

  if (ObjectSynchronizer::is_final_audit() && owner_is_DEFLATER_MARKER()) {
    // The final audit can see an already deflated ObjectMonitor on the
    // in-use list because MonitorList::unlink_deflated() might have
    // blocked for the final safepoint before unlinking all the deflated
    // monitors.
    assert(contentions() < 0, "must be negative: contentions=%d", contentions());
    // Already returned 'true' when it was originally deflated.
    return false;
  }

  const oop obj = object_peek();

  if (obj == nullptr) {
    // If the object died, we can recycle the monitor without racing with
    // Java threads. The GC already broke the association with the object.
    set_owner_from_raw(nullptr, DEFLATER_MARKER);
    assert(contentions() >= 0, "must be non-negative: contentions=%d", contentions());
    _contentions = INT_MIN; // minimum negative int
  } else {
    // Attempt async deflation protocol.

    // Set a null owner to DEFLATER_MARKER to force any contending thread
    // through the slow path. This is just the first part of the async
    // deflation dance.
    if (try_set_owner_from_raw(nullptr, DEFLATER_MARKER) != nullptr) {
      // The owner field is no longer null so we lost the race since the
      // ObjectMonitor is now busy.
      return false;
    }

    if (contentions() > 0 || _waiters != 0) {
      // Another thread has raced to enter the ObjectMonitor after
      // is_busy() above or has already entered and waited on
      // it which makes it busy so no deflation. Restore owner to
      // null if it is still DEFLATER_MARKER.
      if (try_set_owner_from_raw(DEFLATER_MARKER, nullptr) != DEFLATER_MARKER) {
        // Deferred decrement for the JT EnterI() that cancelled the async deflation.
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
      if (try_set_owner_from_raw(DEFLATER_MARKER, nullptr) != DEFLATER_MARKER) {
        // Deferred decrement for the JT EnterI() that cancelled the async deflation.
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
  guarantee(_enter_queue.is_empty(), "must be no contending threads");

  if (obj != nullptr) {
    if (log_is_enabled(Trace, monitorinflation)) {
      ResourceMark rm;
      log_trace(monitorinflation)("deflate_monitor: object=" INTPTR_FORMAT
                                  ", mark=" INTPTR_FORMAT ", type='%s'",
                                  p2i(obj), obj->mark().value(),
                                  obj->klass()->external_name());
    }

    // Install the old mark word if nobody else has already done it.
    install_displaced_markword_in_object(obj);
  }

  // Release object's oop storage since the ObjectMonitor has been deflated:
  release_objects();

  // We leave owner == DEFLATER_MARKER and contentions < 0
  // to force any racing threads to retry.
  return true;  // Success, ObjectMonitor has been deflated.
}

void ObjectMonitor::release_objects() {
  _object.release(_weak_oop_storage);
  _strong_oop_storage->release(_succ);
  _enter_queue.release_handles(_strong_oop_storage);
  _waiter_queue.release_handles(_strong_oop_storage);
}

// Install the displaced mark word (dmw) of a deflating ObjectMonitor
// into the header of the object associated with the monitor. This
// idempotent method is called by a thread that is deflating a
// monitor and by other threads that have detected a race with the
// deflation process.
void ObjectMonitor::install_displaced_markword_in_object(const oop obj) {
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
  ss->print("is_busy: waiters=%d, ", _waiters);
  if (contentions() > 0) {
    ss->print("contentions=%d, ", contentions());
  } else {
    ss->print("contentions=0");
  }
  if (!owner_is_DEFLATER_MARKER()) {
    ss->print("owner=" INTPTR_FORMAT, p2i(owner_raw()));
  } else {
    // We report null instead of DEFLATER_MARKER here because is_busy()
    // ignores DEFLATER_MARKER values.
    ss->print("owner=" INTPTR_FORMAT, NULL_WORD);
  }
  ss->print(", enter_queue.is_empty=%d", _enter_queue.is_empty());
  return ss->base();
}

bool ObjectMonitor::EnterI(JavaThread* current) {
  assert(current->thread_state() != _thread_blocked, "invariant");

  void* owner_phase_1 = (void*)ANONYMOUS_OWNER;

  // Try the lock - TATAS
  if (TryLock(current, &owner_phase_1) > 0) {
    assert(succ() != current->vthread(), "invariant");
    assert(owner_raw() == owner_for(current), "invariant");
    return true;
  }

  assert(InitDone, "Unexpectedly not initialized");

  // We try one round of spinning *before* enqueueing current.
  //
  // If the _owner is ready but OFFPROC we could use a YieldTo()
  // operation to donate the remainder of this thread's quantum
  // to the owner.  This has subtle but beneficial affinity
  // effects.

  if (TrySpin(current, &owner_phase_1) > 0) {
    assert(owner_raw() == owner_for(current), "invariant");
    assert(succ() != current->vthread(), "invariant");
    return true;
  }

  // The Spin failed -- Enqueue and park the thread ...
  assert(succ() != current->vthread(), "invariant");
  assert(owner_raw() != owner_for(current), "invariant");

  current->_ParkEvent->reset();

  // Enqueue "current" on ObjectMonitor's enter queue
  bool enqueued_first = _enter_queue.enqueue(current->vthread());

  // The lock might have been released while this thread was occupied enqueueing
  // itself onto.  To close the race and avoid progress-liveness failure we must
  // resample-retry _owner before parking.
  // Note the Dekker/Lamport duality: ST cxq; MEMBAR; LD Owner.
  // In this case the ST-MEMBAR is accomplished with CAS().
  //
  // TODO: Defer all thread state transitions until park-time.
  // Since state transitions are heavy and inefficient we'd like
  // to defer the state transitions until absolutely necessary,
  // and in doing so avoid some transitions ...

  if (enqueued_first) {
    void* owner_phase_2 = (void*)ANONYMOUS_OWNER;

    // Check for the first enqueue of enter queue.  This indicates
    // the onset of contention.  While contention persists exiting threads
    // will use a ST:MEMBAR:LD 1-1 exit protocol.  When contention abates exit
    // operations revert to the faster 1-0 mode.

    if (TryLock(current, &owner_phase_2) > 0) {
      oop first = _enter_queue.dequeue();
      assert(first == current->vthread(), "must be first in queue");
      set_succ(nullptr);
      // We are the first on the queue - Sit scriptor pars
      return true;
    }

    if (TrySpin(current, &owner_phase_2) > 0) {
      oop first = _enter_queue.dequeue();
      assert(first == current->vthread(), "must be first in queue");
      set_succ(nullptr);
      return true;
    }

    if (owner_phase_2 != nullptr) {
      // We have monitored the owner since enqueuing. If we have had a single owner
      // consistently, then we can't just park, because when exiting, there is a race
      // where the first unlocker after the first enqueuer will miss checking for a
      // successor.
      // By handshaking the single owner, we can ensure that we have moved past that
      // race, and indeed, parking may continue. If the owner is anonymous, we have to
      // handshake all the threads. After the handshake, all exiting threads will be
      // checking their successors, allowing us to park without stranding.
      // When we got here we have already done typically 3 rounds of TrySpin, so the
      // cost of doing a handshake shouldn't be too bad. Especially if the single owner
      // is known.

      class RendezvousHandshakeClosure: public HandshakeClosure {
      public:
        inline RendezvousHandshakeClosure() : HandshakeClosure("RendezvousHandshakeClosure") {}
        inline void do_thread(Thread* thread) {}
      } cl;

      if (owner_phase_2 == (void*)ANONYMOUS_OWNER) {
        log_info(monitorinflation)("Anonymous contention enter handshake");
        Handshake::execute(&cl);
      } else {
        ThreadsListHandle tlh;
        oop* owner = (oop*)owner_phase_2;
        if (owner_raw() == owner_phase_2) {
          // Reload the owner to ensure it isn't freed concurrently. If it changed, we
          // don't need to perform the handshake to rendezvous the stale owner.
          oop owner_thread_obj = NativeAccess<>::oop_load(owner);
          JavaThread* target = java_lang_Thread::thread(owner_thread_obj);
          // If the target thread is exiting, the JavaThread might be null. But then we
          // also don't need to rendezvous any longer.
          if (target != nullptr) {
            log_debug(monitorinflation)("Contention enter handshake");
            Handshake::execute(&cl, &tlh, target);
          }
        }
      }
    }
  }

  // Need to park; aliquis nobis victus
  return false;
}

void ObjectMonitor::EnterIEgress(JavaThread* current) {
  // Egress :
  // Current has acquired the lock
  set_succ(nullptr);
  // We are the first on the queue - Sit scriptor pars
  oop first = _enter_queue.dequeue();
  assert(first == current->vthread(), "must be first in queue");

  assert(owner_raw() == owner_for(current), "invariant");

  assert(succ() != current->vthread(), "invariant");

  // We've acquired ownership with CAS().
  // CAS is serializing -- it has MEMBAR/FENCE-equivalent semantics.
  // But since the CAS() this thread may have also stored into _succ,
  // EntryList, cxq or Responsible.  These meta-data updates must be
  // visible __before this thread subsequently drops the lock.
  // Consider what could occur if we didn't enforce this constraint --
  // STs to monitor meta-data and user-data could reorder with (become
  // visible after) the ST in exit that drops ownership of the lock.
  // Some other thread could then acquire the lock, but observe inconsistent
  // or old monitor meta-data and heap data.  That violates the JMM.
  // To that end, the 1-0 exit() operation must have at least STST|LDST
  // "release" barrier semantics.  Specifically, there must be at least a
  // STST|LDST barrier in exit() before the ST of null into _owner that drops
  // the lock.   The barrier ensures that changes to monitor meta-data and data
  // protected by the lock will be visible before we release the lock, and
  // therefore before some other thread (CPU) has a chance to acquire the lock.
  // See also: http://gee.cs.oswego.edu/dl/jmm/cookbook.html.
  //
  // Critically, any prior STs to _succ or EntryList must be visible before
  // the ST of null into _owner in the *subsequent* (following) corresponding
  // monitorexit.  Recall too, that in 1-0 mode monitorexit does not necessarily
  // execute a serializing instruction.
}

bool ObjectMonitor::EnterISuccessor(JavaThread* current) {
  assert(current != nullptr, "invariant");
  assert(succ() == current->vthread(), "Only call when you know you are the successor");
  void* track_owner = (void*)ANONYMOUS_OWNER;
  if (TryLock(current, &track_owner) > 0) {
    EnterIEgress(current);
    return true;
  }

  // The lock is still contested.
  // Keep a tally of the # of futile wakeups.
  // Note that the counter is not protected by a lock or updated by atomics.
  // That is by design - we trade "lossy" counters which are exposed to
  // races during updates for a lower probe effect.

  // This PerfData object can be used in parallel with a safepoint.
  // See the work around in PerfDataManager::destroy().
  OM_PERFDATA_OP(FutileWakeups, inc());

  // Assuming this is not a spurious wakeup we'll normally find _succ == current.
  // We can defer clearing _succ until after the spin completes
  // TrySpin() must tolerate being called with _succ == current.
  // Try yet another round of adaptive spinning.
  if (TrySpin(current, &track_owner) > 0) {
    EnterIEgress(current);
    return true;
  }

  // We can find that we were unpark()ed and redesignated _succ while
  // we were spinning.  That's harmless.  If we iterate and call park(),
  // park() will consume the event and return immediately and we'll
  // just spin again.  This pattern can repeat, leaving _succ to simply
  // spin on a CPU.
  set_succ(nullptr);
  OrderAccess::fence();
  // Invariant: after clearing _succ a thread *must* retry _owner before parking.
  if (TryLock(current, &track_owner) > 0) {
    EnterIEgress(current);
    return true;
   }

  return false;
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
// There's one exception to the claim above, however.  EnterI() can call
// exit() to drop a lock if the acquirer has been externally suspended.
// In that case exit() is called with _thread_state == _thread_blocked,
// but the monitor's _contentions field is > 0, which inhibits reclamation.
//
// The CAS() in enter provides for safety and exclusion, while the CAS or
// MEMBAR in exit provides for progress and avoids stranding.  1-0 locking
// eliminates the CAS/MEMBAR from the exit path, but it admits stranding.
// We detect and recover from stranding with timers.
//
// If a thread transiently strands it'll park until (a) another
// thread acquires the lock and then drops the lock, at which time the
// exiting thread will notice and unpark the stranded thread, or, (b)
// the timer expires.  If the lock is high traffic then the stranding latency
// will be low due to (a).  If the lock is low traffic then the odds of
// stranding are lower, although the worst-case stranding latency
// is longer.  Critically, we don't want to put excessive load in the
// platform's timer subsystem.  We want to minimize both the timer injection
// rate (timers created/sec) as well as the number of timers active at
// any one time.  (more precisely, we want to minimize timer-seconds, which is
// the integral of the # of active timers at any instant over time).
// Both impinge on OS scalability.  Given that, at most one thread parked on
// a monitor will use a timer.
//
// There is also the risk of a futile wake-up. If we drop the lock
// another thread can reacquire the lock immediately, and we can
// then wake a thread unnecessarily. This is benign, and we've
// structured the code so the windows are short and the frequency
// of such futile wakups is low.

void ObjectMonitor::exit(JavaThread* current, bool not_suspended) {
  void* cur = owner_raw();
  if (owner_for(current) != cur) {
    if (LockingMode != LM_LIGHTWEIGHT && current->is_lock_owned((address)cur)) {
      assert(_recursions == 0, "invariant");
      set_owner_from_BasicLock(cur, current);  // Convert from BasicLock* to Thread*.
      _recursions = 0;
    } else {
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
    assert(owner_for(current) == owner_raw(), "invariant");

    // Drop the lock.
    // release semantics: prior loads and stores from within the critical section
    // must not float (reorder) past the following store that drops the lock.
    // Uses a storeload to separate release_store(owner) from the
    // successor check. The try_set_owner() below uses cmpxchg() so
    // we get the fence down there.
    release_clear_owner(current);
    OrderAccess::storeload();

    if (_enter_queue.is_empty() || succ() != nullptr) {
      return;
    }
    // Other threads are blocked trying to acquire the lock.

    // Normally the exiting thread is responsible for ensuring succession,
    // but if other successors are ready or other entering threads are spinning
    // then this thread can simply store null into _owner and exit without
    // waking a successor.  The existence of spinners or ready successors
    // guarantees proper succession (liveness).  Responsibility passes to the
    // ready or running successors.  The exiting thread delegates the duty.
    // More precisely, if a successor already exists this thread is absolved
    // of the responsibility of waking (unparking) one.
    //
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
    // Another less appealing alternative would be for the exiting thread
    // to drop the lock and then spin briefly to see if a spinner managed
    // to acquire the lock.  If so, the exiting thread could exit
    // immediately without waking a successor, otherwise the exiting
    // thread would need to dequeue and wake a successor.
    // (Note that we'd need to make the post-drop spin short, but no
    // shorter than the worst-case round-trip cache-line migration time.
    // The dropped lock needs to become visible to the spinner, and then
    // the acquisition of the lock by the spinner must become visible to
    // the exiting thread).

    // It appears that an heir-presumptive (successor) must be made ready.
    // Only the current lock owner can manipulate the EntryList or
    // drain _cxq, so we need to reacquire the lock.  If we fail
    // to reacquire the lock the responsibility for ensuring succession
    // falls to the new owner.

    if (TryLock(current, nullptr) <= 0) {
      return;
    }

    guarantee(owner_raw() == owner_for(current), "invariant");

    if (!_enter_queue.is_empty()) {
      oop first = _enter_queue.last();
      ExitEpilog(current, first);
      break;
    }
  }
}

void ObjectMonitor::ExitEpilog(JavaThread* current, oop wakee) {
  assert(owner_raw() == owner_for(current), "invariant");

  // Exit protocol:
  // 1. ST _succ = wakee
  // 2. membar #loadstore|#storestore;
  // 2. ST _owner = nullptr
  // 3. unpark(wakee)
  JavaThread* wakee_jt = java_lang_Thread::thread(wakee); // TODO: Make succ oop

  set_succ(wakee);
  ParkEvent* event = wakee_jt->_ParkEvent;

  // Drop the lock.
  // Uses a fence to separate release_store(owner) from the LD in unpark().
  release_clear_owner(current);
  OrderAccess::fence();

  DTRACE_MONITOR_PROBE(contended__exit, this, object(), current);
  event->unpark();

  // Maintain stats and report events to JVMTI
  OM_PERFDATA_OP(Parks, inc());
}

// complete_exit exits a lock returning recursion count
// complete_exit requires an inflated monitor
// The _owner field is not always the Thread addr even with an
// inflated monitor, e.g. the monitor can be inflated by a non-owning
// thread due to contention.
intx ObjectMonitor::complete_exit(JavaThread* current) {
  assert(InitDone, "Unexpectedly not initialized");

  void* cur = owner_raw();
  if (current != cur) {
    if (LockingMode != LM_LIGHTWEIGHT && current->is_lock_owned((address)cur)) {
      assert(_recursions == 0, "internal state error");
      set_owner_from_BasicLock(cur, current);  // Convert from BasicLock* to Thread*.
      _recursions = 0;
    }
  }

  guarantee(owner_for(current) == owner_raw(), "complete_exit not owner");
  intx save = _recursions; // record the old recursion count
  _recursions = 0;         // set the recursion level to be 0
  exit(current);           // exit the monitor
  guarantee(owner_raw() != owner_for(current), "invariant");
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
  void* cur = owner_raw();
  assert(cur != anon_owner_ptr(), "no anon owner here");
  if (cur == owner_for(current)) {
    return true;
  }
  if (LockingMode != LM_LIGHTWEIGHT && current->is_lock_owned((address)cur)) {
    set_owner_from_BasicLock(cur, current);  // Convert from BasicLock* to Thread*.
    _recursions = 0;
    return true;
  }
  THROW_MSG_(vmSymbols::java_lang_IllegalMonitorStateException(),
             "current thread is not owner", false);
}

// TODO: JVMTI/JFR
//static inline bool is_excluded(const Klass* monitor_klass) {
//  assert(monitor_klass != nullptr, "invariant");
//  NOT_JFR_RETURN_(false);
//  JFR_ONLY(return vmSymbols::jfr_chunk_rotation_monitor() == monitor_klass->name());
//}

//static void post_monitor_wait_event(EventJavaMonitorWait* event,
//                                    ObjectMonitor* monitor,
//                                    uint64_t notifier_tid,
//                                    jlong timeout,
//                                    bool timedout) {
//  assert(event != nullptr, "invariant");
//  assert(monitor != nullptr, "invariant");
//  const Klass* monitor_klass = monitor->object()->klass();
//  if (is_excluded(monitor_klass)) {
//    return;
//  }
//  event->set_monitorClass(monitor_klass);
//  event->set_timeout(timeout);
//  // Set an address that is 'unique enough', such that events close in
//  // time and with the same address are likely (but not guaranteed) to
//  // belong to the same object.
//  event->set_address((uintptr_t)monitor);
//  event->set_notifier(notifier_tid);
//  event->set_timedOut(timedout);
//  event->commit();
//}

// -----------------------------------------------------------------------------
// Wait/Notify/NotifyAll
//
// Note: a subset of changes to ObjectMonitor::wait()
// will need to be replicated in complete_exit
void ObjectMonitor::wait(jlong millis, bool interruptible, TRAPS) {
  JavaThread* current = THREAD;

  assert(InitDone, "Unexpectedly not initialized");

  CHECK_OWNER();  // Throws IMSE if not owner.

  // TODO: JFR
  //EventJavaMonitorWait event;

  // TODO: JVMTI
  //// check for a pending interrupt
  //if (interruptible && current->is_interrupted(true) && !HAS_PENDING_EXCEPTION) {
  //  // post monitor waited event.  Note that this is past-tense, we are done waiting.
  //  if (JvmtiExport::should_post_monitor_waited()) {
  //    // Note: 'false' parameter is passed here because the
  //    // wait was not timed out due to thread interrupt.
  //    JvmtiExport::post_monitor_waited(current, this, false);

  //    // In this short circuit of the monitor wait protocol, the
  //    // current thread never drops ownership of the monitor and
  //    // never gets added to the wait queue so the current thread
  //    // cannot be made the successor. This means that the
  //    // JVMTI_EVENT_MONITOR_WAITED event handler cannot accidentally
  //    // consume an unpark() meant for the ParkEvent associated with
  //    // this ObjectMonitor.
  //  }
  //  if (event.should_commit()) {
  //    post_monitor_wait_event(&event, this, 0, millis, false);
  //  }
  //  THROW(vmSymbols::java_lang_InterruptedException());
  //  return;
  //}

  assert(current->_Stalled == 0, "invariant");
  current->_Stalled = intptr_t(this);
  current->set_current_waiting_monitor(this);

  // create a node to be put into the queue
  // Critically, after we reset() the event but prior to park(), we must check
  // for a pending interrupt.
  current->_ParkEvent->reset();
  OrderAccess::fence();          // ST into Event; membar ; LD interrupted-flag

  _waiter_queue.enqueue(current->vthread());

  intx save = _recursions;     // record the old recursion count
  _waiters++;                  // increment the number of waiters
  _recursions = 0;             // set the recursion level to be 1
  exit(current);               // exit the monitor
  guarantee(owner_raw() != owner_for(current), "invariant");

  // The thread is on the WaitSet list - now park() it.
  // On MP systems it's conceivable that a brief spin before we park
  // could be profitable.
  //
  // TODO-FIXME: change the following logic to a loop of the form
  //   while (!timeout && !interrupted && _notified == 0) park()

  bool was_notified = true;
  int ret = OS_OK;
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
      } else {
        if (millis <= 0) {
          current->_ParkEvent->park();
        } else {
          ret = current->_ParkEvent->park(millis);
        }
      }
    }

    // Notifier dequeues from the waiting list and enqueues to the enter list

    // TODO: JVMTI
    //// Reentry phase -- reacquire the monitor.
    //// re-enter contended monitor after object.wait().
    //// retain OBJECT_WAIT state until re-enter successfully completes
    //// Thread state is thread_in_vm and oop access is again safe,
    //// although the raw address of the object may have changed.
    //// (Don't cache naked oops over safepoints, of course).

    //// post monitor waited event. Note that this is past-tense, we are done waiting.
    //if (JvmtiExport::should_post_monitor_waited()) {
    //  JvmtiExport::post_monitor_waited(current, this, ret == OS_TIMEOUT);

    //  if (node._notified != 0 && _succ == current) {
    //    // In this part of the monitor wait-notify-reenter protocol it
    //    // is possible (and normal) for another thread to do a fastpath
    //    // monitor enter-exit while this thread is still trying to get
    //    // to the reenter portion of the protocol.
    //    //
    //    // The ObjectMonitor was notified and the current thread is
    //    // the successor which also means that an unpark() has already
    //    // been done. The JVMTI_EVENT_MONITOR_WAITED event handler can
    //    // consume the unpark() that was done when the successor was
    //    // set because the same ParkEvent is shared between Java
    //    // monitors and JVM/TI RawMonitors (for now).
    //    //
    //    // We redo the unpark() to ensure forward progress, i.e., we
    //    // don't want all pending threads hanging (parked) with none
    //    // entering the unlocked monitor.
    //    node._event->unpark();
    //  }
    //}

    // TODO: JFR
    //if (event.should_commit()) {
    //  post_monitor_wait_event(&event, this, node._notifier_tid, millis, ret == OS_TIMEOUT);
    //}

    OrderAccess::fence();

    bool maybe_not_notified = ret != OS_OK || (interruptible && current->is_interrupted(true));

    assert(current->_Stalled != 0, "invariant");
    current->_Stalled = 0;

    assert(owner_raw() != owner_for(current), "invariant");
    if (maybe_not_notified) {
      // If we didn't get notified, we are here because of interrupt or timeout.
      _waiter_dequeue_lock.lock();
      if (_waiter_queue.try_unlink(current->vthread())) {
        was_notified = false;
      }
      _waiter_dequeue_lock.unlock();
    }

    if (!was_notified) {
      // We won't be a successor yet; try normal entry
      if (!EnterI(current)) {
        {
          // Park self
          ThreadBlockInVM tbiv(current, true /* allow_suspend */);
          current->_ParkEvent->park();
        }

        for (;;) {
          // We get unparked because we are the successor
          if (EnterISuccessor(current)) {
            break;
          }

          {
            // Park self
            ThreadBlockInVM tbiv(current, true /* allow_suspend */);
            current->_ParkEvent->park();
          }
        }
      }
    } else {
      for (;;) {
        // We get unparked because we are the successor
        if (EnterISuccessor(current)) {
          break;
        }

        {
          // Park self
          ThreadBlockInVM tbiv(current, true /* allow_suspend */);
          current->_ParkEvent->park();
        }
      }
    }


    // current has reacquired the lock.
    assert(owner_raw() == owner_for(current), "invariant");
    assert(succ() != current->vthread(), "invariant");
  } // OSThreadWaitState()

  current->set_current_waiting_monitor(nullptr);

  guarantee(_recursions == 0, "invariant");
  int relock_count = JvmtiDeferredUpdates::get_and_reset_relock_count_after_wait(current);
  _recursions =   save          // restore the old recursion count
                + relock_count; //  increased by the deferred relock count
  current->inc_held_monitor_count(relock_count); // Deopt never entered these counts.
  _waiters--;             // decrement the number of waiters

  // Verify a few postconditions
  assert(owner_raw() == owner_for(current), "invariant");
  assert(succ() != current->vthread(), "invariant");
  assert(object()->mark() == markWord::encode(this), "invariant");

  // check if the notification happened
  if (!was_notified) {
    // no, it could be timeout or Thread.interrupt() or both
    // check for interrupt event, otherwise it is timeout
    if (interruptible && current->is_interrupted(true) && !HAS_PENDING_EXCEPTION) {
      THROW(vmSymbols::java_lang_InterruptedException());
    }
  }

  // NOTE: Spurious wake up will be consider as timeout.
  // Monitor notify has precedence over thread interrupt.
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

bool ObjectMonitor::has_waiters() {
  return !_waiter_queue.is_empty();
}

void ObjectMonitor::NotifyI() {
  _waiter_dequeue_lock.lock();
  oop waitee = _waiter_queue.dequeue();
  _enter_queue.enqueue(waitee);
  _waiter_dequeue_lock.unlock();
}

void ObjectMonitor::notify(TRAPS) {
  JavaThread* current = THREAD;
  CHECK_OWNER();  // Throws IMSE if not owner.
  if (_waiter_queue.is_empty()) {
    return;
  }
  DTRACE_MONITOR_PROBE(notify, this, object(), current);
  NotifyI();
  OM_PERFDATA_OP(Notifications, inc(1));
}


// The current implementation of notifyAll() transfers the waiters one-at-a-time
// from the waiter queue to the entry list. This could be done more efficiently with a
// single bulk transfer but in practice it's not time-critical. Beware too,
// that in prepend-mode we invert the order of the waiters. Let's say that the
// waitset is "ABCD" and the EntryList is "XYZ". After a notifyAll() in prepend
// mode the waitset will be empty and the EntryList will be "DCBAXYZ".

void ObjectMonitor::notifyAll(TRAPS) {
  JavaThread* current = THREAD;
  CHECK_OWNER();  // Throws IMSE if not owner.
  DTRACE_MONITOR_PROBE(notifyAll, this, object(), current);
  int tally = 0;
  while (!_waiter_queue.is_empty()) {
    tally++;
    NotifyI();
  }

  OM_PERFDATA_OP(Notifications, inc(tally));
}

// -----------------------------------------------------------------------------
// Adaptive Spinning Support
//
// Adaptive spin-then-block - rational spinning
//
// Note that we spin "globally" on _owner with a classic SMP-polite TATAS
// algorithm.  On high order SMP systems it would be better to start with
// a brief global spin and then revert to spinning locally.  In the spirit of MCS/CLH,
// a contending thread could enqueue itself on the cxq and then spin locally
// on a thread-specific variable such as its ParkEvent._Event flag.
// That's left as an exercise for the reader.  Note that global spinning is
// not problematic on Niagara, as the L2 cache serves the interconnect and
// has both low latency and massive bandwidth.
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

// Spinning: Fixed frequency (100%), vary duration
int ObjectMonitor::TrySpin(JavaThread* current, void** track_owner) {
  // Dumb, brutal spin.  Good for comparative measurements against adaptive spinning.
  int ctr = Knob_FixedSpin;
  if (ctr != 0) {
    while (--ctr >= 0) {
      if (TryLock(current, track_owner) > 0) return 1;
      SpinPause();
    }
    return 0;
  }

  for (ctr = Knob_PreSpin + 1; --ctr >= 0;) {
    if (TryLock(current, track_owner) > 0) {
      // Increase _SpinDuration ...
      // Note that we don't clamp SpinDuration precisely at SpinLimit.
      // Raising _SpurDuration to the poverty line is key.
      int x = _SpinDuration;
      if (x < Knob_SpinLimit) {
        if (x < Knob_Poverty) x = Knob_Poverty;
        _SpinDuration = x + Knob_BonusB;
      }
      return 1;
    }
    SpinPause();
  }

  // Admission control - verify preconditions for spinning
  //
  // We always spin a little bit, just to prevent _SpinDuration == 0 from
  // becoming an absorbing state.  Put another way, we spin briefly to
  // sample, just in case the system load, parallelism, contention, or lock
  // modality changed.
  //
  // Consider the following alternative:
  // Periodically set _SpinDuration = _SpinLimit and try a long/full
  // spin attempt.  "Periodically" might mean after a tally of
  // the # of failed spin attempts (or iterations) reaches some threshold.
  // This takes us into the realm of 1-out-of-N spinning, where we
  // hold the duration constant but vary the frequency.

  ctr = _SpinDuration;
  if (ctr <= 0) return 0;

  // There are three ways to exit the following loop:
  // 1.  A successful spin where this thread has acquired the lock.
  // 2.  Spin failure with prejudice
  // 3.  Spin failure without prejudice

  while (--ctr >= 0) {
    if (succ() == nullptr) {
      set_succ(current->vthread());
    }

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
        goto Abort;           // abrupt spin egress
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

    if (TryLock(current, track_owner) > 0) {
      if (succ() == current->vthread()) {
        set_succ(nullptr);
      }

      // Increase _SpinDuration :
      // The spin was successful (profitable) so we tend toward
      // longer spin attempts in the future.
      // CONSIDER: factor "ctr" into the _SpinDuration adjustment.
      // If we acquired the lock early in the spin cycle it
      // makes sense to increase _SpinDuration proportionally.
      // Note that we don't clamp SpinDuration precisely at SpinLimit.
      int x = _SpinDuration;
      if (x < Knob_SpinLimit) {
        if (x < Knob_Poverty) x = Knob_Poverty;
        _SpinDuration = x + Knob_Bonus;
      }
      return 1;
    }

    // The CAS failed ... we can take any of the following actions:
    // * penalize: ctr -= CASPenalty
    // * exit spin with prejudice -- goto Abort;
    // * exit spin without prejudice.
    // * Since CAS is high-latency, retry again immediately.

    if (*track_owner == nullptr) {
      // Ownership changed hands; abort spinning
      goto Abort;
    }
  }

  // Spin failed with prejudice -- reduce _SpinDuration.
  // TODO: Use an AIMD-like policy to adjust _SpinDuration.
  // AIMD is globally stable.
  {
    int x = _SpinDuration;
    if (x > 0) {
      // Consider an AIMD scheme like: x -= (x >> 3) + 100
      // This is globally sample and tends to damp the response.
      x -= Knob_Penalty;
      if (x < 0) x = 0;
      _SpinDuration = x;
    }
  }

 Abort:
  if (succ() == current->vthread()) {
    set_succ(nullptr);
    // Invariant: after setting succ=null a contending thread
    // must recheck-retry _owner before parking.  This usually happens
    // in the normal usage of TrySpin(), but it's safest
    // to make TrySpin() as foolproof as possible.
    OrderAccess::fence();
    if (TryLock(current, track_owner) > 0) return 1;
  }
  return 0;
}

// -----------------------------------------------------------------------------
// PerfData support
PerfCounter * ObjectMonitor::_sync_ContendedLockAttempts       = nullptr;
PerfCounter * ObjectMonitor::_sync_FutileWakeups               = nullptr;
PerfCounter * ObjectMonitor::_sync_Parks                       = nullptr;
PerfCounter * ObjectMonitor::_sync_Notifications               = nullptr;
PerfCounter * ObjectMonitor::_sync_Inflations                  = nullptr;
PerfCounter * ObjectMonitor::_sync_Deflations                  = nullptr;
PerfLongVariable * ObjectMonitor::_sync_MonExtant              = nullptr;

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

  if (UsePerfData) {
    EXCEPTION_MARK;
#define NEWPERFCOUNTER(n)                                                \
  {                                                                      \
    n = PerfDataManager::create_counter(SUN_RT, #n, PerfData::U_Events,  \
                                        CHECK);                          \
  }
#define NEWPERFVARIABLE(n)                                                \
  {                                                                       \
    n = PerfDataManager::create_variable(SUN_RT, #n, PerfData::U_Events,  \
                                         CHECK);                          \
  }
    NEWPERFCOUNTER(_sync_Inflations);
    NEWPERFCOUNTER(_sync_Deflations);
    NEWPERFCOUNTER(_sync_ContendedLockAttempts);
    NEWPERFCOUNTER(_sync_FutileWakeups);
    NEWPERFCOUNTER(_sync_Parks);
    NEWPERFCOUNTER(_sync_Notifications);
    NEWPERFVARIABLE(_sync_MonExtant);
#undef NEWPERFCOUNTER
#undef NEWPERFVARIABLE
  }

  _weak_oop_storage = OopStorageSet::create_weak("ObjectSynchronizer Weak", mtSynchronizer);
  _strong_oop_storage = OopStorageSet::create_strong("ObjectSynchronizer Strong", mtSynchronizer);

  DEBUG_ONLY(InitDone = true;)
}

void ObjectMonitor::print_on(outputStream* st) const {
  // The minimal things to print for markWord printing, more can be added for debugging and logging.
  st->print("{contentions=0x%08x,waiters=0x%08x"
            ",recursions=" INTX_FORMAT ",owner=" INTPTR_FORMAT "}",
            contentions(), waiters(), recursions(),
            p2i(owner()));
}
void ObjectMonitor::print() const { print_on(tty); }

#ifdef ASSERT
// Print the ObjectMonitor like a debugger would:
//
// (ObjectMonitor) 0x00007fdfb6012e40 = {
//   _header = 0x0000000000000001
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
//   _EntryList = 0x0000000000000000
//   _cxq = 0x0000000000000000
//   _succ = 0x0000000000000000
//   _Spinner = 0
//   _SpinDuration = 5000
//   _contentions = 0
//   _WaitSet = 0x0000700009756248
//   _waiters = 1
//   _WaitSetLock = 0
// }
//
void ObjectMonitor::print_debug_style_on(outputStream* st) const {
  st->print_cr("(ObjectMonitor*) " INTPTR_FORMAT " = {", p2i(this));
  st->print_cr("  _header = " INTPTR_FORMAT, header().value());
  st->print_cr("  _object = " INTPTR_FORMAT, p2i(object_peek()));
  st->print_cr("  _pad_buf0 = {");
  st->print_cr("    [0] = '\\0'");
  st->print_cr("    ...");
  st->print_cr("    [%d] = '\\0'", (int)sizeof(_pad_buf0) - 1);
  st->print_cr("  }");
  st->print_cr("  _owner = " INTPTR_FORMAT, p2i(owner_raw()));
  st->print_cr("  _previous_owner_tid = " UINT64_FORMAT, _previous_owner_tid);
  st->print_cr("  _pad_buf1 = {");
  st->print_cr("    [0] = '\\0'");
  st->print_cr("    ...");
  st->print_cr("    [%d] = '\\0'", (int)sizeof(_pad_buf1) - 1);
  st->print_cr("  }");
  st->print_cr("  _next_om = " INTPTR_FORMAT, p2i(next_om()));
  st->print_cr("  _recursions = " INTX_FORMAT, _recursions);
  st->print_cr("  _enter_queue.is_empty = %d", _enter_queue.is_empty());
  st->print_cr("  _succ = " INTPTR_FORMAT, p2i(succ()));
  st->print_cr("  _Spinner = %d", _Spinner);
  st->print_cr("  _SpinDuration = %d", _SpinDuration);
  st->print_cr("  _contentions = %d", contentions());
  st->print_cr("  _waiter_queue.is_empty = %d", _waiter_queue.is_empty());
  st->print_cr("  _waiters = %d", _waiters);
  st->print_cr("  _waiter_dequeue_lock = ...");
  st->print_cr("}");
}
#endif
