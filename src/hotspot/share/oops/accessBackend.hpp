/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_ACCESSBACKEND_HPP
#define SHARE_VM_RUNTIME_ACCESSBACKEND_HPP

#include "metaprogramming/conditional.hpp"
#include "metaprogramming/enableIf.hpp"
#include "metaprogramming/integralConstant.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// This metafunction returns either oop or narrowOop depending on whether
// an access needs to use compressed oops or not.
template <DecoratorSet decorators>
struct HeapOopType: AllStatic {
  static const bool needs_oop_compress = HasDecorator<decorators, INTERNAL_CONVERT_COMPRESSED_OOP>::value &&
                                         HasDecorator<decorators, INTERNAL_RT_USE_COMPRESSED_OOPS>::value;
  typedef typename Conditional<needs_oop_compress, narrowOop, oop>::type type;
};

namespace AccessInternal {
  enum BarrierType {
    BARRIER_STORE,
    BARRIER_STORE_AT,
    BARRIER_LOAD,
    BARRIER_LOAD_AT,
    BARRIER_ATOMIC_CMPXCHG,
    BARRIER_ATOMIC_CMPXCHG_AT,
    BARRIER_ATOMIC_XCHG,
    BARRIER_ATOMIC_XCHG_AT,
    BARRIER_ARRAYCOPY,
    BARRIER_CLONE
  };

  template <DecoratorSet decorators>
  struct MustConvertCompressedOop: public IntegralConstant<bool,
    HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value &&
    HasDecorator<decorators, INTERNAL_CONVERT_COMPRESSED_OOP>::value &&
    HasDecorator<decorators, INTERNAL_RT_USE_COMPRESSED_OOPS>::value> {};

  // This metafunction returns an appropriate oop type if the value is oop-like
  // and otherwise returns the same type T.
  template <DecoratorSet decorators, typename T>
  struct EncodedType: AllStatic {
    typedef typename Conditional<
      HasDecorator<decorators, INTERNAL_VALUE_IS_OOP>::value,
      typename HeapOopType<decorators>::type, T>::type type;
  };

  template <DecoratorSet decorators>
  inline typename HeapOopType<decorators>::type*
  oop_field_addr(oop base, ptrdiff_t byte_offset) {
    return reinterpret_cast<typename HeapOopType<decorators>::type*>(
             reinterpret_cast<intptr_t>((void*)base) + byte_offset);
  }

  // This metafunction returns whether it is possible for a type T to require
  // locking to support wide atomics or not.
  template <typename T>
#ifdef SUPPORTS_NATIVE_CX8
  struct PossiblyLockedAccess: public IntegralConstant<bool, false> {};
#else
  struct PossiblyLockedAccess: public IntegralConstant<bool, (sizeof(T) > 4)> {};
#endif

  template <DecoratorSet decorators, typename T>
  struct AccessFunctionTypes {
    typedef T (*load_at_func_t)(oop base, ptrdiff_t offset);
    typedef void (*store_at_func_t)(oop base, ptrdiff_t offset, T value);
    typedef T (*atomic_cmpxchg_at_func_t)(T new_value, oop base, ptrdiff_t offset, T compare_value);
    typedef T (*atomic_xchg_at_func_t)(T new_value, oop base, ptrdiff_t offset);

    typedef T (*load_func_t)(void* addr);
    typedef void (*store_func_t)(void* addr, T value);
    typedef T (*atomic_cmpxchg_func_t)(T new_value, void* addr, T compare_value);
    typedef T (*atomic_xchg_func_t)(T new_value, void* addr);

    typedef bool (*arraycopy_func_t)(arrayOop src_obj, arrayOop dst_obj, T* src, T* dst, size_t length);
    typedef void (*clone_func_t)(oop src, oop dst, size_t size);
  };

