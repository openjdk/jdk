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
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv *jvmti_env = nullptr;

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jint res;

  res = jvm->GetEnv((void **) &jvmti_env, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti_env == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = true;
  caps.can_suspend = true;

  jvmtiError err = jvmti_env->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
   LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT jboolean JNICALL
Java_framecnt01_checkFrames0(JNIEnv *jni, jclass cls, jthread thread, jboolean suspend, jint expected_count) {
  jboolean result = JNI_TRUE;

  if (suspend) {
    suspend_thread(jvmti_env, jni, thread);
  }

  LOG("Testing:\n");
  print_stack_trace(jvmti_env, jni, thread);
  jint frame_count = get_frame_count(jvmti_env, jni, thread);
  if (frame_count != expected_count) {
    LOG("Thread #%s: number of frames expected: %d, got: %d\n",
        get_thread_name(jvmti_env, jni, thread), expected_count, frame_count);
    result = JNI_FALSE;
    print_stack_trace(jvmti_env, jni, thread); // DBG: print stack trace once more
  }

  if (suspend) {
    resume_thread(jvmti_env, jni, thread);
  }

  return result;
}

}
