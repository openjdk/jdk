/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "gc/z/zTask.hpp"
#include "gc/z/zWorkers.hpp"
#include "utilities/globalDefinitions.hpp"

ZTask::Task::Task(ZTask* task, const char* name)
  : WorkerTask(name),
    _task(task),
    _workers(nullptr) {}

void ZTask::Task::work(uint worker_id) {
  if (!_task->account_vtime()) {
    _task->work();
    return;
  }

  jlong start = os::current_thread_cpu_time(true /* user + sys */);
  _task->work();
  jlong elapsed = os::current_thread_cpu_time(true /* user + sys */) - start;
  _workers->add_accumulated_vtime(double(elapsed) / NANOSECS_PER_SEC);
}

ZTask::ZTask(const char* name, bool account_vtime)
  : _worker_task(this, name),
    _account_vtime(account_vtime) {}

const char* ZTask::name() const {
  return _worker_task.name();
}

WorkerTask* ZTask::worker_task() {
  return &_worker_task;
}

void ZTask::set_workers(ZWorkers* workers) {
  _worker_task._workers = workers;
}

bool ZTask::account_vtime() const {
  return _account_vtime;
}

ZRestartableTask::ZRestartableTask(const char* name)
  : ZTask(name) {}

void ZRestartableTask::resize_workers(uint nworkers) {}
