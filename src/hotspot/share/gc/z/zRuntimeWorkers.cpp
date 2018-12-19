/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/workgroup.hpp"
#include "gc/z/zRuntimeWorkers.hpp"
#include "gc/z/zThread.hpp"
#include "runtime/mutexLocker.hpp"

class ZRuntimeWorkersInitializeTask : public AbstractGangTask {
private:
  const uint _nworkers;
  uint       _started;
  Monitor    _monitor;

public:
  ZRuntimeWorkersInitializeTask(uint nworkers) :
      AbstractGangTask("ZRuntimeWorkersInitializeTask"),
      _nworkers(nworkers),
      _started(0),
      _monitor(Monitor::leaf,
               "ZRuntimeWorkersInitialize",
               false /* allow_vm_block */,
               Monitor::_safepoint_check_never) {}

  virtual void work(uint worker_id) {
    // Register as runtime worker
    ZThread::set_runtime_worker();

    // Wait for all threads to start
    MonitorLockerEx ml(&_monitor, Monitor::_no_safepoint_check_flag);
    if (++_started == _nworkers) {
      // All threads started
      ml.notify_all();
    } else {
      while (_started != _nworkers) {
        ml.wait(Monitor::_no_safepoint_check_flag);
      }
    }
  }
};

ZRuntimeWorkers::ZRuntimeWorkers() :
    _workers("RuntimeWorker",
             nworkers(),
             false /* are_GC_task_threads */,
             false /* are_ConcurrentGC_threads */) {

  log_info(gc, init)("Runtime Workers: %u parallel", nworkers());

  // Initialize worker threads
  _workers.initialize_workers();
  _workers.update_active_workers(nworkers());
  if (_workers.active_workers() != nworkers()) {
    vm_exit_during_initialization("Failed to create ZRuntimeWorkers");
  }

  // Execute task to register threads as runtime workers. This also
  // helps reduce latency in early safepoints, which otherwise would
  // have to take on any warmup costs.
  ZRuntimeWorkersInitializeTask task(nworkers());
  _workers.run_task(&task);
}

uint ZRuntimeWorkers::nworkers() const {
  return ParallelGCThreads;
}

WorkGang* ZRuntimeWorkers::workers() {
  return &_workers;
}

void ZRuntimeWorkers::threads_do(ThreadClosure* tc) const {
  _workers.threads_do(tc);
}

void ZRuntimeWorkers::print_threads_on(outputStream* st) const {
  _workers.print_worker_threads_on(st);
}
