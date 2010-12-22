/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2007, 2008 Red Hat, Inc.
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

#ifndef OS_CPU_LINUX_ZERO_VM_ATOMIC_LINUX_ZERO_INLINE_HPP
#define OS_CPU_LINUX_ZERO_VM_ATOMIC_LINUX_ZERO_INLINE_HPP

#include "orderAccess_linux_zero.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "vm_version_zero.hpp"

// Implementation of class atomic

#ifdef M68K

/*
 * __m68k_cmpxchg
 *
 * Atomically store newval in *ptr if *ptr is equal to oldval for user space.
 * Returns newval on success and oldval if no exchange happened.
 * This implementation is processor specific and works on
 * 68020 68030 68040 and 68060.
 *
 * It will not work on ColdFire, 68000 and 68010 since they lack the CAS
 * instruction.
 * Using a kernelhelper would be better for arch complete implementation.
 *
 */

static inline int __m68k_cmpxchg(int oldval, int newval, volatile int *ptr) {
  int ret;
  __asm __volatile ("cas%.l %0,%2,%1"
                   : "=d" (ret), "+m" (*(ptr))
                   : "d" (newval), "0" (oldval));
  return ret;
}

/* Perform an atomic compare and swap: if the current value of `*PTR'
   is OLDVAL, then write NEWVAL into `*PTR'.  Return the contents of
   `*PTR' before the operation.*/
static inline int m68k_compare_and_swap(volatile int *ptr,
                                        int oldval,
                                        int newval) {
  for (;;) {
      int prev = *ptr;
      if (prev != oldval)
        return prev;

      if (__m68k_cmpxchg (prev, newval, ptr) == newval)
        // Success.
        return prev;

      // We failed even though prev == oldval.  Try again.
    }
}

/* Atomically add an int to memory.  */
static inline int m68k_add_and_fetch(volatile int *ptr, int add_value) {
  for (;;) {
      // Loop until success.

      int prev = *ptr;

      if (__m68k_cmpxchg (prev, prev + add_value, ptr) == prev + add_value)
        return prev + add_value;
    }
}

/* Atomically write VALUE into `*PTR' and returns the previous
   contents of `*PTR'.  */
static inline int m68k_lock_test_and_set(volatile int *ptr, int newval) {
  for (;;) {
      // Loop until success.
      int prev = *ptr;

      if (__m68k_cmpxchg (prev, newval, ptr) == prev)
        return prev;
    }
}
#endif // M68K

#ifdef ARM

/*
 * __kernel_cmpxchg
 *
 * Atomically store newval in *ptr if *ptr is equal to oldval for user space.
 * Return zero if *ptr was changed or non-zero if no exchange happened.
 * The C flag is also set if *ptr was changed to allow for assembly
 * optimization in the calling code.
 *
 */

typedef int (__kernel_cmpxchg_t)(int oldval, int newval, volatile int *ptr);
#define __kernel_cmpxchg (*(__kernel_cmpxchg_t *) 0xffff0fc0)



/* Perform an atomic compare and swap: if the current value of `*PTR'
   is OLDVAL, then write NEWVAL into `*PTR'.  Return the contents of
   `*PTR' before the operation.*/
static inline int arm_compare_and_swap(volatile int *ptr,
                                       int oldval,
                                       int newval) {
  for (;;) {
      int prev = *ptr;
      if (prev != oldval)
        return prev;

      if (__kernel_cmpxchg (prev, newval, ptr) == 0)
        // Success.
        return prev;

      // We failed even though prev == oldval.  Try again.
    }
}

/* Atomically add an int to memory.  */
static inline int arm_add_and_fetch(volatile int *ptr, int add_value) {
  for (;;) {
      // Loop until a __kernel_cmpxchg succeeds.

      int prev = *ptr;

      if (__kernel_cmpxchg (prev, prev + add_value, ptr) == 0)
        return prev + add_value;
    }
}

/* Atomically write VALUE into `*PTR' and returns the previous
   contents of `*PTR'.  */
static inline int arm_lock_test_and_set(volatile int *ptr, int newval) {
  for (;;) {
      // Loop until a __kernel_cmpxchg succeeds.
      int prev = *ptr;

      if (__kernel_cmpxchg (prev, newval, ptr) == 0)
        return prev;
    }
}
#endif // ARM

inline void Atomic::store(jint store_value, volatile jint* dest) {
  *dest = store_value;
}

