/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZLIST_HPP
#define SHARE_GC_Z_ZLIST_HPP

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"

template <typename T> class ZList;

// Element in a doubly linked list
template <typename T>
class ZListNode {
  friend class ZList<T>;

private:
  ZListNode* _next;
  ZListNode* _prev;

  ZListNode(ZListNode* next, ZListNode* prev) :
      _next(next),
      _prev(prev) {}

  void set_unused() {
    _next = NULL;
    _prev = NULL;
  }

public:
  ZListNode() {
    set_unused();
  }

  ~ZListNode() {
    set_unused();
  }

  bool is_unused() const {
    return _next == NULL && _prev == NULL;
  }
};

// Doubly linked list
template <typename T>
class ZList {
private:
  ZListNode<T> _head;
  size_t       _size;

  // Passing by value and assignment is not allowed
  ZList(const ZList<T>& list);
  ZList<T>& operator=(const ZList<T>& list);

  void verify() const {
    assert(_head._next->_prev == &_head, "List corrupt");
    assert(_head._prev->_next == &_head, "List corrupt");
  }

  void insert(ZListNode<T>* before, ZListNode<T>* node) {
    verify();

    assert(node->is_unused(), "Already in a list");
    node->_prev = before;
    node->_next = before->_next;
    before->_next = node;
    node->_next->_prev = node;

    _size++;
  }

  ZListNode<T>* cast_to_inner(T* elem) const {
    return &elem->_node;
  }

  T* cast_to_outer(ZListNode<T>* node) const {
    return (T*)((uintptr_t)node - offset_of(T, _node));
  }

public:
  ZList() :
      _head(&_head, &_head),
      _size(0) {
    verify();
  }

  size_t size() const {
    verify();
    return _size;
  }

  bool is_empty() const {
    return _size == 0;
  }

  T* first() const {
    return is_empty() ? NULL : cast_to_outer(_head._next);
  }

  T* last() const {
    return is_empty() ? NULL : cast_to_outer(_head._prev);
  }

  T* next(T* elem) const {
    verify();
    ZListNode<T>* next = cast_to_inner(elem)->_next;
    return (next == &_head) ? NULL : cast_to_outer(next);
  }

  T* prev(T* elem) const {
    verify();
    ZListNode<T>* prev = cast_to_inner(elem)->_prev;
    return (prev == &_head) ? NULL : cast_to_outer(prev);
  }

  void insert_first(T* elem) {
    insert(&_head, cast_to_inner(elem));
  }

  void insert_last(T* elem) {
    insert(_head._prev, cast_to_inner(elem));
  }

  void insert_before(T* before, T* elem) {
    insert(cast_to_inner(before)->_prev, cast_to_inner(elem));
  }

  void insert_after(T* after, T* elem) {
    insert(cast_to_inner(after), cast_to_inner(elem));
  }

  void remove(T* elem) {
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

  T* remove_first() {
    T* elem = first();
    if (elem != NULL) {
      remove(elem);
    }

    return elem;
  }

  T* remove_last() {
    T* elem = last();
    if (elem != NULL) {
      remove(elem);
    }

    return elem;
  }

  void transfer(ZList<T>* list) {
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
};

template <typename T, bool forward>
class ZListIteratorImpl : public StackObj {
private:
  ZList<T>* const _list;
  T*              _next;

public:
  ZListIteratorImpl(ZList<T>* list);

  bool next(T** elem);
};

// Iterator types
#define ZLIST_FORWARD        true
#define ZLIST_REVERSE        false

template <typename T>
class ZListIterator : public ZListIteratorImpl<T, ZLIST_FORWARD> {
public:
  ZListIterator(ZList<T>* list) :
      ZListIteratorImpl<T, ZLIST_FORWARD>(list) {}
};

template <typename T>
class ZListReverseIterator : public ZListIteratorImpl<T, ZLIST_REVERSE> {
public:
  ZListReverseIterator(ZList<T>* list) :
      ZListIteratorImpl<T, ZLIST_REVERSE>(list) {}
};

#endif // SHARE_GC_Z_ZLIST_HPP
