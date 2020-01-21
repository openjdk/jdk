/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahAdaptiveHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "utilities/quickSort.hpp"

ShenandoahAdaptiveHeuristics::ShenandoahAdaptiveHeuristics() :
  ShenandoahHeuristics(),
  _cycle_gap_history(new TruncatedSeq(5)),
  _conc_mark_duration_history(new TruncatedSeq(5)),
  _conc_uprefs_duration_history(new TruncatedSeq(5)) {}

ShenandoahAdaptiveHeuristics::~ShenandoahAdaptiveHeuristics() {}

void ShenandoahAdaptiveHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                         RegionData* data, size_t size,
                                                                         size_t actual_free) {
  size_t garbage_threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahGarbageThreshold / 100;

  // The logic for cset selection in adaptive is as follows:
  //
  //   1. We cannot get cset larger than available free space. Otherwise we guarantee OOME
  //      during evacuation, and thus guarantee full GC. In practice, we also want to let
  //      application to allocate something. This is why we limit CSet to some fraction of
  //      available space. In non-overloaded heap, max_cset would contain all plausible candidates
  //      over garbage threshold.
  //
  //   2. We should not get cset too low so that free threshold would not be met right
  //      after the cycle. Otherwise we get back-to-back cycles for no reason if heap is
  //      too fragmented. In non-overloaded non-fragmented heap min_garbage would be around zero.
  //
  // Therefore, we start by sorting the regions by garbage. Then we unconditionally add the best candidates
  // before we meet min_garbage. Then we add all candidates that fit with a garbage threshold before
  // we hit max_cset. When max_cset is hit, we terminate the cset selection. Note that in this scheme,
  // ShenandoahGarbageThreshold is the soft threshold which would be ignored until min_garbage is hit.

  size_t capacity    = ShenandoahHeap::heap()->max_capacity();
  size_t free_target = capacity / 100 * ShenandoahMinFreeThreshold;
  size_t min_garbage = free_target > actual_free ? (free_target - actual_free) : 0;
  size_t max_cset    = (size_t)((1.0 * capacity / 100 * ShenandoahEvacReserve) / ShenandoahEvacWaste);

  log_info(gc, ergo)("Adaptive CSet Selection. Target Free: " SIZE_FORMAT "%s, Actual Free: "
                     SIZE_FORMAT "%s, Max CSet: " SIZE_FORMAT "%s, Min Garbage: " SIZE_FORMAT "%s",
                     byte_size_in_proper_unit(free_target), proper_unit_for_byte_size(free_target),
                     byte_size_in_proper_unit(actual_free), proper_unit_for_byte_size(actual_free),
                     byte_size_in_proper_unit(max_cset),    proper_unit_for_byte_size(max_cset),
                     byte_size_in_proper_unit(min_garbage), proper_unit_for_byte_size(min_garbage));

  // Better select garbage-first regions
  QuickSort::sort<RegionData>(data, (int)size, compare_by_garbage, false);

  size_t cur_cset = 0;
  size_t cur_garbage = 0;
  _bytes_in_cset = 0;

  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx]._region;

    size_t new_cset    = cur_cset + r->get_live_data_bytes();
    size_t new_garbage = cur_garbage + r->garbage();

    if (new_cset > max_cset) {
      break;
    }

    if ((new_garbage < min_garbage) || (r->garbage() > garbage_threshold)) {
      cset->add_region(r);
      _bytes_in_cset += r->used();
      cur_cset = new_cset;
      cur_garbage = new_garbage;
    }
  }
}

void ShenandoahAdaptiveHeuristics::record_cycle_start() {
  ShenandoahHeuristics::record_cycle_start();
  double last_cycle_gap = (_cycle_start - _last_cycle_end);
  _cycle_gap_history->add(last_cycle_gap);
}

void ShenandoahAdaptiveHeuristics::record_phase_time(ShenandoahPhaseTimings::Phase phase, double secs) {
  if (phase == ShenandoahPhaseTimings::conc_mark) {
    _conc_mark_duration_history->add(secs);
  } else if (phase == ShenandoahPhaseTimings::conc_update_refs) {
    _conc_uprefs_duration_history->add(secs);
  } // Else ignore
}

