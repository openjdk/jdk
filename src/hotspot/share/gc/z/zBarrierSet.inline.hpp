/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZBARRIERSET_INLINE_HPP
#define SHARE_GC_Z_ZBARRIERSET_INLINE_HPP

#include "gc/z/zBarrierSet.hpp"

#include "gc/shared/accessBarrierSupport.inline.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zNMethod.hpp"
#include "gc/z/zIterator.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "utilities/debug.hpp"

template <DecoratorSet decorators, typename BarrierSetT>
template <DecoratorSet expected>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::verify_decorators_present() {
  if ((decorators & expected) == 0) {
    fatal("Using unsupported access decorators");
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
template <DecoratorSet expected>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::verify_decorators_absent() {
  if ((decorators & expected) != 0) {
    fatal("Using unsupported access decorators");
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::unsupported() {
  ShouldNotReachHere();
}

template <DecoratorSet decorators, typename BarrierSetT>
inline zpointer* ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::field_addr(oop base, ptrdiff_t offset) {
  assert(base != NULL, "Invalid base");
  return reinterpret_cast<zpointer*>(reinterpret_cast<intptr_t>((void*)base) + offset);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline zaddress ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::load_barrier(zpointer* p, zpointer o) {
  verify_decorators_absent<ON_UNKNOWN_OOP_REF>();

  if (HasDecorator<decorators, AS_NO_KEEPALIVE>::value) {
    if (HasDecorator<decorators, ON_STRONG_OOP_REF>::value) {
      // Load barriers on strong oop refs don't keep objects alive
      return ZBarrier::load_barrier_on_oop_field_preloaded(p, o);
    } else if (HasDecorator<decorators, ON_WEAK_OOP_REF>::value) {
      return ZBarrier::no_keep_alive_load_barrier_on_weak_oop_field_preloaded(p, o);
    } else {
      assert((HasDecorator<decorators, ON_PHANTOM_OOP_REF>::value), "Must be");
      return ZBarrier::no_keep_alive_load_barrier_on_phantom_oop_field_preloaded(p, o);
    }
  } else {
    if (HasDecorator<decorators, ON_STRONG_OOP_REF>::value) {
      return ZBarrier::load_barrier_on_oop_field_preloaded(p, o);
    } else if (HasDecorator<decorators, ON_WEAK_OOP_REF>::value) {
      return ZBarrier::load_barrier_on_weak_oop_field_preloaded(p, o);
    } else {
      assert((HasDecorator<decorators, ON_PHANTOM_OOP_REF>::value), "Must be");
      return ZBarrier::load_barrier_on_phantom_oop_field_preloaded(p, o);
    }
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline zaddress ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::load_barrier_on_unknown_oop_ref(oop base, ptrdiff_t offset, zpointer* p, zpointer o) {
  verify_decorators_present<ON_UNKNOWN_OOP_REF>();

  const DecoratorSet decorators_known_strength =
    AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset);

  if (HasDecorator<decorators, AS_NO_KEEPALIVE>::value) {
    if (decorators_known_strength & ON_STRONG_OOP_REF) {
      // Load barriers on strong oop refs don't keep objects alive
      return ZBarrier::load_barrier_on_oop_field_preloaded(p, o);
    } else if (decorators_known_strength & ON_WEAK_OOP_REF) {
      return ZBarrier::no_keep_alive_load_barrier_on_weak_oop_field_preloaded(p, o);
    } else {
      assert(decorators_known_strength & ON_PHANTOM_OOP_REF, "Must be");
      return ZBarrier::no_keep_alive_load_barrier_on_phantom_oop_field_preloaded(p, o);
    }
  } else {
    if (decorators_known_strength & ON_STRONG_OOP_REF) {
      return ZBarrier::load_barrier_on_oop_field_preloaded(p, o);
    } else if (decorators_known_strength & ON_WEAK_OOP_REF) {
      return ZBarrier::load_barrier_on_weak_oop_field_preloaded(p, o);
    } else {
      assert(decorators_known_strength & ON_PHANTOM_OOP_REF, "Must be");
      return ZBarrier::load_barrier_on_phantom_oop_field_preloaded(p, o);
    }
  }
}

inline zpointer ZBarrierSet::store_good(oop obj) {
  assert(ZPointerStoreGoodMask != 0, "sanity");

  zaddress addr = to_zaddress(obj);
  return ZAddress::store_good(addr);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::store_barrier_heap_with_healing(zpointer* p) {
  if (!HasDecorator<decorators, IS_DEST_UNINITIALIZED>::value) {
    ZBarrier::store_barrier_on_heap_oop_field(p, true /* heal */);
  } else {
    assert(false, "Should not be used on uinitialized memory");
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::store_barrier_heap_without_healing(zpointer* p) {
  if (!HasDecorator<decorators, IS_DEST_UNINITIALIZED>::value) {
    ZBarrier::store_barrier_on_heap_oop_field(p, false /* heal */);
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::store_barrier_native_with_healing(zpointer* p) {
  if (!HasDecorator<decorators, IS_DEST_UNINITIALIZED>::value) {
    ZBarrier::store_barrier_on_native_oop_field(p, true /* heal */);
  } else {
    assert(false, "Should not be used on uinitialized memory");
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::store_barrier_native_without_healing(zpointer* p) {
  if (!HasDecorator<decorators, IS_DEST_UNINITIALIZED>::value) {
    ZBarrier::store_barrier_on_native_oop_field(p, false /* heal */);
  }
}

//
// In heap
//
template <DecoratorSet decorators, typename BarrierSetT>
inline oop ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_in_heap(zpointer* p) {
  verify_decorators_absent<ON_UNKNOWN_OOP_REF>();

  const zpointer o = Raw::load_in_heap(p);
  assert_is_valid(o);

  return to_oop(load_barrier(p, o));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_in_heap_at(oop base, ptrdiff_t offset) {
  zpointer* const p = field_addr(base, offset);

  const zpointer o = Raw::load_in_heap(p);
  assert_is_valid(o);

  if (HasDecorator<decorators, ON_UNKNOWN_OOP_REF>::value) {
    return to_oop(load_barrier_on_unknown_oop_ref(base, offset, p, o));
  }

  return to_oop(load_barrier(p, o));
}

template <DecoratorSet decorators>
bool needs_store_barrier() {
  if (decorators & ON_STRONG_OOP_REF) {
    return true;
  }

  if (decorators  & ON_WEAK_OOP_REF) {
    // No store barrier
    return false;
  }

  if (decorators  & ON_PHANTOM_OOP_REF) {
    // No store barrier
    return false;
  }

  assert(false, "Unexpected decorator");
  return false;
}

template <DecoratorSet decorators>
inline bool needs_store_barrier(oop base, ptrdiff_t offset) {
  if (!HasDecorator<decorators, ON_UNKNOWN_OOP_REF>::value) {
    return needs_store_barrier<decorators>();
  }

  const DecoratorSet decorators_known_strength =
    AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset);

  if (decorators_known_strength & ON_STRONG_OOP_REF) {
    return true;
  }

  if (decorators_known_strength & ON_WEAK_OOP_REF) {
    return false;
  }

  assert(decorators_known_strength & ON_PHANTOM_OOP_REF, "Must be");
  return false;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_in_heap(zpointer* p, oop value) {
  verify_decorators_absent<ON_UNKNOWN_OOP_REF>();

  if (needs_store_barrier<decorators>()) {
    store_barrier_heap_without_healing(p);
  }

  Raw::store_in_heap(p, store_good(value));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_in_heap_at(oop base, ptrdiff_t offset, oop value) {
  zpointer* const p = field_addr(base, offset);

  if (needs_store_barrier<decorators>(base, offset)) {
    store_barrier_heap_without_healing(p);
  }

  Raw::store_in_heap(p, store_good(value));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_not_in_heap(zpointer* p, oop value) {
  verify_decorators_absent<ON_UNKNOWN_OOP_REF>();

  if (needs_store_barrier<decorators>()) {
    store_barrier_native_without_healing(p);
  }

  Raw::store(p, store_good(value));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_in_heap(zpointer* p, oop compare_value, oop new_value) {
  verify_decorators_present<ON_STRONG_OOP_REF>();
  verify_decorators_absent<AS_NO_KEEPALIVE>();

  store_barrier_heap_with_healing(p);

  const zpointer o = Raw::atomic_cmpxchg_in_heap(p, store_good(compare_value), store_good(new_value));
  assert_is_valid(o);

  return to_oop(ZPointer::uncolor_store_good(o));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_in_heap_at(oop base, ptrdiff_t offset, oop compare_value, oop new_value) {
  verify_decorators_present<ON_STRONG_OOP_REF | ON_UNKNOWN_OOP_REF>();
  verify_decorators_absent<AS_NO_KEEPALIVE>();

  // Through Unsafe.CompareAndExchangeObject()/CompareAndSetObject() we can receive
  // calls with ON_UNKNOWN_OOP_REF set. However, we treat these as ON_STRONG_OOP_REF,
  // with the motivation that if you're doing Unsafe operations on a Reference.referent
  // field, then you're on your own anyway.
  zpointer* const p = field_addr(base, offset);

  store_barrier_heap_with_healing(p);

  const zpointer o = Raw::atomic_cmpxchg_in_heap(p, store_good(compare_value), store_good(new_value));
  assert_is_valid(o);

  return to_oop(ZPointer::uncolor_store_good(o));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_in_heap(zpointer* p, oop new_value) {
  verify_decorators_present<ON_STRONG_OOP_REF>();
  verify_decorators_absent<AS_NO_KEEPALIVE>();

  store_barrier_heap_with_healing(p);

  const zpointer o = Raw::atomic_xchg_in_heap(p, store_good(new_value));
  assert_is_valid(o);

  return to_oop(ZPointer::uncolor_store_good(o));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_in_heap_at(oop base, ptrdiff_t offset, oop new_value) {
  verify_decorators_present<ON_STRONG_OOP_REF>();
  verify_decorators_absent<AS_NO_KEEPALIVE>();

  zpointer* const p = field_addr(base, offset);

  store_barrier_heap_with_healing(p);

  const zpointer o = Raw::atomic_xchg_in_heap(p, store_good(new_value));
  assert_is_valid(o);

  return to_oop(ZPointer::uncolor_store_good(o));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline zaddress ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_copy_one_barriers(zpointer* dst, zpointer* src) {
  store_barrier_heap_without_healing(dst);

  return ZBarrier::load_barrier_on_oop_field(src);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_copy_one(zpointer* dst, zpointer* src) {
  const zaddress obj = oop_copy_one_barriers(dst, src);

  Atomic::store(dst, ZAddress::store_good(obj));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline bool ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_copy_one_check_cast(zpointer* dst, zpointer* src, Klass* dst_klass) {
  const zaddress obj = oop_copy_one_barriers(dst, src);

  if (!oopDesc::is_instanceof_or_null(to_oop(obj), dst_klass)) {
    // Check cast failed
    return false;
  }

  Atomic::store(dst, ZAddress::store_good(obj));

  return true;
}


template <DecoratorSet decorators, typename BarrierSetT>
inline bool ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_arraycopy_in_heap_check_cast(zpointer* dst, zpointer* src, size_t length, Klass* dst_klass) {
  // Check cast and copy each elements
  for (const zpointer* const end = src + length; src < end; src++, dst++) {
    if (!oop_copy_one_check_cast(dst, src, dst_klass)) {
      // Check cast failed
      return false;
    }
  }

  return true;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline bool ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_arraycopy_in_heap_no_check_cast(zpointer* dst, zpointer* src, size_t length) {
  const bool is_disjoint = HasDecorator<decorators, ARRAYCOPY_DISJOINT>::value;

  if (is_disjoint || src > dst) {
    for (const zpointer* const end = src + length; src < end; src++, dst++) {
      oop_copy_one(dst, src);
    }
    return true;
  }

  if (src < dst) {
    const zpointer* const end = src;
    src += length - 1;
    dst += length - 1;
    for ( ; src >= end; src--, dst--) {
      oop_copy_one(dst, src);
    }
    return true;
  }

  // src and dst are the same; nothing to do
  return true;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline bool ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_arraycopy_in_heap(arrayOop src_obj, size_t src_offset_in_bytes, zpointer* src_raw,
                                                                                       arrayOop dst_obj, size_t dst_offset_in_bytes, zpointer* dst_raw,
                                                                                       size_t length) {
  zpointer* const src = arrayOopDesc::obj_offset_to_raw(src_obj, src_offset_in_bytes, src_raw);
  zpointer* const dst = arrayOopDesc::obj_offset_to_raw(dst_obj, dst_offset_in_bytes, dst_raw);

  if (HasDecorator<decorators, ARRAYCOPY_CHECKCAST>::value) {
    Klass* const dst_klass = objArrayOop(dst_obj)->element_klass();
    return oop_arraycopy_in_heap_check_cast(dst, src, length, dst_klass);
  }

  return oop_arraycopy_in_heap_no_check_cast(dst, src, length);
}

class ZColorStoreGoodOopClosure : public BasicOopIterateClosure {
public:
  virtual void do_oop(oop* p_) {
    zpointer* p = (zpointer*)p_;
    const zpointer ptr = ZBarrier::load_atomic(p);
    const zaddress addr = ZPointer::uncolor(ptr);
    *p = ZAddress::store_good(addr);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

class ZLoadBarrierOopClosure : public BasicOopIterateClosure {
public:
  virtual void do_oop(oop* p) {
    ZBarrier::load_barrier_on_oop_field((zpointer*)p);
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::clone_in_heap(oop src, oop dst, size_t size) {
  assert_is_valid(to_zaddress(src));

  // Fix the oops
  ZLoadBarrierOopClosure cl;
  ZIterator::oop_iterate(src, &cl);

  // Clone the object
  Raw::clone_in_heap(src, dst, size);

  // Color store good before handing out
  ZColorStoreGoodOopClosure cl_sg;
  ZIterator::oop_iterate(dst, &cl_sg);
}

//
// Not in heap
//
template <DecoratorSet decorators, typename BarrierSetT>
inline oop ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_not_in_heap(zpointer* p) {
  verify_decorators_absent<ON_UNKNOWN_OOP_REF>();

  const zpointer o = Raw::template load<zpointer>(p);
  assert_is_valid(o);
  return to_oop(load_barrier(p, o));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_load_not_in_heap(oop* p) {
  verify_decorators_absent<ON_UNKNOWN_OOP_REF>();

  if (HasDecorator<decorators, IN_NMETHOD>::value) {
    return ZNMethod::load_oop(p, decorators);
  } else {
    return oop_load_not_in_heap((zpointer*)p);
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_not_in_heap(zpointer* p, oop compare_value, oop new_value) {
  verify_decorators_present<ON_STRONG_OOP_REF>();
  verify_decorators_absent<AS_NO_KEEPALIVE>();

  store_barrier_native_with_healing(p);

  const zpointer o = Raw::atomic_cmpxchg(p, store_good(compare_value), store_good(new_value));
  assert_is_valid(o);

  return to_oop(ZPointer::uncolor_store_good(o));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline oop ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_not_in_heap(zpointer* p, oop new_value) {
  verify_decorators_present<ON_STRONG_OOP_REF>();
  verify_decorators_absent<AS_NO_KEEPALIVE>();

  store_barrier_native_with_healing(p);

  const zpointer o = Raw::atomic_xchg(p, store_good(new_value));
  assert_is_valid(o);

  return to_oop(ZPointer::uncolor_store_good(o));
}

#endif // SHARE_GC_Z_ZBARRIERSET_INLINE_HPP
