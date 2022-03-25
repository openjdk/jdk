/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022, Huawei Technologies Co. Ltd. All rights reserved.
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

#ifndef SHARE_GC_G1_G1SEGMENTEDARRAY_HPP
#define SHARE_GC_G1_G1SEGMENTEDARRAY_HPP

#include "gc/shared/freeListAllocator.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/lockFreeStack.hpp"

// A single segment/arena containing _num_slots blocks of memory of _slot_size.
// G1SegmentedArraySegments can be linked together using a singly linked list.
class G1SegmentedArraySegment {
  const uint _slot_size;
  const uint _num_slots;
  const MEMFLAGS _mem_flag;
  G1SegmentedArraySegment* volatile _next;
  // Index into the next free slot to allocate into. Full if equal (or larger)
  // to _num_slots (can be larger because we atomically increment this value and
  // check only afterwards if the allocation has been successful).
  uint volatile _next_allocate;

  char* _bottom;  // Actual data.
  // Do not add class member variables beyond this point

  static size_t header_size() { return align_up(offset_of(G1SegmentedArraySegment, _bottom), DEFAULT_CACHE_LINE_SIZE); }

  static size_t payload_size(uint slot_size, uint num_slots) {
    // The cast (size_t) is required to guard against overflow wrap around.
    return (size_t)slot_size * num_slots;
  }

  size_t payload_size() const { return payload_size(_slot_size, _num_slots); }

  NONCOPYABLE(G1SegmentedArraySegment);

  G1SegmentedArraySegment(uint slot_size, uint num_slots, G1SegmentedArraySegment* next, MEMFLAGS flag);
  ~G1SegmentedArraySegment() = default;
public:
  G1SegmentedArraySegment* volatile* next_addr() { return &_next; }

  void* get_new_slot();

  uint num_slots() const { return _num_slots; }

  G1SegmentedArraySegment* next() const { return _next; }

  void set_next(G1SegmentedArraySegment* next) {
    assert(next != this, " loop condition");
    _next = next;
  }

  void reset(G1SegmentedArraySegment* next) {
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

  static G1SegmentedArraySegment* create_segment(uint slot_size, uint num_slots, G1SegmentedArraySegment* next, MEMFLAGS mem_flag);
  static void delete_segment(G1SegmentedArraySegment* segment);

  // Copies the (valid) contents of this segment into the destination.
  void copy_to(void* dest) const {
    ::memcpy(dest, _bottom, length() * _slot_size);
  }

  bool is_full() const { return _next_allocate >= _num_slots; }
};

// Set of (free) G1SegmentedArraySegments. The assumed usage is that allocation
// to it and removal of segments is strictly separate, but every action may be
// performed by multiple threads at the same time.
// Counts and memory usage are current on a best-effort basis if accessed concurrently.
class G1SegmentedArrayFreeList {
  static G1SegmentedArraySegment* volatile* next_ptr(G1SegmentedArraySegment& segment) {
    return segment.next_addr();
  }
  using SegmentStack = LockFreeStack<G1SegmentedArraySegment, &G1SegmentedArrayFreeList::next_ptr>;

  SegmentStack _list;

  volatile size_t _num_segments;
  volatile size_t _mem_size;

public:
  G1SegmentedArrayFreeList() : _list(), _num_segments(0), _mem_size(0) { }
  ~G1SegmentedArrayFreeList() { free_all(); }

  void bulk_add(G1SegmentedArraySegment& first, G1SegmentedArraySegment& last, size_t num, size_t mem_size);

  G1SegmentedArraySegment* get();
  G1SegmentedArraySegment* get_all(size_t& num_segments, size_t& mem_size);

  // Give back all memory to the OS.
  void free_all();

  void print_on(outputStream* out, const char* prefix = "");

