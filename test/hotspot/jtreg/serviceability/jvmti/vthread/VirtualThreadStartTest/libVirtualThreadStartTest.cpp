/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
static int started_thread_cnt = 0;
static jrawMonitorID agent_event_lock = nullptr;
static const char* TESTED_TNAME_START = "Tested-VT";
static const size_t TESTED_TNAME_START_LEN = strlen(TESTED_TNAME_START);
static bool can_support_vt_enabled = false;

void JNICALL ThreadStart(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread) {
  char* tname = get_thread_name(jvmti, jni, thread);

  RawMonitorLocker agent_start_locker(jvmti, jni, agent_event_lock);

  if (tname != nullptr && strncmp(tname, TESTED_TNAME_START, TESTED_TNAME_START_LEN) == 0) {
    jboolean is_virtual = jni->IsVirtualThread(thread);
    if (!is_virtual) {
      fatal(jni, "Failed: tested thread expected to be virtual");
    }
    if (can_support_vt_enabled) {
      fatal(jni, "Failed: expected VirtualThreadStart instead of ThreadStart event");
    }
    printf("ThreadStart event: %s\n", tname);
    started_thread_cnt++;
  }
  deallocate(jvmti, jni, (void*)tname);
}

void JNICALL VirtualThreadStart(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread) {
  char* tname = get_thread_name(jvmti, jni, thread);

  RawMonitorLocker agent_start_locker(jvmti, jni, agent_event_lock);

  if (tname != nullptr && strncmp(tname, TESTED_TNAME_START, TESTED_TNAME_START_LEN) == 0) {
    jboolean is_virtual = jni->IsVirtualThread(thread);
    if (!is_virtual) {
      fatal(jni, "Failed: tested thread expected to be virtual");
    }
    if (!can_support_vt_enabled) {
      fatal(jni, "Failed: expected ThreadStart instead of VirtualThreadStart event");
    }
    LOG("VirtualThreadStart event: %s\n", tname);
    started_thread_cnt++;
  }
  deallocate(jvmti, jni, (void*)tname);
}

JNIEXPORT jboolean JNICALL
Java_VirtualThreadStartTest_canSupportVirtualThreads(JNIEnv* jni, jclass clazz) {
  LOG("can_support_virtual_threads: %d\n", can_support_vt_enabled);
  return can_support_vt_enabled ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_VirtualThreadStartTest_getAndResetStartedThreads(JNIEnv* jni, jclass clazz) {
  RawMonitorLocker agent_start_locker(jvmti, jni, agent_event_lock);

  int result = started_thread_cnt;
  started_thread_cnt = 0;
  return result;
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
  callbacks.ThreadStart = &ThreadStart;
  callbacks.VirtualThreadStart = &VirtualThreadStart;

  if (options != nullptr && strcmp(options, "can_support_virtual_threads") == 0) {
    can_support_vt_enabled = true;
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
  } else {
    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_START, nullptr);
    if (err != JVMTI_ERROR_NONE) {
      LOG("Agent init: error in JVMTI SetEventNotificationMode: %s (%d)\n", TranslateError(err), err);
      return JNI_ERR;
    }
  }
  LOG("agent_init: can_support_virtual_threads: %d\n", caps.can_support_virtual_threads);

  err = jvmti->SetEventCallbacks(&callbacks, (jint)sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: error in JVMTI AddCapabilities: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  agent_event_lock = create_raw_monitor(jvmti, "agent_event_lock");

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
