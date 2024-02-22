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
#include "jvmti_common.h"


extern "C" {


#define PASSED 0
#define STATUS_FAILED 2

static jvmtiEnv *jvmti = nullptr;
static jvmtiEventCallbacks callbacks;
static jint result = PASSED;
static int eventsCount = 0;
static int eventsExpected = 0;
static const char *prefix = nullptr;

void JNICALL
ThreadStart(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  jvmtiThreadInfo inf;
  char name[32];

  jvmtiError err = jvmti->GetThreadInfo(thread, &inf);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetThreadInfo#%d) unexpected error: %s (%d)\n", eventsCount, TranslateError(err), err);
    result = STATUS_FAILED;
  }

  LOG(">>> %s\n", inf.name);

  if (inf.name != nullptr && strstr(inf.name, prefix) == inf.name) {
    snprintf(name, sizeof(name), "%s%d", prefix, eventsCount);
    if (strcmp(name, inf.name) != 0) {
      LOG("(#%d) wrong thread name: \"%s\"", eventsCount, inf.name);
      LOG(", expected: \"%s\"\n", name);
      result = STATUS_FAILED;
    }
    eventsCount++;
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
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_threadstart01_getReady(JNIEnv *jni, jclass cls, jint i, jstring name) {
  jvmtiError err;

  if (jvmti == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return;
  }

  prefix = jni->GetStringUTFChars(name, nullptr);
  if (prefix == nullptr) {
    LOG("Failed to copy UTF-8 string!\n");
    result = STATUS_FAILED;
    return;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_THREAD_START, nullptr);
  if (err == JVMTI_ERROR_NONE) {
    eventsExpected = i;
  } else {
    LOG("Failed to enable JVMTI_EVENT_THREAD_START: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }
}

JNIEXPORT jint JNICALL
Java_threadstart01_check(JNIEnv *jni, jclass cls) {
  jvmtiError err;

  if (jvmti == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return STATUS_FAILED;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_THREAD_START, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to disable JVMTI_EVENT_THREAD_START: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  if (eventsCount != eventsExpected) {
    LOG("Wrong number of thread start events: %d, expected: %d\n", eventsCount, eventsExpected);
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
