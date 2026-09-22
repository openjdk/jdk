/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

        .text

// These baseline implementations are used during VM bootstrap and when
// Zacas is unavailable. The byte operations use a naturally aligned word
// LR/SC loop because the base ISA has no byte-sized LR/SC instructions.

        .align 2
DECLARE_FUNC(riscv_atomic_cmpxchg_1_default_impl):
        fence   rw, rw
        andi    t0, a0, -4
        andi    t1, a0, 3
        slli    t1, t1, 3
        li      t2, 0xff
        sll     t2, t2, t1
        sll     t3, a1, t1
        and     t3, t3, t2
        sll     t4, a2, t1
        and     t4, t4, t2
        not     a3, t2
1:      lr.w    a0, 0(t0)
        and     a1, a0, t2
        bne     a1, t3, 2f
        and     a1, a0, a3
        or      a1, a1, t4
        sc.w    a1, a1, 0(t0)
        bnez    a1, 1b
2:      and     a0, a0, t2
        srl     a0, a0, t1
        fence   rw, rw
        ret

        .align 2
DECLARE_FUNC(riscv_atomic_cmpxchg_4_default_impl):
        fence   rw, rw
        sext.w  t0, a1
1:      lr.w    a3, 0(a0)
        bne     a3, t0, 2f
        sc.w    t1, a2, 0(a0)
        bnez    t1, 1b
2:      fence   rw, rw
        mv      a0, a3
        ret

        .align 2
DECLARE_FUNC(riscv_atomic_cmpxchg_8_default_impl):
        fence   rw, rw
1:      lr.d    a3, 0(a0)
        bne     a3, a1, 2f
        sc.d    t0, a2, 0(a0)
        bnez    t0, 1b
2:      fence   rw, rw
        mv      a0, a3
        ret

        .align 2
DECLARE_FUNC(riscv_atomic_cmpxchg_4_release_default_impl):
        sext.w  t0, a1
1:      lr.w    a3, 0(a0)
        bne     a3, t0, 2f
        sc.w.rl t1, a2, 0(a0)
        bnez    t1, 1b
2:      mv      a0, a3
        ret

        .align 2
DECLARE_FUNC(riscv_atomic_cmpxchg_8_release_default_impl):
1:      lr.d    a3, 0(a0)
        bne     a3, a1, 2f
        sc.d.rl t0, a2, 0(a0)
        bnez    t0, 1b
2:      mv      a0, a3
        ret

        .align 2
DECLARE_FUNC(riscv_atomic_cmpxchg_4_seq_cst_default_impl):
        sext.w  t0, a1
1:      lr.w.aq a3, 0(a0)
        bne     a3, t0, 2f
        sc.w.rl t1, a2, 0(a0)
        bnez    t1, 1b
2:      mv      a0, a3
        ret

        .align 2
DECLARE_FUNC(riscv_atomic_cmpxchg_8_seq_cst_default_impl):
1:      lr.d.aq a3, 0(a0)
        bne     a3, a1, 2f
        sc.d.rl t0, a2, 0(a0)
        bnez    t0, 1b
2:      mv      a0, a3
        ret

        .align 2
DECLARE_FUNC(riscv_atomic_cmpxchg_1_relaxed_default_impl):
        andi    t0, a0, -4
        andi    t1, a0, 3
        slli    t1, t1, 3
        li      t2, 0xff
        sll     t2, t2, t1
        sll     t3, a1, t1
        and     t3, t3, t2
        sll     t4, a2, t1
        and     t4, t4, t2
        not     a3, t2
1:      lr.w    a0, 0(t0)
        and     a1, a0, t2
        bne     a1, t3, 2f
        and     a1, a0, a3
        or      a1, a1, t4
        sc.w    a1, a1, 0(t0)
        bnez    a1, 1b
2:      and     a0, a0, t2
        srl     a0, a0, t1
        ret

        .align 2
DECLARE_FUNC(riscv_atomic_cmpxchg_4_relaxed_default_impl):
        sext.w  t0, a1
1:      lr.w    a3, 0(a0)
        bne     a3, t0, 2f
        sc.w    t1, a2, 0(a0)
        bnez    t1, 1b
2:      mv      a0, a3
        ret

        .align 2
DECLARE_FUNC(riscv_atomic_cmpxchg_8_relaxed_default_impl):
1:      lr.d    a3, 0(a0)
        bne     a3, a1, 2f
        sc.d    t0, a2, 0(a0)
        bnez    t0, 1b
2:      mv      a0, a3
        ret
