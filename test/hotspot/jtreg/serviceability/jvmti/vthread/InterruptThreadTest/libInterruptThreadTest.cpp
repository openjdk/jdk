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
#include <atomic>

#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv *jvmti = nullptr;

static void JNICALL
agent_proc(jvmtiEnv* jvmti, JNIEnv* jni, void* arg) {
  fatal(jni, "agent function was not expected to be called");
}

JNIEXPORT jboolean JNICALL
Java_InterruptThreadTest_testJvmtiFunctionsInJNICall(JNIEnv *jni, jobject obji, jthread vthread) {
  jvmtiError err = JVMTI_ERROR_NONE;

  LOG("testJvmtiFunctionsInJNICall: started\n");

  // test JVMTI InterruptThread
  err = jvmti->InterruptThread(vthread);
  check_jvmti_status(jni, err, "InterruptThread");

  LOG("testJvmtiFunctionsInJNICall: finished\n");

  return JNI_TRUE;
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
  caps.can_signal_thread = 1;
  caps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI AddCapabilities: %d\n", err);
    return JNI_ERR;
  }

  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

} // extern "C"
