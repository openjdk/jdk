/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1FullCollector.inline.hpp"
#include "gc/g1/g1FullGCAdjustTask.hpp"
#include "gc/g1/g1FullGCCompactTask.hpp"
#include "gc/g1/g1FullGCMarker.inline.hpp"
#include "gc/g1/g1FullGCMarkTask.hpp"
#include "gc/g1/g1FullGCPrepareTask.inline.hpp"
#include "gc/g1/g1FullGCResetMetadataTask.hpp"
#include "gc/g1/g1FullGCScope.hpp"
#include "gc/g1/g1OopClosures.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1RegionMarkStatsCache.inline.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/verifyOption.hpp"
#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "logging/log.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/debug.hpp"

static void clear_and_activate_derived_pointers() {
#if COMPILER2_OR_JVMCI
  DerivedPointerTable::clear();
#endif
}

static void deactivate_derived_pointers() {
#if COMPILER2_OR_JVMCI
  DerivedPointerTable::set_active(false);
#endif
}

static void update_derived_pointers() {
#if COMPILER2_OR_JVMCI
  DerivedPointerTable::update_pointers();
#endif
}

G1CMBitMap* G1FullCollector::mark_bitmap() {
  return _heap->concurrent_mark()->mark_bitmap();
}

ReferenceProcessor* G1FullCollector::reference_processor() {
  return _heap->ref_processor_stw();
}

uint G1FullCollector::calc_active_workers() {
  G1CollectedHeap* heap = G1CollectedHeap::heap();
  uint max_worker_count = heap->workers()->max_workers();
  // Only calculate number of workers if UseDynamicNumberOfGCThreads
  // is enabled, otherwise use max.
  if (!UseDynamicNumberOfGCThreads) {
    return max_worker_count;
  }

  // Consider G1HeapWastePercent to decide max number of workers. Each worker
  // will in average cause half a region waste.
  uint max_wasted_regions_allowed = ((heap->num_regions() * G1HeapWastePercent) / 100);
  uint waste_worker_count = MAX2((max_wasted_regions_allowed * 2) , 1u);
  uint heap_waste_worker_limit = MIN2(waste_worker_count, max_worker_count);

  // Also consider HeapSizePerGCThread by calling WorkerPolicy to calculate
  // the number of workers.
  uint current_active_workers = heap->workers()->active_workers();
  uint active_worker_limit = WorkerPolicy::calc_active_workers(max_worker_count, current_active_workers, 0);

  // Finally consider the amount of used regions.
  uint used_worker_limit = heap->num_used_regions();
  assert(used_worker_limit > 0, "Should never have zero used regions.");

  // Update active workers to the lower of the limits.
  uint worker_count = MIN3(heap_waste_worker_limit, active_worker_limit, used_worker_limit);
  log_debug(gc, task)("Requesting %u active workers for full compaction (waste limited workers: %u, "
                      "adaptive workers: %u, used limited workers: %u)",
                      worker_count, heap_waste_worker_limit, active_worker_limit, used_worker_limit);
  worker_count = heap->workers()->set_active_workers(worker_count);
  log_info(gc, task)("Using %u workers of %u for full compaction", worker_count, max_worker_count);

  return worker_count;
}

