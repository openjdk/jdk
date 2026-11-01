/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

    # Probe whether IC IVAU is trapped.
    #
    # Returns 1 if IC IVAU is trapped (did not fault), 0 if not trapped
    # (faulted on VA 0x0, signal handler redirected to continuation).
    #
    # int ic_ivau_probe(void);
DECLARE_FUNC(ic_ivau_probe):
DECLARE_FUNC(_ic_ivau_probe_fault):
    ic      ivau, xzr
    mov     x0, #1
    ret
DECLARE_FUNC(_ic_ivau_probe_continuation):
    mov     x0, #0
    ret

/* Emit .note.gnu.property section in case of PAC or BTI being enabled. */
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
