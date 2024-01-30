/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmti_common.h"

extern "C" {

#define STATUS_PASSED 0
#define STATUS_FAILED 2

#define TEST_CLASS_0 "MonitorClass0"
#define TEST_CLASS_2 "MonitorClass2"

static jvmtiEnv *jvmti = nullptr;
static jrawMonitorID agent_monitor = nullptr;
static volatile bool was_lock0_contended = false;
static volatile bool was_lock2_contended = false;
static volatile jint status = STATUS_PASSED;
static volatile jclass test_class_0 = nullptr;
static volatile jclass test_class_2 = nullptr;

static jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved);

static void ShowErrorMessage(jvmtiEnv *jvmti, jvmtiError errCode,
                             const char* func, const char *msg) {
  char *errMsg;
  jvmtiError result;

  result = jvmti->GetErrorName(errCode, &errMsg);
  if (result == JVMTI_ERROR_NONE) {
    LOG("%s: %s %s (%d)\n", func, msg, errMsg, errCode);
    jvmti->Deallocate((unsigned char *)errMsg);
  } else {
    LOG("%s: %s (%d)\n", func, msg, errCode);
  }
}

static jboolean CheckLockObject0(JNIEnv *jni, jobject monitor) {
  if (test_class_0 == nullptr) {
    // JNI_OnLoad has not been called yet, so can't possibly be an instance of TEST_CLASS_0.
    return JNI_FALSE;
  }
  return jni->IsInstanceOf(monitor, test_class_0);
}

static jboolean CheckLockObject2(JNIEnv *jni, jobject monitor) {
  if (test_class_2 == nullptr) {
    // JNI_OnLoad has not been called yet, so can't possibly be an instance of TEST_CLASS_2.
    return JNI_FALSE;
  }
  return jni->IsInstanceOf(monitor, test_class_2);
}

static void
check_contended_monitor(jvmtiEnv *jvmti, JNIEnv *jni, const char* func,
                        jthread thread, char* tname, jboolean is_vt,
                        jobject monitor1, jobject monitor2) {
  jvmtiError err;
  jint state = 0;
  jobject contended_monitor = (jobject)thread; // initialize with wrong but valid value

  // Test GetCurrentContendedMonitor for a vthread.
  err = jvmti->GetCurrentContendedMonitor(thread, &contended_monitor);
  if (err != JVMTI_ERROR_NONE) {
    ShowErrorMessage(jvmti, err, func, "error in JVMTI GetCurrentContendedMonitor");
    status = STATUS_FAILED;
    return;
  }

  LOG("\n%s: %s: contended monitor: %p\n", func, tname, contended_monitor);

  // Check if it is expected monitor.
  if (jni->IsSameObject(monitor1, contended_monitor) == JNI_FALSE &&
      jni->IsSameObject(monitor2, contended_monitor) == JNI_FALSE) {
    LOG("FAIL: is_vt: %d: unexpected monitor from GetCurrentContendedMonitor\n", is_vt);
    LOG("stack trace of current thread:\n");
    print_stack_trace(jvmti, jni, nullptr);
    LOG("stack trace of target thread:\n");
    print_stack_trace(jvmti, jni, thread);
    status = STATUS_FAILED;
    return;
  }
  LOG("%s: GetCurrentContendedMonitor returned expected monitor for %s\n", func, tname);

  // Check GetThreadState for a vthread.
  err = jvmti->GetThreadState(thread, &state);
  if (err != JVMTI_ERROR_NONE) {
    ShowErrorMessage(jvmti, err, func, "error in JVMTI GetThreadState");
    status = STATUS_FAILED;
    return;
  }
  LOG("%s: GetThreadState returned state for %s: %0x\n\n", func, tname, state);

}

