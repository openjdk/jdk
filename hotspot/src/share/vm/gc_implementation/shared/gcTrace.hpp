/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_SHARED_GCTRACE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_SHARED_GCTRACE_HPP

#include "gc_interface/gcCause.hpp"
#include "gc_interface/gcName.hpp"
#include "gc_implementation/shared/gcId.hpp"
#include "gc_implementation/shared/gcWhen.hpp"
#include "gc_implementation/shared/copyFailedInfo.hpp"
#include "memory/allocation.hpp"
#include "memory/metaspace.hpp"
#include "memory/referenceType.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/g1/g1YCTypes.hpp"
#endif
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"


class EvacuationInfo;
class GCHeapSummary;
class MetaspaceChunkFreeListSummary;
class MetaspaceSummary;
class PSHeapSummary;
class ReferenceProcessorStats;
class TimePartitions;
class BoolObjectClosure;

class SharedGCInfo VALUE_OBJ_CLASS_SPEC {
 private:
  GCId _gc_id;
  GCName _name;
  GCCause::Cause _cause;
  Ticks     _start_timestamp;
  Ticks     _end_timestamp;
  Tickspan  _sum_of_pauses;
  Tickspan  _longest_pause;

 public:
  SharedGCInfo(GCName name) :
    _gc_id(GCId::undefined()),
    _name(name),
    _cause(GCCause::_last_gc_cause),
    _start_timestamp(),
    _end_timestamp(),
    _sum_of_pauses(),
    _longest_pause() {
  }

  void set_gc_id(GCId gc_id) { _gc_id = gc_id; }
  const GCId& gc_id() const { return _gc_id; }

  void set_start_timestamp(const Ticks& timestamp) { _start_timestamp = timestamp; }
  const Ticks start_timestamp() const { return _start_timestamp; }

  void set_end_timestamp(const Ticks& timestamp) { _end_timestamp = timestamp; }
  const Ticks end_timestamp() const { return _end_timestamp; }

  void set_name(GCName name) { _name = name; }
  GCName name() const { return _name; }

  void set_cause(GCCause::Cause cause) { _cause = cause; }
  GCCause::Cause cause() const { return _cause; }

  void set_sum_of_pauses(const Tickspan& duration) { _sum_of_pauses = duration; }
  const Tickspan sum_of_pauses() const { return _sum_of_pauses; }

  void set_longest_pause(const Tickspan& duration) { _longest_pause = duration; }
  const Tickspan longest_pause() const { return _longest_pause; }
};

class ParallelOldGCInfo VALUE_OBJ_CLASS_SPEC {
  void* _dense_prefix;
 public:
  ParallelOldGCInfo() : _dense_prefix(NULL) {}
  void report_dense_prefix(void* addr) {
    _dense_prefix = addr;
  }
  void* dense_prefix() const { return _dense_prefix; }
};

#if INCLUDE_ALL_GCS

class G1YoungGCInfo VALUE_OBJ_CLASS_SPEC {
  G1YCType _type;
 public:
  G1YoungGCInfo() : _type(G1YCTypeEndSentinel) {}
  void set_type(G1YCType type) {
    _type = type;
  }
  G1YCType type() const { return _type; }
};

#endif // INCLUDE_ALL_GCS

class GCTracer : public ResourceObj {
 protected:
  SharedGCInfo _shared_gc_info;

 public:
  void report_gc_start(GCCause::Cause cause, const Ticks& timestamp);
  void report_gc_end(const Ticks& timestamp, TimePartitions* time_partitions);
  void report_gc_heap_summary(GCWhen::Type when, const GCHeapSummary& heap_summary) const;
  void report_metaspace_summary(GCWhen::Type when, const MetaspaceSummary& metaspace_summary) const;
  void report_gc_reference_stats(const ReferenceProcessorStats& rp) const;
  void report_object_count_after_gc(BoolObjectClosure* object_filter) NOT_SERVICES_RETURN;
  bool has_reported_gc_start() const;
  const GCId& gc_id() { return _shared_gc_info.gc_id(); }

