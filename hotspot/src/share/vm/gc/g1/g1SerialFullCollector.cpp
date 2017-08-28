/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1FullGCScope.hpp"
#include "gc/g1/g1MarkSweep.hpp"
#include "gc/g1/g1RemSet.inline.hpp"
#include "gc/g1/g1SerialFullCollector.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/referenceProcessor.hpp"

G1SerialFullCollector::G1SerialFullCollector(G1FullGCScope* scope,
                                             ReferenceProcessor* reference_processor) :
    _scope(scope),
    _reference_processor(reference_processor),
    _is_alive_mutator(_reference_processor, NULL),
    _mt_discovery_mutator(_reference_processor, false) {
  // Temporarily make discovery by the STW ref processor single threaded (non-MT)
  // and clear the STW ref processor's _is_alive_non_header field.
}

void G1SerialFullCollector::prepare_collection() {
  _reference_processor->enable_discovery();
  _reference_processor->setup_policy(_scope->should_clear_soft_refs());
}

void G1SerialFullCollector::complete_collection() {
  // Enqueue any discovered reference objects that have
  // not been removed from the discovered lists.
  ReferenceProcessorPhaseTimes pt(NULL, _reference_processor->num_q());
  _reference_processor->enqueue_discovered_references(NULL, &pt);
  pt.print_enqueue_phase();

  // Iterate the heap and rebuild the remembered sets.
  rebuild_remembered_sets();
}

void G1SerialFullCollector::collect() {
  // Do the actual collection work.
  G1MarkSweep::invoke_at_safepoint(_reference_processor, _scope->should_clear_soft_refs());
}

class PostMCRemSetClearClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  ModRefBarrierSet* _mr_bs;
public:
  PostMCRemSetClearClosure(G1CollectedHeap* g1h, ModRefBarrierSet* mr_bs) :
    _g1h(g1h), _mr_bs(mr_bs) {}

  bool doHeapRegion(HeapRegion* r) {
    HeapRegionRemSet* hrrs = r->rem_set();

    _g1h->reset_gc_time_stamps(r);

    if (r->is_continues_humongous()) {
      // We'll assert that the strong code root list and RSet is empty
      assert(hrrs->strong_code_roots_list_length() == 0, "sanity");
      assert(hrrs->occupied() == 0, "RSet should be empty");
    } else {
      hrrs->clear();
    }
    // You might think here that we could clear just the cards
    // corresponding to the used region.  But no: if we leave a dirty card
    // in a region we might allocate into, then it would prevent that card
    // from being enqueued, and cause it to be missed.
    // Re: the performance cost: we shouldn't be doing full GC anyway!
    _mr_bs->clear(MemRegion(r->bottom(), r->end()));

    return false;
  }
};


class RebuildRSOutOfRegionClosure: public HeapRegionClosure {
  G1CollectedHeap*   _g1h;
  RebuildRSOopClosure _cl;
public:
  RebuildRSOutOfRegionClosure(G1CollectedHeap* g1, uint worker_i = 0) :
    _cl(g1->g1_rem_set(), worker_i),
    _g1h(g1)
  { }

  bool doHeapRegion(HeapRegion* r) {
    if (!r->is_continues_humongous()) {
      _cl.set_from(r);
      r->oop_iterate(&_cl);
    }
    return false;
  }
};

class ParRebuildRSTask: public AbstractGangTask {
  G1CollectedHeap* _g1;
  HeapRegionClaimer _hrclaimer;

public:
  ParRebuildRSTask(G1CollectedHeap* g1) :
      AbstractGangTask("ParRebuildRSTask"), _g1(g1), _hrclaimer(g1->workers()->active_workers()) {}

  void work(uint worker_id) {
    RebuildRSOutOfRegionClosure rebuild_rs(_g1, worker_id);
    _g1->heap_region_par_iterate(&rebuild_rs, worker_id, &_hrclaimer);
  }
};

void G1SerialFullCollector::rebuild_remembered_sets() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  // First clear the stale remembered sets.
  PostMCRemSetClearClosure rs_clear(g1h, g1h->g1_barrier_set());
  g1h->heap_region_iterate(&rs_clear);

  // Rebuild remembered sets of all regions.
  uint n_workers = AdaptiveSizePolicy::calc_active_workers(g1h->workers()->total_workers(),
                                                           g1h->workers()->active_workers(),
                                                           Threads::number_of_non_daemon_threads());
  g1h->workers()->update_active_workers(n_workers);
  log_info(gc,task)("Using %u workers of %u to rebuild remembered set", n_workers, g1h->workers()->total_workers());

  ParRebuildRSTask rebuild_rs_task(g1h);
  g1h->workers()->run_task(&rebuild_rs_task);
}
