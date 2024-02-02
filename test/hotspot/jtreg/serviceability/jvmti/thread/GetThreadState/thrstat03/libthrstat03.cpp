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
#include <string.h>
#include "jvmti.h"
#include "jvmti_common.h"

extern "C" {

#define WAIT_START 100

static jvmtiEnv *jvmti = nullptr;
static jint wait_time = 0;
static jint state[] = {
    0,                               /*  JVMTI_THREAD_STATUS_NOT_STARTED, */
    JVMTI_THREAD_STATE_SLEEPING,
    JVMTI_THREAD_STATE_TERMINATED    /*  JVMTI_THREAD_STATUS_ZOMBIE */
};

JNIEXPORT void JNICALL
Java_thrstat03_init(JNIEnv *env, jclass cls, jint waitTime) {
  wait_time = waitTime * 60000;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }
  return JNI_OK;
}

JNIEXPORT jboolean JNICALL
Java_thrstat03_check(JNIEnv *jni, jclass cls, jthread thread, jint statInd) {
  jboolean result = JNI_TRUE;
  jrawMonitorID wait_lock;
  jint thr_state = 0;

  if (jvmti == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return JNI_FALSE;
  }

  wait_lock = create_raw_monitor(jvmti, "_wait_lock");
  for (int i = WAIT_START; i < wait_time; i <<= 1) {
    thr_state = get_thread_state(jvmti, jni, thread);
    LOG(">>> thread state: %s (%d)\n", TranslateState(thr_state), thr_state);

    if ((thr_state & JVMTI_THREAD_STATE_RUNNABLE) == 0) {
      break;
    }

    RawMonitorLocker rml = RawMonitorLocker(jvmti, jni, wait_lock);
    rml.wait(i);
  }

  destroy_raw_monitor(jvmti, jni,  wait_lock);

  /* We expect that thread is NOT_STARTED if statInd == 0 */
  if (statInd == 0 && thr_state != state[statInd]) {
    result = JNI_FALSE;
  } else if (statInd != 0 && (thr_state & state[statInd]) == 0) {
    result = JNI_FALSE;
  }
  if (result == JNI_FALSE) {
    LOG("Wrong state: %s (%d)\n", TranslateState(thr_state), thr_state);
    LOG("   expected: %s (%d)\n", TranslateState(state[statInd]), state[statInd]);
  }

  return result;
}

}
