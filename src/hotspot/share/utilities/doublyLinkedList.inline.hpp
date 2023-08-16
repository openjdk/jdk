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

inline DoublyLinkedListNode::DoublyLinkedListNode()
  : _next(this),
    _prev(this) { }

inline DoublyLinkedListNode::~DoublyLinkedListNode() {
  verify_links_unlinked();
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

template <typename NodeTraits>
inline void DoublyLinkedList<NodeTraits>::verify_head() const {
  _head.verify_links();
}

template <typename NodeTraits>
inline DoublyLinkedList<NodeTraits>::~DoublyLinkedList() = default;

template <typename NodeTraits>
inline void DoublyLinkedList<NodeTraits>::insert(Node* before, Node* node) {
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


template <typename NodeTraits>
inline DoublyLinkedListNode* DoublyLinkedList<NodeTraits>::cast_to_inner(T* elem) const {
  return NodeTraits::to_node_ptr(elem);
}

template <typename NodeTraits>
inline typename NodeTraits::ValueType* DoublyLinkedList<NodeTraits>::cast_to_outer(Node* node) const {
  return NodeTraits::to_value_ptr(node);
}

template <typename NodeTraits>
inline DoublyLinkedList<NodeTraits>::DoublyLinkedList()
  : _head(),
    _size(0) {
  verify_head();
}

template <typename NodeTraits>
inline size_t DoublyLinkedList<NodeTraits>::size() const {
  verify_head();
  return _size;
}

template <typename NodeTraits>
inline bool DoublyLinkedList<NodeTraits>::is_empty() const {
  return size() == 0;
}

template <typename NodeTraits>
inline typename NodeTraits::ValueType* DoublyLinkedList<NodeTraits>::first() const {
  return is_empty() ? nullptr : cast_to_outer(_head._next);
}

template <typename NodeTraits>
inline typename NodeTraits::ValueType* DoublyLinkedList<NodeTraits>::last() const {
  return is_empty() ? nullptr : cast_to_outer(_head._prev);
}

template <typename NodeTraits>
inline DoublyLinkedListNode* DoublyLinkedList<NodeTraits>::next(Node* elem) const {
  verify_head();

  return elem->_next;
}

template <typename NodeTraits>
inline DoublyLinkedListNode* DoublyLinkedList<NodeTraits>::prev(Node* elem) const {
  verify_head();

  return elem->_prev;
}

template <typename NodeTraits>
inline void DoublyLinkedList<NodeTraits>::insert_first(T* elem) {
  insert(&_head, cast_to_inner(elem));
}

template <typename NodeTraits>
inline void DoublyLinkedList<NodeTraits>::insert_last(T* elem) {
  insert(_head._prev, cast_to_inner(elem));
}

template <typename NodeTraits>
inline void DoublyLinkedList<NodeTraits>::insert_before(T* before, T* elem) {
  insert(cast_to_inner(before)->_prev, cast_to_inner(elem));
}

template <typename NodeTraits>
inline void DoublyLinkedList<NodeTraits>::insert_after(T* after, T* elem) {
  insert(cast_to_inner(after), cast_to_inner(elem));
}

template <typename NodeTraits>
inline void DoublyLinkedList<NodeTraits>::remove(T* elem) {
  verify_head();

  Node* const node = cast_to_inner(elem);
  node->verify_links_linked();

  Node* const next = node->_next;
  Node* const prev = node->_prev;
  next->verify_links_linked();
  prev->verify_links_linked();

  node->_next = prev->_next;
  node->_prev = next->_prev;
  node->verify_links_unlinked();

  next->_prev = prev;
  prev->_next = next;
  next->verify_links();
  prev->verify_links();

  assert(_size > 0, "Sanity check!");
  _size--;
}

template <typename NodeTraits>
inline typename NodeTraits::ValueType* DoublyLinkedList<NodeTraits>::remove_first() {
  T* elem = first();
  if (elem != nullptr) {
    remove(elem);
  }
  return elem;
}

template <typename NodeTraits>
inline typename NodeTraits::ValueType* DoublyLinkedList<NodeTraits>::remove_last() {
  T* elem = last();
  if (elem != nullptr) {
    remove(elem);
  }
  return elem;
}

template <typename NodeTraits>
inline typename DoublyLinkedList<NodeTraits>::Iterator DoublyLinkedList<NodeTraits>::begin() {
 return Iterator(this, _head._next);
}

template <typename NodeTraits>
inline typename DoublyLinkedList<NodeTraits>::Iterator DoublyLinkedList<NodeTraits>::end() {
 return Iterator(this, &_head);
}

template <typename NodeTraits>
inline DoublyLinkedList<NodeTraits>::Iterator::Iterator(const DoublyLinkedList<NodeTraits>* list, DoublyLinkedListNode* start)
  : _list(list),
    _cur_node(start) {}

template <typename NodeTraits>
inline DoublyLinkedList<NodeTraits>::RemoveIterator::RemoveIterator(DoublyLinkedList<NodeTraits>* list, bool forward_iterate)
  : _list(list),
    _forward(forward_iterate) {}


template <typename NodeTraits>
inline bool DoublyLinkedList<NodeTraits>::RemoveIterator::next(T** elem) {
  *elem = _forward ? _list->remove_first() : _list->remove_last();
  return *elem != nullptr;
}

#endif // SHARE_UTILITIES_DOUBLYLINKEDLIST_INLINE_HPP
