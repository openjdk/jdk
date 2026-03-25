/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jni.h"
#include "jvmti_common.hpp"

#ifdef __cplusplus
extern "C" {
#endif

static jvmtiEnv *jvmti = nullptr;


JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("GetEnv failed, res = %d", (int)res);
    return JNI_ERR;
  }

  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_access_local_variables = 1;
  jvmtiError err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("AddCapabilities failed: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  return JNI_OK;
}

static void log_value(JNIEnv *jni, jobject value) {
  jclass cls = jni->GetObjectClass(value);
  if (cls == nullptr) {
    LOG("ERROR: value class is nullptr\n");
    return;
  }

  char* sig = nullptr;
  check_jvmti_error(jvmti->GetClassSignature(cls, &sig, nullptr), "GetClassSignature");

  LOG(" - the value class: %s\n", sig);
  jvmti->Deallocate((unsigned char *)sig);
}

static jobject get_local(JNIEnv *jni, jthread thread, jint depth, jint slot) {
  LOG("GetLocalObject for slot %d...\n", (int)slot);
  jobject value = nullptr;
  check_jvmti_error(jvmti->GetLocalObject(thread, depth, slot, &value), "GetLocalObject");

  log_value(jni, value);

  return value;
}

static void set_local(jthread thread, jint depth, jint slot, jobject value) {
  LOG("SetLocalObject for slot %d...\n", (int)slot);
  check_jvmti_error(jvmti->SetLocalObject(thread, depth, slot, value), "SetLocalObject");
}

static jobject get_this(JNIEnv *jni, jthread thread, jint depth) {
  LOG("GetLocalInstance...\n");
  jobject value = nullptr;
  check_jvmti_error(jvmti->GetLocalInstance(thread, depth, &value), "GetLocalInstance");

  log_value(jni, value);

  return value;
}

JNIEXPORT jboolean JNICALL
Java_ValueGetSetLocal_nTestLocals(JNIEnv *jni, jclass thisClass, jthread thread, jboolean testSetLocal) {
  bool result = true;
  const jint depth = 1;

  jobject obj0 = get_local(jni, thread, depth, 0);
  jobject obj1 = get_local(jni, thread, depth, 1);
  jobject obj2 = get_local(jni, thread, depth, 2);
  jobject obj3 = get_local(jni, thread, depth, 3);
  jobject obj_this = get_this(jni, thread, depth);

  // obj0 is expected to be equal "this"
  if (!jni->IsSameObject(obj0, obj_this)) {
    LOG("ERROR: obj0 != obj_this\n");
    result = false;
  }
  // obj3 is expected to be equal obj2
  if (!jni->IsSameObject(obj3, obj2)) {
    LOG("ERROR: obj3 != obj2\n");
    result = false;
  }

  if (testSetLocal) {
    // set obj3 = obj1
    set_local(thread, depth, 3, obj1);
  }

  return result ? JNI_TRUE : JNI_FALSE;
}

#ifdef __cplusplus
}
#endif

