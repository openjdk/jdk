/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1ANALYTICSSEQUENCES_INLINE_HPP
#define SHARE_GC_G1_G1ANALYTICSSEQUENCES_INLINE_HPP

#include "gc/g1/g1AnalyticsSequences.hpp"

#include "gc/g1/g1Predictions.hpp"

bool G1PhaseDependentSeq::enough_samples_to_use_mixed_seq() const {
  return G1Analytics::enough_samples_available(&_mixed_seq);
}

G1PhaseDependentSeq::G1PhaseDependentSeq(int length) :
  _young_only_seq(length),
  _initial_value(0.0),
  _mixed_seq(length)
{ }

TruncatedSeq* G1PhaseDependentSeq::seq_raw(bool use_young_only_phase_seq) {
  return use_young_only_phase_seq ? &_young_only_seq : &_mixed_seq;
}

void G1PhaseDependentSeq::set_initial(double value) {
  _initial_value = value;
}

void G1PhaseDependentSeq::add(double value, bool for_young_only_phase) {
  seq_raw(for_young_only_phase)->add(value);
}

double G1PhaseDependentSeq::predict(const G1Predictions* predictor, bool use_young_only_phase_seq) const {
  if (use_young_only_phase_seq || !enough_samples_to_use_mixed_seq()) {
    if (_young_only_seq.num() == 0) {
      return _initial_value;
    }
    return predictor->predict(&_young_only_seq);
  } else {
    assert(_mixed_seq.num() > 0, "must not ask this with no samples");
    return predictor->predict(&_mixed_seq);
  }
}

#endif /* SHARE_GC_G1_G1ANALYTICSSEQUENCES_INLINE_HPP */
