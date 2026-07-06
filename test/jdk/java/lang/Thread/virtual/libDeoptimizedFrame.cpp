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

#include <stdio.h>
#include <string.h>
#include "jvmti.h"
#include "jni.h"

extern "C" {

static jvmtiEnv *jvmti = nullptr;
static jthread test_vthread = nullptr;
static jobject test_monitor = nullptr;
static jrawMonitorID agent_monitor = nullptr;
static bool called_contended_enter = false;

class RawMonitorLocker {
 private:
  jvmtiEnv* _jvmti;
  JNIEnv* _jni;
  jrawMonitorID _monitor;

  void check_jvmti_status(JNIEnv* jni, jvmtiError err, const char* msg) {
    if (err != JVMTI_ERROR_NONE) {
      jni->FatalError(msg);
    }
  }

 public:
  RawMonitorLocker(jvmtiEnv *jvmti, JNIEnv* jni, jrawMonitorID monitor): _jvmti(jvmti), _jni(jni), _monitor(monitor) {
    check_jvmti_status(_jni, _jvmti->RawMonitorEnter(_monitor), "Fatal Error in RawMonitorEnter.");
  }
  ~RawMonitorLocker() {
    check_jvmti_status(_jni, _jvmti->RawMonitorExit(_monitor), "Fatal Error in RawMonitorExit.");
  }
  void wait() {
    check_jvmti_status(_jni, _jvmti->RawMonitorWait(_monitor, 0), "Fatal Error in RawMonitorWait.");
  }
  void notify_all() {
    check_jvmti_status(_jni, _jvmti->RawMonitorNotifyAll(_monitor), "Fatal Error in RawMonitorNotifyAll.");
  }
};

JNIEXPORT void JNICALL
MonitorContendedEnter(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread, jobject monitor) {
  if (!jni->IsSameObject(test_vthread, vthread)) {
    printf("Thread is not the required one\n");
    return;
  }

  if (!jni->IsSameObject(test_monitor, monitor)) {
    printf("Monitor is not the required one\n");
    return;
  }

  RawMonitorLocker rml(jvmti, jni, agent_monitor);
  called_contended_enter = true;
  rml.notify_all();
  rml.wait();
}

static
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jint res;
  jvmtiError err;
  jvmtiCapabilities caps;
  jvmtiEventCallbacks callbacks;

  printf("Agent_OnLoad started\n");

  res = jvm->GetEnv((void **)&jvmti, JVMTI_VERSION);
  if (res != JNI_OK || jvmti == nullptr) {
    printf("Agent_OnLoad: Error in GetEnv: %d\n", res);
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_generate_monitor_events = 1;
  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    printf("Agent_OnLoad: Error in JVMTI AddCapabilities: %d\n", err);
    return JNI_ERR;
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.MonitorContendedEnter   = &MonitorContendedEnter;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    printf("Agent_OnLoad: Error in JVMTI SetEventCallbacks: %d\n", err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_MONITOR_CONTENDED_ENTER, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    printf("Agent_OnLoad: Error in JVMTI SetEventNotificationMode: %d\n", err);
    return JNI_ERR;
  }

  err = jvmti->CreateRawMonitor("agent sync monitor", &agent_monitor);
  if (err != JVMTI_ERROR_NONE) {
    printf("Agent_OnLoad: Error in JVMTI CreateRawMonitor: %d\n", err);
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL
Java_DeoptimizedFrame_setupReferences(JNIEnv *jni, jclass cls, jthread vthread, jobject monitor) {
  test_vthread = (jthread)jni->NewGlobalRef(vthread);
  test_monitor = jni->NewGlobalRef(monitor);

  if (test_vthread == nullptr || test_monitor == nullptr) {
    printf("GlobalRef null");
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_DeoptimizedFrame_waitForVThread(JNIEnv *jni, jclass cls) {
  RawMonitorLocker rml(jvmti, jni, agent_monitor);
  while (!called_contended_enter) {
    rml.wait();
  }
}

JNIEXPORT void JNICALL
Java_DeoptimizedFrame_notifyVThread(JNIEnv *jni, jclass cls) {
  RawMonitorLocker rml(jvmti, jni, agent_monitor);
  rml.notify_all();
}

} // extern "C"