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

#include "gc/g1/g1SegmentedArray.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalCounter.hpp"
#include "utilities/globalCounter.inline.hpp"


// ==== SegmentedArrayBuffer ====

template<MEMFLAGS flag>
SegmentedArrayBuffer<flag>::SegmentedArrayBuffer(uint elem_size, uint num_instances, SegmentedArrayBuffer* next) :
  _elem_size(elem_size), _num_elems(num_instances), _next(next), _next_allocate(0) {

  _buffer = NEW_C_HEAP_ARRAY(char, (size_t)_num_elems * elem_size, mtGCCardSet);
}

template<MEMFLAGS flag>
SegmentedArrayBuffer<flag>::~SegmentedArrayBuffer() {
  FREE_C_HEAP_ARRAY(mtGCCardSet, _buffer);
}

template<MEMFLAGS flag>
void* SegmentedArrayBuffer<flag>::get_new_buffer_elem() {
  if (_next_allocate >= _num_elems) {
    return nullptr;
  }
  uint result = Atomic::fetch_and_add(&_next_allocate, 1u, memory_order_relaxed);
  if (result >= _num_elems) {
    return nullptr;
  }
  void* r = _buffer + (uint)result * _elem_size;
  return r;
}


// ==== SegmentedArrayBufferList ====

template<MEMFLAGS flag>
void SegmentedArrayBufferList<flag>::bulk_add(SegmentedArrayBuffer<flag>& first,
                                              SegmentedArrayBuffer<flag>& last,
                                              size_t num,
                                              size_t mem_size) {
  _list.prepend(first, last);
  Atomic::add(&_num_buffers, num, memory_order_relaxed);
  Atomic::add(&_mem_size, mem_size, memory_order_relaxed);
}

template<MEMFLAGS flag>
void SegmentedArrayBufferList<flag>::print_on(outputStream* out, const char* prefix) {
  out->print_cr("%s: buffers %zu size %zu",
                prefix, Atomic::load(&_num_buffers), Atomic::load(&_mem_size));
}

template<MEMFLAGS flag>
SegmentedArrayBuffer<flag>* SegmentedArrayBufferList<flag>::get() {
  GlobalCounter::CriticalSection cs(Thread::current());

  SegmentedArrayBuffer<flag>* result = _list.pop();
  if (result != nullptr) {
    Atomic::dec(&_num_buffers, memory_order_relaxed);
    Atomic::sub(&_mem_size, result->mem_size(), memory_order_relaxed);
  }
  return result;
}

template<MEMFLAGS flag>
SegmentedArrayBuffer<flag>* SegmentedArrayBufferList<flag>::get_all(size_t& num_buffers,
                                                                    size_t& mem_size) {
  GlobalCounter::CriticalSection cs(Thread::current());

  SegmentedArrayBuffer<flag>* result = _list.pop_all();
  num_buffers = Atomic::load(&_num_buffers);
  mem_size = Atomic::load(&_mem_size);

  if (result != nullptr) {
    Atomic::sub(&_num_buffers, num_buffers, memory_order_relaxed);
    Atomic::sub(&_mem_size, mem_size, memory_order_relaxed);
  }
  return result;
}

template<MEMFLAGS flag>
void SegmentedArrayBufferList<flag>::free_all() {
  size_t num_freed = 0;
  size_t mem_size_freed = 0;
  SegmentedArrayBuffer<flag>* cur;

  while ((cur = _list.pop()) != nullptr) {
    mem_size_freed += cur->mem_size();
    num_freed++;
    delete cur;
  }

  Atomic::sub(&_num_buffers, num_freed, memory_order_relaxed);
  Atomic::sub(&_mem_size, mem_size_freed, memory_order_relaxed);
}


// ==== G1SegmentedArray ====

template <class Elem, MEMFLAGS flag>
SegmentedArrayBuffer<flag>* G1SegmentedArray<Elem, flag>::create_new_buffer(
  SegmentedArrayBuffer<flag>* const prev) {

  // Take an existing buffer if available.
  SegmentedArrayBuffer<flag>* next = _free_buffer_list->get();
  if (next == nullptr) {
    uint prev_num_elems = (prev != nullptr) ? prev->num_elems() : 0;
    uint num_elems = _alloc_options.next_num_elems(prev_num_elems);
    next = new SegmentedArrayBuffer<flag>(elem_size(), num_elems, prev);
  } else {
    assert(elem_size() == next->elem_size() ,
           "Mismatch %d != %d Elem %zu", elem_size(), next->elem_size(), sizeof(Elem));
    next->reset(prev);
  }

  // Install it as current allocation buffer.
  SegmentedArrayBuffer<flag>* old = Atomic::cmpxchg(&_first, prev, next);
  if (old != prev) {
    // Somebody else installed the buffer, use that one.
    delete next;
    return old;
  } else {
    // Did we install the first element in the list? If so, this is also the last.
    if (prev == nullptr) {
      _last = next;
    }
    // Successfully installed the buffer into the list.
    Atomic::inc(&_num_buffers, memory_order_relaxed);
    Atomic::add(&_mem_size, next->mem_size(), memory_order_relaxed);
    Atomic::add(&_num_available_nodes, next->num_elems(), memory_order_relaxed);
    return next;
  }
}

