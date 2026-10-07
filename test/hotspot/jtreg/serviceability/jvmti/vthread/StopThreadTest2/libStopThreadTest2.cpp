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
Java_StopThreadTest2_suspendAllVirtualThreads(JNIEnv* jni, jclass cls) {
  check_jvmti_status(jni, jvmti->SuspendAllVirtualThreads(0, nullptr), "Error in SuspendAllVirtualThreads");
}

JNIEXPORT void JNICALL
Java_StopThreadTest2_resumeAllVirtualThreads(JNIEnv* jni, jclass cls) {
  check_jvmti_status(jni, jvmti->ResumeAllVirtualThreads(0, nullptr), "Error in ResumeAllVirtualThreads");
}

JNIEXPORT jboolean JNICALL
Java_StopThreadTest2_stopThread(JNIEnv* jni, jclass cls, jthread thread, jobject exception, jboolean allowNotAlive) {
  jvmtiError err = jvmti->StopThread(thread, exception);
  // The target might be suspended at a VirtualThread method
  // so we ignore JVMTI_ERROR_OPAQUE_FRAME.
  if (err == JVMTI_ERROR_OPAQUE_FRAME || (allowNotAlive && err == JVMTI_ERROR_THREAD_NOT_ALIVE)) {
    return false;
  }
  check_jvmti_status(jni, err, "Error during StopThread()");
  return true;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;

  LOG("Agent_OnLoad: started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    LOG("Agent_OnLoad: error in GetEnv");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_suspend = 1;
  caps.can_signal_thread = 1;
  caps.can_support_virtual_threads = 1;
  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: error in JVMTI AddCapabilities: %d\n", err);
    return JNI_ERR;
  }

  LOG("Agent_OnLoad: finished\n");

  return 0;
}

} // extern "C"
