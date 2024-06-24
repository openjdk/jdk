/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1FullCollector.inline.hpp"
#include "gc/g1/g1FullGCResetMetadataTask.hpp"
#include "utilities/ticks.hpp"

G1FullGCResetMetadataTask::G1ResetMetadataClosure::G1ResetMetadataClosure(G1FullCollector* collector) :
  _g1h(G1CollectedHeap::heap()),
  _collector(collector) { }

void G1FullGCResetMetadataTask::G1ResetMetadataClosure::reset_region_metadata(G1HeapRegion* hr) {
  hr->rem_set()->clear();
  hr->clear_cardtable();
}

bool G1FullGCResetMetadataTask::G1ResetMetadataClosure::do_heap_region(G1HeapRegion* hr) {
  uint const region_idx = hr->hrm_index();
  if (!_collector->is_compaction_target(region_idx)) {
    assert(!hr->is_free(), "all free regions should be compaction targets");
    assert(_collector->is_skip_compacting(region_idx), "must be");
    if (hr->needs_scrubbing_during_full_gc()) {
      scrub_skip_compacting_region(hr, hr->is_young());
    }
    if (_collector->is_skip_compacting(region_idx)) {
      reset_skip_compacting(hr);
    }
  }
  // Reset data structures not valid after Full GC.
  reset_region_metadata(hr);

  return false;
}

void G1FullGCResetMetadataTask::G1ResetMetadataClosure::scrub_skip_compacting_region(G1HeapRegion* hr, bool update_bot_for_live) {
  assert(hr->needs_scrubbing_during_full_gc(), "must be");

  HeapWord* limit = hr->top();
  HeapWord* current_obj = hr->bottom();
  G1CMBitMap* bitmap = _collector->mark_bitmap();

  while (current_obj < limit) {
    if (bitmap->is_marked(current_obj)) {
      oop current = cast_to_oop(current_obj);
      size_t size = current->size();
      if (update_bot_for_live) {
        hr->update_bot_for_block(current_obj, current_obj + size);
      }
      current_obj += size;
      continue;
    }
    // Found dead object, which is potentially unloaded, scrub to next
    // marked object.
    HeapWord* scrub_start = current_obj;
    HeapWord* scrub_end = bitmap->get_next_marked_addr(scrub_start, limit);
    assert(scrub_start != scrub_end, "must advance");
    hr->fill_range_with_dead_objects(scrub_start, scrub_end);

    current_obj = scrub_end;
  }
}

void G1FullGCResetMetadataTask::G1ResetMetadataClosure::reset_skip_compacting(G1HeapRegion* hr) {
#ifdef ASSERT
  uint region_index = hr->hrm_index();
  assert(_collector->is_skip_compacting(region_index), "Only call on is_skip_compacting regions");

  if (hr->is_humongous()) {
    oop obj = cast_to_oop(hr->humongous_start_region()->bottom());
    assert(hr->humongous_start_region()->has_pinned_objects() ||
           _collector->mark_bitmap()->is_marked(obj), "must be live");
  } else {
    assert(hr->has_pinned_objects() || _collector->live_words(region_index) > _collector->scope()->region_compaction_threshold(),
           "should be quite full or pinned %u", region_index);
  }

  assert(_collector->compaction_top(hr) == nullptr,
         "region %u compaction_top " PTR_FORMAT " must not be different from bottom " PTR_FORMAT,
         hr->hrm_index(), p2i(_collector->compaction_top(hr)), p2i(hr->bottom()));
#endif
  hr->reset_skip_compacting_after_full_gc();
}

void G1FullGCResetMetadataTask::work(uint worker_id) {
  Ticks start = Ticks::now();
  G1ResetMetadataClosure hc(collector());
  G1CollectedHeap::heap()->heap_region_par_iterate_from_worker_offset(&hc, &_claimer, worker_id);

  log_task("Reset Metadata task", worker_id, start);
}
