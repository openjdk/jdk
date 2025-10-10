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
 */

#include <stdio.h>

#include "jni.h"

static void method0(JNIEnv* env, jclass cls) {
  printf("method0\n");
}

static void method1(JNIEnv* env, jclass cls) {
  printf("method1\n");
}

JNIEXPORT void JNICALL
Java_gc_NativeWrapperCollection_TestNativeWrapperCollection_callRegisterNatives
(JNIEnv *env, jclass cls, jint index) {
  JNINativeMethod nativeMethods[] = {
    {
      (char*) "method",                        // name
      (char*) "()V",                           // sig
      (void*) (index == 0 ? method0 : method1) // native method ptr
    }
  };
  (*env)->RegisterNatives(env, cls, nativeMethods, 1);
}
