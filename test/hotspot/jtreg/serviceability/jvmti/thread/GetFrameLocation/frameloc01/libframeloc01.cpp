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

static jvmtiEnv *jvmti_env = nullptr;
static jint result = PASSED;
static jmethodID mid1;

// If mustPass is false we just check if we have reached the correct instruction location.
// This is used to wait for the child thread to reach the expected position.
jboolean checkFrame(jvmtiEnv *jvmti_env, JNIEnv *jni, jthread thr, jmethodID exp_mid,
                    jlocation exp_loc, jlocation exp_loc_alternative, jboolean mustPass) {
  jvmtiError err;
  jmethodID mid = nullptr;
  jlocation loc = -1;
  char *meth, *sig, *generic;
  jboolean isOk = JNI_FALSE;

  err = jvmti_env->GetMethodName(exp_mid, &meth, &sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodName) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }

  err = jvmti_env->GetFrameLocation(thr, 0, &mid, &loc);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetFrameLocation#%s) unexpected error: %s (%d)\n", meth, TranslateError(err), err);
    result = STATUS_FAILED;
  } else {
    if (exp_mid != mid) {
      LOG("Method \"%s\" current frame's method ID", meth);
      LOG(" expected: 0x%p, got: 0x%p\n", exp_mid, mid);
      result = STATUS_FAILED;
    }
    isOk = exp_loc == loc || exp_loc_alternative == loc;
    if (!isOk && mustPass) {
      LOG("Method \"%s\" current frame's location", meth);
      LOG(" expected: 0x%x or 0x%x, got: 0x%x%08x\n",
          (jint) exp_loc, (jint) exp_loc_alternative, (jint) (loc >> 32), (jint) loc);
      result = STATUS_FAILED;
    }
  }
  return isOk && result == PASSED;
}

void JNICALL
ExceptionCatch(jvmtiEnv *jvmti_env, JNIEnv *env, jthread thr,
               jmethodID method, jlocation location, jobject exception) {
  if (method == mid1) {
    checkFrame(jvmti_env, (JNIEnv *) env, thr, method,
               location, location, JNI_TRUE);
  }
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiEventCallbacks callbacks;
  jvmtiError err;

  jint res = jvm->GetEnv((void **) &jvmti_env, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti_env == nullptr) {
    LOG("Wrong result of a valid call to GetEnv !\n");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_suspend = true;
  caps.can_generate_exception_events = true;
  err = jvmti_env->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
   LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  callbacks.ExceptionCatch = &ExceptionCatch;
  err = jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("(SetEventCallbacks) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_frameloc01_getReady(JNIEnv *jni, jclass cls, jclass klass) {
  jvmtiError err;
  mid1 = jni->GetMethodID(klass, "meth01", "(I)V");
  if (mid1 == nullptr) {
    LOG("Cannot get jmethodID for method \"meth01\"\n");
    result = STATUS_FAILED;
    return;
  }

  err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_EXCEPTION_CATCH, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(SetEventNotificationMode) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
  }
}

JNIEXPORT jboolean JNICALL
Java_frameloc01_checkFrame01(JNIEnv *jni, jclass cls, jthread thr, jclass klass, jboolean mustPass) {
  jmethodID mid;
  jboolean isOk = JNI_FALSE;

  mid = jni->GetMethodID(klass, "run", "()V");
  if (mid == nullptr) {
    LOG("Cannot get jmethodID for method \"run\"\n");
    result = STATUS_FAILED;
    return JNI_TRUE;
  }

  suspend_thread(jvmti_env, jni, thr);

  // This tests the location of a throw/catch statement.
  // The returned location may be either the throw or the catch statement.
  // It seems like the throw statement is returned in compiled code (-Xcomp),
  // but the catch statement is returned in interpreted code.
  // Both locations are valid.
  // See bug JDK-4527281.
  isOk = checkFrame(jvmti_env, jni, thr, mid, 31, 32, mustPass);

  resume_thread(jvmti_env, jni, thr);
  return isOk && result == PASSED;
}

JNIEXPORT jint JNICALL
Java_frameloc01_getRes(JNIEnv *env, jclass cls) {
  return result;
}

}
