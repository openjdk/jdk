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
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/oop.inline.hpp"

template <class T>
inline oop ShenandoahBarrierSet::load_reference_barrier(DecoratorSet decorators, oop obj, T* load_addr) {
  if (obj == nullptr) {
    return nullptr;
  }

  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Reference strength must be known");

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

  // No need for the barrier if there are no forwarded objects.
  if (!_heap->has_forwarded_objects()) {
    return obj;
  }

  return load_reference_barrier_slow(obj, load_addr);
}

template <class T>
inline void ShenandoahBarrierSet::keepalive_barrier(DecoratorSet decorators, T* addr, oop obj, Filter filter) {
  // Uninitialized and no-keepalive loads/stores do not need barrier.
  if (((decorators & IS_DEST_UNINITIALIZED) != 0) ||
      ((decorators & AS_NO_KEEPALIVE) != 0)) {
    return;
  }

  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Reference strength must be known");

  // No need for barriers on weaks, if requested. Normally filtered for stores, accepted for loads.
  if (((filter & FILTER_WEAK) != 0) &&
        (((decorators & ON_WEAK_OOP_REF) != 0) ||
         ((decorators & ON_PHANTOM_OOP_REF) != 0))) {
    return;
  }

  // No need for the barrier if marking is not in progress.
  if (!_heap->is_concurrent_mark_in_progress()) {
    return;
  }

  if (addr != nullptr) {
    assert(obj == nullptr, "Ambiguity: use addr or obj?");
    obj = RawAccess<>::oop_load(addr);
  }

  // Null objects require no barriers.
  if (obj == nullptr) {
    return;
  }

  keepalive_barrier_slow(obj, filter);
}

template <typename T>
inline void ShenandoahBarrierSet::card_barrier(T* field, oop new_value) {
  if (!ShenandoahCardBarrier) {
    return;
  }

  if (new_value == nullptr) {
    // Null reference stores do not require card mark.
    return;
  }

  if (_heap->is_in_young(field)) {
    // Young field stores do not require card mark.
    return;
  }

  if (!_heap->is_in_young(new_value)) {
    // Not an old->young reference store.
    return;
  }

  volatile CardTable::CardValue* byte = card_table()->byte_for(field);
  if (UseCondCardMark && (*byte == CardTable::dirty_card_val())) {
    return;
  }
  *byte = CardTable::dirty_card_val();
}

inline void ShenandoahBarrierSet::card_barrier_array(HeapWord* start, size_t count) {
  if (!ShenandoahCardBarrier) {
    return;
  }
  card_barrier_array_slow(start, count);
}

template <typename T>
inline oop ShenandoahBarrierSet::oop_load(DecoratorSet decorators, T* addr, bool in_heap) {
  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Reference strength must be known");

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  shenandoah_assert_not_in_cset_loc_except(addr, !in_heap || heap->cancelled_gc());

  oop value = RawAccess<>::oop_load(addr);

  // Perform LRB to handle evacuation and possibly weak loads.
  value = load_reference_barrier(decorators, value, addr);

  // If weak load survived the LRB, we need to keep-alive the value.
  if (!is_strong_access(decorators)) {
    keepalive_barrier(decorators, (T*)nullptr, value, FILTER_MARKED);
  }

  return value;
}

template <typename T>
inline void ShenandoahBarrierSet::oop_store(DecoratorSet decorators, T* addr, oop new_value, bool in_heap) {
  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Reference strength must be known");

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  shenandoah_assert_not_in_cset_loc_except(addr, !in_heap || heap->cancelled_gc());
  shenandoah_assert_not_in_cset_except(nullptr, new_value, new_value == nullptr || heap->cancelled_gc());
  shenandoah_assert_not_forwarded_except(nullptr, new_value, new_value == nullptr || heap->cancelled_gc());

  shenandoah_assert_marked_if(nullptr, new_value,
                              !CompressedOops::is_null(new_value) &&
                              heap->is_evacuation_in_progress() &&
                              !(heap->active_generation()->is_young() && heap->heap_region_containing(new_value)->is_old()));

  // Handle the previous value through SATB, as we are about to perform the store.
  keepalive_barrier(decorators, addr, nullptr, FILTER_WEAK_AND_MARKED);

  RawAccess<>::oop_store(addr, new_value);

  // Handle card table updates if needed.
  if (in_heap) {
    card_barrier(addr, new_value);
  }
}

