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
Java_compiler_valhalla_valuetypes_TestCCMismatchNativeMethods_00024B1_m1(JNIEnv* env, jobject receiver, jobject vt1, jobject vt2) {
  jclass cls = (*env)->GetObjectClass(env, vt2);
  jfieldID fid = (*env)->GetFieldID(env, cls, "x", "I");
  return (*env)->GetIntField(env, vt2, fid);
}

JNIEXPORT jint JNICALL
Java_compiler_valhalla_valuetypes_TestCCMismatchNativeMethods_00024D2_m1(JNIEnv* env, jobject receiver, jobject vt1, jobject vt2) {
  jclass cls = (*env)->GetObjectClass(env, vt2);
  jfieldID fid = (*env)->GetFieldID(env, cls, "x", "I");
  return (*env)->GetIntField(env, vt2, fid);
}