G1FullCollector::G1FullCollector(G1CollectedHeap* heap,
                                 bool clear_soft_refs,
                                 bool do_maximal_compaction,
                                 G1FullGCTracer* tracer) :
    _heap(heap),
    _scope(heap->monitoring_support(), clear_soft_refs, do_maximal_compaction, tracer),
    _num_workers(calc_active_workers()),
    _has_compaction_targets(false),
    _has_humongous(false),
    _oop_queue_set(_num_workers),
    _array_queue_set(_num_workers),
    _preserved_marks_set(true),
    _serial_compaction_point(this, nullptr),
    _humongous_compaction_point(this, nullptr),
    _is_alive(this, heap->concurrent_mark()->mark_bitmap()),
    _is_alive_mutator(heap->ref_processor_stw(), &_is_alive),
    _humongous_compaction_regions(8),
    _always_subject_to_discovery(),
    _is_subject_mutator(heap->ref_processor_stw(), &_always_subject_to_discovery),
    _region_attr_table() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");

  _preserved_marks_set.init(_num_workers);
  _markers = NEW_C_HEAP_ARRAY(G1FullGCMarker*, _num_workers, mtGC);
  _compaction_points = NEW_C_HEAP_ARRAY(G1FullGCCompactionPoint*, _num_workers, mtGC);

  _live_stats = NEW_C_HEAP_ARRAY(G1RegionMarkStats, _heap->max_regions(), mtGC);
  _compaction_tops = NEW_C_HEAP_ARRAY(HeapWord*, _heap->max_regions(), mtGC);
  for (uint j = 0; j < heap->max_regions(); j++) {
    _live_stats[j].clear();
    _compaction_tops[j] = nullptr;
  }

  for (uint i = 0; i < _num_workers; i++) {
    _markers[i] = new G1FullGCMarker(this, i, _live_stats);
    _compaction_points[i] = new G1FullGCCompactionPoint(this, _preserved_marks_set.get(i));
    _oop_queue_set.register_queue(i, marker(i)->oop_stack());
    _array_queue_set.register_queue(i, marker(i)->objarray_stack());
  }
  _serial_compaction_point.set_preserved_stack(_preserved_marks_set.get(0));
  _humongous_compaction_point.set_preserved_stack(_preserved_marks_set.get(0));
  _region_attr_table.initialize(heap->reserved(), HeapRegion::GrainBytes);
}

G1FullCollector::~G1FullCollector() {
  for (uint i = 0; i < _num_workers; i++) {
    delete _markers[i];
    delete _compaction_points[i];
  }

  FREE_C_HEAP_ARRAY(G1FullGCMarker*, _markers);
  FREE_C_HEAP_ARRAY(G1FullGCCompactionPoint*, _compaction_points);
  FREE_C_HEAP_ARRAY(HeapWord*, _compaction_tops);
  FREE_C_HEAP_ARRAY(G1RegionMarkStats, _live_stats);
}

class PrepareRegionsClosure : public HeapRegionClosure {
  G1FullCollector* _collector;

public:
  PrepareRegionsClosure(G1FullCollector* collector) : _collector(collector) { }

  bool do_heap_region(HeapRegion* hr) {
    hr->prepare_for_full_gc();
    G1CollectedHeap::heap()->prepare_region_for_full_compaction(hr);
    _collector->before_marking_update_attribute_table(hr);
    return false;
  }
};

void G1FullCollector::prepare_collection() {
  _heap->policy()->record_full_collection_start();

  // Verification needs the bitmap, so we should clear the bitmap only later.
  bool in_concurrent_cycle = _heap->abort_concurrent_cycle();
  _heap->verify_before_full_collection();
  if (in_concurrent_cycle) {
    GCTraceTime(Debug, gc) debug("Clear Bitmap");
    _heap->concurrent_mark()->clear_bitmap(_heap->workers());
  }

  _heap->gc_prologue(true);
  _heap->retire_tlabs();
  _heap->flush_region_pin_cache();
  _heap->prepare_heap_for_full_collection();

  PrepareRegionsClosure cl(this);
  _heap->heap_region_iterate(&cl);

  reference_processor()->start_discovery(scope()->should_clear_soft_refs());

  // Clear and activate derived pointer collection.
  clear_and_activate_derived_pointers();
}

void G1FullCollector::collect() {
  G1CollectedHeap::start_codecache_marking_cycle_if_inactive(false /* concurrent_mark_start */);

  phase1_mark_live_objects();
  verify_after_marking();

  // Don't add any more derived pointers during later phases
  deactivate_derived_pointers();

  phase2_prepare_compaction();

  if (has_compaction_targets()) {
    phase3_adjust_pointers();

    phase4_do_compaction();
  } else {
    // All regions have a high live ratio thus will not be compacted.
    // The live ratio is only considered if do_maximal_compaction is false.
    log_info(gc, phases) ("No Regions selected for compaction. Skipping Phase 3: Adjust pointers and Phase 4: Compact heap");
  }

  phase5_reset_metadata();

  G1CollectedHeap::finish_codecache_marking_cycle();
}

