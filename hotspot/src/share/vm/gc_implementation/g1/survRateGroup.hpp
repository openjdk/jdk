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

class G1CollectorPolicy;

class SurvRateGroup : public CHeapObj {
private:
  G1CollectorPolicy* _g1p;
  const char* _name;

  size_t  _stats_arrays_length;
  double* _surv_rate;
  double* _accum_surv_rate_pred;
  double  _last_pred;
  double  _accum_surv_rate;
  TruncatedSeq** _surv_rate_pred;
  NumberSeq**    _summary_surv_rates;
  size_t         _summary_surv_rates_len;
  size_t         _summary_surv_rates_max_len;

  int _all_regions_allocated;
  size_t _region_num;
  size_t _setup_seq_num;

public:
  SurvRateGroup(G1CollectorPolicy* g1p,
                const char* name,
                size_t summary_surv_rates_len);
  void reset();
  void start_adding_regions();
  void stop_adding_regions();
  void record_surviving_words(int age_in_group, size_t surv_words);
  void all_surviving_words_recorded(bool propagate);
  const char* name() { return _name; }

  size_t region_num() { return _region_num; }
  double accum_surv_rate_pred(int age) {
    assert(age >= 0, "must be");
    if ((size_t)age < _stats_arrays_length)
      return _accum_surv_rate_pred[age];
    else {
      double diff = (double) (age - _stats_arrays_length + 1);
      return _accum_surv_rate_pred[_stats_arrays_length-1] + diff * _last_pred;
    }
  }

  double accum_surv_rate(size_t adjustment);

  TruncatedSeq* get_seq(size_t age) {
    if (age >= _setup_seq_num) {
      guarantee( _setup_seq_num > 0, "invariant" );
      age = _setup_seq_num-1;
    }
    TruncatedSeq* seq = _surv_rate_pred[age];
    guarantee( seq != NULL, "invariant" );
    return seq;
  }

  int next_age_index();
  int age_in_group(int age_index) {
    int ret = (int) (_all_regions_allocated - age_index);
    assert( ret >= 0, "invariant" );
    return ret;
  }
  void finished_recalculating_age_indexes() {
    _all_regions_allocated = 0;
  }

#ifndef PRODUCT
  void print();
  void print_surv_rate_summary();
#endif // PRODUCT
};
