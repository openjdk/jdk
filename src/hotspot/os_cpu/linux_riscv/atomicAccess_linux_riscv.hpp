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
    static_assert(byte_size >= 4);
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
  static_assert(1 == sizeof(T));
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
  static_assert(4 == sizeof(T));
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
  static_assert(8 == sizeof(T));
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
  static_assert(byte_size >= 4);
#endif

  static_assert(byte_size == sizeof(T));
  static_assert(byte_size == 4 || byte_size == 8);

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

// Zalasr load-acquire and store-release.
//
// With UseZalasr the JIT compiles a Java volatile store to a bare
// s{b|h|w|d}.rl and elides the trailing StoreLoad fence, relying on RVWMO
// preserved program order rule 7 ("a and b both have RCsc annotations") to
// order that store before a later load. A plain load carries no RCsc
// annotation and no preserved-program-order rule applies to it, so the ordered
// accesses below have to be RCsc as well to interoperate. The template
// interpreter solves the same problem with a leading fence instead, see
// needs_volatile_load_leading_fence() in templateTable_riscv.cpp.
//
// This matters for HeapAccess<MO_SEQ_CST>, which implements a Java volatile
// access whenever it is not compiled: interpreted Unsafe.getXVolatile() (and
// hence reflection on a volatile field), and Thread.interrupted.
//
// The accesses are emitted with .insn rather than the l{b|h|w|d}.aq and
// s{b|h|w|d}.rl mnemonics, which need an assembler that knows the Zalasr
// extension. That is not implied by the toolchain check gating UseZalasr: the
// check is about how the compiler lowers __atomic_*, not about what the
// assembler accepts. Since this code is compiled unconditionally, using the
// mnemonics would raise the minimum binutils for every RISC-V build, whether or
// not Zalasr is ever enabled at run time. Encodings mirror
// Assembler::zalasr_base() in assembler_riscv.hpp and must be kept in sync
// with it:
//
//   opcode        = 0b0101111 (OP_AMO_MAJOR)                          = 0x2f
//   funct3        = width, 0b000 / 0b001 / 0b010 / 0b011 for 1/2/4/8 bytes
//   load-acquire  = funct5 0b00110, aq 0b10 -> funct7 0b0011010 = 0x1a, rs2 = x0
//   store-release = funct5 0b00111, rl 0b01 -> funct7 0b0011101 = 0x1d, rd  = x0
//
// Like everything in the AMO opcode space these require the address to be
// naturally aligned, which is already assumed for the LR/SC and AMO sequences
// used elsewhere in this file.

// funct3 encoding, log2 of the access size in bytes; matches
// Assembler::ZalasrWidthFunct3. 1 -> lb.aq/sb.rl, 2 -> lh.aq/sh.rl,
// 4 -> lw.aq/sw.rl, 8 -> ld.aq/sd.rl.
constexpr int zalasr_width(size_t byte_size) {
  switch (byte_size) {
    case 1: return 0;
    case 2: return 1;
    case 4: return 2;
    case 8: return 3;
    default: return -1;
  }
}

template<size_t byte_size>
inline uint64_t zalasr_load_acquire(const void* p) {
  constexpr int width = zalasr_width(byte_size);
  static_assert(width >= 0, "unsupported Zalasr access size");
  uint64_t data;
  __asm__ __volatile__ (".insn r 0x2f, %2, 0x1a, %0, %1, zero"
                        : "=r" (data)
                        : "r" (p), "i" (width)
                        : "memory");
  return data;
}

template<size_t byte_size>
inline void zalasr_store_release(void* p, uint64_t v) {
  constexpr int width = zalasr_width(byte_size);
  static_assert(width >= 0, "unsupported Zalasr access size");
  __asm__ __volatile__ (".insn r 0x2f, %2, 0x1d, zero, %0, %1"
                        : /* no output */
                        : "r" (p), "r" (v), "i" (width)
                        : "memory");
}

template<size_t byte_size>
struct AtomicAccess::PlatformOrderedLoad<byte_size, X_ACQUIRE>
{
  template <typename T>
  T operator()(const volatile T* p) const {
    static_assert(byte_size == sizeof(T));
    static_assert(byte_size == 1 || byte_size == 2 || byte_size == 4 || byte_size == 8);
    if (VM_Version::use_zalasr_atomics()) {
      // Zalasr has no zero-extending form; l{b|h|w}.aq sign-extend. Narrowing
      // the result back to T discards the extra bits, so both signed and
      // unsigned T end up with the value the caller expects.
      return (T)zalasr_load_acquire<byte_size>((const void*)p);
    } else {
      T data;
      __atomic_load(const_cast<T*>(p), &data, __ATOMIC_ACQUIRE);
      return data;
    }
  }
};

template<size_t byte_size>
struct AtomicAccess::PlatformOrderedStore<byte_size, RELEASE_X>
{
  template <typename T>
  void operator()(volatile T* p, T v) const {
    static_assert(byte_size == sizeof(T));
    static_assert(byte_size == 1 || byte_size == 2 || byte_size == 4 || byte_size == 8);
    if (VM_Version::use_zalasr_atomics()) {
      // s{b|h|w|d}.rl stores the low byte_size bytes of the register, so
      // widening v here is value-preserving for both signed and unsigned T.
      zalasr_store_release<byte_size>((void*)p, (uint64_t)v);
    } else {
      __atomic_store(const_cast<T*>(p), &v, __ATOMIC_RELEASE);
    }
  }
};

template<size_t byte_size>
struct AtomicAccess::PlatformOrderedStore<byte_size, RELEASE_X_FENCE>
{
  template <typename T>
  void operator()(volatile T* p, T v) const { release_store(p, v); OrderAccess::fence(); }
};

#undef FULL_COMPILER_ATOMIC_SUPPORT

#endif // OS_CPU_LINUX_RISCV_ATOMICACCESS_LINUX_RISCV_HPP
