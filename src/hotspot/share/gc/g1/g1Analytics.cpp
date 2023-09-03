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

#include "precompiled.hpp"
#include "gc/g1/g1Analytics.hpp"
#include "gc/g1/g1AnalyticsSequences.inline.hpp"
#include "gc/g1/g1Predictions.hpp"
#include "gc/shared/gc_globals.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/numberSeq.hpp"

// Different defaults for different number of GC threads
// They were chosen by running GCOld and SPECjbb on debris with different
//   numbers of GC threads and choosing them based on the results

static double cost_per_logged_card_ms_defaults[] = {
  0.01, 0.005, 0.005, 0.003, 0.003, 0.002, 0.002, 0.0015
};

// all the same
static double young_card_scan_to_merge_ratio_defaults[] = {
  1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0
};

static double young_only_cost_per_card_scan_ms_defaults[] = {
  0.015, 0.01, 0.01, 0.008, 0.008, 0.0055, 0.0055, 0.005
};

static double cost_per_byte_ms_defaults[] = {
  0.00006, 0.00003, 0.00003, 0.000015, 0.000015, 0.00001, 0.00001, 0.000009
};

// these should be pretty consistent
static double constant_other_time_ms_defaults[] = {
  5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0
};

static double young_other_cost_per_region_ms_defaults[] = {
  0.3, 0.2, 0.2, 0.15, 0.15, 0.12, 0.12, 0.1
};

static double non_young_other_cost_per_region_ms_defaults[] = {
  1.0, 0.7, 0.7, 0.5, 0.5, 0.42, 0.42, 0.30
};

G1Analytics::G1Analytics(const G1Predictions* predictor) :
    _predictor(predictor),
    _recent_gc_times_ms(NumPrevPausesForHeuristics),
    _concurrent_mark_remark_times_ms(NumPrevPausesForHeuristics),
    _concurrent_mark_cleanup_times_ms(NumPrevPausesForHeuristics),
    _alloc_rate_ms_seq(TruncatedSeqLength),
    _prev_collection_pause_end_ms(0.0),
    _concurrent_refine_rate_ms_seq(TruncatedSeqLength),
    _dirtied_cards_rate_ms_seq(TruncatedSeqLength),
    _dirtied_cards_in_thread_buffers_seq(TruncatedSeqLength),
    _card_scan_to_merge_ratio_seq(TruncatedSeqLength),
    _cost_per_card_scan_ms_seq(TruncatedSeqLength),
    _cost_per_card_merge_ms_seq(TruncatedSeqLength),
    _cost_per_code_root_ms_seq(TruncatedSeqLength),
    _cost_per_byte_copied_ms_seq(TruncatedSeqLength),
    _pending_cards_seq(TruncatedSeqLength),
    _rs_length_seq(TruncatedSeqLength),
    _code_root_rs_length_seq(TruncatedSeqLength),
    _constant_other_time_ms_seq(TruncatedSeqLength),
    _young_other_cost_per_region_ms_seq(TruncatedSeqLength),
    _non_young_other_cost_per_region_ms_seq(TruncatedSeqLength),
    _recent_prev_end_times_for_all_gcs_sec(NumPrevPausesForHeuristics),
    _long_term_pause_time_ratio(0.0),
    _short_term_pause_time_ratio(0.0) {

  // Seed sequences with initial values.
  _recent_prev_end_times_for_all_gcs_sec.add(os::elapsedTime());
  _prev_collection_pause_end_ms = os::elapsedTime() * 1000.0;

  uint index = MIN2(ParallelGCThreads - 1, 7u);

  // Start with inverse of maximum STW cost.
  _concurrent_refine_rate_ms_seq.add(1/cost_per_logged_card_ms_defaults[0]);
  // Some applications have very low rates for logging cards.
  _dirtied_cards_rate_ms_seq.add(0.0);

  _card_scan_to_merge_ratio_seq.set_initial(young_card_scan_to_merge_ratio_defaults[index]);
  _cost_per_card_scan_ms_seq.set_initial(young_only_cost_per_card_scan_ms_defaults[index]);
  _rs_length_seq.set_initial(0);
  _code_root_rs_length_seq.set_initial(0);
  _cost_per_byte_copied_ms_seq.set_initial(cost_per_byte_ms_defaults[index]);

  _constant_other_time_ms_seq.add(constant_other_time_ms_defaults[index]);
  _young_other_cost_per_region_ms_seq.add(young_other_cost_per_region_ms_defaults[index]);
  _non_young_other_cost_per_region_ms_seq.add(non_young_other_cost_per_region_ms_defaults[index]);

  // start conservatively (around 50ms is about right)
  _concurrent_mark_remark_times_ms.add(0.05);
  _concurrent_mark_cleanup_times_ms.add(0.20);
}