  template <DecoratorSet decorators, typename T, BarrierType barrier> struct AccessFunction {};

#define ACCESS_GENERATE_ACCESS_FUNCTION(bt, func)                   \
  template <DecoratorSet decorators, typename T>                    \
  struct AccessFunction<decorators, T, bt>: AllStatic{              \
    typedef typename AccessFunctionTypes<decorators, T>::func type; \
  }
  ACCESS_GENERATE_ACCESS_FUNCTION(BARRIER_STORE, store_func_t);
  ACCESS_GENERATE_ACCESS_FUNCTION(BARRIER_STORE_AT, store_at_func_t);
  ACCESS_GENERATE_ACCESS_FUNCTION(BARRIER_LOAD, load_func_t);
  ACCESS_GENERATE_ACCESS_FUNCTION(BARRIER_LOAD_AT, load_at_func_t);
  ACCESS_GENERATE_ACCESS_FUNCTION(BARRIER_ATOMIC_CMPXCHG, atomic_cmpxchg_func_t);
  ACCESS_GENERATE_ACCESS_FUNCTION(BARRIER_ATOMIC_CMPXCHG_AT, atomic_cmpxchg_at_func_t);
  ACCESS_GENERATE_ACCESS_FUNCTION(BARRIER_ATOMIC_XCHG, atomic_xchg_func_t);
  ACCESS_GENERATE_ACCESS_FUNCTION(BARRIER_ATOMIC_XCHG_AT, atomic_xchg_at_func_t);
  ACCESS_GENERATE_ACCESS_FUNCTION(BARRIER_ARRAYCOPY, arraycopy_func_t);
  ACCESS_GENERATE_ACCESS_FUNCTION(BARRIER_CLONE, clone_func_t);
#undef ACCESS_GENERATE_ACCESS_FUNCTION

  template <DecoratorSet decorators, typename T, BarrierType barrier_type>
  typename AccessFunction<decorators, T, barrier_type>::type resolve_barrier();

  template <DecoratorSet decorators, typename T, BarrierType barrier_type>
  typename AccessFunction<decorators, T, barrier_type>::type resolve_oop_barrier();

  class AccessLocker VALUE_OBJ_CLASS_SPEC {
  public:
    AccessLocker();
    ~AccessLocker();
  };
  bool wide_atomic_needs_locking();

  void* field_addr(oop base, ptrdiff_t offset);

  // Forward calls to Copy:: in the cpp file to reduce dependencies and allow
  // faster build times, given how frequently included access is.
  void arraycopy_arrayof_conjoint_oops(void* src, void* dst, size_t length);
  void arraycopy_conjoint_oops(oop* src, oop* dst, size_t length);
  void arraycopy_conjoint_oops(narrowOop* src, narrowOop* dst, size_t length);

  void arraycopy_disjoint_words(void* src, void* dst, size_t length);
  void arraycopy_disjoint_words_atomic(void* src, void* dst, size_t length);

  template<typename T>
  void arraycopy_conjoint(T* src, T* dst, size_t length);
  template<typename T>
  void arraycopy_arrayof_conjoint(T* src, T* dst, size_t length);
  template<typename T>
  void arraycopy_conjoint_atomic(T* src, T* dst, size_t length);
}

// This mask specifies what decorators are relevant for raw accesses. When passing
// accesses to the raw layer, irrelevant decorators are removed.
const DecoratorSet RAW_DECORATOR_MASK = INTERNAL_DECORATOR_MASK | MO_DECORATOR_MASK |
                                        ARRAYCOPY_DECORATOR_MASK | OOP_DECORATOR_MASK;

// The RawAccessBarrier performs raw accesses with additional knowledge of
// memory ordering, so that OrderAccess/Atomic is called when necessary.
// It additionally handles compressed oops, and hence is not completely "raw"
// strictly speaking.
template <DecoratorSet decorators>
class RawAccessBarrier: public AllStatic {
protected:
  static inline void* field_addr(oop base, ptrdiff_t byte_offset) {
    return AccessInternal::field_addr(base, byte_offset);
  }

protected:
  // Only encode if INTERNAL_VALUE_IS_OOP
  template <DecoratorSet idecorators, typename T>
  static inline typename EnableIf<
    AccessInternal::MustConvertCompressedOop<idecorators>::value,
    typename HeapOopType<idecorators>::type>::type
  encode_internal(T value);

