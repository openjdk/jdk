/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

static jvmtiEnv *jvmti = nullptr;
static jmethodID mid_B = nullptr;
static jrawMonitorID monitor = nullptr;
static volatile bool bp_sync_reached = false;

static void JNICALL
Breakpoint(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread,
           jmethodID method, jlocation location) {
  jvmtiError err;

  if (method != mid_B) {
    fatal(jni, "Breakpoint: Failed with wrong location: expected in method TestTask.B()");
  }

  LOG("Breakpoint: In method TestTask.B() before sync section\n");
  {
    RawMonitorLocker rml(jvmti, jni, monitor);
    bp_sync_reached = true;
    rml.wait(0);
  }
  LOG("Breakpoint: In method TestTask.B() after sync section\n");

  jint force_return = 0;
  err = jvmti->GetLocalInt(thread, 0 /* top frame */, 0 /* slot #0 */, &force_return);
  check_jvmti_status(jni, err, "Breakpoint: Failed in JVMTI GetLocalInt");

  if (force_return != 0) {
    jobject ret_obj = nullptr;

    err = jvmti->ClearBreakpoint(mid_B, 0);
    check_jvmti_status(jni, err, "Breakpoint: Failed in JVMTI ClearBreakpoint");

    err = jvmti->GetLocalObject(thread, 0 /* top frame */, 1 /* slot #1 */, &ret_obj);
    check_jvmti_status(jni, err, "Breakpoint: Failed in JVMTI GetLocalObject");

    LOG("Breakpoint: Self early return from method TestTask.B()\n");
    err = jvmti->ForceEarlyReturnObject(thread, ret_obj);
    LOG("Breakpoint: ForceEarlyReturnObject returned code: %s (%d)\n", TranslateError(err), err);
    check_jvmti_status(jni, err, "Breakpoint: Failed in ForceEarlyReturnObject");
  }
  LOG("Breakpoint: In method TestTask.B() finished\n");
}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  static jvmtiCapabilities caps;
  static jvmtiEventCallbacks callbacks;
  jvmtiError err;
  jint res;

  LOG("Agent init\n");
  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Agent init: Failed in GetEnv!\n");
    return JNI_ERR;
  }
  err = jvmti->GetPotentialCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: Failed in GetPotentialCapabilities: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: Failed in AddCapabilities: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: Failed in GetCapabilities: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  if (!caps.can_generate_breakpoint_events) {
    LOG("Agent init: Failed: Breakpoint event is not implemented\n");
    return JNI_ERR;
  }
  callbacks.Breakpoint = &Breakpoint;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: Failed in SetEventCallbacks: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  monitor = create_raw_monitor(jvmti, "Raw monitor to test");
  return JNI_OK;
}

extern JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT void JNICALL
Java_ForceEarlyReturnTest_prepareAgent(JNIEnv *jni, jclass cls, jclass task_clazz) {
  jvmtiError err;

  LOG("Main: prepareAgent started\n");

  if (jvmti == nullptr) {
    fatal(jni, "prepareAgent: Failed as JVMTI client was not properly loaded!\n");
  }
  mid_B = jni->GetStaticMethodID(task_clazz, "B", "(ZLjava/lang/String;)Ljava/lang/String;");
  if (mid_B == nullptr) {
    fatal(jni, "prepareAgent: Failed to find Method ID for method: TestTask.B()\n");
  }
  err = jvmti->SetBreakpoint(mid_B, 0); // location: 0
  check_jvmti_status(jni, err, "prepareAgent: Failed in JVMTI SetBreakpoint");

  set_event_notification_mode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullptr);

  LOG("Main: prepareAgent finished\n");
}

JNIEXPORT void JNICALL
Java_ForceEarlyReturnTest_suspendThread(JNIEnv *jni, jclass cls, jthread thread) {
  LOG("Main: suspendThread\n");
  suspend_thread(jvmti, jni, thread);
}

JNIEXPORT void JNICALL
Java_ForceEarlyReturnTest_resumeThread(JNIEnv *jni, jclass cls, jthread thread) {
  LOG("Main: resumeThread\n");
  resume_thread(jvmti, jni, thread);
}

JNIEXPORT jint JNICALL
Java_ForceEarlyReturnTest_forceEarlyReturnV(JNIEnv *jni, jclass cls, jthread thread) {
  jvmtiError err = jvmti->ForceEarlyReturnVoid(thread);
  LOG("Main: forceEarlyReturn: ForceEarlyReturnVoid returned code: %s (%d)\n", TranslateError(err), err);
  return (jint)err;
}

JNIEXPORT jint JNICALL
Java_ForceEarlyReturnTest_forceEarlyReturnI(JNIEnv *jni, jclass cls, jthread thread, jint val) {
  jvmtiError err = jvmti->ForceEarlyReturnInt(thread, val);
  LOG("Main: forceEarlyReturn: ForceEarlyReturnInt returned code: %s (%d)\n", TranslateError(err), err);
  return (jint)err;
}

JNIEXPORT jint JNICALL
Java_ForceEarlyReturnTest_forceEarlyReturnO(JNIEnv *jni, jclass cls, jthread thread, jobject obj) {
  jvmtiError err = jvmti->ForceEarlyReturnObject(thread, obj);
  LOG("Main: forceEarlyReturn: ForceEarlyReturnObject returned code: %s (%d)\n", TranslateError(err), err);
  return (jint)err;
}


JNIEXPORT void JNICALL
Java_ForceEarlyReturnTest_ensureAtBreakpoint(JNIEnv *jni, jclass cls) {
  bool need_stop = false;

  LOG("Main: ensureAtBreakpoint\n");
  while (!need_stop) {
    RawMonitorLocker rml(jvmti, jni, monitor);
    need_stop = bp_sync_reached;
    sleep_ms(1); // 1 millisecond
  }
}

JNIEXPORT void JNICALL
Java_ForceEarlyReturnTest_notifyAtBreakpoint(JNIEnv *jni, jclass cls) {
  LOG("Main: notifyAtBreakpoint\n");
  RawMonitorLocker rml(jvmti, jni, monitor);
  bp_sync_reached = false;
  rml.notify_all();
}

} // extern "C"
