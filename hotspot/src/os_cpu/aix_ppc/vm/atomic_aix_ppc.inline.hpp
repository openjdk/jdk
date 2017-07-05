/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2014, SAP AG. All rights reserved.
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

#ifndef OS_CPU_AIX_OJDKPPC_VM_ATOMIC_AIX_PPC_INLINE_HPP
#define OS_CPU_AIX_OJDKPPC_VM_ATOMIC_AIX_PPC_INLINE_HPP

#include "runtime/atomic.hpp"
#include "runtime/os.hpp"

#ifndef _LP64
#error "Atomic currently only impleneted for PPC64"
#endif

// Implementation of class atomic

inline void Atomic::store    (jbyte    store_value, jbyte*    dest) { *dest = store_value; }
inline void Atomic::store    (jshort   store_value, jshort*   dest) { *dest = store_value; }
inline void Atomic::store    (jint     store_value, jint*     dest) { *dest = store_value; }
inline void Atomic::store    (jlong    store_value, jlong*    dest) { *dest = store_value; }
inline void Atomic::store_ptr(intptr_t store_value, intptr_t* dest) { *dest = store_value; }
inline void Atomic::store_ptr(void*    store_value, void*     dest) { *(void**)dest = store_value; }

inline void Atomic::store    (jbyte    store_value, volatile jbyte*    dest) { *dest = store_value; }
inline void Atomic::store    (jshort   store_value, volatile jshort*   dest) { *dest = store_value; }
inline void Atomic::store    (jint     store_value, volatile jint*     dest) { *dest = store_value; }
inline void Atomic::store    (jlong    store_value, volatile jlong*    dest) { *dest = store_value; }
inline void Atomic::store_ptr(intptr_t store_value, volatile intptr_t* dest) { *dest = store_value; }
inline void Atomic::store_ptr(void*    store_value, volatile void*     dest) { *(void* volatile *)dest = store_value; }

inline jlong Atomic::load(volatile jlong* src) { return *src; }

//
//   machine barrier instructions:
//
//   - ppc_sync            two-way memory barrier, aka fence
//   - ppc_lwsync          orders  Store|Store,
//                                  Load|Store,
//                                  Load|Load,
//                         but not Store|Load
//   - ppc_eieio           orders memory accesses for device memory (only)
//   - ppc_isync           invalidates speculatively executed instructions
//                         From the POWER ISA 2.06 documentation:
//                          "[...] an isync instruction prevents the execution of
//                         instructions following the isync until instructions
//                         preceding the isync have completed, [...]"
//                         From IBM's AIX assembler reference:
//                          "The isync [...] instructions causes the processor to
//                         refetch any instructions that might have been fetched
//                         prior to the isync instruction. The instruction isync
//                         causes the processor to wait for all previous instructions
//                         to complete. Then any instructions already fetched are
//                         discarded and instruction processing continues in the
//                         environment established by the previous instructions."
//
//   semantic barrier instructions:
//   (as defined in orderAccess.hpp)
//
//   - ppc_release         orders Store|Store,       (maps to ppc_lwsync)
//                                 Load|Store
//   - ppc_acquire         orders  Load|Store,       (maps to ppc_lwsync)
//                                 Load|Load
//   - ppc_fence           orders Store|Store,       (maps to ppc_sync)
//                                 Load|Store,
//                                 Load|Load,
//                                Store|Load
//

#define strasm_sync                       "\n  sync    \n"
#define strasm_lwsync                     "\n  lwsync  \n"
#define strasm_isync                      "\n  isync   \n"
#define strasm_release                    strasm_lwsync
#define strasm_acquire                    strasm_lwsync
#define strasm_fence                      strasm_sync
#define strasm_nobarrier                  ""
#define strasm_nobarrier_clobber_memory   ""

inline jint     Atomic::add    (jint     add_value, volatile jint*     dest) {

  unsigned int result;

  __asm__ __volatile__ (
    strasm_lwsync
    "1: lwarx   %0,  0, %2    \n"
    "   add     %0, %0, %1    \n"
    "   stwcx.  %0,  0, %2    \n"
    "   bne-    1b            \n"
    strasm_isync
    : /*%0*/"=&r" (result)
    : /*%1*/"r" (add_value), /*%2*/"r" (dest)
    : "cc", "memory" );

  return (jint) result;
}


