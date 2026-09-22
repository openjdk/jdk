/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef OS_CPU_LINUX_RISCV_ATOMICACCESS_LINUX_RISCV_HPP
#define OS_CPU_LINUX_RISCV_ATOMICACCESS_LINUX_RISCV_HPP

#include "atomic_riscv.hpp"
#include "runtime/vm_version.hpp"

// Implementation of class AtomicAccess

// Note that memory_order_conservative requires a full barrier after atomic stores.
// See https://patchwork.kernel.org/patch/3575821/

#if defined(__clang_major__)
#define FULL_COMPILER_ATOMIC_SUPPORT
#elif (__GNUC__ > 13) || ((__GNUC__ == 13) && (__GNUC_MINOR__ > 2))
#define FULL_COMPILER_ATOMIC_SUPPORT
#endif

// Call one of the generated stubs from C++. This uses the C calling
// convention, but explicitly lists the registers used by the stubs so the
// compiler does not save all caller-saved registers around the call.
//
// This is intentionally not a template. See GCC bug 33661: explicit local
// register variables can be ignored in template functions.
inline uint64_t bare_riscv_atomic_fastcall(address stub, volatile void* ptr,
                                            uint64_t compare_value,
                                            uint64_t exchange_value) {
  register uint64_t reg0 __asm__("a0") = (uint64_t)ptr;
  register uint64_t reg1 __asm__("a1") = compare_value;
  register uint64_t reg2 __asm__("a2") = exchange_value;
  register uint64_t reg3 __asm__("a3") = (uint64_t)stub;
  register uint64_t result __asm__("a0");
  asm volatile("jalr ra, 0(%1)"
               : "=r"(result), "+r"(reg3), "+r"(reg2), "+r"(reg1)
               : "0"(reg0)
               : "ra", "t0", "t1", "t2", "t3", "t4", "a4", "a5", "a6",
                 "memory");
  return result;
}

template <typename F, typename T>
inline T riscv_atomic_fastcall(F stub, volatile T* dest, T compare_value,
                               T exchange_value) {
  return (T)bare_riscv_atomic_fastcall(CAST_FROM_FN_PTR(address, stub), dest,
                                       (uint64_t)compare_value,
                                       (uint64_t)exchange_value);
}

template<size_t byte_size>
struct AtomicAccess::PlatformAdd {
  template<typename D, typename I>
  D fetch_then_add(D volatile* dest, I add_value, atomic_memory_order order) const {

#ifndef FULL_COMPILER_ATOMIC_SUPPORT
    // If we add add and fetch for sub word and are using older compiler
    // it must be added here due to not using lib atomic.
    STATIC_ASSERT(byte_size >= 4);
#endif

    switch (order) {
      case memory_order_relaxed:
        return __atomic_fetch_add(dest, add_value, __ATOMIC_RELAXED);
      default:
        // The release part of the RMW orders preceding accesses, so only the
        // trailing full barrier required by memory_order_conservative remains.
        D result = __atomic_fetch_add(dest, add_value, __ATOMIC_ACQ_REL);
        FULL_MEM_BARRIER;
        return result;
    }
  }

  template<typename D, typename I>
  D add_then_fetch(D volatile* dest, I add_value, atomic_memory_order order) const {
    return fetch_then_add(dest, add_value, order) + add_value;
  }
};

template<>
template<typename T>
inline T AtomicAccess::PlatformCmpxchg<1>::operator()(T volatile* dest,
                                                      T compare_value,
                                                      T exchange_value,
                                                      atomic_memory_order order) const {
  STATIC_ASSERT(1 == sizeof(T));
  riscv_atomic_stub_t stub;
  switch (order) {
    case memory_order_relaxed:
      stub = riscv_atomic_cmpxchg_1_relaxed_impl; break;
    default:
      stub = riscv_atomic_cmpxchg_1_impl; break;
    }
  return riscv_atomic_fastcall(stub, dest, compare_value, exchange_value);
}

template<>
template<typename T>
inline T AtomicAccess::PlatformCmpxchg<4>::operator()(T volatile* dest,
                                                      T compare_value,
                                                      T exchange_value,
                                                      atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
  riscv_atomic_stub_t stub;
  switch (order) {
    case memory_order_relaxed:
      stub = riscv_atomic_cmpxchg_4_relaxed_impl; break;
    case memory_order_release:
      stub = riscv_atomic_cmpxchg_4_release_impl; break;
    case memory_order_acq_rel:
    case memory_order_seq_cst:
      stub = riscv_atomic_cmpxchg_4_seq_cst_impl; break;
    default:
      stub = riscv_atomic_cmpxchg_4_impl; break;
  }
  return riscv_atomic_fastcall(stub, dest, compare_value, exchange_value);
}

template<>
template<typename T>
inline T AtomicAccess::PlatformCmpxchg<8>::operator()(T volatile* dest,
                                                      T compare_value,
                                                      T exchange_value,
                                                      atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  riscv_atomic_stub_t stub;
  switch (order) {
    case memory_order_relaxed:
      stub = riscv_atomic_cmpxchg_8_relaxed_impl; break;
    case memory_order_release:
      stub = riscv_atomic_cmpxchg_8_release_impl; break;
    case memory_order_acq_rel:
    case memory_order_seq_cst:
      stub = riscv_atomic_cmpxchg_8_seq_cst_impl; break;
    default:
      stub = riscv_atomic_cmpxchg_8_impl; break;
  }
  return riscv_atomic_fastcall(stub, dest, compare_value, exchange_value);
}

template<>
struct AtomicAccess::PlatformXchg<1> : AtomicAccess::XchgUsingCmpxchg<1> {};

template<size_t byte_size>
template<typename T>
inline T AtomicAccess::PlatformXchg<byte_size>::operator()(T volatile* dest,
                                                           T exchange_value,
                                                           atomic_memory_order order) const {
#ifndef FULL_COMPILER_ATOMIC_SUPPORT
  // If we add xchg for sub word and are using older compiler
  // it must be added here due to not using lib atomic.
  STATIC_ASSERT(byte_size >= 4);
#endif

  STATIC_ASSERT(byte_size == sizeof(T));
  STATIC_ASSERT(byte_size == 4 || byte_size == 8);

  switch (order) {
    case memory_order_relaxed:
      return __atomic_exchange_n(dest, exchange_value, __ATOMIC_RELAXED);
    default:
      // The release part of the RMW orders preceding accesses, so only the
      // trailing full barrier required by memory_order_conservative remains.
      T result = __atomic_exchange_n(dest, exchange_value, __ATOMIC_ACQ_REL);
      FULL_MEM_BARRIER;
      return result;
  }
}

template<size_t byte_size>
struct AtomicAccess::PlatformOrderedLoad<byte_size, X_ACQUIRE>
{
  template <typename T>
  T operator()(const volatile T* p) const { T data; __atomic_load(const_cast<T*>(p), &data, __ATOMIC_ACQUIRE); return data; }
};

template<size_t byte_size>
struct AtomicAccess::PlatformOrderedStore<byte_size, RELEASE_X>
{
  template <typename T>
  void operator()(volatile T* p, T v) const { __atomic_store(const_cast<T*>(p), &v, __ATOMIC_RELEASE); }
};

template<size_t byte_size>
struct AtomicAccess::PlatformOrderedStore<byte_size, RELEASE_X_FENCE>
{
  template <typename T>
  void operator()(volatile T* p, T v) const { release_store(p, v); OrderAccess::fence(); }
};

#undef FULL_COMPILER_ATOMIC_SUPPORT

#endif // OS_CPU_LINUX_RISCV_ATOMICACCESS_LINUX_RISCV_HPP
