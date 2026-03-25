/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zAddress.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zHeap.hpp"
#include "gc/z/zNMethod.hpp"
#include "oops/inlineKlass.inline.hpp"
#include "oops/objArrayOop.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

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
  assert(base != nullptr, "Invalid base");
  return reinterpret_cast<zpointer*>(reinterpret_cast<intptr_t>((void*)base) + offset);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline zaddress ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::load_barrier(zpointer* p, zpointer o) {
  verify_decorators_absent<ON_UNKNOWN_OOP_REF>();

  if (HasDecorator<decorators, AS_NO_KEEPALIVE>::value) {
    if (HasDecorator<decorators, ON_STRONG_OOP_REF>::value) {
      // Load barriers on strong oop refs don't keep objects alive
      return ZBarrierSet::load_barrier_on_oop_field_preloaded(p, o);
    } else if (HasDecorator<decorators, ON_WEAK_OOP_REF>::value) {
      return ZBarrierSet::no_keep_alive_load_barrier_on_weak_oop_field_preloaded(p, o);
    } else {
      assert((HasDecorator<decorators, ON_PHANTOM_OOP_REF>::value), "Must be");
      return ZBarrierSet::no_keep_alive_load_barrier_on_phantom_oop_field_preloaded(p, o);
    }
  } else {
    if (HasDecorator<decorators, ON_STRONG_OOP_REF>::value) {
      return ZBarrierSet::load_barrier_on_oop_field_preloaded(p, o);
    } else if (HasDecorator<decorators, ON_WEAK_OOP_REF>::value) {
      return ZBarrierSet::load_barrier_on_weak_oop_field_preloaded(p, o);
    } else {
      assert((HasDecorator<decorators, ON_PHANTOM_OOP_REF>::value), "Must be");
      return ZBarrierSet::load_barrier_on_phantom_oop_field_preloaded(p, o);
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
      return ZBarrierSet::load_barrier_on_oop_field_preloaded(p, o);
    } else if (decorators_known_strength & ON_WEAK_OOP_REF) {
      return ZBarrierSet::no_keep_alive_load_barrier_on_weak_oop_field_preloaded(p, o);
    } else {
      assert(decorators_known_strength & ON_PHANTOM_OOP_REF, "Must be");
      return ZBarrierSet::no_keep_alive_load_barrier_on_phantom_oop_field_preloaded(p, o);
    }
  } else {
    if (decorators_known_strength & ON_STRONG_OOP_REF) {
      return ZBarrierSet::load_barrier_on_oop_field_preloaded(p, o);
    } else if (decorators_known_strength & ON_WEAK_OOP_REF) {
      return ZBarrierSet::load_barrier_on_weak_oop_field_preloaded(p, o);
    } else {
      assert(decorators_known_strength & ON_PHANTOM_OOP_REF, "Must be");
      return ZBarrierSet::load_barrier_on_phantom_oop_field_preloaded(p, o);
    }
  }
}

