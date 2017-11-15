/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_GLOBALDEFINITIONS_SPARC_HPP
#define CPU_SPARC_VM_GLOBALDEFINITIONS_SPARC_HPP

// Size of Sparc Instructions
const int BytesPerInstWord = 4;

const int StackAlignmentInBytes = (2*wordSize);

// Indicates whether the C calling conventions require that
// 32-bit integer argument values are extended to 64 bits.
const bool CCallingConventionRequiresIntsAsLongs = true;

#define SUPPORTS_NATIVE_CX8

// The expected size in bytes of a cache line, used to pad data structures.
#if defined(TIERED)
  // tiered, 64-bit, large machine
  #define DEFAULT_CACHE_LINE_SIZE 128
#elif defined(COMPILER1)
  // pure C1, 32-bit, small machine
  #define DEFAULT_CACHE_LINE_SIZE 16
#elif defined(COMPILER2)
  // pure C2, 64-bit, large machine
  #define DEFAULT_CACHE_LINE_SIZE 128
#endif

#if defined(SOLARIS)
#define SUPPORT_RESERVED_STACK_AREA
#endif

// SPARC have implemented the local polling
#define THREAD_LOCAL_POLL

#endif // CPU_SPARC_VM_GLOBALDEFINITIONS_SPARC_HPP
