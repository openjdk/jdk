/*
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
 */

#include <errno.h>

#include "export.h"

EXPORT void set_errno_V(int value) {
    errno = value;
}

EXPORT int set_errno_I(int value) {
    errno = value;
    return 42;
}

EXPORT double set_errno_D(int value) {
    errno = value;
    return 42.0;
}

struct SL {
    long long x;
};

EXPORT struct SL set_errno_SL(int value) {
    errno = value;
    struct SL s;
    s.x = 42;
    return s;
}

struct SLL {
    long long x;
    long long y;
};

EXPORT struct SLL set_errno_SLL(int value) {
    errno = value;
    struct SLL s;
    s.x = 42;
    s.y = 42;
    return s;
}

struct SLLL {
    long long x;
    long long y;
    long long z;
};

EXPORT struct SLLL set_errno_SLLL(int value) {
    errno = value;
    struct SLLL s;
    s.x = 42;
    s.y = 42;
    s.z = 42;
    return s;
}

struct SD {
    double x;
};

EXPORT struct SD set_errno_SD(int value) {
    errno = value;
    struct SD s;
    s.x = 42.0;
    return s;
}

struct SDD {
    double x;
    double y;
};

EXPORT struct SDD set_errno_SDD(int value) {
    errno = value;
    struct SDD s;
    s.x = 42.0;
    s.y = 42.0;
    return s;
}

struct SDDD {
    double x;
    double y;
    double z;
};

EXPORT struct SDDD set_errno_SDDD(int value) {
    errno = value;
    struct SDDD s;
    s.x = 42.0;
    s.y = 42.0;
    s.z = 42.0;
    return s;
}
