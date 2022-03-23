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

#include <jni.h>

#include <stdlib.h>

#include "jlong.h"
#include "JNICB.h"

#ifdef _WIN64
#define THREAD_LOCAL __declspec(thread)
#else
#define THREAD_LOCAL __thread
#endif

THREAD_LOCAL struct {
  JNICB cb;
  JNIEnv* env;
} ctx_opt;

static int comparator(const void* e0, const void* e1) {
    JNICB jniCb = ctx_opt.cb;
    JNIEnv* env = ctx_opt.env;
    jint j0 = *((jint*) e0);
    jint j1 = *((jint*) e1);
    return (*env)->CallStaticIntMethod(env, jniCb->holder, jniCb->mid, j0, j1);
}

JNIEXPORT void JNICALL Java_org_openjdk_bench_jdk_incubator_foreign_QSort_jni_1qsort_1optimized
        (JNIEnv *env, jclass cls, jintArray arr, jlong cb) {

    ctx_opt.cb = jlong_to_ptr(cb);
    ctx_opt.env = env;

    jint* ints = (*env)->GetIntArrayElements(env, arr, NULL);
    jsize length = (*env)->GetArrayLength(env, arr);

    qsort(ints, length, sizeof(jint), &comparator);

    (*env)->ReleaseIntArrayElements(env, arr, ints, 0);
}

JavaVM* VM = NULL;

int java_cmp(const void *a, const void *b) {
   int v1 = *((int*)a);
   int v2 = *((int*)b);

   JNIEnv* env;
   (*VM)->GetEnv(VM, (void**) &env, JNI_VERSION_10);

   jclass qsortClass = (*env)->FindClass(env, "org/openjdk/bench/jdk/incubator/foreign/QSort");
   jmethodID methodId = (*env)->GetStaticMethodID(env, qsortClass, "jni_upcall_compar", "(II)I");

   return (*env)->CallStaticIntMethod(env, qsortClass, methodId, v1, v2);
}

JNIEXPORT void JNICALL Java_org_openjdk_bench_jdk_incubator_foreign_QSort_jni_1qsort_1naive
        (JNIEnv *env, jclass cls, jintArray arr) {
    if (VM == NULL) {
        (*env)->GetJavaVM(env, &VM);
    }

    jint* carr = (*env)->GetIntArrayElements(env, arr, 0);
    jsize length = (*env)->GetArrayLength(env, arr);
    qsort(carr, length, sizeof(jint), java_cmp);
    (*env)->ReleaseIntArrayElements(env, arr, carr, 0);
}