static void
check_owned_monitor(jvmtiEnv *jvmti, JNIEnv *jni, const char* func,
                    jthread thread, char* tname, jboolean is_vt, jobject monitor) {
  jvmtiError err;
  jint state = 0;
  jint mcount = -1;
  jobject *owned_monitors = nullptr;

  err = jvmti->GetOwnedMonitorInfo(thread, &mcount, &owned_monitors);
  if (err != JVMTI_ERROR_NONE) {
    ShowErrorMessage(jvmti, err, func,
                     "error in JVMTI GetOwnedMonitorInfo");
    status = STATUS_FAILED;
    return;
  }
  LOG("\n%s: GetOwnedMonitorInfo: %s owns %d monitor(s)\n", func, tname, mcount);
  jvmti->Deallocate((unsigned char *)owned_monitors);

  if (is_vt == JNI_TRUE && mcount < 2) {
    LOG("%s: FAIL: monitorCount for %s expected to be >= 2\n", func, tname);
    status = STATUS_FAILED;
    return;
  }
  if (is_vt == JNI_FALSE && mcount != 0) {
    LOG("%s: FAIL: monitorCount for %s expected to be 0\n", func, tname);
    status = STATUS_FAILED;
    return;
  }

  LOG("%s: GetOwnedMonitorInfo: returned expected number of monitors for %s\n", func, tname);

  // Check GetThreadState for a vthread.
  err = jvmti->GetThreadState(thread, &state);
  if (err != JVMTI_ERROR_NONE) {
    ShowErrorMessage(jvmti, err, func, "error in JVMTI GetThreadState");
    status = STATUS_FAILED;
    return;
  }
  LOG("%s: GetThreadState returned state for %s: %0x\n\n", func, tname, state);

}

JNIEXPORT void JNICALL
MonitorContendedEnter(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread, jobject monitor) {
  bool is_lock0 = CheckLockObject0(jni, monitor) == JNI_TRUE;
  bool is_lock2 = CheckLockObject2(jni, monitor) == JNI_TRUE;

  if (is_lock0) {
    RawMonitorLocker rml(jvmti, jni, agent_monitor);
    was_lock0_contended = true;
  }
  if (is_lock2) {
    RawMonitorLocker rml(jvmti, jni, agent_monitor);
    was_lock2_contended = true;
  }
  if (!is_lock0) {
    return; // Not tested monitor
  }

  jthread cthread = get_carrier_thread(jvmti, jni, vthread);

  char* vtname = get_thread_name(jvmti, jni, vthread);
  char* ctname = get_thread_name(jvmti, jni, cthread);

  check_contended_monitor(jvmti, jni, "MonitorContendedEnter",
                          vthread, vtname, JNI_TRUE, monitor, nullptr);
  check_contended_monitor(jvmti, jni, "MonitorContendedEnter",
                          cthread,  ctname, JNI_FALSE, nullptr, nullptr);
  check_owned_monitor(jvmti, jni, "MonitorContendedEnter",
                      vthread, vtname, JNI_TRUE, monitor);
  check_owned_monitor(jvmti, jni, "MonitorContendedEnter",
                      cthread, ctname, JNI_FALSE, monitor);
  deallocate(jvmti, jni, (void*)vtname);
  deallocate(jvmti, jni, (void*)ctname);
}

