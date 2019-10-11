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
                                                _notified(0), _t_state(TS_RUN) {
}

GrowableArray<JvmtiRawMonitor*>* JvmtiPendingMonitors::_monitors =
  new (ResourceObj::C_HEAP, mtInternal) GrowableArray<JvmtiRawMonitor*>(1, true);

void JvmtiPendingMonitors::transition_raw_monitors() {
  assert((Threads::number_of_threads()==1),
         "Java thread has not been created yet or more than one java thread "
         "is running. Raw monitor transition will not work");
  JavaThread* current_java_thread = JavaThread::current();
  assert(current_java_thread->thread_state() == _thread_in_vm, "Must be in vm");
  for (int i = 0; i < count(); i++) {
    JvmtiRawMonitor* rmonitor = monitors()->at(i);
    rmonitor->raw_enter(current_java_thread);
  }
  // pending monitors are converted to real monitor so delete them all.
  dispose();
}

//
// class JvmtiRawMonitor
//

JvmtiRawMonitor::JvmtiRawMonitor(const char* name) : _owner(NULL),
                                                     _recursions(0),
                                                     _entry_list(NULL),
                                                     _wait_set(NULL),
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

void JvmtiRawMonitor::simple_enter(Thread* self) {
  for (;;) {
    if (Atomic::replace_if_null(self, &_owner)) {
      return;
    }

    QNode node(self);
    self->_ParkEvent->reset();     // strictly optional
    node._t_state = QNode::TS_ENTER;

    RawMonitor_lock->lock_without_safepoint_check();
    node._next = _entry_list;
    _entry_list = &node;
    OrderAccess::fence();
    if (_owner == NULL && Atomic::replace_if_null(self, &_owner)) {
      _entry_list = node._next;
      RawMonitor_lock->unlock();
      return;
    }
    RawMonitor_lock->unlock();
    while (node._t_state == QNode::TS_ENTER) {
      self->_ParkEvent->park();
    }
  }
}

void JvmtiRawMonitor::simple_exit(Thread* self) {
  guarantee(_owner == self, "invariant");
  OrderAccess::release_store(&_owner, (Thread*)NULL);
  OrderAccess::fence();
  if (_entry_list == NULL) {
    return;
  }

  RawMonitor_lock->lock_without_safepoint_check();
  QNode* w = _entry_list;
  if (w != NULL) {
    _entry_list = w->_next;
  }
  RawMonitor_lock->unlock();
  if (w != NULL) {
    guarantee(w ->_t_state == QNode::TS_ENTER, "invariant");
    // Once we set _t_state to TS_RUN the waiting thread can complete
    // simple_enter and 'w' is pointing into random stack space. So we have
    // to ensure we extract the ParkEvent (which is in type-stable memory)
    // before we set the state, and then don't access 'w'.
    ParkEvent* ev = w->_event;
    OrderAccess::loadstore();
    w->_t_state = QNode::TS_RUN;
    OrderAccess::fence();
    ev->unpark();
  }
  return;
}

int JvmtiRawMonitor::simple_wait(Thread* self, jlong millis) {
  guarantee(_owner == self  , "invariant");
  guarantee(_recursions == 0, "invariant");

  QNode node(self);
  node._notified = 0;
  node._t_state = QNode::TS_WAIT;

  RawMonitor_lock->lock_without_safepoint_check();
  node._next = _wait_set;
  _wait_set = &node;
  RawMonitor_lock->unlock();

  simple_exit(self);
  guarantee(_owner != self, "invariant");

  int ret = OS_OK;
  if (millis <= 0) {
    self->_ParkEvent->park();
  } else {
    ret = self->_ParkEvent->park(millis);
  }

  // If thread still resides on the waitset then unlink it.
  // Double-checked locking -- the usage is safe in this context
  // as _t_state is volatile and the lock-unlock operators are
  // serializing (barrier-equivalent).

  if (node._t_state == QNode::TS_WAIT) {
    RawMonitor_lock->lock_without_safepoint_check();
    if (node._t_state == QNode::TS_WAIT) {
      // Simple O(n) unlink, but performance isn't critical here.
      QNode* p;
      QNode* q = NULL;
      for (p = _wait_set; p != &node; p = p->_next) {
        q = p;
      }
      guarantee(p == &node, "invariant");
      if (q == NULL) {
        guarantee (p == _wait_set, "invariant");
        _wait_set = p->_next;
      } else {
        guarantee(p == q->_next, "invariant");
        q->_next = p->_next;
      }
      node._t_state = QNode::TS_RUN;
    }
    RawMonitor_lock->unlock();
  }

  guarantee(node._t_state == QNode::TS_RUN, "invariant");
  simple_enter(self);

  guarantee(_owner == self, "invariant");
  guarantee(_recursions == 0, "invariant");
  return ret;
}

