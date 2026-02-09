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
                                                                           bool concurrent, bool only_promote_regions) :
  WorkerTask("Shenandoah Evacuation"),
  _heap(heap),
  _generation(generation),
  _regions(iterator),
  _concurrent(concurrent),
  _only_promote_regions(only_promote_regions)
{
  shenandoah_assert_generational();
}

void ShenandoahGenerationalEvacuationTask::work(uint worker_id) {
  if (_concurrent) {
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    ShenandoahSuspendibleThreadSetJoiner stsj;
    do_work();
  } else {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    do_work();
  }
}

void ShenandoahGenerationalEvacuationTask::do_work() {
  if (_only_promote_regions) {
    // No allocations will be made, do not enter oom-during-evac protocol.
    assert(_heap->collection_set()->is_empty(), "Should not have a collection set here");
    promote_regions();
  } else {
    assert(!_heap->collection_set()->is_empty(), "Should have a collection set here");
    ShenandoahEvacOOMScope oom_evac_scope;
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
  LogTarget(Debug, gc) lt;
  ShenandoahInPlacePromoter promoter(_heap);
  ShenandoahHeapRegion* r;
  while ((r = _regions->next()) != nullptr) {
    if (lt.is_enabled()) {
      LogStream ls(lt);
      log_region(r, &ls);
    }

    promoter.maybe_promote_region(r);

    if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
      break;
    }
  }
}

void ShenandoahGenerationalEvacuationTask::evacuate_and_promote_regions() {
  LogTarget(Debug, gc) lt;
  ShenandoahConcurrentEvacuator cl(_heap);
  ShenandoahInPlacePromoter promoter(_heap);
  ShenandoahHeapRegion* r;

  while ((r = _regions->next()) != nullptr) {
    if (lt.is_enabled()) {
      LogStream ls(lt);
      log_region(r, &ls);
    }

    if (r->is_cset()) {
      assert(r->has_live(), "Region %zu should have been reclaimed early", r->index());
      _heap->marked_object_iterate(r, &cl);
    } else {
      promoter.maybe_promote_region(r);
    }

    if (_heap->check_cancelled_gc_and_yield(_concurrent)) {
      break;
    }
  }
}
