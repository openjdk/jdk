/*
 * Copyright (c) 2001, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcId.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/workgroup.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "runtime/atomic.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "runtime/semaphore.hpp"
#include "runtime/thread.inline.hpp"

// WorkGang dispatcher implemented with semaphores.
//
// Semaphores don't require the worker threads to re-claim the lock when they wake up.
// This helps lowering the latency when starting and stopping the worker threads.
class GangTaskDispatcher : public CHeapObj<mtGC> {
  // The task currently being dispatched to the GangWorkers.
  AbstractGangTask* _task;

  volatile uint _started;
  volatile uint _not_finished;

  // Semaphore used to start the GangWorkers.
  Semaphore* _start_semaphore;
  // Semaphore used to notify the coordinator that all workers are done.
  Semaphore* _end_semaphore;

public:
  GangTaskDispatcher() :
      _task(NULL),
      _started(0),
      _not_finished(0),
      _start_semaphore(new Semaphore()),
      _end_semaphore(new Semaphore())
{ }

  ~GangTaskDispatcher() {
    delete _start_semaphore;
    delete _end_semaphore;
  }

  // Coordinator API.

  // Distributes the task out to num_workers workers.
  // Returns when the task has been completed by all workers.
  void coordinator_execute_on_workers(AbstractGangTask* task, uint num_workers) {
    // No workers are allowed to read the state variables until they have been signaled.
    _task         = task;
    _not_finished = num_workers;

    // Dispatch 'num_workers' number of tasks.
    _start_semaphore->signal(num_workers);

    // Wait for the last worker to signal the coordinator.
    _end_semaphore->wait();

    // No workers are allowed to read the state variables after the coordinator has been signaled.
    assert(_not_finished == 0, "%d not finished workers?", _not_finished);
    _task    = NULL;
    _started = 0;

  }

  // Worker API.

  // Waits for a task to become available to the worker.
  // Returns when the worker has been assigned a task.
  WorkData worker_wait_for_task() {
    // Wait for the coordinator to dispatch a task.
    _start_semaphore->wait();

    uint num_started = Atomic::add(&_started, 1u);

    // Subtract one to get a zero-indexed worker id.
    uint worker_id = num_started - 1;

    return WorkData(_task, worker_id);
  }

  // Signal to the coordinator that the worker is done with the assigned task.
  void worker_done_with_task() {
    // Mark that the worker is done with the task.
    // The worker is not allowed to read the state variables after this line.
    uint not_finished = Atomic::sub(&_not_finished, 1u);

    // The last worker signals to the coordinator that all work is completed.
    if (not_finished == 0) {
      _end_semaphore->signal();
    }
  }
};
// Definitions of WorkGang methods.

WorkGang::WorkGang(const char* name, uint workers) :
    _workers(NULL),
    _total_workers(workers),
    _active_workers(0),
    _created_workers(0),
    _name(name),
    _dispatcher(new GangTaskDispatcher())
  { }

WorkGang::~WorkGang() {
  delete _dispatcher;
}

// The current implementation will exit if the allocation
// of any worker fails.
void WorkGang::initialize_workers() {
  log_develop_trace(gc, workgang)("Constructing work gang %s with %u threads", name(), total_workers());
  _workers = NEW_C_HEAP_ARRAY(GangWorker*, total_workers(), mtInternal);

  const uint initial_active_workers = UseDynamicNumberOfGCThreads ? 1 : _total_workers;
  if (update_active_workers(initial_active_workers) != initial_active_workers) {
    vm_exit_during_initialization();
  }
}

GangWorker* WorkGang::create_worker(uint id) {
  if (is_init_completed() && InjectGCWorkerCreationFailure) {
    return NULL;
  }

  GangWorker* const worker = new GangWorker(this, id);

  if (!os::create_thread(worker, os::gc_thread)) {
    delete worker;
    return NULL;
  }

  os::start_thread(worker);

  return worker;
}

uint WorkGang::update_active_workers(uint num_workers) {
  assert(num_workers > 0 && num_workers <= _total_workers,
         "Invalid number of active workers %u (should be 1-%u)",
         num_workers, _total_workers);

  while (_created_workers < num_workers) {
    GangWorker* const worker = create_worker(_created_workers);
    if (worker == NULL) {
      log_error(gc, task)("Failed to create worker thread");
      break;
    }

    _workers[_created_workers] = worker;
    _created_workers++;
  }

  _active_workers = MIN2(_created_workers, num_workers);

  log_trace(gc, task)("%s: using %d out of %d workers", _name, _active_workers, _total_workers);

  return _active_workers;
}

GangWorker* WorkGang::worker(uint i) const {
  // Array index bounds checking.
  GangWorker* result = NULL;
  assert(_workers != NULL, "No workers for indexing");
  assert(i < total_workers(), "Worker index out of bounds");
  result = _workers[i];
  assert(result != NULL, "Indexing to null worker");
  return result;
}

void WorkGang::threads_do(ThreadClosure* tc) const {
  assert(tc != NULL, "Null ThreadClosure");
  uint workers = created_workers();
  for (uint i = 0; i < workers; i++) {
    tc->do_thread(worker(i));
  }
}

void WorkGang::run_task(AbstractGangTask* task) {
  run_task(task, active_workers());
}

void WorkGang::run_task(AbstractGangTask* task, uint num_workers) {
  guarantee(num_workers <= total_workers(),
            "Trying to execute task %s with %u workers which is more than the amount of total workers %u.",
            task->name(), num_workers, total_workers());
  guarantee(num_workers > 0, "Trying to execute task %s with zero workers", task->name());
  uint old_num_workers = _active_workers;
  update_active_workers(num_workers);
  _dispatcher->coordinator_execute_on_workers(task, num_workers);
  update_active_workers(old_num_workers);
}

GangWorker::GangWorker(WorkGang* gang, uint id) {
  _gang = gang;
  set_id(id);
  set_name("%s#%d", gang->name(), id);
}

void GangWorker::run() {
  initialize();
  loop();
}

void GangWorker::initialize() {
  assert(_gang != NULL, "No gang to run in");
  os::set_priority(this, NearMaxPriority);
  log_develop_trace(gc, workgang)("Running gang worker for gang %s id %u", gang()->name(), id());
  assert(!Thread::current()->is_VM_thread(), "VM thread should not be part"
         " of a work gang");
}

WorkData GangWorker::wait_for_task() {
  return gang()->dispatcher()->worker_wait_for_task();
}

void GangWorker::signal_task_done() {
  gang()->dispatcher()->worker_done_with_task();
}

void GangWorker::run_task(WorkData data) {
  GCIdMark gc_id_mark(data._task->gc_id());
  log_develop_trace(gc, workgang)("Running work gang: %s task: %s worker: %u", name(), data._task->name(), data._worker_id);

  data._task->work(data._worker_id);

  log_develop_trace(gc, workgang)("Finished work gang: %s task: %s worker: %u thread: " PTR_FORMAT,
                                  name(), data._task->name(), data._worker_id, p2i(Thread::current()));
}

void GangWorker::loop() {
  while (true) {
    WorkData data = wait_for_task();

    run_task(data);

    signal_task_done();
  }
}
