/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "nmt/memTag.hpp"
#include "utilities/align.hpp"
#include "runtime/os.hpp"

#include <limits>
#include <stdlib.h>
#include <type_traits>

#ifndef SHARE_NMT_CONTIGUOUSALLOCATOR_HPP
#define SHARE_NMT_CONTIGUOUSALLOCATOR_HPP

class VirtualMemoryTracker;

class NMTContiguousAllocator {
  friend class ContiguousAllocatorTestFixture;

  char* reserve_virtual_address_range();
  char* allocate_chunk(size_t requested_size);
  bool unreserve() {
    return os::pd_release_memory(_start, _size);
  }

public:
  MemTag _flag;
  size_t _size;
  size_t _chunk_size;
  char* _start; // Start of memory
  char* _offset; // Last returned point of allocation
  char* _committed_boundary; // Anything below this is paged in, invariant: is_aligned with VM page size
  NMTContiguousAllocator(size_t size, MemTag flag)
  : _flag(flag), _size(align_up(size, os::vm_page_size())),
    _chunk_size(os::vm_page_size()),
    _start(reserve_virtual_address_range()),
    _offset(_start),
    _committed_boundary(_start) {}

  NMTContiguousAllocator(const NMTContiguousAllocator& other)
  : _flag(other._flag),
    _size(other._size),
    _chunk_size(os::vm_page_size()),
    _start(reserve_virtual_address_range()),
    _offset(_start),
    _committed_boundary(_start) {
    char* alloc_addr = this->alloc(other._committed_boundary - other._start);
    if (alloc_addr == nullptr) {
      unreserve();
      _start = nullptr;
      _size = 0;
      return;
    }
    size_t bytes_allocated = other._offset - other._start;
    memcpy(alloc_addr, other._start, bytes_allocated);
    _offset = _start + bytes_allocated;
  }

  ~NMTContiguousAllocator();

  char* alloc(size_t size) {
    assert(is_reserved(), "must be");
    return allocate_chunk(size);
  }

  size_t size() const { assert(is_reserved(), "must be"); return _size; }
  size_t amount_committed() const { assert(is_reserved(), "must be"); return _committed_boundary - _start;}

  char* at_offset(size_t offset) {
    assert(is_reserved(), "must be");
    char* loc = _start + offset;
    assert(loc < _offset, "must be");
    return loc;
  }

  bool is_reserved() const {
    return _start != nullptr;
  }

  bool reserve_memory() {
    if (!is_reserved()) {
      char* addr = reserve_virtual_address_range();
      if (addr != nullptr) {
        this->_start = addr;
        assert(is_aligned(this->_start, this->_chunk_size), "must be");
        this->_offset = _start;
        return true;
      }
    }
    return false;
  }

  void register_virtual_memory_usage(VirtualMemoryTracker& tracker);
};

// A static array which is backed by a NMTContiguousAllocator.
// The IndexType is used in order to minimize the size of index references to this array.
template<typename T, typename IType>
class NMTStaticArray {
protected:
  using IndexType = IType;
  using ThisArray = NMTStaticArray<T, IndexType>;
  NMTContiguousAllocator _allocator;
  IndexType _num_allocated;
  const static size_t _max_reserved_size =
      sizeof(T) * static_cast<size_t>(std::numeric_limits<IndexType>::max());

public:

  NMTStaticArray(size_t size = 0)
  : _allocator(size == 0 ? _max_reserved_size : size, mtNMT),
    _num_allocated(0) {}

  // Snapshotting constructor
  NMTStaticArray(const ThisArray& original)
  : _allocator(original._allocator),
    _num_allocated(original._num_allocated) {}

  T* adr_at(IndexType index) {
    if (_num_allocated <= index) {
      IndexType number_of_indices_to_allocate = index - _num_allocated + 1;
      char* ret = _allocator.alloc(number_of_indices_to_allocate * sizeof(T));
      if (ret == nullptr) {
        return nullptr;
      }
      _num_allocated += number_of_indices_to_allocate;
      // Initialize the memory
      T* base = reinterpret_cast<T*>(_allocator.at_offset(0));
      for (size_t mm = _num_allocated; mm <= index; mm++) {
        new (&base[mm]) T();
      }
    }
    char* offset = _allocator.at_offset(sizeof(T) * index);
    return (T*)offset;
  }

  const T* adr_at(IndexType index) const {
    return const_cast<ThisArray*>(this)->adr_at((IndexType)index);
  }

  T& operator[](IndexType i) {
    return *adr_at(i);
  }

  const T& operator[](IndexType i) const {
    return *const_cast<ThisArray*>(this)->adr_at(i);
  }

  T& operator[](int i) {
    assert(i <= std::numeric_limits<IndexType>::max(), "must be");
    return *adr_at((IndexType)i);
  }

  const T& operator[](int i) const {
    assert(i <= std::numeric_limits<IndexType>::max(), "must be");
    return *const_cast<ThisArray*>(this)->adr_at((IndexType)i);
  }


  IndexType number_of_tags_allocated() {
    return _num_allocated;
  }

  bool is_valid() {
    return _allocator.is_reserved();
  }
};

#endif // SHARE_NMT_CONTIGUOUSALLOCATOR_HPP
