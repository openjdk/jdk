/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZLIST_INLINE_HPP
#define SHARE_GC_Z_ZLIST_INLINE_HPP

#include "gc/z/zList.hpp"
#include "utilities/debug.hpp"

template <typename T>
inline ZListNode<T>::ZListNode(ZListNode* next, ZListNode* prev) :
    _next(next),
    _prev(prev) {}

template <typename T>
inline void ZListNode<T>::set_unused() {
  _next = NULL;
  _prev = NULL;
}

template <typename T>
inline ZListNode<T>::ZListNode() {
  set_unused();
}

template <typename T>
inline ZListNode<T>::~ZListNode() {
  set_unused();
}

template <typename T>
inline bool ZListNode<T>::is_unused() const {
  return _next == NULL && _prev == NULL;
}

template <typename T>
inline void ZList<T>::verify() const {
  assert(_head._next->_prev == &_head, "List corrupt");
  assert(_head._prev->_next == &_head, "List corrupt");
}

template <typename T>
inline void ZList<T>::insert(ZListNode<T>* before, ZListNode<T>* node) {
  verify();

  assert(node->is_unused(), "Already in a list");
  node->_prev = before;
  node->_next = before->_next;
  before->_next = node;
  node->_next->_prev = node;

  _size++;
}

template <typename T>
inline ZListNode<T>* ZList<T>::cast_to_inner(T* elem) const {
  return &elem->_node;
}

template <typename T>
inline T* ZList<T>::cast_to_outer(ZListNode<T>* node) const {
  return (T*)((uintptr_t)node - offset_of(T, _node));
}

template <typename T>
inline ZList<T>::ZList() :
    _head(&_head, &_head),
    _size(0) {
  verify();
}

template <typename T>
inline size_t ZList<T>::size() const {
  verify();
  return _size;
}

template <typename T>
inline bool ZList<T>::is_empty() const {
  return _size == 0;
}

template <typename T>
inline T* ZList<T>::first() const {
  return is_empty() ? NULL : cast_to_outer(_head._next);
}

template <typename T>
inline T* ZList<T>::last() const {
  return is_empty() ? NULL : cast_to_outer(_head._prev);
}

template <typename T>
inline T* ZList<T>::next(T* elem) const {
  verify();
  ZListNode<T>* next = cast_to_inner(elem)->_next;
  return (next == &_head) ? NULL : cast_to_outer(next);
}

template <typename T>
inline T* ZList<T>::prev(T* elem) const {
  verify();
  ZListNode<T>* prev = cast_to_inner(elem)->_prev;
  return (prev == &_head) ? NULL : cast_to_outer(prev);
}

template <typename T>
inline void ZList<T>::insert_first(T* elem) {
  insert(&_head, cast_to_inner(elem));
}

template <typename T>
inline void ZList<T>::insert_last(T* elem) {
  insert(_head._prev, cast_to_inner(elem));
}

template <typename T>
inline void ZList<T>::insert_before(T* before, T* elem) {
  insert(cast_to_inner(before)->_prev, cast_to_inner(elem));
}

template <typename T>
inline void ZList<T>::insert_after(T* after, T* elem) {
  insert(cast_to_inner(after), cast_to_inner(elem));
}

template <typename T>
inline void ZList<T>::remove(T* elem) {
  verify();

  ZListNode<T>* const node = cast_to_inner(elem);
  assert(!node->is_unused(), "Not in a list");

  ZListNode<T>* const next = node->_next;
  ZListNode<T>* const prev = node->_prev;
  assert(next->_prev == node, "List corrupt");
  assert(prev->_next == node, "List corrupt");

  prev->_next = next;
  next->_prev = prev;
  node->set_unused();

  _size--;
}

template <typename T>
inline T* ZList<T>::remove_first() {
  T* elem = first();
  if (elem != NULL) {
    remove(elem);
  }

  return elem;
}

template <typename T>
inline T* ZList<T>::remove_last() {
  T* elem = last();
  if (elem != NULL) {
    remove(elem);
  }

  return elem;
}

template <typename T>
inline void ZList<T>::transfer(ZList<T>* list) {
  verify();

  if (!list->is_empty()) {
    list->_head._next->_prev = _head._prev;
    list->_head._prev->_next = _head._prev->_next;

    _head._prev->_next = list->_head._next;
    _head._prev = list->_head._prev;

    list->_head._next = &list->_head;
    list->_head._prev = &list->_head;

    _size += list->_size;
    list->_size = 0;

    list->verify();
    verify();
  }
}

template <typename T, bool Forward>
inline ZListIteratorImpl<T, Forward>::ZListIteratorImpl(const ZList<T>* list) :
    _list(list),
    _next(Forward ? list->first() : list->last()) {}

template <typename T, bool Forward>
inline bool ZListIteratorImpl<T, Forward>::next(T** elem) {
  if (_next != NULL) {
    *elem = _next;
    _next = Forward ? _list->next(_next) : _list->prev(_next);
    return true;
  }

  // No more elements
  return false;
}

template <typename T, bool Forward>
inline ZListRemoveIteratorImpl<T, Forward>::ZListRemoveIteratorImpl(ZList<T>* list) :
    _list(list) {}

template <typename T, bool Forward>
inline bool ZListRemoveIteratorImpl<T, Forward>::next(T** elem) {
  *elem = Forward ? _list->remove_first() : _list->remove_last();
  return *elem != NULL;
}

template <typename T>
inline ZListIterator<T>::ZListIterator(const ZList<T>* list) :
    ZListIteratorImpl<T, ZLIST_FORWARD>(list) {}

template <typename T>
inline ZListReverseIterator<T>::ZListReverseIterator(const ZList<T>* list) :
    ZListIteratorImpl<T, ZLIST_REVERSE>(list) {}

template <typename T>
inline ZListRemoveIterator<T>::ZListRemoveIterator(ZList<T>* list) :
    ZListRemoveIteratorImpl<T, ZLIST_FORWARD>(list) {}

#endif // SHARE_GC_Z_ZLIST_INLINE_HPP
