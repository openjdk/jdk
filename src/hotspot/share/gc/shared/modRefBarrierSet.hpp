/*
 * Copyright (c) 2000, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_MODREFBARRIERSET_HPP
#define SHARE_VM_GC_SHARED_MODREFBARRIERSET_HPP

#include "gc/shared/barrierSet.hpp"
#include "memory/memRegion.hpp"

class Klass;

class ModRefBarrierSet: public BarrierSet {
protected:
  ModRefBarrierSet(const BarrierSet::FakeRtti& fake_rtti)
    : BarrierSet(fake_rtti.add_tag(BarrierSet::ModRef)) { }
  ~ModRefBarrierSet() { }

public:
  template <DecoratorSet decorators, typename T>
  inline void write_ref_field_pre(T* addr) {}

  template <DecoratorSet decorators, typename T>
  inline void write_ref_field_post(T *addr, oop new_value) {}

  // Causes all refs in "mr" to be assumed to be modified.
  virtual void invalidate(MemRegion mr) = 0;

  // The caller guarantees that "mr" contains no references.  (Perhaps it's
  // objects have been moved elsewhere.)
  virtual void clear(MemRegion mr) = 0;

  // The ModRef abstraction introduces pre and post barriers
  template <DecoratorSet decorators, typename BarrierSetT>
  class AccessBarrier: public BarrierSet::AccessBarrier<decorators, BarrierSetT> {
    typedef BarrierSet::AccessBarrier<decorators, BarrierSetT> Raw;

  public:
    template <typename T>
    static void oop_store_in_heap(T* addr, oop value);
    template <typename T>
    static oop oop_atomic_cmpxchg_in_heap(oop new_value, T* addr, oop compare_value);
    template <typename T>
    static oop oop_atomic_xchg_in_heap(oop new_value, T* addr);

    template <typename T>
    static bool oop_arraycopy_in_heap(arrayOop src_obj, arrayOop dst_obj, T* src, T* dst, size_t length);

    static void clone_in_heap(oop src, oop dst, size_t size);

    static void oop_store_in_heap_at(oop base, ptrdiff_t offset, oop value) {
      oop_store_in_heap(AccessInternal::oop_field_addr<decorators>(base, offset), value);
    }

    static oop oop_atomic_xchg_in_heap_at(oop new_value, oop base, ptrdiff_t offset) {
      return oop_atomic_xchg_in_heap(new_value, AccessInternal::oop_field_addr<decorators>(base, offset));
    }

    static oop oop_atomic_cmpxchg_in_heap_at(oop new_value, oop base, ptrdiff_t offset, oop compare_value) {
      return oop_atomic_cmpxchg_in_heap(new_value, AccessInternal::oop_field_addr<decorators>(base, offset), compare_value);
    }
  };
};

template<>
struct BarrierSet::GetName<ModRefBarrierSet> {
  static const BarrierSet::Name value = BarrierSet::ModRef;
};

#endif // SHARE_VM_GC_SHARED_MODREFBARRIERSET_HPP
