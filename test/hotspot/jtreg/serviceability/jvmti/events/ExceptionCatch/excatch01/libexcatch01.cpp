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

typedef struct {
  char *name;
  char *c_cls;
  char *c_name;
  char *c_sig;
  jlocation c_loc;
} writable_exceptionInfo;

typedef struct {
  const char *name;
  const char *c_cls;
  const char *c_name;
  const char *c_sig;
  jlocation c_loc;
} exceptionInfo;

static jvmtiEnv *jvmti = nullptr;
static jvmtiCapabilities caps;
static jvmtiEventCallbacks callbacks;
static jint result = PASSED;

static exceptionInfo exs[] = {
  { "Lexcatch01c;",
    "Lexcatch01a;", "run", "()V", 14 },
  { "Ljava/lang/ArithmeticException;",
    "Lexcatch01a;", "run", "()V", 24 },
  { "Ljava/lang/ArrayIndexOutOfBoundsException;",
    "Lexcatch01a;", "run", "()V", 34 }
};

static jboolean isVirtualExpected = JNI_FALSE;
static int eventsCount = 0;
static int eventsExpected = 0;

void JNICALL
ExceptionCatch(jvmtiEnv *jvmti, JNIEnv *jni, jthread thr, jmethodID method, jlocation location, jobject exception) {
  jvmtiError err;
  jclass cls;
  writable_exceptionInfo ex;
  char *generic;

  LOG(">>> retrieving ExceptionCatch info ...\n");

  cls = jni->GetObjectClass(exception);
  err = jvmti->GetClassSignature(cls, &ex.name, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetClassSignature#e) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetMethodDeclaringClass(method, &cls);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodDeclaringClass) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetClassSignature(cls, &ex.c_cls, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetClassSignature#c) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetMethodName(method, &ex.c_name, &ex.c_sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodName) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  ex.c_loc = location;
  LOG(">>> %s\n", ex.name);
  LOG(">>>    catch at %s.%s%s:0x%x%08x\n", ex.c_cls, ex.c_name, ex.c_sig, (jint)(ex.c_loc >> 32), (jint)ex.c_loc);
  LOG(">>> ... done\n");

  bool found = false;
  for (size_t i = 0; i < sizeof(exs)/sizeof(exceptionInfo); i++) {
    if (ex.name != nullptr && strcmp(ex.name, exs[i].name) == 0
        && ex.c_cls != nullptr && strcmp(ex.c_cls, exs[i].c_cls) == 0
        && ex.c_name != nullptr && strcmp(ex.c_name, exs[i].c_name) == 0
        && ex.c_sig != nullptr && strcmp(ex.c_sig, exs[i].c_sig) == 0
        && ex.c_loc == exs[i].c_loc) {
      jboolean isVirtual = jni->IsVirtualThread(thr);
      if (isVirtualExpected != isVirtual) {
        LOG("The thread IsVirtualThread %d differs from expected %d.\n", isVirtual, isVirtualExpected);
        result = STATUS_FAILED;
      } else {
        eventsCount++;
      }
      found = true;
      break;
    }
  }
  if (!found) {
    LOG("Unexpected exception catch event:\n");
    LOG("  %s\n", ex.name);
    LOG("     catch at %s.%s%s:0x%x%08x\n", ex.c_cls, ex.c_name, ex.c_sig, (jint)(ex.c_loc >> 32), (jint)ex.c_loc);
    result = STATUS_FAILED;
  }
}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;
  jint res;


  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_generate_exception_events = 1;
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

  if (caps.can_generate_exception_events) {
    callbacks.ExceptionCatch = &ExceptionCatch;
    err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (err != JVMTI_ERROR_NONE) {
      LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
      return JNI_ERR;
    }
  } else {
    LOG("Warning: Exception event is not implemented\n");
  }

  return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_excatch01_check(JNIEnv *jni, jclass cls) {
  jvmtiError err;
  jclass clz;
  jmethodID mid;
  jthread thread;

  if (jvmti == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return STATUS_FAILED;
  }

  clz = jni->FindClass("excatch01c");
  if (clz == nullptr) {
    LOG("Cannot find excatch01c class!\n");
    return STATUS_FAILED;
  }
  clz = jni->FindClass("excatch01b");
  if (clz == nullptr) {
    LOG("Cannot find excatch01b class!\n");
    return STATUS_FAILED;
  }
  clz = jni->FindClass("excatch01a");
  if (clz == nullptr) {
    LOG("Cannot find excatch01a class!\n");
    return STATUS_FAILED;
  }
  mid = jni->GetStaticMethodID(clz, "run", "()V");
  if (mid == nullptr) {
    LOG("Cannot find method run!\n");
    return STATUS_FAILED;
  }

  err = jvmti->GetCurrentThread(&thread);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to get current thread: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return STATUS_FAILED;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_EXCEPTION_CATCH, nullptr);
  if (err == JVMTI_ERROR_NONE) {
      eventsExpected = sizeof(exs)/sizeof(exceptionInfo);
  } else {
      LOG("Failed to enable JVMTI_EVENT_EXCEPTION_CATCH: %s (%d)\n", TranslateError(err), err);
      result = STATUS_FAILED;
  }

  eventsCount = 0;
  isVirtualExpected = jni->IsVirtualThread(thread);
  jni->CallStaticVoidMethod(clz, mid);
  err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_EXCEPTION_CATCH, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to disable JVMTI_EVENT_EXCEPTION_CATCH: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  if (eventsCount != eventsExpected) {
    LOG("Wrong number of exception catch events: %d, expected: %d\n", eventsCount, eventsExpected);
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