inline intptr_t Atomic::add_ptr(intptr_t add_value, volatile intptr_t* dest) {

  long result;

  __asm__ __volatile__ (
    strasm_lwsync
    "1: ldarx   %0,  0, %2    \n"
    "   add     %0, %0, %1    \n"
    "   stdcx.  %0,  0, %2    \n"
    "   bne-    1b            \n"
    strasm_isync
    : /*%0*/"=&r" (result)
    : /*%1*/"r" (add_value), /*%2*/"r" (dest)
    : "cc", "memory" );

  return (intptr_t) result;
}

inline void*    Atomic::add_ptr(intptr_t add_value, volatile void*     dest) {
  return (void*)add_ptr(add_value, (volatile intptr_t*)dest);
}


inline void Atomic::inc    (volatile jint*     dest) {

  unsigned int temp;

  __asm__ __volatile__ (
    strasm_nobarrier
    "1: lwarx   %0,  0, %2    \n"
    "   addic   %0, %0,  1    \n"
    "   stwcx.  %0,  0, %2    \n"
    "   bne-    1b            \n"
    strasm_nobarrier
    : /*%0*/"=&r" (temp), "=m" (*dest)
    : /*%2*/"r" (dest), "m" (*dest)
    : "cc" strasm_nobarrier_clobber_memory);

}

inline void Atomic::inc_ptr(volatile intptr_t* dest) {

  long temp;

  __asm__ __volatile__ (
    strasm_nobarrier
    "1: ldarx   %0,  0, %2    \n"
    "   addic   %0, %0,  1    \n"
    "   stdcx.  %0,  0, %2    \n"
    "   bne-    1b            \n"
    strasm_nobarrier
    : /*%0*/"=&r" (temp), "=m" (*dest)
    : /*%2*/"r" (dest), "m" (*dest)
    : "cc" strasm_nobarrier_clobber_memory);

}

inline void Atomic::inc_ptr(volatile void*     dest) {
  inc_ptr((volatile intptr_t*)dest);
}


inline void Atomic::dec    (volatile jint*     dest) {

  unsigned int temp;

  __asm__ __volatile__ (
    strasm_nobarrier
    "1: lwarx   %0,  0, %2    \n"
    "   addic   %0, %0, -1    \n"
    "   stwcx.  %0,  0, %2    \n"
    "   bne-    1b            \n"
    strasm_nobarrier
    : /*%0*/"=&r" (temp), "=m" (*dest)
    : /*%2*/"r" (dest), "m" (*dest)
    : "cc" strasm_nobarrier_clobber_memory);

}

inline void Atomic::dec_ptr(volatile intptr_t* dest) {

  long temp;

  __asm__ __volatile__ (
    strasm_nobarrier
    "1: ldarx   %0,  0, %2    \n"
    "   addic   %0, %0, -1    \n"
    "   stdcx.  %0,  0, %2    \n"
    "   bne-    1b            \n"
    strasm_nobarrier
    : /*%0*/"=&r" (temp), "=m" (*dest)
    : /*%2*/"r" (dest), "m" (*dest)
    : "cc" strasm_nobarrier_clobber_memory);

}

inline void Atomic::dec_ptr(volatile void*     dest) {
  dec_ptr((volatile intptr_t*)dest);
}

inline jint Atomic::xchg(jint exchange_value, volatile jint* dest) {

  // Note that xchg_ptr doesn't necessarily do an acquire
  // (see synchronizer.cpp).

  unsigned int old_value;
  const uint64_t zero = 0;

  __asm__ __volatile__ (
    /* lwsync */
    strasm_lwsync
    /* atomic loop */
    "1:                                                 \n"
    "   lwarx   %[old_value], %[dest], %[zero]          \n"
    "   stwcx.  %[exchange_value], %[dest], %[zero]     \n"
    "   bne-    1b                                      \n"
    /* isync */
    strasm_sync
    /* exit */
    "2:                                                 \n"
    /* out */
    : [old_value]       "=&r"   (old_value),
                        "=m"    (*dest)
    /* in */
    : [dest]            "b"     (dest),
      [zero]            "r"     (zero),
      [exchange_value]  "r"     (exchange_value),
                        "m"     (*dest)
    /* clobber */
    : "cc",
      "memory"
    );

  return (jint) old_value;
}

