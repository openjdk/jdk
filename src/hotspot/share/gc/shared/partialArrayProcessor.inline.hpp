/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_PARTIALARRAYPROCESSOR_INLINE_HPP
#define SHARE_GC_SHARED_PARTIALARRAYPROCESSOR_INLINE_HPP

#include "gc/shared/partialArrayProcessor.hpp"
#include "gc/shared/partialArrayState.hpp"
#include "gc/shared/partialArrayTaskStepper.inline.hpp"
#include "utilities/checkedCast.hpp"

template <typename T>
void PartialArrayProcessor<T>::set_partial_array_state_allocator(PartialArrayStateAllocator* alloc) {
  assert(_partial_array_state_allocator == nullptr, "Set PartialArrayStateAllocator twice");
  _partial_array_state_allocator = alloc;
}
template <typename T>
void PartialArrayProcessor<T>::set_partial_array_state_allocator_index(uint i) {
  assert(_partial_array_state_allocator_index == UINT_MAX, "Set PartialArrayStateAllocator index twice");
  _partial_array_state_allocator_index = i;
}

template <typename T>
PartialArrayProcessor<T>::PartialArrayProcessor(uint n_workers, size_t chunk_size, PartialArrayStateAllocator* allocator, T* q) :
  _partial_array_stepper(n_workers, chunk_size),
  _partial_array_state_allocator(allocator),
  _partial_array_state_allocator_index(UINT_MAX),
  _queue(q) { }

template <typename T>
PartialArrayProcessor<T>::PartialArrayProcessor(uint n_workers, size_t chunk_size, T* q) :
  _partial_array_stepper(n_workers, chunk_size),
  _partial_array_state_allocator(nullptr),
  _partial_array_state_allocator_index(UINT_MAX),
  _queue(q) { }

template <typename T>
template <typename PUSH_FUNC, typename PROC_FUNC>
void PartialArrayProcessor<T>::start(objArrayOop from_array, objArrayOop to_array, PUSH_FUNC& pushf, PROC_FUNC& procf) {
  size_t array_length = from_array->length();
  PartialArrayTaskStepper::Step step = _partial_array_stepper.start(array_length);
  if (step._ncreate > 0) {
    assert(_partial_array_state_allocator != nullptr, "PartialArrayStateAllocator not initialized");
    TASKQUEUE_STATS_ONLY(_queue->record_arrays_chunked());
    PartialArrayState* state =
    _partial_array_state_allocator->allocate(_partial_array_state_allocator_index,
                                             from_array, to_array,
                                             step._index,
                                             array_length,
                                             step._ncreate);
    for (uint i = 0; i < step._ncreate; ++i) {
      pushf(state);
    }
    TASKQUEUE_STATS_ONLY(_queue->record_array_chunk_pushes(step._ncreate));
  }

  procf(from_array, to_array, 0, checked_cast<int>(step._index));
}

template <typename T>
template <typename PUSH_FUNC, typename PROC_FUNC>
void PartialArrayProcessor<T>::process_array_chunk(PartialArrayState* state, PUSH_FUNC& pushf, PROC_FUNC& procf) {
  TASKQUEUE_STATS_ONLY(_queue->record_array_chunks_processed());

  // Claim a chunk.  Push additional tasks before processing the claimed
  // chunk to allow other workers to steal while we're processing.
  PartialArrayTaskStepper::Step step = _partial_array_stepper.next(state);
  if (step._ncreate > 0) {
    state->add_references(step._ncreate);
    for (uint i = 0; i < step._ncreate; ++i) {
      pushf(state);
    }
    TASKQUEUE_STATS_ONLY(_queue->record_array_chunk_pushes(step._ncreate));
  }
  int start = checked_cast<int>(step._index);
  int end = checked_cast<int>(step._index + _partial_array_stepper.chunk_size());
  assert(start < end, "invariant");
  procf(objArrayOop(state->source()), objArrayOop(state->destination()), start, end);

  // Release reference to state, now that we're done with it.
  _partial_array_state_allocator->release(_partial_array_state_allocator_index, state);
}
#endif // SHARE_GC_SHARED_PARTIALARRAYPROCESSOR_INLINE_HPP