  template <DecoratorSet idecorators, typename T>
  static inline typename EnableIf<
    !AccessInternal::MustConvertCompressedOop<idecorators>::value, T>::type
  encode_internal(T value) {
    return value;
  }

  template <typename T>
  static inline typename AccessInternal::EncodedType<decorators, T>::type
  encode(T value) {
    return encode_internal<decorators, T>(value);
  }

  // Only decode if INTERNAL_VALUE_IS_OOP
  template <DecoratorSet idecorators, typename T>
  static inline typename EnableIf<
    AccessInternal::MustConvertCompressedOop<idecorators>::value, T>::type
  decode_internal(typename HeapOopType<idecorators>::type value);

  template <DecoratorSet idecorators, typename T>
  static inline typename EnableIf<
    !AccessInternal::MustConvertCompressedOop<idecorators>::value, T>::type
  decode_internal(T value) {
    return value;
  }

  template <typename T>
  static inline T decode(typename AccessInternal::EncodedType<decorators, T>::type value) {
    return decode_internal<decorators, T>(value);
  }

protected:
  template <DecoratorSet ds, typename T>
  static typename EnableIf<
    HasDecorator<ds, MO_SEQ_CST>::value, T>::type
  load_internal(void* addr);

  template <DecoratorSet ds, typename T>
  static typename EnableIf<
    HasDecorator<ds, MO_ACQUIRE>::value, T>::type
  load_internal(void* addr);

  template <DecoratorSet ds, typename T>
  static typename EnableIf<
    HasDecorator<ds, MO_RELAXED>::value, T>::type
  load_internal(void* addr);

  template <DecoratorSet ds, typename T>
  static inline typename EnableIf<
    HasDecorator<ds, MO_VOLATILE>::value, T>::type
  load_internal(void* addr) {
    return *reinterpret_cast<const volatile T*>(addr);
  }

  template <DecoratorSet ds, typename T>
  static inline typename EnableIf<
    HasDecorator<ds, MO_UNORDERED>::value, T>::type
  load_internal(void* addr) {
    return *reinterpret_cast<const T*>(addr);
  }

  template <DecoratorSet ds, typename T>
  static typename EnableIf<
    HasDecorator<ds, MO_SEQ_CST>::value>::type
  store_internal(void* addr, T value);

  template <DecoratorSet ds, typename T>
  static typename EnableIf<
    HasDecorator<ds, MO_RELEASE>::value>::type
  store_internal(void* addr, T value);

  template <DecoratorSet ds, typename T>
  static typename EnableIf<
    HasDecorator<ds, MO_RELAXED>::value>::type
  store_internal(void* addr, T value);

  template <DecoratorSet ds, typename T>
  static inline typename EnableIf<
    HasDecorator<ds, MO_VOLATILE>::value>::type
  store_internal(void* addr, T value) {
    (void)const_cast<T&>(*reinterpret_cast<volatile T*>(addr) = value);
  }

  template <DecoratorSet ds, typename T>
  static inline typename EnableIf<
    HasDecorator<ds, MO_UNORDERED>::value>::type
  store_internal(void* addr, T value) {
    *reinterpret_cast<T*>(addr) = value;
  }

  template <DecoratorSet ds, typename T>
  static typename EnableIf<
    HasDecorator<ds, MO_SEQ_CST>::value, T>::type
  atomic_cmpxchg_internal(T new_value, void* addr, T compare_value);

  template <DecoratorSet ds, typename T>
  static typename EnableIf<
    HasDecorator<ds, MO_RELAXED>::value, T>::type
  atomic_cmpxchg_internal(T new_value, void* addr, T compare_value);

