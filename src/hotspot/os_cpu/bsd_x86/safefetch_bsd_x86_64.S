#
# Copyright (c) 2022 SAP SE. All rights reserved.
# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

#ifdef __APPLE__
# Darwin uses _ prefixed global symbols
#define SYMBOL(s) _ ## s
#define ELF_TYPE(name, description)
#else
#define SYMBOL(s) s
#define ELF_TYPE(name, description) .type name,description
#endif

    .globl SYMBOL(_SafeFetch32)
    .globl SYMBOL(_SafeFetch64)
    .globl SYMBOL(_SafeFetch32_fault)
    .globl SYMBOL(_SafeFetch64_fault)
    .globl SYMBOL(_SafeFetch32_continuation)
    .globl SYMBOL(_SafeFetch64_continuation)

    .text

    # Support for int SafeFetch32(int* address, int defaultval);
    #
    #  %rdi : address
    #  %esi : defaultval
	ELF_TYPE(_SafeFetch32,@function)
SYMBOL(_SafeFetch32:)
SYMBOL(_SafeFetch32_fault:)
    movl (%rdi), %eax
    ret
SYMBOL(_SafeFetch32_continuation:)
    movl %esi, %eax
    ret

    # Support for uint64_t SafeFetch64(uint64_t* address, uint64_t defaultval);
    #
    #  %rdi : address
    #  %rsi : defaultval
    ELF_TYPE(_SafeFetch64,@function)
SYMBOL(_SafeFetch64:)
SYMBOL(_SafeFetch64_fault:)
    movq (%rdi), %rax
    ret
SYMBOL(_SafeFetch64_continuation:)
    movq %rsi, %rax
    ret
