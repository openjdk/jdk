/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_STACK_INLINE_HPP
#define SHARE_VM_UTILITIES_STACK_INLINE_HPP

#include "utilities/stack.hpp"

StackBase::StackBase(size_t segment_size, size_t max_cache_size,
                     size_t max_size):
  _seg_size(segment_size),
  _max_cache_size(max_cache_size),
  _max_size(adjust_max_size(max_size, segment_size))
{
  assert(_max_size % _seg_size == 0, "not a multiple");
}

size_t StackBase::adjust_max_size(size_t max_size, size_t seg_size)
{
  assert(seg_size > 0, "cannot be 0");
  assert(max_size >= seg_size || max_size == 0, "max_size too small");
  const size_t limit = max_uintx - (seg_size - 1);
  if (max_size == 0 || max_size > limit) {
    max_size = limit;
  }
  return (max_size + seg_size - 1) / seg_size * seg_size;
}

template <class E>
Stack<E>::Stack(size_t segment_size, size_t max_cache_size, size_t max_size):
  StackBase(adjust_segment_size(segment_size), max_cache_size, max_size)
{
  reset(true);
}

template <class E>
void Stack<E>::push(E item)
{
  assert(!is_full(), "pushing onto a full stack");
  if (_cur_seg_size == _seg_size) {
    push_segment();
  }
  _cur_seg[_cur_seg_size] = item;
  ++_cur_seg_size;
}

template <class E>
E Stack<E>::pop()
{
  assert(!is_empty(), "popping from an empty stack");
  if (_cur_seg_size == 1) {
    E tmp = _cur_seg[--_cur_seg_size];
    pop_segment();
    return tmp;
  }
  return _cur_seg[--_cur_seg_size];
}

template <class E>
void Stack<E>::clear(bool clear_cache)
{
  free_segments(_cur_seg);
  if (clear_cache) free_segments(_cache);
  reset(clear_cache);
}

template <class E>
size_t Stack<E>::default_segment_size()
{
  // Number of elements that fit in 4K bytes minus the size of two pointers
  // (link field and malloc header).
  return (4096 - 2 * sizeof(E*)) / sizeof(E);
}

template <class E>
size_t Stack<E>::adjust_segment_size(size_t seg_size)
{
  const size_t elem_sz = sizeof(E);
  const size_t ptr_sz = sizeof(E*);
  assert(elem_sz % ptr_sz == 0 || ptr_sz % elem_sz == 0, "bad element size");
  if (elem_sz < ptr_sz) {
    return align_size_up(seg_size * elem_sz, ptr_sz) / elem_sz;
  }
  return seg_size;
}

template <class E>
size_t Stack<E>::link_offset() const
{
  return align_size_up(_seg_size * sizeof(E), sizeof(E*));
}

template <class E>
size_t Stack<E>::segment_bytes() const
{
  return link_offset() + sizeof(E*);
}

template <class E>
E** Stack<E>::link_addr(E* seg) const
{
  return (E**) ((char*)seg + link_offset());
}

template <class E>
E* Stack<E>::get_link(E* seg) const
{
  return *link_addr(seg);
}

template <class E>
E* Stack<E>::set_link(E* new_seg, E* old_seg)
{
  *link_addr(new_seg) = old_seg;
  return new_seg;
}

template <class E>
E* Stack<E>::alloc(size_t bytes)
{
  return (E*) NEW_C_HEAP_ARRAY(char, bytes);
}

template <class E>
void Stack<E>::free(E* addr, size_t bytes)
{
  FREE_C_HEAP_ARRAY(char, (char*) addr);
}

