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

#include <time.h>

extern "C" {

/* scaffold objects */
static jlong timeout = 0;

/* This is how long we verify that the thread has really suspended (in ms) */
static jlong verificationTime = 5 * 1000;

/* constant names */
#define THREAD_NAME     "TestedThread"

/* constants */
#define EVENTS_COUNT            1

static const int THREADS_COUNT = 10;

/* events list */
static jvmtiEvent eventsList[EVENTS_COUNT] = {
    JVMTI_EVENT_THREAD_END
};

static jthread* threads = nullptr;

static volatile int eventsReceived = 0;

static int find_threads_by_name(jvmtiEnv* jvmti, JNIEnv* jni,
                                const char name[], int foundCount, jthread foundThreads[]);

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv* jvmti, JNIEnv* jni, void* arg) {

  LOG("Wait for threads to start\n");
  if (!agent_wait_for_sync(timeout))
    return;

  /* perform testing */
  {
    jvmtiError* results = nullptr;

    LOG("Allocate threads array: %d threads\n", THREADS_COUNT);
    check_jvmti_status(jni, jvmti->Allocate((THREADS_COUNT * sizeof(jthread)),
                                          (unsigned char**)&threads), "Allocate failed");
    LOG("  ... allocated array: %p\n", (void*)threads);

    LOG("Allocate results array: %d threads\n", THREADS_COUNT);
     check_jvmti_status(jni, jvmti->Allocate((THREADS_COUNT * sizeof(jvmtiError)),
                                          (unsigned char**)&results), "Allocate failed");
    LOG("  ... allocated array: %p\n", (void*)threads);

    LOG("Find threads: %d threads\n", THREADS_COUNT);
    if (find_threads_by_name(jvmti, jni, THREAD_NAME, THREADS_COUNT, threads) == 0) {
      return;
    }

    LOG("Suspend threads list\n");
    jvmtiError err = jvmti->SuspendThreadList(THREADS_COUNT, threads, results);
    if (err != JVMTI_ERROR_NONE) {
      set_agent_fail_status();
      return;
    }

    LOG("Check threads results:\n");
    for (int i = 0; i < THREADS_COUNT; i++) {
      LOG("  ... thread #%d: %s (%d)\n", i, TranslateError(results[i]), (int)results[i]);
      if (results[i] != JVMTI_ERROR_NONE) {
        set_agent_fail_status();
      }
    }

    eventsReceived = 0;
    LOG("Enable event: %s\n", "THREAD_END");
    enable_events_notifications(jvmti, jni, JVMTI_ENABLE, EVENTS_COUNT, eventsList, nullptr);

    LOG("Let threads to run and finish\n");
    if (!agent_resume_sync())
      return;

    LOG("Check that THREAD_END event NOT received for timeout: %ld ms\n", (long)verificationTime);
    {
      jlong delta = 1000;
      jlong ctime;
      for (ctime = 0; ctime < verificationTime; ctime += delta) {
        if (eventsReceived > 0) {
          COMPLAIN("Some threads ran and finished after suspension: %d threads\n", eventsReceived);
          set_agent_fail_status();
          break;
        }
        sleep_sec(delta);
      }
    }

    LOG("Disable event: %s\n", "THREAD_END");
    enable_events_notifications(jvmti, jni,JVMTI_DISABLE, EVENTS_COUNT, eventsList, nullptr);

    LOG("Resume threads list\n");
    err = jvmti->ResumeThreadList(THREADS_COUNT, threads, results);
    if (err != JVMTI_ERROR_NONE) {
      set_agent_fail_status();
      return;
    }

    LOG("Wait for thread to finish\n");
    if (!agent_wait_for_sync(timeout)) {
      return;
    }

    LOG("Delete threads references\n");
    for (int i = 0; i < THREADS_COUNT; i++) {
      if (threads[i] != nullptr) {
        jni->DeleteGlobalRef(threads[i]);
      }
    }

    LOG("Deallocate threads array: %p\n", (void*)threads);
    check_jvmti_status(jni, jvmti->Deallocate((unsigned char*)threads), "");


    LOG("Deallocate results array: %p\n", (void*)results);
    check_jvmti_status(jni, jvmti->Deallocate((unsigned char*)results), "");
  }

  LOG("Let debugee to finish\n");
  if (!agent_resume_sync())
    return;
}

/** Find threads whose name starts with specified name prefix. */
static int find_threads_by_name(jvmtiEnv* jvmti, JNIEnv* jni,
                            const char name[], int foundCount, jthread foundThreads[]) {
  jint count = 0;
  jthread* threads = nullptr;
  size_t len = strlen(name);
  int found = 0;

  for (int i = 0; i < foundCount; i++) {
    foundThreads[i] = nullptr;
  }

  check_jvmti_status(jni, jvmti->GetAllThreads(&count, &threads), "");

  found = 0;
  for (int i = 0; i < count; i++) {
    jvmtiThreadInfo info;

    check_jvmti_status(jni, jvmti->GetThreadInfo(threads[i], &info), "");
    if (info.name != nullptr && strncmp(name, info.name, len) == 0) {
      LOG("  ... found thread #%d: %p (%s)\n", found, threads[i], info.name);
      if (found < foundCount) {
        foundThreads[found] = threads[i];
      }
      found++;
    }
  }

  check_jvmti_status(jni, jvmti->Deallocate((unsigned char*)threads), "Deallocate failed.");

  if (found != foundCount) {
    COMPLAIN("Unexpected number of tested threads found:\n"
             "#   name:     %s\n"
             "#   found:    %d\n"
             "#   expected: %d\n",
             name, found, foundCount);
    set_agent_fail_status();
    return JNI_FALSE;
  }

  LOG("Make global references for threads: %d threads\n", foundCount);
  for (int i = 0; i < foundCount; i++) {
    foundThreads[i] = (jthread) jni->NewGlobalRef(foundThreads[i]);
    if (foundThreads[i] == nullptr) {
      set_agent_fail_status();
      return JNI_FALSE;
    }
    LOG("  ... thread #%d: %p\n", i, foundThreads[i]);
  }

  return JNI_TRUE;
}

/** THREAD_END callback. */
JNIEXPORT void JNICALL
callbackThreadEnd(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread) {
  /* check if event is for tested thread */
  for (int i = 0; i < THREADS_COUNT; i++) {
    if (thread != nullptr && jni->IsSameObject(threads[i], thread)) {
      LOG("  ... received THREAD_END event for thread #%d: %p\n", i, (void*)thread);
      eventsReceived++;
      return;
    }
  }
  LOG("  ... received THREAD_END event for unknown thread: %p\n", (void*)thread);
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiEnv* jvmti = nullptr;

  timeout =  60 * 1000;

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
