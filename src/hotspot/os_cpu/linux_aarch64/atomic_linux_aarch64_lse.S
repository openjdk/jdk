// Copyright (c) 2021, 2022, Red Hat Inc. All rights reserved.
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



        .text

        .globl aarch64_atomic_fetch_add_8_default_impl
        .align 5
aarch64_atomic_fetch_add_8_default_impl:
        prfm    pstl1strm, [x0]
0:      ldaddal x1, x2, [x0]
        dmb     ish
        mov     x0, x2
        ret

        .globl aarch64_atomic_fetch_add_4_default_impl
        .align 5
aarch64_atomic_fetch_add_4_default_impl:
        prfm    pstl1strm, [x0]
0:      ldaddal w1, w2, [x0]
        dmb     ish
        mov     w0, w2
        ret

        .global aarch64_atomic_fetch_add_8_relaxed_default_impl
        .align 5
aarch64_atomic_fetch_add_8_relaxed_default_impl:
        prfm    pstl1strm, [x0]
0:      ldadd   x1, x2, [x0]
        mov     x0, x2
        ret

        .global aarch64_atomic_fetch_add_4_relaxed_default_impl
        .align 5
aarch64_atomic_fetch_add_4_relaxed_default_impl:
        .globl aarch64_atomic_xchg_4_default_impl
        .align 5
        prfm    pstl1strm, [x0]
0:      ldadd   w1, w2, [x0]
        mov     w0, w2
        ret

aarch64_atomic_xchg_4_default_impl:
        prfm    pstl1strm, [x0]
0:      swpal   w1, w2, [x0]
        dmb     ish
        mov     w0, w2
        ret

        .globl aarch64_atomic_xchg_8_default_impl
        .align 5
aarch64_atomic_xchg_8_default_impl:
        prfm    pstl1strm, [x0]
0:      swpal   x1, x2, [x0]
        dmb     ish
        mov     x0, x2
        ret

        .globl aarch64_atomic_cmpxchg_1_default_impl
        .align 5
aarch64_atomic_cmpxchg_1_default_impl:
        prfm    pstl1strm, [x0]
0:      mov     x3, x1
        casalb  w3, w2, [x0]
        dmb     ish
        mov     w0, w3
        ret

        .globl aarch64_atomic_cmpxchg_4_default_impl
        .align 5
aarch64_atomic_cmpxchg_4_default_impl:
        prfm    pstl1strm, [x0]
0:      mov     x3, x1
        casal   w3, w2, [x0]
        dmb     ish
        mov     w0, w3
        ret

        .globl aarch64_atomic_cmpxchg_8_default_impl
        .align 5
aarch64_atomic_cmpxchg_8_default_impl:
        prfm    pstl1strm, [x0]
0:      mov     x3, x1
        casal   x3, x2, [x0]
        dmb     ish
        mov     x0, x3
        ret

        .globl aarch64_atomic_cmpxchg_4_release_default_impl
        .align 5
aarch64_atomic_cmpxchg_4_release_default_impl:
        prfm    pstl1strm, [x0]
0:      mov     x3, x1
        casl    w3, w2, [x0]
        mov     w0, w3
        ret

        .globl aarch64_atomic_cmpxchg_8_release_default_impl
        .align 5
aarch64_atomic_cmpxchg_8_release_default_impl:
        prfm    pstl1strm, [x0]
0:      mov     x3, x1
        casl    x3, x2, [x0]
        mov     x0, x3
        ret

        .globl aarch64_atomic_cmpxchg_4_seq_cst_default_impl
        .align 5
aarch64_atomic_cmpxchg_4_seq_cst_default_impl:
        prfm    pstl1strm, [x0]
0:      mov     x3, x1
        casal   w3, w2, [x0]
        mov     w0, w3
        ret

        .globl aarch64_atomic_cmpxchg_8_seq_cst_default_impl
        .align 5
aarch64_atomic_cmpxchg_8_seq_cst_default_impl:
        prfm    pstl1strm, [x0]
0:      mov     x3, x1
        casal   x3, x2, [x0]
        mov     x0, x3
        ret

.globl aarch64_atomic_cmpxchg_1_relaxed_default_impl
        .align 5
aarch64_atomic_cmpxchg_1_relaxed_default_impl:
        prfm    pstl1strm, [x0]
0:      mov     x3, x1
        casb    w3, w2, [x0]
        mov     w0, w3
        ret

        .globl aarch64_atomic_cmpxchg_4_relaxed_default_impl
        .align 5
aarch64_atomic_cmpxchg_4_relaxed_default_impl:
        prfm    pstl1strm, [x0]
0:      mov     x3, x1
        cas     w3, w2, [x0]
        mov     w0, w3
        ret

        .globl aarch64_atomic_cmpxchg_8_relaxed_default_impl
        .align 5
aarch64_atomic_cmpxchg_8_relaxed_default_impl:
        prfm    pstl1strm, [x0]
0:      mov     x3, x1
        cas     x3, x2, [x0]
        mov     x0, x3
        ret
