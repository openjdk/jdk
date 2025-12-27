/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2025 SAP SE. All rights reserved.
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

#ifndef CPU_PPC_ATOMICACCESS_PPC_HPP
#define CPU_PPC_ATOMICACCESS_PPC_HPP

#ifndef PPC64
#error "Atomic currently only implemented for PPC64"
#endif

#include "orderAccess_ppc.hpp"
#include "utilities/debug.hpp"

// Implementation of class AtomicAccess

//
// machine barrier instructions:
//
// - sync            two-way memory barrier, aka fence
// - lwsync          orders  Store|Store,
//                            Load|Store,
//                            Load|Load,
//                   but not Store|Load
// - eieio           orders memory accesses for device memory (only)
// - isync           invalidates speculatively executed instructions
//                   From the POWER ISA 2.06 documentation:
//                    "[...] an isync instruction prevents the execution of
//                   instructions following the isync until instructions
//                   preceding the isync have completed, [...]"
//                   From IBM's AIX assembler reference:
//                    "The isync [...] instructions causes the processor to
//                   refetch any instructions that might have been fetched
//                   prior to the isync instruction. The instruction isync
//                   causes the processor to wait for all previous instructions
//                   to complete. Then any instructions already fetched are
//                   discarded and instruction processing continues in the
//                   environment established by the previous instructions."
//
// semantic barrier instructions:
// (as defined in orderAccess.hpp)
//
// - release         orders Store|Store,       (maps to lwsync)
//                           Load|Store
// - acquire         orders  Load|Store,       (maps to lwsync)
//                           Load|Load
// - fence           orders Store|Store,       (maps to sync)
//                           Load|Store,
//                           Load|Load,
//                          Store|Load
//

inline void pre_membar(atomic_memory_order order) {
  switch (order) {
    case memory_order_relaxed:
    case memory_order_acquire: break;
    case memory_order_release:
    case memory_order_acq_rel: __asm__ __volatile__ ("lwsync" : : : "memory"); break;
    default /*conservative*/ : __asm__ __volatile__ ("sync"   : : : "memory"); break;
  }
}

inline void post_membar(atomic_memory_order order) {
  switch (order) {
    case memory_order_relaxed:
    case memory_order_release: break;
    case memory_order_acquire:
    case memory_order_acq_rel: __asm__ __volatile__ ("isync"  : : : "memory"); break;
    default /*conservative*/ : __asm__ __volatile__ ("sync"   : : : "memory"); break;
  }
}



template<size_t byte_size>
struct AtomicAccess::PlatformAdd {
  template<typename D, typename I>
  D add_then_fetch(D volatile* dest, I add_value, atomic_memory_order order) const;

  template<typename D, typename I>
  D fetch_then_add(D volatile* dest, I add_value, atomic_memory_order order) const {
    return add_then_fetch(dest, add_value, order) - add_value;
  }
};

template<>
template<typename D, typename I>
inline D AtomicAccess::PlatformAdd<4>::add_then_fetch(D volatile* dest, I add_value,
                                                      atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(I));
  STATIC_ASSERT(4 == sizeof(D));

  D result;

  pre_membar(order);

  __asm__ __volatile__ (
    "1: lwarx   %[result], 0, %[dest]                 \n"
    "   add     %[result], %[result], %[add_value]    \n"
    "   stwcx.  %[result], 0, %[dest]                 \n"
    "   bne-    1b                                    \n"
    : [result]     "=&r"  (result)
    : [add_value]  "r"    (add_value),
      [dest]       "b"    (dest)
    : "cc", "memory" );

  post_membar(order);

  return result;
}


template<>
template<typename D, typename I>
inline D AtomicAccess::PlatformAdd<8>::add_then_fetch(D volatile* dest, I add_value,
                                                      atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(I));
  STATIC_ASSERT(8 == sizeof(D));

  D result;

  pre_membar(order);

  __asm__ __volatile__ (
    "1: ldarx   %[result], 0, %[dest]                 \n"
    "   add     %[result], %[result], %[add_value]    \n"
    "   stdcx.  %[result], 0, %[dest]                 \n"
    "   bne-    1b                                    \n"
    : [result]     "=&r"  (result)
    : [add_value]  "r"    (add_value),
      [dest]       "b"    (dest)
    : "cc", "memory" );

  post_membar(order);

  return result;
}

