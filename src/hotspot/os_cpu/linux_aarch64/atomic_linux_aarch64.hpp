/*
 * Copyright (c) 1999, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2019, Red Hat Inc. All rights reserved.
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

#ifndef OS_CPU_LINUX_AARCH64_ATOMIC_LINUX_AARCH64_HPP
#define OS_CPU_LINUX_AARCH64_ATOMIC_LINUX_AARCH64_HPP

#include "runtime/vm_version.hpp"

// Implementation of class atomic
// Note that memory_order_conservative requires a full barrier after atomic stores.
// See https://patchwork.kernel.org/patch/3575821/

typedef uint64_t (*aarch64_atomic_fetch_add_8_type)(volatile uint64_t *ptr, long val);
extern aarch64_atomic_fetch_add_8_type aarch64_atomic_fetch_add_8_impl;
typedef uint32_t (*aarch64_atomic_fetch_add_4_type)(volatile uint32_t *ptr, int val);
extern aarch64_atomic_fetch_add_4_type aarch64_atomic_fetch_add_4_impl;

typedef uint32_t (*aarch64_atomic_xchg_4_type)(volatile uint32_t *ptr, uint32_t val);
extern aarch64_atomic_xchg_4_type aarch64_atomic_xchg_4_impl;
typedef uint64_t (*aarch64_atomic_xchg_8_type)(volatile uint64_t *ptr, uint64_t val);
extern aarch64_atomic_xchg_8_type aarch64_atomic_xchg_8_impl;

typedef uint8_t (*aarch64_atomic_cmpxchg_1_type)(volatile uint8_t *ptr,
                                         uint8_t compare_val,
                                         uint8_t exchange_val);
extern aarch64_atomic_cmpxchg_1_type aarch64_atomic_cmpxchg_1_impl;
typedef uint32_t (*aarch64_atomic_cmpxchg_4_type)(volatile uint32_t *ptr,
                                          uint32_t compare_val,
                                          uint32_t exchange_val);
extern aarch64_atomic_cmpxchg_4_type aarch64_atomic_cmpxchg_4_impl;
typedef uint64_t (*aarch64_atomic_cmpxchg_8_type)(volatile uint64_t *ptr,
                                          uint64_t compare_val,
                                          uint64_t exchange_val);
extern aarch64_atomic_cmpxchg_8_type aarch64_atomic_cmpxchg_8_impl;


template<size_t byte_size>
struct Atomic::PlatformAdd {
  template<typename D, typename I>
  D fetch_and_add(D volatile* dest, I add_value, atomic_memory_order order) const;

  template<typename D, typename I>
  D add_and_fetch(D volatile* dest, I add_value, atomic_memory_order order) const {
    D old_value = fetch_and_add(dest, add_value, order) + add_value;
    FULL_MEM_BARRIER;
    return old_value;
  }
};

template<>
template<typename D, typename I>
inline D Atomic::PlatformAdd<4>::fetch_and_add(D volatile* dest, I add_value,
                                               atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(I));
  STATIC_ASSERT(4 == sizeof(D));
  D old_value = (D)aarch64_atomic_fetch_add_4_impl((volatile uint32_t *)dest, add_value);
  return old_value;
}

template<>
template<typename D, typename I>
inline D Atomic::PlatformAdd<8>::fetch_and_add(D volatile* dest, I add_value,
                                               atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(I));
  STATIC_ASSERT(8 == sizeof(D));
  D old_value = (D)aarch64_atomic_fetch_add_8_impl((volatile uint64_t *)dest, add_value);
  return old_value;
}

template<>
template<typename T>
inline T Atomic::PlatformXchg<4>::operator()(T volatile* dest,
                                             T exchange_value,
                                             atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
  T old_value = (T)aarch64_atomic_xchg_4_impl((volatile uint32_t *)dest, (uint32_t)exchange_value);
  FULL_MEM_BARRIER;
  return old_value;
}

template<>
template<typename T>
inline T Atomic::PlatformXchg<8>::operator()(T volatile* dest, T exchange_value,
                                             atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  T old_value = (T)aarch64_atomic_xchg_8_impl((volatile uint64_t *)dest, (uint64_t)exchange_value);
  FULL_MEM_BARRIER;
  return old_value;
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<1>::operator()(T volatile* dest,
                                                T compare_value,
                                                T exchange_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(1 == sizeof(T));
  if (order == memory_order_relaxed) {
    T old_value = (T)aarch64_atomic_cmpxchg_1_impl((volatile uint8_t *)dest,
                                              (uint8_t)compare_value, (uint8_t)exchange_value);
    return old_value;
  } else {
    FULL_MEM_BARRIER;
    T old_value = (T)aarch64_atomic_cmpxchg_1_impl((volatile uint8_t *)dest,
                                              (uint8_t)compare_value, (uint8_t)exchange_value);
    FULL_MEM_BARRIER;
    return old_value;
  }
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<4>::operator()(T volatile* dest,
                                                T compare_value,
                                                T exchange_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
  if (order == memory_order_relaxed) {
    T old_value = (T)aarch64_atomic_cmpxchg_4_impl((volatile uint32_t *)dest,
                                              (uint32_t)compare_value, (uint32_t)exchange_value);
    return old_value;
  } else {
    FULL_MEM_BARRIER;
    T old_value = (T)aarch64_atomic_cmpxchg_4_impl((volatile uint32_t *)dest,
                                              (uint32_t)compare_value, (uint32_t)exchange_value);
    FULL_MEM_BARRIER;
    return old_value;
  }
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<8>::operator()(T volatile* dest,
                                                T compare_value,
                                                T exchange_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  if (order == memory_order_relaxed) {
    T old_value
      = (T)aarch64_atomic_cmpxchg_8_impl((volatile uint64_t *)dest,
                                    (uint64_t)compare_value, (uint64_t)exchange_value);
    return old_value;
  } else {
    FULL_MEM_BARRIER;
    T old_value
      = (T)aarch64_atomic_cmpxchg_8_impl((volatile uint64_t *)dest,
                                    (uint64_t)compare_value, (uint64_t)exchange_value);
    FULL_MEM_BARRIER;
    return old_value;
  }
}

template<size_t byte_size>
struct Atomic::PlatformOrderedLoad<byte_size, X_ACQUIRE>
{
  template <typename T>
  T operator()(const volatile T* p) const { T data; __atomic_load(const_cast<T*>(p), &data, __ATOMIC_ACQUIRE); return data; }
};

template<size_t byte_size>
struct Atomic::PlatformOrderedStore<byte_size, RELEASE_X>
{
  template <typename T>
  void operator()(volatile T* p, T v) const { __atomic_store(const_cast<T*>(p), &v, __ATOMIC_RELEASE); }
};

template<size_t byte_size>
struct Atomic::PlatformOrderedStore<byte_size, RELEASE_X_FENCE>
{
  template <typename T>
  void operator()(volatile T* p, T v) const { release_store(p, v); OrderAccess::fence(); }
};

#endif // OS_CPU_LINUX_AARCH64_ATOMIC_LINUX_AARCH64_HPP
