// Copyright (c) 2021, Red Hat Inc. All rights reserved.
// Copyright (c) 2026, IBM Inc. All rights reserved.
// Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
// DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
//
// This code is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License version 2 only, as
// published by the Free Software Foundation.
//
// This code is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// version 2 for more details (a copy is included in the LICENSE file that
// accompanied this code).
//
// You should have received a copy of the GNU General Public License version
// 2 along with this work; if not, write to the Free Software Foundation,
// Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
//
// Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
// or visit www.oracle.com if you need additional information or have any
// questions.

#include "defs.S.inc"

        .text

#ifdef __ARM_FEATURE_ATOMICS
#define ATOMIC_OP(LOAD, OP, STORE, SZ, MEMBAR, LSEOP)    \
        hint    #0x22 /* bti c */               ;\
        LSEOP SZ ## 1, SZ ## 2, [x0]            ;\
        MEMBAR                                  ;\
        mov     SZ ## 0, SZ ## 2                ;\
        ret
#else
#define ATOMIC_OP(LOAD, OP, STORE, SZ, MEMBAR, LSEOP)    \
        hint    #0x22 /* bti c */               ;\
        prfm    pstl1strm, [x0]                 ;\
0:      LOAD    SZ ## 2, [x0]                   ;\
        OP      SZ ## 8, SZ ## 2, SZ ## 1       ;\
        STORE   w9, SZ ## 8, [x0]               ;\
        cbnz    w9, 0b                          ;\
        MEMBAR                                  ;\
        mov     SZ ## 0, SZ ## 2                ;\
        ret
#endif

#define DMB                              ;\
        dmb ish                          ;

        .align 5
DECLARE_FUNC(aarch64_atomic_fetch_add_8_default_impl):
        ATOMIC_OP(ldaxr, add, stlxr, x, DMB, ldaddal)
        .align 5
DECLARE_FUNC(aarch64_atomic_fetch_add_4_default_impl):
        ATOMIC_OP(ldaxr, add, stlxr, w, DMB, ldaddal)

        .align 5
DECLARE_FUNC(aarch64_atomic_fetch_add_8_relaxed_default_impl):
        ATOMIC_OP(ldxr, add, stxr, x, , ldadd)
        .align 5
DECLARE_FUNC(aarch64_atomic_fetch_add_4_relaxed_default_impl):
        ATOMIC_OP(ldxr, add, stxr, w, , ldadd)

#undef ATOMIC_OP

#ifdef __ARM_FEATURE_ATOMICS
#define ATOMIC_OP(LOAD, STORE, SZ, LSEOP)        \
        hint    #0x22 /* bti c */               ;\
        LSEOP SZ ## 1, SZ ## 2, [x0]            ;\
        dmb     ish                             ;\
        mov     SZ ## 0, SZ ## 2                ;\
        ret
#else
#define ATOMIC_OP(LOAD, STORE, SZ, LSEOP)        \
        hint    #0x22 /* bti c */               ;\
        prfm    pstl1strm, [x0]                 ;\
0:      LOAD    SZ ## 2, [x0]                   ;\
        STORE   w8, SZ ## 1, [x0]               ;\
        cbnz    w8, 0b                          ;\
        dmb     ish                             ;\
        mov     SZ ## 0, SZ ## 2                ;\
        ret
#endif

DECLARE_FUNC(aarch64_atomic_xchg_8_default_impl):
        ATOMIC_OP(ldaxr, stlxr, x, swpal)
DECLARE_FUNC(aarch64_atomic_xchg_4_default_impl):
        ATOMIC_OP(ldaxr, stlxr, w, swpal)

#undef ATOMIC_OP
#ifdef __ARM_FEATURE_ATOMICS
#define ATOMIC_OP(LOAD, STORE, SZ, CMP, MEMBAR, LSEOP) \
        hint    #0x22 /* bti c */               ;\
        mov     SZ ## 3, SZ ## 1                ;\
        LSEOP   SZ ## 3, SZ ## 2, [x0]          ;\
        MEMBAR                                  ;\
        mov     SZ ## 0, SZ ## 3                ;\
        ret
#else
#define ATOMIC_OP(LOAD, STORE, SZ, CMP, MEMBAR, LSEOP) \
        hint    #0x22 /* bti c */               ;\
        MEMBAR                                  ;\
        prfm    pstl1strm, [x0]                 ;\
0:      LOAD    SZ ## 3, [x0]                   ;\
        CMP                                     ;\
        STORE   w8, SZ ## 2, [x0]               ;\
        cbnz    w8, 0b                          ;\
1:      MEMBAR                                  ;\
        mov     SZ ## 0, SZ ## 3                ;\
        ret
#endif

#define CMPB                              \
        eor     w8, w3, w1               ;\
        tst     x8, #0xff                ;\
        b.ne    1f                       ;
#define CMPW                             ;\
        cmp     w3, w1                   ;\
        b.ne    1f                       ;
#define CMPX                             ;\
        cmp     x3, x1                   ;\
        b.ne    1f

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_1_default_impl):
        ATOMIC_OP(ldxrb, stxrb, w, CMPB, DMB, casalb)
        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_4_default_impl):
        ATOMIC_OP(ldxr, stxr, w, CMPW, DMB, casal)
        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_8_default_impl):
        ATOMIC_OP(ldxr, stxr, x, CMPX, DMB, casal)

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_4_release_default_impl):
        ATOMIC_OP(ldxr, stlxr, w, CMPW, , casl)
        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_8_release_default_impl):
        ATOMIC_OP(ldxr, stlxr, x, CMPX, , casl)

       .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_4_seq_cst_default_impl):
        ATOMIC_OP(ldaxr, stlxr, w, CMPW, , casl)
        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_8_seq_cst_default_impl):
        ATOMIC_OP(ldaxr, stlxr, x, CMPX, , casl)

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_1_relaxed_default_impl):
        ATOMIC_OP(ldxrb, stxrb, w, CMPB, , casb)
        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_4_relaxed_default_impl):
        ATOMIC_OP(ldxr, stxr, w, CMPW, , casl)
        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_8_relaxed_default_impl):
        ATOMIC_OP(ldxr, stxr, x, CMPX, , casl)

/* Emit .note.gnu.property section in case of PAC or BTI being enabled.
 * For more details see "ELF for the Arm(R) 64-bit Architecture (AArch64)".
 * https://github.com/ARM-software/abi-aa/blob/main/aaelf64/aaelf64.rst
 */
#ifdef __ARM_FEATURE_BTI_DEFAULT
    #ifdef __ARM_FEATURE_PAC_DEFAULT
        #define GNU_PROPERTY_AARCH64_FEATURE 3
    #else
        #define GNU_PROPERTY_AARCH64_FEATURE 1
    #endif
#else
    #ifdef __ARM_FEATURE_PAC_DEFAULT
        #define GNU_PROPERTY_AARCH64_FEATURE 2
    #else
        #define GNU_PROPERTY_AARCH64_FEATURE 0
    #endif
#endif

#if (GNU_PROPERTY_AARCH64_FEATURE != 0)
        .pushsection .note.gnu.property, "a"
        .align  3
        .long   4          /* name length */
        .long   0x10       /* data length */
        .long   5          /* note type: NT_GNU_PROPERTY_TYPE_0 */
        .string "GNU"      /* vendor name */
        .long   0xc0000000 /* GNU_PROPERTY_AARCH64_FEATURE_1_AND */
        .long   4          /* pr_datasze */
        .long   GNU_PROPERTY_AARCH64_FEATURE
        .long   0
        .popsection
#endif
