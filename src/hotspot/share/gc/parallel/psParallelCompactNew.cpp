/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "compiler/oopMap.hpp"
#include "gc/parallel/objectStartArray.inline.hpp"
#include "gc/parallel/parallelArguments.hpp"
#include "gc/parallel/parallelScavengeHeap.inline.hpp"
#include "gc/parallel/parMarkBitMap.inline.hpp"
#include "gc/parallel/psAdaptiveSizePolicy.hpp"
#include "gc/parallel/psCompactionManagerNew.inline.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/parallel/psParallelCompactNew.inline.hpp"
#include "gc/parallel/psPromotionManager.inline.hpp"
#include "gc/parallel/psScavenge.hpp"
#include "gc/parallel/psYoungGen.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/fullGCForwarding.inline.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/shared/oopStorageSetParState.inline.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "gc/shared/workerThread.hpp"
#include "gc/shared/workerUtils.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/memoryReserver.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "nmt/memTracker.hpp"
#include "oops/methodData.hpp"
#include "runtime/java.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/threads.hpp"
#include "runtime/vmThread.hpp"
#include "services/memoryService.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/events.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#endif

SpaceInfoNew PSParallelCompactNew::_space_info[PSParallelCompactNew::last_space_id];

size_t PSParallelCompactNew::_num_regions;
PCRegionData* PSParallelCompactNew::_region_data_array;
size_t PSParallelCompactNew::_num_regions_serial;
PCRegionData* PSParallelCompactNew::_region_data_array_serial;
PCRegionData** PSParallelCompactNew::_per_worker_region_data;
bool PSParallelCompactNew::_serial = false;

SpanSubjectToDiscoveryClosure PSParallelCompactNew::_span_based_discoverer;
ReferenceProcessor* PSParallelCompactNew::_ref_processor = nullptr;

void PSParallelCompactNew::print_on_error(outputStream* st) {
  _mark_bitmap.print_on_error(st);
}

STWGCTimer          PSParallelCompactNew::_gc_timer;
ParallelOldTracer   PSParallelCompactNew::_gc_tracer;
elapsedTimer        PSParallelCompactNew::_accumulated_time;
unsigned int        PSParallelCompactNew::_maximum_compaction_gc_num = 0;
CollectorCounters*  PSParallelCompactNew::_counters = nullptr;
ParMarkBitMap       PSParallelCompactNew::_mark_bitmap;

PSParallelCompactNew::IsAliveClosure PSParallelCompactNew::_is_alive_closure;

class PCAdjustPointerClosure: public BasicOopIterateClosure {
  template <typename T>
  void do_oop_work(T* p) { PSParallelCompactNew::adjust_pointer(p); }

public:
  void do_oop(oop* p) final          { do_oop_work(p); }
  void do_oop(narrowOop* p) final    { do_oop_work(p); }

  ReferenceIterationMode reference_iteration_mode() final { return DO_FIELDS; }
};

static PCAdjustPointerClosure pc_adjust_pointer_closure;

class IsAliveClosure: public BoolObjectClosure {
public:
  bool do_object_b(oop p) final;
};


bool PSParallelCompactNew::IsAliveClosure::do_object_b(oop p) { return mark_bitmap()->is_marked(p); }

void PSParallelCompactNew::post_initialize() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  _span_based_discoverer.set_span(heap->reserved_region());
  _ref_processor =
    new ReferenceProcessor(&_span_based_discoverer,
                           ParallelGCThreads,   // mt processing degree
                           ParallelGCThreads,   // mt discovery degree
                           false,               // concurrent_discovery
                           &_is_alive_closure); // non-header is alive closure

  _counters = new CollectorCounters("Parallel full collection pauses", 1);

  // Initialize static fields in ParCompactionManager.
  ParCompactionManagerNew::initialize(mark_bitmap());
}

bool PSParallelCompactNew::initialize_aux_data() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  MemRegion mr = heap->reserved_region();
  assert(mr.byte_size() != 0, "heap should be reserved");

  initialize_space_info();

  if (!_mark_bitmap.initialize(mr)) {
    vm_shutdown_during_initialization(
      err_msg("Unable to allocate %zuKB bitmaps for parallel "
      "garbage collection for the requested %zuKB heap.",
      _mark_bitmap.reserved_byte_size()/K, mr.byte_size()/K));
    return false;
  }

  return true;
}

void PSParallelCompactNew::initialize_space_info()
{
  memset(&_space_info, 0, sizeof(_space_info));

  PSYoungGen* young_gen = ParallelScavengeHeap::young_gen();

  _space_info[old_space_id].set_space(ParallelScavengeHeap::old_gen()->object_space());
  _space_info[eden_space_id].set_space(young_gen->eden_space());
  _space_info[from_space_id].set_space(young_gen->from_space());
  _space_info[to_space_id].set_space(young_gen->to_space());

  _space_info[old_space_id].set_start_array(ParallelScavengeHeap::old_gen()->start_array());
}

void
PSParallelCompactNew::clear_data_covering_space(SpaceId id)
{
  // At this point, top is the value before GC, new_top() is the value that will
  // be set at the end of GC.  The marking bitmap is cleared to top; nothing
  // should be marked above top.
  MutableSpace* const space = _space_info[id].space();
  HeapWord* const bot = space->bottom();
  HeapWord* const top = space->top();

  _mark_bitmap.clear_range(bot, top);
}

