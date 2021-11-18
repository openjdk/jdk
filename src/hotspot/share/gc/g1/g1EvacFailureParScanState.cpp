/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Huawei and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1EvacFailureParScanState.hpp"

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1EvacFailureRegions.inline.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionManager.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "runtime/atomic.hpp"

class G1PreRemoveSelfForwardClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  uint _worker_id;

  G1EvacFailureRegions* _evac_failure_regions;

  G1EvacFailureParScanTasksQueue* _task_queue;

public:
  G1PreRemoveSelfForwardClosure(uint worker_id,
                                G1EvacFailureRegions* evac_failure_regions,
                                G1EvacFailureParScanTasksQueue* task_queue) :
    _g1h(G1CollectedHeap::heap()),
    _worker_id(worker_id),
    _evac_failure_regions(evac_failure_regions),
    _task_queue(task_queue) {
  }

  size_t prepare_evac_failure_objs(HeapRegion* hr) {
    return hr->prepare_evac_failure_objs(_task_queue);
  }

  bool do_heap_region(HeapRegion *hr) {
    assert(!hr->is_pinned(), "Unexpected pinned region at index %u", hr->hrm_index());
    assert(hr->in_collection_set(), "bad CS");

    if (_evac_failure_regions->contains(hr->hrm_index())) {
      hr->clear_index_in_opt_cset();

      bool during_concurrent_start = _g1h->collector_state()->in_concurrent_start_gc();
      bool during_concurrent_mark = _g1h->collector_state()->mark_or_rebuild_in_progress();

      hr->note_self_forwarding_removal_start(during_concurrent_start,
                                             during_concurrent_mark);
      _g1h->verifier()->check_bitmaps("Self-Forwarding Ptr Removal", hr);

      hr->reset_bot();

      size_t live_bytes = prepare_evac_failure_objs(hr);

      hr->rem_set()->clean_strong_code_roots(hr);
      hr->rem_set()->clear_locked(true);

      hr->note_self_forwarding_removal_end(live_bytes);
    }
    return false;
  }
};

class G1PostRemoveSelfForwardClosure: public HeapRegionClosure {

  bool do_heap_region(HeapRegion *hr) {
    hr->reset_evac_failure_objs();
    return false;
  }
};

class G1RemoveSelfForwardClosure: public ObjectClosure {
  G1CollectedHeap* _g1h;
  G1ConcurrentMark* _cm;
  HeapRegion* _hr;
  size_t _marked_words;
  bool _during_concurrent_start;
  uint _worker_id;
  HeapWord* _last_forwarded_object_end;

  // Fill the memory area from start to end with filler objects, and update the BOT
  // and the mark bitmap accordingly.
  void zap_dead_objects(HeapWord* start, HeapWord* end) {
    if (start == end) {
      return;
    }

    size_t gap_size = pointer_delta(end, start);
    MemRegion mr(start, gap_size);
    if (gap_size >= CollectedHeap::min_fill_size()) {
      CollectedHeap::fill_with_objects(start, gap_size);

      size_t dummy_size = cast_to_oop(start)->size();
      HeapWord* end_first_obj = start + dummy_size;
      _hr->update_bot_at(start, dummy_size, false);
      // Fill_with_objects() may have created multiple (i.e. two)
      // objects, as the max_fill_size() is half a region.
      // After updating the BOT for the first object, also update the
      // BOT for the second object to make the BOT complete.
      if (end_first_obj != end) {
        _hr->update_bot_at(end_first_obj, cast_to_oop(end_first_obj)->size(), false);
#ifdef ASSERT
        size_t size_second_obj = cast_to_oop(end_first_obj)->size();
        HeapWord* end_of_second_obj = end_first_obj + size_second_obj;
        assert(end == end_of_second_obj,
               "More than two objects were used to fill the area from " PTR_FORMAT " to " PTR_FORMAT ", "
                                                                                                     "second objects size " SIZE_FORMAT " ends at " PTR_FORMAT,
               p2i(start), p2i(end), size_second_obj, p2i(end_of_second_obj));
#endif
      }
    }
    _cm->par_clear_range_in_prev_bitmap(mr);
  }

  void zap_remainder() {
    zap_dead_objects(_last_forwarded_object_end, _hr->top());
  }

public:
  G1RemoveSelfForwardClosure(bool during_concurrent_start, uint worker_id) :
    _g1h(G1CollectedHeap::heap()),
    _cm(_g1h->concurrent_mark()),
    _marked_words(0),
    _during_concurrent_start(during_concurrent_start),
    _worker_id(worker_id),
    _last_forwarded_object_end(nullptr) { }

  void set_state(HeapRegion* region, G1EvacFailureParScanTask& task) {
    _hr = region;
    _last_forwarded_object_end = const_cast<HeapWord*>(task.previous_object_end());
  }