template<>
struct AtomicAccess::PlatformXchg<1> : AtomicAccess::XchgUsingCmpxchg<1> {};

template<>
template<typename T>
inline T AtomicAccess::PlatformXchg<4>::operator()(T volatile* dest,
                                                   T exchange_value,
                                                   atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));
  // Note that xchg doesn't necessarily do an acquire
  // (see synchronizer.cpp).

  T old_value;

  pre_membar(order);

  __asm__ __volatile__ (
    /* atomic loop */
    "1:                                                 \n"
    "   lwarx   %[old_value], 0, %[dest]                \n"
    "   stwcx.  %[exchange_value], 0, %[dest]           \n"
    "   bne-    1b                                      \n"
    /* exit */
    "2:                                                 \n"
    /* out */
    : [old_value]       "=&r"   (old_value)
    /* in */
    : [dest]            "b"     (dest),
      [exchange_value]  "r"     (exchange_value)
    /* clobber */
    : "cc",
      "memory"
    );

  post_membar(order);

  return old_value;
}

template<>
template<typename T>
inline T AtomicAccess::PlatformXchg<8>::operator()(T volatile* dest,
                                                   T exchange_value,
                                                   atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));
  // Note that xchg doesn't necessarily do an acquire
  // (see synchronizer.cpp).

  T old_value;

  pre_membar(order);

  __asm__ __volatile__ (
    /* atomic loop */
    "1:                                                 \n"
    "   ldarx   %[old_value], 0, %[dest]                \n"
    "   stdcx.  %[exchange_value], 0, %[dest]           \n"
    "   bne-    1b                                      \n"
    /* exit */
    "2:                                                 \n"
    /* out */
    : [old_value]       "=&r"   (old_value)
    /* in */
    : [dest]            "b"     (dest),
      [exchange_value]  "r"     (exchange_value)
    /* clobber */
    : "cc",
      "memory"
    );

  post_membar(order);

  return old_value;
}

template<>
template<typename T>
inline T AtomicAccess::PlatformCmpxchg<1>::operator()(T volatile* dest,
                                                      T compare_value,
                                                      T exchange_value,
                                                      atomic_memory_order order) const {
  STATIC_ASSERT(1 == sizeof(T));

  // Note that cmpxchg guarantees a two-way memory barrier across
  // the cmpxchg, so it's really a 'fence_cmpxchg_fence' if not
  // specified otherwise (see atomicAccess.hpp).

  const unsigned int masked_compare_val = (unsigned int)(unsigned char)compare_value;

  unsigned int old_value;

  pre_membar(order);

  __asm__ __volatile__ (
    /* simple guard */
    "   lbz     %[old_value], 0(%[dest])                  \n"
    "   cmpw    %[masked_compare_val], %[old_value]       \n"
    "   bne-    2f                                        \n"
    /* atomic loop */
    "1:                                                   \n"
    "   lbarx   %[old_value], 0, %[dest]                  \n"
    "   cmpw    %[masked_compare_val], %[old_value]       \n"
    "   bne-    2f                                        \n"
    "   stbcx.  %[exchange_value], 0, %[dest]             \n"
    "   bne-    1b                                        \n"
    /* exit */
    "2:                                                   \n"
    /* out */
    : [old_value]       "=&r"   (old_value)
    /* in */
    : [dest]                   "b"     (dest),
      [masked_compare_val]     "r"     (masked_compare_val),
      [exchange_value]         "r"     (exchange_value)
    /* clobber */
    : "cc",
      "memory"
    );

  post_membar(order);

  return PrimitiveConversions::cast<T>((unsigned char)old_value);
}