JNIEXPORT void JNICALL
MonitorContendedEntered(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread, jobject monitor) {
  if (CheckLockObject0(jni, monitor) == JNI_FALSE) {
    return; // Not tested monitor
  }

  jthread cthread = get_carrier_thread(jvmti, jni, vthread);
  char* vtname = get_thread_name(jvmti, jni, vthread);
  char* ctname = get_thread_name(jvmti, jni, cthread);

  check_contended_monitor(jvmti, jni, "MonitorContendedEntered",
                          vthread, vtname, JNI_TRUE, nullptr, nullptr);
  check_contended_monitor(jvmti, jni, "MonitorContendedEntered",
                          cthread, ctname, JNI_FALSE, nullptr, nullptr);

  deallocate(jvmti, jni, (void*)vtname);
  deallocate(jvmti, jni, (void*)ctname);
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

static
jclass find_test_class(JNIEnv *jni, const char* cname) {
  jclass k = jni->FindClass(cname);

  if (k == nullptr) {
    LOG("Error: Could not find class %s!\n", cname);
  } else {
    k = (jclass)jni->NewGlobalRef(k);
  }
  return k;
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *jvm, void *reserved) {
  jint res;
  JNIEnv *jni;

  res = jvm->GetEnv((void **)&jni, JNI_VERSION_9);
  if (res != JNI_OK || jni == nullptr) {
    LOG("Error: GetEnv call failed(%d)!\n", res);
    return JNI_ERR;
  }

  test_class_0 = find_test_class(jni, TEST_CLASS_0);
  test_class_2 = find_test_class(jni, TEST_CLASS_2);

  if (test_class_0 == nullptr || test_class_2 == nullptr) {
    return JNI_ERR;
  }
  return JNI_VERSION_9;
}

static
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jint res;
  jvmtiError err;
  jvmtiCapabilities caps;
  jvmtiEventCallbacks callbacks;

  LOG("Agent_OnLoad started\n");

  res = jvm->GetEnv((void **)&jvmti, JVMTI_VERSION);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Error: wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  err = jvmti->GetPotentialCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    ShowErrorMessage(jvmti, err, "Agent_OnLoad",
                    "error in JVMTI GetPotentialCapabilities");
    return JNI_ERR;
  }

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    ShowErrorMessage(jvmti, err, "Agent_OnLoad",
                     "error in JVMTI AddCapabilities");
    return JNI_ERR;
  }

  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    ShowErrorMessage(jvmti, err, "Agent_OnLoad",
                     "error in JVMTI GetCapabilities");
    return JNI_ERR;
  }

  if (!caps.can_generate_monitor_events) {
    LOG("Warning: Monitor events are not implemented\n");
    return JNI_ERR;
  }
  if (!caps.can_get_owned_monitor_info) {
    LOG("Warning: GetOwnedMonitorInfo is not implemented\n");
    return JNI_ERR;
  }
  if (!caps.can_support_virtual_threads) {
    LOG("Warning: virtual threads are not supported\n");
    return JNI_ERR;
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.MonitorContendedEnter   = &MonitorContendedEnter;
  callbacks.MonitorContendedEntered = &MonitorContendedEntered;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    ShowErrorMessage(jvmti, err, "Agent_OnLoad",
                     "error in JVMTI SetEventCallbacks");
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                                        JVMTI_EVENT_MONITOR_CONTENDED_ENTER, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    ShowErrorMessage(jvmti, err, "Agent_OnLoad",
                     "error in JVMTI SetEventNotificationMode #1");
    return JNI_ERR;
  }
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                                        JVMTI_EVENT_MONITOR_CONTENDED_ENTERED, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    ShowErrorMessage(jvmti, err, "Agent_OnLoad",
                     "error in JVMTI SetEventNotificationMode #2");
    return JNI_ERR;
  }

  agent_monitor = create_raw_monitor(jvmti, "Events Monitor");

  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

JNIEXPORT jboolean JNICALL
Java_VThreadMonitorTest_hasEventPosted(JNIEnv *jni, jclass cls) {
  RawMonitorLocker rml(jvmti, jni, agent_monitor);
  return (was_lock0_contended && was_lock2_contended) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_VThreadMonitorTest_checkContendedMonitor(JNIEnv *jni, jclass cls, jthread vthread,
                                              jobject monitor1, jobject monitor2) {
  char* tname = get_thread_name(jvmti, jni, vthread);

  check_contended_monitor(jvmti, jni, "checkContendedMonitor",
                          vthread, tname, JNI_TRUE, monitor1, monitor2);

  deallocate(jvmti, jni, (void*)tname);
}

JNIEXPORT jint JNICALL
Java_VThreadMonitorTest_check(JNIEnv *jni, jclass cls) {
  return status;
}

} // extern "C"