  // Iterate over the live objects in the region to find self-forwarded objects
  // that need to be kept live. We need to update the remembered sets of these
  // objects. Further update the BOT and marks.
  // We can coalesce and overwrite the remaining heap contents with dummy objects
  // as they have either been dead or evacuated (which are unreferenced now, i.e.
  // dead too) already.
  void do_object(oop obj) {

    HeapWord* obj_addr = cast_from_oop<HeapWord*>(obj);
    assert(_last_forwarded_object_end <= obj_addr, "should iterate in ascending address order");
    assert(_hr->is_in(obj_addr), "sanity");

    // The object failed to move.
    assert(obj->is_forwarded() && obj->forwardee() == obj, "sanity");

    zap_dead_objects(_last_forwarded_object_end, obj_addr);
    // We consider all objects that we find self-forwarded to be
    // live. What we'll do is that we'll update the prev marking
    // info so that they are all under PTAMS and explicitly marked.
    if (!_cm->is_marked_in_prev_bitmap(obj)) {
      _cm->par_mark_in_prev_bitmap(obj);
    }
    if (_during_concurrent_start) {
      // For the next marking info we'll only mark the
      // self-forwarded objects explicitly if we are during
      // concurrent start (since, normally, we only mark objects pointed
      // to by roots if we succeed in copying them). By marking all
      // self-forwarded objects we ensure that we mark any that are
      // still pointed to be roots. During concurrent marking, and
      // after concurrent start, we don't need to mark any objects
      // explicitly and all objects in the CSet are considered
      // (implicitly) live. So, we won't mark them explicitly and
      // we'll leave them over NTAMS.
      _cm->mark_in_next_bitmap(_worker_id, _hr, obj);
    }
    size_t obj_size = obj->size();

    _marked_words += obj_size;
    PreservedMarks::init_forwarded_mark(obj);

    HeapWord* obj_end = obj_addr + obj_size;
    _last_forwarded_object_end = obj_end;
    _hr->update_bot_at(obj_addr, obj_size, false);
  }

  void process_last() {
    zap_remainder();
    // As we have process the self forwardee in parallel,
    // it's necessary to update the bot threshold explicitly.
    _hr->update_bot_threshold();
  }
};

void G1EvacFailureParScanState::dispatch_task(G1EvacFailureParScanTask& task, G1RemoveSelfForwardClosure& closure) {
  DEBUG_ONLY(task.verify();)
  HeapRegion* region = G1CollectedHeap::heap()->region_at(task._region->hrm_index());
  closure.set_state(region, task);
  region->iterate_evac_failure_objs(&closure, task);
  if (task.last()) {
    closure.process_last();
  }
}

void G1EvacFailureParScanState::trim_queue_to_threshold(uint threshold, G1RemoveSelfForwardClosure& closure) {
  G1EvacFailureParScanTask task;
  do {
    while (_task_queue->pop_overflow(task)) {
      if (!_task_queue->try_push_to_taskqueue(task)) {
        dispatch_task(task, closure);
      }
    }
    while (_task_queue->pop_local(task, threshold)) {
      dispatch_task(task, closure);
    }
  } while (!_task_queue->overflow_empty());
}

void G1EvacFailureParScanState::trim_queue(G1RemoveSelfForwardClosure& closure) {
  trim_queue_to_threshold(0, closure);
  assert(_task_queue->overflow_empty(), "invariant");
  assert(_task_queue->taskqueue_empty(), "invariant");
}

void G1EvacFailureParScanState::steal_and_trim_queue(G1RemoveSelfForwardClosure& closure) {
  G1EvacFailureParScanTask stolen_task;
  while (_task_queues->steal(_worker_id, stolen_task)) {
    dispatch_task(stolen_task, closure);
    // Processing stolen task may have added tasks to our queue.
    trim_queue(closure);
  }
}

void G1EvacFailureParScanState::prev_scan() {
  assert(_worker_id < _task_queues->size(), "must be");
  G1PreRemoveSelfForwardClosure closure(_worker_id, _evac_failure_regions, _task_queues->queue(_worker_id));

  // Iterate through all regions that failed evacuation during the entire collection.
  _evac_failure_regions->par_iterate(&closure, _prev_claimer, _worker_id);
}

void G1EvacFailureParScanState::scan() {
  bool during_concurrent_start = G1CollectedHeap::heap()->collector_state()->in_concurrent_start_gc();
  G1RemoveSelfForwardClosure closure(during_concurrent_start, _worker_id);

  trim_queue(closure);
  do {
    steal_and_trim_queue(closure);
  } while (!offer_termination());
}

void G1EvacFailureParScanState::post_scan() {
  G1PostRemoveSelfForwardClosure closure;

  // Iterate through all regions that failed evacuation during the entire collection.
  _evac_failure_regions->par_iterate(&closure, _post_claimer, _worker_id);
}

G1EvacFailureParScanState::G1EvacFailureParScanState(G1EvacFailureRegions* evac_failure_regions,
                                                     G1EvacFailureParScanTasksQueueSet* queues,
                                                     TaskTerminator* terminator,
                                                     uint worker_id,
                                                     HeapRegionClaimer* claimer_1,
                                                     HeapRegionClaimer* claimer_2) :
  _evac_failure_regions(evac_failure_regions),
  _task_queues(queues),
  _worker_id(worker_id),
  _task_queue(queues->queue(_worker_id)),
  _terminator(terminator),
  _prev_claimer(claimer_1),
  _post_claimer(claimer_2) { }

void G1EvacFailureParScanState::do_void() {
  prev_scan();
  scan();
  post_scan();
}
