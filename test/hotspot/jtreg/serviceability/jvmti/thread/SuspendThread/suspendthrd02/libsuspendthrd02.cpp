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

#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"
#include "jvmti_thread.hpp"

extern "C" {

/* scaffold objects */
static jlong timeout = 0;

/* This is how long we verify that the thread has really suspended (in ms) */
static jlong verificationTime = 5 * 1000;

/* constant names */
#define THREAD_NAME     "TestedThread"

/* constants */
#define EVENTS_COUNT    1

/* events list */
static jvmtiEvent eventsList[EVENTS_COUNT] = {
    JVMTI_EVENT_THREAD_END
};

static volatile int eventsReceived = 0;
static jthread testedThread = nullptr;

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv *jvmti, JNIEnv *jni, void *arg) {
  jvmtiError err;

  LOG("Wait for thread to start\n");
  if (!agent_wait_for_sync(timeout))
    return;

  /* perform testing */
  {
    LOG("Find thread: %s\n", THREAD_NAME);
    testedThread = find_thread_by_name(jvmti, jni, THREAD_NAME);
    if (testedThread == nullptr) {
      return;
    }
    LOG("  ... found thread: %p\n", (void *) testedThread);

    eventsReceived = 0;
    LOG("Enable event: %s\n", "THREAD_END");
    enable_events_notifications(jvmti, jni, JVMTI_ENABLE, EVENTS_COUNT, eventsList, nullptr);

    LOG("Suspend thread: %p\n", (void *) testedThread);
    err = jvmti->SuspendThread(testedThread);
    if (err != JVMTI_ERROR_NONE) {
      set_agent_fail_status();
      return;
    }

    LOG("Let thread to run and finish\n");
    if (!agent_resume_sync())
      return;

    LOG("Check that THREAD_END event NOT received for timeout: %ld ms\n", (long) verificationTime);
    {
      jlong delta = 1000;
      jlong time;
      for (time = 0; time < verificationTime; time += delta) {
        if (eventsReceived > 0) {
          COMPLAIN("Thread ran and finished after suspension\n");
          set_agent_fail_status();
          break;
        }
        sleep_sec(delta);
      }
    }

    LOG("Disable event: %s\n", "THREAD_END");
    enable_events_notifications(jvmti, jni, JVMTI_DISABLE, EVENTS_COUNT, eventsList, nullptr);

    LOG("Resume thread: %p\n", (void *) testedThread);
    err = jvmti->ResumeThread(testedThread);
    if (err != JVMTI_ERROR_NONE) {
      set_agent_fail_status();
      return;
    }

    LOG("Wait for thread to finish\n");
    if (!agent_wait_for_sync(timeout))
      return;

    LOG("Delete thread reference\n");
    jni->DeleteGlobalRef(testedThread);
  }

  LOG("Let debugee to finish\n");
  if (!agent_resume_sync())
    return;
}

/** THREAD_END callback. */
JNIEXPORT void JNICALL
callbackThreadEnd(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  /* check if event is for tested thread */
  if (thread != nullptr && jni->IsSameObject(testedThread, thread)) {
    LOG("  ... received THREAD_END event for tested thread: %p\n", (void *) thread);
    eventsReceived++;
  } else {
    LOG("  ... received THREAD_END event for unknown thread: %p\n", (void *) thread);
  }
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiEnv *jvmti = nullptr;

  timeout = 60 * 1000;

  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_9);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  /* add specific capabilities for suspending thread */
  {
    jvmtiCapabilities suspendCaps;
    memset(&suspendCaps, 0, sizeof(suspendCaps));
    suspendCaps.can_suspend = 1;
    if (jvmti->AddCapabilities(&suspendCaps) != JVMTI_ERROR_NONE) {
      return JNI_ERR;
    }
  }

  /* set callbacks for THREAD_END event */
  {
    jvmtiEventCallbacks callbacks;
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.ThreadEnd = callbackThreadEnd;
    jvmtiError err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (err != JVMTI_ERROR_NONE) {
      LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
      return JNI_ERR;
    }
  }

  if (init_agent_data(jvmti, &agent_data) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  /* register agent proc and arg */
  if (!set_agent_proc(agentProc, nullptr)) {
    return JNI_ERR;
  }

  return JNI_OK;
}

}
