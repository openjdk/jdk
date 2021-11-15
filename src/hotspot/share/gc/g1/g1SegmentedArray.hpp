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

#ifndef SHARE_GC_G1_G1SEGMENTEDARRAY_HPP
#define SHARE_GC_G1_G1SEGMENTEDARRAY_HPP

#include "memory/allocation.hpp"
#include "utilities/lockFreeStack.hpp"

// A single buffer/arena containing _num_elems blocks of memory of _elem_size.
// G1SegmentedArrayBuffers can be linked together using a singly linked list.
template<MEMFLAGS flag>
class G1SegmentedArrayBuffer : public CHeapObj<flag> {
  const uint _elem_size;
  const uint _num_elems;

  G1SegmentedArrayBuffer* volatile _next;

  char* _buffer;  // Actual data.

  // Index into the next free block to allocate into. Full if equal (or larger)
  // to _num_elems (can be larger because we atomically increment this value and
  // check only afterwards if the allocation has been successful).
  uint volatile _next_allocate;

public:
  G1SegmentedArrayBuffer(uint elem_size, uint num_elems, G1SegmentedArrayBuffer* next);
  ~G1SegmentedArrayBuffer();

  G1SegmentedArrayBuffer* volatile* next_addr() { return &_next; }

  void* get_new_buffer_elem();

  uint num_elems() const { return _num_elems; }

  G1SegmentedArrayBuffer* next() const { return _next; }

  void set_next(G1SegmentedArrayBuffer* next) {
    assert(next != this, " loop condition");
    _next = next;
  }

  void reset(G1SegmentedArrayBuffer* next) {
    _next_allocate = 0;
    assert(next != this, " loop condition");
    set_next(next);
    memset((void*)_buffer, 0, (size_t)_num_elems * _elem_size);
  }

  uint elem_size() const { return _elem_size; }

  size_t mem_size() const { return sizeof(*this) + (size_t)_num_elems * _elem_size; }

  uint length() const {
    // _next_allocate might grow larger than _num_elems in multi-thread environments
    // due to races.
    return MIN2(_next_allocate, _num_elems);
  }

  // Copies the (valid) contents of this buffer into the destination.
  void copy_to(void* dest) const {
    ::memcpy(dest, _buffer, length() * _elem_size);
  }

  bool is_full() const { return _next_allocate >= _num_elems; }
};

// Set of (free) G1SegmentedArrayBuffers. The assumed usage is that allocation
// to it and removal of elements is strictly separate, but every action may be
// performed by multiple threads at the same time.
// Counts and memory usage are current on a best-effort basis if accessed concurrently.
template<MEMFLAGS flag>
class G1SegmentedArrayBufferList {
  static G1SegmentedArrayBuffer<flag>* volatile* next_ptr(G1SegmentedArrayBuffer<flag>& node) {
    return node.next_addr();
  }
  typedef LockFreeStack<G1SegmentedArrayBuffer<flag>, &G1SegmentedArrayBufferList::next_ptr> NodeStack;

  NodeStack _list;

  volatile size_t _num_buffers;
  volatile size_t _mem_size;

public:
  G1SegmentedArrayBufferList() : _list(), _num_buffers(0), _mem_size(0) { }
  ~G1SegmentedArrayBufferList() { free_all(); }

  void bulk_add(G1SegmentedArrayBuffer<flag>& first, G1SegmentedArrayBuffer<flag>& last, size_t num, size_t mem_size);

  G1SegmentedArrayBuffer<flag>* get();
  G1SegmentedArrayBuffer<flag>* get_all(size_t& num_buffers, size_t& mem_size);

  // Give back all memory to the OS.
  void free_all();

  void print_on(outputStream* out, const char* prefix = "");

  size_t num_buffers() const { return Atomic::load(&_num_buffers); }
  size_t mem_size() const { return Atomic::load(&_mem_size); }
};

