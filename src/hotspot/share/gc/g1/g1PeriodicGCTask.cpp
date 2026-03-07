/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1ConcurrentMarkThread.inline.hpp"
#include "gc/g1/g1GCCounters.hpp"
#include "gc/g1/g1PeriodicGCTask.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "logging/log.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"

// Helper predicates for behavior table logic.

bool G1PeriodicGCTask::is_periodic_interval_set_on_cmdline() {
  return !FLAG_IS_DEFAULT(G1PeriodicGCInterval);
}

bool G1PeriodicGCTask::is_periodic_gc_explicitly_enabled() {
  return is_periodic_interval_set_on_cmdline() && G1PeriodicGCInterval > 0;
}

bool G1PeriodicGCTask::is_periodic_gc_explicitly_disabled() {
  return is_periodic_interval_set_on_cmdline() && G1PeriodicGCInterval == 0;
}

bool G1PeriodicGCTask::is_load_threshold_enabled() {
  return G1PeriodicGCSystemLoadThreshold > 0.0;
}

uintx G1PeriodicGCTask::get_effective_periodic_interval() {
  // Priority order handles both cmdline and runtime flag changes:
  //
  // 1. If G1PeriodicGCInterval > 0: use it (works for cmdline or runtime set)
  // 2. If G1PeriodicGCInterval == 0 AND was set on cmdline: explicitly disabled
  // 3. If G1PeriodicGCInterval == 0 AND was NOT set on cmdline: use ergonomic
  //
  // This means:
  // - Cmdline -XX:G1PeriodicGCInterval=5000 -> uses 5000
  // - Cmdline -XX:G1PeriodicGCInterval=0 -> disabled forever
  // - No cmdline, runtime sets to 5000 -> uses 5000
  // - No cmdline, runtime sets to 5000, then 0 -> falls back to ergonomic

  if (G1PeriodicGCInterval > 0) {
    // Explicit non-zero interval takes priority (cmdline or runtime).
    return G1PeriodicGCInterval;
  }

  // G1PeriodicGCInterval is 0 - check why.
  if (is_periodic_interval_set_on_cmdline()) {
    // User explicitly disabled on cmdline - respect that.
    return 0;
  }

  // Interval was never set on cmdline and is currently 0.
  // Use ergonomic interval if configured (JDK-8213198 enhancement).
  return G1PeriodicGCErgonomicInterval;
}

bool G1PeriodicGCTask::should_use_concurrent_periodic_gc() {
  // See behavior table in header file.

  // Check if we're in ergonomic mode (interval not set on cmdline AND currently 0).
  bool using_ergonomic = !is_periodic_interval_set_on_cmdline() && G1PeriodicGCInterval == 0;
  if (using_ergonomic) {
    // Always use concurrent GC for ergonomic periodic GC (string table cleanup).
    return true;
  }

  // Y (== 0) case: Explicitly disabled - this shouldn't be called,
  // but return false for safety.
  if (G1PeriodicGCInterval == 0) {
    return false;
  }

  // Y (!= 0) cases: Interval explicitly set to non-zero value.
  if (G1PeriodicGCInvokesConcurrent) {
    // Y + Y: Always concurrent.
    return true;
  }

  // Y + N cases: Concurrent flag is false.
  if (is_load_threshold_enabled()) {
    // Y + N + Y: Full GC when load < threshold, Concurrent when load >= threshold.
    double recent_load;
    if (os::loadavg(&recent_load, 1) != -1) {
      return recent_load >= G1PeriodicGCSystemLoadThreshold;
    }
    // If load check fails, default to full GC.
    return false;
  }

  // Y + N + N: Always full GC.
  return false;
}

