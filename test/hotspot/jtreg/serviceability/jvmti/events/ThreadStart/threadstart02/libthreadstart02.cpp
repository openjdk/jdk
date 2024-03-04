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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <jvmti.h>
#include "jvmti_common.hpp"

extern "C" {

#define PASSED 0
#define STATUS_FAILED 2
#define WAIT_TIME 20000

static jvmtiEnv *jvmti = nullptr;
static jvmtiCapabilities caps;
static jvmtiEventCallbacks callbacks;
/* volatile variables */
static jrawMonitorID agent_start_lock, thr_start_lock, thr_resume_lock, thr_event_lock;
static volatile jthread agent_thread = nullptr;
static volatile jboolean terminate_debug_agent = JNI_FALSE;
static volatile jboolean debug_agent_timed_out = JNI_FALSE;
static volatile jboolean debug_agent_started = JNI_FALSE;
static volatile jthread next_thread = nullptr;
static jvmtiThreadInfo inf;
static volatile int eventsCount = 0;
static volatile jint result = PASSED;

/*
    The agent runs special debugger agent (debug_agent) in a separate thread
    that operates on behalf of other threads.
    Upon receiving ThreadStart event, the debugger agent:
    - suspends the new thread
    - calls jni_DeleteGlobalRef with a jnienv * for that new thread
    - resumes the new thread
    Then the thread suspend status is checked in ThreadStart callback.

    The following monitors are used to synchronize debugger thread with other
    threads:
    1. agent_start_lock
       used to notify VMInit callback as well as ThreadStart callback
       that agent thread has been started.
    2. thr_event_lock
       used to guarantee that only one ThreadStart event is proceeded at
       the time.
    3. thr_start_lock
       used to notify agent thread that new thread has been started.
    4. thr_resume_lock
       used to notify ThreadStart callback that agent thread finished
       suspending and resuming the thread.

    So, the threads behaves as following:

VMInit                  | debug_agent                 |   ThreadStart
-------------------------------------------------------------------------
                        |                             |
 agent_start_lock.enter |                             | agent_start_lock.enter
                        |                             |
 ... create debug_agent | ... start                   |  while (!debug_agent)
 agent_start_lock.wait  |                             |    agent_start_lock.wait
                        | agent_start_lock.enter      |
                        | agent_start_lock.notifyAll  |
                        | agent_start_lock.exit       |
 agent_start_lock.exit  |                             |  agent_start_lock.exit
                        |                             |
                        |                             |  thr_event_lock.enter
                        |                             |
                        | thr_start_lock.enter        |  thr_start_lock.enter
                        | if (!next_thread)           |  thr_resume_lock.enter
                        |     thr_start_lock.wait     |
                        |                             |  ... next_thread = ...
                        |                             |  thr_start_lock.notify
                        |                             |  thr_start_lock.exit
                        |                             |
                        | ... suspend new thread      |  thr_resume_lock.wait
                        | ... resume new thread       |
                        |                             |
                        | thr_resume_lock.enter       |
                        | thr_resume_lock.notify      |
                        | thr_resume_lock.exit        |
                        |                             |  ... check next_thread state
                        |                             |  thr_resume_lock.exit
                        | thr_start_lock.exit         |
                                                      | thr_event_lock.exit


*/

static void JNICALL
debug_agent(jvmtiEnv *jvmti, JNIEnv *jni, void *p) {
  jint thrStat;
  jobject temp;

 /*
  * Notify VMInit callback as well as ThreadStart callback (if any)
  * that agent thread has been started
  */
  {
    RawMonitorLocker agent_start_locker(jvmti, jni, agent_start_lock);
    agent_start_locker.notify_all();
  }

  LOG(">>> [agent] agent created\n");

  debug_agent_started = JNI_TRUE;

  RawMonitorLocker thr_start_locker(jvmti, jni, thr_start_lock);
  while (terminate_debug_agent != JNI_TRUE) {

    if (next_thread == nullptr) {
    /* wait till new thread will be created and started */
      thr_start_locker.wait();
    }

    if (next_thread != nullptr) {
    /* hmm, why NewGlobalRef is called one more time???
     * next_thread = env->NewGlobalRef(next_thread);
     */
      check_jvmti_status(jni, jvmti->SuspendThread(next_thread), "Failed to suspend thread");

      LOG(">>> [agent] thread#%d %s suspended ...\n", eventsCount, inf.name);

      /* these dummy calls provoke VM to hang */
      temp = jni->NewGlobalRef(next_thread);
      jni->DeleteGlobalRef(temp);

      check_jvmti_status(jni, jvmti->ResumeThread(next_thread), "Failed to resume thread");

      LOG(">>> [agent] thread#%d %s resumed ...\n", eventsCount, inf.name);

      check_jvmti_status(jni, jvmti->GetThreadState(next_thread, &thrStat), "Failed to get thread state for");
    }

    LOG(">>> [agent] %s threadState=%s (%x)\n", inf.name, TranslateState(thrStat), thrStat);

    if (thrStat & JVMTI_THREAD_STATE_SUSPENDED) {
      COMPLAIN("[agent] \"%s\" was not resumed\n", inf.name);
      jni->FatalError("[agent] could not recover");
    }

    jni->DeleteGlobalRef(next_thread);
    next_thread = nullptr;

    /* Notify ThreadStart callback that thread has been resumed */

    RawMonitorLocker thr_resume_locker(jvmti, jni, thr_resume_lock);
    debug_agent_timed_out = JNI_FALSE;
    thr_resume_locker.notify();
  }

  /*
   * We don't call RawMonitorExit(thr_start_lock) in the loop so we don't
   * lose any notify calls.
   */
  LOG(">>> [agent] done.\n");
}

void JNICALL ThreadStart(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  jint thrStat;
  jvmtiPhase phase;

  LOG(">>> [ThreadStart hook] start\n");

  /* skip if thread is 'agent thread' */
  if (jni->IsSameObject(agent_thread, thread) == JNI_TRUE) {
    LOG(">>> [ThreadStart hook] skip agent thread\n");
    LOG(">>> [ThreadStart hook] end\n");
    return;
  }

  /* wait till agent thread is started
   * (otherwise can fail while waiting on thr_resume_thread due to timeout)
   */
  if (debug_agent_started != JNI_TRUE) {
    RawMonitorLocker agent_start_locker(jvmti, jni, agent_start_lock);

    while (debug_agent_started != JNI_TRUE) {
      LOG(">>> [ThreadStart hook] waiting %dms for agent thread to start\n", WAIT_TIME);
      agent_start_locker.wait(WAIT_TIME);
    }
  }


  /* get JVMTI phase */
  check_jvmti_status(jni, jvmti->GetPhase(&phase), "[ThreadStart hook] Failed to get JVMTI phase");

  /* Acquire event lock,
   * so only one StartThread callback could be proceeded at the time
   */
  RawMonitorLocker thr_event_locker(jvmti, jni, thr_event_lock);

  {
    /* Get thread name */
    inf.name = (char *) "UNKNOWN";
    if (phase == JVMTI_PHASE_LIVE) {
      /* GetThreadInfo may only be called during the live phase */
      check_jvmti_status(jni, jvmti->GetThreadInfo(thread, &inf), "GetThreadInfo failed.");
    }

    LOG(">>> [ThreadStart hook] thread#%d: %s\n", eventsCount, inf.name);

    /* Acquire thr_start_lock */

    check_jvmti_status(jni, jvmti->RawMonitorEnter(thr_start_lock), "RawMonitorEnter failed");

    /* Acquire thr_resume_lock before we release thr_start_lock to prevent
     * debug agent from notifying us before we are ready.
    */
    check_jvmti_status(jni, jvmti->RawMonitorEnter(thr_resume_lock), "RawMonitorEnter failed");


    /* Store thread */
    next_thread = jni->NewGlobalRef(thread);
    debug_agent_timed_out = JNI_TRUE;

    /* Notify agent thread about new started thread and let agent thread to work with it */
    check_jvmti_status(jni, jvmti->RawMonitorNotify(thr_start_lock), "RawMonitorNotify failed");

    check_jvmti_status(jni, jvmti->RawMonitorExit(thr_start_lock), "RawMonitorExit failed");

    /* Wait till this started thread will be resumed by agent thread */
    check_jvmti_status(jni, jvmti->RawMonitorWait(thr_resume_lock, (jlong) WAIT_TIME), "");

    if (debug_agent_timed_out == JNI_TRUE) {
      COMPLAIN("[ThreadStart hook] \"%s\": debug agent timed out\n", inf.name);
      jni->FatalError("[ThreadStart hook] could not recover");
    }

    /* Release thr_resume_lock lock */
    check_jvmti_status(jni, jvmti->RawMonitorExit(thr_resume_lock), "");

    /* check that thread is not in SUSPENDED state */
    check_jvmti_status(jni, jvmti->GetThreadState(thread, &thrStat), "");

    LOG(">>> [ThreadStart hook] threadState=%s (%x)\n", TranslateState(thrStat), thrStat);

    if (thrStat & JVMTI_THREAD_STATE_SUSPENDED) {
      COMPLAIN("[ThreadStart hook] \"%s\" was self-suspended\n", inf.name);
      jni->FatalError("[ThreadStart hook] could not recover");
    }

    eventsCount++;
  }

  LOG(">>> [ThreadStart hook] end\n");
}

void JNICALL VMInit(jvmtiEnv *jvmti, JNIEnv *jni, jthread thr) {
  jclass cls = nullptr;
  jmethodID mid = nullptr;

  LOG(">>> VMInit event: start\n");

  check_jvmti_status(jni, jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_START, nullptr),
    "Failed to enable JVMTI_EVENT_THREAD_START");

  /* Start agent thread */
  cls = jni->FindClass("java/lang/Thread");
  if (cls == nullptr) {
    result = STATUS_FAILED;
    COMPLAIN("TEST FAILED: Cannot start agent thread: FindClass() failed\n");
    return;
  }

  mid = jni->GetMethodID(cls, "<init>", "()V");
  if (mid == nullptr) {
    result = STATUS_FAILED;
    COMPLAIN("TEST FAILED: Cannot start agent thread: GetMethodID() failed\n");
    return;
  }

  agent_thread = jni->NewObject(cls, mid);
  if (agent_thread == nullptr) {
    result = STATUS_FAILED;
    COMPLAIN("Cannot start agent thread: NewObject() failed\n");
    return;
  }

  agent_thread = (jthread) jni->NewGlobalRef(agent_thread);
  if (agent_thread == nullptr) {
    result = STATUS_FAILED;
    COMPLAIN("Cannot create global reference for agent_thread\n");
    return;
  }

  /*
   * Grab agent_start_lock before launching debug_agent to prevent
   * debug_agent from notifying us before we are ready.
   */

  RawMonitorLocker agent_start_locker(jvmti, jni, agent_start_lock);

  check_jvmti_status(jni, jvmti->RunAgentThread(agent_thread, debug_agent, nullptr, JVMTI_THREAD_NORM_PRIORITY),
                     "Failed to RunAgentThread");
  agent_start_locker.wait();
  LOG(">>> VMInit event: end\n");
}

