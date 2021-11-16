/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include <stdlib.h>

#include "jni.h"

static jclass test_class;
static jmethodID mid;
static jint current_jni_version = JNI_VERSION_10;

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    jclass cl;

    (*vm)->GetEnv(vm, (void **) &env, current_jni_version);

    cl = (*env)->FindClass(env, "NativeMethod");
    test_class = (*env)->NewGlobalRef(env, cl);
    mid = (*env)->GetMethodID(env, test_class, "walk", "()V");

    return current_jni_version;
}

/*
 * Class:     NativeMethod
 * Method:    test
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_NativeMethod_test(JNIEnv *env, jobject obj) {
    (*env)->CallVoidMethod(env, obj, mid);

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->FatalError(env, "Exception thrown");
    }
}
