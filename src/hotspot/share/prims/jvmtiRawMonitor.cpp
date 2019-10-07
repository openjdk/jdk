/*
 * Copyright (c) 2003, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.inline.hpp"
#include "prims/jvmtiRawMonitor.hpp"
#include "runtime/atomic.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/thread.inline.hpp"

JvmtiRawMonitor::QNode::QNode(Thread* thread) : _next(NULL), _prev(NULL),
                                                _event(thread->_ParkEvent),
                                                _notified(0), TState(TS_RUN) {
}

GrowableArray<JvmtiRawMonitor*> *JvmtiPendingMonitors::_monitors =
  new (ResourceObj::C_HEAP, mtInternal) GrowableArray<JvmtiRawMonitor*>(1, true);

void JvmtiPendingMonitors::transition_raw_monitors() {
  assert((Threads::number_of_threads()==1),
         "Java thread has not been created yet or more than one java thread \
is running. Raw monitor transition will not work");
  JavaThread *current_java_thread = JavaThread::current();
  assert(current_java_thread->thread_state() == _thread_in_vm, "Must be in vm");
  for(int i=0; i< count(); i++) {
    JvmtiRawMonitor *rmonitor = monitors()->at(i);
    rmonitor->raw_enter(current_java_thread);
  }
  // pending monitors are converted to real monitor so delete them all.
  dispose();
}

//
// class JvmtiRawMonitor
//

JvmtiRawMonitor::JvmtiRawMonitor(const char *name) : _owner(NULL),
                                                     _recursions(0),
                                                     _EntryList(NULL),
                                                     _WaitSet(NULL),
                                                     _waiters(0),
                                                     _magic(JVMTI_RM_MAGIC),
                                                     _name(NULL) {
#ifdef ASSERT
  _name = strcpy(NEW_C_HEAP_ARRAY(char, strlen(name) + 1, mtInternal), name);
#endif
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
// The JVMTI raw monitor subsystem is entirely distinct from normal
// java-synchronization or jni-synchronization.  JVMTI raw monitors are not
// associated with objects.  They can be implemented in any manner
// that makes sense.  The original implementors decided to piggy-back
// the raw-monitor implementation on the existing Java ObjectMonitor mechanism.
// Now we just use a simplified form of that ObjectMonitor code.
//
// Note that we use the single RawMonitor_lock to protect queue operations for
// _all_ raw monitors.  This is a scalability impediment, but since raw monitor usage
// is fairly rare, this is not of concern.  The RawMonitor_lock can not
// be held indefinitely.  The critical sections must be short and bounded.
//
// -------------------------------------------------------------------------

void JvmtiRawMonitor::SimpleEnter (Thread * Self) {
  for (;;) {
    if (Atomic::replace_if_null(Self, &_owner)) {
       return ;
    }

    QNode Node (Self) ;
    Self->_ParkEvent->reset() ;     // strictly optional
    Node.TState = QNode::TS_ENTER ;

    RawMonitor_lock->lock_without_safepoint_check() ;
    Node._next  = _EntryList ;
    _EntryList  = &Node ;
    OrderAccess::fence() ;
    if (_owner == NULL && Atomic::replace_if_null(Self, &_owner)) {
        _EntryList = Node._next ;
        RawMonitor_lock->unlock() ;
        return ;
    }
    RawMonitor_lock->unlock() ;
    while (Node.TState == QNode::TS_ENTER) {
       Self->_ParkEvent->park() ;
    }
  }
}

void JvmtiRawMonitor::SimpleExit (Thread * Self) {
  guarantee (_owner == Self, "invariant") ;
  OrderAccess::release_store(&_owner, (Thread*)NULL) ;
  OrderAccess::fence() ;
  if (_EntryList == NULL) return ;
  QNode * w ;

  RawMonitor_lock->lock_without_safepoint_check() ;
  w = _EntryList ;
  if (w != NULL) {
      _EntryList = w->_next ;
  }
  RawMonitor_lock->unlock() ;
  if (w != NULL) {
      guarantee (w ->TState == QNode::TS_ENTER, "invariant") ;
      // Once we set TState to TS_RUN the waiting thread can complete
      // SimpleEnter and 'w' is pointing into random stack space. So we have
      // to ensure we extract the ParkEvent (which is in type-stable memory)
      // before we set the state, and then don't access 'w'.
      ParkEvent * ev = w->_event ;
      OrderAccess::loadstore();
      w->TState = QNode::TS_RUN ;
      OrderAccess::fence() ;
      ev->unpark() ;
  }
  return ;
}

int JvmtiRawMonitor::SimpleWait (Thread * Self, jlong millis) {
  guarantee (_owner == Self  , "invariant") ;
  guarantee (_recursions == 0, "invariant") ;

  QNode Node (Self) ;
  Node._notified = 0 ;
  Node.TState    = QNode::TS_WAIT ;

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
  // as TState is volatile and the lock-unlock operators are
  // serializing (barrier-equivalent).

  if (Node.TState == QNode::TS_WAIT) {
    RawMonitor_lock->lock_without_safepoint_check() ;
    if (Node.TState == QNode::TS_WAIT) {
      // Simple O(n) unlink, but performance isn't critical here.
      QNode * p ;
      QNode * q = NULL ;
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
      Node.TState = QNode::TS_RUN ;
    }
    RawMonitor_lock->unlock() ;
  }

  guarantee (Node.TState == QNode::TS_RUN, "invariant") ;
  SimpleEnter (Self) ;

  guarantee (_owner == Self, "invariant") ;
  guarantee (_recursions == 0, "invariant") ;
  return ret ;
}

void JvmtiRawMonitor::SimpleNotify (Thread * Self, bool All) {
  guarantee (_owner == Self, "invariant") ;
  if (_WaitSet == NULL) return ;

  // We have two options:
  // A. Transfer the threads from the WaitSet to the EntryList
  // B. Remove the thread from the WaitSet and unpark() it.
  //
  // We use (B), which is crude and results in lots of futile
  // context switching.  In particular (B) induces lots of contention.

  ParkEvent * ev = NULL ;       // consider using a small auto array ...
  RawMonitor_lock->lock_without_safepoint_check() ;
  for (;;) {
      QNode * w = _WaitSet ;
      if (w == NULL) break ;
      _WaitSet = w->_next ;
      if (ev != NULL) { ev->unpark(); ev = NULL; }
      ev = w->_event ;
      OrderAccess::loadstore() ;
      w->TState = QNode::TS_RUN ;
      OrderAccess::storeload();
      if (!All) break ;
  }
  RawMonitor_lock->unlock() ;
  if (ev != NULL) ev->unpark();
  return ;
}

// Any JavaThread will enter here with state _thread_blocked
void JvmtiRawMonitor::raw_enter(Thread * Self) {
  void * Contended ;
  JavaThread * jt = NULL;
  // don't enter raw monitor if thread is being externally suspended, it will
  // surprise the suspender if a "suspended" thread can still enter monitor
  if (Self->is_Java_thread()) {
    jt = (JavaThread*) Self;
    jt->SR_lock()->lock_without_safepoint_check();
    while (jt->is_external_suspend()) {
      jt->SR_lock()->unlock();
      jt->java_suspend_self();
      jt->SR_lock()->lock_without_safepoint_check();
    }
    // guarded by SR_lock to avoid racing with new external suspend requests.
    Contended = Atomic::cmpxchg(jt, &_owner, (Thread*)NULL);
    jt->SR_lock()->unlock();
  } else {
    Contended = Atomic::cmpxchg(Self, &_owner, (Thread*)NULL);
  }

  if (Contended == Self) {
     _recursions ++ ;
     return ;
  }

  if (Contended == NULL) {
     guarantee (_owner == Self, "invariant") ;
     guarantee (_recursions == 0, "invariant") ;
     return ;
  }

  Self->set_current_pending_raw_monitor(this);

  if (!Self->is_Java_thread()) {
     SimpleEnter (Self) ;
  } else {
    guarantee (jt->thread_state() == _thread_blocked, "invariant") ;
    for (;;) {
      jt->set_suspend_equivalent();
      // cleared by handle_special_suspend_equivalent_condition() or
      // java_suspend_self()
      SimpleEnter (jt) ;

      // were we externally suspended while we were waiting?
      if (!jt->handle_special_suspend_equivalent_condition()) break ;

      // This thread was externally suspended
      // We have reentered the contended monitor, but while we were
      // waiting another thread suspended us. We don't want to reenter
      // the monitor while suspended because that would surprise the
      // thread that suspended us.
      //
      // Drop the lock
      SimpleExit (jt) ;

      jt->java_suspend_self();
    }
  }

  Self->set_current_pending_raw_monitor(NULL);

  guarantee (_owner == Self, "invariant") ;
  guarantee (_recursions == 0, "invariant") ;
}

int JvmtiRawMonitor::raw_exit(Thread * Self) {
  if (Self != _owner) {
    return M_ILLEGAL_MONITOR_STATE;
  }
  if (_recursions > 0) {
    --_recursions ;
  } else {
    SimpleExit (Self) ;
  }

  return M_OK;
}

// All JavaThreads will enter here with state _thread_blocked

int JvmtiRawMonitor::raw_wait(jlong millis, bool interruptible, Thread * Self) {
  if (Self != _owner) {
    return M_ILLEGAL_MONITOR_STATE;
  }

  // To avoid spurious wakeups we reset the parkevent -- This is strictly optional.
  // The caller must be able to tolerate spurious returns from raw_wait().
  Self->_ParkEvent->reset() ;
  OrderAccess::fence() ;

  JavaThread * jt = NULL;
  // check interrupt event
  if (interruptible) {
    assert(Self->is_Java_thread(), "Only JavaThreads can be interruptible");
    jt = (JavaThread*) Self;
    if (jt->is_interrupted(true)) {
      return M_INTERRUPTED;
    }
  } else {
    assert(!Self->is_Java_thread(), "JavaThreads must be interuptible");
  }

  intptr_t save = _recursions ;
  _recursions = 0 ;
  _waiters ++ ;
  if (Self->is_Java_thread()) {
    guarantee (jt->thread_state() == _thread_blocked, "invariant") ;
    jt->set_suspend_equivalent();
  }
  int rv = SimpleWait (Self, millis) ;
  _recursions = save ;
  _waiters -- ;

  guarantee (Self == _owner, "invariant") ;
  if (Self->is_Java_thread()) {
     for (;;) {
        if (!jt->handle_special_suspend_equivalent_condition()) break ;
        SimpleExit (jt) ;
        jt->java_suspend_self();
        SimpleEnter (jt) ;
        jt->set_suspend_equivalent() ;
     }
     guarantee (jt == _owner, "invariant") ;
  }

  if (interruptible && jt->is_interrupted(true)) {
    return M_INTERRUPTED;
  }

  return M_OK ;
}

int JvmtiRawMonitor::raw_notify(Thread * Self) {
  if (Self != _owner) {
    return M_ILLEGAL_MONITOR_STATE;
  }
  SimpleNotify (Self, false) ;
  return M_OK;
}

int JvmtiRawMonitor::raw_notifyAll(Thread * Self) {
  if (Self != _owner) {
    return M_ILLEGAL_MONITOR_STATE;
  }
  SimpleNotify (Self, true) ;
  return M_OK;
}
