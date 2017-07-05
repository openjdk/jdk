/*
 * Copyright (c) 1999, 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifndef OS_CPU_LINUX_AARCH64_VM_ATOMIC_LINUX_AARCH64_HPP
#define OS_CPU_LINUX_AARCH64_VM_ATOMIC_LINUX_AARCH64_HPP

#include "vm_version_aarch64.hpp"

// Implementation of class atomic

#define FULL_MEM_BARRIER  __sync_synchronize()
#define READ_MEM_BARRIER  __atomic_thread_fence(__ATOMIC_ACQUIRE);
#define WRITE_MEM_BARRIER __atomic_thread_fence(__ATOMIC_RELEASE);

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


inline jint Atomic::add(jint add_value, volatile jint* dest)
{
 return __sync_add_and_fetch(dest, add_value);
}

inline void Atomic::inc(volatile jint* dest)
{
 add(1, dest);
}

inline void Atomic::inc_ptr(volatile void* dest)
{
 add_ptr(1, dest);
}

inline void Atomic::dec (volatile jint* dest)
{
 add(-1, dest);
}

inline void Atomic::dec_ptr(volatile void* dest)
{
 add_ptr(-1, dest);
}

inline jint Atomic::xchg (jint exchange_value, volatile jint* dest)
{
  jint res = __sync_lock_test_and_set (dest, exchange_value);
  FULL_MEM_BARRIER;
  return res;
}

inline void* Atomic::xchg_ptr(void* exchange_value, volatile void* dest)
{
  return (void *) xchg_ptr((intptr_t) exchange_value,
                           (volatile intptr_t*) dest);
}

template <typename T> T generic_cmpxchg(T exchange_value, volatile T* dest,
                                        T compare_value, cmpxchg_memory_order order)
{
  if (order == memory_order_relaxed) {
    T value = compare_value;
    __atomic_compare_exchange(dest, &value, &exchange_value, /*weak*/false,
                              __ATOMIC_RELAXED, __ATOMIC_RELAXED);
    return value;
  } else {
    return __sync_val_compare_and_swap(dest, compare_value, exchange_value);
  }
}

#define VM_HAS_SPECIALIZED_CMPXCHG_BYTE
inline jbyte Atomic::cmpxchg (jbyte exchange_value, volatile jbyte* dest, jbyte compare_value, cmpxchg_memory_order order)
{
  return generic_cmpxchg(exchange_value, dest, compare_value, order);
}

inline jint Atomic::cmpxchg (jint exchange_value, volatile jint* dest, jint compare_value, cmpxchg_memory_order order)
{
  return generic_cmpxchg(exchange_value, dest, compare_value, order);
}

inline void Atomic::store (jlong store_value, jlong* dest) { *dest = store_value; }
inline void Atomic::store (jlong store_value, volatile jlong* dest) { *dest = store_value; }

inline intptr_t Atomic::add_ptr(intptr_t add_value, volatile intptr_t* dest)
{
 return __sync_add_and_fetch(dest, add_value);
}

inline void* Atomic::add_ptr(intptr_t add_value, volatile void* dest)
{
  return (void *) add_ptr(add_value, (volatile intptr_t *) dest);
}

inline void Atomic::inc_ptr(volatile intptr_t* dest)
{
 add_ptr(1, dest);
}

inline void Atomic::dec_ptr(volatile intptr_t* dest)
{
 add_ptr(-1, dest);
}

inline intptr_t Atomic::xchg_ptr(intptr_t exchange_value, volatile intptr_t* dest)
{
  intptr_t res = __sync_lock_test_and_set (dest, exchange_value);
  FULL_MEM_BARRIER;
  return res;
}

inline jlong Atomic::cmpxchg (jlong exchange_value, volatile jlong* dest, jlong compare_value, cmpxchg_memory_order order)
{
  return generic_cmpxchg(exchange_value, dest, compare_value, order);
}

inline intptr_t Atomic::cmpxchg_ptr(intptr_t exchange_value, volatile intptr_t* dest, intptr_t compare_value, cmpxchg_memory_order order)
{
  return generic_cmpxchg(exchange_value, dest, compare_value, order);
}

inline void* Atomic::cmpxchg_ptr(void* exchange_value, volatile void* dest, void* compare_value, cmpxchg_memory_order order)
{
  return (void *) cmpxchg_ptr((intptr_t) exchange_value,
                              (volatile intptr_t*) dest,
                              (intptr_t) compare_value,
                              order);
}

inline jlong Atomic::load(volatile jlong* src) { return *src; }

#endif // OS_CPU_LINUX_AARCH64_VM_ATOMIC_LINUX_AARCH64_HPP
