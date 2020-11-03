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
 *
 */

#include "precompiled.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1ConcurrentMarkThread.inline.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1ServiceThread.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "memory/universe.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"

G1SentinelTask::G1SentinelTask() : G1ServiceTask("Sentinel Task") {
  set_time(max_jlong);
  set_next(this);
}

void G1SentinelTask::execute() {
  guarantee(false, "Sentinel service task should never be executed.");
}

// Task handling periodic GCs
class G1PeriodicGCTask : public G1ServiceTask {
  bool should_start_periodic_gc() {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    // If we are currently in a concurrent mark we are going to uncommit memory soon.
    if (g1h->concurrent_mark()->cm_thread()->in_progress()) {
      log_debug(gc, periodic)("Concurrent cycle in progress. Skipping.");
      return false;
    }

    // Check if enough time has passed since the last GC.
    uintx time_since_last_gc = (uintx)g1h->time_since_last_collection().milliseconds();
    if ((time_since_last_gc < G1PeriodicGCInterval)) {
      log_debug(gc, periodic)("Last GC occurred " UINTX_FORMAT "ms before which is below threshold " UINTX_FORMAT "ms. Skipping.",
                              time_since_last_gc, G1PeriodicGCInterval);
      return false;
    }

    // Check if load is lower than max.
    double recent_load;
    if ((G1PeriodicGCSystemLoadThreshold > 0.0f) &&
        (os::loadavg(&recent_load, 1) == -1 || recent_load > G1PeriodicGCSystemLoadThreshold)) {
      log_debug(gc, periodic)("Load %1.2f is higher than threshold %1.2f. Skipping.",
                              recent_load, G1PeriodicGCSystemLoadThreshold);
      return false;
    }
    return true;
  }

  void check_for_periodic_gc(){
    // If disabled, just return.
    if (G1PeriodicGCInterval == 0) {
      return;
    }

    log_debug(gc, periodic)("Checking for periodic GC.");
    if (should_start_periodic_gc()) {
      if (!G1CollectedHeap::heap()->try_collect(GCCause::_g1_periodic_collection)) {
        log_debug(gc, periodic)("GC request denied. Skipping.");
      }
    }
  }
public:
  G1PeriodicGCTask(const char* name) : G1ServiceTask(name) { }

  virtual void execute() {
    check_for_periodic_gc();
    // G1PeriodicGCInterval is a manageable flag and can be updated
    // during runtime. If no value is set, wait a second and run it
    // again to see if the value has been updated. Otherwise use the
    // real value provided.
    schedule(G1PeriodicGCInterval == 0 ? 1000 : G1PeriodicGCInterval);
  }
};

class G1YoungRemSetSamplingClosure : public HeapRegionClosure {
  SuspendibleThreadSetJoiner* _sts;
  size_t _regions_visited;
  size_t _sampled_rs_length;
public:
  G1YoungRemSetSamplingClosure(SuspendibleThreadSetJoiner* sts) :
    HeapRegionClosure(), _sts(sts), _regions_visited(0), _sampled_rs_length(0) { }

  virtual bool do_heap_region(HeapRegion* r) {
    size_t rs_length = r->rem_set()->occupied();
    _sampled_rs_length += rs_length;

    // Update the collection set policy information for this region
    G1CollectedHeap::heap()->collection_set()->update_young_region_prediction(r, rs_length);

    _regions_visited++;

    if (_regions_visited == 10) {
      if (_sts->should_yield()) {
        _sts->yield();
        // A gc may have occurred and our sampling data is stale and further
        // traversal of the collection set is unsafe
        return true;
      }
      _regions_visited = 0;
    }
    return false;
  }

  size_t sampled_rs_length() const { return _sampled_rs_length; }
};

