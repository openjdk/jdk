/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_jvmtiRawMonitor.cpp.incl"

GrowableArray<JvmtiRawMonitor*> *JvmtiPendingMonitors::_monitors = new (ResourceObj::C_HEAP) GrowableArray<JvmtiRawMonitor*>(1,true);

void JvmtiPendingMonitors::transition_raw_monitors() {
  assert((Threads::number_of_threads()==1),
         "Java thread has not created yet or more than one java thread \
is running. Raw monitor transition will not work");
  JavaThread *current_java_thread = JavaThread::current();
  assert(current_java_thread->thread_state() == _thread_in_vm, "Must be in vm");
  {
    ThreadBlockInVM __tbivm(current_java_thread);
    for(int i=0; i< count(); i++) {
      JvmtiRawMonitor *rmonitor = monitors()->at(i);
      int r = rmonitor->raw_enter(current_java_thread);
      assert(r == ObjectMonitor::OM_OK, "raw_enter should have worked");
    }
  }
  // pending monitors are converted to real monitor so delete them all.
  dispose();
}

//
// class JvmtiRawMonitor
//

JvmtiRawMonitor::JvmtiRawMonitor(const char *name) {
#ifdef ASSERT
  _name = strcpy(NEW_C_HEAP_ARRAY(char, strlen(name) + 1), name);
#else
  _name = NULL;
#endif
  _magic = JVMTI_RM_MAGIC;
}

JvmtiRawMonitor::~JvmtiRawMonitor() {
#ifdef ASSERT
  FreeHeap(_name);
#endif
  _magic = 0;
}


bool
JvmtiRawMonitor::is_valid() {
  int value = 0;

  // This object might not be a JvmtiRawMonitor so we can't assume
  // the _magic field is properly aligned. Get the value in a safe
  // way and then check against JVMTI_RM_MAGIC.

  switch (sizeof(_magic)) {
  case 2:
    value = Bytes::get_native_u2((address)&_magic);
    break;

  case 4:
    value = Bytes::get_native_u4((address)&_magic);
    break;

  case 8:
    value = Bytes::get_native_u8((address)&_magic);
    break;

  default:
    guarantee(false, "_magic field is an unexpected size");
  }

  return value == JVMTI_RM_MAGIC;
}

// -------------------------------------------------------------------------
// The raw monitor subsystem is entirely distinct from normal
// java-synchronization or jni-synchronization.  raw monitors are not
// associated with objects.  They can be implemented in any manner
// that makes sense.  The original implementors decided to piggy-back
// the raw-monitor implementation on the existing Java objectMonitor mechanism.
// This flaw needs to fixed.  We should reimplement raw monitors as sui-generis.
// Specifically, we should not implement raw monitors via java monitors.
// Time permitting, we should disentangle and deconvolve the two implementations
// and move the resulting raw monitor implementation over to the JVMTI directories.
// Ideally, the raw monitor implementation would be built on top of
// park-unpark and nothing else.
//
// raw monitors are used mainly by JVMTI
// The raw monitor implementation borrows the ObjectMonitor structure,
// but the operators are degenerate and extremely simple.
//
// Mixed use of a single objectMonitor instance -- as both a raw monitor
// and a normal java monitor -- is not permissible.
//
// Note that we use the single RawMonitor_lock to protect queue operations for
// _all_ raw monitors.  This is a scalability impediment, but since raw monitor usage
// is deprecated and rare, this is not of concern.  The RawMonitor_lock can not
// be held indefinitely.  The critical sections must be short and bounded.
//
// -------------------------------------------------------------------------

int JvmtiRawMonitor::SimpleEnter (Thread * Self) {
  for (;;) {
    if (Atomic::cmpxchg_ptr (Self, &_owner, NULL) == NULL) {
       return OS_OK ;
    }

    ObjectWaiter Node (Self) ;
    Self->_ParkEvent->reset() ;     // strictly optional
    Node.TState = ObjectWaiter::TS_ENTER ;

    RawMonitor_lock->lock_without_safepoint_check() ;
    Node._next  = _EntryList ;
    _EntryList  = &Node ;
    OrderAccess::fence() ;
    if (_owner == NULL && Atomic::cmpxchg_ptr (Self, &_owner, NULL) == NULL) {
        _EntryList = Node._next ;
        RawMonitor_lock->unlock() ;
        return OS_OK ;
    }
    RawMonitor_lock->unlock() ;
    while (Node.TState == ObjectWaiter::TS_ENTER) {
       Self->_ParkEvent->park() ;
    }
  }
}

