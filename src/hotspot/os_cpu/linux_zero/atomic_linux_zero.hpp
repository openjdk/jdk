/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2007, 2008, 2011, 2015, Red Hat, Inc.
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

#ifndef OS_CPU_LINUX_ZERO_ATOMIC_LINUX_ZERO_HPP
#define OS_CPU_LINUX_ZERO_ATOMIC_LINUX_ZERO_HPP

#include "orderAccess_linux_zero.hpp"

// Implementation of class atomic

template<size_t byte_size>
struct Atomic::PlatformAdd {
  template<typename D, typename I>
  D add_and_fetch(D volatile* dest, I add_value, atomic_memory_order order) const;

  template<typename D, typename I>
  D fetch_and_add(D volatile* dest, I add_value, atomic_memory_order order) const {
    return add_and_fetch(dest, add_value, order) - add_value;
  }
};

template<>
template<typename D, typename I>
inline D Atomic::PlatformAdd<4>::add_and_fetch(D volatile* dest, I add_value,
                                               atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(I));
  STATIC_ASSERT(4 == sizeof(D));

  D res = __atomic_add_fetch(dest, add_value, __ATOMIC_RELEASE);
  FULL_MEM_BARRIER;
  return res;
}

template<>
template<typename D, typename I>
inline D Atomic::PlatformAdd<8>::add_and_fetch(D volatile* dest, I add_value,
                                               atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(I));
  STATIC_ASSERT(8 == sizeof(D));

  D res = __atomic_add_fetch(dest, add_value, __ATOMIC_RELEASE);
  FULL_MEM_BARRIER;
  return res;
}

template<>
template<typename T>
inline T Atomic::PlatformXchg<4>::operator()(T volatile* dest,
                                             T exchange_value,
                                             atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
  // __sync_lock_test_and_set is a bizarrely named atomic exchange
  // operation.  Note that some platforms only support this with the
  // limitation that the only valid value to store is the immediate
  // constant 1.  There is a test for this in JNI_CreateJavaVM().
  T result = __sync_lock_test_and_set (dest, exchange_value);
  // All atomic operations are expected to be full memory barriers
  // (see atomic.hpp). However, __sync_lock_test_and_set is not
  // a full memory barrier, but an acquire barrier. Hence, this added
  // barrier. Some platforms (notably ARM) have peculiarities with
  // their barrier implementations, delegate it to OrderAccess.
  OrderAccess::fence();
  return result;
}

template<>
template<typename T>
inline T Atomic::PlatformXchg<8>::operator()(T volatile* dest,
                                             T exchange_value,
                                             atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  T result = __sync_lock_test_and_set (dest, exchange_value);
  OrderAccess::fence();
  return result;
}

// No direct support for cmpxchg of bytes; emulate using int.
template<>
struct Atomic::PlatformCmpxchg<1> : Atomic::CmpxchgByteUsingInt {};

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<4>::operator()(T volatile* dest,
                                                T compare_value,
                                                T exchange_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));

  T value = compare_value;
  FULL_MEM_BARRIER;
  __atomic_compare_exchange(dest, &value, &exchange_value, /*weak*/false,
                            __ATOMIC_RELAXED, __ATOMIC_RELAXED);
  FULL_MEM_BARRIER;
  return value;
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<8>::operator()(T volatile* dest,
                                                T compare_value,
                                                T exchange_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));

  FULL_MEM_BARRIER;
  T value = compare_value;
  __atomic_compare_exchange(dest, &value, &exchange_value, /*weak*/false,
                            __ATOMIC_RELAXED, __ATOMIC_RELAXED);
  FULL_MEM_BARRIER;
  return value;
}

// Atomically copy 64 bits of data
inline void atomic_copy64(const volatile void *src, volatile void *dst) {
#if defined(PPC32) && !defined(__SPE__)
  double tmp;
  asm volatile ("lfd  %0, %2\n"
                "stfd %0, %1\n"
                : "=&f"(tmp), "=Q"(*(volatile double*)dst)
                : "Q"(*(volatile double*)src));
#elif defined(PPC32) && defined(__SPE__)
  long tmp;
  asm volatile ("evldd  %0, %2\n"
                "evstdd %0, %1\n"
                : "=&r"(tmp), "=Q"(*(volatile long*)dst)
                : "Q"(*(volatile long*)src));
#elif defined(S390) && !defined(_LP64)
  double tmp;
  asm volatile ("ld  %0, %2\n"
                "std %0, %1\n"
                : "=&f"(tmp), "=Q"(*(volatile double*)dst)
                : "Q"(*(volatile double*)src));
#elif defined(__ARM_ARCH_7A__)
  // The only way to perform the atomic 64-bit load/store
  // is to use ldrexd/strexd for both reads and writes.
  // For store, we need to have the matching (fake) load first.
  // Put clrex between exclusive ops on src and dst for clarity.
  uint64_t tmp_r, tmp_w;
  uint32_t flag_w;
  asm volatile ("ldrexd %[tmp_r], [%[src]]\n"
                "clrex\n"
                "1:\n"
                "ldrexd %[tmp_w], [%[dst]]\n"
                "strexd %[flag_w], %[tmp_r], [%[dst]]\n"
                "cmp    %[flag_w], 0\n"
                "bne    1b\n"
                : [tmp_r] "=&r" (tmp_r), [tmp_w] "=&r" (tmp_w),
                  [flag_w] "=&r" (flag_w)
                : [src] "r" (src), [dst] "r" (dst)
                : "cc", "memory");
#else
  *(jlong *) dst = *(const jlong *) src;
#endif
}

template<>
template<typename T>
inline T Atomic::PlatformLoad<8>::operator()(T const volatile* src) const {
  STATIC_ASSERT(8 == sizeof(T));
  volatile int64_t dest;
  atomic_copy64(reinterpret_cast<const volatile int64_t*>(src), reinterpret_cast<volatile int64_t*>(&dest));
  return PrimitiveConversions::cast<T>(dest);
}

template<>
template<typename T>
inline void Atomic::PlatformStore<8>::operator()(T volatile* dest,
                                                 T store_value) const {
  STATIC_ASSERT(8 == sizeof(T));
  atomic_copy64(reinterpret_cast<const volatile int64_t*>(&store_value), reinterpret_cast<volatile int64_t*>(dest));
}

#endif // OS_CPU_LINUX_ZERO_ATOMIC_LINUX_ZERO_HPP
