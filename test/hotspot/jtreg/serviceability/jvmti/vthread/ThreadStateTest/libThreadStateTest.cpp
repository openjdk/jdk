/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmti_common.h"

// set by Agent_OnLoad
static jvmtiEnv* jvmti = nullptr;

extern "C" {

static void JNICALL
SingleStep(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
           jmethodID method, jlocation location) {
}

static void JNICALL
MonitorContended(jvmtiEnv* jvmti, JNIEnv* jni_env, jthread thread,
                 jobject object) {
}

JNIEXPORT void JNICALL
Java_ThreadStateTest_setSingleSteppingMode(JNIEnv* jni, jclass klass, jboolean enable) {
  jvmtiError err = jvmti->SetEventNotificationMode(enable ? JVMTI_ENABLE : JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, nullptr);
  check_jvmti_status(jni, err, "event handler: error in JVMTI SetEventNotificationMode for event JVMTI_EVENT_SINGLE_STEP");
}

JNIEXPORT void JNICALL
Java_ThreadStateTest_setMonitorContendedMode(JNIEnv* jni, jclass klass, jboolean enable) {
  jvmtiError err = jvmti->SetEventNotificationMode(enable ? JVMTI_ENABLE : JVMTI_DISABLE, JVMTI_EVENT_MONITOR_CONTENDED_ENTER, nullptr);
  check_jvmti_status(jni, err, "event handler: error in JVMTI SetEventNotificationMode for event JVMTI_EVENT_MONITOR_CONTENDED_ENTER");
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;

  printf("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    LOG("error in GetEnv");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_generate_single_step_events = 1;
  caps.can_support_virtual_threads = 1;
  caps.can_generate_monitor_events = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI AddCapabilities: %d\n", err);
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.SingleStep  = &SingleStep;
  callbacks.MonitorContendedEnter  = &MonitorContended;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetEventCallbacks: %d\n", err);
  }

  return 0;
}

} // extern "C"