void PSParallelCompactNew::pre_compact()
{
  // Update the from & to space pointers in space_info, since they are swapped
  // at each young gen gc.  Do the update unconditionally (even though a
  // promotion failure does not swap spaces) because an unknown number of young
  // collections will have swapped the spaces an unknown number of times.
  GCTraceTime(Debug, gc, phases) tm("Pre Compact", &_gc_timer);
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  _space_info[from_space_id].set_space(ParallelScavengeHeap::young_gen()->from_space());
  _space_info[to_space_id].set_space(ParallelScavengeHeap::young_gen()->to_space());

  // Increment the invocation count
  heap->increment_total_collections(true);

  CodeCache::on_gc_marking_cycle_start();

  heap->print_heap_before_gc();
  heap->trace_heap_before_gc(&_gc_tracer);

  // Fill in TLABs
  heap->ensure_parsability(true);  // retire TLABs

  if (VerifyBeforeGC && heap->total_collections() >= VerifyGCStartAt) {
    Universe::verify("Before GC");
  }

  DEBUG_ONLY(mark_bitmap()->verify_clear();)
}

void PSParallelCompactNew::post_compact()
{
  GCTraceTime(Info, gc, phases) tm("Post Compact", &_gc_timer);

  CodeCache::on_gc_marking_cycle_finish();
  CodeCache::arm_all_nmethods();

  for (unsigned int id = old_space_id; id < last_space_id; ++id) {
    // Clear the marking bitmap, summary data and split info.
    clear_data_covering_space(SpaceId(id));
  }

  {
    PCRegionData* last_live[last_space_id];
    for (uint i = old_space_id; i < last_space_id; ++i) {
      last_live[i] = nullptr;
    }

    // Figure out last region in each space that has live data.
    uint space_id = old_space_id;
    MutableSpace* space = _space_info[space_id].space();
    size_t num_regions = get_num_regions();
    PCRegionData* region_data_array = get_region_data_array();
    last_live[space_id] = &region_data_array[0];
    for (size_t idx = 0; idx < num_regions; idx++) {
      PCRegionData* rd = region_data_array + idx;
      if(!space->contains(rd->bottom())) {
        ++space_id;
        assert(space_id < last_space_id, "invariant");
        space = _space_info[space_id].space();
        log_develop_trace(gc, compaction)("Last live for space: %u: %zu", space_id, idx);
        last_live[space_id] = rd;
      }
      assert(space->contains(rd->bottom()), "next space should contain next region");
      log_develop_trace(gc, compaction)("post-compact region: idx: %zu, bottom: " PTR_FORMAT ", new_top: " PTR_FORMAT ", end: " PTR_FORMAT, rd->idx(), p2i(rd->bottom()), p2i(rd->new_top()), p2i(rd->end()));
      if (rd->new_top() > rd->bottom()) {
        last_live[space_id] = rd;
        log_develop_trace(gc, compaction)("Bump last live for space: %u", space_id);
      }
    }

    for (uint i = old_space_id; i < last_space_id; ++i) {
      PCRegionData* rd = last_live[i];
        log_develop_trace(gc, compaction)(
                "Last live region in space: %u, compaction region, " PTR_FORMAT ", #%zu: [" PTR_FORMAT ", " PTR_FORMAT "), new_top: " PTR_FORMAT,
                i, p2i(rd), rd->idx(),
                p2i(rd->bottom()), p2i(rd->end()), p2i(rd->new_top()));
    }

    // Fill all gaps and update the space boundaries.
    space_id = old_space_id;
    space = _space_info[space_id].space();
    size_t total_live = 0;
    size_t total_waste = 0;
    for (size_t idx = 0; idx < num_regions; idx++) {
      PCRegionData* rd = &region_data_array[idx];
      PCRegionData* last_live_in_space = last_live[space_id];
      assert(last_live_in_space != nullptr, "last live must not be null");
      if (rd != last_live_in_space) {
        if (rd->new_top() < rd->end()) {
          ObjectStartArray* sa = start_array(SpaceId(space_id));
          if (sa != nullptr) {
            sa->update_for_block(rd->new_top(), rd->end());
          }
          ParallelScavengeHeap::heap()->fill_with_dummy_object(rd->new_top(), rd->end(), false);
        }
        size_t live = pointer_delta(rd->new_top(), rd->bottom());
        size_t waste = pointer_delta(rd->end(), rd->new_top());
        total_live += live;
        total_waste += waste;
        log_develop_trace(gc, compaction)(
                "Live compaction region, #%zu: [" PTR_FORMAT ", " PTR_FORMAT "), new_top: " PTR_FORMAT ", live: %zu, waste: %zu",
                rd->idx(),
                p2i(rd->bottom()), p2i(rd->end()), p2i(rd->new_top()), live, waste);
      } else {
        // Update top of space.
        space->set_top(rd->new_top());
        size_t live = pointer_delta(rd->new_top(), rd->bottom());
        total_live += live;
        log_develop_trace(gc, compaction)(
                "Live compaction region, #%zu: [" PTR_FORMAT ", " PTR_FORMAT "), new_top: " PTR_FORMAT ", live: %zu, waste: %zu",
                rd->idx(),
                p2i(rd->bottom()), p2i(rd->end()), p2i(rd->new_top()), live, size_t(0));

        // Fast-Forward to next space.
        for (; idx < num_regions - 1; idx++) {
          rd = &region_data_array[idx + 1];
          if (!space->contains(rd->bottom())) {
            space_id++;
            assert(space_id < last_space_id, "must be");
            space = _space_info[space_id].space();
            assert(space->contains(rd->bottom()), "space must contain region");
            break;
          }
        }
      }
    }
    log_develop_debug(gc, compaction)("total live: %zu, total waste: %zu, ratio: %f", total_live, total_waste, ((float)total_waste)/((float)(total_live + total_waste)));
  }
  {
    FREE_C_HEAP_ARRAY(PCRegionData*, _per_worker_region_data);
    FREE_C_HEAP_ARRAY(PCRegionData, _region_data_array);
    FREE_C_HEAP_ARRAY(PCRegionData, _region_data_array_serial);
  }
#ifdef ASSERT
  {
    mark_bitmap()->verify_clear();
  }
#endif

  ParCompactionManagerNew::flush_all_string_dedup_requests();

  MutableSpace* const eden_space = _space_info[eden_space_id].space();
  MutableSpace* const from_space = _space_info[from_space_id].space();
  MutableSpace* const to_space   = _space_info[to_space_id].space();

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  bool eden_empty = eden_space->is_empty();

  // Update heap occupancy information which is used as input to the soft ref
  // clearing policy at the next gc.
  Universe::heap()->update_capacity_and_used_at_gc();

  bool young_gen_empty = eden_empty && from_space->is_empty() &&
    to_space->is_empty();

  PSCardTable* ct = heap->card_table();
  MemRegion old_mr = ParallelScavengeHeap::old_gen()->committed();
  if (young_gen_empty) {
    ct->clear_MemRegion(old_mr);
  } else {
    ct->dirty_MemRegion(old_mr);
  }

  {
    // Delete metaspaces for unloaded class loaders and clean up loader_data graph
    GCTraceTime(Debug, gc, phases) t("Purge Class Loader Data", gc_timer());
    ClassLoaderDataGraph::purge(true /* at_safepoint */);
    DEBUG_ONLY(MetaspaceUtils::verify();)
  }

  // Need to clear claim bits for the next mark.
  ClassLoaderDataGraph::clear_claimed_marks();

  heap->prune_scavengable_nmethods();

#if COMPILER2_OR_JVMCI
  DerivedPointerTable::update_pointers();
#endif

  // Signal that we have completed a visit to all live objects.
  Universe::heap()->record_whole_heap_examined_timestamp();
}

