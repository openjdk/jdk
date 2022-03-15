/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_LINUX_SAFEFETCH_LINUX_HPP
#define OS_LINUX_SAFEFETCH_LINUX_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

#ifndef ZERO
#if defined(AARCH64) || defined(X86)

#define HAVE_STATIC_SAFEFETCH

extern "C" int _SafeFetch32(int* adr, int errValue);
extern "C" char _SafeFetch32_continuation[] __attribute__ ((visibility ("hidden")));
extern "C" char _SafeFetch32_fault[] __attribute__ ((visibility ("hidden")));

#ifdef _LP64
extern "C" uint64_t _SafeFetch64(uint64_t* adr, uint64_t errValue);
extern "C" char _SafeFetch64_continuation[] __attribute__ ((visibility ("hidden")));
extern "C" char _SafeFetch64_fault[] __attribute__ ((visibility ("hidden")));
#endif // _LP64

#endif // aarch64 or x64 or x86
#endif // !ZERO

#endif