void G1FullCollector::complete_collection() {
  // Restore all marks.
  restore_marks();

  // When the pointers have been adjusted and moved, we can
  // update the derived pointer table.
  update_derived_pointers();

  // Need completely cleared claim bits for the next concurrent marking or full gc.
  ClassLoaderDataGraph::clear_claimed_marks();

  // Prepare the bitmap for the next (potentially concurrent) marking.
  _heap->concurrent_mark()->clear_bitmap(_heap->workers());

  _heap->prepare_for_mutator_after_full_collection();

  _heap->resize_all_tlabs();

  _heap->policy()->record_full_collection_end();
  _heap->gc_epilogue(true);

  _heap->verify_after_full_collection();

  _heap->print_heap_after_full_collection();
}

void G1FullCollector::before_marking_update_attribute_table(HeapRegion* hr) {
  if (hr->is_free()) {
    _region_attr_table.set_free(hr->hrm_index());
  } else if (hr->is_humongous() || hr->has_pinned_objects()) {
    // Humongous objects or pinned regions will never be moved in the "main"
    // compaction phase, but non-pinned regions might afterwards in a special phase.
    _region_attr_table.set_skip_compacting(hr->hrm_index());
  } else {
    // Everything else should be compacted.
    _region_attr_table.set_compacting(hr->hrm_index());
  }
}

class G1FullGCRefProcProxyTask : public RefProcProxyTask {
  G1FullCollector& _collector;

public:
  G1FullGCRefProcProxyTask(G1FullCollector &collector, uint max_workers)
    : RefProcProxyTask("G1FullGCRefProcProxyTask", max_workers),
      _collector(collector) {}

  void work(uint worker_id) override {
    assert(worker_id < _max_workers, "sanity");
    G1IsAliveClosure is_alive(&_collector);
    uint index = (_tm == RefProcThreadModel::Single) ? 0 : worker_id;
    G1FullKeepAliveClosure keep_alive(_collector.marker(index));
    BarrierEnqueueDiscoveredFieldClosure enqueue;
    G1FollowStackClosure* complete_gc = _collector.marker(index)->stack_closure();
    _rp_task->rp_work(worker_id, &is_alive, &keep_alive, &enqueue, complete_gc);
  }
};

void G1FullCollector::phase1_mark_live_objects() {
  // Recursively traverse all live objects and mark them.
  GCTraceTime(Info, gc, phases) info("Phase 1: Mark live objects", scope()->timer());

  {
    // Do the actual marking.
    G1FullGCMarkTask marking_task(this);
    run_task(&marking_task);
  }

  {
    uint old_active_mt_degree = reference_processor()->num_queues();
    reference_processor()->set_active_mt_degree(workers());
    GCTraceTime(Debug, gc, phases) debug("Phase 1: Reference Processing", scope()->timer());
    // Process reference objects found during marking.
    ReferenceProcessorPhaseTimes pt(scope()->timer(), reference_processor()->max_num_queues());
    G1FullGCRefProcProxyTask task(*this, reference_processor()->max_num_queues());
    const ReferenceProcessorStats& stats = reference_processor()->process_discovered_references(task, pt);
    scope()->tracer()->report_gc_reference_stats(stats);
    pt.print_all_references();
    assert(marker(0)->oop_stack()->is_empty(), "Should be no oops on the stack");

    reference_processor()->set_active_mt_degree(old_active_mt_degree);
  }

  {
    GCTraceTime(Debug, gc, phases) debug("Phase 1: Flush Mark Stats Cache", scope()->timer());
    for (uint i = 0; i < workers(); i++) {
      marker(i)->flush_mark_stats_cache();
    }
  }

  // Weak oops cleanup.
  {
    GCTraceTime(Debug, gc, phases) debug("Phase 1: Weak Processing", scope()->timer());
    WeakProcessor::weak_oops_do(_heap->workers(), &_is_alive, &do_nothing_cl, 1);
  }

  // Class unloading and cleanup.
  if (ClassUnloading) {
    _heap->unload_classes_and_code("Phase 1: Class Unloading and Cleanup", &_is_alive, scope()->timer());
  }

  {
    GCTraceTime(Debug, gc, phases) debug("Report Object Count", scope()->timer());
    scope()->tracer()->report_object_count_after_gc(&_is_alive, _heap->workers());
  }
#if TASKQUEUE_STATS
  oop_queue_set()->print_and_reset_taskqueue_stats("Oop Queue");
  array_queue_set()->print_and_reset_taskqueue_stats("ObjArrayOop Queue");
#endif
}

