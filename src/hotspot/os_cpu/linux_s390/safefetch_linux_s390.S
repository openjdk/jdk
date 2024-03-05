/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

    .globl SafeFetchN_impl
    .globl _SafeFetchN_fault
    .globl _SafeFetchN_continuation
    .globl SafeFetch32_impl
    .globl _SafeFetch32_fault
    .globl _SafeFetch32_continuation

    .hidden SafeFetchN_impl
    .hidden _SafeFetchN_fault
    .hidden _SafeFetchN_continuation
    .hidden SafeFetch32_impl
    .hidden _SafeFetch32_fault
    .hidden _SafeFetch32_continuation

    # Support for int SafeFetch32(int* address, int defaultval);
    #
    #  r2 : address
    #  r3 : defaultval
    #  r2 : retval
SafeFetch32_impl:
_SafeFetch32_fault:
    lgf      %r2, 0(%r2)
    br       %r14
_SafeFetch32_continuation:
    lgr      %r2, %r3
    br       %r14

    # Support for intptr_t SafeFetchN(intptr_t* address, intptr_t defaultval);
    #
    #  r2 : address
    #  r3 : defaultval
    #  r2 : retval
SafeFetchN_impl:
_SafeFetchN_fault:
    lg       %r2, 0(%r2)
    br       %r14
_SafeFetchN_continuation:
    lgr      %r2, %r3
    br       %r14
