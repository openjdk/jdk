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

jrawMonitorID monitor;
jrawMonitorID monitor_completed;

jvmtiEnv *jvmti_env;

// Accessed using 'monitor' monitor.
bool is_breakpoint_reached = JNI_FALSE;

static void
set_breakpoint(JNIEnv *jni, jclass klass, const char *mname) {
  jlocation location = (jlocation)0L;
  jmethodID method = find_method(jvmti_env, jni, klass, mname);
  jvmtiError err;

  if (method == nullptr) {
    jni->FatalError("Error in set_breakpoint: not found method");
  }
  err = jvmti_env->SetBreakpoint(method, location);
  check_jvmti_status(jni, err, "set_or_clear_breakpoint: error in JVMTI SetBreakpoint");
}

extern "C" {

JNIEXPORT void JNICALL
Java_WaitNotifySuspendedVThreadTask_setBreakpoint(JNIEnv *jni, jclass klass) {
  jvmtiError err;

  LOG("setBreakpoint: started\n");
  set_breakpoint(jni, klass, "methBreakpoint");

  // Enable Breakpoint events globally
  err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullptr);
  check_jvmti_status(jni, err, "enableEvents: error in JVMTI SetEventNotificationMode: enable BREAKPOINT");

  LOG("setBreakpoint: finished\n");
}

JNIEXPORT void JNICALL
Java_WaitNotifySuspendedVThreadTask_notifyRawMonitors(JNIEnv *jni, jclass klass, jthread thread) {

  // Wait until virtual thread reach breakpoint and lock 'montior' monitor
  bool is_breakpoint_reached_local = JNI_FALSE;
  while (!is_breakpoint_reached_local) {
    RawMonitorLocker rml(jvmti_env, jni, monitor);
    is_breakpoint_reached_local = is_breakpoint_reached;
  }

  LOG("Main thread: suspending virtual and carrier threads\n");

  check_jvmti_status(jni, jvmti_env->SuspendThread(thread), "SuspendThread thread");
  jthread cthread = get_carrier_thread(jvmti_env, jni, thread);
  check_jvmti_status(jni, jvmti_env->SuspendThread(cthread), "SuspendThread thread");

  RawMonitorLocker completed(jvmti_env, jni, monitor_completed);

  {
    RawMonitorLocker rml(jvmti_env, jni, monitor);

    LOG("Main thread: calling monitor.notifyAll()\n");
    rml.notify_all();
  }

  LOG("Main thread: resuming virtual thread\n");
  check_jvmti_status(jni, jvmti_env->ResumeThread(thread), "ResumeThread thread");

  LOG("Main thread: before monitor_completed.wait()\n");
  completed.wait();
  LOG("Main thread: after monitor_completed.wait()\n");

  LOG("Main thread: resuming carrier thread\n");
  check_jvmti_status(jni, jvmti_env->ResumeThread(cthread), "ResumeThread cthread");
}

} // extern "C"

static void JNICALL
Breakpoint(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
           jmethodID method, jlocation location) {
  char* mname = get_method_name(jvmti, jni, method);

  if (strcmp(mname, "methBreakpoint") != 0) {
    LOG("FAILED: got  unexpected breakpoint in method %s()\n", mname);
    deallocate(jvmti, jni, (void*)mname);
    fatal(jni, "Error in breakpoint");
    return;
  }

  char* tname = get_thread_name(jvmti, jni, thread);
  const char* virt = jni->IsVirtualThread(thread) ? "virtual" : "carrier";

  {
    RawMonitorLocker rml(jvmti, jni, monitor);

    LOG("Breakpoint: before monitor.wait(): %s in %s thread\n", mname, virt);
    is_breakpoint_reached = JNI_TRUE;
    rml.wait();
    LOG("Breakpoint: after monitor.wait(): %s in %s thread\n", mname, virt);
  }

  RawMonitorLocker completed(jvmti, jni, monitor_completed);

  LOG("Breakpoint: calling monitor_completed.notifyAll()\n");
  completed.notify_all();

  deallocate(jvmti, jni, (void*)tname);
  deallocate(jvmti, jni, (void*)mname);
}

/* ============================================================================= */

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiEnv * jvmti = nullptr;

  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;
  jint res;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  jvmti_env = jvmti;
  monitor = create_raw_monitor(jvmti, "Monitor");
  monitor_completed = create_raw_monitor(jvmti, "Monitor Completed");

  /* add capability to generate compiled method events */
  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_support_virtual_threads = 1;
  caps.can_generate_breakpoint_events = 1;
  caps.can_suspend = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n",
           TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetCapabilities) unexpected error: %s (%d)\n",
           TranslateError(err), err);
    return JNI_ERR;
  }

  /* set event callback */
  LOG("setting event callbacks ...\n");
  (void) memset(&callbacks, 0, sizeof(callbacks));
  callbacks.Breakpoint = &Breakpoint;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("(SetEventCallbacks) unexpected error: %s (%d)\n",
           TranslateError(err), err);
    return JNI_ERR;
  }

  return JNI_OK;
}
