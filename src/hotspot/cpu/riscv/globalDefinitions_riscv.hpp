/*
 * Copyright (c) 1999, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2015, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_GLOBALDEFINITIONS_RISCV_HPP
#define CPU_RISCV_GLOBALDEFINITIONS_RISCV_HPP

const int StackAlignmentInBytes = 16;
const size_t pd_segfault_address = 1024;

// Indicates whether the C calling conventions require that
// 32-bit integer argument values are extended to 64 bits.
const bool CCallingConventionRequiresIntsAsLongs = false;

// RISCV has adopted a multicopy atomic model closely following
// that of ARMv8.
#define CPU_MULTI_COPY_ATOMIC

// To be safe, we deoptimize when we come across an access that needs
// patching. This is similar to what is done on aarch64.
#define DEOPTIMIZE_WHEN_PATCHING

#define SUPPORTS_NATIVE_CX8

#define SUPPORT_MONITOR_COUNT

#define SUPPORT_RESERVED_STACK_AREA

#define USE_POINTERS_TO_REGISTER_IMPL_ARRAY

// auipc useable for all cc -> cc calls and jumps
#define CODE_CACHE_SIZE_LIMIT ((2*G)-(2*K))

// The expected size in bytes of a cache line.
#define DEFAULT_CACHE_LINE_SIZE 64

// The default padding size for data structures to avoid false sharing.
#define DEFAULT_PADDING_SIZE DEFAULT_CACHE_LINE_SIZE

#endif // CPU_RISCV_GLOBALDEFINITIONS_RISCV_HPP
