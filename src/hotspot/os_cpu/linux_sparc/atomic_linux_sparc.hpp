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

#ifndef OS_CPU_LINUX_SPARC_VM_ATOMIC_LINUX_SPARC_INLINE_HPP
#define OS_CPU_LINUX_SPARC_VM_ATOMIC_LINUX_SPARC_INLINE_HPP

// Implementation of class atomic

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

  D rv;
  __asm__ volatile(
    "1: \n\t"
    " ld     [%2], %%o2\n\t"
    " add    %1, %%o2, %%o3\n\t"
    " cas    [%2], %%o2, %%o3\n\t"
    " cmp    %%o2, %%o3\n\t"
    " bne    1b\n\t"
    "  nop\n\t"
    " add    %1, %%o2, %0\n\t"
    : "=r" (rv)
    : "r" (add_value), "r" (dest)
    : "memory", "o2", "o3");
  return rv;
}

template<>
template<typename I, typename D>
inline D Atomic::PlatformAdd<8>::add_and_fetch(I add_value, D volatile* dest,
                                               atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(I));
  STATIC_ASSERT(8 == sizeof(D));

  D rv;
  __asm__ volatile(
    "1: \n\t"
    " ldx    [%2], %%o2\n\t"
    " add    %1, %%o2, %%o3\n\t"
    " casx   [%2], %%o2, %%o3\n\t"
    " cmp    %%o2, %%o3\n\t"
    " bne    %%xcc, 1b\n\t"
    "  nop\n\t"
    " add    %1, %%o2, %0\n\t"
    : "=r" (rv)
    : "r" (add_value), "r" (dest)
    : "memory", "o2", "o3");
  return rv;
}

template<>
template<typename T>
inline T Atomic::PlatformXchg<4>::operator()(T exchange_value,
                                             T volatile* dest,
                                             atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
  T rv = exchange_value;
  __asm__ volatile(
    " swap   [%2],%1\n\t"
    : "=r" (rv)
    : "0" (exchange_value) /* we use same register as for return value */, "r" (dest)
    : "memory");
  return rv;
}

template<>
template<typename T>
inline T Atomic::PlatformXchg<8>::operator()(T exchange_value,
                                             T volatile* dest,
                                             atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  T rv = exchange_value;
  __asm__ volatile(
    "1:\n\t"
    " mov    %1, %%o3\n\t"
    " ldx    [%2], %%o2\n\t"
    " casx   [%2], %%o2, %%o3\n\t"
    " cmp    %%o2, %%o3\n\t"
    " bne    %%xcc, 1b\n\t"
    "  nop\n\t"
    " mov    %%o2, %0\n\t"
    : "=r" (rv)
    : "r" (exchange_value), "r" (dest)
    : "memory", "o2", "o3");
  return rv;
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

#endif // OS_CPU_LINUX_SPARC_VM_ATOMIC_LINUX_SPARC_INLINE_HPP
