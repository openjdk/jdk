/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1ConcurrentMarkBitMap.inline.hpp"
#include "gc/g1/g1FullCollector.hpp"
#include "gc/g1/g1FullCollector.inline.hpp"
#include "gc/g1/g1FullGCCompactionPoint.hpp"
#include "gc/g1/g1FullGCCompactTask.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "logging/log.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/ticks.hpp"

// Do work for all skip-compacting regions.
class G1ResetSkipCompactingClosure : public HeapRegionClosure {
  G1FullCollector* _collector;

public:
  G1ResetSkipCompactingClosure(G1FullCollector* collector) : _collector(collector) { }

  bool do_heap_region(HeapRegion* r) {
    uint region_index = r->hrm_index();
    // Only for skip-compaction regions; early return otherwise.
    if (!_collector->is_skip_compacting(region_index)) {
      return false;
    }
#ifdef ASSERT
    if (r->is_humongous()) {
      oop obj = cast_to_oop(r->humongous_start_region()->bottom());
      assert(_collector->mark_bitmap()->is_marked(obj), "must be live");
    } else if (r->is_open_archive()) {
      bool is_empty = (_collector->live_words(r->hrm_index()) == 0);
      assert(!is_empty, "should contain at least one live obj");
    } else if (r->is_closed_archive()) {
      // should early-return above
      ShouldNotReachHere();
    } else {
      assert(_collector->live_words(region_index) > _collector->scope()->region_compaction_threshold(),
             "should be quite full");
    }
#endif
    r->reset_skip_compacting_after_full_gc();
    return false;
  }
};

void G1FullGCCompactTask::G1CompactRegionClosure::clear_in_prev_bitmap(oop obj) {
  assert(_bitmap->is_marked(obj), "Should only compact marked objects");
  _bitmap->clear(obj);
}

size_t G1FullGCCompactTask::G1CompactRegionClosure::apply(oop obj) {
  size_t size = obj->size();
  if (!obj->is_forwarded()) {
    // Object not moving, but clear the mark to allow reuse of the bitmap.
    clear_in_prev_bitmap(obj);
    return size;
  }

  HeapWord* destination = cast_from_oop<HeapWord*>(obj->forwardee());

  // copy object and reinit its mark
  HeapWord* obj_addr = cast_from_oop<HeapWord*>(obj);
  assert(obj_addr != destination, "everything in this pass should be moving");
  Copy::aligned_conjoint_words(obj_addr, destination, size);
  cast_to_oop(destination)->init_mark();
  assert(cast_to_oop(destination)->klass() != NULL, "should have a class");

  // Clear the mark for the compacted object to allow reuse of the
  // bitmap without an additional clearing step.
  clear_in_prev_bitmap(obj);
  return size;
}

void G1FullGCCompactTask::compact_region(HeapRegion* hr) {
  assert(!hr->is_pinned(), "Should be no pinned region in compaction queue");
  assert(!hr->is_humongous(), "Should be no humongous regions in compaction queue");

  if (!collector()->is_free(hr->hrm_index())) {
    // The compaction closure not only copies the object to the new
    // location, but also clears the bitmap for it. This is needed
    // for bitmap verification and to be able to use the prev_bitmap
    // for evacuation failures in the next young collection. Testing
    // showed that it was better overall to clear bit by bit, compared
    // to clearing the whole region at the end. This difference was
    // clearly seen for regions with few marks.
    G1CompactRegionClosure compact(collector()->mark_bitmap());
    hr->apply_to_marked_objects(collector()->mark_bitmap(), &compact);
  }

  hr->reset_compacted_after_full_gc();
}

void G1FullGCCompactTask::work(uint worker_id) {
  Ticks start = Ticks::now();
  GrowableArray<HeapRegion*>* compaction_queue = collector()->compaction_point(worker_id)->regions();
  for (GrowableArrayIterator<HeapRegion*> it = compaction_queue->begin();
       it != compaction_queue->end();
       ++it) {
    compact_region(*it);
  }

  G1ResetSkipCompactingClosure hc(collector());
  G1CollectedHeap::heap()->heap_region_par_iterate_from_worker_offset(&hc, &_claimer, worker_id);
  log_task("Compaction task", worker_id, start);
}

void G1FullGCCompactTask::serial_compaction() {
  GCTraceTime(Debug, gc, phases) tm("Phase 4: Serial Compaction", collector()->scope()->timer());
  GrowableArray<HeapRegion*>* compaction_queue = collector()->serial_compaction_point()->regions();
  for (GrowableArrayIterator<HeapRegion*> it = compaction_queue->begin();
       it != compaction_queue->end();
       ++it) {
    compact_region(*it);
  }
}
