/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmti.h"
#include "jvmti_common.h"

extern "C" {

#define MAX_FRAME_COUNT 30

enum Slots {
  SlotInvalid0 = -1,
  SlotString = 0,
  SlotThread = 1,
  SlotInt = 2,
  SlotLong = 3,
  SlotUnaligned = 4,
  SlotFloat = 5,
  SlotDouble = 6,
};

static jvmtiEnv *jvmti = nullptr;

static void
check_jvmti_error_not_suspended(JNIEnv* jni, const char* func_name, jvmtiError err) {
  if (err != JVMTI_ERROR_THREAD_NOT_SUSPENDED) {
    LOG("%s failed: expected JVMTI_ERROR_THREAD_NOT_SUSPENDED instead of: %d\n", func_name, err);
    fatal(jni, func_name);
  }
}

static void
test_GetLocal(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  jvmtiError err;
  const int depth = 0;

  jobject msg = nullptr;
  jint ii = 0;
  jlong ll = 0L;
  jfloat ff = 0.0;
  jdouble dd = 0.0;

  LOG("\ntest_GetLocal started\n");

  // #0: Test JVMTI GetLocalInstance function
  err = jvmti->GetLocalInstance(thread, depth, &msg);
  check_jvmti_error_not_suspended(jni, "GetLocalInstance", err);
  LOG("check for JVMTI GetLocalInstance succeeded\n");

  // #1: Test JVMTI GetLocalObject function
  err = jvmti->GetLocalObject(thread, depth, SlotString, &msg);
  check_jvmti_error_not_suspended(jni, "GetLocalObject", err);
  LOG("check for JVMTI GetLocalObject succeeded\n");

  // #2: Test JVMTI GetLocalInt function
  err = jvmti->GetLocalInt(thread, depth, SlotInt, &ii);
  check_jvmti_error_not_suspended(jni, "GetLocalInt", err);
  LOG("check for JVMTI GetLocalInt succeeded\n");

  // #3: Test JVMTI GetLocalLong function
  err = jvmti->GetLocalLong(thread, depth, SlotLong, &ll);
  check_jvmti_error_not_suspended(jni, "GetLocalLong", err);
  LOG("check for JVMTI GetLocalLong succeeded\n");

  // #4: Test JVMTI GetLocalFloat function
  err = jvmti->GetLocalFloat(thread, depth, SlotFloat, &ff);
  check_jvmti_error_not_suspended(jni, "GetLocalFloat", err);
  LOG("check for JVMTI GetLocalFloat succeeded\n");

  // #5: Test JVMTI GetLocalDouble function
  err = jvmti->GetLocalDouble(thread, depth, SlotDouble, &dd);
  check_jvmti_error_not_suspended(jni, "GetLocalDouble", err);
  LOG("check for JVMTI GetLocalDouble succeeded\n");

  LOG("test_GetLocal finished\n");
}

static void
test_SetLocal(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  jvmtiError err;
  const int depth = 0;

  const jobject msg = nullptr;
  const jint ii = 0;
  const jlong ll = 0L;
  const jfloat ff = 0.0;
  const jdouble dd = 0.0;

  LOG("\ntest_SetLocal started\n");

  // #1: Test JVMTI SetLocalObject function
  err = jvmti->SetLocalObject(thread, depth, SlotString, msg);
  check_jvmti_error_not_suspended(jni, "SetLocalObject", err);
  LOG("check for JVMTI SetLocalObject succeeded\n");

  // #2: Test JVMTI SetLocalInt function
  err = jvmti->SetLocalInt(thread, depth, SlotInt, ii);
  check_jvmti_error_not_suspended(jni, "SetLocalInt", err);
  LOG("check for JVMTI SetLocalInt succeeded\n");

  // #3: Test JVMTI SetLocalLong function
  err = jvmti->SetLocalLong(thread, depth, SlotLong, ll);
  check_jvmti_error_not_suspended(jni, "SetLocalLong", err);
  LOG("check for JVMTI SetLocalLong succeeded\n");

  // #4: Test JVMTI SetLocalFloat function
  err = jvmti->SetLocalFloat(thread, depth, SlotFloat, ff);
  check_jvmti_error_not_suspended(jni, "SetLocalFloat", err);
  LOG("check for JVMTI SetLocalFloat succeeded\n");

  // #5: Test JVMTI SetLocalDouble function
  err = jvmti->SetLocalDouble(thread, depth, SlotDouble, dd);
  check_jvmti_error_not_suspended(jni, "SetLocalDouble", err);
  LOG("check for JVMTI SetLocalDouble succeeded\n");

  LOG("test_SetLocal finished\n");
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;

  LOG("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = 1;
  caps.can_access_local_variables = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI AddCapabilities: %d\n", err);
    return JNI_ERR;
  }
  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_GetSetLocalUnsuspended_testUnsuspendedThread(JNIEnv *jni, jclass klass, jthread thread) {
  char* tname = get_thread_name(jvmti, jni, thread);
  jmethodID method = nullptr;
  jlocation location = 0;

  LOG("\ntestUnsuspendedThread: started for thread: %s\n", tname);

  test_GetLocal(jvmti, jni, thread);
  test_SetLocal(jvmti, jni, thread);

  LOG("\ntestUnsuspendedThread: finished for thread: %s\n", tname);
  deallocate(jvmti, jni, (void*)tname);
}

} // extern "C"
