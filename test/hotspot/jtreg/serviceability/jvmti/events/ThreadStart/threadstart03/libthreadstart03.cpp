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
#define WAIT_TIME 1000

static jvmtiEnv *jvmti = nullptr;
static jvmtiEventCallbacks callbacks;
static jint result = PASSED;
static jrawMonitorID wait_lock;
static const char *threadName = nullptr;
static int startsCount = 0;
static int startsExpected = 0;
static int endsCount = 0;
static int endsExpected = 0;

void JNICALL ThreadStart(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  jvmtiError err;
  jvmtiThreadInfo inf;

  err = jvmti->GetThreadInfo(thread, &inf);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetThreadInfo, start) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  LOG(">>> start: %s\n", inf.name);

  if (inf.name != nullptr && strcmp(inf.name, threadName) == 0) {
    startsCount++;
  }
}

void JNICALL ThreadEnd(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  jvmtiError err;
  jvmtiThreadInfo inf;

  err = jvmti->GetThreadInfo(thread, &inf);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetThreadInfo, end) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  LOG(">>> end: %s\n", inf.name);

  if (inf.name != nullptr && strcmp(inf.name, threadName) == 0) {
    endsCount++;
  }
}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jvmtiError err;
  jint res;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  callbacks.ThreadStart = &ThreadStart;
  callbacks.ThreadEnd = &ThreadEnd;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  return JNI_OK;
}

static void JNICALL
threadProc(jvmtiEnv* jvmti, JNIEnv* jni, void *unused) {
  RawMonitorLocker wait_locker(jvmti, jni, wait_lock);
  wait_locker.notify();
}

JNIEXPORT jint JNICALL
Java_threadstart03_check(JNIEnv *jni, jclass cls, jthread thr, jstring name) {
  jvmtiError err;

  if (jvmti == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return STATUS_FAILED;
  }

  threadName = jni->GetStringUTFChars(name, nullptr);
  if (threadName == nullptr) {
    LOG("Failed to copy UTF-8 string!\n");
    return STATUS_FAILED;
  }

  wait_lock = create_raw_monitor(jvmti, "_wait_lock");

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_START, nullptr);
  if (err == JVMTI_ERROR_NONE) {
    startsExpected = 1;
  } else {
    LOG("Failed to enable JVMTI_EVENT_THREAD_START: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_END, nullptr);
  if (err == JVMTI_ERROR_NONE) {
    endsExpected = 1;
  } else {
    LOG("Failed to enable JVMTI_EVENT_THREAD_END: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  LOG(">>> starting agent thread ...\n");

  {
    RawMonitorLocker wait_locker(jvmti, jni, wait_lock);
    err = jvmti->RunAgentThread(thr, threadProc, nullptr, JVMTI_THREAD_MAX_PRIORITY);
    if (err != JVMTI_ERROR_NONE) {
      LOG("(RunAgentThread) unexpected error: %s (%d)\n", TranslateError(err), err);
      result = STATUS_FAILED;
    }
    wait_locker.wait();
  }

  {
    RawMonitorLocker wait_locker(jvmti, jni, wait_lock);
    // Wait for up to 3 seconds for the thread end event

    for (int i = 0; i < 3; i++) {
      wait_locker.wait( (jlong) WAIT_TIME);
      if (endsCount == endsExpected || err != JVMTI_ERROR_NONE) {
        break;
      }
    }

  }

  err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_THREAD_START, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to disable JVMTI_EVENT_THREAD_START: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_THREAD_END, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to disable JVMTI_EVENT_THREAD_END: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  if (startsCount != startsExpected) {
    LOG("Wrong number of thread start events: %d, expected: %d\n", startsCount, startsExpected);
    result = STATUS_FAILED;
  }

  if (endsCount != endsExpected) {
    LOG("Wrong number of thread end events: %d, expected: %d\n", endsCount, endsExpected);
    result = STATUS_FAILED;
  }

  return result;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

}