void PSParallelCompactNew::setup_regions_parallel() {
  static const size_t REGION_SIZE_WORDS = (SpaceAlignment / HeapWordSize);
  size_t num_regions = 0;
  for (uint i = old_space_id; i < last_space_id; ++i) {
    MutableSpace* const space = _space_info[i].space();
    size_t const space_size_words = space->capacity_in_words();
    num_regions += align_up(space_size_words, REGION_SIZE_WORDS) / REGION_SIZE_WORDS;
  }
  _region_data_array = NEW_C_HEAP_ARRAY(PCRegionData, num_regions, mtGC);

  size_t region_idx = 0;
  for (uint i = old_space_id; i < last_space_id; ++i) {
    const MutableSpace* space = _space_info[i].space();
    HeapWord* addr = space->bottom();
    HeapWord* sp_end = space->end();
    HeapWord* sp_top = space->top();
    while (addr < sp_end) {
      HeapWord* end = MIN2(align_up(addr + REGION_SIZE_WORDS, REGION_SIZE_WORDS), space->end());
      if (addr < sp_top) {
        HeapWord* prev_obj_start = _mark_bitmap.find_obj_beg_reverse(addr, end);
        if (prev_obj_start < end) {
          HeapWord* prev_obj_end = prev_obj_start + cast_to_oop(prev_obj_start)->size();
          if (end < prev_obj_end) {
            // Object crosses region boundary, adjust end to be after object's last word.
            end = prev_obj_end;
          }
        }
      }
      assert(region_idx < num_regions, "must not exceed number of regions: region_idx: %zu, num_regions: %zu", region_idx, num_regions);
      HeapWord* top;
      if (sp_top < addr) {
        top = addr;
      } else if (sp_top >= end) {
        top = end;
      } else {
        top = sp_top;
      }
      assert(ParallelScavengeHeap::heap()->is_in_reserved(addr), "addr must be in heap: " PTR_FORMAT, p2i(addr));
      new (_region_data_array + region_idx) PCRegionData(region_idx, addr, top, end);
      addr = end;
      region_idx++;
    }
  }
  _num_regions = region_idx;
  log_info(gc)("Number of regions: %zu", _num_regions);
}

void PSParallelCompactNew::setup_regions_serial() {
  _num_regions_serial = last_space_id;
  _region_data_array_serial = NEW_C_HEAP_ARRAY(PCRegionData, _num_regions_serial, mtGC);
  new (_region_data_array_serial + old_space_id)  PCRegionData(old_space_id, space(old_space_id)->bottom(), space(old_space_id)->top(), space(old_space_id)->end());
  new (_region_data_array_serial + eden_space_id) PCRegionData(eden_space_id, space(eden_space_id)->bottom(), space(eden_space_id)->top(), space(eden_space_id)->end());
  new (_region_data_array_serial + from_space_id) PCRegionData(from_space_id, space(from_space_id)->bottom(), space(from_space_id)->top(), space(from_space_id)->end());
  new (_region_data_array_serial + to_space_id)   PCRegionData(to_space_id, space(to_space_id)->bottom(), space(to_space_id)->top(), space(to_space_id)->end());
}

