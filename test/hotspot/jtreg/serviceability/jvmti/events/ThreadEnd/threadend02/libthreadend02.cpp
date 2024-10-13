/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmti_thread.hpp"


extern "C" {

/* scaffold objects */
static jvmtiEnv *jvmti = nullptr;
static jlong timeout = 0;

static int eventCount = 0;

JNIEXPORT void JNICALL
cbThreadEnd(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread) {
  eventCount++;
}

static int
enableEvent(jvmtiEventMode enable, jvmtiEvent event) {
  jvmtiError err;

  if (enable == JVMTI_ENABLE) {
    LOG("enabling %s\n", TranslateEvent(event));
  } else {
    LOG("disabling %s\n", TranslateEvent(event));
  }

  err = jvmti->SetEventNotificationMode(enable, event, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    set_agent_fail_status();
    return JNI_FALSE;
  }

  return JNI_TRUE;
}

/* ============================================================================= */

int checkEvents() {

  int result = JNI_TRUE;

  if (eventCount == 0) {
    set_agent_fail_status();
    COMPLAIN("Number of THREAD_END events must be greater than 0\n");
    set_agent_fail_status();
    result = JNI_FALSE;
  }

  return result;
}

/* ============================================================================= */

static int
setCallBacks() {
  jvmtiError err;
  jvmtiEventCallbacks eventCallbacks;
  memset(&eventCallbacks, 0, sizeof(eventCallbacks));

  eventCallbacks.ThreadEnd = cbThreadEnd;

  err = jvmti->SetEventCallbacks(&eventCallbacks, sizeof(eventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    return JNI_FALSE;
  }

  return JNI_TRUE;
}

/* ============================================================================= */

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv* jvmti, JNIEnv* agentJNI, void* arg) {

  LOG("Wait for debuggee to become ready\n");
  if (!agent_wait_for_sync(timeout))
    return;

  LOG("Let debuggee to continue\n");
  if (!agent_resume_sync())
    return;

  if (!agent_wait_for_sync(timeout))
    return;

  if (!checkEvents()) {
    set_agent_fail_status();
  }

  LOG("Let debuggee to finish\n");
  if (!agent_resume_sync())
    return;

}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jvmtiError err;
  jint res;

  timeout = 60 * 1000;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }


  err = init_agent_data(jvmti, &agent_data);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  if (!setCallBacks()) {
    return JNI_ERR;
  }

  if (!enableEvent(JVMTI_ENABLE, JVMTI_EVENT_THREAD_END)) {
    COMPLAIN("Events could not be enabled");
    set_agent_fail_status();
    return JNI_ERR;
  }

  set_agent_proc(agentProc, nullptr);

  return JNI_OK;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

}
