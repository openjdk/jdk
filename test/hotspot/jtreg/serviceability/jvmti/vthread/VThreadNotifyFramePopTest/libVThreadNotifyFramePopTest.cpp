/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

#define MAX_FRAME_COUNT 20

static jvmtiEnv *jvmti = nullptr;
static jrawMonitorID event_mon = nullptr;
static int breakpoint_count = 0;
static int frame_pop_count = 0;
static int brkptBreakpointHit = 0;
static jboolean received_frame_pop_event = JNI_FALSE;
static jboolean passed = JNI_TRUE;

static jmethodID *test_methods = nullptr;
jint test_method_count = 0;
jclass test_class = nullptr;

static jmethodID *url_methods = nullptr;
jint url_method_count = 0;
jclass url_class = nullptr;

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

 LOG("%s #%d: thread: %s, method: %s.%s%s\n",
         event_name, event_count, tname, cname, mname, msign);

  if (strcmp(event_name, "SingleStep") != 0) {
    print_stack_trace(jvmti, jni, thread);
  }
  deallocate(jvmti, jni, (void*)tname);
  deallocate(jvmti, jni, (void*)cname);
  deallocate(jvmti, jni, (void*)mname);
  deallocate(jvmti, jni, (void*)msign);
}

static void
print_cont_event_info(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jint frames_cnt, const char* event_name) {
  char* tname = get_thread_name(jvmti, jni, thread);

 LOG("%s: thread: %s, frames: %d\n\n", event_name, tname, frames_cnt);

  print_stack_trace(jvmti, jni, thread);

  deallocate(jvmti, jni, (void*)tname);
}

static void
set_or_clear_breakpoint(JNIEnv *jni, jboolean set, const char *methodName,
                     jclass klass, jmethodID methods[], int method_count) {
  jlocation location = (jlocation)0L;
  jmethodID method = nullptr;
  jvmtiError err;

  // Find the jmethodID of the specified method
  while (--method_count >= 0) {
    jmethodID meth = methods[method_count];
    char* mname = get_method_name(jvmti, jni, meth);

    if (strcmp(mname, methodName) == 0) {
      //LOG("setupBreakpoint: found method %s() to %s a breakpoint\n", mname, set ? "set" : "clear");
      method = meth;
    }
    deallocate(jvmti, jni, (void*)mname);
  }
  if (method == nullptr) {
     LOG("setupBreakpoint: not found method %s() to %s a breakpoint\n",
             methodName, set ? "set" : "clear");
      jni->FatalError("Error in setupBreakpoint: not found method");
  }

  if (set) {
      err = jvmti->SetBreakpoint(method, location);
  } else {
      err = jvmti->ClearBreakpoint(method, location);
  }
  check_jvmti_status(jni, err, "setupBreakpoint: error in JVMTI SetBreakpoint");


}

static void
set_breakpoint(JNIEnv *jni, const char *methodName,
              jclass klass, jmethodID methods[], int method_count) {
  set_or_clear_breakpoint(jni, JNI_TRUE, methodName, klass, methods, method_count);
}

static void
clear_breakpoint(JNIEnv *jni, const char *methodName,
                jclass klass, jmethodID methods[], int method_count) {
  set_or_clear_breakpoint(jni, JNI_FALSE, methodName, klass, methods, method_count);
}

static void
breakpoint_hit1(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, char* mname) {
  jvmtiError err;

  if (strcmp(mname, "openStream") != 0) {
   LOG("FAILED: got  unexpected breakpoint in method %s()\n", mname);
    passed = JNI_FALSE;
    return;
  }

  // Setup NotifyFramePop on the vthread.
  err = jvmti->NotifyFramePop(thread, 0);
  check_jvmti_status(jni, err, "Breakpoint: error in JVMTI NotifyFramePop");

  // Setup next breakpoint
  set_breakpoint(jni, "brkpoint", test_class, test_methods, test_method_count);
}

static void
breakpoint_hit2(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, char* mname) {
  jvmtiError err;

  if (strcmp(mname, "brkpoint") != 0) {
   LOG("FAILED: got unexpected breakpoint in method %s()\n", mname);
    passed = JNI_FALSE;
    return;
  }

  // Verify that we got the FRAME_POP event before we hit this breakpoint.
  if (!received_frame_pop_event) {
    passed = JNI_FALSE;
   LOG("FAILED: did not get FRAME_POP event before second breakpoint event\n");
  }

  // Disable breakpoing events and let the test complete.
  err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_BREAKPOINT, thread);
  check_jvmti_status(jni, err, "Breakpoint: error in JVMTI SetEventNotificationMode: disable BREAKPOINT");
}

