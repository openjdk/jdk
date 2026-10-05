/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_PARTIALARRAYTASKSTEPPER_HPP
#define SHARE_GC_SHARED_PARTIALARRAYTASKSTEPPER_HPP

#include "utilities/globalDefinitions.hpp"

// Helper for partial array chunking tasks.
//
// When an array is large, we want to split it up into chunks that can be
// processed in parallel.  Each task (implicitly) represents such a chunk.  We
// can enqueue multiple tasks at the same time.  We want to enqueue enough
// tasks to benefit from the available parallelism, while not so many as to
// substantially expand the task queues.
class PartialArrayTaskStepper {
public:
  explicit PartialArrayTaskStepper(uint num_workers);

  // Number of continuation tasks to publish after claiming the full-sized
  // chunk starting at start.  Initially there is one queued task.  Every claim
  // must publish the count returned here, even if other claims have advanced
  // the shared index in the meantime.
  inline uint continuation_tasks(size_t start, size_t length, size_t chunk_size) const;

private:
  // Limit on pending tasks for one array, including tasks currently claiming.
  uint _task_limit;
  // Maximum number of new tasks to create when processing an existing task.
  uint _task_fanout;
};

#endif // SHARE_GC_SHARED_PARTIALARRAYTASKSTEPPER_HPP
