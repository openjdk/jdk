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

#ifndef SHARE_GC_G1_G1ANALYTICSSEQUENCES_HPP
#define SHARE_GC_G1_G1ANALYTICSSEQUENCES_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/numberSeq.hpp"

#include <float.h>

class G1Predictions;

// Container for TruncatedSeqs that need separate predictors by GC phase.
class G1PhaseDependentSeq {
  TruncatedSeq _young_only_seq;
  double _initial_value;
  TruncatedSeq _mixed_seq;

  NONCOPYABLE(G1PhaseDependentSeq);

  TruncatedSeq* seq_raw(bool use_young_only_phase_seq);

  bool enough_samples_to_use_mixed_seq() const;
public:

  G1PhaseDependentSeq(int length);

  void set_initial(double value);
  void add(double value, bool for_young_only_phase);

  double predict(const G1Predictions* predictor, bool use_young_only_phase_seq) const;
};

#endif /* SHARE_GC_G1_G1ANALYTICSSEQUENCES_HPP */
