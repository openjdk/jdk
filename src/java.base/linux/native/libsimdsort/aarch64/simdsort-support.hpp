/*
 * Copyright (c) 2023 Intel Corporation. All rights reserved.
 * Copyright 2025 Arm Limited and/or its affiliates.
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

#ifndef SIMDSORT_SUPPORT_HPP
#define SIMDSORT_SUPPORT_HPP
#include <stdio.h>
#include <stdlib.h>

#undef assert
#define assert(cond, msg) { if (!(cond)) { fprintf(stderr, "assert fails %s %d: %s\n", __FILE__, __LINE__, msg); abort(); }}

// GCC >= 10.1 is required for a full support of ARM SVE ACLE intrinsics (which also includes the header file - arm_sve.h)
#if defined(__aarch64__) && defined(_LP64) && defined(__GNUC__) && \
    ((__GNUC__ > 10) || (__GNUC__ == 10 && __GNUC_MINOR__ >= 1))
#define __SIMDSORT_SUPPORTED_LINUX
#endif

#endif //SIMDSORT_SUPPORT_HPP
