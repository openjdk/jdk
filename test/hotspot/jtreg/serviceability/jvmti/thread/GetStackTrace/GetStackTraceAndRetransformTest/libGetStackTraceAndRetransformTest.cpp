/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Datadog, Inc. All rights reserved.
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
#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"
#include "../get_stack_trace.hpp"


extern "C" {

static jvmtiEnv *jvmti = nullptr;
static jmethodID* ids = nullptr;
static int ids_size = 0;

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    printf("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }
  ids = (jmethodID*)malloc(sizeof(jmethodID) * 10);
  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_GetStackTraceAndRetransformTest_initialize(JNIEnv *env, jclass cls, jclass tgt) {
  // we need to force jmethodids to be created for the methods we are going to retransform
  env->GetStaticMethodID(tgt, "redefineAndStacktrace", "()V");
  env->GetStaticMethodID(tgt, "stacktrace", "()V");
}

JNIEXPORT void JNICALL
Java_Transformable_capture(JNIEnv *env, jclass cls, jthread thread) {
  jint count;
  const int MAX_NUMBER_OF_FRAMES = 32;
  jvmtiFrameInfo frames[MAX_NUMBER_OF_FRAMES];

  jvmtiError err = jvmti->GetStackTrace(thread, 0, MAX_NUMBER_OF_FRAMES, frames, &count);
  check_jvmti_status(env, err, "GetStackTrace failed.");

  ids[ids_size++] = frames[1].method;
}

JNIEXPORT void JNICALL
Java_GetStackTraceAndRetransformTest_check(JNIEnv *jni, jclass cls, jint expected) {
  if (ids_size != expected) {
    fprintf(stderr, "Unexpected number methods captured: %d (expected %d)\n", ids_size, expected);
    exit(2);
  }
  for (int i = 0; i < ids_size; i++) {
    jclass rslt = nullptr;
    char* class_name = nullptr;
    jvmti->GetMethodDeclaringClass(ids[i], &rslt);
    if (rslt != nullptr) {
        jvmti->GetClassSignature(rslt, &class_name, nullptr);
    }
  }
}
}
