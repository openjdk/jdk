/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <cstdlib>
#include <cstring>
#include <jvmti.h>
#include "jvmti_common.hpp"

extern "C" {

const int MAX_COUNT = 50;
static jvmtiEnv *jvmti;

JNIEXPORT jobjectArray JNICALL
Java_VirtualStackTraceTest_getStackTrace(JNIEnv* jni, jclass clazz) {
  jvmtiError err;
  jint count = 0;
  jint skipped = 0;

  jobject visibleFrames[MAX_COUNT];
  jvmtiFrameInfo frameInfo[MAX_COUNT];

  err = jvmti->GetStackTrace(nullptr, 0, MAX_COUNT, frameInfo, &count);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetStackTrace call");

  for (int idx = 0; idx < count; idx++) {
    jclass declaringClass = nullptr;
    char *clasSignature = nullptr;
    char *methodName = nullptr;

    err = jvmti->GetMethodDeclaringClass(frameInfo[idx].method, &declaringClass);
    check_jvmti_status(jni, err, "event handler: error in JVMTI GetMethodDeclaringClass call");

    err = jvmti->GetClassSignature(declaringClass, &clasSignature, nullptr);
    check_jvmti_status(jni, err, "event handler: error in JVMTI GetClassSignature call");

    err = jvmti->GetMethodName(frameInfo[idx].method, &methodName, nullptr, nullptr);
    check_jvmti_status(jni, err, "event handler: error in JVMTI GetMethodName call");

    if (strchr(clasSignature, '.')) {
      skipped++;
      continue;
    }
    visibleFrames[idx - skipped] = jni->NewStringUTF(methodName);

    jvmti->Deallocate(reinterpret_cast<unsigned char*>(methodName));
    jvmti->Deallocate(reinterpret_cast<unsigned char*>(clasSignature));
  }
  jobjectArray methodNames = jni->NewObjectArray(count - skipped, jni->FindClass("java/lang/String"), nullptr);
  for (int idx = 0; idx < count - skipped; idx++) {
    jni->SetObjectArrayElement(methodNames, idx, visibleFrames[idx]);
  }
  print_stack_trace(jvmti, jni, nullptr);

  return methodNames;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  LOG("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  return JNI_OK;
}

JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  LOG("Agent_OnAttach started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  return JNI_OK;
}

} // extern "C"
