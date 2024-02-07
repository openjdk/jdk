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
#include "jvmti_common.h"
#include "jvmti_thread.h"

extern "C" {

/* ============================================================================= */

/* scaffold objects */
static jlong timeout = 0;

/* constant names */
#define THREAD_NAME     "TestedThread"

/* ============================================================================= */

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv *jvmti, JNIEnv *jni, void *arg) {
  jvmtiError err;
  LOG("Wait for thread to start\n");
  if (!agent_wait_for_sync(timeout))
    return;

  /* perform testing */
  {
    jthread testedThread = nullptr;

    LOG("Find thread: %s\n", THREAD_NAME);
    testedThread = find_thread_by_name(jvmti, jni, THREAD_NAME);
    if (testedThread == nullptr) {
      return;
    }
    LOG("  ... found thread: %p\n", (void *) testedThread);

    LOG("Suspend thread: %p\n", (void *) testedThread);
    suspend_thread(jvmti, jni, testedThread);

    LOG("Resume thread: %p\n", (void *) testedThread);
    resume_thread(jvmti, jni, testedThread);

    LOG("Get state vector for thread: %p\n", (void *) testedThread);
    {
      jint state = 0;

      err = jvmti->GetThreadState(testedThread, &state);
      if (err != JVMTI_ERROR_NONE) {
        set_agent_fail_status();
        return;
      }
      LOG("  ... got state vector: %s (%d)", TranslateState(state), (int) state);

      if ((state & JVMTI_THREAD_STATE_SUSPENDED) != 0) {
        LOG("SuspendThread() does not turn off flag SUSPENDED:\n"
               "#   state:  %s (%d)\n", TranslateState(state), (int) state);
        set_agent_fail_status();
      }
    }

    LOG("Let thread to run and finish\n");
    if (!agent_resume_sync())
      return;

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

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiEnv *jvmti = nullptr;

  timeout = 60 * 1000;
  LOG("Agent_OnLoad started\n");

  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_9);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_suspend = 1;
  if (jvmti->AddCapabilities(&caps) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  if (init_agent_data(jvmti, &agent_data) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  if (!set_agent_proc(agentProc, nullptr)) {
    return JNI_ERR;
  }

  return JNI_OK;
}

}
