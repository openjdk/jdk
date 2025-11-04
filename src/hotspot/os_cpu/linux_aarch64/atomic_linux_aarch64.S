// Copyright (c) 2021, Red Hat Inc. All rights reserved.
// Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

        .align 5
DECLARE_FUNC(aarch64_atomic_fetch_add_8_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        ldaddal x1, x2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldaxr   x2, [x0]
        add     x8, x2, x1
        stlxr   w9, x8, [x0]
        cbnz    w9, 0b
#endif
        dmb     ish
        mov     x0, x2
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_fetch_add_4_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        ldaddal w1, w2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldaxr   w2, [x0]
        add     w8, w2, w1
        stlxr   w9, w8, [x0]
        cbnz    w9, 0b
#endif
        dmb     ish
        mov     w0, w2
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_fetch_add_8_relaxed_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        ldadd   x1, x2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldxr    x2, [x0]
        add     x8, x2, x1
        stxr    w9, x8, [x0]
        cbnz    w9, 0b
#endif
        mov     x0, x2
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_fetch_add_4_relaxed_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        ldadd   w1, w2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldxr    w2, [x0]
        add     w8, w2, w1
        stxr    w9, w8, [x0]
        cbnz    w9, 0b
#endif
        mov     w0, w2
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_xchg_4_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        swpal   w1, w2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldaxr   w2, [x0]
        stlxr   w8, w1, [x0]
        cbnz    w8, 0b
#endif
        dmb     ish
        mov     w0, w2
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_xchg_8_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        swpal   x1, x2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldaxr   x2, [x0]
        stlxr   w8, x1, [x0]
        cbnz    w8, 0b
#endif
        dmb     ish
        mov     x0, x2
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_1_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        mov     x3, x1
        casalb  w3, w2, [x0]
#else
        dmb     ish
        prfm    pstl1strm, [x0]
0:      ldxrb   w3, [x0]
        eor     w8, w3, w1
        tst     x8, #0xff
        b.ne    1f
        stxrb   w8, w2, [x0]
        cbnz    w8, 0b
#endif
1:      dmb     ish
        mov     w0, w3
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_4_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        mov     x3, x1
        casal   w3, w2, [x0]
#else
        dmb     ish
        prfm    pstl1strm, [x0]
0:      ldxr    w3, [x0]
        cmp     w3, w1
        b.ne    1f
        stxr    w8, w2, [x0]
        cbnz    w8, 0b
#endif
1:      dmb     ish
        mov     w0, w3
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_8_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        mov     x3, x1
        casal   x3, x2, [x0]
#else
        dmb     ish
        prfm    pstl1strm, [x0]
0:      ldxr    x3, [x0]
        cmp     x3, x1
        b.ne    1f
        stxr    w8, x2, [x0]
        cbnz    w8, 0b
#endif
1:      dmb     ish
        mov     x0, x3
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_4_release_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        mov     x3, x1
        casl    w3, w2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldxr    w3, [x0]
        cmp     w3, w1
        b.ne    1f
        stlxr   w8, w2, [x0]
        cbnz    w8, 0b
#endif
1:      mov     w0, w3
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_8_release_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        mov     x3, x1
        casl    x3, x2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldxr    x3, [x0]
        cmp     x3, x1
        b.ne    1f
        stlxr   w8, x2, [x0]
        cbnz    w8, 0b
#endif
1:      mov     x0, x3
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_4_seq_cst_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        mov     x3, x1
        casal   w3, w2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldaxr   w3, [x0]
        cmp     w3, w1
        b.ne    1f
        stlxr   w8, w2, [x0]
        cbnz    w8, 0b
#endif
1:      mov     w0, w3
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_8_seq_cst_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        mov     x3, x1
        casal   x3, x2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldaxr   x3, [x0]
        cmp     x3, x1
        b.ne    1f
        stlxr   w8, x2, [x0]
        cbnz    w8, 0b
#endif
1:      mov     x0, x3
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_1_relaxed_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        mov     x3, x1
        casb    w3, w2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldxrb   w3, [x0]
        eor     w8, w3, w1
        tst     x8, #0xff
        b.ne    1f
        stxrb   w8, w2, [x0]
        cbnz    w8, 0b
#endif
1:      mov     w0, w3
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_4_relaxed_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        mov     x3, x1
        cas     w3, w2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldxr    w3, [x0]
        cmp     w3, w1
        b.ne    1f
        stxr    w8, w2, [x0]
        cbnz    w8, 0b
#endif
1:      mov     w0, w3
        ret

        .align 5
DECLARE_FUNC(aarch64_atomic_cmpxchg_8_relaxed_default_impl):
        hint    #0x22 // bti c
#ifdef __ARM_FEATURE_ATOMICS
        mov     x3, x1
        cas     x3, x2, [x0]
#else
        prfm    pstl1strm, [x0]
0:      ldxr    x3, [x0]
        cmp     x3, x1
        b.ne    1f
        stxr    w8, x2, [x0]
        cbnz    w8, 0b
#endif
1:      mov     x0, x3
        ret

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
