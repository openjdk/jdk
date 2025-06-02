/*
 * Copyright (c) 2016, Linaro Ltd. All rights reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "defs.S.inc"

s       .req    x0
d       .req    x1
count   .req    x2
t0      .req    x3
t1      .req    x4
t2      .req    x5
t3      .req    x6
t4      .req    x7
t5      .req    x8
t6      .req    x9
t7      .req    x10

        .align  6
DECLARE_FUNC(_Copy_disjoint_words):
        // Ensure 2 word aligned
        tbz     s, #3, fwd_copy_aligned
        ldr     t0, [s], #8
        str     t0, [d], #8
        sub     count, count, #1

fwd_copy_aligned:
        // Bias s & d so we only pre index on the last copy
        sub     s, s, #16
        sub     d, d, #16

        ldp     t0, t1, [s, #16]
        ldp     t2, t3, [s, #32]
        ldp     t4, t5, [s, #48]
        ldp     t6, t7, [s, #64]!

        subs    count, count, #16
        blo     fwd_copy_drain

fwd_copy_again:
        prfm    pldl1keep, [s, #256]
        stp     t0, t1, [d, #16]
        ldp     t0, t1, [s, #16]
        stp     t2, t3, [d, #32]
        ldp     t2, t3, [s, #32]
        stp     t4, t5, [d, #48]
        ldp     t4, t5, [s, #48]
        stp     t6, t7, [d, #64]!
        ldp     t6, t7, [s, #64]!
        subs    count, count, #8
        bhs     fwd_copy_again

fwd_copy_drain:
        stp     t0, t1, [d, #16]
        stp     t2, t3, [d, #32]
        stp     t4, t5, [d, #48]
        stp     t6, t7, [d, #64]!

        // count is now -8..-1 for 0..7 words to copy
        adr     t0, 0f
        add     t0, t0, count, lsl #5
        br      t0

        .align  5
        // -8 == 0 words
        hint    #0x24 // bti j
        ret
        .align  5
        // -7 == 1 word
        hint    #0x24 // bti j
        ldr     t0, [s, #16]
        str     t0, [d, #16]
        ret
        .align  5
        // -6 == 2 words
        hint    #0x24 // bti j
        ldp     t0, t1, [s, #16]
        stp     t0, t1, [d, #16]
        ret
        .align  5
        // -5 == 3 words
        hint    #0x24 // bti j
        ldp     t0, t1, [s, #16]
        ldr     t2, [s, #32]
        stp     t0, t1, [d, #16]
        str     t2, [d, #32]
        ret
        .align  5
        // -4 == 4 words
        hint    #0x24 // bti j
        ldp     t0, t1, [s, #16]
        ldp     t2, t3, [s, #32]
        stp     t0, t1, [d, #16]
        stp     t2, t3, [d, #32]
        ret
        .align  5
        // -3 == 5 words
        hint    #0x24 // bti j
        ldp     t0, t1, [s, #16]
        ldp     t2, t3, [s, #32]
        ldr     t4, [s, #48]
        stp     t0, t1, [d, #16]
        stp     t2, t3, [d, #32]
        str     t4, [d, #48]
        ret
        .align  5
        // -2 == 6 words
        hint    #0x24 // bti j
        ldp     t0, t1, [s, #16]
        ldp     t2, t3, [s, #32]
        ldp     t4, t5, [s, #48]
        stp     t0, t1, [d, #16]
        stp     t2, t3, [d, #32]
        stp     t4, t5, [d, #48]
        ret
        .align  5
        // -1 == 7 words
        hint    #0x24 // bti j
        ldp     t0, t1, [s, #16]
        ldp     t2, t3, [s, #32]
        ldp     t4, t5, [s, #48]
        ldr     t6, [s, #64]
        stp     t0, t1, [d, #16]
        stp     t2, t3, [d, #32]
        stp     t4, t5, [d, #48]
        // Is always aligned here, code for 7 words is two instructions
        // too large so it just falls through.
        .align  5
0:
        str     t6, [d, #64]
        ret

        .align  6
DECLARE_FUNC(_Copy_conjoint_words):
        sub     t0, d, s
        cmp     t0, count, lsl #3
        bhs     _Copy_disjoint_words

        add     s, s, count, lsl #3
        add     d, d, count, lsl #3

        // Ensure 2 word aligned
        tbz     s, #3, bwd_copy_aligned
        ldr     t0, [s, #-8]!
        str     t0, [d, #-8]!
        sub     count, count, #1

bwd_copy_aligned:
        ldp     t0, t1, [s, #-16]
        ldp     t2, t3, [s, #-32]
        ldp     t4, t5, [s, #-48]
        ldp     t6, t7, [s, #-64]!

        subs    count, count, #16
        blo     bwd_copy_drain

bwd_copy_again:
        prfum   pldl1keep, [s, #-256]
        stp     t0, t1, [d, #-16]
        ldp     t0, t1, [s, #-16]
        stp     t2, t3, [d, #-32]
        ldp     t2, t3, [s, #-32]
        stp     t4, t5, [d, #-48]
        ldp     t4, t5, [s, #-48]
        stp     t6, t7, [d, #-64]!
        ldp     t6, t7, [s, #-64]!
        subs    count, count, #8
        bhs     bwd_copy_again

bwd_copy_drain:
        stp     t0, t1, [d, #-16]
        stp     t2, t3, [d, #-32]
        stp     t4, t5, [d, #-48]
        stp     t6, t7, [d, #-64]!

        // count is now -8..-1 for 0..7 words to copy
        adr     t0, 0f
        add     t0, t0, count, lsl #5
        br      t0

        .align  5
        // -8 == 0 words
        hint    #0x24 // bti j
        ret
        .align  5
        // -7 == 1 word
        hint    #0x24 // bti j
        ldr     t0, [s, #-8]
        str     t0, [d, #-8]
        ret
        .align  5
        // -6 == 2 words
        hint    #0x24 // bti j
        ldp     t0, t1, [s, #-16]
        stp     t0, t1, [d, #-16]
        ret
        .align  5
        // -5 == 3 words
        hint    #0x24 // bti j
        ldp     t0, t1, [s, #-16]
        ldr     t2, [s, #-24]
        stp     t0, t1, [d, #-16]
        str     t2, [d, #-24]
        ret
        .align  5
        // -4 == 4 words
        hint    #0x24 // bti j
        ldp     t0, t1, [s, #-16]
        ldp     t2, t3, [s, #-32]
        stp     t0, t1, [d, #-16]
        stp     t2, t3, [d, #-32]
        ret
        .align  5
        // -3 == 5 words
        hint    #0x24 // bti j
        ldp     t0, t1, [s, #-16]
        ldp     t2, t3, [s, #-32]
        ldr     t4, [s, #-40]
        stp     t0, t1, [d, #-16]
        stp     t2, t3, [d, #-32]
        str     t4, [d, #-40]
        ret
        .align  5
        // -2 == 6 words
        hint    #0x24 // bti j
        ldp     t0, t1, [s, #-16]
        ldp     t2, t3, [s, #-32]
        ldp     t4, t5, [s, #-48]
        stp     t0, t1, [d, #-16]
        stp     t2, t3, [d, #-32]
        stp     t4, t5, [d, #-48]
        ret
        .align  5
        // -1 == 7 words
        hint    #0x24 // bti j
        ldp     t0, t1, [s, #-16]
        ldp     t2, t3, [s, #-32]
        ldp     t4, t5, [s, #-48]
        ldr     t6, [s, #-56]
        stp     t0, t1, [d, #-16]
        stp     t2, t3, [d, #-32]
        stp     t4, t5, [d, #-48]
        // Is always aligned here, code for 7 words is two instructions
        // too large so it just falls through.
        .align  5
0:
        str     t6, [d, #-56]
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
