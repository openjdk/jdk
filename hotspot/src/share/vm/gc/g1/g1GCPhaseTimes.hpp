/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1GCPHASETIMES_HPP
#define SHARE_VM_GC_G1_G1GCPHASETIMES_HPP

#include "memory/allocation.hpp"

class LineBuffer;

template <class T> class WorkerDataArray;

class G1GCPhaseTimes : public CHeapObj<mtGC> {
  friend class G1GCParPhasePrinter;

  uint _active_gc_threads;
  uint _max_gc_threads;

 public:
  enum GCParPhases {
    GCWorkerStart,
    ExtRootScan,
    ThreadRoots,
    StringTableRoots,
    UniverseRoots,
    JNIRoots,
    ObjectSynchronizerRoots,
    FlatProfilerRoots,
    ManagementRoots,
    SystemDictionaryRoots,
    CLDGRoots,
    JVMTIRoots,
    CMRefRoots,
    WaitForStrongCLD,
    WeakCLDRoots,
    SATBFiltering,
    UpdateRS,
    ScanHCC,
    ScanRS,
    CodeRoots,
    ObjCopy,
    Termination,
    Other,
    GCWorkerTotal,
    GCWorkerEnd,
    StringDedupQueueFixup,
    StringDedupTableFixup,
    RedirtyCards,
    GCParPhasesSentinel
  };

 private:
  // Markers for grouping the phases in the GCPhases enum above
  static const int GCMainParPhasesLast = GCWorkerEnd;
  static const int StringDedupPhasesFirst = StringDedupQueueFixup;
  static const int StringDedupPhasesLast = StringDedupTableFixup;

  WorkerDataArray<double>* _gc_par_phases[GCParPhasesSentinel];
  WorkerDataArray<size_t>* _update_rs_processed_buffers;
  WorkerDataArray<size_t>* _termination_attempts;
  WorkerDataArray<size_t>* _redirtied_cards;

  double _cur_collection_par_time_ms;
  double _cur_collection_code_root_fixup_time_ms;
  double _cur_strong_code_root_purge_time_ms;

  double _cur_evac_fail_recalc_used;
  double _cur_evac_fail_restore_remsets;
  double _cur_evac_fail_remove_self_forwards;

  double _cur_string_dedup_fixup_time_ms;

  double _cur_clear_ct_time_ms;
  double _cur_expand_heap_time_ms;
  double _cur_ref_proc_time_ms;
  double _cur_ref_enq_time_ms;

  double _cur_collection_start_sec;
  double _root_region_scan_wait_time_ms;

  double _recorded_young_cset_choice_time_ms;
  double _recorded_non_young_cset_choice_time_ms;

  double _recorded_redirty_logged_cards_time_ms;

  double _recorded_young_free_cset_time_ms;
  double _recorded_non_young_free_cset_time_ms;

  double _cur_fast_reclaim_humongous_time_ms;
  double _cur_fast_reclaim_humongous_register_time_ms;
  size_t _cur_fast_reclaim_humongous_total;
  size_t _cur_fast_reclaim_humongous_candidates;
  size_t _cur_fast_reclaim_humongous_reclaimed;

  double _cur_verify_before_time_ms;
  double _cur_verify_after_time_ms;

  // Helper methods for detailed logging
  void print_stats(int level, const char* str, double value);
  void print_stats(int level, const char* str, size_t value);
  void print_stats(int level, const char* str, double value, uint workers);

  void note_gc_end();

 public:
  G1GCPhaseTimes(uint max_gc_threads);
  void note_gc_start(uint active_gc_threads);
  void print(double pause_time_sec);

  // record the time a phase took in seconds
  void record_time_secs(GCParPhases phase, uint worker_i, double secs);

  // add a number of seconds to a phase
  void add_time_secs(GCParPhases phase, uint worker_i, double secs);

  void record_thread_work_item(GCParPhases phase, uint worker_i, size_t count);

  // return the average time for a phase in milliseconds
  double average_time_ms(GCParPhases phase);

  size_t sum_thread_work_items(GCParPhases phase);

 private:
  double get_time_ms(GCParPhases phase, uint worker_i);
  double sum_time_ms(GCParPhases phase);
  double min_time_ms(GCParPhases phase);
  double max_time_ms(GCParPhases phase);
  size_t get_thread_work_item(GCParPhases phase, uint worker_i);
  double average_thread_work_items(GCParPhases phase);
  size_t min_thread_work_items(GCParPhases phase);
  size_t max_thread_work_items(GCParPhases phase);

