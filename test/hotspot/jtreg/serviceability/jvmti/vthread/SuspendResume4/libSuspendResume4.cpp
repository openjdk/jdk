/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
#include <jvmti.h>
#include <stdio.h>
#include <string.h>
#include "jvmti_common.hpp"

// set by Agent_OnLoad
static jvmtiEnv* jvmti = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_SuspendResume4_suspendThread(JNIEnv* jni, jclass klass, jthread thread) {
  jvmtiError err = jvmti->SuspendThread(thread);
  if (err != JVMTI_ERROR_NONE && err != JVMTI_ERROR_THREAD_NOT_ALIVE) {
    jni->FatalError("error in JVMTI SuspendThread");
  }
}

JNIEXPORT void JNICALL
Java_SuspendResume4_resumeThread(JNIEnv* jni, jclass klass, jthread thread) {
  jvmtiError err = jvmti->ResumeThread(thread);
  if (err != JVMTI_ERROR_NONE && err != JVMTI_ERROR_THREAD_NOT_ALIVE) {
    jni->FatalError("error in JVMTI ResumeThread");
  }
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;

  printf("Agent_OnLoad: started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    LOG("Agent_OnLoad: error in GetEnv");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_suspend = 1;
  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: error in JVMTI AddCapabilities: %d\n", err);
  }

  printf("Agent_OnLoad: finished\n");

  return 0;
}

} // extern "C"
