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

// Task handling periodic GCs
class G1PeriodicGCTask : public G1ServiceTask {
  double _last_attempt;

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
    if ((os::elapsedTime() - _last_attempt) > (G1PeriodicGCInterval / 1000.0)) {
      log_debug(gc, periodic)("Checking for periodic GC.");
      if (should_start_periodic_gc()) {
        if (!G1CollectedHeap::heap()->try_collect(GCCause::_g1_periodic_collection)) {
          log_debug(gc, periodic)("GC request denied. Skipping.");
        }
      }
      _last_attempt = os::elapsedTime();
    }
  }
public:
  G1PeriodicGCTask(const char* name) :
    G1ServiceTask(name),
    _last_attempt(os::elapsedTime()) { }

  virtual void execute() {
    check_for_periodic_gc();
  }

  virtual double interval() {
    // G1PeriodicGCInterval is a manageable flag and can be updated
    // during runtime. If no value is set, wait a second and run it
    // again to see if the value has been updated. Otherwise use the
    // real value provided.
    return G1PeriodicGCInterval == 0 ? 1.0 : (G1PeriodicGCInterval / 1000.0);
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
  }

  virtual double interval() {
    return G1ConcRefinementServiceIntervalMillis/1000.0;
  }
};

G1ServiceThread::G1ServiceThread() :
    ConcurrentGCThread(),
    _monitor(Mutex::nonleaf,
             "G1ServiceThread monitor",
             true,
             Monitor::_safepoint_check_never),
    _task_list(),
    _vtime_accum(0) {
  set_name("G1 Service");
  create_and_start();
}

void G1ServiceThread::register_task(G1ServiceTask* task) {
  // Add the task to the list.
  _task_list.add_ordered(task);

  log_debug(gc, task)("G1 Service Thread (%s) (register)", task->name());

  // Notify the service thread that there is a new task, thread might
  // be waiting and the newly added task might be first in the list.
  MonitorLocker ml(&_monitor, Mutex::_no_safepoint_check_flag);
  _monitor.notify();
}

int64_t G1ServiceThread::timeout_to_next_task() {
  double time_to_next_task = _task_list.peek_first()->time() - os::elapsedTime();
  return (int64_t) (time_to_next_task * MILLIUNITS);
}

void G1ServiceThread::sleep_before_next_cycle() {
  MonitorLocker ml(&_monitor, Mutex::_no_safepoint_check_flag);
  if (!should_terminate()) {
    int64_t timeout = timeout_to_next_task();
    if (timeout > 0) {
      // Log in seconds for consistency.
      log_trace(gc, task)("G1 Service Thread (wait) %1.3fs", timeout/1000.0);
      ml.wait(timeout);
    }
  }
}

void G1ServiceThread::reschedule_task(G1ServiceTask* task) {
  task->set_time(os::elapsedTime() + task->interval());
  _task_list.add_ordered(task);
  log_trace(gc, task)("G1 Service Thread (%s) (schedule) @%1.3fs", task->name(), task->time());;
}

void G1ServiceThread::run_task(G1ServiceTask* task) {
  double start = os::elapsedVTime();

  log_debug(gc, task, start)("G1 Service Thread (%s) (run)", task->name());
  task->execute();

  double duration = os::elapsedVTime() - start;
  log_debug(gc, task)("G1 Service Thread (%s) (run) %1.3fms", task->name(), duration * MILLIUNITS);
}

void G1ServiceThread::run_tasks() {
  // Execute tasks that are due. Need to check for termination to avoid
  // running forever if task reschedule with very short interval.
  while (!should_terminate() && timeout_to_next_task() <= 0) {
    G1ServiceTask* task = _task_list.pop_first();

    run_task(task);
    reschedule_task(task);
  }
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
    run_tasks();

    if (os::supports_vtime()) {
      _vtime_accum = (os::elapsedVTime() - vtime_start);
    } else {
      _vtime_accum = 0.0;
    }
    sleep_before_next_cycle();
  }
}

void G1ServiceThread::stop_service() {
  MutexLocker x(&_monitor, Mutex::_no_safepoint_check_flag);
  _monitor.notify();
}

void G1ServiceTask::execute() {
  guarantee(false, "Sentinel service task should never be executed.");
}

double G1ServiceTask::interval() {
  guarantee(false, "Sentinel service task should never be sceduled.");
  return 0.0;
}

G1ServiceTaskList::G1ServiceTaskList() :
    _sentinel("Sentinel"),
    _lock(Mutex::leaf,
          "G1ServiceTaskList lock",
          Mutex::_allow_vm_block_flag,
          Mutex::_safepoint_check_never) {
  _sentinel.set_next(&_sentinel);
  _sentinel.set_time(DBL_MAX);
}

G1ServiceTask* G1ServiceTaskList::pop_first() {
  MutexLocker ml(&_lock, Monitor::_no_safepoint_check_flag);
  verify_task_list();

  G1ServiceTask* task = _sentinel.next();
  _sentinel.set_next(task->next());
  task->set_next(NULL);

  return task;
}

G1ServiceTask* G1ServiceTaskList::peek_first() {
  MutexLocker ml(&_lock, Monitor::_no_safepoint_check_flag);
  verify_task_list();
  return _sentinel.next();
}

void G1ServiceTaskList::add_ordered(G1ServiceTask* task) {
  MutexLocker ml(&_lock, Monitor::_no_safepoint_check_flag);
  assert(task->next() == NULL, "invariant");
  assert(task->time() != DBL_MAX, "invalid time for task");

  G1ServiceTask* current = &_sentinel;
  while (task->time() >= current->next()->time()) {
    assert(task != current, "Task should only be added once.");
    current = current->next();
  }

  // Update the links.
  task->set_next(current->next());
  current->set_next(task);

  verify_task_list();
}

#ifdef ASSERT
void G1ServiceTaskList::verify_task_list() {
  G1ServiceTask* cur = _sentinel.next();
  while (cur != &_sentinel) {
    G1ServiceTask* next = cur->next();
    assert(cur->time() <= next->time(),
           "Tasks out of order, prev: %s (%1.3fs), next: %s (%1.3fs)",
           cur->name(), cur->time(), next->name(), next->time());

    assert(cur != next, "Invariant");
    cur = next;
  }
}
#endif
