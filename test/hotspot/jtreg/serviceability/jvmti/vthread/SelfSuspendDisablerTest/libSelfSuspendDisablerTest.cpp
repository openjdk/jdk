/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>
#include "jvmti.h"
#include "jvmti_common.h"

static jvmtiEnv *jvmti = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_SelfSuspendDisablerTest_resume(JNIEnv* jni, jclass cls, jthread thread) {
  check_jvmti_status(jni, jvmti->ResumeThread(thread), "Error in ResumeThread");
}

JNIEXPORT void JNICALL
Java_SelfSuspendDisablerTest_selfSuspend(JNIEnv* jni, jclass cls) {
  jthread thread;
  check_jvmti_status(jni, jvmti->GetCurrentThread(&thread), "Error in CurrentThread");
  check_jvmti_status(jni, jvmti->SuspendThread(thread), "Error in SuspendThread");
}

JNIEXPORT jboolean JNICALL
Java_SelfSuspendDisablerTest_isSuspended(JNIEnv* jni, jclass cls, jthread thread) {
  jint state;
  check_jvmti_status(jni, jvmti->GetThreadState(thread, &state), "Error in GetThreadState");
  return (state & JVMTI_THREAD_STATE_SUSPENDED) != 0;
}

JNIEXPORT void JNICALL
Java_SelfSuspendDisablerTest_suspendAllVirtualThreads(JNIEnv* jni, jclass cls) {
  check_jvmti_status(jni, jvmti->SuspendAllVirtualThreads(0, nullptr), "Error in SuspendAllVirtualThreads");
}

JNIEXPORT void JNICALL
Java_SelfSuspendDisablerTest_resumeAllVirtualThreads(JNIEnv* jni, jclass cls) {
  check_jvmti_status(jni, jvmti->ResumeAllVirtualThreads(0, nullptr), "Error in ResumeAllVirtualThreads");
}

JNIEXPORT jint JNICALL
Java_SelfSuspendDisablerTest_getThreadState(JNIEnv* jni, jclass cls, jthread thread) {
  jint state;
  check_jvmti_status(jni, jvmti->GetThreadState(thread, &state), "Error in GetThreadState");
  return state;
}

} // extern "C"


JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiError err;

  LOG("Agent init started\n");

  if (jvm->GetEnv((void **)(&jvmti), JVMTI_VERSION) != JNI_OK) {
    LOG("Agent init: error in getting JvmtiEnv with GetEnv\n");
    return JNI_ERR;
  }

  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_suspend = 1;
  caps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  LOG("Agent init finished\n");
  return JNI_OK;
}
