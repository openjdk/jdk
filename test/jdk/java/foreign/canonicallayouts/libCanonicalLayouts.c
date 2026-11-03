/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "export.h"

#include <stdint.h>
#include <wchar.h>
#include <stdbool.h>
#include <stdalign.h>

// Inverted test to avoid compiler warnings
#define IS_SIGNED(T) (!((T)0 < (T)-1))

// Expose some basic information about the type, such as size, align, and signedness
#define CHECKS_ALL(T, NAME) \
    EXPORT long long NAME##_size() { \
        return sizeof(T); \
    } \
    EXPORT long long NAME##_align() { \
        return alignof(T); \
    }

#define CHECKS_INTEGRAL(T, NAME) \
CHECKS_ALL(T, NAME) \
    EXPORT bool NAME##_signedness() { \
        return IS_SIGNED(T); \
    }

CHECKS_ALL(bool, bool)
CHECKS_ALL(float, float)
CHECKS_ALL(double, double)
CHECKS_ALL(void*, void_star)

CHECKS_INTEGRAL(char, char)
CHECKS_INTEGRAL(unsigned char, unsigned_char)
CHECKS_INTEGRAL(short, short)
CHECKS_INTEGRAL(unsigned short, unsigned_short)
CHECKS_INTEGRAL(int, int)
CHECKS_INTEGRAL(unsigned int, unsigned_int)
CHECKS_INTEGRAL(long, long)
CHECKS_INTEGRAL(unsigned long, unsigned_long)
CHECKS_INTEGRAL(long long, long_long)
CHECKS_INTEGRAL(unsigned long long, unsigned_long_long)
CHECKS_INTEGRAL(size_t, size_t)
CHECKS_INTEGRAL(wchar_t, wchar_t)
CHECKS_INTEGRAL(int8_t, int8_t)
CHECKS_INTEGRAL(int16_t, int16_t)
CHECKS_INTEGRAL(int32_t, int32_t)
CHECKS_INTEGRAL(int64_t, int64_t)
CHECKS_INTEGRAL(uint8_t, uint8_t)
CHECKS_INTEGRAL(uint16_t, uint16_t)
CHECKS_INTEGRAL(uint32_t, uint32_t)
CHECKS_INTEGRAL(uint64_t, uint64_t)
