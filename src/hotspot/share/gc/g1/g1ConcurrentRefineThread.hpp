/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CONCURRENTREFINETHREAD_HPP
#define SHARE_GC_G1_G1CONCURRENTREFINETHREAD_HPP

#include "gc/shared/concurrentGCThread.hpp"
#include "runtime/mutex.hpp"
#include "utilities/globalDefinitions.hpp"

// Forward Decl.
class G1ConcurrentRefine;

// Concurrent refinement control thread watching card mark accrual on the card table
// and starting refinement work.
class G1ConcurrentRefineThread: public ConcurrentGCThread {
  friend class G1CollectedHeap;

  Monitor _notifier;
  bool _requested_active;

  uint _worker_id;

  G1ConcurrentRefine* _cr;

  NONCOPYABLE(G1ConcurrentRefineThread);

  G1ConcurrentRefineThread(G1ConcurrentRefine* cr);

  Monitor* notifier() { return &_notifier; }
  bool requested_active() const { return _requested_active; }

  // Returns !should_terminate().
  // precondition: this is the current thread.
  bool wait_for_work();

  // Deactivate if appropriate.  Returns true if deactivated.
  // precondition: this is the current thread.
  bool deactivate();

  // Swap card table and do a complete re-examination/refinement pass over the
  // refinement table.
  void do_refinement();

  // Update concurrent refine threads cpu time stats.
  void update_perf_counter_cpu_time();

  void report_active(const char* reason) const;
  void report_inactive(const char* reason) const;

  G1ConcurrentRefine* cr() const { return _cr; }

  void run_service() override;
  void stop_service() override;

public:
  static G1ConcurrentRefineThread* create(G1ConcurrentRefine* cr);

  // Activate this thread.
  // precondition: this is not the current thread.
  void activate();

  // Total cpu time spent in this thread so far.
  jlong cpu_time();
};

#endif // SHARE_GC_G1_G1CONCURRENTREFINETHREAD_HPP