// Task handling young gen remembered set sampling.
class G1RemSetSamplingTask : public G1ServiceTask {
  // Sample the current length of remembered sets for young.
  //
  // At the end of the GC G1 determines the length of the young gen based on
  // how much time the next GC can take, and when the next GC may occur
  // according to the MMU.
  //
  // The assumption is that a significant part of the GC is spent on scanning
  // the remembered sets (and many other components), so this thread constantly
  // reevaluates the prediction for the remembered set scanning costs, and potentially
  // G1Policy resizes the young gen. This may do a premature GC or even
  // increase the young gen size to keep pause time length goal.
  void sample_young_list_rs_length(){
    SuspendibleThreadSetJoiner sts;
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    G1Policy* policy = g1h->policy();

    if (policy->use_adaptive_young_list_length()) {
      G1YoungRemSetSamplingClosure cl(&sts);

      G1CollectionSet* g1cs = g1h->collection_set();
      g1cs->iterate(&cl);

      if (cl.is_complete()) {
        policy->revise_young_list_target_length_if_necessary(cl.sampled_rs_length());
      }
    }
  }
public:
  G1RemSetSamplingTask(const char* name) : G1ServiceTask(name) { }
  virtual void execute() {
    sample_young_list_rs_length();
    schedule(G1ConcRefinementServiceIntervalMillis);
  }
};

G1ServiceThread::G1ServiceThread() :
    ConcurrentGCThread(),
    _monitor(Mutex::nonleaf,
             "G1ServiceThread monitor",
             true,
             Monitor::_safepoint_check_never),
    _task_queue(),
    _vtime_accum(0) {
  set_name("G1 Service");
  create_and_start();
}

void G1ServiceThread::register_task(G1ServiceTask* task, jlong delay) {
  guarantee(!task->is_registered(), "Task already registered");
  guarantee(task->next() == NULL, "Task already in queue");

  log_debug(gc, task)("G1 Service Thread (%s) (register)", task->name());

  // Associate the task with the service thread.
  task->set_service_thread(this);

  // Schedule the task to run after the given delay.
  schedule_task(task, delay);

  // Notify the service thread that there is a new task, thread might
  // be waiting and the newly added task might be first in the list.
  MonitorLocker ml(&_monitor, Mutex::_no_safepoint_check_flag);
  ml.notify();
}

void G1ServiceThread::schedule_task(G1ServiceTask* task, jlong delay_ms) {
  guarantee(task->is_registered(), "Must be registered before scheduled");
  guarantee(task->next() == NULL, "Task already in queue");

  // Schedule task by setting the task time and adding it to queue.
  jlong delay = TimeHelper::millis_to_counter(delay_ms);
  task->set_time(os::elapsed_counter() + delay);

  MutexLocker ml(&_monitor, Mutex::_no_safepoint_check_flag);
  _task_queue.add_ordered(task);

  log_trace(gc, task)("G1 Service Thread (%s) (schedule) @%1.3fs",
                      task->name(), TimeHelper::counter_to_seconds(task->time()));
}

int64_t G1ServiceThread::time_to_next_task_ms() {
  assert(_monitor.owned_by_self(), "Must be owner of lock");
  assert(!_task_queue.is_empty(), "Should not be called for empty list");

  jlong time_diff = _task_queue.peek()->time() - os::elapsed_counter();
  if (time_diff < 0) {
    // Run without sleeping.
    return 0;
  }

  // Return sleep time in milliseconds.
  return (int64_t) TimeHelper::counter_to_millis(time_diff);
}

void G1ServiceThread::sleep_before_next_cycle() {
  if (should_terminate()) {
    return;
  }

  MonitorLocker ml(&_monitor, Mutex::_no_safepoint_check_flag);
  if (_task_queue.is_empty()) {
    // Sleep until new task is registered if no tasks available.
    log_trace(gc, task)("G1 Service Thread (wait for new tasks)");
    ml.wait(0);
  } else {
    int64_t sleep_ms = time_to_next_task_ms();
    if (sleep_ms > 0) {
      log_trace(gc, task)("G1 Service Thread (wait) %1.3fs", sleep_ms / 1000.0);
      ml.wait(sleep_ms);
    }
  }
}

G1ServiceTask* G1ServiceThread::pop_due_task() {
  MutexLocker ml(&_monitor, Mutex::_no_safepoint_check_flag);
  if (_task_queue.is_empty() || time_to_next_task_ms() != 0) {
    return NULL;
  }

  return _task_queue.pop();
}