inline intptr_t Atomic::xchg_ptr(intptr_t exchange_value, volatile intptr_t* dest) {

  // Note that xchg_ptr doesn't necessarily do an acquire
  // (see synchronizer.cpp).

  long old_value;
  const uint64_t zero = 0;

  __asm__ __volatile__ (
    /* lwsync */
    strasm_lwsync
    /* atomic loop */
    "1:                                                 \n"
    "   ldarx   %[old_value], %[dest], %[zero]          \n"
    "   stdcx.  %[exchange_value], %[dest], %[zero]     \n"
    "   bne-    1b                                      \n"
    /* isync */
    strasm_sync
    /* exit */
    "2:                                                 \n"
    /* out */
    : [old_value]       "=&r"   (old_value),
                        "=m"    (*dest)
    /* in */
    : [dest]            "b"     (dest),
      [zero]            "r"     (zero),
      [exchange_value]  "r"     (exchange_value),
                        "m"     (*dest)
    /* clobber */
    : "cc",
      "memory"
    );

  return (intptr_t) old_value;
}

inline void* Atomic::xchg_ptr(void* exchange_value, volatile void* dest) {
  return (void*)xchg_ptr((intptr_t)exchange_value, (volatile intptr_t*)dest);
}

#define VM_HAS_SPECIALIZED_CMPXCHG_BYTE
inline jbyte Atomic::cmpxchg(jbyte exchange_value, volatile jbyte* dest, jbyte compare_value) {

  // Note that cmpxchg guarantees a two-way memory barrier across
  // the cmpxchg, so it's really a a 'fence_cmpxchg_acquire'
  // (see atomic.hpp).

  // Using 32 bit internally.
  volatile int *dest_base = (volatile int*)((uintptr_t)dest & ~3);

#ifdef VM_LITTLE_ENDIAN
  const unsigned int shift_amount        = ((uintptr_t)dest & 3) * 8;
#else
  const unsigned int shift_amount        = ((~(uintptr_t)dest) & 3) * 8;
#endif
  const unsigned int masked_compare_val  = ((unsigned int)(unsigned char)compare_value),
                     masked_exchange_val = ((unsigned int)(unsigned char)exchange_value),
                     xor_value           = (masked_compare_val ^ masked_exchange_val) << shift_amount;

  unsigned int old_value, value32;

  __asm__ __volatile__ (
    /* fence */
    strasm_sync
    /* simple guard */
    "   lbz     %[old_value], 0(%[dest])                  \n"
    "   cmpw    %[masked_compare_val], %[old_value]       \n"
    "   bne-    2f                                        \n"
    /* atomic loop */
    "1:                                                   \n"
    "   lwarx   %[value32], 0, %[dest_base]               \n"
    /* extract byte and compare */
    "   srd     %[old_value], %[value32], %[shift_amount] \n"
    "   clrldi  %[old_value], %[old_value], 56            \n"
    "   cmpw    %[masked_compare_val], %[old_value]       \n"
    "   bne-    2f                                        \n"
    /* replace byte and try to store */
    "   xor     %[value32], %[xor_value], %[value32]      \n"
    "   stwcx.  %[value32], 0, %[dest_base]               \n"
    "   bne-    1b                                        \n"
    /* acquire */
    strasm_sync
    /* exit */
    "2:                                                   \n"
    /* out */
    : [old_value]           "=&r"   (old_value),
      [value32]             "=&r"   (value32),
                            "=m"    (*dest),
                            "=m"    (*dest_base)
    /* in */
    : [dest]                "b"     (dest),
      [dest_base]           "b"     (dest_base),
      [shift_amount]        "r"     (shift_amount),
      [masked_compare_val]  "r"     (masked_compare_val),
      [xor_value]           "r"     (xor_value),
                            "m"     (*dest),
                            "m"     (*dest_base)
    /* clobber */
    : "cc",
      "memory"
    );

  return (jbyte)(unsigned char)old_value;
}

