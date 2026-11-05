/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CardSetGroup.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1RemSetTrackingPolicy.hpp"
#include "runtime/safepoint.hpp"

void G1RemSetTrackingPolicy::log_card_set_group_after_rebuild(G1CardSetGroup* gr) {
  Log(gc, remset, tracking) log;
  if (!log.is_trace()) {
    return;
  }

  size_t live_bytes = 0;
  for (G1CardSetGroupItem ci : *gr) {
    live_bytes += G1CollectedHeap::heap()->concurrent_mark()->live_bytes(ci._r->hrm_index());
  }

  log.trace("After rebuild group %u (liveness %zu remset occupancy %zu size %zu)",
            gr->group_id(),
            live_bytes,
            gr->cards_occupied(),
            gr->card_set()->mem_size());
}

bool G1RemSetTrackingPolicy::should_rebuild_humongous(G1HeapRegion* r) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(r->is_starts_humongous(), "Region %u should be Humongous", r->hrm_index());

  assert(!r->rem_set()->is_updating(), "Remembered set of region %u is updating before rebuild", r->hrm_index());

  // Humongous regions are remset-tracked to support eager-reclaim. However, their
  // remset state can be reset after Full-GC. Try to re-enable remset-tracking for
  // them if possible.
  return !r->rem_set()->is_tracked();
}

static bool region_occupancy_low_enough_for_evac(size_t live_bytes) {
  size_t mixed_gc_live_threshold_bytes = G1HeapRegion::GrainBytes * (size_t)G1MixedGCLiveThresholdPercent / 100;
  return live_bytes < mixed_gc_live_threshold_bytes;
}

bool G1RemSetTrackingPolicy::should_rebuild_old(G1HeapRegion* r) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(r->is_old(), "Region %u should be Old", r->hrm_index());

  assert(!r->rem_set()->is_updating(), "Remembered set of region %u is updating before rebuild", r->hrm_index());

  return
    (region_occupancy_low_enough_for_evac(r->live_bytes()) &&
    !G1CollectedHeap::heap()->is_old_gc_alloc_region(r) &&
    !r->rem_set()->is_tracked());
}

bool G1RemSetTrackingPolicy::update_humongous_after_rebuild(G1CardSetGroup* gr) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  precond(gr->region_at(0)->is_humongous());

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  // We can drop remembered sets of humongous regions that have a too large remembered set:
  // We will never try to eagerly reclaim or move them anyway until the next concurrent
  // cycle as e.g. remembered set entries will always be added.
  if (!g1h->is_potential_eager_reclaim_candidate(gr->region_at(0))) {
    return false;
  }

  if (!gr->is_complete()) {
    gr->set_complete();
  }

  log_card_set_group_after_rebuild(gr);

  return true;
}

void G1RemSetTrackingPolicy::update_old_after_rebuild(G1CardSetGroup* gr) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(gr->region_at(0)->is_old(), "only handles card set groups with old regions");
  precond(gr->is_updating());

  gr->set_complete();

  log_card_set_group_after_rebuild(gr);
}