  template <DecoratorSet ds, typename T>
  static typename EnableIf<
    HasDecorator<ds, MO_SEQ_CST>::value, T>::type
  atomic_xchg_internal(T new_value, void* addr);

  // The following *_locked mechanisms serve the purpose of handling atomic operations
  // that are larger than a machine can handle, and then possibly opt for using
  // a slower path using a mutex to perform the operation.

  template <DecoratorSet ds, typename T>
  static inline typename EnableIf<
    !AccessInternal::PossiblyLockedAccess<T>::value, T>::type
  atomic_cmpxchg_maybe_locked(T new_value, void* addr, T compare_value) {
    return atomic_cmpxchg_internal<ds>(new_value, addr, compare_value);
  }

  template <DecoratorSet ds, typename T>
  static typename EnableIf<
    AccessInternal::PossiblyLockedAccess<T>::value, T>::type
  atomic_cmpxchg_maybe_locked(T new_value, void* addr, T compare_value);

  template <DecoratorSet ds, typename T>
  static inline typename EnableIf<
    !AccessInternal::PossiblyLockedAccess<T>::value, T>::type
  atomic_xchg_maybe_locked(T new_value, void* addr) {
    return atomic_xchg_internal<ds>(new_value, addr);
  }

  template <DecoratorSet ds, typename T>
  static typename EnableIf<
    AccessInternal::PossiblyLockedAccess<T>::value, T>::type
  atomic_xchg_maybe_locked(T new_value, void* addr);

public:
  template <typename T>
  static inline void store(void* addr, T value) {
    store_internal<decorators>(addr, value);
  }

  template <typename T>
  static inline T load(void* addr) {
    return load_internal<decorators, T>(addr);
  }

  template <typename T>
  static inline T atomic_cmpxchg(T new_value, void* addr, T compare_value) {
    return atomic_cmpxchg_maybe_locked<decorators>(new_value, addr, compare_value);
  }

  template <typename T>
  static inline T atomic_xchg(T new_value, void* addr) {
    return atomic_xchg_maybe_locked<decorators>(new_value, addr);
  }

  template <typename T>
  static bool arraycopy(T* src, T* dst, size_t length);

  template <typename T>
  static void oop_store(void* addr, T value);
  template <typename T>
  static void oop_store_at(oop base, ptrdiff_t offset, T value);

  template <typename T>
  static T oop_load(void* addr);
  template <typename T>
  static T oop_load_at(oop base, ptrdiff_t offset);

  template <typename T>
  static T oop_atomic_cmpxchg(T new_value, void* addr, T compare_value);
  template <typename T>
  static T oop_atomic_cmpxchg_at(T new_value, oop base, ptrdiff_t offset, T compare_value);

  template <typename T>
  static T oop_atomic_xchg(T new_value, void* addr);
  template <typename T>
  static T oop_atomic_xchg_at(T new_value, oop base, ptrdiff_t offset);

  template <typename T>
  static void store_at(oop base, ptrdiff_t offset, T value) {
    store(field_addr(base, offset), value);
  }

  template <typename T>
  static T load_at(oop base, ptrdiff_t offset) {
    return load<T>(field_addr(base, offset));
  }

  template <typename T>
  static T atomic_cmpxchg_at(T new_value, oop base, ptrdiff_t offset, T compare_value) {
    return atomic_cmpxchg(new_value, field_addr(base, offset), compare_value);
  }

  template <typename T>
  static T atomic_xchg_at(T new_value, oop base, ptrdiff_t offset) {
    return atomic_xchg(new_value, field_addr(base, offset));
  }

  template <typename T>
  static bool oop_arraycopy(arrayOop src_obj, arrayOop dst_obj, T* src, T* dst, size_t length);
  static bool oop_arraycopy(arrayOop src_obj, arrayOop dst_obj, HeapWord* src, HeapWord* dst, size_t length);

  static void clone(oop src, oop dst, size_t size);
};

#endif // SHARE_VM_RUNTIME_ACCESSBACKEND_HPP
