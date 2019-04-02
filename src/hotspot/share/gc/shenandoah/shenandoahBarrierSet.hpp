/*
 * Copyright (c) 2013, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHBARRIERSET_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHBARRIERSET_HPP

#include "gc/shared/accessBarrierSupport.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahSATBMarkQueueSet.hpp"

class ShenandoahBarrierSetAssembler;

class ShenandoahBarrierSet: public BarrierSet {
private:
  enum ArrayCopyStoreValMode {
    NONE,
    READ_BARRIER,
    WRITE_BARRIER
  };

  ShenandoahHeap* _heap;
  ShenandoahSATBMarkQueueSet _satb_mark_queue_set;

public:
  ShenandoahBarrierSet(ShenandoahHeap* heap);

  static ShenandoahBarrierSetAssembler* assembler();

  inline static ShenandoahBarrierSet* barrier_set() {
    return barrier_set_cast<ShenandoahBarrierSet>(BarrierSet::barrier_set());
  }

  static ShenandoahSATBMarkQueueSet& satb_mark_queue_set() {
    return barrier_set()->_satb_mark_queue_set;
  }

  void print_on(outputStream* st) const;

  bool is_a(BarrierSet::Name bsn);

  bool is_aligned(HeapWord* hw);

  void write_ref_array(HeapWord* start, size_t count);

  template <class T> void
  write_ref_array_pre_work(T* dst, size_t count);

  void write_ref_array_pre(oop* dst, size_t count, bool dest_uninitialized);

  void write_ref_array_pre(narrowOop* dst, size_t count, bool dest_uninitialized);

  // We export this to make it available in cases where the static
  // type of the barrier set is known.  Note that it is non-virtual.
  template <class T> inline void inline_write_ref_field_pre(T* field, oop new_val);

  // These are the more general virtual versions.
  void write_ref_field_pre_work(oop* field, oop new_val);
  void write_ref_field_pre_work(narrowOop* field, oop new_val);
  void write_ref_field_pre_work(void* field, oop new_val);

  void write_ref_field_work(void* v, oop o, bool release = false);
  void write_region(MemRegion mr);

  virtual void on_thread_create(Thread* thread);
  virtual void on_thread_destroy(Thread* thread);
  virtual void on_thread_attach(Thread* thread);
  virtual void on_thread_detach(Thread* thread);

  virtual oop read_barrier(oop src);

  static inline oop resolve_forwarded_not_null(oop p);
  static inline oop resolve_forwarded(oop p);

  virtual oop write_barrier(oop obj);

  oop write_barrier_mutator(oop obj);

  virtual oop storeval_barrier(oop obj);

  virtual void keep_alive_barrier(oop obj);

  bool obj_equals(oop obj1, oop obj2);

#ifdef CHECK_UNHANDLED_OOPS
  bool oop_equals_operator_allowed() { return !ShenandoahVerifyObjectEquals; }
#endif

  void enqueue(oop obj);

private:
  inline bool need_update_refs_barrier();

  template <class T, bool STOREVAL_WRITE_BARRIER>
  void write_ref_array_loop(HeapWord* start, size_t count);

  oop write_barrier_impl(oop obj);

  static void keep_alive_if_weak(DecoratorSet decorators, oop value) {
    assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Reference strength must be known");
    const bool on_strong_oop_ref = (decorators & ON_STRONG_OOP_REF) != 0;
    const bool peek              = (decorators & AS_NO_KEEPALIVE) != 0;
    if (!peek && !on_strong_oop_ref && value != NULL) {
      ShenandoahBarrierSet::barrier_set()->keep_alive_barrier(value);
    }
  }

  template <typename T>
  bool arraycopy_loop_1(T* src, T* dst, size_t length, Klass* bound,
                        bool checkcast, bool satb, bool disjoint, ShenandoahBarrierSet::ArrayCopyStoreValMode storeval_mode);

  template <typename T, bool CHECKCAST>
  bool arraycopy_loop_2(T* src, T* dst, size_t length, Klass* bound,
                        bool satb, bool disjoint, ShenandoahBarrierSet::ArrayCopyStoreValMode storeval_mode);

  template <typename T, bool CHECKCAST, bool SATB>
  bool arraycopy_loop_3(T* src, T* dst, size_t length, Klass* bound,
                        bool disjoint, ShenandoahBarrierSet::ArrayCopyStoreValMode storeval_mode);

  template <typename T, bool CHECKCAST, bool SATB, ShenandoahBarrierSet::ArrayCopyStoreValMode STOREVAL_MODE>
  bool arraycopy_loop(T* src, T* dst, size_t length, Klass* bound, bool disjoint);

  template <typename T, bool CHECKCAST, bool SATB, ShenandoahBarrierSet::ArrayCopyStoreValMode STOREVAL_MODE>
  bool arraycopy_element(T* cur_src, T* cur_dst, Klass* bound, Thread* const thread, ShenandoahMarkingContext* const ctx);

public:
  // Callbacks for runtime accesses.
  template <DecoratorSet decorators, typename BarrierSetT = ShenandoahBarrierSet>
  class AccessBarrier: public BarrierSet::AccessBarrier<decorators, BarrierSetT> {
    typedef BarrierSet::AccessBarrier<decorators, BarrierSetT> Raw;

  public:
    // Primitive heap accesses. These accessors get resolved when
    // IN_HEAP is set (e.g. when using the HeapAccess API), it is
    // not an oop_* overload, and the barrier strength is AS_NORMAL.
    template <typename T>
    static T load_in_heap(T* addr) {
      ShouldNotReachHere();
      return Raw::template load<T>(addr);
    }

    template <typename T>
    static T load_in_heap_at(oop base, ptrdiff_t offset) {
      base = ShenandoahBarrierSet::resolve_forwarded(base);
      return Raw::template load_at<T>(base, offset);
    }

    template <typename T>
    static void store_in_heap(T* addr, T value) {
      ShouldNotReachHere();
      Raw::store(addr, value);
    }

    template <typename T>
    static void store_in_heap_at(oop base, ptrdiff_t offset, T value) {
      base = ShenandoahBarrierSet::barrier_set()->write_barrier(base);
      Raw::store_at(base, offset, value);
    }

    template <typename T>
    static T atomic_cmpxchg_in_heap(T new_value, T* addr, T compare_value) {
      ShouldNotReachHere();
      return Raw::atomic_cmpxchg(new_value, addr, compare_value);
    }

    template <typename T>
    static T atomic_cmpxchg_in_heap_at(T new_value, oop base, ptrdiff_t offset, T compare_value) {
      base = ShenandoahBarrierSet::barrier_set()->write_barrier(base);
      return Raw::atomic_cmpxchg_at(new_value, base, offset, compare_value);
    }

    template <typename T>
    static T atomic_xchg_in_heap(T new_value, T* addr) {
      ShouldNotReachHere();
      return Raw::atomic_xchg(new_value, addr);
    }

    template <typename T>
    static T atomic_xchg_in_heap_at(T new_value, oop base, ptrdiff_t offset) {
      base = ShenandoahBarrierSet::barrier_set()->write_barrier(base);
      return Raw::atomic_xchg_at(new_value, base, offset);
    }

    template <typename T>
    static void arraycopy_in_heap(arrayOop src_obj, size_t src_offset_in_bytes, T* src_raw,
                                  arrayOop dst_obj, size_t dst_offset_in_bytes, T* dst_raw,
                                  size_t length);

    // Heap oop accesses. These accessors get resolved when
    // IN_HEAP is set (e.g. when using the HeapAccess API), it is
    // an oop_* overload, and the barrier strength is AS_NORMAL.
    template <typename T>
    static oop oop_load_in_heap(T* addr) {
      // ShouldNotReachHere();
      oop value = Raw::template oop_load<oop>(addr);
      keep_alive_if_weak(decorators, value);
      return value;
    }

    static oop oop_load_in_heap_at(oop base, ptrdiff_t offset) {
      base = ShenandoahBarrierSet::resolve_forwarded(base);
      oop value = Raw::template oop_load_at<oop>(base, offset);
      keep_alive_if_weak(AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset), value);
      return value;
    }

    template <typename T>
    static void oop_store_in_heap(T* addr, oop value) {
      const bool keep_alive = (decorators & AS_NO_KEEPALIVE) == 0;
      if (keep_alive) {
        ShenandoahBarrierSet::barrier_set()->write_ref_field_pre_work(addr, value);
      }
      Raw::oop_store(addr, value);
    }

    static void oop_store_in_heap_at(oop base, ptrdiff_t offset, oop value) {
      base = ShenandoahBarrierSet::barrier_set()->write_barrier(base);
      value = ShenandoahBarrierSet::barrier_set()->storeval_barrier(value);

      oop_store_in_heap(AccessInternal::oop_field_addr<decorators>(base, offset), value);
    }

    template <typename T>
    static oop oop_atomic_cmpxchg_in_heap(oop new_value, T* addr, oop compare_value);

    static oop oop_atomic_cmpxchg_in_heap_at(oop new_value, oop base, ptrdiff_t offset, oop compare_value) {
      base = ShenandoahBarrierSet::barrier_set()->write_barrier(base);
      new_value = ShenandoahBarrierSet::barrier_set()->storeval_barrier(new_value);
      return oop_atomic_cmpxchg_in_heap(new_value, AccessInternal::oop_field_addr<decorators>(base, offset), compare_value);
    }

    template <typename T>
    static oop oop_atomic_xchg_in_heap(oop new_value, T* addr);

    static oop oop_atomic_xchg_in_heap_at(oop new_value, oop base, ptrdiff_t offset) {
      base = ShenandoahBarrierSet::barrier_set()->write_barrier(base);
      new_value = ShenandoahBarrierSet::barrier_set()->storeval_barrier(new_value);
      return oop_atomic_xchg_in_heap(new_value, AccessInternal::oop_field_addr<decorators>(base, offset));
    }

    template <typename T>
    static bool oop_arraycopy_in_heap(arrayOop src_obj, size_t src_offset_in_bytes, T* src_raw,
                                      arrayOop dst_obj, size_t dst_offset_in_bytes, T* dst_raw,
                                      size_t length);

    // Clone barrier support
    static void clone_in_heap(oop src, oop dst, size_t size);

    // Needed for loads on non-heap weak references
    template <typename T>
    static oop oop_load_not_in_heap(T* addr) {
      oop value = Raw::oop_load_not_in_heap(addr);
      keep_alive_if_weak(decorators, value);
      return value;
    }

    static oop resolve(oop obj) {
      return ShenandoahBarrierSet::barrier_set()->write_barrier(obj);
    }

    static bool equals(oop o1, oop o2) {
      return ShenandoahBarrierSet::barrier_set()->obj_equals(o1, o2);
    }

  };

};

template<>
struct BarrierSet::GetName<ShenandoahBarrierSet> {
  static const BarrierSet::Name value = BarrierSet::ShenandoahBarrierSet;
};

template<>
struct BarrierSet::GetType<BarrierSet::ShenandoahBarrierSet> {
  typedef ::ShenandoahBarrierSet type;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHBARRIERSET_HPP
