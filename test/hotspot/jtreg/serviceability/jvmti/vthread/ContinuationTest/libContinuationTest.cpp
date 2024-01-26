/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>
#include "jvmti.h"
#include "jvmti_common.h"

extern "C" {

#define MAX_FRAME_COUNT 20
#define FRAMES_TO_NOTIFY_POP 7

static jvmtiEnv *jvmti = nullptr;
static jthread exp_thread = nullptr;
static jrawMonitorID event_mon = nullptr;
static int method_entry_count = 0;
static int frame_pop_count = 0;

static void
print_frame_event_info(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method, const char* event_name) {
  char* tname = get_thread_name(jvmti, jni, thread);
  char* cname = get_method_class_name(jvmti, jni, method);
  char* mname = nullptr;
  char* msign = nullptr;
  jboolean is_virtual = jni->IsVirtualThread(thread);
  const char* virt =  is_virtual ? "virtual" : "carrier";
  jvmtiError err;

  err = jvmti->GetMethodName(method, &mname, &msign, nullptr);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetMethodName call");

  if (strcmp(event_name, "MethodEntry") == 0) {
    LOG("%s event #%d: %s thread: %s, method: %s: %s%s\n",
           event_name, method_entry_count, virt, tname, cname, mname, msign);
  } else {
    LOG("%s event #%d: %s thread: %s, method: %s: %s%s\n",
           event_name, frame_pop_count, virt, tname, cname, mname, msign);
  }

  deallocate(jvmti, jni, (void*)tname);
  deallocate(jvmti, jni, (void*)cname);
  deallocate(jvmti, jni, (void*)mname);
  deallocate(jvmti, jni, (void*)msign);
}

static void JNICALL
MethodEntry(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method) {
  char* mname = get_method_name(jvmti, jni, method);
  jvmtiError err;

  RawMonitorLocker rml(jvmti, jni, event_mon);

  if (strcmp(mname, "yield0") == 0) {
    print_frame_event_info(jvmti, jni, thread, method, "MethodEntry");

    LOG("\nMethodEntry: Requesting FramePop notifications for %d frames:\n", FRAMES_TO_NOTIFY_POP);

    // Request FramePop notifications for all continuation frames.
    // They all are expected to be cleared as a part of yield protocol.
    for (jint depth = 0; depth < FRAMES_TO_NOTIFY_POP; depth++) {
      jmethodID frame_method = nullptr;
      jlocation location = 0LL;

      err = jvmti->NotifyFramePop(thread, depth);
      check_jvmti_status(jni, err, "MethodEntry: error in JVMTI NotifyFramePop");

      err = jvmti->GetFrameLocation(thread, depth, &frame_method, &location);
      check_jvmti_status(jni, err, "MethodEntry: error in JVMTI GetFrameLocation");

      print_method(jvmti, jni, frame_method, depth);
    }
    LOG("\n");
  }

  deallocate(jvmti, jni, (void*)mname);
}

static void JNICALL
FramePop(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method,
         jboolean was_popped_by_exception) {
  RawMonitorLocker rml(jvmti, jni, event_mon);
  frame_pop_count++;
  print_frame_event_info(jvmti, jni, thread, method, "FramePop");
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;

  LOG("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.MethodEntry       = &MethodEntry;
  callbacks.FramePop          = &FramePop;

  memset(&caps, 0, sizeof(caps));
  caps.can_generate_method_entry_events = 1;
  caps.can_generate_frame_pop_events = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI AddCapabilities: %d\n", err);
  }

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetEventCallbacks: %d\n", err);
  }

  event_mon = create_raw_monitor(jvmti, "Events Monitor");

  LOG("Agent_OnLoad finished\n");


  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_ContinuationTest_enableEvents(JNIEnv *jni, jclass cls, jthread thread) {
  jvmtiError err;

  LOG("enableEvents: started\n");
  exp_thread = (jthread)jni->NewGlobalRef(thread);

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY, thread);
  check_jvmti_status(jni, err, "enableEvents: error in JVMTI SetEventNotificationMode: enable METHOD_ENTRY");

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FRAME_POP, thread);
  check_jvmti_status(jni, err, "enableEvents: error in JVMTI SetEventNotificationMode: enable FRAME_POP");

  LOG("enableEvents: finished\n");

}

JNIEXPORT jboolean JNICALL
Java_ContinuationTest_check(JNIEnv *jni, jclass cls) {
  jvmtiError err;

  LOG("\n");
  LOG("check: started\n");

  err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_METHOD_ENTRY, exp_thread);
  check_jvmti_status(jni, err, "enableEvents: error in JVMTI SetEventNotificationMode: disable METHOD_ENTRY");

  err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_FRAME_POP, exp_thread);
  check_jvmti_status(jni, err, "error in JVMTI SetEventNotificationMode: disable FRAME_POP");

  LOG("check: finished\n");
  LOG("\n");


  return frame_pop_count == 0;
}
} // extern "C"
