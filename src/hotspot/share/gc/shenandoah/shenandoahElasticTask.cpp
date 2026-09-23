/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shenandoah/shenandoahController.hpp"
#include "gc/shenandoah/shenandoahElasticTask.hpp"

ShenandoahElasticTaskCoordinator::ShenandoahElasticTaskCoordinator()
  : _admitted(0)
  , _capable(0)
  , _gate(Mutex::safepoint - 5, "ShenandoahElasticWorkers_lock", true) {
}

void ShenandoahElasticTaskCoordinator::reset(const ShenandoahController* controller) {
  MonitorLocker locker(&_gate, Mutex::_no_safepoint_check_flag);
  assert(is_done(), "Cannot reset an active phase");
  _admitted = _capable = controller->concurrent_worker_count();
}

bool ShenandoahElasticTaskCoordinator::is_done() const {
  return _capable == 0;
}

void ShenandoahElasticTaskCoordinator::increase_workers(size_t max_concurrent_workers) {
  MonitorLocker locker(&_gate, Mutex::_no_safepoint_check_flag);
  if (is_done()) {
    // This phase is already complete, we cannot admit more
    // workers or allow another thread to begin work.
    return;
  }

  assert(max_concurrent_workers >= _admitted, "Cannot decrease");
  const uint delta = max_concurrent_workers - _admitted;
  if (delta > 0) {
    _admitted += delta;
    _capable += delta;
    locker.notify_all();
  }
}

void ShenandoahElasticTaskCoordinator::complete() {
  MonitorLocker locker(&_gate, Mutex::_no_safepoint_check_flag);
  assert(_capable > 0, "Too many completions");
  _capable -= 1;
  if (is_done()) {
    // Capable workers are done, wake up reserved workers to exit
    locker.notify_all();
  }
}

bool ShenandoahElasticTaskCoordinator::wait_for_work() {
  MonitorLocker locker(&_gate, Mutex::_no_safepoint_check_flag);
  while (WorkerThread::worker_id() >= _admitted && _capable > 0) {
    // We are in reserve and other threads are still working
    locker.wait();
  }
  return !is_done();
}
