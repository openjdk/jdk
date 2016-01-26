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

#ifndef SHARE_VM_GC_CMS_ALLOCATIONSTATS_HPP
#define SHARE_VM_GC_CMS_ALLOCATIONSTATS_HPP

#include "gc/shared/gcUtil.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class AllocationStats VALUE_OBJ_CLASS_SPEC {
  // A duration threshold (in ms) used to filter
  // possibly unreliable samples.
  static float _threshold;

  // We measure the demand between the end of the previous sweep and
  // beginning of this sweep:
  //   Count(end_last_sweep) - Count(start_this_sweep)
  //     + split_births(between) - split_deaths(between)
  // The above number divided by the time since the end of the
  // previous sweep gives us a time rate of demand for blocks
  // of this size. We compute a padded average of this rate as
  // our current estimate for the time rate of demand for blocks
  // of this size. Similarly, we keep a padded average for the time
  // between sweeps. Our current estimate for demand for blocks of
  // this size is then simply computed as the product of these two
  // estimates.
  AdaptivePaddedAverage _demand_rate_estimate;

  ssize_t     _desired;          // Demand estimate computed as described above
  ssize_t     _coal_desired;     // desired +/- small-percent for tuning coalescing

  ssize_t     _surplus;          // count - (desired +/- small-percent),
                                 // used to tune splitting in best fit
  ssize_t     _bfr_surp;         // surplus at start of current sweep
  ssize_t     _prev_sweep;       // count from end of previous sweep
  ssize_t     _before_sweep;     // count from before current sweep
  ssize_t     _coal_births;      // additional chunks from coalescing
  ssize_t     _coal_deaths;      // loss from coalescing
  ssize_t     _split_births;     // additional chunks from splitting
  ssize_t     _split_deaths;     // loss from splitting
  size_t      _returned_bytes;   // number of bytes returned to list.
 public:
  void initialize(bool split_birth = false) {
    AdaptivePaddedAverage* dummy =
      new (&_demand_rate_estimate) AdaptivePaddedAverage(CMS_FLSWeight,
                                                         CMS_FLSPadding);
    _desired = 0;
    _coal_desired = 0;
    _surplus = 0;
    _bfr_surp = 0;
    _prev_sweep = 0;
    _before_sweep = 0;
    _coal_births = 0;
    _coal_deaths = 0;
    _split_births = (split_birth ? 1 : 0);
    _split_deaths = 0;
    _returned_bytes = 0;
  }

  AllocationStats() {
    initialize();
  }

  // The rate estimate is in blocks per second.
  void compute_desired(size_t count,
                       float inter_sweep_current,
                       float inter_sweep_estimate,
                       float intra_sweep_estimate) {
    // If the latest inter-sweep time is below our granularity
    // of measurement, we may call in here with
    // inter_sweep_current == 0. However, even for suitably small
    // but non-zero inter-sweep durations, we may not trust the accuracy
    // of accumulated data, since it has not been "integrated"
    // (read "low-pass-filtered") long enough, and would be
    // vulnerable to noisy glitches. In such cases, we
    // ignore the current sample and use currently available
    // historical estimates.
    assert(prev_sweep() + split_births() + coal_births()        // "Total Production Stock"
           >= split_deaths() + coal_deaths() + (ssize_t)count, // "Current stock + depletion"
           "Conservation Principle");
    if (inter_sweep_current > _threshold) {
      ssize_t demand = prev_sweep() - (ssize_t)count + split_births() + coal_births()
                       - split_deaths() - coal_deaths();
      assert(demand >= 0,
             "Demand (" SSIZE_FORMAT ") should be non-negative for "
             PTR_FORMAT " (size=" SIZE_FORMAT ")",
             demand, p2i(this), count);
      // Defensive: adjust for imprecision in event counting
      if (demand < 0) {
        demand = 0;
      }
      float old_rate = _demand_rate_estimate.padded_average();
      float rate = ((float)demand)/inter_sweep_current;
      _demand_rate_estimate.sample(rate);
      float new_rate = _demand_rate_estimate.padded_average();
      ssize_t old_desired = _desired;
      float delta_ise = (CMSExtrapolateSweep ? intra_sweep_estimate : 0.0);
      _desired = (ssize_t)(new_rate * (inter_sweep_estimate + delta_ise));
      log_trace(gc, freelist)("demand: " SSIZE_FORMAT ", old_rate: %f, current_rate: %f, "
                              "new_rate: %f, old_desired: " SSIZE_FORMAT ", new_desired: " SSIZE_FORMAT,
                              demand, old_rate, rate, new_rate, old_desired, _desired);
    }
  }

  ssize_t desired() const { return _desired; }
  void set_desired(ssize_t v) { _desired = v; }

  ssize_t coal_desired() const { return _coal_desired; }
  void set_coal_desired(ssize_t v) { _coal_desired = v; }

  ssize_t surplus() const { return _surplus; }
  void set_surplus(ssize_t v) { _surplus = v; }
  void increment_surplus() { _surplus++; }
  void decrement_surplus() { _surplus--; }

  ssize_t bfr_surp() const { return _bfr_surp; }
  void set_bfr_surp(ssize_t v) { _bfr_surp = v; }
  ssize_t prev_sweep() const { return _prev_sweep; }
  void set_prev_sweep(ssize_t v) { _prev_sweep = v; }
  ssize_t before_sweep() const { return _before_sweep; }
  void set_before_sweep(ssize_t v) { _before_sweep = v; }

  ssize_t coal_births() const { return _coal_births; }
  void set_coal_births(ssize_t v) { _coal_births = v; }
  void increment_coal_births() { _coal_births++; }

  ssize_t coal_deaths() const { return _coal_deaths; }
  void set_coal_deaths(ssize_t v) { _coal_deaths = v; }
  void increment_coal_deaths() { _coal_deaths++; }

  ssize_t split_births() const { return _split_births; }
  void set_split_births(ssize_t v) { _split_births = v; }
  void increment_split_births() { _split_births++; }

  ssize_t split_deaths() const { return _split_deaths; }
  void set_split_deaths(ssize_t v) { _split_deaths = v; }
  void increment_split_deaths() { _split_deaths++; }

  NOT_PRODUCT(
    size_t returned_bytes() const { return _returned_bytes; }
    void set_returned_bytes(size_t v) { _returned_bytes = v; }
  )
};

#endif // SHARE_VM_GC_CMS_ALLOCATIONSTATS_HPP
