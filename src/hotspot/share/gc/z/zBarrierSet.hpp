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

#ifndef SHARE_GC_Z_ZBARRIERSET_HPP
#define SHARE_GC_Z_ZBARRIERSET_HPP

#include "gc/shared/barrierSet.hpp"
#include "gc/z/zAddress.hpp"

class ZBarrierSetAssembler;

class ZBarrierSet : public BarrierSet {
private:
  static zpointer store_good(oop obj);

public:
  ZBarrierSet();

  static ZBarrierSetAssembler* assembler();
  static bool barrier_needed(DecoratorSet decorators, BasicType type);

  static void clone_obj_array(objArrayOop src, objArrayOop dst, size_t size);

  virtual void on_thread_create(Thread* thread);
  virtual void on_thread_destroy(Thread* thread);
  virtual void on_thread_attach(Thread* thread);
  virtual void on_thread_detach(Thread* thread);

  virtual void on_slowpath_allocation_exit(JavaThread* thread, oop new_obj);

  virtual void print_on(outputStream* st) const;

  template <DecoratorSet decorators, typename BarrierSetT = ZBarrierSet>
  class AccessBarrier : public BarrierSet::AccessBarrier<decorators, BarrierSetT> {
  private:
    typedef BarrierSet::AccessBarrier<decorators, BarrierSetT> Raw;

    template <DecoratorSet expected>
    static void verify_decorators_present();

    template <DecoratorSet expected>
    static void verify_decorators_absent();

    static zpointer* field_addr(oop base, ptrdiff_t offset);

    static zaddress load_barrier(zpointer* p, zpointer o);
    static zaddress load_barrier_on_unknown_oop_ref(oop base, ptrdiff_t offset, zpointer* p, zpointer o);

    static void store_barrier_heap_with_healing(zpointer* p);
    static void store_barrier_heap_without_healing(zpointer* p);
    static void no_keep_alive_store_barrier_heap(zpointer* p);

    static void store_barrier_native_with_healing(zpointer* p);
    static void store_barrier_native_without_healing(zpointer* p);

    static void unsupported();
    static zaddress load_barrier(narrowOop* p, zpointer o) { unsupported(); return zaddress::null; }
    static zaddress load_barrier_on_unknown_oop_ref(oop base, ptrdiff_t offset, narrowOop* p, zpointer o) { unsupported(); return zaddress::null; }
    static void store_barrier_heap_with_healing(narrowOop* p) { unsupported(); }
    static void store_barrier_heap_without_healing(narrowOop* p)  { unsupported(); }
    static void no_keep_alive_store_barrier_heap(narrowOop* p)  { unsupported(); }
    static void store_barrier_native_with_healing(narrowOop* p)  { unsupported(); }
    static void store_barrier_native_without_healing(narrowOop* p)  { unsupported(); }

    static zaddress oop_copy_one_barriers(zpointer* dst, zpointer* src);
    static bool oop_copy_one_check_cast(zpointer* dst, zpointer* src, Klass* dst_klass);
    static void oop_copy_one(zpointer* dst, zpointer* src);

    static bool oop_arraycopy_in_heap_check_cast(zpointer* dst, zpointer* src, size_t length, Klass* dst_klass);
    static bool oop_arraycopy_in_heap_no_check_cast(zpointer* dst, zpointer* src, size_t length);

  public:
    //
    // In heap
    //
    static oop oop_load_in_heap(zpointer* p);
    static oop oop_load_in_heap(oop* p)       { return oop_load_in_heap((zpointer*)p); };
    static oop oop_load_in_heap(narrowOop* p) { unsupported(); return nullptr; }

    static oop oop_load_in_heap_at(oop base, ptrdiff_t offset);

    static void oop_store_in_heap(zpointer* p, oop value);
    static void oop_store_in_heap(oop* p, oop value)       { oop_store_in_heap((zpointer*)p, value); }
    static void oop_store_in_heap(narrowOop* p, oop value) { unsupported(); }
    static void oop_store_in_heap_at(oop base, ptrdiff_t offset, oop value);

