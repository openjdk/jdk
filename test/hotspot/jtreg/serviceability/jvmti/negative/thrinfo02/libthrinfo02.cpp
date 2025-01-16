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
#include "jvmti_common.hpp"

extern "C" {


#define PASSED 0
#define STATUS_FAILED 2

static jvmtiEnv *jvmti = nullptr;
static jint result = PASSED;
static jboolean printdump = JNI_FALSE;

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jint res;

  if (options != nullptr && strcmp(options, "printdump") == 0) {
    printdump = JNI_TRUE;
  }

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_thrinfo02_check(JNIEnv *env, jclass cls, jthread thr, jthreadGroup group) {
  jvmtiError err;
  jvmtiThreadInfo inf;

  if (jvmti == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return STATUS_FAILED;
  }

  if (printdump == JNI_TRUE) {
    LOG(">>> invalid thread check ...\n");
  }
  err = jvmti->GetThreadInfo(nullptr, &inf);
  if (err != JVMTI_ERROR_NONE) {
      LOG("Error expected: JVMTI_ERROR_NONE,\n");
      LOG("           got: %s (%d)\n", TranslateError(err), err);
      result = STATUS_FAILED;
  }

  if (inf.name == nullptr || strcmp(inf.name, "main")) {
    LOG("Thread %s: incorrect name: %s\n", "main", inf.name);
    result = STATUS_FAILED;
  }
  if (inf.priority != JVMTI_THREAD_NORM_PRIORITY) {
    LOG("Thread %s: priority expected: %d, got: %d\n", "main", JVMTI_THREAD_NORM_PRIORITY, inf.priority);
    result = STATUS_FAILED;
  }
  if (inf.is_daemon != 0) {
    LOG("Thread %s: is_daemon expected: %d, got: %d\n", "main", 0, inf.is_daemon);
    result = STATUS_FAILED;
  }
  if (!env->IsSameObject(group, inf.thread_group)) {
    LOG("Thread %s: invalid thread group\n", "main");
    result = STATUS_FAILED;
  }

  if (printdump == JNI_TRUE) {
    LOG(">>> null pointer check ...\n");
  }
  err = jvmti->GetThreadInfo(thr, nullptr);
  if (err != JVMTI_ERROR_NULL_POINTER) {
    LOG("Error expected: JVMTI_ERROR_NULL_POINTER,\n");
    LOG("           got: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  return result;
}

}
