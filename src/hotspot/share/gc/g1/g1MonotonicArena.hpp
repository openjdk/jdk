/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef SHARE_GC_G1_G1MONOTONICARENA_HPP
#define SHARE_GC_G1_G1MONOTONICARENA_HPP

#include "gc/shared/freeListAllocator.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/lockFreeStack.hpp"

// A G1MonotonicArena extends the FreeListConfig, memory
// blocks allocated from the OS are managed as a linked-list of Segments.
//
// Implementation details as below:
//
// Allocation arena for (card set, or ...) heap memory objects (Slot slots).
//
// Actual allocation from the C heap occurs as memory blocks called Segments.
// The allocation pattern for these Segments is assumed to be strictly two-phased:
//
// - in the first phase, Segments are allocated from the C heap (or a free
// list given at initialization time). This allocation may occur in parallel. This
// typically corresponds to a single mutator phase, but may extend over multiple.
//
// - in the second phase, Segments are added in bulk to the free list.
// This is typically done during a GC pause.
//
// Some third party is responsible for giving back memory from the free list to
// the operating system.
//
// Allocation and deallocation in the first phase basis may occur by multiple threads concurrently.
//
// The class also manages a few counters for statistics using atomic operations.
// Their values are only consistent within each other with extra global
// synchronization.
class G1MonotonicArena : public FreeListConfig {
public:
  class AllocOptions;
  class Segment;
  class SegmentFreeList;
private:
  // AllocOptions provides parameters for Segment sizing and expansion.
  const AllocOptions* _alloc_options;

  Segment* volatile _first;       // The (start of the) list of all segments.
  Segment* _last;                 // The last segment of the list of all segments.
  volatile uint _num_segments;    // Number of assigned segments to this allocator.
  volatile size_t _mem_size;      // Memory used by all segments.

  SegmentFreeList* _segment_free_list;  // The global free segment list to preferentially
                                        // get new segments from.

  volatile uint _num_total_slots; // Number of slots available in all segments (allocated + not yet used).
  volatile uint _num_allocated_slots; // Number of total slots allocated ever (including free and pending).

  inline Segment* new_segment(Segment* const prev);

  DEBUG_ONLY(uint calculate_length() const;)

public:
  const Segment* first_segment() const { return Atomic::load(&_first); }

  uint num_total_slots() const { return Atomic::load(&_num_total_slots); }
  uint num_allocated_slots() const {
    uint allocated = Atomic::load(&_num_allocated_slots);
    assert(calculate_length() == allocated, "Must be");
    return allocated;
  }

  uint slot_size() const;

  G1MonotonicArena(const AllocOptions* alloc_options,
                   SegmentFreeList* segment_free_list);
  ~G1MonotonicArena();

  // Deallocate all segments to the free segment list and reset this allocator. Must
  // be called in a globally synchronized area.
  void drop_all();

  uint num_segments() const;

  template<typename SegmentClosure>
  void iterate_segments(SegmentClosure& closure) const;
protected:
  void* allocate() override;
  // We do not deallocate individual slots
  void deallocate(void* slot) override { ShouldNotReachHere(); }
};

// A single segment/arena containing _num_slots blocks of memory of _slot_size.
// Segments can be linked together using a singly linked list.
class G1MonotonicArena::Segment {
  const uint _slot_size;
  const uint _num_slots;
  Segment* volatile _next;
  // Index into the next free slot to allocate into. Full if equal (or larger)
  // to _num_slots (can be larger because we atomically increment this value and
  // check only afterwards if the allocation has been successful).
  uint volatile _next_allocate;
  const MEMFLAGS _mem_flag;

  char* _bottom;  // Actual data.
  // Do not add class member variables beyond this point

  static size_t header_size() { return align_up(sizeof(Segment), DEFAULT_PADDING_SIZE); }

  static size_t payload_size(uint slot_size, uint num_slots) {
    // The cast (size_t) is required to guard against overflow wrap around.
    return (size_t)slot_size * num_slots;
  }

