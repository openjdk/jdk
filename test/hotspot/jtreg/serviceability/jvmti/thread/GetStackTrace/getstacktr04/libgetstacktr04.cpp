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
static jmethodID mid;
static frame_info expected_platform_frames[] = {
    {"Lgetstacktr04$TestThread;", "checkPoint", "()V"},
    {"Lgetstacktr04$TestThread;", "chain4", "()V"},
    {"Lgetstacktr04$TestThread;", "chain3", "()V"},
    {"Lgetstacktr04$TestThread;", "chain2", "()V"},
    {"Lgetstacktr04$TestThread;", "chain1", "()V"},
    {"Lgetstacktr04$TestThread;", "run", "()V"},
    {"Ljava/lang/Thread;", "runWith", "(Ljava/lang/Object;Ljava/lang/Runnable;)V"},
    {"Ljava/lang/Thread;", "run", "()V"},
};

static frame_info expected_virtual_frames[] = {
    {"Lgetstacktr04$TestThread;", "checkPoint", "()V"},
    {"Lgetstacktr04$TestThread;", "chain4", "()V"},
    {"Lgetstacktr04$TestThread;", "chain3", "()V"},
    {"Lgetstacktr04$TestThread;", "chain2", "()V"},
    {"Lgetstacktr04$TestThread;", "chain1", "()V"},
    {"Lgetstacktr04$TestThread;", "run", "()V"},
    {"Ljava/lang/VirtualThread;", "runWith", "(Ljava/lang/Object;Ljava/lang/Runnable;)V"},
    {"Ljava/lang/VirtualThread;", "run", "(Ljava/lang/Runnable;)V"},
    {"Ljava/lang/VirtualThread$VThreadContinuation;", "lambda$new$0", "(Ljava/lang/VirtualThread;Ljava/lang/Runnable;)V"},
    {"Ljava/lang/VirtualThread$VThreadContinuation$$Lambda;", "run", "()V"},
    {"Ljdk/internal/vm/Continuation;", "enter0", "()V"},
    {"Ljdk/internal/vm/Continuation;", "enter", "(Ljdk/internal/vm/Continuation;Z)V"},

};


void JNICALL Breakpoint(jvmtiEnv *jvmti_env, JNIEnv *jni,
                        jthread thread, jmethodID method, jlocation location) {
  frame_info *expected_frames = jni->IsVirtualThread(thread)
      ? expected_virtual_frames
      : expected_platform_frames;
  int expected_number_of_stack_frames = jni->IsVirtualThread(thread)
      ? ((int) (sizeof(expected_virtual_frames) / sizeof(frame_info)))
      : ((int) (sizeof(expected_platform_frames) / sizeof(frame_info)));

  if (mid != method) {
    jni->FatalError("ERROR: didn't know where we got called from");
  }
  if (!compare_stack_trace(jvmti_env, jni, thread, expected_frames, expected_number_of_stack_frames)) {
    jni->ThrowNew(jni->FindClass("java/lang/RuntimeException"), "Stacktrace differs from expected.");
  }
}

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
  caps.can_generate_breakpoint_events = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
   LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  jvmtiEventCallbacks callbacks;
  callbacks.Breakpoint = &Breakpoint;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_getstacktr04_getReady(JNIEnv *jni, jclass cls, jclass clazz) {
  mid = jni->GetMethodID(clazz, "checkPoint", "()V");
  if (mid == nullptr) {
    jni->FatalError("Cannot find Method ID for method checkPoint\n");
  }

  check_jvmti_status(jni, jvmti->SetBreakpoint(mid, 0), "SetBreakpoint failed.");
  set_event_notification_mode(jvmti, jni, JVMTI_ENABLE,JVMTI_EVENT_BREAKPOINT, nullptr);
}

}
