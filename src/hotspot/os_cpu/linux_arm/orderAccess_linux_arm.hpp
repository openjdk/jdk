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

#ifndef OS_CPU_LINUX_ARM_VM_ORDERACCESS_LINUX_ARM_HPP
#define OS_CPU_LINUX_ARM_VM_ORDERACCESS_LINUX_ARM_HPP

// Included in orderAccess.hpp header file.

#include "runtime/os.hpp"
#include "vm_version_arm.hpp"

// Implementation of class OrderAccess.
// - we define the high level barriers below and use the general
//   implementation in orderAccess.hpp, with customizations
//   on AARCH64 via the specialized_* template functions

// Memory Ordering on ARM is weak.
//
// Implement all 4 memory ordering barriers by DMB, since it is a
// lighter version of DSB.
// dmb_sy implies full system shareability domain. RD/WR access type.
// dmb_st implies full system shareability domain. WR only access type.
//
// NOP on < ARMv6 (MP not supported)
//
// Non mcr instructions can be used if we build for armv7 or higher arch
//    __asm__ __volatile__ ("dmb" : : : "memory");
//    __asm__ __volatile__ ("dsb" : : : "memory");
//
// inline void _OrderAccess_dsb() {
//    volatile intptr_t dummy = 0;
//    if (os::is_MP()) {
//      __asm__ volatile (
//        "mcr p15, 0, %0, c7, c10, 4"
//        : : "r" (dummy) : "memory");
//    }
// }

inline static void dmb_sy() {
   if (!os::is_MP()) {
     return;
   }
#ifdef AARCH64
   __asm__ __volatile__ ("dmb sy" : : : "memory");
#else
   if (VM_Version::arm_arch() >= 7) {
#ifdef __thumb__
     __asm__ volatile (
     "dmb sy": : : "memory");
#else
     __asm__ volatile (
     ".word 0xF57FF050 | 0xf" : : : "memory");
#endif
   } else {
     intptr_t zero = 0;
     __asm__ volatile (
       "mcr p15, 0, %0, c7, c10, 5"
       : : "r" (zero) : "memory");
   }
#endif
}

inline static void dmb_st() {
   if (!os::is_MP()) {
     return;
   }
#ifdef AARCH64
   __asm__ __volatile__ ("dmb st" : : : "memory");
#else
   if (VM_Version::arm_arch() >= 7) {
#ifdef __thumb__
     __asm__ volatile (
     "dmb st": : : "memory");
#else
     __asm__ volatile (
     ".word 0xF57FF050 | 0xe" : : : "memory");
#endif
   } else {
     intptr_t zero = 0;
     __asm__ volatile (
       "mcr p15, 0, %0, c7, c10, 5"
       : : "r" (zero) : "memory");
   }
#endif
}

// Load-Load/Store barrier
inline static void dmb_ld() {
#ifdef AARCH64
   if (!os::is_MP()) {
     return;
   }
   __asm__ __volatile__ ("dmb ld" : : : "memory");
#else
   dmb_sy();
#endif
}


inline void OrderAccess::loadload()   { dmb_ld(); }
inline void OrderAccess::loadstore()  { dmb_ld(); }
inline void OrderAccess::acquire()    { dmb_ld(); }
inline void OrderAccess::storestore() { dmb_st(); }
inline void OrderAccess::storeload()  { dmb_sy(); }
inline void OrderAccess::release()    { dmb_sy(); }
inline void OrderAccess::fence()      { dmb_sy(); }

// specializations for Aarch64
// TODO-AARCH64: evaluate effectiveness of ldar*/stlr* implementations compared to 32-bit ARM approach

#ifdef AARCH64

template<>
struct OrderAccess::PlatformOrderedLoad<1, X_ACQUIRE>
{
  template <typename T>
  T operator()(const volatile T* p) const {
    volatile T result;
    __asm__ volatile(
      "ldarb %w[res], [%[ptr]]"
      : [res] "=&r" (result)
      : [ptr] "r" (p)
      : "memory");
    return result;
  }
};

template<>
struct OrderAccess::PlatformOrderedLoad<2, X_ACQUIRE>
{
  template <typename T>
  T operator()(const volatile T* p) const {
    volatile T result;
    __asm__ volatile(
      "ldarh %w[res], [%[ptr]]"
      : [res] "=&r" (result)
      : [ptr] "r" (p)
      : "memory");
    return result;
  }
};

template<>
struct OrderAccess::PlatformOrderedLoad<4, X_ACQUIRE>
{
  template <typename T>
  T operator()(const volatile T* p) const {
    volatile T result;
    __asm__ volatile(
      "ldar %w[res], [%[ptr]]"
      : [res] "=&r" (result)
      : [ptr] "r" (p)
      : "memory");
    return result;
  }
};

template<>
struct OrderAccess::PlatformOrderedLoad<8, X_ACQUIRE>
{
  template <typename T>
  T operator()(const volatile T* p) const {
    volatile T result;
    __asm__ volatile(
      "ldar %[res], [%[ptr]]"
      : [res] "=&r" (result)
      : [ptr] "r" (p)
      : "memory");
    return result;
  }
};

template<>
struct OrderAccess::PlatformOrderedStore<1, RELEASE_X_FENCE>
{
  template <typename T>
  void operator()(T v, volatile T* p) const {
    __asm__ volatile(
      "stlrb %w[val], [%[ptr]]"
      :
      : [ptr] "r" (p), [val] "r" (v)
      : "memory");
  }
};

template<>
struct OrderAccess::PlatformOrderedStore<2, RELEASE_X_FENCE>
{
  template <typename T>
  void operator()(T v, volatile T* p) const {
    __asm__ volatile(
      "stlrh %w[val], [%[ptr]]"
      :
      : [ptr] "r" (p), [val] "r" (v)
      : "memory");
  }
};

template<>
struct OrderAccess::PlatformOrderedStore<4, RELEASE_X_FENCE>
{
  template <typename T>
  void operator()(T v, volatile T* p) const {
    __asm__ volatile(
      "stlr %w[val], [%[ptr]]"
      :
      : [ptr] "r" (p), [val] "r" (v)
      : "memory");
  }
};

template<>
struct OrderAccess::PlatformOrderedStore<8, RELEASE_X_FENCE>
{
  template <typename T>
  void operator()(T v, volatile T* p) const {
    __asm__ volatile(
      "stlr %[val], [%[ptr]]"
      :
      : [ptr] "r" (p), [val] "r" (v)
      : "memory");
  }
};

#endif // AARCH64

#endif // OS_CPU_LINUX_ARM_VM_ORDERACCESS_LINUX_ARM_HPP