  size_t payload_size() const { return payload_size(_slot_size, _num_slots); }

  NONCOPYABLE(Segment);

  Segment(uint slot_size, uint num_slots, Segment* next, MEMFLAGS flag);
  ~Segment() = default;
public:
  Segment* volatile* next_addr() { return &_next; }

  void* allocate_slot();

  uint num_slots() const { return _num_slots; }

  Segment* next() const { return _next; }

  void set_next(Segment* next) {
    assert(next != this, " loop condition");
    _next = next;
  }

  void reset(Segment* next) {
    _next_allocate = 0;
    assert(next != this, " loop condition");
    set_next(next);
    memset((void*)_bottom, 0, payload_size());
  }

  uint slot_size() const { return _slot_size; }

  size_t mem_size() const { return header_size() + payload_size(); }

  uint length() const {
    // _next_allocate might grow larger than _num_slots in multi-thread environments
    // due to races.
    return MIN2(_next_allocate, _num_slots);
  }

  static size_t size_in_bytes(uint slot_size, uint num_slots) {
    return header_size() + payload_size(slot_size, num_slots);
  }

  static Segment* create_segment(uint slot_size, uint num_slots, Segment* next, MEMFLAGS mem_flag);
  static void delete_segment(Segment* segment);

  // Copies the contents of this segment into the destination.
  void copy_to(void* dest) const {
    ::memcpy(dest, _bottom, length() * _slot_size);
  }

  bool is_full() const { return _next_allocate >= _num_slots; }
};


// Set of (free) Segments. The assumed usage is that allocation
// to it and removal of segments is strictly separate, but every action may be
// performed by multiple threads concurrently.
// Counts and memory usage are current on a best-effort basis if accessed concurrently.
class G1MonotonicArena::SegmentFreeList {
  static Segment* volatile* next_ptr(Segment& segment) {
    return segment.next_addr();
  }
  using SegmentStack = LockFreeStack<Segment, &SegmentFreeList::next_ptr>;

  SegmentStack _list;

  volatile size_t _num_segments;
  volatile size_t _mem_size;

public:
  SegmentFreeList() : _list(), _num_segments(0), _mem_size(0) { }
  ~SegmentFreeList() { free_all(); }

  void bulk_add(Segment& first, Segment& last, size_t num, size_t mem_size);

  Segment* get();
  Segment* get_all(size_t& num_segments, size_t& mem_size);

  // Give back all memory to the OS.
  void free_all();

  void print_on(outputStream* out, const char* prefix = "");

  size_t num_segments() const { return Atomic::load(&_num_segments); }
  size_t mem_size() const { return Atomic::load(&_mem_size); }
};

// Configuration for G1MonotonicArena, e.g slot size, slot number of next Segment.
class G1MonotonicArena::AllocOptions {

protected:
  const MEMFLAGS _mem_flag;
  const uint _slot_size;
  const uint _initial_num_slots;
  // Defines a limit to the number of slots in the segment
  const uint _max_num_slots;
  const uint _slot_alignment;

public:
  AllocOptions(MEMFLAGS mem_flag, uint slot_size, uint initial_num_slots, uint max_num_slots, uint alignment) :
    _mem_flag(mem_flag),
    _slot_size(align_up(slot_size, alignment)),
    _initial_num_slots(initial_num_slots),
    _max_num_slots(max_num_slots),
    _slot_alignment(alignment) {
    assert(_slot_size > 0, "Must be");
    assert(_initial_num_slots > 0, "Must be");
    assert(_max_num_slots > 0, "Must be");
    assert(_slot_alignment > 0, "Must be");
  }

  virtual uint next_num_slots(uint prev_num_slots) const {
    return _initial_num_slots;
  }

  uint slot_size() const { return _slot_size; }

  uint slot_alignment() const { return _slot_alignment; }

  MEMFLAGS mem_flag() const {return _mem_flag; }
};

#endif //SHARE_GC_G1_MONOTONICARENA_HPP
