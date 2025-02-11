#
# Copyright (c) 2022 SAP SE. All rights reserved.
# Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "defs.S.inc"

    .text

    # Support for int SafeFetch32(int* address, int defaultval);
    #
    #  8(%esp) : default value
    #  4(%esp) : crash address
    #  0(%esp) : return pc
DECLARE_FUNC(SafeFetch32_impl):
    movl 4(%esp),%ecx         # load address from stack
DECLARE_FUNC(_SafeFetch32_fault):
    movl (%ecx), %eax         # load target value, may fault
    ret
DECLARE_FUNC(_SafeFetch32_continuation):
    movl 8(%esp),%eax         # load default value from stack
    ret