template <class Elem, MEMFLAGS flag>
uint G1SegmentedArray<Elem, flag>::elem_size() const {
  return _alloc_options.elem_size();
}

template <class Elem, MEMFLAGS flag>
G1SegmentedArray<Elem, flag>::G1SegmentedArray(const char* name,
                 const G1SegmentedArrayAllocOptions& buffer_options,
                 SegmentedArrayBufferList<flag>* free_buffer_list) :
     _alloc_options(buffer_options),
     _num_available_nodes(0),
     _num_allocated_nodes(0),
     _first(nullptr),
     _last(nullptr),
     _num_buffers(0),
     _mem_size(0),
     _free_buffer_list(free_buffer_list) {
  assert(_free_buffer_list != nullptr, "precondition!");
}

template <class Elem, MEMFLAGS flag>
void G1SegmentedArray<Elem, flag>::drop_all() {
  SegmentedArrayBuffer<flag>* cur = Atomic::load_acquire(&_first);

  if (cur != nullptr) {
    assert(_last != nullptr, "If there is at least one element, there must be a last one.");

    SegmentedArrayBuffer<flag>* first = cur;
#ifdef ASSERT
    // Check list consistency.
    SegmentedArrayBuffer<flag>* last = cur;
    uint num_buffers = 0;
    size_t mem_size = 0;
    while (cur != nullptr) {
      mem_size += cur->mem_size();
      num_buffers++;

      SegmentedArrayBuffer<flag>* next = cur->next();
      last = cur;
      cur = next;
    }
#endif
    assert(num_buffers == _num_buffers, "Buffer count inconsistent %u %u", num_buffers, _num_buffers);
    assert(mem_size == _mem_size, "Memory size inconsistent");
    assert(last == _last, "Inconsistent last element");

    _free_buffer_list->bulk_add(*first, *_last, _num_buffers, _mem_size);
  }

  _first = nullptr;
  _last = nullptr;
  _num_buffers = 0;
  _mem_size = 0;
  _num_available_nodes = 0;
  _num_allocated_nodes = 0;
}

template <class Elem, MEMFLAGS flag>
Elem* G1SegmentedArray<Elem, flag>::allocate() {
  SegmentedArrayBuffer<flag>* cur = Atomic::load_acquire(&_first);
  if (cur == nullptr) {
    cur = create_new_buffer(cur);
  }

  while (true) {
    Elem* elem = (Elem*)cur->get_new_buffer_elem();
    if (elem != nullptr) {
      Atomic::inc(&_num_allocated_nodes, memory_order_relaxed);
      guarantee(is_aligned(elem, _alloc_options.alignment()),
                "result " PTR_FORMAT " not aligned at %u", p2i(elem), _alloc_options.alignment());
      return elem;
    }
    // The buffer is full. Next round.
    assert(cur->is_full(), "must be");
    cur = create_new_buffer(cur);
  }
}

template <class Elem, MEMFLAGS flag>
inline uint G1SegmentedArray<Elem, flag>::num_buffers() const {
  return Atomic::load(&_num_buffers);
}

class LengthVisitor {
  uint _total;
public:
  LengthVisitor() : _total(0) {}
  void visit(SegmentedArrayBuffer<mtGC>* node, uint32_t limit) {
    _total += limit;
  }
  uint length() {
    return _total;
  }
};

template <class Elem, MEMFLAGS flag>
uint G1SegmentedArray<Elem, flag>::length() {
  LengthVisitor v;
  iterate_nodes(v);
  return v.length();
}

template <class Elem, MEMFLAGS flag>
template <typename Visitor>
void G1SegmentedArray<Elem, flag>::iterate_nodes(Visitor& v)  {
  SegmentedArrayBuffer<flag>* cur = Atomic::load_acquire(&_first);

  if (cur != nullptr) {
    assert(_last != nullptr, "If there is at least one element, there must be a last one.");

    while (cur != nullptr) {
      uint limit = cur->length();
      v.visit(cur, limit);

      cur = cur->next();
    }
  }
}
