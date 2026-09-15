/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalEvacuationTask.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahInPlacePromoter.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"

class ShenandoahConcurrentEvacuator : public ObjectClosure {
private:
  ShenandoahGenerationalHeap* const _heap;
  Thread* const _thread;
public:
  explicit ShenandoahConcurrentEvacuator(ShenandoahGenerationalHeap* heap) :
          _heap(heap), _thread(Thread::current()) {}

  void do_object(oop p) override {
    shenandoah_assert_marked(nullptr, p);
    if (!p->is_forwarded()) {
      _heap->evacuate_object(p, _thread);
    }
  }
};

#ifdef ASSERT
void ShenandoahCsetTaskAdapter::assert_empty() const {
  // Okay for some regions to not be evacuated when there are evacuation failures
}
#endif

uint ShenandoahCsetTaskAdapter::tasks() const {
  return checked_cast<uint>(_collection_set->remaining());
}

ShenandoahGenerationalEvacuationTask::ShenandoahGenerationalEvacuationTask(ShenandoahGenerationalHeap* heap,
                                                                           ShenandoahGeneration* generation,
                                                                           ShenandoahRegionIterator* iterator,
                                                                           bool only_promote_regions) :
  WorkerTask("Shenandoah Evacuation"),
  _heap(heap),
  _generation(generation),
  _regions(iterator),
  _collection_set(_heap->collection_set()),
  _collection_set_tasks(_collection_set),
  _terminator(_heap->workers()->active_workers(), &_collection_set_tasks),
  _only_promote_regions(only_promote_regions)
{
  shenandoah_assert_generational();
}

void ShenandoahGenerationalEvacuationTask::work(uint worker_id) {
  ShenandoahConcurrentWorkerSession worker_session(worker_id);
  SuspendibleThreadSetJoiner stsj;
  do_work();
}

void ShenandoahGenerationalEvacuationTask::do_work() {
  assert(_only_promote_regions == _heap->collection_set()->is_empty(),
         "Collection set must be empty iff only promoting regions");
  evacuate_and_promote_regions();
}

void maybe_log_region(const ShenandoahHeapRegion* r) {
  // This is true for every region we take action for, whether it is being evacuated or promoted in place
  assert(r->has_live(), "Region %zu should have been reclaimed early", r->index());
  if (LogTarget(Debug, gc) lt; lt.is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("GenerationalEvacuationTask, looking at %s region %zu, (age: %d) [%s, %s, %s]",
                r->is_old()? "old": r->is_young()? "young": "free", r->index(), r->age(),
                r->is_active()? "active": "inactive",
                r->is_humongous()? (r->is_humongous_start()? "humongous_start": "humongous_continuation"): "regular",
                r->is_cset()? "cset": "not-cset");
  }
}

void ShenandoahGenerationalEvacuationTask::evacuate_and_promote_regions() {
  ShenandoahConcurrentEvacuator cl(_heap);
  ShenandoahTerminatorTerminator tt(_heap, true);
  ShenandoahInPlacePromoter promoter(_heap);
  bool canEvacuate = true;

  while (true) {
    if (_heap->check_cancelled_gc_and_yield()) {
      // GC is cancelled (vm is stopping), no further work
      assert(_heap->is_stopping(), "Can only stop evacuation for shutdown");
      return;
    }

    ShenandoahHeapRegion* r = nullptr;
    if (tt.can_work()) {
      if (canEvacuate && !_only_promote_regions) {
        // Take evacuation work first
        r = _collection_set->claim_next();
      }

      if (r != nullptr) {
        ShenandoahWorkerTimingsTracker timer(ShenandoahPhaseTimings::conc_evac,
                                  ShenandoahPhaseTimings::Work,
                                             WorkerThread::worker_id(), true);
        maybe_log_region(r);
        _heap->marked_object_iterate(r, &cl);
        if (ShenandoahCollectorPolicy::should_abandon_evacuations(r)) {
          canEvacuate = false;
        }
      } else {
        // No evac work left, or this thread is out of LAB space for evacuations, or we
        // are just running in place promotions
        r = _regions->next();
        if (r != nullptr) {
          if (promoter.maybe_promote_region(r)) {
            maybe_log_region(r);
          }
        } else {
          // No cset regions left, no promotion regions left, retire this worker so it
          // parks in offer_termination instead of coming back to look for more work.
          tt.retire();
        }
      }
    }

    // No promotion work, no evacuation work, or thread is in reserve, try to terminate
    if (r == nullptr) {
      if (_terminator.offer_termination(&tt)) {
        break;
      }
    }
  }
}
