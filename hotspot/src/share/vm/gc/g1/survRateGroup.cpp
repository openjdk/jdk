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
#include "memory/allocation.hpp"

SurvRateGroup::SurvRateGroup(G1Predictions* predictor,
                             const char* name,
                             size_t summary_surv_rates_len) :
    _predictor(predictor), _name(name),
    _summary_surv_rates_len(summary_surv_rates_len),
    _summary_surv_rates_max_len(0),
    _summary_surv_rates(NULL),
    _surv_rate(NULL),
    _accum_surv_rate_pred(NULL),
    _surv_rate_pred(NULL),
    _stats_arrays_length(0) {
  reset();
  if (summary_surv_rates_len > 0) {
    size_t length = summary_surv_rates_len;
      _summary_surv_rates = NEW_C_HEAP_ARRAY(NumberSeq*, length, mtGC);
    for (size_t i = 0; i < length; ++i) {
      _summary_surv_rates[i] = new NumberSeq();
    }
  }

  start_adding_regions();
}

double SurvRateGroup::get_new_prediction(TruncatedSeq const* seq) const {
  return _predictor->get_new_prediction(seq);
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
  guarantee( _stats_arrays_length == 1, "invariant" );
  guarantee( _surv_rate_pred[0] != NULL, "invariant" );
  _surv_rate_pred[0]->add(0.4);
  all_surviving_words_recorded(false);
  _region_num = 0;
}

void SurvRateGroup::start_adding_regions() {
  _setup_seq_num   = _stats_arrays_length;
  _region_num      = 0;
}

void SurvRateGroup::stop_adding_regions() {
  if (_region_num > _stats_arrays_length) {
    double* old_surv_rate = _surv_rate;
    double* old_accum_surv_rate_pred = _accum_surv_rate_pred;
    TruncatedSeq** old_surv_rate_pred = _surv_rate_pred;

    _surv_rate = NEW_C_HEAP_ARRAY(double, _region_num, mtGC);
    _accum_surv_rate_pred = NEW_C_HEAP_ARRAY(double, _region_num, mtGC);
    _surv_rate_pred = NEW_C_HEAP_ARRAY(TruncatedSeq*, _region_num, mtGC);

    for (size_t i = 0; i < _stats_arrays_length; ++i) {
      _surv_rate_pred[i] = old_surv_rate_pred[i];
    }
    for (size_t i = _stats_arrays_length; i < _region_num; ++i) {
      _surv_rate_pred[i] = new TruncatedSeq(10);
    }

    _stats_arrays_length = _region_num;

    if (old_surv_rate != NULL) {
      FREE_C_HEAP_ARRAY(double, old_surv_rate);
    }
    if (old_accum_surv_rate_pred != NULL) {
      FREE_C_HEAP_ARRAY(double, old_accum_surv_rate_pred);
    }
    if (old_surv_rate_pred != NULL) {
      FREE_C_HEAP_ARRAY(TruncatedSeq*, old_surv_rate_pred);
    }
  }

  for (size_t i = 0; i < _stats_arrays_length; ++i) {
    _surv_rate[i] = 0.0;
  }
}

int SurvRateGroup::next_age_index() {
  ++_region_num;
  return (int) ++_all_regions_allocated;
}

void SurvRateGroup::record_surviving_words(int age_in_group, size_t surv_words) {
  guarantee( 0 <= age_in_group && (size_t) age_in_group < _region_num,
             "pre-condition" );
  guarantee( _surv_rate[age_in_group] <= 0.00001,
             "should only update each slot once" );

  double surv_rate = (double) surv_words / (double) HeapRegion::GrainWords;
  _surv_rate[age_in_group] = surv_rate;
  _surv_rate_pred[age_in_group]->add(surv_rate);
  if ((size_t)age_in_group < _summary_surv_rates_len) {
    _summary_surv_rates[age_in_group]->add(surv_rate);
    if ((size_t)(age_in_group+1) > _summary_surv_rates_max_len)
      _summary_surv_rates_max_len = age_in_group+1;
  }
}

void SurvRateGroup::all_surviving_words_recorded(bool update_predictors) {
  if (update_predictors && _region_num > 0) { // conservative
    double surv_rate = _surv_rate_pred[_region_num-1]->last();
    for (size_t i = _region_num; i < _stats_arrays_length; ++i) {
      guarantee( _surv_rate[i] <= 0.00001,
                 "the slot should not have been updated" );
      _surv_rate_pred[i]->add(surv_rate);
    }
  }

  double accum = 0.0;
  double pred = 0.0;
  for (size_t i = 0; i < _stats_arrays_length; ++i) {
    pred = get_new_prediction(_surv_rate_pred[i]);
    if (pred > 1.0) pred = 1.0;
    accum += pred;
    _accum_surv_rate_pred[i] = accum;
  }
  _last_pred = pred;
}

#ifndef PRODUCT
void SurvRateGroup::print() {
  gclog_or_tty->print_cr("Surv Rate Group: %s (" SIZE_FORMAT " entries)",
                _name, _region_num);
  for (size_t i = 0; i < _region_num; ++i) {
    gclog_or_tty->print_cr("    age " SIZE_FORMAT_W(4) "   surv rate %6.2lf %%   pred %6.2lf %%",
                           i, _surv_rate[i] * 100.0,
                           _predictor->get_new_prediction(_surv_rate_pred[i]) * 100.0);
  }
}

void
SurvRateGroup::print_surv_rate_summary() {
  size_t length = _summary_surv_rates_max_len;
  if (length == 0)
    return;

  gclog_or_tty->cr();
  gclog_or_tty->print_cr("%s Rate Summary (for up to age " SIZE_FORMAT ")", _name, length-1);
  gclog_or_tty->print_cr("      age range     survival rate (avg)      samples (avg)");
  gclog_or_tty->print_cr("  ---------------------------------------------------------");

  size_t index = 0;
  size_t limit = MIN2((int) length, 10);
  while (index < limit) {
    gclog_or_tty->print_cr("           " SIZE_FORMAT_W(4)
                           "                 %6.2lf%%             %6.2lf",
                           index, _summary_surv_rates[index]->avg() * 100.0,
                           (double) _summary_surv_rates[index]->num());
    ++index;
  }

  gclog_or_tty->print_cr("  ---------------------------------------------------------");

  int num = 0;
  double sum = 0.0;
  int samples = 0;
  while (index < length) {
    ++num;
    sum += _summary_surv_rates[index]->avg() * 100.0;
    samples += _summary_surv_rates[index]->num();
    ++index;

    if (index == length || num % 10 == 0) {
      gclog_or_tty->print_cr("   " SIZE_FORMAT_W(4) " .. " SIZE_FORMAT_W(4)
                             "                 %6.2lf%%             %6.2lf",
                             (index-1) / 10 * 10, index-1, sum / (double) num,
                             (double) samples / (double) num);
      sum = 0.0;
      num = 0;
      samples = 0;
    }
  }

  gclog_or_tty->print_cr("  ---------------------------------------------------------");
}
#endif // PRODUCT