void G1FullCollector::phase2_prepare_compaction() {
  GCTraceTime(Info, gc, phases) info("Phase 2: Prepare compaction", scope()->timer());

  phase2a_determine_worklists();

  if (!has_compaction_targets()) {
    return;
  }

  bool has_free_compaction_targets = phase2b_forward_oops();

  // Try to avoid OOM immediately after Full GC in case there are no free regions
  // left after determining the result locations (i.e. this phase). Prepare to
  // maximally compact the tail regions of the compaction queues serially.
  if (scope()->do_maximal_compaction() || !has_free_compaction_targets) {
    phase2c_prepare_serial_compaction();

    if (scope()->do_maximal_compaction() &&
        has_humongous() &&
        serial_compaction_point()->has_regions()) {
      phase2d_prepare_humongous_compaction();
    }
  }
}

void G1FullCollector::phase2a_determine_worklists() {
  GCTraceTime(Debug, gc, phases) debug("Phase 2: Determine work lists", scope()->timer());

  G1DetermineCompactionQueueClosure cl(this);
  _heap->heap_region_iterate(&cl);
}

bool G1FullCollector::phase2b_forward_oops() {
  GCTraceTime(Debug, gc, phases) debug("Phase 2: Prepare parallel compaction", scope()->timer());

  G1FullGCPrepareTask task(this);
  run_task(&task);

  return task.has_free_compaction_targets();
}

uint G1FullCollector::truncate_parallel_cps() {
  uint lowest_current = UINT_MAX;
  for (uint i = 0; i < workers(); i++) {
    G1FullGCCompactionPoint* cp = compaction_point(i);
    if (cp->has_regions()) {
      lowest_current = MIN2(lowest_current, cp->current_region()->hrm_index());
    }
  }

  for (uint i = 0; i < workers(); i++) {
    G1FullGCCompactionPoint* cp = compaction_point(i);
    if (cp->has_regions()) {
      cp->remove_at_or_above(lowest_current);
    }
  }
  return lowest_current;
}

void G1FullCollector::phase2c_prepare_serial_compaction() {
  GCTraceTime(Debug, gc, phases) debug("Phase 2: Prepare serial compaction", scope()->timer());
  // At this point, we know that after parallel compaction there will be regions that
  // are partially compacted into. Thus, the last compaction region of all
  // compaction queues still have space in them. We try to re-compact these regions
  // in serial to avoid a premature OOM when the mutator wants to allocate the first
  // eden region after gc.

  // For maximum compaction, we need to re-prepare all objects above the lowest
  // region among the current regions for all thread compaction points. It may
  // happen that due to the uneven distribution of objects to parallel threads, holes
  // have been created as threads compact to different target regions between the
  // lowest and the highest region in the tails of the compaction points.

  uint start_serial = truncate_parallel_cps();
  assert(start_serial < _heap->max_reserved_regions(), "Called on empty parallel compaction queues");

  G1FullGCCompactionPoint* serial_cp = serial_compaction_point();
  assert(!serial_cp->is_initialized(), "sanity!");

  HeapRegion* start_hr = _heap->region_at(start_serial);
  serial_cp->add(start_hr);
  serial_cp->initialize(start_hr);

  HeapWord* dense_prefix_top = compaction_top(start_hr);
  G1SerialRePrepareClosure re_prepare(serial_cp, dense_prefix_top);

  for (uint i = start_serial + 1; i < _heap->max_reserved_regions(); i++) {
    if (is_compaction_target(i)) {
      HeapRegion* current = _heap->region_at(i);
      set_compaction_top(current, current->bottom());
      serial_cp->add(current);
      current->apply_to_marked_objects(mark_bitmap(), &re_prepare);
    }
  }
  serial_cp->update();
}

