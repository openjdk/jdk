//
// Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
// Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifdef BUILTIN_SIM

        .globl SafeFetch32, Fetch32PFI, Fetch32Resume
        .align  16
        .type   SafeFetch32,@function
        // Prototype: int SafeFetch32 (int * Adr, int ErrValue) 
SafeFetch32:
        movl    %esi, %eax
Fetch32PFI:
        movl    (%rdi), %eax
Fetch32Resume:
        ret

        .globl SafeFetchN, FetchNPFI, FetchNResume
        .align  16
        .type   SafeFetchN,@function
        // Prototype: intptr_t SafeFetchN (intptr_t * Adr, intptr_t ErrValue) 
SafeFetchN:
        movq    %rsi, %rax
FetchNPFI:
        movq    (%rdi), %rax
FetchNResume:
        ret

#endif
