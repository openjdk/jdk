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

#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv* jvmti = nullptr;

JNIEXPORT jint JNICALL
Java_jvmti_JVMTIUtils_init(JNIEnv *jni, jclass cls) {
  JavaVM* jvm;
  jni->GetJavaVM(&jvm);

  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof (caps));
  caps.can_suspend = 1;
  caps.can_signal_thread = 1;
  jvmtiError err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI AddCapabilities: %d\n", err);
    return JNI_ERR;
  }
  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_jvmti_JVMTIUtils_stopThread(JNIEnv *jni, jclass cls, jthread thread, jobject exception) {
  jvmtiError err =  jvmti->StopThread(thread, exception);
  if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
    LOG("JVMTI_ERROR_THREAD_NOT_ALIVE happened\n");
    return;
  }
  check_jvmti_status(jni, err, "Error during StopThread()");
}

JNIEXPORT jint JNICALL
Java_jvmti_JVMTIUtils_suspendThread0(JNIEnv *jni, jclass cls, jthread thread) {
  return jvmti->SuspendThread(thread);
}

JNIEXPORT jint JNICALL
Java_jvmti_JVMTIUtils_resumeThread0(JNIEnv *jni, jclass cls, jthread thread) {
  return jvmti->ResumeThread(thread);
}

}
