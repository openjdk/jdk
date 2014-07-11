/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved.
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

template <class T>
class WorkerDataArray  : public CHeapObj<mtGC> {
  T*          _data;
  uint        _length;
  const char* _print_format;
  bool        _print_sum;

  NOT_PRODUCT(static const T _uninitialized;)

  // We are caching the sum and average to only have to calculate them once.
  // This is not done in an MT-safe way. It is intended to allow single
  // threaded code to call sum() and average() multiple times in any order
  // without having to worry about the cost.
  bool   _has_new_data;
  T      _sum;
  double _average;

 public:
  WorkerDataArray(uint length, const char* print_format, bool print_sum = true) :
  _length(length), _print_format(print_format), _print_sum(print_sum), _has_new_data(true) {
    assert(length > 0, "Must have some workers to store data for");
    _data = NEW_C_HEAP_ARRAY(T, _length, mtGC);
  }

  ~WorkerDataArray() {
    FREE_C_HEAP_ARRAY(T, _data, mtGC);
  }

  void set(uint worker_i, T value) {
    assert(worker_i < _length, err_msg("Worker %d is greater than max: %d", worker_i, _length));
    assert(_data[worker_i] == (T)-1, err_msg("Overwriting data for worker %d", worker_i));
    _data[worker_i] = value;
    _has_new_data = true;
  }

  T get(uint worker_i) {
    assert(worker_i < _length, err_msg("Worker %d is greater than max: %d", worker_i, _length));
    assert(_data[worker_i] != (T)-1, err_msg("No data to add to for worker %d", worker_i));
    return _data[worker_i];
  }

  void add(uint worker_i, T value) {
    assert(worker_i < _length, err_msg("Worker %d is greater than max: %d", worker_i, _length));
    assert(_data[worker_i] != (T)-1, err_msg("No data to add to for worker %d", worker_i));
    _data[worker_i] += value;
    _has_new_data = true;
  }

  double average(){
    if (_has_new_data) {
      calculate_totals();
    }
    return _average;
  }

  T sum() {
    if (_has_new_data) {
      calculate_totals();
    }
    return _sum;
  }

  void print(int level, const char* title);

  void reset() PRODUCT_RETURN;
  void verify() PRODUCT_RETURN;

 private:

  void calculate_totals(){
    _sum = (T)0;
    for (uint i = 0; i < _length; ++i) {
      _sum += _data[i];
    }
    _average = (double)_sum / (double)_length;
    _has_new_data = false;
  }
};

class G1GCPhaseTimes : public CHeapObj<mtGC> {

 private:
  uint _active_gc_threads;
  uint _max_gc_threads;

  WorkerDataArray<double> _last_gc_worker_start_times_ms;
  WorkerDataArray<double> _last_ext_root_scan_times_ms;
  WorkerDataArray<double> _last_satb_filtering_times_ms;
  WorkerDataArray<double> _last_update_rs_times_ms;
  WorkerDataArray<int>    _last_update_rs_processed_buffers;
  WorkerDataArray<double> _last_scan_rs_times_ms;
  WorkerDataArray<double> _last_strong_code_root_scan_times_ms;
  WorkerDataArray<double> _last_obj_copy_times_ms;
  WorkerDataArray<double> _last_termination_times_ms;
  WorkerDataArray<size_t> _last_termination_attempts;
  WorkerDataArray<double> _last_gc_worker_end_times_ms;
  WorkerDataArray<double> _last_gc_worker_times_ms;
  WorkerDataArray<double> _last_gc_worker_other_times_ms;

  double _cur_collection_par_time_ms;
  double _cur_collection_code_root_fixup_time_ms;
  double _cur_strong_code_root_migration_time_ms;
  double _cur_strong_code_root_purge_time_ms;

  double _cur_evac_fail_recalc_used;
  double _cur_evac_fail_restore_remsets;
  double _cur_evac_fail_remove_self_forwards;

  double                  _cur_string_dedup_fixup_time_ms;
  WorkerDataArray<double> _cur_string_dedup_queue_fixup_worker_times_ms;
  WorkerDataArray<double> _cur_string_dedup_table_fixup_worker_times_ms;

  double _cur_clear_ct_time_ms;
  double _cur_ref_proc_time_ms;
  double _cur_ref_enq_time_ms;

  double _cur_collection_start_sec;
  double _root_region_scan_wait_time_ms;

  double _recorded_young_cset_choice_time_ms;
  double _recorded_non_young_cset_choice_time_ms;

