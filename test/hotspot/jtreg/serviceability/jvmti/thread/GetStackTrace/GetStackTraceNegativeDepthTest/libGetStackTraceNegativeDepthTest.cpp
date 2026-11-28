/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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

extern "C" {

static const int MAX_FRAMES = 1024;
static jvmtiEnv* jvmti = nullptr;
static jvmtiError last_error = JVMTI_ERROR_NONE;

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  if (jvm->GetEnv((void**)&jvmti, JVMTI_VERSION) != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }
  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = 1;
  if (jvmti->AddCapabilities(&caps) != JVMTI_ERROR_NONE) {
    LOG("AddCapabilities failed\n");
    return JNI_ERR;
  }
  return JNI_OK;
}

JNIEXPORT jobjectArray JNICALL
Java_GetStackTraceNegativeDepthTest_stackTrace(JNIEnv* jni, jclass cls, jthread thread,
                                               jint start_depth, jint max_count) {
  jvmtiFrameInfo frames[MAX_FRAMES];
  jint count = 0;
  last_error = jvmti->GetStackTrace(thread, start_depth, max_count < MAX_FRAMES ? max_count : MAX_FRAMES,
                                    frames, &count);
  if (last_error != JVMTI_ERROR_NONE) {
    return nullptr;
  }
  jobjectArray result = jni->NewObjectArray(count, jni->FindClass("java/lang/String"), nullptr);
  for (jint i = 0; i < count; i++) {
    char* cname = get_method_class_name(jvmti, jni, frames[i].method);
    char* mname = nullptr;
    check_jvmti_status(jni, jvmti->GetMethodName(frames[i].method, &mname, nullptr, nullptr),
                       "GetMethodName failed");
    char name[1024];
    snprintf(name, sizeof(name), "%s.%s@%ld", cname, mname, (long)frames[i].location);
    jni->SetObjectArrayElement(result, i, jni->NewStringUTF(name));
    deallocate(jvmti, jni, cname);
    deallocate(jvmti, jni, mname);
  }
  return result;
}

JNIEXPORT jint JNICALL
Java_GetStackTraceNegativeDepthTest_lastError(JNIEnv* jni, jclass cls) {
  return last_error;
}

}
