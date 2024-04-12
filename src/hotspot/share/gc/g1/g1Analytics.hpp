/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1ANALYTICS_HPP
#define SHARE_GC_G1_G1ANALYTICS_HPP

#include "gc/g1/g1AnalyticsSequences.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

class TruncatedSeq;
class G1Predictions;

class G1Analytics: public CHeapObj<mtGC> {
  const static int TruncatedSeqLength = 10;
  const static int NumPrevPausesForHeuristics = 10;
  const G1Predictions* _predictor;

  // These exclude marking times.
  TruncatedSeq _recent_gc_times_ms;

  TruncatedSeq _concurrent_mark_remark_times_ms;
  TruncatedSeq _concurrent_mark_cleanup_times_ms;

  TruncatedSeq _alloc_rate_ms_seq;
  double        _prev_collection_pause_end_ms;

  TruncatedSeq _concurrent_refine_rate_ms_seq;
  TruncatedSeq _dirtied_cards_rate_ms_seq;
  TruncatedSeq _dirtied_cards_in_thread_buffers_seq;
  // The ratio between the number of scanned cards and actually merged cards, for
  // young-only and mixed gcs.
  G1PhaseDependentSeq _card_scan_to_merge_ratio_seq;

  // The cost to scan a card during young-only and mixed gcs in ms.
  G1PhaseDependentSeq _cost_per_card_scan_ms_seq;
  // The cost to merge a card during young-only and mixed gcs in ms.
  G1PhaseDependentSeq _cost_per_card_merge_ms_seq;
  // The cost to scan entries in the code root remembered set in ms.
  G1PhaseDependentSeq _cost_per_code_root_ms_seq;
  // The cost to copy a byte in ms.
  G1PhaseDependentSeq _cost_per_byte_copied_ms_seq;

  G1PhaseDependentSeq _pending_cards_seq;
  G1PhaseDependentSeq _card_rs_length_seq;
  G1PhaseDependentSeq _code_root_rs_length_seq;

  TruncatedSeq _constant_other_time_ms_seq;
  TruncatedSeq _young_other_cost_per_region_ms_seq;
  TruncatedSeq _non_young_other_cost_per_region_ms_seq;

  TruncatedSeq _cost_per_byte_ms_during_cm_seq;

  // Statistics kept per GC stoppage, pause or full.
  TruncatedSeq _recent_prev_end_times_for_all_gcs_sec;

  // Cached values for long and short term pause time ratios. See
  // compute_pause_time_ratios() for how they are computed.
  double _long_term_pause_time_ratio;
  double _short_term_pause_time_ratio;

  double predict_in_unit_interval(TruncatedSeq const* seq) const;
  size_t predict_size(TruncatedSeq const* seq) const;
  double predict_zero_bounded(TruncatedSeq const* seq) const;

  double predict_in_unit_interval(G1PhaseDependentSeq const* seq, bool for_young_only_phase) const;
  size_t predict_size(G1PhaseDependentSeq const* seq, bool for_young_only_phase) const;
  double predict_zero_bounded(G1PhaseDependentSeq const* seq, bool for_young_only_phase) const;

  double oldest_known_gc_end_time_sec() const;
  double most_recent_gc_end_time_sec() const;

public:
  G1Analytics(const G1Predictions* predictor);

  // Returns whether the sequence have enough samples to get a "good" prediction.
  // The constant used is random but "small".
  static bool enough_samples_available(TruncatedSeq const* seq);

  double prev_collection_pause_end_ms() const {
    return _prev_collection_pause_end_ms;
  }

  double long_term_pause_time_ratio() const {
    return _long_term_pause_time_ratio;
  }

  double short_term_pause_time_ratio() const {
    return _short_term_pause_time_ratio;
  }

  uint number_of_recorded_pause_times() const {
    return NumPrevPausesForHeuristics;
  }

  void append_prev_collection_pause_end_ms(double ms) {
    _prev_collection_pause_end_ms += ms;
  }

  void set_prev_collection_pause_end_ms(double ms) {
    _prev_collection_pause_end_ms = ms;
  }

  void report_concurrent_mark_remark_times_ms(double ms);
  void report_concurrent_mark_cleanup_times_ms(double ms);
  void report_alloc_rate_ms(double alloc_rate);
  void report_concurrent_refine_rate_ms(double cards_per_ms);
  void report_dirtied_cards_rate_ms(double cards_per_ms);
  void report_dirtied_cards_in_thread_buffers(size_t num_cards);
  void report_cost_per_card_scan_ms(double cost_per_remset_card_ms, bool for_young_only_phase);
  void report_cost_per_card_merge_ms(double cost_per_card_ms, bool for_young_only_phase);
  void report_cost_per_code_root_scan_ms(double cost_per_code_root_ms, bool for_young_only_phase);
  void report_card_scan_to_merge_ratio(double cards_per_entry_ratio, bool for_young_only_phase);
  void report_cost_per_byte_ms(double cost_per_byte_ms, bool for_young_only_phase);
  void report_young_other_cost_per_region_ms(double other_cost_per_region_ms);
  void report_non_young_other_cost_per_region_ms(double other_cost_per_region_ms);
  void report_constant_other_time_ms(double constant_other_time_ms);
  void report_pending_cards(double pending_cards, bool for_young_only_phase);
  void report_card_rs_length(double card_rs_length, bool for_young_only_phase);
  void report_code_root_rs_length(double code_root_rs_length, bool for_young_only_phase);

  double predict_alloc_rate_ms() const;
  int num_alloc_rate_ms() const;

  double predict_concurrent_refine_rate_ms() const;
  double predict_dirtied_cards_rate_ms() const;
  size_t predict_dirtied_cards_in_thread_buffers() const;

  // Predict how many of the given remembered set of length card_rs_length will add to
  // the number of total cards scanned.
  size_t predict_scan_card_num(size_t card_rs_length, bool for_young_only_phase) const;

  double predict_card_merge_time_ms(size_t card_num, bool for_young_only_phase) const;
  double predict_card_scan_time_ms(size_t card_num, bool for_young_only_phase) const;

  double predict_code_root_scan_time_ms(size_t code_root_num, bool for_young_only_phase) const;

  double predict_object_copy_time_ms(size_t bytes_to_copy, bool for_young_only_phase) const;

  double predict_constant_other_time_ms() const;

  double predict_young_other_time_ms(size_t young_num) const;

  double predict_non_young_other_time_ms(size_t non_young_num) const;

  double predict_remark_time_ms() const;

  double predict_cleanup_time_ms() const;

  size_t predict_card_rs_length(bool for_young_only_phase) const;
  size_t predict_code_root_rs_length(bool for_young_only_phase) const;
  size_t predict_pending_cards(bool for_young_only_phase) const;

  // Add a new GC of the given duration and end time to the record.
  void update_recent_gc_times(double end_time_sec, double elapsed_ms);
  void compute_pause_time_ratios(double end_time_sec, double pause_time_ms);
};

#endif // SHARE_GC_G1_G1ANALYTICS_HPP
