/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/universe.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/monitorDeflationThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/synchronizer.hpp"

void MonitorDeflationThread::initialize() {
  EXCEPTION_MARK;

  const char* name = "Monitor Deflation Thread";
  Handle thread_oop = JavaThread::create_system_thread_object(name, CHECK);

  MonitorDeflationThread* thread = new MonitorDeflationThread(&monitor_deflation_thread_entry);
  JavaThread::vm_exit_on_osthread_failure(thread);

  JavaThread::start_internal_daemon(THREAD, thread, thread_oop, NearMaxPriority);
}

void MonitorDeflationThread::monitor_deflation_thread_entry(JavaThread* jt, TRAPS) {

  // We wait for the lowest of these three intervals:
  //  - GuaranteedSafepointInterval
  //      While deflation is not related to safepoint anymore, this keeps compatibility with
  //      the old behavior when deflation also happened at safepoints. Users who set this
  //      option to get more/less frequent deflations would be served with this option.
  //  - AsyncDeflationInterval
  //      Normal threshold-based deflation heuristic checks the conditions at this interval.
  //      See is_async_deflation_needed().
  //  - GuaranteedAsyncDeflationInterval
  //      Backup deflation heuristic checks the conditions at this interval.
  //      See is_async_deflation_needed().
  //
  intx wait_time = max_intx;
  if (GuaranteedSafepointInterval > 0) {
    wait_time = MIN2(wait_time, GuaranteedSafepointInterval);
  }
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
      while (!ObjectSynchronizer::is_async_deflation_needed()) {
        // Wait until notified that there is some work to do.
        ml.wait(wait_time);
      }
    }

    (void)ObjectSynchronizer::deflate_idle_monitors();
  }
}
