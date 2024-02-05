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

#include <cstdlib>
#include <cstring>
#include <jvmti.h>
#include "jvmti_common.h"

extern "C" {

static jvmtiEnv *jvmti;
static volatile int vthread_started_cnt = 0;
static volatile int vthread_ended_cnt = 0;
static volatile int thread_started_cnt = 0;
static volatile int thread_ended_cnt = 0;

static jrawMonitorID agent_lock = nullptr;
static volatile jboolean agent_started = JNI_FALSE;

static void check_and_print_thread_names(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
                                         bool is_virtual, const char* msg) {
  jthread cthread = nullptr;
  jthread vthread = nullptr;

  if (is_virtual) {
    vthread = thread;
    cthread = get_carrier_thread(jvmti, jni, vthread);
    if (jni->IsVirtualThread(cthread)) {
      fatal(jni, "Failed: expected to be carrier thread");
    }
  } else {
    cthread = thread;
  }
  char* ctname = get_thread_name(jvmti, jni, cthread);
  char* vtname = vthread == nullptr ? nullptr : get_thread_name(jvmti, jni, vthread);

  LOG("Event: %s virtual: %d ct: %s vt: %s\n", msg, is_virtual, ctname, vtname);

  deallocate(jvmti, jni, (void*)ctname);
  deallocate(jvmti, jni, (void*)vtname);
}

void JNICALL VirtualThreadStart(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread) {
  if (!jni->IsVirtualThread(thread)) {
    fatal(jni, "Failed: expected to be virtual thread");
  }
  RawMonitorLocker agent_locker(jvmti, jni, agent_lock);

  vthread_started_cnt++;
  check_and_print_thread_names(jvmti, jni, thread, /* is_virtual */ true, "VirtualThreadStart");
}

void JNICALL VirtualThreadEnd(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread) {
  if (!jni->IsVirtualThread(thread)) {
    fatal(jni, "Failed: expected to be virtual thread");
  }
  RawMonitorLocker agent_locker(jvmti, jni, agent_lock);

  vthread_ended_cnt++;
  check_and_print_thread_names(jvmti, jni, thread, /* is_virtual */ true, "VirtualThreadEnd");
}

void JNICALL ThreadStart(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread) {
  if (jni->IsVirtualThread(thread)) {
    fatal(jni, "Failed: expected to be platform thread");
  }
  RawMonitorLocker agent_locker(jvmti, jni, agent_lock);

  thread_started_cnt++;
  check_and_print_thread_names(jvmti, jni, thread, /*is_virtual*/ false, "ThreadStart");
}

void JNICALL ThreadEnd(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread) {
  if (jni->IsVirtualThread(thread)) {
    fatal(jni, "Failed: expected to be platform thread");
  }
  RawMonitorLocker agent_locker(jvmti, jni, agent_lock);

  thread_ended_cnt++;
  check_and_print_thread_names(jvmti, jni, thread, /*is_virtual*/ false, "ThreadEnd");
}

JNIEXPORT jboolean JNICALL
Java_ToggleNotifyJvmtiTest_IsAgentStarted(JNIEnv* jni, jclass clazz) {
  RawMonitorLocker agent_locker(jvmti, jni, agent_lock);

  return agent_started;
}

JNIEXPORT jint JNICALL
Java_ToggleNotifyJvmtiTest_VirtualThreadStartedCount(JNIEnv* jni, jclass clazz) {
  RawMonitorLocker agent_locker(jvmti, jni, agent_lock);

  return vthread_started_cnt;
}

JNIEXPORT jint JNICALL
Java_ToggleNotifyJvmtiTest_VirtualThreadEndedCount(JNIEnv* jni, jclass clazz) {
  RawMonitorLocker agent_locker(jvmti, jni, agent_lock);

  return vthread_ended_cnt;
}

JNIEXPORT jint JNICALL
Java_ToggleNotifyJvmtiTest_ThreadStartedCount(JNIEnv* jni, jclass clazz) {
  RawMonitorLocker agent_locker(jvmti, jni, agent_lock);

  return thread_started_cnt;
}

JNIEXPORT jint JNICALL
Java_ToggleNotifyJvmtiTest_ThreadEndedCount(JNIEnv* jni, jclass clazz) {
  RawMonitorLocker agent_locker(jvmti, jni, agent_lock);

  return thread_ended_cnt;
}

jint agent_init(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiEventCallbacks callbacks;
  jvmtiError err;

  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  memset(&caps, 0, sizeof(caps));
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.VirtualThreadStart = &VirtualThreadStart;
  callbacks.VirtualThreadEnd = &VirtualThreadEnd;
  callbacks.ThreadStart = &ThreadStart;
  callbacks.ThreadEnd = &ThreadEnd;

  {
    caps.can_support_virtual_threads = 1;

    err = jvmti->AddCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
      LOG("Agent init: error in JVMTI AddCapabilities: %s (%d)\n", TranslateError(err), err);
      return JNI_ERR;
    }
    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VIRTUAL_THREAD_START, nullptr);
    if (err != JVMTI_ERROR_NONE) {
      LOG("Agent init: error in JVMTI SetEventNotificationMode: %s (%d)\n", TranslateError(err), err);
      return JNI_ERR;
    }
    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VIRTUAL_THREAD_END, nullptr);
    if (err != JVMTI_ERROR_NONE) {
      LOG("Agent init: error in JVMTI SetEventNotificationMode: %s (%d)\n", TranslateError(err), err);
      return JNI_ERR;
    }
    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_START, nullptr);
    if (err != JVMTI_ERROR_NONE) {
      LOG("Agent init: error in JVMTI SetEventNotificationMode: %s (%d)\n", TranslateError(err), err);
      return JNI_ERR;
    }
    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_END, nullptr);
    if (err != JVMTI_ERROR_NONE) {
      LOG("Agent init: error in JVMTI SetEventNotificationMode: %s (%d)\n", TranslateError(err), err);
      return JNI_ERR;
    }
  }
  LOG("Agent init: can_support_virtual_threads capability: %d\n",  caps.can_support_virtual_threads);

  err = jvmti->SetEventCallbacks(&callbacks, (jint)sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: error in JVMTI AddCapabilities: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  agent_lock = create_raw_monitor(jvmti, "agent_lock");
  agent_started = JNI_TRUE;

  return JNI_OK;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  LOG("Agent_OnLoad started\n");
  return agent_init(jvm, options, reserved);
}

JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  LOG("Agent_OnAttach started\n");
  return agent_init(jvm, options, reserved);
}

} // extern "C"
