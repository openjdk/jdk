/*
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

#ifndef SHARE_GC_G1_G1EVACFAILUREPARSCANTASK_HPP
#define SHARE_GC_G1_G1EVACFAILUREPARSCANTASK_HPP

#include "gc/shared/taskqueue.hpp"
#include "memory/allocation.hpp"

class HeapRegion;

class G1EvacFailureParScanTask {
  friend class G1EvacFailureParScanState;

  HeapRegion* _region;
  // The previous live object end before this task.
  // It could be bottom of the region if this task is the first part of an region.
  HeapWord* _previous_object_end;
  // Inclusive
  uint _start;
  // Exclusive
  uint _end;
  // If this is the task including the last part of an region.
  bool _last;

public:
  G1EvacFailureParScanTask(HeapRegion* region = nullptr,
                           HeapWord* previous_obj = nullptr,
                           uint start = -1,
                           uint end = -1,
                           bool last = false);

  G1EvacFailureParScanTask& operator=(const G1EvacFailureParScanTask& o);

  HeapRegion* region() { return _region; }
  HeapWord* previous_object_end() { return _previous_object_end; }
  uint start() { return _start; }
  uint end() { return _end; }
  bool last() { return _last; }

  DEBUG_ONLY(void verify();)
};

typedef OverflowTaskQueue<G1EvacFailureParScanTask, mtGC> G1EvacFailureParScanTasksQueue;
typedef GenericTaskQueueSet<G1EvacFailureParScanTasksQueue, mtGC> G1EvacFailureParScanTasksQueueSet;

#endif // SHARE_GC_G1_G1EVACFAILUREPARSCANTASK_HPP
