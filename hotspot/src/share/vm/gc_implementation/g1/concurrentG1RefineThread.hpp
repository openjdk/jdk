/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTG1REFINETHREAD_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTG1REFINETHREAD_HPP

#include "gc_implementation/shared/concurrentGCThread.hpp"

// Forward Decl.
class ConcurrentG1Refine;

// The G1 Concurrent Refinement Thread (could be several in the future).

class ConcurrentG1RefineThread: public ConcurrentGCThread {
  friend class VMStructs;
  friend class G1CollectedHeap;

  double _vtime_start;  // Initial virtual time.
  double _vtime_accum;  // Initial virtual time.
  uint _worker_id;
  uint _worker_id_offset;

  // The refinement threads collection is linked list. A predecessor can activate a successor
  // when the number of the rset update buffer crosses a certain threshold. A successor
  // would self-deactivate when the number of the buffers falls below the threshold.
  bool _active;
  ConcurrentG1RefineThread* _next;
  Monitor* _monitor;
  ConcurrentG1Refine* _cg1r;

  int _thread_threshold_step;
  // This thread activation threshold
  int _threshold;
  // This thread deactivation threshold
  int _deactivation_threshold;

  void sample_young_list_rs_lengths();
  void run_young_rs_sampling();
  void wait_for_completed_buffers();

  void set_active(bool x) { _active = x; }
  bool is_active();
  void activate();
  void deactivate();

public:
  virtual void run();
  // Constructor
  ConcurrentG1RefineThread(ConcurrentG1Refine* cg1r, ConcurrentG1RefineThread* next,
                           uint worker_id_offset, uint worker_id);

  void initialize();

  // Printing
  void print() const;
  void print_on(outputStream* st) const;

  // Total virtual time so far.
  double vtime_accum() { return _vtime_accum; }

  ConcurrentG1Refine* cg1r() { return _cg1r;     }

  // shutdown
  void stop();
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTG1REFINETHREAD_HPP
