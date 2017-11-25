/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1SATBCARDTABLEMODREFBS_INLINE_HPP
#define SHARE_VM_GC_G1_G1SATBCARDTABLEMODREFBS_INLINE_HPP

#include "gc/shared/accessBarrierSupport.inline.hpp"
#include "gc/g1/g1SATBCardTableModRefBS.hpp"
#include "oops/oop.inline.hpp"

template <DecoratorSet decorators, typename T>
inline void G1SATBCardTableModRefBS::write_ref_field_pre(T* field) {
  if (HasDecorator<decorators, ARRAYCOPY_DEST_NOT_INITIALIZED>::value ||
      HasDecorator<decorators, AS_NO_KEEPALIVE>::value) {
    return;
  }

  T heap_oop = oopDesc::load_heap_oop(field);
  if (!oopDesc::is_null(heap_oop)) {
    enqueue(oopDesc::decode_heap_oop_not_null(heap_oop));
  }
}

template <DecoratorSet decorators, typename T>
inline void G1SATBCardTableLoggingModRefBS::write_ref_field_post(T* field, oop new_val) {
  volatile jbyte* byte = byte_for(field);
  if (*byte != g1_young_gen) {
    // Take a slow path for cards in old
    write_ref_field_post_slow(byte);
  }
}

void G1SATBCardTableModRefBS::set_card_claimed(size_t card_index) {
  jbyte val = _byte_map[card_index];
  if (val == clean_card_val()) {
    val = (jbyte)claimed_card_val();
  } else {
    val |= (jbyte)claimed_card_val();
  }
  _byte_map[card_index] = val;
}

inline void G1SATBCardTableModRefBS::enqueue_if_weak(DecoratorSet decorators, oop value) {
  assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Reference strength must be known");
  const bool on_strong_oop_ref = (decorators & ON_STRONG_OOP_REF) != 0;
  const bool peek              = (decorators & AS_NO_KEEPALIVE) != 0;

  if (!peek && !on_strong_oop_ref && value != NULL) {
    enqueue(value);
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop G1SATBCardTableLoggingModRefBS::AccessBarrier<decorators, BarrierSetT>::
oop_load_not_in_heap(T* addr) {
  oop value = ModRef::oop_load_not_in_heap(addr);
  enqueue_if_weak(decorators, value);
  return value;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop G1SATBCardTableLoggingModRefBS::AccessBarrier<decorators, BarrierSetT>::
oop_load_in_heap(T* addr) {
  oop value = ModRef::oop_load_in_heap(addr);
  enqueue_if_weak(decorators, value);
  return value;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop G1SATBCardTableLoggingModRefBS::AccessBarrier<decorators, BarrierSetT>::
oop_load_in_heap_at(oop base, ptrdiff_t offset) {
  oop value = ModRef::oop_load_in_heap_at(base, offset);
  enqueue_if_weak(AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset), value);
  return value;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline void G1SATBCardTableLoggingModRefBS::AccessBarrier<decorators, BarrierSetT>::
oop_store_not_in_heap(T* addr, oop new_value) {
  if (HasDecorator<decorators, IN_CONCURRENT_ROOT>::value) {
    // For roots not scanned in a safepoint, we have to apply SATB barriers
    // even for roots.
    G1SATBCardTableLoggingModRefBS *bs = barrier_set_cast<G1SATBCardTableLoggingModRefBS>(BarrierSet::barrier_set());
    bs->write_ref_field_pre<decorators>(addr);
  }
  Raw::oop_store(addr, new_value);
}

#endif // SHARE_VM_GC_G1_G1SATBCARDTABLEMODREFBS_INLINE_HPP
