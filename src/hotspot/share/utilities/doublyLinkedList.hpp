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

#ifndef SHARE_UTILITIES_DOUBLYLINKEDLIST_HPP
#define SHARE_UTILITIES_DOUBLYLINKEDLIST_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

template <typename T>
class DoublyLinkedList;

// Element in a doubly linked list

class DoublyLinkedListNode {
  template<typename T>
  friend class DoublyLinkedList;

  DoublyLinkedListNode* _next;
  DoublyLinkedListNode* _prev;

  NONCOPYABLE(DoublyLinkedListNode);

  void swap(DoublyLinkedListNode* rhs);

  void verify_links() const;
  void verify_links_linked() const;
  void verify_links_unlinked() const;

protected:
  ~DoublyLinkedListNode();

public:
  DoublyLinkedListNode();
};

//
// The DoublyLinkedList template provides efficient insertion, removal, and traversal of elements in a doubly linked list structure.
// Each node in the list contains an element of type T, and the nodes are connected bidirectionally.
// The class supports forward and backward traversal, as well as insertion and removal operations at the beginning, end,
// or any position within the list.
//
// \tparam T The type of elements stored in the linked list. It must be default-constructible and derived from DoublyLinkedListNode.
//
// Note: The DoublyLinkedList class does not perform memory allocation or deallocation for the elements.
//       Therefore, it is the responsibility of the class template users to manage the memory of the elements added or removed from the list.
//
template <typename T>
class DoublyLinkedList {
  static_assert(std::is_default_constructible<T>::value,
                "DoublyLinkedList requires default-constructible elements");

  static_assert(std::is_base_of<DoublyLinkedListNode, T>::value,
                "DoublyLinkedList requires elements derived from DoublyLinkedListNode");

  T _head;
  size_t _size;

  NONCOPYABLE(DoublyLinkedList);

  void verify_head() const;

  void insert(DoublyLinkedListNode* before, DoublyLinkedListNode* node);

  T* cast_to_outer(DoublyLinkedListNode* node) const;

  T* next(T* elem) const;
  T* prev(T* elem) const;

public:
  DoublyLinkedList();

  size_t size() const;
  bool is_empty() const;

  T* first() const;
  T* last() const;

  void insert_first(T* elem);
  void insert_last(T* elem);
  void insert_before(T* before, T* elem);
  void insert_after(T* after, T* elem);

  void remove(T* elem);
  T* remove_first();
  T* remove_last();

  void swap(DoublyLinkedList& other);

  class Iterator;

  class RemoveIterator;

  Iterator begin();
  Iterator end();
};

template <typename T>
class DoublyLinkedList<T>::Iterator : public StackObj {
  friend class DoublyLinkedList<T>;

  const DoublyLinkedList<T>* const _list;
  T* _cur_node;

  Iterator(const DoublyLinkedList<T>* list, T* start) :
    _list(list),
    _cur_node(start)
  { }

public:
  Iterator& operator++() {
    _cur_node = _list->next(_cur_node);
    return *this;
  }

  Iterator operator++(int) {
    Iterator tmp = *this;
    ++(*this);
    return tmp;
  }

  Iterator& operator--() {
    assert(_cur_node != nullptr, "Sanity");
    _cur_node = _list->prev(_cur_node);
    return *this;
  }

  Iterator operator--(int) {
    Iterator tmp = *this;
    --(*this);
    return tmp;
  }

  T* operator*() { return _cur_node; }

  bool operator==(const Iterator& rhs) {
    assert(_list == rhs._list, "iterator belongs to different List");
    return _cur_node == rhs._cur_node;
  }

  bool operator!=(const Iterator& rhs) {
    assert(_list == rhs._list, "iterator belongs to different List");
    return _cur_node != rhs._cur_node;
  }
};

template <typename T>
class DoublyLinkedList<T>::RemoveIterator : public StackObj {
private:
  DoublyLinkedList<T>* const _list;
  const bool _forward;

public:
  explicit RemoveIterator(DoublyLinkedList<T>* list, bool forward_iterate = true);

  bool next(T** elem);
};

#endif // SHARE_UTILITIES_DOUBLYLINKEDLIST_HPP
