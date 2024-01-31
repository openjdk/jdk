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

#ifndef SHARE_GC_X_XLIST_HPP
#define SHARE_GC_X_XLIST_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

template <typename T> class XList;

// Element in a doubly linked list
template <typename T>
class XListNode {
  friend class XList<T>;

private:
  XListNode<T>* _next;
  XListNode<T>* _prev;

  NONCOPYABLE(XListNode);

  void verify_links() const;
  void verify_links_linked() const;
  void verify_links_unlinked() const;

public:
  XListNode();
  ~XListNode();
};

// Doubly linked list
template <typename T>
class XList {
private:
  XListNode<T> _head;
  size_t       _size;

  NONCOPYABLE(XList);

  void verify_head() const;

  void insert(XListNode<T>* before, XListNode<T>* node);

  XListNode<T>* cast_to_inner(T* elem) const;
  T* cast_to_outer(XListNode<T>* node) const;

public:
  XList();

  size_t size() const;
  bool is_empty() const;

  T* first() const;
  T* last() const;
  T* next(T* elem) const;
  T* prev(T* elem) const;

  void insert_first(T* elem);
  void insert_last(T* elem);
  void insert_before(T* before, T* elem);
  void insert_after(T* after, T* elem);

  void remove(T* elem);
  T* remove_first();
  T* remove_last();
};

template <typename T, bool Forward>
class XListIteratorImpl : public StackObj {
private:
  const XList<T>* const _list;
  T*                    _next;

public:
  XListIteratorImpl(const XList<T>* list);

  bool next(T** elem);
};

template <typename T, bool Forward>
class XListRemoveIteratorImpl : public StackObj {
private:
  XList<T>* const _list;

public:
  XListRemoveIteratorImpl(XList<T>* list);

  bool next(T** elem);
};

template <typename T> using XListIterator = XListIteratorImpl<T, true /* Forward */>;
template <typename T> using XListReverseIterator = XListIteratorImpl<T, false /* Forward */>;
template <typename T> using XListRemoveIterator = XListRemoveIteratorImpl<T, true /* Forward */>;

#endif // SHARE_GC_X_XLIST_HPP
