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
#include <inttypes.h>
#include "jvmti.h"
#include "jvmti_common.h"

extern "C" {


#define PASSED 0
#define STATUS_FAILED 2

typedef struct {
  const char *cls_sig;
  const char *name;
  const char *sig;
  jlocation loc;
} pop_info;

static jvmtiEnv *jvmti = nullptr;
static jvmtiEventCallbacks callbacks;
static jint result = PASSED;
static volatile jboolean isVirtualExpected = JNI_FALSE;
static size_t eventsExpected = 0;
static size_t eventsCount = 0;
static pop_info pops[] = {
    { "Lframepop01;", "chain", "()V", 0 },
    { "Lframepop01a;", "dummy", "()V", 3 },
};

void JNICALL Breakpoint(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jmethodID method, jlocation location) {
  jvmtiError err;

  err = jvmti->NotifyFramePop(thread, 0);
  if (err == JVMTI_ERROR_NONE) {
    eventsExpected++;
  } else {
    LOG("(NotifyFramePop#0) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  err = jvmti->NotifyFramePop(thread, 1);
  if (err == JVMTI_ERROR_NONE) {
    eventsExpected++;
  } else {
    LOG("(NotifyFramePop#1) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }
}

void JNICALL FramePop(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread_obj, jmethodID method, jboolean wasPopedByException) {
  jvmtiError err;
  char *cls_sig, *name, *sig, *generic;
  jclass cls;
  jmethodID mid;
  jlocation loc;

  LOG(">>> retrieving frame pop info ...\n");

  err = jvmti->GetMethodDeclaringClass(method, &cls);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodDeclaringClass) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetClassSignature(cls, &cls_sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetClassSignature) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetMethodName(method, &name, &sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodName) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return;
  }
  err = jvmti->GetFrameLocation(thread_obj, 0, &mid, &loc);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetFrameLocation) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  LOG(">>>      class: \"%s\"\n", cls_sig);
  LOG(">>>     method: \"%s%s\"\n", name, sig);
  LOG(">>>   location: 0x%x%08x\n", (jint)(loc >> 32), (jint)loc);
  print_thread_info(jvmti, jni, thread_obj);
  LOG(">>> ... done\n");

  if (eventsCount < sizeof(pops)/sizeof(pop_info)) {
    if (cls_sig == nullptr || strcmp(cls_sig, pops[eventsCount].cls_sig) != 0) {
      LOG("(pop#%" PRIuPTR ") wrong class: \"%s\"", eventsCount, cls_sig);
      LOG(", expected: \"%s\"\n", pops[eventsCount].cls_sig);
      result = STATUS_FAILED;
    }
    if (name == nullptr || strcmp(name, pops[eventsCount].name) != 0) {
      LOG("(pop#%" PRIuPTR ") wrong method name: \"%s\"", eventsCount, name);
      LOG(", expected: \"%s\"\n", pops[eventsCount].name);
      result = STATUS_FAILED;
    }
    if (sig == nullptr || strcmp(sig, pops[eventsCount].sig) != 0) {
      LOG("(pop#%" PRIuPTR ") wrong method sig: \"%s\"", eventsCount, sig);
      LOG(", expected: \"%s\"\n", pops[eventsCount].sig);
      result = STATUS_FAILED;
    }
    if (loc != pops[eventsCount].loc) {
      LOG("(pop#%" PRIuPTR ") wrong location: 0x%x%08x", eventsCount, (jint)(loc >> 32), (jint)loc);
      LOG(", expected: 0x%x\n", (jint)pops[eventsCount].loc);
      result = STATUS_FAILED;
    }
    jboolean isVirtual = jni->IsVirtualThread(thread_obj);
    if (isVirtualExpected != isVirtual) {
      LOG("The thread IsVirtualThread %d differs from expected %d.\n", isVirtual, isVirtualExpected);
      result = STATUS_FAILED;
    }
  } else {
    LOG("Unexpected frame pop catched:");
    LOG("     class: \"%s\"\n", cls_sig);
    LOG("    method: \"%s%s\"\n", name, sig);
    LOG("  location: 0x%x%08x\n", (jint)(loc >> 32), (jint)loc);
    result = STATUS_FAILED;
  }
  eventsCount++;
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
  caps.can_generate_frame_pop_events = 1;
  caps.can_generate_breakpoint_events = 1;
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

  if (caps.can_generate_frame_pop_events && caps.can_generate_breakpoint_events) {
    callbacks.Breakpoint = &Breakpoint;
    callbacks.FramePop = &FramePop;
    err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (err != JVMTI_ERROR_NONE) {
      LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
      return JNI_ERR;
    }
  } else {
    LOG("Warning: FramePop or Breakpoint event is not implemented\n");
  }

  return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_framepop01_check(JNIEnv *jni, jclass cls) {
  jvmtiError err;
  jclass clz;
  jmethodID mid;
  jthread thread;

  err = jvmti->GetCurrentThread(&thread);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to get current thread: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return STATUS_FAILED;
  }

  if (jvmti == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return STATUS_FAILED;
  }

  mid = jni->GetStaticMethodID(cls, "chain", "()V");
  if (mid == 0) {
    LOG("Cannot find Method ID for method chain\n");
    return STATUS_FAILED;
  }
  err = jvmti->SetBreakpoint(mid, 0);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to SetBreakpoint: %s (%d)\n", TranslateError(err), err);
    return STATUS_FAILED;
  }
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FRAME_POP, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to enable JVMTI_EVENT_FRAME_POP event: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to enable BREAKPOINT event: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  clz = jni->FindClass("framepop01a");
  if (clz == nullptr) {
    LOG("Cannot find framepop01a class!\n");
    result = STATUS_FAILED;
    return STATUS_FAILED;
  }

  mid = jni->GetStaticMethodID(clz, "dummy", "()V");
  if (mid == 0) {
    LOG("Cannot find Method ID for method dummy\n");
    return STATUS_FAILED;
  }

  isVirtualExpected = jni->IsVirtualThread(thread);

  jni->CallStaticVoidMethod(clz, mid);

  eventsCount = 0;
  eventsExpected = 0;

  mid = jni->GetStaticMethodID(cls, "chain", "()V");
  if (mid == 0) {
    LOG("Cannot find Method ID for method chain\n");
    return STATUS_FAILED;
  }
  err = jvmti->ClearBreakpoint(mid, 0);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Failed to ClearBreakpoint: %s (%d)\n", TranslateError(err), err);
    return STATUS_FAILED;
  }

  if (eventsCount != eventsExpected) {
    LOG("Wrong number of frame pop events: %" PRIuPTR ", expected: %" PRIuPTR "\n", eventsCount, eventsExpected);
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
