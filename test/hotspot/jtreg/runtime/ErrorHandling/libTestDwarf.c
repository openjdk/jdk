/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "jni.h"
#include "libTestDwarfHelper.h"
#include <stdio.h>

int zero = 0;
int result = 0;
int limit = 20;

// Explicitly don't inline. foo needs complexity so GCC/Clang don't optimize it away.
#if !defined(_MSC_VER)
__attribute__((noinline))
#endif
void foo(int x) {
    printf("foo3:");
    printf(" %d\n", x);
    for (int i = 0; i < limit; i++) {
        result += zero + i;
    }
    if (x == 3) {
        for (int i = 0; i < limit; i++) {
            result -= zero + i;
        }
        result = 3 / zero; // Crash
    } else {
        for (int i = 0; i < limit; i++) {
            result -= zero + i;
        }
        result = 3 / 2; // No crash
    }

    for (int i = 0; i < limit; i++) {
        for (int j = zero; j < limit; j++) {
            result += zero - i;
        }
    }
}

JNIEXPORT void JNICALL Java_TestDwarf_crashNativeDivByZero(JNIEnv* env, jclass jclazz) {
  limit = 21;
  foo(34 / zero); // Crash
}

JNIEXPORT void JNICALL Java_TestDwarf_crashNativeDereferenceNull(JNIEnv* env, jclass jclazz) {
  dereference_null();
}

JNIEXPORT void JNICALL Java_TestDwarf_crashNativeMultipleMethods(JNIEnv* env, jclass jclazz, jint x) {
  // foo() is not inlined
  foo(x - 2);
  foo(x - 1);
  foo(x);
  for (int i = 0; i < limit; i++) {
    result += zero + i;
  }
  for (int i = 0; i < limit; i++) {
    result += zero + i;
  }
}

// Need to tell if Clang was used to build libTestDwarf.
JNIEXPORT jboolean JNICALL Java_TestDwarf_isUsingClang(JNIEnv* env, jobject obj) {
#if defined(__clang__)
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

