/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1EVACSTATS_HPP
#define SHARE_GC_G1_G1EVACSTATS_HPP

#include "gc/shared/gcUtil.hpp"
#include "gc/shared/plab.hpp"

// Records various memory allocation statistics gathered during evacuation. All sizes
// are in HeapWords.
class G1EvacStats : public PLABStats {
  size_t _default_plab_size;
  size_t _desired_net_plab_size; // Output of filter (below), suitably trimmed and quantized
  AdaptiveWeightedAverage
         _net_plab_size_filter;  // Integrator with decay

  size_t _region_end_waste; // Number of words wasted due to skipping to the next region.
  uint   _regions_filled;   // Number of regions filled completely.
  size_t _num_plab_filled; // Number of PLABs filled and retired.
  size_t _direct_allocated; // Number of words allocated directly into the regions.
  size_t _num_direct_allocated; // Number of direct allocation attempts.

  // Number of words in live objects remaining in regions that ultimately suffered an
  // evacuation failure. This is used in the regions when the regions are made old regions.
  size_t _failure_used;
  // Number of words wasted in regions which failed evacuation. This is the sum of space
  // for objects successfully copied out of the regions (now dead space) plus waste at the
  // end of regions.
  size_t _failure_waste;

  virtual void reset() {
    PLABStats::reset();
    _region_end_waste = 0;
    _regions_filled = 0;
    _num_plab_filled = 0;
    _direct_allocated = 0;
    _num_direct_allocated = 0;
    _failure_used = 0;
    _failure_waste = 0;
  }

  void log_plab_allocation();
  void log_sizing(size_t calculated_words, size_t net_desired_words);

  size_t compute_desired_plab_size() const;

public:
  G1EvacStats(const char* description, size_t default_per_thread_plab_size, unsigned wt);

  // Calculates plab size for current number of gc worker threads.
  size_t desired_plab_size(uint no_of_gc_workers) const;

  // Computes the new desired PLAB size assuming one gc worker thread, updating
  // _desired_plab_sz, and clearing statistics for the next GC.
  // Should be called at the end of a GC pause.
  void adjust_desired_plab_size();

  uint regions_filled() const { return _regions_filled; }
  size_t num_plab_filled() const { return _num_plab_filled; }
  size_t region_end_waste() const { return _region_end_waste; }
  size_t direct_allocated() const { return _direct_allocated; }
  size_t num_direct_allocated() const { return _num_direct_allocated; }

  // Amount of space in heapwords used in the failing regions when an evacuation failure happens.
  size_t failure_used() const { return _failure_used; }
  // Amount of space in heapwords wasted (unused) in the failing regions when an evacuation failure happens.
  size_t failure_waste() const { return _failure_waste; }

  inline void add_num_plab_filled(size_t value);
  inline void add_direct_allocated(size_t value);
  inline void add_num_direct_allocated(size_t value);
  inline void add_region_end_waste(size_t value);
  inline void add_failure_used_and_waste(size_t used, size_t waste);
};

#endif // SHARE_GC_G1_G1EVACSTATS_HPP
