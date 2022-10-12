/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/* 
 * File:   g1AnalyticsSequences.inline.hpp
 * Author: tschatzl
 *
 * Created on October 17, 2022, 1:24 PM
 */

#ifndef SHARE_GC_G1_G1ANALYTICSSEQUENCES_INLINE_HPP
#define SHARE_GC_G1_G1ANALYTICSSEQUENCES_INLINE_HPP

#include "gc/g1/g1AnalyticsSequences.hpp"
#include "gc/g1/g1Predictions.hpp"

bool G1PhaseDependentSeq::enough_samples_to_use_mixed_seq() const {
  return _young_only_seq.num() >= 3;
}

G1PhaseDependentSeq::G1PhaseDependentSeq(int length) :
  _young_only_seq(length),
  _mixed_seq(length)
{ }

TruncatedSeq* G1PhaseDependentSeq::seq_raw(bool use_young_only_phase_seq) {
  return use_young_only_phase_seq ? &_young_only_seq : &_mixed_seq;
}

void G1PhaseDependentSeq::set_initial(double value) {
  _young_only_seq.add(value);
}

void G1PhaseDependentSeq::add(double value, bool for_young_only_phase) {
  seq_raw(for_young_only_phase)->add(value);
}

double G1PhaseDependentSeq::predict(const G1Predictions* predictor, bool use_young_only_phase_seq) const {
  if (use_young_only_phase_seq || !enough_samples_to_use_mixed_seq()) {
    return predictor->predict(&_young_only_seq);
  } else {
    return predictor->predict(&_mixed_seq);
  }
}

#endif /* SHARE_GC_G1_G1ANALYTICSSEQUENCES_INLINE_HPP */

