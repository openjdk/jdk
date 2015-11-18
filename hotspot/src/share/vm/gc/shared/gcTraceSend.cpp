/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/copyFailedInfo.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcWhen.hpp"
#include "runtime/os.hpp"
#include "trace/traceBackend.hpp"
#include "trace/tracing.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/evacuationInfo.hpp"
#include "gc/g1/g1YCTypes.hpp"
#include "tracefiles/traceEventClasses.hpp"
#endif

// All GC dependencies against the trace framework is contained within this file.

typedef uintptr_t TraceAddress;

void GCTracer::send_garbage_collection_event() const {
  EventGCGarbageCollection event(UNTIMED);
  if (event.should_commit()) {
    event.set_gcId(GCId::current());
    event.set_name(_shared_gc_info.name());
    event.set_cause((u2) _shared_gc_info.cause());
    event.set_sumOfPauses(_shared_gc_info.sum_of_pauses());
    event.set_longestPause(_shared_gc_info.longest_pause());
    event.set_starttime(_shared_gc_info.start_timestamp());
    event.set_endtime(_shared_gc_info.end_timestamp());
    event.commit();
  }
}

void GCTracer::send_reference_stats_event(ReferenceType type, size_t count) const {
  EventGCReferenceStatistics e;
  if (e.should_commit()) {
      e.set_gcId(GCId::current());
      e.set_type((u1)type);
      e.set_count(count);
      e.commit();
  }
}

void GCTracer::send_metaspace_chunk_free_list_summary(GCWhen::Type when, Metaspace::MetadataType mdtype,
                                                      const MetaspaceChunkFreeListSummary& summary) const {
  EventMetaspaceChunkFreeListSummary e;
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_when(when);
    e.set_metadataType(mdtype);

    e.set_specializedChunks(summary.num_specialized_chunks());
    e.set_specializedChunksTotalSize(summary.specialized_chunks_size_in_bytes());

    e.set_smallChunks(summary.num_small_chunks());
    e.set_smallChunksTotalSize(summary.small_chunks_size_in_bytes());

    e.set_mediumChunks(summary.num_medium_chunks());
    e.set_mediumChunksTotalSize(summary.medium_chunks_size_in_bytes());

    e.set_humongousChunks(summary.num_humongous_chunks());
    e.set_humongousChunksTotalSize(summary.humongous_chunks_size_in_bytes());

    e.commit();
  }
}

void ParallelOldTracer::send_parallel_old_event() const {
  EventGCParallelOld e(UNTIMED);
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_densePrefix((TraceAddress)_parallel_old_gc_info.dense_prefix());
    e.set_starttime(_shared_gc_info.start_timestamp());
    e.set_endtime(_shared_gc_info.end_timestamp());
    e.commit();
  }
}

void YoungGCTracer::send_young_gc_event() const {
  EventGCYoungGarbageCollection e(UNTIMED);
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_tenuringThreshold(_tenuring_threshold);
    e.set_starttime(_shared_gc_info.start_timestamp());
    e.set_endtime(_shared_gc_info.end_timestamp());
    e.commit();
  }
}

bool YoungGCTracer::should_send_promotion_in_new_plab_event() const {
  return EventPromoteObjectInNewPLAB::is_enabled();
}

bool YoungGCTracer::should_send_promotion_outside_plab_event() const {
  return EventPromoteObjectOutsidePLAB::is_enabled();
}

void YoungGCTracer::send_promotion_in_new_plab_event(Klass* klass, size_t obj_size,
                                                     uint age, bool tenured,
                                                     size_t plab_size) const {

  EventPromoteObjectInNewPLAB event;
  if (event.should_commit()) {
    event.set_gcId(GCId::current());
    event.set_class(klass);
    event.set_objectSize(obj_size);
    event.set_tenured(tenured);
    event.set_tenuringAge(age);
    event.set_plabSize(plab_size);
    event.commit();
  }
}