inline jint Atomic::cmpxchg(jint exchange_value, volatile jint* dest, jint compare_value) {

  // Note that cmpxchg guarantees a two-way memory barrier across
  // the cmpxchg, so it's really a a 'fence_cmpxchg_acquire'
  // (see atomic.hpp).

  unsigned int old_value;
  const uint64_t zero = 0;

  __asm__ __volatile__ (
    /* fence */
    strasm_sync
    /* simple guard */
    "   lwz     %[old_value], 0(%[dest])                \n"
    "   cmpw    %[compare_value], %[old_value]          \n"
    "   bne-    2f                                      \n"
    /* atomic loop */
    "1:                                                 \n"
    "   lwarx   %[old_value], %[dest], %[zero]          \n"
    "   cmpw    %[compare_value], %[old_value]          \n"
    "   bne-    2f                                      \n"
    "   stwcx.  %[exchange_value], %[dest], %[zero]     \n"
    "   bne-    1b                                      \n"
    /* acquire */
    strasm_sync
    /* exit */
    "2:                                                 \n"
    /* out */
    : [old_value]       "=&r"   (old_value),
                        "=m"    (*dest)
    /* in */
    : [dest]            "b"     (dest),
      [zero]            "r"     (zero),
      [compare_value]   "r"     (compare_value),
      [exchange_value]  "r"     (exchange_value),
                        "m"     (*dest)
    /* clobber */
    : "cc",
      "memory"
    );

  return (jint) old_value;
}

inline jlong Atomic::cmpxchg(jlong exchange_value, volatile jlong* dest, jlong compare_value) {

  // Note that cmpxchg guarantees a two-way memory barrier across
  // the cmpxchg, so it's really a a 'fence_cmpxchg_acquire'
  // (see atomic.hpp).

  long old_value;
  const uint64_t zero = 0;

  __asm__ __volatile__ (
    /* fence */
    strasm_sync
    /* simple guard */
    "   ld      %[old_value], 0(%[dest])                \n"
    "   cmpd    %[compare_value], %[old_value]          \n"
    "   bne-    2f                                      \n"
    /* atomic loop */
    "1:                                                 \n"
    "   ldarx   %[old_value], %[dest], %[zero]          \n"
    "   cmpd    %[compare_value], %[old_value]          \n"
    "   bne-    2f                                      \n"
    "   stdcx.  %[exchange_value], %[dest], %[zero]     \n"
    "   bne-    1b                                      \n"
    /* acquire */
    strasm_sync
    /* exit */
    "2:                                                 \n"
    /* out */
    : [old_value]       "=&r"   (old_value),
                        "=m"    (*dest)
    /* in */
    : [dest]            "b"     (dest),
      [zero]            "r"     (zero),
      [compare_value]   "r"     (compare_value),
      [exchange_value]  "r"     (exchange_value),
                        "m"     (*dest)
    /* clobber */
    : "cc",
      "memory"
    );

  return (jlong) old_value;
}

inline intptr_t Atomic::cmpxchg_ptr(intptr_t exchange_value, volatile intptr_t* dest, intptr_t compare_value) {
  return (intptr_t)cmpxchg((jlong)exchange_value, (volatile jlong*)dest, (jlong)compare_value);
}

inline void* Atomic::cmpxchg_ptr(void* exchange_value, volatile void* dest, void* compare_value) {
  return (void*)cmpxchg((jlong)exchange_value, (volatile jlong*)dest, (jlong)compare_value);
}

#undef strasm_sync
#undef strasm_lwsync
#undef strasm_isync
#undef strasm_release
#undef strasm_acquire
#undef strasm_fence
#undef strasm_nobarrier
#undef strasm_nobarrier_clobber_memory

#endif // OS_CPU_AIX_OJDKPPC_VM_ATOMIC_AIX_PPC_INLINE_HPP
