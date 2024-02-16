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
#include <string.h>
#include "jvmti.h"
#include "jvmti_common.h"

extern "C" {

typedef struct {
  const char *name;
  jboolean is_name_exact;
  jint priority;
  jboolean is_daemon;
} info;

static jvmtiEnv *jvmti_env = nullptr;
static info expected_info_array[] = {
    {"main", JNI_TRUE,JVMTI_THREAD_NORM_PRIORITY, JNI_FALSE},
    {"thread1",JNI_TRUE,JVMTI_THREAD_MIN_PRIORITY + 2, JNI_TRUE},
    {"Thread-", JNI_FALSE,JVMTI_THREAD_MIN_PRIORITY, JNI_TRUE},
    {"vthread", JNI_FALSE,JVMTI_THREAD_NORM_PRIORITY, JNI_TRUE}
};

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jint res;
  jvmtiCapabilities caps;

  res = jvm->GetEnv((void **) &jvmti_env, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti_env == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = 1;
  res = jvmti_env->AddCapabilities(&caps);
  if (res != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI AddCapabilities: %d\n", res);
    return JNI_ERR;
  }
  return JNI_OK;
}

JNIEXPORT jboolean JNICALL
Java_thrinfo01_checkInfo0(JNIEnv *jni, jclass cls, jthread thread, jthreadGroup thread_group, jint expected_idx) {
  jboolean result = JNI_TRUE;
  jvmtiThreadInfo inf;

  LOG("Checking thread info for\n");
  print_thread_info(jvmti_env, jni, thread);

  info expected_info = expected_info_array[expected_idx];

  check_jvmti_status(jni, jvmti_env->GetThreadInfo(thread, &inf), "Error in GetThreadInfo.");
  if (inf.name == nullptr) {
    LOG("Thread %s: incorrect name in null\n", expected_info.name);
    result = JNI_FALSE;
  }


  if (strstr(inf.name, expected_info.name) != inf.name ||
      (expected_info.is_name_exact && strlen(inf.name) != strlen(expected_info.name))) {
    LOG("Thread %s: incorrect name: %s\n", expected_info.name, inf.name);
    result = JNI_FALSE;
  }

  if (inf.priority != expected_info.priority) {
    LOG("Thread %s: priority expected: %d, got: %d\n", expected_info.name, expected_info.priority, inf.priority);
    result = JNI_FALSE;
  }
  if (inf.is_daemon != expected_info.is_daemon) {
    LOG("Thread %s: is_daemon expected: %d, got: %d\n", expected_info.name, expected_info.is_daemon, inf.is_daemon);
    result = JNI_FALSE;
  }
  if (!jni->IsSameObject(thread_group, inf.thread_group)) {
    LOG("Thread %s: invalid thread thread_group\n", expected_info.name);
    result = JNI_FALSE;
  }

  LOG("Check completed.\n");
  return result;
}


}