bool G1PeriodicGCTask::should_start_periodic_gc(G1CollectedHeap* g1h,
                                                G1GCCounters* counters) {
  // Ensure no GC safepoints while we're doing the checks, to avoid data races.
  SuspendibleThreadSetJoiner sts;

  // If we are currently in a concurrent mark we are going to uncommit memory soon.
  if (g1h->concurrent_mark()->in_progress()) {
    log_debug(gc, periodic)("Concurrent cycle in progress. Skipping.");
    return false;
  }

  // Check if enough time has passed since the last GC.
  uintx effective_interval = get_effective_periodic_interval();
  uintx time_since_last_gc = (uintx)g1h->time_since_last_collection().milliseconds();
  if (time_since_last_gc < effective_interval) {
    log_debug(gc, periodic)("Last GC occurred %zums before which is below threshold %zums. Skipping.",
                            time_since_last_gc, effective_interval);
    return false;
  }

  // Load threshold check - only applies when interval is explicitly set.
  if (is_periodic_interval_set_on_cmdline() && is_load_threshold_enabled()) {
    double recent_load;
    if (os::loadavg(&recent_load, 1) == -1) {
      G1PeriodicGCSystemLoadThreshold = 0.0;
      log_warning(gc, periodic)("System loadavg() call failed, "
                                "disabling G1PeriodicGCSystemLoadThreshold check.");
      // Fall through and start the periodic GC.
    } else if (recent_load > G1PeriodicGCSystemLoadThreshold) {
      // Only skip GC if concurrent flag is true (Y + Y + Y case).
      if (G1PeriodicGCInvokesConcurrent) {
        log_debug(gc, periodic)("Load %1.2f is higher than threshold %1.2f. Skipping.",
                                recent_load, G1PeriodicGCSystemLoadThreshold);
        return false;
      }
      // For Y + N + Y case, proceed - GC type decision handles load.
    }
  }

  // Record counters with GC safepoints blocked, to get a consistent snapshot.
  // These are passed to try_collect so a GC between our release of the
  // STS-joiner and the GC VMOp can be detected and cancel the request.
  *counters = G1GCCounters(g1h);
  return true;
}

void G1PeriodicGCTask::check_for_periodic_gc() {
  uintx effective_interval = get_effective_periodic_interval();
  log_debug(gc, periodic)("Checking for periodic GC (interval: %zu ms).", effective_interval);

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1GCCounters counters;

  if (should_start_periodic_gc(g1h, &counters)) {
    bool trigger_concurrent = should_use_concurrent_periodic_gc();

    // Check if we're in ergonomic mode (interval not set on cmdline).
    bool using_ergonomic = !is_periodic_interval_set_on_cmdline() && G1PeriodicGCInterval == 0;

    if (trigger_concurrent) {
      if (using_ergonomic) {
        log_debug(gc, periodic)("Triggering periodic concurrent GC for string table cleanup.");
      } else {
        log_debug(gc, periodic)("Triggering periodic concurrent GC.");
      }
    } else {
      log_debug(gc, periodic)("Triggering periodic full GC.");
    }

    // The concurrent vs full decision is made by
    // G1CollectedHeap::should_do_concurrent_full_gc() which calls
    // should_use_concurrent_periodic_gc().
    if (!g1h->try_collect(0 /* allocation_word_size */, GCCause::_g1_periodic_collection, counters)) {
      log_debug(gc, periodic)("Periodic GC request denied. Skipping.");
    }
  }
}

G1PeriodicGCTask::G1PeriodicGCTask(const char* name) :
  G1ServiceTask(name) { }

void G1PeriodicGCTask::execute() {
  uintx effective_interval = get_effective_periodic_interval();

  // Only check for periodic GC if it's enabled.
  if (effective_interval > 0) {
    check_for_periodic_gc();
  }

  // Schedule next execution.
  // Use the effective interval if it's less than 1 second, otherwise use 1 second.
  // The 1 second default allows detecting changes to the manageable flag.
  uintx check_interval = (effective_interval > 0 && effective_interval < 1000)
                         ? effective_interval
                         : 1000;
  schedule(check_interval);
}