    static void oop_store_not_in_heap(zpointer* p, oop value);
    static void oop_store_not_in_heap(oop* p, oop value)       { oop_store_not_in_heap((zpointer*)p, value); }
    static void oop_store_not_in_heap(narrowOop* p, oop value) { unsupported(); }
    static void oop_store_not_in_heap_at(oop base, ptrdiff_t offset, oop value);

    static oop oop_atomic_cmpxchg_in_heap(zpointer* p, oop compare_value, oop new_value);
    static oop oop_atomic_cmpxchg_in_heap(oop* p, oop compare_value, oop new_value)       { return oop_atomic_cmpxchg_in_heap((zpointer*)p, compare_value, new_value); }
    static oop oop_atomic_cmpxchg_in_heap(narrowOop* p, oop compare_value, oop new_value) { unsupported(); return nullptr; }
    static oop oop_atomic_cmpxchg_in_heap_at(oop base, ptrdiff_t offset, oop compare_value, oop new_value);

    static oop oop_atomic_xchg_in_heap(zpointer* p, oop new_value);
    static oop oop_atomic_xchg_in_heap(oop* p, oop new_value)       { return oop_atomic_xchg_in_heap((zpointer*)p, new_value); }
    static oop oop_atomic_xchg_in_heap(narrowOop* p, oop new_value) { unsupported(); return nullptr; }
    static oop oop_atomic_xchg_in_heap_at(oop base, ptrdiff_t offset, oop new_value);

    static bool oop_arraycopy_in_heap(arrayOop src_obj, size_t src_offset_in_bytes, zpointer* src_raw,
                                      arrayOop dst_obj, size_t dst_offset_in_bytes, zpointer* dst_raw,
                                      size_t length);
    static bool oop_arraycopy_in_heap(arrayOop src_obj, size_t src_offset_in_bytes, oop* src_raw,
                                      arrayOop dst_obj, size_t dst_offset_in_bytes, oop* dst_raw,
                                      size_t length) {
      return oop_arraycopy_in_heap(src_obj, src_offset_in_bytes, (zpointer*)src_raw,
                                   dst_obj, dst_offset_in_bytes, (zpointer*)dst_raw,
                                   length);
    }
    static bool oop_arraycopy_in_heap(arrayOop src_obj, size_t src_offset_in_bytes, narrowOop* src_raw,
                                      arrayOop dst_obj, size_t dst_offset_in_bytes, narrowOop* dst_raw,
                                      size_t length) { unsupported(); return false; }

    static void clone_in_heap(oop src, oop dst, size_t size);

    //
    // Not in heap
    //
    static oop oop_load_not_in_heap(zpointer* p);
    static oop oop_load_not_in_heap(oop* p);
    static oop oop_load_not_in_heap(narrowOop* p) { unsupported(); return nullptr; }

    static oop oop_atomic_cmpxchg_not_in_heap(zpointer* p, oop compare_value, oop new_value);
    static oop oop_atomic_cmpxchg_not_in_heap(oop* p, oop compare_value, oop new_value) {
      return oop_atomic_cmpxchg_not_in_heap((zpointer*)p, compare_value, new_value);
    }
    static oop oop_atomic_cmpxchg_not_in_heap(narrowOop* addr, oop compare_value, oop new_value) { unsupported(); return nullptr; }

    static oop oop_atomic_xchg_not_in_heap(zpointer* p, oop new_value);
    static oop oop_atomic_xchg_not_in_heap(oop* p, oop new_value)       { return oop_atomic_xchg_not_in_heap((zpointer*)p, new_value); }
    static oop oop_atomic_xchg_not_in_heap(narrowOop* p, oop new_value) { unsupported(); return nullptr; }
  };
};

template<> struct BarrierSet::GetName<ZBarrierSet> {
  static const BarrierSet::Name value = BarrierSet::ZBarrierSet;
};

template<> struct BarrierSet::GetType<BarrierSet::ZBarrierSet> {
  typedef ::ZBarrierSet type;
};

#endif // SHARE_GC_Z_ZBARRIERSET_HPP
