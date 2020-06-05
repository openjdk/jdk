/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zThread.hpp"
#include "gc/z/zWorkers.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"

class ZWorkersInitializeTask : public ZTask {
private:
  const uint _nworkers;
  uint       _started;
  Monitor    _monitor;

public:
  ZWorkersInitializeTask(uint nworkers) :
      ZTask("ZWorkersInitializeTask"),
      _nworkers(nworkers),
      _started(0),
      _monitor(Monitor::leaf,
               "ZWorkersInitialize",
               false /* allow_vm_block */,
               Monitor::_safepoint_check_never) {}

  virtual void work() {
    // Register as worker
    ZThread::set_worker();

    // Wait for all threads to start
    MonitorLocker ml(&_monitor, Monitor::_no_safepoint_check_flag);
    if (++_started == _nworkers) {
      // All threads started
      ml.notify_all();
    } else {
      while (_started != _nworkers) {
        ml.wait();
      }
    }
  }
};

ZWorkers::ZWorkers() :
    _boost(false),
    _workers("ZWorker",
             nworkers(),
             true /* are_GC_task_threads */,
             true /* are_ConcurrentGC_threads */) {

  log_info_p(gc, init)("Workers: %u parallel, %u concurrent", nparallel(), nconcurrent());

  // Initialize worker threads
  _workers.initialize_workers();
  _workers.update_active_workers(nworkers());
  if (_workers.active_workers() != nworkers()) {
    vm_exit_during_initialization("Failed to create ZWorkers");
  }

  // Execute task to register threads as workers. This also helps
  // reduce latency in early GC pauses, which otherwise would have
  // to take on any warmup costs.
  ZWorkersInitializeTask task(nworkers());
  run(&task, nworkers());
}

void ZWorkers::set_boost(bool boost) {
  if (boost) {
    log_debug(gc)("Boosting workers");
  }

  _boost = boost;
}

void ZWorkers::run(ZTask* task, uint nworkers) {
  log_debug(gc, task)("Executing Task: %s, Active Workers: %u", task->name(), nworkers);
  _workers.update_active_workers(nworkers);
  _workers.run_task(task->gang_task());
}

void ZWorkers::run_parallel(ZTask* task) {
  run(task, nparallel());
}

void ZWorkers::run_concurrent(ZTask* task) {
  run(task, nconcurrent());
}

void ZWorkers::threads_do(ThreadClosure* tc) const {
  _workers.threads_do(tc);
}
