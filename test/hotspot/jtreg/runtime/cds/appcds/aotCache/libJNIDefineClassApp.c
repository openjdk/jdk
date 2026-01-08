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

#include <jni.h>

JNIEXPORT jclass JNICALL
Java_JNIDefineClassApp_nativeDefineClass(JNIEnv* env, jclass clazz /*unused*/,
                                         jstring className, jobject classLoader, jbyteArray bytecode) {
    const char* classNameChar = (*env)->GetStringUTFChars(env, className, NULL);
    jbyte* arrayContent = (*env)->GetByteArrayElements(env, bytecode, NULL);
    jsize bytecodeLength = (*env)->GetArrayLength(env, bytecode);
    jclass returnValue = (*env)->DefineClass(env, classNameChar, classLoader, arrayContent, bytecodeLength);
    (*env)->ReleaseByteArrayElements(env, bytecode, arrayContent, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, className, classNameChar);
    return returnValue;
}
