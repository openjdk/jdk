/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

#if defined(__clang_major__)
#define FULL_COMPILER_ATOMIC_SUPPORT
#elif (__GNUC__ > 13) || ((__GNUC__ == 13) && (__GNUC_MINOR__ > 2))
#define FULL_COMPILER_ATOMIC_SUPPORT
#endif

template<size_t byte_size>
struct Atomic::PlatformAdd {
  template<typename D, typename I>
  D add_then_fetch(D volatile* dest, I add_value, atomic_memory_order order) const {

#ifndef FULL_COMPILER_ATOMIC_SUPPORT
    // If we add add and fetch for sub word and are using older compiler
    // it must be added here due to not using lib atomic.
    STATIC_ASSERT(byte_size >= 4);
#endif

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
  D fetch_then_add(D volatile* dest, I add_value, atomic_memory_order order) const {
    return add_then_fetch(dest, add_value, order) - add_value;
  }
};

#ifndef FULL_COMPILER_ATOMIC_SUPPORT
template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<1>::operator()(T volatile* dest __attribute__((unused)),
                                                T compare_value,
                                                T exchange_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(1 == sizeof(T));

  if (order != memory_order_relaxed) {
    FULL_MEM_BARRIER;
  }

  uint32_t volatile* aligned_dst = (uint32_t volatile*)(((uintptr_t)dest) & (~((uintptr_t)0x3)));
  int shift = 8 * (((uintptr_t)dest) - ((uintptr_t)aligned_dst)); // 0, 8, 16, 24

  uint64_t mask = 0xfful << shift; // 0x00000000..FF..
  uint64_t remask = ~mask;         // 0xFFFFFFFF..00..

  uint64_t w_cv = ((uint64_t)(unsigned char)compare_value) << shift;  // widen to 64-bit 0x00000000..CC..
  uint64_t w_ev = ((uint64_t)(unsigned char)exchange_value) << shift; // widen to 64-bit 0x00000000..EE..

  uint64_t old_value;
  uint64_t rc_temp;

  __asm__ __volatile__ (
    "1:  lr.w      %0, %2      \n\t"
    "    and       %1, %0, %5  \n\t" // ignore unrelated bytes and widen to 64-bit 0x00000000..XX..
    "    bne       %1, %3, 2f  \n\t" // compare 64-bit w_cv
    "    and       %1, %0, %6  \n\t" // remove old byte
    "    or        %1, %1, %4  \n\t" // add new byte
    "    sc.w      %1, %1, %2  \n\t" // store new word
    "    bnez      %1, 1b      \n\t"
    "2:                        \n\t"
    : /*%0*/"=&r" (old_value), /*%1*/"=&r" (rc_temp), /*%2*/"+A" (*aligned_dst)
    : /*%3*/"r" (w_cv), /*%4*/"r" (w_ev), /*%5*/"r" (mask), /*%6*/"r" (remask)
    : "memory" );

  if (order != memory_order_relaxed) {
    FULL_MEM_BARRIER;
  }

  return (T)((old_value & mask) >> shift);
}
#endif

#ifndef FULL_COMPILER_ATOMIC_SUPPORT
// The implementation of `__atomic_compare_exchange` lacks sign extensions
// in GCC 13.2 and lower when using with 32-bit unsigned integers on RV64,
// so we should implement it manually.
// GCC bug: https://gcc.gnu.org/bugzilla/show_bug.cgi?id=114130.
// See also JDK-8326936.
template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<4>::operator()(T volatile* dest __attribute__((unused)),
                                                T compare_value,
                                                T exchange_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));

  int32_t old_value;
  uint64_t rc_temp;

  if (order != memory_order_relaxed) {
    FULL_MEM_BARRIER;
  }

  __asm__ __volatile__ (
    "1:  lr.w      %0, %2      \n\t"
    "    bne       %0, %3, 2f  \n\t"
    "    sc.w      %1, %4, %2  \n\t"
    "    bnez      %1, 1b      \n\t"
    "2:                        \n\t"
    : /*%0*/"=&r" (old_value), /*%1*/"=&r" (rc_temp), /*%2*/"+A" (*dest)
    : /*%3*/"r" ((int64_t)(int32_t)compare_value), /*%4*/"r" (exchange_value)
    : "memory" );

  if (order != memory_order_relaxed) {
    FULL_MEM_BARRIER;
  }
  return (T)old_value;
}
#endif

template<size_t byte_size>
template<typename T>
inline T Atomic::PlatformXchg<byte_size>::operator()(T volatile* dest,
                                                     T exchange_value,
                                                     atomic_memory_order order) const {
#ifndef FULL_COMPILER_ATOMIC_SUPPORT
  // If we add xchg for sub word and are using older compiler
  // it must be added here due to not using lib atomic.
  STATIC_ASSERT(byte_size >= 4);
#endif

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

#ifndef FULL_COMPILER_ATOMIC_SUPPORT
  STATIC_ASSERT(byte_size > 4);
#endif

  STATIC_ASSERT(byte_size == sizeof(T));
  if (order != memory_order_relaxed) {
    FULL_MEM_BARRIER;
  }

  __atomic_compare_exchange(dest, &compare_value, &exchange_value, /* weak */ false,
                            __ATOMIC_RELAXED, __ATOMIC_RELAXED);

  if (order != memory_order_relaxed) {
    FULL_MEM_BARRIER;
  }
  return compare_value;
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

#undef FULL_COMPILER_ATOMIC_SUPPORT

#endif // OS_CPU_LINUX_RISCV_ATOMIC_LINUX_RISCV_HPP
