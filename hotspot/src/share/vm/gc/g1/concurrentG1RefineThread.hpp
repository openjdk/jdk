/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_CONCURRENTG1REFINETHREAD_HPP
#define SHARE_VM_GC_G1_CONCURRENTG1REFINETHREAD_HPP

#include "gc/shared/concurrentGCThread.hpp"

// Forward Decl.
class CardTableEntryClosure;
class ConcurrentG1Refine;

// One or more G1 Concurrent Refinement Threads may be active if concurrent
// refinement is in progress.
class ConcurrentG1RefineThread: public ConcurrentGCThread {
  friend class VMStructs;
  friend class G1CollectedHeap;

  double _vtime_start;  // Initial virtual time.
  double _vtime_accum;  // Accumulated virtual time.
  uint _worker_id;
  uint _worker_id_offset;

  // The refinement threads collection is linked list. A predecessor can activate a successor
  // when the number of the rset update buffer crosses a certain threshold. A successor
  // would self-deactivate when the number of the buffers falls below the threshold.
  bool _active;
  ConcurrentG1RefineThread* _next;
  Monitor* _monitor;
  ConcurrentG1Refine* _cg1r;

  // The closure applied to completed log buffers.
  CardTableEntryClosure* _refine_closure;

  // This thread's activation/deactivation thresholds
  size_t _activation_threshold;
  size_t _deactivation_threshold;

  void wait_for_completed_buffers();

  void set_active(bool x) { _active = x; }
  bool is_active();
  void activate();
  void deactivate();

  bool is_primary() { return (_worker_id == 0); }

  void run_service();
  void stop_service();

public:
  // Constructor
  ConcurrentG1RefineThread(ConcurrentG1Refine* cg1r, ConcurrentG1RefineThread* next,
                           CardTableEntryClosure* refine_closure,
                           uint worker_id_offset, uint worker_id,
                           size_t activate, size_t deactivate);

  void update_thresholds(size_t activate, size_t deactivate);
  size_t activation_threshold() const { return _activation_threshold; }

  // Total virtual time so far.
  double vtime_accum() { return _vtime_accum; }

  ConcurrentG1Refine* cg1r() { return _cg1r;     }
};

#endif // SHARE_VM_GC_G1_CONCURRENTG1REFINETHREAD_HPP
