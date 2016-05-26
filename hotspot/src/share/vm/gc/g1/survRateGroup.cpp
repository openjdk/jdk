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

#include "precompiled.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1Predictions.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/survRateGroup.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"

SurvRateGroup::SurvRateGroup() :
    _accum_surv_rate_pred(NULL),
    _surv_rate_pred(NULL),
    _stats_arrays_length(0) {
  reset();
  start_adding_regions();
}

void SurvRateGroup::reset() {
  _all_regions_allocated = 0;
  _setup_seq_num         = 0;
  _last_pred             = 0.0;
  // the following will set up the arrays with length 1
  _region_num            = 1;

  // The call to stop_adding_regions() will use "new" to refill
  // the _surv_rate_pred array, so we need to make sure to call
  // "delete".
  for (size_t i = 0; i < _stats_arrays_length; ++i) {
    delete _surv_rate_pred[i];
  }
  _stats_arrays_length = 0;

  stop_adding_regions();

  // Seed initial _surv_rate_pred and _accum_surv_rate_pred values
  guarantee( _stats_arrays_length == 1, "invariant" );
  guarantee( _surv_rate_pred[0] != NULL, "invariant" );
  const double initial_surv_rate = 0.4;
  _surv_rate_pred[0]->add(initial_surv_rate);
  _last_pred = _accum_surv_rate_pred[0] = initial_surv_rate;

  _region_num = 0;
}

void SurvRateGroup::start_adding_regions() {
  _setup_seq_num   = _stats_arrays_length;
  _region_num      = 0;
}

void SurvRateGroup::stop_adding_regions() {
  if (_region_num > _stats_arrays_length) {
    _accum_surv_rate_pred = REALLOC_C_HEAP_ARRAY(double, _accum_surv_rate_pred, _region_num, mtGC);
    _surv_rate_pred = REALLOC_C_HEAP_ARRAY(TruncatedSeq*, _surv_rate_pred, _region_num, mtGC);

    for (size_t i = _stats_arrays_length; i < _region_num; ++i) {
      _surv_rate_pred[i] = new TruncatedSeq(10);
    }

    _stats_arrays_length = _region_num;
  }
}

void SurvRateGroup::record_surviving_words(int age_in_group, size_t surv_words) {
  guarantee( 0 <= age_in_group && (size_t) age_in_group < _region_num,
             "pre-condition" );

  double surv_rate = (double) surv_words / (double) HeapRegion::GrainWords;
  _surv_rate_pred[age_in_group]->add(surv_rate);
}

void SurvRateGroup::all_surviving_words_recorded(const G1Predictions& predictor, bool update_predictors) {
  if (update_predictors) {
    fill_in_last_surv_rates();
  }
  finalize_predictions(predictor);
}

void SurvRateGroup::fill_in_last_surv_rates() {
  if (_region_num > 0) { // conservative
    double surv_rate = _surv_rate_pred[_region_num-1]->last();
    for (size_t i = _region_num; i < _stats_arrays_length; ++i) {
      _surv_rate_pred[i]->add(surv_rate);
    }
  }
}

void SurvRateGroup::finalize_predictions(const G1Predictions& predictor) {
  double accum = 0.0;
  double pred = 0.0;
  for (size_t i = 0; i < _stats_arrays_length; ++i) {
    pred = predictor.get_new_prediction(_surv_rate_pred[i]);
    if (pred > 1.0) pred = 1.0;
    accum += pred;
    _accum_surv_rate_pred[i] = accum;
  }
  _last_pred = pred;
}
