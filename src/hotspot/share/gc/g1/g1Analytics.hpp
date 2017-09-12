/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1MEASUREMENTS_HPP
#define SHARE_VM_GC_G1_G1MEASUREMENTS_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

class TruncatedSeq;
class G1Predictions;

class G1Analytics: public CHeapObj<mtGC> {
  const static int TruncatedSeqLength = 10;
  const static int NumPrevPausesForHeuristics = 10;
  const G1Predictions* _predictor;

  // These exclude marking times.
  TruncatedSeq* _recent_gc_times_ms;

  TruncatedSeq* _concurrent_mark_remark_times_ms;
  TruncatedSeq* _concurrent_mark_cleanup_times_ms;

  TruncatedSeq* _alloc_rate_ms_seq;
  double        _prev_collection_pause_end_ms;

  TruncatedSeq* _rs_length_diff_seq;
  TruncatedSeq* _cost_per_card_ms_seq;
  TruncatedSeq* _cost_scan_hcc_seq;
  TruncatedSeq* _young_cards_per_entry_ratio_seq;
  TruncatedSeq* _mixed_cards_per_entry_ratio_seq;
  TruncatedSeq* _cost_per_entry_ms_seq;
  TruncatedSeq* _mixed_cost_per_entry_ms_seq;
  TruncatedSeq* _cost_per_byte_ms_seq;
  TruncatedSeq* _constant_other_time_ms_seq;
  TruncatedSeq* _young_other_cost_per_region_ms_seq;
  TruncatedSeq* _non_young_other_cost_per_region_ms_seq;

  TruncatedSeq* _pending_cards_seq;
  TruncatedSeq* _rs_lengths_seq;

  TruncatedSeq* _cost_per_byte_ms_during_cm_seq;

  // Statistics kept per GC stoppage, pause or full.
  TruncatedSeq* _recent_prev_end_times_for_all_gcs_sec;

  // The ratio of gc time to elapsed time, computed over recent pauses,
  // and the ratio for just the last pause.
  double _recent_avg_pause_time_ratio;
  double _last_pause_time_ratio;

  double get_new_prediction(TruncatedSeq const* seq) const;
  size_t get_new_size_prediction(TruncatedSeq const* seq) const;

public:
  G1Analytics(const G1Predictions* predictor);

  double prev_collection_pause_end_ms() const {
    return _prev_collection_pause_end_ms;
  }

  double recent_avg_pause_time_ratio() const {
    return _recent_avg_pause_time_ratio;
  }

  double last_pause_time_ratio() const {
    return _last_pause_time_ratio;
  }

  uint number_of_recorded_pause_times() const {
    return NumPrevPausesForHeuristics;
  }

  void append_prev_collection_pause_end_ms(double ms) {
    _prev_collection_pause_end_ms += ms;
  }

  void report_concurrent_mark_remark_times_ms(double ms);
  void report_concurrent_mark_cleanup_times_ms(double ms);
  void report_alloc_rate_ms(double alloc_rate);
  void report_cost_per_card_ms(double cost_per_card_ms);
  void report_cost_scan_hcc(double cost_scan_hcc);
  void report_cost_per_entry_ms(double cost_per_entry_ms, bool last_gc_was_young);
  void report_cards_per_entry_ratio(double cards_per_entry_ratio, bool last_gc_was_young);
  void report_rs_length_diff(double rs_length_diff);
  void report_cost_per_byte_ms(double cost_per_byte_ms, bool in_marking_window);
  void report_young_other_cost_per_region_ms(double other_cost_per_region_ms);
  void report_non_young_other_cost_per_region_ms(double other_cost_per_region_ms);
  void report_constant_other_time_ms(double constant_other_time_ms);
  void report_pending_cards(double pending_cards);
  void report_rs_lengths(double rs_lengths);

  size_t predict_rs_length_diff() const;

  double predict_alloc_rate_ms() const;
  int num_alloc_rate_ms() const;

  double predict_cost_per_card_ms() const;

  double predict_scan_hcc_ms() const;

  double predict_rs_update_time_ms(size_t pending_cards) const;

  double predict_young_cards_per_entry_ratio() const;

  double predict_mixed_cards_per_entry_ratio() const;

  size_t predict_card_num(size_t rs_length, bool gcs_are_young) const;

  double predict_rs_scan_time_ms(size_t card_num, bool gcs_are_young) const;

  double predict_mixed_rs_scan_time_ms(size_t card_num) const;

  double predict_object_copy_time_ms_during_cm(size_t bytes_to_copy) const;

  double predict_object_copy_time_ms(size_t bytes_to_copy, bool during_concurrent_mark) const;

  double predict_constant_other_time_ms() const;

  double predict_young_other_time_ms(size_t young_num) const;

  double predict_non_young_other_time_ms(size_t non_young_num) const;

  double predict_remark_time_ms() const;

  double predict_cleanup_time_ms() const;

  size_t predict_rs_lengths() const;
  size_t predict_pending_cards() const;

  double predict_cost_per_byte_ms() const;

  // Add a new GC of the given duration and end time to the record.
  void update_recent_gc_times(double end_time_sec, double elapsed_ms);
  void compute_pause_time_ratio(double interval_ms, double pause_time_ms);

  double last_known_gc_end_time_sec() const;
};

#endif // SHARE_VM_GC_G1_G1MEASUREMENTS_HPP
