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

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  if (jvm->GetEnv((void**)&jvmti, JVMTI_VERSION) != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }
  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = 1;
  caps.can_suspend = 1;
  caps.can_get_owned_monitor_stack_depth_info = 1;
  if (jvmti->AddCapabilities(&caps) != JVMTI_ERROR_NONE) {
    LOG("AddCapabilities failed\n");
    return JNI_ERR;
  }
  return JNI_OK;
}

// Returns "<class>.<method>", with '.' between package names as in StackTraceElement.
static jstring frame_name(JNIEnv* jni, jmethodID method) {
  char* cname = get_method_class_name(jvmti, jni, method);
  char* mname = nullptr;
  check_jvmti_status(jni, jvmti->GetMethodName(method, &mname, nullptr, nullptr), "GetMethodName failed");
  char name[1024];
  snprintf(name, sizeof(name), "%s.%s", cname, mname);
  for (char* p = name; *p != '\0'; p++) {
    if (*p == '/') {
      *p = '.';
    }
  }
  deallocate(jvmti, jni, cname);
  deallocate(jvmti, jni, mname);
  return jni->NewStringUTF(name);
}

JNIEXPORT jobjectArray JNICALL
Java_YieldedVThreadStackTest_stackTrace(JNIEnv* jni, jclass cls, jthread thread) {
  jvmtiFrameInfo frames[MAX_FRAMES];
  jint count = 0;
  check_jvmti_status(jni, jvmti->GetStackTrace(thread, 0, MAX_FRAMES, frames, &count), "GetStackTrace failed");
  jobjectArray result = jni->NewObjectArray(count, jni->FindClass("java/lang/String"), nullptr);
  for (jint i = 0; i < count; i++) {
    jni->SetObjectArrayElement(result, i, frame_name(jni, frames[i].method));
  }
  return result;
}

JNIEXPORT jint JNICALL
Java_YieldedVThreadStackTest_frameCount(JNIEnv* jni, jclass cls, jthread thread) {
  jint count = 0;
  check_jvmti_status(jni, jvmti->GetFrameCount(thread, &count), "GetFrameCount failed");
  return count;
}

JNIEXPORT jstring JNICALL
Java_YieldedVThreadStackTest_frameAt(JNIEnv* jni, jclass cls, jthread thread, jint depth) {
  jmethodID method = nullptr;
  jlocation location = 0;
  check_jvmti_status(jni, jvmti->GetFrameLocation(thread, depth, &method, &location), "GetFrameLocation failed");
  return frame_name(jni, method);
}

JNIEXPORT jint JNICALL
Java_YieldedVThreadStackTest_monitorDepth(JNIEnv* jni, jclass cls, jthread thread, jobject monitor) {
  jint count = 0;
  jvmtiMonitorStackDepthInfo* info = nullptr;
  check_jvmti_status(jni, jvmti->GetOwnedMonitorStackDepthInfo(thread, &count, &info),
                     "GetOwnedMonitorStackDepthInfo failed");
  jint depth = -1;
  for (jint i = 0; i < count; i++) {
    if (jni->IsSameObject(info[i].monitor, monitor)) {
      depth = info[i].stack_depth;
    }
  }
  deallocate(jvmti, jni, info);
  return depth;
}

JNIEXPORT void JNICALL
Java_YieldedVThreadStackTest_suspend(JNIEnv* jni, jclass cls, jthread thread) {
  check_jvmti_status(jni, jvmti->SuspendThread(thread), "SuspendThread failed");
}

JNIEXPORT void JNICALL
Java_YieldedVThreadStackTest_resume(JNIEnv* jni, jclass cls, jthread thread) {
  check_jvmti_status(jni, jvmti->ResumeThread(thread), "ResumeThread failed");
}

}
