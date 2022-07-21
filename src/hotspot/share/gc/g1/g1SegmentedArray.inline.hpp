/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef SHARE_GC_G1_G1SEGMENTEDARRAY_INLINE_HPP
#define SHARE_GC_G1_G1SEGMENTEDARRAY_INLINE_HPP

#include "gc/g1/g1SegmentedArray.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalCounter.inline.hpp"

inline void* G1SegmentedArraySegment::get_new_slot() {
  if (_next_allocate >= _num_slots) {
    return nullptr;
  }
  uint result = Atomic::fetch_and_add(&_next_allocate, 1u, memory_order_relaxed);
  if (result >= _num_slots) {
    return nullptr;
  }
  void* r = _bottom + (size_t)result * _slot_size;
  return r;
}

inline G1SegmentedArraySegment* G1SegmentedArrayFreeList::get() {
  GlobalCounter::CriticalSection cs(Thread::current());

  G1SegmentedArraySegment* result = _list.pop();
  if (result != nullptr) {
    Atomic::dec(&_num_segments, memory_order_relaxed);
    Atomic::sub(&_mem_size, result->mem_size(), memory_order_relaxed);
  }
  return result;
}

#endif //SHARE_GC_G1_G1SEGMENTEDARRAY_INLINE_HPP
