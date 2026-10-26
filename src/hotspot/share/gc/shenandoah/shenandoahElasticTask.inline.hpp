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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHELASTICTASK_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHELASTICTASK_INLINE_HPP

#include "gc/shenandoah/shenandoahElasticTask.hpp"

#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"

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
      terminator->notify_all();
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
    }

    // Thread must leave the suspendible thread set while it waits for termination,
    // or it may prevent safepoints from synchronizing.
    SuspendibleThreadSetLeaver stsl(Cancellable);
    if (terminator->offer_termination(&tt)) {
      break;
    }
  }
}

template<bool Cancellable, typename WorkFn>
void ShenandoahElasticMonotonicTask::elastic_loop(uint worker_id, WorkFn work) {
  if (!_coordinator->wait_for_work()) {
    // This thread never became eligible to work
    return;
  }

  ShenandoahConcurrentWorkerSession worker_session(worker_id);
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  SuspendibleThreadSetJoiner stsj(Cancellable);
  while (true) {
    if (Cancellable && heap->check_cancelled_gc_and_yield()) {
      // The work is cancelled
      break;
    }

    const ShenandoahWorkResult result = work();
    if (result != ShenandoahWorkResult::DidWork) {
      // This thread did not do any work. Either there is no more work left,
      // or this thread cannot perform the remaining work.
      break;
    }
  }

  SuspendibleThreadSetLeaver stsl(Cancellable);
  _coordinator->complete();
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHELASTICTASK_INLINE_HPP
