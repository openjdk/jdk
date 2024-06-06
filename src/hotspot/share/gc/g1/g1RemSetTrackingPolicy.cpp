/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSetChooser.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1RemSetTrackingPolicy.hpp"
#include "runtime/safepoint.hpp"

bool G1RemSetTrackingPolicy::needs_scan_for_rebuild(G1HeapRegion* r) const {
  // All non-free and non-young regions need to be scanned for references;
  // At every gc we gather references to other regions in young.
  // Free regions trivially do not need scanning because they do not contain live
  // objects.
  return !(r->is_young() || r->is_free());
}

void G1RemSetTrackingPolicy::update_at_allocate(G1HeapRegion* r) {
  assert(r->is_young() || r->is_humongous() || r->is_old(),
        "Region %u with unexpected heap region type %s", r->hrm_index(), r->get_type_str());
  if (r->is_old()) {
    // By default, do not create remembered set for new old regions.
    r->rem_set()->set_state_untracked();
    return;
  }
  // Always collect remembered set for young regions and for humongous regions.
  // Humongous regions need that for eager reclaim.
  r->rem_set()->set_state_complete();
}

void G1RemSetTrackingPolicy::update_at_free(G1HeapRegion* r) {
  /* nothing to do */
}

bool G1RemSetTrackingPolicy::update_humongous_before_rebuild(G1HeapRegion* r) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(r->is_starts_humongous(), "Region %u should be Humongous", r->hrm_index());

  assert(!r->rem_set()->is_updating(), "Remembered set of region %u is updating before rebuild", r->hrm_index());

  bool selected_for_rebuild = false;
  // Humongous regions containing type-array objs are remset-tracked to
  // support eager-reclaim. However, their remset state can be reset after
  // Full-GC. Try to re-enable remset-tracking for them if possible.
  if (cast_to_oop(r->bottom())->is_typeArray() && !r->rem_set()->is_tracked()) {
    auto on_humongous_region = [] (G1HeapRegion* r) {
      r->rem_set()->set_state_updating();
    };
    G1CollectedHeap::heap()->humongous_obj_regions_iterate(r, on_humongous_region);
    selected_for_rebuild = true;
  }

  return selected_for_rebuild;
}

bool G1RemSetTrackingPolicy::update_old_before_rebuild(G1HeapRegion* r) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(r->is_old(), "Region %u should be Old", r->hrm_index());

  assert(!r->rem_set()->is_updating(), "Remembered set of region %u is updating before rebuild", r->hrm_index());

  bool selected_for_rebuild = false;

  if (G1CollectionSetChooser::region_occupancy_low_enough_for_evac(r->live_bytes()) &&
      !r->rem_set()->is_tracked()) {
    r->rem_set()->set_state_updating();
    selected_for_rebuild = true;
  }

  return selected_for_rebuild;
}

void G1RemSetTrackingPolicy::update_after_rebuild(G1HeapRegion* r) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");

  if (r->is_old_or_humongous()) {
    if (r->rem_set()->is_updating()) {
      r->rem_set()->set_state_complete();
    }
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    // We can drop remembered sets of humongous regions that have a too large remembered set:
    // We will never try to eagerly reclaim or move them anyway until the next concurrent
    // cycle as e.g. remembered set entries will always be added.
    if (r->is_starts_humongous() && !g1h->is_potential_eager_reclaim_candidate(r)) {
      // Handle HC regions with the HS region.
      g1h->humongous_obj_regions_iterate(r,
                                         [&] (G1HeapRegion* r) {
                                           assert(!r->is_continues_humongous() || r->rem_set()->is_empty(),
                                                  "Continues humongous region %u remset should be empty", r->hrm_index());
                                           r->rem_set()->clear(true /* only_cardset */);
                                         });
    }
    G1ConcurrentMark* cm = G1CollectedHeap::heap()->concurrent_mark();
    log_trace(gc, remset, tracking)("After rebuild region %u "
                                    "(tams " PTR_FORMAT " "
                                    "liveness %zu "
                                    "remset occ %zu "
                                    "size %zu)",
                                    r->hrm_index(),
                                    p2i(cm->top_at_mark_start(r)),
                                    cm->live_bytes(r->hrm_index()),
                                    r->rem_set()->occupied(),
                                    r->rem_set()->mem_size());
  }
}
