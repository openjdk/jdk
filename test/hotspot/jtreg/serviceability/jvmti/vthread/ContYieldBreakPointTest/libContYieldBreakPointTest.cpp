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
#include "jvmti_common.hpp"

extern "C" {


static jvmtiEnv *jvmti = nullptr;
static jthread exp_thread = nullptr;
static jrawMonitorID event_mon = nullptr;
static int breakpoint_count = 0;
static int single_step_count = 0;

static void
print_frame_event_info(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method,
                       const char* event_name, int event_count) {
  char* tname = get_thread_name(jvmti, jni, thread);
  char* cname = get_method_class_name(jvmti, jni, method);
  char* mname = nullptr;
  char* msign = nullptr;
  jvmtiError err;

  err = jvmti->GetMethodName(method, &mname, &msign, nullptr);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetMethodName call");

  LOG("\n%s event #%d: thread: %s, method: %s: %s%s\n",
         event_name, event_count, tname, cname, mname, msign);

  if (strcmp(event_name, "SingleStep") != 0) {
    print_current_stack_trace(jvmti, jni);
  }

  deallocate(jvmti, jni, (void*)tname);
  deallocate(jvmti, jni, (void*)cname);
  deallocate(jvmti, jni, (void*)mname);
  deallocate(jvmti, jni, (void*)msign);
}

 void
print_cont_event_info(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jint frames_cnt, const char* event_name) {
  char* tname = get_thread_name(jvmti, jni, thread);

  LOG("\n%s event: thread: %s, frames: %d\n\n", event_name, tname, frames_cnt);

  print_current_stack_trace(jvmti, jni);


  deallocate(jvmti, jni, (void*)tname);
}

static void JNICALL
Breakpoint(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
           jmethodID method, jlocation location) {
  char* mname = get_method_name(jvmti, jni, method);
  jvmtiError err;

  RawMonitorLocker rml(jvmti, jni, event_mon);

  if (strcmp(mname, "yield0") != 0) {
    deallocate(jvmti, jni, (void*)mname);
    return; // ignore unrelated events
  }
  print_frame_event_info(jvmti, jni, thread, method,
                         "Breakpoint", ++breakpoint_count);

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, thread);
  check_jvmti_status(jni, err, "Breakpoint: error in JVMTI SetEventNotificationMode: enable SINGLE_STEP");

  deallocate(jvmti, jni, (void*)mname);
}

static void JNICALL
SingleStep(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
           jmethodID method, jlocation location) {
  char* mname = get_method_name(jvmti, jni, method);

  RawMonitorLocker rml(jvmti, jni, event_mon);

  if (strcmp(mname, "yield0") != 0) {
    deallocate(jvmti, jni, (void*)mname);
    return; // ignore unrelated events
  }
  print_frame_event_info(jvmti, jni, thread, method,
                         "SingleStep", ++single_step_count);

  deallocate(jvmti, jni, (void*)mname);
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
  callbacks.Breakpoint  = &Breakpoint;
  callbacks.SingleStep  = &SingleStep;

  memset(&caps, 0, sizeof(caps));
  caps.can_generate_breakpoint_events = 1;
  caps.can_generate_single_step_events = 1;

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
Java_ContYieldBreakPointTest_enableEvents(JNIEnv *jni, jclass klass, jthread thread, jclass contKlass) {
  jint method_count = 0;
  jmethodID* methods = nullptr;
  jmethodID method = nullptr;
  jlocation location = (jlocation)0L;
  jvmtiError err;

  LOG("enableEvents: started\n");

  err = jvmti->GetClassMethods(contKlass, &method_count, &methods);
  check_jvmti_status(jni, err, "enableEvents: error in JVMTI GetClassMethods");

  // Find jmethodID of Continuation.yield0()
  while (--method_count >= 0) {
    jmethodID meth = methods[method_count];
    char* mname = get_method_name(jvmti, jni, meth);

    if (strcmp(mname, "yield0") == 0) {
      LOG("enableEvents: found method %s() to set a breakpoint\n", mname);

      method = meth;
    }
    deallocate(jvmti, jni, (void*)mname);
  }
  if (method == nullptr) {
    jni->FatalError("Error in enableEvents: not found method fibTest()");
  }

  err = jvmti->SetBreakpoint(method, location);
  check_jvmti_status(jni, err, "enableEvents: error in JVMTI SetBreakpoint");

  // Enable Breakpoint events globally
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullptr);
  check_jvmti_status(jni, err, "enableEvents: error in JVMTI SetEventNotificationMode: enable BREAKPOINT");

  LOG("enableEvents: finished\n");

}

JNIEXPORT jboolean JNICALL
Java_ContYieldBreakPointTest_check(JNIEnv *jni, jclass cls) {
  LOG("\n");
  LOG("check: started\n");

  LOG("check: breakpoint_count:   %d\n", breakpoint_count);
  LOG("check: single_step_count:  %d\n", single_step_count);

  LOG("check: finished\n");
  LOG("\n");


  // Getting this far without a crash or assert means the test passed.
  return JNI_TRUE;
}
} // extern "C"
