/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

// Size of PPC Instructions
const int BytesPerInstWord = 4;

const int StackAlignmentInBytes = 16;

// Indicates whether the C calling conventions require that
// 32-bit integer argument values are properly extended to 64 bits.
// If set, SharedRuntime::c_calling_convention() must adapt
// signatures accordingly.
const bool CCallingConventionRequiresIntsAsLongs = true;

#define SUPPORTS_NATIVE_CX8

// The PPC CPUs are NOT multiple-copy-atomic.
#define CPU_NOT_MULTIPLE_COPY_ATOMIC

#endif // CPU_PPC_VM_GLOBALDEFINITIONS_PPC_HPP
