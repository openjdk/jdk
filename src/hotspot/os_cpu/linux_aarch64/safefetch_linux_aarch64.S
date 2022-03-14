/*
 * Copyright (c) 2022 SAP. All rights reserved.
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

		.globl _SafeFetch64
		.globl _SafeFetch64_fault
		.globl _SafeFetch64_continuation
		.globl _SafeFetch32
		.globl _SafeFetch32_fault
		.globl _SafeFetch32_continuation

_SafeFetch64:
		# x0: suspicious address
		# x1: default return value
_SafeFetch64_fault:
        ldr      x1, [x0]
_SafeFetch64_continuation:
        mov      x0, x1
        ret

_SafeFetch32:
		# x0: suspicious address
		# x1: default return value
_SafeFetch32_fault:
        ldr      w1, [x0]
_SafeFetch32_continuation:
        mov      x0, x1
        ret
