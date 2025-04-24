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

#ifndef JVMTI_THREAD_HPP
#define JVMTI_THREAD_HPP

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <ctype.h>


#ifdef _WIN32

#define LL "I64"
#include <STDDEF.H>

#else // !_WIN32

#include <stdint.h>

#ifdef _LP64
#define LL "l"
#else
#define LL "ll"
#endif

#endif // !_WIN32


extern "C" {


#define STATUS_PASSED       0
#define STATUS_FAILED       2

static jvmtiEnv* agent_jvmti_env = nullptr;
static JNIEnv* agent_jni_env = nullptr;

static volatile int current_agent_status = STATUS_PASSED;

static jthread jvmti_agent_thread = nullptr;
static jvmtiStartFunction agent_thread_proc = nullptr;
static void* agent_thread_arg = nullptr;

void set_agent_fail_status() {
  current_agent_status = STATUS_FAILED;
}

jint get_agent_status() {
  return current_agent_status;
}

typedef enum { NEW, RUNNABLE, WAITING, SUSPENDED, TERMINATED } thread_state_t;

typedef struct agent_data_t {
  volatile thread_state_t thread_state;
  int last_debuggee_status;
  jrawMonitorID monitor;
} agent_data_t;

int set_agent_proc(jvmtiStartFunction proc, void* arg) {
  agent_thread_proc = proc;
  agent_thread_arg = arg;
  return JNI_TRUE;
}

static agent_data_t agent_data;


static jvmtiError init_agent_data(jvmtiEnv *jvmti_env, agent_data_t *data) {
  data->thread_state = NEW;
  data->last_debuggee_status = STATUS_PASSED;
  agent_jvmti_env = jvmti_env;
  return jvmti_env->CreateRawMonitor("agent_data_monitor", &data->monitor);
}

void exitOnError(jvmtiError error) {
  if (error != JVMTI_ERROR_NONE) {
    exit(error);
  }
}

/** Wait for sync point with Java code. */
int agent_wait_for_sync(jlong timeout) {
  static const int inc_timeout = 1000;

  jlong t = 0;
  int result = JNI_TRUE;

  RawMonitorLocker monitor_locker(agent_jvmti_env, agent_jni_env, agent_data.monitor);

  agent_data.thread_state = WAITING;

  /* SP2.2-n - notify agent is waiting and wait */
  /* SP4.1-n - notify agent is waiting and wait */
  monitor_locker.notify();

  while (agent_data.thread_state == WAITING) {
    /* SP3.2-w - wait to start test */
    /* SP6.2-w - wait to end test */
    monitor_locker.wait(inc_timeout);

    if (timeout == 0) continue;

    t += inc_timeout;

    if (t >= timeout) break;
  }

  if (agent_data.thread_state == WAITING) {
      COMPLAIN("No status sync occured for timeout: %" LL "d ms\n", timeout);
    set_agent_fail_status();
    result = JNI_FALSE;
  }

  return result;
}

/** Resume java code suspended on sync point. */
int agent_resume_sync() {
  int result;
  RawMonitorLocker monitor_locker(agent_jvmti_env, agent_jni_env, agent_data.monitor);

  if (agent_data.thread_state == SUSPENDED) {
    result = JNI_TRUE;
    agent_data.thread_state = RUNNABLE;
    /* SP5.2-n - notify suspend done */
    /* SP7.2-n - notify agent end */
    monitor_locker.notify();
  }
  else {
    COMPLAIN("Debuggee was not suspended on status sync\n");
    set_agent_fail_status();
    result = JNI_FALSE;
  }

  return JNI_TRUE;
}

/* ============================================================================= */
static void
set_agent_thread_state(thread_state_t value) {
  RawMonitorLocker monitor_locker(agent_jvmti_env, agent_jni_env, agent_data.monitor);
  agent_data.thread_state = value;
  monitor_locker.notify();
}

/** Wrapper for user agent thread. */
static void JNICALL agent_thread_wrapper(jvmtiEnv* jvmti_env, JNIEnv* agentJNI, void* arg) {
  agent_jni_env = agentJNI;

  /* run user agent proc */
  {
    set_agent_thread_state(RUNNABLE);

    // TODO was NSK_TRACE
    (*agent_thread_proc)(jvmti_env, agentJNI, agent_thread_arg);

    set_agent_thread_state(TERMINATED);
  }

  /* finalize agent thread */
  {
    /* gelete global ref for agent thread */
    agentJNI->DeleteGlobalRef(jvmti_agent_thread);
    jvmti_agent_thread = nullptr;
  }
}


/** Start wrapper for user agent thread. */
static jthread start_agent_thread_wrapper(JNIEnv *jni_env, jvmtiEnv* jvmti_env) {
  const jint  THREAD_PRIORITY = JVMTI_THREAD_MAX_PRIORITY;
  const char* THREAD_NAME = "JVMTI agent thread";
  const char* THREAD_CLASS_NAME = "java/lang/Thread";
  const char* THREAD_CTOR_NAME = "<init>";
  const char* THREAD_CTOR_SIGNATURE = "(Ljava/lang/String;)V";

  jobject threadName = nullptr;
  jclass threadClass = nullptr;
  jmethodID threadCtor = nullptr;
  jobject threadObject = nullptr;
  jobject threadGlobalRef = nullptr;
  jvmtiError err;

  threadClass = jni_env->FindClass(THREAD_CLASS_NAME);
  if (threadClass == nullptr) {
    return nullptr;
  }

  threadCtor = jni_env->GetMethodID(threadClass, THREAD_CTOR_NAME, THREAD_CTOR_SIGNATURE);
  if (threadCtor == nullptr) {
    return nullptr;
  }

  threadName = jni_env->NewStringUTF(THREAD_NAME);
  if (threadName == nullptr) {
    return nullptr;
  }

  threadObject = jni_env->NewObject(threadClass, threadCtor, threadName);
  if (threadObject == nullptr) {
    return nullptr;
  }

  threadGlobalRef = jni_env->NewGlobalRef(threadObject);
  if (threadGlobalRef == nullptr) {
    jni_env->DeleteLocalRef(threadObject);
    return nullptr;
  }
  jvmti_agent_thread = (jthread)threadGlobalRef;

  err = jvmti_env->RunAgentThread(jvmti_agent_thread, &agent_thread_wrapper, agent_thread_arg, THREAD_PRIORITY);
  if (err != JVMTI_ERROR_NONE) {
    jni_env->DeleteGlobalRef(threadGlobalRef);
    jni_env->DeleteLocalRef(threadObject);
    return nullptr;
  }
  return jvmti_agent_thread;
}

/** Run registered user agent thread via wrapper. */
static jthread run_agent_thread(JNIEnv *jni_env, jvmtiEnv* jvmti_env) {
  /* start agent thread wrapper */
  jthread thread = start_agent_thread_wrapper(jni_env, jvmti_env);
  if (thread == nullptr) {
    set_agent_fail_status();
    return nullptr;
  }

  return thread;
}

/** Sync point called from Java code. */
static jint sync_debuggee_status(JNIEnv* jni_env, jvmtiEnv* jvmti_env, jint debuggeeStatus) {
  jint result = STATUS_FAILED;

  printf("Data %p %p\n", jvmti_env, agent_data.monitor);
  RawMonitorLocker monitor_locker(agent_jvmti_env, agent_jni_env, agent_data.monitor);

  /* save last debugee status */
  agent_data.last_debuggee_status = debuggeeStatus;

  /* we don't enter if-stmt in second call */
  if (agent_data.thread_state == NEW) {
    if (run_agent_thread(jni_env, jvmti_env) == nullptr) {
      return result;
    }

    /* SP2.2-w - wait for agent thread */
    while (agent_data.thread_state == NEW) {
      monitor_locker.wait();
    }
  }

  /* wait for sync permit */
  /* we don't enter loop in first call */
  while (agent_data.thread_state != WAITING && agent_data.thread_state != TERMINATED) {
    /* SP4.2-w - second wait for agent thread */
   monitor_locker.wait();
  }

  if (agent_data.thread_state != TERMINATED) {
    agent_data.thread_state = SUSPENDED;
    /* SP3.2-n - notify to start test */
    /* SP6.2-n - notify to end test */
    monitor_locker.notify();
  } else {
    COMPLAIN("Debuggee status sync aborted because agent thread has finished\n");
    return result;
  }

  /* update status from debuggee */
  if (debuggeeStatus != STATUS_PASSED) {
    printf("FAIL: Status is %d\n", debuggeeStatus);
    set_agent_fail_status();
  }

  while (agent_data.thread_state == SUSPENDED) {
    /* SP5.2-w - wait while testing */
    /* SP7.2 - wait for agent end */
    monitor_locker.wait();
  }

  agent_data.last_debuggee_status = get_agent_status();
  result = agent_data.last_debuggee_status;
  return result;
}

/** Native function for Java code to provide sync point. */
JNIEXPORT jint JNICALL
Java_jdk_test_lib_jvmti_DebugeeClass_checkStatus(JNIEnv* jni_env, jclass cls, jint debuggeeStatus) {
  jint status;
  printf("Synchronization point checkStatus(%d) called.\n", debuggeeStatus);
  status = sync_debuggee_status(jni_env, agent_jvmti_env, debuggeeStatus);
  return status;
}


/** Native function for Java code to reset agent data. */
JNIEXPORT void JNICALL
Java_jdk_test_lib_jvmti_DebugeeClass_resetAgentData(JNIEnv* jni, jclass cls) {
  RawMonitorLocker monitor_locker(agent_jvmti_env, jni, agent_data.monitor);
  /* wait for agent_thread_wrapper() to finish */
  while (agent_data.thread_state != TERMINATED) {
    monitor_locker.wait(10);
  }
  agent_data.thread_state = NEW;
  agent_data.last_debuggee_status = STATUS_PASSED;
}



}

#endif
