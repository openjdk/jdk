/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Huawei and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1EVACFAILUREPARSCANSTATE_HPP
#define SHARE_GC_G1_G1EVACFAILUREPARSCANSTATE_HPP

#include "gc/g1/g1EvacFailureParScanTask.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "gc/shared/workerThread.hpp"
#include "memory/allocation.hpp"

class G1EvacFailureRegions;
class G1RemoveSelfForwardClosure;
class HeapRegionClaimer;
class TaskTerminator;

class G1EvacFailureParScanState {
  G1EvacFailureRegions* _evac_failure_regions;

  G1EvacFailureParScanTasksQueueSet* _task_queues;
  uint _worker_id;
  G1EvacFailureParScanTasksQueue* _task_queue;

  TaskTerminator* _terminator;

  HeapRegionClaimer* _prev_claimer;
  HeapRegionClaimer* _post_claimer;

  void dispatch_task(G1EvacFailureParScanTask& task, G1RemoveSelfForwardClosure& closure);

  void trim_queue(G1RemoveSelfForwardClosure& closure);

  void trim_queue_to_threshold(uint threshold, G1RemoveSelfForwardClosure& closure);

  void steal_and_trim_queue(G1RemoveSelfForwardClosure& closure);

  inline bool offer_termination() {
    return (_terminator == nullptr) ? true : _terminator->offer_termination();
  }

  void prev_scan();
  void scan();
  void post_scan();

public:
  G1EvacFailureParScanState(G1EvacFailureRegions* evac_failure_regions,
                            G1EvacFailureParScanTasksQueueSet* queues,
                            TaskTerminator* terminator,
                            uint worker_id,
                            HeapRegionClaimer* pre_claimer,
                            HeapRegionClaimer* post_claimer);

  void do_void();
};

#endif //SHARE_GC_G1_G1EVACFAILUREPARSCANSTATE_HPP