 public:

  void record_clear_ct_time(double ms) {
    _cur_clear_ct_time_ms = ms;
  }

  void record_expand_heap_time(double ms) {
    _cur_expand_heap_time_ms = ms;
  }

  void record_par_time(double ms) {
    _cur_collection_par_time_ms = ms;
  }

  void record_code_root_fixup_time(double ms) {
    _cur_collection_code_root_fixup_time_ms = ms;
  }

  void record_strong_code_root_purge_time(double ms) {
    _cur_strong_code_root_purge_time_ms = ms;
  }

  void record_evac_fail_recalc_used_time(double ms) {
    _cur_evac_fail_recalc_used = ms;
  }

  void record_evac_fail_restore_remsets(double ms) {
    _cur_evac_fail_restore_remsets = ms;
  }

  void record_evac_fail_remove_self_forwards(double ms) {
    _cur_evac_fail_remove_self_forwards = ms;
  }

  void record_string_dedup_fixup_time(double ms) {
    _cur_string_dedup_fixup_time_ms = ms;
  }

  void record_ref_proc_time(double ms) {
    _cur_ref_proc_time_ms = ms;
  }

  void record_ref_enq_time(double ms) {
    _cur_ref_enq_time_ms = ms;
  }

  void record_root_region_scan_wait_time(double time_ms) {
    _root_region_scan_wait_time_ms = time_ms;
  }

  void record_young_free_cset_time_ms(double time_ms) {
    _recorded_young_free_cset_time_ms = time_ms;
  }

  void record_non_young_free_cset_time_ms(double time_ms) {
    _recorded_non_young_free_cset_time_ms = time_ms;
  }

  void record_fast_reclaim_humongous_stats(double time_ms, size_t total, size_t candidates) {
    _cur_fast_reclaim_humongous_register_time_ms = time_ms;
    _cur_fast_reclaim_humongous_total = total;
    _cur_fast_reclaim_humongous_candidates = candidates;
  }

  void record_fast_reclaim_humongous_time_ms(double value, size_t reclaimed) {
    _cur_fast_reclaim_humongous_time_ms = value;
    _cur_fast_reclaim_humongous_reclaimed = reclaimed;
  }

  void record_young_cset_choice_time_ms(double time_ms) {
    _recorded_young_cset_choice_time_ms = time_ms;
  }

  void record_non_young_cset_choice_time_ms(double time_ms) {
    _recorded_non_young_cset_choice_time_ms = time_ms;
  }

  void record_redirty_logged_cards_time_ms(double time_ms) {
    _recorded_redirty_logged_cards_time_ms = time_ms;
  }

  void record_cur_collection_start_sec(double time_ms) {
    _cur_collection_start_sec = time_ms;
  }

  void record_verify_before_time_ms(double time_ms) {
    _cur_verify_before_time_ms = time_ms;
  }

  void record_verify_after_time_ms(double time_ms) {
    _cur_verify_after_time_ms = time_ms;
  }

  double accounted_time_ms();

  double cur_collection_start_sec() {
    return _cur_collection_start_sec;
  }

  double cur_collection_par_time_ms() {
    return _cur_collection_par_time_ms;
  }

  double cur_clear_ct_time_ms() {
    return _cur_clear_ct_time_ms;
  }

  double cur_expand_heap_time_ms() {
    return _cur_expand_heap_time_ms;
  }

  double root_region_scan_wait_time_ms() {
    return _root_region_scan_wait_time_ms;
  }

  double young_cset_choice_time_ms() {
    return _recorded_young_cset_choice_time_ms;
  }

  double young_free_cset_time_ms() {
    return _recorded_young_free_cset_time_ms;
  }

  double non_young_cset_choice_time_ms() {
    return _recorded_non_young_cset_choice_time_ms;
  }

  double non_young_free_cset_time_ms() {
    return _recorded_non_young_free_cset_time_ms;
  }

  double fast_reclaim_humongous_time_ms() {
    return _cur_fast_reclaim_humongous_time_ms;
  }
};

class G1GCParPhaseTimesTracker : public StackObj {
  double _start_time;
  G1GCPhaseTimes::GCParPhases _phase;
  G1GCPhaseTimes* _phase_times;
  uint _worker_id;
public:
  G1GCParPhaseTimesTracker(G1GCPhaseTimes* phase_times, G1GCPhaseTimes::GCParPhases phase, uint worker_id);
  ~G1GCParPhaseTimesTracker();
};

#endif // SHARE_VM_GC_G1_G1GCPHASETIMES_HPP
