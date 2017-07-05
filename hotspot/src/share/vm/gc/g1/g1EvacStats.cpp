/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1EvacStats.hpp"
#include "gc/shared/gcId.hpp"
#include "trace/tracing.hpp"

void G1EvacStats::adjust_desired_plab_sz() {
  if (PrintPLAB) {
    gclog_or_tty->print(" (allocated = " SIZE_FORMAT " wasted = " SIZE_FORMAT " "
                        "unused = " SIZE_FORMAT " used = " SIZE_FORMAT " "
                        "undo_waste = " SIZE_FORMAT " region_end_waste = " SIZE_FORMAT " "
                        "regions filled = %u direct_allocated = " SIZE_FORMAT " "
                        "failure_used = " SIZE_FORMAT " failure_waste = " SIZE_FORMAT ") ",
                        _allocated, _wasted, _unused, used(), _undo_wasted, _region_end_waste,
                        _regions_filled, _direct_allocated, _failure_used, _failure_waste);
  }

  if (ResizePLAB) {

    assert(is_object_aligned(max_size()) && min_size() <= max_size(),
           "PLAB clipping computation may be incorrect");

    if (_allocated == 0) {
      assert((_unused == 0),
             err_msg("Inconsistency in PLAB stats: "
                     "_allocated: "SIZE_FORMAT", "
                     "_wasted: "SIZE_FORMAT", "
                     "_region_end_waste: "SIZE_FORMAT", "
                     "_unused: "SIZE_FORMAT", "
                     "_used  : "SIZE_FORMAT,
                     _allocated, _wasted, _region_end_waste, _unused, used()));
      _allocated = 1;
    }
    // We account region end waste fully to PLAB allocation. This is not completely fair,
    // but is a conservative assumption because PLABs may be sized flexibly while we
    // cannot adjust direct allocations.
    // In some cases, wasted_frac may become > 1 but that just reflects the problem
    // with region_end_waste.
    double wasted_frac    = (double)(_unused + _wasted + _region_end_waste) / (double)_allocated;
    size_t target_refills = (size_t)((wasted_frac * TargetSurvivorRatio) / TargetPLABWastePct);
    if (target_refills == 0) {
      target_refills = 1;
    }
    size_t cur_plab_sz = used() / target_refills;
    // Take historical weighted average
    _filter.sample(cur_plab_sz);
    // Clip from above and below, and align to object boundary
    size_t plab_sz;
    plab_sz = MAX2(min_size(), (size_t)_filter.average());
    plab_sz = MIN2(max_size(), plab_sz);
    plab_sz = align_object_size(plab_sz);
    // Latch the result
    _desired_net_plab_sz = plab_sz;
    if (PrintPLAB) {
      gclog_or_tty->print_cr(" (plab_sz = " SIZE_FORMAT " desired_plab_sz = " SIZE_FORMAT ") ", cur_plab_sz, plab_sz);
    }
  }
  // Clear accumulators for next round.
  reset();
}

