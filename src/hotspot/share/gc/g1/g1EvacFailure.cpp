/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1EvacFailure.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1EvacFailureRegions.hpp"
#include "gc/g1/g1EvacFailureParScanState.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "utilities/stack.inline.hpp"

G1ParRemoveSelfForwardPtrsTask::G1ParRemoveSelfForwardPtrsTask(G1EvacFailureRegions* evac_failure_regions) :
    WorkerTask("G1 Remove Self-forwarding Pointers"),
    _hrclaimer(G1CollectedHeap::heap()->workers()->active_workers()),
    _hrclaimer_2(G1CollectedHeap::heap()->workers()->active_workers()),
    _evac_failure_regions(evac_failure_regions),
    _task_queues(new G1EvacFailureParScanTasksQueueSet(_evac_failure_regions->num_regions_failed_evacuation())),
    _terminator(MIN2(_task_queues->size(), G1CollectedHeap::heap()->workers()->active_workers()), _task_queues) {
  for (uint i = 0; i < _task_queues->size(); i++) {
    G1EvacFailureParScanTasksQueue* q = new G1EvacFailureParScanTasksQueue();
    _task_queues->register_queue(i, q);
  }
}

G1ParRemoveSelfForwardPtrsTask::~G1ParRemoveSelfForwardPtrsTask() {
  if (_task_queues == nullptr) return;
  for (uint i = 0; i < _task_queues->size(); i++) {
    delete _task_queues->queue(i);
  }
  delete _task_queues;
}

void G1ParRemoveSelfForwardPtrsTask::work(uint worker_id) {
  if (worker_id >= _task_queues->size()) {
    return;
  }
  G1EvacFailureParScanState scan_state(_evac_failure_regions, _task_queues, &_terminator, worker_id, &_hrclaimer, &_hrclaimer_2);
  scan_state.do_void();
}
