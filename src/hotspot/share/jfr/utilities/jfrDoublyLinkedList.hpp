/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_UTILITIES_JFRDOUBLYLINKEDLIST_HPP
#define SHARE_JFR_UTILITIES_JFRDOUBLYLINKEDLIST_HPP

#include "memory/allocation.hpp"

template <typename T>
class JfrDoublyLinkedList {
 private:
  T* _head;
  T* _tail;
  size_t _count;

  T** list_head() { return &_head; }
  T** list_tail() { return &_tail; }

 public:
  typedef T Node;
  JfrDoublyLinkedList() : _head(nullptr), _tail(nullptr), _count(0) {}
  T* head() const { return _head; }
  T* tail() const { return _tail; }
  size_t count() const { return _count; }
  T* clear(bool return_tail = false);
  T* remove(T* const node);
  void prepend(T* const node);
  void append(T* const node);
  void append_list(T* const head_node, T* const tail_node, size_t count);
  bool in_list(const T* const target_node) const;
  bool locate(const T* start_node, const T* const target_node) const;
};

template <typename T>
inline void JfrDoublyLinkedList<T>::prepend(T* const node) {
  assert(node != nullptr, "invariant");
  node->set_prev(nullptr);
  assert(!in_list(node), "already in list error");
  T** lh = list_head();
  if (*lh != nullptr) {
    (*lh)->set_prev(node);
    node->set_next(*lh);
  } else {
    T** lt = list_tail();
    assert(*lt == nullptr, "invariant");
    *lt = node;
    node->set_next(nullptr);
    assert(tail() == node, "invariant");
    assert(node->next() == nullptr, "invariant");
  }
  *lh = node;
  ++_count;
  assert(head() == node, "head error");
  assert(in_list(node), "not in list error");
  assert(node->prev() == nullptr, "invariant");
}

template <typename T>
void JfrDoublyLinkedList<T>::append(T* const node) {
  assert(node != nullptr, "invariant");
  node->set_next(nullptr);
  assert(!in_list(node), "already in list error");
  T** lt = list_tail();
  if (*lt != nullptr) {
    // already an existing tail
    node->set_prev(*lt);
    (*lt)->set_next(node);
  } else {
    // if no tail, also update head
    assert(*lt == nullptr, "invariant");
    T** lh = list_head();
    assert(*lh == nullptr, "invariant");
    node->set_prev(nullptr);
    *lh = node;
    assert(head() == node, "invariant");
  }
  *lt = node;
  ++_count;
  assert(tail() == node, "invariant");
  assert(in_list(node), "not in list error");
  assert(node->next() == nullptr, "invariant");
}

template <typename T>
T* JfrDoublyLinkedList<T>::remove(T* const node) {
  assert(node != nullptr, "invariant");
  assert(in_list(node), "invariant");
  T* const prev = (T*)node->prev();
  T* const next = (T*)node->next();
  if (prev == nullptr) {
    assert(head() == node, "head error");
    if (next != nullptr) {
      next->set_prev(nullptr);
    } else {
      assert(next == nullptr, "invariant");
      assert(tail() == node, "tail error");
      T** lt = list_tail();
      *lt = nullptr;
      assert(tail() == nullptr, "invariant");
    }
    T** lh = list_head();
    *lh = next;
    assert(head() == next, "invariant");
  } else {
    assert(prev != nullptr, "invariant");
    if (next == nullptr) {
      assert(tail() == node, "tail error");
      T** lt = list_tail();
      *lt = prev;
      assert(tail() == prev, "invariant");
    } else {
       next->set_prev(prev);
    }
    prev->set_next(next);
  }
  --_count;
  assert(!in_list(node), "still in list error");
  return node;
}

template <typename T>
T* JfrDoublyLinkedList<T>::clear(bool return_tail /* false */) {
  T* const node = return_tail ? tail() : head();
  T** l = list_head();
  *l = nullptr;
  l = list_tail();
  *l = nullptr;
  _count = 0;
  assert(head() == nullptr, "invariant");
  assert(tail() == nullptr, "invariant");
  return node;
}

template <typename T>
bool JfrDoublyLinkedList<T>::locate(const T* node, const T* const target) const {
  assert(target != nullptr, "invariant");
  while (node != nullptr) {
    if (node == target) {
      return true;
    }
    node = (T*)node->next();
  }
  return false;
}

template <typename T>
bool JfrDoublyLinkedList<T>::in_list(const T* const target) const {
  assert(target != nullptr, "invariant");
  return locate(head(), target);
}

template <typename T>
inline void validate_count_param(T* node, size_t count_param) {
  assert(node != nullptr, "invariant");
  size_t count = 0;
  while (node) {
    ++count;
    node = (T*)node->next();
  }
  assert(count_param == count, "invariant");
}

template <typename T>
void JfrDoublyLinkedList<T>::append_list(T* const head_node, T* const tail_node, size_t count) {
  assert(head_node != nullptr, "invariant");
  assert(!in_list(head_node), "already in list error");
  assert(tail_node != nullptr, "invariant");
  assert(!in_list(tail_node), "already in list error");
  assert(tail_node->next() == nullptr, "invariant");
  // ensure passed in list nodes are connected
  assert(locate(head_node, tail_node), "invariant");
  T** lt = list_tail();
  if (*lt != nullptr) {
    head_node->set_prev(*lt);
    (*lt)->set_next(head_node);
  } else {
    // no head
    assert(*lt == nullptr, "invariant");
    T** lh = list_head();
    assert(*lh == nullptr, "invariant");
    head_node->set_prev(nullptr);
    *lh = head_node;
    assert(head() == head_node, "invariant");
  }
  *lt = tail_node;
  const T* node = head_node;
  DEBUG_ONLY(validate_count_param(node, count);)
    _count += count;
  assert(tail() == tail_node, "invariant");
  assert(in_list(tail_node), "not in list error");
  assert(in_list(head_node), "not in list error");
}

#endif // SHARE_JFR_UTILITIES_JFRDOUBLYLINKEDLIST_HPP
