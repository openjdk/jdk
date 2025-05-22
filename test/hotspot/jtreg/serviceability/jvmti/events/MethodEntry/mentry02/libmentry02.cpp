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
#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {


#define PASSED 0
#define STATUS_FAILED 2

static jvmtiEnv *jvmti = nullptr;
static jvmtiEventCallbacks callbacks;
static jint result = PASSED;
static jboolean isVirtualExpected = JNI_FALSE;
static int MethodEntriesExpected = 0;
static int MethodExitsExpected = 0;
static int MethodEntriesCount = 0;
static int MethodExitsCount = 0;
static jmethodID mid = nullptr;

void JNICALL MethodEntry(jvmtiEnv *jvmti, JNIEnv *jni,
                         jthread thread_obj, jmethodID method) {
  if (mid == method) {
    jboolean isVirtual = jni->IsVirtualThread(thread_obj);
    if (isVirtualExpected != isVirtual) {
      LOG("The thread IsVirtualThread %d differs from expected %d.\n", isVirtual, isVirtualExpected);
      result = STATUS_FAILED;
    } else {
      MethodEntriesCount++;
    }
  }
}

void JNICALL MethodExit(jvmtiEnv *jvmti, JNIEnv *jni,
                        jthread thread_obj, jmethodID method,
                        jboolean was_poped_by_exc, jvalue return_value) {
  if (mid == method) {
    jboolean isVirtual = jni->IsVirtualThread(thread_obj);
    if (isVirtualExpected != isVirtual) {
      LOG("The thread IsVirtualThread %d differs from expected %d.\n", isVirtual, isVirtualExpected);
      result = STATUS_FAILED;
    } else {
      MethodExitsCount++;
    }
  }
}

jint  Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jint res;
  jvmtiError err;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_generate_method_entry_events = 1;
  caps.can_generate_method_exit_events = 1;
  caps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  if (caps.can_generate_method_entry_events && caps.can_generate_method_exit_events) {
    callbacks.MethodEntry = &MethodEntry;
    callbacks.MethodExit = &MethodExit;
    err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (err != JVMTI_ERROR_NONE) {
      LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
      return JNI_ERR;
    }
  } else {
    LOG("Warning: MethodEntry or MethodExit event is not implemented\n");
  }

  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_mentry02_getReady(JNIEnv *jni, jclass cls, jint i) {
  jvmtiError err;

  if (jvmti == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return;
  }

  jthread thread;
  err = jvmti->GetCurrentThread(&thread);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to get current thread: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }
  isVirtualExpected = jni->IsVirtualThread(thread);

  mid = jni->GetStaticMethodID(cls, "emptyMethod", "()V");
  if (mid == nullptr) {
    LOG("Cannot find Method ID for emptyMethod\n");
    result = STATUS_FAILED;
    return;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY, nullptr);
  if (err == JVMTI_ERROR_NONE) {
    MethodEntriesCount = 0;
    MethodEntriesExpected = i;
  } else {
    LOG("Failed to enable JVMTI_EVENT_METHOD_ENTRY event: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_METHOD_EXIT, nullptr);
  if (err == JVMTI_ERROR_NONE) {
    MethodExitsCount = 0;
    MethodExitsExpected = i;
  } else {
    LOG("Failed to enable JVMTI_EVENT_METHOD_EXIT event: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }
}

JNIEXPORT jint JNICALL
Java_mentry02_check(JNIEnv *jni, jclass cls) {
  LOG(">>> MethodEntry events: %d, MethodExit events: %d\n", MethodEntriesCount, MethodExitsCount);
  if (MethodEntriesCount != MethodEntriesExpected) {
    LOG("Wrong number of method entry events: %d, expected: %d\n", MethodEntriesCount, MethodEntriesExpected);
    result = STATUS_FAILED;
  }
  if (MethodExitsCount != MethodExitsExpected) {
    LOG("Wrong number of method exit events: %d, expected: %d\n", MethodExitsCount, MethodExitsExpected);
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
