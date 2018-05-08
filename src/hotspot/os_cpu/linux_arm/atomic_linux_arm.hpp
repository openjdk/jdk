/*
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_LINUX_ARM_VM_ATOMIC_LINUX_ARM_HPP
#define OS_CPU_LINUX_ARM_VM_ATOMIC_LINUX_ARM_HPP

#include "runtime/os.hpp"
#include "vm_version_arm.hpp"

// Implementation of class atomic

/*
 * Atomic long operations on 32-bit ARM
 * ARM v7 supports LDREXD/STREXD synchronization instructions so no problem.
 * ARM < v7 does not have explicit 64 atomic load/store capability.
 * However, gcc emits LDRD/STRD instructions on v5te and LDM/STM on v5t
 * when loading/storing 64 bits.
 * For non-MP machines (which is all we support for ARM < v7)
 * under current Linux distros these instructions appear atomic.
 * See section A3.5.3 of ARM Architecture Reference Manual for ARM v7.
 * Also, for cmpxchg64, if ARM < v7 we check for cmpxchg64 support in the
 * Linux kernel using _kuser_helper_version. See entry-armv.S in the Linux
 * kernel source or kernel_user_helpers.txt in Linux Doc.
 */

#ifndef AARCH64
template<>
template<typename T>
inline T Atomic::PlatformLoad<8>::operator()(T const volatile* src) const {
  STATIC_ASSERT(8 == sizeof(T));
  return PrimitiveConversions::cast<T>(
    (*os::atomic_load_long_func)(reinterpret_cast<const volatile int64_t*>(src)));
}

template<>
template<typename T>
inline void Atomic::PlatformStore<8>::operator()(T store_value,
                                                 T volatile* dest) const {
  STATIC_ASSERT(8 == sizeof(T));
  (*os::atomic_store_long_func)(
    PrimitiveConversions::cast<int64_t>(store_value), reinterpret_cast<volatile int64_t*>(dest));
}
#endif

// As per atomic.hpp all read-modify-write operations have to provide two-way
// barriers semantics. For AARCH64 we are using load-acquire-with-reservation and
// store-release-with-reservation. While load-acquire combined with store-release
// do not generally form two-way barriers, their use with reservations does - the
// ARMv8 architecture manual Section F "Barrier Litmus Tests" indicates they
// provide sequentially consistent semantics. All we need to add is an explicit
// barrier in the failure path of the cmpxchg operations (as these don't execute
// the store) - arguably this may be overly cautious as there is a very low
// likelihood that the hardware would pull loads/stores into the region guarded
// by the reservation.
//
// For ARMv7 we add explicit barriers in the stubs.

template<size_t byte_size>
struct Atomic::PlatformAdd
  : Atomic::AddAndFetch<Atomic::PlatformAdd<byte_size> >
{
  template<typename I, typename D>
  D add_and_fetch(I add_value, D volatile* dest, atomic_memory_order order) const;
};

template<>
template<typename I, typename D>
inline D Atomic::PlatformAdd<4>::add_and_fetch(I add_value, D volatile* dest,
                                               atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(I));
  STATIC_ASSERT(4 == sizeof(D));
#ifdef AARCH64
  D val;
  int tmp;
  __asm__ volatile(
    "1:\n\t"
    " ldaxr %w[val], [%[dest]]\n\t"
    " add %w[val], %w[val], %w[add_val]\n\t"
    " stlxr %w[tmp], %w[val], [%[dest]]\n\t"
    " cbnz %w[tmp], 1b\n\t"
    : [val] "=&r" (val), [tmp] "=&r" (tmp)
    : [add_val] "r" (add_value), [dest] "r" (dest)
    : "memory");
  return val;
#else
  return add_using_helper<int32_t>(os::atomic_add_func, add_value, dest);
#endif
}

#ifdef AARCH64
template<>
template<typename I, typename D>
inline D Atomic::PlatformAdd<8>::add_and_fetch(I add_value, D volatile* dest,
                                               atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(I));
  STATIC_ASSERT(8 == sizeof(D));
  D val;
  int tmp;
  __asm__ volatile(
    "1:\n\t"
    " ldaxr %[val], [%[dest]]\n\t"
    " add %[val], %[val], %[add_val]\n\t"
    " stlxr %w[tmp], %[val], [%[dest]]\n\t"
    " cbnz %w[tmp], 1b\n\t"
    : [val] "=&r" (val), [tmp] "=&r" (tmp)
    : [add_val] "r" (add_value), [dest] "r" (dest)
    : "memory");
  return val;
}
#endif

