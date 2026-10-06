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

#include "gc/shared/diagnosticWorkers.hpp"
#include "gc/shared/gc_globals.hpp"
#include "logging/log.hpp"
#include "memory/universe.hpp"
#include "runtime/os.hpp"
#include "runtime/safepoint.hpp"

DiagnosticWorkers::DiagnosticWorkers() :
    WorkerThreads("DiagWorker", (uint)os::initial_active_processor_count()) { }

WorkerThreads* DiagnosticWorkers::_workers = nullptr;

void DiagnosticWorkers::on_create_worker(WorkerThread* thread) {
  Universe::heap()->initialize_diagnostic_worker(thread);
}

WorkerThreads* DiagnosticWorkers::workers() {
  if (_workers == nullptr && os::initial_active_processor_count() > 1) {
    assert_at_safepoint();
    _workers = new DiagnosticWorkers();
    log_info(gc, task)("Created diagnostic worker pool (max %u workers)", _workers->max_workers());
  }
  return _workers;
}

void DiagnosticWorkers::diagnostic_threads_do(ThreadClosure* tc) {
  if (_workers != nullptr) {
    _workers->threads_do(tc);
  }
}

uint DiagnosticWorkers::try_and_set_active_workers(uint num_workers) {
  if (_workers == nullptr) {
    return 0;
  }

  return _workers->set_active_workers(MIN2(num_workers, _workers->max_workers()));
}
