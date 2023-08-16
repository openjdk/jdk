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

template<typename NodeTraits>
class DoublyLinkedList;

// Base element in a doubly linked list.
class DoublyLinkedListNode {
  template<typename T>
  friend class DoublyLinkedList;

  DoublyLinkedListNode* _next;
  DoublyLinkedListNode* _prev;

  NONCOPYABLE(DoublyLinkedListNode);

  void verify_links() const;
  void verify_links_linked() const;
  void verify_links_unlinked() const;

public:
  DoublyLinkedListNode();
  ~DoublyLinkedListNode();
};

template <typename T, size_t m_offset>
struct ListNodeTraits {
  using ValueType = T;

  static DoublyLinkedListNode* to_node_ptr(ValueType* elem) {
    return reinterpret_cast<DoublyLinkedListNode*>(
              reinterpret_cast<char*>(elem) + m_offset);
  }

  static ValueType* to_value_ptr(DoublyLinkedListNode* node ) {
    return reinterpret_cast<ValueType*>(
              reinterpret_cast<char*>(node) - m_offset);
  }
};


// The DoublyLinkedList template provides insertion, removal, and traversal of elements in a doubly
// linked list structure. The DoublyLinkedList is configured using NodeTraits which contain
// information on the Nodes in the list. NodeTraits must support the interface:
//    typedef ValueType; - Type of elements in the list
//    static ValueType* to_value_ptr(DoublyLinkedListNode* node ); - return the element that contains the list node.
//    static DoublyLinkedListNode* to_node_ptr(ValueType* elem); - return the DoublyLinkedList node associated with the list.
//
// Note: The DoublyLinkedList does not perform memory allocation or deallocation for the elements.
// Therefore, it is the responsibility of the class template users to manage the memory of the
// elements added or removed from the list.
//
template<typename NodeTraits>
class DoublyLinkedList {
  using Node = DoublyLinkedListNode;
  using T = typename NodeTraits::ValueType;

  Node _head;
  size_t _size;

  NONCOPYABLE(DoublyLinkedList);

  void verify_head() const;

  void insert(Node* before, Node* node);

  Node* cast_to_inner(T* elem) const;
  T* cast_to_outer(Node* node) const;

  Node* next(Node* elem) const;
  Node* prev(Node* elem) const;

public:
  DoublyLinkedList();
  ~DoublyLinkedList();

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

  class Iterator;

  class RemoveIterator;

  Iterator begin();
  Iterator end();
};

template <typename NodeTraits>
class DoublyLinkedList<NodeTraits>::Iterator : public StackObj {
  using T = typename NodeTraits::ValueType;
  friend class DoublyLinkedList<NodeTraits>;

  const DoublyLinkedList<NodeTraits>* const _list;
  DoublyLinkedListNode* _cur_node;

  Iterator(const DoublyLinkedList<NodeTraits>* list, DoublyLinkedListNode* start);

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

  T* operator*() { return _list->cast_to_outer(_cur_node); }

  bool operator==(const Iterator& rhs) {
    assert(_list == rhs._list, "iterator belongs to different List");
    return _cur_node == rhs._cur_node;
  }

  bool operator!=(const Iterator& rhs) {
    assert(_list == rhs._list, "iterator belongs to different List");
    return _cur_node != rhs._cur_node;
  }
};

template <typename NodeTraits>
class DoublyLinkedList<NodeTraits>::RemoveIterator : public StackObj {
  using T = typename NodeTraits::ValueType;
private:
  DoublyLinkedList<NodeTraits>* const _list;
  const bool _forward;

public:
  explicit RemoveIterator(DoublyLinkedList<NodeTraits>* list, bool forward_iterate = true);

  bool next(T** elem);
};

#endif // SHARE_UTILITIES_DOUBLYLINKEDLIST_HPP
