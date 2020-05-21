/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1PARSCANTHREADSTATE_INLINE_HPP
#define SHARE_GC_G1_G1PARSCANTHREADSTATE_INLINE_HPP

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1OopStarChunkedList.inline.hpp"
#include "gc/g1/g1ParScanThreadState.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"

template <class T> void G1ParScanThreadState::do_oop_evac(T* p) {
  // Reference should not be NULL here as such are never pushed to the task queue.
  oop obj = RawAccess<IS_NOT_NULL>::oop_load(p);

  // Although we never intentionally push references outside of the collection
  // set, due to (benign) races in the claim mechanism during RSet scanning more
  // than one thread might claim the same card. So the same card may be
  // processed multiple times, and so we might get references into old gen here.
  // So we need to redo this check.
  const G1HeapRegionAttr region_attr = _g1h->region_attr(obj);
  // References pushed onto the work stack should never point to a humongous region
  // as they are not added to the collection set due to above precondition.
  assert(!region_attr.is_humongous(),
         "Obj " PTR_FORMAT " should not refer to humongous region %u from " PTR_FORMAT,
         p2i(obj), _g1h->addr_to_region(cast_from_oop<HeapWord*>(obj)), p2i(p));

  if (!region_attr.is_in_cset()) {
    // In this case somebody else already did all the work.
    return;
  }

  markWord m = obj->mark_raw();
  if (m.is_marked()) {
    obj = (oop) m.decode_pointer();
  } else {
    obj = copy_to_survivor_space(region_attr, obj, m);
  }
  RawAccess<IS_NOT_NULL>::oop_store(p, obj);

  assert(obj != NULL, "Must be");
  if (HeapRegion::is_in_same_region(p, obj)) {
    return;
  }
  HeapRegion* from = _g1h->heap_region_containing(p);
  if (!from->is_young()) {
    enqueue_card_if_tracked(_g1h->region_attr(obj), p, obj);
  }
}

inline void G1ParScanThreadState::push_on_queue(ScannerTask task) {
  verify_task(task);
  _task_queue->push(task);
}

inline void G1ParScanThreadState::do_partial_array(PartialArrayScanTask task) {
  oop from_obj = task.to_source_array();

  assert(_g1h->is_in_reserved(from_obj), "must be in heap.");
  assert(from_obj->is_objArray(), "must be obj array");
  objArrayOop from_obj_array = objArrayOop(from_obj);
  // The from-space object contains the real length.
  int length                 = from_obj_array->length();

  assert(from_obj->is_forwarded(), "must be forwarded");
  oop to_obj                 = from_obj->forwardee();
  assert(from_obj != to_obj, "should not be chunking self-forwarded objects");
  objArrayOop to_obj_array   = objArrayOop(to_obj);
  // We keep track of the next start index in the length field of the
  // to-space object.
  int next_index             = to_obj_array->length();
  assert(0 <= next_index && next_index < length,
         "invariant, next index: %d, length: %d", next_index, length);

  int start                  = next_index;
  int end                    = length;
  int remainder              = end - start;
  // We'll try not to push a range that's smaller than ParGCArrayScanChunk.
  if (remainder > 2 * ParGCArrayScanChunk) {
    end = start + ParGCArrayScanChunk;
    to_obj_array->set_length(end);
    // Push the remainder before we process the range in case another
    // worker has run out of things to do and can steal it.
    push_on_queue(ScannerTask(PartialArrayScanTask(from_obj)));
  } else {
    assert(length == end, "sanity");
    // We'll process the final range for this object. Restore the length
    // so that the heap remains parsable in case of evacuation failure.
    to_obj_array->set_length(end);
  }

  HeapRegion* hr = _g1h->heap_region_containing(to_obj);
  G1ScanInYoungSetter x(&_scanner, hr->is_young());
  // Process indexes [start,end). It will also process the header
  // along with the first chunk (i.e., the chunk with start == 0).
  // Note that at this point the length field of to_obj_array is not
  // correct given that we are using it to keep track of the next
  // start index. oop_iterate_range() (thankfully!) ignores the length
  // field and only relies on the start / end parameters.  It does
  // however return the size of the object which will be incorrect. So
  // we have to ignore it even if we wanted to use it.
  to_obj_array->oop_iterate_range(&_scanner, start, end);
}

inline void G1ParScanThreadState::dispatch_task(ScannerTask task) {
  verify_task(task);
  if (task.is_narrow_oop_ptr()) {
    do_oop_evac(task.to_narrow_oop_ptr());
  } else if (task.is_oop_ptr()) {
    do_oop_evac(task.to_oop_ptr());
  } else {
    do_partial_array(task.to_partial_array_task());
  }
}

