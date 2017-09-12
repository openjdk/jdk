/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/concurrentGCPhaseManager.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/thread.hpp"

#define assert_ConcurrentGC_thread() \
  assert(Thread::current()->is_ConcurrentGC_thread(), "precondition")

#define assert_not_enter_unconstrained(phase) \
  assert((phase) != UNCONSTRAINED_PHASE, "Cannot enter \"unconstrained\" phase")

#define assert_manager_is_tos(manager, stack, kind)  \
  assert((manager) == (stack)->_top, kind " manager is not top of stack")

ConcurrentGCPhaseManager::Stack::Stack() :
  _requested_phase(UNCONSTRAINED_PHASE),
  _top(NULL)
{ }

ConcurrentGCPhaseManager::ConcurrentGCPhaseManager(int phase, Stack* stack) :
  _phase(phase),
  _active(true),
  _prev(NULL),
  _stack(stack)
{
  assert_ConcurrentGC_thread();
  assert_not_enter_unconstrained(phase);
  assert(stack != NULL, "precondition");
  MonitorLockerEx ml(CGCPhaseManager_lock, Mutex::_no_safepoint_check_flag);
  if (stack->_top != NULL) {
    assert(stack->_top->_active, "precondition");
    _prev = stack->_top;
  }
  stack->_top = this;
  ml.notify_all();
}

ConcurrentGCPhaseManager::~ConcurrentGCPhaseManager() {
  assert_ConcurrentGC_thread();
  MonitorLockerEx ml(CGCPhaseManager_lock, Mutex::_no_safepoint_check_flag);
  assert_manager_is_tos(this, _stack, "This");
  wait_when_requested_impl();
  _stack->_top = _prev;
  ml.notify_all();
}

bool ConcurrentGCPhaseManager::is_requested() const {
  assert_ConcurrentGC_thread();
  MonitorLockerEx ml(CGCPhaseManager_lock, Mutex::_no_safepoint_check_flag);
  assert_manager_is_tos(this, _stack, "This");
  return _active && (_stack->_requested_phase == _phase);
}

bool ConcurrentGCPhaseManager::wait_when_requested_impl() const {
  assert_ConcurrentGC_thread();
  assert_lock_strong(CGCPhaseManager_lock);
  bool waited = false;
  while (_active && (_stack->_requested_phase == _phase)) {
    waited = true;
    CGCPhaseManager_lock->wait(Mutex::_no_safepoint_check_flag);
  }
  return waited;
}

bool ConcurrentGCPhaseManager::wait_when_requested() const {
  assert_ConcurrentGC_thread();
  MonitorLockerEx ml(CGCPhaseManager_lock, Mutex::_no_safepoint_check_flag);
  assert_manager_is_tos(this, _stack, "This");
  return wait_when_requested_impl();
}

void ConcurrentGCPhaseManager::set_phase(int phase, bool force) {
  assert_ConcurrentGC_thread();
  assert_not_enter_unconstrained(phase);
  MonitorLockerEx ml(CGCPhaseManager_lock, Mutex::_no_safepoint_check_flag);
  assert_manager_is_tos(this, _stack, "This");
  if (!force) wait_when_requested_impl();
  _phase = phase;
  ml.notify_all();
}

void ConcurrentGCPhaseManager::deactivate() {
  assert_ConcurrentGC_thread();
  MonitorLockerEx ml(CGCPhaseManager_lock, Mutex::_no_safepoint_check_flag);
  assert_manager_is_tos(this, _stack, "This");
  _active = false;
  ml.notify_all();
}

bool ConcurrentGCPhaseManager::wait_for_phase(int phase, Stack* stack) {
  assert(Thread::current()->is_Java_thread(), "precondition");
  assert(stack != NULL, "precondition");
  MonitorLockerEx ml(CGCPhaseManager_lock);
  // Update request and notify service of change.
  if (stack->_requested_phase != phase) {
    stack->_requested_phase = phase;
    ml.notify_all();
  }

  if (phase == UNCONSTRAINED_PHASE) {
    return true;
  }

  // Wait until phase or IDLE is active.
  while (true) {
    bool idle = false;
    for (ConcurrentGCPhaseManager* manager = stack->_top;
         manager != NULL;
         manager = manager->_prev) {
      if (manager->_phase == phase) {
        return true;            // phase is active.
      } else if (manager->_phase == IDLE_PHASE) {
        idle = true;            // Note idle active, continue search for phase.
      }
    }
    if (idle) {
      return false;             // idle is active and phase is not.
    } else {
      ml.wait();                // Wait for phase change.
    }
  }
}
