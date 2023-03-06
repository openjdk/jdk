/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1FullCollector.inline.hpp"
#include "gc/g1/g1FullGCCompactionPoint.hpp"
#include "gc/g1/g1FullGCCompactTask.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/slidingForwarding.inline.hpp"
#include "logging/log.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/ticks.hpp"

void G1FullGCCompactTask::G1CompactRegionClosure::clear_in_bitmap(oop obj) {
  assert(_bitmap->is_marked(obj), "Should only compact marked objects");
  _bitmap->clear(obj);
}

size_t G1FullGCCompactTask::G1CompactRegionClosure::apply(oop obj) {
  size_t size = obj->size();
  if (obj->is_forwarded()) {
    HeapWord* destination = cast_from_oop<HeapWord*>(_forwarding->forwardee(obj));

    // copy object and reinit its mark
    HeapWord* obj_addr = cast_from_oop<HeapWord*>(obj);
    assert(obj_addr != destination, "everything in this pass should be moving");
    Copy::aligned_conjoint_words(obj_addr, destination, size);

    // There is no need to transform stack chunks - marking already did that.
    cast_to_oop(destination)->init_mark();
    assert(cast_to_oop(destination)->klass() != NULL, "should have a class");
  }

  // Clear the mark for the compacted object to allow reuse of the
  // bitmap without an additional clearing step.
  clear_in_bitmap(obj);
  return size;
}

void G1FullGCCompactTask::compact_region(HeapRegion* hr) {
  assert(!hr->is_pinned(), "Should be no pinned region in compaction queue");
  assert(!hr->is_humongous(), "Should be no humongous regions in compaction queue");

  if (!collector()->is_free(hr->hrm_index())) {
    // The compaction closure not only copies the object to the new
    // location, but also clears the bitmap for it. This is needed
    // for bitmap verification and to be able to use the bitmap
    // for evacuation failures in the next young collection. Testing
    // showed that it was better overall to clear bit by bit, compared
    // to clearing the whole region at the end. This difference was
    // clearly seen for regions with few marks.
    G1CompactRegionClosure compact(collector()->mark_bitmap());
    hr->apply_to_marked_objects(collector()->mark_bitmap(), &compact);
  }

  hr->reset_compacted_after_full_gc(_collector->compaction_top(hr));
}

void G1FullGCCompactTask::work(uint worker_id) {
  Ticks start = Ticks::now();
  GrowableArray<HeapRegion*>* compaction_queue = collector()->compaction_point(worker_id)->regions();
  for (GrowableArrayIterator<HeapRegion*> it = compaction_queue->begin();
       it != compaction_queue->end();
       ++it) {
    compact_region(*it);
  }
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