void G1ServiceThread::run_task(G1ServiceTask* task) {
  double start = os::elapsedTime();
  double vstart = os::elapsedVTime();

  log_debug(gc, task, start)("G1 Service Thread (%s) (run)", task->name());
  task->execute();

  double duration = os::elapsedTime() - start;
  double vduration = os::elapsedVTime() - vstart;
  log_debug(gc, task)("G1 Service Thread (%s) (run) %1.3fms (cpu: %1.3fms)",
                      task->name(), duration * MILLIUNITS, vduration * MILLIUNITS);
}

void G1ServiceThread::run_service() {
  double vtime_start = os::elapsedVTime();

  // Setup the tasks handeled by the service thread and
  // add them to the task list.
  G1PeriodicGCTask gc_task("Periodic GC Task");
  register_task(&gc_task);

  G1RemSetSamplingTask remset_task("Remembered Set Sampling Task");
  register_task(&remset_task);

  while (!should_terminate()) {
    G1ServiceTask* task = pop_due_task();
    if (task != NULL) {
      run_task(task);
    }

    if (os::supports_vtime()) {
      _vtime_accum = (os::elapsedVTime() - vtime_start);
    } else {
      _vtime_accum = 0.0;
    }
    sleep_before_next_cycle();
  }
}

void G1ServiceThread::stop_service() {
  MonitorLocker ml(&_monitor, Mutex::_no_safepoint_check_flag);
  ml.notify();
}

G1ServiceTask::G1ServiceTask(const char* name) :
  _time(),
  _name(name),
  _next(NULL),
  _service_thread(NULL) { }

void G1ServiceTask::set_service_thread(G1ServiceThread* thread) {
  _service_thread = thread;
}

bool G1ServiceTask::is_registered() {
  return _service_thread != NULL;
}

void G1ServiceTask::schedule(jlong delay_ms) {
  _service_thread->schedule_task(this, delay_ms);
}

const char* G1ServiceTask::name() {
  return _name;
}

void G1ServiceTask::set_time(jlong time) {
  assert(_next == NULL, "Not allowed to update time while in queue");
  _time = time;
}

jlong G1ServiceTask::time() {
  return _time;
}

void G1ServiceTask::set_next(G1ServiceTask* next) {
  _next = next;
}

G1ServiceTask* G1ServiceTask::next() {
  return _next;
}

G1ServiceTaskQueue::G1ServiceTaskQueue() : _sentinel() { }

G1ServiceTask* G1ServiceTaskQueue::pop() {
  verify_task_queue();

  G1ServiceTask* task = _sentinel.next();
  _sentinel.set_next(task->next());
  task->set_next(NULL);

  return task;
}

G1ServiceTask* G1ServiceTaskQueue::peek() {
  verify_task_queue();
  return _sentinel.next();
}

bool G1ServiceTaskQueue::is_empty() {
  return &_sentinel == _sentinel.next();
}

void G1ServiceTaskQueue::add_ordered(G1ServiceTask* task) {
  assert(task != NULL, "not a valid task");
  assert(task->next() == NULL, "invariant");
  assert(task->time() != max_jlong, "invalid time for task");

  G1ServiceTask* current = &_sentinel;
  while (task->time() >= current->next()->time()) {
    assert(task != current, "Task should only be added once.");
    current = current->next();
  }

  // Update the links.
  task->set_next(current->next());
  current->set_next(task);

  verify_task_queue();
}

#ifdef ASSERT
void G1ServiceTaskQueue::verify_task_queue() {
  G1ServiceTask* cur = _sentinel.next();

  assert(cur != &_sentinel, "Should never try to verify empty queue");
  while (cur != &_sentinel) {
    G1ServiceTask* next = cur->next();
    assert(cur->time() <= next->time(),
           "Tasks out of order, prev: %s (%1.3fs), next: %s (%1.3fs)",
           cur->name(), TimeHelper::counter_to_seconds(cur->time()), next->name(), TimeHelper::counter_to_seconds(next->time()));

    assert(cur != next, "Invariant");
    cur = next;
  }
}
#endif
