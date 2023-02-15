/*
 * Copyright (c) 1999, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef OS_CPU_LINUX_RISCV_ATOMIC_LINUX_RISCV_HPP
#define OS_CPU_LINUX_RISCV_ATOMIC_LINUX_RISCV_HPP

#include "runtime/vm_version.hpp"

// Implementation of class atomic

// Note that memory_order_conservative requires a full barrier after atomic stores.
// See https://patchwork.kernel.org/patch/3575821/

template<size_t byte_size>
struct Atomic::PlatformAdd {
  template<typename D, typename I>
  D add_and_fetch(D volatile* dest, I add_value, atomic_memory_order order) const {
    if (order != memory_order_relaxed) {
      FULL_MEM_BARRIER;
    }

    D res = __atomic_add_fetch(dest, add_value, __ATOMIC_RELAXED);

    if (order != memory_order_relaxed) {
      FULL_MEM_BARRIER;
    }
    return res;
  }

  template<typename D, typename I>
  D fetch_and_add(D volatile* dest, I add_value, atomic_memory_order order) const {
    return add_and_fetch(dest, add_value, order) - add_value;
  }
};

template<size_t byte_size>
template<typename T>
inline T Atomic::PlatformXchg<byte_size>::operator()(T volatile* dest,
                                                     T exchange_value,
                                                     atomic_memory_order order) const {
  STATIC_ASSERT(byte_size == sizeof(T));
  if (order != memory_order_relaxed) {
    FULL_MEM_BARRIER;
  }

  T res = __atomic_exchange_n(dest, exchange_value, __ATOMIC_RELAXED);

  if (order != memory_order_relaxed) {
    FULL_MEM_BARRIER;
  }
  return res;
}

// __attribute__((unused)) on dest is to get rid of spurious GCC warnings.
template<size_t byte_size>
template<typename T>
inline T Atomic::PlatformCmpxchg<byte_size>::operator()(T volatile* dest __attribute__((unused)),
                                                        T compare_value,
                                                        T exchange_value,
                                                        atomic_memory_order order) const {
  STATIC_ASSERT(byte_size == sizeof(T));
  T value = compare_value;
  if (order != memory_order_relaxed) {
    FULL_MEM_BARRIER;
  }

  __atomic_compare_exchange(dest, &value, &exchange_value, /* weak */ false,
                            __ATOMIC_RELAXED, __ATOMIC_RELAXED);

  if (order != memory_order_relaxed) {
    FULL_MEM_BARRIER;
  }
  return value;
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<4>::operator()(T volatile* dest __attribute__((unused)),
                                                T compare_value,
                                                T exchange_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));

  T old_value;
  long rc;

  if (order != memory_order_relaxed) {
    FULL_MEM_BARRIER;
  }

  __asm__ __volatile__ (
    "1:  sext.w    %1, %3      \n\t" // sign-extend compare_value
    "    lr.w      %0, %2      \n\t"
    "    bne       %0, %1, 2f  \n\t"
    "    sc.w      %1, %4, %2  \n\t"
    "    bnez      %1, 1b      \n\t"
    "2:                        \n\t"
    : /*%0*/"=&r" (old_value), /*%1*/"=&r" (rc), /*%2*/"+A" (*dest)
    : /*%3*/"r" (compare_value), /*%4*/"r" (exchange_value)
    : "memory" );

  if (order != memory_order_relaxed) {
    FULL_MEM_BARRIER;
  }
  return old_value;
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

#endif // OS_CPU_LINUX_RISCV_ATOMIC_LINUX_RISCV_HPP
