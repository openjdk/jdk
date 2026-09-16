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

enum class ShenandoahWorkResult {
  DidWork, NoWork, Retire
};


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
    ShenandoahTerminatorTerminator tt(_heap, Cancellable);
    SuspendibleThreadSetJoiner stsj(Cancellable);
    while (true) {
      if (Cancellable && _heap->check_cancelled_gc_and_yield()) {
        return;
      }

      if (tt.can_work()) {
        const ShenandoahWorkResult result = work();
        if (result == ShenandoahWorkResult::DidWork) {
          continue;
        }

        if (result == ShenandoahWorkResult::Retire) {
          tt.retire();
        }
      }

      SuspendibleThreadSetLeaver stsl(Cancellable);
      if (_terminator.offer_termination(&tt)) {
        break;
      }
    }
  }
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHELASTICTASK_HPP
