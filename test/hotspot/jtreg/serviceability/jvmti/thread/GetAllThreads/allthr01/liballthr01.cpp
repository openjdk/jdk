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
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

typedef struct {
  int cnt;
  const char **thr_names;
} info;

typedef struct  {
  info expected;
  info unexpected;
} thread_info;

static jvmtiEnv *jvmti_env;
static jrawMonitorID starting_agent_thread_lock;
static jrawMonitorID stopping_agent_thread_lock;

static const char main_name[] = "main";
static const char thread1_name[] = "thread1";
static const char sys_thread_name[] = "SysThread";
// Test uses -Djdk.virtualThreadScheduler.maxPoolSize=1
// to make name of carrier thread deterministic
static const char fj_thread_name[] = "ForkJoinPool-1-worker-1";

static const char *main_only[] = { main_name };
static const char *thr1_only[] = { thread1_name };
static const char *sys_only[] = { sys_thread_name };
static const char *main_thr1[] = { main_name, thread1_name };
static const char *main_sys[] = { main_name, sys_thread_name };
static const char *thr1_sys[] = { thread1_name, sys_thread_name };
static const char *main_fj[] = { main_name, fj_thread_name };

static thread_info thr_info[] = {
  {{1, main_only},    {2, thr1_sys}},
  {{1, main_only},    {2, thr1_sys}},
  {{2, main_thr1},    {1, sys_only}},
  {{1, main_only},    {2, thr1_sys}},
  {{2, main_sys},     {1, thr1_only}},
  {{2, main_fj},      {1, thr1_sys}}
};

jthread create_jthread(JNIEnv *jni) {
  jclass thr_class = jni->FindClass("java/lang/Thread");
  jmethodID cid = jni->GetMethodID(thr_class, "<init>", "(Ljava/lang/String;)V");
  jstring thread_name = jni->NewStringUTF(sys_thread_name);
  jthread res = jni->NewObject(thr_class, cid, thread_name);
  jni->DeleteLocalRef(thread_name);
  return res;
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
  if (res != JNI_OK || jvmti_env == nullptr) {
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
  jint threads_count = -1;
  jthread *threads;
  jvmtiThreadInfo inf;

  LOG(" >>> Check point: %d\n", idx);

  jvmtiError err = jvmti_env->GetAllThreads(&threads_count, &threads);
  check_jvmti_status(jni, err, "Failed in GetAllThreads");

  // check unexpected threads
  for (int i = 0; i < threads_count; i++) {
    err = jvmti_env->GetThreadInfo(threads[i], &inf);
    check_jvmti_status(jni, err, "Failed in GetThreadInfo");
    char *name = get_thread_name(jvmti_env, jni, threads[i]);
    LOG(" >>> %s", name);

    bool found = false;
    for (int j = 0; j < thr_info[idx].unexpected.cnt && !found; j++) {
      found = strcmp(name, thr_info[idx].unexpected.thr_names[j]) == 0;
    }
    if (found) {
      LOG("Point %d: detected unexpected thread %s\n", idx, inf.name);
      return JNI_FALSE;
    }
  }

  LOG("\n");

  // verify all expected threads are present
  for (int i = 0; i < thr_info[idx].expected.cnt; i++) {
    bool found = false;
    for (int j = 0; j < threads_count && !found; j++) {
      char *name = get_thread_name(jvmti_env, jni, threads[j]);
      found = strcmp(name, thr_info[idx].expected.thr_names[i]) == 0;
    }
    if (!found) {
      LOG("Point %d: thread %s not detected\n", idx, thr_info[idx].expected.thr_names[i]);
      return JNI_FALSE;
    }
  }

  deallocate(jvmti_env, jni, threads);
  return JNI_TRUE;
}

JNIEXPORT void
Java_allthr01_startAgentThread(JNIEnv *jni) {
  RawMonitorLocker rml1 = RawMonitorLocker(jvmti_env, jni, starting_agent_thread_lock);
  jvmtiError err = jvmti_env->RunAgentThread(create_jthread(jni),
                                             sys_thread, nullptr,JVMTI_THREAD_NORM_PRIORITY);
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

JNIEXPORT jboolean JNICALL
Java_allthr01_checkInfo0(JNIEnv *env, jclass cls, jint expected_idx) {
  return check_info(env, expected_idx);
}

}
