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
#include <atomic>

#include "jvmti.h"
#include "jvmti_common.h"

extern "C" {

static jvmtiEnv *jvmti = nullptr;
static jrawMonitorID monitor;

JNIEXPORT void JNICALL
Java_RawMonitorTest_rawMonitorEnter(JNIEnv *env, jobject obj) {
  jthread vthread = get_current_thread(jvmti, env);
  jthread cthread = get_carrier_thread(jvmti, env, vthread);
  char*   vt_name = get_thread_name(jvmti, env, vthread);
  char*   ct_name = get_thread_name(jvmti, env, cthread);

  jvmtiError err = jvmti->RawMonitorEnter(monitor);
  check_jvmti_status(env, err, "Fatal Error in RawMonitorEnter");

  LOG("\n%s/%s: rawMonitorEnter: entered\n", vt_name, ct_name);

  deallocate(jvmti, env, (void*)vt_name);
  deallocate(jvmti, env, (void*)ct_name);
}

JNIEXPORT void JNICALL
Java_RawMonitorTest_rawMonitorExit(JNIEnv *env, jobject obj) {
  jthread vthread = get_current_thread(jvmti, env);
  jthread cthread = get_carrier_thread(jvmti, env, vthread);
  char*   vt_name = get_thread_name(jvmti, env, vthread);
  char*   ct_name = get_thread_name(jvmti, env, cthread);

  jvmtiError err = jvmti->RawMonitorExit(monitor);
  check_jvmti_status(env, err, "Fatal Error in RawMonitorExit");

  LOG("%s/%s: rawMonitorExit: exited\n", vt_name, ct_name);

  deallocate(jvmti, env, (void*)vt_name);
  deallocate(jvmti, env, (void*)ct_name);
}

JNIEXPORT void JNICALL
Java_RawMonitorTest_rawMonitorWait(JNIEnv *env, jobject obj) {
  jthread vthread = get_current_thread(jvmti, env);
  jthread cthread = get_carrier_thread(jvmti, env, vthread);
  char*   vt_name = get_thread_name(jvmti, env, vthread);
  char*   ct_name = get_thread_name(jvmti, env, cthread);

  LOG("%s/%s: rawMonitorWait: before waiting\n", vt_name, ct_name);

  jvmtiError err = jvmti->RawMonitorWait(monitor, 1L);
  check_jvmti_status(env, err, "Fatal Error in RawMonitorWait");

  LOG("\n%s/%s: rawMonitorWait: after waiting\n", vt_name, ct_name);

  deallocate(jvmti, env, (void*)vt_name);
  deallocate(jvmti, env, (void*)ct_name);
}

JNIEXPORT void JNICALL
Java_RawMonitorTest_rawMonitorNotifyAll(JNIEnv *env, jobject obj) {
  jvmtiError err = jvmti->RawMonitorNotifyAll(monitor);
  check_jvmti_status(env, err, "Fatal Error in RawMonitorNotifyAll");

}

extern JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;

  LOG("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **)(&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  memset(&caps, 0, sizeof (caps));
  caps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI AddCapabilities: %d\n", err);
    return JNI_ERR;
  }
  monitor = create_raw_monitor(jvmti, "Raw monitor to test");

  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

} // extern "C"
