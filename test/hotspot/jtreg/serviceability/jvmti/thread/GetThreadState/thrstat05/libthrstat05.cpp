/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 */

#include <stdio.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

#define THREAD_STATE_MASK ~(JVMTI_THREAD_STATE_SUSPENDED \
                            | JVMTI_THREAD_STATE_INTERRUPTED \
                            | JVMTI_THREAD_STATE_IN_NATIVE \
                            | JVMTI_THREAD_STATE_VENDOR_1 \
                            | JVMTI_THREAD_STATE_VENDOR_2 \
                            | JVMTI_THREAD_STATE_VENDOR_3)

static int g_ThreadState[] = {
    0,                                                 /* TS_NEW */
    JVMTI_THREAD_STATE_TERMINATED,                     /* TS_TERMINATED */
    JVMTI_THREAD_STATE_ALIVE
        | JVMTI_THREAD_STATE_RUNNABLE,                 /* TS_RUN_RUNNING */
    JVMTI_THREAD_STATE_ALIVE
        | JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER, /* TS_RUN_BLOCKED */
    JVMTI_THREAD_STATE_ALIVE
        | JVMTI_THREAD_STATE_IN_OBJECT_WAIT
        | JVMTI_THREAD_STATE_WAITING
        | JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT,     /* TS_RUN_WAIT_TIMED */
    JVMTI_THREAD_STATE_ALIVE
        | JVMTI_THREAD_STATE_IN_OBJECT_WAIT
        | JVMTI_THREAD_STATE_WAITING
        | JVMTI_THREAD_STATE_WAITING_INDEFINITELY,     /* TS_RUN_WAIT_INDEF */
    JVMTI_THREAD_STATE_ALIVE
        | JVMTI_THREAD_STATE_PARKED
        | JVMTI_THREAD_STATE_WAITING
        | JVMTI_THREAD_STATE_WAITING_INDEFINITELY,     /* TS_RUN_WAIT_PARKED_INDEF */
    JVMTI_THREAD_STATE_ALIVE
        | JVMTI_THREAD_STATE_PARKED
        | JVMTI_THREAD_STATE_WAITING
        | JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT,     /* TS_RUN_WAIT_PARKED_TIMED */
    JVMTI_THREAD_STATE_ALIVE
        | JVMTI_THREAD_STATE_SLEEPING
        | JVMTI_THREAD_STATE_WAITING
        | JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT,     /* TS_RUN_WAIT_SLEEPING */
};

static jvmtiEnv *jvmti_env = nullptr;
static int g_wait_time = 1000;
jrawMonitorID wait_lock; /* Monitor is used just for sleeping */


JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jint res = jvm->GetEnv((void **) &jvmti_env, JVMTI_VERSION_1_1);
  if (res != JNI_OK || !jvmti_env) {
    LOG("Agent_OnLoad: Error: GetEnv returned error or null\n");
    return JNI_ERR;
  }
  wait_lock = create_raw_monitor(jvmti_env, "beast");
  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_thrstat05_setWaitTime(JNIEnv *jni, jclass klass, jint wait_time) {
  g_wait_time = wait_time;
}

JNIEXPORT jboolean JNICALL
Java_thrstat05_checkThreadState(JNIEnv *jni, jclass klass, jthread thread, jint stateIdx) {
  int wait_time = 10;

  /* Repeat querying status until wait_time < g_wait_time */
  do {
    jint thrState = get_thread_state(jvmti_env, jni, thread);
    jint maskedThrState = thrState & THREAD_STATE_MASK;
    LOG("GetThreadState = %x. Masked: %x. Must be: %x\n", thrState, maskedThrState, g_ThreadState[stateIdx]);
    fflush(stdout);

    if (maskedThrState == g_ThreadState[stateIdx])
      return JNI_TRUE;

    LOG("checkThreadState: wait %d ms\n", wait_time);
    fflush(stdout);
    RawMonitorLocker wait_locker = RawMonitorLocker(jvmti_env, jni,wait_lock);
    wait_locker.wait(wait_time);
    wait_time <<= 1;

  } while (wait_time < g_wait_time);

  return JNI_FALSE;
}

}
