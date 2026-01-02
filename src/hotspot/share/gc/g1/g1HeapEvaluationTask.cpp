/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1HeapEvaluationTask.hpp"
#include "gc/g1/g1HeapSizingPolicy.hpp"
#include "gc/g1/g1ServiceThread.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

G1HeapEvaluationTask::G1HeapEvaluationTask(G1CollectedHeap* g1h, G1HeapSizingPolicy* heap_sizing_policy) :
  G1ServiceTask("G1 Heap Evaluation Task"),
  _g1h(g1h),
  _heap_sizing_policy(heap_sizing_policy) {
}

void G1HeapEvaluationTask::execute() {
  log_debug(gc, sizing)("Starting uncommit evaluation.");

  size_t resize_amount;

  // Use SuspendibleThreadSetJoiner for proper synchronization during heap evaluation
  // This ensures we don't race with concurrent GC operations while scanning region states
  {
    SuspendibleThreadSetJoiner sts;
    resize_amount = _heap_sizing_policy->evaluate_heap_resize_for_uncommit();
  }

  static int evaluation_count = 0;

  if (resize_amount > 0) {
    log_info(gc, sizing)("Uncommit evaluation: shrinking heap by %zuMB using time-based selection.", resize_amount / M);
    log_debug(gc, sizing)("Uncommit evaluation: policy recommends shrinking by %zuB.", resize_amount);
    // Request VM operation outside of suspendible thread set.
    _g1h->request_heap_shrink(resize_amount);
  } else {
    if (++evaluation_count % 10 == 0) { // Log every 10th evaluation when no action taken.
      log_info(gc, sizing)("Uncommit evaluation: no heap uncommit needed (evaluation #%d)", evaluation_count);
    }
  }

  // Schedule the next evaluation.
  schedule(G1TimeBasedEvaluationIntervalMillis);
}
