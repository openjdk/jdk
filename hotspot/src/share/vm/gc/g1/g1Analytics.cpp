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

#include "precompiled.hpp"
#include "gc/g1/g1Analytics.hpp"
#include "gc/g1/g1Predictions.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/numberSeq.hpp"

// Different defaults for different number of GC threads
// They were chosen by running GCOld and SPECjbb on debris with different
//   numbers of GC threads and choosing them based on the results

// all the same
static double rs_length_diff_defaults[] = {
  0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
};

static double cost_per_card_ms_defaults[] = {
  0.01, 0.005, 0.005, 0.003, 0.003, 0.002, 0.002, 0.0015
};

// all the same
static double young_cards_per_entry_ratio_defaults[] = {
  1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0
};

static double cost_per_entry_ms_defaults[] = {
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
    _recent_gc_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
    _concurrent_mark_remark_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
    _concurrent_mark_cleanup_times_ms(new TruncatedSeq(NumPrevPausesForHeuristics)),
    _alloc_rate_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
    _prev_collection_pause_end_ms(0.0),
    _rs_length_diff_seq(new TruncatedSeq(TruncatedSeqLength)),
    _cost_per_card_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
    _cost_scan_hcc_seq(new TruncatedSeq(TruncatedSeqLength)),
    _young_cards_per_entry_ratio_seq(new TruncatedSeq(TruncatedSeqLength)),
    _mixed_cards_per_entry_ratio_seq(new TruncatedSeq(TruncatedSeqLength)),
    _cost_per_entry_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
    _mixed_cost_per_entry_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
    _cost_per_byte_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
    _cost_per_byte_ms_during_cm_seq(new TruncatedSeq(TruncatedSeqLength)),
    _constant_other_time_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
    _young_other_cost_per_region_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
    _non_young_other_cost_per_region_ms_seq(new TruncatedSeq(TruncatedSeqLength)),
    _pending_cards_seq(new TruncatedSeq(TruncatedSeqLength)),
    _rs_lengths_seq(new TruncatedSeq(TruncatedSeqLength)),
    _recent_prev_end_times_for_all_gcs_sec(new TruncatedSeq(NumPrevPausesForHeuristics)) {

  // Seed sequences with initial values.
  _recent_prev_end_times_for_all_gcs_sec->add(os::elapsedTime());
  _prev_collection_pause_end_ms = os::elapsedTime() * 1000.0;

  int index = MIN2(ParallelGCThreads - 1, 7u);

  _rs_length_diff_seq->add(rs_length_diff_defaults[index]);
  _cost_per_card_ms_seq->add(cost_per_card_ms_defaults[index]);
  _cost_scan_hcc_seq->add(0.0);
  _young_cards_per_entry_ratio_seq->add(young_cards_per_entry_ratio_defaults[index]);
  _cost_per_entry_ms_seq->add(cost_per_entry_ms_defaults[index]);
  _cost_per_byte_ms_seq->add(cost_per_byte_ms_defaults[index]);
  _constant_other_time_ms_seq->add(constant_other_time_ms_defaults[index]);
  _young_other_cost_per_region_ms_seq->add(young_other_cost_per_region_ms_defaults[index]);
  _non_young_other_cost_per_region_ms_seq->add(non_young_other_cost_per_region_ms_defaults[index]);

  // start conservatively (around 50ms is about right)
  _concurrent_mark_remark_times_ms->add(0.05);
  _concurrent_mark_cleanup_times_ms->add(0.20);
}

double G1Analytics::get_new_prediction(TruncatedSeq const* seq) const {
  return _predictor->get_new_prediction(seq);
}

size_t G1Analytics::get_new_size_prediction(TruncatedSeq const* seq) const {
  return (size_t)get_new_prediction(seq);
}

int G1Analytics::num_alloc_rate_ms() const {
  return _alloc_rate_ms_seq->num();
}

void G1Analytics::report_concurrent_mark_remark_times_ms(double ms) {
  _concurrent_mark_remark_times_ms->add(ms);
}

void G1Analytics::report_alloc_rate_ms(double alloc_rate) {
  _alloc_rate_ms_seq->add(alloc_rate);
}

void G1Analytics::compute_pause_time_ratio(double interval_ms, double pause_time_ms) {
  _recent_avg_pause_time_ratio = _recent_gc_times_ms->sum() / interval_ms;
  if (_recent_avg_pause_time_ratio < 0.0 ||
      (_recent_avg_pause_time_ratio - 1.0 > 0.0)) {
    // Clip ratio between 0.0 and 1.0, and continue. This will be fixed in
    // CR 6902692 by redoing the manner in which the ratio is incrementally computed.
    if (_recent_avg_pause_time_ratio < 0.0) {
      _recent_avg_pause_time_ratio = 0.0;
    } else {
      assert(_recent_avg_pause_time_ratio - 1.0 > 0.0, "Ctl-point invariant");
      _recent_avg_pause_time_ratio = 1.0;
    }
  }

  // Compute the ratio of just this last pause time to the entire time range stored
  // in the vectors. Comparing this pause to the entire range, rather than only the
  // most recent interval, has the effect of smoothing over a possible transient 'burst'
  // of more frequent pauses that don't really reflect a change in heap occupancy.
  // This reduces the likelihood of a needless heap expansion being triggered.
  _last_pause_time_ratio =
    (pause_time_ms * _recent_prev_end_times_for_all_gcs_sec->num()) / interval_ms;
}

void G1Analytics::report_cost_per_card_ms(double cost_per_card_ms) {
  _cost_per_card_ms_seq->add(cost_per_card_ms);
}

void G1Analytics::report_cost_scan_hcc(double cost_scan_hcc) {
  _cost_scan_hcc_seq->add(cost_scan_hcc);
}

void G1Analytics::report_cost_per_entry_ms(double cost_per_entry_ms, bool last_gc_was_young) {
  if (last_gc_was_young) {
    _cost_per_entry_ms_seq->add(cost_per_entry_ms);
  } else {
    _mixed_cost_per_entry_ms_seq->add(cost_per_entry_ms);
  }
}

void G1Analytics::report_cards_per_entry_ratio(double cards_per_entry_ratio, bool last_gc_was_young) {
  if (last_gc_was_young) {
    _young_cards_per_entry_ratio_seq->add(cards_per_entry_ratio);
  } else {
    _mixed_cards_per_entry_ratio_seq->add(cards_per_entry_ratio);
  }
}

void G1Analytics::report_rs_length_diff(double rs_length_diff) {
  _rs_length_diff_seq->add(rs_length_diff);
}

void G1Analytics::report_cost_per_byte_ms(double cost_per_byte_ms, bool in_marking_window) {
  if (in_marking_window) {
    _cost_per_byte_ms_during_cm_seq->add(cost_per_byte_ms);
  } else {
    _cost_per_byte_ms_seq->add(cost_per_byte_ms);
  }
}

void G1Analytics::report_young_other_cost_per_region_ms(double other_cost_per_region_ms) {
  _young_other_cost_per_region_ms_seq->add(other_cost_per_region_ms);
}

void G1Analytics::report_non_young_other_cost_per_region_ms(double other_cost_per_region_ms) {
  _non_young_other_cost_per_region_ms_seq->add(other_cost_per_region_ms);
}

void G1Analytics::report_constant_other_time_ms(double constant_other_time_ms) {
  _constant_other_time_ms_seq->add(constant_other_time_ms);
}

void G1Analytics::report_pending_cards(double pending_cards) {
  _pending_cards_seq->add(pending_cards);
}

void G1Analytics::report_rs_lengths(double rs_lengths) {
  _rs_lengths_seq->add(rs_lengths);
}

size_t G1Analytics::predict_rs_length_diff() const {
  return get_new_size_prediction(_rs_length_diff_seq);
}

double G1Analytics::predict_alloc_rate_ms() const {
  return get_new_prediction(_alloc_rate_ms_seq);
}

double G1Analytics::predict_cost_per_card_ms() const {
  return get_new_prediction(_cost_per_card_ms_seq);
}

double G1Analytics::predict_scan_hcc_ms() const {
  return get_new_prediction(_cost_scan_hcc_seq);
}

double G1Analytics::predict_rs_update_time_ms(size_t pending_cards) const {
  return pending_cards * predict_cost_per_card_ms() + predict_scan_hcc_ms();
}

double G1Analytics::predict_young_cards_per_entry_ratio() const {
  return get_new_prediction(_young_cards_per_entry_ratio_seq);
}

double G1Analytics::predict_mixed_cards_per_entry_ratio() const {
  if (_mixed_cards_per_entry_ratio_seq->num() < 2) {
    return predict_young_cards_per_entry_ratio();
  } else {
    return get_new_prediction(_mixed_cards_per_entry_ratio_seq);
  }
}

size_t G1Analytics::predict_card_num(size_t rs_length, bool gcs_are_young) const {
  if (gcs_are_young) {
    return (size_t) (rs_length * predict_young_cards_per_entry_ratio());
  } else {
    return (size_t) (rs_length * predict_mixed_cards_per_entry_ratio());
  }
}

double G1Analytics::predict_rs_scan_time_ms(size_t card_num, bool gcs_are_young) const {
  if (gcs_are_young) {
    return card_num * get_new_prediction(_cost_per_entry_ms_seq);
  } else {
    return predict_mixed_rs_scan_time_ms(card_num);
  }
}

double G1Analytics::predict_mixed_rs_scan_time_ms(size_t card_num) const {
  if (_mixed_cost_per_entry_ms_seq->num() < 3) {
    return card_num * get_new_prediction(_cost_per_entry_ms_seq);
  } else {
    return card_num * get_new_prediction(_mixed_cost_per_entry_ms_seq);
  }
}

double G1Analytics::predict_object_copy_time_ms_during_cm(size_t bytes_to_copy) const {
  if (_cost_per_byte_ms_during_cm_seq->num() < 3) {
    return (1.1 * bytes_to_copy) * get_new_prediction(_cost_per_byte_ms_seq);
  } else {
    return bytes_to_copy * get_new_prediction(_cost_per_byte_ms_during_cm_seq);
  }
}

double G1Analytics::predict_object_copy_time_ms(size_t bytes_to_copy, bool during_concurrent_mark) const {
  if (during_concurrent_mark) {
    return predict_object_copy_time_ms_during_cm(bytes_to_copy);
  } else {
    return bytes_to_copy * get_new_prediction(_cost_per_byte_ms_seq);
  }
}

double G1Analytics::predict_constant_other_time_ms() const {
  return get_new_prediction(_constant_other_time_ms_seq);
}

double G1Analytics::predict_young_other_time_ms(size_t young_num) const {
  return young_num * get_new_prediction(_young_other_cost_per_region_ms_seq);
}

double G1Analytics::predict_non_young_other_time_ms(size_t non_young_num) const {
  return non_young_num * get_new_prediction(_non_young_other_cost_per_region_ms_seq);
}

double G1Analytics::predict_remark_time_ms() const {
  return get_new_prediction(_concurrent_mark_remark_times_ms);
}

double G1Analytics::predict_cleanup_time_ms() const {
  return get_new_prediction(_concurrent_mark_cleanup_times_ms);
}

size_t G1Analytics::predict_rs_lengths() const {
  return get_new_size_prediction(_rs_lengths_seq);
}

size_t G1Analytics::predict_pending_cards() const {
  return get_new_size_prediction(_pending_cards_seq);
}

double G1Analytics::last_known_gc_end_time_sec() const {
  return _recent_prev_end_times_for_all_gcs_sec->oldest();
}

void G1Analytics::update_recent_gc_times(double end_time_sec,
                                         double pause_time_ms) {
  _recent_gc_times_ms->add(pause_time_ms);
  _recent_prev_end_times_for_all_gcs_sec->add(end_time_sec);
  _prev_collection_pause_end_ms = end_time_sec * 1000.0;
}

void G1Analytics::report_concurrent_mark_cleanup_times_ms(double ms) {
  _concurrent_mark_cleanup_times_ms->add(ms);
}