bool ShenandoahAdaptiveHeuristics::should_start_gc() const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  size_t capacity = heap->max_capacity();
  size_t available = heap->free_set()->available();

  // Check if we are falling below the worst limit, time to trigger the GC, regardless of
  // anything else.
  size_t min_threshold = capacity / 100 * ShenandoahMinFreeThreshold;
  if (available < min_threshold) {
    log_info(gc)("Trigger: Free (" SIZE_FORMAT "%s) is below minimum threshold (" SIZE_FORMAT "%s)",
                 byte_size_in_proper_unit(available),     proper_unit_for_byte_size(available),
                 byte_size_in_proper_unit(min_threshold), proper_unit_for_byte_size(min_threshold));
    return true;
  }

  // Check if are need to learn a bit about the application
  const size_t max_learn = ShenandoahLearningSteps;
  if (_gc_times_learned < max_learn) {
    size_t init_threshold = capacity / 100 * ShenandoahInitFreeThreshold;
    if (available < init_threshold) {
      log_info(gc)("Trigger: Learning " SIZE_FORMAT " of " SIZE_FORMAT ". Free (" SIZE_FORMAT "%s) is below initial threshold (" SIZE_FORMAT "%s)",
                   _gc_times_learned + 1, max_learn,
                   byte_size_in_proper_unit(available),      proper_unit_for_byte_size(available),
                   byte_size_in_proper_unit(init_threshold), proper_unit_for_byte_size(init_threshold));
      return true;
    }
  }

  // Check if allocation headroom is still okay. This also factors in:
  //   1. Some space to absorb allocation spikes
  //   2. Accumulated penalties from Degenerated and Full GC

  size_t allocation_headroom = available;

  size_t spike_headroom = capacity / 100 * ShenandoahAllocSpikeFactor;
  size_t penalties      = capacity / 100 * _gc_time_penalties;

  allocation_headroom -= MIN2(allocation_headroom, spike_headroom);
  allocation_headroom -= MIN2(allocation_headroom, penalties);

  // TODO: Allocation rate is way too averaged to be useful during state changes

  double average_gc = _gc_time_history->avg();
  double time_since_last = time_since_last_gc();
  double allocation_rate = heap->bytes_allocated_since_gc_start() / time_since_last;

  if (average_gc > allocation_headroom / allocation_rate) {
    log_info(gc)("Trigger: Average GC time (%.2f ms) is above the time for allocation rate (%.0f %sB/s) to deplete free headroom (" SIZE_FORMAT "%s)",
                 average_gc * 1000,
                 byte_size_in_proper_unit(allocation_rate),     proper_unit_for_byte_size(allocation_rate),
                 byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom));
    log_info(gc, ergo)("Free headroom: " SIZE_FORMAT "%s (free) - " SIZE_FORMAT "%s (spike) - " SIZE_FORMAT "%s (penalties) = " SIZE_FORMAT "%s",
                 byte_size_in_proper_unit(available),           proper_unit_for_byte_size(available),
                 byte_size_in_proper_unit(spike_headroom),      proper_unit_for_byte_size(spike_headroom),
                 byte_size_in_proper_unit(penalties),           proper_unit_for_byte_size(penalties),
                 byte_size_in_proper_unit(allocation_headroom), proper_unit_for_byte_size(allocation_headroom));
    return true;
  }

  return ShenandoahHeuristics::should_start_gc();
}

bool ShenandoahAdaptiveHeuristics::should_start_update_refs() {
  if (! _update_refs_adaptive) {
    return _update_refs_early;
  }

  double cycle_gap_avg = _cycle_gap_history->avg();
  double conc_mark_avg = _conc_mark_duration_history->avg();
  double conc_uprefs_avg = _conc_uprefs_duration_history->avg();

  if (_update_refs_early) {
    double threshold = ShenandoahMergeUpdateRefsMinGap / 100.0;
    if (conc_mark_avg + conc_uprefs_avg > cycle_gap_avg * threshold) {
      _update_refs_early = false;
    }
  } else {
    double threshold = ShenandoahMergeUpdateRefsMaxGap / 100.0;
    if (conc_mark_avg + conc_uprefs_avg < cycle_gap_avg * threshold) {
      _update_refs_early = true;
    }
  }
  return _update_refs_early;
}

const char* ShenandoahAdaptiveHeuristics::name() {
  return "adaptive";
}

bool ShenandoahAdaptiveHeuristics::is_diagnostic() {
  return false;
}

bool ShenandoahAdaptiveHeuristics::is_experimental() {
  return false;
}