bool PSParallelCompactNew::check_maximum_compaction() {

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  // Check System.GC
  bool is_max_on_system_gc = UseMaximumCompactionOnSystemGC
                          && GCCause::is_user_requested_gc(heap->gc_cause());

  // JVM flags
  const uint total_invocations = heap->total_full_collections();
  assert(total_invocations >= _maximum_compaction_gc_num, "sanity");
  const size_t gcs_since_max = total_invocations - _maximum_compaction_gc_num;
  const bool is_interval_ended = gcs_since_max > HeapMaximumCompactionInterval;

  if (is_max_on_system_gc || is_interval_ended) {
    _maximum_compaction_gc_num = total_invocations;
    return true;
  }

  return false;
}

void PSParallelCompactNew::summary_phase() {
  GCTraceTime(Info, gc, phases) tm("Summary Phase", &_gc_timer);

  setup_regions_serial();
  setup_regions_parallel();

#ifndef PRODUCT
  for (size_t idx = 0; idx < _num_regions; idx++) {
    PCRegionData* rd = &_region_data_array[idx];
    log_develop_trace(gc, compaction)("Compaction region #%zu: [" PTR_FORMAT ", " PTR_FORMAT ")", rd->idx(), p2i(
            rd->bottom()), p2i(rd->end()));
  }
#endif
}

// This method should contain all heap-specific policy for invoking a full
// collection.  invoke_no_policy() will only attempt to compact the heap; it
// will do nothing further.  If we need to bail out for policy reasons, scavenge
// before full gc, or any other specialized behavior, it needs to be added here.
//
// Note that this method should only be called from the vm_thread while at a
// safepoint.
//
// Note that the all_soft_refs_clear flag in the soft ref policy
// may be true because this method can be called without intervening
// activity.  For example when the heap space is tight and full measure
// are being taken to free space.
bool PSParallelCompactNew::invoke(bool clear_all_soft_refs, bool serial) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == (Thread*)VMThread::vm_thread(),
         "should be in vm thread");

  SvcGCMarker sgcm(SvcGCMarker::FULL);
  IsSTWGCActiveMark mark;

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  clear_all_soft_refs = clear_all_soft_refs
                     || heap->soft_ref_policy()->should_clear_all_soft_refs();

  return PSParallelCompactNew::invoke_no_policy(clear_all_soft_refs, serial);
}