void JvmtiRawMonitor::simple_notify(Thread* self, bool all) {
  guarantee(_owner == self, "invariant");
  if (_wait_set == NULL) {
    return;
  }

  // We have two options:
  // A. Transfer the threads from the _wait_set to the _entry_list
  // B. Remove the thread from the _wait_set and unpark() it.
  //
  // We use (B), which is crude and results in lots of futile
  // context switching.  In particular (B) induces lots of contention.

  ParkEvent* ev = NULL;       // consider using a small auto array ...
  RawMonitor_lock->lock_without_safepoint_check();
  for (;;) {
    QNode* w = _wait_set;
    if (w == NULL) break;
    _wait_set = w->_next;
    if (ev != NULL) {
      ev->unpark();
      ev = NULL;
    }
    ev = w->_event;
    OrderAccess::loadstore();
    w->_t_state = QNode::TS_RUN;
    OrderAccess::storeload();
    if (!all) {
      break;
    }
  }
  RawMonitor_lock->unlock();
  if (ev != NULL) {
    ev->unpark();
  }
  return;
}

// Any JavaThread will enter here with state _thread_blocked
void JvmtiRawMonitor::raw_enter(Thread* self) {
  void* contended;
  JavaThread* jt = NULL;
  // don't enter raw monitor if thread is being externally suspended, it will
  // surprise the suspender if a "suspended" thread can still enter monitor
  if (self->is_Java_thread()) {
    jt = (JavaThread*)self;
    jt->SR_lock()->lock_without_safepoint_check();
    while (jt->is_external_suspend()) {
      jt->SR_lock()->unlock();
      jt->java_suspend_self();
      jt->SR_lock()->lock_without_safepoint_check();
    }
    // guarded by SR_lock to avoid racing with new external suspend requests.
    contended = Atomic::cmpxchg(jt, &_owner, (Thread*)NULL);
    jt->SR_lock()->unlock();
  } else {
    contended = Atomic::cmpxchg(self, &_owner, (Thread*)NULL);
  }

  if (contended == self) {
    _recursions++;
    return;
  }

  if (contended == NULL) {
    guarantee(_owner == self, "invariant");
    guarantee(_recursions == 0, "invariant");
    return;
  }

  self->set_current_pending_raw_monitor(this);

  if (!self->is_Java_thread()) {
    simple_enter(self);
  } else {
    guarantee(jt->thread_state() == _thread_blocked, "invariant");
    for (;;) {
      jt->set_suspend_equivalent();
      // cleared by handle_special_suspend_equivalent_condition() or
      // java_suspend_self()
      simple_enter(jt);

      // were we externally suspended while we were waiting?
      if (!jt->handle_special_suspend_equivalent_condition()) {
        break;
      }

      // This thread was externally suspended
      // We have reentered the contended monitor, but while we were
      // waiting another thread suspended us. We don't want to reenter
      // the monitor while suspended because that would surprise the
      // thread that suspended us.
      //
      // Drop the lock
      simple_exit(jt);

      jt->java_suspend_self();
    }
  }

  self->set_current_pending_raw_monitor(NULL);

  guarantee(_owner == self, "invariant");
  guarantee(_recursions == 0, "invariant");
}

int JvmtiRawMonitor::raw_exit(Thread* self) {
  if (self != _owner) {
    return M_ILLEGAL_MONITOR_STATE;
  }
  if (_recursions > 0) {
    _recursions--;
  } else {
    simple_exit(self);
  }

  return M_OK;
}

// All JavaThreads will enter here with state _thread_blocked

int JvmtiRawMonitor::raw_wait(jlong millis, bool interruptible, Thread* self) {
  if (self != _owner) {
    return M_ILLEGAL_MONITOR_STATE;
  }

  // To avoid spurious wakeups we reset the parkevent. This is strictly optional.
  // The caller must be able to tolerate spurious returns from raw_wait().
  self->_ParkEvent->reset();
  OrderAccess::fence();

  JavaThread* jt = NULL;
  // check interrupt event
  if (interruptible) {
    assert(self->is_Java_thread(), "Only JavaThreads can be interruptible");
    jt = (JavaThread*)self;
    if (jt->is_interrupted(true)) {
      return M_INTERRUPTED;
    }
  } else {
    assert(!self->is_Java_thread(), "JavaThreads must be interuptible");
  }

  intptr_t save = _recursions;
  _recursions = 0;
  _waiters++;
  if (self->is_Java_thread()) {
    guarantee(jt->thread_state() == _thread_blocked, "invariant");
    jt->set_suspend_equivalent();
  }
  int rv = simple_wait(self, millis);
  _recursions = save;
  _waiters--;

  guarantee(self == _owner, "invariant");
  if (self->is_Java_thread()) {
    for (;;) {
      if (!jt->handle_special_suspend_equivalent_condition()) {
        break;
      }
      simple_exit(jt);
      jt->java_suspend_self();
      simple_enter(jt);
      jt->set_suspend_equivalent();
    }
    guarantee(jt == _owner, "invariant");
  }

  if (interruptible && jt->is_interrupted(true)) {
    return M_INTERRUPTED;
  }

  return M_OK;
}

int JvmtiRawMonitor::raw_notify(Thread* self) {
  if (self != _owner) {
    return M_ILLEGAL_MONITOR_STATE;
  }
  simple_notify(self, false);
  return M_OK;
}

int JvmtiRawMonitor::raw_notifyAll(Thread* self) {
  if (self != _owner) {
    return M_ILLEGAL_MONITOR_STATE;
  }
  simple_notify(self, true);
  return M_OK;
}
