/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gc_globals.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/globals.hpp"

void G1EvacStats::log_plab_allocation() {
  log_debug(gc, plab)("%s PLAB allocation: "
                      "allocated: " SIZE_FORMAT "B, "
                      "wasted: " SIZE_FORMAT "B, "
                      "unused: " SIZE_FORMAT "B, "
                      "used: " SIZE_FORMAT "B, "
                      "undo waste: " SIZE_FORMAT "B, ",
                      _description,
                      _allocated * HeapWordSize,
                      _wasted * HeapWordSize,
                      _unused * HeapWordSize,
                      used() * HeapWordSize,
                      _undo_wasted * HeapWordSize);
  log_debug(gc, plab)("%s other allocation: "
                      "region end waste: " SIZE_FORMAT "B, "
                      "regions filled: %u, "
                      "direct allocated: " SIZE_FORMAT "B, "
                      "failure used: " SIZE_FORMAT "B, "
                      "failure wasted: " SIZE_FORMAT "B",
                      _description,
                      _region_end_waste * HeapWordSize,
                      _regions_filled,
                      _direct_allocated * HeapWordSize,
                      _failure_used * HeapWordSize,
                      _failure_waste * HeapWordSize);
}

void G1EvacStats::log_sizing(size_t calculated_words, size_t net_desired_words) {
  log_debug(gc, plab)("%s sizing: "
                      "calculated: " SIZE_FORMAT "B, "
                      "actual: " SIZE_FORMAT "B",
                      _description,
                      calculated_words * HeapWordSize,
                      net_desired_words * HeapWordSize);
}

size_t G1EvacStats::compute_desired_plab_sz() {
  // The size of the PLAB caps the amount of space that can be wasted at the
  // end of the collection. In the worst case the last PLAB could be completely
  // empty.
  // This allows us to calculate the new PLAB size to achieve the
  // TargetPLABWastePct given the latest memory usage and that the last buffer
  // will be G1LastPLABAverageOccupancy full.
  //
  // E.g. assume that if in the current GC 100 words were allocated and a
  // TargetPLABWastePct of 10 had been set.
  //
  // So we could waste up to 10 words to meet that percentage. Given that we
  // also assume that that buffer is typically half-full, the new desired PLAB
  // size is set to 20 words.
  //
  // The amount of allocation performed should be independent of the number of
  // threads, so should the maximum waste we can spend in total. So if
  // we used n threads to allocate, each of them can spend maximum waste/n words in
  // a first rough approximation. The number of threads only comes into play later
  // when actually retrieving the actual desired PLAB size.
  //
  // After calculating this optimal PLAB size the algorithm applies the usual
  // exponential decaying average over this value to guess the next PLAB size.
  //
  // We account region end waste fully to PLAB allocation (in the calculation of
  // what we consider as "used_for_waste_calculation" below). This is not
  // completely fair, but is a conservative assumption because PLABs may be sized
  // flexibly while we cannot adjust inline allocations.
  // Allocation during GC will try to minimize region end waste so this impact
  // should be minimal.
  //
  // We need to cover overflow when calculating the amount of space actually used
  // by objects in PLABs when subtracting the region end waste.
  // Region end waste may be higher than actual allocation. This may occur if many
  // threads do not allocate anything but a few rather large objects. In this
  // degenerate case the PLAB size would simply quickly tend to minimum PLAB size,
  // which is an okay reaction.
  size_t const used_for_waste_calculation = used() > _region_end_waste ? used() - _region_end_waste : 0;

  size_t const total_waste_allowed = used_for_waste_calculation * TargetPLABWastePct;
  size_t const cur_plab_sz = (size_t)((double)total_waste_allowed / G1LastPLABAverageOccupancy);
  return cur_plab_sz;
}

G1EvacStats::G1EvacStats(const char* description, size_t default_per_thread_plab_size, unsigned wt) :
  PLABStats(description),
  _default_plab_sz(default_per_thread_plab_size),
  _desired_net_plab_sz(default_per_thread_plab_size * ParallelGCThreads),
  _filter(wt),
  _region_end_waste(0),
  _regions_filled(0),
  _direct_allocated(0),
  _failure_used(0),
  _failure_waste(0) {
}

// Calculates plab size for current number of gc worker threads.
size_t G1EvacStats::desired_plab_sz(uint no_of_gc_workers) {
  if (!ResizePLAB) {
      return _default_plab_sz;
  }
  return align_object_size(clamp(_desired_net_plab_sz / no_of_gc_workers, min_size(), max_size()));
}

// Compute desired plab size for one gc worker thread and latch result for later
// use. This should be called once at the end of parallel
// scavenge; it clears the sensor accumulators.
void G1EvacStats::adjust_desired_plab_sz() {
  log_plab_allocation();

  if (!ResizePLAB) {
    // Clear accumulators for next round.
    reset();
    return;
  }

  assert(is_object_aligned(max_size()) && min_size() <= max_size(),
         "PLAB clipping computation may be incorrect");

  assert(_allocated != 0 || _unused == 0,
         "Inconsistency in PLAB stats: "
         "_allocated: " SIZE_FORMAT ", "
         "_wasted: " SIZE_FORMAT ", "
         "_unused: " SIZE_FORMAT ", "
         "_undo_wasted: " SIZE_FORMAT,
         _allocated, _wasted, _unused, _undo_wasted);

  size_t plab_sz = compute_desired_plab_sz();
  // Take historical weighted average
  _filter.sample(plab_sz);
  _desired_net_plab_sz = MAX2(min_size(), (size_t)_filter.average());

  log_sizing(plab_sz, _desired_net_plab_sz);
  // Clear accumulators for next round
  reset();
}