void YoungGCTracer::send_promotion_outside_plab_event(Klass* klass, size_t obj_size,
                                                      uint age, bool tenured) const {

  EventPromoteObjectOutsidePLAB event;
  if (event.should_commit()) {
    event.set_gcId(GCId::current());
    event.set_class(klass);
    event.set_objectSize(obj_size);
    event.set_tenured(tenured);
    event.set_tenuringAge(age);
    event.commit();
  }
}

void OldGCTracer::send_old_gc_event() const {
  EventGCOldGarbageCollection e(UNTIMED);
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_starttime(_shared_gc_info.start_timestamp());
    e.set_endtime(_shared_gc_info.end_timestamp());
    e.commit();
  }
}

static TraceStructCopyFailed to_trace_struct(const CopyFailedInfo& cf_info) {
  TraceStructCopyFailed failed_info;
  failed_info.set_objectCount(cf_info.failed_count());
  failed_info.set_firstSize(cf_info.first_size());
  failed_info.set_smallestSize(cf_info.smallest_size());
  failed_info.set_totalSize(cf_info.total_size());
  return failed_info;
}

void YoungGCTracer::send_promotion_failed_event(const PromotionFailedInfo& pf_info) const {
  EventPromotionFailed e;
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_data(to_trace_struct(pf_info));
    e.set_thread(pf_info.thread()->thread_id());
    e.commit();
  }
}

// Common to CMS and G1
void OldGCTracer::send_concurrent_mode_failure_event() {
  EventConcurrentModeFailure e;
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.commit();
  }
}

#if INCLUDE_ALL_GCS
void G1NewTracer::send_g1_young_gc_event() {
  EventGCG1GarbageCollection e(UNTIMED);
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_type(_g1_young_gc_info.type());
    e.set_starttime(_shared_gc_info.start_timestamp());
    e.set_endtime(_shared_gc_info.end_timestamp());
    e.commit();
  }
}

void G1MMUTracer::send_g1_mmu_event(double timeSlice, double gcTime, double maxTime) {
  EventGCG1MMU e;
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_timeSlice(timeSlice);
    e.set_gcTime(gcTime);
    e.set_maxGcTime(maxTime);
    e.commit();
  }
}

void G1NewTracer::send_evacuation_info_event(EvacuationInfo* info) {
  EventEvacuationInfo e;
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_cSetRegions(info->collectionset_regions());
    e.set_cSetUsedBefore(info->collectionset_used_before());
    e.set_cSetUsedAfter(info->collectionset_used_after());
    e.set_allocationRegions(info->allocation_regions());
    e.set_allocRegionsUsedBefore(info->alloc_regions_used_before());
    e.set_allocRegionsUsedAfter(info->alloc_regions_used_before() + info->bytes_copied());
    e.set_bytesCopied(info->bytes_copied());
    e.set_regionsFreed(info->regions_freed());
    e.commit();
  }
}

void G1NewTracer::send_evacuation_failed_event(const EvacuationFailedInfo& ef_info) const {
  EventEvacuationFailed e;
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_data(to_trace_struct(ef_info));
    e.commit();
  }
}

static TraceStructG1EvacStats create_g1_evacstats(unsigned gcid, const G1EvacSummary& summary) {
  TraceStructG1EvacStats s;
  s.set_gcId(gcid);
  s.set_allocated(summary.allocated() * HeapWordSize);
  s.set_wasted(summary.wasted() * HeapWordSize);
  s.set_used(summary.used() * HeapWordSize);
  s.set_undoWaste(summary.undo_wasted() * HeapWordSize);
  s.set_regionEndWaste(summary.region_end_waste() * HeapWordSize);
  s.set_regionsRefilled(summary.regions_filled());
  s.set_directAllocated(summary.direct_allocated() * HeapWordSize);
  s.set_failureUsed(summary.failure_used() * HeapWordSize);
  s.set_failureWaste(summary.failure_waste() * HeapWordSize);
  return s;
}

void G1NewTracer::send_young_evacuation_statistics(const G1EvacSummary& summary) const {
  EventGCG1EvacuationYoungStatistics surv_evt;
  if (surv_evt.should_commit()) {
    surv_evt.set_stats(create_g1_evacstats(GCId::current(), summary));
    surv_evt.commit();
  }
}

