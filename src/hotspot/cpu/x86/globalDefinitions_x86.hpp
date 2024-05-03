/*
 * Copyright (c) 1999, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_GLOBALDEFINITIONS_X86_HPP
#define CPU_X86_GLOBALDEFINITIONS_X86_HPP

const int StackAlignmentInBytes  = 16;
const size_t pd_segfault_address = 1024;

// Indicates whether the C calling conventions require that
// 32-bit integer argument values are extended to 64 bits.
const bool CCallingConventionRequiresIntsAsLongs = false;

#define SUPPORTS_NATIVE_CX8

#define SUPPORT_MONITOR_COUNT

#define CPU_MULTI_COPY_ATOMIC

// The expected size in bytes of a cache line.
#define DEFAULT_CACHE_LINE_SIZE 64

// The default padding size for data structures to avoid false sharing.
#ifdef _LP64
// The common wisdom is that adjacent cache line prefetchers on some hardware
// may pull two cache lines on access, so we have to pessimistically assume twice
// the cache line size for padding. TODO: Check if this is still true for modern
// hardware. If not, DEFAULT_CACHE_LINE_SIZE might as well suffice.
#define DEFAULT_PADDING_SIZE (DEFAULT_CACHE_LINE_SIZE*2)
#else
#define DEFAULT_PADDING_SIZE DEFAULT_CACHE_LINE_SIZE
#endif

#if defined(COMPILER2)
// Include Restricted Transactional Memory lock eliding optimization
#define INCLUDE_RTM_OPT 1
#endif

#if defined(LINUX) || defined(__APPLE__)
#define SUPPORT_RESERVED_STACK_AREA
#endif

#define USE_POINTERS_TO_REGISTER_IMPL_ARRAY

#endif // CPU_X86_GLOBALDEFINITIONS_X86_HPP