template <typename T>
inline oop ShenandoahBarrierSet::oop_cmpxchg(DecoratorSet decorators, T* addr, oop compare_value, oop new_value, bool in_heap) {
  assert((decorators & AS_NO_KEEPALIVE) == 0, "CAS only with keep-alive");
  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "CAS should have resolved ref strength");
  assert((decorators & ON_STRONG_OOP_REF) != 0, "CAS only for strong refs");

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  shenandoah_assert_not_in_cset_loc_except(addr, !in_heap || heap->cancelled_gc());
  shenandoah_assert_not_in_cset_except(nullptr, compare_value, compare_value == nullptr || heap->cancelled_gc());
  shenandoah_assert_not_in_cset_except(nullptr, new_value, new_value == nullptr || heap->cancelled_gc());
  shenandoah_assert_not_forwarded_except(addr, compare_value, compare_value == nullptr || heap->cancelled_gc());
  shenandoah_assert_not_forwarded_except(addr, new_value, new_value == nullptr || heap->cancelled_gc());

  // Handle the previous value through SATB, as we are about to perform the store.
  oop prev = RawAccess<>::oop_load(addr);
  keepalive_barrier(decorators, (T*)nullptr, prev, FILTER_MARKED);

  // Perform LRB on location to fix it up for this and all following accesses.
  // This guarantees there are no false negatives due to concurrent evacuation,
  // and the value loaded later by CAS is sanitized by some LRB, or is null.
  load_reference_barrier(decorators, prev, addr);

  oop result = RawAccess<>::oop_atomic_cmpxchg(addr, compare_value, new_value);

  // Handle card table updates if needed.
  if (in_heap) {
    card_barrier(addr, new_value);
  }

  return result;
}