static void JNICALL
Breakpoint(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
           jmethodID method, jlocation location) {
  char* mname = get_method_name(jvmti, jni, method);

  RawMonitorLocker rml(jvmti, jni, event_mon);

  brkptBreakpointHit++;
  print_frame_event_info(jvmti, jni, thread, method, "Breakpoint", ++breakpoint_count);

  if (brkptBreakpointHit == 1) { // This should be for URL.openStream()
    breakpoint_hit1(jvmti, jni, thread, mname);
  } else if (brkptBreakpointHit == 2) { // This should be for NotifyFramePop.brkpoint()
    breakpoint_hit2(jvmti, jni, thread, mname);
  } else {
   LOG("FAILED: Breakpoint: too many breakpoints hit.\n");
    passed = JNI_FALSE;
  }

  deallocate(jvmti, jni, (void*)mname);
}

static void JNICALL
FramePop(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method,
         jboolean was_popped_by_exception) {
  char* mname = get_method_name(jvmti, jni, method);

  RawMonitorLocker rml(jvmti, jni, event_mon);

  received_frame_pop_event = JNI_TRUE;
  frame_pop_count++;

 LOG("\nFramePop #%d: Hit #%d:  method: %s, thread: %p\n",
         frame_pop_count, brkptBreakpointHit, mname, (void*)thread);

  print_frame_event_info(jvmti, jni, thread, method, "FramePop", frame_pop_count);


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
  callbacks.FramePop    = &FramePop;

  memset(&caps, 0, sizeof(caps));
  caps.can_generate_breakpoint_events = 1;
  caps.can_generate_frame_pop_events = 1;
  caps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
   LOG("Agent_OnLoad: Error in JVMTI AddCapabilities: %d\n", err);
  }

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
   LOG("Agent_OnLoad: Error in JVMTI SetEventCallbacks: %d\n", err);
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FRAME_POP, nullptr);
  if (err != JVMTI_ERROR_NONE) {
   LOG("error in JVMTI SetEventNotificationMode: %d\n", err);
  }

  event_mon = create_raw_monitor(jvmti, "Events Monitor");

 LOG("Agent_OnLoad finished\n");


  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_VThreadNotifyFramePopTest_enableEvents(JNIEnv *jni, jclass klass, jthread thread,
                                 jclass testKlass, jclass urlKlass) {
  jvmtiError err;

 LOG("enableEvents: started\n");

  test_class = (jclass)jni->NewGlobalRef(urlKlass);
  err = jvmti->GetClassMethods(urlKlass, &url_method_count, &url_methods);
  check_jvmti_status(jni, err, "enableEvents: error in JVMTI GetClassMethods for urlKlass");

  test_class = (jclass)jni->NewGlobalRef(testKlass);
  err = jvmti->GetClassMethods(testKlass, &test_method_count, &test_methods);
  check_jvmti_status(jni, err, "enableEvents: error in JVMTI GetClassMethods for testKlass");

  set_breakpoint(jni, "openStream", urlKlass, url_methods, url_method_count);

  // Enable Breakpoint events globally
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullptr);
  check_jvmti_status(jni, err, "enableEvents: error in JVMTI SetEventNotificationMode: enable BREAKPOINT");

 LOG("enableEvents: finished\n");

}

JNIEXPORT jboolean JNICALL
Java_VThreadNotifyFramePopTest_check(JNIEnv *jni, jclass cls) {
 LOG("\n");
 LOG("check: started\n");

 LOG("check: breakpoint_count:  %d\n", breakpoint_count);
 LOG("check: frame_pop_count:   %d\n", frame_pop_count);

  if (breakpoint_count != 2) {
    passed = JNI_FALSE;
   LOG("FAILED: breakpoint_count != 2\n");
  }
  if (frame_pop_count == 0) {
    passed = JNI_FALSE;
   LOG("FAILED: frame_pop_count == 0\n");
  }

 LOG("check: finished\n");
  LOG("\n");


  return passed;
}
} // extern "C"