inline zpointer ZBarrierSet::store_good(oop obj) {
  assert(ZPointerStoreGoodMask != 0, "sanity");

  const zaddress addr = to_zaddress(obj);
  return ZAddress::store_good(addr);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::store_barrier_heap_with_healing(zpointer* p) {
  if (!HasDecorator<decorators, IS_DEST_UNINITIALIZED>::value) {
    ZBarrierSet::store_barrier_on_heap_oop_field(p, true /* heal */);
  } else {
    assert(false, "Should not be used on uninitialized memory");
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::store_barrier_heap_without_healing(zpointer* p) {
  if (!HasDecorator<decorators, IS_DEST_UNINITIALIZED>::value) {
    ZBarrierSet::store_barrier_on_heap_oop_field(p, false /* heal */);
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::no_keep_alive_store_barrier_heap(zpointer* p) {
  if (!HasDecorator<decorators, IS_DEST_UNINITIALIZED>::value) {
    ZBarrierSet::no_keep_alive_store_barrier_on_heap_oop_field(p);
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::store_barrier_native_with_healing(zpointer* p) {
  if (!HasDecorator<decorators, IS_DEST_UNINITIALIZED>::value) {
    ZBarrierSet::store_barrier_on_native_oop_field(p, true /* heal */);
  } else {
    assert(false, "Should not be used on uninitialized memory");
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::store_barrier_native_without_healing(zpointer* p) {
  if (!HasDecorator<decorators, IS_DEST_UNINITIALIZED>::value) {
    ZBarrierSet::store_barrier_on_native_oop_field(p, false /* heal */);
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
bool is_store_barrier_no_keep_alive() {
  if (HasDecorator<decorators, ON_STRONG_OOP_REF>::value) {
    return HasDecorator<decorators, AS_NO_KEEPALIVE>::value;
  }

  if (HasDecorator<decorators, ON_WEAK_OOP_REF>::value) {
    return true;
  }

  assert((decorators & ON_PHANTOM_OOP_REF) != 0, "Must be");
  return true;
}

template <DecoratorSet decorators>
inline bool is_store_barrier_no_keep_alive(oop base, ptrdiff_t offset) {
  if (!HasDecorator<decorators, ON_UNKNOWN_OOP_REF>::value) {
    return is_store_barrier_no_keep_alive<decorators>();
  }

  const DecoratorSet decorators_known_strength =
      AccessBarrierSupport::resolve_possibly_unknown_oop_ref_strength<decorators>(base, offset);

  if ((decorators_known_strength & ON_STRONG_OOP_REF) != 0) {
    return (decorators & AS_NO_KEEPALIVE) != 0;
  }

  if ((decorators_known_strength & ON_WEAK_OOP_REF) != 0) {
    return true;
  }

  assert((decorators_known_strength & ON_PHANTOM_OOP_REF) != 0, "Must be");
  return true;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_in_heap(zpointer* p, oop value) {
  verify_decorators_absent<ON_UNKNOWN_OOP_REF>();

  if (is_store_barrier_no_keep_alive<decorators>()) {
    no_keep_alive_store_barrier_heap(p);
  } else {
    store_barrier_heap_without_healing(p);
  }

  Raw::store_in_heap(p, store_good(value));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_in_heap_at(oop base, ptrdiff_t offset, oop value) {
  zpointer* const p = field_addr(base, offset);

  if (is_store_barrier_no_keep_alive<decorators>(base, offset)) {
    no_keep_alive_store_barrier_heap(p);
  } else {
    store_barrier_heap_without_healing(p);
  }

  Raw::store_in_heap(p, store_good(value));
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_store_not_in_heap(zpointer* p, oop value) {
  verify_decorators_absent<ON_UNKNOWN_OOP_REF>();

  if (!is_store_barrier_no_keep_alive<decorators>()) {
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

  return ZBarrierSet::load_barrier_on_oop_field(src);
}

template <DecoratorSet decorators, typename BarrierSetT>
inline OopCopyResult ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_copy_one(zpointer* dst, zpointer* src) {
  const zaddress obj = oop_copy_one_barriers(dst, src);

  if (HasDecorator<decorators, ARRAYCOPY_NOTNULL>::value && is_null(obj)) {
    return OopCopyResult::failed_check_null;
  }

  AtomicAccess::store(dst, ZAddress::store_good(obj));

  return OopCopyResult::ok;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline OopCopyResult ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_clear_one(zpointer* dst) {
  if (HasDecorator<decorators, ARRAYCOPY_NOTNULL>::value) {
    return OopCopyResult::failed_check_null;
  }

  // Store barrier
  store_barrier_heap_without_healing(dst);

  // Store colored null
  AtomicAccess::store(dst, color_null());

  return OopCopyResult::ok;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline OopCopyResult ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_copy_one_check_cast(zpointer* dst, zpointer* src, Klass* dst_klass) {
  const zaddress obj = oop_copy_one_barriers(dst, src);

  if (HasDecorator<decorators, ARRAYCOPY_NOTNULL>::value && is_null(obj)) {
    return OopCopyResult::failed_check_null;
  }

  if (!oopDesc::is_instanceof_or_null(to_oop(obj), dst_klass)) {
    // Check cast failed
    return OopCopyResult::failed_check_class_cast;
  }

  AtomicAccess::store(dst, ZAddress::store_good(obj));

  return OopCopyResult::ok;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline OopCopyResult ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_arraycopy_in_heap_check_cast(zpointer* dst, zpointer* src, size_t length, Klass* dst_klass) {
  // Check cast and copy each elements
  for (const zpointer* const end = src + length; src < end; src++, dst++) {
    const OopCopyResult result = oop_copy_one_check_cast(dst, src, dst_klass);
    if (result != OopCopyResult::ok) {
      return result;
    }
  }

  return OopCopyResult::ok;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline OopCopyResult ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_arraycopy_in_heap_no_check_cast(zpointer* dst, zpointer* src, size_t length) {
  const bool is_disjoint = HasDecorator<decorators, ARRAYCOPY_DISJOINT>::value;

  if (is_disjoint || src > dst) {
    for (const zpointer* const end = src + length; src < end; src++, dst++) {
      const OopCopyResult result = oop_copy_one(dst, src);
      if (result != OopCopyResult::ok) {
        return result;
      }
    }

    return OopCopyResult::ok;
  }

  if (src < dst) {
    const zpointer* const end = src;
    src += length - 1;
    dst += length - 1;
    for ( ; src >= end; src--, dst--) {
      const OopCopyResult result = oop_copy_one(dst, src);
      if (result != OopCopyResult::ok) {
        return result;
      }
    }

    return OopCopyResult::ok;
  }

  // src and dst are the same; nothing to do
  return OopCopyResult::ok;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline OopCopyResult ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_arraycopy_in_heap(arrayOop src_obj, size_t src_offset_in_bytes, zpointer* src_raw,
                                                                                                arrayOop dst_obj, size_t dst_offset_in_bytes, zpointer* dst_raw,
                                                                                                size_t length) {
  zpointer* const src = arrayOopDesc::obj_offset_to_raw(src_obj, src_offset_in_bytes, src_raw);
  zpointer* const dst = arrayOopDesc::obj_offset_to_raw(dst_obj, dst_offset_in_bytes, dst_raw);

  if (HasDecorator<decorators, ARRAYCOPY_CHECKCAST>::value) {
    Klass* const dst_klass = objArrayOop(dst_obj)->element_klass();
    return oop_arraycopy_in_heap_check_cast(dst, src, length, dst_klass);
  } else {
    return oop_arraycopy_in_heap_no_check_cast(dst, src, length);
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::clone_in_heap(oop src, oop dst, size_t size) {
  check_is_valid_zaddress(src);

  if (dst->is_refArray()) {
    // Cloning an object array is similar to performing array copy.
    // If an array is large enough to have its allocation segmented,
    // this operation might require GC barriers. However, the intrinsics
    // for cloning arrays transform the clone to an optimized allocation
    // and arraycopy sequence, so the performance of this runtime call
    // does not matter for object arrays.
    clone_obj_array(objArrayOop(src), objArrayOop(dst));
    return;
  }

  // Fix the oops
  ZBarrierSet::load_barrier_all(src, size);

  // Clone the object
  Raw::clone_in_heap(src, dst, size);

  // Color store good before handing out
  ZBarrierSet::color_store_good_all(dst, size);
}

static inline void copy_primitive_payload(const void* src, const void* dst, const size_t payload_size_bytes, size_t& copied_bytes) {
  if (payload_size_bytes == 0) {
    return;
  }

  void* src_payload = (void*)(address(src) + copied_bytes);
  void* dst_payload = (void*)(address(dst) + copied_bytes);
  Copy::copy_value_content(src_payload, dst_payload, payload_size_bytes);
  copied_bytes += payload_size_bytes;
}

static inline void clear_primitive_payload(const void* dst, const size_t payload_size_bytes, size_t& copied_bytes) {
  if (payload_size_bytes == 0) {
    return;
  }

  void* dst_payload = (void*)(address(dst) + copied_bytes);
  Copy::fill_to_memory_atomic(dst_payload, payload_size_bytes);
  copied_bytes += payload_size_bytes;
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::value_copy_in_heap(const ValuePayload& src, const ValuePayload& dst) {
  precond(src.klass() == dst.klass());

  const LayoutKind lk = LayoutKindHelper::get_copy_layout(src.layout_kind(), dst.layout_kind());
  const InlineKlass* md = src.klass();
  if (md->contains_oops()) {
    assert(!LayoutKindHelper::is_atomic_flat(lk) ||
               (md->nonstatic_oop_map_count() == 1 &&
                md->layout_size_in_bytes(lk) == sizeof(zpointer)),
           "ZGC can only handle atomic flat values with a single oop");

    // Iterate over each oop map, performing:
    //   1) possibly raw copy for any primitive payload before each map
    //   2) load and store barrier for each oop
    //   3) possibly raw copy for any primitive payload trailer

    // addr() points at the payload start, the oop map offset are relative to
    // the object header, adjust address to account for this discrepancy.
    const address src_addr = src.addr();
    const address dst_addr = dst.addr();
    const address oop_map_adjusted_src_addr = src_addr - md->payload_offset();
    OopMapBlock* map = md->start_of_nonstatic_oop_maps();
    const OopMapBlock* const end = map + md->nonstatic_oop_map_count();
    size_t size_in_bytes = md->layout_size_in_bytes(lk);
    size_t copied_bytes = 0;
    while (map != end) {
      zpointer* src_p = (zpointer*)(oop_map_adjusted_src_addr + map->offset());
      const uintptr_t oop_offset = uintptr_t(src_p) - uintptr_t(src_addr);
      zpointer* dst_p = (zpointer*)(uintptr_t(dst_addr) + oop_offset);

      // Copy any leading primitive payload before every cluster of oops
      assert(copied_bytes < oop_offset || copied_bytes == oop_offset, "Negative sized leading payload segment");
      copy_primitive_payload(src_addr, dst_addr, oop_offset - copied_bytes, copied_bytes);

      // Copy a cluster of oops
      for (const zpointer* const src_end = src_p + map->count(); src_p < src_end; src_p++, dst_p++) {
        oop_copy_one(dst_p, src_p);
        copied_bytes += sizeof(zpointer);
      }
      map++;
    }

    // Copy trailing primitive payload after potential oops
    assert(copied_bytes < size_in_bytes || copied_bytes == size_in_bytes, "Negative sized trailing payload segment");
    copy_primitive_payload(src_addr, dst_addr, size_in_bytes - copied_bytes, copied_bytes);
  } else {
    Raw::value_copy_in_heap(src, dst);
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
inline void ZBarrierSet::AccessBarrier<decorators, BarrierSetT>::value_store_null_in_heap(const ValuePayload& dst) {
  const LayoutKind lk = dst.layout_kind();
  assert(!LayoutKindHelper::is_null_free_flat(lk), "Cannot store null in null free layout");
  const InlineKlass* md = dst.klass();

  if (md->contains_oops()) {
    assert(!LayoutKindHelper::is_atomic_flat(lk) ||
               (md->nonstatic_oop_map_count() == 1 &&
                md->layout_size_in_bytes(lk) == sizeof(zpointer)),
           "ZGC can only handle atomic flat values with a single oop");

    // Iterate over each oop map, performing:
    //   1) possibly raw clear for any primitive payload before each map
    //   2) store barrier and clear for each oop
    //   3) possibly raw clear for any primitive payload trailer

    // addr() points at the payload start, the oop map offset are relative to
    // the object header, adjust address to account for this discrepancy.
    const address dst_addr = dst.addr();
    const address oop_map_adjusted_dst_addr = dst_addr - md->payload_offset();
    OopMapBlock* map = md->start_of_nonstatic_oop_maps();
    const OopMapBlock* const end = map + md->nonstatic_oop_map_count();
    size_t size_in_bytes = md->layout_size_in_bytes(lk);
    size_t copied_bytes = 0;
    while (map != end) {
      zpointer* dst_p = (zpointer*)(oop_map_adjusted_dst_addr + map->offset());
      const uintptr_t oop_offset = uintptr_t(dst_p) - uintptr_t(dst_addr);

      // Clear any leading primitive payload before every cluster of oops
      assert(copied_bytes < oop_offset || copied_bytes == oop_offset, "Negative sized leading payload segment");
      clear_primitive_payload(dst_addr, oop_offset - copied_bytes, copied_bytes);

      // Clear a cluster of oops
      for (const zpointer* const dst_end = dst_p + map->count(); dst_p < dst_end; dst_p++) {
        oop_clear_one(dst_p);
        copied_bytes += sizeof(zpointer);
      }
      map++;
    }

    // Clear trailing primitive payload after potential oops
    assert(copied_bytes < size_in_bytes || copied_bytes == size_in_bytes, "Negative sized trailing payload segment");
    clear_primitive_payload(dst_addr, size_in_bytes - copied_bytes, copied_bytes);
  } else {
    Raw::value_store_null(dst);
  }
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

  return oop_load_not_in_heap((zpointer*)p);
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
