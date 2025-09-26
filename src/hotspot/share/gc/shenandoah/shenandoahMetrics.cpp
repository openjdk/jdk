/*
 * Copyright (c) 2013, 2019, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMetrics.hpp"

ShenandoahMetricsSnapshot::ShenandoahMetricsSnapshot(ShenandoahFreeSet* free_set)
  : _free_set(free_set)
  , _used_before(free_set->used())
  , _if_before(free_set->internal_fragmentation())
  , _ef_before(free_set->external_fragmentation()) {
}

bool ShenandoahMetricsSnapshot::is_good_progress() const {
  // Under the critical threshold?
  const size_t free_actual = _free_set->available();
  assert(free_actual != ShenandoahFreeSet::FreeSetUnderConstruction, "Avoid this race");

  // ShenandoahCriticalFreeThreshold is expressed as a percentage.  We multiply this percentage by 1/100th
  // of the soft max capacity to determine whether the available memory within the mutator partition of the
  // freeset exceeds the critical threshold.
  const size_t free_expected = (ShenandoahHeap::heap()->soft_max_capacity() / 100) * ShenandoahCriticalFreeThreshold;
  const bool prog_free = free_actual >= free_expected;
  log_info(gc, ergo)("%s progress for free space: " PROPERFMT ", need " PROPERFMT,
                     prog_free ? "Good" : "Bad", PROPERFMTARGS(free_actual), PROPERFMTARGS(free_expected));
  if (!prog_free) {
    return false;
  }

  // Freed up enough?
  const size_t used_after = _free_set->used();
  const size_t progress_actual   = (_used_before > used_after) ? _used_before - used_after : 0;
  const size_t progress_expected = ShenandoahHeapRegion::region_size_bytes();
  const bool prog_used = progress_actual >= progress_expected;
  log_info(gc, ergo)("%s progress for used space: " PROPERFMT ", need " PROPERFMT,
                     prog_used ? "Good" : "Bad", PROPERFMTARGS(progress_actual), PROPERFMTARGS(progress_expected));
  if (prog_used) {
    return true;
  }

  // Internal fragmentation is down?
  const double if_after = _free_set->internal_fragmentation();
  const double if_actual = _if_before - if_after;
  const double if_expected = 0.01; // 1% should be enough
  const bool prog_if = if_actual >= if_expected;
  log_info(gc, ergo)("%s progress for internal fragmentation: %.1f%%, need %.1f%%",
                     prog_if ? "Good" : "Bad",
                     if_actual * 100, if_expected * 100);
  if (prog_if) {
    return true;
  }

  // External fragmentation is down?
  const double ef_after = _free_set->external_fragmentation();
  const double ef_actual = _ef_before - ef_after;
  const double ef_expected = 0.01; // 1% should be enough
  const bool prog_ef = ef_actual >= ef_expected;
  log_info(gc, ergo)("%s progress for external fragmentation: %.1f%%, need %.1f%%",
                     prog_ef ? "Good" : "Bad",
                     ef_actual * 100, ef_expected * 100);
  if (prog_ef) {
    return true;
  }

  // Nothing good had happened.
  return false;
}