template <class E>
void Stack<E>::push_segment()
{
  assert(_cur_seg_size == _seg_size, "current segment is not full");
  E* next;
  if (_cache_size > 0) {
    // Use a cached segment.
    next = _cache;
    _cache = get_link(_cache);
    --_cache_size;
  } else {
    next = alloc(segment_bytes());
    DEBUG_ONLY(zap_segment(next, true);)
  }
  const bool at_empty_transition = is_empty();
  _cur_seg = set_link(next, _cur_seg);
  _cur_seg_size = 0;
  _full_seg_size += at_empty_transition ? 0 : _seg_size;
  DEBUG_ONLY(verify(at_empty_transition);)
}

template <class E>
void Stack<E>::pop_segment()
{
  assert(_cur_seg_size == 0, "current segment is not empty");
  E* const prev = get_link(_cur_seg);
  if (_cache_size < _max_cache_size) {
    // Add the current segment to the cache.
    DEBUG_ONLY(zap_segment(_cur_seg, false);)
    _cache = set_link(_cur_seg, _cache);
    ++_cache_size;
  } else {
    DEBUG_ONLY(zap_segment(_cur_seg, true);)
    free(_cur_seg, segment_bytes());
  }
  const bool at_empty_transition = prev == NULL;
  _cur_seg = prev;
  _cur_seg_size = _seg_size;
  _full_seg_size -= at_empty_transition ? 0 : _seg_size;
  DEBUG_ONLY(verify(at_empty_transition);)
}

template <class E>
void Stack<E>::free_segments(E* seg)
{
  const size_t bytes = segment_bytes();
  while (seg != NULL) {
    E* const prev = get_link(seg);
    free(seg, bytes);
    seg = prev;
  }
}

template <class E>
void Stack<E>::reset(bool reset_cache)
{
  _cur_seg_size = _seg_size; // So push() will alloc a new segment.
  _full_seg_size = 0;
  _cur_seg = NULL;
  if (reset_cache) {
    _cache_size = 0;
    _cache = NULL;
  }
}

#ifdef ASSERT
template <class E>
void Stack<E>::verify(bool at_empty_transition) const
{
  assert(size() <= max_size(), "stack exceeded bounds");
  assert(cache_size() <= max_cache_size(), "cache exceeded bounds");
  assert(_cur_seg_size <= segment_size(), "segment index exceeded bounds");

  assert(_full_seg_size % _seg_size == 0, "not a multiple");
  assert(at_empty_transition || is_empty() == (size() == 0), "mismatch");
  assert((_cache == NULL) == (cache_size() == 0), "mismatch");

  if (is_empty()) {
    assert(_cur_seg_size == segment_size(), "sanity");
  }
}

template <class E>
void Stack<E>::zap_segment(E* seg, bool zap_link_field) const
{
  if (!ZapStackSegments) return;
  const size_t zap_bytes = segment_bytes() - (zap_link_field ? 0 : sizeof(E*));
  uint32_t* cur = (uint32_t*)seg;
  const uint32_t* end = cur + zap_bytes / sizeof(uint32_t);
  while (cur < end) {
    *cur++ = 0xfadfaded;
  }
}
#endif

template <class E>
E* ResourceStack<E>::alloc(size_t bytes)
{
  return (E*) resource_allocate_bytes(bytes);
}

template <class E>
void ResourceStack<E>::free(E* addr, size_t bytes)
{
  resource_free_bytes((char*) addr, bytes);
}

template <class E>
void StackIterator<E>::sync()
{
  _full_seg_size = _stack._full_seg_size;
  _cur_seg_size = _stack._cur_seg_size;
  _cur_seg = _stack._cur_seg;
}

template <class E>
E* StackIterator<E>::next_addr()
{
  assert(!is_empty(), "no items left");
  if (_cur_seg_size == 1) {
    E* addr = _cur_seg;
    _cur_seg = _stack.get_link(_cur_seg);
    _cur_seg_size = _stack.segment_size();
    _full_seg_size -= _stack.segment_size();
    return addr;
  }
  return _cur_seg + --_cur_seg_size;
}

#endif // SHARE_VM_UTILITIES_STACK_INLINE_HPP
