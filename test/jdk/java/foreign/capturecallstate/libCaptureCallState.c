/*
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
 */

#include <errno.h>

#ifdef _WIN64
#include <Windows.h>
#include <Winsock2.h>

#define EXPORT __declspec(dllexport)
#else
#define EXPORT
#endif

EXPORT void set_errno_V(int capture_state_value) {
    errno = capture_state_value;
}

EXPORT void get_errno_V(int* value_out, void (*cb)(void)) {
    cb();
    *value_out = errno;
}

EXPORT int set_errno_I(int capture_state_value, int test_value) {
    errno = capture_state_value;
    return test_value;
}

EXPORT int get_errno_I(int* value_out, int (*cb)(void)) {
    int i = cb();
    *value_out = errno;
    return i;
}

EXPORT double set_errno_D(int capture_state_value, double test_value) {
    errno = capture_state_value;
    return test_value;
}

EXPORT double get_errno_D(int* value_out, double (*cb)(void)) {
    double d = cb();
    *value_out = errno;
    return d;
}

struct SL {
    long long x;
};

EXPORT struct SL set_errno_SL(int capture_state_value, struct SL test_value) {
    errno = capture_state_value;
    return test_value;
}

EXPORT struct SL get_errno_SL(int* value_out, struct SL (*cb)(void)) {
    struct SL s = cb();
    *value_out = errno;
    return s;
}

struct SLL {
    long long x;
    long long y;
};

EXPORT struct SLL set_errno_SLL(int capture_state_value, struct SLL test_value) {
    errno = capture_state_value;
    return test_value;
}

EXPORT struct SLL get_errno_SLL(int* value_out, struct SLL (*cb)(void)) {
    struct SLL s = cb();
    *value_out = errno;
    return s;
}

struct SLLL {
    long long x;
    long long y;
    long long z;
};

EXPORT struct SLLL set_errno_SLLL(int capture_state_value, struct SLLL test_value) {
    errno = capture_state_value;
    return test_value;
}

EXPORT struct SLLL get_errno_SLLL(int* value_out, struct SLLL (*cb)(void)) {
    struct SLLL s = cb();
    *value_out = errno;
    return s;
}

struct SD {
    double x;
};

EXPORT struct SD set_errno_SD(int capture_state_value, struct SD test_value) {
    errno = capture_state_value;
    return test_value;
}

EXPORT struct SD get_errno_SD(int* value_out, struct SD (*cb)(void)) {
    struct SD s = cb();
    *value_out = errno;
    return s;
}

struct SDD {
    double x;
    double y;
};

EXPORT struct SDD set_errno_SDD(int capture_state_value, struct SDD test_value) {
    errno = capture_state_value;
    return test_value;
}

EXPORT struct SDD get_errno_SDD(int* value_out, struct SDD (*cb)(void)) {
    struct SDD s = cb();
    *value_out = errno;
    return s;
}

struct SDDD {
    double x;
    double y;
    double z;
};

EXPORT struct SDDD set_errno_SDDD(int capture_state_value, struct SDDD test_value) {
    errno = capture_state_value;
    return test_value;
}

EXPORT struct SDDD get_errno_SDDD(int* value_out, struct SDDD (*cb)(void)) {
    struct SDDD s = cb();
    *value_out = errno;
    return s;
}

#ifdef _WIN64
EXPORT void set_last_error(int capture_state_value) {
    SetLastError(capture_state_value);
}

EXPORT void set_wsa_last_error(int capture_state_value) {
    WSASetLastError(capture_state_value);
}
#endif
