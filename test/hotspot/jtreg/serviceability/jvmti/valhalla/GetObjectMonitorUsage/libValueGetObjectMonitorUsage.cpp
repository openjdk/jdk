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
  caps.can_get_monitor_info = 1;
  jvmtiError err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("AddCapabilities failed: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT jboolean JNICALL
Java_ValueGetObjectMonitorUsage_nTestGetObjectMonitorUsage(JNIEnv *jni, jclass thisClass, jobject obj) {
  bool result = true;
  jvmtiMonitorUsage info;
  memset(&info, 0, sizeof(info));
  check_jvmti_error(jvmti->GetObjectMonitorUsage(obj, &info), "GetObjectMonitorUsage");

  if (info.owner != nullptr) {
    LOG("ERROR: owner is not nullptr\n");
    result = false;
  }
  if (info.entry_count != 0) {
    LOG("ERROR: entry_count is non-zero: %d\n", (int)info.entry_count);
    result = false;
  }
  if (info.waiter_count != 0) {
    LOG("ERROR: waiter_count is no-zero: %d\n", (int)info.waiter_count);
    result = false;
  }
  if (info.notify_waiter_count != 0) {
    LOG("ERROR: notify_waiter_count is no-zero: %d\n", (int)info.notify_waiter_count);
    result = false;
  }

  jvmti->Deallocate((unsigned char *)info.waiters);
  jvmti->Deallocate((unsigned char *)info.notify_waiters);

  return result ? JNI_TRUE : JNI_FALSE;
}

#ifdef __cplusplus
}
#endif