template<>
template<typename T>
inline T Atomic::PlatformXchg<4>::operator()(T exchange_value,
                                             T volatile* dest,
                                             atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
#ifdef AARCH64
  T old_val;
  int tmp;
  __asm__ volatile(
    "1:\n\t"
    " ldaxr %w[old_val], [%[dest]]\n\t"
    " stlxr %w[tmp], %w[new_val], [%[dest]]\n\t"
    " cbnz %w[tmp], 1b\n\t"
    : [old_val] "=&r" (old_val), [tmp] "=&r" (tmp)
    : [new_val] "r" (exchange_value), [dest] "r" (dest)
    : "memory");
  return old_val;
#else
  return xchg_using_helper<int32_t>(os::atomic_xchg_func, exchange_value, dest);
#endif
}

#ifdef AARCH64
template<>
template<typename T>
inline T Atomic::PlatformXchg<8>::operator()(T exchange_value,
                                             T volatile* dest,
                                             atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  T old_val;
  int tmp;
  __asm__ volatile(
    "1:\n\t"
    " ldaxr %[old_val], [%[dest]]\n\t"
    " stlxr %w[tmp], %[new_val], [%[dest]]\n\t"
    " cbnz %w[tmp], 1b\n\t"
    : [old_val] "=&r" (old_val), [tmp] "=&r" (tmp)
    : [new_val] "r" (exchange_value), [dest] "r" (dest)
    : "memory");
  return old_val;
}
#endif // AARCH64

// The memory_order parameter is ignored - we always provide the strongest/most-conservative ordering

// No direct support for cmpxchg of bytes; emulate using int.
template<>
struct Atomic::PlatformCmpxchg<1> : Atomic::CmpxchgByteUsingInt {};

#ifndef AARCH64

inline int32_t reorder_cmpxchg_func(int32_t exchange_value,
                                    int32_t volatile* dest,
                                    int32_t compare_value) {
  // Warning:  Arguments are swapped to avoid moving them for kernel call
  return (*os::atomic_cmpxchg_func)(compare_value, exchange_value, dest);
}

inline int64_t reorder_cmpxchg_long_func(int64_t exchange_value,
                                         int64_t volatile* dest,
                                         int64_t compare_value) {
  assert(VM_Version::supports_cx8(), "Atomic compare and exchange int64_t not supported on this architecture!");
  // Warning:  Arguments are swapped to avoid moving them for kernel call
  return (*os::atomic_cmpxchg_long_func)(compare_value, exchange_value, dest);
}

#endif // !AARCH64

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<4>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
#ifdef AARCH64
  T rv;
  int tmp;
  __asm__ volatile(
    "1:\n\t"
    " ldaxr %w[rv], [%[dest]]\n\t"
    " cmp %w[rv], %w[cv]\n\t"
    " b.ne 2f\n\t"
    " stlxr %w[tmp], %w[ev], [%[dest]]\n\t"
    " cbnz %w[tmp], 1b\n\t"
    " b 3f\n\t"
    "2:\n\t"
    " dmb sy\n\t"
    "3:\n\t"
    : [rv] "=&r" (rv), [tmp] "=&r" (tmp)
    : [ev] "r" (exchange_value), [dest] "r" (dest), [cv] "r" (compare_value)
    : "memory");
  return rv;
#else
  return cmpxchg_using_helper<int32_t>(reorder_cmpxchg_func, exchange_value, dest, compare_value);
#endif
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<8>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
#ifdef AARCH64
  T rv;
  int tmp;
  __asm__ volatile(
    "1:\n\t"
    " ldaxr %[rv], [%[dest]]\n\t"
    " cmp %[rv], %[cv]\n\t"
    " b.ne 2f\n\t"
    " stlxr %w[tmp], %[ev], [%[dest]]\n\t"
    " cbnz %w[tmp], 1b\n\t"
    " b 3f\n\t"
    "2:\n\t"
    " dmb sy\n\t"
    "3:\n\t"
    : [rv] "=&r" (rv), [tmp] "=&r" (tmp)
    : [ev] "r" (exchange_value), [dest] "r" (dest), [cv] "r" (compare_value)
    : "memory");
  return rv;
#else
  return cmpxchg_using_helper<int64_t>(reorder_cmpxchg_long_func, exchange_value, dest, compare_value);
#endif
}

#endif // OS_CPU_LINUX_ARM_VM_ATOMIC_LINUX_ARM_HPP
