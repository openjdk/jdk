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
  char *t_cls;
  char *t_name;
  char *t_sig;
  jlocation t_loc;
  char *c_cls;
  char *c_name;
  char *c_sig;
  jlocation c_loc;
} writable_exceptionInfo;

typedef struct {
  const char *name;
  const char *t_cls;
  const char *t_name;
  const char *t_sig;
  jlocation t_loc;
  const char *c_cls;
  const char *c_name;
  const char *c_sig;
  jlocation c_loc;
} exceptionInfo;

static jvmtiEnv *jvmti_env = nullptr;
static jvmtiEventCallbacks callbacks;
static jint result = PASSED;
static exceptionInfo exs[] = {
    { "Lexception01c;",
        "Lexception01b;", "meth1", "()V", 7,
        "Lexception01a;", "run", "()V", 14 },
    { "Ljava/lang/ArithmeticException;",
        "Lexception01b;", "meth2", "(I)I", 3,
        "Lexception01a;", "run", "()V", 24 },
    { "Ljava/lang/ArrayIndexOutOfBoundsException;",
        "Lexception01b;", "meth3", "(I)I", 10,
        "Lexception01a;", "run", "()V", 34 }
};
static jboolean isVirtualExpected = JNI_FALSE;
static int eventsCount = 0;
static int eventsExpected = 0;

void JNICALL
Exception(jvmtiEnv *jvmti, JNIEnv *jni, jthread thr,
          jmethodID method, jlocation location, jobject exception,
          jmethodID catch_method, jlocation catch_location) {
  jvmtiError err;
  writable_exceptionInfo ex;
  jclass cls;
  char *generic;

  LOG(">>> retrieving Exception info ...\n");

  cls = jni->GetObjectClass(exception);
  err = jvmti->GetClassSignature(cls, &ex.name, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetClassSignature) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetMethodDeclaringClass(method, &cls);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodDeclaringClass#t) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetClassSignature(cls, &ex.t_cls, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetClassSignature#t) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetMethodName(method, &ex.t_name, &ex.t_sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodName#t) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  ex.t_loc = location;
  err = jvmti->GetMethodDeclaringClass(catch_method, &cls);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodDeclaringClass#c) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetClassSignature(cls, &ex.c_cls, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetClassSignature#c) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetMethodName(catch_method, &ex.c_name, &ex.c_sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodName#c) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  ex.c_loc = catch_location;
  LOG(">>> %s\n", ex.name);
  LOG(">>>   thrown at %s.%s%s:0x%x%08x\n",
         ex.t_cls, ex.t_name, ex.t_sig, (jint)(ex.t_loc >> 32), (jint)ex.t_loc);
  LOG(">>>    catch at %s.%s%s:0x%x%08x\n",
         ex.c_cls, ex.c_name, ex.c_sig, (jint)(ex.c_loc >> 32), (jint)ex.c_loc);
  LOG(">>> ... done\n");

  bool found = false;
  for (size_t i = 0; i < sizeof(exs)/sizeof(exceptionInfo); i++) {
    if (ex.name != nullptr && strcmp(ex.name, exs[i].name) == 0
        && ex.t_cls != nullptr && strcmp(ex.t_cls, exs[i].t_cls) == 0
        && ex.t_name != nullptr && strcmp(ex.t_name, exs[i].t_name) == 0
        && ex.t_sig != nullptr && strcmp(ex.t_sig, exs[i].t_sig) == 0
        && ex.c_cls != nullptr && strcmp(ex.c_cls, exs[i].c_cls) == 0
        && ex.c_name != nullptr && strcmp(ex.c_name, exs[i].c_name) == 0
        && ex.c_sig != nullptr && strcmp(ex.c_sig, exs[i].c_sig) == 0
        && ex.t_loc == exs[i].t_loc && ex.c_loc == exs[i].c_loc) {
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
    LOG("Unexpected exception event:\n");
    LOG("  %s\n", ex.name);
    LOG("    thrown at %s.%s%s:0x%x%08x\n",
           ex.t_cls, ex.t_name, ex.t_sig, (jint)(ex.t_loc >> 32), (jint)ex.t_loc);
    LOG("     catch at %s.%s%s:0x%x%08x\n",
           ex.c_cls, ex.c_name, ex.c_sig, (jint)(ex.c_loc >> 32), (jint)ex.c_loc);
    result = STATUS_FAILED;
  }
}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jvmtiError err;
  jint res;
  jvmtiCapabilities caps;

  res = jvm->GetEnv((void **) &jvmti_env, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti_env == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_generate_exception_events = 1;
  caps.can_support_virtual_threads = 1;

  err = jvmti_env->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti_env->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  if (caps.can_generate_exception_events) {
    callbacks.Exception = &Exception;
    err = jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks));
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
Java_exception01_check(JNIEnv *jni, jclass cls) {
  jvmtiError err;
  jthread thread;
  jclass clz;
  jmethodID mid;

  if (jvmti_env == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return STATUS_FAILED;
  }
  clz = jni->FindClass("exception01c");
  if (clz == nullptr) {
    LOG("Cannot find exception01c class!\n");
    return STATUS_FAILED;
  }
  clz = jni->FindClass("exception01b");
  if (clz == nullptr) {
    LOG("Cannot find exception01b class!\n");
    return STATUS_FAILED;
  }
  clz = jni->FindClass("exception01a");
  if (clz == nullptr) {
    LOG("Cannot find exception01a class!\n");
    return STATUS_FAILED;
  }
  mid = jni->GetStaticMethodID(clz, "run", "()V");
  if (mid == nullptr) {
    LOG("Cannot find method run!\n");
    return STATUS_FAILED;
  }

  err = jvmti_env->GetCurrentThread(&thread);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to get current thread: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return STATUS_FAILED;
  }

  err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_EXCEPTION, thread);
  if (err == JVMTI_ERROR_NONE) {
    eventsExpected = sizeof(exs)/sizeof(exceptionInfo);
  } else {
    LOG("Failed to enable JVMTI_EVENT_EXCEPTION: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  eventsCount = 0;
  isVirtualExpected = jni->IsVirtualThread(thread);

  jni->CallStaticVoidMethod(clz, mid);

  err = jvmti_env->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_EXCEPTION, thread);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to disable JVMTI_EVENT_EXCEPTION: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  if (eventsCount != eventsExpected) {
    LOG("Wrong number of exception events: %d, expected: %d\n", eventsCount, eventsExpected);
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
