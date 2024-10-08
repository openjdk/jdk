/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
static frame_info expected_platform_frames[] = {
    {"Ljava/lang/Object;", "wait", "()V"},
    {"Lgetstacktr03;", "dummy", "()V"},
    {"Lgetstacktr03;", "chain", "()V"},
    {"Lgetstacktr03$Task;", "run", "()V"},
    {"Ljava/lang/Thread;", "runWith", "(Ljava/lang/Object;Ljava/lang/Runnable;)V"},
    {"Ljava/lang/Thread;", "run", "()V"},
};

static frame_info expected_virtual_frames[] = {
    {"Ljava/lang/Object;", "wait", "()V"},
    {"Lgetstacktr03;", "dummy", "()V"},
    {"Lgetstacktr03;", "chain", "()V"},
    {"Lgetstacktr03$Task;", "run", "()V"},
    {"Ljava/lang/Thread;", "runWith", "(Ljava/lang/Object;Ljava/lang/Runnable;)V"},
    {"Ljava/lang/VirtualThread;", "run", "(Ljava/lang/Runnable;)V"},
    {"Ljava/lang/VirtualThread$VThreadContinuation$1;", "run", "()V"},
    {"Ljdk/internal/vm/Continuation;", "enter0", "()V"},
    {"Ljdk/internal/vm/Continuation;", "enter", "(Ljdk/internal/vm/Continuation;Z)V"}
};

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiError err;
  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_suspend = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
   LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_getstacktr03_chain(JNIEnv *env, jclass cls) {
  jmethodID mid = env->GetStaticMethodID(cls, "dummy", "()V");
  env->CallStaticVoidMethod(cls, mid);
}

JNIEXPORT int JNICALL
Java_getstacktr03_check(JNIEnv *jni, jclass cls, jthread thread) {
  suspend_thread(jvmti, jni, thread);
  frame_info *expected_frames = jni->IsVirtualThread(thread)
      ? expected_virtual_frames
      : expected_platform_frames;
  int expected_number_of_stack_frames = jni->IsVirtualThread(thread)
      ? ((int) (sizeof(expected_virtual_frames) / sizeof(frame_info)))
      : ((int) (sizeof(expected_platform_frames) / sizeof(frame_info)));

  if (!compare_stack_trace(jvmti, jni, thread, expected_frames, expected_number_of_stack_frames)) {
    jni->ThrowNew(jni->FindClass("java/lang/RuntimeException"), "Stacktrace differs from expected.");
  }
  resume_thread(jvmti, jni, thread);
  return 0;
}

}