  size_t num_segments() const { return Atomic::load(&_num_segments); }
  size_t mem_size() const { return Atomic::load(&_mem_size); }
};

// Configuration for G1SegmentedArray, e.g slot size, slot number of next G1SegmentedArraySegment.
class G1SegmentedArrayAllocOptions {

protected:
  const MEMFLAGS _mem_flag;
  const uint _slot_size;
  const uint _initial_num_slots;
  // Defines a limit to the number of slots in the segment
  const uint _max_num_slots;
  const uint _slot_alignment;

public:
  G1SegmentedArrayAllocOptions(MEMFLAGS mem_flag, uint slot_size, uint initial_num_slots, uint max_num_slots, uint alignment) :
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

// A segmented array where G1SegmentedArraySegment is the segment, and
// G1SegmentedArrayFreeList is the free list to cache G1SegmentedArraySegments,
// and G1SegmentedArrayAllocOptions is the configuration for G1SegmentedArray
// attributes.
//
// Implementation details as below:
//
// Arena-like allocator for (card set, or ...) heap memory objects (Slot slots).
//
// Actual allocation from the C heap occurs on G1SegmentedArraySegment basis, i.e. segments
// of slots. The assumed allocation pattern for these G1SegmentedArraySegment slots
// is assumed to be strictly two-phased:
//
// - in the first phase, G1SegmentedArraySegments are allocated from the C heap (or a free
// list given at initialization time). This allocation may occur in parallel. This
// typically corresponds to a single mutator phase, but may extend over multiple.
//
// - in the second phase, G1SegmentedArraySegments are given back in bulk to the free list.
// This is typically done during a GC pause.
//
// Some third party is responsible for giving back memory from the free list to
// the operating system.
//
// Allocation and deallocation in the first phase basis may occur by multiple threads at once.
//
// The class also manages a few counters for statistics using atomic operations.
// Their values are only consistent within each other with extra global
// synchronization.

class G1SegmentedArray : public FreeListConfig  {
  // G1SegmentedArrayAllocOptions provides parameters for allocation segment
  // sizing and expansion.
  const G1SegmentedArrayAllocOptions* _alloc_options;

  G1SegmentedArraySegment* volatile _first;       // The (start of the) list of all segments.
  G1SegmentedArraySegment* _last;                 // The last segment of the list of all segments.
  volatile uint _num_segments;                    // Number of assigned segments to this allocator.
  volatile size_t _mem_size;                      // Memory used by all segments.

  G1SegmentedArrayFreeList* _free_segment_list;   // The global free segment list to preferentially
                                                  // get new segments from.

  volatile uint _num_available_slots; // Number of slots available in all segments (allocated + free + pending + not yet used).
  volatile uint _num_allocated_slots; // Number of total slots allocated and in use.

private:
  inline G1SegmentedArraySegment* create_new_segment(G1SegmentedArraySegment* const prev);

  DEBUG_ONLY(uint calculate_length() const;)

public:
  const G1SegmentedArraySegment* first_array_segment() const { return Atomic::load(&_first); }

  uint num_available_slots() const { return Atomic::load(&_num_available_slots); }
  uint num_allocated_slots() const {
    uint allocated = Atomic::load(&_num_allocated_slots);
    assert(calculate_length() == allocated, "Must be");
    return allocated;
  }

  uint slot_size() const;

  G1SegmentedArray(const G1SegmentedArrayAllocOptions* alloc_options,
                   G1SegmentedArrayFreeList* free_segment_list);
  ~G1SegmentedArray();

  // Deallocate all segments to the free segment list and reset this allocator. Must
  // be called in a globally synchronized area.
  void drop_all();

  inline void* allocate() override;

  // We do not deallocate individual slots
  inline void deallocate(void* node) override { ShouldNotReachHere(); }

  uint num_segments() const;

  template<typename SegmentClosure>
  void iterate_segments(SegmentClosure& closure) const;
};

#endif //SHARE_GC_G1_G1SEGMENTEDARRAY_HPP
