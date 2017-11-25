/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_MODREFBARRIERSET_INLINE_HPP
#define SHARE_VM_GC_SHARED_MODREFBARRIERSET_INLINE_HPP

#include "gc/shared/modRefBarrierSet.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.hpp"

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline void ModRefBarrierSet::AccessBarrier<decorators, BarrierSetT>::
oop_store_in_heap(T* addr, oop value) {
  BarrierSetT *bs = barrier_set_cast<BarrierSetT>(barrier_set());
  bs->template write_ref_field_pre<decorators>(addr);
  Raw::oop_store(addr, value);
  bs->template write_ref_field_post<decorators>(addr, value);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ModRefBarrierSet::AccessBarrier<decorators, BarrierSetT>::
oop_atomic_cmpxchg_in_heap(oop new_value, T* addr, oop compare_value) {
  BarrierSetT *bs = barrier_set_cast<BarrierSetT>(barrier_set());
  bs->template write_ref_field_pre<decorators>(addr);
  oop result = Raw::oop_atomic_cmpxchg(new_value, addr, compare_value);
  if (result == compare_value) {
    bs->template write_ref_field_post<decorators>(addr, new_value);
  }
  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ModRefBarrierSet::AccessBarrier<decorators, BarrierSetT>::
oop_atomic_xchg_in_heap(oop new_value, T* addr) {
  BarrierSetT *bs = barrier_set_cast<BarrierSetT>(barrier_set());
  bs->template write_ref_field_pre<decorators>(addr);
  oop result = Raw::oop_atomic_xchg(new_value, addr);
  bs->template write_ref_field_post<decorators>(addr, new_value);
  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline bool ModRefBarrierSet::AccessBarrier<decorators, BarrierSetT>::
oop_arraycopy_in_heap(arrayOop src_obj, arrayOop dst_obj, T* src, T* dst, size_t length) {
  BarrierSetT *bs = barrier_set_cast<BarrierSetT>(barrier_set());

  if (!HasDecorator<decorators, ARRAYCOPY_CHECKCAST>::value) {
    // Optimized covariant case
    bs->write_ref_array_pre(dst, (int)length,
                            HasDecorator<decorators, ARRAYCOPY_DEST_NOT_INITIALIZED>::value);
    Raw::oop_arraycopy(src_obj, dst_obj, src, dst, length);
    bs->write_ref_array((HeapWord*)dst, length);
  } else {
    Klass* bound = objArrayOop(dst_obj)->element_klass();
    T* from = src;
    T* end = from + length;
    for (T* p = dst; from < end; from++, p++) {
      T element = *from;
      if (bound->is_instanceof_or_null(element)) {
        bs->template write_ref_field_pre<decorators>(p);
        *p = element;
      } else {
        // We must do a barrier to cover the partial copy.
        const size_t pd = pointer_delta(p, dst, (size_t)heapOopSize);
        // pointer delta is scaled to number of elements (length field in
        // objArrayOop) which we assume is 32 bit.
        assert(pd == (size_t)(int)pd, "length field overflow");
        bs->write_ref_array((HeapWord*)dst, pd);
        return false;
      }
    }
    bs->write_ref_array((HeapWord*)dst, length);
  }
  return true;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ModRefBarrierSet::AccessBarrier<decorators, BarrierSetT>::
clone_in_heap(oop src, oop dst, size_t size) {
  Raw::clone(src, dst, size);
  BarrierSetT *bs = barrier_set_cast<BarrierSetT>(barrier_set());
  bs->write_region(MemRegion((HeapWord*)(void*)dst, size));
}

#endif // SHARE_VM_GC_SHARED_MODREFBARRIERSET_INLINE_HPP