int JvmtiRawMonitor::SimpleExit (Thread * Self) {
  guarantee (_owner == Self, "invariant") ;
  OrderAccess::release_store_ptr (&_owner, NULL) ;
  OrderAccess::fence() ;
  if (_EntryList == NULL) return OS_OK ;
  ObjectWaiter * w ;

  RawMonitor_lock->lock_without_safepoint_check() ;
  w = _EntryList ;
  if (w != NULL) {
      _EntryList = w->_next ;
  }
  RawMonitor_lock->unlock() ;
  if (w != NULL) {
      guarantee (w ->TState == ObjectWaiter::TS_ENTER, "invariant") ;
      ParkEvent * ev = w->_event ;
      w->TState = ObjectWaiter::TS_RUN ;
      OrderAccess::fence() ;
      ev->unpark() ;
  }
  return OS_OK ;
}

int JvmtiRawMonitor::SimpleWait (Thread * Self, jlong millis) {
  guarantee (_owner == Self  , "invariant") ;
  guarantee (_recursions == 0, "invariant") ;

  ObjectWaiter Node (Self) ;
  Node._notified = 0 ;
  Node.TState    = ObjectWaiter::TS_WAIT ;

  RawMonitor_lock->lock_without_safepoint_check() ;
  Node._next     = _WaitSet ;
  _WaitSet       = &Node ;
  RawMonitor_lock->unlock() ;

  SimpleExit (Self) ;
  guarantee (_owner != Self, "invariant") ;

  int ret = OS_OK ;
  if (millis <= 0) {
    Self->_ParkEvent->park();
  } else {
    ret = Self->_ParkEvent->park(millis);
  }

  // If thread still resides on the waitset then unlink it.
  // Double-checked locking -- the usage is safe in this context
  // as we TState is volatile and the lock-unlock operators are
  // serializing (barrier-equivalent).

  if (Node.TState == ObjectWaiter::TS_WAIT) {
    RawMonitor_lock->lock_without_safepoint_check() ;
    if (Node.TState == ObjectWaiter::TS_WAIT) {
      // Simple O(n) unlink, but performance isn't critical here.
      ObjectWaiter * p ;
      ObjectWaiter * q = NULL ;
      for (p = _WaitSet ; p != &Node; p = p->_next) {
         q = p ;
      }
      guarantee (p == &Node, "invariant") ;
      if (q == NULL) {
        guarantee (p == _WaitSet, "invariant") ;
        _WaitSet = p->_next ;
      } else {
        guarantee (p == q->_next, "invariant") ;
        q->_next = p->_next ;
      }
      Node.TState = ObjectWaiter::TS_RUN ;
    }
    RawMonitor_lock->unlock() ;
  }

  guarantee (Node.TState == ObjectWaiter::TS_RUN, "invariant") ;
  SimpleEnter (Self) ;

  guarantee (_owner == Self, "invariant") ;
  guarantee (_recursions == 0, "invariant") ;
  return ret ;
}

int JvmtiRawMonitor::SimpleNotify (Thread * Self, bool All) {
  guarantee (_owner == Self, "invariant") ;
  if (_WaitSet == NULL) return OS_OK ;

  // We have two options:
  // A. Transfer the threads from the WaitSet to the EntryList
  // B. Remove the thread from the WaitSet and unpark() it.
  //
  // We use (B), which is crude and results in lots of futile
  // context switching.  In particular (B) induces lots of contention.

  ParkEvent * ev = NULL ;       // consider using a small auto array ...
  RawMonitor_lock->lock_without_safepoint_check() ;
  for (;;) {
      ObjectWaiter * w = _WaitSet ;
      if (w == NULL) break ;
      _WaitSet = w->_next ;
      if (ev != NULL) { ev->unpark(); ev = NULL; }
      ev = w->_event ;
      OrderAccess::loadstore() ;
      w->TState = ObjectWaiter::TS_RUN ;
      OrderAccess::storeload();
      if (!All) break ;
  }
  RawMonitor_lock->unlock() ;
  if (ev != NULL) ev->unpark();
  return OS_OK ;
}

