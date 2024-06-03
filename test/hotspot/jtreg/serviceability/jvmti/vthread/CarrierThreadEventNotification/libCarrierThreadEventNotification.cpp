/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>
#include <jvmti.h>
#include <stdio.h>
#include <string.h>
#include "jvmti_common.hpp"


extern "C" {

// set by Agent_OnLoad
static jvmtiEnv* jvmti = nullptr;

static jthread* carrier_threads = nullptr;
static jint cthread_cnt = 0;

static const char* CTHREAD_NAME_START = "ForkJoinPool";
static const size_t CTHREAD_NAME_START_LEN = strlen("ForkJoinPool");

static jint
get_cthreads(JNIEnv* jni, jthread** cthreads_p) {
  jthread* cthreads = nullptr;
  jint all_cnt = 0;
  jint ct_cnt = 0;

  jvmtiError err = jvmti->GetAllThreads(&all_cnt, &cthreads);
  check_jvmti_status(jni, err, "get_cthreads: error in JVMTI GetAllThreads");

  for (int idx = 0; idx < all_cnt; idx++) {
    jthread thread = cthreads[idx];
    char* tname = get_thread_name(jvmti, jni, thread);

    if (strncmp(tname, CTHREAD_NAME_START, CTHREAD_NAME_START_LEN) == 0) {
      cthreads[ct_cnt++] = jni->NewGlobalRef(thread);
    }
    deallocate(jvmti, jni, tname);
  }
  *cthreads_p = cthreads;
  return ct_cnt;
}

static void JNICALL
SingleStep(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
           jmethodID method, jlocation location) {
  jboolean is_virtual = jni->IsVirtualThread(thread);
  if (is_virtual) {
    jni->FatalError("Virtual thread should not have posted single stepping event");
  }
}

JNIEXPORT void JNICALL
Java_CarrierThreadEventNotification_setSingleSteppingMode(JNIEnv* jni, jclass klass, jboolean enable) {
  if (enable) {
    if (cthread_cnt != 0 || carrier_threads != nullptr) {
      jni->FatalError("Should not be set");
    }
    cthread_cnt = get_cthreads(jni, &carrier_threads);
    for (int i = 0; i < cthread_cnt; i++) {
      jthread thread = carrier_threads[i];
      jvmtiError err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, thread);
      check_jvmti_status(jni, err, "event handler: error in JVMTI SetEventNotificationMode for event JVMTI_EVENT_SINGLE_STEP");
    }
  } else {
    if (carrier_threads == nullptr) {
      jni->FatalError("Should be set");
    }
    for (int i = 0; i < cthread_cnt; i++) {
      jthread thread = carrier_threads[i];
      jvmtiError err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, thread);
      check_jvmti_status(jni, err, "event handler: error in JVMTI SetEventNotificationMode for event JVMTI_EVENT_SINGLE_STEP");
      jni->DeleteGlobalRef(thread);
    }
    deallocate(jvmti, jni, carrier_threads);
    cthread_cnt = 0;
    carrier_threads = nullptr;
  }
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;

  printf("Agent_OnLoad: started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    LOG("error in GetEnv");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_generate_single_step_events = 1;
  caps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI AddCapabilities: %d\n", err);
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.SingleStep = &SingleStep;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetEventCallbacks: %d\n", err);
  }

  return 0;
}

} // extern "C"
