/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1ParScanThreadState.hpp"

#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1OopStarChunkedList.inline.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"

inline void G1ParScanThreadState::push_on_queue(ScannerTask task) {
  verify_task(task);
  _task_queue->push(task);
}

bool G1ParScanThreadState::needs_partial_trimming() const {
  return !_task_queue->overflow_empty() ||
         (_task_queue->size() > _stack_trim_upper_threshold);
}

void G1ParScanThreadState::trim_queue_partially() {
  if (!needs_partial_trimming()) {
    return;
  }

  const Ticks start = Ticks::now();
  trim_queue_to_threshold(_stack_trim_lower_threshold);
  assert(_task_queue->overflow_empty(), "invariant");
  assert(_task_queue->size() <= _stack_trim_lower_threshold, "invariant");
  _trim_ticks += Ticks::now() - start;
}

void G1ParScanThreadState::trim_queue() {
  trim_queue_to_threshold(0);
  assert(_task_queue->overflow_empty(), "invariant");
  assert(_task_queue->taskqueue_empty(), "invariant");
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

template <class T> void G1ParScanThreadState::write_ref_field_post(T* p, oop obj) {
  assert(obj != NULL, "Must be");
  if (HeapRegion::is_in_same_region(p, obj)) {
    return;
  }
  HeapRegion* from = _g1h->heap_region_containing(p);
  if (!from->is_young()) {
    enqueue_card_if_tracked(_g1h->region_attr(obj), p, obj);
  }
}

template <class T> void G1ParScanThreadState::enqueue_card_if_tracked(G1HeapRegionAttr region_attr, T* p, oop o) {
  assert(!HeapRegion::is_in_same_region(p, o), "Should have filtered out cross-region references already.");
  assert(!_g1h->heap_region_containing(p)->is_young(), "Should have filtered out from-young references already.");

#ifdef ASSERT
  HeapRegion* const hr_obj = _g1h->heap_region_containing(o);
  assert(region_attr.needs_remset_update() == hr_obj->rem_set()->is_tracked(),
         "State flag indicating remset tracking disagrees (%s) with actual remembered set (%s) for region %u",
         BOOL_TO_STR(region_attr.needs_remset_update()),
         BOOL_TO_STR(hr_obj->rem_set()->is_tracked()),
         hr_obj->hrm_index());
#endif
  if (!region_attr.needs_remset_update()) {
    return;
  }
  size_t card_index = ct()->index_for(p);
  // If the card hasn't been added to the buffer, do it.
  if (_last_enqueued_card != card_index) {
    _rdc_local_qset.enqueue(ct()->byte_for_index(card_index));
    _last_enqueued_card = card_index;
  }
}

#endif // SHARE_GC_G1_G1PARSCANTHREADSTATE_INLINE_HPP
