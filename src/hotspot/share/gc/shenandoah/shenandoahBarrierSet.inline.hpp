/*
 * Copyright (c) 2015, 2022, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shenandoah/shenandoahBarrierSet.hpp"

#include "gc/shared/accessBarrierSupport.inline.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.inline.hpp"
#include "gc/shenandoah/shenandoahEvacOOMHandler.inline.hpp"
#include "gc/shenandoah/shenandoahForwarding.inline.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
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
  if (p != nullptr) {
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
    assert(_heap->is_evacuation_in_progress(), "evac should be in progress");
    Thread* const t = Thread::current();
    ShenandoahEvacOOMScope scope(t);
    fwd = _heap->evacuate_object(obj, t);
  }

  if (load_addr != nullptr && fwd != obj) {
    // Since we are here and we know the load address, update the reference.
    ShenandoahHeap::atomic_update_oop(fwd, load_addr, obj);
  }

  return fwd;
}

inline oop ShenandoahBarrierSet::load_reference_barrier(oop obj) {
  if (!ShenandoahLoadRefBarrier) {
    return obj;
  }
  if (_heap->has_forwarded_objects() && _heap->in_collection_set(obj)) {
    // Subsumes null-check
    assert(obj != nullptr, "cset check must have subsumed null-check");
    oop fwd = resolve_forwarded_not_null(obj);
    if (obj == fwd && _heap->is_evacuation_in_progress()) {
      Thread* t = Thread::current();
      ShenandoahEvacOOMScope oom_evac_scope(t);
      return _heap->evacuate_object(obj, t);
    }
    return fwd;
  }
  return obj;
}

template <class T>
inline oop ShenandoahBarrierSet::load_reference_barrier(DecoratorSet decorators, oop obj, T* load_addr) {
  if (obj == nullptr) {
    return nullptr;
  }

  // Prevent resurrection of unreachable phantom (i.e. weak-native) references.
  if ((decorators & ON_PHANTOM_OOP_REF) != 0 &&
      _heap->is_concurrent_weak_root_in_progress() &&
      _heap->is_in_active_generation(obj) &&
      !_heap->marking_context()->is_marked(obj)) {
    return nullptr;
  }

  // Prevent resurrection of unreachable weak references.
  if ((decorators & ON_WEAK_OOP_REF) != 0 &&
      _heap->is_concurrent_weak_root_in_progress() &&
      _heap->is_in_active_generation(obj) &&
      !_heap->marking_context()->is_marked_strong(obj)) {
    return nullptr;
  }

  // Allow runtime to see unreachable objects that are visited during concurrent class-unloading.
  if ((decorators & AS_NO_KEEPALIVE) != 0 &&
      _heap->is_concurrent_weak_root_in_progress() &&
      !_heap->marking_context()->is_marked(obj)) {
    return obj;
  }

  oop fwd = load_reference_barrier(obj);
  if (load_addr != nullptr && fwd != obj) {
    // Since we are here and we know the load address, update the reference.
    ShenandoahHeap::atomic_update_oop(fwd, load_addr, obj);
  }

  return fwd;
}

inline void ShenandoahBarrierSet::enqueue(oop obj) {
  assert(obj != nullptr, "checked by caller");
  assert(_satb_mark_queue_set.is_active(), "only get here when SATB active");

  // Filter marked objects before hitting the SATB queues. The same predicate would
  // be used by SATBMQ::filter to eliminate already marked objects downstream, but
  // filtering here helps to avoid wasteful SATB queueing work to begin with.
  if (!_heap->requires_marking(obj)) return;

  SATBMarkQueue& queue = ShenandoahThreadLocalData::satb_mark_queue(Thread::current());
  _satb_mark_queue_set.enqueue_known_active(queue, obj);
}

template <DecoratorSet decorators, typename T>
inline void ShenandoahBarrierSet::satb_barrier(T *field) {
  // Uninitialized and no-keepalive stores do not need barrier.
  if (HasDecorator<decorators, IS_DEST_UNINITIALIZED>::value ||
      HasDecorator<decorators, AS_NO_KEEPALIVE>::value) {
    return;
  }

  // Stores to weak/phantom require no barrier. The original references would
  // have been enqueued in the SATB buffer by the load barrier if they were needed.
  if (HasDecorator<decorators, ON_WEAK_OOP_REF>::value ||
      HasDecorator<decorators, ON_PHANTOM_OOP_REF>::value) {
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
  if (value != nullptr && ShenandoahSATBBarrier && _heap->is_concurrent_mark_in_progress()) {
    enqueue(value);
  }
}

inline void ShenandoahBarrierSet::keep_alive_if_weak(DecoratorSet decorators, oop value) {
  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Reference strength must be known");
  const bool on_strong_oop_ref = (decorators & ON_STRONG_OOP_REF) != 0;
  const bool peek              = (decorators & AS_NO_KEEPALIVE) != 0;
  if (!peek && !on_strong_oop_ref) {
    satb_enqueue(value);
  }
}

template <DecoratorSet decorators, typename T>
inline void ShenandoahBarrierSet::write_ref_field_post(T* field) {
  assert(ShenandoahCardBarrier, "Should have been checked by caller");
  volatile CardTable::CardValue* byte = card_table()->byte_for(field);
  *byte = CardTable::dirty_card_val();
}

template <typename T>
inline oop ShenandoahBarrierSet::oop_load(DecoratorSet decorators, T* addr) {
  oop value = RawAccess<>::oop_load(addr);
  value = load_reference_barrier(decorators, value, addr);
  keep_alive_if_weak(decorators, value);
  return value;
}

template <typename T>
inline oop ShenandoahBarrierSet::oop_cmpxchg(DecoratorSet decorators, T* addr, oop compare_value, oop new_value) {
  oop res;
  oop expected = compare_value;
  do {
    compare_value = expected;
    res = RawAccess<>::oop_atomic_cmpxchg(addr, compare_value, new_value);
    expected = res;
  } while ((compare_value != expected) && (resolve_forwarded(compare_value) == resolve_forwarded(expected)));

  // Note: We don't need a keep-alive-barrier here. We already enqueue any loaded reference for SATB anyway,
  // because it must be the previous value.
  res = load_reference_barrier(decorators, res, static_cast<T*>(nullptr));
  satb_enqueue(res);
  return res;
}

template <typename T>
inline oop ShenandoahBarrierSet::oop_xchg(DecoratorSet decorators, T* addr, oop new_value) {
  oop previous = RawAccess<>::oop_atomic_xchg(addr, new_value);
  // Note: We don't need a keep-alive-barrier here. We already enqueue any loaded reference for SATB anyway,
  // because it must be the previous value.
  previous = load_reference_barrier<T>(decorators, previous, static_cast<T*>(nullptr));
  satb_enqueue(previous);
  return previous;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_not_in_heap(T* addr) {
  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "must be absent");
  ShenandoahBarrierSet* const bs = ShenandoahBarrierSet::barrier_set();
  return bs->oop_load(decorators, addr);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_in_heap(T* addr) {
  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "must be absent");
  ShenandoahBarrierSet* const bs = ShenandoahBarrierSet::barrier_set();
  return bs->oop_load(decorators, addr);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_in_heap_at(oop base, ptrdiff_t offset) {
  ShenandoahBarrierSet* const bs = ShenandoahBarrierSet::barrier_set();
  DecoratorSet resolved_decorators = AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset);
  return bs->oop_load(resolved_decorators, AccessInternal::oop_field_addr<decorators>(base, offset));
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_common(T* addr, oop value) {
  shenandoah_assert_marked_if(nullptr, value,
                              !CompressedOops::is_null(value) && ShenandoahHeap::heap()->is_evacuation_in_progress()
                              && !(ShenandoahHeap::heap()->active_generation()->is_young()
                                   && ShenandoahHeap::heap()->heap_region_containing(value)->is_old()));
  shenandoah_assert_not_in_cset_if(addr, value, value != nullptr && !ShenandoahHeap::heap()->cancelled_gc());
  ShenandoahBarrierSet* const bs = ShenandoahBarrierSet::barrier_set();
  bs->satb_barrier<decorators>(addr);
  Raw::oop_store(addr, value);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_not_in_heap(T* addr, oop value) {
  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Reference strength must be known");
  oop_store_common(addr, value);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_in_heap(T* addr, oop value) {
  shenandoah_assert_not_in_cset_loc_except(addr, ShenandoahHeap::heap()->cancelled_gc());
  shenandoah_assert_not_forwarded_except  (addr, value, value == nullptr || ShenandoahHeap::heap()->cancelled_gc() || !ShenandoahHeap::heap()->is_concurrent_mark_in_progress());

  oop_store_common(addr, value);
  if (ShenandoahCardBarrier) {
    ShenandoahBarrierSet* bs = ShenandoahBarrierSet::barrier_set();
    bs->write_ref_field_post<decorators>(addr);
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_in_heap_at(oop base, ptrdiff_t offset, oop value) {
  oop_store_in_heap(AccessInternal::oop_field_addr<decorators>(base, offset), value);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_not_in_heap(T* addr, oop compare_value, oop new_value) {
  assert((decorators & (AS_NO_KEEPALIVE | ON_UNKNOWN_OOP_REF)) == 0, "must be absent");
  ShenandoahBarrierSet* bs = ShenandoahBarrierSet::barrier_set();
  return bs->oop_cmpxchg(decorators, addr, compare_value, new_value);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_in_heap(T* addr, oop compare_value, oop new_value) {
  assert((decorators & (AS_NO_KEEPALIVE | ON_UNKNOWN_OOP_REF)) == 0, "must be absent");
  ShenandoahBarrierSet* bs = ShenandoahBarrierSet::barrier_set();
  oop result = bs->oop_cmpxchg(decorators, addr, compare_value, new_value);
  if (ShenandoahCardBarrier) {
    bs->write_ref_field_post<decorators>(addr);
  }
  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_in_heap_at(oop base, ptrdiff_t offset, oop compare_value, oop new_value) {
  assert((decorators & AS_NO_KEEPALIVE) == 0, "must be absent");
  ShenandoahBarrierSet* bs = ShenandoahBarrierSet::barrier_set();
  DecoratorSet resolved_decorators = AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset);
  auto addr = AccessInternal::oop_field_addr<decorators>(base, offset);
  oop result = bs->oop_cmpxchg(resolved_decorators, addr, compare_value, new_value);
  if (ShenandoahCardBarrier) {
    bs->write_ref_field_post<decorators>(addr);
  }
  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_not_in_heap(T* addr, oop new_value) {
  assert((decorators & (AS_NO_KEEPALIVE | ON_UNKNOWN_OOP_REF)) == 0, "must be absent");
  ShenandoahBarrierSet* bs = ShenandoahBarrierSet::barrier_set();
  return bs->oop_xchg(decorators, addr, new_value);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_in_heap(T* addr, oop new_value) {
  assert((decorators & (AS_NO_KEEPALIVE | ON_UNKNOWN_OOP_REF)) == 0, "must be absent");
  ShenandoahBarrierSet* bs = ShenandoahBarrierSet::barrier_set();
  oop result = bs->oop_xchg(decorators, addr, new_value);
  if (ShenandoahCardBarrier) {
    bs->write_ref_field_post<decorators>(addr);
  }
  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_in_heap_at(oop base, ptrdiff_t offset, oop new_value) {
  assert((decorators & AS_NO_KEEPALIVE) == 0, "must be absent");
  ShenandoahBarrierSet* bs = ShenandoahBarrierSet::barrier_set();
  DecoratorSet resolved_decorators = AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset);
  auto addr = AccessInternal::oop_field_addr<decorators>(base, offset);
  oop result = bs->oop_xchg(resolved_decorators, addr, new_value);
  if (ShenandoahCardBarrier) {
    bs->write_ref_field_post<decorators>(addr);
  }
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
  T* src = arrayOopDesc::obj_offset_to_raw(src_obj, src_offset_in_bytes, src_raw);
  T* dst = arrayOopDesc::obj_offset_to_raw(dst_obj, dst_offset_in_bytes, dst_raw);

  ShenandoahBarrierSet* bs = ShenandoahBarrierSet::barrier_set();
  bs->arraycopy_barrier(src, dst, length);
  bool result = Raw::oop_arraycopy_in_heap(src_obj, src_offset_in_bytes, src_raw, dst_obj, dst_offset_in_bytes, dst_raw, length);
  if (ShenandoahCardBarrier) {
    bs->write_ref_array((HeapWord*) dst, length);
  }
  return result;
}

template <class T, bool HAS_FWD, bool EVAC, bool ENQUEUE>
void ShenandoahBarrierSet::arraycopy_work(T* src, size_t count) {
  // Young cycles are allowed to run when old marking is in progress. When old marking is in progress,
  // this barrier will be called with ENQUEUE=true and HAS_FWD=false, even though the young generation
  // may have forwarded objects. In this case, the `arraycopy_work` is first called with HAS_FWD=true and
  // ENQUEUE=false.
  assert(HAS_FWD == _heap->has_forwarded_objects() || _heap->is_concurrent_old_mark_in_progress(),
         "Forwarded object status is sane");
  // This function cannot be called to handle marking and evacuation at the same time (they operate on
  // different sides of the copy).
  assert((HAS_FWD || EVAC) != ENQUEUE, "Cannot evacuate and mark both sides of copy.");

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
        shenandoah_assert_forwarded_except(elem_ptr, obj, _heap->cancelled_gc());
        ShenandoahHeap::atomic_update_oop(fwd, elem_ptr, o);
      }
      if (ENQUEUE && !ctx->is_marked_strong_or_old(obj)) {
        _satb_mark_queue_set.enqueue_known_active(queue, obj);
      }
    }
  }
}

template <class T>
void ShenandoahBarrierSet::arraycopy_barrier(T* src, T* dst, size_t count) {
  if (count == 0) {
    // No elements to copy, no need for barrier
    return;
  }

  char gc_state = ShenandoahThreadLocalData::gc_state(Thread::current());
  if ((gc_state & ShenandoahHeap::EVACUATION) != 0) {
    arraycopy_evacuation(src, count);
  } else if ((gc_state & ShenandoahHeap::UPDATE_REFS) != 0) {
    arraycopy_update(src, count);
  }

  if (_heap->mode()->is_generational()) {
    assert(ShenandoahSATBBarrier, "Generational mode assumes SATB mode");
    if ((gc_state & ShenandoahHeap::YOUNG_MARKING) != 0) {
      arraycopy_marking(src, dst, count, false);
    }
    if ((gc_state & ShenandoahHeap::OLD_MARKING) != 0) {
      arraycopy_marking(src, dst, count, true);
    }
  } else if ((gc_state & ShenandoahHeap::MARKING) != 0) {
    arraycopy_marking(src, dst, count, false);
  }
}

template <class T>
void ShenandoahBarrierSet::arraycopy_marking(T* src, T* dst, size_t count, bool is_old_marking) {
  assert(_heap->is_concurrent_mark_in_progress(), "only during marking");
  /*
   * Note that an old-gen object is considered live if it is live at the start of OLD marking or if it is promoted
   * following the start of OLD marking.
   *
   * 1. Every object promoted following the start of OLD marking will be above TAMS within its old-gen region
   * 2. Every object live at the start of OLD marking will be referenced from a "root" or it will be referenced from
   *    another live OLD-gen object.  With regards to old-gen, roots include stack locations and all of live young-gen.
   *    All root references to old-gen are identified during a bootstrap young collection.  All references from other
   *    old-gen objects will be marked during the traversal of all old objects, or will be marked by the SATB barrier.
   *
   * During old-gen marking (which is interleaved with young-gen collections), call arraycopy_work() if:
   *
   * 1. The overwritten array resides in old-gen and it is below TAMS within its old-gen region
   * 2. Do not call arraycopy_work for any array residing in young-gen because young-gen collection is idle at this time
   *
   * During young-gen marking, call arraycopy_work() if:
   *
   * 1. The overwritten array resides in young-gen and is below TAMS within its young-gen region
   * 2. Additionally, if array resides in old-gen, regardless of its relationship to TAMS because this old-gen array
   *    may hold references to young-gen
   */
  if (ShenandoahSATBBarrier) {
    T* array = dst;
    HeapWord* array_addr = reinterpret_cast<HeapWord*>(array);
    ShenandoahHeapRegion* r = _heap->heap_region_containing(array_addr);
    if (is_old_marking) {
      // Generational, old marking
      assert(_heap->mode()->is_generational(), "Invariant");
      if (r->is_old() && (array_addr < _heap->marking_context()->top_at_mark_start(r))) {
        arraycopy_work<T, false, false, true>(array, count);
      }
    } else if (_heap->mode()->is_generational()) {
      // Generational, young marking
      if (r->is_old() || (array_addr < _heap->marking_context()->top_at_mark_start(r))) {
        arraycopy_work<T, false, false, true>(array, count);
      }
    } else if (array_addr < _heap->marking_context()->top_at_mark_start(r)) {
      // Non-generational, marking
      arraycopy_work<T, false, false, true>(array, count);
    }
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
