/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1TRACE_HPP
#define SHARE_GC_G1_G1TRACE_HPP

#include "gc/g1/g1YCTypes.hpp"
#include "gc/shared/gcTrace.hpp"

class G1EvacuationInfo;
class G1HeapSummary;
class G1EvacSummary;

class G1YoungGCInfo {
  G1YCType _type;
public:
  G1YoungGCInfo() : _type(G1YCTypeEndSentinel) {}
  void set_type(G1YCType type) {
    _type = type;
  }
  G1YCType type() const { return _type; }
};

class G1NewTracer : public YoungGCTracer {
  G1YoungGCInfo _g1_young_gc_info;

public:
  G1NewTracer() : YoungGCTracer(G1New) {}

  void initialize();
  void report_yc_type(G1YCType type);
  void report_gc_end_impl(const Ticks& timestamp, TimePartitions* time_partitions);
  void report_evacuation_info(G1EvacuationInfo* info);
  void report_evacuation_failed(EvacuationFailedInfo& ef_info);

  void report_evacuation_statistics(const G1EvacSummary& young_summary, const G1EvacSummary& old_summary) const;

  void report_basic_ihop_statistics(size_t threshold,
                                    size_t target_occupancy,
                                    size_t current_occupancy,
                                    size_t last_allocation_size,
                                    double last_allocation_duration,
                                    double last_marking_length);
  void report_adaptive_ihop_statistics(size_t threshold,
                                       size_t internal_target_occupancy,
                                       size_t current_occupancy,
                                       size_t additional_buffer_size,
                                       double predicted_allocation_rate,
                                       double predicted_marking_length,
                                       bool prediction_active);
private:
  void send_g1_young_gc_event();
  void send_evacuation_info_event(G1EvacuationInfo* info);
  void send_evacuation_failed_event(const EvacuationFailedInfo& ef_info) const;

  void send_young_evacuation_statistics(const G1EvacSummary& summary) const;
  void send_old_evacuation_statistics(const G1EvacSummary& summary) const;

  void send_basic_ihop_statistics(size_t threshold,
                                  size_t target_occupancy,
                                  size_t current_occupancy,
                                  size_t last_allocation_size,
                                  double last_allocation_duration,
                                  double last_marking_length);
  void send_adaptive_ihop_statistics(size_t threshold,
                                     size_t internal_target_occupancy,
                                     size_t current_occupancy,
                                     size_t additional_buffer_size,
                                     double predicted_allocation_rate,
                                     double predicted_marking_length,
                                     bool prediction_active);
};

class G1OldTracer : public OldGCTracer {
 protected:
  void report_gc_start_impl(GCCause::Cause cause, const Ticks& timestamp);
 public:
  G1OldTracer() : OldGCTracer(G1Old) {}
  void set_gc_cause(GCCause::Cause cause);
};

class G1FullGCTracer : public OldGCTracer {
public:
  G1FullGCTracer() : OldGCTracer(G1Full) {}
};

class G1MMUTracer : public AllStatic {
  static void send_g1_mmu_event(double time_slice_ms, double gc_time_ms, double max_time_ms);

public:
  static void report_mmu(double time_slice_sec, double gc_time_sec, double max_time_sec);
};

#endif
