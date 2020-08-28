/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

template <typename T>
class ZArray : public GrowableArrayCHeap<T, mtGC> {
public:
  ZArray();

  void transfer(ZArray<T>* from);
};

template <typename T, bool parallel>
class ZArrayIteratorImpl : public StackObj {
private:
  ZArray<T>* const _array;
  int              _next;

public:
  ZArrayIteratorImpl(ZArray<T>* array);

  bool next(T* elem);
};

// Iterator types
#define ZARRAY_SERIAL      false
#define ZARRAY_PARALLEL    true

template <typename T> using ZArrayIterator = ZArrayIteratorImpl<T, ZARRAY_SERIAL>;
template <typename T> using ZArrayParallelIterator = ZArrayIteratorImpl<T, ZARRAY_PARALLEL>;

#endif // SHARE_GC_Z_ZARRAY_HPP
