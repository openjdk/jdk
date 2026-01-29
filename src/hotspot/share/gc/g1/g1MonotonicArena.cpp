/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1MonotonicArena.inline.hpp"
#include "memory/allocation.hpp"
#include "runtime/vmOperations.hpp"
#include "utilities/globalCounter.inline.hpp"

G1MonotonicArena::Segment::Segment(uint slot_size, uint num_slots, Segment* next, MemTag mem_tag) :
  _slot_size(slot_size),
  _num_slots(num_slots),
  _next(next),
  _next_allocate(0),
  _mem_tag(mem_tag) {
  guarantee(is_aligned(this, SegmentPayloadMaxAlignment), "Make sure Segments are always created at correctly aligned memory");
}

G1MonotonicArena::Segment* G1MonotonicArena::Segment::create_segment(uint slot_size,
                                                                     uint num_slots,
                                                                     Segment* next,
                                                                     MemTag mem_tag) {
  size_t block_size = size_in_bytes(slot_size, num_slots);
  char* alloc_block = NEW_C_HEAP_ARRAY(char, block_size, mem_tag);
  return new (alloc_block) Segment(slot_size, num_slots, next, mem_tag);
}

void G1MonotonicArena::Segment::delete_segment(Segment* segment) {
  // Wait for concurrent readers of the segment to exit before freeing; but only if the VM
  // isn't exiting.
  if (!VM_Exit::vm_exited()) {
    GlobalCounter::write_synchronize();
  }
  segment->~Segment();
  FREE_C_HEAP_ARRAY(_mem_tag, segment);
}

void G1MonotonicArena::SegmentFreeList::bulk_add(Segment& first,
                                                 Segment& last,
                                                 size_t num,
                                                 size_t mem_size) {
  _list.prepend(first, last);
  _num_segments.add_then_fetch(num, memory_order_relaxed);
  _mem_size.add_then_fetch(mem_size, memory_order_relaxed);
}

void G1MonotonicArena::SegmentFreeList::print_on(outputStream* out, const char* prefix) {
  out->print_cr("%s: segments %zu size %zu",
                prefix, _num_segments.load_relaxed(), _mem_size.load_relaxed());
}

G1MonotonicArena::Segment* G1MonotonicArena::SegmentFreeList::get_all(size_t& num_segments,
                                                                      size_t& mem_size) {
  GlobalCounter::CriticalSection cs(Thread::current());

  Segment* result = _list.pop_all();
  num_segments = _num_segments.load_relaxed();
  mem_size = _mem_size.load_relaxed();

  if (result != nullptr) {
    _num_segments.sub_then_fetch(num_segments, memory_order_relaxed);
    _mem_size.sub_then_fetch(mem_size, memory_order_relaxed);
  }
  return result;
}

void G1MonotonicArena::SegmentFreeList::free_all() {
  size_t num_freed = 0;
  size_t mem_size_freed = 0;
  Segment* cur;

  while ((cur = _list.pop()) != nullptr) {
    mem_size_freed += cur->mem_size();
    num_freed++;
    Segment::delete_segment(cur);
  }

  _num_segments.sub_then_fetch(num_freed, memory_order_relaxed);
  _mem_size.sub_then_fetch(mem_size_freed, memory_order_relaxed);
}

G1MonotonicArena::Segment* G1MonotonicArena::new_segment(Segment* const prev) {
  // Take an existing segment if available.
  Segment* next = _segment_free_list->get();
  if (next == nullptr) {
    uint prev_num_slots = (prev != nullptr) ? prev->num_slots() : 0;
    uint num_slots = _alloc_options->next_num_slots(prev_num_slots);

    next = Segment::create_segment(slot_size(), num_slots, prev, _alloc_options->mem_tag());
  } else {
    assert(slot_size() == next->slot_size() ,
           "Mismatch %d != %d", slot_size(), next->slot_size());
    next->reset(prev);
  }

  // Install it as current allocation segment.
  Segment* old = _first.compare_exchange(prev, next);
  if (old != prev) {
    // Somebody else installed the segment, use that one.
    Segment::delete_segment(next);
    return old;
  } else {
    // Did we install the first segment in the list? If so, this is also the last.
    if (prev == nullptr) {
      _last = next;
    }
    // Successfully installed the segment into the list.
    _num_segments.add_then_fetch(1u, memory_order_relaxed);
    _mem_size.add_then_fetch(next->mem_size(), memory_order_relaxed);
    _num_total_slots.add_then_fetch(next->num_slots(), memory_order_relaxed);
    return next;
  }
}