inline void Atomic::store_ptr(intptr_t store_value, intptr_t* dest) {
  *dest = store_value;
}

inline jint Atomic::add(jint add_value, volatile jint* dest) {
#ifdef ARM
  return arm_add_and_fetch(dest, add_value);
#else
#ifdef M68K
  return m68k_add_and_fetch(dest, add_value);
#else
  return __sync_add_and_fetch(dest, add_value);
#endif // M68K
#endif // ARM
}

inline intptr_t Atomic::add_ptr(intptr_t add_value, volatile intptr_t* dest) {
#ifdef ARM
  return arm_add_and_fetch(dest, add_value);
#else
#ifdef M68K
  return m68k_add_and_fetch(dest, add_value);
#else
  return __sync_add_and_fetch(dest, add_value);
#endif // M68K
#endif // ARM
}

inline void* Atomic::add_ptr(intptr_t add_value, volatile void* dest) {
  return (void *) add_ptr(add_value, (volatile intptr_t *) dest);
}

inline void Atomic::inc(volatile jint* dest) {
  add(1, dest);
}

inline void Atomic::inc_ptr(volatile intptr_t* dest) {
  add_ptr(1, dest);
}

inline void Atomic::inc_ptr(volatile void* dest) {
  add_ptr(1, dest);
}

inline void Atomic::dec(volatile jint* dest) {
  add(-1, dest);
}

inline void Atomic::dec_ptr(volatile intptr_t* dest) {
  add_ptr(-1, dest);
}

inline void Atomic::dec_ptr(volatile void* dest) {
  add_ptr(-1, dest);
}

inline jint Atomic::xchg(jint exchange_value, volatile jint* dest) {
#ifdef ARM
  return arm_lock_test_and_set(dest, exchange_value);
#else
#ifdef M68K
  return m68k_lock_test_and_set(dest, exchange_value);
#else
  // __sync_lock_test_and_set is a bizarrely named atomic exchange
  // operation.  Note that some platforms only support this with the
  // limitation that the only valid value to store is the immediate
  // constant 1.  There is a test for this in JNI_CreateJavaVM().
  return __sync_lock_test_and_set (dest, exchange_value);
#endif // M68K
#endif // ARM
}

inline intptr_t Atomic::xchg_ptr(intptr_t exchange_value,
                                 volatile intptr_t* dest) {
#ifdef ARM
  return arm_lock_test_and_set(dest, exchange_value);
#else
#ifdef M68K
  return m68k_lock_test_and_set(dest, exchange_value);
#else
  return __sync_lock_test_and_set (dest, exchange_value);
#endif // M68K
#endif // ARM
}

inline void* Atomic::xchg_ptr(void* exchange_value, volatile void* dest) {
  return (void *) xchg_ptr((intptr_t) exchange_value,
                           (volatile intptr_t*) dest);
}

inline jint Atomic::cmpxchg(jint exchange_value,
                            volatile jint* dest,
                            jint compare_value) {
#ifdef ARM
  return arm_compare_and_swap(dest, compare_value, exchange_value);
#else
#ifdef M68K
  return m68k_compare_and_swap(dest, compare_value, exchange_value);
#else
  return __sync_val_compare_and_swap(dest, compare_value, exchange_value);
#endif // M68K
#endif // ARM
}

inline jlong Atomic::cmpxchg(jlong exchange_value,
                             volatile jlong* dest,
                             jlong compare_value) {

  return __sync_val_compare_and_swap(dest, compare_value, exchange_value);
}

inline intptr_t Atomic::cmpxchg_ptr(intptr_t exchange_value,
                                    volatile intptr_t* dest,
                                    intptr_t compare_value) {
#ifdef ARM
  return arm_compare_and_swap(dest, compare_value, exchange_value);
#else
#ifdef M68K
  return m68k_compare_and_swap(dest, compare_value, exchange_value);
#else
  return __sync_val_compare_and_swap(dest, compare_value, exchange_value);
#endif // M68K
#endif // ARM
}

inline void* Atomic::cmpxchg_ptr(void* exchange_value,
                                 volatile void* dest,
                                 void* compare_value) {

  return (void *) cmpxchg_ptr((intptr_t) exchange_value,
                              (volatile intptr_t*) dest,
                              (intptr_t) compare_value);
}

#endif // OS_CPU_LINUX_ZERO_VM_ATOMIC_LINUX_ZERO_INLINE_HPP
