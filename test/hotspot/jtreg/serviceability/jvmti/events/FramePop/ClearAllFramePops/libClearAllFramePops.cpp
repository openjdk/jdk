/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmti_common.hpp"


extern "C" {

static jvmtiEnv *jvmti;
static jvmtiEventCallbacks callbacks;
static jrawMonitorID event_lock;
static jboolean watch_events = JNI_FALSE;
static int pop_count;
static const char* TEST_THREAD_NAME_BASE = "Test Thread";
static const char* TEST_CLASS_SIG = "LClearAllFramePops$TestTask;";

static
bool isTestThread(JNIEnv *jni, jvmtiEnv *jvmti, jthread thr) {
  char* tname = get_thread_name(jvmti, jni, thr);
  bool result = strncmp(tname, TEST_THREAD_NAME_BASE, strlen(TEST_THREAD_NAME_BASE)) == 0;
  deallocate(jvmti, jni, tname);

  return result;
}

static
void printInfo(JNIEnv *jni, jvmtiEnv *jvmti, jthread thr, jmethodID method, int depth) {
  jclass cls;
  char *mname, *msig, *csig;
  char* tname = get_thread_name(jvmti, jni, thr);

  check_jvmti_status(jni, jvmti->GetMethodDeclaringClass(method, &cls), "Error in GetMethodDeclaringClass.");
  check_jvmti_status(jni, jvmti->GetClassSignature(cls, &csig, nullptr), "Error in GetClassSignature.");
  check_jvmti_status(jni, jvmti->GetMethodName(method, &mname, &msig, nullptr), "Error in GetMethodName.");

  LOG(" %s: %s.%s%s, depth = %d\n", tname, csig, mname, msig, depth);

  deallocate(jvmti, jni, tname);
  deallocate(jvmti, jni, mname);
  deallocate(jvmti, jni, msig);
  deallocate(jvmti, jni, csig);
}

void JNICALL MethodEntry(jvmtiEnv *jvmti, JNIEnv *jni,
                         jthread thr, jmethodID method) {
  RawMonitorLocker rml(jvmti, jni, event_lock);

  if (watch_events == JNI_FALSE) {
    return;
  }
  if (!isTestThread(jni, jvmti, thr)) {
    return; // not a tested thread
  }
  jclass cls;
  char *csig;

  check_jvmti_status(jni, jvmti->GetMethodDeclaringClass(method, &cls), "Error in GetMethodDeclaringClass.");
  check_jvmti_status(jni, jvmti->GetClassSignature(cls, &csig, nullptr), "Error in GetClassSignature.");

  if (strcmp(csig, TEST_CLASS_SIG) != 0 ||
      strcmp(get_method_name(jvmti, jni, method), "run") != 0) {
    return; // not a tested method
  }
  LOG("\n>>>Method entry event:");
  printInfo(jni, jvmti, thr, method, get_frame_count(jvmti, jni, thr));

  check_jvmti_status(jni, jvmti->NotifyFramePop(thr, 0), "Error in NotifyFramePop.");
  deallocate(jvmti, jni, csig);
}

void JNICALL FramePop(jvmtiEnv *jvmti, JNIEnv *jni,
                      jthread thr, jmethodID method, jboolean wasPopedByException) {
  RawMonitorLocker rml(jvmti, jni, event_lock);

  jint frameCount = get_frame_count(jvmti, jni, thr);

  LOG("\n>>> Frame Pop event:");
  printInfo(jni, jvmti, thr, method, frameCount);
  pop_count++;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;

  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Failed: Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }
  event_lock = create_raw_monitor(jvmti, "_event_lock");

  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_generate_frame_pop_events = 1;
  caps.can_generate_method_entry_events = 1;
  caps.can_support_virtual_threads = 1;

  callbacks.MethodEntry = &MethodEntry;
  callbacks.FramePop = &FramePop;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  err = set_event_notification_mode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  return JNI_OK;
}

JNIEXPORT void JNICALL Java_ClearAllFramePops_clearAllFramePops(JNIEnv *jni, jclass cls) {
  RawMonitorLocker rml(jvmti, jni, event_lock);

  char* tname = get_thread_name(jvmti, jni, nullptr);

  check_jvmti_status(jni, jvmti->ClearAllFramePops(nullptr), "Error in ClearAllFramePops");
  LOG("Called ClearAllFramePops for thread: %s\n", tname);

  deallocate(jvmti, jni, tname);
}

JNIEXPORT void JNICALL Java_ClearAllFramePops_getReady(JNIEnv *jni, jclass cls) {
  RawMonitorLocker rml(jvmti, jni, event_lock);

  watch_events = JNI_TRUE;
  set_event_notification_mode(jvmti, jni, JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY, nullptr);
  set_event_notification_mode(jvmti, jni, JVMTI_ENABLE, JVMTI_EVENT_FRAME_POP, nullptr);
}

JNIEXPORT void JNICALL Java_ClearAllFramePops_check(JNIEnv *jni, jclass cls) {
  RawMonitorLocker rml(jvmti, jni, event_lock);

  watch_events = JNI_FALSE;
  set_event_notification_mode(jvmti, jni, JVMTI_DISABLE, JVMTI_EVENT_METHOD_ENTRY, nullptr);
  set_event_notification_mode(jvmti, jni, JVMTI_DISABLE, JVMTI_EVENT_FRAME_POP, nullptr);
  LOG("\n>>> Total frame pops: %d\n", pop_count);

  if (pop_count > 0) {
    fatal(jni, "Failed: FramePop events are not expected");
  }
}

}