template <typename T>
inline oop ShenandoahBarrierSet::oop_xchg(DecoratorSet decorators, T* addr, oop new_value, bool in_heap) {
  assert((decorators & AS_NO_KEEPALIVE) == 0, "XCHG only with keep-alive");
  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "XCHG should have resolved ref strength");
  assert((decorators & ON_STRONG_OOP_REF) != 0, "XCHG only for strong refs");

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  shenandoah_assert_not_in_cset_loc_except(addr, !in_heap || heap->cancelled_gc());
  shenandoah_assert_not_in_cset_except(nullptr, new_value, new_value == nullptr || heap->cancelled_gc());
  shenandoah_assert_not_forwarded_except(addr, new_value, new_value == nullptr || heap->cancelled_gc());

  // Handle the previous value through SATB, as we are about to perform the store.
  oop prev = RawAccess<>::oop_load(addr);
  keepalive_barrier(decorators, (T*)nullptr, prev, FILTER_MARKED);

  // Perform LRB on location to fix it up for this and all following accesses.
  // This is purely opportunistic: we would not have any false negatives here.
  // This guarantees the value loaded later by XCHG is sanitized by some LRB, or is null.
  load_reference_barrier(decorators, prev, addr);

  oop result = RawAccess<>::oop_atomic_xchg(addr, new_value);

  // Handle card table updates if needed.
  if (in_heap) {
    card_barrier(addr, new_value);
  }

  return result;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline DecoratorSet ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::resolve_unknown(oop base, ptrdiff_t offset) {
  return AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline DecoratorSet ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::resolve_unknown_to_strong(oop base, ptrdiff_t offset) {
  // Unsafe operations come to this barrier set with ON_UNKNOWN_OOP_REF set.
  // These are normally strong refs, but one can use Unsafe on Reference.referent.
  // We cannot deal with that case. If application does Unsafe operations on
  // Reference.referent field, this likely breaks weak reference semantics already.
  // We upgrade the access to strong in (sometimes futile) attempt to maintain heap
  // integrity, and assert in debug builds for better diagnostics.
  assert((decorators & (ON_STRONG_OOP_REF | ON_UNKNOWN_OOP_REF)) != 0, "Only strong or unknown expected here");
  DecoratorSet resolved_decorators = AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset);
  assert((resolved_decorators & ON_STRONG_OOP_REF) != 0, "Application error: Unsupported operation on weak location");
  return (resolved_decorators & ~ON_DECORATOR_MASK) | ON_STRONG_OOP_REF;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_not_in_heap(T* addr) {
  return barrier_set()->oop_load(decorators, addr, /* in_heap = */ false);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_in_heap(T* addr) {
  return barrier_set()->oop_load(decorators, addr, /* in_heap = */ true);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_in_heap_at(oop base, ptrdiff_t offset) {
  DecoratorSet resolved_decorators = resolve_unknown(base, offset);
  auto addr = AccessInternal::oop_field_addr<decorators>(base, offset);
  return barrier_set()->oop_load(resolved_decorators, addr, /* in_heap = */ true);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_not_in_heap(T* addr, oop value) {
  barrier_set()->oop_store(decorators, addr, value, /* in_heap = */ false);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_in_heap(T* addr, oop value) {
  barrier_set()->oop_store(decorators, addr, value, /* in_heap = */ true);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_in_heap_at(oop base, ptrdiff_t offset, oop value) {
  DecoratorSet resolved_decorators = resolve_unknown(base, offset);
  auto addr = AccessInternal::oop_field_addr<decorators>(base, offset);
  barrier_set()->oop_store(resolved_decorators, addr, value, /* in_heap = */ true);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_not_in_heap(T* addr, oop compare_value, oop new_value) {
  return barrier_set()->oop_cmpxchg(decorators, addr, compare_value, new_value, /* in_heap = */ false);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_in_heap(T* addr, oop compare_value, oop new_value) {
  return barrier_set()->oop_cmpxchg(decorators, addr, compare_value, new_value, /* in_heap = */ true);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_in_heap_at(oop base, ptrdiff_t offset, oop compare_value, oop new_value) {
  DecoratorSet resolved_decorators = resolve_unknown_to_strong(base, offset);
  auto addr = AccessInternal::oop_field_addr<decorators>(base, offset);
  return barrier_set()->oop_cmpxchg(resolved_decorators, addr, compare_value, new_value, /* in_heap = */ true);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_not_in_heap(T* addr, oop new_value) {
  return barrier_set()->oop_xchg(decorators, addr, new_value, /* in_heap = */ false);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_in_heap(T* addr, oop new_value) {
  return barrier_set()->oop_xchg(decorators, addr, new_value, /* in_heap = */ true);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_in_heap_at(oop base, ptrdiff_t offset, oop new_value) {
  DecoratorSet resolved_decorators = resolve_unknown_to_strong(base, offset);
  auto addr = AccessInternal::oop_field_addr<decorators>(base, offset);
  return barrier_set()->oop_xchg(resolved_decorators, addr, new_value, /* in_heap = */ true);
}

template <DecoratorSet decorators, typename BarrierSetT>
void ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::clone_in_heap(oop src, oop dst, size_t size) {
  // Hot code path, called from compiler/runtime. Make sure fast path is fast.

  // Fix up src before doing the copy, if needed.
  const char gc_state = ShenandoahThreadLocalData::gc_state(Thread::current());
  if (gc_state != 0 && ShenandoahCloneBarrier) {
    ShenandoahBarrierSet* bs = barrier_set();
    if ((gc_state & ShenandoahHeap::EVACUATION) != 0) {
      bs->clone_work<true>(src);
    } else if ((gc_state & ShenandoahHeap::UPDATE_REFS) != 0) {
      bs->clone_work<false>(src);
    }
  }

  Raw::clone(src, dst, size);

  // Current allocator never allocates in old, so clone destination is guaranteed to be in young.
  // Otherwise we need card barriers.
  shenandoah_assert_in_young_if(nullptr, dst, ShenandoahCardBarrier);
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
OopCopyResult ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_arraycopy_in_heap(arrayOop src_obj, size_t src_offset_in_bytes, T* src_raw,
                                                                                                  arrayOop dst_obj, size_t dst_offset_in_bytes, T* dst_raw,
                                                                                                  size_t length) {
  T* src = arrayOopDesc::obj_offset_to_raw(src_obj, src_offset_in_bytes, src_raw);
  T* dst = arrayOopDesc::obj_offset_to_raw(dst_obj, dst_offset_in_bytes, dst_raw);

  ShenandoahBarrierSet* bs = barrier_set();
  bs->arraycopy_barrier(src, dst, length);
  OopCopyResult result = Raw::oop_arraycopy_in_heap(src_obj, src_offset_in_bytes, src_raw, dst_obj, dst_offset_in_bytes, dst_raw, length);
  bs->card_barrier_array((HeapWord*) dst, length);
  return result;
}

template <class T>
void ShenandoahBarrierSet::arraycopy_barrier(T* src, T* dst, size_t count) {
  if (count == 0) {
    // No elements to copy, no need for barrier
    return;
  }

  const char gc_state = ShenandoahThreadLocalData::gc_state(Thread::current());
  if ((gc_state & ShenandoahHeap::MARKING) != 0) {
    // If marking old or young, we must evaluate the SATB barrier. This will be the only
    // action if we are not marking old. If we are marking old, we must still evaluate the
    // load reference barrier for a young collection.
    if (_heap->mode()->is_generational()) {
      arraycopy_marking<true>(dst, count);
    } else {
      arraycopy_marking<false>(dst, count);
    }
  }

  if ((gc_state & ShenandoahHeap::EVACUATION) != 0) {
    assert((gc_state & ShenandoahHeap::YOUNG_MARKING) == 0, "Cannot be marking young during evacuation");
    arraycopy_evacuation(src, count);
  } else if ((gc_state & ShenandoahHeap::UPDATE_REFS) != 0) {
    assert((gc_state & ShenandoahHeap::YOUNG_MARKING) == 0, "Cannot be marking young during update-refs");
    arraycopy_update(src, count);
  }
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHBARRIERSET_INLINE_HPP