// This method contains no policy. You should probably
// be calling invoke() instead.
bool PSParallelCompactNew::invoke_no_policy(bool clear_all_soft_refs, bool serial) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");
  assert(ref_processor() != nullptr, "Sanity");

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  GCIdMark gc_id_mark;
  _gc_timer.register_gc_start();
  _gc_tracer.report_gc_start(heap->gc_cause(), _gc_timer.gc_start());

  GCCause::Cause gc_cause = heap->gc_cause();
  PSYoungGen* young_gen = ParallelScavengeHeap::young_gen();
  PSOldGen* old_gen = ParallelScavengeHeap::old_gen();
  PSAdaptiveSizePolicy* size_policy = heap->size_policy();

  // The scope of casr should end after code that can change
  // SoftRefPolicy::_should_clear_all_soft_refs.
  ClearedAllSoftRefs casr(clear_all_soft_refs,
                          heap->soft_ref_policy());

  // Make sure data structures are sane, make the heap parsable, and do other
  // miscellaneous bookkeeping.
  pre_compact();

  const PreGenGCValues pre_gc_values = heap->get_pre_gc_values();

  {
    const uint active_workers =
      WorkerPolicy::calc_active_workers(ParallelScavengeHeap::heap()->workers().max_workers(),
                                        ParallelScavengeHeap::heap()->workers().active_workers(),
                                        Threads::number_of_non_daemon_threads());
    ParallelScavengeHeap::heap()->workers().set_active_workers(active_workers);

    if (serial /*|| check_maximum_compaction()*/) {
      // Serial compaction executes the forwarding and compaction phases serially,
      // thus achieving perfect compaction.
      // Marking and ajust-references would still be executed in parallel threads.
      _serial = true;
    } else {
      _serial = false;
    }

    GCTraceCPUTime tcpu(&_gc_tracer);
    GCTraceTime(Info, gc) tm("Pause Full", nullptr, gc_cause, true);

    heap->pre_full_gc_dump(&_gc_timer);

    TraceCollectorStats tcs(counters());
    TraceMemoryManagerStats tms(heap->old_gc_manager(), gc_cause, "end of major GC");

    if (log_is_enabled(Debug, gc, heap, exit)) {
      accumulated_time()->start();
    }

    // Let the size policy know we're starting
    size_policy->major_collection_begin();

#if COMPILER2_OR_JVMCI
    DerivedPointerTable::clear();
#endif

    ref_processor()->start_discovery(clear_all_soft_refs);

    ClassUnloadingContext ctx(1 /* num_nmethod_unlink_workers */,
                              false /* unregister_nmethods_during_purge */,
                              false /* lock_nmethod_free_separately */);

    marking_phase(&_gc_tracer);

    summary_phase();

#if COMPILER2_OR_JVMCI
    assert(DerivedPointerTable::is_active(), "Sanity");
    DerivedPointerTable::set_active(false);
#endif

    forward_to_new_addr();

    adjust_pointers();

    compact();

    ParCompactionManagerNew::_preserved_marks_set->restore(&ParallelScavengeHeap::heap()->workers());

    // Reset the mark bitmap, summary data, and do other bookkeeping.  Must be
    // done before resizing.
    post_compact();

    // Let the size policy know we're done
    size_policy->major_collection_end(old_gen->used_in_bytes(), gc_cause);

    if (UseAdaptiveSizePolicy) {
      log_debug(gc, ergo)("AdaptiveSizeStart: collection: %d ", heap->total_collections());
      log_trace(gc, ergo)("old_gen_capacity: %zu young_gen_capacity: %zu",
                          old_gen->capacity_in_bytes(), young_gen->capacity_in_bytes());

      // Don't check if the size_policy is ready here.  Let
      // the size_policy check that internally.
      if (UseAdaptiveGenerationSizePolicyAtMajorCollection &&
          AdaptiveSizePolicy::should_update_promo_stats(gc_cause)) {
        // Swap the survivor spaces if from_space is empty. The
        // resize_young_gen() called below is normally used after
        // a successful young GC and swapping of survivor spaces;
        // otherwise, it will fail to resize the young gen with
        // the current implementation.
        if (young_gen->from_space()->is_empty()) {
          young_gen->from_space()->clear(SpaceDecorator::Mangle);
          young_gen->swap_spaces();
        }

        // Calculate optimal free space amounts
        assert(young_gen->max_gen_size() >
          young_gen->from_space()->capacity_in_bytes() +
          young_gen->to_space()->capacity_in_bytes(),
          "Sizes of space in young gen are out-of-bounds");

        size_t young_live = young_gen->used_in_bytes();
        size_t eden_live = young_gen->eden_space()->used_in_bytes();
        size_t old_live = old_gen->used_in_bytes();
        size_t cur_eden = young_gen->eden_space()->capacity_in_bytes();
        size_t max_old_gen_size = old_gen->max_gen_size();
        size_t max_eden_size = young_gen->max_gen_size() -
          young_gen->from_space()->capacity_in_bytes() -
          young_gen->to_space()->capacity_in_bytes();

        // Used for diagnostics
        size_policy->clear_generation_free_space_flags();

        size_policy->compute_generations_free_space(young_live,
                                                    eden_live,
                                                    old_live,
                                                    cur_eden,
                                                    max_old_gen_size,
                                                    max_eden_size,
                                                    true /* full gc*/);

        size_policy->check_gc_overhead_limit(eden_live,
                                             max_old_gen_size,
                                             max_eden_size,
                                             true /* full gc*/,
                                             gc_cause,
                                             heap->soft_ref_policy());

        size_policy->decay_supplemental_growth(true /* full gc*/);

        heap->resize_old_gen(
          size_policy->calculated_old_free_size_in_bytes());

        heap->resize_young_gen(size_policy->calculated_eden_size_in_bytes(),
                               size_policy->calculated_survivor_size_in_bytes());
      }

      log_debug(gc, ergo)("AdaptiveSizeStop: collection: %d ", heap->total_collections());
    }

    if (UsePerfData) {
      PSGCAdaptivePolicyCounters* const counters = ParallelScavengeHeap::gc_policy_counters();
      counters->update_counters();
      counters->update_old_capacity(old_gen->capacity_in_bytes());
      counters->update_young_capacity(young_gen->capacity_in_bytes());
    }

    heap->resize_all_tlabs();

    // Resize the metaspace capacity after a collection
    MetaspaceGC::compute_new_size();

    if (log_is_enabled(Debug, gc, heap, exit)) {
      accumulated_time()->stop();
    }

    heap->print_heap_change(pre_gc_values);

    // Track memory usage and detect low memory
    MemoryService::track_memory_usage();
    heap->update_counters();

    heap->post_full_gc_dump(&_gc_timer);
  }

  if (VerifyAfterGC && heap->total_collections() >= VerifyGCStartAt) {
    Universe::verify("After GC");
  }

  heap->print_heap_after_gc();
  heap->trace_heap_after_gc(&_gc_tracer);

  AdaptiveSizePolicyOutput::print(size_policy, heap->total_collections());

  _gc_timer.register_gc_end();

  _gc_tracer.report_gc_end(_gc_timer.gc_end(), _gc_timer.time_partitions());

  return true;
}

class PCAddThreadRootsMarkingTaskClosureNew : public ThreadClosure {
private:
  uint _worker_id;

public:
  explicit PCAddThreadRootsMarkingTaskClosureNew(uint worker_id) : _worker_id(worker_id) { }
  void do_thread(Thread* thread) final {
    assert(ParallelScavengeHeap::heap()->is_stw_gc_active(), "called outside gc");

    ResourceMark rm;

    ParCompactionManagerNew* cm = ParCompactionManagerNew::gc_thread_compaction_manager(_worker_id);

    MarkingNMethodClosure mark_and_push_in_blobs(&cm->_mark_and_push_closure,
                                                 !NMethodToOopClosure::FixRelocations,
                                                 true /* keepalive nmethods */);

    thread->oops_do(&cm->_mark_and_push_closure, &mark_and_push_in_blobs);

    // Do the real work
    cm->follow_marking_stacks();
  }
};

void steal_marking_work_new(TaskTerminator& terminator, uint worker_id) {
  assert(ParallelScavengeHeap::heap()->is_stw_gc_active(), "called outside gc");

  ParCompactionManagerNew* cm =
    ParCompactionManagerNew::gc_thread_compaction_manager(worker_id);

  do {
    ScannerTask task;
    if (ParCompactionManagerNew::steal(worker_id, task)) {
      cm->follow_contents(task, true);
    }
    cm->follow_marking_stacks();
  } while (!terminator.offer_termination());
}

class MarkFromRootsTaskNew : public WorkerTask {
  StrongRootsScope _strong_roots_scope; // needed for Threads::possibly_parallel_threads_do
  OopStorageSetStrongParState<false /* concurrent */, false /* is_const */> _oop_storage_set_par_state;
  TaskTerminator _terminator;
  uint _active_workers;

public:
  explicit MarkFromRootsTaskNew(uint active_workers) :
      WorkerTask("MarkFromRootsTaskNew"),
      _strong_roots_scope(active_workers),
      _terminator(active_workers, ParCompactionManagerNew::marking_stacks()),
      _active_workers(active_workers) {}

