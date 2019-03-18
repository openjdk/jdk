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

#ifndef SHARE_GC_Z_ZARRAY_HPP
#define SHARE_GC_Z_ZARRAY_HPP

#include "memory/allocation.hpp"

template <typename T>
class ZArray {
private:
  static const size_t initial_capacity = 32;

  T*     _array;
  size_t _size;
  size_t _capacity;

  // Copy and assignment are not allowed
  ZArray(const ZArray<T>& array);
  ZArray<T>& operator=(const ZArray<T>& array);

  void expand(size_t new_capacity);

public:
  ZArray();
  ~ZArray();

  size_t size() const;
  bool is_empty() const;

  T at(size_t index) const;

  void add(T value);
  void transfer(ZArray<T>* from);
  void clear();
};

template <typename T, bool parallel>
class ZArrayIteratorImpl : public StackObj {
private:
  ZArray<T>* const _array;
  size_t           _next;

public:
  ZArrayIteratorImpl(ZArray<T>* array);

  bool next(T* elem);
};

// Iterator types
#define ZARRAY_SERIAL      false
#define ZARRAY_PARALLEL    true

template <typename T>
class ZArrayIterator : public ZArrayIteratorImpl<T, ZARRAY_SERIAL> {
public:
  ZArrayIterator(ZArray<T>* array) :
      ZArrayIteratorImpl<T, ZARRAY_SERIAL>(array) {}
};

template <typename T>
class ZArrayParallelIterator : public ZArrayIteratorImpl<T, ZARRAY_PARALLEL> {
public:
  ZArrayParallelIterator(ZArray<T>* array) :
      ZArrayIteratorImpl<T, ZARRAY_PARALLEL>(array) {}
};

#endif // SHARE_GC_Z_ZARRAY_HPP
