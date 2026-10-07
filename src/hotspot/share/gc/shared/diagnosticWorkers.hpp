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

#ifndef SHARE_GC_SHARED_DIAGNOSTICWORKERS_HPP
#define SHARE_GC_SHARED_DIAGNOSTICWORKERS_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

class Thread;
class ThreadClosure;
class WorkerThreads;

// Worker pool shared by heap dump and heap inspection. The pool is created lazily
// on the first parallel request and threads are added to the pool as each request
// needs them. Threads are reused and exist until the VM exits.
class DiagnosticWorkers : public AllStatic {
private:
  static WorkerThreads* _workers;

  // Calculates the maximum number of workers the pool can hold.
  static uint calc_max_workers();

public:
  // Returns the diagnostic worker pool, creating it on the first call. Returns
  // nullptr if calc_max_workers() <= 1, since the pool could not run in parallel.
  static WorkerThreads* workers();

  // Applies the closure to each diagnostic worker only if the worker pool is initialized.
  static void diagnostic_threads_do(ThreadClosure* tc);

  // Sets the number of active worker threads (capped at max_workers()) and returns the
  // active thread count.
  static uint try_and_set_active_workers(uint num_workers);

  // Returns true if the thread passed in is a diagnostic worker thread.
  static bool is_diagnostic_thread(const Thread* t);
};

#endif // SHARE_GC_SHARED_DIAGNOSTICWORKERS_HPP
