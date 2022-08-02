/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FULLGCPREPARETASK_INLINE_HPP
#define SHARE_GC_G1_G1FULLGCPREPARETASK_INLINE_HPP

#include "gc/g1/g1FullGCPrepareTask.hpp"

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1FullCollector.hpp"
#include "gc/g1/g1FullGCCompactionPoint.hpp"
#include "gc/g1/g1FullGCScope.hpp"
#include "gc/g1/heapRegion.inline.hpp"

template<bool is_humongous>
void G1DetermineCompactionQueueClosure::free_pinned_region(HeapRegion* hr) {
  if (is_humongous) {
    _g1h->free_humongous_region(hr, nullptr);
  } else {
    _g1h->free_region(hr, nullptr);
  }
  _collector->set_free(hr->hrm_index());
  add_to_compaction_queue(hr);
}

inline bool G1DetermineCompactionQueueClosure::should_compact(HeapRegion* hr) const {
  // There is no need to iterate and forward objects in pinned regions ie.
  // prepare them for compaction.
  if (hr->is_pinned()) {
    return false;
  }
  size_t live_words = _collector->live_words(hr->hrm_index());
  size_t live_words_threshold = _collector->scope()->region_compaction_threshold();
  // High live ratio region will not be compacted.
  return live_words <= live_words_threshold;
}

inline uint G1DetermineCompactionQueueClosure::next_worker() {
  uint result = _cur_worker;
  _cur_worker = (_cur_worker + 1) % _collector->workers();
  return result;
}

inline G1FullGCCompactionPoint* G1DetermineCompactionQueueClosure::next_compaction_point() {
  return _collector->compaction_point(next_worker());
}

inline void G1DetermineCompactionQueueClosure::add_to_compaction_queue(HeapRegion* hr) {
  hr->set_compaction_top(hr->bottom());
  G1FullGCCompactionPoint* cp = next_compaction_point();
  if (!cp->is_initialized()) {
    cp->initialize(hr);
  }
  // Add region to the compaction queue.
  cp->add(hr);
}

inline bool G1DetermineCompactionQueueClosure::do_heap_region(HeapRegion* hr) {
  if (should_compact(hr)) {
    assert(!hr->is_humongous(), "moving humongous objects not supported.");
    add_to_compaction_queue(hr);
  } else {
    assert(hr->containing_set() == nullptr, "already cleared by PrepareRegionsClosure");
    if (hr->is_humongous()) {
      oop obj = cast_to_oop(hr->humongous_start_region()->bottom());
      bool is_empty = !_collector->mark_bitmap()->is_marked(obj);
      if (is_empty) {
        free_pinned_region<true>(hr);
      }
    } else if (hr->is_open_archive()) {
      bool is_empty = _collector->live_words(hr->hrm_index()) == 0;
      if (is_empty) {
        free_pinned_region<false>(hr);
      }
    } else if (hr->is_closed_archive()) {
      // nothing to do with closed archive region
    } else {
      assert(MarkSweepDeadRatio > 0,
             "only skip compaction for other regions when MarkSweepDeadRatio > 0");

      // Too many live objects in the region; skip compacting it.
      _collector->update_from_compacting_to_skip_compacting(hr->hrm_index());
      log_trace(gc, phases)("Phase 2: skip compaction region index: %u, live words: " SIZE_FORMAT,
                            hr->hrm_index(), _collector->live_words(hr->hrm_index()));
    }
  }

  return false;
}

inline size_t G1SerialRePrepareClosure::apply(oop obj) {
  // We only re-prepare objects forwarded within the current region, so
  // skip objects that are already forwarded to another region.
  if (obj->is_forwarded() && !_current->is_in(obj->forwardee())) {
    return obj->size();
  }

  // Get size and forward.
  size_t size = obj->size();
  _cp->forward(obj, size);

  return size;
}

#endif // SHARE_GC_G1_G1FULLGCPREPARETASK_INLINE_HPP
