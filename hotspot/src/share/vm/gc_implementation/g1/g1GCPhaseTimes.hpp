/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1GCPHASETIMESLOG_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1GCPHASETIMESLOG_HPP

#include "memory/allocation.hpp"
#include "gc_interface/gcCause.hpp"

class G1GCPhaseTimes : public CHeapObj<mtGC> {
  friend class G1CollectorPolicy;
  friend class TraceGen0TimeData;

 private:
  uint _active_gc_threads;
  uint _max_gc_threads;

  GCCause::Cause _gc_cause;
  bool           _is_young_gc;
  bool           _is_initial_mark_gc;

  double _pause_start_time_sec;

  double* _par_last_gc_worker_start_times_ms;
  double* _par_last_ext_root_scan_times_ms;
  double* _par_last_satb_filtering_times_ms;
  double* _par_last_update_rs_times_ms;
  double* _par_last_update_rs_processed_buffers;
  double* _par_last_scan_rs_times_ms;
  double* _par_last_obj_copy_times_ms;
  double* _par_last_termination_times_ms;
  double* _par_last_termination_attempts;
  double* _par_last_gc_worker_end_times_ms;
  double* _par_last_gc_worker_times_ms;
  double* _par_last_gc_worker_other_times_ms;

  double _cur_collection_par_time_ms;

  double _cur_collection_code_root_fixup_time_ms;

  double _cur_clear_ct_time_ms;
  double _cur_ref_proc_time_ms;
  double _cur_ref_enq_time_ms;

  // Helper methods for detailed logging
  void print_par_stats(int level, const char* str, double* data, bool showDecimals = true);
  void print_stats(int level, const char* str, double value);
  void print_stats(int level, const char* str, double value, int workers);
  void print_stats(int level, const char* str, int value);
  double avg_value(double* data);
  double max_value(double* data);
  double sum_of_values(double* data);
  double max_sum(double* data1, double* data2);
  double accounted_time_ms();

  // Card Table Count Cache stats
  double _min_clear_cc_time_ms;         // min
  double _max_clear_cc_time_ms;         // max
  double _cur_clear_cc_time_ms;         // clearing time during current pause
  double _cum_clear_cc_time_ms;         // cummulative clearing time
  jlong  _num_cc_clears;                // number of times the card count cache has been cleared

  // The following insance variables are directly accessed by G1CollectorPolicy
  // and TraceGen0TimeData. This is why those classes are declared friends.
  // An alternative is to add getters and setters for all of these fields.
  // It might also be possible to restructure the code to reduce these
  // dependencies.
  double _ext_root_scan_time;
  double _satb_filtering_time;
  double _update_rs_time;
  double _update_rs_processed_buffers;
  double _scan_rs_time;
  double _obj_copy_time;
  double _termination_time;

  double _cur_collection_start_sec;
  double _root_region_scan_wait_time_ms;

  double _recorded_young_cset_choice_time_ms;
  double _recorded_non_young_cset_choice_time_ms;

  double _recorded_young_free_cset_time_ms;
  double _recorded_non_young_free_cset_time_ms;

  void print(double pause_time_ms);

 public:
  G1GCPhaseTimes(uint max_gc_threads);
  void note_gc_start(double pause_start_time_sec, uint active_gc_threads,
    bool is_young_gc, bool is_initial_mark_gc, GCCause::Cause gc_cause);
  void note_gc_end(double pause_end_time_sec);
  void collapse_par_times();

  void record_gc_worker_start_time(uint worker_i, double ms) {
    assert(worker_i >= 0, "worker index must be > 0");
    assert(worker_i < _active_gc_threads, "worker index out of bounds");
    _par_last_gc_worker_start_times_ms[worker_i] = ms;
  }

  void record_ext_root_scan_time(uint worker_i, double ms) {
    assert(worker_i >= 0, "worker index must be > 0");
    assert(worker_i < _active_gc_threads, "worker index out of bounds");
    _par_last_ext_root_scan_times_ms[worker_i] = ms;
  }

  void record_satb_filtering_time(uint worker_i, double ms) {
    assert(worker_i >= 0, "worker index must be > 0");
    assert(worker_i < _active_gc_threads, "worker index out of bounds");
    _par_last_satb_filtering_times_ms[worker_i] = ms;
  }

  void record_update_rs_time(uint worker_i, double ms) {
    assert(worker_i >= 0, "worker index must be > 0");
    assert(worker_i < _active_gc_threads, "worker index out of bounds");
    _par_last_update_rs_times_ms[worker_i] = ms;
  }

  void record_update_rs_processed_buffers (uint worker_i,
                                           double processed_buffers) {
    assert(worker_i >= 0, "worker index must be > 0");
    assert(worker_i < _active_gc_threads, "worker index out of bounds");
    _par_last_update_rs_processed_buffers[worker_i] = processed_buffers;
  }

  void record_scan_rs_time(uint worker_i, double ms) {
    assert(worker_i >= 0, "worker index must be > 0");
    assert(worker_i < _active_gc_threads, "worker index out of bounds");
    _par_last_scan_rs_times_ms[worker_i] = ms;
  }

  void reset_obj_copy_time(uint worker_i) {
    assert(worker_i >= 0, "worker index must be > 0");
    assert(worker_i < _active_gc_threads, "worker index out of bounds");
    _par_last_obj_copy_times_ms[worker_i] = 0.0;
  }

  void reset_obj_copy_time() {
    reset_obj_copy_time(0);
  }

  void record_obj_copy_time(uint worker_i, double ms) {
    assert(worker_i >= 0, "worker index must be > 0");
    assert(worker_i < _active_gc_threads, "worker index out of bounds");
    _par_last_obj_copy_times_ms[worker_i] += ms;
  }

  void record_termination(uint worker_i, double ms, size_t attempts) {
    assert(worker_i >= 0, "worker index must be > 0");
    assert(worker_i < _active_gc_threads, "worker index out of bounds");
    _par_last_termination_times_ms[worker_i] = ms;
    _par_last_termination_attempts[worker_i] = (double) attempts;
  }

  void record_gc_worker_end_time(uint worker_i, double ms) {
    assert(worker_i >= 0, "worker index must be > 0");
    assert(worker_i < _active_gc_threads, "worker index out of bounds");
    _par_last_gc_worker_end_times_ms[worker_i] = ms;
  }

  void record_clear_ct_time(double ms) {
    _cur_clear_ct_time_ms = ms;
  }

  void record_par_time(double ms) {
    _cur_collection_par_time_ms = ms;
  }

  void record_code_root_fixup_time(double ms) {
    _cur_collection_code_root_fixup_time_ms = ms;
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

  void record_cc_clear_time_ms(double ms);

  void record_young_free_cset_time_ms(double time_ms) {
    _recorded_young_free_cset_time_ms = time_ms;
  }

  void record_non_young_free_cset_time_ms(double time_ms) {
    _recorded_non_young_free_cset_time_ms = time_ms;
  }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1GCPHASETIMESLOG_HPP
