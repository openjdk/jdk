/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1ConcurrentMarkRemarkTasks.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionPrinter.hpp"
#include "gc/g1/g1RemSetTrackingPolicy.hpp"
#include "logging/log.hpp"
#include "runtime/mutexLocker.hpp"

struct G1UpdateRegionLivenessAndSelectForRebuildTask::G1OnRegionClosure : public G1HeapRegionClosure {
  G1CollectedHeap* _g1h;
  G1ConcurrentMark* _cm;
  // The number of regions actually selected for rebuild.
  uint _num_selected_for_rebuild;

  size_t _freed_bytes;
  uint _num_old_regions_removed;
  uint _num_humongous_regions_removed;
  G1FreeRegionList* _local_cleanup_list;

  G1OnRegionClosure(G1CollectedHeap* g1h,
                    G1ConcurrentMark* cm,
                    G1FreeRegionList* local_cleanup_list) :
    _g1h(g1h),
    _cm(cm),
    _num_selected_for_rebuild(0),
    _freed_bytes(0),
    _num_old_regions_removed(0),
    _num_humongous_regions_removed(0),
    _local_cleanup_list(local_cleanup_list) {}

  void reclaim_empty_region_common(G1HeapRegion* hr) {
    assert(!hr->has_pinned_objects(), "precondition");
    assert(hr->used() > 0, "precondition");

    _freed_bytes += hr->used();
    hr->set_containing_set(nullptr);
    hr->clear_both_card_tables();
    _cm->clear_statistics(hr);
    G1HeapRegionPrinter::mark_reclaim(hr);
    _g1h->concurrent_refine()->notify_region_reclaimed(hr);
  }

  void reclaim_empty_humongous_region(G1HeapRegion* hr) {
    assert(hr->is_starts_humongous(), "precondition");

    auto on_humongous_region = [&] (G1HeapRegion* hr) {
      assert(hr->is_humongous(), "precondition");

      _num_humongous_regions_removed++;
      reclaim_empty_region_common(hr);
      _g1h->free_humongous_region(hr, _local_cleanup_list);
    };

    _g1h->humongous_obj_regions_iterate(hr, on_humongous_region);
  }

  void reclaim_empty_old_region(G1HeapRegion* hr) {
    assert(hr->is_old(), "precondition");

    _num_old_regions_removed++;
    reclaim_empty_region_common(hr);
    _g1h->free_region(hr, _local_cleanup_list);
  }

  bool do_heap_region(G1HeapRegion* hr) override {
    G1RemSetTrackingPolicy* tracker = _g1h->policy()->remset_tracker();
    if (hr->is_starts_humongous()) {
      // The liveness of this humongous obj decided by either its allocation
      // time (allocated after conc-mark-start, i.e. live) or conc-marking.
      const bool is_live = _cm->top_at_mark_start(hr) == hr->bottom()
                        || _cm->contains_live_object(hr->hrm_index())
                        || hr->has_pinned_objects();
      if (is_live) {
        const bool selected_for_rebuild = tracker->update_humongous_before_rebuild(hr);
        auto on_humongous_region = [&] (G1HeapRegion* hr) {
          if (selected_for_rebuild) {
            _num_selected_for_rebuild++;
          }
          _cm->update_top_at_rebuild_start(hr);
        };

        _g1h->humongous_obj_regions_iterate(hr, on_humongous_region);
      } else {
        reclaim_empty_humongous_region(hr);
      }
    } else if (hr->is_old()) {
      uint region_idx = hr->hrm_index();
      hr->note_end_of_marking(_cm->top_at_mark_start(hr), _cm->live_bytes(region_idx), _cm->incoming_refs(region_idx));

      const bool is_live = hr->live_bytes() != 0
                        || hr->has_pinned_objects();
      if (is_live) {
        const bool selected_for_rebuild = tracker->update_old_before_rebuild(hr);
        if (selected_for_rebuild) {
          _num_selected_for_rebuild++;
        }
        _cm->update_top_at_rebuild_start(hr);
      } else {
        reclaim_empty_old_region(hr);
      }
    }

    return false;
  }
};

G1UpdateRegionLivenessAndSelectForRebuildTask::G1UpdateRegionLivenessAndSelectForRebuildTask(G1CollectedHeap* g1h,
                                                                                             G1ConcurrentMark* cm,
                                                                                             uint num_workers) :
  WorkerTask("G1 Update Region Liveness and Select For Rebuild"),
  _g1h(g1h),
  _cm(cm),
  _hrclaimer(num_workers),
  _total_selected_for_rebuild(0),
  _cleanup_list("Empty Regions After Mark List") {}

G1UpdateRegionLivenessAndSelectForRebuildTask::~G1UpdateRegionLivenessAndSelectForRebuildTask() {
  if (!_cleanup_list.is_empty()) {
    log_debug(gc)("Reclaimed %u empty regions", _cleanup_list.length());
    // And actually make them available.
    _g1h->prepend_to_freelist(&_cleanup_list);
  }
}

void G1UpdateRegionLivenessAndSelectForRebuildTask::work(uint worker_id) {
  G1FreeRegionList local_cleanup_list("Local Cleanup List");
  G1OnRegionClosure on_region_cl(_g1h, _cm, &local_cleanup_list);
  _g1h->heap_region_par_iterate_from_worker_offset(&on_region_cl, &_hrclaimer, worker_id);

  _total_selected_for_rebuild.add_then_fetch(on_region_cl._num_selected_for_rebuild);

  // Update the old/humongous region sets
  _g1h->remove_from_old_gen_sets(on_region_cl._num_old_regions_removed,
                                 on_region_cl._num_humongous_regions_removed);

  {
    MutexLocker x(G1RareEvent_lock, Mutex::_no_safepoint_check_flag);
    _g1h->decrement_summary_bytes(on_region_cl._freed_bytes);

    _cleanup_list.add_ordered(&local_cleanup_list);
    assert(local_cleanup_list.is_empty(), "post-condition");
  }
}

uint G1UpdateRegionLivenessAndSelectForRebuildTask::desired_num_workers(uint num_regions) {
  const uint num_regions_per_worker = 384;
  return (num_regions + num_regions_per_worker - 1) / num_regions_per_worker;
}
