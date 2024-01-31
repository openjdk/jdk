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
Java_getstacktr09_check(JNIEnv *env, jclass cls, jthread thread1, jthread thread2) {
  jvmtiError err;
  jvmtiFrameInfo frame;
  jint count;

  if (jvmti == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return STATUS_FAILED;
  }

  if (printdump == JNI_TRUE) {
    LOG(">>> Not yet started thread check ...\n");
  }
  err = jvmti->GetStackTrace(thread1, 0, 1, &frame, &count);
  if (err != JVMTI_ERROR_THREAD_NOT_ALIVE) {
    LOG("For not yet started thread:\n");
    LOG("Error expected: JVMTI_ERROR_THREAD_NOT_ALIVE, got: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  if (printdump == JNI_TRUE) {
    LOG(">>> Already finished thread check ...\n");
  }
  err = jvmti->GetStackTrace(thread2, 0, 1, &frame, &count);
  if (err != JVMTI_ERROR_THREAD_NOT_ALIVE) {
    LOG("For already finished thread:\n");
    LOG("Error expected: JVMTI_ERROR_THREAD_NOT_ALIVE, got: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  if (printdump == JNI_TRUE) {
    LOG(">>> ... done\n");
  }

  return result;
}

}
