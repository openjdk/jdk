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
static jrawMonitorID access_lock, wait_lock;
static jthread thr_ptr = nullptr;
static jint wait_time = 0;
static jint state[] = {
    JVMTI_THREAD_STATE_RUNNABLE,
    JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER,
    JVMTI_THREAD_STATE_IN_OBJECT_WAIT
};

void printStateFlags(jint flags) {
  if (flags & JVMTI_THREAD_STATE_SUSPENDED) {
    LOG(" JVMTI_THREAD_STATE_SUSPENDED");
  }
  if (flags & JVMTI_THREAD_STATE_INTERRUPTED) {
    LOG(" JVMTI_THREAD_STATE_INTERRUPTED");
  }
  if (flags & JVMTI_THREAD_STATE_IN_NATIVE) {
    LOG(" JVMTI_THREAD_STATE_IN_NATIVE");
  }
  LOG(" (0x%0x)\n", flags);
}

void JNICALL
VMInit(jvmtiEnv *jvmti_env, JNIEnv *jni, jthread thr) {
  jvmtiError err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_START, nullptr);
  check_jvmti_status(jni, err, "Failed to enable THREAD_START event");
}

void JNICALL
ThreadStart(jvmtiEnv *jvmti_env, JNIEnv *jni, jthread thread) {
  RawMonitorLocker rml = RawMonitorLocker(jvmti, jni, access_lock);
  jvmtiThreadInfo thread_info = get_thread_info(jvmti_env, jni, thread);
  if (thread_info.name != nullptr && strcmp(thread_info.name, "tested_thread_thr1") == 0) {
    thr_ptr = jni->NewGlobalRef(thread);
    LOG(">>> ThreadStart: \"%s\", 0x%p\n", thread_info.name, thr_ptr);
  }
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiEventCallbacks callbacks;
  jint res;
  jvmtiError err;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = true;
  caps.can_suspend = true;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
   LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  access_lock = create_raw_monitor(jvmti, "_access_lock");
  wait_lock =  create_raw_monitor(jvmti, "_wait_lock");

  callbacks.VMInit = &VMInit;
  callbacks.ThreadStart = &ThreadStart;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to enable VM_INIT event: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_thrstat02_init(JNIEnv *jni, jclass cls, jint waitTime) {
  wait_time = waitTime * 60000;
}

void wait_for(JNIEnv *jni, jint millis) {
  RawMonitorLocker rml = RawMonitorLocker(jvmti, jni, wait_lock);
  rml.wait(millis);
}

JNIEXPORT jboolean JNICALL
Java_thrstat02_checkStatus0(JNIEnv *jni, jclass cls, jint statInd, jboolean suspended) {
  jboolean result = JNI_TRUE;
  jint thrState;
  jint suspState = -1;
  jint right_stat = (suspended ? JVMTI_THREAD_STATE_SUSPENDED : 0);
  jvmtiError right_ans = (suspended ? JVMTI_ERROR_THREAD_SUSPENDED : JVMTI_ERROR_NONE);
  const char *suspStr = (suspended ? ", suspended" : "");
  jvmtiError err;
  jint millis;
  jboolean timeout_is_reached;
  unsigned int waited_millis;

  if (jvmti == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return JNI_FALSE;
  }

  if (thr_ptr == nullptr) {
    LOG("Missing thread \"tested_thread_thr1\" start event\n");
    return JNI_FALSE;
  }

  LOG("START checkStatus for \"tested_thread_thr1\" (0x%p%s), check state: %s\n",
         thr_ptr, suspStr, TranslateState(state[statInd]));

  timeout_is_reached = JNI_TRUE;
  for (millis = WAIT_START, waited_millis = 0; millis < wait_time; millis <<= 1) {
    err = jvmti->GetThreadState(thr_ptr, &thrState);
    if (err != JVMTI_ERROR_NONE) {
      LOG("(GetThreadState#%d) unexpected error: %s (%d)\n", statInd, TranslateError(err), err);
      result = JNI_FALSE;
      timeout_is_reached = JNI_FALSE;
      break;
    }
    suspState = thrState & JVMTI_THREAD_STATE_SUSPENDED;
    if (suspended || (thrState & JVMTI_THREAD_STATE_RUNNABLE) == 0 || (state[statInd] == JVMTI_THREAD_STATE_RUNNABLE)) {
      timeout_is_reached = JNI_FALSE;
      break;
    }

    waited_millis += millis;
    wait_for(jni, millis);
  }

  LOG(">>> thread \"tested_thread_thr1\" (0x%p) state: %s (%d)\n",
         thr_ptr, TranslateState(thrState), thrState);
  LOG(">>>\tflags:");
  printStateFlags(suspState);

  if (timeout_is_reached == JNI_TRUE) {
    LOG("Error: timeout (%d secs) has been reached\n", waited_millis / 1000);
  }
  if ((thrState & state[statInd]) == 0) {
    LOG("#1: Wrong thread \"tested_thread_thr1\" (0x%p%s) state:\n", thr_ptr, suspStr);
    LOG("    expected: %s (%d)\n", TranslateState(state[statInd]), state[statInd]);
    LOG("      actual: %s (%d)\n", TranslateState(thrState), thrState);
    result = JNI_FALSE;
  }
  if (suspState != right_stat) {
    LOG("#2: Wrong thread \"tested_thread_thr1\" (0x%p%s) state flags:\n", thr_ptr, suspStr);
    LOG("    expected:");
    printStateFlags(right_stat);
    LOG("    actual:");
    printStateFlags(suspState);
    result = JNI_FALSE;
  }

  err = jvmti->SuspendThread(thr_ptr);
  if (err != right_ans) {
    LOG("#3: Wrong result of SuspendThread() for \"tested_thread_thr1\" (0x%p%s):\n", thr_ptr, suspStr);
    LOG("    expected: %s (%d), actual: %s (%d)\n", TranslateError(right_ans), right_ans, TranslateError(err), err);
    result = JNI_FALSE;
  }

  if (!suspended) {
    // wait till thread is not suspended
    timeout_is_reached = JNI_TRUE;
    for (millis = WAIT_START, waited_millis = 0; millis < wait_time; millis <<= 1) {
      waited_millis += millis;
      wait_for(jni, millis);
      err = jvmti->GetThreadState(thr_ptr, &thrState);
      if (err != JVMTI_ERROR_NONE) {
        LOG("(GetThreadState#%d,after) unexpected error: %s (%d)\n", statInd, TranslateError(err), err);
        timeout_is_reached = JNI_FALSE;
        result = JNI_FALSE;
        break;
      }
      suspState = thrState & JVMTI_THREAD_STATE_SUSPENDED;
      if (suspState) {
        timeout_is_reached = JNI_FALSE;
        break;
      }
    }

    if (timeout_is_reached == JNI_TRUE) {
      LOG("Error: timeout (%d secs) has been reached\n", waited_millis / 1000);
    }
    if ((thrState & state[statInd]) == 0) {
      LOG("#4: Wrong thread \"tested_thread_thr1\" (0x%p) state after SuspendThread:\n", thr_ptr);
      LOG("    expected: %s (%d)\n", TranslateState(state[statInd]), state[statInd]);
      LOG("      actual: %s (%d)\n", TranslateState(thrState), thrState);
      result = JNI_FALSE;
    }
    if (suspState != JVMTI_THREAD_STATE_SUSPENDED) {
      LOG("#5: Wrong thread \"tested_thread_thr1\" (0x%p) state flags", thr_ptr);
      LOG(" after SuspendThread:\n");
      LOG("    expected:");
      printStateFlags(JVMTI_THREAD_STATE_SUSPENDED);
      LOG("    actual:");
      printStateFlags(suspState);
      result = JNI_FALSE;
    }
    resume_thread(jvmti, jni, thr_ptr);
  }
  return result;
}

}