bool G1Analytics::enough_samples_available(TruncatedSeq const* seq) {
  return seq->num() >= 3;
}

double G1Analytics::predict_in_unit_interval(TruncatedSeq const* seq) const {
  return _predictor->predict_in_unit_interval(seq);
}

size_t G1Analytics::predict_size(TruncatedSeq const* seq) const {
  return (size_t)predict_zero_bounded(seq);
}

double G1Analytics::predict_zero_bounded(TruncatedSeq const* seq) const {
  return _predictor->predict_zero_bounded(seq);
}

double G1Analytics::predict_in_unit_interval(G1PhaseDependentSeq const* seq, bool for_young_only_phase) const {
  return clamp(seq->predict(_predictor, for_young_only_phase), 0.0, 1.0);
}

size_t G1Analytics::predict_size(G1PhaseDependentSeq const* seq, bool for_young_only_phase) const {
  return (size_t)predict_zero_bounded(seq, for_young_only_phase);
}

double G1Analytics::predict_zero_bounded(G1PhaseDependentSeq const* seq, bool for_young_only_phase) const {
  return MAX2(seq->predict(_predictor, for_young_only_phase), 0.0);
}

int G1Analytics::num_alloc_rate_ms() const {
  return _alloc_rate_ms_seq.num();
}

void G1Analytics::report_concurrent_mark_remark_times_ms(double ms) {
  _concurrent_mark_remark_times_ms.add(ms);
}

void G1Analytics::report_alloc_rate_ms(double alloc_rate) {
  _alloc_rate_ms_seq.add(alloc_rate);
}

void G1Analytics::compute_pause_time_ratios(double end_time_sec, double pause_time_ms) {
  double long_interval_ms = (end_time_sec - oldest_known_gc_end_time_sec()) * 1000.0;
  double gc_pause_time_ms = _recent_gc_times_ms.sum() - _recent_gc_times_ms.oldest() + pause_time_ms;
  _long_term_pause_time_ratio = gc_pause_time_ms / long_interval_ms;
  _long_term_pause_time_ratio = clamp(_long_term_pause_time_ratio, 0.0, 1.0);

  double short_interval_ms = (end_time_sec - most_recent_gc_end_time_sec()) * 1000.0;
  _short_term_pause_time_ratio = pause_time_ms / short_interval_ms;
  _short_term_pause_time_ratio = clamp(_short_term_pause_time_ratio, 0.0, 1.0);
}

void G1Analytics::report_concurrent_refine_rate_ms(double cards_per_ms) {
  _concurrent_refine_rate_ms_seq.add(cards_per_ms);
}

void G1Analytics::report_dirtied_cards_rate_ms(double cards_per_ms) {
  _dirtied_cards_rate_ms_seq.add(cards_per_ms);
}

void G1Analytics::report_dirtied_cards_in_thread_buffers(size_t cards) {
  _dirtied_cards_in_thread_buffers_seq.add(double(cards));
}

void G1Analytics::report_cost_per_card_scan_ms(double cost_per_card_ms, bool for_young_only_phase) {
  _cost_per_card_scan_ms_seq.add(cost_per_card_ms, for_young_only_phase);
}

void G1Analytics::report_cost_per_card_merge_ms(double cost_per_card_ms, bool for_young_only_phase) {
  _cost_per_card_merge_ms_seq.add(cost_per_card_ms, for_young_only_phase);
}

void G1Analytics::report_cost_per_code_root_scan_ms(double cost_per_code_root_ms, bool for_young_only_phase) {
  _cost_per_code_root_ms_seq.add(cost_per_code_root_ms, for_young_only_phase);
}

void G1Analytics::report_card_scan_to_merge_ratio(double merge_to_scan_ratio, bool for_young_only_phase) {
  _card_scan_to_merge_ratio_seq.add(merge_to_scan_ratio, for_young_only_phase);
}

void G1Analytics::report_cost_per_byte_ms(double cost_per_byte_ms, bool for_young_only_phase) {
  _cost_per_byte_copied_ms_seq.add(cost_per_byte_ms, for_young_only_phase);
}

void G1Analytics::report_young_other_cost_per_region_ms(double other_cost_per_region_ms) {
  _young_other_cost_per_region_ms_seq.add(other_cost_per_region_ms);
}

void G1Analytics::report_non_young_other_cost_per_region_ms(double other_cost_per_region_ms) {
  _non_young_other_cost_per_region_ms_seq.add(other_cost_per_region_ms);
}

