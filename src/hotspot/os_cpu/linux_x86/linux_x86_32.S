#
# Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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

        .globl _Atomic_cmpxchg_long
        .globl _Atomic_move_long

	.text

        .globl  SpinPause
	.type   SpinPause,@function
        .p2align 4,,15
SpinPause:
        rep
        nop
        movl    $1, %eax
        ret


        # Support for jlong Atomic::cmpxchg(volatile jlong* dest,
        #                                   jlong compare_value,
        #                                   jlong exchange_value)
        #
        .p2align 4,,15
	.type    _Atomic_cmpxchg_long,@function
_Atomic_cmpxchg_long:
                                   #  8(%esp) : return PC
        pushl    %ebx              #  4(%esp) : old %ebx
        pushl    %edi              #  0(%esp) : old %edi
        movl     12(%esp), %ebx    # 12(%esp) : exchange_value (low)
        movl     16(%esp), %ecx    # 16(%esp) : exchange_value (high)
        movl     24(%esp), %eax    # 24(%esp) : compare_value (low)
        movl     28(%esp), %edx    # 28(%esp) : compare_value (high)
        movl     20(%esp), %edi    # 20(%esp) : dest
        lock cmpxchg8b (%edi)
        popl     %edi
        popl     %ebx
        ret


        # Support for jlong Atomic::load and Atomic::store.
        # void _Atomic_move_long(const volatile jlong* src, volatile jlong* dst)
        .p2align 4,,15
	.type    _Atomic_move_long,@function
_Atomic_move_long:
        movl     4(%esp), %eax   # src
        fildll    (%eax)
        movl     8(%esp), %eax   # dest
        fistpll   (%eax)
        ret
