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
  return JNI_OK;
}

JNIEXPORT jboolean JNICALL
Java_ValueGetClassFields_nTestGetClassFields(JNIEnv *jni, jclass thisClass, jclass cls, jint fieldNum) {
  bool result = true;
  jint field_count;
  jfieldID* fields = nullptr;
  check_jvmti_error(jvmti->GetClassFields(cls, &field_count, &fields), "GetClassFields");

  if (field_count != fieldNum) {
    LOG("ERROR: GetClassFields returned unexpected field count: %d (expected %d)\n", (int)field_count, (int)fieldNum);
    result = false;
  } else {
    // Use GetFieldName to verify correctness of the returned fields.
    for (jint i = 0; i < field_count; i++) {
      char *name = nullptr;
      char *signature = nullptr;

      check_jvmti_error(jvmti->GetFieldName(cls, fields[i], &name, &signature, nullptr), "GetFieldName");

      LOG(" - field %s, sig = %s\n", name, signature);
      jvmti->Deallocate((unsigned char *)name);
      jvmti->Deallocate((unsigned char *)signature);
    }
  }

  jvmti->Deallocate((unsigned char *)fields);
  return result ? JNI_TRUE : JNI_FALSE;
}

#ifdef __cplusplus
}
#endif

