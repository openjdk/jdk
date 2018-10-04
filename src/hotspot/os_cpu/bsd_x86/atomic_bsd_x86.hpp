/*
 * Copyright (c) 1999, 2018, Oracle and/or its affiliates. All rights reserved.
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

template<size_t byte_size>
struct Atomic::PlatformAdd
  : Atomic::FetchAndAdd<Atomic::PlatformAdd<byte_size> >
{
  template<typename I, typename D>
  D fetch_and_add(I add_value, D volatile* dest, atomic_memory_order /* order */) const;
};

template<>
template<typename I, typename D>
inline D Atomic::PlatformAdd<4>::fetch_and_add(I add_value, D volatile* dest,
                                               atomic_memory_order /* order */) const {
  STATIC_ASSERT(4 == sizeof(I));
  STATIC_ASSERT(4 == sizeof(D));
  D old_value;
  __asm__ volatile (  "lock xaddl %0,(%2)"
                    : "=r" (old_value)
                    : "0" (add_value), "r" (dest)
                    : "cc", "memory");
  return old_value;
}

template<>
template<typename T>
inline T Atomic::PlatformXchg<4>::operator()(T exchange_value,
                                             T volatile* dest,
                                             atomic_memory_order /* order */) const {
  STATIC_ASSERT(4 == sizeof(T));
  __asm__ volatile (  "xchgl (%2),%0"
                    : "=r" (exchange_value)
                    : "0" (exchange_value), "r" (dest)
                    : "memory");
  return exchange_value;
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<1>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                atomic_memory_order /* order */) const {
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
                                                atomic_memory_order /* order */) const {
  STATIC_ASSERT(4 == sizeof(T));
  __asm__ volatile (  "lock cmpxchgl %1,(%3)"
                    : "=a" (exchange_value)
                    : "r" (exchange_value), "a" (compare_value), "r" (dest)
                    : "cc", "memory");
  return exchange_value;
}

#ifdef AMD64
template<>
template<typename I, typename D>
inline D Atomic::PlatformAdd<8>::fetch_and_add(I add_value, D volatile* dest,
                                               atomic_memory_order /* order */) const {
  STATIC_ASSERT(8 == sizeof(I));
  STATIC_ASSERT(8 == sizeof(D));
  D old_value;
  __asm__ __volatile__ (  "lock xaddq %0,(%2)"
                        : "=r" (old_value)
                        : "0" (add_value), "r" (dest)
                        : "cc", "memory");
  return old_value;
}

template<>
template<typename T>
inline T Atomic::PlatformXchg<8>::operator()(T exchange_value,
                                             T volatile* dest,
                                             atomic_memory_order /* order */) const {
  STATIC_ASSERT(8 == sizeof(T));
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
                                                atomic_memory_order /* order */) const {
  STATIC_ASSERT(8 == sizeof(T));
  __asm__ __volatile__ (  "lock cmpxchgq %1,(%3)"
                        : "=a" (exchange_value)
                        : "r" (exchange_value), "a" (compare_value), "r" (dest)
                        : "cc", "memory");
  return exchange_value;
}

#else // !AMD64

extern "C" {
  // defined in bsd_x86.s
  int64_t _Atomic_cmpxchg_long(int64_t, volatile int64_t*, int64_t);
  void _Atomic_move_long(const volatile int64_t* src, volatile int64_t* dst);
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<8>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                atomic_memory_order /* order */) const {
  STATIC_ASSERT(8 == sizeof(T));
  return cmpxchg_using_helper<int64_t>(_Atomic_cmpxchg_long, exchange_value, dest, compare_value);
}

template<>
template<typename T>
inline T Atomic::PlatformLoad<8>::operator()(T const volatile* src) const {
  STATIC_ASSERT(8 == sizeof(T));
  volatile int64_t dest;
  _Atomic_move_long(reinterpret_cast<const volatile int64_t*>(src), reinterpret_cast<volatile int64_t*>(&dest));
  return PrimitiveConversions::cast<T>(dest);
}

template<>
template<typename T>
inline void Atomic::PlatformStore<8>::operator()(T store_value,
                                                 T volatile* dest) const {
  STATIC_ASSERT(8 == sizeof(T));
  _Atomic_move_long(reinterpret_cast<const volatile int64_t*>(&store_value), reinterpret_cast<volatile int64_t*>(dest));
}

#endif // AMD64

#endif // OS_CPU_BSD_X86_VM_ATOMIC_BSD_X86_HPP
