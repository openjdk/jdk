/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Microsoft Corporation. All rights reserved.
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

#ifndef OS_CPU_WINDOWS_AARCH64_ATOMICACCESS_WINDOWS_AARCH64_HPP
#define OS_CPU_WINDOWS_AARCH64_ATOMICACCESS_WINDOWS_AARCH64_HPP

#include <intrin.h>
#include <windows.h>

template<size_t byte_size>
struct AtomicAccess::PlatformAdd {
  template<typename D, typename I>
  D add_then_fetch(D volatile* dest, I add_value, atomic_memory_order order) const;

  template<typename D, typename I>
  D fetch_then_add(D volatile* dest, I add_value, atomic_memory_order order) const {
    return add_then_fetch(dest, add_value, order) - add_value;
  }
};

// The Interlocked* APIs only take long and will not accept __int32. That is
// acceptable on Windows, since long is a 32-bits integer type.

#define DEFINE_INTRINSIC_ADD(IntrinsicName, IntrinsicType)                \
  template<>                                                              \
  template<typename D, typename I>                                        \
  inline D AtomicAccess::PlatformAdd<sizeof(IntrinsicType)>::add_then_fetch(D volatile* dest, \
                                                                            I add_value, \
                                                                            atomic_memory_order order) const { \
    STATIC_ASSERT(sizeof(IntrinsicType) == sizeof(D));                    \
    IntrinsicType volatile* d =                                           \
        reinterpret_cast<IntrinsicType volatile*>(dest);                  \
    IntrinsicType inc =                                                   \
        PrimitiveConversions::cast<IntrinsicType>(add_value);             \
    IntrinsicType result;                                                 \
    switch (order) {                                                      \
    case memory_order_relaxed:                                            \
      result = _##IntrinsicName##_nf(d, inc); break;                      \
    case memory_order_acquire:                                            \
      result = _##IntrinsicName##_acq(d, inc); break;                     \
    case memory_order_release:                                            \
      result = _##IntrinsicName##_rel(d, inc); break;                     \
    case memory_order_conservative:                                       \
      result = _##IntrinsicName(d, inc);                                  \
      OrderAccess::fence();                                               \
      break;                                                              \
    default:                                                              \
      result = _##IntrinsicName(d, inc); break;                           \
    }                                                                     \
    return PrimitiveConversions::cast<D>(result);                         \
  }

DEFINE_INTRINSIC_ADD(InterlockedAdd,   long)
DEFINE_INTRINSIC_ADD(InterlockedAdd64, __int64)

#undef DEFINE_INTRINSIC_ADD

template<>
struct AtomicAccess::PlatformXchg<1> : AtomicAccess::XchgUsingCmpxchg<1> {};

#define DEFINE_INTRINSIC_XCHG(IntrinsicName, IntrinsicType)               \
  template<>                                                              \
  template<typename T>                                                    \
  inline T AtomicAccess::PlatformXchg<sizeof(IntrinsicType)>::operator()(T volatile* dest, \
                                                                         T exchange_value, \
                                                                         atomic_memory_order order) const { \
    STATIC_ASSERT(sizeof(IntrinsicType) == sizeof(T));                    \
    STATIC_ASSERT(sizeof(IntrinsicType) == 4 ||                           \
                  sizeof(IntrinsicType) == 8);                            \
    IntrinsicType volatile* d =                                           \
        reinterpret_cast<IntrinsicType volatile*>(dest);                  \
    IntrinsicType xchg =                                                  \
        PrimitiveConversions::cast<IntrinsicType>(exchange_value);        \
    IntrinsicType result;                                                 \
    switch (order) {                                                      \
    case memory_order_relaxed:                                            \
      result = _##IntrinsicName##_nf(d, xchg); break;                     \
    case memory_order_acquire:                                            \
      result = _##IntrinsicName##_acq(d, xchg); break;                    \
    case memory_order_release:                                            \
      result = _##IntrinsicName##_rel(d, xchg); break;                    \
    case memory_order_conservative:                                       \
      result = _##IntrinsicName(d, xchg);                                 \
      OrderAccess::fence();                                               \
      break;                                                              \
    default:                                                              \
      result = _##IntrinsicName(d, xchg); break;                          \
    }                                                                     \
    return PrimitiveConversions::cast<T>(result);                         \
  }

DEFINE_INTRINSIC_XCHG(InterlockedExchange,   long)
DEFINE_INTRINSIC_XCHG(InterlockedExchange64, __int64)

#undef DEFINE_INTRINSIC_XCHG