void G1FullCollector::phase2d_prepare_humongous_compaction() {
  GCTraceTime(Debug, gc, phases) debug("Phase 2: Prepare humongous compaction", scope()->timer());
  G1FullGCCompactionPoint* serial_cp = serial_compaction_point();
  assert(serial_cp->has_regions(), "Sanity!" );

  uint last_serial_target = serial_cp->current_region()->hrm_index();
  uint region_index = last_serial_target + 1;
  uint max_reserved_regions = _heap->max_reserved_regions();

  G1FullGCCompactionPoint* humongous_cp = humongous_compaction_point();

  while (region_index < max_reserved_regions) {
    HeapRegion* hr = _heap->region_at_or_null(region_index);

    if (hr == nullptr) {
      region_index++;
      continue;
    } else if (hr->is_starts_humongous()) {
      size_t obj_size = cast_to_oop(hr->bottom())->size();
      uint num_regions = (uint)G1CollectedHeap::humongous_obj_size_in_regions(obj_size);
      // Even during last-ditch compaction we should not move pinned humongous objects.
      if (!hr->has_pinned_objects()) {
        humongous_cp->forward_humongous(hr);
      }
      region_index += num_regions; // Advance over all humongous regions.
      continue;
    } else if (is_compaction_target(region_index)) {
      assert(!hr->has_pinned_objects(), "pinned regions should not be compaction targets");
      // Add the region to the humongous compaction point.
      humongous_cp->add(hr);
    }
    region_index++;
  }
}

void G1FullCollector::phase3_adjust_pointers() {
  // Adjust the pointers to reflect the new locations
  GCTraceTime(Info, gc, phases) info("Phase 3: Adjust pointers", scope()->timer());

  G1FullGCAdjustTask task(this);
  run_task(&task);
}

void G1FullCollector::phase4_do_compaction() {
  // Compact the heap using the compaction queues created in phase 2.
  GCTraceTime(Info, gc, phases) info("Phase 4: Compact heap", scope()->timer());
  G1FullGCCompactTask task(this);
  run_task(&task);

  // Serial compact to avoid OOM when very few free regions.
  if (serial_compaction_point()->has_regions()) {
    task.serial_compaction();
  }

  if (!_humongous_compaction_regions.is_empty()) {
    assert(scope()->do_maximal_compaction(), "Only compact humongous during maximal compaction");
    task.humongous_compaction();
  }
}

void G1FullCollector::phase5_reset_metadata() {
  // Clear region metadata that is invalid after GC for all regions.
  GCTraceTime(Info, gc, phases) info("Phase 5: Reset Metadata", scope()->timer());
  G1FullGCResetMetadataTask task(this);
  run_task(&task);
}

void G1FullCollector::restore_marks() {
  _preserved_marks_set.restore(_heap->workers());
  _preserved_marks_set.reclaim();
}

void G1FullCollector::run_task(WorkerTask* task) {
  _heap->workers()->run_task(task, _num_workers);
}

void G1FullCollector::verify_after_marking() {
  if (!VerifyDuringGC || !_heap->verifier()->should_verify(G1HeapVerifier::G1VerifyFull)) {
    // Only do verification if VerifyDuringGC and G1VerifyFull is set.
    return;
  }

#if COMPILER2_OR_JVMCI
  DerivedPointerTableDeactivate dpt_deact;
#endif
  _heap->prepare_for_verify();
  // Note: we can verify only the heap here. When an object is
  // marked, the previous value of the mark word (including
  // identity hash values, ages, etc) is preserved, and the mark
  // word is set to markWord::marked_value - effectively removing
  // any hash values from the mark word. These hash values are
  // used when verifying the dictionaries and so removing them
  // from the mark word can make verification of the dictionaries
  // fail. At the end of the GC, the original mark word values
  // (including hash values) are restored to the appropriate
  // objects.
  GCTraceTime(Info, gc, verify) tm("Verifying During GC (full)");
  _heap->verify(VerifyOption::G1UseFullMarking);
}
