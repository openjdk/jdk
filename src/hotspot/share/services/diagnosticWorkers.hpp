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

#ifndef SHARE_SERVICES_DIAGNOSTICWORKERS_HPP
#define SHARE_SERVICES_DIAGNOSTICWORKERS_HPP

#include "memory/allStatic.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"

class ThreadClosure;
class WorkerThreads;

// Worker pool used by diagnostic tasks, for example heap dump and heap inspection.
// The pool is created lazily on the first parallel request and threads are added to
// the pool as each request needs them. Threads are reused and exist until the VM exits.
class DiagnosticWorkers : public AllStatic {
private:
  // Written once by the VM thread at a safepoint. Also read by the error reporter,
  // which can run outside a safepoint.
  static Atomic<WorkerThreads*> _workers;

public:
  // Returns the diagnostic worker pool, creating it on first use and sizing it to the
  // active processor count at that time. Returns nullptr if the pool hasn't been created
  // and the active processor count is 1. Can only be called by the VM thread at a safepoint.
  static WorkerThreads* workers();

  // Applies the closure to each diagnostic worker if the worker pool has been created.
  static void threads_do(ThreadClosure* tc);

  // Sets the number of active worker threads (capped at max_workers()) and returns the
  // active thread count. Creates the worker pool on first use. Returns 0 if the pool
  // wasn't created or num_workers is 0. Can only be called by the VM thread at a safepoint.
  static uint try_and_set_active_workers(uint num_workers);
};

#endif // SHARE_SERVICES_DIAGNOSTICWORKERS_HPP
