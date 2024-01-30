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
#include <time.h>
#include "jvmti.h"
#include "jvmti_common.h"


extern "C" {

#define MAX_THREADS 100

typedef struct item *item_t;
struct item {
  item_t next;
  jmethodID method;
  int depth;
} item;

typedef struct thr {
  jthread thread;
  item_t tos;
} thr;

static jvmtiEnv *jvmti = nullptr;
static jvmtiEventCallbacks callbacks;
static jrawMonitorID event_lock;
static jboolean printdump = JNI_TRUE;
static jboolean watch_events = JNI_FALSE;

static int pop_count = 0;
static int push_count = 0;
static int thr_count = 0;
static int max_depth = 0;
static thr threads[MAX_THREADS];

static volatile int callbacksEnabled = JNI_FALSE;
static jrawMonitorID agent_lock;

void print_current_time() {
  char buf[80];
  time_t current_time = time(0);
  struct tm tm = *localtime(&current_time);
  strftime(buf, sizeof(buf), "%Y-%m-%d.%X", &tm);
  printf("[%s]", buf);
}

static
bool isTestThread(JNIEnv *jni, jvmtiEnv *jvmti, jthread thr) {
  jvmtiThreadInfo inf;
  const char* TEST_THREAD_NAME_BASE = "Test Thread";
  check_jvmti_status(jni, jvmti->GetThreadInfo(thr, &inf), "Error in GetThreadInfo.");

  bool result = strncmp(inf.name, TEST_THREAD_NAME_BASE, strlen(TEST_THREAD_NAME_BASE)) == 0;
  jvmti->Deallocate((unsigned char *)inf.name);

  return result;
}

static
void printInfo(JNIEnv *jni, jvmtiEnv *jvmti, jthread thr, jmethodID method, int depth) {
  jvmtiThreadInfo inf;
  char *clsig, *name, *sig, *generic;
  jclass cls;

  check_jvmti_status(jni, jvmti->GetThreadInfo(thr, &inf), "Error in GetThreadInfo.\"");
  check_jvmti_status(jni, jvmti->GetMethodDeclaringClass(method, &cls), "Error in GetMethodDeclaringClass.");
  check_jvmti_status(jni, jvmti->GetClassSignature(cls, &clsig, &generic), "Error in GetClassSignature.");
  check_jvmti_status(jni, jvmti->GetMethodName(method, &name, &sig, &generic), "Error in GetMethodName.");

  LOG("  %s: %s.%s%s, depth = %d\n", inf.name, clsig, name, sig, depth);

  jvmti->Deallocate((unsigned char *)sig);
  jvmti->Deallocate((unsigned char *)name);
  jvmti->Deallocate((unsigned char *)clsig);
  jvmti->Deallocate((unsigned char *)inf.name);
}

static
void pop(jvmtiEnv *jvmti, JNIEnv *jni, jthread thr, jmethodID method, int depth) {
  item_t old;
  int i, count = 0;

  for (i = 0; i < thr_count; i++) {
    if (jni->IsSameObject(threads[i].thread, thr)) {
      break;
    }
  }

  if (i == thr_count) {
    watch_events = JNI_FALSE;
    printInfo(jni, jvmti, thr, method, depth);
    fatal(jni, "Unknown thread:\n");
  }

  if (threads[i].tos == nullptr) {
    watch_events = JNI_FALSE;
    printInfo(jni, jvmti, thr, method, depth);
    fatal(jni, "Stack underflow:\n");
  }

  do {
    pop_count++;
    old = threads[i].tos;
    threads[i].tos = threads[i].tos->next;
    if (old->method == method && old->depth == depth) {
      free(old);
      return;
    }
    free(old);
  } while (threads[i].tos != nullptr);

  watch_events = JNI_FALSE;
  printInfo(jni, jvmti, thr, method, depth);
  fatal(jni, "Frame pop does not match any entry:\n");
}

static
void push(JNIEnv *jni, jthread thr, jmethodID method, int depth) {
  item_t new_item;
  int i;

  for (i = 0; i < thr_count; i++) {
    if (jni->IsSameObject(threads[i].thread, thr)) {
      break;
    }
  }

  if (i == thr_count) {
    thr_count++;
    if (thr_count == MAX_THREADS) {
      fatal(jni, "Out of threads\n");
    }
    threads[i].thread = jni->NewGlobalRef(thr);
    threads[i].tos = nullptr;
  }

  new_item = (item_t)malloc(sizeof(item));
  if (new_item == nullptr) {
    fatal(jni, "Out of memory\n");
  }

  new_item->next = threads[i].tos;
  new_item->method = method;
  new_item->depth = depth;
  threads[i].tos = new_item;
  push_count++;
  max_depth = (max_depth < depth) ? depth : max_depth;
}

void JNICALL MethodEntry(jvmtiEnv *jvmti, JNIEnv *jni,
                         jthread thr, jmethodID method) {
  jboolean isNative;
  jint frameCount;

  if (watch_events == JNI_FALSE) return;

  if (!isTestThread(jni, jvmti, thr)) {
    return; // not a tested thread
  }

  RawMonitorLocker rml(jvmti, jni, agent_lock);

  if (!callbacksEnabled) {
    return;
  }

  check_jvmti_status(jni, jvmti->GetFrameCount(thr, &frameCount), "Error in GetFrameCount");
  check_jvmti_status(jni, jvmti->IsMethodNative(method, &isNative), "Error in IsMethodNative.");

  {
    if (printdump == JNI_TRUE) {
      print_current_time();
      fflush(0);
      LOG(">>> %sMethod entry\n>>>", (isNative == JNI_TRUE) ? "Native " : "");
      printInfo(jni, jvmti, thr, method, frameCount);
    }
    if (isNative == JNI_FALSE) {
      RawMonitorLocker rml(jvmti, jni, event_lock);
      push(jni, thr, method, frameCount);
      check_jvmti_status(jni, jvmti->NotifyFramePop(thr, 0), "Error in NotifyFramePop.");
    }
  }
}

void JNICALL VMStart(jvmtiEnv *jvmti, JNIEnv* jni) {
  RawMonitorLocker rml(jvmti, jni, agent_lock);
  callbacksEnabled = JNI_TRUE;
}


void JNICALL VMDeath(jvmtiEnv *jvmti, JNIEnv* jni) {
  RawMonitorLocker rml(jvmti, jni, agent_lock);
  callbacksEnabled = JNI_FALSE;
}

void JNICALL FramePop(jvmtiEnv *jvmti, JNIEnv *jni,
                      jthread thr, jmethodID method, jboolean wasPopedByException) {
  jint frameCount;

  RawMonitorLocker rml(jvmti, jni, agent_lock);

  if (!callbacksEnabled) {
    return;
  }
  check_jvmti_status(jni, jvmti->GetFrameCount(thr, &frameCount), "Error in GetFrameCount.");

  {
    if (printdump == JNI_TRUE) {
      print_current_time();
      fflush(0);
      LOG(" >>> Frame Pop\n>>>");
      printInfo(jni, jvmti, thr, method, frameCount);
    }
    RawMonitorLocker rml(jvmti, jni, event_lock);
    pop(jvmti, (JNIEnv *)jni, thr, method, frameCount);
  }
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;

  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  event_lock = create_raw_monitor(jvmti, "_event_lock");

  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_generate_frame_pop_events = 1;
  caps.can_generate_method_entry_events = 1;
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

  callbacks.MethodEntry = &MethodEntry;
  callbacks.FramePop = &FramePop;
  callbacks.VMStart = &VMStart;
  callbacks.VMDeath = &VMDeath;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_START, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  agent_lock = create_raw_monitor(jvmti, "agent_lock");

  return JNI_OK;
}

JNIEXPORT void JNICALL Java_framepop02_getReady(JNIEnv *jni, jclass cls) {
  check_jvmti_status(jni, jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY, nullptr),
                     "Error in SetEventNotificationMode");
  check_jvmti_status(jni, jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FRAME_POP, nullptr),
                     "Error in SetEventNotificationMode");
  watch_events = JNI_TRUE;
}

JNIEXPORT void JNICALL Java_framepop02_check(JNIEnv *jni, jclass cls) {
  watch_events = JNI_FALSE;
  check_jvmti_status(jni, jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_FRAME_POP, nullptr),
                     "Error in SetEventNotificationMode");
  check_jvmti_status(jni, jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_METHOD_ENTRY, nullptr),
                     "Error in SetEventNotificationMode");

  if (printdump == JNI_TRUE) {
    LOG("%d threads, %d method entrys, %d frame pops, max depth = %d\n", thr_count, push_count, pop_count, max_depth);
  }
}

}
