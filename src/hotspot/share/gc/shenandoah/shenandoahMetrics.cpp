/*
 * Copyright (c) 2013, 2018, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahMetrics.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"

/*
 * Internal fragmentation metric: describes how fragmented the heap regions are.
 *
 * It is derived as:
 *
 *               sum(used[i]^2, i=0..k)
 *   IF = 1 - ------------------------------
 *              C * sum(used[i], i=0..k)
 *
 * ...where k is the number of regions in computation, C is the region capacity, and
 * used[i] is the used space in the region.
 *
 * The non-linearity causes IF to be lower for the cases where the same total heap
 * used is densely packed. For example:
 *   a) Heap is completely full  => IF = 0
 *   b) Heap is half full, first 50% regions are completely full => IF = 0
 *   c) Heap is half full, each region is 50% full => IF = 1/2
 *   d) Heap is quarter full, first 50% regions are completely full => IF = 0
 *   e) Heap is quarter full, each region is 25% full => IF = 3/4
 *   f) Heap has the small object per each region => IF =~ 1
 */
double ShenandoahMetrics::internal_fragmentation() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  double squared = 0;
  double linear = 0;
  int count = 0;
  for (size_t c = 0; c < heap->num_regions(); c++) {
    ShenandoahHeapRegion* r = heap->get_region(c);
    size_t used = r->used();
    squared += used * used;
    linear += used;
    count++;
  }

  if (count > 0) {
    double s = squared / (ShenandoahHeapRegion::region_size_bytes() * linear);
    return 1 - s;
  } else {
    return 0;
  }
}

/*
 * External fragmentation metric: describes how fragmented the heap is.
 *
 * It is derived as:
 *
 *   EF = 1 - largest_contiguous_free / total_free
 *
 * For example:
 *   a) Heap is completely empty => EF = 0
 *   b) Heap is completely full => EF = 1
 *   c) Heap is first-half full => EF = 1/2
 *   d) Heap is half full, full and empty regions interleave => EF =~ 1
 */
double ShenandoahMetrics::external_fragmentation() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  size_t last_idx = 0;
  size_t max_contig = 0;
  size_t empty_contig = 0;

  size_t free = 0;
  for (size_t c = 0; c < heap->num_regions(); c++) {
    ShenandoahHeapRegion* r = heap->get_region(c);

    if (r->is_empty() && (last_idx + 1 == c)) {
      empty_contig++;
    } else {
      empty_contig = 0;
    }

    free += r->free();
    max_contig = MAX2(max_contig, empty_contig);
    last_idx = c;
  }

  if (free > 0) {
    return 1 - (1.0 * max_contig * ShenandoahHeapRegion::region_size_bytes() / free);
  } else {
    return 1;
  }
}

ShenandoahMetricsSnapshot::ShenandoahMetricsSnapshot() {
  _heap = ShenandoahHeap::heap();
}

void ShenandoahMetricsSnapshot::snap_before() {
  _used_before = _heap->used();
  _if_before = ShenandoahMetrics::internal_fragmentation();
  _ef_before = ShenandoahMetrics::external_fragmentation();
}
void ShenandoahMetricsSnapshot::snap_after() {
  _used_after = _heap->used();
  _if_after = ShenandoahMetrics::internal_fragmentation();
  _ef_after = ShenandoahMetrics::external_fragmentation();
}

void ShenandoahMetricsSnapshot::print() {
  log_info(gc, ergo)("Used: before: " SIZE_FORMAT "M, after: " SIZE_FORMAT "M", _used_before/M, _used_after/M);
  log_info(gc, ergo)("Internal frag: before: %.1f%%, after: %.1f%%", _if_before * 100, _if_after * 100);
  log_info(gc, ergo)("External frag: before: %.1f%%, after: %.1f%%", _ef_before * 100, _ef_after * 100);
}

bool ShenandoahMetricsSnapshot::is_good_progress(const char *label) {
  // Under the critical threshold? Declare failure.
  size_t free_actual   = _heap->free_set()->available();
  size_t free_expected = _heap->max_capacity() / 100 * ShenandoahCriticalFreeThreshold;
  if (free_actual < free_expected) {
    log_info(gc, ergo)("Not enough free space (" SIZE_FORMAT "M, need " SIZE_FORMAT "M) after %s",
                       free_actual / M, free_expected / M, label);
    return false;
  }

  // Freed up enough? Good! Declare victory.
  size_t progress_actual   = (_used_before > _used_after) ? _used_before - _used_after : 0;
  size_t progress_expected = ShenandoahHeapRegion::region_size_bytes();
  if (progress_actual >= progress_expected) {
    return true;
  }
  log_info(gc,ergo)("Not enough progress (" SIZE_FORMAT "M, need " SIZE_FORMAT "M) after %s",
                    progress_actual / M, progress_expected / M, label);

  // Internal fragmentation is down? Good! Declare victory.
  double if_actual = _if_before - _if_after;
  double if_expected = 0.01; // 1% should be enough
  if (if_actual > if_expected) {
    return true;
  }
  log_info(gc,ergo)("Not enough internal fragmentation improvement (%.1f%%, need %.1f%%) after %s",
                    if_actual * 100, if_expected * 100, label);

  // External fragmentation is down? Good! Declare victory.
  double ef_actual = _ef_before - _ef_after;
  double ef_expected = 0.01; // 1% should be enough
  if (ef_actual > ef_expected) {
    return true;
  }
  log_info(gc,ergo)("Not enough external fragmentation improvement (%.1f%%, need %.1f%%) after %s",
                    if_actual * 100, if_expected * 100, label);

  // Nothing good had happened.
  return false;
}
