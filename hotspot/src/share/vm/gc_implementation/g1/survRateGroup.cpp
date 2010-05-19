/*
 * Copyright 2001-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_survRateGroup.cpp.incl"

SurvRateGroup::SurvRateGroup(G1CollectorPolicy* g1p,
                             const char* name,
                             size_t summary_surv_rates_len) :
    _g1p(g1p), _name(name),
    _summary_surv_rates_len(summary_surv_rates_len),
    _summary_surv_rates_max_len(0),
    _summary_surv_rates(NULL),
    _surv_rate(NULL),
    _accum_surv_rate_pred(NULL),
    _surv_rate_pred(NULL)
{
  reset();
  if (summary_surv_rates_len > 0) {
    size_t length = summary_surv_rates_len;
      _summary_surv_rates = NEW_C_HEAP_ARRAY(NumberSeq*, length);
    if (_summary_surv_rates == NULL) {
      vm_exit_out_of_memory(sizeof(NumberSeq*) * length,
                            "Not enough space for surv rate summary");
    }
    for (size_t i = 0; i < length; ++i)
      _summary_surv_rates[i] = new NumberSeq();
  }

  start_adding_regions();
}


void SurvRateGroup::reset()
{
  _all_regions_allocated = 0;
  _setup_seq_num         = 0;
  _stats_arrays_length   = 0;
  _accum_surv_rate       = 0.0;
  _last_pred             = 0.0;
  // the following will set up the arrays with length 1
  _region_num            = 1;
  stop_adding_regions();
  guarantee( _stats_arrays_length == 1, "invariant" );
  guarantee( _surv_rate_pred[0] != NULL, "invariant" );
  _surv_rate_pred[0]->add(0.4);
  all_surviving_words_recorded(false);
  _region_num = 0;
}


void
SurvRateGroup::start_adding_regions() {
  _setup_seq_num   = _stats_arrays_length;
  _region_num      = 0;
  _accum_surv_rate = 0.0;

#if 0
  gclog_or_tty->print_cr("[%s] start adding regions, seq num %d, length %d",
                         _name, _setup_seq_num, _region_num);
#endif // 0
}

void
SurvRateGroup::stop_adding_regions() {

#if 0
  gclog_or_tty->print_cr("[%s] stop adding regions, length %d", _name, _region_num);
#endif // 0

  if (_region_num > _stats_arrays_length) {
    double* old_surv_rate = _surv_rate;
    double* old_accum_surv_rate_pred = _accum_surv_rate_pred;
    TruncatedSeq** old_surv_rate_pred = _surv_rate_pred;

    _surv_rate = NEW_C_HEAP_ARRAY(double, _region_num);
    if (_surv_rate == NULL) {
      vm_exit_out_of_memory(sizeof(double) * _region_num,
                            "Not enough space for surv rate array.");
    }
    _accum_surv_rate_pred = NEW_C_HEAP_ARRAY(double, _region_num);
    if (_accum_surv_rate_pred == NULL) {
      vm_exit_out_of_memory(sizeof(double) * _region_num,
                         "Not enough space for accum surv rate pred array.");
    }
    _surv_rate_pred = NEW_C_HEAP_ARRAY(TruncatedSeq*, _region_num);
    if (_surv_rate == NULL) {
      vm_exit_out_of_memory(sizeof(TruncatedSeq*) * _region_num,
                            "Not enough space for surv rate pred array.");
    }

    for (size_t i = 0; i < _stats_arrays_length; ++i)
      _surv_rate_pred[i] = old_surv_rate_pred[i];

#if 0
    gclog_or_tty->print_cr("[%s] stop adding regions, new seqs %d to %d",
                  _name, _array_length, _region_num - 1);
#endif // 0

    for (size_t i = _stats_arrays_length; i < _region_num; ++i) {
      _surv_rate_pred[i] = new TruncatedSeq(10);
      // _surv_rate_pred[i]->add(last_pred);
    }

    _stats_arrays_length = _region_num;

    if (old_surv_rate != NULL)
      FREE_C_HEAP_ARRAY(double, old_surv_rate);
    if (old_accum_surv_rate_pred != NULL)
      FREE_C_HEAP_ARRAY(double, old_accum_surv_rate_pred);
    if (old_surv_rate_pred != NULL)
      FREE_C_HEAP_ARRAY(NumberSeq*, old_surv_rate_pred);
  }

  for (size_t i = 0; i < _stats_arrays_length; ++i)
    _surv_rate[i] = 0.0;
}

double
SurvRateGroup::accum_surv_rate(size_t adjustment) {
  // we might relax this one in the future...
  guarantee( adjustment == 0 || adjustment == 1, "pre-condition" );

  double ret = _accum_surv_rate;
  if (adjustment > 0) {
    TruncatedSeq* seq = get_seq(_region_num+1);
    double surv_rate = _g1p->get_new_prediction(seq);
    ret += surv_rate;
  }

  return ret;
}

int
SurvRateGroup::next_age_index() {
  TruncatedSeq* seq = get_seq(_region_num);
  double surv_rate = _g1p->get_new_prediction(seq);
  _accum_surv_rate += surv_rate;

  ++_region_num;
  return (int) ++_all_regions_allocated;
}

void
SurvRateGroup::record_surviving_words(int age_in_group, size_t surv_words) {
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

void
SurvRateGroup::all_surviving_words_recorded(bool propagate) {
  if (propagate && _region_num > 0) { // conservative
    double surv_rate = _surv_rate_pred[_region_num-1]->last();

#if 0
    gclog_or_tty->print_cr("propagating %1.2lf from %d to %d",
                  surv_rate, _curr_length, _array_length - 1);
#endif // 0

    for (size_t i = _region_num; i < _stats_arrays_length; ++i) {
      guarantee( _surv_rate[i] <= 0.00001,
                 "the slot should not have been updated" );
      _surv_rate_pred[i]->add(surv_rate);
    }
  }

  double accum = 0.0;
  double pred = 0.0;
  for (size_t i = 0; i < _stats_arrays_length; ++i) {
    pred = _g1p->get_new_prediction(_surv_rate_pred[i]);
    if (pred > 1.0) pred = 1.0;
    accum += pred;
    _accum_surv_rate_pred[i] = accum;
    // gclog_or_tty->print_cr("age %3d, accum %10.2lf", i, accum);
  }
  _last_pred = pred;
}

#ifndef PRODUCT
void
SurvRateGroup::print() {
  gclog_or_tty->print_cr("Surv Rate Group: %s (%d entries)",
                _name, _region_num);
  for (size_t i = 0; i < _region_num; ++i) {
    gclog_or_tty->print_cr("    age %4d   surv rate %6.2lf %%   pred %6.2lf %%",
                  i, _surv_rate[i] * 100.0,
                  _g1p->get_new_prediction(_surv_rate_pred[i]) * 100.0);
  }
}

void
SurvRateGroup::print_surv_rate_summary() {
  size_t length = _summary_surv_rates_max_len;
  if (length == 0)
    return;

  gclog_or_tty->print_cr("");
  gclog_or_tty->print_cr("%s Rate Summary (for up to age %d)", _name, length-1);
  gclog_or_tty->print_cr("      age range     survival rate (avg)      samples (avg)");
  gclog_or_tty->print_cr("  ---------------------------------------------------------");

  size_t index = 0;
  size_t limit = MIN2((int) length, 10);
  while (index < limit) {
    gclog_or_tty->print_cr("           %4d                 %6.2lf%%             %6.2lf",
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
      gclog_or_tty->print_cr("   %4d .. %4d                 %6.2lf%%             %6.2lf",
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
