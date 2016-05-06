/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_SURVRATEGROUP_HPP
#define SHARE_VM_GC_G1_SURVRATEGROUP_HPP

#include "utilities/numberSeq.hpp"

class G1Predictions;

class SurvRateGroup : public CHeapObj<mtGC> {
private:
  size_t  _stats_arrays_length;
  double* _accum_surv_rate_pred;
  double  _last_pred;
  TruncatedSeq** _surv_rate_pred;

  int _all_regions_allocated;
  size_t _region_num;
  size_t _setup_seq_num;

  void fill_in_last_surv_rates();
  void finalize_predictions(const G1Predictions& predictor);
public:
  SurvRateGroup();
  void reset();
  void start_adding_regions();
  void stop_adding_regions();
  void record_surviving_words(int age_in_group, size_t surv_words);
  void all_surviving_words_recorded(const G1Predictions& predictor, bool update_predictors);

  size_t region_num() const { return _region_num; }

  double accum_surv_rate_pred(int age) const {
    assert(age >= 0, "must be");
    if ((size_t)age < _stats_arrays_length)
      return _accum_surv_rate_pred[age];
    else {
      double diff = (double) (age - _stats_arrays_length + 1);
      return _accum_surv_rate_pred[_stats_arrays_length-1] + diff * _last_pred;
    }
  }

  TruncatedSeq* get_seq(size_t age) const {
    if (age >= _setup_seq_num) {
      guarantee( _setup_seq_num > 0, "invariant" );
      age = _setup_seq_num-1;
    }
    TruncatedSeq* seq = _surv_rate_pred[age];
    guarantee( seq != NULL, "invariant" );
    return seq;
  }

  int next_age_index() {
    ++_region_num;
    return (int) ++_all_regions_allocated;
  }

  int age_in_group(int age_index) const {
    int ret = (int) (_all_regions_allocated - age_index);
    assert( ret >= 0, "invariant" );
    return ret;
  }
  void finished_recalculating_age_indexes() {
    _all_regions_allocated = 0;
  }

};

#endif // SHARE_VM_GC_G1_SURVRATEGROUP_HPP
