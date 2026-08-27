/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

static jvmtiEnv *jvmti = nullptr;

JNIEXPORT jboolean JNICALL
Java_suspendthrd01_suspendTestedThread(JNIEnv *jni, jclass cls, jthread thread) {
  LOG("Suspend thread: %p\n", (void *) thread);
  jvmtiError err = jvmti->SuspendThread(thread);
  if (err != JVMTI_ERROR_NONE) {
    LOG("suspendTestedThread: SuspendThread failed: %s (%d)\n", TranslateError(err), err);
    return JNI_FALSE;
  }
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_suspendthrd01_checkSuspendedState(JNIEnv *jni, jclass cls, jthread thread) {
  jint state = get_thread_state(jvmti, jni, thread);
  LOG("Thread state: %s (%d)\n", TranslateState(state), (int) state);
  return (state & JVMTI_THREAD_STATE_SUSPENDED) != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_suspendthrd01_resumeTestedThread(JNIEnv *jni, jclass cls, jthread thread) {
  LOG("Resume thread: %p\n", (void *) thread);
  jvmtiError err = jvmti->ResumeThread(thread);
  if (err != JVMTI_ERROR_NONE) {
    LOG("resumeTestedThread: ResumeThread failed: %s (%d)\n", TranslateError(err), err);
    return JNI_FALSE;
  }
  return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_9);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }
  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_suspend = 1;
  if (jvmti->AddCapabilities(&caps) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  return JNI_OK;
}

}