// Configuration for G1SegmentedArray, e.g element size, element number of next G1SegmentedArrayBuffer.
class G1SegmentedArrayAllocOptions {

protected:
  const uint _elem_size;
  const uint _initial_num_elems;
  // Defines a limit to the number of elements in the buffer
  const uint _max_num_elems;
  const uint _alignment;

public:
  G1SegmentedArrayAllocOptions(uint elem_size, uint initial_num_elems, uint max_num_elems, uint alignment) :
    _elem_size(elem_size),
    _initial_num_elems(initial_num_elems),
    _max_num_elems(max_num_elems),
    _alignment(alignment) {
    assert(_elem_size > 0, "Must be");
    assert(_initial_num_elems > 0, "Must be");
    assert(_max_num_elems > 0, "Must be");
    assert(_alignment > 0, "Must be");
  }

  virtual uint next_num_elems(uint prev_num_elems) const {
    return _initial_num_elems;
  }

  uint elem_size() const { return _elem_size; }

  uint alignment() const { return _alignment; }
};

// A segmented array where G1SegmentedArrayBuffer is the segment, and
// G1SegmentedArrayBufferList is the free list to cache G1SegmentedArrayBuffer,
// and G1SegmentedArrayAllocOptions is the configuration for G1SegmentedArray
// attributes.
//
// Implementation details as below:
//
// Arena-like allocator for (card set, or ...) heap memory objects (Elem elements).
//
// Actual allocation from the C heap occurs on G1SegmentedArrayBuffer basis, i.e. segments
// of elements. The assumed allocation pattern for these G1SegmentedArrayBuffer elements
// is assumed to be strictly two-phased:
//
// - in the first phase, G1SegmentedArrayBuffers are allocated from the C heap (or a free
// list given at initialization time). This allocation may occur in parallel. This
// typically corresponds to a single mutator phase, but may extend over multiple.
//
// - in the second phase, G1SegmentedArrayBuffers are given back in bulk to the free list.
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
template <class Elem, MEMFLAGS flag>
class G1SegmentedArray {
  // G1SegmentedArrayAllocOptions provides parameters for allocation buffer
  // sizing and expansion.
  const G1SegmentedArrayAllocOptions* _alloc_options;

  G1SegmentedArrayBuffer<flag>* volatile _first;       // The (start of the) list of all buffers.
  G1SegmentedArrayBuffer<flag>* _last;                 // The last element of the list of all buffers.
  volatile uint _num_buffers;                          // Number of assigned buffers to this allocator.
  volatile size_t _mem_size;                           // Memory used by all buffers.

  G1SegmentedArrayBufferList<flag>* _free_buffer_list; // The global free buffer list to
                                                       // preferentially get new buffers from.

  volatile uint _num_available_nodes; // Number of nodes available in all buffers (allocated + free + pending + not yet used).
  volatile uint _num_allocated_nodes; // Number of total nodes allocated and in use.

private:
  inline G1SegmentedArrayBuffer<flag>* create_new_buffer(G1SegmentedArrayBuffer<flag>* const prev);

  DEBUG_ONLY(uint calculate_length() const;)

public:
  const G1SegmentedArrayBuffer<flag>* first_array_buffer() const { return Atomic::load(&_first); }

  uint num_available_nodes() const { return Atomic::load(&_num_available_nodes); }
  uint num_allocated_nodes() const {
    uint allocated = Atomic::load(&_num_allocated_nodes);
    assert(calculate_length() == allocated, "Must be");
    return allocated;
  }

  inline uint elem_size() const;

  G1SegmentedArray(const G1SegmentedArrayAllocOptions* buffer_options,
                   G1SegmentedArrayBufferList<flag>* free_buffer_list);
  ~G1SegmentedArray();

  // Deallocate all buffers to the free buffer list and reset this allocator. Must
  // be called in a globally synchronized area.
  void drop_all();

  inline Elem* allocate();

  inline uint num_buffers() const;

  template<typename BufferClosure>
  void iterate_nodes(BufferClosure& closure) const;
};

#endif //SHARE_GC_G1_G1SEGMENTEDARRAY_HPP
