/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_SHARED_ALLOCATIONSTATS_HPP
#define SHARE_VM_GC_IMPLEMENTATION_SHARED_ALLOCATIONSTATS_HPP

#ifndef SERIALGC
#include "gc_implementation/shared/gcUtil.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#endif

class AllocationStats VALUE_OBJ_CLASS_SPEC {
  // A duration threshold (in ms) used to filter
  // possibly unreliable samples.
  static float _threshold;

  // We measure the demand between the end of the previous sweep and
  // beginning of this sweep:
  //   Count(end_last_sweep) - Count(start_this_sweep)
  //     + splitBirths(between) - splitDeaths(between)
  // The above number divided by the time since the end of the
  // previous sweep gives us a time rate of demand for blocks
  // of this size. We compute a padded average of this rate as
  // our current estimate for the time rate of demand for blocks
  // of this size. Similarly, we keep a padded average for the time
  // between sweeps. Our current estimate for demand for blocks of
  // this size is then simply computed as the product of these two
  // estimates.
  AdaptivePaddedAverage _demand_rate_estimate;

  ssize_t     _desired;         // Demand stimate computed as described above
  ssize_t     _coalDesired;     // desired +/- small-percent for tuning coalescing

  ssize_t     _surplus;         // count - (desired +/- small-percent),
                                // used to tune splitting in best fit
  ssize_t     _bfrSurp;         // surplus at start of current sweep
  ssize_t     _prevSweep;       // count from end of previous sweep
  ssize_t     _beforeSweep;     // count from before current sweep
  ssize_t     _coalBirths;      // additional chunks from coalescing
  ssize_t     _coalDeaths;      // loss from coalescing
  ssize_t     _splitBirths;     // additional chunks from splitting
  ssize_t     _splitDeaths;     // loss from splitting
  size_t      _returnedBytes;   // number of bytes returned to list.
 public:
  void initialize(bool split_birth = false) {
    AdaptivePaddedAverage* dummy =
      new (&_demand_rate_estimate) AdaptivePaddedAverage(CMS_FLSWeight,
                                                         CMS_FLSPadding);
    _desired = 0;
    _coalDesired = 0;
    _surplus = 0;
    _bfrSurp = 0;
    _prevSweep = 0;
    _beforeSweep = 0;
    _coalBirths = 0;
    _coalDeaths = 0;
    _splitBirths = split_birth? 1 : 0;
    _splitDeaths = 0;
    _returnedBytes = 0;
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
    // XXX NEEDS TO BE FIXED
    // assert(prevSweep() + splitBirths() >= splitDeaths() + (ssize_t)count, "Conservation Principle");
    //     ^^^^^^^^^^^^^^^^^^^^^^^^^^^    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    //     "Total Stock"                  "Not used at this block size"
    if (inter_sweep_current > _threshold) {
      ssize_t demand = prevSweep() - (ssize_t)count + splitBirths() - splitDeaths();
      // XXX NEEDS TO BE FIXED
      // assert(demand >= 0, "Demand should be non-negative");
      // Defensive: adjust for imprecision in event counting
      if (demand < 0) {
        demand = 0;
      }
      float old_rate = _demand_rate_estimate.padded_average();
      float rate = ((float)demand)/inter_sweep_current;
      _demand_rate_estimate.sample(rate);
      float new_rate = _demand_rate_estimate.padded_average();
      ssize_t old_desired = _desired;
      _desired = (ssize_t)(new_rate * (inter_sweep_estimate
                                       + CMSExtrapolateSweep
                                         ? intra_sweep_estimate
                                         : 0.0));
      if (PrintFLSStatistics > 1) {
        gclog_or_tty->print_cr("demand: %d, old_rate: %f, current_rate: %f, new_rate: %f, old_desired: %d, new_desired: %d",
                                demand,     old_rate,     rate,             new_rate,     old_desired,     _desired);
      }
    }
  }

  ssize_t desired() const { return _desired; }
  void set_desired(ssize_t v) { _desired = v; }

  ssize_t coalDesired() const { return _coalDesired; }
  void set_coalDesired(ssize_t v) { _coalDesired = v; }

  ssize_t surplus() const { return _surplus; }
  void set_surplus(ssize_t v) { _surplus = v; }
  void increment_surplus() { _surplus++; }
  void decrement_surplus() { _surplus--; }

  ssize_t bfrSurp() const { return _bfrSurp; }
  void set_bfrSurp(ssize_t v) { _bfrSurp = v; }
  ssize_t prevSweep() const { return _prevSweep; }
  void set_prevSweep(ssize_t v) { _prevSweep = v; }
  ssize_t beforeSweep() const { return _beforeSweep; }
  void set_beforeSweep(ssize_t v) { _beforeSweep = v; }

  ssize_t coalBirths() const { return _coalBirths; }
  void set_coalBirths(ssize_t v) { _coalBirths = v; }
  void increment_coalBirths() { _coalBirths++; }

  ssize_t coalDeaths() const { return _coalDeaths; }
  void set_coalDeaths(ssize_t v) { _coalDeaths = v; }
  void increment_coalDeaths() { _coalDeaths++; }

  ssize_t splitBirths() const { return _splitBirths; }
  void set_splitBirths(ssize_t v) { _splitBirths = v; }
  void increment_splitBirths() { _splitBirths++; }

  ssize_t splitDeaths() const { return _splitDeaths; }
  void set_splitDeaths(ssize_t v) { _splitDeaths = v; }
  void increment_splitDeaths() { _splitDeaths++; }

  NOT_PRODUCT(
    size_t returnedBytes() const { return _returnedBytes; }
    void set_returnedBytes(size_t v) { _returnedBytes = v; }
  )
};

#endif // SHARE_VM_GC_IMPLEMENTATION_SHARED_ALLOCATIONSTATS_HPP