template<>
template<typename T>
inline T AtomicAccess::PlatformCmpxchg<4>::operator()(T volatile* dest,
                                                      T compare_value,
                                                      T exchange_value,
                                                      atomic_memory_order order) const {
  STATIC_ASSERT(4 == sizeof(T));

  // Note that cmpxchg guarantees a two-way memory barrier across
  // the cmpxchg, so it's really a 'fence_cmpxchg_fence' if not
  // specified otherwise (see atomicAccess.hpp).

  T old_value;

  pre_membar(order);

  __asm__ __volatile__ (
    /* simple guard */
    "   lwz     %[old_value], 0(%[dest])                \n"
    "   cmpw    %[compare_value], %[old_value]          \n"
    "   bne-    2f                                      \n"
    /* atomic loop */
    "1:                                                 \n"
    "   lwarx   %[old_value], 0, %[dest]                \n"
    "   cmpw    %[compare_value], %[old_value]          \n"
    "   bne-    2f                                      \n"
    "   stwcx.  %[exchange_value], 0, %[dest]           \n"
    "   bne-    1b                                      \n"
    /* exit */
    "2:                                                 \n"
    /* out */
    : [old_value]       "=&r"   (old_value)
    /* in */
    : [dest]            "b"     (dest),
      [compare_value]   "r"     (compare_value),
      [exchange_value]  "r"     (exchange_value)
    /* clobber */
    : "cc",
      "memory"
    );

  post_membar(order);

  return old_value;
}

template<>
template<typename T>
inline T AtomicAccess::PlatformCmpxchg<8>::operator()(T volatile* dest,
                                                      T compare_value,
                                                      T exchange_value,
                                                      atomic_memory_order order) const {
  STATIC_ASSERT(8 == sizeof(T));

  // Note that cmpxchg guarantees a two-way memory barrier across
  // the cmpxchg, so it's really a 'fence_cmpxchg_fence' if not
  // specified otherwise (see atomicAccess.hpp).

  T old_value;

  pre_membar(order);

  __asm__ __volatile__ (
    /* simple guard */
    "   ld      %[old_value], 0(%[dest])                \n"
    "   cmpd    %[compare_value], %[old_value]          \n"
    "   bne-    2f                                      \n"
    /* atomic loop */
    "1:                                                 \n"
    "   ldarx   %[old_value], 0, %[dest]                \n"
    "   cmpd    %[compare_value], %[old_value]          \n"
    "   bne-    2f                                      \n"
    "   stdcx.  %[exchange_value], 0, %[dest]           \n"
    "   bne-    1b                                      \n"
    /* exit */
    "2:                                                 \n"
    /* out */
    : [old_value]       "=&r"   (old_value)
    /* in */
    : [dest]            "b"     (dest),
      [compare_value]   "r"     (compare_value),
      [exchange_value]  "r"     (exchange_value)
    /* clobber */
    : "cc",
      "memory"
    );

  post_membar(order);

  return old_value;
}

template<size_t byte_size>
struct AtomicAccess::PlatformOrderedLoad<byte_size, X_ACQUIRE>
{
  template <typename T>
  T operator()(const volatile T* p) const {
    T t = AtomicAccess::load(p);
    // Use twi-isync for load_acquire (faster than lwsync).
    __asm__ __volatile__ ("twi 0,%0,0\n isync\n" : : "r" (t) : "memory");
    return t;
  }
};

template<>
class AtomicAccess::PlatformBitops<4, true> {
public:
  template<typename T>
  T fetch_then_and(T volatile* dest, T bits, atomic_memory_order order) const {
    STATIC_ASSERT(4 == sizeof(T));
    T old_value, result;

    pre_membar(order);

    __asm__ __volatile__ (
      "1: lwarx   %[old_value], 0, %[dest]            \n"
      "   and     %[result], %[old_value], %[bits]    \n"
      "   stwcx.  %[result], 0, %[dest]               \n"
      "   bne-    1b                                  \n"
      : [old_value]  "=&r"  (old_value),
        [result]     "=&r"  (result)
      : [dest]       "b"    (dest),
        [bits]       "r"    (bits)
      : "cc", "memory" );

    post_membar(order);
    return old_value;
  }

