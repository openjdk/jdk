/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv *jvmti = nullptr;
static jrawMonitorID monitor = nullptr;
static bool is_waiting = false;

static void check_thread_not_interrupted(JNIEnv *jni, int check_idx) {
  jint state = get_thread_state(jvmti, jni, nullptr);

  LOG("\ntest: check #%d: Thread State: (0x%x) %s\n",
      check_idx, state, TranslateState(state));

  if ((state & JVMTI_THREAD_STATE_INTERRUPTED) != 0) {
    fatal(jni, "Failed: JVMTI_THREAD_STATE_INTERRUPTED bit expected to be cleared");
  }
}

JNIEXPORT void JNICALL
Java_InterruptRawMonitor_waitForCondition(JNIEnv *jni, jclass clazz, jthread thread) {
  jint state = 0;
  RawMonitorLocker rml(jvmti, jni, monitor);

  while (!is_waiting) {
    state = get_thread_state(jvmti, jni, thread);
    LOG("main: waitForCondition: target Thread State: (0x%x) %s\n",
        state, TranslateState(state));
    rml.wait(10);
  }
  state = get_thread_state(jvmti, jni, thread);
  LOG("main: waitForCondition: target Thread State: (0x%x) %s\n\n",
      state, TranslateState(state));
}

JNIEXPORT void JNICALL
Java_InterruptRawMonitor_test(JNIEnv *jni, jclass clazz) {
  RawMonitorLocker rml(jvmti, jni, monitor);

  check_thread_not_interrupted(jni, 0);
  is_waiting = true;

  // expected to be interrupted
  jvmtiError err = jvmti->RawMonitorWait(monitor, 0);
  LOG("test: JVMTI RawMonitorWait returned expected error code: (%d) %s\n",
      err, TranslateError(err));
  if (err != JVMTI_ERROR_INTERRUPT) {
    fatal(jni, "Failed: expected JVMTI_ERROR_INTERRUPT from RawMonitorWait");
  }

  check_thread_not_interrupted(jni, 1);

  rml.wait(10); // expected to be non-interrupted

  check_thread_not_interrupted(jni, 2);
}

extern JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  LOG("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **)(&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  monitor = create_raw_monitor(jvmti, "Test Monitor");
  LOG("test: JVMTI_THREAD_STATE_INTERRUPTED bit: 0x%x\n", JVMTI_THREAD_STATE_INTERRUPTED);

  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

} // extern "C"
