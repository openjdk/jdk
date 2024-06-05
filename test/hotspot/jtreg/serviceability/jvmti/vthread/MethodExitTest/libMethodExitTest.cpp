/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
static int vthread_mounted_count = 0;
static int vthread_unmounted_count = 0;
static int breakpoint_count = 0;
static int method_entry_count = 0;
static int method_exit_count = 0;
static int frame_pop_count = 0;
static int brkptBreakpointHit = 0;
static jboolean received_method_exit_event = JNI_FALSE;
static jboolean passed = JNI_TRUE;
static bool done = false;

static jmethodID *test_methods = nullptr;
jint test_method_count = 0;
jclass test_class = nullptr;

static void
print_frame_event_info(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method,
                       const char* event_name, int event_count) {
  char* tname = get_thread_name(jvmti, jni, thread);
  char* cname = get_method_class_name(jvmti, jni, method);
  char* mname = get_method_name(jvmti, jni, method);

  LOG("\n%s #%d: method: %s::%s, thread: %s\n",
         event_name, event_count, cname, mname, tname);

  if (strcmp(event_name, "SingleStep") != 0) {
    print_stack_trace(jvmti, jni, thread);
  }
  deallocate(jvmti, jni, (void*)tname);
  deallocate(jvmti, jni, (void*)cname);
  deallocate(jvmti, jni, (void*)mname);
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
                     jclass klass, jmethodID methods[], int method_count)
{
  jlocation location = (jlocation)0L;
  jmethodID method = nullptr;
  jvmtiError err;

  // Find the jmethodID of the specified method
  while (--method_count >= 0) {
    jmethodID meth = methods[method_count];
    char* mname = get_method_name(jvmti, jni, meth);

    if (strcmp(mname, methodName) == 0) {
      // LOG("setupBreakpoint: found method %s() to %s a breakpoint\n", mname, set ? "set" : "clear");
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
              jclass klass, jmethodID methods[], int method_count)
{
  set_or_clear_breakpoint(jni, JNI_TRUE, methodName, klass, methods, method_count);
}

static void
clear_breakpoint(JNIEnv *jni, const char *methodName,
                jclass klass, jmethodID methods[], int method_count)
{
  set_or_clear_breakpoint(jni, JNI_FALSE, methodName, klass, methods, method_count);
}

static void* tls_data = 0;
static const void* const tls_data1 = (const void*)0x111;
static const void* const tls_data2 = (const void*)0x222;

static void
breakpoint_hit1(jvmtiEnv *jvmti, JNIEnv* jni,
                jthread thread, jthread cthread,
                jboolean is_virtual, char* mname) {
  char* tname = get_thread_name(jvmti, jni, cthread);
  jthread vthread = nullptr;
  jvmtiError err;

  // Test GetVirtualThread for carrier thread.
  LOG("Hit #1: Breakpoint: %s: checking GetVirtualThread on carrier thread: %p, %s\n",
         mname, (void*)cthread, tname);

  vthread = get_virtual_thread(jvmti, jni, cthread);

  if (jni->IsSameObject(thread, vthread) != JNI_TRUE) {
    passed = JNI_FALSE;
    LOG("FAILED: GetVirtualThread for carrier thread returned wrong vthread\n\n");
  } else {
    LOG("GetVirtualThread for carrier thread %p returned expected virtual thread: %p\n\n",
           (void*)cthread, (void*)vthread);
  }

  // Test GetThreadLocalStorage for carrier thread.
  LOG("Hit #1: Breakpoint: %s: checking GetThreadLocalStorage on carrier thread: %p\n",
         mname, (void*)cthread);
  err = jvmti->GetThreadLocalStorage(cthread, &tls_data);
  check_jvmti_status(jni, err, "Breakpoint: error in JVMTI GetThreadLocalStorage");

  if (tls_data != tls_data1) {
    passed = JNI_FALSE;
    LOG("FAILED: GetThreadLocalStorage for carrier thread returned value: %p, expected %p\n\n", tls_data, tls_data1);
  } else {
    LOG("GetThreadLocalStorage for carrier thread returned value %p as expected\n\n", tls_data);
  }
  {
    jmethodID method = nullptr;
    jlocation loc = 0L;
    char* mname1 = nullptr;
    char* cname1 = nullptr;

    err = jvmti->GetFrameLocation(cthread, 0, &method, &loc);
    check_jvmti_status(jni, err, "Breakpoint: error in JVMTI GetFrameLocation");

    mname1 = get_method_name(jvmti, jni, method);
    cname1 = get_method_class_name(jvmti, jni, method);

    // Enable METHOD_EXIT events on the cthread. We should not get one.
    LOG("Hit #1: Breakpoint: %s: enabling MethodExit events on carrier thread: %p\n",
           mname, (void*)cthread);
    set_event_notification_mode(jvmti, jni, JVMTI_ENABLE, JVMTI_EVENT_METHOD_EXIT, cthread);

    // Setup NotifyFramePop on the cthread.
    LOG("Hit #1: Breakpoint: %s: enabling FramePop event for method: %s::%s on carrier thread: %p\n",
           mname, cname1, mname1, (void*)cthread);
    err = jvmti->NotifyFramePop(cthread, 0);
    check_jvmti_status(jni, err, "Breakpoint: error in JVMTI NotifyFramePop");

    // Print stack trace of cthread.
    LOG("Hit #1: Breakpoint: %s: Stack Trace of carrier thread: %p\n",
           mname, (void*)cthread);
    print_stack_trace(jvmti, jni, cthread);
  }
  deallocate(jvmti, jni, (void*)tname);
}

static void
breakpoint_hit2(jvmtiEnv *jvmti, JNIEnv* jni,
                jthread thread, jthread cthread,
                jboolean is_virtual, char* mname) {
  jvmtiError err;

  // need to reset this value after the breakpoint_hit1
  received_method_exit_event = JNI_FALSE;

  // Disable METHOD_EXIT events on the cthread.
  LOG("Hit #2: Breakpoint: %s: disabling MethodExit events on carrier thread: %p\n",
          mname, (void*)cthread);
  set_event_notification_mode(jvmti, jni, JVMTI_DISABLE, JVMTI_EVENT_METHOD_EXIT, cthread);

  // Enable METHOD_EXIT events on the vthread. We should get one.
  LOG("Hit #2: Breakpoint: %s: enabling MethodExit events on %s thread: %p\n",
          mname, is_virtual ? "virtual" : "carrier", (void*)thread);
  set_event_notification_mode(jvmti, jni, JVMTI_ENABLE, JVMTI_EVENT_METHOD_EXIT, thread);

  // Enable VIRTUAL_THREAD_MOUNT events on the vthread.
  LOG("Hit #2: Breakpoint: %s: enabling VirtualThreadMount events on %s thread: %p\n",
          mname, is_virtual ? "virtual" : "carrier", (void*)thread);
  set_event_notification_mode(jvmti, jni, JVMTI_ENABLE, EXT_EVENT_VIRTUAL_THREAD_MOUNT, thread);

  // Enable VIRTUAL_THREAD_UNMOUNT events on the vthread.
  LOG("Hit #2: Breakpoint: %s: enabling VirtualThreadUnmount events on %s thread: %p\n",
          mname, is_virtual ? "virtual" : "carrier", (void*)thread);
  set_event_notification_mode(jvmti, jni, JVMTI_ENABLE, EXT_EVENT_VIRTUAL_THREAD_UNMOUNT, thread);

  // Test GetThreadLocalStorage for virtual thread.
  LOG("Hit #2: Breakpoint: %s: checking GetThreadLocalStorage on virtual thread: %p\n",
         mname, (void*)thread);
  err = jvmti->GetThreadLocalStorage(thread, &tls_data);
  check_jvmti_status(jni, err, "Breakpoint: error in JVMTI GetThreadLocalStorage");

  if (tls_data != tls_data2) {
    passed = JNI_FALSE;
    LOG("FAILED: GetThreadLocalStorage for virtual thread returned value: %p, expected %p\n\n", tls_data, tls_data2);
  } else {
    LOG("GetThreadLocalStorage for virtual thread returned value %p as expected\n\n", tls_data);
  }
}

static void
breakpoint_hit3(jvmtiEnv *jvmti, JNIEnv* jni,
                jthread thread, jthread cthread,
                jboolean is_virtual, char* mname) {
  jvmtiError err;

  // Verify that we got a METHOD_EXIT when enabled on the vthread.
  if (!received_method_exit_event) {
    LOG("FAILED: did not get METHOD_EXIT event on the vthread: %p\n", (void*)thread);
    passed = JNI_FALSE;
  }

  // Disable breakpoint events.
  clear_breakpoint(jni, "brkpt", test_class, test_methods, test_method_count);
  set_event_notification_mode(jvmti, jni, JVMTI_DISABLE, JVMTI_EVENT_BREAKPOINT, nullptr);

  // Disable METHOD_EXIT events on the vthread.
  LOG("Hit #3: Breakpoint: %s: disabling MethodExit events on virtual thread: %p\n", mname, (void*)thread);
  set_event_notification_mode(jvmti, jni, JVMTI_DISABLE, JVMTI_EVENT_METHOD_EXIT, thread);

  // Setup NotifyFramePop on the vthread.
  LOG("Hit #3: Breakpoint: %s: enabling FramePop event for method: %s on virtual thread: %p\n",
         mname, mname, (void*)thread);
  err = jvmti->NotifyFramePop(thread, 0);
  check_jvmti_status(jni, err, "Breakpoint: error in JVMTI NotifyFramePop");

  // Disable VIRTUAL_THREAD_MOUNT events on the vthread.
  LOG("Hit #3: Breakpoint: %s: disabling VirtualThreadMount events on virtual thread: %p\n", mname, (void*)thread);
  set_event_notification_mode(jvmti, jni, JVMTI_DISABLE, EXT_EVENT_VIRTUAL_THREAD_MOUNT, thread);

  // Disable VIRTUAL_THREAD_UNMOUNT events on the vthread.
  LOG("Hit #3: Breakpoint: %s: disabling VirtualThreadUnmount events on virtual thread: %p\n", mname, (void*)thread);
  set_event_notification_mode(jvmti, jni, JVMTI_DISABLE, EXT_EVENT_VIRTUAL_THREAD_UNMOUNT, thread);
}

static void JNICALL
Breakpoint(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
           jmethodID method, jlocation location) {
  if (done) {
    return; // avoid failures with JVMTI_ERROR_WRONG_PHASE
  }
  jthread cthread = nullptr;
  char* mname = get_method_name(jvmti, jni, method);
  jboolean is_virtual = jni->IsVirtualThread(thread);

  if (strcmp(mname, "brkpt") != 0) {
    LOG("FAILED: got  unexpected breakpoint in method %s()\n", mname);
    passed = JNI_FALSE;
    deallocate(jvmti, jni, (void*)mname);
    return;
  }
  RawMonitorLocker rml(jvmti, jni, event_mon);

  brkptBreakpointHit++;
  print_frame_event_info(jvmti, jni, thread, method,
                         "Breakpoint", ++breakpoint_count);

  cthread = get_carrier_thread(jvmti, jni, thread);

  if (brkptBreakpointHit == 1) { // 1st MethodExitTest.brkpt() breakpoint
    breakpoint_hit1(jvmti, jni, thread, cthread, is_virtual, mname);

  } else if (brkptBreakpointHit == 2) { // 2nd MethodExitTest.brkpt breakpoint
    breakpoint_hit2(jvmti, jni, thread, cthread, is_virtual, mname);

  } else if (brkptBreakpointHit == 3) { // 3rd MethodExitTest.brkpt breakpoint
    breakpoint_hit3(jvmti, jni, thread, cthread, is_virtual, mname);

  } else {
    LOG("FAILED: Breakpoint: too many brkpt breakpoints.\n");
    passed = JNI_FALSE;
  }
  deallocate(jvmti, jni, (void*)mname);
}

static void JNICALL
MethodEntry(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method) {
  if (done) {
    return; // avoid failures with JVMTI_ERROR_WRONG_PHASE
  }
  char* mname = get_method_name(jvmti, jni, method);

  RawMonitorLocker rml(jvmti, jni, event_mon);
  method_entry_count++;

  LOG("Hit #%d: MethodEntry #%d: method: %s, thread: %p\n",
         brkptBreakpointHit, method_entry_count,  mname, (void*)thread);

  // print_frame_event_info(jvmti, jni, thread, method, "MethodEntry", method_entry_count);

  deallocate(jvmti, jni, (void*)mname);
}

static void JNICALL
MethodExit(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method,
           jboolean was_popped_by_exception, jvalue return_value) {
  if (done) {
    return; // avoid failures with JVMTI_ERROR_WRONG_PHASE
  }
  char* mname = get_method_name(jvmti, jni, method);

  RawMonitorLocker rml(jvmti, jni, event_mon);
  method_exit_count++;

  if (brkptBreakpointHit == 1) {
    received_method_exit_event = JNI_TRUE; // set it for any method as it is not expected
  }

  // print_frame_event_info(jvmti, jni, thread, method, "MethodExit", method_exit_count);
  if (strstr(mname, "brkpt") != nullptr) { // event IS in the "brkpt" method
    LOG("Hit #%d: MethodExit #%d: method: %s on thread: %p\n",
           brkptBreakpointHit, method_exit_count, mname, (void*)thread);
    received_method_exit_event = JNI_TRUE; // set it for brkpt method only if brkptBreakpointHit > 1

    set_event_notification_mode(jvmti, jni, JVMTI_DISABLE, JVMTI_EVENT_METHOD_EXIT, thread);
  }
  deallocate(jvmti, jni, (void*)mname);
}

static void JNICALL
FramePop(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method,
         jboolean was_popped_by_exception) {
  if (done) {
    return; // avoid failures with JVMTI_ERROR_WRONG_PHASE
  }
  char* mname = get_method_name(jvmti, jni, method);

  RawMonitorLocker rml(jvmti, jni, event_mon);
  frame_pop_count++;

  LOG("\nHit #%d: FramePop #%d: method: %s on thread: %p\n",
         brkptBreakpointHit, frame_pop_count, mname, (void*)thread);

  print_frame_event_info(jvmti, jni, thread, method, "FramePop", frame_pop_count);

  deallocate(jvmti, jni, (void*)mname);
}

static void JNICALL
ThreadStart(jvmtiEnv *jvmti, JNIEnv* jni, jthread cthread) {
  if (done) {
    return; // avoid failures with JVMTI_ERROR_WRONG_PHASE
  }
  char* tname = get_thread_name(jvmti, jni, cthread);
  void* loc_tls_data = 0;
  jvmtiError err;

  RawMonitorLocker rml(jvmti, jni, event_mon);

  LOG("\nThreadStart: cthread: %p, name: %s\n", (void*)cthread, tname);

  // Test SetThreadLocalStorage for carrier thread.
  err = jvmti->SetThreadLocalStorage(cthread, tls_data1);
  check_jvmti_status(jni, err, "ThreadStart: error in JVMTI SetThreadLocalStorage");

  // Test GetThreadLocalStorage for carrier thread.
  err = jvmti->GetThreadLocalStorage(cthread, &loc_tls_data);
  check_jvmti_status(jni, err, "ThreadStart: error in JVMTI GetThreadLocalStorage");

  if (loc_tls_data != tls_data1) {
    passed = JNI_FALSE;
    LOG("ThreadStart: FAILED: GetThreadLocalStorage for carrier thread returned value: %p, expected %p\n\n", loc_tls_data, tls_data1);
  } else {
    LOG("ThreadStart: GetThreadLocalStorage for carrier thread returned value %p as expected\n\n", loc_tls_data);
  }
  deallocate(jvmti, jni, (void*)tname);
}

static void JNICALL
VirtualThreadStart(jvmtiEnv *jvmti, JNIEnv* jni, jthread vthread) {
  if (done) {
    return; // avoid failures with JVMTI_ERROR_WRONG_PHASE
  }
  char* tname = get_thread_name(jvmti, jni, vthread);
  jvmtiError err;
  jboolean is_virtual = jni->IsVirtualThread(vthread);
  const char* virt = is_virtual == JNI_TRUE ? "virtual" : "carrier";

  RawMonitorLocker rml(jvmti, jni, event_mon);

  LOG("\nVirtualThreadStart: %s thread: %p, name: %s\n", virt, (void*)vthread, tname);

  // Test SetThreadLocalStorage for virtual thread.
  err = jvmti->SetThreadLocalStorage(vthread, tls_data2);
  check_jvmti_status(jni, err, "VirtualThreadMount: error in JVMTI SetThreadLocalStorage");

  deallocate(jvmti, jni, (void*)tname);
}

// Parameters: (jvmtiEnv *jvmti, JNIEnv* jni, jthread thread)
static void JNICALL
VirtualThreadMount(jvmtiEnv *jvmti, ...) {
  if (done) {
    return; // avoid failures with JVMTI_ERROR_WRONG_PHASE
  }
  jmethodID method = nullptr;
  jlocation loc = 0L;
  char* mname = nullptr;
  char* cname = nullptr;
  jvmtiError err;

  va_list ap;
  JNIEnv* jni = nullptr;
  jthread thread = nullptr;

  va_start(ap, jvmti);
  jni = va_arg(ap, JNIEnv*);
  thread = va_arg(ap, jthread);
  va_end(ap);

  RawMonitorLocker rml(jvmti, jni, event_mon);

  err = jvmti->GetFrameLocation(thread, 0, &method, &loc);
  check_jvmti_status(jni, err, "VirtualThreadMount: error in JVMTI GetFrameLocation");

  mname = get_method_name(jvmti, jni, method);
  cname = get_method_class_name(jvmti, jni, method);

  LOG("\nHit #%d: VirtualThreadMount #%d: enabling FramePop for method: %s::%s on virtual thread: %p\n",
         brkptBreakpointHit, ++vthread_mounted_count, cname, mname, (void*)thread);

  err = jvmti->NotifyFramePop(thread, 0);
  check_jvmti_status(jni, err, "VirtualThreadMount: error in JVMTI NotifyFramePop");

  print_frame_event_info(jvmti, jni, thread, method, "VirtualThreadMount", vthread_mounted_count);

  // Test SetThreadLocalStorage for virtual thread.
  err = jvmti->SetThreadLocalStorage(thread, tls_data2);
  check_jvmti_status(jni, err, "VirtualThreadMount: error in JVMTI SetThreadLocalStorage");

  deallocate(jvmti, jni, (void*)mname);
  deallocate(jvmti, jni, (void*)cname);
}

// Parameters: (jvmtiEnv *jvmti, JNIEnv* jni, jthread thread)
static void JNICALL
VirtualThreadUnmount(jvmtiEnv *jvmti, ...) {
  if (done) {
    return; // avoid failures with JVMTI_ERROR_WRONG_PHASE
  }
  jmethodID method = nullptr;
  jlocation loc = 0L;
  char* mname = nullptr;
  char* cname = nullptr;
  jvmtiError err;

  va_list ap;
  JNIEnv* jni = nullptr;
  jthread thread = nullptr;

  va_start(ap, jvmti);
  jni = va_arg(ap, JNIEnv*);
  thread = va_arg(ap, jthread);
  va_end(ap);

  RawMonitorLocker rml(jvmti, jni, event_mon);

  err = jvmti->GetFrameLocation(thread, 0, &method, &loc);
  check_jvmti_status(jni, err, "VirtualThreadUnmount: error in JVMTI GetFrameLocation");

  mname = get_method_name(jvmti, jni, method);
  cname = get_method_class_name(jvmti, jni, method);

  LOG("\nHit #%d: VirtualThreadUnmount #%d: enabling FramePop for method: %s::%s on virtual thread: %p\n",
         brkptBreakpointHit, ++vthread_unmounted_count, cname, mname, (void*)thread);

  err = jvmti->NotifyFramePop(thread, 0);
  check_jvmti_status(jni, err, "VirtualThreadUnmount: error in JVMTI NotifyFramePop");

  print_frame_event_info(jvmti, jni, thread, method, "VirtualThreadUnmount", vthread_unmounted_count);

  deallocate(jvmti, jni, (void*)mname);
  deallocate(jvmti, jni, (void*)cname);
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;

  LOG("Agent_OnLoad started\n");

  if (jvm->GetEnv((void **)(&jvmti), JVMTI_VERSION) != JNI_OK) {
    LOG("Error: GetEnv call for JVMTI_VERSION failed\n");
    return JNI_ERR;
  }
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.Breakpoint  = &Breakpoint;
  callbacks.FramePop    = &FramePop;
  callbacks.MethodEntry = &MethodEntry;
  callbacks.MethodExit  = &MethodExit;
  callbacks.ThreadStart = &ThreadStart;
  callbacks.VirtualThreadStart = &VirtualThreadStart;

  err = set_ext_event_callback(jvmti, "VirtualThreadMount", VirtualThreadMount);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetExtEventCallback for VirtualThreadMount: %s(%d)\n",
           TranslateError(err), err);
    return JNI_ERR;
  }
  err = set_ext_event_callback(jvmti, "VirtualThreadUnmount", VirtualThreadUnmount);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetExtEventCallback for VirtualThreadUnmount: %s(%d)\n",
           TranslateError(err), err);
    return JNI_ERR;
  }
  memset(&caps, 0, sizeof(caps));
  caps.can_generate_breakpoint_events = 1;
  caps.can_generate_frame_pop_events = 1;
  caps.can_generate_method_entry_events = 1;
  caps.can_generate_method_exit_events = 1;
  caps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI AddCapabilities: %d\n", err);
    return JNI_ERR;
  }
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetEventCallbacks: %d\n", err);
    return JNI_ERR;
  }
  set_event_notification_mode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_FRAME_POP, nullptr);
  set_event_notification_mode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_THREAD_START, nullptr);
  set_event_notification_mode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VIRTUAL_THREAD_START, nullptr);

  event_mon = create_raw_monitor(jvmti, "Events Monitor");

  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_MethodExitTest_enableEvents(JNIEnv *jni, jclass klass, jthread thread, jclass testKlass) {
  jvmtiError err;

  LOG("enableEvents: started\n");

  test_class = (jclass)jni->NewGlobalRef(testKlass);
  err = jvmti->GetClassMethods(testKlass, &test_method_count, &test_methods);
  check_jvmti_status(jni, err, "enableEvents: error in JVMTI GetClassMethods for testKlass");

  set_breakpoint(jni, "brkpt", testKlass, test_methods, test_method_count);

  // Enable Breakpoint events globally
  set_event_notification_mode(jvmti, jni, JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullptr);

  LOG("enableEvents: finished\n");
}

JNIEXPORT jboolean JNICALL
Java_MethodExitTest_check(JNIEnv *jni, jclass cls) {
  done = true; // defence against failures with JVMTI_ERROR_WRONG_PHASE

  LOG("\n");
  LOG("check: started\n");

  LOG("check: vthread_mounted_count:   %d\n", vthread_mounted_count);
  LOG("check: vthread_unmounted_count: %d\n", vthread_unmounted_count);
  LOG("check: breakpoint_count:        %d\n", breakpoint_count);
  LOG("check: method_exit_count:       %d\n", method_exit_count);
  LOG("check: frame_pop_count:         %d\n", frame_pop_count);

  if (method_exit_count == 0) {
    passed = JNI_FALSE;
    LOG("FAILED: method_exit_count == 0\n");
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
