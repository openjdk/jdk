/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "nativeStack.hpp"

#if !defined(_MSC_VER) && (!defined(__has_builtin) || !__has_builtin(__builtin_stack_address))
#ifndef S390
NOINLINE address NativeStack::current() {
  return __builtin_dwarf_cfa();
}
#else
asm (R"(
    .globl _ZN11NativeStack7currentEv
    .hidden _ZN11NativeStack7currentEv
    .type _ZN11NativeStack7currentEv, %function
_ZN11NativeStack7currentEv:
    lgr     %r2, %r15
    br      %r14
)");
#endif
#endif
