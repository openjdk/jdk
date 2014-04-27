/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/shared/suspendibleThreadSet.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/thread.inline.hpp"

uint   SuspendibleThreadSet::_nthreads          = 0;
uint   SuspendibleThreadSet::_nthreads_stopped  = 0;
bool   SuspendibleThreadSet::_suspend_all       = false;
double SuspendibleThreadSet::_suspend_all_start = 0.0;

void SuspendibleThreadSet::join() {
  MonitorLockerEx ml(STS_lock, Mutex::_no_safepoint_check_flag);
  while (_suspend_all) {
    ml.wait(Mutex::_no_safepoint_check_flag);
  }
  _nthreads++;
}

void SuspendibleThreadSet::leave() {
  MonitorLockerEx ml(STS_lock, Mutex::_no_safepoint_check_flag);
  assert(_nthreads > 0, "Invalid");
  _nthreads--;
  if (_suspend_all) {
    ml.notify_all();
  }
}

void SuspendibleThreadSet::yield() {
  if (_suspend_all) {
    MonitorLockerEx ml(STS_lock, Mutex::_no_safepoint_check_flag);
    if (_suspend_all) {
      _nthreads_stopped++;
      if (_nthreads_stopped == _nthreads) {
        if (ConcGCYieldTimeout > 0) {
          double now = os::elapsedTime();
          guarantee((now - _suspend_all_start) * 1000.0 < (double)ConcGCYieldTimeout, "Long delay");
        }
      }
      ml.notify_all();
      while (_suspend_all) {
        ml.wait(Mutex::_no_safepoint_check_flag);
      }
      assert(_nthreads_stopped > 0, "Invalid");
      _nthreads_stopped--;
      ml.notify_all();
    }
  }
}

void SuspendibleThreadSet::synchronize() {
  assert(Thread::current()->is_VM_thread(), "Must be the VM thread");
  if (ConcGCYieldTimeout > 0) {
    _suspend_all_start = os::elapsedTime();
  }
  MonitorLockerEx ml(STS_lock, Mutex::_no_safepoint_check_flag);
  assert(!_suspend_all, "Only one at a time");
  _suspend_all = true;
  while (_nthreads_stopped < _nthreads) {
    ml.wait(Mutex::_no_safepoint_check_flag);
  }
}

void SuspendibleThreadSet::desynchronize() {
  assert(Thread::current()->is_VM_thread(), "Must be the VM thread");
  MonitorLockerEx ml(STS_lock, Mutex::_no_safepoint_check_flag);
  assert(_nthreads_stopped == _nthreads, "Invalid");
  _suspend_all = false;
  ml.notify_all();
}
