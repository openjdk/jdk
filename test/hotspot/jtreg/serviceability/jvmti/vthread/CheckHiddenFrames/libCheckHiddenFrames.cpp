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

#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

const int MAX_COUNT = 50;
static jvmtiEnv *jvmti = nullptr;

static char*
get_frame_method_name(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jint depth) {
  jmethodID method = nullptr;
  jlocation location = 0;

  jvmtiError err = jvmti->GetFrameLocation(thread, 0, &method, &location);
  check_jvmti_status(jni, err, "get_method_name_at_depth: error in JVMTI GetFrameLocation");

  return get_method_name(jvmti, jni, method);
}

static bool
method_must_be_hidden(char* mname) {
  return strcmp(mname, "yield")  == 0 ||
         strcmp(mname, "yield0") == 0;
}

static jboolean
check_top_frames_location(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread) {
  jboolean status = JNI_TRUE;

  for (int depth = 0; depth < 2; depth++) {
    char* mname = get_frame_method_name(jvmti, jni, thread, depth);

    if (method_must_be_hidden(mname)) {
      LOG("Failed: GetFrameLocation returned info for frame expected to be hidden: frame[%d]=%s\n", depth, mname);
      status = JNI_FALSE;
    }
    deallocate(jvmti, jni, mname);
  }
  return status;
}

static jboolean
check_top_frames_in_stack_trace(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread) {
  jboolean status = JNI_TRUE;
  jvmtiFrameInfo frameInfo[MAX_COUNT];
  jint count1 = 0;
  jint count2 = 0;

  jvmtiError err = jvmti->GetStackTrace(thread, 0, MAX_COUNT, frameInfo, &count1);
  check_jvmti_status(jni, err, "check_top_frames_in_stack_trace: error in JVMTI GetStackTrace");

  for (int depth = 0; depth < 2; depth++) {
    char* mname = get_method_name(jvmti, jni, frameInfo[depth].method);

    if (method_must_be_hidden(mname)) {
      LOG("Failed: GetStackTrace returned info for frame expected to be hidden: frame[%d]=%s\n", depth, mname);
      status = JNI_FALSE;
    }
    deallocate(jvmti, jni, mname);
  }

  err = jvmti->GetFrameCount(thread, &count2);
  check_jvmti_status(jni, err, "check_top_frames_in_stack_trace: error in JVMTI GetFrameCount");

  if (count1 != count2) {
    LOG("Failed: frame counts returned by GetStackTrace and GetFrameCount do not match: %d!=%d\n", count1, count2);
    status = JNI_FALSE;
  }
  return status;
}

JNIEXPORT jboolean JNICALL
Java_CheckHiddenFrames_checkHidden(JNIEnv *jni, jclass clazz, jthread thread) {
  jboolean status = JNI_TRUE;

  wait_for_state(jvmti, jni, thread, JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT);
  print_stack_trace(jvmti, jni, thread);


  if (!check_top_frames_location(jvmti, jni, thread)) {
    status = JNI_FALSE;
  }
  if (!check_top_frames_in_stack_trace(jvmti, jni, thread)) {
    status = JNI_FALSE;
  }
  return status;
}

extern JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  LOG("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **)(&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

} // extern "C"
