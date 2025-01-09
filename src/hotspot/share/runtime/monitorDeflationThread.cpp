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

#include "precompiled.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/universe.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/monitorDeflationThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/synchronizer.hpp"

bool volatile MonitorDeflationThread::_is_async_deflation_requested = false;
jlong MonitorDeflationThread::_last_async_deflation_time_ns = 0;

// monitors_used_above_threshold() policy is as follows:
//
// The ratio of the current in_use_list() count to the ceiling is used
// to determine if we are above MonitorUsedDeflationThreshold and need
// to do an async monitor deflation cycle. The ceiling is increased by
// AvgMonitorsPerThreadEstimate when a thread is added to the system
// and is decreased by AvgMonitorsPerThreadEstimate when a thread is
// removed from the system.
//
// Note: If the in_use_list() max exceeds the ceiling, then
// monitors_used_above_threshold() will use the in_use_list max instead
// of the thread count derived ceiling because we have used more
// ObjectMonitors than the estimated average.
//
// Note: If deflate_idle_monitors() has NoAsyncDeflationProgressMax
// no-progress async monitor deflation cycles in a row, then the ceiling
// is adjusted upwards by monitors_used_above_threshold().
//
// Start the ceiling with the estimate for one thread in initialize()
// which is called after cmd line options are processed.
static size_t _in_use_list_ceiling = 0;
static uintx _no_progress_cnt = 0;
static bool _no_progress_skip_increment = false;

size_t MonitorDeflationThread::in_use_list_ceiling() {
  return _in_use_list_ceiling;
}

void MonitorDeflationThread::dec_in_use_list_ceiling() {
  Atomic::sub(&_in_use_list_ceiling, AvgMonitorsPerThreadEstimate);
}

void MonitorDeflationThread::inc_in_use_list_ceiling() {
  Atomic::add(&_in_use_list_ceiling, AvgMonitorsPerThreadEstimate);
}

void MonitorDeflationThread::update_heuristics(size_t deflated_count) {
  if (deflated_count != 0) {
    _no_progress_cnt = 0;
  } else if (_no_progress_skip_increment) {
    _no_progress_skip_increment = false;
  } else {
    _no_progress_cnt++;
  }
}

bool MonitorDeflationThread::monitors_used_above_threshold() {
  if (MonitorUsedDeflationThreshold == 0) {  // disabled case is easy
    return false;
  }

  MonitorList* list = ObjectSynchronizer::in_use_list();

  // Start with ceiling based on a per-thread estimate:
  size_t ceiling = _in_use_list_ceiling;
  size_t old_ceiling = ceiling;
  if (ceiling < list->max()) {
    // The max used by the system has exceeded the ceiling so use that:
    ceiling = list->max();
  }
  size_t monitors_used = list->count();
  if (monitors_used == 0) {  // empty list is easy
    return false;
  }
  if (NoAsyncDeflationProgressMax != 0 &&
      _no_progress_cnt >= NoAsyncDeflationProgressMax) {
    double remainder = (100.0 - MonitorUsedDeflationThreshold) / 100.0;
    size_t new_ceiling = ceiling + (size_t)((double)ceiling * remainder) + 1;
    _in_use_list_ceiling = new_ceiling;
    log_info(monitorinflation)("Too many deflations without progress; "
                               "bumping in_use_list_ceiling from " SIZE_FORMAT
                               " to " SIZE_FORMAT, old_ceiling, new_ceiling);
    _no_progress_cnt = 0;
    ceiling = new_ceiling;
  }

  // Check if our monitor usage is above the threshold:
  size_t monitor_usage = (monitors_used * 100LL) / ceiling;
  if (int(monitor_usage) > MonitorUsedDeflationThreshold) {
    log_info(monitorinflation)("monitors_used=" SIZE_FORMAT ", ceiling=" SIZE_FORMAT
                               ", monitor_usage=" SIZE_FORMAT ", threshold=%d",
                               monitors_used, ceiling, monitor_usage, MonitorUsedDeflationThreshold);
    return true;
  }

  return false;
}


