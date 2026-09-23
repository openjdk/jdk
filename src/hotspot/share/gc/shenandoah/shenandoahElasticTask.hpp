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

#include "gc/shared/taskTerminator.hpp"
#include "gc/shared/workerThread.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"
#include "runtime/mutex.hpp"

class ShenandoahHeap;
class ShenandoahController;

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
void shenandoah_elastic_loop(ShenandoahHeap* heap, TaskTerminator* terminator, WorkFn work);


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
  ShenandoahElasticTask(ShenandoahHeap* heap, const Adapter& adapter, const char* name);

protected:
  template<bool Cancellable, typename WorkFn>
  void elastic_loop(WorkFn work);
};


// Holds fields to be shared by elastic tasks, needs to be
// reachable by shController so that _admitted can be increased.
class ShenandoahElasticTaskCoordinator {
  uint _admitted; // allowed concurrent worker count
  uint _capable;  // outstanding participants (must be less than or equal to _admitted).
  Monitor _gate;  // serialize admitted/capable changes

public:
  ShenandoahElasticTaskCoordinator();

  // Must read concurrent worker count under _gate to prevent losing updates
  // to concurrent worker count that would be ignored before the reset is complete.
  void reset(const ShenandoahController* controller);

  // Called by mutators when they experience an allocation stall
  void increase_workers(size_t concurrent_worker_limit);

  // Called by workers when they have finished (or can no longer work)
  void complete();

  // Called by workers. Blocks if the worker is not allowed to work.
  // Returns true when there is work available, false otherwise.
  bool wait_for_work();

private:
  // True when there are no longer participants with remaining work. Must hold _gate lock
  bool is_done() const;
};

class ShenandoahElasticMonotonicTask : public WorkerTask {
  ShenandoahElasticTaskCoordinator* _coordinator;

protected:
  ShenandoahHeap* _heap;

public:
  ShenandoahElasticMonotonicTask(ShenandoahHeap* heap, ShenandoahElasticTaskCoordinator* coordinator, const char* name)
    : WorkerTask(name)
    , _coordinator(coordinator)
    , _heap(heap) {
  }

protected:
  template<bool Cancellable, typename WorkFn>
  void elastic_loop(WorkFn work);
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHELASTICTASK_HPP
