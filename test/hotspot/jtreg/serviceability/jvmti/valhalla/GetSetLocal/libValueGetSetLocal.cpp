/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

JNIEXPORT void JNICALL
Java_ValueGetSetLocal_testLocals(JNIEnv *jni, jclass thisClass, jthread thread, jboolean testSetLocal) {
  const jint depth = 1;

  LOG("\ntestLocals\n");
  jobject obj0 = get_local_object(jvmti, jni, thread, depth, 0);
  jobject obj1 = get_local_object(jvmti, jni, thread, depth, 1);
  jobject obj2 = get_local_object(jvmti, jni, thread, depth, 2);
  jobject obj3 = get_local_object(jvmti, jni, thread, depth, 3);
  jobject obj_this = get_local_instance(jvmti, jni, thread, depth);

  // obj0 is expected to be equal "this"
  if (!jni->IsSameObject(obj0, obj_this)) {
    fatal(jni, "Failed: obj0 != obj_this\n");
  }
  // obj3 is expected to be equal obj2
  if (!jni->IsSameObject(obj3, obj2)) {
    fatal(jni, "Failed: obj3 != obj2\n");
  }

  if (testSetLocal) {
    // set obj3 = obj1
    set_local_object(jvmti, thread, depth, 3, obj1);
    obj3 = get_local_object(jvmti, jni, thread, depth, 3);
    if (!jni->IsSameObject(obj3, obj1)) {
      fatal(jni, "Failed: obj3 != obj1\n");
    }
  }
}

#ifdef __cplusplus
}
#endif

