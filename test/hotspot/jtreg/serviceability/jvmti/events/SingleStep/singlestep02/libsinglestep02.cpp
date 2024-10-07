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
#include <stdlib.h>
#include <string.h>
#include <jvmti.h>
#include "jvmti_common.hpp"

extern "C" {

#define STATUS_FAILED 2
#define PASSED 0

static volatile jint result = PASSED;
static volatile long wrongStepEv = 0;

static jvmtiEnv *jvmti = nullptr;

/** callback functions **/
void JNICALL
SingleStep(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jmethodID method, jlocation location) {
  jvmtiPhase phase;
  jvmtiError err;

  err = jvmti->GetPhase(&phase);
  if (err != JVMTI_ERROR_NONE) {
    result = STATUS_FAILED;
    COMPLAIN("TEST FAILED: unable to obtain phase of the VM execution during SingleStep callback\n\n");
  } else {
    if (phase != JVMTI_PHASE_LIVE) {
      wrongStepEv++;
      result = STATUS_FAILED;
      COMPLAIN("TEST FAILED: SingleStep event received during non-live phase %s\n", TranslatePhase(phase));
    }
  }
}

void JNICALL
VMDeath(jvmtiEnv *jvmti, JNIEnv *jni) {
  LOG("VMDeath event received\n");

  if (wrongStepEv != 0) {
    LOG("TEST FAILED: there are %ld SingleStep events\n"
        "sent during non-live phase of the VM execution\n", wrongStepEv);
    jni->FatalError("Test Failed.");
  }

  if (result == STATUS_FAILED) {
    jni->FatalError("Test Failed.");
  }
}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;
  jint res;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  /* add capability to generate compiled method events */
  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_generate_single_step_events = 1;
  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  if (!caps.can_generate_single_step_events) {
    LOG("Warning: generation of single step events is not implemented\n");
    return JNI_ERR;
  }

  /* set event callback */
  LOG("setting event callbacks ...\n");
  (void) memset(&callbacks, 0, sizeof(callbacks));
  callbacks.SingleStep = &SingleStep;
  callbacks.VMDeath = &VMDeath;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  LOG("setting event callbacks done\nenabling JVMTI events ...\n");
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  LOG("enabling the events done\n\n");

  return JNI_OK;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

}
