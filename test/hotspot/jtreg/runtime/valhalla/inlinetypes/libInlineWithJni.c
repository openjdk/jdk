/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

JNIEXPORT void JNICALL
Java_runtime_valhalla_inlinetypes_InlineWithJni_doJniMonitorEnter(JNIEnv *env, jobject obj) {
    int ret = (*env)->MonitorEnter(env, obj);
    jclass class = (*env)->GetObjectClass(env, obj);
    jfieldID fieldId = (*env)->GetStaticFieldID(env, class, "returnValue", "I");
    (*env)->SetStaticIntField(env, class, fieldId, ret);
}

JNIEXPORT void JNICALL
Java_runtime_valhalla_inlinetypes_InlineWithJni_doJniMonitorExit(JNIEnv *env, jobject obj) {
    (*env)->MonitorExit(env, obj);
}

JNIEXPORT jobject JNICALL Java_runtime_valhalla_inlinetypes_InlineWithJni_readInstanceField(JNIEnv *env,
                       jclass k, jobject obj, jstring name, jstring signature) {
    jclass class = (*env)->GetObjectClass(env, obj);
    jboolean copy;
    const char* name_string = (*env)->GetStringUTFChars(env, name, &copy);
    const char *signature_string = (*env)->GetStringUTFChars(env, signature, &copy);
    jfieldID fieldId = (*env)->GetFieldID(env, class, name_string, signature_string);
    jobject ret =  (*env)->GetObjectField(env, obj, fieldId);
    (*env)->ReleaseStringUTFChars(env, name, name_string);
    (*env)->ReleaseStringUTFChars(env, signature, signature_string);
    return ret;
}

JNIEXPORT void JNICALL Java_runtime_valhalla_inlinetypes_InlineWithJni_writeInstanceField(JNIEnv *env,
                        jclass k, jobject obj, jstring name, jstring signature, jobject value)
{
    jclass class = (*env)->GetObjectClass(env, obj);
    jboolean copy;
    const char *name_string = (*env)->GetStringUTFChars(env, name, &copy);
    const char *signature_string = (*env)->GetStringUTFChars(env, signature, &copy);
    jfieldID fieldId = (*env)->GetFieldID(env, class, name_string, signature_string);
    (*env)->SetObjectField(env, obj, fieldId, value);
    (*env)->ReleaseStringUTFChars(env, name, name_string);
    (*env)->ReleaseStringUTFChars(env, signature, signature_string);
    return;
}

JNIEXPORT jobject JNICALL Java_runtime_valhalla_inlinetypes_InlineWithJni_readArrayElement(JNIEnv *env,
                        jclass k, jarray array, int index) {
    return (*env)->GetObjectArrayElement(env, array, index);
}

JNIEXPORT void JNICALL Java_runtime_valhalla_inlinetypes_InlineWithJni_writeArrayElement(JNIEnv *env,
                        jclass k, jarray array, int index, jobject value) {
    (*env)->SetObjectArrayElement(env, array, index, value);
}