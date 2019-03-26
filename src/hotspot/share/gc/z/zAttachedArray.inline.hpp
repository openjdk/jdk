/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZATTACHEDARRAY_INLINE_HPP
#define SHARE_GC_Z_ZATTACHEDARRAY_INLINE_HPP

#include "gc/z/zAttachedArray.hpp"
#include "memory/allocation.hpp"
#include "utilities/align.hpp"

template <typename ObjectT, typename ArrayT>
inline size_t ZAttachedArray<ObjectT, ArrayT>::object_size() {
  return align_up(sizeof(ObjectT), sizeof(ArrayT));
}

template <typename ObjectT, typename ArrayT>
inline void* ZAttachedArray<ObjectT, ArrayT>::alloc(size_t length) {
  const size_t array_size = sizeof(ArrayT) * length;
  char* const addr = AllocateHeap(object_size() + array_size, mtGC);
  ::new (addr + object_size()) ArrayT[length];
  return addr;
}

template <typename ObjectT, typename ArrayT>
inline void ZAttachedArray<ObjectT, ArrayT>::free(ObjectT* obj) {
  FreeHeap(obj);
}

template <typename ObjectT, typename ArrayT>
inline ZAttachedArray<ObjectT, ArrayT>::ZAttachedArray(size_t length) :
    _length(length) {}

template <typename ObjectT, typename ArrayT>
inline uint32_t ZAttachedArray<ObjectT, ArrayT>::length() const {
  return _length;
}

template <typename ObjectT, typename ArrayT>
inline ArrayT* ZAttachedArray<ObjectT, ArrayT>::operator()(const ObjectT* obj) const {
  return reinterpret_cast<ArrayT*>(reinterpret_cast<uintptr_t>(obj) + object_size());
}

#endif // SHARE_GC_Z_ZATTACHEDARRAY_INLINE_HPP
