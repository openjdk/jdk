/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
static jboolean did_single_step = false;

extern "C" {

static void JNICALL
SingleStep(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
           jmethodID method, jlocation location) {
  did_single_step = true;
}

JNIEXPORT jboolean JNICALL
Java_SingleStepKlassInit_didSingleStep(JNIEnv* jni, jclass klass) {
  return did_single_step;
}

JNIEXPORT void JNICALL
Java_SingleStepKlassInit_setSingleSteppingMode(JNIEnv* jni, jclass klass, jboolean enable) {
  jvmtiError err = jvmti->SetEventNotificationMode(enable ? JVMTI_ENABLE : JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, nullptr);
  check_jvmti_status(jni, err, "setSingleSteppingMode: error in JVMTI SetEventNotificationMode for JVMTI_EVENT_SINGLE_STEP");
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;

  LOG("Agent_OnLoad: started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    LOG("Agent init: Failed in GetEnv\n");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_generate_single_step_events = 1;
  caps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: Failed in AddCapabilities: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.SingleStep  = &SingleStep;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: Failed in SetEventCallbacks: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  LOG("Agent_OnLoad: finished\n");
  return 0;
}

} // extern "C"
