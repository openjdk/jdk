/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Huawei Technologies Co. Ltd. All rights reserved.
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

#ifndef SHARE_GC_G1_G1SEGMENTEDARRAY_INLINE_HPP
#define SHARE_GC_G1_G1SEGMENTEDARRAY_INLINE_HPP

#include "gc/g1/g1SegmentedArray.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalCounter.inline.hpp"

template<MEMFLAGS flag>
G1SegmentedArraySegment<flag>::G1SegmentedArraySegment(uint slot_size, uint num_slots, G1SegmentedArraySegment* next) :
  _slot_size(slot_size), _num_slots(num_slots), _next(next), _next_allocate(0) {

  _segment = NEW_C_HEAP_ARRAY(char, (size_t)_num_slots * slot_size, flag);
}

template<MEMFLAGS flag>
G1SegmentedArraySegment<flag>::~G1SegmentedArraySegment() {
  FREE_C_HEAP_ARRAY(flag, _segment);
}

template<MEMFLAGS flag>
void* G1SegmentedArraySegment<flag>::get_new_slot() {
  if (_next_allocate >= _num_slots) {
    return nullptr;
  }
  uint result = Atomic::fetch_and_add(&_next_allocate, 1u, memory_order_relaxed);
  if (result >= _num_slots) {
    return nullptr;
  }
  void* r = _segment + (uint)result * _slot_size;
  return r;
}

template<MEMFLAGS flag>
void G1SegmentedArrayFreeList<flag>::bulk_add(G1SegmentedArraySegment<flag>& first,
                                              G1SegmentedArraySegment<flag>& last,
                                              size_t num,
                                              size_t mem_size) {
  _list.prepend(first, last);
  Atomic::add(&_num_segments, num, memory_order_relaxed);
  Atomic::add(&_mem_size, mem_size, memory_order_relaxed);
}

template<MEMFLAGS flag>
void G1SegmentedArrayFreeList<flag>::print_on(outputStream* out, const char* prefix) {
  out->print_cr("%s: segments %zu size %zu",
                prefix, Atomic::load(&_num_segments), Atomic::load(&_mem_size));
}

template<MEMFLAGS flag>
G1SegmentedArraySegment<flag>* G1SegmentedArrayFreeList<flag>::get() {
  GlobalCounter::CriticalSection cs(Thread::current());

  G1SegmentedArraySegment<flag>* result = _list.pop();
  if (result != nullptr) {
    Atomic::dec(&_num_segments, memory_order_relaxed);
    Atomic::sub(&_mem_size, result->mem_size(), memory_order_relaxed);
  }
  return result;
}

template<MEMFLAGS flag>
G1SegmentedArraySegment<flag>* G1SegmentedArrayFreeList<flag>::get_all(size_t& num_segments,
                                                                       size_t& mem_size) {
  GlobalCounter::CriticalSection cs(Thread::current());

  G1SegmentedArraySegment<flag>* result = _list.pop_all();
  num_segments = Atomic::load(&_num_segments);
  mem_size = Atomic::load(&_mem_size);

  if (result != nullptr) {
    Atomic::sub(&_num_segments, num_segments, memory_order_relaxed);
    Atomic::sub(&_mem_size, mem_size, memory_order_relaxed);
  }
  return result;
}

template<MEMFLAGS flag>
void G1SegmentedArrayFreeList<flag>::free_all() {
  size_t num_freed = 0;
  size_t mem_size_freed = 0;
  G1SegmentedArraySegment<flag>* cur;

  while ((cur = _list.pop()) != nullptr) {
    mem_size_freed += cur->mem_size();
    num_freed++;
    delete cur;
  }

  Atomic::sub(&_num_segments, num_freed, memory_order_relaxed);
  Atomic::sub(&_mem_size, mem_size_freed, memory_order_relaxed);
}

template <class Slot, MEMFLAGS flag>
G1SegmentedArraySegment<flag>* G1SegmentedArray<Slot, flag>::create_new_segment(G1SegmentedArraySegment<flag>* const prev) {
  // Take an existing segment if available.
  G1SegmentedArraySegment<flag>* next = _free_segment_list->get();
  if (next == nullptr) {
    uint prev_num_slots = (prev != nullptr) ? prev->num_slots() : 0;
    uint num_slots = _alloc_options->next_num_slots(prev_num_slots);
    next = new G1SegmentedArraySegment<flag>(slot_size(), num_slots, prev);
  } else {
    assert(slot_size() == next->slot_size() ,
           "Mismatch %d != %d Slot %zu", slot_size(), next->slot_size(), sizeof(Slot));
    next->reset(prev);
  }

  // Install it as current allocation segment.
  G1SegmentedArraySegment<flag>* old = Atomic::cmpxchg(&_first, prev, next);
  if (old != prev) {
    // Somebody else installed the segment, use that one.
    delete next;
    return old;
  } else {
    // Did we install the first segment in the list? If so, this is also the last.
    if (prev == nullptr) {
      _last = next;
    }
    // Successfully installed the segment into the list.
    Atomic::inc(&_num_segments, memory_order_relaxed);
    Atomic::add(&_mem_size, next->mem_size(), memory_order_relaxed);
    Atomic::add(&_num_available_slots, next->num_slots(), memory_order_relaxed);
    return next;
  }
}

