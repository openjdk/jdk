// Copyright (c) 2026, IBM Inc. All rights reserved.
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

#define DECLARE_LOCAL_FUNC(func) \
    .local func ; \
    .type func, %function ; \
    func

#define DEFAULT_ATOMIC_OP_NAME(OPNAME, SIZE, MEMORDER) \
aarch64_atomic_ ## OPNAME ## _ ## SIZE ## MEMORDER ## _impl

#define DEFAULT_ATOMIC_OP(OPNAME, SIZE, MEMORDER)                       \
        .align 3;                                                       \
        .globl DEFAULT_ATOMIC_OP_NAME(OPNAME, SIZE, MEMORDER);          \
        .hidden DEFAULT_ATOMIC_OP_NAME(OPNAME, SIZE, MEMORDER);         \
        .type DEFAULT_ATOMIC_OP_NAME(OPNAME, SIZE, MEMORDER), %object;  \
        .size DEFAULT_ATOMIC_OP_NAME(OPNAME, SIZE, MEMORDER), 8;        \
DEFAULT_ATOMIC_OP_NAME(OPNAME, SIZE, MEMORDER):                         \
  .xword aarch64_atomic_ ## OPNAME ## _ ## SIZE ## MEMORDER ## _default_impl;

#define ATOMIC_OPS_START                        \
        .data;                                  \
        .align 3;                               \
        .global aarch64_atomic_ops;             \
        .type aarch64_atomic_ops, %object;      \
aarch64_atomic_ops:;

#define ATOMIC_OPS_END                          \
        .data;                                  \
        .global aarch64_atomic_ops_end;         \
        .type aarch64_atomic_ops_end, %object;  \
aarch64_atomic_ops_end:;

#include "../../cpu/aarch64/atomic_aarch64.hpp"

#include "atomic_linux_aarch64.s.inc"

#ifndef __ARM_FEATURE_ATOMICS

#define __ARM_FEATURE_ATOMICS

#undef ATOMIC_OPS_START
#undef ATOMIC_OPS_END

#define ATOMIC_OPS_START                        \
        .section ".rodata";                     \
        .align 3;                               \
        .global lse_aarch64_atomic_ops;         \
        .type lse_aarch64_atomic_ops, %object;  \
lse_aarch64_atomic_ops:;

#define ATOMIC_OPS_END                                  \
        .section ".rodata";                             \
        .global lse_aarch64_atomic_ops_end;             \
        .type lse_aarch64_atomic_ops_end, %object;      \
lse_aarch64_atomic_ops_end:;

#undef DEFAULT_ATOMIC_OP_NAME
#define DEFAULT_ATOMIC_OP_NAME(OPNAME, SIZE, MEMORDER) \
lse_aarch64_atomic_ ## OPNAME ## _ ## SIZE ## MEMORDER ## _impl

#undef DEFAULT_ATOMIC_OP
#define DEFAULT_ATOMIC_OP(OPNAME, SIZE, MEMORDER)                     \
        .align 3;                                                       \
        .globl DEFAULT_ATOMIC_OP_NAME(OPNAME, SIZE, MEMORDER);          \
        .hidden DEFAULT_ATOMIC_OP_NAME(OPNAME, SIZE, MEMORDER);         \
        .type DEFAULT_ATOMIC_OP_NAME(OPNAME, SIZE, MEMORDER), %object;  \
        .size DEFAULT_ATOMIC_OP_NAME(OPNAME, SIZE, MEMORDER), 8;        \
DEFAULT_ATOMIC_OP_NAME(OPNAME, SIZE, MEMORDER):                         \
  .xword lse_aarch64_atomic_ ## OPNAME ## _ ## SIZE ## MEMORDER ## _default_impl;

#undef DECLARE_LOCAL_FUNC
#define DECLARE_LOCAL_FUNC(func)                \
    .local lse_  ## func ;                      \
    .type lse_ ## func, %function ;             \
    lse_ ##func

        .arch armv8.1-a

#undef CPU_AARCH64_ATOMIC_AARCH64_HPP
#include "../../cpu/aarch64/atomic_aarch64.hpp"

#include "atomic_linux_aarch64.s.inc"

#endif // __ARM_FEATURE_ATOMICS

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
