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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHELASTICTASK_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHELASTICTASK_HPP

#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "gc/shared/workerThread.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"

/*
 * The code here provides an abstraction over a coordinated termination
 * protocol for a thread work group. Shenandoah uses this idiom to increase
 * the number of workers during a concurrent gc phase when allocation
 * stalls are experienced. All threads in the work group are 'active',
 * however, only N threads will perform work (N = shHeap::eligible_workers).
 * The remaining threads will immediately offer termination. These threads
 * are woken periodically (per the termination protocol) to check for more
 * work. If the N has increased (in response to an allocation failure), the
 * reserved threads will withdraw their termination offer and begin claiming
 * work.
 */

// This represents the outcome of an increment of work performed by the thread.
// If it claimed any work, it DidWork. If the supply of work is exhausted, it
// should return NoWork. If there is work remaining, but the thread cannot
// perform it (LABs exhausted, for instance), then it should Retire.
enum class ShenandoahWorkResult {
  DidWork, NoWork, Retire
};

template<bool Cancellable, typename WorkFn>
void shenandoah_elastic_loop(ShenandoahHeap* heap, TaskTerminator* terminator, WorkFn work) {
  // The TerminatorTerminator is responsible for controlling when a thread
  // should withdraw its termination offer and resume the loop here. This
  // component is responsible for holding excess threads in reserve and holding
  // down 'retired' threads.
  ShenandoahTerminatorTerminator tt(heap, Cancellable);
  SuspendibleThreadSetJoiner stsj(Cancellable);
  while (true) {
    if (Cancellable && heap->check_cancelled_gc_and_yield()) {
      // The termination offer is withdrawn when a cycle cancellation is
      // observed. The thread returns to the top of the loop here and exits.
      return;
    }

    if (tt.can_work()) {
      // A thread can work if it is a member of the eligible thread set, there
      // are tasks remaining, and it has not been 'retired'.
      const ShenandoahWorkResult result = work();
      if (result == ShenandoahWorkResult::DidWork) {
        // This thread claimed work and believes there is more remaining.
        continue;
      }

      if (result == ShenandoahWorkResult::Retire) {
        // This thread can no longer work, though there may be tasks remaining.
        tt.retire();
      }
    }

    // Thread must leave the suspendible thread set while it waits for termination,
    // or it may prevent safepoints from synchronizing.
    SuspendibleThreadSetLeaver stsl(Cancellable);
    if (terminator->offer_termination(&tt)) {
      break;
    }
  }
}

// A small helper class to set up the required components for the elastic loop.
// The adapter should be an implementation of the TaskQueueSetSuper interface.
template <typename Adapter>
class ShenandoahElasticTask : public WorkerTask {
protected:
  ShenandoahHeap* _heap;

private:
  Adapter _adapter;
  TaskTerminator _terminator;

public:
  ShenandoahElasticTask(ShenandoahHeap* heap, const Adapter& adapter, const char* name)
    : WorkerTask(name)
    , _heap(heap)
    , _adapter(adapter)
    , _terminator(_heap->workers()->active_workers(), &_adapter) {
  }

protected:
  template<bool Cancellable, typename WorkFn>
  void elastic_loop(WorkFn work) {
    shenandoah_elastic_loop<Cancellable>(_heap, &_terminator, work);
  }
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHELASTICTASK_HPP