void G1ParScanThreadState::steal_and_trim_queue(G1ScannerTasksQueueSet *task_queues) {
  ScannerTask stolen_task;
  while (task_queues->steal(_worker_id, stolen_task)) {
    dispatch_task(stolen_task);

    // We've just processed a task and we might have made
    // available new entries on the queues. So we have to make sure
    // we drain the queues as necessary.
    trim_queue();
  }
}

inline bool G1ParScanThreadState::needs_partial_trimming() const {
  return !_task_queue->overflow_empty() ||
         (_task_queue->size() > _stack_trim_upper_threshold);
}

inline bool G1ParScanThreadState::is_partially_trimmed() const {
  return _task_queue->overflow_empty() &&
         (_task_queue->size() <= _stack_trim_lower_threshold);
}

inline void G1ParScanThreadState::trim_queue_to_threshold(uint threshold) {
  ScannerTask task;
  // Drain the overflow stack first, so other threads can potentially steal.
  while (_task_queue->pop_overflow(task)) {
    if (!_task_queue->try_push_to_taskqueue(task)) {
      dispatch_task(task);
    }
  }

  while (_task_queue->pop_local(task, threshold)) {
    dispatch_task(task);
  }
}

inline void G1ParScanThreadState::trim_queue_partially() {
  if (!needs_partial_trimming()) {
    return;
  }

  const Ticks start = Ticks::now();
  do {
    trim_queue_to_threshold(_stack_trim_lower_threshold);
  } while (!is_partially_trimmed());
  _trim_ticks += Ticks::now() - start;
}

inline Tickspan G1ParScanThreadState::trim_ticks() const {
  return _trim_ticks;
}

inline void G1ParScanThreadState::reset_trim_ticks() {
  _trim_ticks = Tickspan();
}

template <typename T>
inline void G1ParScanThreadState::remember_root_into_optional_region(T* p) {
  oop o = RawAccess<IS_NOT_NULL>::oop_load(p);
  uint index = _g1h->heap_region_containing(o)->index_in_opt_cset();
  assert(index < _num_optional_regions,
         "Trying to access optional region idx %u beyond " SIZE_FORMAT, index, _num_optional_regions);
  _oops_into_optional_regions[index].push_root(p);
}

template <typename T>
inline void G1ParScanThreadState::remember_reference_into_optional_region(T* p) {
  oop o = RawAccess<IS_NOT_NULL>::oop_load(p);
  uint index = _g1h->heap_region_containing(o)->index_in_opt_cset();
  assert(index < _num_optional_regions,
         "Trying to access optional region idx %u beyond " SIZE_FORMAT, index, _num_optional_regions);
  _oops_into_optional_regions[index].push_oop(p);
  verify_task(p);
}

G1OopStarChunkedList* G1ParScanThreadState::oops_into_optional_region(const HeapRegion* hr) {
  assert(hr->index_in_opt_cset() < _num_optional_regions,
         "Trying to access optional region idx %u beyond " SIZE_FORMAT " " HR_FORMAT,
         hr->index_in_opt_cset(), _num_optional_regions, HR_FORMAT_PARAMS(hr));
  return &_oops_into_optional_regions[hr->index_in_opt_cset()];
}

void G1ParScanThreadState::initialize_numa_stats() {
  if (_numa->is_enabled()) {
    LogTarget(Info, gc, heap, numa) lt;

    if (lt.is_enabled()) {
      uint num_nodes = _numa->num_active_nodes();
      // Record only if there are multiple active nodes.
      _obj_alloc_stat = NEW_C_HEAP_ARRAY(size_t, num_nodes, mtGC);
      memset(_obj_alloc_stat, 0, sizeof(size_t) * num_nodes);
    }
  }
}

void G1ParScanThreadState::flush_numa_stats() {
  if (_obj_alloc_stat != NULL) {
    uint node_index = _numa->index_of_current_thread();
    _numa->copy_statistics(G1NUMAStats::LocalObjProcessAtCopyToSurv, node_index, _obj_alloc_stat);
  }
}

void G1ParScanThreadState::update_numa_stats(uint node_index) {
  if (_obj_alloc_stat != NULL) {
    _obj_alloc_stat[node_index]++;
  }
}

#endif // SHARE_GC_G1_G1PARSCANTHREADSTATE_INLINE_HPP