  template<typename T>
  T fetch_then_or(T volatile* dest, T bits, atomic_memory_order order) const {
    STATIC_ASSERT(4 == sizeof(T));
    T old_value, result;

    pre_membar(order);

    __asm__ __volatile__ (
      "1: lwarx   %[old_value], 0, %[dest]            \n"
      "   or      %[result], %[old_value], %[bits]    \n"
      "   stwcx.  %[result], 0, %[dest]               \n"
      "   bne-    1b                                  \n"
      : [old_value]  "=&r"  (old_value),
        [result]     "=&r"  (result)
      : [dest]       "b"    (dest),
        [bits]       "r"    (bits)
      : "cc", "memory" );

    post_membar(order);
    return old_value;
  }

  template<typename T>
  T fetch_then_xor(T volatile* dest, T bits, atomic_memory_order order) const {
    STATIC_ASSERT(4 == sizeof(T));
    T old_value, result;

    pre_membar(order);

    __asm__ __volatile__ (
      "1: lwarx   %[old_value], 0, %[dest]            \n"
      "   xor     %[result], %[old_value], %[bits]    \n"
      "   stwcx.  %[result], 0, %[dest]               \n"
      "   bne-    1b                                  \n"
      : [old_value]  "=&r"  (old_value),
        [result]     "=&r"  (result)
      : [dest]       "b"    (dest),
        [bits]       "r"    (bits)
      : "cc", "memory" );

    post_membar(order);
    return old_value;
  }

  template<typename T>
  T and_then_fetch(T volatile* dest, T bits, atomic_memory_order order) const {
    STATIC_ASSERT(4 == sizeof(T));
    T result;

    pre_membar(order);

    __asm__ __volatile__ (
      "1: lwarx   %[result], 0, %[dest]            \n"
      "   and     %[result], %[result], %[bits]    \n"
      "   stwcx.  %[result], 0, %[dest]            \n"
      "   bne-    1b                               \n"
      : [result]  "=&r"  (result)
      : [dest]    "b"    (dest),
        [bits]    "r"    (bits)
      : "cc", "memory" );

    post_membar(order);
    return result;
  }

  template<typename T>
  T or_then_fetch(T volatile* dest, T bits, atomic_memory_order order) const {
    STATIC_ASSERT(4 == sizeof(T));
    T result;

    pre_membar(order);

    __asm__ __volatile__ (
      "1: lwarx   %[result], 0, %[dest]            \n"
      "   or      %[result], %[result], %[bits]    \n"
      "   stwcx.  %[result], 0, %[dest]            \n"
      "   bne-    1b                               \n"
      : [result]  "=&r"  (result)
      : [dest]    "b"    (dest),
        [bits]    "r"    (bits)
      : "cc", "memory" );

    post_membar(order);
    return result;
  }

  template<typename T>
  T xor_then_fetch(T volatile* dest, T bits, atomic_memory_order order) const {
    STATIC_ASSERT(4 == sizeof(T));
    T result;

    pre_membar(order);

    __asm__ __volatile__ (
      "1: lwarx   %[result], 0, %[dest]            \n"
      "   xor     %[result], %[result], %[bits]    \n"
      "   stwcx.  %[result], 0, %[dest]            \n"
      "   bne-    1b                               \n"
      : [result]  "=&r"  (result)
      : [dest]    "b"    (dest),
        [bits]    "r"    (bits)
      : "cc", "memory" );

    post_membar(order);
    return result;
  }
};

