/*
 * Copyright (c) 2003, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_BSD_X86_ORDERACCESS_BSD_X86_HPP
#define OS_CPU_BSD_X86_ORDERACCESS_BSD_X86_HPP

// Included in orderAccess.hpp header file.

// Compiler version last used for testing: clang 5.1
// Please update this information when this file changes

// A compiler barrier, forcing the C++ compiler to invalidate all memory assumptions
static inline void compiler_barrier() {
  __asm__ volatile ("" : : : "memory");
}

// x86 is TSO and hence only needs a fence for storeload
// However, a compiler barrier is still needed to prevent reordering
// between volatile and non-volatile memory accesses.

// Implementation of class OrderAccess.

inline void OrderAccess::loadload()   { compiler_barrier(); }
inline void OrderAccess::storestore() { compiler_barrier(); }
inline void OrderAccess::loadstore()  { compiler_barrier(); }
inline void OrderAccess::storeload()  { fence();            }

inline void OrderAccess::acquire()    { compiler_barrier(); }
inline void OrderAccess::release()    { compiler_barrier(); }

inline void OrderAccess::fence() {
  // always use locked addl since mfence is sometimes expensive
#ifdef AMD64
  __asm__ volatile ("lock; addl $0,0(%%rsp)" : : : "cc", "memory");
#else
  __asm__ volatile ("lock; addl $0,0(%%esp)" : : : "cc", "memory");
#endif
  compiler_barrier();
}

inline void OrderAccess::cross_modify_fence() {
  int idx = 0;
  __asm__ volatile ("cpuid " : "+a" (idx) : : "ebx", "ecx", "edx", "memory");
}

template<>
struct OrderAccess::PlatformOrderedStore<1, RELEASE_X_FENCE>
{
  template <typename T>
  void operator()(T v, volatile T* p) const {
    __asm__ volatile (  "xchgb (%2),%0"
                      : "=q" (v)
                      : "0" (v), "r" (p)
                      : "memory");
  }
};

template<>
struct OrderAccess::PlatformOrderedStore<2, RELEASE_X_FENCE>
{
  template <typename T>
  void operator()(T v, volatile T* p) const {
    __asm__ volatile (  "xchgw (%2),%0"
                      : "=r" (v)
                      : "0" (v), "r" (p)
                      : "memory");
  }
};

template<>
struct OrderAccess::PlatformOrderedStore<4, RELEASE_X_FENCE>
{
  template <typename T>
  void operator()(T v, volatile T* p) const {
    __asm__ volatile (  "xchgl (%2),%0"
                      : "=r" (v)
                      : "0" (v), "r" (p)
                      : "memory");
  }
};

#ifdef AMD64
template<>
struct OrderAccess::PlatformOrderedStore<8, RELEASE_X_FENCE>
{
  template <typename T>
  void operator()(T v, volatile T* p) const {
    __asm__ volatile (  "xchgq (%2), %0"
                      : "=r" (v)
                      : "0" (v), "r" (p)
                      : "memory");
  }
};
#endif // AMD64

#endif // OS_CPU_BSD_X86_ORDERACCESS_BSD_X86_HPP
