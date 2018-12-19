/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zGlobals.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zThread.hpp"
#include "gc/z/zWorkers.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"

static uint calculate_nworkers_based_on_ncpus(double cpu_share_in_percent) {
  return ceil(os::initial_active_processor_count() * cpu_share_in_percent / 100.0);
}

static uint calculate_nworkers_based_on_heap_size(double reserve_share_in_percent) {
  const int nworkers = ((MaxHeapSize * (reserve_share_in_percent / 100.0)) - ZPageSizeMedium) / ZPageSizeSmall;
  return MAX2(nworkers, 1);
}

static uint calculate_nworkers(double cpu_share_in_percent) {
  // Cap number of workers so that we never use more than 10% of the max heap
  // for the reserve. This is useful when using small heaps on large machines.
  return MIN2(calculate_nworkers_based_on_ncpus(cpu_share_in_percent),
              calculate_nworkers_based_on_heap_size(10.0));
}

uint ZWorkers::calculate_nparallel() {
  // Use 60% of the CPUs, rounded up. We would like to use as many threads as
  // possible to increase parallelism. However, using a thread count that is
  // close to the number of processors tends to lead to over-provisioning and
  // scheduling latency issues. Using 60% of the active processors appears to
  // be a fairly good balance.
  return calculate_nworkers(60.0);
}

uint ZWorkers::calculate_nconcurrent() {
  // Use 12.5% of the CPUs, rounded up. The number of concurrent threads we
  // would like to use heavily depends on the type of workload we are running.
  // Using too many threads will have a negative impact on the application
  // throughput, while using too few threads will prolong the GC-cycle and
  // we then risk being out-run by the application. Using 12.5% of the active
  // processors appears to be a fairly good balance.
  return calculate_nworkers(12.5);
}

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

ZWorkers::ZWorkers() :
    _boost(false),
    _workers("ZWorker",
             nworkers(),
             true /* are_GC_task_threads */,
             true /* are_ConcurrentGC_threads */) {

  log_info(gc, init)("Workers: %u parallel, %u concurrent", nparallel(), nconcurrent());

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
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at a safepoint");
  run(task, nparallel());
}

void ZWorkers::run_concurrent(ZTask* task) {
  run(task, nconcurrent());
}

void ZWorkers::threads_do(ThreadClosure* tc) const {
  _workers.threads_do(tc);
}

void ZWorkers::print_threads_on(outputStream* st) const {
  _workers.print_worker_threads_on(st);
}