void JNICALL VMDeath(jvmtiEnv *jvmti, JNIEnv *jni) {
  LOG(">>> VMDeath event\n");
  terminate_debug_agent = JNI_TRUE;
}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jint res;
  jvmtiError err;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  err = jvmti->GetPotentialCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }


  if (!caps.can_suspend) {
    LOG("WARNING: suspend/resume is not implemented\n");
  }

  /* create raw monitors */
  agent_start_lock = create_raw_monitor(jvmti, "_agent_start_lock");
  thr_event_lock = create_raw_monitor(jvmti, "_thr_event_lock");
  thr_start_lock = create_raw_monitor(jvmti, "_thr_start_lock");
  thr_resume_lock =   create_raw_monitor(jvmti, "_thr_resume_lock");

  callbacks.VMInit = &VMInit;
  callbacks.VMDeath = &VMDeath;
  callbacks.ThreadStart = &ThreadStart;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to disable JVMTI_EVENT_THREAD_START: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to disable JVMTI_EVENT_THREAD_END: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return JNI_ERR;
  }
  return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_threadstart02_check(JNIEnv *jni, jclass cls) {
  if (eventsCount == 0) {
    COMPLAIN("None of thread start events!\n");
    result = STATUS_FAILED;
  }

  LOG(">>> total of thread start events: %d\n", eventsCount);

  return result;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

}
