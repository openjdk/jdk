/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

#include "jni.h"
#include <stdio.h>

JNIEXPORT jbyte JNICALL
Java_TestNative_getByteRaw(JNIEnv *env, jclass cls, jlong addr, jint index) {
    jbyte *arr = (jbyte*)addr;
    return arr[index];
}

JNIEXPORT jbyte JNICALL
Java_TestNative_getByteBuffer(JNIEnv *env, jclass cls, jobject buf, jint index) {
    jlong addr = (jlong)(*env)->GetDirectBufferAddress(env, buf);
    return Java_TestNative_getByteRaw(env, cls, addr, index);
}

JNIEXPORT jchar JNICALL
Java_TestNative_getCharRaw(JNIEnv *env, jclass cls, jlong addr, jint index) {
    jchar *arr = (jchar*)addr;
    return arr[index];
}

JNIEXPORT jchar JNICALL
Java_TestNative_getCharBuffer(JNIEnv *env, jclass cls, jobject buf, jint index) {
    jlong addr = (jlong)(*env)->GetDirectBufferAddress(env, buf);
    return Java_TestNative_getCharRaw(env, cls, addr, index);
}

JNIEXPORT jshort JNICALL
Java_TestNative_getShortRaw(JNIEnv *env, jclass cls, jlong addr, jint index) {
    jshort *arr = (jshort*)addr;
    return arr[index];
}

JNIEXPORT jshort JNICALL
Java_TestNative_getShortBuffer(JNIEnv *env, jclass cls, jobject buf, jint index) {
    jlong addr = (jlong)(*env)->GetDirectBufferAddress(env, buf);
    return Java_TestNative_getShortRaw(env, cls, addr, index);
}

JNIEXPORT jint JNICALL
Java_TestNative_getIntRaw(JNIEnv *env, jclass cls, jlong addr, jint index) {
    jint *arr = (jint*)addr;
    return arr[index];
}

JNIEXPORT jint JNICALL
Java_TestNative_getIntBuffer(JNIEnv *env, jclass cls, jobject buf, jint index) {
    jlong addr = (jlong)(*env)->GetDirectBufferAddress(env, buf);
    return Java_TestNative_getIntRaw(env, cls, addr, index);
}

JNIEXPORT jfloat JNICALL
Java_TestNative_getFloatRaw(JNIEnv *env, jclass cls, jlong addr, jint index) {
    jfloat *arr = (jfloat*)addr;
    return arr[index];
}

JNIEXPORT jfloat JNICALL
Java_TestNative_getFloatBuffer(JNIEnv *env, jclass cls, jobject buf, jint index) {
    jlong addr = (jlong)(*env)->GetDirectBufferAddress(env, buf);
    return Java_TestNative_getFloatRaw(env, cls, addr, index);
}

JNIEXPORT jlong JNICALL
Java_TestNative_getLongRaw(JNIEnv *env, jclass cls, jlong addr, jint index) {
    jlong *arr = (jlong*)addr;
    return arr[index];
}

JNIEXPORT jlong JNICALL
Java_TestNative_getLongBuffer(JNIEnv *env, jclass cls, jobject buf, jint index) {
    jlong addr = (jlong)(*env)->GetDirectBufferAddress(env, buf);
    return Java_TestNative_getLongRaw(env, cls, addr, index);
}

JNIEXPORT jdouble JNICALL
Java_TestNative_getDoubleRaw(JNIEnv *env, jclass cls, jlong addr, jint index) {
    jdouble *arr = (jdouble*)addr;
    return arr[index];
}

JNIEXPORT jdouble JNICALL
Java_TestNative_getDoubleBuffer(JNIEnv *env, jclass cls, jobject buf, jint index) {
    jlong addr = (jlong)(*env)->GetDirectBufferAddress(env, buf);
    return Java_TestNative_getDoubleRaw(env, cls, addr, index);
}

JNIEXPORT jlong JNICALL
Java_TestNative_getCapacity(JNIEnv *env, jclass cls, jobject buf) {
    return (*env)->GetDirectBufferCapacity(env, buf);
}