bool MonitorDeflationThread::is_async_deflation_needed() {
  if (_is_async_deflation_requested) {
    // Async deflation request.
    log_info(monitorinflation)("Async deflation needed: explicit request");
    return true;
  }

  jlong ms_since_last = (os::javaTimeNanos() - _last_async_deflation_time_ns) / (NANOUNITS / MILLIUNITS);

  if (AsyncDeflationInterval > 0 &&
      ms_since_last > AsyncDeflationInterval &&
      monitors_used_above_threshold()) {
    // It's been longer than our specified deflate interval and there
    // are too many monitors in use. We don't deflate more frequently
    // than AsyncDeflationInterval (unless is_async_deflation_requested)
    // in order to not swamp the MonitorDeflationThread.
    log_info(monitorinflation)("Async deflation needed: monitors used are above the threshold");
    return true;
  }

  if (GuaranteedAsyncDeflationInterval > 0 &&
      ms_since_last > GuaranteedAsyncDeflationInterval) {
    // It's been longer than our specified guaranteed deflate interval.
    // We need to clean up the used monitors even if the threshold is
    // not reached, to keep the memory utilization at bay when many threads
    // touched many monitors.
    log_info(monitorinflation)("Async deflation needed: guaranteed interval (" INTX_FORMAT " ms) "
                               "is greater than time since last deflation (" JLONG_FORMAT " ms)",
                               GuaranteedAsyncDeflationInterval, ms_since_last);

    // If this deflation has no progress, then it should not affect the no-progress
    // tracking, otherwise threshold heuristics would think it was triggered, experienced
    // no progress, and needs to backoff more aggressively. In this "no progress" case,
    // the generic code would bump the no-progress counter, and we compensate for that
    // by telling it to skip the update.
    //
    // If this deflation has progress, then it should let non-progress tracking
    // know about this, otherwise the threshold heuristics would kick in, potentially
    // experience no-progress due to aggressive cleanup by this deflation, and think
    // it is still in no-progress stride. In this "progress" case, the generic code would
    // zero the counter, and we allow it to happen.
    _no_progress_skip_increment = true;

    return true;
  }

  return false;
}

void MonitorDeflationThread::request_deflate_idle_monitors() {
  MonitorLocker ml(MonitorDeflation_lock, Mutex::_no_safepoint_check_flag);
  _is_async_deflation_requested = true;
  ml.notify_all();
}

bool MonitorDeflationThread::request_deflate_idle_monitors_from_wb() {
  JavaThread* current = JavaThread::current();
  bool ret_code = false;

  jlong last_time = _last_async_deflation_time_ns;

  request_deflate_idle_monitors();

  const int N_CHECKS = 5;
  for (int i = 0; i < N_CHECKS; i++) {  // sleep for at most 5 seconds
    if (_last_async_deflation_time_ns > last_time) {
      log_info(monitorinflation)("Async Deflation happened after %d check(s).", i);
      ret_code = true;
      break;
    }
    {
      // JavaThread has to honor the blocking protocol.
      ThreadBlockInVM tbivm(current);
      os::naked_short_sleep(999);  // sleep for almost 1 second
    }
  }
  if (!ret_code) {
    log_info(monitorinflation)("Async Deflation DID NOT happen after %d checks.", N_CHECKS);
  }

  return ret_code;
}

void MonitorDeflationThread::initialize() {
  EXCEPTION_MARK;

  const char* name = "Monitor Deflation Thread";
  Handle thread_oop = JavaThread::create_system_thread_object(name, CHECK);

  MonitorDeflationThread* thread = new MonitorDeflationThread(&monitor_deflation_thread_entry);
  JavaThread::vm_exit_on_osthread_failure(thread);

  // Start the timer for deflations, so it does not trigger immediately.
  _last_async_deflation_time_ns = os::javaTimeNanos();

  // Start the ceiling with the estimate for one thread.
  _in_use_list_ceiling = AvgMonitorsPerThreadEstimate;

  JavaThread::start_internal_daemon(THREAD, thread, thread_oop, NearMaxPriority);
}

void MonitorDeflationThread::monitor_deflation_thread_entry(JavaThread* jt, TRAPS) {

  // We wait for the lowest of these two intervals:
  //  - AsyncDeflationInterval
  //      Normal threshold-based deflation heuristic checks the conditions at this interval.
  //      See is_async_deflation_needed().
  //  - GuaranteedAsyncDeflationInterval
  //      Backup deflation heuristic checks the conditions at this interval.
  //      See is_async_deflation_needed().
  //
  intx wait_time = max_intx;
  if (AsyncDeflationInterval > 0) {
    wait_time = MIN2(wait_time, AsyncDeflationInterval);
  }
  if (GuaranteedAsyncDeflationInterval > 0) {
    wait_time = MIN2(wait_time, GuaranteedAsyncDeflationInterval);
  }

  // If all options are disabled, then wait time is not defined, and the deflation
  // is effectively disabled. In that case, exit the thread immediately after printing
  // a warning message.
  if (wait_time == max_intx) {
    warning("Async deflation is disabled");
    return;
  }

  while (true) {
    {
      // Need state transition ThreadBlockInVM so that this thread
      // will be handled by safepoint correctly when this thread is
      // notified at a safepoint.

      ThreadBlockInVM tbivm(jt);

      MonitorLocker ml(MonitorDeflation_lock, Mutex::_no_safepoint_check_flag);
      while (!is_async_deflation_needed()) {
        // Wait until notified that there is some work to do.
        ml.wait(wait_time);
      }
    }

    (void)ObjectSynchronizer::deflate_idle_monitors();

    if (log_is_enabled(Debug, monitorinflation)) {
      // The VMThread calls do_final_audit_and_print_stats() which calls
      // audit_and_print_stats() at the Info level at VM exit time.
      LogStreamHandle(Debug, monitorinflation) ls;
      ObjectSynchronizer::audit_and_print_stats(&ls, false /* on_exit */);
    }
  }
}
