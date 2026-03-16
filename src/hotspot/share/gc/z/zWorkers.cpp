/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zWorkers.hpp"
#include "runtime/java.hpp"

static const char* workers_name(ZGenerationId id) {
  return (id == ZGenerationId::young) ? "ZWorkerYoung" : "ZWorkerOld";
}

static const char* generation_name(ZGenerationId id) {
  return (id == ZGenerationId::young) ? "Young" : "Old";
}

static uint max_nworkers(ZGenerationId id) {
  return id == ZGenerationId::young ? ZYoungGCThreads : ZOldGCThreads;
}

ZWorkers::ZWorkers(ZGenerationId id, ZStatWorkers* stats)
  : _workers(workers_name(id),
             max_nworkers(id)),
    _generation_name(generation_name(id)),
    _resize_lock(),
    _requested_nworkers(0),
    _is_active(false),
    _stats(stats) {

  log_info_p(gc, init)("GC Workers for %s Generation: %u (%s)",
                       _generation_name,
                       _workers.max_workers(),
                       UseDynamicNumberOfGCThreads ? "dynamic" : "static");

  // Initialize worker threads
  _workers.initialize_workers();
  _workers.set_active_workers(_workers.max_workers());
  if (_workers.active_workers() != _workers.max_workers()) {
    vm_exit_during_initialization("Failed to create ZWorkers");
  }
}

bool ZWorkers::is_active() const {
  return _is_active;
}

uint ZWorkers::active_workers() const {
  return _workers.active_workers();
}

void ZWorkers::set_active_workers(uint nworkers) {
  log_info(gc, task)("Using %u Workers for %s Generation", nworkers, _generation_name);
  ZLocker<ZLock> locker(&_resize_lock);
  _workers.set_active_workers(nworkers);
}

void ZWorkers::set_active() {
  ZLocker<ZLock> locker(&_resize_lock);
  _is_active = true;
  _requested_nworkers = 0;
}

void ZWorkers::set_inactive() {
  ZLocker<ZLock> locker(&_resize_lock);
  _is_active = false;
}

void ZWorkers::run(ZTask* task) {
  log_debug(gc, task)("Executing %s using %s with %u workers", task->name(), _workers.name(), active_workers());

  {
    ZLocker<ZLock> locker(&_resize_lock);
    _stats->at_start(active_workers());
  }

  _workers.run_task(task->worker_task());

  {
    ZLocker<ZLock> locker(&_resize_lock);
    _stats->at_end();
  }
}

void ZWorkers::run(ZRestartableTask* task) {
  for (;;) {
    // Run task
    run(static_cast<ZTask*>(task));

    ZLocker<ZLock> locker(&_resize_lock);
    if (_requested_nworkers == 0) {
      // Task completed
      return;
    }

    // Restart task with requested number of active workers
    _workers.set_active_workers(_requested_nworkers);
    task->resize_workers(active_workers());
    _requested_nworkers = 0;
  }
}

void ZWorkers::run_all(ZTask* task) {
  // Get and set number of active workers
  const uint prev_active_workers = _workers.active_workers();
  _workers.set_active_workers(_workers.max_workers());

  // Execute task using all workers
  log_debug(gc, task)("Executing %s using %s with %u workers", task->name(), _workers.name(), active_workers());
  _workers.run_task(task->worker_task());

  // Restore number of active workers
  _workers.set_active_workers(prev_active_workers);
}

void ZWorkers::threads_do(ThreadClosure* tc) const {
  _workers.threads_do(tc);
}

ZLock* ZWorkers::resizing_lock() {
  return &_resize_lock;
}

void ZWorkers::request_resize_workers(uint nworkers) {
  assert(nworkers != 0, "Never ask for zero workers");

  ZLocker<ZLock> locker(&_resize_lock);

  if (_requested_nworkers == nworkers) {
    // Already requested
    return;
  }

  if (_workers.active_workers() == nworkers) {
    // Already the right amount of threads
    return;
  }

  log_info(gc, task)("Adjusting Workers for %s Generation: %u -> %u",
                     _generation_name, _workers.active_workers(), nworkers);

  _requested_nworkers = nworkers;
}
