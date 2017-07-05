/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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

inline void Atomic::store    (jbyte    store_value, jbyte*    dest) { *dest = store_value; }
inline void Atomic::store    (jshort   store_value, jshort*   dest) { *dest = store_value; }
inline void Atomic::store    (jint     store_value, jint*     dest) { *dest = store_value; }
inline void Atomic::store_ptr(intptr_t store_value, intptr_t* dest) { *dest = store_value; }
inline void Atomic::store_ptr(void*    store_value, void*     dest) { *(void**)dest = store_value; }

inline void Atomic::store    (jbyte    store_value, volatile jbyte*    dest) { *dest = store_value; }
inline void Atomic::store    (jshort   store_value, volatile jshort*   dest) { *dest = store_value; }
inline void Atomic::store    (jint     store_value, volatile jint*     dest) { *dest = store_value; }
inline void Atomic::store_ptr(intptr_t store_value, volatile intptr_t* dest) { *dest = store_value; }
inline void Atomic::store_ptr(void*    store_value, volatile void*     dest) { *(void* volatile *)dest = store_value; }

inline jlong Atomic::load (volatile jlong* src) {
  assert(((intx)src & (sizeof(jlong)-1)) == 0, "Atomic load jlong mis-aligned");
#ifdef AARCH64
  return *src;
#else
  return (*os::atomic_load_long_func)(src);
#endif
}

inline void Atomic::store (jlong value, volatile jlong* dest) {
  assert(((intx)dest & (sizeof(jlong)-1)) == 0, "Atomic store jlong mis-aligned");
#ifdef AARCH64
  *dest = value;
#else
  (*os::atomic_store_long_func)(value, dest);
#endif
}

inline void Atomic::store (jlong value, jlong* dest) {
  store(value, (volatile jlong*)dest);
}

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

inline jint Atomic::add(jint add_value, volatile jint* dest) {
#ifdef AARCH64
  jint val;
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
  return (*os::atomic_add_func)(add_value, dest);
#endif
}

inline void Atomic::inc(volatile jint* dest) {
  Atomic::add(1, (volatile jint *)dest);
}

inline void Atomic::dec(volatile jint* dest) {
  Atomic::add(-1, (volatile jint *)dest);
}

inline intptr_t Atomic::add_ptr(intptr_t add_value, volatile intptr_t* dest) {
#ifdef AARCH64
  intptr_t val;
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
#else
  return (intptr_t)Atomic::add((jint)add_value, (volatile jint*)dest);
#endif
}

inline void* Atomic::add_ptr(intptr_t add_value, volatile void* dest) {
  return (void*)add_ptr(add_value, (volatile intptr_t*)dest);
}

inline void Atomic::inc_ptr(volatile intptr_t* dest) {
  Atomic::add_ptr(1, dest);
}

inline void Atomic::dec_ptr(volatile intptr_t* dest) {
  Atomic::add_ptr(-1, dest);
}

inline void Atomic::inc_ptr(volatile void* dest) {
  inc_ptr((volatile intptr_t*)dest);
}

inline void Atomic::dec_ptr(volatile void* dest) {
  dec_ptr((volatile intptr_t*)dest);
}


inline jint Atomic::xchg(jint exchange_value, volatile jint* dest) {
#ifdef AARCH64
  jint old_val;
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
  return (*os::atomic_xchg_func)(exchange_value, dest);
#endif
}

inline intptr_t Atomic::xchg_ptr(intptr_t exchange_value, volatile intptr_t* dest) {
#ifdef AARCH64
  intptr_t old_val;
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
#else
  return (intptr_t)xchg((jint)exchange_value, (volatile jint*)dest);
#endif
}

inline void* Atomic::xchg_ptr(void* exchange_value, volatile void* dest) {
  return (void*)xchg_ptr((intptr_t)exchange_value, (volatile intptr_t*)dest);
}

// The memory_order parameter is ignored - we always provide the strongest/most-conservative ordering

inline jint Atomic::cmpxchg(jint exchange_value, volatile jint* dest, jint compare_value, cmpxchg_memory_order order) {
#ifdef AARCH64
  jint rv;
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
  // Warning:  Arguments are swapped to avoid moving them for kernel call
  return (*os::atomic_cmpxchg_func)(compare_value, exchange_value, dest);
#endif
}

inline jlong Atomic::cmpxchg (jlong exchange_value, volatile jlong* dest, jlong compare_value, cmpxchg_memory_order order) {
#ifdef AARCH64
  jlong rv;
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
  assert(VM_Version::supports_cx8(), "Atomic compare and exchange jlong not supported on this architecture!");
  return (*os::atomic_cmpxchg_long_func)(compare_value, exchange_value, dest);
#endif
}

inline intptr_t Atomic::cmpxchg_ptr(intptr_t exchange_value, volatile intptr_t* dest, intptr_t compare_value, cmpxchg_memory_order order) {
#ifdef AARCH64
  return (intptr_t)cmpxchg((jlong)exchange_value, (volatile jlong*)dest, (jlong)compare_value, order);
#else
  return (intptr_t)cmpxchg((jint)exchange_value, (volatile jint*)dest, (jint)compare_value, order);
#endif
}

inline void* Atomic::cmpxchg_ptr(void* exchange_value, volatile void* dest, void* compare_value, cmpxchg_memory_order order) {
  return (void*)cmpxchg_ptr((intptr_t)exchange_value, (volatile intptr_t*)dest, (intptr_t)compare_value, order);
}

#endif // OS_CPU_LINUX_ARM_VM_ATOMIC_LINUX_ARM_HPP
