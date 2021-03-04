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
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1ConcurrentMarkThread.inline.hpp"
#include "gc/g1/g1PeriodicGCTask.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"

bool G1PeriodicGCTask::should_start_periodic_gc() {
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

void G1PeriodicGCTask::check_for_periodic_gc() {
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

G1PeriodicGCTask::G1PeriodicGCTask(const char* name) :
  G1ServiceTask(name) { }

void G1PeriodicGCTask::execute() {
  check_for_periodic_gc();
  // G1PeriodicGCInterval is a manageable flag and can be updated
  // during runtime. If no value is set, wait a second and run it
  // again to see if the value has been updated. Otherwise use the
  // real value provided.
  schedule(G1PeriodicGCInterval == 0 ? 1000 : G1PeriodicGCInterval);
}