  WorkerDataArray<double> _last_redirty_logged_cards_time_ms;
  WorkerDataArray<size_t> _last_redirty_logged_cards_processed_cards;
  double _recorded_redirty_logged_cards_time_ms;

  double _recorded_young_free_cset_time_ms;
  double _recorded_non_young_free_cset_time_ms;

  double _cur_verify_before_time_ms;
  double _cur_verify_after_time_ms;

  // Helper methods for detailed logging
  void print_stats(int level, const char* str, double value);
  void print_stats(int level, const char* str, double value, uint workers);

 public:
  G1GCPhaseTimes(uint max_gc_threads);
  void note_gc_start(uint active_gc_threads);
  void note_gc_end();
  void print(double pause_time_sec);

  void record_gc_worker_start_time(uint worker_i, double ms) {
    _last_gc_worker_start_times_ms.set(worker_i, ms);
  }

  void record_ext_root_scan_time(uint worker_i, double ms) {
    _last_ext_root_scan_times_ms.set(worker_i, ms);
  }

  void record_satb_filtering_time(uint worker_i, double ms) {
    _last_satb_filtering_times_ms.set(worker_i, ms);
  }

  void record_update_rs_time(uint worker_i, double ms) {
    _last_update_rs_times_ms.set(worker_i, ms);
  }

  void record_update_rs_processed_buffers(uint worker_i, int processed_buffers) {
    _last_update_rs_processed_buffers.set(worker_i, processed_buffers);
  }

  void record_scan_rs_time(uint worker_i, double ms) {
    _last_scan_rs_times_ms.set(worker_i, ms);
  }

  void record_strong_code_root_scan_time(uint worker_i, double ms) {
    _last_strong_code_root_scan_times_ms.set(worker_i, ms);
  }

  void record_obj_copy_time(uint worker_i, double ms) {
    _last_obj_copy_times_ms.set(worker_i, ms);
  }

  void add_obj_copy_time(uint worker_i, double ms) {
    _last_obj_copy_times_ms.add(worker_i, ms);
  }

  void record_termination(uint worker_i, double ms, size_t attempts) {
    _last_termination_times_ms.set(worker_i, ms);
    _last_termination_attempts.set(worker_i, attempts);
  }

  void record_gc_worker_end_time(uint worker_i, double ms) {
    _last_gc_worker_end_times_ms.set(worker_i, ms);
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

  void record_strong_code_root_migration_time(double ms) {
    _cur_strong_code_root_migration_time_ms = ms;
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

  void note_string_dedup_fixup_start();
  void note_string_dedup_fixup_end();

  void record_string_dedup_fixup_time(double ms) {
    _cur_string_dedup_fixup_time_ms = ms;
  }

  void record_string_dedup_queue_fixup_worker_time(uint worker_id, double ms) {
    _cur_string_dedup_queue_fixup_worker_times_ms.set(worker_id, ms);
  }

  void record_string_dedup_table_fixup_worker_time(uint worker_id, double ms) {
    _cur_string_dedup_table_fixup_worker_times_ms.set(worker_id, ms);
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

  void record_young_cset_choice_time_ms(double time_ms) {
    _recorded_young_cset_choice_time_ms = time_ms;
  }

  void record_non_young_cset_choice_time_ms(double time_ms) {
    _recorded_non_young_cset_choice_time_ms = time_ms;
  }

  void record_redirty_logged_cards_time_ms(uint worker_i, double time_ms) {
    _last_redirty_logged_cards_time_ms.set(worker_i, time_ms);
  }

  void record_redirty_logged_cards_processed_cards(uint worker_i, size_t processed_buffers) {
    _last_redirty_logged_cards_processed_cards.set(worker_i, processed_buffers);
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

  double average_last_update_rs_time() {
    return _last_update_rs_times_ms.average();
  }

  int sum_last_update_rs_processed_buffers() {
    return _last_update_rs_processed_buffers.sum();
  }

  double average_last_scan_rs_time(){
    return _last_scan_rs_times_ms.average();
  }

  double average_last_strong_code_root_scan_time(){
    return _last_strong_code_root_scan_times_ms.average();
  }

  double average_last_obj_copy_time() {
    return _last_obj_copy_times_ms.average();
  }

  double average_last_termination_time() {
    return _last_termination_times_ms.average();
  }

  double average_last_ext_root_scan_time() {
    return _last_ext_root_scan_times_ms.average();
  }

  double average_last_satb_filtering_times_ms() {
    return _last_satb_filtering_times_ms.average();
  }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1GCPHASETIMESLOG_HPP