// Any JavaThread will enter here with state _thread_blocked
int JvmtiRawMonitor::raw_enter(TRAPS) {
  TEVENT (raw_enter) ;
  void * Contended ;

  // don't enter raw monitor if thread is being externally suspended, it will
  // surprise the suspender if a "suspended" thread can still enter monitor
  JavaThread * jt = (JavaThread *)THREAD;
  if (THREAD->is_Java_thread()) {
    jt->SR_lock()->lock_without_safepoint_check();
    while (jt->is_external_suspend()) {
      jt->SR_lock()->unlock();
      jt->java_suspend_self();
      jt->SR_lock()->lock_without_safepoint_check();
    }
    // guarded by SR_lock to avoid racing with new external suspend requests.
    Contended = Atomic::cmpxchg_ptr (THREAD, &_owner, NULL) ;
    jt->SR_lock()->unlock();
  } else {
    Contended = Atomic::cmpxchg_ptr (THREAD, &_owner, NULL) ;
  }

  if (Contended == THREAD) {
     _recursions ++ ;
     return OM_OK ;
  }

  if (Contended == NULL) {
     guarantee (_owner == THREAD, "invariant") ;
     guarantee (_recursions == 0, "invariant") ;
     return OM_OK ;
  }

  THREAD->set_current_pending_monitor(this);

  if (!THREAD->is_Java_thread()) {
     // No other non-Java threads besides VM thread would acquire
     // a raw monitor.
     assert(THREAD->is_VM_thread(), "must be VM thread");
     SimpleEnter (THREAD) ;
   } else {
     guarantee (jt->thread_state() == _thread_blocked, "invariant") ;
     for (;;) {
       jt->set_suspend_equivalent();
       // cleared by handle_special_suspend_equivalent_condition() or
       // java_suspend_self()
       SimpleEnter (THREAD) ;

       // were we externally suspended while we were waiting?
       if (!jt->handle_special_suspend_equivalent_condition()) break ;

       // This thread was externally suspended
       //
       // This logic isn't needed for JVMTI raw monitors,
       // but doesn't hurt just in case the suspend rules change. This
           // logic is needed for the JvmtiRawMonitor.wait() reentry phase.
           // We have reentered the contended monitor, but while we were
           // waiting another thread suspended us. We don't want to reenter
           // the monitor while suspended because that would surprise the
           // thread that suspended us.
           //
           // Drop the lock -
       SimpleExit (THREAD) ;

           jt->java_suspend_self();
         }

     assert(_owner == THREAD, "Fatal error with monitor owner!");
     assert(_recursions == 0, "Fatal error with monitor recursions!");
  }

  THREAD->set_current_pending_monitor(NULL);
  guarantee (_recursions == 0, "invariant") ;
  return OM_OK;
}

// Used mainly for JVMTI raw monitor implementation
// Also used for JvmtiRawMonitor::wait().
int JvmtiRawMonitor::raw_exit(TRAPS) {
  TEVENT (raw_exit) ;
  if (THREAD != _owner) {
    return OM_ILLEGAL_MONITOR_STATE;
  }
  if (_recursions > 0) {
    --_recursions ;
    return OM_OK ;
  }

  void * List = _EntryList ;
  SimpleExit (THREAD) ;

  return OM_OK;
}

// Used for JVMTI raw monitor implementation.
// All JavaThreads will enter here with state _thread_blocked

int JvmtiRawMonitor::raw_wait(jlong millis, bool interruptible, TRAPS) {
  TEVENT (raw_wait) ;
  if (THREAD != _owner) {
    return OM_ILLEGAL_MONITOR_STATE;
  }

  // To avoid spurious wakeups we reset the parkevent -- This is strictly optional.
  // The caller must be able to tolerate spurious returns from raw_wait().
  THREAD->_ParkEvent->reset() ;
  OrderAccess::fence() ;

  // check interrupt event
  if (interruptible && Thread::is_interrupted(THREAD, true)) {
    return OM_INTERRUPTED;
  }

  intptr_t save = _recursions ;
  _recursions = 0 ;
  _waiters ++ ;
  if (THREAD->is_Java_thread()) {
    guarantee (((JavaThread *) THREAD)->thread_state() == _thread_blocked, "invariant") ;
    ((JavaThread *)THREAD)->set_suspend_equivalent();
  }
  int rv = SimpleWait (THREAD, millis) ;
  _recursions = save ;
  _waiters -- ;

  guarantee (THREAD == _owner, "invariant") ;
  if (THREAD->is_Java_thread()) {
     JavaThread * jSelf = (JavaThread *) THREAD ;
     for (;;) {
        if (!jSelf->handle_special_suspend_equivalent_condition()) break ;
        SimpleExit (THREAD) ;
        jSelf->java_suspend_self();
        SimpleEnter (THREAD) ;
        jSelf->set_suspend_equivalent() ;
     }
  }
  guarantee (THREAD == _owner, "invariant") ;

  if (interruptible && Thread::is_interrupted(THREAD, true)) {
    return OM_INTERRUPTED;
  }
  return OM_OK ;
}

int JvmtiRawMonitor::raw_notify(TRAPS) {
  TEVENT (raw_notify) ;
  if (THREAD != _owner) {
    return OM_ILLEGAL_MONITOR_STATE;
  }
  SimpleNotify (THREAD, false) ;
  return OM_OK;
}

int JvmtiRawMonitor::raw_notifyAll(TRAPS) {
  TEVENT (raw_notifyAll) ;
  if (THREAD != _owner) {
    return OM_ILLEGAL_MONITOR_STATE;
  }
  SimpleNotify (THREAD, true) ;
  return OM_OK;
}

