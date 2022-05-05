/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

/* scaffold objects */
static jlong timeout = 0;

/* constant names */
#define THREAD_NAME     "TestedThread"
#define N_LATE_CALLS    10000

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv *jvmti, JNIEnv *jni, void *arg) {
  jvmtiError err;
  /* Original agentProc test block starts here: */
  LOG("Wait for thread to start\n");
  // SP2.1-n - notify agent is waiting and wait
  // SP3.1-w - wait to start test
  if (!agent_wait_for_sync(timeout))
    return;

  /* perform testing */
  {
    jthread testedThread = NULL;
    int late_count;

    LOG("Find thread: %s\n", THREAD_NAME);
    testedThread = find_thread_by_name(jvmti, jni, THREAD_NAME);
    if (testedThread == NULL) {
      return;
    }
    LOG("  ... found thread: %p\n", (void *) testedThread);

    LOG("Suspend thread: %p\n", (void *) testedThread);
    err = jvmti->SuspendThread(testedThread);
    if (err != JVMTI_ERROR_NONE) {
      set_agent_fail_status();
      return;
    }

    LOG("Let thread to run and finish\n");
    // SP5.1-n - notify suspend done
    if (!agent_resume_sync())
      return;

    LOG("Get state vector for thread: %p\n", (void *) testedThread);
    {
      jint state = 0;

      err = jvmti->GetThreadState(testedThread, &state);
      if (err != JVMTI_ERROR_NONE) {
        set_agent_fail_status();
        return;
      }
      LOG("  ... got state vector: %s (%d)\n", TranslateState(state), (int) state);

      if ((state & JVMTI_THREAD_STATE_SUSPENDED) == 0) {
        LOG("SuspendThread() does not turn on flag SUSPENDED:\n"
            "#   state: %s (%d)\n", TranslateState(state), (int) state);
        set_agent_fail_status();
      }
    }

    LOG("Resume thread: %p\n", (void *) testedThread);
    err = jvmti->ResumeThread(testedThread);
    if (err != JVMTI_ERROR_NONE) {
      set_agent_fail_status();
      return;
    }
    /* Original agentProc test block ends here. */

    /*
     * Using LOG() instead of LOG() in this loop
     * in order to slow down the rate of SuspendThread() calls.
     */
    for (late_count = 0; late_count < N_LATE_CALLS; late_count++) {
      jvmtiError l_err;
      LOG("INFO: Late suspend thread: %p\n", (void *) testedThread);
      l_err = jvmti->SuspendThread(testedThread);
      if (l_err != JVMTI_ERROR_NONE) {
        LOG("INFO: Late suspend thread err: %d\n", l_err);
        // testedThread has exited so we're done with late calls
        break;
      }

      // Only resume a thread if suspend worked. Using LOG()
      // here because we want ResumeThread() to be faster.
      LOG("INFO: Late resume thread: %p\n", (void *) testedThread);
      err = jvmti->ResumeThread(testedThread);
      if (err != JVMTI_ERROR_NONE) {
        set_agent_fail_status();
        return;
      }
    }

    LOG("INFO: made %d late calls to JVM/TI SuspendThread()\n", late_count);
    LOG("INFO: N_LATE_CALLS == %d value is %slarge enough to cause a "
        "SuspendThread() call after thread exit.\n", N_LATE_CALLS,
        (late_count == N_LATE_CALLS) ? "NOT " : "");

    /* Second part of original agentProc test block starts here: */
    LOG("Wait for thread to finish\n");
    // SP4.1-n - notify agent is waiting and wait
    // SP6.1-w - wait to end test
    if (!agent_wait_for_sync(timeout))
      return;

    LOG("Delete thread reference\n");
    jni->DeleteGlobalRef(testedThread);
  }

  LOG("Let debugee to finish\n");
  // SP7.1-n - notify agent end
  if (!agent_resume_sync())
    return;
  /* Second part of original agentProc test block ends here. */
}

jint Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiEnv *jvmti = NULL;

  timeout = 60 * 1000;

  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_9);
  if (res != JNI_OK || jvmti == NULL) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  /* add specific capabilities for suspending thread */
  jvmtiCapabilities suspendCaps;
  memset(&suspendCaps, 0, sizeof(suspendCaps));
  suspendCaps.can_suspend = 1;
  if (jvmti->AddCapabilities(&suspendCaps) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  if (init_agent_data(jvmti, &agent_data) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  /* register agent proc and arg */
  if (!set_agent_proc(agentProc, NULL)) {
    return JNI_ERR;
  }

  return JNI_OK;
}

}
