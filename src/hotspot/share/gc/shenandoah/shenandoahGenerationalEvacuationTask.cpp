/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

ShenandoahGenerationalEvacuationTask::ShenandoahGenerationalEvacuationTask(ShenandoahGenerationalHeap* heap,
                                                                           ShenandoahGeneration* generation,
                                                                           ShenandoahRegionIterator* iterator,
                                                                           bool only_promote_regions) :
  WorkerTask("Shenandoah Evacuation"),
  _heap(heap),
  _generation(generation),
  _regions(iterator),
  _collection_set(_heap->collection_set()),
  _only_promote_regions(only_promote_regions)
{
  shenandoah_assert_generational();
}

void ShenandoahGenerationalEvacuationTask::work(uint worker_id) {
  ShenandoahWorkerTimingsTracker timer(ShenandoahPhaseTimings::conc_evac, ShenandoahPhaseTimings::Work, worker_id, true);
  ShenandoahConcurrentWorkerSession worker_session(worker_id);
  SuspendibleThreadSetJoiner stsj;
  do_work();
}

void ShenandoahGenerationalEvacuationTask::do_work() {
  if (_only_promote_regions) {
    assert(_heap->collection_set()->is_empty(), "Should not have a collection set here");
    promote_regions();
  } else {
    assert(!_heap->collection_set()->is_empty(), "Should have a collection set here");
    evacuate_and_promote_regions();
  }
}

void log_region(const ShenandoahHeapRegion* r, LogStream* ls) {
  ls->print_cr("GenerationalEvacuationTask, looking at %s region %zu, (age: %d) [%s, %s, %s]",
              r->is_old()? "old": r->is_young()? "young": "free", r->index(), r->age(),
              r->is_active()? "active": "inactive",
              r->is_humongous()? (r->is_humongous_start()? "humongous_start": "humongous_continuation"): "regular",
              r->is_cset()? "cset": "not-cset");
}

void ShenandoahGenerationalEvacuationTask::promote_regions() {

  ShenandoahInPlacePromoter promoter(_heap);
  ShenandoahHeapRegion* r;
  while ((r = _regions->next()) != nullptr) {
    if (LogTarget(Debug, gc) lt; lt.is_enabled()) {
      LogStream ls(lt);
      log_region(r, &ls);
    }

    promoter.maybe_promote_region(r);

    if (_heap->check_cancelled_gc_and_yield()) {
      break;
    }
  }
}

void ShenandoahGenerationalEvacuationTask::evacuate_and_promote_regions() {
  ShenandoahConcurrentEvacuator cl(_heap);
  ShenandoahHeapRegion* r;

  while ((r = _collection_set->claim_next()) != nullptr) {
    if (LogTarget(Debug, gc) lt; lt.is_enabled()) {
      LogStream ls(lt);
      log_region(r, &ls);
    }

    assert(r->has_live(), "Region %zu should have been reclaimed early", r->index());
    _heap->marked_object_iterate(r, &cl);

    if (ShenandoahCollectorPolicy::should_abandon_evacuations(r)) {
      // No more evacuations for this thread, but it may yet complete in-place promotions
      break;
    }

    if (_heap->check_cancelled_gc_and_yield()) {
      // GC is cancelled (vm is stopping), no further work
      assert(_heap->cancelled_cause() == GCCause::_shenandoah_stop_vm,
        "Evacuation should not be cancelled for: %s", GCCause::to_string(_heap->cancelled_cause()));
      return;
    }
  }

  promote_regions();
}

