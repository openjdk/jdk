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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jvmti.h"
#include "jvmti_common.h"

extern "C" {

typedef struct {
  int cnt;
  const char **thr_names;
} info;

static jvmtiEnv *jvmti_env;
static jrawMonitorID starting_agent_thread_lock;
static jrawMonitorID stopping_agent_thread_lock;
static int system_threads_count;
static const char *names0[] = {"main"};
static const char *names1[] = {"main", "thread1"};
static const char *names2[] = {"main", "Thread-"};
static const char *names3[] = {"main", "ForkJoinPool-"};

/*
 * Expected number and names of threads started by test for each test point
 */
static info expected_thread_info[] = {
    {1, names0}, {1, names0}, {2, names1},
    {1, names0}, {2, names2},  {2, names3}
};

const char VTHREAD_PREFIX[] = "ForkJoinPool";


jthread create_jthread(JNIEnv *jni) {
  jclass thrClass = jni->FindClass("java/lang/Thread");
  jmethodID cid = jni->GetMethodID(thrClass, "<init>", "()V");
  return jni->NewObject(thrClass, cid);
}

static void JNICALL
sys_thread(jvmtiEnv *jvmti, JNIEnv *jni, void *p) {
  RawMonitorLocker rml2 = RawMonitorLocker(jvmti, jni, stopping_agent_thread_lock);
  {
    RawMonitorLocker rml1 = RawMonitorLocker(jvmti, jni, starting_agent_thread_lock);
    rml1.notify();
  }
  rml2.wait();
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;
  jint res;

  res = jvm->GetEnv((void **) &jvmti_env, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti_env == NULL) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }
  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = true;

  err = jvmti_env->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  starting_agent_thread_lock = create_raw_monitor(jvmti_env, "_started_agent_thread_lock");
  stopping_agent_thread_lock = create_raw_monitor(jvmti_env, "_stopping_agent_thread_lock");

  return JNI_OK;
}

JNIEXPORT jboolean check_info(JNIEnv *jni, int idx) {
  jboolean result = JNI_TRUE;
  jint threads_count = -1;
  jthread *threads;
  int num_unexpected = 0;

  LOG(" >>> Check point: %d\n", idx);

  jvmtiError err = jvmti_env->GetAllThreads(&threads_count, &threads);
  check_jvmti_status(jni, err, "Failed in GetAllThreads");

  for (int i = 0; i < threads_count; i++) {
    if (!isThreadExpected(jvmti_env, threads[i])) {
      num_unexpected++;
      LOG(">>> unexpected:  ");
    } else {
      LOG(">>> expected: ");
    }
    print_thread_info(jvmti_env, jni, threads[i]);
  }

  if (threads_count - num_unexpected != expected_thread_info[idx].cnt + system_threads_count) {
    LOG("Point %d: number of threads expected: %d, got: %d\n",
           idx, expected_thread_info[idx].cnt + system_threads_count, threads_count - num_unexpected);
    return JNI_FALSE;
  }

  for (int i = 0; i < expected_thread_info[idx].cnt; i++) {
    bool found = false;
    for (int j = 0; j < threads_count && !found; j++) {
      char *name = get_thread_name(jvmti_env, jni, threads[j]);
      found = strstr(name, expected_thread_info[idx].thr_names[i]);
      if (found) {
        LOG(" >>> found: %s\n", name);
      }
    }

    if (!found) {
      LOG("Point %d: thread %s not detected\n", idx, expected_thread_info[idx].thr_names[i]);
      result = JNI_FALSE;
    }
  }

  deallocate(jvmti_env, jni, threads);

  return result;
}

JNIEXPORT void Java_allthr01_startAgentThread(JNIEnv *jni) {
  RawMonitorLocker rml1 = RawMonitorLocker(jvmti_env, jni, starting_agent_thread_lock);
  jvmtiError err = jvmti_env->RunAgentThread(create_jthread(jni),
                                             sys_thread, NULL,JVMTI_THREAD_NORM_PRIORITY);
  check_jvmti_status(jni, err, "Failed to run AgentThread");
  rml1.wait();
  LOG("Started Agent Thread\n");
}

JNIEXPORT void
Java_allthr01_stopAgentThread(JNIEnv *jni) {
  RawMonitorLocker rml2 = RawMonitorLocker(jvmti_env, jni, stopping_agent_thread_lock);
  rml2.notify();
  LOG("Stopped Agent Thread\n");
}



JNIEXPORT void JNICALL
Java_allthr01_setSysCnt(JNIEnv *jni, jclass cls) {
  jint threadsCount = -1;
  jthread *threads;

  jvmtiError err = jvmti_env->GetAllThreads(&threadsCount, &threads);
  check_jvmti_status(jni,err, "Failed in GetAllThreads");

  system_threads_count = threadsCount - 1;

  for (int i = 0; i < threadsCount; i++) {
    if (!isThreadExpected(jvmti_env, threads[i])) {
      system_threads_count--;
    }
  }

  LOG(" >>> number of system threads: %d\n", system_threads_count);
}

JNIEXPORT jboolean JNICALL
Java_allthr01_checkInfo0(JNIEnv *env, jclass cls, jint expected_idx) {
  return check_info(env, expected_idx);
}

}
