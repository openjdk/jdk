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
static jvmtiCapabilities caps;
static jint result = PASSED;
static jboolean printdump = JNI_FALSE;


JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiError err;
  jint res;

  if (options != nullptr && strcmp(options, "printdump") == 0) {
    printdump = JNI_TRUE;
  }

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  err = jvmti->GetPotentialCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetPotentialCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  if (!caps.can_suspend) {
    LOG("Warning: suspend/resume is not implemented\n");
  }

  return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_frameloc03_check(JNIEnv *env, jclass cls, jthread thr) {
  jvmtiError err;
  jmethodID mid;
  jlocation loc;

  if (jvmti == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return STATUS_FAILED;
  }

  if (printdump == JNI_TRUE) {
    LOG(">>> invalid thread check ...\n");
  }
  err = jvmti->GetFrameLocation(cls, 0, &mid, &loc);
  if (err != JVMTI_ERROR_INVALID_THREAD) {
    LOG("Error expected: JVMTI_ERROR_INVALID_THREAD,\n");
    LOG("        actual: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    }

  if (!caps.can_suspend) {
    return result;
  }

  err = jvmti->SuspendThread(thr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(SuspendThread) unexpected error: %s (%d)\n",
               TranslateError(err), err);
    result = STATUS_FAILED;
  }

  if (printdump == JNI_TRUE) {
    LOG(">>> invalid depth check ...\n");
  }
  err = jvmti->GetFrameLocation(thr, -1, &mid, &loc);
  if (err != JVMTI_ERROR_ILLEGAL_ARGUMENT) {
    LOG("Error expected: JVMTI_ERROR_ILLEGAL_ARGUMENT,\n");
    LOG("        actual: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  if (printdump == JNI_TRUE) {
    LOG(">>> (methodPtr) null pointer check ...\n");
  }
  err = jvmti->GetFrameLocation(thr, 0, nullptr, &loc);
  if (err != JVMTI_ERROR_NULL_POINTER) {
    LOG("Error expected: JVMTI_ERROR_NULL_POINTER,\n");
    LOG("        actual: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  if (printdump == JNI_TRUE) {
    LOG(">>> (locationPtr) null pointer check ...\n");
  }
  err = jvmti->GetFrameLocation(thr, 0, &mid, nullptr);
  if (err != JVMTI_ERROR_NULL_POINTER) {
    LOG("Error expected: JVMTI_ERROR_NULL_POINTER,\n");
    LOG("        actual: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  err = jvmti->ResumeThread(thr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(ResumeThread) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  if (printdump == JNI_TRUE) {
    LOG(">>> ... done\n");
  }

  return result;
}

}
