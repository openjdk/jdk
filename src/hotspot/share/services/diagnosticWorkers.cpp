/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shared/workerThread.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "runtime/os.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"
#include "services/diagnosticWorkers.hpp"

Atomic<WorkerThreads*> DiagnosticWorkers::_workers{};

WorkerThreads* DiagnosticWorkers::workers() {
  assert(Thread::current()->is_VM_thread(), "Must be the VM thread");
  assert_at_safepoint();
  const uint max_workers = (uint)os::initial_active_processor_count();
  if (_workers.load_relaxed() == nullptr && max_workers > 1) {
    WorkerThreads* pool = new WorkerThreads("DiagWorker", max_workers);
    // Use release_store() because the error reporter can read _workers while
    // the pool is being created.
    _workers.release_store(pool);
    log_info(gc, task)("Created diagnostic worker pool (max %u workers)", pool->max_workers());
  }
  return _workers.load_relaxed();
}

void DiagnosticWorkers::diagnostic_threads_do(ThreadClosure* tc) {
  // The error reporter can call this method outside a safepoint, so use load_acquire().
  WorkerThreads* workers = _workers.load_acquire();
  if (workers != nullptr) {
    workers->threads_do(tc);
  }
}

uint DiagnosticWorkers::try_and_set_active_workers(uint num_workers) {
  assert(Thread::current()->is_VM_thread(), "Must be the VM thread");
  assert_at_safepoint();
  WorkerThreads* workers = _workers.load_relaxed();
  if (workers == nullptr || num_workers == 0) {
    return 0;
  }

  return workers->set_active_workers(MIN2(num_workers, workers->max_workers()));
}