template <class Slot, MEMFLAGS flag>
uint G1SegmentedArray<Slot, flag>::slot_size() const {
  return _alloc_options->slot_size();
}

template <class Slot, MEMFLAGS flag>
G1SegmentedArray<Slot, flag>::G1SegmentedArray(const G1SegmentedArrayAllocOptions* alloc_options,
                                               G1SegmentedArrayFreeList<flag>* free_segment_list) :
     _alloc_options(alloc_options),
     _first(nullptr),
     _last(nullptr),
     _num_segments(0),
     _mem_size(0),
     _free_segment_list(free_segment_list),
     _num_available_slots(0),
     _num_allocated_slots(0) {
  assert(_free_segment_list != nullptr, "precondition!");
}

template <class Slot, MEMFLAGS flag>
G1SegmentedArray<Slot, flag>::~G1SegmentedArray() {
  drop_all();
}

template <class Slot, MEMFLAGS flag>
void G1SegmentedArray<Slot, flag>::drop_all() {
  G1SegmentedArraySegment<flag>* cur = Atomic::load_acquire(&_first);

  if (cur != nullptr) {
    assert(_last != nullptr, "If there is at least one segment, there must be a last one.");

    G1SegmentedArraySegment<flag>* first = cur;
#ifdef ASSERT
    // Check list consistency.
    G1SegmentedArraySegment<flag>* last = cur;
    uint num_segments = 0;
    size_t mem_size = 0;
    while (cur != nullptr) {
      mem_size += cur->mem_size();
      num_segments++;

      G1SegmentedArraySegment<flag>* next = cur->next();
      last = cur;
      cur = next;
    }
#endif
    assert(num_segments == _num_segments, "Segment count inconsistent %u %u", num_segments, _num_segments);
    assert(mem_size == _mem_size, "Memory size inconsistent");
    assert(last == _last, "Inconsistent last segment");

    _free_segment_list->bulk_add(*first, *_last, _num_segments, _mem_size);
  }

  _first = nullptr;
  _last = nullptr;
  _num_segments = 0;
  _mem_size = 0;
  _num_available_slots = 0;
  _num_allocated_slots = 0;
}

template <class Slot, MEMFLAGS flag>
Slot* G1SegmentedArray<Slot, flag>::allocate() {
  assert(slot_size() > 0, "instance size not set.");

  G1SegmentedArraySegment<flag>* cur = Atomic::load_acquire(&_first);
  if (cur == nullptr) {
    cur = create_new_segment(cur);
  }

  while (true) {
    Slot* slot = (Slot*)cur->get_new_slot();
    if (slot != nullptr) {
      Atomic::inc(&_num_allocated_slots, memory_order_relaxed);
      guarantee(is_aligned(slot, _alloc_options->slot_alignment()),
                "result " PTR_FORMAT " not aligned at %u", p2i(slot), _alloc_options->slot_alignment());
      return slot;
    }
    // The segment is full. Next round.
    assert(cur->is_full(), "must be");
    cur = create_new_segment(cur);
  }
}

template <class Slot, MEMFLAGS flag>
inline uint G1SegmentedArray<Slot, flag>::num_segments() const {
  return Atomic::load(&_num_segments);
}

#ifdef ASSERT
template <MEMFLAGS flag>
class LengthClosure {
  uint _total;
public:
  LengthClosure() : _total(0) {}
  void do_segment(G1SegmentedArraySegment<flag>* segment, uint limit) {
    _total += limit;
  }
  uint length() const {
    return _total;
  }
};

template <class Slot, MEMFLAGS flag>
uint G1SegmentedArray<Slot, flag>::calculate_length() const {
  LengthClosure<flag> closure;
  iterate_segments(closure);
  return closure.length();
}
#endif

template <class Slot, MEMFLAGS flag>
template <typename SegmentClosure>
void G1SegmentedArray<Slot, flag>::iterate_segments(SegmentClosure& closure) const {
  G1SegmentedArraySegment<flag>* cur = Atomic::load_acquire(&_first);

  assert((cur != nullptr) == (_last != nullptr),
         "If there is at least one segment, there must be a last one");

  while (cur != nullptr) {
    closure.do_segment(cur, cur->length());
    cur = cur->next();
  }
}

#endif //SHARE_GC_G1_G1SEGMENTEDARRAY_INLINE_HPP