void G1NewTracer::send_old_evacuation_statistics(const G1EvacSummary& summary) const {
  EventGCG1EvacuationOldStatistics old_evt;
  if (old_evt.should_commit()) {
    old_evt.set_stats(create_g1_evacstats(GCId::current(), summary));
    old_evt.commit();
  }
}

void G1NewTracer::send_basic_ihop_statistics(size_t threshold,
                                             size_t target_occupancy,
                                             size_t current_occupancy,
                                             size_t last_allocation_size,
                                             double last_allocation_duration,
                                             double last_marking_length) {
  EventGCG1BasicIHOP evt;
  if (evt.should_commit()) {
    evt.set_gcId(GCId::current());
    evt.set_threshold(threshold);
    evt.set_targetOccupancy(target_occupancy);
    evt.set_thresholdPercentage(target_occupancy > 0 ? (threshold * 100 / target_occupancy) : 0);
    evt.set_currentOccupancy(current_occupancy);
    evt.set_lastAllocationSize(last_allocation_size);
    evt.set_lastAllocationDuration(last_allocation_duration);
    evt.set_lastAllocationRate(last_allocation_duration != 0.0 ? last_allocation_size / last_allocation_duration : 0.0);
    evt.set_lastMarkingLength(last_marking_length);
    evt.commit();
  }
}

void G1NewTracer::send_adaptive_ihop_statistics(size_t threshold,
                                                size_t internal_target_occupancy,
                                                size_t current_occupancy,
                                                size_t additional_buffer_size,
                                                double predicted_allocation_rate,
                                                double predicted_marking_length,
                                                bool prediction_active) {
  EventGCG1AdaptiveIHOP evt;
  if (evt.should_commit()) {
    evt.set_gcId(GCId::current());
    evt.set_threshold(threshold);
    evt.set_thresholdPercentage(internal_target_occupancy > 0 ? (threshold * 100 / internal_target_occupancy) : 0);
    evt.set_internalTargetOccupancy(internal_target_occupancy);
    evt.set_currentOccupancy(current_occupancy);
    evt.set_additionalBufferSize(additional_buffer_size);
    evt.set_predictedAllocationRate(predicted_allocation_rate);
    evt.set_predictedMarkingLength(predicted_marking_length);
    evt.set_predictionActive(prediction_active);
    evt.commit();
  }
}

#endif

static TraceStructVirtualSpace to_trace_struct(const VirtualSpaceSummary& summary) {
  TraceStructVirtualSpace space;
  space.set_start((TraceAddress)summary.start());
  space.set_committedEnd((TraceAddress)summary.committed_end());
  space.set_committedSize(summary.committed_size());
  space.set_reservedEnd((TraceAddress)summary.reserved_end());
  space.set_reservedSize(summary.reserved_size());
  return space;
}

static TraceStructObjectSpace to_trace_struct(const SpaceSummary& summary) {
  TraceStructObjectSpace space;
  space.set_start((TraceAddress)summary.start());
  space.set_end((TraceAddress)summary.end());
  space.set_used(summary.used());
  space.set_size(summary.size());
  return space;
}

class GCHeapSummaryEventSender : public GCHeapSummaryVisitor {
  GCWhen::Type _when;
 public:
  GCHeapSummaryEventSender(GCWhen::Type when) : _when(when) {}

  void visit(const GCHeapSummary* heap_summary) const {
    const VirtualSpaceSummary& heap_space = heap_summary->heap();

    EventGCHeapSummary e;
    if (e.should_commit()) {
      e.set_gcId(GCId::current());
      e.set_when((u1)_when);
      e.set_heapSpace(to_trace_struct(heap_space));
      e.set_heapUsed(heap_summary->used());
      e.commit();
    }
  }