  void work(uint worker_id) final {
    ParCompactionManagerNew* cm = ParCompactionManagerNew::gc_thread_compaction_manager(worker_id);
    {
      CLDToOopClosure cld_closure(&cm->_mark_and_push_closure, ClassLoaderData::_claim_stw_fullgc_mark);
      ClassLoaderDataGraph::always_strong_cld_do(&cld_closure);

      // Do the real work
      cm->follow_marking_stacks();
    }

    {
      PCAddThreadRootsMarkingTaskClosureNew closure(worker_id);
      Threads::possibly_parallel_threads_do(_active_workers > 1 /* is_par */, &closure);
    }

    // Mark from OopStorages
    {
      _oop_storage_set_par_state.oops_do(&cm->_mark_and_push_closure);
      // Do the real work
      cm->follow_marking_stacks();
    }

    if (_active_workers > 1) {
      steal_marking_work_new(_terminator, worker_id);
    }
  }
};

class ParallelCompactRefProcProxyTaskNew : public RefProcProxyTask {
  TaskTerminator _terminator;

public:
  explicit ParallelCompactRefProcProxyTaskNew(uint max_workers)
    : RefProcProxyTask("ParallelCompactRefProcProxyTaskNew", max_workers),
      _terminator(_max_workers, ParCompactionManagerNew::marking_stacks()) {}

  void work(uint worker_id) final {
    assert(worker_id < _max_workers, "sanity");
    ParCompactionManagerNew* cm = (_tm == RefProcThreadModel::Single) ? ParCompactionManagerNew::get_vmthread_cm() : ParCompactionManagerNew::gc_thread_compaction_manager(worker_id);
    BarrierEnqueueDiscoveredFieldClosure enqueue;
    ParCompactionManagerNew::FollowStackClosure complete_gc(cm, (_tm == RefProcThreadModel::Single) ? nullptr : &_terminator, worker_id);
    _rp_task->rp_work(worker_id, PSParallelCompactNew::is_alive_closure(), &cm->_mark_and_push_closure, &enqueue, &complete_gc);
  }

  void prepare_run_task_hook() final {
    _terminator.reset_for_reuse(_queue_count);
  }
};

void PSParallelCompactNew::marking_phase(ParallelOldTracer *gc_tracer) {
  // Recursively traverse all live objects and mark them
  GCTraceTime(Info, gc, phases) tm("Marking Phase", &_gc_timer);

  uint active_gc_threads = ParallelScavengeHeap::heap()->workers().active_workers();

  ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_stw_fullgc_mark);
  {
    GCTraceTime(Debug, gc, phases) pm_tm("Par Mark", &_gc_timer);

    MarkFromRootsTaskNew task(active_gc_threads);
    ParallelScavengeHeap::heap()->workers().run_task(&task);
  }

  // Process reference objects found during marking
  {
    GCTraceTime(Debug, gc, phases) rp_tm("Reference Processing", &_gc_timer);

    ReferenceProcessorStats stats;
    ReferenceProcessorPhaseTimes pt(&_gc_timer, ref_processor()->max_num_queues());

    ref_processor()->set_active_mt_degree(active_gc_threads);
    ParallelCompactRefProcProxyTaskNew task(ref_processor()->max_num_queues());
    stats = ref_processor()->process_discovered_references(task, pt);

    gc_tracer->report_gc_reference_stats(stats);
    pt.print_all_references();
  }

  // This is the point where the entire marking should have completed.
  ParCompactionManagerNew::verify_all_marking_stack_empty();

  {
    GCTraceTime(Debug, gc, phases) wp_tm("Weak Processing", &_gc_timer);
    WeakProcessor::weak_oops_do(&ParallelScavengeHeap::heap()->workers(),
                                is_alive_closure(),
                                &do_nothing_cl,
                                1);
  }

  {
    GCTraceTime(Debug, gc, phases) tm_m("Class Unloading", &_gc_timer);

    ClassUnloadingContext* ctx = ClassUnloadingContext::context();

    bool unloading_occurred;
    {
      CodeCache::UnlinkingScope scope(is_alive_closure());

      // Follow system dictionary roots and unload classes.
      unloading_occurred = SystemDictionary::do_unloading(&_gc_timer);

      // Unload nmethods.
      CodeCache::do_unloading(unloading_occurred);
    }

    {
      GCTraceTime(Debug, gc, phases) t("Purge Unlinked NMethods", gc_timer());
      // Release unloaded nmethod's memory.
      ctx->purge_nmethods();
    }
    {
      GCTraceTime(Debug, gc, phases) ur("Unregister NMethods", &_gc_timer);
      ParallelScavengeHeap::heap()->prune_unlinked_nmethods();
    }
    {
      GCTraceTime(Debug, gc, phases) t("Free Code Blobs", gc_timer());
      ctx->free_nmethods();
    }

    // Prune dead klasses from subklass/sibling/implementor lists.
    Klass::clean_weak_klass_links(unloading_occurred);

    // Clean JVMCI metadata handles.
    JVMCI_ONLY(JVMCI::do_unloading(unloading_occurred));
  }

  {
    GCTraceTime(Debug, gc, phases) roc_tm("Report Object Count", &_gc_timer);
    _gc_tracer.report_object_count_after_gc(is_alive_closure(), &ParallelScavengeHeap::heap()->workers());
  }