void G1Analytics::report_constant_other_time_ms(double constant_other_time_ms) {
  _constant_other_time_ms_seq.add(constant_other_time_ms);
}

void G1Analytics::report_pending_cards(double pending_cards, bool for_young_only_phase) {
  _pending_cards_seq.add(pending_cards, for_young_only_phase);
}

void G1Analytics::report_rs_length(double rs_length, bool for_young_only_phase) {
  _rs_length_seq.add(rs_length, for_young_only_phase);
}

void G1Analytics::report_code_root_rs_length(double code_root_rs_length, bool for_young_only_phase) {
  _code_root_rs_length_seq.add(code_root_rs_length, for_young_only_phase);
}

double G1Analytics::predict_alloc_rate_ms() const {
  if (enough_samples_available(&_alloc_rate_ms_seq)) {
    return predict_zero_bounded(&_alloc_rate_ms_seq);
  } else {
    return 0.0;
  }
}

double G1Analytics::predict_concurrent_refine_rate_ms() const {
  return predict_zero_bounded(&_concurrent_refine_rate_ms_seq);
}

double G1Analytics::predict_dirtied_cards_rate_ms() const {
  return predict_zero_bounded(&_dirtied_cards_rate_ms_seq);
}

size_t G1Analytics::predict_dirtied_cards_in_thread_buffers() const {
  return predict_size(&_dirtied_cards_in_thread_buffers_seq);
}

size_t G1Analytics::predict_scan_card_num(size_t rs_length, bool for_young_only_phase) const {
  return rs_length * predict_in_unit_interval(&_card_scan_to_merge_ratio_seq, for_young_only_phase);
}

double G1Analytics::predict_card_merge_time_ms(size_t card_num, bool for_young_only_phase) const {
  return card_num * predict_zero_bounded(&_cost_per_card_merge_ms_seq, for_young_only_phase);
}

double G1Analytics::predict_code_root_scan_time_ms(size_t code_root_num, bool for_young_only_phase) const {
  return code_root_num * predict_zero_bounded(&_cost_per_code_root_ms_seq, for_young_only_phase);
}

double G1Analytics::predict_card_scan_time_ms(size_t card_num, bool for_young_only_phase) const {
  return card_num * predict_zero_bounded(&_cost_per_card_scan_ms_seq, for_young_only_phase);
}

double G1Analytics::predict_object_copy_time_ms(size_t bytes_to_copy, bool for_young_only_phase) const {
  return bytes_to_copy * predict_zero_bounded(&_cost_per_byte_copied_ms_seq, for_young_only_phase);
}

double G1Analytics::predict_constant_other_time_ms() const {
  return predict_zero_bounded(&_constant_other_time_ms_seq);
}

double G1Analytics::predict_young_other_time_ms(size_t young_num) const {
  return young_num * predict_zero_bounded(&_young_other_cost_per_region_ms_seq);
}

double G1Analytics::predict_non_young_other_time_ms(size_t non_young_num) const {
  return non_young_num * predict_zero_bounded(&_non_young_other_cost_per_region_ms_seq);
}

double G1Analytics::predict_remark_time_ms() const {
  return predict_zero_bounded(&_concurrent_mark_remark_times_ms);
}

double G1Analytics::predict_cleanup_time_ms() const {
  return predict_zero_bounded(&_concurrent_mark_cleanup_times_ms);
}

size_t G1Analytics::predict_rs_length(bool for_young_only_phase) const {
  return predict_size(&_rs_length_seq, for_young_only_phase);
}

size_t G1Analytics::predict_code_root_rs_length(bool for_young_only_phase) const {
  return predict_size(&_code_root_rs_length_seq, for_young_only_phase);
}

size_t G1Analytics::predict_pending_cards(bool for_young_only_phase) const {
  return predict_size(&_pending_cards_seq, for_young_only_phase);
}

double G1Analytics::oldest_known_gc_end_time_sec() const {
  return _recent_prev_end_times_for_all_gcs_sec.oldest();
}

double G1Analytics::most_recent_gc_end_time_sec() const {
  return _recent_prev_end_times_for_all_gcs_sec.last();
}

void G1Analytics::update_recent_gc_times(double end_time_sec,
                                         double pause_time_ms) {
  _recent_gc_times_ms.add(pause_time_ms);
  _recent_prev_end_times_for_all_gcs_sec.add(end_time_sec);
}

void G1Analytics::report_concurrent_mark_cleanup_times_ms(double ms) {
  _concurrent_mark_cleanup_times_ms.add(ms);
}
