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

#ifndef OS_CPU_SOLARIS_SPARC_VM_ATOMIC_SOLARIS_SPARC_HPP
#define OS_CPU_SOLARIS_SPARC_VM_ATOMIC_SOLARIS_SPARC_HPP

// Implementation of class atomic

// Implement ADD using a CAS loop.
template<size_t byte_size>
struct Atomic::PlatformAdd {
  template<typename I, typename D>
  inline D operator()(I add_value, D volatile* dest, atomic_memory_order order) const {
    D old_value = *dest;
    while (true) {
      D new_value = old_value + add_value;
      D result = cmpxchg(new_value, dest, old_value);
      if (result == old_value) break;
      old_value = result;
    }
    return old_value + add_value;
  }
};

template<>
template<typename T>
inline T Atomic::PlatformXchg<4>::operator()(T exchange_value,
                                             T volatile* dest,
                                             atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
  __asm__ volatile (  "swap [%2],%0"
                    : "=r" (exchange_value)
                    : "0" (exchange_value), "r" (dest)
                    : "memory");
  return exchange_value;
}

template<>
template<typename T>
inline T Atomic::PlatformXchg<8>::operator()(T exchange_value,
                                             T volatile* dest,
                                             atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  T old_value = *dest;
  while (true) {
    T result = cmpxchg(exchange_value, dest, old_value);
    if (result == old_value) break;
    old_value = result;
  }
  return old_value;
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
  T rv;
  __asm__ volatile(
    " cas    [%2], %3, %0"
    : "=r" (rv)
    : "0" (exchange_value), "r" (dest), "r" (compare_value)
    : "memory");
  return rv;
}

template<>
template<typename T>
inline T Atomic::PlatformCmpxchg<8>::operator()(T exchange_value,
                                                T volatile* dest,
                                                T compare_value,
                                                atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  T rv;
  __asm__ volatile(
    " casx   [%2], %3, %0"
    : "=r" (rv)
    : "0" (exchange_value), "r" (dest), "r" (compare_value)
    : "memory");
  return rv;
}

#endif // OS_CPU_SOLARIS_SPARC_VM_ATOMIC_SOLARIS_SPARC_HPP
