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
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"

Atomic<WorkerThreads*> DiagnosticWorkers::_workers{};

WorkerThreads* DiagnosticWorkers::workers() {
  assert(Thread::current()->is_VM_thread(), "Must be the VM thread");
  assert_at_safepoint();
  if (_workers.load_relaxed() == nullptr && ParallelGCThreads > 1) {
    WorkerThreads* pool = new WorkerThreads("DiagWorker", ParallelGCThreads);
    _workers.release_store(pool);
    log_info(gc, task)("Created diagnostic worker pool (max %u workers)", pool->max_workers());
  }
  return _workers.load_relaxed();
}

void DiagnosticWorkers::diagnostic_threads_do(ThreadClosure* tc) {
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

#ifdef ASSERT
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

bool DiagnosticWorkers::is_diagnostic_thread(const Thread* t) {
  WorkerThreads* workers = _workers.load_acquire();
  if (workers == nullptr) {
    return false;
  }

  DiagnosticThreadClosure cl(t);
  workers->threads_do(&cl);
  return cl.found();
}
#endif // ASSERT
