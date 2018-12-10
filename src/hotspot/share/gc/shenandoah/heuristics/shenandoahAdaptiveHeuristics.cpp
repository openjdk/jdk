/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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
  _conc_uprefs_duration_history(new TruncatedSeq(5)) {

  SHENANDOAH_ERGO_ENABLE_FLAG(ExplicitGCInvokesConcurrent);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahImplicitGCInvokesConcurrent);

  // Final configuration checks
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahSATBBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahReadBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahWriteBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahStoreValReadBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahKeepAliveBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCASBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahAcmpBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCloneBarrier);
}

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

  size_t capacity    = ShenandoahHeap::heap()->capacity();
  size_t free_target = ShenandoahMinFreeThreshold * capacity / 100;
  size_t min_garbage = free_target > actual_free ? (free_target - actual_free) : 0;
  size_t max_cset    = (size_t)(1.0 * ShenandoahEvacReserve * capacity / 100 / ShenandoahEvacWaste);

  log_info(gc, ergo)("Adaptive CSet Selection. Target Free: " SIZE_FORMAT "M, Actual Free: "
                     SIZE_FORMAT "M, Max CSet: " SIZE_FORMAT "M, Min Garbage: " SIZE_FORMAT "M",
                     free_target / M, actual_free / M, max_cset / M, min_garbage / M);

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

bool ShenandoahAdaptiveHeuristics::should_start_normal_gc() const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  size_t capacity = heap->capacity();
  size_t available = heap->free_set()->available();

  // Check if we are falling below the worst limit, time to trigger the GC, regardless of
  // anything else.
  size_t min_threshold = ShenandoahMinFreeThreshold * heap->capacity() / 100;
  if (available < min_threshold) {
    log_info(gc)("Trigger: Free (" SIZE_FORMAT "M) is below minimum threshold (" SIZE_FORMAT "M)",
                 available / M, min_threshold / M);
    return true;
  }

  // Check if are need to learn a bit about the application
  const size_t max_learn = ShenandoahLearningSteps;
  if (_gc_times_learned < max_learn) {
    size_t init_threshold = ShenandoahInitFreeThreshold * heap->capacity() / 100;
    if (available < init_threshold) {
      log_info(gc)("Trigger: Learning " SIZE_FORMAT " of " SIZE_FORMAT ". Free (" SIZE_FORMAT "M) is below initial threshold (" SIZE_FORMAT "M)",
                   _gc_times_learned + 1, max_learn, available / M, init_threshold / M);
      return true;
    }
  }

  // Check if allocation headroom is still okay. This also factors in:
  //   1. Some space to absorb allocation spikes
  //   2. Accumulated penalties from Degenerated and Full GC

  size_t allocation_headroom = available;

  size_t spike_headroom = ShenandoahAllocSpikeFactor * capacity / 100;
  size_t penalties      = _gc_time_penalties         * capacity / 100;

  allocation_headroom -= MIN2(allocation_headroom, spike_headroom);
  allocation_headroom -= MIN2(allocation_headroom, penalties);

  // TODO: Allocation rate is way too averaged to be useful during state changes

  double average_gc = _gc_time_history->avg();
  double time_since_last = time_since_last_gc();
  double allocation_rate = heap->bytes_allocated_since_gc_start() / time_since_last;

  if (average_gc > allocation_headroom / allocation_rate) {
    log_info(gc)("Trigger: Average GC time (%.2f ms) is above the time for allocation rate (%.2f MB/s) to deplete free headroom (" SIZE_FORMAT "M)",
                 average_gc * 1000, allocation_rate / M, allocation_headroom / M);
    log_info(gc, ergo)("Free headroom: " SIZE_FORMAT "M (free) - " SIZE_FORMAT "M (spike) - " SIZE_FORMAT "M (penalties) = " SIZE_FORMAT "M",
                       available / M, spike_headroom / M, penalties / M, allocation_headroom / M);
    return true;
  }

  return ShenandoahHeuristics::should_start_normal_gc();
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
