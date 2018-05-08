/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_BSD_ZERO_VM_ATOMIC_BSD_ZERO_HPP
#define OS_CPU_BSD_ZERO_VM_ATOMIC_BSD_ZERO_HPP

#include "runtime/os.hpp"

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
static inline int m68k_compare_and_swap(int newval,
                                        volatile int *ptr,
                                        int oldval) {
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
static inline int m68k_add_and_fetch(int add_value, volatile int *ptr) {
  for (;;) {
      // Loop until success.

      int prev = *ptr;

      if (__m68k_cmpxchg (prev, prev + add_value, ptr) == prev + add_value)
        return prev + add_value;
    }
}

/* Atomically write VALUE into `*PTR' and returns the previous
   contents of `*PTR'.  */
static inline int m68k_lock_test_and_set(int newval, volatile int *ptr) {
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
static inline int arm_compare_and_swap(int newval,
                                       volatile int *ptr,
                                       int oldval) {
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
static inline int arm_add_and_fetch(int add_value, volatile int *ptr) {
  for (;;) {
      // Loop until a __kernel_cmpxchg succeeds.

      int prev = *ptr;

      if (__kernel_cmpxchg (prev, prev + add_value, ptr) == 0)
        return prev + add_value;
    }
}

/* Atomically write VALUE into `*PTR' and returns the previous
   contents of `*PTR'.  */
static inline int arm_lock_test_and_set(int newval, volatile int *ptr) {
  for (;;) {
      // Loop until a __kernel_cmpxchg succeeds.
      int prev = *ptr;

      if (__kernel_cmpxchg (prev, newval, ptr) == 0)
        return prev;
    }
}
#endif // ARM

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

#ifdef ARM
  return add_using_helper<int>(arm_add_and_fetch, add_value, dest);
#else
#ifdef M68K
  return add_using_helper<int>(m68k_add_and_fetch, add_value, dest);
#else
  return __sync_add_and_fetch(dest, add_value);
#endif // M68K
#endif // ARM
}

template<>
template<typename I, typename D>
inline D Atomic::PlatformAdd<8>::add_and_fetch(I add_value, D volatile* dest,
                                               atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(I));
  STATIC_ASSERT(8 == sizeof(D));

  return __sync_add_and_fetch(dest, add_value);
}

template<>
template<typename T>
inline T Atomic::PlatformXchg<4>::operator()(T exchange_value,
                                             T volatile* dest,
                                             atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
#ifdef ARM
  return xchg_using_helper<int>(arm_lock_test_and_set, exchange_value, dest);
#else
#ifdef M68K
  return xchg_using_helper<int>(m68k_lock_test_and_set, exchange_value, dest);
#else
  // __sync_lock_test_and_set is a bizarrely named atomic exchange
  // operation.  Note that some platforms only support this with the
  // limitation that the only valid value to store is the immediate
  // constant 1.  There is a test for this in JNI_CreateJavaVM().
  T result = __sync_lock_test_and_set (dest, exchange_value);
  // All atomic operations are expected to be full memory barriers
  // (see atomic.hpp). However, __sync_lock_test_and_set is not
  // a full memory barrier, but an acquire barrier. Hence, this added
  // barrier.
  __sync_synchronize();
  return result;
#endif // M68K
#endif // ARM
}

template<>
template<typename T>
inline T Atomic::PlatformXchg<8>::operator()(T exchange_value,
                                             T volatile* dest,
                                             atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  T result = __sync_lock_test_and_set (dest, exchange_value);
  __sync_synchronize();
  return result;
}

// No direct support for cmpxchg of bytes; emulate using int.
template<>
struct Atomic::PlatformCmpxchg<1> : Atomic::CmpxchgByteUsingInt {};

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<4>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
#ifdef ARM
  return cmpxchg_using_helper<int>(arm_compare_and_swap, exchange_value, dest, compare_value);
#else
#ifdef M68K
  return cmpxchg_using_helper<int>(m68k_compare_and_swap, exchange_value, dest, compare_value);
#else
  return __sync_val_compare_and_swap(dest, compare_value, exchange_value);
#endif // M68K
#endif // ARM
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<8>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  return __sync_val_compare_and_swap(dest, compare_value, exchange_value);
}

template<>
template<typename T>
inline T Atomic::PlatformLoad<8>::operator()(T const volatile* src) const {
  STATIC_ASSERT(8 == sizeof(T));
  volatile int64_t dest;
  os::atomic_copy64(reinterpret_cast<const volatile int64_t*>(src), reinterpret_cast<volatile int64_t*>(&dest));
  return PrimitiveConversions::cast<T>(dest);
}

template<>
template<typename T>
inline void Atomic::PlatformStore<8>::operator()(T store_value,
                                                 T volatile* dest) const {
  STATIC_ASSERT(8 == sizeof(T));
  os::atomic_copy64(reinterpret_cast<const volatile int64_t*>(&store_value), reinterpret_cast<volatile int64_t*>(dest));
}

#endif // OS_CPU_BSD_ZERO_VM_ATOMIC_BSD_ZERO_HPP
