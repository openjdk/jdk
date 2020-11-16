/*
 * Copyright (c) 2015, 2020, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHBARRIERSET_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHBARRIERSET_INLINE_HPP

#include "gc/shared/barrierSet.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.inline.hpp"
#include "gc/shenandoah/shenandoahEvacOOMHandler.inline.hpp"
#include "gc/shenandoah/shenandoahForwarding.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/oop.inline.hpp"

inline oop ShenandoahBarrierSet::resolve_forwarded_not_null(oop p) {
  return ShenandoahForwarding::get_forwardee(p);
}

inline oop ShenandoahBarrierSet::resolve_forwarded(oop p) {
  if (p != NULL) {
    return resolve_forwarded_not_null(p);
  } else {
    return p;
  }
}

inline oop ShenandoahBarrierSet::resolve_forwarded_not_null_mutator(oop p) {
  return ShenandoahForwarding::get_forwardee_mutator(p);
}

template <class T>
inline oop ShenandoahBarrierSet::load_reference_barrier_mutator(oop obj, T* load_addr) {
  assert(ShenandoahLoadRefBarrier, "should be enabled");
  shenandoah_assert_in_cset(load_addr, obj);

  oop fwd = resolve_forwarded_not_null_mutator(obj);
  if (obj == fwd) {
    assert(_heap->is_evacuation_in_progress(),
           "evac should be in progress");
    Thread* const t = Thread::current();
    ShenandoahEvacOOMScope scope(t);
    fwd = _heap->evacuate_object(obj, t);
  }

  if (load_addr != NULL && fwd != obj) {
    // Since we are here and we know the load address, update the reference.
    ShenandoahHeap::cas_oop(fwd, load_addr, obj);
  }

  return fwd;
}

template <class T>
inline oop ShenandoahBarrierSet::load_reference_barrier_native(oop obj, T* load_addr) {
  if (CompressedOops::is_null(obj)) {
    return NULL;
  }

  ShenandoahMarkingContext* const marking_context = _heap->marking_context();
  if (_heap->is_concurrent_weak_root_in_progress() && !marking_context->is_marked(obj)) {
    Thread* thr = Thread::current();
    if (thr->is_Java_thread()) {
      return NULL;
    } else {
      return obj;
    }
  }

  oop fwd = load_reference_barrier_not_null(obj);
  if (ShenandoahSelfFixing && load_addr != NULL && fwd != obj) {
    // Since we are here and we know the load address, update the reference.
    ShenandoahHeap::cas_oop(fwd, load_addr, obj);
  }

  return fwd;
}

inline void ShenandoahBarrierSet::enqueue(oop obj) {
  assert(obj != NULL, "checked by caller");
  assert(_satb_mark_queue_set.is_active(), "only get here when SATB active");

  // Filter marked objects before hitting the SATB queues. The same predicate would
  // be used by SATBMQ::filter to eliminate already marked objects downstream, but
  // filtering here helps to avoid wasteful SATB queueing work to begin with.
  if (!_heap->requires_marking(obj)) return;

  ShenandoahThreadLocalData::satb_mark_queue(Thread::current()).enqueue_known_active(obj);
}

template <DecoratorSet decorators, typename T>
inline void ShenandoahBarrierSet::satb_barrier(T *field) {
  if (HasDecorator<decorators, IS_DEST_UNINITIALIZED>::value ||
      HasDecorator<decorators, AS_NO_KEEPALIVE>::value) {
    return;
  }
  if (ShenandoahSATBBarrier && _heap->is_concurrent_mark_in_progress()) {
    T heap_oop = RawAccess<>::oop_load(field);
    if (!CompressedOops::is_null(heap_oop)) {
      enqueue(CompressedOops::decode(heap_oop));
    }
  }
}

inline void ShenandoahBarrierSet::satb_enqueue(oop value) {
  assert(value != NULL, "checked before");
  if (ShenandoahSATBBarrier && _heap->is_concurrent_mark_in_progress()) {
    enqueue(value);
  }
}

inline void ShenandoahBarrierSet::storeval_barrier(oop obj) {
  if (ShenandoahStoreValEnqueueBarrier && obj != NULL && _heap->is_concurrent_mark_in_progress()) {
    enqueue(obj);
  }
}

inline void ShenandoahBarrierSet::keep_alive_if_weak(DecoratorSet decorators, oop value) {
  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Reference strength must be known");
  assert(value != NULL, "checked by caller");
  const bool on_strong_oop_ref = (decorators & ON_STRONG_OOP_REF) != 0;
  const bool peek              = (decorators & AS_NO_KEEPALIVE) != 0;
  if (!peek && !on_strong_oop_ref) {
    satb_enqueue(value);
  }
}

template <DecoratorSet decorators>
inline void ShenandoahBarrierSet::keep_alive_if_weak(oop value) {
  assert(value != NULL, "checked by caller");
  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Reference strength must be known");
  if (!HasDecorator<decorators, ON_STRONG_OOP_REF>::value &&
      !HasDecorator<decorators, AS_NO_KEEPALIVE>::value) {
    satb_enqueue(value);
  }
}

template <DecoratorSet decorators, typename T>
inline void ShenandoahBarrierSet::write_ref_field_post(T* field, oop newVal) {
  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    volatile CardTable::CardValue* byte = card_table()->byte_for(field);
    if (card_table()->scanned_concurrently()) {
      // Perform a releasing store if the card table is scanned concurrently
      Atomic::release_store(byte, CardTable::dirty_card_val());
    } else {
      *byte = CardTable::dirty_card_val();
    }
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_not_in_heap(T* addr) {
  oop value = Raw::oop_load_not_in_heap(addr);
  if (value != NULL) {
    ShenandoahBarrierSet *const bs = ShenandoahBarrierSet::barrier_set();
    value = bs->load_reference_barrier_native(value, addr);
    if (value != NULL) {
      bs->keep_alive_if_weak<decorators>(value);
    }
  }
  return value;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_in_heap(T* addr) {
  oop value = Raw::oop_load_in_heap(addr);
  if (value != NULL) {
    ShenandoahBarrierSet *const bs = ShenandoahBarrierSet::barrier_set();
    value = bs->load_reference_barrier_not_null(value);
    bs->keep_alive_if_weak<decorators>(value);
  }
  return value;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_in_heap_at(oop base, ptrdiff_t offset) {
  oop value = Raw::oop_load_in_heap_at(base, offset);
  if (value != NULL) {
    ShenandoahBarrierSet *const bs = ShenandoahBarrierSet::barrier_set();
    value = bs->load_reference_barrier_not_null(value);
    bs->keep_alive_if_weak(AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset),
                           value);
  }
  return value;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_not_in_heap(T* addr, oop value) {
  shenandoah_assert_marked_if(NULL, value, !CompressedOops::is_null(value) && ShenandoahHeap::heap()->is_evacuation_in_progress());
  ShenandoahBarrierSet* const bs = ShenandoahBarrierSet::barrier_set();
  bs->storeval_barrier(value);
  bs->satb_barrier<decorators>(addr);
  Raw::oop_store(addr, value);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_in_heap(T* addr, oop value) {
  shenandoah_assert_not_in_cset_loc_except(addr, ShenandoahHeap::heap()->cancelled_gc());
  shenandoah_assert_not_forwarded_except  (addr, value, value == NULL || ShenandoahHeap::heap()->cancelled_gc() || !ShenandoahHeap::heap()->is_concurrent_mark_in_progress());
  shenandoah_assert_not_in_cset_except    (addr, value, value == NULL || ShenandoahHeap::heap()->cancelled_gc() || !ShenandoahHeap::heap()->is_concurrent_mark_in_progress());

  oop_store_not_in_heap(addr, value);
  ShenandoahBarrierSet::barrier_set()->write_ref_field_post<decorators>(addr, value);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_in_heap_at(oop base, ptrdiff_t offset, oop value) {
  oop_store_in_heap(AccessInternal::oop_field_addr<decorators>(base, offset), value);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_not_in_heap(T* addr, oop compare_value, oop new_value) {
  ShenandoahBarrierSet* bs = ShenandoahBarrierSet::barrier_set();
  bs->storeval_barrier(new_value);

  oop res;
  oop expected = compare_value;
  do {
    compare_value = expected;
    res = Raw::oop_atomic_cmpxchg(addr, compare_value, new_value);
    expected = res;
  } while ((compare_value != expected) && (resolve_forwarded(compare_value) == resolve_forwarded(expected)));

  // Note: We don't need a keep-alive-barrier here. We already enqueue any loaded reference for SATB anyway,
  // because it must be the previous value.
  if (res != NULL) {
    res = ShenandoahBarrierSet::barrier_set()->load_reference_barrier_not_null(res);
    bs->satb_enqueue(res);
  }
  return res;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_in_heap(T* addr, oop compare_value, oop new_value) {
  oop result = oop_atomic_cmpxchg_not_in_heap(addr, compare_value, new_value);
  ShenandoahBarrierSet::barrier_set()->write_ref_field_post<decorators>(addr, new_value);
  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_in_heap_at(oop base, ptrdiff_t offset, oop compare_value, oop new_value) {
  return oop_atomic_cmpxchg_in_heap(AccessInternal::oop_field_addr<decorators>(base, offset), compare_value, new_value);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_not_in_heap(T* addr, oop new_value) {
  ShenandoahBarrierSet* bs = ShenandoahBarrierSet::barrier_set();
  bs->storeval_barrier(new_value);

  oop previous = Raw::oop_atomic_xchg(addr, new_value);

  // Note: We don't need a keep-alive-barrier here. We already enqueue any loaded reference for SATB anyway,
  // because it must be the previous value.
  if (previous != NULL) {
    previous = ShenandoahBarrierSet::barrier_set()->load_reference_barrier_not_null(previous);
    bs->satb_enqueue(previous);
  }
  return previous;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_in_heap(T* addr, oop new_value) {
  oop result = oop_atomic_xchg_not_in_heap(addr, new_value);
  ShenandoahBarrierSet::barrier_set()->write_ref_field_post<decorators>(addr, new_value);
  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_in_heap_at(oop base, ptrdiff_t offset, oop new_value) {
  return oop_atomic_xchg_in_heap(AccessInternal::oop_field_addr<decorators>(base, offset), new_value);
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
  T* src = arrayOopDesc::obj_offset_to_raw(src_obj, src_offset_in_bytes, src_raw);
  T* dst = arrayOopDesc::obj_offset_to_raw(dst_obj, dst_offset_in_bytes, dst_raw);

  ShenandoahBarrierSet* bs = ShenandoahBarrierSet::barrier_set();
  bs->arraycopy_barrier(src, dst, length);
  bool result = Raw::oop_arraycopy_in_heap(src_obj, src_offset_in_bytes, src_raw, dst_obj, dst_offset_in_bytes, dst_raw, length);
  bs->write_ref_array((HeapWord*) dst, length);
  return result;
}

template <class T, bool HAS_FWD, bool EVAC, bool ENQUEUE>
void ShenandoahBarrierSet::arraycopy_work(T* src, size_t count) {
  assert(HAS_FWD == _heap->has_forwarded_objects(), "Forwarded object status is sane");

  Thread* thread = Thread::current();
  SATBMarkQueue& queue = ShenandoahThreadLocalData::satb_mark_queue(thread);
  ShenandoahMarkingContext* ctx = _heap->marking_context();
  const ShenandoahCollectionSet* const cset = _heap->collection_set();
  T* end = src + count;
  for (T* elem_ptr = src; elem_ptr < end; elem_ptr++) {
    T o = RawAccess<>::oop_load(elem_ptr);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      if (HAS_FWD && cset->is_in(obj)) {
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
void ShenandoahBarrierSet::arraycopy_barrier(T* src, T* dst, size_t count) {
  if (count == 0) {
    return;
  }
  int gc_state = _heap->gc_state();
  if ((gc_state & ShenandoahHeap::MARKING) != 0) {
    arraycopy_marking(src, dst, count);
  } else if ((gc_state & ShenandoahHeap::EVACUATION) != 0) {
    arraycopy_evacuation(src, count);
  } else if ((gc_state & ShenandoahHeap::UPDATEREFS) != 0) {
    arraycopy_update(src, count);
  }
}

template <class T>
void ShenandoahBarrierSet::arraycopy_marking(T* src, T* dst, size_t count) {
  assert(_heap->is_concurrent_mark_in_progress(), "only during marking");
  T* array = ShenandoahSATBBarrier ? dst : src;
  if (!_heap->marking_context()->allocated_after_mark_start(reinterpret_cast<HeapWord*>(array))) {
    arraycopy_work<T, false, false, true>(array, count);
  }
}

inline bool ShenandoahBarrierSet::need_bulk_update(HeapWord* ary) {
  return ary < _heap->heap_region_containing(ary)->get_update_watermark();
}

template <class T>
void ShenandoahBarrierSet::arraycopy_evacuation(T* src, size_t count) {
  assert(_heap->is_evacuation_in_progress(), "only during evacuation");
  if (need_bulk_update(reinterpret_cast<HeapWord*>(src))) {
    ShenandoahEvacOOMScope oom_evac;
    arraycopy_work<T, true, true, false>(src, count);
  }
}

template <class T>
void ShenandoahBarrierSet::arraycopy_update(T* src, size_t count) {
  assert(_heap->is_update_refs_in_progress(), "only during update-refs");
  if (need_bulk_update(reinterpret_cast<HeapWord*>(src))) {
    arraycopy_work<T, true, false, false>(src, count);
  }
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHBARRIERSET_INLINE_HPP
