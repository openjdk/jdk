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
static jvmtiEventCallbacks callbacks;
static jboolean wasFramePop = JNI_FALSE;
static jmethodID mid_checkPoint, mid_chain4;
static jbyteArray classBytes;
static frame_info expected_platform_frames[] = {
    {"Lgetstacktr08$TestThread;", "checkPoint", "()V"},
    {"Lgetstacktr08$TestThread;", "chain5", "()V"},
    {"Lgetstacktr08$TestThread;", "chain4", "()V"},
    {"Lgetstacktr08;", "nativeChain", "(Ljava/lang/Class;)V"},
    {"Lgetstacktr08$TestThread;", "chain3", "()V"},
    {"Lgetstacktr08$TestThread;", "chain2", "()V"},
    {"Lgetstacktr08$TestThread;", "chain1", "()V"},
    {"Lgetstacktr08$TestThread;", "run", "()V"},
    {"Ljava/lang/Thread;", "runWith", "(Ljava/lang/Object;Ljava/lang/Runnable;)V"},
    {"Ljava/lang/Thread;", "run", "()V"},
};

static frame_info expected_virtual_frames[] = {
    {"Lgetstacktr08$TestThread;", "checkPoint", "()V"},
    {"Lgetstacktr08$TestThread;", "chain5", "()V"},
    {"Lgetstacktr08$TestThread;", "chain4", "()V"},
    {"Lgetstacktr08;", "nativeChain", "(Ljava/lang/Class;)V"},
    {"Lgetstacktr08$TestThread;", "chain3", "()V"},
    {"Lgetstacktr08$TestThread;", "chain2", "()V"},
    {"Lgetstacktr08$TestThread;", "chain1", "()V"},
    {"Lgetstacktr08$TestThread;", "run", "()V"},
    {"Ljava/lang/VirtualThread;", "runWith", "(Ljava/lang/Object;Ljava/lang/Runnable;)V"},
    {"Ljava/lang/VirtualThread;", "run", "(Ljava/lang/Runnable;)V"},
    {"Ljava/lang/VirtualThread$VThreadContinuation;", "lambda$new$0", "(Ljava/lang/VirtualThread;Ljava/lang/Runnable;)V"},
    {"Ljava/lang/VirtualThread$VThreadContinuation$$Lambda;", "run", "()V"},
    {"Ljdk/internal/vm/Continuation;", "enter0", "()V"},
    {"Ljdk/internal/vm/Continuation;", "enter", "(Ljdk/internal/vm/Continuation;Z)V"},
};

int compare_stack_trace(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, int offset = 0) {
  frame_info *expected_frames = jni->IsVirtualThread(thread)
      ? expected_virtual_frames
      : expected_platform_frames;
  int expected_number_of_stack_frames = jni->IsVirtualThread(thread)
      ? ((int) (sizeof(expected_virtual_frames) / sizeof(frame_info)))
      : ((int) (sizeof(expected_platform_frames) / sizeof(frame_info)));
  return compare_stack_trace(jvmti, jni, thread, expected_frames, expected_number_of_stack_frames, offset);
}

void JNICALL Breakpoint(jvmtiEnv *jvmti_env, JNIEnv *jni, jthread thread, jmethodID method, jlocation location) {
  if (mid_checkPoint != method) {
    jni->FatalError("ERROR: don't know where we get called from");
  }
  check_jvmti_status(jni, jvmti_env->ClearBreakpoint(mid_checkPoint, 0), "ClearBreakpoint failed.");

  if (!compare_stack_trace(jvmti_env, jni,  thread)) {
    jni->ThrowNew(jni->FindClass("java/lang/RuntimeException"), "Stacktrace differs from expected.");
    return;
  }

  set_event_notification_mode(jvmti_env, jni, JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, thread);
  LOG(">>> stepping ...\n");
}

void JNICALL SingleStep(jvmtiEnv *jvmti_env, JNIEnv *jni,
                        jthread thread, jmethodID method, jlocation location) {
  jclass klass;
  jvmtiClassDefinition classDef;
  LOG(">>> In SingleStep ...\n");
  print_stack_trace(jvmti_env, jni, thread);

  if (wasFramePop == JNI_FALSE) {

    if (!compare_stack_trace(jvmti_env, jni, thread, 1)) {
      // Disable single-stepping to don't cause stackoverflow
      set_event_notification_mode(jvmti_env, jni, JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, thread);
      jni->ThrowNew(jni->FindClass("java/lang/RuntimeException"), "Stacktrace differs from expected.");
    }

    LOG(">>> popping frame ...\n");

    check_jvmti_status(jni, jvmti_env->PopFrame(thread), "PopFrame failed.");
    wasFramePop = JNI_TRUE;
  } else {
    set_event_notification_mode(jvmti_env, jni, JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, thread);
    if (!compare_stack_trace(jvmti_env, jni, thread, 2)) {
      jni->ThrowNew(jni->FindClass("java/lang/RuntimeException"), "Stacktrace differs from expected.");
    }


    if (classBytes == nullptr) {
      jni->FatalError("ERROR: don't have any bytes");
    }

    check_jvmti_status(jni, jvmti_env->GetMethodDeclaringClass(method, &klass), "GetMethodDeclaringClass failed.");
    LOG(">>> redefining class ...\n");

    classDef.klass = klass;
    classDef.class_byte_count = jni->GetArrayLength(classBytes);
    classDef.class_bytes = (unsigned char *) jni->GetByteArrayElements(classBytes, nullptr);
    check_jvmti_status(jni, jvmti_env->RedefineClasses(1, &classDef), "RedefineClasses failed.");

    jni->DeleteGlobalRef(classBytes);
    classBytes = nullptr;
    if (!compare_stack_trace(jvmti_env, jni, thread, 2)) {
      jni->ThrowNew(jni->FindClass("java/lang/RuntimeException"), "Stacktrace differs from expected.");
    }
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
  caps.can_generate_single_step_events = 1;
  caps.can_pop_frame = 1;
  caps.can_redefine_classes = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
   LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  callbacks.Breakpoint = &Breakpoint;
  callbacks.SingleStep = &SingleStep;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_getstacktr08_getReady(JNIEnv *jni, jclass cls, jclass clazz, jbyteArray bytes) {
  classBytes = (jbyteArray) jni->NewGlobalRef(bytes);
  wasFramePop = JNI_FALSE;
  mid_checkPoint = jni->GetStaticMethodID(clazz, "checkPoint", "()V");
  mid_chain4 = jni->GetStaticMethodID(clazz, "chain4", "()V");

  check_jvmti_status(jni, jvmti->SetBreakpoint(mid_checkPoint, 0), "SetBreakpoint failed.");
  set_event_notification_mode(jvmti, jni, JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullptr);
}

JNIEXPORT void JNICALL
Java_getstacktr08_nativeChain(JNIEnv *jni, jclass cls, jclass clazz) {
  if (mid_chain4 != nullptr) {
    jni->CallStaticVoidMethod(clazz, mid_chain4);
  }
  if (!compare_stack_trace(jvmti, jni, get_current_thread(jvmti, jni), 3)) {
    jni->ThrowNew(jni->FindClass("java/lang/RuntimeException"), "Stacktrace differs from expected.");
  }
}


}
