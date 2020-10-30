/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include <stdlib.h>

#include "points.h"

#ifdef _LP64
#define JLONG_TO_PTR(value, type) ((type*) (value))
#define PTR_TO_JLONG(value) ((jlong) (value))
#else
#define JLONG_TO_PTR(value, type) ((type*) (jint) (value))
#define PTR_TO_JLONG(value) ((jlong) (jint) (value))
#endif

JNIEXPORT jlong JNICALL Java_org_openjdk_bench_jdk_incubator_foreign_points_support_JNIPoint_allocate
  (JNIEnv *env, jclass nativePointClass) {
    Point* p = malloc(sizeof *p);
    return PTR_TO_JLONG(p);
}

JNIEXPORT void JNICALL Java_org_openjdk_bench_jdk_incubator_foreign_points_support_JNIPoint_free
  (JNIEnv *env, jclass cls, jlong thisPoint) {
    free(JLONG_TO_PTR(thisPoint, Point));
}

JNIEXPORT jint JNICALL Java_org_openjdk_bench_jdk_incubator_foreign_points_support_JNIPoint_getX
  (JNIEnv *env, jclass cls, jlong thisPoint) {
    Point* point = JLONG_TO_PTR(thisPoint, Point);
    return point->x;
}

JNIEXPORT void JNICALL Java_org_openjdk_bench_jdk_incubator_foreign_points_support_JNIPoint_setX
  (JNIEnv *env, jclass cls, jlong thisPoint, jint value) {
    Point* point = JLONG_TO_PTR(thisPoint, Point);
    point->x = value;
}

JNIEXPORT jint JNICALL Java_org_openjdk_bench_jdk_incubator_foreign_points_support_JNIPoint_getY
  (JNIEnv *env, jclass cls, jlong thisPoint) {
    Point* point = JLONG_TO_PTR(thisPoint, Point);
    return point->y;
}

JNIEXPORT void JNICALL Java_org_openjdk_bench_jdk_incubator_foreign_points_support_JNIPoint_setY
  (JNIEnv *env, jclass cls, jlong thisPoint, jint value) {
    Point* point = JLONG_TO_PTR(thisPoint, Point);
    point->y = value;
}
