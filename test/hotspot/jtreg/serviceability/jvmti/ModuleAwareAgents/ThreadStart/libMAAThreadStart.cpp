/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#define PASSED 0
#define FAILED 2

static jvmtiEnv *jvmti = NULL;
static int thread_start_events_vm_start = 0;
static jrawMonitorID agent_lock = nullptr;

static jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved);

JNIEXPORT
jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT
jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT
jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
  return JNI_VERSION_9;
}

void JNICALL ThreadStart(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  jvmtiError err;
  jvmtiPhase phase;

  RawMonitorLocker agent_locker(jvmti, jni, agent_lock);

  err = jvmti->GetPhase(&phase);
  check_jvmti_status(jni, err, "GetPhase");

  if (phase == JVMTI_PHASE_START) {
    thread_start_events_vm_start++;
    LOG(">>>    ThreadStart event: phase: %s\n", TranslatePhase(phase));
  }
  agent_locker.notify(); // notify VM_INIT thread
}


void JNICALL VMInit(jvmtiEnv *jvmti, JNIEnv *jni, jthread thr) {
  RawMonitorLocker agent_locker(jvmti, jni, agent_lock);

  LOG(">>>    VMInit event\n");
  if (thread_start_events_vm_start == 0) {
    // wait for at least one thread to start in early VM_START phase
    LOG(">>>    VMInit event: waiting for any ThreadStart event\n");
    agent_locker.wait(200);
  }
}

static
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jint res, size;
  jvmtiCapabilities caps;
  jvmtiEventCallbacks callbacks;
  jvmtiError err;

  res = jvm->GetEnv((void **)(&jvmti), JVMTI_VERSION_9);
  if (res != JNI_OK || jvmti == NULL) {
    LOG("    Error: wrong result of a valid call to GetEnv!\n");
      return JNI_ERR;
  }

  LOG("Enabling following capability: can_generate_early_vmstart\n");
  memset(&caps, 0, sizeof(caps));
  caps.can_generate_early_vmstart = 1;

  err = jvmti->AddCapabilities(&caps);
  check_jvmti_error(jvmti->AddCapabilities(&caps), "AddCapabilities");

  size = (jint)sizeof(callbacks);
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.VMInit = VMInit;
  callbacks.ThreadStart = ThreadStart;

  err = jvmti->SetEventCallbacks(&callbacks, size);
  check_jvmti_error(jvmti->AddCapabilities(&caps), "SetEventCallbacks");

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, NULL);
  check_jvmti_error(jvmti->AddCapabilities(&caps), "SetEventNotificationMode for VM_INIT");

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_START, NULL);
  check_jvmti_error(jvmti->AddCapabilities(&caps), "SetEventNotificationMode for THREAD_START");

  agent_lock = create_raw_monitor(jvmti, "agent_lock");
  return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_MAAThreadStart_check(JNIEnv *jni, jclass cls) {
  jobject loader = NULL;

  RawMonitorLocker agent_locker(jvmti, jni, agent_lock);

  if (jvmti == NULL) {
    fatal(jni, "JVMTI client was not properly loaded!\n");
    return FAILED;
  }

  /*
   * Expecting that ThreadStart events are sent during VM Start phase when
   * can_generate_early_vmstart capability is enabled.
   */
  if (thread_start_events_vm_start == 0) {
    fatal(jni, "Didn't get ThreadStart events in VM early start phase!\n");
      return FAILED;
  }
  return PASSED;
}

} // extern "C"
