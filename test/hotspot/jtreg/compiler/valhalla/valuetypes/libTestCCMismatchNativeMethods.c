/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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

JNIEXPORT jint JNICALL
Java_compiler_valhalla_valuetypes_B1_m1(JNIEnv* env, jobject receiver, jobject vt1, jobject vt2) {
  jclass cls = (*env)->GetObjectClass(env, vt2);
  jfieldID fid0 = (*env)->GetFieldID(env, cls, "x0", "I");
  jfieldID fid1 = (*env)->GetFieldID(env, cls, "x1", "I");
  jfieldID fid2 = (*env)->GetFieldID(env, cls, "x2", "I");
  jfieldID fid3 = (*env)->GetFieldID(env, cls, "x3", "I");
  jfieldID fid4 = (*env)->GetFieldID(env, cls, "x4", "I");
  jfieldID fid5 = (*env)->GetFieldID(env, cls, "x5", "I");
  return (*env)->GetIntField(env, vt2, fid0) +
    (*env)->GetIntField(env, vt2, fid1) +
    (*env)->GetIntField(env, vt2, fid2) +
    (*env)->GetIntField(env, vt2, fid3) +
    (*env)->GetIntField(env, vt2, fid4) +
    (*env)->GetIntField(env, vt2, fid5);
}

JNIEXPORT jobject JNICALL
Java_compiler_valhalla_valuetypes_B1_m2(JNIEnv* env, jobject receiver) {
  jclass cls = (*env)->GetObjectClass(env, receiver);
  jfieldID fid = (*env)->GetStaticFieldID(env, cls, "fieldV1", "Lcompiler/valhalla/valuetypes/V1;");
  return (*env)->GetStaticObjectField(env, cls, fid);
}

JNIEXPORT jint JNICALL
Java_compiler_valhalla_valuetypes_D2_m1(JNIEnv* env, jobject receiver, jobject vt1, jobject vt2) {
  jclass cls = (*env)->GetObjectClass(env, vt2);
  jfieldID fid0 = (*env)->GetFieldID(env, cls, "x0", "I");
  jfieldID fid1 = (*env)->GetFieldID(env, cls, "x1", "I");
  jfieldID fid2 = (*env)->GetFieldID(env, cls, "x2", "I");
  jfieldID fid3 = (*env)->GetFieldID(env, cls, "x3", "I");
  jfieldID fid4 = (*env)->GetFieldID(env, cls, "x4", "I");
  jfieldID fid5 = (*env)->GetFieldID(env, cls, "x5", "I");
  return (*env)->GetIntField(env, vt2, fid0) +
    (*env)->GetIntField(env, vt2, fid1) +
    (*env)->GetIntField(env, vt2, fid2) +
    (*env)->GetIntField(env, vt2, fid3) +
    (*env)->GetIntField(env, vt2, fid4) +
    (*env)->GetIntField(env, vt2, fid5);
}

JNIEXPORT jobject JNICALL
Java_compiler_valhalla_valuetypes_D2_m2(JNIEnv* env, jobject receiver) {
  jclass cls = (*env)->GetObjectClass(env, receiver);
  jfieldID fid = (*env)->GetStaticFieldID(env, cls, "fieldV1", "Lcompiler/valhalla/valuetypes/V1;");
  return (*env)->GetStaticObjectField(env, cls, fid);
}

JNIEXPORT jint JNICALL
Java_compiler_valhalla_valuetypes_V1_add(JNIEnv* env, jobject receiver, jobject vt1) {
  jclass cls = (*env)->GetObjectClass(env, receiver);
  jfieldID fid0 = (*env)->GetFieldID(env, cls, "x0", "I");
  jfieldID fid1 = (*env)->GetFieldID(env, cls, "x1", "I");
  jfieldID fid2 = (*env)->GetFieldID(env, cls, "x2", "I");
  jfieldID fid3 = (*env)->GetFieldID(env, cls, "x3", "I");
  jfieldID fid4 = (*env)->GetFieldID(env, cls, "x4", "I");
  jfieldID fid5 = (*env)->GetFieldID(env, cls, "x5", "I");
  return (*env)->GetIntField(env, receiver, fid0) +
    (*env)->GetIntField(env, receiver, fid1) +
    (*env)->GetIntField(env, receiver, fid2) +
    (*env)->GetIntField(env, receiver, fid3) +
    (*env)->GetIntField(env, receiver, fid4) +
    (*env)->GetIntField(env, receiver, fid5) +
    (*env)->GetIntField(env, vt1, fid0) +
    (*env)->GetIntField(env, vt1, fid1) +
    (*env)->GetIntField(env, vt1, fid2) +
    (*env)->GetIntField(env, vt1, fid3) +
    (*env)->GetIntField(env, vt1, fid4) +
    (*env)->GetIntField(env, vt1, fid5);
}
