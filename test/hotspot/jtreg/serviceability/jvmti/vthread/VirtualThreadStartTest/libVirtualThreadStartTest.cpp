/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
static jrawMonitorID agent_event_lock = NULL;

void JNICALL VirtualThreadStart(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread) {
  RawMonitorLocker agent_start_locker(jvmti, jni, agent_event_lock);
  started_thread_cnt++;
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

  LOG("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  memset(&caps, 0, sizeof(caps));
  memset(&callbacks, 0, sizeof(callbacks));

  caps.can_support_virtual_threads = 1;
  callbacks.VirtualThreadStart = &VirtualThreadStart;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: error in JVMTI AddCapabilities: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->SetEventCallbacks(&callbacks, (jint)sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: error in JVMTI AddCapabilities: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VIRTUAL_THREAD_START, NULL);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: error in JVMTI SetEventNotificationMode: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  agent_event_lock = create_raw_monitor(jvmti, "agent_event_lock");

  return JNI_OK;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return agent_init(jvm, options, reserved);
}

JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return agent_init(jvm, options, reserved);
}

} // extern "C"