G1MonotonicArena::G1MonotonicArena(const AllocOptions* alloc_options,
                                   SegmentFreeList* segment_free_list) :
  _alloc_options(alloc_options),
  _first(nullptr),
  _last(nullptr),
  _num_segments(0),
  _mem_size(0),
  _segment_free_list(segment_free_list),
  _num_total_slots(0),
  _num_allocated_slots(0) {
  assert(_segment_free_list != nullptr, "precondition!");
}

G1MonotonicArena::~G1MonotonicArena() {
  drop_all();
}

uint G1MonotonicArena::slot_size() const {
  return _alloc_options->slot_size();
}

void G1MonotonicArena::drop_all() {
  Segment* cur = _first.load_acquire();

  if (cur != nullptr) {
    assert(_last != nullptr, "If there is at least one segment, there must be a last one.");

    Segment* first = cur;
#ifdef ASSERT
    // Check list consistency.
    Segment* last = cur;
    uint num_segments = 0;
    size_t mem_size = 0;
    while (cur != nullptr) {
      mem_size += cur->mem_size();
      num_segments++;

      Segment* next = cur->next();
      last = cur;
      cur = next;
    }
#endif
    assert(num_segments == _num_segments.load_relaxed(), "Segment count inconsistent %u %u", num_segments, _num_segments.load_relaxed());
    assert(mem_size == _mem_size.load_relaxed(), "Memory size inconsistent");
    assert(last == _last, "Inconsistent last segment");

    _segment_free_list->bulk_add(*first, *_last, _num_segments.load_relaxed(), _mem_size.load_relaxed());
  }

  _first.store_relaxed(nullptr);
  _last = nullptr;
  _num_segments.store_relaxed(0);
  _mem_size.store_relaxed(0);
  _num_total_slots.store_relaxed(0);
  _num_allocated_slots.store_relaxed(0);
}

void* G1MonotonicArena::allocate() {
  assert(slot_size() > 0, "instance size not set.");

  Segment* cur = _first.load_acquire();
  if (cur == nullptr) {
    cur = new_segment(cur);
  }

  while (true) {
    void* slot = cur->allocate_slot();
    if (slot != nullptr) {
      _num_allocated_slots.add_then_fetch(1u, memory_order_relaxed);
      guarantee(is_aligned(slot, _alloc_options->slot_alignment()),
                "result " PTR_FORMAT " not aligned at %u", p2i(slot), _alloc_options->slot_alignment());
      return slot;
    }
    // The segment is full. Next round.
    assert(cur->is_full(), "must be");
    cur = new_segment(cur);
  }
}

uint G1MonotonicArena::num_segments() const {
  return _num_segments.load_relaxed();
}

#ifdef ASSERT
class LengthClosure {
  uint _total;
public:
  LengthClosure() : _total(0) {}
  void do_segment(G1MonotonicArena::Segment* segment, uint limit) {
    _total += limit;
  }
  uint length() const {
    return _total;
  }
};

uint G1MonotonicArena::calculate_length() const {
  LengthClosure closure;
  iterate_segments(closure);
  return closure.length();
}
#endif

template <typename SegmentClosure>
void G1MonotonicArena::iterate_segments(SegmentClosure& closure) const {
  Segment* cur = _first.load_acquire();

  assert((cur != nullptr) == (_last != nullptr),
         "If there is at least one segment, there must be a last one");

  while (cur != nullptr) {
    closure.do_segment(cur, cur->length());
    cur = cur->next();
  }
}