#if TASKQUEUE_STATS
  ParCompactionManagerNew::print_and_reset_taskqueue_stats();
#endif
}

void PSParallelCompactNew::adjust_pointers_in_spaces(uint worker_id) {
  auto start_time = Ticks::now();
  for (size_t i = 0; i < _num_regions; i++) {
    PCRegionData* region = &_region_data_array[i];
    if (!region->claim()) {
      continue;
    }
    log_trace(gc, compaction)("Adjusting pointers in region: %zu (worker_id: %u)", region->idx(), worker_id);
    HeapWord* end = region->top();
    HeapWord* current = _mark_bitmap.find_obj_beg(region->bottom(), end);
    while (current < end) {
      assert(_mark_bitmap.is_marked(current), "must be marked");
      oop obj = cast_to_oop(current);
      size_t size = obj->size();
      obj->oop_iterate(&pc_adjust_pointer_closure);
      current = _mark_bitmap.find_obj_beg(current + size, end);
    }
  }
  log_trace(gc, phases)("adjust_pointers_in_spaces worker %u: %.3f ms", worker_id, (Ticks::now() - start_time).seconds() * 1000);
}

class PSAdjustTaskNew final : public WorkerTask {
  SubTasksDone                               _sub_tasks;
  WeakProcessor::Task                        _weak_proc_task;
  OopStorageSetStrongParState<false, false>  _oop_storage_iter;
  uint                                       _nworkers;

  enum PSAdjustSubTask {
    PSAdjustSubTask_code_cache,

    PSAdjustSubTask_num_elements
  };

public:
  explicit PSAdjustTaskNew(uint nworkers) :
    WorkerTask("PSAdjust task"),
    _sub_tasks(PSAdjustSubTask_num_elements),
    _weak_proc_task(nworkers),
    _nworkers(nworkers) {

    ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_stw_fullgc_adjust);
    if (nworkers > 1) {
      Threads::change_thread_claim_token();
    }
  }

  ~PSAdjustTaskNew() {
    Threads::assert_all_threads_claimed();
  }

  void work(uint worker_id) final {
    ParCompactionManagerNew* cm = ParCompactionManagerNew::gc_thread_compaction_manager(worker_id);
    cm->preserved_marks()->adjust_during_full_gc();
    {
      // adjust pointers in all spaces
      PSParallelCompactNew::adjust_pointers_in_spaces(worker_id);
    }
    {
      ResourceMark rm;
      Threads::possibly_parallel_oops_do(_nworkers > 1, &pc_adjust_pointer_closure, nullptr);
    }
    _oop_storage_iter.oops_do(&pc_adjust_pointer_closure);
    {
      CLDToOopClosure cld_closure(&pc_adjust_pointer_closure, ClassLoaderData::_claim_stw_fullgc_adjust);
      ClassLoaderDataGraph::cld_do(&cld_closure);
    }
    {
      AlwaysTrueClosure always_alive;
      _weak_proc_task.work(worker_id, &always_alive, &pc_adjust_pointer_closure);
    }
    if (_sub_tasks.try_claim_task(PSAdjustSubTask_code_cache)) {
      NMethodToOopClosure adjust_code(&pc_adjust_pointer_closure, NMethodToOopClosure::FixRelocations);
      CodeCache::nmethods_do(&adjust_code);
    }
    _sub_tasks.all_tasks_claimed();
  }
};

void PSParallelCompactNew::adjust_pointers() {
  // Adjust the pointers to reflect the new locations
  GCTraceTime(Info, gc, phases) tm("Adjust Pointers", &_gc_timer);
  uint num_workers = ParallelScavengeHeap::heap()->workers().active_workers();
  PSAdjustTaskNew task(num_workers);
  ParallelScavengeHeap::heap()->workers().run_task(&task);
}

