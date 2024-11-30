/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

EXPORT void empty() {}

EXPORT int identity(int value) {
    return value;
}

// 128 bit struct returned in buffer on SysV
struct Big {
    long long x;
    long long y;
};

EXPORT struct Big with_return_buffer() {
    struct Big b;
    b.x = 10;
    b.y = 11;
    return b;
}

EXPORT void do_upcall(void(*f)(void)) {
    f();
}

// copy bytes into heap array
EXPORT void test_allow_heap_void(unsigned char* heapArr, unsigned char* nativeArr, int numBytes) {
    for (int i = 0; i < numBytes; i++) {
        heapArr[i] = nativeArr[i];
    }
}

EXPORT int test_allow_heap_int(int a0, unsigned char* heapArr, unsigned char* nativeArr, int numBytes) {
    for (int i = 0; i < numBytes; i++) {
        heapArr[i] = nativeArr[i];
    }
    return a0;
}

struct L2 {
    long long x;
    long long y;
};

EXPORT struct L2 test_allow_heap_return_buffer(struct L2 a0, unsigned char* heapArr, unsigned char* nativeArr, int numBytes) {
    for (int i = 0; i < numBytes; i++) {
        heapArr[i] = nativeArr[i];
    }
    return a0;
}

struct L3 {
    long long x;
    long long y;
    long long z;
};

EXPORT struct L3 test_allow_heap_imr(struct L3 a0, unsigned char* heapArr, unsigned char* nativeArr, int numBytes) {
    for (int i = 0; i < numBytes; i++) {
        heapArr[i] = nativeArr[i];
    }
    return a0;
}

// copy bytes into heap array
EXPORT void test_allow_heap_void_stack(long long a0, long long a1, long long a2, long long a3, long long a4, long long a5,
                                       long long a6, long long a7, char c0, short s0, int i0,
                                       unsigned char* heapArr, unsigned char* nativeArr, int numBytes) {
    for (int i = 0; i < numBytes; i++) {
        heapArr[i] = nativeArr[i];
    }
}
