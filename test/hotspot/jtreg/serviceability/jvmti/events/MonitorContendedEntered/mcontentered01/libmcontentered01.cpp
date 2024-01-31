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
#include <jvmti.h>
#include "jvmti_common.h"
#include "jvmti_thread.h"

extern "C" {

/* ========================================================================== */

/* scaffold objects */
static JNIEnv *jni = nullptr;
static jvmtiEnv *jvmti = nullptr;
static jlong timeout = 0;

/* test objects */
static jthread expected_thread = nullptr;
static jobject expected_object = nullptr;
static volatile int eventsCount = 0;


/* Check GetPotentialCapabilities function
 */
void JNICALL
MonitorContendedEntered(jvmtiEnv *jvmti, JNIEnv *jni, jthread thr, jobject obj) {

  LOG("MonitorContendedEntered event:\n\tthread: %p, object: %p, expected object: %p\n",thr, obj, expected_object);

  print_thread_info(jvmti, jni, thr);

  if (expected_thread == nullptr) {
    jni->FatalError("expected_thread is null.");
  }

  if (expected_object == nullptr) {
    jni->FatalError("expected_object is null.");
  }

  /* check if event is for tested thread and for tested object */
  if (jni->IsSameObject(expected_thread, thr) &&
      jni->IsSameObject(expected_object, obj)) {
    eventsCount++;
    LOG("Increasing eventCount to %d\n", eventsCount);
  }
}

void JNICALL
MonitorContendedEnter(jvmtiEnv *jvmti, JNIEnv *jni, jthread thr, jobject obj) {

  LOG("MonitorContendedEnter event:\n\tthread: %p, object: %p, expected object: %p\n",thr, obj, expected_object);
  print_thread_info(jvmti, jni, thr);

  if (expected_thread == nullptr) {
    jni->FatalError("expected_thread is null.");
  }

  if (expected_object == nullptr) {
    jni->FatalError("expected_object is null.");
  }

  /* check if event is for tested thread and for tested object */
  if (jni->IsSameObject(expected_thread, thr) &&
      jni->IsSameObject(expected_object, obj)) {
    eventsCount++;
    LOG("Increasing eventCount to %d\n", eventsCount);
  }
}

/* ========================================================================== */

static int prepare() {
  jvmtiError err;

  LOG("Prepare: find tested thread\n");

  /* enable MonitorContendedEntered event */
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_MONITOR_CONTENDED_ENTERED, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Prepare: 11\n");
    return JNI_FALSE;
  }

  /* enable MonitorContendedEnter event */
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_MONITOR_CONTENDED_ENTER, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Prepare: 11\n");
    return JNI_FALSE;
  }

  return JNI_TRUE;
}

static int clean() {
  jvmtiError err;
  /* disable MonitorContendedEntered event */
  err = jvmti->SetEventNotificationMode(JVMTI_DISABLE,JVMTI_EVENT_MONITOR_CONTENDED_ENTERED,nullptr);
  if (err != JVMTI_ERROR_NONE) {
    set_agent_fail_status();
  }
  return JNI_TRUE;
}

/* agent algorithm
 */
static void JNICALL
agentProc(jvmtiEnv *jvmti, JNIEnv *agentJNI, void *arg) {
  jni = agentJNI;

/* wait for initial sync */
  if (!agent_wait_for_sync(timeout))
    return;

  if (!prepare()) {
    set_agent_fail_status();
    return;
  }

  /* clear events count */
  eventsCount = 0;

  /* resume debugee to catch MonitorContendedEntered event */
  if (!((agent_resume_sync() == JNI_TRUE) && (agent_wait_for_sync(timeout) == JNI_TRUE))) {
    return;
  }

  LOG("Number of MonitorContendedEntered events: %d\n", eventsCount);

  if (eventsCount == 0) {
    COMPLAIN("No any MonitorContendedEntered event\n");
    set_agent_fail_status();
  }

  if (!clean()) {
    set_agent_fail_status();
    return;
  }

/* resume debugee after last sync */
  if (!agent_resume_sync())
    return;
}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiEventCallbacks callbacks;
  jvmtiError err;
  jint res;

  timeout = 60000;
  LOG("Timeout: %d msc\n", (int) timeout);

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  err = init_agent_data(jvmti, &agent_data);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_generate_monitor_events = 1;
  caps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n",
           TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  if (!caps.can_generate_monitor_events) {
    return JNI_ERR;
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.MonitorContendedEntered = &MonitorContendedEntered;
  callbacks.MonitorContendedEnter = &MonitorContendedEnter;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  /* register agent proc and arg */
  set_agent_proc(agentProc, nullptr);

  return JNI_OK;
}

JNIEXPORT jint JNICALL Java_mcontentered01_getEventCount(JNIEnv *jni, jobject obj) {
  return eventsCount;
}

JNIEXPORT void JNICALL Java_mcontentered01_setExpected(JNIEnv *jni, jobject clz, jobject obj, jobject thread) {
  LOG("Remembering global reference for monitor object is %p\n", obj);
  /* make object accessible for a long time */
  expected_object = jni->NewGlobalRef(obj);
  if (expected_object == nullptr) {
    jni->FatalError("Error saving global reference to monitor.\n");
  }

  /* make thread accessable for a long time */
  expected_thread = jni->NewGlobalRef(thread);
  if (thread == nullptr) {
    jni->FatalError("Error saving global reference to thread.\n");
  }

  return;
}


JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}
}
