/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "jni.h"
#include "jvmti.h"
#include "jvmti_common.hpp"

static jvmtiEnv *jvmti;

extern "C" {

extern JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiError err;
  jvmtiCapabilities caps;

  if (jvm->GetEnv((void **) &jvmti, JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_suspend = 1;
  caps.can_access_local_variables = 1;
  err = jvmti->AddCapabilities( &caps);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_GetCtorLocalStrictInitFields_suspendThread(JNIEnv *jni, jclass ignore, jthread target) {
  return (jint) jvmti->SuspendThread(target);
}

JNIEXPORT jint JNICALL
Java_GetCtorLocalStrictInitFields_resumeThread(JNIEnv *jni, jclass ignore, jthread target) {
  return (jint) jvmti->ResumeThread(target);
}


JNIEXPORT jint JNICALL
Java_GetCtorLocalStrictInitFields_testGetLocalInstance(JNIEnv *jni, jclass ignore, jthread thread) {
  const jint depth = 0;

  jmethodID method = get_frame_method(jvmti, jni, thread, depth);
  char* mname = get_method_name(jvmti, jni, method);
  jobject obj_this = nullptr;
  jobject obj0 = nullptr;

  LOG("\ntestGetLocalInstance: frame method: %s\n", mname);

  jvmtiError err = jvmti->GetLocalInstance(thread, depth, &obj_this);
  LOG("testGetLocalInstance: GetLocalInstance returned error code: %s (%d)\n", TranslateError(err), err);

  jvmtiError err_obj = jvmti->GetLocalObject(thread, depth, 0, &obj0);
  LOG("testGetLocalInstance: GetLocalObject returned error code: %s (%d)\n", TranslateError(err_obj), err_obj);

  if (err_obj != err) {
    LOG("Failed: expected from GetLocalObject same error code as from GetLocalInstance: expected %s (%d), got: %s (%d)\n",
        TranslateError(err), err, TranslateError(err_obj), err_obj);

    jni->FatalError("Failed: expected from GetLocalObject same error code as from GetLocalInstance");
  }
  if (err == JVMTI_ERROR_NONE) {
    if ((obj_this == nullptr || obj0 == nullptr)) {
      LOG("Failed: expected non-null object refs from GetLocalInstance (%p) and GetLocalObject(%p)\n", obj_this, obj0);
      jni->FatalError("Failed: expected non-null object refs from GetLocalInstance and GetLocalObject");
    }
    if (!jni->IsSameObject(obj_this, obj0)) {
      LOG("Failed: expected GetLocalInstance (%p) and GetLocalObject(%p) to return same object\n", obj_this, obj0);
      jni->FatalError("Failed: GetLocalInstance and GetLocalObject returned different object refs");
    }
  }
  deallocate(jvmti, jni, (void*)mname);
  return (jint)err;
}

}
