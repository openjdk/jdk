/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All rights reserved.
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
 */

#include "defs.S.inc"

// Load a one-byte VM flag through the GOT. Clobbers dst.
    .macro LOAD_BOOL_FLAG flag, dst
    .option push
    .option pic
    la      \dst, \flag
    .option pop
    lbu     \dst, 0(\dst)
    .endm

// RVV requires both build-time support and UseRVV. Clobbers tmp.
    .macro BRANCH_IF_RVV target, tmp
#if defined(__riscv_vector) && __riscv_v_elen >= 64
    LOAD_BOOL_FLAG UseRVV, \tmp
    bnez    \tmp, \target
#endif
    .endm

// a0: source, a1: destination, a2: count in eight-byte HeapWords.
// The caller handles counts of eight words or fewer.
// Both addresses must be HeapWord aligned for per-word atomicity.
// Load each chunk before storing to preserve overlapping source data.

    .text
    .p2align 5
DECLARE_FUNC(_Copy_disjoint_words):
    BRANCH_IF_RVV .Lforward_rvv, t0
    li      a4, 8
    bltu    a2, a4, .Lforward_tail

    .p2align 4
.Lforward_loop:
    ld      t0, 0(a0)
    ld      t1, 8(a0)
    ld      t2, 16(a0)
    ld      t3, 24(a0)
    ld      t4, 32(a0)
    ld      t5, 40(a0)
    ld      t6, 48(a0)
    ld      a3, 56(a0)
    sd      t0, 0(a1)
    sd      t1, 8(a1)
    sd      t2, 16(a1)
    sd      t3, 24(a1)
    sd      t4, 32(a1)
    sd      t5, 40(a1)
    sd      t6, 48(a1)
    sd      a3, 56(a1)
    addi    a0, a0, 64
    addi    a1, a1, 64
    addi    a2, a2, -8
    bgeu    a2, a4, .Lforward_loop

.Lforward_tail:
    andi    t0, a2, 4
    beqz    t0, .Lforward_two
    ld      t0, 0(a0)
    ld      t1, 8(a0)
    ld      t2, 16(a0)
    ld      t3, 24(a0)
    sd      t0, 0(a1)
    sd      t1, 8(a1)
    sd      t2, 16(a1)
    sd      t3, 24(a1)
    addi    a0, a0, 32
    addi    a1, a1, 32
.Lforward_two:
    andi    t0, a2, 2
    beqz    t0, .Lforward_one
    ld      t0, 0(a0)
    ld      t1, 8(a0)
    sd      t0, 0(a1)
    sd      t1, 8(a1)
    addi    a0, a0, 16
    addi    a1, a1, 16
.Lforward_one:
    andi    t0, a2, 1
    beqz    t0, .Lforward_done
    ld      t0, 0(a0)
    sd      t0, 0(a1)
.Lforward_done:
    ret

#if defined(__riscv_vector) && __riscv_v_elen >= 64
    .p2align 4
.Lforward_rvv:
    // e64 preserves HeapWord atomicity.
    vsetvli t0, a2, e64, m2, ta, ma
    slli    t1, t0, 3
    vle64.v v0, (a0)
    vse64.v v0, (a1)
    add     a0, a0, t1
    add     a1, a1, t1
    sub     a2, a2, t0
    bnez    a2, .Lforward_rvv
    ret
#endif
    .size _Copy_disjoint_words, .-_Copy_disjoint_words

    .p2align 5
DECLARE_FUNC(_Copy_conjoint_words):
    slli    t0, a2, 3
    sub     t1, a1, a0
    // Unsigned comparison also selects forward copying when dst < src.
    bgeu    t1, t0, _Copy_disjoint_words

    // Copy backward for overlapping ranges with dst >= src.
    add     a0, a0, t0
    add     a1, a1, t0
    BRANCH_IF_RVV .Lbackward_rvv, t0
    // Both pointers are just past the remaining ranges.
    li      a4, 8
    bltu    a2, a4, .Lbackward_tail

    .p2align 4
.Lbackward_loop:
    ld      t0, -8(a0)
    ld      t1, -16(a0)
    ld      t2, -24(a0)
    ld      t3, -32(a0)
    ld      t4, -40(a0)
    ld      t5, -48(a0)
    ld      t6, -56(a0)
    ld      a3, -64(a0)
    sd      t0, -8(a1)
    sd      t1, -16(a1)
    sd      t2, -24(a1)
    sd      t3, -32(a1)
    sd      t4, -40(a1)
    sd      t5, -48(a1)
    sd      t6, -56(a1)
    sd      a3, -64(a1)
    addi    a0, a0, -64
    addi    a1, a1, -64
    addi    a2, a2, -8
    bgeu    a2, a4, .Lbackward_loop

.Lbackward_tail:
    andi    t0, a2, 4
    beqz    t0, .Lbackward_two
    ld      t0, -8(a0)
    ld      t1, -16(a0)
    ld      t2, -24(a0)
    ld      t3, -32(a0)
    sd      t0, -8(a1)
    sd      t1, -16(a1)
    sd      t2, -24(a1)
    sd      t3, -32(a1)
    addi    a0, a0, -32
    addi    a1, a1, -32
.Lbackward_two:
    andi    t0, a2, 2
    beqz    t0, .Lbackward_one
    ld      t0, -8(a0)
    ld      t1, -16(a0)
    sd      t0, -8(a1)
    sd      t1, -16(a1)
    addi    a0, a0, -16
    addi    a1, a1, -16
.Lbackward_one:
    andi    t0, a2, 1
    beqz    t0, .Lbackward_done
    ld      t0, -8(a0)
    sd      t0, -8(a1)
.Lbackward_done:
    ret

#if defined(__riscv_vector) && __riscv_v_elen >= 64
    .p2align 4
.Lbackward_rvv:
    vsetvli t0, a2, e64, m2, ta, ma
    slli    t1, t0, 3
    sub     a0, a0, t1
    sub     a1, a1, t1
    vle64.v v0, (a0)
    vse64.v v0, (a1)
    sub     a2, a2, t0
    bnez    a2, .Lbackward_rvv
    ret
#endif
    .size _Copy_conjoint_words, .-_Copy_conjoint_words

    .section .note.GNU-stack,"",@progbits