void PSParallelCompactNew::forward_to_new_addr() {
  GCTraceTime(Info, gc, phases) tm("Forward", &_gc_timer);
  uint num_workers = get_num_workers();
  _per_worker_region_data = NEW_C_HEAP_ARRAY(PCRegionData*, num_workers, mtGC);
  for (uint i = 0; i < num_workers; i++) {
    _per_worker_region_data[i] = nullptr;
  }

  class ForwardState {
    uint _worker_id;
    PCRegionData* _compaction_region;
    HeapWord* _compaction_point;

    void ensure_compaction_point() {
      if (_compaction_point == nullptr) {
        assert(_compaction_region == nullptr, "invariant");
        _compaction_region = _per_worker_region_data[_worker_id];
        assert(_compaction_region != nullptr, "invariant");
        _compaction_point = _compaction_region->bottom();
      }
    }
  public:
    explicit ForwardState(uint worker_id) :
            _worker_id(worker_id),
            _compaction_region(nullptr),
            _compaction_point(nullptr) {
    }

    size_t available() const {
      return pointer_delta(_compaction_region->end(), _compaction_point);
    }

    void forward_objs_in_region(ParCompactionManagerNew* cm, PCRegionData* region) {
      ensure_compaction_point();
      HeapWord* end = region->end();
      HeapWord* current = _mark_bitmap.find_obj_beg(region->bottom(), end);
      while (current < end) {
        assert(_mark_bitmap.is_marked(current), "must be marked");
        oop obj = cast_to_oop(current);
        assert(region->contains(obj), "object must not cross region boundary: obj: " PTR_FORMAT ", obj_end: " PTR_FORMAT ", region start: " PTR_FORMAT ", region end: " PTR_FORMAT, p2i(obj), p2i(cast_from_oop<HeapWord*>(obj) + obj->size()), p2i(region->bottom()), p2i(region->end()));
        size_t size = obj->size();
        while (size > available()) {
          _compaction_region->set_new_top(_compaction_point);
          _compaction_region = _compaction_region->local_next();
          assert(_compaction_region != nullptr, "must find a compaction region");
          _compaction_point = _compaction_region->bottom();
        }
        //log_develop_trace(gc, compaction)("Forwarding obj: " PTR_FORMAT ", to: " PTR_FORMAT, p2i(obj), p2i(_compaction_point));
        if (current != _compaction_point) {
          cm->preserved_marks()->push_if_necessary(obj, obj->mark());
          FullGCForwarding::forward_to(obj, cast_to_oop(_compaction_point));
        }
        _compaction_point += size;
        assert(_compaction_point <= _compaction_region->end(), "object must fit in region");
        current += size;
        assert(current <= end, "object must not cross region boundary");
        current = _mark_bitmap.find_obj_beg(current, end);
      }
    }
    void finish() {
      if (_compaction_region != nullptr) {
        _compaction_region->set_new_top(_compaction_point);
      }
    }
  };

  struct ForwardTask final : public WorkerTask {
    ForwardTask() : WorkerTask("PSForward task") {}

    void work(uint worker_id) override {
      ParCompactionManagerNew* cm = ParCompactionManagerNew::gc_thread_compaction_manager(worker_id);
      ForwardState state(worker_id);
      PCRegionData** last_link = &_per_worker_region_data[worker_id];
      size_t idx = worker_id;
      uint num_workers = get_num_workers();
      size_t num_regions = get_num_regions();
      PCRegionData* region_data_array = get_region_data_array();
      while (idx < num_regions) {
        PCRegionData* region = region_data_array + idx;
        *last_link = region;
        last_link = region->local_next_addr();
        state.forward_objs_in_region(cm, region);
        idx += num_workers;
      }
      state.finish();
    }
  } task;

  uint par_workers = ParallelScavengeHeap::heap()->workers().active_workers();
  ParallelScavengeHeap::heap()->workers().set_active_workers(num_workers);
  ParallelScavengeHeap::heap()->workers().run_task(&task);
  ParallelScavengeHeap::heap()->workers().set_active_workers(par_workers);

#ifndef PRODUCT
  for (uint wid = 0; wid < num_workers; wid++) {
    for (PCRegionData* rd = _per_worker_region_data[wid]; rd != nullptr; rd = rd->local_next()) {
      log_develop_trace(gc, compaction)("Per worker compaction region, worker: %d, #%zu: [" PTR_FORMAT ", " PTR_FORMAT "), new_top: " PTR_FORMAT, wid, rd->idx(),
                                        p2i(rd->bottom()), p2i(rd->end()), p2i(rd->new_top()));
    }
  }
#endif
}

void PSParallelCompactNew::compact() {
  GCTraceTime(Info, gc, phases) tm("Compaction Phase", &_gc_timer);
  class CompactTask final : public WorkerTask {
    static void compact_region(PCRegionData* region) {
      HeapWord* bottom = region->bottom();
      HeapWord* end = region->top();
      if (bottom == end) {
        return;
      }
      HeapWord* current = _mark_bitmap.find_obj_beg(bottom, end);
      while (current < end) {
        oop obj = cast_to_oop(current);
        size_t size = obj->size();
        if (FullGCForwarding::is_forwarded(obj)) {
          oop fwd = FullGCForwarding::forwardee(obj);
          auto* dst = cast_from_oop<HeapWord*>(fwd);
          ObjectStartArray* sa = start_array(space_id(dst));
          if (sa != nullptr) {
            assert(dst != current, "expect moving object");
            sa->update_for_block(dst, dst + size);
          }

          Copy::aligned_conjoint_words(current, dst, size);
          fwd->init_mark();
        } else {
          // The start_array must be updated even if the object is not moving.
          ObjectStartArray* sa = start_array(space_id(current));
          if (sa != nullptr) {
            sa->update_for_block(current, current + size);
          }
        }
        current = _mark_bitmap.find_obj_beg(current + size, end);
      }
    }
  public:
    explicit CompactTask() : WorkerTask("PSCompact task") {}
    void work(uint worker_id) override {
      PCRegionData* region = _per_worker_region_data[worker_id];
      while (region != nullptr) {
        log_trace(gc)("Compact worker: %u, compacting region: %zu", worker_id, region->idx());
        compact_region(region);
        region = region->local_next();
      }
    }
  } task;

  uint num_workers = get_num_workers();
  uint par_workers = ParallelScavengeHeap::heap()->workers().active_workers();
  ParallelScavengeHeap::heap()->workers().set_active_workers(num_workers);
  ParallelScavengeHeap::heap()->workers().run_task(&task);
  ParallelScavengeHeap::heap()->workers().set_active_workers(par_workers);
}

// Return the SpaceId for the space containing addr.  If addr is not in the
// heap, last_space_id is returned.  In debug mode it expects the address to be
// in the heap and asserts such.
PSParallelCompactNew::SpaceId PSParallelCompactNew::space_id(HeapWord* addr) {
  assert(ParallelScavengeHeap::heap()->is_in_reserved(addr), "addr not in the heap");

  for (unsigned int id = old_space_id; id < last_space_id; ++id) {
    if (_space_info[id].space()->contains(addr)) {
      return SpaceId(id);
    }
  }

  assert(false, "no space contains the addr");
  return last_space_id;
}
