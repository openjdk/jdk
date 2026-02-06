/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
static jrawMonitorID agent_event_lock = nullptr;

extern "C" {

static void JNICALL
MonitorContended(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread,
                 jobject object) {
}

JNIEXPORT void JNICALL
Java_ThreadStateTest2_testSuspendResume(JNIEnv* jni, jclass klass, jthread thread) {
  jvmtiError err;
  RawMonitorLocker event_locker(jvmti, jni, agent_event_lock);

  LOG("\nMAIN: testSuspendResume: before suspend\n");
  err = jvmti->SuspendThread(thread);
  if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
    return;
  }
  check_jvmti_status(jni, err, "testSuspendResume error in JVMTI SuspendThread");
  LOG("\nMAIN: testSuspendResume:  after suspend\n");

  event_locker.wait(1);

  LOG("MAIN: testSuspendResume: before resume\n");
  err = jvmti->ResumeThread(thread);
  check_jvmti_status(jni, err, "testSuspendResume error in JVMTI ResumeThread");
}

JNIEXPORT void JNICALL
Java_ThreadStateTest2_setMonitorContendedMode(JNIEnv* jni, jclass klass, jboolean enable) {
  set_event_notification_mode(jvmti, jni, enable ? JVMTI_ENABLE : JVMTI_DISABLE, JVMTI_EVENT_MONITOR_CONTENDED_ENTER, nullptr);
}

JNIEXPORT void JNICALL
Java_ThreadStateTest2_testInterruptThread(JNIEnv* jni, jclass klass, jthread vthread) {
  char* tname = get_thread_name(jvmti, jni, vthread);
  LOG("VT-2: testInterruptThread: %s\n", tname);

  jvmtiError err = jvmti->InterruptThread(vthread);
  check_jvmti_status(jni, err, "testInterruptThread error in JVMTI InterruptThread");
}

JNIEXPORT jint JNICALL
Java_ThreadStateTest2_testGetThreadState(JNIEnv* jni, jclass klass, jthread vthread) {
  jint  state = get_thread_state(jvmti, jni, vthread);
  char* tname = get_thread_name(jvmti, jni, vthread);

  LOG("VT-2: testGetThreadState: %s state: %x\n", tname, state);
  return state;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;

  printf("Agent_OnLoad: started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    LOG("Agent_OnLoad: error in GetEnv");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_suspend = 1;
  caps.can_signal_thread = 1;
  caps.can_support_virtual_threads = 1;
  caps.can_generate_monitor_events = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: error in JVMTI AddCapabilities: %d\n", err);
  }
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.MonitorContendedEnter = &MonitorContended;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetEventCallbacks: %d\n", err);
  }
  agent_event_lock = create_raw_monitor(jvmti, "agent_event_lock");
  printf("Agent_OnLoad: finished\n");

  return 0;
}

} // extern "C"
