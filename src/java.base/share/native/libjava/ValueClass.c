/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
#include "jvm.h"

#include "jdk_internal_value_ValueClass.h"

JNIEXPORT jarray JNICALL
Java_jdk_internal_value_ValueClass_newNullRestrictedNonAtomicArray(JNIEnv *env, jclass cls, jclass elmClass, jint len, jobject initVal)
{
    return JVM_NewNullRestrictedNonAtomicArray(env, elmClass, len, initVal);
}

JNIEXPORT jarray JNICALL
Java_jdk_internal_value_ValueClass_newNullRestrictedAtomicArray(JNIEnv *env, jclass cls, jclass elmClass, jint len, jobject initVal)
{
    return JVM_NewNullRestrictedAtomicArray(env, elmClass, len, initVal);
}

JNIEXPORT jarray JNICALL
Java_jdk_internal_value_ValueClass_newNullableAtomicArray(JNIEnv *env, jclass cls, jclass elmClass, jint len)
{
    return JVM_NewNullableAtomicArray(env, elmClass, len);
}

JNIEXPORT jarray JNICALL
Java_jdk_internal_value_ValueClass_newReferenceArray(JNIEnv *env, jclass cls, jclass elmClass, jint len)
{
    return JVM_NewReferenceArray(env, elmClass, len);
}

JNIEXPORT jboolean JNICALL
Java_jdk_internal_value_ValueClass_isFlatArray(JNIEnv *env, jclass cls, jobject obj)
{
    return JVM_IsFlatArray(env, obj);
}

JNIEXPORT jarray JNICALL
Java_jdk_internal_value_ValueClass_copyOfSpecialArray0(JNIEnv *env, jclass cls, jarray array, jint from, jint to)
{
    return JVM_CopyOfSpecialArray(env, array, from, to);
}

JNIEXPORT jboolean JNICALL
Java_jdk_internal_value_ValueClass_isNullRestrictedArray(JNIEnv *env, jclass cls, jobject obj)
{
    return JVM_IsNullRestrictedArray(env, obj);
}

JNIEXPORT jboolean JNICALL
Java_jdk_internal_value_ValueClass_isAtomicArray(JNIEnv *env, jclass cls, jobject obj)
{
    return JVM_IsAtomicArray(env, obj);
}
