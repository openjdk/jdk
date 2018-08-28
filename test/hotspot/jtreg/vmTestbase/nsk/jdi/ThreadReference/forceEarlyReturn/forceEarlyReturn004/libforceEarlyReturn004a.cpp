/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifdef __cplusplus
extern "C" {
#endif

#ifndef JNI_ENV_PTR

#ifdef __cplusplus
#define JNI_ENV_ARG_2(x, y) y
#define JNI_ENV_ARG_3(x, y, z) y, z
#define JNI_ENV_ARG_4(x, y, z, a) y, z, a
#define JNI_ENV_PTR(x) x
#else
#define JNI_ENV_ARG_2(x,y) x, y
#define JNI_ENV_ARG_3(x, y, z) x, y, z
#define JNI_ENV_ARG_4(x, y, z, a) x, y, z, a
#define JNI_ENV_PTR(x) (*x)
#endif

#endif

int always_true = 1;

JNIEXPORT jint JNICALL
Java_nsk_jdi_ThreadReference_forceEarlyReturn_forceEarlyReturn004_forceEarlyReturn004a_nativeMethod(JNIEnv *env, jobject classObject, jobject object)
{
    int dummy_counter = 0;
    // notify another thread that thread in native method
    jclass klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, object));
    jfieldID field = JNI_ENV_PTR(env)->GetFieldID(JNI_ENV_ARG_4(env, klass, "threadInNative", "Z"));
    JNI_ENV_PTR(env)->SetBooleanField(JNI_ENV_ARG_4(env, object, field, 1));

    // execute infinite loop to be sure that thread in native method
    while(always_true)
    {
        // Need some dummy code so the optimizer does not remove this loop.
        dummy_counter = dummy_counter < 1000 ? 0 : dummy_counter + 1;
    }
    // The optimizer can be surprisingly clever.
    // Use dummy_counter so it can never be optimized out.
    // This statement will always return 0.
    return dummy_counter >= 0 ? 0 : 1;
}

#ifdef __cplusplus
}
#endif
