/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmti_thread.h"

#define MAX_FRAME_COUNT 80

const char CONTINUATION_CLASS_NAME[] = "jdk/internal/vm/Continuation";
const char CONTINUATION_METHOD_NAME[] = "enter";

static void test_stack_trace(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  jvmtiFrameInfo frames[MAX_FRAME_COUNT];
  jint count = -1;
  jmethodID method = nullptr;
  jvmtiError err;

  err = jvmti->GetStackTrace(vthread, 0, MAX_FRAME_COUNT, frames, &count);
  if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
    LOG("Agent: No stacktrace for non-alive vthread\n");
    return;
  }
  check_jvmti_status(jni, err, "GetStackTrace returns error");
  if (count <= 0) {
    LOG("Agent: Stacktrace in virtual thread is incorrect: count: %d\n", count);
    print_thread_info(jvmti, jni, vthread);
    print_stack_trace_frames(jvmti, jni, count, frames);
    fatal(jni, "Incorrect frame count");
  }

  method = frames[count - 1].method;

  const char* class_name = get_method_class_name(jvmti, jni, method);
  const char* method_name = get_method_name(jvmti, jni, method);

  if (strcmp(CONTINUATION_CLASS_NAME, class_name) != 0 || strcmp(CONTINUATION_METHOD_NAME, method_name) != 0) {
    LOG("Agent: Stacktrace of virtual thread is incorrect: doesn't start from enter(...):\n");
    print_stack_trace_frames(jvmti, jni, count, frames);
    fatal(jni, "incorrect stacktrace");
  }
}


/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv * jvmti, JNIEnv * jni, void * arg) {

  static jlong timeout = 0;
  LOG("Agent: wait for thread to start\n");
  if (!agent_wait_for_sync(timeout)) {
    return;
  }
  if (!agent_resume_sync()) {
    return;
  }
  LOG("Agent: started\n");

  while (true) {
    jthread *threads = nullptr;
    jint count = 0;
    jvmtiError err;

    sleep_ms(100);

    err = jvmti->GetAllThreads(&count, &threads);
    if (err == JVMTI_ERROR_WRONG_PHASE) {
      return;
    }
    check_jvmti_status(jni, err,  "Error in JVMTI GetAllThreads");
    for (int i = 0; i < count; i++) {
      jthread tested_thread = nullptr;

      err = GetVirtualThread(jvmti, jni, threads[i], &tested_thread);
      if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
        continue;
      }
      if (err == JVMTI_ERROR_WRONG_PHASE) {
        return;
      }
      check_jvmti_status(jni, err,  "Error in JVMTI extension GetVirtualThread");
      if (tested_thread != nullptr) {
        test_stack_trace(jvmti, jni, tested_thread);
      }
    }
    check_jvmti_status(jni, jvmti->Deallocate((unsigned char *) threads), "Error in JVMTI Deallocate");
  }
  LOG("Agent: finished\n");
}


extern JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;
  jvmtiEnv* jvmti;

  LOG("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI AddCapabilities: %d\n", err);
  }

  err = init_agent_data(jvmti, &agent_data);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  if (set_agent_proc(agentProc, nullptr) != JNI_TRUE) {
    return JNI_ERR;
  }

  LOG("Agent_OnLoad finished\n");
  return 0;
}
