/*
 * Copyright (c) 1999, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2016 SAP SE. All rights reserved.
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

#ifndef CPU_PPC_GLOBALDEFINITIONS_PPC_HPP
#define CPU_PPC_GLOBALDEFINITIONS_PPC_HPP

// Size of PPC Instructions
const int BytesPerInstWord = 4;

const int StackAlignmentInBytes = 16;

#ifdef AIX
const size_t pd_segfault_address = -1;
#else
const size_t pd_segfault_address = 1024;
#endif

// Indicates whether the C calling conventions require that
// 32-bit integer argument values are extended to 64 bits.
const bool CCallingConventionRequiresIntsAsLongs = true;

#define SUPPORTS_NATIVE_CX8

#define SUPPORT_MONITOR_COUNT

// PPC64 is not specified as multi-copy-atomic
// So we must not #define CPU_MULTI_COPY_ATOMIC

// The expected size in bytes of a cache line.
#define DEFAULT_CACHE_LINE_SIZE 128

// The default padding size for data structures to avoid false sharing.
#define DEFAULT_PADDING_SIZE DEFAULT_CACHE_LINE_SIZE

#define SUPPORT_RESERVED_STACK_AREA

// If UseSIGTRAP is active, we only use the poll bit and no polling page.
// Otherwise, we fall back to usage of the polling page in nmethods.
// Define the condition to use this -XX flag.
#define USE_POLL_BIT_ONLY UseSIGTRAP

#endif // CPU_PPC_GLOBALDEFINITIONS_PPC_HPP
