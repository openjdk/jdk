/*
 * Copyright (c) 2023, Google and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_BREAKPOINT_HPP
#define SHARE_UTILITIES_BREAKPOINT_HPP

#include <csignal>

// BREAKPOINT
//
// Programatically triggers a breakpoint for debuggers.

#if defined(TARGET_COMPILER_gcc)

#define BREAKPOINT __builtin_debugtrap()

#elif defined(TARGET_COMPILER_visCPP)

#include <intrin.h>

#pragma intrinsic(__debugbreak)

#define BREAKPOINT __debugbreak()

#elif defined(TARGET_COMPILER_xlc)

#if defined(SIGTRAP)

#define BREAKPOINT ::raise(SIGTRAP)

#elif defined(SIGINT)

#define BREAKPOINT ::raise(SIGINT)

#else

#error Neither SIGTRAP or SIGINT is defined.

#endif

#else

#error Unknown toolchain.

#endif

#endif // SHARE_UTILITIES_BREAKPOINT_HPP