template<>
class AtomicAccess::PlatformBitops<8, true> {
public:
  template<typename T>
  T fetch_then_and(T volatile* dest, T bits, atomic_memory_order order) const {
    STATIC_ASSERT(8 == sizeof(T));
    T old_value, result;

    pre_membar(order);

    __asm__ __volatile__ (
      "1: ldarx   %[old_value], 0, %[dest]            \n"
      "   and     %[result], %[old_value], %[bits]    \n"
      "   stdcx.  %[result], 0, %[dest]               \n"
      "   bne-    1b                                  \n"
      : [old_value]  "=&r"  (old_value),
        [result]     "=&r"  (result)
      : [dest]       "b"    (dest),
        [bits]       "r"    (bits)
      : "cc", "memory" );

    post_membar(order);
    return old_value;
  }

  template<typename T>
  T fetch_then_or(T volatile* dest, T bits, atomic_memory_order order) const {
    STATIC_ASSERT(8 == sizeof(T));
    T old_value, result;

    pre_membar(order);

    __asm__ __volatile__ (
      "1: ldarx   %[old_value], 0, %[dest]            \n"
      "   or      %[result], %[old_value], %[bits]    \n"
      "   stdcx.  %[result], 0, %[dest]               \n"
      "   bne-    1b                                  \n"
      : [old_value]  "=&r"  (old_value),
        [result]     "=&r"  (result)
      : [dest]       "b"    (dest),
        [bits]       "r"    (bits)
      : "cc", "memory" );

    post_membar(order);
    return old_value;
  }

  template<typename T>
  T fetch_then_xor(T volatile* dest, T bits, atomic_memory_order order) const {
    STATIC_ASSERT(8 == sizeof(T));
    T old_value, result;

    pre_membar(order);

    __asm__ __volatile__ (
      "1: ldarx   %[old_value], 0, %[dest]            \n"
      "   xor     %[result], %[old_value], %[bits]    \n"
      "   stdcx.  %[result], 0, %[dest]               \n"
      "   bne-    1b                                  \n"
      : [old_value]  "=&r"  (old_value),
        [result]     "=&r"  (result)
      : [dest]       "b"    (dest),
        [bits]       "r"    (bits)
      : "cc", "memory" );

    post_membar(order);
    return old_value;
  }

  template<typename T>
  T and_then_fetch(T volatile* dest, T bits, atomic_memory_order order) const {
    STATIC_ASSERT(8 == sizeof(T));
    T result;

    pre_membar(order);

    __asm__ __volatile__ (
      "1: ldarx   %[result], 0, %[dest]            \n"
      "   and     %[result], %[result], %[bits]    \n"
      "   stdcx.  %[result], 0, %[dest]            \n"
      "   bne-    1b                               \n"
      : [result]     "=&r"  (result)
      : [dest]       "b"    (dest),
        [bits]       "r"    (bits)
      : "cc", "memory" );

    post_membar(order);
    return result;
  }

  template<typename T>
  T or_then_fetch(T volatile* dest, T bits, atomic_memory_order order) const {
    STATIC_ASSERT(8 == sizeof(T));
    T result;

    pre_membar(order);

    __asm__ __volatile__ (
      "1: ldarx   %[result], 0, %[dest]            \n"
      "   or      %[result], %[result], %[bits]    \n"
      "   stdcx.  %[result], 0, %[dest]            \n"
      "   bne-    1b                               \n"
      : [result]     "=&r"  (result)
      : [dest]       "b"    (dest),
        [bits]       "r"    (bits)
      : "cc", "memory" );

    post_membar(order);
    return result;
  }

  template<typename T>
  T xor_then_fetch(T volatile* dest, T bits, atomic_memory_order order) const {
    STATIC_ASSERT(8 == sizeof(T));
    T result;

    pre_membar(order);

    __asm__ __volatile__ (
      "1: ldarx   %[result], 0, %[dest]            \n"
      "   xor     %[result], %[result], %[bits]    \n"
      "   stdcx.  %[result], 0, %[dest]            \n"
      "   bne-    1b                               \n"
      : [result]     "=&r"  (result)
      : [dest]       "b"    (dest),
        [bits]       "r"    (bits)
      : "cc", "memory" );

    post_membar(order);
    return result;
  }
};
#endif // CPU_PPC_ATOMICACCESS_PPC_HPP
