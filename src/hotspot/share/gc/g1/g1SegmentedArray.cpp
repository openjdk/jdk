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

#include "precompiled.hpp"

#include "gc/g1/g1SegmentedArray.inline.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalCounter.inline.hpp"

G1SegmentedArraySegment::G1SegmentedArraySegment(uint slot_size, uint num_slots, G1SegmentedArraySegment* next, MEMFLAGS flag) :
  _slot_size(slot_size),
  _num_slots(num_slots),
  _mem_flag(flag),
  _next(next),
  _next_allocate(0) {
  _bottom = ((char*) this) + header_size();
}

G1SegmentedArraySegment* G1SegmentedArraySegment::create_segment(uint slot_size,
                                                                 uint num_slots,
                                                                 G1SegmentedArraySegment* next,
                                                                 MEMFLAGS mem_flag) {
  size_t block_size = size_in_bytes(slot_size, num_slots);
  char* alloc_block = NEW_C_HEAP_ARRAY(char, block_size, mem_flag);
  return new (alloc_block) G1SegmentedArraySegment(slot_size, num_slots, next, mem_flag);
}

void G1SegmentedArraySegment::delete_segment(G1SegmentedArraySegment* segment) {
  segment->~G1SegmentedArraySegment();
  FREE_C_HEAP_ARRAY(_mem_flag, segment);
}

void G1SegmentedArrayFreeList::bulk_add(G1SegmentedArraySegment& first,
                                        G1SegmentedArraySegment& last,
                                        size_t num,
                                        size_t mem_size) {
  _list.prepend(first, last);
  Atomic::add(&_num_segments, num, memory_order_relaxed);
  Atomic::add(&_mem_size, mem_size, memory_order_relaxed);
}

void G1SegmentedArrayFreeList::print_on(outputStream* out, const char* prefix) {
  out->print_cr("%s: segments %zu size %zu",
                prefix, Atomic::load(&_num_segments), Atomic::load(&_mem_size));
}

G1SegmentedArraySegment* G1SegmentedArrayFreeList::get_all(size_t& num_segments,
                                                           size_t& mem_size) {
  GlobalCounter::CriticalSection cs(Thread::current());

  G1SegmentedArraySegment* result = _list.pop_all();
  num_segments = Atomic::load(&_num_segments);
  mem_size = Atomic::load(&_mem_size);

  if (result != nullptr) {
    Atomic::sub(&_num_segments, num_segments, memory_order_relaxed);
    Atomic::sub(&_mem_size, mem_size, memory_order_relaxed);
  }
  return result;
}

void G1SegmentedArrayFreeList::free_all() {
  size_t num_freed = 0;
  size_t mem_size_freed = 0;
  G1SegmentedArraySegment* cur;

  while ((cur = _list.pop()) != nullptr) {
    mem_size_freed += cur->mem_size();
    num_freed++;
    G1SegmentedArraySegment::delete_segment(cur);
  }

  Atomic::sub(&_num_segments, num_freed, memory_order_relaxed);
  Atomic::sub(&_mem_size, mem_size_freed, memory_order_relaxed);
}

G1SegmentedArraySegment* G1SegmentedArray::create_new_segment(G1SegmentedArraySegment* const prev) {
  // Take an existing segment if available.
  G1SegmentedArraySegment* next = _free_segment_list->get();
  if (next == nullptr) {
    uint prev_num_slots = (prev != nullptr) ? prev->num_slots() : 0;
    uint num_slots = _alloc_options->next_num_slots(prev_num_slots);

    next = G1SegmentedArraySegment::create_segment(slot_size(), num_slots, prev, _alloc_options->mem_flag());
  } else {
    assert(slot_size() == next->slot_size() ,
           "Mismatch %d != %d", slot_size(), next->slot_size());
    next->reset(prev);
  }

  // Install it as current allocation segment.
  G1SegmentedArraySegment* old = Atomic::cmpxchg(&_first, prev, next);
  if (old != prev) {
    // Somebody else installed the segment, use that one.
    G1SegmentedArraySegment::delete_segment(next);
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

G1SegmentedArray::G1SegmentedArray(const G1SegmentedArrayAllocOptions* alloc_options,
                                   G1SegmentedArrayFreeList* free_segment_list) :
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

G1SegmentedArray::~G1SegmentedArray() {
  drop_all();
}

uint G1SegmentedArray::slot_size() const {
  return _alloc_options->slot_size();
}

void G1SegmentedArray::drop_all() {
  G1SegmentedArraySegment* cur = Atomic::load_acquire(&_first);

  if (cur != nullptr) {
    assert(_last != nullptr, "If there is at least one segment, there must be a last one.");

    G1SegmentedArraySegment* first = cur;
#ifdef ASSERT
    // Check list consistency.
    G1SegmentedArraySegment* last = cur;
    uint num_segments = 0;
    size_t mem_size = 0;
    while (cur != nullptr) {
      mem_size += cur->mem_size();
      num_segments++;

      G1SegmentedArraySegment* next = cur->next();
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

void* G1SegmentedArray::allocate() {
  assert(slot_size() > 0, "instance size not set.");

  G1SegmentedArraySegment* cur = Atomic::load_acquire(&_first);
  if (cur == nullptr) {
    cur = create_new_segment(cur);
  }

  while (true) {
    void* slot = cur->get_new_slot();
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

uint G1SegmentedArray::num_segments() const {
  return Atomic::load(&_num_segments);
}

#ifdef ASSERT
class LengthClosure {
  uint _total;
public:
  LengthClosure() : _total(0) {}
  void do_segment(G1SegmentedArraySegment* segment, uint limit) {
    _total += limit;
  }
  uint length() const {
    return _total;
  }
};

uint G1SegmentedArray::calculate_length() const {
  LengthClosure closure;
  iterate_segments(closure);
  return closure.length();
}
#endif

template <typename SegmentClosure>
void G1SegmentedArray::iterate_segments(SegmentClosure& closure) const {
  G1SegmentedArraySegment* cur = Atomic::load_acquire(&_first);

  assert((cur != nullptr) == (_last != nullptr),
         "If there is at least one segment, there must be a last one");

  while (cur != nullptr) {
    closure.do_segment(cur, cur->length());
    cur = cur->next();
  }
}
