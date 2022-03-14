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
    .globl _SafeFetch32
    .globl _SafeFetch64
    .globl _SafeFetch32_fault
    .globl _SafeFetch64_fault
    .globl _SafeFetch32_continuation
    .globl _SafeFetch64_continuation

	.text


    # Support for int SafeFetch32(int* address, int defaultval);
    #
    #  %rdi : address
    #  %esi : defaultval
	.type    _SafeFetch32,@function
_SafeFetch32:
_SafeFetch32_fault:
    movl (%rdi), %eax       # load target value, may fault
    ret
_SafeFetch32_continuation:
    movl %esi, %eax         # return default
    ret

    # Support for uint64_t SafeFetch64(uint64_t* address, uint64_t defaultval);
    #
    #  %rdi : address
    #  %rsi : defaultval
	.type    _SafeFetch64,@function
_SafeFetch64:
_SafeFetch64_fault:
    movq (%rdi), %rax       # load target value, may fault
    ret
_SafeFetch64_continuation:
    movq %rsi, %rax         # return default
    ret