// Note: the order of the parameters is different between
// AtomicAccess::PlatformCmpxchg<*>::operator() and the
// _InterlockedCompareExchange* intrinsics:
//   HotSpot:  (dest, compare_value, exchange_value)
//   MSVC:     (dest, exchange_value, compare_value)

#define DEFINE_INTRINSIC_CMPXCHG(IntrinsicName, IntrinsicType)            \
  template<>                                                              \
  template<typename T>                                                    \
  inline T AtomicAccess::PlatformCmpxchg<sizeof(IntrinsicType)>::operator()(T volatile* dest, \
                                                                            T compare_value, \
                                                                            T exchange_value, \
                                                                            atomic_memory_order order) const { \
    STATIC_ASSERT(sizeof(IntrinsicType) == sizeof(T));                    \
    IntrinsicType volatile* d =                                           \
        reinterpret_cast<IntrinsicType volatile*>(dest);                  \
    IntrinsicType xchg =                                                  \
        PrimitiveConversions::cast<IntrinsicType>(exchange_value);        \
    IntrinsicType cmp =                                                   \
        PrimitiveConversions::cast<IntrinsicType>(compare_value);         \
    IntrinsicType result;                                                 \
    switch (order) {                                                      \
    case memory_order_relaxed:                                            \
      result = _##IntrinsicName##_nf(d, xchg, cmp); break;                \
    case memory_order_acquire:                                            \
      result = _##IntrinsicName##_acq(d, xchg, cmp); break;               \
    case memory_order_release:                                            \
      result = _##IntrinsicName##_rel(d, xchg, cmp); break;               \
    case memory_order_conservative:                                       \
      result = _##IntrinsicName(d, xchg, cmp);                            \
      OrderAccess::fence();                                               \
      break;                                                              \
    default:                                                              \
      result = _##IntrinsicName(d, xchg, cmp); break;                     \
    }                                                                     \
    return PrimitiveConversions::cast<T>(result);                         \
  }

DEFINE_INTRINSIC_CMPXCHG(InterlockedCompareExchange8,  char) // Use the intrinsic as InterlockedCompareExchange8 does not exist
DEFINE_INTRINSIC_CMPXCHG(InterlockedCompareExchange,   long)
DEFINE_INTRINSIC_CMPXCHG(InterlockedCompareExchange64, __int64)

#undef DEFINE_INTRINSIC_CMPXCHG

#define DEFINE_ORDERED_LOAD(Size, Name, Type)                             \
  template<>                                                              \
  struct AtomicAccess::PlatformOrderedLoad<Size, X_ACQUIRE> {             \
    template <typename T>                                                 \
    T operator()(const volatile T* p) const {                             \
      T* noconst_ptr = const_cast<T*>(p);                                 \
      unsigned Type value = Name(reinterpret_cast<unsigned Type volatile*>(noconst_ptr)); \
      return PrimitiveConversions::cast<T>(value);                        \
    }                                                                     \
  };

DEFINE_ORDERED_LOAD(1, __ldar8,  __int8)
DEFINE_ORDERED_LOAD(2, __ldar16, __int16)
DEFINE_ORDERED_LOAD(4, __ldar32, __int32)
DEFINE_ORDERED_LOAD(8, __ldar64, __int64)

#undef DEFINE_ORDERED_LOAD

#define DEFINE_ORDERED_STORE(Size, Name, Type)                            \
  template<>                                                              \
  struct AtomicAccess::PlatformOrderedStore<Size, RELEASE_X> {            \
    template <typename T>                                                 \
    void operator()(volatile T* p, T v) const {                           \
      Name(reinterpret_cast<unsigned Type volatile*>(p), PrimitiveConversions::cast<unsigned Type>(v)); \
    }                                                                     \
  };

DEFINE_ORDERED_STORE(1, __stlr8,  __int8)
DEFINE_ORDERED_STORE(2, __stlr16, __int16)
DEFINE_ORDERED_STORE(4, __stlr32, __int32)
DEFINE_ORDERED_STORE(8, __stlr64, __int64)

#undef DEFINE_ORDERED_STORE

template<size_t byte_size>
struct AtomicAccess::PlatformOrderedStore<byte_size, RELEASE_X_FENCE>
{
  template <typename T>
  void operator()(volatile T* p, T v) const {
    PlatformOrderedStore<byte_size, RELEASE_X>()(p, v);
    OrderAccess::fence();
  }
};

#endif // OS_CPU_WINDOWS_AARCH64_ATOMICACCESS_WINDOWS_AARCH64_HPP
