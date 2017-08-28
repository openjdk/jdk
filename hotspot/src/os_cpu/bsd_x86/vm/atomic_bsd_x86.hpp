/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_BSD_X86_VM_ATOMIC_BSD_X86_HPP
#define OS_CPU_BSD_X86_VM_ATOMIC_BSD_X86_HPP

// Implementation of class atomic

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


template<size_t byte_size>
struct Atomic::PlatformAdd
  : Atomic::FetchAndAdd<Atomic::PlatformAdd<byte_size> >
{
  template<typename I, typename D>
  D fetch_and_add(I add_value, D volatile* dest) const;
};

template<>
template<typename I, typename D>
inline D Atomic::PlatformAdd<4>::fetch_and_add(I add_value, D volatile* dest) const {
  STATIC_ASSERT(4 == sizeof(I));
  STATIC_ASSERT(4 == sizeof(D));
  D old_value;
  __asm__ volatile (  "lock xaddl %0,(%2)"
                    : "=r" (old_value)
                    : "0" (add_value), "r" (dest)
                    : "cc", "memory");
  return old_value;
}

inline void Atomic::inc    (volatile jint*     dest) {
  __asm__ volatile (  "lock addl $1,(%0)" :
                    : "r" (dest) : "cc", "memory");
}

inline void Atomic::inc_ptr(volatile void*     dest) {
  inc_ptr((volatile intptr_t*)dest);
}

inline void Atomic::dec    (volatile jint*     dest) {
  __asm__ volatile (  "lock subl $1,(%0)" :
                    : "r" (dest) : "cc", "memory");
}

inline void Atomic::dec_ptr(volatile void*     dest) {
  dec_ptr((volatile intptr_t*)dest);
}

inline jint     Atomic::xchg    (jint     exchange_value, volatile jint*     dest) {
  __asm__ volatile (  "xchgl (%2),%0"
                    : "=r" (exchange_value)
                    : "0" (exchange_value), "r" (dest)
                    : "memory");
  return exchange_value;
}

inline void*    Atomic::xchg_ptr(void*    exchange_value, volatile void*     dest) {
  return (void*)xchg_ptr((intptr_t)exchange_value, (volatile intptr_t*)dest);
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<1>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                cmpxchg_memory_order /* order */) const {
  STATIC_ASSERT(1 == sizeof(T));
  __asm__ volatile (  "lock cmpxchgb %1,(%3)"
                    : "=a" (exchange_value)
                    : "q" (exchange_value), "a" (compare_value), "r" (dest)
                    : "cc", "memory");
  return exchange_value;
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<4>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                cmpxchg_memory_order /* order */) const {
  STATIC_ASSERT(4 == sizeof(T));
  __asm__ volatile (  "lock cmpxchgl %1,(%3)"
                    : "=a" (exchange_value)
                    : "r" (exchange_value), "a" (compare_value), "r" (dest)
                    : "cc", "memory");
  return exchange_value;
}

#ifdef AMD64
inline void Atomic::store    (jlong    store_value, jlong*    dest) { *dest = store_value; }
inline void Atomic::store    (jlong    store_value, volatile jlong*    dest) { *dest = store_value; }

template<>
template<typename I, typename D>
inline D Atomic::PlatformAdd<8>::fetch_and_add(I add_value, D volatile* dest) const {
  STATIC_ASSERT(8 == sizeof(I));
  STATIC_ASSERT(8 == sizeof(D));
  D old_value;
  __asm__ __volatile__ (  "lock xaddq %0,(%2)"
                        : "=r" (old_value)
                        : "0" (add_value), "r" (dest)
                        : "cc", "memory");
  return old_value;
}

inline void Atomic::inc_ptr(volatile intptr_t* dest) {
  __asm__ __volatile__ (  "lock addq $1,(%0)"
                        :
                        : "r" (dest)
                        : "cc", "memory");
}

inline void Atomic::dec_ptr(volatile intptr_t* dest) {
  __asm__ __volatile__ (  "lock subq $1,(%0)"
                        :
                        : "r" (dest)
                        : "cc", "memory");
}

inline intptr_t Atomic::xchg_ptr(intptr_t exchange_value, volatile intptr_t* dest) {
  __asm__ __volatile__ ("xchgq (%2),%0"
                        : "=r" (exchange_value)
                        : "0" (exchange_value), "r" (dest)
                        : "memory");
  return exchange_value;
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<8>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                cmpxchg_memory_order /* order */) const {
  STATIC_ASSERT(8 == sizeof(T));
  __asm__ __volatile__ (  "lock cmpxchgq %1,(%3)"
                        : "=a" (exchange_value)
                        : "r" (exchange_value), "a" (compare_value), "r" (dest)
                        : "cc", "memory");
  return exchange_value;
}

inline jlong Atomic::load(const volatile jlong* src) { return *src; }

#else // !AMD64

inline void Atomic::inc_ptr(volatile intptr_t* dest) {
  inc((volatile jint*)dest);
}

inline void Atomic::dec_ptr(volatile intptr_t* dest) {
  dec((volatile jint*)dest);
}

inline intptr_t Atomic::xchg_ptr(intptr_t exchange_value, volatile intptr_t* dest) {
  return (intptr_t)xchg((jint)exchange_value, (volatile jint*)dest);
}

extern "C" {
  // defined in bsd_x86.s
  jlong _Atomic_cmpxchg_long(jlong, volatile jlong*, jlong, bool);
  void _Atomic_move_long(const volatile jlong* src, volatile jlong* dst);
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<8>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                cmpxchg_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  return cmpxchg_using_helper<jlong>(_Atomic_cmpxchg_long, exchange_value, dest, compare_value);
}

inline jlong Atomic::load(const volatile jlong* src) {
  volatile jlong dest;
  _Atomic_move_long(src, &dest);
  return dest;
}

inline void Atomic::store(jlong store_value, jlong* dest) {
  _Atomic_move_long((volatile jlong*)&store_value, (volatile jlong*)dest);
}

inline void Atomic::store(jlong store_value, volatile jlong* dest) {
  _Atomic_move_long((volatile jlong*)&store_value, dest);
}

#endif // AMD64

#endif // OS_CPU_BSD_X86_VM_ATOMIC_BSD_X86_HPP
