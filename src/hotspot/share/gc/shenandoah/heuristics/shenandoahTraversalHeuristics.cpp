/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahTraversalHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahTraversalGC.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "utilities/quickSort.hpp"

ShenandoahTraversalHeuristics::ShenandoahTraversalHeuristics() : ShenandoahHeuristics(),
  _last_cset_select(0)
 {
  FLAG_SET_DEFAULT(ShenandoahSATBBarrier,            false);
  FLAG_SET_DEFAULT(ShenandoahStoreValEnqueueBarrier, true);
  FLAG_SET_DEFAULT(ShenandoahKeepAliveBarrier,       false);
  FLAG_SET_DEFAULT(ShenandoahAllowMixedAllocs,       false);

  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahRefProcFrequency, 1);

  // Adjust class unloading settings only if globally enabled.
  if (ClassUnloadingWithConcurrentMark) {
    SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahUnloadClassesFrequency, 1);
  }

  SHENANDOAH_ERGO_ENABLE_FLAG(ExplicitGCInvokesConcurrent);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahImplicitGCInvokesConcurrent);

  // Final configuration checks
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahLoadRefBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahStoreValEnqueueBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCASBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCloneBarrier);
}

bool ShenandoahTraversalHeuristics::should_start_normal_gc() const {
  return false;
}

bool ShenandoahTraversalHeuristics::is_experimental() {
  return true;
}

bool ShenandoahTraversalHeuristics::is_diagnostic() {
  return false;
}

bool ShenandoahTraversalHeuristics::can_do_traversal_gc() {
  return true;
}

const char* ShenandoahTraversalHeuristics::name() {
  return "traversal";
}

void ShenandoahTraversalHeuristics::choose_collection_set(ShenandoahCollectionSet* collection_set) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  ShenandoahTraversalGC* traversal_gc = heap->traversal_gc();

  ShenandoahHeapRegionSet* traversal_set = traversal_gc->traversal_set();
  traversal_set->clear();

  RegionData *data = get_region_data_cache(heap->num_regions());
  size_t cnt = 0;

  // Step 0. Prepare all regions

  for (size_t i = 0; i < heap->num_regions(); i++) {
    ShenandoahHeapRegion* r = heap->get_region(i);
    if (r->used() > 0) {
      if (r->is_regular()) {
        data[cnt]._region = r;
        data[cnt]._garbage = r->garbage();
        data[cnt]._seqnum_last_alloc = r->seqnum_last_alloc_mutator();
        cnt++;
      }
      traversal_set->add_region(r);
    }
  }

  // The logic for cset selection is similar to that of adaptive:
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
  //
  // The significant complication is that liveness data was collected at the previous cycle, and only
  // for those regions that were allocated before previous cycle started.

  size_t capacity    = heap->capacity();
  size_t actual_free = heap->free_set()->available();
  size_t free_target = ShenandoahMinFreeThreshold * capacity / 100;
  size_t min_garbage = free_target > actual_free ? (free_target - actual_free) : 0;
  size_t max_cset    = (size_t)(1.0 * ShenandoahEvacReserve * capacity / 100 / ShenandoahEvacWaste);

  log_info(gc, ergo)("Adaptive CSet Selection. Target Free: " SIZE_FORMAT "M, Actual Free: "
                     SIZE_FORMAT "M, Max CSet: " SIZE_FORMAT "M, Min Garbage: " SIZE_FORMAT "M",
                     free_target / M, actual_free / M, max_cset / M, min_garbage / M);

  // Better select garbage-first regions, and then older ones
  QuickSort::sort<RegionData>(data, (int) cnt, compare_by_garbage_then_alloc_seq_ascending, false);

  size_t cur_cset = 0;
  size_t cur_garbage = 0;

  size_t garbage_threshold = ShenandoahHeapRegion::region_size_bytes() / 100 * ShenandoahGarbageThreshold;

  // Step 1. Add trustworthy regions to collection set.
  //
  // We can trust live/garbage data from regions that were fully traversed during
  // previous cycle. Even if actual liveness is different now, we can only have _less_
  // live objects, because dead objects are not resurrected. Which means we can undershoot
  // the collection set, but not overshoot it.

  for (size_t i = 0; i < cnt; i++) {
    if (data[i]._seqnum_last_alloc > _last_cset_select) continue;

    ShenandoahHeapRegion* r = data[i]._region;
    assert (r->is_regular(), "should have been filtered before");

    size_t new_garbage = cur_garbage + r->garbage();
    size_t new_cset    = cur_cset    + r->get_live_data_bytes();

    if (new_cset > max_cset) {
      break;
    }

    if ((new_garbage < min_garbage) || (r->garbage() > garbage_threshold)) {
      assert(!collection_set->is_in(r), "must not yet be in cset");
      collection_set->add_region(r);
      cur_cset = new_cset;
      cur_garbage = new_garbage;
    }
  }

  // Step 2. Try to catch some recently allocated regions for evacuation ride.
  //
  // Pessimistically assume we are going to evacuate the entire region. While this
  // is very pessimistic and in most cases undershoots the collection set when regions
  // are mostly dead, it also provides more safety against running into allocation
  // failure when newly allocated regions are fully live.

  for (size_t i = 0; i < cnt; i++) {
    if (data[i]._seqnum_last_alloc <= _last_cset_select) continue;

    ShenandoahHeapRegion* r = data[i]._region;
    assert (r->is_regular(), "should have been filtered before");

    // size_t new_garbage = cur_garbage + 0; (implied)
    size_t new_cset = cur_cset + r->used();

    if (new_cset > max_cset) {
      break;
    }

    assert(!collection_set->is_in(r), "must not yet be in cset");
    collection_set->add_region(r);
    cur_cset = new_cset;
  }

  // Step 3. Clear liveness data
  // TODO: Merge it with step 0, but save live data in RegionData before.
  for (size_t i = 0; i < heap->num_regions(); i++) {
    ShenandoahHeapRegion* r = heap->get_region(i);
    if (r->used() > 0) {
      r->clear_live_data();
    }
  }

  collection_set->update_region_status();

  _last_cset_select = ShenandoahHeapRegion::seqnum_current_alloc();
}

bool ShenandoahTraversalHeuristics::should_start_traversal_gc() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(!heap->has_forwarded_objects(), "no forwarded objects here");

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

  double average_gc = _gc_time_history->avg();
  double time_since_last = time_since_last_gc();
  double allocation_rate = heap->bytes_allocated_since_gc_start() / time_since_last;

  if (average_gc > allocation_headroom / allocation_rate) {
    log_info(gc)("Trigger: Average GC time (%.2f ms) is above the time for allocation rate (%.2f MB/s) to deplete free headroom (" SIZE_FORMAT "M)",
                 average_gc * 1000, allocation_rate / M, allocation_headroom / M);
    log_info(gc, ergo)("Free headroom: " SIZE_FORMAT "M (free) - " SIZE_FORMAT "M (spike) - " SIZE_FORMAT "M (penalties) = " SIZE_FORMAT "M",
                       available / M, spike_headroom / M, penalties / M, allocation_headroom / M);
    return true;
  } else if (ShenandoahHeuristics::should_start_normal_gc()) {
    return true;
  }

  return false;
}

void ShenandoahTraversalHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* set,
                                                                          RegionData* data, size_t data_size,
                                                                          size_t free) {
  ShouldNotReachHere();
}
