/*
 * Copyright (c) 2015, 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHBARRIERSET_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHBARRIERSET_INLINE_HPP

#include "gc/shared/barrierSet.hpp"
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.inline.hpp"
#include "gc/shenandoah/shenandoahForwarding.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/oop.inline.hpp"

inline oop ShenandoahBarrierSet::resolve_forwarded_not_null(oop p) {
  return ShenandoahForwarding::get_forwardee(p);
}

inline oop ShenandoahBarrierSet::resolve_forwarded(oop p) {
  if (((HeapWord*) p) != NULL) {
    return resolve_forwarded_not_null(p);
  } else {
    return p;
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_in_heap(T* addr) {
  oop value = Raw::oop_load_in_heap(addr);
  value = ShenandoahBarrierSet::barrier_set()->load_reference_barrier(value);
  keep_alive_if_weak(decorators, value);
  return value;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_in_heap_at(oop base, ptrdiff_t offset) {
  oop value = Raw::oop_load_in_heap_at(base, offset);
  value = ShenandoahBarrierSet::barrier_set()->load_reference_barrier(value);
  keep_alive_if_weak(AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset), value);
  return value;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_not_in_heap(T* addr) {
  oop value = Raw::oop_load_not_in_heap(addr);
  value = ShenandoahBarrierSet::barrier_set()->oop_load_from_native_barrier(value);
  keep_alive_if_weak(decorators, value);
  return value;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_in_heap(T* addr, oop value) {
  ShenandoahBarrierSet::barrier_set()->storeval_barrier(value);
  const bool keep_alive = (decorators & AS_NO_KEEPALIVE) == 0;
  if (keep_alive) {
    ShenandoahBarrierSet::barrier_set()->write_ref_field_pre_work(addr, value);
  }
  Raw::oop_store_in_heap(addr, value);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_in_heap_at(oop base, ptrdiff_t offset, oop value) {
  oop_store_in_heap(AccessInternal::oop_field_addr<decorators>(base, offset), value);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_not_in_heap(T* addr, oop value) {
  shenandoah_assert_marked_if(NULL, value, !CompressedOops::is_null(value) && ShenandoahHeap::heap()->is_evacuation_in_progress());
  Raw::oop_store(addr, value);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_not_in_heap(oop new_value, T* addr, oop compare_value) {
  oop res;
  oop expected = compare_value;
  do {
    compare_value = expected;
    res = Raw::oop_atomic_cmpxchg(new_value, addr, compare_value);
    expected = res;
  } while ((compare_value != expected) && (resolve_forwarded(compare_value) == resolve_forwarded(expected)));
  if (res != NULL) {
    return ShenandoahBarrierSet::barrier_set()->load_reference_barrier_not_null(res);
  } else {
    return res;
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_in_heap_impl(oop new_value, T* addr, oop compare_value) {
  ShenandoahBarrierSet::barrier_set()->storeval_barrier(new_value);
  oop result = oop_atomic_cmpxchg_not_in_heap(new_value, addr, compare_value);
  const bool keep_alive = (decorators & AS_NO_KEEPALIVE) == 0;
  if (keep_alive && ShenandoahSATBBarrier && !CompressedOops::is_null(result) &&
      (result == compare_value) &&
      ShenandoahHeap::heap()->is_concurrent_mark_in_progress()) {
    ShenandoahBarrierSet::barrier_set()->enqueue(result);
  }
  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_in_heap(oop new_value, T* addr, oop compare_value) {
  oop result = oop_atomic_cmpxchg_in_heap_impl(new_value, addr, compare_value);
  keep_alive_if_weak(decorators, result);
  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_in_heap_at(oop new_value, oop base, ptrdiff_t offset, oop compare_value) {
  oop result = oop_atomic_cmpxchg_in_heap_impl(new_value, AccessInternal::oop_field_addr<decorators>(base, offset), compare_value);
  keep_alive_if_weak(AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset), result);
  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_not_in_heap(oop new_value, T* addr) {
  oop previous = Raw::oop_atomic_xchg(new_value, addr);
  if (previous != NULL) {
    return ShenandoahBarrierSet::barrier_set()->load_reference_barrier_not_null(previous);
  } else {
    return previous;
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_in_heap_impl(oop new_value, T* addr) {
  ShenandoahBarrierSet::barrier_set()->storeval_barrier(new_value);
  oop result = oop_atomic_xchg_not_in_heap(new_value, addr);
  const bool keep_alive = (decorators & AS_NO_KEEPALIVE) == 0;
  if (keep_alive && ShenandoahSATBBarrier && !CompressedOops::is_null(result) &&
      ShenandoahHeap::heap()->is_concurrent_mark_in_progress()) {
    ShenandoahBarrierSet::barrier_set()->enqueue(result);
  }
  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_in_heap(oop new_value, T* addr) {
  oop result = oop_atomic_xchg_in_heap_impl(new_value, addr);
  keep_alive_if_weak(addr, result);
  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_in_heap_at(oop new_value, oop base, ptrdiff_t offset) {
  oop result = oop_atomic_xchg_in_heap_impl(new_value, AccessInternal::oop_field_addr<decorators>(base, offset));
  keep_alive_if_weak(AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset), result);
  return result;
}

// Clone barrier support
template <DecoratorSet decorators, typename BarrierSetT>
void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::clone_in_heap(oop src, oop dst, size_t size) {
  if (ShenandoahCloneBarrier) {
    ShenandoahBarrierSet::barrier_set()->clone_barrier_runtime(src);
  }
  Raw::clone(src, dst, size);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
bool ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_arraycopy_in_heap(arrayOop src_obj, size_t src_offset_in_bytes, T* src_raw,
                                                                                         arrayOop dst_obj, size_t dst_offset_in_bytes, T* dst_raw,
                                                                                         size_t length) {
  ShenandoahBarrierSet* bs = ShenandoahBarrierSet::barrier_set();
  bs->arraycopy_pre(arrayOopDesc::obj_offset_to_raw(src_obj, src_offset_in_bytes, src_raw),
                    arrayOopDesc::obj_offset_to_raw(dst_obj, dst_offset_in_bytes, dst_raw),
                    length);
  return Raw::oop_arraycopy_in_heap(src_obj, src_offset_in_bytes, src_raw, dst_obj, dst_offset_in_bytes, dst_raw, length);
}

template <class T, bool HAS_FWD, bool EVAC, bool ENQUEUE>
void ShenandoahBarrierSet::arraycopy_work(T* src, size_t count) {
  Thread* thread = Thread::current();
  SATBMarkQueue& queue = ShenandoahThreadLocalData::satb_mark_queue(thread);
  ShenandoahMarkingContext* ctx = _heap->marking_context();
  const ShenandoahCollectionSet* const cset = _heap->collection_set();
  T* end = src + count;
  for (T* elem_ptr = src; elem_ptr < end; elem_ptr++) {
    T o = RawAccess<>::oop_load(elem_ptr);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      if (HAS_FWD && cset->is_in((HeapWord *) obj)) {
        assert(_heap->has_forwarded_objects(), "only get here with forwarded objects");
        oop fwd = resolve_forwarded_not_null(obj);
        if (EVAC && obj == fwd) {
          fwd = _heap->evacuate_object(obj, thread);
        }
        assert(obj != fwd || _heap->cancelled_gc(), "must be forwarded");
        oop witness = ShenandoahHeap::cas_oop(fwd, elem_ptr, o);
        obj = fwd;
      }
      if (ENQUEUE && !ctx->is_marked(obj)) {
        queue.enqueue_known_active(obj);
      }
    }
  }
}

template <class T>
void ShenandoahBarrierSet::arraycopy_pre_work(T* src, T* dst, size_t count) {
  if (_heap->is_concurrent_mark_in_progress()) {
    if (_heap->has_forwarded_objects()) {
      arraycopy_work<T, true, false, true>(dst, count);
    } else {
      arraycopy_work<T, false, false, true>(dst, count);
    }
  }

  arraycopy_update_impl(src, count);
}

void ShenandoahBarrierSet::arraycopy_pre(oop* src, oop* dst, size_t count) {
  arraycopy_pre_work(src, dst, count);
}

void ShenandoahBarrierSet::arraycopy_pre(narrowOop* src, narrowOop* dst, size_t count) {
  arraycopy_pre_work(src, dst, count);
}

template <class T>
void ShenandoahBarrierSet::arraycopy_update_impl(T* src, size_t count) {
  if (_heap->is_evacuation_in_progress()) {
    ShenandoahEvacOOMScope oom_evac;
    arraycopy_work<T, true, true, false>(src, count);
  } else if (_heap->is_concurrent_traversal_in_progress()){
    ShenandoahEvacOOMScope oom_evac;
    arraycopy_work<T, true, true, true>(src, count);
  } else if (_heap->has_forwarded_objects()) {
    arraycopy_work<T, true, false, false>(src, count);
  }
}

void ShenandoahBarrierSet::arraycopy_update(oop* src, size_t count) {
  arraycopy_update_impl(src, count);
}

void ShenandoahBarrierSet::arraycopy_update(narrowOop* src, size_t count) {
  arraycopy_update_impl(src, count);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHBARRIERSET_INLINE_HPP
