/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1UncommitRegionTask.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "utilities/ticks.hpp"

G1UncommitRegionTask* G1UncommitRegionTask::_instance = NULL;

G1UncommitRegionTask::G1UncommitRegionTask() :
    G1ServiceTask("G1 Uncommit Region Task"),
    _state(TaskState::inactive) { }

void G1UncommitRegionTask::initialize() {
  assert(_instance == NULL, "Already initialized");
  _instance = new G1UncommitRegionTask();

  // Register the task with the service thread. This will automatically
  // schedule the task so  we change the state to active.
  _instance->set_state(TaskState::active);
  G1CollectedHeap::heap()->service_thread()->register_task(_instance);
}

G1UncommitRegionTask* G1UncommitRegionTask::instance() {
  if (_instance == NULL) {
    initialize();
  }
  return _instance;
}

void G1UncommitRegionTask::activate() {
  assert_at_safepoint_on_vm_thread();

  G1UncommitRegionTask* uncommit_task = instance();
  if (!uncommit_task->is_active()) {
    uncommit_task->set_state(TaskState::active);
    uncommit_task->schedule(0);
  }
}

bool G1UncommitRegionTask::is_active() {
  return _state == TaskState::active;
}

void G1UncommitRegionTask::set_state(TaskState state) {
  assert(_state != state, "Must do a state change");
  _state = state;
  log_trace(gc, heap)("%s, new state: %s", name(), is_active() ? "active" : "inactive");
}

void G1UncommitRegionTask::execute() {
  assert(_state == TaskState::active, "Must be active");

  HeapRegionManager* hrm = G1CollectedHeap::heap()->hrm();

  uint regions_left = UncommitChunkSize;
  uint total_regions = 0;
  uint total_size = 0;

  SuspendibleThreadSetJoiner sts;
  Tickspan total_time;

  do {
    if (sts.should_yield()) {
      sts.yield();
    }

    Ticks start = Ticks::now();
    uint uncommit_count = hrm->uncommit_inactive_regions(regions_left);
    Tickspan chunk_time = (Ticks::now() - start);

    regions_left -= uncommit_count;
    total_regions += uncommit_count;
    total_time += chunk_time;

    if (uncommit_count == 0) {
      break;
    }
  } while (regions_left > 0);

  log_debug(gc, heap)("Concurrent uncommit: regions %u, " SIZE_FORMAT "%s, %1.3fms",
                      total_regions,
                      byte_size_in_proper_unit(total_regions * HeapRegion::GrainBytes),
                      proper_unit_for_byte_size(total_regions * HeapRegion::GrainBytes),
                      total_time.seconds() * 1000);

  // Reschedule if there are more regions to uncommit, otherwise
  // change state to inactive.
  if (hrm->has_inactive_regions()) {
    // No delay, reason to reschedule rather then to loop is to allow
    // other tasks to run without waiting for a full uncommit cycle.
    schedule(0);
  } else {
    set_state(TaskState::inactive);
  }
}