  void visit(const G1HeapSummary* g1_heap_summary) const {
    visit((GCHeapSummary*)g1_heap_summary);

    EventG1HeapSummary e;
    if (e.should_commit()) {
      e.set_gcId(GCId::current());
      e.set_when((u1)_when);
      e.set_edenUsedSize(g1_heap_summary->edenUsed());
      e.set_edenTotalSize(g1_heap_summary->edenCapacity());
      e.set_survivorUsedSize(g1_heap_summary->survivorUsed());
      e.commit();
    }
  }

  void visit(const PSHeapSummary* ps_heap_summary) const {
    visit((GCHeapSummary*)ps_heap_summary);

    const VirtualSpaceSummary& old_summary = ps_heap_summary->old();
    const SpaceSummary& old_space = ps_heap_summary->old_space();
    const VirtualSpaceSummary& young_summary = ps_heap_summary->young();
    const SpaceSummary& eden_space = ps_heap_summary->eden();
    const SpaceSummary& from_space = ps_heap_summary->from();
    const SpaceSummary& to_space = ps_heap_summary->to();

    EventPSHeapSummary e;
    if (e.should_commit()) {
      e.set_gcId(GCId::current());
      e.set_when((u1)_when);

      e.set_oldSpace(to_trace_struct(ps_heap_summary->old()));
      e.set_oldObjectSpace(to_trace_struct(ps_heap_summary->old_space()));
      e.set_youngSpace(to_trace_struct(ps_heap_summary->young()));
      e.set_edenSpace(to_trace_struct(ps_heap_summary->eden()));
      e.set_fromSpace(to_trace_struct(ps_heap_summary->from()));
      e.set_toSpace(to_trace_struct(ps_heap_summary->to()));
      e.commit();
    }
  }
};

void GCTracer::send_gc_heap_summary_event(GCWhen::Type when, const GCHeapSummary& heap_summary) const {
  GCHeapSummaryEventSender visitor(when);
  heap_summary.accept(&visitor);
}

static TraceStructMetaspaceSizes to_trace_struct(const MetaspaceSizes& sizes) {
  TraceStructMetaspaceSizes meta_sizes;

  meta_sizes.set_committed(sizes.committed());
  meta_sizes.set_used(sizes.used());
  meta_sizes.set_reserved(sizes.reserved());

  return meta_sizes;
}

void GCTracer::send_meta_space_summary_event(GCWhen::Type when, const MetaspaceSummary& meta_space_summary) const {
  EventMetaspaceSummary e;
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_when((u1) when);
    e.set_gcThreshold(meta_space_summary.capacity_until_GC());
    e.set_metaspace(to_trace_struct(meta_space_summary.meta_space()));
    e.set_dataSpace(to_trace_struct(meta_space_summary.data_space()));
    e.set_classSpace(to_trace_struct(meta_space_summary.class_space()));
    e.commit();
  }
}

class PhaseSender : public PhaseVisitor {
 public:
  template<typename T>
  void send_phase(PausePhase* pause) {
    T event(UNTIMED);
    if (event.should_commit()) {
      event.set_gcId(GCId::current());
      event.set_name(pause->name());
      event.set_starttime(pause->start());
      event.set_endtime(pause->end());
      event.commit();
    }
  }

  void visit(GCPhase* pause) { ShouldNotReachHere(); }
  void visit(ConcurrentPhase* pause) { Unimplemented(); }
  void visit(PausePhase* pause) {
    assert(PhasesStack::PHASE_LEVELS == 5, "Need more event types");

    switch (pause->level()) {
      case 0: send_phase<EventGCPhasePause>(pause); break;
      case 1: send_phase<EventGCPhasePauseLevel1>(pause); break;
      case 2: send_phase<EventGCPhasePauseLevel2>(pause); break;
      case 3: send_phase<EventGCPhasePauseLevel3>(pause); break;
      default: /* Ignore sending this phase */ break;
    }
  }
};

void GCTracer::send_phase_events(TimePartitions* time_partitions) const {
  PhaseSender phase_reporter;

  TimePartitionPhasesIterator iter(time_partitions);
  while (iter.has_next()) {
    GCPhase* phase = iter.next();
    phase->accept(&phase_reporter);
  }
}
