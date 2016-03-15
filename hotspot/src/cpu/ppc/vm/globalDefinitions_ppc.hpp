/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2015 SAP SE. All rights reserved.
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

#ifndef CPU_PPC_VM_GLOBALDEFINITIONS_PPC_HPP
#define CPU_PPC_VM_GLOBALDEFINITIONS_PPC_HPP

#ifdef CC_INTERP
#error "CC_INTERP is no longer supported. Removed in change 8145117."
#endif

// Size of PPC Instructions
const int BytesPerInstWord = 4;

const int StackAlignmentInBytes = 16;

// Indicates whether the C calling conventions require that
// 32-bit integer argument values are extended to 64 bits.
const bool CCallingConventionRequiresIntsAsLongs = true;

#define SUPPORTS_NATIVE_CX8

// The PPC CPUs are NOT multiple-copy-atomic.
#define CPU_NOT_MULTIPLE_COPY_ATOMIC

// The expected size in bytes of a cache line, used to pad data structures.
#define DEFAULT_CACHE_LINE_SIZE 128

#if defined(COMPILER2) && (defined(AIX) || defined(linux))
// Include Transactional Memory lock eliding optimization
#define INCLUDE_RTM_OPT 1
#endif

#endif // CPU_PPC_VM_GLOBALDEFINITIONS_PPC_HPP
