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
#include "gc/shared/workerThread.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "runtime/os.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"

class DiagnosticThreadClosure : public ThreadClosure {
private:
  bool _found = false;
  const Thread* _target;
public:
  DiagnosticThreadClosure(const Thread* t) : _target(t) {}

  void do_thread(Thread* t) override {
    if (_target == t) {
      _found = true;
    }
  }

  bool found() const { return _found; }
};

WorkerThreads* DiagnosticWorkers::_workers = nullptr;

uint DiagnosticWorkers::calc_max_workers() {
  return clamp(ParallelGCThreads, 1u, (uint)os::initial_active_processor_count());
}

WorkerThreads* DiagnosticWorkers::workers() {
  if (_workers == nullptr && calc_max_workers() > 1) {
    assert_at_safepoint();
    _workers = new WorkerThreads("DiagWorker", calc_max_workers());
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
  if (_workers == nullptr || num_workers == 0) {
    return 0;
  }

  return _workers->set_active_workers(MIN2(num_workers, _workers->max_workers()));
}

bool DiagnosticWorkers::is_diagnostic_thread(const Thread* t) {
  if (_workers == nullptr) {
    return false;
  }

  DiagnosticThreadClosure cl(t);
  _workers->threads_do(&cl);
  return cl.found();
}