 protected:
  GCTracer(GCName name) : _shared_gc_info(name) {}
  void report_gc_start_impl(GCCause::Cause cause, const Ticks& timestamp);
  virtual void report_gc_end_impl(const Ticks& timestamp, TimePartitions* time_partitions);

 private:
  void send_garbage_collection_event() const;
  void send_gc_heap_summary_event(GCWhen::Type when, const GCHeapSummary& heap_summary) const;
  void send_meta_space_summary_event(GCWhen::Type when, const MetaspaceSummary& meta_space_summary) const;
  void send_metaspace_chunk_free_list_summary(GCWhen::Type when, Metaspace::MetadataType mdtype, const MetaspaceChunkFreeListSummary& summary) const;
  void send_reference_stats_event(ReferenceType type, size_t count) const;
  void send_phase_events(TimePartitions* time_partitions) const;
};

class YoungGCTracer : public GCTracer {
  static const uint UNSET_TENURING_THRESHOLD = (uint) -1;

  uint _tenuring_threshold;

 protected:
  YoungGCTracer(GCName name) : GCTracer(name), _tenuring_threshold(UNSET_TENURING_THRESHOLD) {}
  virtual void report_gc_end_impl(const Ticks& timestamp, TimePartitions* time_partitions);

 public:
  void report_promotion_failed(const PromotionFailedInfo& pf_info);
  void report_tenuring_threshold(const uint tenuring_threshold);

 private:
  void send_young_gc_event() const;
  void send_promotion_failed_event(const PromotionFailedInfo& pf_info) const;
};

class OldGCTracer : public GCTracer {
 protected:
  OldGCTracer(GCName name) : GCTracer(name) {}
  virtual void report_gc_end_impl(const Ticks& timestamp, TimePartitions* time_partitions);

 public:
  void report_concurrent_mode_failure();

 private:
  void send_old_gc_event() const;
  void send_concurrent_mode_failure_event();
};

class ParallelOldTracer : public OldGCTracer {
  ParallelOldGCInfo _parallel_old_gc_info;

 public:
  ParallelOldTracer() : OldGCTracer(ParallelOld) {}
  void report_dense_prefix(void* dense_prefix);

 protected:
  void report_gc_end_impl(const Ticks& timestamp, TimePartitions* time_partitions);

 private:
  void send_parallel_old_event() const;
};

class SerialOldTracer : public OldGCTracer {
 public:
  SerialOldTracer() : OldGCTracer(SerialOld) {}
};

class ParallelScavengeTracer : public YoungGCTracer {
 public:
  ParallelScavengeTracer() : YoungGCTracer(ParallelScavenge) {}
};

class DefNewTracer : public YoungGCTracer {
 public:
  DefNewTracer() : YoungGCTracer(DefNew) {}
};

class ParNewTracer : public YoungGCTracer {
 public:
  ParNewTracer() : YoungGCTracer(ParNew) {}
};

#if INCLUDE_ALL_GCS
class G1NewTracer : public YoungGCTracer {
  G1YoungGCInfo _g1_young_gc_info;

 public:
  G1NewTracer() : YoungGCTracer(G1New) {}

  void report_yc_type(G1YCType type);
  void report_gc_end_impl(const Ticks& timestamp, TimePartitions* time_partitions);
  void report_evacuation_info(EvacuationInfo* info);
  void report_evacuation_failed(EvacuationFailedInfo& ef_info);

 private:
  void send_g1_young_gc_event();
  void send_evacuation_info_event(EvacuationInfo* info);
  void send_evacuation_failed_event(const EvacuationFailedInfo& ef_info) const;
};
#endif

class CMSTracer : public OldGCTracer {
 public:
  CMSTracer() : OldGCTracer(ConcurrentMarkSweep) {}
};

class G1OldTracer : public OldGCTracer {
 public:
  G1OldTracer() : OldGCTracer(G1Old) {}
};

#endif // SHARE_VM_GC_IMPLEMENTATION_SHARED_GCTRACE_HPP
