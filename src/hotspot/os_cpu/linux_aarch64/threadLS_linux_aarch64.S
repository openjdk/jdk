// Copyright (c) 2015, 2022, Red Hat Inc. All rights reserved.
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

        // JavaThread::aarch64_get_thread_helper()
        //
        // Return the current thread pointer in x0.
        // Clobber x1, flags.
        // All other registers are preserved,

DECLARE_FUNC(_ZN10JavaThread25aarch64_get_thread_helperEv):
        hint #0x19      // paciasp
        stp x29, x30, [sp, -16]!
        adrp x0, :tlsdesc:_ZN6Thread12_thr_currentE
        ldr x1, [x0, #:tlsdesc_lo12:_ZN6Thread12_thr_currentE]
        add x0, x0, :tlsdesc_lo12:_ZN6Thread12_thr_currentE
        .tlsdesccall _ZN6Thread12_thr_currentE
        blr x1
        mrs x1, tpidr_el0
        add x0, x1, x0
        ldr x0, [x0]
        ldp x29, x30, [sp], 16
        hint #0x1d      // autiasp
        ret

        .size _ZN10JavaThread25aarch64_get_thread_helperEv, .-_ZN10JavaThread25aarch64_get_thread_helperEv

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
