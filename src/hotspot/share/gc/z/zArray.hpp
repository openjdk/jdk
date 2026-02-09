/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cppstdlib/type_traits.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "utilities/growableArray.hpp"

template<typename T> class ZArray;
class ZLock;

template <typename T>
class ZArraySlice : public GrowableArrayView<T> {
  friend class ZArray<T>;
  friend class ZArray<std::remove_const_t<T>>;
  friend class ZArraySlice<std::remove_const_t<T>>;
  friend class ZArraySlice<const T>;

private:
  ZArraySlice(T* data, int len);

public:
  ZArraySlice<T> slice_front(int end);
  ZArraySlice<const T> slice_front(int end) const;

  ZArraySlice<T> slice_back(int start);
  ZArraySlice<const T> slice_back(int start) const;

  ZArraySlice<T> slice(int start, int end);
  ZArraySlice<const T> slice(int start, int end) const;

  operator ZArraySlice<const T>() const;
};

template <typename T>
class ZArray : public GrowableArrayCHeap<T, mtGC> {
public:
  using GrowableArrayCHeap<T, mtGC>::GrowableArrayCHeap;

  ZArraySlice<T> slice_front(int end);
  ZArraySlice<const T> slice_front(int end) const;

  ZArraySlice<T> slice_back(int start);
  ZArraySlice<const T> slice_back(int start) const;

  ZArraySlice<T> slice(int start, int end);
  ZArraySlice<const T> slice(int start, int end) const;

  operator ZArraySlice<T>();
  operator ZArraySlice<const T>() const;
};

template <typename T, bool Parallel>
class ZArrayIteratorImpl : public StackObj {
private:
  using NextType = std::conditional_t<Parallel, Atomic<size_t>, size_t>;

  NextType       _next;
  const size_t   _end;
  const T* const _array;

  bool next_serial(size_t* index);
  bool next_parallel(size_t* index);

public:
  ZArrayIteratorImpl(const T* array, size_t length);
  ZArrayIteratorImpl(const ZArray<T>* array);

  bool next(T* elem);

  template <typename Function, typename... Args>
  bool next_if(T* elem, Function predicate, Args&&... args);

  bool next_index(size_t* index);

  T index_to_elem(size_t index);
};

template <typename T> using ZArrayIterator = ZArrayIteratorImpl<T, false /* Parallel */>;
template <typename T> using ZArrayParallelIterator = ZArrayIteratorImpl<T, true /* Parallel */>;

template <typename T>
class ZActivatedArray {
private:
  typedef typename std::remove_extent<T>::type ItemT;

  ZLock*         _lock;
  uint64_t       _count;
  ZArray<ItemT*> _array;

public:
  explicit ZActivatedArray(bool locked = true);
  ~ZActivatedArray();

  void activate();
  template <typename Function>
  void deactivate_and_apply(Function function);

  bool is_activated() const;
  bool add_if_activated(ItemT* item);
};

#endif // SHARE_GC_Z_ZARRAY_HPP
