/*
 * Copyright (c) 2001, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1CONCURRENTREFINETHREAD_HPP
#define SHARE_VM_GC_G1_G1CONCURRENTREFINETHREAD_HPP

#include "gc/g1/dirtyCardQueue.hpp"
#include "gc/shared/concurrentGCThread.hpp"

// Forward Decl.
class CardTableEntryClosure;
class G1ConcurrentRefine;

// One or more G1 Concurrent Refinement Threads may be active if concurrent
// refinement is in progress.
class G1ConcurrentRefineThread: public ConcurrentGCThread {
  friend class VMStructs;
  friend class G1CollectedHeap;

  double _vtime_start;  // Initial virtual time.
  double _vtime_accum;  // Accumulated virtual time.
  uint _worker_id;

  bool _active;
  Monitor* _monitor;
  G1ConcurrentRefine* _cr;

  void wait_for_completed_buffers();

  void set_active(bool x) { _active = x; }
  // Deactivate this thread.
  void deactivate();

  bool is_primary() { return (_worker_id == 0); }

  void run_service();
  void stop_service();
public:
  G1ConcurrentRefineThread(G1ConcurrentRefine* cg1r, uint worker_id);

  bool is_active();
  // Activate this thread.
  void activate();

  // Total virtual time so far.
  double vtime_accum() { return _vtime_accum; }
};

#endif // SHARE_VM_GC_G1_G1CONCURRENTREFINETHREAD_HPP
