/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_DOUBLYLINKEDLIST_INLINE_HPP
#define SHARE_UTILITIES_DOUBLYLINKEDLIST_INLINE_HPP

#include "utilities/doublyLinkedList.hpp"
#include "utilities/debug.hpp"

inline DoublyLinkedListNode::DoublyLinkedListNode() :
    _next(this),
    _prev(this) {}

inline DoublyLinkedListNode::~DoublyLinkedListNode() {
  verify_links_unlinked();
}

inline void DoublyLinkedListNode::swap(DoublyLinkedListNode* rhs) {
  if (this == rhs) {
    return;
  }

  if (this->_next != this) {
    if (rhs->_next != rhs) {
      ::swap(this->_next, rhs->_next);
      ::swap(this->_prev,rhs->_prev);
      this->_next->_prev = this->_prev->_next = this;
      rhs->_next->_prev = rhs->_prev->_next = rhs;
    } else {
      rhs->_next = this->_next;
      rhs->_prev = this->_prev;
      rhs->_next->_prev = rhs->_prev->_next = rhs;
      this->_next = this->_prev = this;
    }
  } else if (rhs->_next != rhs) {
    this->_next = rhs->_next;
    this->_prev = rhs->_prev;
    this->_next->_prev = this->_prev->_next = this;
    rhs->_next = rhs->_prev = rhs;
  }
}

inline void DoublyLinkedListNode::verify_links() const {
  assert(_next->_prev == this, "Corrupt list node");
  assert(_prev->_next == this, "Corrupt list node");
}

inline void DoublyLinkedListNode::verify_links_linked() const {
  assert(_next != this, "Should be in a list");
  assert(_prev != this, "Should be in a list");
  verify_links();
}

inline void DoublyLinkedListNode::verify_links_unlinked() const {
  assert(_next == this, "Should not be in a list");
  assert(_prev == this, "Should not be in a list");
}

template <typename T>
inline void DoublyLinkedList<T>::verify_head() const {
  _head.verify_links();
}

template <typename T>
inline void DoublyLinkedList<T>::insert(DoublyLinkedListNode* before, DoublyLinkedListNode* node) {
  verify_head();

  before->verify_links();
  node->verify_links_unlinked();

  node->_prev = before;
  node->_next = before->_next;
  before->_next = node;
  node->_next->_prev = node;

  before->verify_links_linked();
  node->verify_links_linked();

  _size++;
}

template <typename T>
inline T* DoublyLinkedList<T>::cast_to_outer(DoublyLinkedListNode* node) const {
  return static_cast<T*>(node);
}

template <typename T>
inline DoublyLinkedList<T>::DoublyLinkedList() :
    _head(),
    _size(0) {
  verify_head();
}

template <typename T>
inline void DoublyLinkedList<T>::swap(DoublyLinkedList& other) {

  _head.swap(&other._head);
  ::swap(this->_size, other._size);

  other.verify_head();
  verify_head();
}

template <typename T>
inline size_t DoublyLinkedList<T>::size() const {
  verify_head();
  return _size;
}

template <typename T>
inline bool DoublyLinkedList<T>::is_empty() const {
  return size() == 0;
}

template <typename T>
inline T* DoublyLinkedList<T>::first() const {
  return is_empty() ? nullptr : cast_to_outer(_head._next);
}

template <typename T>
inline T* DoublyLinkedList<T>::last() const {
  return is_empty() ? nullptr : cast_to_outer(_head._prev);
}

template <typename T>
inline T* DoublyLinkedList<T>::next(T* elem) const {
  verify_head();

  return cast_to_outer(elem->_next);
}

template <typename T>
inline T* DoublyLinkedList<T>::prev(T* elem) const {
  verify_head();

  return cast_to_outer(elem->_prev);
}

template <typename T>
inline void DoublyLinkedList<T>::insert_first(T* elem) {
  insert(&_head, elem);
}

template <typename T>
inline void DoublyLinkedList<T>::insert_last(T* elem) {
  insert(_head._prev, elem);
}

template <typename T>
inline void DoublyLinkedList<T>::insert_before(T* before, T* elem) {
  insert(before->_prev, elem);
}

template <typename T>
inline void DoublyLinkedList<T>::insert_after(T* after, T* elem) {
  insert(after, elem);
}

template <typename T>
inline void DoublyLinkedList<T>::remove(T* elem) {
  verify_head();

  elem->verify_links_linked();

  DoublyLinkedListNode* const next = elem->_next;
  DoublyLinkedListNode* const prev = elem->_prev;
  next->verify_links_linked();
  prev->verify_links_linked();

  elem->_next = prev->_next;
  elem->_prev = next->_prev;
  elem->verify_links_unlinked();

  next->_prev = prev;
  prev->_next = next;
  next->verify_links();
  prev->verify_links();

  assert(_size > 0, "Sanity check!");
  _size--;
}

template <typename T>
inline T* DoublyLinkedList<T>::remove_first() {
  T* elem = first();
  if (elem != nullptr) {
    remove(elem);
  }
  return elem;
}

template <typename T>
inline T* DoublyLinkedList<T>::remove_last() {
  T* elem = last();
  if (elem != nullptr) {
    remove(elem);
  }
  return elem;
}

template <typename T>
inline typename DoublyLinkedList<T>::Iterator DoublyLinkedList<T>::begin() {
 return Iterator(this, cast_to_outer(_head._next));
}

template <typename T>
inline typename DoublyLinkedList<T>::Iterator DoublyLinkedList<T>::end() {
 return Iterator(this, &_head);
}

template <typename T>
inline DoublyLinkedList<T>::RemoveIterator::RemoveIterator(DoublyLinkedList<T>* list, bool forward_iterate):
    _list(list),
    _forward(forward_iterate) {}


template <typename T>
inline bool DoublyLinkedList<T>::RemoveIterator::next(T** elem) {
  *elem = _forward ? _list->remove_first() : _list->remove_last();
  return *elem != nullptr;
}

#endif // SHARE_UTILITIES_DOUBLYLINKEDLIST_INLINE_HPP
